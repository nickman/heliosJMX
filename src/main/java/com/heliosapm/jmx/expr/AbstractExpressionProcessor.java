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

import java.util.Arrays;
import java.util.Map;

import javax.management.ObjectName;
import javax.script.Bindings;
import javax.script.CompiledScript;

import com.heliosapm.jmx.util.helpers.ArrayUtils;
import com.heliosapm.jmx.util.helpers.CacheService;
import com.heliosapm.opentsdb.ExpressionResult;
import com.heliosapm.script.StateService;

/**
 * <p>Title: AbstractExpressionProcessor</p>
 * <p>Description: </p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.jmx.expr.AbstractExpressionProcessor</code></p>
 */

public abstract class AbstractExpressionProcessor implements ExpressionProcessor {
	/** A reference to the script compiler service */
	protected final StateService state = StateService.getInstance();
	/** A reference to the cache service */
	protected final CacheService cache = CacheService.getInstance();
	
	/** The expression result that handles the expression processing and result buffering */
	protected final ExpressionResult er;
	/**
	 * Creates a new AbstractExpressionProcessor
	 * @param er The expression result that handles the expression processing and result buffering
	 */
	public AbstractExpressionProcessor(ExpressionResult er) {
		this.er = er;
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.jmx.expr.ExpressionProcessor#process(java.lang.String, java.util.Map, javax.management.ObjectName, java.lang.Object[])
	 */
	public CharSequence process(final String sourceId, final Map<String, Object> attrValues, final ObjectName objectName, final Object...loopers) {
		doName(sourceId, attrValues, objectName);
		doValue(sourceId, attrValues, objectName);
		return er.renderPut();		
	}
	
	
	/**
	 * Invokes a script evaluation
	 * @param cs The compiled script to invoke
	 * @param bindings The script bindings
	 * @return the result of the script invocation
	 */
	protected static Object invokeEval(final Object cs, final Bindings bindings) {
		return invokeEval(cs, bindings, null);
	}
	
	/**
	 * Invokes a script evaluation
	 * @param cs The compiled script to invoke
	 * @param bindings The script bindings
	 * @param defaultValue The default value returned if the script invocation throws an exception
	 * @return the result of the script invocation
	 */
	protected static Object invokeEval(final Object cs, final Bindings bindings, final Object defaultValue) {
		try {
			return ((CompiledScript)cs).eval(bindings);
		} catch (Exception x) {
			if(defaultValue!=null) return defaultValue;
			throw new RuntimeException(x);
		}
	}
	
	/**
	 * Executes the name part of the expression
	 * @param sourceId A unique id identifying where the values and object name were collected from
	 * @param attrValues A map of attribute values keyed by the attribute name
	 * @param objectName The JMX ObjectName of the MBean the attribute values were sampled from
	 */
	protected abstract void doName(final String sourceId, final Map<String, Object> attrValues, final ObjectName objectName);
	
	/**
	 * Executes the value part of the expression
	 * @param sourceId A unique id identifying where the values and object name were collected from
	 * @param attrValues A map of attribute values keyed by the attribute name
	 * @param objectName The JMX ObjectName of the MBean the attribute values were sampled from
	 */
	protected abstract void doValue(final String sourceId, final Map<String, Object> attrValues, final ObjectName objectName);
	
	/**
	 * Returns the passed object cast or wrapped as an {@link Iterable}
	 * @param iterable The object to cast or wrap to an Iterable
	 * @return the  {@link Iterable}
	 */
	@SuppressWarnings("unchecked")
	public static <T> Iterable<T> iter(final Object iterable) {
		if(iterable==null) throw new IllegalArgumentException("The passed iterable was null");
		if(iterable instanceof Iterable) {
			return (Iterable<T>)iterable;
		}
		if(iterable.getClass().isArray()) {
			return (Iterable<T>) Arrays.asList(ArrayUtils.flatten(iterable));			
		}
		throw new IllegalArgumentException("The passed object could not be itered [" + iterable.getClass().getName() + "]");
	}
	

}
