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
package com.heliosapm.jmx.expr;



/**
 * <p>Title: DirectiveCodeProvider</p>
 * <p>Description: Defines a provider that generates the code to resolve a jmx expression</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.jmx.expr.DirectiveCodeProvider</code></p>
 */

public interface DirectiveCodeProvider {
	
	/**
	 * Appends the applicable expression reolution code to the passed buffer for the specified directive.
	 * The following variables will be available in the context of the generated code:<ul>
	 * 	<li><b>nBuff</b> : (StringBuilder) the current name builder for appending  </li>
	 *  <li><b>sourceId ($1)</b> : (String) the unique identifier of the source of the attribute values  </li>
	 *  <li><b>attrValues ($2)</b> : (Map<String, Object>) The MBean attribute values keyed by the attribute name </li>
	 *  <li><b>objectName ($3)</b> : (ObjectName) the ObjectName being sampled from</li>
	 *  <li><b>result ($4)</b> : (ExpressionResult) the expression result to load</li>
	 * </ul>
	 * @param directive The directive to generate the code for
	 * @param code The code buffer to append to
	 * @param phase The load phase
	 */
	public void generate(final String directive, final StringBuilder code, final LoadPhase phase);
	
	/**
	 * Indicates if the passed directive should be processed by this provider
	 * @param directive The directive to test
	 * @return true if the passed directive should be processed by this provider, false otherwise
	 */
	public boolean match(final String directive);
		
}
