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

import java.util.Map;

import javax.management.ObjectName;

/**
 * <p>Title: LoopingExpressionProcessor</p>
 * <p>Description: An expression processor that supports repeated executions in a loop over one or more {@link Iterable}s</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.jmx.expr.LoopingExpressionProcessor</code></p>
 */

public interface LoopingExpressionProcessor extends ExpressionProcessor {

	//public ExpressionResult process(
		//final String sourceId, 
		// Map<String, Object> attrValues, 
		//ObjectName objectName, 
		//ExpressionResult result);
	
	/**
	 * Executes the passed processor for each iterable
	 * @param processor The processor to execute
	 * @param loopers The iterables to nest the execution with
	 */
	public void process(ExpressionProcessor processor, Iterable<?>...loopers);
	

	/**
	 * Executes this processor for each iterable
	 * @param loopers The iterables to nest the execution with
	 */
	public void process(Iterable<?>...loopers);
	
}
