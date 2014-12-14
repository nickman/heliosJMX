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

import groovy.lang.Closure;
import groovy.lang.GroovyClassLoader;

import java.io.BufferedReader;
import java.io.File;
import java.io.Reader;
import java.io.StringReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.AnnotatedNode;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassCodeVisitorSupport;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.Variable;
import org.codehaus.groovy.ast.expr.AnnotationConstantExpression;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.DeclarationExpression;
import org.codehaus.groovy.ast.expr.ListExpression;
import org.codehaus.groovy.classgen.GeneratorContext;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.control.customizers.CompilationCustomizer;
import org.codehaus.groovy.control.customizers.ImportCustomizer;
import org.codehaus.groovy.control.customizers.SourceAwareCustomizer;
import org.codehaus.groovy.transform.FieldASTTransformation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.heliosapm.jmx.util.helpers.URLHelper;
import com.heliosapm.script.annotations.Inject;



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
			"import groovy.transform.*",						// Groovy AST transforms
			"import com.heliosapm.jmx.remote.tunnel.*"			// SSH Options
	};
	
	
	/** An import customizer added to all compiler configs */
	protected final ImportCustomizer importCustomizer = new ImportCustomizer();
//	/** Annotation finder to find script level annotations and promote them to the class level */
//	protected final AnnotationFinder annotationFinder = new AnnotationFinder(CompilePhase.OUTPUT);  //CompilePhase.CANONICALIZATION);
//	/** Fixture processor to add the fixture fields */
//	protected final FixtureProcessor fixtureProcessor = new FixtureProcessor(CompilePhase.CANONICALIZATION);
	
	/** The injection processor */
	protected final InjectionProcessor injectionProcessor = new InjectionProcessor(CompilePhase.SEMANTIC_ANALYSIS); //CANONICALIZATION
	//protected final FieldTransformer fieldTransformer = new FieldTransformer(CompilePhase.SEMANTIC_ANALYSIS);
	protected final FieldASTTransformation fieldTransformer = new FieldASTTransformation();
	
	protected final SourceAwareCustomizer importAwareSourceCustomizer = new SourceAwareCustomizer(importCustomizer); 
	
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
	/** The inject annotation class node */
	protected static final ClassNode INJECT_CLASS_NODE = ClassHelper.make(com.heliosapm.script.annotations.Inject.class);
	/** The inject info annotation class node */
	protected static final ClassNode INJECT_INFO_CLASS_NODE = ClassHelper.make(com.heliosapm.script.annotations.InjectInfo.class);
	/** The groovy @Field transform class node */
	protected static final ClassNode FIELD_CLASS_NODE = ClassHelper.make(groovy.transform.Field.class);
	/** The groovy @Field transform annotation */
	protected static final AnnotationNode FIELD_CLASS_ANNOTATION = new AnnotationNode(FIELD_CLASS_NODE);
	/** The package name of our supported annotations */
	public static final String ANN_PACKAGE_NAME = Inject.class.getPackage().getName();
	
	
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
			final Closure<Boolean> allValidator = new Closure<Boolean>(this) {
				@Override
				public Boolean call() {					
					return true;
				}
				
				public Boolean doCall(SourceUnit su) {
					return true;
				}
				public Boolean doCall(ClassNode clazz) {
					return true;
				}
				public Boolean doCall(String baseName) {
					return true;
				}
				
				
			}; 
			importAwareSourceCustomizer.setSourceUnitValidator(allValidator);
			importAwareSourceCustomizer.setClassValidator(allValidator);
			importAwareSourceCustomizer.setBaseNameValidator(allValidator);
			all = new CompilationCustomizer[] {importAwareSourceCustomizer, importCustomizer, injectionProcessor};
			this.defaultConfig.addCompilationCustomizers(all);
		} catch (Exception ex) {
			ex.printStackTrace(System.err);
			throw new RuntimeException(ex);
		}
		defaultGroovyClassLoader = new GroovyClassLoader(getClass().getClassLoader(), defaultConfig);
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
	

	private class FieldTransformer extends CompilationCustomizer {
		/**
		 * Creates a new FieldTransformer
		 * @param phase The compile phase
		 */
		public FieldTransformer(final CompilePhase phase) {
			super(phase);
		}

		@Override
		public void call(final SourceUnit source, final GeneratorContext context, final ClassNode classNode) throws CompilationFailedException {
			if(compilerContext.containsKey("fields")) {
				return;
			}
			try {
				final FieldASTTransformation visitor = new FieldASTTransformation() {
					@Override
					protected SourceUnit getSourceUnit() {
						return source;
					}

				};
			} finally {
				compilerContext.putIfAbsent("fields", true);
			}
		}
	}
	
	private class InjectionProcessor extends CompilationCustomizer {
		/**
		 * Creates a new InjectionProcessor
		 * @param phase the compile phase to execute in
		 */
		public InjectionProcessor(final CompilePhase phase) {
			super(phase);
		}

		/**
		 * {@inheritDoc}
		 * @see org.codehaus.groovy.control.CompilationUnit.PrimaryClassNodeOperation#call(org.codehaus.groovy.control.SourceUnit, org.codehaus.groovy.classgen.GeneratorContext, org.codehaus.groovy.ast.ClassNode)
		 */
		@Override
		public void call(final SourceUnit source, final GeneratorContext context, final ClassNode classNode) throws CompilationFailedException {
			source.getConfiguration().addCompilationCustomizers(importCustomizer);
			if(compilerContext.containsKey("injections")) {
				return;
			}
			try {
				final AnnotationNode injectInfoNode = new AnnotationNode(INJECT_INFO_CLASS_NODE);
				final ListExpression injectMembers = new ListExpression();
				injectInfoNode.addMember("injections", injectMembers);
				final AtomicInteger addedInjects = new AtomicInteger(0);
				
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
						List<AnnotationNode> annotationsToAddToNode = new ArrayList<AnnotationNode>();
						final List<AnnotationNode> nodeAnnotations = node.getAnnotations();  //INJECT_CLASS_NODE
						final Iterator<AnnotationNode> annotationIterator = nodeAnnotations.iterator();
						if(!nodeAnnotations.isEmpty()) {
							
							while(annotationIterator.hasNext()) {
								AnnotationNode nodeAnnotation = annotationIterator.next();
								if(ANN_PACKAGE_NAME.equals(nodeAnnotation.getClassNode().getPackageName())) {
									ElementTypeMapping.setMaskFor(nodeAnnotation);
									if(nodeAnnotation.isTargetAllowed(AnnotationNode.TYPE_TARGET)) {
										if(classNode.getAnnotations(nodeAnnotation.getClassNode()).isEmpty()) {
											classNode.addAnnotation(nodeAnnotation);
											annotationIterator.remove();
											try {
												log.info("Applying Annotation [{}] to class level in [{}]", nodeAnnotation.getClassNode(), classNode.getDeclaringClass()); //nodeAnnotation.getClassNode().getName(), classNode.getDeclaringClass().getName());
											} catch (Exception ex) {
												log.info("Error Applying Annotation", ex);
											}
										}
									} else {
										if(node instanceof Variable || node instanceof DeclarationExpression) {
											final Variable var = (node instanceof Variable) ? (Variable)node : ((DeclarationExpression)node).getVariableExpression().getAccessedVariable(); 
											final String nodeName = var.getName();
											nodeAnnotation.setMember("fieldName", new ConstantExpression(nodeName));
											fieldTransformer.visit(new ASTNode[] {FIELD_CLASS_ANNOTATION, node}, source);
											injectMembers.addExpression(new AnnotationConstantExpression(nodeAnnotation));
											addedInjects.incrementAndGet();
											log.info("Tracking InjectionInfo for annotation [{}] on {}:[{}]", nodeAnnotation.getClassNode().getName(), node.getText(), node.getClass().getSimpleName());
										}
									}
								}
							}
							node.getAnnotations().addAll(annotationsToAddToNode);
//							final AnnotationNode fix = fixAnns.get(0);
//							injectMembers.addAnnotation(fix);
//							addedInjects.incrementAndGet();
//							node.addAnnotation(new AnnotationNode(FIELD_CLASS_NODE));							
						}
					}
				};			
				classNode.visitContents(visitor);
				if(addedInjects.get()>0) {
					classNode.addAnnotation(injectInfoNode);
				}
				
			} finally {
				compilerContext.putIfAbsent("injections", true);
			}
		}		
	}
	
	
//	private class AnnotationFinder extends CompilationCustomizer {
//
//		public AnnotationFinder(final CompilePhase cp) {
//			super(cp);
//		}
//
//		@Override
//		public void call(final SourceUnit source, final GeneratorContext context, final ClassNode classNode) throws CompilationFailedException {
//			try {
//				if(compilerContext.containsKey("annotations")) {
//					return;
//				}
//				final ClassCodeVisitorSupport visitor = new ClassCodeVisitorSupport() {
//					@Override
//					protected SourceUnit getSourceUnit() {
//						return source;
//					}
//					/**
//					 * {@inheritDoc}
//					 * @see org.codehaus.groovy.ast.ClassCodeVisitorSupport#visitAnnotations(org.codehaus.groovy.ast.AnnotatedNode)
//					 */
//					@Override
//					public void visitAnnotations(final AnnotatedNode node) {
//						final Iterator<AnnotationNode> annotationIterator = node.getAnnotations().iterator();
//						while(annotationIterator.hasNext()) {
//							AnnotationNode dep = annotationIterator.next();
//							int bitMask = ElementTypeMapping.getMaskFor(dep);
//							dep.setAllowedTargets(bitMask);
//							if(dep.isTargetAllowed(AnnotationNode.TYPE_TARGET)) {
//								node.getDeclaringClass().addAnnotation(dep);
//								compilerContext.put("annotations", true);
//								log.info("Applying Annotation [{}] to class level in [{}]", dep.getClassNode().getName(), node.getDeclaringClass().getName());								
//								annotationIterator.remove();
//							}							
//						}
//						super.visitAnnotations(node);
//					}
//				};
//				classNode.visitContents(visitor);
//			} finally {
//				compilerContext.putIfAbsent("annotations", true);
//			}
//		}
//		
//	}




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
	}
	
}
