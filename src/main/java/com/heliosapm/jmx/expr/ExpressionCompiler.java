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

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javassist.ClassPool;

import org.cliffc.high_scale_lib.NonBlockingHashMap;

/**
 * <p>Title: ExpressionCompiler</p>
 * <p>Description: Compiles an expression into a java class</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.jmx.expr.ExpressionCompiler</code></p>
 */

public class ExpressionCompiler {
	/** The singleton instance */
	private static volatile ExpressionCompiler instance = null;
	/** The singleton instance ctor lock */
	private static final Object lock = new Object();
	
	/** A cache of compiled claass constructors */
	private final NonBlockingHashMap<String, Constructor<ExpressionProcessor>> classes = new NonBlockingHashMap<String, Constructor<ExpressionProcessor>>();
	/** Serial number to name generated classes */
	private final AtomicLong classSerial = new AtomicLong();
	
	public static final Pattern VALUE_LOOKUP = Pattern.compile("\\{(.*?):(.*?)\\}");
	public static final Pattern DOMAIN_VALUE = Pattern.compile("\\{domain\\}");
	public static final Pattern FULL_EXPR = Pattern.compile("(.*?)\\s+*?\\->(.*?)");
	public static final Pattern NAME_EXPR = Pattern.compile("(.*?)\\s+*?\\->(.*?)");
	
	
	public static final Set<String> NAME_KEYS = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
			"key" // resolves to the value of the key name pairs in the object name for the specified value, objectName.getKeyProperty(key)
	))); 
	
	/**
	 * Acquires and returns the ExpressionCompiler singleton instance
	 * @return the ExpressionCompiler singleton instance
	 */
	public static ExpressionCompiler getInstance() {
		if(instance==null) {
			synchronized(lock) {
				if(instance==null) {
					instance = new ExpressionCompiler();
				}
			}
		}
		return instance;
	}
	
	public ExpressionProcessor get(final String fullExpression) {
		if(fullExpression==null || fullExpression.trim().isEmpty()) throw new IllegalArgumentException("The passed full expression was null or empty");		
		final String key = fullExpression.trim();
		Constructor<ExpressionProcessor> ctor = classes.get(key);
		if(ctor==null) {
			synchronized(classes) {
				ctor = classes.get(key);
				if(ctor==null) {
					ctor = build(key);
					classes.put(key, ctor);
				}
			}
		}
		try {
			return ctor.newInstance();
		} catch (Exception ex) {
			throw new RuntimeException("Failed to construct ExpressionProcessor for [" + key + "]", ex);
		}
	}
	
	
	private Constructor<ExpressionProcessor> build(final String fullExpression) {
		Matcher m = FULL_EXPR.matcher(fullExpression);
		if(!m.matches()) throw new RuntimeException("Invalid full expression: [" + fullExpression + "]");
		String nameExpr = m.group(1).trim();
		String valueExpr = m.group(2).trim();
		ClassPool cp = new ClassPool();
		
		return null;
	}
	
	//public ExpressionResult process(Map<String, Object> attrValues, ObjectName objectName);
//	protected abstract void doName(final Map<String, Object> attrValues, final ObjectName objectName, final ExpressionResult result);	
//	protected abstract void doValue(final Map<String, Object> attrValues, final ObjectName objectName, final ExpressionResult result);

	
	
	/**
	 * Creates a new ExpressionCompiler
	 */
	private ExpressionCompiler() {
	
	}

}
