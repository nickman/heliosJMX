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
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtMethod;
import javassist.CtNewConstructor;
import javassist.LoaderClassPath;
import javassist.Modifier;

import org.cliffc.high_scale_lib.NonBlockingHashMap;

import com.heliosapm.SimpleLogger;
import com.heliosapm.SimpleLogger.SLogger;
import com.heliosapm.jmx.util.helpers.JMXHelper;

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
	
	
	/** Instance simple logger */
	protected static final SLogger log = SimpleLogger.logger(ExpressionCompiler.class);
	
	/** A cache of compiled claass constructors */
	private final NonBlockingHashMap<String, Constructor<ExpressionProcessor>> classes = new NonBlockingHashMap<String, Constructor<ExpressionProcessor>>();
	/** Serial number to name generated classes */
	private final AtomicLong classSerial = new AtomicLong();
	
	/** The registered directives */
	protected final Set<DirectiveCodeProvider> providers = new CopyOnWriteArraySet<DirectiveCodeProvider>(Directives.PROVIDERS);
	
	public static final Pattern VALUE_LOOKUP = Pattern.compile("\\{(.*?):(.*?)\\}");
	public static final Pattern DOMAIN_VALUE = Pattern.compile("\\{domain\\}");
	public static final Pattern FULL_EXPR = Pattern.compile("(.*?)\\s*?\\->(.*?)");
	
	public static final Pattern KEY_EXPR = Pattern.compile("key:(.*?)");
	
	public static final Pattern TOKEN_PATTERN = Pattern.compile("\\{(.*?)(?::(.*?))?\\}");
	
	/** The CtClass for AbstractExpressionProcessor */
	protected static final CtClass abstractExpressionProcessorCtClass;
	/** The package in which new processors will be created in */
	public static final String PROCESSOR_PACKAGE = ExpressionCompiler.class.getPackage().getName() + ".impls";
	
	static {
		final ClassPool cp = new ClassPool();
		cp.appendSystemPath();
		cp.appendClassPath(new LoaderClassPath(AbstractExpressionProcessor.class.getClassLoader()));
		try {
			abstractExpressionProcessorCtClass = cp.get(AbstractExpressionProcessor.class.getName());
		} catch(Exception ex) {
			throw new RuntimeException(ex);
		}
	}
	
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
//					classes.put(key, ctor);
				}
			}
		}
		try {
//			return ctor.newInstance();
			return null;
		} catch (Exception ex) {
			throw new RuntimeException("Failed to construct ExpressionProcessor for [" + key + "]", ex);
		}
	}
	
	public static String DO_NAME_DESCR = "(Ljava/lang/String;Ljava/util/Map;Ljavax/management/ObjectName;Lcom/heliosapm/jmx/expr/ExpressionResult;)V";
	
	public CtMethod findMethod(final CtClass clazz, final String name) {
		for(CtMethod ctm: clazz.getMethods()) {
			if(ctm.equals(name)) return ctm;
		}
		throw new RuntimeException("Failed to find method named [" + name + "] in CtClass [" + clazz.getName() + "]");
	}
	
	private Constructor<ExpressionProcessor> build(final String fullExpression) {
		Matcher m = FULL_EXPR.matcher(fullExpression);
		if(!m.matches()) throw new RuntimeException("Invalid full expression: [" + fullExpression + "]");
		String nameExpr = m.group(1).trim();
		String valueExpr = m.group(2).trim();
		ClassPool cp = new ClassPool();
		cp.appendSystemPath();
//		cp.importPackage("java.lang");
		cp.importPackage(JMXHelper.class.getPackage().getName());
		CtClass processorCtClass = null;
		final String className = PROCESSOR_PACKAGE + ".ExpressionProcessorImpl" + classSerial.incrementAndGet();
		StringBuilder nameBuffer = new StringBuilder("{\n\tfinal StringBuilder nBuff = new StringBuilder();");
		StringBuilder valueBuffer = new StringBuilder();
		process(nameExpr, nameBuffer);
		nameBuffer.append("\n\t$4.objectName(nBuff);\n}");
		log.log("Generated Name Code:\n%s", nameBuffer);
//		log.log("Generated Tags Code:\n%s", tagsBuffer);
		try {
			processorCtClass = cp.makeClass(className, abstractExpressionProcessorCtClass);
			CtMethod absDoNameMethod = abstractExpressionProcessorCtClass.getDeclaredMethod("doName");
			CtMethod doNameMethod = new CtMethod(absDoNameMethod.getReturnType(), "doName", absDoNameMethod.getParameterTypes(), processorCtClass);
			//CtConstructor ctor = new CtConstructor(new CtClass[0], processorCtClass);
			//ctor.setBody(null);
			processorCtClass.addConstructor(CtNewConstructor.defaultConstructor(processorCtClass));
			doNameMethod.setBody(nameBuffer.toString());
			doNameMethod.setModifiers(doNameMethod.getModifiers() & ~Modifier.ABSTRACT);
			processorCtClass.addMethod(doNameMethod);
			log.log("Got doName method: [%s]", doNameMethod);
			
			Class<ExpressionProcessor> clazz = (Class<ExpressionProcessor>) processorCtClass.getClass();
			return clazz.getDeclaredConstructor();
			
		} catch (Exception ex) {
			throw new RuntimeException("Failed to generate Expression Processor Class for ["  + fullExpression + "]", ex);
		}
	}
	
	/*
	 * protected abstract void doName(final String sourceId, final Map<String, Object> attrValues, final ObjectName objectName, final ExpressionResult result);
	 * nBuff = new StringBuilder();
	 * <generated code>
	 * exprResult.objectName(nBuff)
	 */
	
	
	/** The validation and group defs for the name segment of the expression */
	public static final Pattern NAME_SEGMENT = Pattern.compile("^(.*?):(.*?)$");
	
	/** Splits the domain part of the expression from the tags */
	public static final Pattern NAME_TAG_SPLITTER = Pattern.compile("::");
	
	
	protected void process(final String fullExpression, final StringBuilder nameCode) {
		String[] metricAndTags = NAME_TAG_SPLITTER.split(fullExpression);
		if(metricAndTags.length!=2) throw new RuntimeException("Expression [" + fullExpression + "] did not split to 2 segments");
		if(metricAndTags[0]==null || metricAndTags[0].trim().isEmpty()) throw new RuntimeException("MetricName segment in [" + fullExpression + "] was null or empty");
		if(metricAndTags[1]==null || metricAndTags[1].trim().isEmpty()) throw new RuntimeException("Tags segment in [" + fullExpression + "] was null or empty");
		metricAndTags[0] = metricAndTags[0].replace(" ", "");
		metricAndTags[1] = metricAndTags[1].replace(" ", "");		
		build(metricAndTags[0], nameCode);
		nameCode.append("\n\tnBuff.append(\":\");");
		build(metricAndTags[1], nameCode);
		
	}
	
	public static void main(String[] args) {
		log.log("Testing ExpressionCompiler");
		//getInstance().get("{domain}.gc.{attr:Foo}::type={key:type},type={key:name}->{attr:A/B/C}");
		getInstance().get("{domain}.gc.{attr:Foo}::{allkeys}->{attr:A/B/C}");
		
	}
	
	/**
	 * Builds the method:
	 * 	<pre>
	 * 		doName(
	 * 			String sourceId,
	 * 			Map<String, Object> attrValues, 
	 * 			ObjectName objectName, 
	 * 			ExpressionResult result)
	 *  </pre> 
	 *  @param nameSegment The name portion of the raw expression
	 */
	protected void build(final String nameSegment, final StringBuilder nameCode) {
		//localVar nBuff builds the metric name
        int lstart = 0;
        int matcherStart = 0;
        int matcherEnd = 0;
		Matcher m = TOKEN_PATTERN.matcher(nameSegment);
		while(m.find()) {
			matcherStart = m.start();
			matcherEnd = m.end();
			if(matcherStart > lstart) {
				nameCode.append("\n\tnBuff.append(\"").append(nameSegment.substring(lstart, matcherStart)).append("\");");				
			}
			resolveDirective(m.group(), nameCode);
			lstart = matcherEnd;
		}		
		if(lstart < nameSegment.length()) {
			nameCode.append("\n\tnBuff.append(\"").append(nameSegment.substring(lstart, nameSegment.length())).append("\");");
		}
	}
	
	
	protected void resolveDirective(final String directive, final StringBuilder nameCode) {
		for(DirectiveCodeProvider provider: providers) {
			if(provider.match(directive)) {
				provider.generate(directive, nameCode);
				break;
			}
		}
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

