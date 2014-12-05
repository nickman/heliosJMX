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

import java.io.BufferedReader;
import java.io.Reader;
import java.io.StringReader;
import java.net.URL;
import java.util.Arrays;
import java.util.Properties;
import java.util.regex.Pattern;

import org.codehaus.groovy.ast.AnnotatedNode;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassCodeVisitorSupport;
import org.codehaus.groovy.ast.ClassNode;
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
	/** The default compiler configuration */
	protected final CompilerConfiguration defaultConfig;
	/** Instance logger */
	private final Logger log = LoggerFactory.getLogger(getClass());
	
	/** A set of default imports added to all compiler configurations */
	private final String[] imports = new String[]{
			"import com.heliosapm.script.annotations.*",		// configuration annotations
			"import javax.management.*", 						// JMX Core
			"import javax.management.remote.*", 				// JMX Remoting
	};
	
	/** An import customizer added to all compiler configs */
	protected final ImportCustomizer importCustomizer = new ImportCustomizer();
	/** Annotation finder to find script level annotations and promote them to the class level */
	protected final AnnotationFinder annotationFinder = new AnnotationFinder(CompilePhase.CANONICALIZATION);
	/** The compilation customizers to apply to the groovy compiler */
	protected final CompilationCustomizer[] all;
	
	/** End of line splitter */
	protected static final Pattern EOL_SPLITTER = Pattern.compile("\n");
	/** Pattern to clean up the header line to convert into properties */
	protected static final Pattern CLEAN_HEADER = Pattern.compile("(?:,|$)");
	
	/** The platform EOL string */
	public static final String EOL = System.getProperty("line.separator", "\n");
	

	/**
	 * Creates a new GroovyCompilationCustomizer
	 */
	public GroovyCompilationCustomizer() {
		this.defaultConfig = new CompilerConfiguration(CompilerConfiguration.DEFAULT);
		this.defaultConfig.setTolerance(0);		
		try {
			applyImports(imports);
			all = new CompilationCustomizer[] {importCustomizer, annotationFinder};
			this.defaultConfig.addCompilationCustomizers(all);
		} catch (Exception ex) {
			ex.printStackTrace(System.err);
			throw new RuntimeException(ex);
		}
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
	public CompilerConfiguration getConfiguration(final URL source) {
		return customizeCompiler(getHeaderLine(source));
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
	 * Returns a customized compiler configuration for the passed source
	 * @param source the script source
	 * @return the compiler configuration
	 */
	public CompilerConfiguration getConfiguration(final String source) {
		return customizeCompiler(getHeaderLine(source));
	}
	
	/**
	 * Clones the default compiler configuration and attempts to read the customized compler options
	 * from the passed header line
	 * @param headerLine The first line of the source
	 * @return The compiler configuration
	 */
	protected CompilerConfiguration customizeCompiler(final String headerLine) {
		final CompilerConfiguration cc = new CompilerConfiguration(defaultConfig);
		if(headerLine!=null && !headerLine.trim().isEmpty() && headerLine.trim().startsWith("//")) {
			final Properties p = getHeaderProperties(headerLine.trim());
			cc.configure(p);
		}
		return cc;		
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
	 * <p>Title: AnnotationFinder</p>
	 * <p>Description: CompilationCustomizer to find local field annotations defined in a script and promote them to the class</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.script.compilers.groovy.GroovyCompilationCustomizer.AnnotationFinder</code></p>
	 */
	private class AnnotationFinder extends CompilationCustomizer {

		public AnnotationFinder(final CompilePhase cp) {
			super(cp);
		}

		@Override
		public void call(final SourceUnit source, final GeneratorContext context, final ClassNode classNode) throws CompilationFailedException {
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
					for(AnnotationNode dep: node.getAnnotations()) {
						if(dep.isTargetAllowed(AnnotationNode.TYPE_TARGET)) {
							node.getDeclaringClass().addAnnotation(dep);
							log.debug("Applying Annotation [{}] to class level in [{}]", dep.getClassNode().getName(), node.getDeclaringClass().getName());
						}
					}
					super.visitAnnotations(node);
				}
			};
			classNode.visitContents(visitor);
		}
		
	}


	/**
	 * Returns 
	 * @return the defaultConfig
	 */
	public final CompilerConfiguration getDefaultConfig() {
		return defaultConfig;
	}
}
