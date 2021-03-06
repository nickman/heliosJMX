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

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtMethod;
import javassist.CtNewConstructor;
import javassist.CtNewMethod;
import javassist.LoaderClassPath;
import javassist.Modifier;

import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;
import javax.management.MBeanServerDelegate;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import org.cliffc.high_scale_lib.NonBlockingHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.heliosapm.jmx.batch.aggregate.AggregateFunction;
import com.heliosapm.jmx.util.helpers.JMXHelper;
import com.heliosapm.opentsdb.TSDBSubmitter;
import com.heliosapm.opentsdb.TSDBSubmitterConnection;
import com.heliosapm.opentsdb.TSDBSubmitterImpl.ExpressionResult;
import com.heliosapm.script.StateService;

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
	
	
	/** Static class logger */
	protected static final Logger log = LoggerFactory.getLogger(ExpressionCompiler.class);
	
	/** A cache of compiled claass constructors */
	private final NonBlockingHashMap<String, Constructor<ExpressionProcessor>> classes = new NonBlockingHashMap<String, Constructor<ExpressionProcessor>>();
	/** Serial number to name generated classes */
	private final AtomicLong classSerial = new AtomicLong();
	
	/** The registered directives */
	protected final Set<DirectiveCodeProvider> providers = new CopyOnWriteArraySet<DirectiveCodeProvider>(Directives.PROVIDERS);

	/**
	 * {@inheritDoc}
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return new StringBuilder(getClass().getSimpleName())
			.append("[Compiled Classes:").append(classes.size())
			.append(", Providers:").append(providers.size())
			.append("]")
			.toString();
	}
	
	
	public static final Pattern FULL_EXPR = Pattern.compile("(.*?)\\s*?\\->(.*?)");
	public static final Pattern TOKEN_PATTERN = Pattern.compile("\\{(.*?)(?::(.*?))?\\}");
	public static final Pattern LOOPERS_PATTERN = Pattern.compile("foreach\\((.*?)\\)\\s+" + FULL_EXPR.pattern());
	public static final Pattern LOOPERS_ALL_PATTERN = Pattern.compile("forall\\((.*?)\\)\\[(.*?)\\]\\s+" + FULL_EXPR.pattern());
	public static final Pattern LOOPER_INSTANCE_PATTERN = Pattern.compile("\\{iter(\\d+)\\}");
	public static final Pattern LOOPER_SPLITTER = Pattern.compile("\\|");
	public static final Pattern FREPL = Pattern.compile("(\\$4\\[(\\d+)\\])");
	
	
	/*
	 * Loopers represent Iterables.
	 * If loopers are passed as (a, b, c), then the logic flow would be
	 * 		for(x in a) {
	 * 			for(y in b) {
	 * 				for(z in c) {
	 * 					// execute an expression which should probably reference x, y and z
	 * 				}
	 * 			}
	 * 		}
	 * 
	 * Execution Models:
	 * =================
	 *   Pure Symbolic:  The loopers are all symbolic so the full looped expression can be executed standalone
	 *   	LoopingExpressionProcessor lep =  
	 *   		(LoopingExpressionProcessor)ExpressionCompiler.getInstance().get("<looping expr>");
	 *   	
	 *   <need to add sourceId to js bindings and enhance arbitrary binding support>
	 *   
	 *   Symbolic and Actual Loopers:
	 *   	
	 */

	
//	ExpressionProcessor ep = ExpressionCompiler.getInstance().get("{domain}::{allkeys}->{attr:CollectionCount}"); 
//	ExpressionResult er = ep.process(agentId, attrValues, on, null);
	
	/** The CtClass for AbstractExpressionProcessor */
	protected static final CtClass abstractExpressionProcessorCtClass;
	/** The CtClass for ExpressionResult */
	protected static final CtClass expressionResultCtClass;
	/** The package in which new processors will be created in */
	public static final String PROCESSOR_PACKAGE = ExpressionCompiler.class.getPackage().getName() + ".impls";
	/** Empty CtClass Array Const */
	public static final CtClass[] EMPTY_CT_CLASS_ARR = {};
	/** String CtClass Const */
	public static final CtClass STRING_CT_CLASS;
	/** The validation and group defs for the name segment of the expression */
	public static final Pattern NAME_SEGMENT = Pattern.compile("^(.*?):(.*?)$");
	/** Splits the domain part of the expression from the tags */
	public static final Pattern NAME_TAG_SPLITTER = Pattern.compile("::");

	
	static {
		final ClassPool cp = new ClassPool();
		cp.appendSystemPath();
		cp.appendClassPath(new LoaderClassPath(AbstractExpressionProcessor.class.getClassLoader()));
		cp.appendClassPath(new LoaderClassPath(ExpressionResult.class.getClassLoader()));
		try {
			abstractExpressionProcessorCtClass = cp.get(AbstractExpressionProcessor.class.getName());
			expressionResultCtClass = cp.get(ExpressionResult.class.getName());
			STRING_CT_CLASS = cp.get(String.class.getName());
		} catch(Exception ex) {
			throw new RuntimeException(ex);
		}
	}
	
	
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
	
	/**
	 * @param fullExpression The expression to compile
	 * @param er The expression result handler the returned processor will flush results to 
	 * @return The compiled expression processor
	 */
	public ExpressionProcessor get(final String fullExpression, final ExpressionResult er) {
		if(fullExpression==null || fullExpression.trim().isEmpty()) throw new IllegalArgumentException("The passed full expression was null or empty");		
		final String key = fullExpression.trim();
		Constructor<ExpressionProcessor> ctor = classes.get(key);
		if(ctor==null) {
			synchronized(classes) {
				ctor = classes.get(key);
				if(ctor==null) {
					ctor = build(key, er);
					classes.put(key, ctor);
				}
			}
		}
		try {
			final Object[] args = ctor.getParameterTypes().length==1 ? new Object[]{er} : new Object[]{er, true};
			return ctor.newInstance(args);
		} catch (Exception ex) {
			throw new RuntimeException("Failed to construct ExpressionProcessor for [" + key + "]", ex);
		}
	}
	
	
	
	public CtMethod findMethod(final CtClass clazz, final String name) {
		for(CtMethod ctm: clazz.getMethods()) {
			if(ctm.equals(name)) return ctm;
		}
		throw new RuntimeException("Failed to find method named [" + name + "] in CtClass [" + clazz.getName() + "]");
	}
	
	/**
	 * Indicates if the passed expression is a looper
	 * @param fullExpression The expression to test
	 * @return true if the expression is a looper, false otherwise
	 */
	private boolean isLooper(final String fullExpression) {
		if(fullExpression==null || fullExpression.trim().isEmpty()) return false;
		return LOOPERS_PATTERN.matcher(fullExpression).matches() || LOOPERS_ALL_PATTERN.matcher(fullExpression).matches();
	}
	
//	/**
//	 * Builds and returns the looper all source code block, returning null if no loopers are found
//	 * @param fullExpression The full expression
//	 * @param looperDecodes The looper tokens are placed in here
//	 * @return the looper source code block or null
//	 */
//	protected String buildLooperAllCode(final String fullExpression, final LinkedHashMap<String, String> looperDecodes) {
//		
//	}
	
	
	/**
	 * Builds and returns the looper each source code block, returning null if no loopers are found
	 * @param fullExpression The full expression
	 * @param looperDecodes The looper tokens are placed in here
	 * @return the looper source code block or null
	 */
	protected String buildLooperCode(final String fullExpression, final LinkedHashMap<String, String> looperDecodes) {
		if(!isLooper(fullExpression)) return null;
		final CodeBuilder openLooperCode = new CodeBuilder();
		final CodeBuilder closeLooperCode = new CodeBuilder();
		// true for an iterator, false for a map
		final LinkedHashMap<String, Boolean> looperTypes = new LinkedHashMap<String, Boolean>(); 
		int looperSeq = 0;
		// true for an all looper, false for an each
		final boolean looperAll = LOOPERS_ALL_PATTERN.matcher(fullExpression).matches();
		

		final Matcher fullExpressionMatcher = looperAll ? LOOPERS_ALL_PATTERN.matcher(fullExpression) : LOOPERS_PATTERN.matcher(fullExpression);
		if(!fullExpressionMatcher.matches()) throw new RuntimeException("Invalid expression: [" + fullExpression + "]");
		final String looperExpr = fullExpressionMatcher.group(1);
		final String looperAggregators = looperAll ? fullExpressionMatcher.group(2) : null;
		final AggregateFunction[] aggregations = looperAll ? AggregateFunction.forNames(looperAggregators) : null;
		if(looperAll && (aggregations==null || aggregations.length==0)) throw new RuntimeException("Invalid forall aggregation list [" + looperAggregators + "] in expression: [" + fullExpression + "]");
		if(looperAll) {
			for(AggregateFunction af: aggregations) {
				if(af.numeric) {
					openLooperCode.append("\n\tfinal Map<String, List<Number>> %sAggrMap = new HashMap<String, List<Number>>();");
				}
				// TODO:  Handle JSON aggregations
			}
		}
		
		// =======================================================================
		// Collect looper directives
		// =======================================================================
		final String[] looperExprs = looperExpr.split(",");  //  split(",") -->  a, b:c, d:<e:f>
		final List<String> trimmedLoopers = new ArrayList<String>(looperExprs.length);
		for(int i = 0; i < looperExprs.length; i++) {
			if(looperExprs[i]==null) continue;
			looperExprs[i] = looperExprs[i].replace(" ", ""); 
			if(looperExprs[i].isEmpty()) continue;
			trimmedLoopers.add(looperExprs[i]);
		}
		if(!trimmedLoopers.isEmpty()) {
			for(String s: trimmedLoopers) {
				String[] frags = s.split(":");
				if(frags.length==1) looperTypes.put(s, true);
				else if(frags.length==2) looperTypes.put(s, false);
				else throw new RuntimeException("Invalid looper declaration [" + s + "] in expression [" + fullExpression + "]");
			}
		}
		if(!looperTypes.isEmpty()) {
			final StringBuilder looperValuesBuff = new StringBuilder();
			int expectedArgsCount = 0;
			for(Map.Entry<String, Boolean> entry : looperTypes.entrySet()) {
				looperValuesBuff.append(entry.getKey()).append(",");
				expectedArgsCount += (entry.getValue() ? 1 : 2);
			}
			final String looperValuesSig = looperValuesBuff.deleteCharAt(looperValuesBuff.length()-1).toString().replace(':', ',');
			openLooperCode.append("\n\t{\nif($4.length != %s) throw new RuntimeException(\"Expecting %s vargs but got only \" + $4.length);", looperTypes.size(), looperTypes.size());
			openLooperCode.append("\n\tfinal StringBuilder erBuff = new StringBuilder();"); 
			int index = 0;
			for(Map.Entry<String, Boolean> entry: looperTypes.entrySet()) {
				final boolean isIter = entry.getValue();
				
				if(isIter) {
					openLooperCode.append("\n\tfinal Iterable %sIter = $4[%s];", entry.getKey(), index);							
				} else {
					final String mapName = entry.getKey().replace(":", "");
					openLooperCode.append("\n\tfinal Map %sMap = $4[%s];", mapName, index);
				}
				index++;
			}
			index = 0;
			for(Map.Entry<String, Boolean> entry: looperTypes.entrySet()) {
				openLooperCode.append("\n\t");
				closeLooperCode.append("\n\t");						
				for(int x = 0; x < index; x++) {
					openLooperCode.append("\t");
					closeLooperCode.append("\t");
				}
				closeLooperCode.append("}");
				final boolean isIter = entry.getValue();
				if(isIter) {
					openLooperCode.append("for(Iterator %sIterator = %sIter.iterator(); %sIterator.hasNext();) { \nfinal String %s = iterStr(%sIterator);", entry.getKey(), entry.getKey(), entry.getKey(), entry.getKey(), entry.getKey());    
					if(!looperDecodes.containsKey("{" + entry.getKey() + "}")) {
						looperDecodes.put("{" + entry.getKey() + "}", "$4[" + looperSeq + "]");
						looperSeq++;
					}
				} else {
					final String mapName = entry.getKey().replace(":", "");
					final String[] keyVal = entry.getKey().split(":");
					final String key = keyVal[0];
					final String val = keyVal[1];
					openLooperCode.append("\n\tfinal Set %sMapEntrySet = %sMap.entrySet();", mapName, mapName);
					openLooperCode.append("for(Iterator %sMapIterator = %sMapEntrySet.iterator(); %sMapIterator.hasNext();) { \n final java.util.Map.Entry %sEntry = (java.util.Map.Entry)%sMapIterator.next();", mapName, mapName, mapName, mapName, mapName);  
					char[] indent = new char[index+2];
					Arrays.fill(indent, '\t');
					indent[0] = '\n';
					openLooperCode.append(new String(indent));
					openLooperCode.append("final String %s = mapEntryKeyStr(%sEntry);", key, mapName);
					openLooperCode.append("final String %s = mapEntryValStr(%sEntry);", val, mapName);
					if(!looperDecodes.containsKey("{" + key + "}")) {
						looperDecodes.put("{" + key + "}", "$4[" + looperSeq + "]");
						looperSeq++;								
					}
					if(!looperDecodes.containsKey("{" + val + "}")) {
						looperDecodes.put("{" + val + "}", "$4[" + looperSeq + "]");
						looperSeq++;
					}
				}
			}
			char[] indent = new char[index+3];
			Arrays.fill(indent, '\t');
			indent[0] = '\n';
			openLooperCode.append(new String(indent)).append("final String[] _sargs_ = new String[]{%s};", looperValuesSig);
			openLooperCode.append(new String(indent)).append("final boolean _dname_ = doName($1, $2, $3, _sargs_);");
			openLooperCode.append(new String(indent)).append("final boolean _dvalue_ = doValue($1, $2, $3, _sargs_);");	
			
			if(looperAll) {
				// openLooperCode.append("\n\tfinal Map<String, List<Number>> %sAggrMap = new HashMap<String, List<Number>>();");
				
			} else {
				openLooperCode.append(new String(indent)).append("if(_dname_ && _dvalue_) { er.flush(erBuff); erBuff.append(EOL);}");
			}

			openLooperCode.append(new String(indent)).append("log.debug(\"dn:{}, dv:{}, ARGS:{}\", new Object[]{ \"\"+_dname_, \"\"+_dvalue_, _sargs_});");
			
			closeLooperCode.append("\n\treturn erBuff;\n}");
			return openLooperCode.append(closeLooperCode.render()).render();
		} else {
			return null;
		}		
	}
	
	
	
	/**
	 * Tests the passed entry to see if it is a looper variable, and adds it's index to the passed map if it is.
	 * @param variableNames The variable name map to add to
	 * @param entry The entry to test
	 */
	protected void addVariableName(final Map<Integer, String> variableNames, final Map.Entry<String, String> entry) {
		final String v = entry.getValue();
		if(v==null || v.trim().isEmpty()) return;
		final Matcher m = FREPL.matcher(v);
		if(!m.matches()) return;
		final int key = Integer.parseInt(m.group(2));
		variableNames.put(key, entry.getKey().replace("{", "").replace("}", ""));
	}
	
	/**
	 * Here's where the real work is done
	 * @param fullExpression
	 * @param er
	 * @return
	 */
	private Constructor<ExpressionProcessor> build(final String preLoopFullExpression, final ExpressionResult er) {
		final LinkedHashMap<String, String> looperVariableDecodes = new LinkedHashMap<String, String>();
		final HashMap<Integer, String> looperVariableNames = new HashMap<Integer, String>();
		final String looperCodeBlock = buildLooperCode(preLoopFullExpression, looperVariableDecodes); 
		final boolean isLooper = looperCodeBlock!=null;
		final String fullExpression;
		if(isLooper && !looperVariableDecodes.isEmpty()) {
			String tmp = preLoopFullExpression;
			for(Map.Entry<String, String> entry: looperVariableDecodes.entrySet()) {
				addVariableName(looperVariableNames, entry);
				final String token = entry.getKey();
				final String replacement = entry.getValue();
				tmp = tmp.replace(token, replacement);
			}
			fullExpression = tmp;
		} else {
			fullExpression = preLoopFullExpression;
		}
		
		final String nameExpr;
		final String valueExpr;
		final Matcher fullExpressionMatcher;
		if(isLooper) {
			fullExpressionMatcher = LOOPERS_PATTERN.matcher(fullExpression);
			if(!fullExpressionMatcher.matches()) throw new RuntimeException("Invalid expression: [" + fullExpression + "]");
			nameExpr = fullExpressionMatcher.group(2);
			valueExpr = fullExpressionMatcher.group(3);
		} else {
			fullExpressionMatcher = FULL_EXPR.matcher(fullExpression);
			if(!fullExpressionMatcher.matches()) throw new RuntimeException("Invalid expression: [" + fullExpression + "]");
			nameExpr = fullExpressionMatcher.group(1);
			valueExpr = fullExpressionMatcher.group(2);			
		}
		ClassPool cp = new ClassPool();
		cp.appendSystemPath();
		cp.appendClassPath(new LoaderClassPath(ExpressionResult.class.getClassLoader()));
		cp.importPackage(JMXHelper.class.getPackage().getName());
		cp.importPackage(StateService.class.getPackage().getName());
		cp.importPackage("javax.script");
		cp.importPackage("java.util");
		cp.importPackage("com.heliosapm.jmx.util.helpers");
		
		CtClass processorCtClass = null;
		final String className = PROCESSOR_PACKAGE + ".ExpressionProcessorImpl" + classSerial.incrementAndGet();
		CtConstructor defaultCtor = null;
		
		try {
			processorCtClass = cp.makeClass(className, abstractExpressionProcessorCtClass);
		
			// Add default ctor
			if(isLooper) {				
				defaultCtor = CtNewConstructor.make(new CtClass[] {expressionResultCtClass, CtClass.booleanType}, new CtClass[0], processorCtClass);
			} else {
				defaultCtor = CtNewConstructor.make(new CtClass[] {expressionResultCtClass}, new CtClass[0], processorCtClass);
			}
			processorCtClass.addConstructor(defaultCtor);
			
			// =======================================================================
			// CodeBuilder for naming and value
			// =======================================================================
			final CodeBuilder codeBuffer = new CodeBuilder().append("{\n\tfinal StringBuilder nBuff = new StringBuilder();\n");
			codeBuffer.push();
			// =======================================================================
			// Collect name directives
			// =======================================================================

			processName(nameExpr, codeBuffer, looperVariableNames);
			codeBuffer.append("\n\ter.objectName(nBuff);\n\treturn true;}");
			log.info("Generated Name Code for Expr [{}]:\n{}", fullExpression, codeBuffer);

			// =======================================================================
			// Add do name method
			// =======================================================================
			CtMethod absDoNameMethod = abstractExpressionProcessorCtClass.getDeclaredMethod("doName");
			CtMethod doNameMethod = CtNewMethod.copy(absDoNameMethod, processorCtClass, null); 
			doNameMethod.setBody(codeBuffer.render());
			doNameMethod.setModifiers(doNameMethod.getModifiers() & ~Modifier.ABSTRACT);
			processorCtClass.addMethod(doNameMethod);
			
			// Reset the code buffer
			codeBuffer.pop();
			
			// =======================================================================
			// Collect value directives
			// =======================================================================
			processValue(valueExpr, codeBuffer, looperVariableNames);
			codeBuffer.append("\n\ter.value(nBuff);\n\treturn true;}");
			log.info("Generated Value Code:\n{}", codeBuffer);
			
			// =======================================================================
			// Add do value method
			// =======================================================================
			CtMethod absDoValueMethod = abstractExpressionProcessorCtClass.getDeclaredMethod("doValue");
			CtMethod doValueMethod = new CtMethod(absDoValueMethod.getReturnType(), "doValue", absDoValueMethod.getParameterTypes(), processorCtClass);
			
			doValueMethod.setBody(codeBuffer.render());
			doValueMethod.setModifiers(doValueMethod.getModifiers() & ~Modifier.ABSTRACT);
			processorCtClass.addMethod(doValueMethod);

			// =======================================================================
			// Add toString method
			// =======================================================================
		
			CtMethod toStringMethod = new CtMethod(STRING_CT_CLASS, "toString", EMPTY_CT_CLASS_ARR, processorCtClass);
			toStringMethod.setModifiers(toStringMethod.getModifiers() & ~Modifier.ABSTRACT);
			processorCtClass.addMethod(toStringMethod);			
			toStringMethod.setBody("{ return getClass().getName() + \" --> [" + preLoopFullExpression + "]\"; }");
			
			// =======================================================================
			// Add processLoop method
			// =======================================================================
			if(isLooper) {
				// looperCodeBlock
				CtMethod processLoopMethod = null;
				for(CtMethod ctm: processorCtClass.getMethods()) {
					if("processLoop".equals(ctm.getName())) {
						processLoopMethod = ctm;
						break;
					}
				}
				log.info("\n\t===============\n\tAdding Looper:\n{}\n\t====================\n", looperCodeBlock);
				final CtMethod processLoopOverrideMethod = CtNewMethod.copy(processLoopMethod, processorCtClass, null);
				processLoopOverrideMethod.setBody(looperCodeBlock);
				processorCtClass.addMethod(processLoopOverrideMethod);				
			}
			
			// =======================================================================
			// Generate class
			// =======================================================================
			processorCtClass.writeFile(System.getProperty("java.io.tmpdir") + File.separator + "heliosjmx");
			Class<ExpressionProcessor> clazz = processorCtClass.toClass();
			final Class<?>[] sig = defaultCtor.getParameterTypes().length==1 ? new Class[]{ExpressionResult.class} : new Class[]{ExpressionResult.class, boolean.class}; 
			return clazz.getDeclaredConstructor(sig);
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
	
	
	
	
	protected void processName(final String fullExpression, final CodeBuilder nameCode, final Map<Integer, String> variableNames) {
		String[] metricAndTags = NAME_TAG_SPLITTER.split(fullExpression);
		if(metricAndTags.length!=2) throw new RuntimeException("Expression [" + fullExpression + "] did not split to 2 segments");
		if(metricAndTags[0]==null || metricAndTags[0].trim().isEmpty()) throw new RuntimeException("MetricName segment in [" + fullExpression + "] was null or empty");
		if(metricAndTags[1]==null || metricAndTags[1].trim().isEmpty()) throw new RuntimeException("Tags segment in [" + fullExpression + "] was null or empty");
		metricAndTags[0] = metricAndTags[0].trim();
		metricAndTags[1] = metricAndTags[1].trim();		
		build(metricAndTags[0], nameCode, variableNames);
		nameCode.append("\n\tnBuff.append(\":\");");		
		build(metricAndTags[1], nameCode, variableNames);		
//		nameCode.append("\n\targsRepl(nBuff, $4);");
	}
	
	protected void processValue(final String valueExpression, final CodeBuilder valueCode, final Map<Integer, String> variableNames) {
		if(valueExpression==null || valueExpression.trim().isEmpty()) throw new RuntimeException("Value Expression was null or empty");
		String valueExpr = valueExpression.trim();
		build(valueExpr, valueCode, variableNames);				
	}
	
	public static void main(String[] args) {
		try {
			log.info("Testing ExpressionCompiler");
			final TSDBSubmitter submitter = TSDBSubmitterConnection.getTSDBSubmitterConnection("localhost", 4242).submitter().addRootTag("app", "StockTrader").addRootTag("host", "ro-dev9").setLogTraces(true);
			final ExpressionResult er = submitter.newExpressionResult();
			//getInstance().get("{domain}.gc.{attr:Foo}::type={key:type},type={key:name}->{attr:A/B/C}");
	//		getInstance().get("{domain}.gc.{attr:Foo}::{allkeys}->{attr:A/B/C}");
			try {
				getInstance().get("{domain}.gc.{eval:d(nuthin):ON.getKeyProperty('Foo');}::{allkeys}->{attr:A/B/C}", er);
				ManagementFactory.getMemoryMXBean().gc();
				
				MBeanServer server = ManagementFactory.getPlatformMBeanServer();			
				final String agentId = server.getAttribute(MBeanServerDelegate.DELEGATE_NAME, "MBeanServerId").toString();
				for(ObjectName on: server.queryNames(JMXHelper.objectName(ManagementFactory.GARBAGE_COLLECTOR_MXBEAN_DOMAIN_TYPE + ",*"), null)) {
					Map<String, Object> attrValues = JMXHelper.getAttributes(on, server, JMXHelper.getAttributeNames(on));
					ExpressionProcessor ep = ExpressionCompiler.getInstance().get("{domain}::{allkeys}->{attr:CollectionCount}", er); 
					CharSequence m = ep.process(agentId, attrValues, on);
					log.info("Expr [{}]: {}", on, er);
				}
			} catch (Exception ex) {
				ex.printStackTrace(System.err);
			}
			log.info("\n\t===============================\n\tHBase Test\n\t===============================\n");
			try {
				JMXServiceURL serviceUrl = new JMXServiceURL("service:jmx:attach:///[.*HMaster.*]");
				JMXConnector jconn = JMXConnectorFactory.connect(serviceUrl);
				MBeanServerConnection server = jconn.getMBeanServerConnection();
				final String agentId = JMXHelper.getAgentId(server); //server.getAttribute(MBeanServerDelegate.DELEGATE_NAME, "MBeanServerId").toString();
				log.info("Connected to HMaster. ServerID: [{}]", agentId);
				for(ObjectName on: server.queryNames(JMXHelper.objectName(ManagementFactory.GARBAGE_COLLECTOR_MXBEAN_DOMAIN_TYPE + ",*"), null)) {
					Map<String, Object> attrValues = JMXHelper.getAttributes(on, server, JMXHelper.getAttributeNames(on));
					ExpressionProcessor ep = ExpressionCompiler.getInstance().get("{domain}::{allkeys}->{attr:CollectionCount}", er); 
					String rez = ep.process(agentId, attrValues, on).toString();
					log.info("Expr [{}]: {}", on, rez);
				}
				Set<String> memoryPoolNames = new HashSet<String>(5);
				int index = 0;
				for(ObjectName on: server.queryNames(JMXHelper.objectName("java.lang:type=MemoryPool,*"), null)) {
					memoryPoolNames.add(on.getKeyProperty("name"));
				}
				log.info("Memory Pool Names: {}", memoryPoolNames.toString());
				
				ExpressionProcessor ep = ExpressionCompiler.getInstance()
	//					.get("{domain}::{allkeys},pool={attr:MemoryPoolNames([0])}->{attr:LastGCInfo}");
						.get("{domain}::{allkeys},pool={eval:attrValues.get('MemoryPoolNames')[0]}->1", er);
				
				
				for(ObjectName on: server.queryNames(JMXHelper.objectName("java.lang:type=GarbageCollector,*"), null)) {
					final String gcName = on.getKeyProperty("name");
					String memPoolName = ((String[])server.getAttribute(on, "MemoryPoolNames"))[0];
					log.info("GC: [{}], First Pool: [{}]", gcName, memPoolName);
					String rez = ep.process(agentId, JMXHelper.getAttributes(on, server, JMXHelper.getAttributeNames(on, server)), on).toString();
					log.info("ER: [{}], rez: [{}]", er, rez);
				}
				
				// java.lang:type=OperatingSystem
				ObjectName on = JMXHelper.objectName("java.lang:type=OperatingSystem");
				Map<String, Object> attrValues = JMXHelper.getAttributes(on, server, JMXHelper.getAttributeNames(on));
				ep = ExpressionCompiler.getInstance().get("{domain}::{allkeys}->{attr:FreeSwapSpaceSize}", er); 
				String rez = ep.process(agentId, attrValues, on).toString();
				log.info("ER: [{}], rez: [{}]", er, rez);
				int cnt = 0;
				for(String k: System.getenv().keySet()) {
					attrValues.put(k, cnt);
					cnt++;
				}
				
				ep = ExpressionCompiler.getInstance().get("foreach(a, b:c) {domain}.os.{a}::{allkeys}->{attr:{a}}", er);
				rez = ep.process(agentId, attrValues, on, System.getenv().keySet(), System.getProperties()).toString();
				ep.deepFlush();
//				log.info("ER: [{}], rez: [{}]", ep, rez);
				
				
				
				//  {eval:attrValues.get('MemoryPoolNames')[0]}
				
				/*
				 * LastGcInfo / 
				 * 		GcThreadCount, duration
				 * 		memoryUsageBeforeGc / memoryUsageAfterGc
				 * 			key: <pool name>  e.g. CMS Old Gen
				 * 			value: MemoryUsage
				 * 				committed, init, max, used
				 */
				
				jconn.close();
				
			} catch (Exception ex) {
				ex.printStackTrace(System.err);
			}
		} finally {
			System.exit(0);
		}
	}
	
	/**
	 * Finds the maximum looper iteration id, which is also the 
	 * minimum number of symbolic and parameterized loopers 
	 * @param expr The expression
	 * @return the max iter id, or -1 if none were found
	 */
	public int maxIterator(final String expr) {
		final Matcher m = LOOPER_INSTANCE_PATTERN.matcher(expr);
		TreeSet<Integer> iterIds = new TreeSet<Integer>();
		while(m.find()) {
			iterIds.add(Integer.parseInt(m.group(1)));
		}
		if(iterIds.isEmpty()) return -1;
		return iterIds.last();
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
	 * @param nameSegment The name portion of the raw expression
	 * @param nameCode The code builder 
	 */
	protected void build(final String nameSegment, final CodeBuilder nameCode, final Map<Integer, String> variableNames) {
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
			resolveDirective(m.group(), nameCode, variableNames);
			lstart = matcherEnd;
		}		
		if(lstart < nameSegment.length()) {
			nameCode.append("\n\tnBuff.append(\"").append(nameSegment.substring(lstart, nameSegment.length())).append("\");");
		}
		nameCode.append("\n\targsRepl(nBuff, $4);");
	}
	
	
	protected void resolveDirective(final String directive, final CodeBuilder nameCode, final Map<Integer, String> variableNames) {
		for(DirectiveCodeProvider provider: providers) {
			if(provider.match(directive)) {
				provider.generate(directive, nameCode, variableNames);
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

