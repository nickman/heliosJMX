/**
 * Helios, OpenSource Monitoring
 * Brought to you by the Helios Development Group
 *
 * Copyright 2014, Helios Development Group and individual contributors
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
package com.heliosapm.script.compilers;

import groovy.lang.GroovyShell;
import groovy.lang.Script;

import java.io.File;
import java.io.StringReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;

import org.codehaus.groovy.GroovyBugError;
import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.AnnotatedNode;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.PropertyNode;
import org.codehaus.groovy.classgen.GeneratorContext;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.control.customizers.CompilationCustomizer;
import org.codehaus.groovy.control.customizers.ImportCustomizer;
import org.codehaus.groovy.transform.ASTTransformation;
import org.codehaus.groovy.transform.ASTTransformationCollectorCodeVisitor;
import org.codehaus.groovy.transform.GroovyASTTransformation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.heliosapm.jmx.config.Dependency;
import com.heliosapm.jmx.util.helpers.URLHelper;
import com.heliosapm.script.DeployedScript;
import com.heliosapm.script.GroovyDeployedScript;

/**
 * <p>Title: GroovyCompiler</p>
 * <p>Description: Native groovy script compiler.</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.script.compilers.GroovyCompiler</code></p>
 */

public class GroovyCompiler implements DeploymentCompiler<Script> {
	/** The extensions */
	private static final String[] extensions = new String[]{"groovy"};
	/** The default compiler configuration */
	protected final CompilerConfiguration defaultConfig;
	/** The default groovy shell */
	protected final GroovyShell groovyShell;
	/** Instance logger */
	private final Logger log = LoggerFactory.getLogger(getClass());
	
	/** A set of default imports added to all compiler configurations */
	private final String[] imports = new String[]{
			"import com.heliosapm.jmx.config.*"		// configuration annotations
	};
	
	/** An import customizer added to all compiler configs */
	protected final ImportCustomizer importCustomizer = new ImportCustomizer();
	
	protected final CompilationCustomizer[] all;
	
	/** End of line splitter */
	protected static final Pattern EOL_SPLITTER = Pattern.compile("\n");
	/** Pattern to clean up the header line to convert into properties */
	protected static final Pattern CLEAN_HEADER = Pattern.compile("(?:,|$)");
	
	
	/**
	 * Creates a new GroovyCompiler
	 * @param defaultConfig The default configuration
	 */
	public GroovyCompiler(final CompilerConfiguration defaultConfig) {		
		this.defaultConfig = defaultConfig==null ? new CompilerConfiguration(CompilerConfiguration.DEFAULT) : defaultConfig;
		this.defaultConfig.setTolerance(0);		
		try {
			applyImports(imports);
			all = new CompilationCustomizer[] {importCustomizer, new AnnotationFinder(CompilePhase.SEMANTIC_ANALYSIS)};
			this.defaultConfig.addCompilationCustomizers(all);
			groovyShell = new GroovyShell(this.defaultConfig);
		} catch (Exception ex) {
			ex.printStackTrace(System.err);
			throw new RuntimeException(ex);
		}
	}
	
	/**
	 * Creates a new GroovyCompiler
	 */
	public GroovyCompiler() {
		this((CompilerConfiguration)null);
	}

	/**
	 * Creates a new GroovyCompiler
	 * @param defaultConfig The default configuration properties
	 */
	public GroovyCompiler(final Properties defaultConfig) {
		this(defaultConfig==null ? new CompilerConfiguration(CompilerConfiguration.DEFAULT) : new CompilerConfiguration(defaultConfig));
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
	 * {@inheritDoc}
	 * @see com.heliosapm.script.compilers.DeploymentCompiler#compile(java.net.URL)
	 */
	@Override
	public Script compile(final URL source) throws CompilerException {
		if(source==null) throw new IllegalArgumentException("The passed source URL was null");
		final String extension = URLHelper.getExtension(source, "").trim().toLowerCase();
		if(!"groovy".equals(extension)) throw new RuntimeException("Source type [" + extension + "] in source URL [" + source + "] is not supported by this compiler");
		String sourceCode = URLHelper.getTextFromURL(source, 1000, 1000);
		String headerLine = EOL_SPLITTER.split(sourceCode, 2)[0].trim();
		GroovyShell shellToUse = groovyShell;
		if(headerLine.startsWith("//")) {
			Properties p = getHeaderProperties(headerLine);
			if(!p.isEmpty()) {
				CompilerConfiguration cc = new CompilerConfiguration(defaultConfig);
				cc.configure(p);
				cc.addCompilationCustomizers(all);
				shellToUse = new GroovyShell(cc);
			}
		}
		try {
			final Script script = shellToUse.parse(sourceCode, source.getFile());
			return script;
		} catch (CompilationFailedException cfe) {
			log.error("Failed to compile source [" + source + "]", getDiagnostic(cfe),  cfe);
			throw new CompilerException("Failed to compile source [" + source + "]", getDiagnostic(cfe),  cfe);
		}
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.compilers.DeploymentCompiler#deploy(java.lang.String)
	 */
	@Override
	public DeployedScript<Script> deploy(final String sourceFile) throws CompilerException {
		final Script executable;
		try {
			executable = compile(URLHelper.toURL(new File(sourceFile)));
			return new GroovyDeployedScript(new File(sourceFile), executable);
		} catch (CompilerException er) {
			final GroovyDeployedScript gds = new GroovyDeployedScript(new File(sourceFile), null);
			final URL sourceURL = URLHelper.toURL(sourceFile);
			final long ad32 = URLHelper.adler32(sourceURL);
			final long ts = URLHelper.getLastModified(sourceURL);
			gds.setFailedExecutable(er.getDiagnostic(), ad32, ts);
			return gds;
		}				
	}
	
 
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.compilers.DeploymentCompiler#getSupportedExtensions()
	 */
	@Override
	public String[] getSupportedExtensions() {
		return extensions;
	}
	
	/**
	 * Renders the exception into a compiler diagnostic
	 * @param t The compiler thrown exception
	 * @return the compiler diagnostic
	 */
	protected String getDiagnostic(final Throwable t) {
		if(t==null) return "Null underlying exception";
		if(t instanceof CompilationFailedException) {
			final CompilationFailedException se = (CompilationFailedException)t;
			return new StringBuilder()
				.append("Message: ").append(se.getMessage())
				.append(", ProcessingUnit: ").append(se.getUnit()==null ? "none" : se.getUnit().getPhaseDescription())
				.append(", Module: ").append(se.getModule()==null ? "none" : se.getModule().getDescription())
				.toString();
		}
		return "NonCompiler Diagnostic:" + t.toString();
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
	
	
	private static class AnnotationFinder extends CompilationCustomizer implements ASTTransformation {
		protected final AnnotatedNode inherritCtorsNode = ClassHelper.make(Dependency.class);
		
		public static CompilationCustomizer[] get() {
			List<CompilationCustomizer> finders = new ArrayList<CompilationCustomizer>();
			for(CompilePhase cp: CompilePhase.values()) {
				finders.add(new AnnotationFinder(cp));
			}
			return finders.toArray(new CompilationCustomizer[finders.size()]);
		}

		public AnnotationFinder(CompilePhase cp) {
			super(cp);
		}

		@Override
		public void call(SourceUnit source, GeneratorContext context, ClassNode classNode) throws CompilationFailedException {
			ASTTransformationCollectorCodeVisitor ast = new ASTTransformationCollectorCodeVisitor(source, source.getClassLoader());
			ast.visitClass(classNode);
			ast.visitAnnotations(inherritCtorsNode);
			for(PropertyNode fn : classNode.getProperties()) {				
				List<AnnotationNode> anns = fn.getAnnotations();
				if(!anns.isEmpty()) {
					System.out.println(anns);
				}
			}
			for(FieldNode fn : classNode.getFields()) {				
				List<AnnotationNode> anns = fn.getAnnotations();
				if(!anns.isEmpty()) {
					System.out.println(anns);
				}
			}
			
			List<AnnotationNode> anns = classNode.getAnnotations();
			if(!anns.isEmpty()) {
				System.out.println(anns);
			}
			
		}
		
		protected SourceUnit sourceUnit;

		@Override
		public void visit(ASTNode[] nodes, SourceUnit source) {
	        if (nodes == null || nodes.length != 2 || !(nodes[0] instanceof AnnotationNode) || !(nodes[1] instanceof AnnotatedNode)) {
	            throw new GroovyBugError("Internal error: expecting [AnnotationNode, AnnotatedNode] but got: " + (nodes == null ? null : Arrays.asList(nodes)));
	        }
	        this.sourceUnit = sourceUnit;
	        
		}
		
	}

}


/*
		DeploymentCompiler Options
		================
		
"groovy.warnings"	getWarningLevel()
"groovy.source.encoding"	getSourceEncoding()
"groovy.target.directory"	getTargetDirectory()
"groovy.target.bytecode"	getTargetBytecode()
"groovy.classpath"	getClasspath()
"groovy.output.verbose"	getVerbose()
"groovy.output.debug"	getDebug()
"groovy.errors.tolerance"	getTolerance()
"groovy.script.extension"	getDefaultScriptExtension()
"groovy.script.base"	getScriptBaseClass()
"groovy.recompile"	getRecompileGroovySource()
"groovy.recompile.minimumInterval"	getMinimumRecompilationInterval()		
*/