/**
 * Helios, OpenSource Monitoring
 * Brought to you by the Helios Development Group
 *
 * Copyright 2007, Helios Development Group and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org. 
 *
 */
package com.heliosapm.script.compilers.groovy;

import groovy.lang.GroovyClassLoader;

import java.io.BufferedReader;
import java.io.File;
import java.io.Reader;
import java.io.StringReader;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.codehaus.groovy.ast.AnnotatedNode;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassCodeVisitorSupport;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.expr.ClassExpression;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.classgen.GeneratorContext;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.control.customizers.CompilationCustomizer;
import org.codehaus.groovy.control.customizers.ImportCustomizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.heliosapm.jmx.util.helpers.URLHelper;



/**
 * <p>Title: GroovyCompilationCustomizer</p>
 * <p>Description: Base groovy compiler customizer</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.script.compilers.groovy.GroovyCompilationCustomizer</code></p>
 * <p><li>
 * <ul><b>groovy.warnings</b>: Sets the warning level of the compiler:<li>
 *     <ul><b>{@link org.codehaus.groovy.control.messages.WarningMessage#NONE} (0)</b>: Ignore all errors</ul>
 *     <ul><b>{@link org.codehaus.groovy.control.messages.WarningMessage#LIKELY_ERRORS} (1)</b>: Likely errors</ul>
 *     <ul><b>{@link org.codehaus.groovy.control.messages.WarningMessage#POSSIBLE_ERRORS} (2)</b>: Possible errors</ul>
 *     <ul><b>{@link org.codehaus.groovy.control.messages.WarningMessage#PARANOIA} (3)</b>: Any and all errors</ul>
 * </li></ul>
 * <ul><b>groovy.source.encoding</b>: Sets the Source Encoding</ul>
 * <ul><b>groovy.target.bytecode</b>: Sets the Target Bytecode</ul>
 * <ul><b>groovy.classpath</b>: Sets the Classpath</ul>
 * <ul><b>groovy.output.verbose</b>: Sets the Verbosity (true/false)</ul>
 * <ul><b>groovy.output.debug</b>: Sets the Debug (true/false)</ul>
 * <ul><b>groovy.errors.tolerance</b>: Sets the Tolerance which is the number of non-fatal errors (per unit) that should be tolerated before compilation is aborted</ul>
 * <ul><b>groovy.script.extension</b>: Sets the Default Script Extension</ul>
 * <ul><b>groovy.script.base</b>: Sets the Script Base Class</ul>
 * </li></p>
 */

public class GroovyCompilationCustomizer {
	
	/** Understood compiler option keys */
	public static final Set<String> COMPILER_OPTIONS = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
			"groovy.warnings", "groovy.source.encoding", "groovy.target.bytecode", "groovy.classpath", "groovy.output.verbose", "groovy.output.debug", "groovy.errors.tolerance", "groovy.script.extension", "groovy.script.base"
	)));
	
	/** The default compiler configuration */
	protected final CompilerConfiguration defaultConfig;
	/** Instance logger */
	private final Logger log = LoggerFactory.getLogger(getClass());
	
	/** A set of default imports added to all compiler configurations */
	private final String[] imports = new String[]{
			"import com.heliosapm.script.annotations.*",		// configuration annotations
			"import javax.management.*", 						// JMX Core
			"import javax.management.remote.*", 				// JMX Remoting
			"import groovy.transform.*"							// Groovy AST transforms
	};
	
	/** The compiler context post-init code buffer key */
	public static final String POSTINIT_CODE_BUFFER = "post-code-buffer";
	/** The compiler context post-init fixture initializers key */
	public static final String POSTINIT_FIXTURES = "post-fixtures";
	/** The compiler context post-init fixture result initializers key */
	public static final String POSTINIT_FIXTURE_RESULTS = "post-fixture-results";
	
	/** An import customizer added to all compiler configs */
	protected final ImportCustomizer importCustomizer = new ImportCustomizer();
	/** Annotation finder to find script level annotations and promote them to the class level */
	protected final AnnotationFinder annotationFinder = new AnnotationFinder(CompilePhase.OUTPUT);  //CompilePhase.CANONICALIZATION);
	/** Fixture processor to add the fixture fields */
	protected final FixtureProcessor fixtureProcessor = new FixtureProcessor(CompilePhase.CANONICALIZATION);
	
	/** The compilation customizers to apply to the groovy compiler */
	protected final CompilationCustomizer[] all;
	
	/** A map to track what's been done during the compilation phase */
	protected final ConcurrentHashMap<String, Object> compilerContext = new ConcurrentHashMap<String, Object>();
	
	
	/** The default groovy class loader returned if no compilation customizations are found */
	protected final GroovyClassLoader defaultGroovyClassLoader;
	
	/** End of line splitter */
	protected static final Pattern EOL_SPLITTER = Pattern.compile("\n");
	/** Pattern to clean up the header line to convert into properties */
	protected static final Pattern CLEAN_HEADER = Pattern.compile("(?:,|$)");
	/** The fixture annotation class node */
	protected static final ClassNode FIXTURE_CLASS_NODE = ClassHelper.make(com.heliosapm.script.annotations.Fixture.class);
	/** The fixture param annotation class node */
	protected static final ClassNode FIXTURE_ARG_CLASS_NODE = ClassHelper.make(com.heliosapm.script.annotations.FixtureArg.class);
	
	/** The fixture annotation node */
	protected static final AnnotationNode FIXTURE_ANNOTATION = new AnnotationNode(FIXTURE_CLASS_NODE);
	/** The fixture param annotation node */
	protected static final AnnotationNode FIXTURE_ARG_ANNOTATION = new AnnotationNode(FIXTURE_ARG_CLASS_NODE);
	
	
	/** The platform EOL string */
	public static final String EOL = System.getProperty("line.separator", "\n");
	

	/**
	 * Creates a new GroovyCompilationCustomizer
	 */
	public GroovyCompilationCustomizer() {
		compilerContext.put(POSTINIT_CODE_BUFFER , new StringBuilder());
		compilerContext.put(POSTINIT_FIXTURES, new HashSet<AnnotationNode>());
		compilerContext.put(POSTINIT_FIXTURE_RESULTS, new HashSet<AnnotationNode>());

		this.defaultConfig = new CompilerConfiguration(CompilerConfiguration.DEFAULT);
		this.defaultConfig.setTolerance(0);				
		try {
			applyImports(imports);
			all = new CompilationCustomizer[] {importCustomizer, annotationFinder, fixtureProcessor};
			this.defaultConfig.addCompilationCustomizers(all);
		} catch (Exception ex) {
			ex.printStackTrace(System.err);
			throw new RuntimeException(ex);
		}
		defaultGroovyClassLoader = new GroovyClassLoader(getClass().getClassLoader(), defaultConfig);
	}
	
	/**
	 * Adds a post-init @InjectFixture annotation to the class level aggregator
	 * @param annotation The annotation node to reference
	 * @param node The node that was annotated
	 */
	public void addFixture(final AnnotationNode annotation, final AnnotatedNode node) {
		
	}
	
	/**
	 * Adds a post-init @InjectFixtureResult annotation to the class level aggregator
	 * @param annotation The annotation node to reference
	 * @param node The node that was annotated
	 */
	public void addFixtureResult(final AnnotationNode annotation, final AnnotatedNode node) {
		
	}
	
	/**
	 * Applies the configured imports to the compiler configuration
	 * @param imps  The imports to add
	 */
	public void applyImports(String...imps) {		
		for(String imp: imps) {
			String _imp = imp.trim().replaceAll("\\s+", " ");
			if(!_imp.startsWith("import")) {
				log.warn("Unrecognized import [" + imp + "]");
				continue;
			}
			if(_imp.startsWith("import static ")) {
				if(_imp.endsWith(".*")) {
					importCustomizer.addStaticStars(_imp.replace("import static ", "").replace(".*", ""));
				} else {
					String cleaned = _imp.replace("import static ", "").replace(".*", "");
					int index = cleaned.lastIndexOf('.');
					if(index==-1) {
						log.warn("Failed to parse non-star static import [" + imp + "]");
						continue;
					}
					importCustomizer.addStaticImport(cleaned.substring(0, index), cleaned.substring(index+1));
				}
			} else {
				if(_imp.endsWith(".*")) {
					importCustomizer.addStarImports(_imp.replace("import ", "").replace(".*", ""));
				} else {
					importCustomizer.addImports(_imp.replace("import ", ""));
				}
			}
		}
		defaultConfig.addCompilationCustomizers(importCustomizer);
	}
	
	/**
	 * Returns a customized compiler configuration for the passed source
	 * @param source the script source
	 * @return the compiler configuration
	 */
	public CompilerConfiguration getConfiguration(final File source) {
		return customizeCompiler(getHeaderLine(URLHelper.toURL(source)));
	}
	
	/**
	 * Returns a GroovyClassLoader with a customized compiler configuration 
	 * for the passed source if the source specifies recognized compiler config in a header
	 * @param source the script source
	 * @return the compiler configuration
	 */
	@SuppressWarnings("resource")
	public GroovyClassLoader getGroovyClassLoader(final File source) {
		final CompilerConfiguration cc = customizeCompiler(getHeaderLine(URLHelper.toURL(source)));
		return cc==defaultConfig ? defaultGroovyClassLoader : new GroovyClassLoader(defaultGroovyClassLoader, cc);
	}
	
	/**
	 * Returns a customized compiler configuration for the passed source
	 * @param source the script source
	 * @return the compiler configuration
	 */
	public CompilerConfiguration getConfiguration(final URL source) {
		return customizeCompiler(getHeaderLine(source));
	}
	
	/**
	 * Returns a GroovyClassLoader with a customized compiler configuration 
	 * for the passed source if the source specifies recognized compiler config in a header
	 * @param source the script source
	 * @return the compiler configuration
	 */
	@SuppressWarnings("resource")
	public GroovyClassLoader getGroovyClassLoader(final URL source) {
		final CompilerConfiguration cc = customizeCompiler(getHeaderLine(source));
		return cc==defaultConfig ? defaultGroovyClassLoader : new GroovyClassLoader(defaultGroovyClassLoader, cc);
	}
	
	
	/**
	 * Returns a customized compiler configuration for the passed source
	 * @param source the script source
	 * @return the compiler configuration
	 */
	public CompilerConfiguration getConfiguration(final Reader source) {
		return customizeCompiler(getHeaderLine(source));
	}
	
	/**
	 * Returns a GroovyClassLoader with a customized compiler configuration 
	 * for the passed source if the source specifies recognized compiler config in a header
	 * @param source the script source
	 * @return the compiler configuration
	 */
	@SuppressWarnings("resource")
	public GroovyClassLoader getGroovyClassLoader(final Reader source) {
		final CompilerConfiguration cc = customizeCompiler(getHeaderLine(source));
		return cc==defaultConfig ? defaultGroovyClassLoader : new GroovyClassLoader(defaultGroovyClassLoader, cc);
	}
	
	
	/**
	 * Returns a customized compiler configuration for the passed source
	 * @param source the script source
	 * @return the compiler configuration
	 */
	public CompilerConfiguration getConfiguration(final String source) {
		return customizeCompiler(getHeaderLine(source));
	}
	
	/**
	 * Returns a GroovyClassLoader with a customized compiler configuration 
	 * for the passed source if the source specifies recognized compiler config in a header
	 * @param source the script source
	 * @return the compiler configuration
	 */
	@SuppressWarnings("resource")
	public GroovyClassLoader getGroovyClassLoader(final String source) {
		final CompilerConfiguration cc = customizeCompiler(getHeaderLine(source));
		return cc==defaultConfig ? defaultGroovyClassLoader : new GroovyClassLoader(defaultGroovyClassLoader, cc);
	}
	
	
	/**
	 * Clones the default compiler configuration and attempts to read the customized compler options
	 * from the passed header line
	 * @param headerLine The first line of the source
	 * @return The compiler configuration
	 */
	protected CompilerConfiguration customizeCompiler(final String headerLine) {		
		if(headerLine!=null && !headerLine.trim().isEmpty() && headerLine.trim().startsWith("//")) {
			final Properties p = getHeaderProperties(headerLine.trim());
			Set<String> props = p.stringPropertyNames();
			props.retainAll(p.stringPropertyNames());
			if(!props.isEmpty()) {
				final CompilerConfiguration cc = new CompilerConfiguration(defaultConfig);
				cc.configure(p);
				return cc;
			}
		}
		return defaultConfig;		
	}
	
	/**
	 * Reads the header line from the passed reader
	 * @param reader The source reader
	 * @return The first line from the reader
	 */
	protected String getHeaderLine(final Reader reader) {
		final BufferedReader br = new BufferedReader(reader);
		try {
			return br.readLine();
		} catch (Exception ex) {
			throw new RuntimeException("Failed to get header line from reader:" + reader, ex);
		} finally {
			try { br.close(); } catch (Exception x) {/* No Op */}
			try { reader.close(); } catch (Exception x) {/* No Op */}
		}
	}
	
	/**
	 * Reads the header line from the passed string
	 * @param source The source to get the header from
	 * @return The first line from the source
	 */
	protected String getHeaderLine(final String source) {
		final int index = source.indexOf(EOL);
		if(index==-1) return "";
		return source.substring(0, index);
	}
	
	/**
	 * Reads the header line from the source accessed at the passed URL
	 * @param source The URL of the source to get the header from
	 * @return The first line from the source
	 */
	protected String getHeaderLine(final URL source) {
		return URLHelper.getLines(source, 1)[0];
	}
	
	
	
	/**
	 * Reads the header line of the source file and parses into a properties file
	 * @param headerLine The header line of the source file
	 * @return the read properties
	 */
	protected Properties getHeaderProperties(final String headerLine) {
		Properties p = new Properties();
		String line = headerLine.trim();
		if(line.startsWith("//")) {
			try {
				p.load(new StringReader(Arrays.toString(CLEAN_HEADER.split(line.substring(2).replace(" ", ""))).replace("[", "").replace("]", "").replace(" ", "").replace(",", "\n")));
			} catch (Exception ex) {
				ex.printStackTrace(System.err);
			}			
		}		
		return p;
	}
	
	/**
	 * Adds a CompilationCustomizer to the compiler config
	 * @param cc the CompilationCustomizer to add
	 */
	public void addCompilationCustomizer(final CompilationCustomizer cc) {
		defaultConfig.addCompilationCustomizers(cc);
	}
	
	
	
	private class FixtureProcessor extends CompilationCustomizer {

		/**
		 * Creates a new FixtureProcessor
		 * @param phase
		 */
		public FixtureProcessor(final CompilePhase phase) {
			super(phase);
		}

		/**
		 * {@inheritDoc}
		 * @see org.codehaus.groovy.control.CompilationUnit.PrimaryClassNodeOperation#call(org.codehaus.groovy.control.SourceUnit, org.codehaus.groovy.classgen.GeneratorContext, org.codehaus.groovy.ast.ClassNode)
		 */
		@Override
		public void call(SourceUnit source, GeneratorContext context, ClassNode classNode) throws CompilationFailedException {
			if(!compilerContext.containsKey("annotations")) {
				annotationFinder.call(source, context, classNode);
			}
			if((boolean)compilerContext.get("annotations")) {
				final List<AnnotationNode> fixAnns = classNode.getAnnotations(FIXTURE_CLASS_NODE);
				if(!fixAnns.isEmpty()) {
					final AnnotationNode fix = fixAnns.get(0);
					String name = ((ConstantExpression)fix.getMember("name")).getText();
					Class<?> clazz = ((ClassExpression)fix.getMember("type")).getType().getTypeClass();
				}
			}
		}
	}
	
	private class AnnotationFinder extends CompilationCustomizer {

		public AnnotationFinder(final CompilePhase cp) {
			super(cp);
		}

		@Override
		public void call(final SourceUnit source, final GeneratorContext context, final ClassNode classNode) throws CompilationFailedException {
			try {
				if(compilerContext.containsKey("annotations")) {
					return;
				}
				final ClassCodeVisitorSupport visitor = new ClassCodeVisitorSupport() {
					@Override
					protected SourceUnit getSourceUnit() {
						return source;
					}
					/**
					 * {@inheritDoc}
					 * @see org.codehaus.groovy.ast.ClassCodeVisitorSupport#visitAnnotations(org.codehaus.groovy.ast.AnnotatedNode)
					 */
					@Override
					public void visitAnnotations(final AnnotatedNode node) {
						final Iterator<AnnotationNode> annotationIterator = node.getAnnotations().iterator();
						while(annotationIterator.hasNext()) {
							AnnotationNode dep = annotationIterator.next();
							int bitMask = ElementTypeMapping.getMaskFor(dep);
							dep.setAllowedTargets(bitMask);
							if(dep.isTargetAllowed(AnnotationNode.TYPE_TARGET)) {
								node.getDeclaringClass().addAnnotation(dep);
								compilerContext.put("annotations", true);
								log.info("Applying Annotation [{}] to class level in [{}]", dep.getClassNode().getName(), node.getDeclaringClass().getName());								
								annotationIterator.remove();
							}							
						}
						super.visitAnnotations(node);
					}
				};
				classNode.visitContents(visitor);
			} finally {
				compilerContext.putIfAbsent("annotations", true);
			}
		}
		
	}




	/**
	 * Returns 
	 * @return the defaultConfig
	 */
	public final CompilerConfiguration getDefaultConfig() {
		return defaultConfig;
	}

	/**
	 * Returns the compiler context map
	 * @return the compilerContext
	 */
	public ConcurrentHashMap<String, Object> getCompilerContext() {
		return compilerContext;
	}
	
	/**
	 * Clears the compiler context and re-adds an empty code buffer
	 */
	public void clearCompilerContext() {
		compilerContext.clear();
		compilerContext.put(POSTINIT_CODE_BUFFER , new StringBuilder());
		compilerContext.put(POSTINIT_FIXTURES, new HashSet<AnnotationNode>());
		compilerContext.put(POSTINIT_FIXTURE_RESULTS, new HashSet<AnnotationNode>());		
	}
	
}
