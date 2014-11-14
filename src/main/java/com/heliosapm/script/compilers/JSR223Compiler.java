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

import java.io.File;
import java.net.URL;

import javax.script.CompiledScript;
import javax.script.ScriptException;

import com.heliosapm.jmx.util.helpers.URLHelper;
import com.heliosapm.script.DeployedScript;
import com.heliosapm.script.JavaxScriptDeployedScript;
import com.heliosapm.script.StateService;

/**
 * <p>Title: JSR223Compiler</p>
 * <p>Description: </p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.script.compilers.JSR223Compiler</code></p>
 */

public class JSR223Compiler implements DeploymentCompiler<CompiledScript> {
	/** The extensions */
	private static final String[] extensions = new String[]{"*"};
	
	/** The state service providing the managed script engines */
	protected final StateService stateService;
	
	/**
	 * Creates a new JSR223Compiler
	 * @param stateService The state service supporting this compiler
	 */
	public JSR223Compiler(final StateService stateService) {
		this.stateService = stateService;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.compilers.DeploymentCompiler#compile(java.net.URL)
	 */
	@Override
	public CompiledScript compile(final URL source) throws CompilerException {
		if(source==null) throw new IllegalArgumentException("The passed source URL was null");
		final String extension = URLHelper.getExtension(source, "").trim().toLowerCase();
		if(extension.isEmpty()) throw new RuntimeException("The source URL [" + source + "] has no extension");
		if(!stateService.isExtensionSupported(extension)) throw new RuntimeException("Source type [" + extension + "] in source URL [" + source + "] is not supported");
		try {
			return stateService.getCompilerForExtension(extension).compile(URLHelper.getTextFromURL(source, 1000, 1000));
		} catch (Exception ex) {
			throw new CompilerException("Failed to compile source [" + source + "]", getDiagnostic(ex),  ex);
		}
	}
	

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.compilers.DeploymentCompiler#deploy(java.lang.String)
	 */
	@Override
	public DeployedScript<CompiledScript> deploy(final String sourceFile) throws CompilerException {
		final CompiledScript executable = compile(URLHelper.toURL(new File(sourceFile)));
		return new JavaxScriptDeployedScript(new File(sourceFile), executable);
	}
	
	
	/**
	 * Renders the exception into a compiler diagnostic
	 * @param t The compiler thrown exception
	 * @return the compiler diagnostic
	 */
	protected String getDiagnostic(final Throwable t) {
		if(t==null) return "Null underlying exception";
		if(t instanceof ScriptException) {
			final ScriptException se = (ScriptException)t;
			return new StringBuilder()
				.append("Message: ").append(se.getMessage())
				.append(", File Name: ").append(se.getFileName())
				.append(", Line: ").append(se.getLineNumber())
				.append(", Column: ").append(se.getColumnNumber())
				.toString();
		}
		return "NonCompiler Diagnostic:" + t.toString();
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.compilers.DeploymentCompiler#getSupportedExtensions()
	 */
	@Override
	public String[] getSupportedExtensions() {
		return extensions;
	}

}
