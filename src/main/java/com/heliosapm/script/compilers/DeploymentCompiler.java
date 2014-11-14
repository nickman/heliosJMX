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

import java.net.URL;

import com.heliosapm.script.DeployedScript;

/**
 * <p>Title: DeploymentCompiler</p>
 * <p>Description: Defines a deployment compiler</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.script.compilers.DeploymentCompiler</code></p>
 * @param <T> The executable type created by this compiler
 */

public interface DeploymentCompiler<T> {
	
	/**
	 * Compiles the passed source
	 * @param source The source to compile
	 * @return the executable deployment
	 * @throws CompilerException thrown if the compilation fails
	 */
	public T compile(final URL source) throws CompilerException;
	
	/**
	 * Prepares a DeployedScript from the passed source file and executable instance
	 * @param sourceFile The source file 
	 * @return the deployed script
	 * @throws CompilerException thrown if the compilation fails
	 */
	public DeployedScript<T> deploy(final String sourceFile) throws CompilerException;
	
	/**
	 * Returns an array of the source file extensions supported by this compiler.
	 * If the array contains a single entry of <b><code>*</code></b>, it will be considered a catch-all. 
	 * @return an array of source file extensions 
	 */
	public String[] getSupportedExtensions();
	
}
