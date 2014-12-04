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
package com.heliosapm.script.compilers;

import java.util.Map;

/**
 * <p>Title: CompiledExecutable</p>
 * <p>Description: A generic wrapper for different types of underlying compiled scripts</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.script.compilers.CompiledExecutable</code></p>
 * @param <T> The type of the underlying executable script
 */

public interface CompiledExecutable<T> {

	/**
	 * Invokes this executable
	 * @param args The optional argument map specifiying the parameters to the invocation
	 * @return the return value of the invocation
	 */
	public Object execute(Map<String, Object> args);
	
	/**
	 * Invokes this executable
	 * @return the return value of the invocation
	 */
	public Object execute();
	
	/**
	 * Returns the underlying executable script
	 * @return the underlying executable script
	 */
	public T getUnderlyingScript();
	

}
