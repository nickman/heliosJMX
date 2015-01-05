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
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.management.ObjectName;
import javax.script.Bindings;
import javax.script.CompiledScript;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.heliosapm.jmx.util.helpers.ArrayUtils;
import com.heliosapm.jmx.util.helpers.CacheService;
import com.heliosapm.opentsdb.TSDBSubmitterImpl.ExpressionResult;
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
	protected static final StateService state = StateService.getInstance();
	/** A reference to the cache service */
	protected static final CacheService cache = CacheService.getInstance();
	
	/** End of line separator */
	public static final String EOL = System.getProperty("line.separator", "\n");
	/** Replacer for args replacements */
	public static final Pattern FREPL = Pattern.compile("(\\$4\\[(\\d+)\\])");
	
	/** The expression result that handles the expression processing and result buffering */
	protected final ExpressionResult er;
	/** Indicates if this a looping processor */
	protected final boolean looper;
	
	/** Instance logger */
	protected final Logger log = LoggerFactory.getLogger(getClass());
	
	/**
	 * Creates a new AbstractExpressionProcessor
	 * @param er The expression result that handles the expression processing and result buffering
	 * @param looper true if this a looping processor, false otherwise
	 */
	public AbstractExpressionProcessor(final ExpressionResult er, final boolean looper) {
		this.er = er;
		this.looper = looper;
	}
	
	/**
	 * Creates a new non-looping AbstractExpressionProcessor
	 * @param er The expression result that handles the expression processing and result buffering
	 */
	public AbstractExpressionProcessor(final ExpressionResult er) {
		this(er, false);
	}
	
	/**
	 * The default looper impl call.
	 * @param sourceId A unique id identifying where the values and object name were collected from
	 * @param attrValues A map of attribute values keyed by the attribute name
	 * @param objectName The JMX ObjectName of the MBean the attribute values were sampled from
	 * @param loopers The loopers iterables to nest the execution with
	 * @return blank string by default. Overrides should return a string of EOL separated metric puts
	 */
	public CharSequence processLoop(final String sourceId, final Map<String, Object> attrValues, final ObjectName objectName, final Object...loopers) {
		return "";
	}
	
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.jmx.expr.ExpressionProcessor#process(java.lang.String, java.util.Map, javax.management.ObjectName, java.lang.Object[])
	 */
	public CharSequence process(final String sourceId, final Map<String, Object> attrValues, final ObjectName objectName, final Object...loopers) {
		try {
			if(looper) {
				return processLoop(sourceId, attrValues, objectName, loopers);
			} else {
				if(doName(sourceId, attrValues, objectName)) {
					if(doValue(sourceId, attrValues, objectName)) {
						final StringBuilder b = new StringBuilder();
						er.flush(b);
						return b;
					}
				}
			}
		} catch (Exception ex) {
			log.error("[{}] Expression Execution Failed", toString(), ex);
		}
		return "";				
	}
	
	public void flush() {
		er.flush();
	}
	
	public void deepFlush() {
		er.deepFlush();
	}
	
	public static void argsRepl(final StringBuilder buff, final String...args) {
		if(args.length==0 || buff.length()<1) return;
		final Matcher m = FREPL.matcher(buff);
		int maxIndex = args.length-1;
		while(m.find()) {
			String tok = m.group(1);
			int index = Integer.parseInt(m.group(2));
			if(index < 0 || index > maxIndex) throw new RuntimeException("Failed on code segment [" + buff + "] caused by args index [" + index + "] which was greater than the max [" + maxIndex + "]");
			int pos = buff.indexOf(tok);
			buff.replace(pos, pos + tok.length(), args[index]);
		}
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
	 * Computes a key that uniquely identifies the combination of the passed values
	 * @param sourceId The source ID of the values
	 * @param attrValues The metric tags
	 * @param objectName The JMX ObjectName
	 * @return the unique key
	 */
	public static String key(final String sourceId, final Map<String, Object> attrValues, final ObjectName objectName) {
		final StringBuilder b = new StringBuilder(sourceId).append("/");
		if(attrValues!=null & !attrValues.isEmpty()) {
			b.append("{");
			for(Map.Entry<String, Object> entry: attrValues.entrySet()) {
				b.append(entry.getKey()).append(",");
			}
			b.deleteCharAt(b.length()-1).append("}/");
		}
		if(objectName!=null) {
			b.append(objectName.toString());
		}
		return b.toString();
	}
	
	/**
	 * Executes the name part of the expression
	 * @param sourceId A unique id identifying where the values and object name were collected from
	 * @param attrValues A map of attribute values keyed by the attribute name
	 * @param objectName The JMX ObjectName of the MBean the attribute values were sampled from
	 * @param looperValues The resolved looper values
	 * @return true to continue, false to abort tracing
	 */
	protected abstract boolean doName(final String sourceId, final Map<String, Object> attrValues, final ObjectName objectName, String...looperValues);
	
	/**
	 * Executes the value part of the expression
	 * @param sourceId A unique id identifying where the values and object name were collected from
	 * @param attrValues A map of attribute values keyed by the attribute name
	 * @param objectName The JMX ObjectName of the MBean the attribute values were sampled from
	 * @param looperValues The resolved looper values
	 * @return true to continue, false to abort tracing
	 */
	protected abstract boolean doValue(final String sourceId, final Map<String, Object> attrValues, final ObjectName objectName, String...looperValues);
	
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
	
	public static String iterStr(final Iterator iter) {		
		try {
			Object obj = iter.next();
			return obj==null ? "" : obj.toString();
		} catch (Exception ex) {
			return "1";
		}
	}
	
	public static String mapEntryKeyStr(final Map.Entry entry) {
		try {
			Object obj = entry.getKey();
			return obj==null ? "" : obj.toString();
		} catch (Exception ex) {
			return "1";
		}		
	}
	
	public static String mapEntryValStr(final Map.Entry entry) {
		try {
			Object obj = entry.getValue();
			return obj==null ? "" : obj.toString();
		} catch (Exception ex) {
			return "1";
		}		
	}
	

}
