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

/**
 * <p>Title: CompilerException</p>
 * <p>Description: Common compiler exception class, thrown when a compiler fails to compile a source file</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.script.compilers.CompilerException</code></p>
 */

public class CompilerException extends Exception {
	
	/**  */
	private static final long serialVersionUID = -5918091984426117564L;
	
	/** The compilation failure diagnostic message */
	protected final String diagnostic;


	/**
	 * Creates a new CompilerException
	 * @param message The exception message. Should contain the name of the file that failed compilation.
	 * @param diagnostic The compilation failure diagnostic message
	 * @param cause The underlying compiler specific exception
	 */
	public CompilerException(final String message, final String diagnostic, final Throwable cause) {
		super(message, cause);
		this.diagnostic = diagnostic;
	}


	/**
	 * Returns the compilation failure diagnostic message
	 * @return the failed compilation diagnostic
	 */
	public String getDiagnostic() {
		return diagnostic;
	}
	
	/**
	 * {@inheritDoc}
	 * @see java.lang.Throwable#toString()
	 */
	@Override
	public String toString() {
		return new StringBuilder(this.getMessage())
			.append("\nDiagnostic:").append(diagnostic)
			.append("\nUnderlying:").append(getCause().toString())		
			.toString();
	}


}
