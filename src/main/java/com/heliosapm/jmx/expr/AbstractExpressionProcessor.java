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
package com.heliosapm.jmx.expr;

import java.util.Map;

import javax.management.ObjectName;
import javax.script.Bindings;
import javax.script.CompiledScript;

/**
 * <p>Title: AbstractExpressionProcessor</p>
 * <p>Description: </p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.jmx.expr.AbstractExpressionProcessor</code></p>
 */

public abstract class AbstractExpressionProcessor implements ExpressionProcessor {

	/**
	 * Creates a new AbstractExpressionProcessor
	 */
	public AbstractExpressionProcessor() {

	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.jmx.expr.ExpressionProcessor#process(java.lang.String, java.util.Map, javax.management.ObjectName, com.heliosapm.jmx.expr.ExpressionResult)
	 */
	public ExpressionResult process(final String sourceId, final Map<String, Object> attrValues, final ObjectName objectName, final ExpressionResult result) {
		ExpressionResult er = result != null ? result : ExpressionResult.newInstance();
		doName(sourceId, attrValues, objectName, er);
		doValue(sourceId, attrValues, objectName, er);
		return er;
	}
	
	protected Object invokeEval(final Object cs, final Bindings bindings) {
		return invokeEval(cs, bindings);
	}
	
	protected Object invokeEval(final Object cs, final Bindings bindings, final Object defaultValue) {
		try {
			return ((CompiledScript)cs).eval(bindings);
		} catch (Exception x) {
			if(defaultValue!=null) return defaultValue;
			throw new RuntimeException(x);
		}
	}
	
	protected abstract void doName(final String sourceId, final Map<String, Object> attrValues, final ObjectName objectName, final ExpressionResult result);
	
	protected abstract void doValue(final String sourceId, final Map<String, Object> attrValues, final ObjectName objectName, final ExpressionResult result);

}
