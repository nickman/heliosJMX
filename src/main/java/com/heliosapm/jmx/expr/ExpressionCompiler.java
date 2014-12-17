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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
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

import com.heliosapm.SimpleLogger;
import com.heliosapm.SimpleLogger.SLogger;
import com.heliosapm.jmx.util.helpers.ArrayUtils;
import com.heliosapm.jmx.util.helpers.JMXHelper;
import com.heliosapm.opentsdb.ExpressionResult;
import com.heliosapm.opentsdb.TSDBSubmitter;
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
	
	
	/** Instance simple logger */
	protected static final SLogger log = SimpleLogger.logger(ExpressionCompiler.class);
	
	/** A cache of compiled claass constructors */
	private final NonBlockingHashMap<String, Constructor<ExpressionProcessor>> classes = new NonBlockingHashMap<String, Constructor<ExpressionProcessor>>();
	/** Serial number to name generated classes */
	private final AtomicLong classSerial = new AtomicLong();
	
	/** The registered directives */
	protected final Set<DirectiveCodeProvider> providers = new CopyOnWriteArraySet<DirectiveCodeProvider>(Directives.PROVIDERS);

	
	public static final Pattern FULL_EXPR = Pattern.compile("(.*?)\\s*?\\->(.*?)");
	public static final Pattern TOKEN_PATTERN = Pattern.compile("\\{(.*?)(?::(.*?))?\\}");
	public static final Pattern LOOPERS_PATTERN = Pattern.compile("foreach\\((.*?)\\)\\s+" + FULL_EXPR.pattern());
	public static final Pattern LOOPER_INSTANCE_PATTERN = Pattern.compile("\\{iter(\\d+)\\}");
	public static final Pattern LOOPER_SPLITTER = Pattern.compile("\\|");
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
	
	static {
		final ClassPool cp = new ClassPool();
		cp.appendSystemPath();
		cp.appendClassPath(new LoaderClassPath(AbstractExpressionProcessor.class.getClassLoader()));
		cp.appendClassPath(new LoaderClassPath(ExpressionResult.class.getClassLoader()));
		try {
			abstractExpressionProcessorCtClass = cp.get(AbstractExpressionProcessor.class.getName());
			expressionResultCtClass = cp.get(ExpressionResult.class.getName());
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
			return ctor.newInstance(er);
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
	
	private boolean isLooper(final String fullExpression) {
		return LOOPERS_PATTERN.matcher(fullExpression).matches();
	}
	
	private Constructor<ExpressionProcessor> build(final String fullExpression, final ExpressionResult er) {
		final boolean isLooper = isLooper(fullExpression);
		final String looperExpr;
		final String nameExpr;
		final String valueExpr;
		final Matcher fullExpressionMatcher;
		final int maxIter = maxIterator(fullExpression);
		if(isLooper) {
			fullExpressionMatcher = LOOPERS_PATTERN.matcher(fullExpression);
			if(!fullExpressionMatcher.matches()) throw new RuntimeException("Invalid expression: [" + fullExpression + "]");
			looperExpr = fullExpressionMatcher.group(1);
			nameExpr = fullExpressionMatcher.group(2);
			valueExpr = fullExpressionMatcher.group(3);
		} else {
			fullExpressionMatcher = FULL_EXPR.matcher(fullExpression);
			if(!fullExpressionMatcher.matches()) throw new RuntimeException("Invalid expression: [" + fullExpression + "]");
			looperExpr = null;
			nameExpr = fullExpressionMatcher.group(1);
			valueExpr = fullExpressionMatcher.group(2);			
		}
		ClassPool cp = new ClassPool();
		cp.appendSystemPath();
		cp.appendClassPath(new LoaderClassPath(ExpressionResult.class.getClassLoader()));
		cp.importPackage(JMXHelper.class.getPackage().getName());
		cp.importPackage(StateService.class.getPackage().getName());
		cp.importPackage("javax.script");
		cp.importPackage("com.heliosapm.jmx.util.helpers");
		
		

//		===================================================================
/*
 * Next:
 * 	at start, determine if we're a foreach loop or not: i.e. matches LOOPERS_PATTERN
 * 	if not, it's the same as now.
 * 
 *  if it is:
 *  	find all iter symbols
 *  	define looping variables for each one
 *  	implement the nested iterator loop  
 *  	(impl should save # of symbolic loopers 
 *  		and by derivation, the number of parameterized loopers required)
 *  
 *  	!!!  parameterized loopers are always inner to symbolics  !!!
 *  
 *  	execution is nested loop on iterables, action is:
 *  		bind looper variables
 *  		resolve all opens 
 *  		execute process
 * 		
 */
//		===================================================================		
		
		
		
		CtClass processorCtClass = null;
		final String className = PROCESSOR_PACKAGE + ".ExpressionProcessorImpl" + classSerial.incrementAndGet();
		
		
		try {
			processorCtClass = cp.makeClass(className, abstractExpressionProcessorCtClass);
			// Add default ctor
			CtConstructor defaultCtor = CtNewConstructor.make(new CtClass[] {expressionResultCtClass}, new CtClass[0], processorCtClass); 
			processorCtClass.addConstructor(defaultCtor);
		

			// =======================================================================
			// Collect looper directives
			// =======================================================================
			if(isLooper && maxIter >= 0) {
				// LOOPER_INSTANCE_PATTERN = Pattern.compile("\\{iter(\\d+)\\}")
				Set<String> looperNames = new HashSet<String>();
				Matcher m = LOOPER_INSTANCE_PATTERN.matcher(looperExpr);
				
				String[] looperExpressions = ArrayUtils.trim(LOOPER_SPLITTER.split(looperExpr));
				if(looperExpressions.length > 0) {
					
				}
			}
		
			
			
			// CodeBuilder for naming and value
			final CodeBuilder codeBuffer = new CodeBuilder().append("{\n\tfinal StringBuilder nBuff = new StringBuilder();");
			codeBuffer.push();
			// =======================================================================
			// Collect name directives
			// =======================================================================

			processName(nameExpr, codeBuffer);
			codeBuffer.append("\n\ter.objectName(nBuff);\n}");
			log.log("Generated Name Code:\n%s", codeBuffer);

			// =======================================================================
			// Add do name method
			// =======================================================================
			CtMethod absDoNameMethod = abstractExpressionProcessorCtClass.getDeclaredMethod("doName");
			//CtMethod doNameMethod = new CtMethod(absDoNameMethod.getReturnType(), "doName", absDoNameMethod.getParameterTypes(), processorCtClass);
			CtMethod doNameMethod = CtNewMethod.copy(absDoNameMethod, processorCtClass, null); 
//			for(CtClass ct: doNameMethod.getParameterTypes()) {
//				ct.setModifiers(ct.getModifiers() | Modifier.FINAL);
//			}
			doNameMethod.setBody(codeBuffer.render());
			doNameMethod.setModifiers(doNameMethod.getModifiers() & ~Modifier.ABSTRACT);
			processorCtClass.addMethod(doNameMethod);

			
			
			// Reset the code buffer
			codeBuffer.pop();
			
			// =======================================================================
			// Collect value directives
			// =======================================================================
			processValue(valueExpr, codeBuffer);
			codeBuffer.append("\n\ter.value(nBuff);\n}");
			log.log("Generated Value Code:\n%s", codeBuffer);
			
			// =======================================================================
			// Add do value method
			// =======================================================================
			CtMethod absDoValueMethod = abstractExpressionProcessorCtClass.getDeclaredMethod("doValue");
			CtMethod doValueMethod = new CtMethod(absDoValueMethod.getReturnType(), "doValue", absDoValueMethod.getParameterTypes(), processorCtClass);
			for(CtClass ct: doValueMethod.getParameterTypes()) {
				ct.setModifiers(ct.getModifiers() | Modifier.FINAL);
			}
			
			doValueMethod.setBody(codeBuffer.render());
			doValueMethod.setModifiers(doValueMethod.getModifiers() & ~Modifier.ABSTRACT);
			processorCtClass.addMethod(doValueMethod);
			// =======================================================================
			// Generate class
			// =======================================================================
			processorCtClass.writeFile(System.getProperty("java.io.tmpdir") + File.separator + "heliosjmx");
			Class<ExpressionProcessor> clazz = processorCtClass.toClass();
			return clazz.getDeclaredConstructor(ExpressionResult.class);
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
	
	
	protected void processName(final String fullExpression, final CodeBuilder nameCode) {
		String[] metricAndTags = NAME_TAG_SPLITTER.split(fullExpression);
		if(metricAndTags.length!=2) throw new RuntimeException("Expression [" + fullExpression + "] did not split to 2 segments");
		if(metricAndTags[0]==null || metricAndTags[0].trim().isEmpty()) throw new RuntimeException("MetricName segment in [" + fullExpression + "] was null or empty");
		if(metricAndTags[1]==null || metricAndTags[1].trim().isEmpty()) throw new RuntimeException("Tags segment in [" + fullExpression + "] was null or empty");
		metricAndTags[0] = metricAndTags[0].trim();
		metricAndTags[1] = metricAndTags[1].trim();		
		build(metricAndTags[0], nameCode);
		nameCode.append("\n\tnBuff.append(\":\");");
		build(metricAndTags[1], nameCode);		
	}
	
	protected void processValue(final String valueExpression, final CodeBuilder valueCode) {
		if(valueExpression==null || valueExpression.trim().isEmpty()) throw new RuntimeException("Value Expression was null or empty");
		String valueExpr = valueExpression.trim();
		build(valueExpr, valueCode);				
	}
	
	public static void main(String[] args) {
		log.log("Testing ExpressionCompiler");
		final TSDBSubmitter submitter = new TSDBSubmitter("localhost", 4242).addRootTag("app", "StockTrader").addRootTag("host", "ro-dev9");
		final ExpressionResult er = submitter.newExpressionResult();
		//getInstance().get("{domain}.gc.{attr:Foo}::type={key:type},type={key:name}->{attr:A/B/C}");
//		getInstance().get("{domain}.gc.{attr:Foo}::{allkeys}->{attr:A/B/C}");
		getInstance().get("{domain}.gc.{eval:d(nuthin):ON.getKeyProperty('Foo');}::{allkeys}->{attr:A/B/C}", er);
		ManagementFactory.getMemoryMXBean().gc();
		try {
			MBeanServer server = ManagementFactory.getPlatformMBeanServer();			
			final String agentId = server.getAttribute(MBeanServerDelegate.DELEGATE_NAME, "MBeanServerId").toString();
			for(ObjectName on: server.queryNames(JMXHelper.objectName(ManagementFactory.GARBAGE_COLLECTOR_MXBEAN_DOMAIN_TYPE + ",*"), null)) {
				Map<String, Object> attrValues = JMXHelper.getAttributes(on, server, JMXHelper.getAttributeNames(on));
				ExpressionProcessor ep = ExpressionCompiler.getInstance().get("{domain}::{allkeys}->{attr:CollectionCount}", er); 
				CharSequence m = ep.process(agentId, attrValues, on);
				log.log("Expr [%s]: %s", on, er);
			}
		} catch (Exception ex) {
			ex.printStackTrace(System.err);
		}
		log.log("\n\t===============================\n\tHBase Test\n\t===============================\n");
		try {
			JMXServiceURL serviceUrl = new JMXServiceURL("service:jmx:attach:///[.*HMaster.*]");
			JMXConnector jconn = JMXConnectorFactory.connect(serviceUrl);
			MBeanServerConnection server = jconn.getMBeanServerConnection();
			final String agentId = server.getAttribute(MBeanServerDelegate.DELEGATE_NAME, "MBeanServerId").toString();
			log.log("Connected to HMaster. ServerID: [%s]", agentId);
			for(ObjectName on: server.queryNames(JMXHelper.objectName(ManagementFactory.GARBAGE_COLLECTOR_MXBEAN_DOMAIN_TYPE + ",*"), null)) {
				Map<String, Object> attrValues = JMXHelper.getAttributes(on, server, JMXHelper.getAttributeNames(on));
				ExpressionProcessor ep = ExpressionCompiler.getInstance().get("{domain}::{allkeys}->{attr:CollectionCount}", er); 
				String rez = ep.process(agentId, attrValues, on).toString();
				log.log("Expr [%s]: %s", on, rez);
			}
			Set<String> memoryPoolNames = new HashSet<String>(5);
			int index = 0;
			for(ObjectName on: server.queryNames(JMXHelper.objectName("java.lang:type=MemoryPool,*"), null)) {
				memoryPoolNames.add(on.getKeyProperty("name"));
			}
			log.log("Memory Pool Names: %s", memoryPoolNames.toString());
			
			ExpressionProcessor ep = ExpressionCompiler.getInstance()
//					.get("{domain}::{allkeys},pool={attr:MemoryPoolNames([0])}->{attr:LastGCInfo}");
					.get("{domain}::{allkeys},pool={eval:attrValues.get('MemoryPoolNames')[0]}->1", er);
			
			
			for(ObjectName on: server.queryNames(JMXHelper.objectName("java.lang:type=GarbageCollector,*"), null)) {
				final String gcName = on.getKeyProperty("name");
				String memPoolName = ((String[])server.getAttribute(on, "MemoryPoolNames"))[0];
				log.log("GC: [%s], First Pool: [%s]", gcName, memPoolName);
				String rez = ep.process(agentId, JMXHelper.getAttributes(on, server, JMXHelper.getAttributeNames(on, server)), on).toString();
				log.log("ER: %s", er);
			}
			
			// java.lang:type=OperatingSystem
			ObjectName on = JMXHelper.objectName("java.lang:type=OperatingSystem");
			Map<String, Object> attrValues = JMXHelper.getAttributes(on, server, JMXHelper.getAttributeNames(on));
			ep = ExpressionCompiler.getInstance().get("{domain}::{allkeys}->{attr:CollectionCount}", er); 
			String rez = ep.process(agentId, attrValues, on).toString();
			
			
			
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
	protected void build(final String nameSegment, final CodeBuilder nameCode) {
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
	
	
	protected void resolveDirective(final String directive, final CodeBuilder nameCode) {
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

