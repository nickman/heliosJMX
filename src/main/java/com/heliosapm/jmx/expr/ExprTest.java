package com.heliosapm.jmx.expr;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.heliosapm.jmx.util.helpers.CacheService;
import com.heliosapm.jmx.util.helpers.JMXHelper;
import com.heliosapm.jmx.util.helpers.SystemClock;
import com.heliosapm.opentsdb.TSDBSubmitter;
import com.heliosapm.opentsdb.TSDBSubmitterConnection;
import com.heliosapm.opentsdb.TSDBSubmitterImpl.ExpressionResult;

class ExprTest {
	/** Static class logger */
	protected static final Logger log = LoggerFactory.getLogger(ExprTest.class);

    /** The Classloading MXBean OjectName */
    static final ObjectName CLASSLOADING_ON = JMXHelper.objectName("java.lang:type=ClassLoading");
    /** The Compilation MXBean OjectName */
    static final ObjectName COMPILATION_ON = JMXHelper.objectName("java.lang:type=Compilation");
    /** The Memory MXBean OjectName */
    static final ObjectName MEMORY_ON = JMXHelper.objectName("java.lang:type=Memory");
	

        static final String[] testcases = new String[] {
        "'Tumblr' is an amazing app",
        "Tumblr is an amazing 'app'",
        "Tumblr is an 'amazing' app",
        "Tumblr is 'awesome' and 'amazing' ",
        "Tumblr's users' are disappointed ",
        "Tumblr's 'acquisition' complete but users' loyalty doubtful"
        };
        static final Pattern p = Pattern.compile("(?:^|\\s)'([^']*?)'(?:$|\\s)", Pattern.MULTILINE);
//        for (String arg : testcases) {
//            System.out.print("Input: "+arg+" -> Matches: ");
//            Matcher m = p.matcher(arg);
//            if (m.find()) {
//                System.out.print(m.group());
//                while (m.find()) System.out.print(", "+m.group());
//                System.out.println();
//            } else {
//                System.out.println("NONE");
//            }
//        } 


    public static void main (String[] args) {
    	log.info("JMX Expression Compiler Test");
    	final Set<ExprTest> exprTests = new HashSet<ExprTest>();
    	exprTests.add(new ExprTest("service:jmx:attach:///[.*HMaster.*]", "hserval", "HBaseMaster"));
    	exprTests.add(new ExprTest("service:jmx:local:///DefaultDomain", "hserval", "ExprTest"));
//    	exprTests.add(new ExprTest("service:jmx:tunnel://tpsolaris:8006/ssh/jmxmp:u=nwhitehe,p=mysol!1", "tpsolaris", "GroovyEngine"));
    	//etLocal.traceJVMStats();
    	while(true) {
    		for(ExprTest ex: exprTests) {
    			ex.traceJVMStats();
    		}
    		SystemClock.sleep(5000);
    	}
//    	System.exit(-1);
    	
    }
    
    
    private final JMXConnector jmxConn;
    private final TSDBSubmitterConnection tsdbConn;
    private final TSDBSubmitter tsdbSub;    
    private final ExpressionResult er;
    private MBeanServerConnection mbeanServer = null;
    private String agentId = null;
    private final Set<ObjectName> gcNames;
    private final Set<ObjectName> memPoolNames;
    private Map<ObjectName, Map<String, Object>> targetObjectNames = null;
    
    public ExprTest(final String jmxUrl, final String host, final String app) {
    	Thread t = new Thread("ExprTest Closer") {
    		public void run() {
    			close();
    		}
    	};
    	t.setDaemon(true);
    	Runtime.getRuntime().addShutdownHook(t);
    	jmxConn = JMXHelper.getJMXConnection(jmxUrl);
    	mbeanServer = JMXHelper.getMBeanServerConnection(jmxConn);    	
    	agentId = JMXHelper.getAgentId(mbeanServer);
    	log.info("Connected to MBeanServer [{}] at JMX Endpoint: [{}]", agentId, jmxUrl);
    	tsdbConn = TSDBSubmitterConnection.getTSDBSubmitterConnection("localhost").setSendBufferSize(1024 * 1000).setTcpNoDelay(true).setKeepAlive(true).connect();
    	tsdbSub = tsdbConn.submitter().addRootTag("host", host).addRootTag("app", app).setLogTraces(true).setTraceInSeconds(true);
    	er = tsdbSub.newExpressionResult(); 
    	gcNames = new HashSet<ObjectName>(Arrays.asList(JMXHelper.query(mbeanServer, ManagementFactory.GARBAGE_COLLECTOR_MXBEAN_DOMAIN_TYPE + ",*", null)));
    	memPoolNames = new HashSet<ObjectName>(Arrays.asList(JMXHelper.query(mbeanServer, ManagementFactory.MEMORY_POOL_MXBEAN_DOMAIN_TYPE + ",*", null)));    	
    	targetObjectNames = JMXHelper.getMBeanAttributeMap(mbeanServer, JMXHelper.objectName("java.*:*"), "/");
//    	for(Map.Entry<ObjectName, Map<String, Object>> entry: targetObjectNames.entrySet()) {
//    		final ObjectName on = entry.getKey();
//    		final Set<String> numericAttrs = new HashSet<String>();
//    		for(Map.Entry<String, Object> data : entry.getValue().entrySet()) {
//    			final String key = data.getKey();
//    			final Object value = data.getValue();
//    			if(value==null) continue;
//    			if(Number.class.isAssignableFrom(value.getClass())) {
//    				numericAttrs.add(key.split("/")[0]);
//    			}
//    		}
//    		CacheService.getInstance().put(agentId + on, numericAttrs.toArray(new String[numericAttrs.size()]));
//    	}
    	log.info("Loaded [{}]", jmxUrl);
    }
    
    public void flush() {
    	er.deepFlush();
    }
    
    public void traceJVMStats() {
    	traceBasicNumerics(agentId, mbeanServer, er, CLASSLOADING_ON);
    	traceBasicNumerics(agentId, mbeanServer, er, COMPILATION_ON);
    	traceBasicNumerics(agentId, mbeanServer, er, MEMORY_ON);
    	for(ObjectName on: gcNames) {
    		traceBasicNumerics(agentId, mbeanServer, er, on);
    	}
    	for(ObjectName on: memPoolNames) {
    		traceBasicNumerics(agentId, mbeanServer, er, on);
    	}
    	
    	flush();
    }
    
    public void close() {    
    	try { jmxConn.close(); log.info("Closed JMXConnection"); } catch (Exception x) {/* No Op */}
    	try { tsdbConn.close(); log.info("Closed TSDBSubmitterConnection"); } catch (Exception x) {/* No Op */}    	
    }
    
    static final List<String> REF_VAR = Collections.unmodifiableList(new ArrayList<String>(0));
    static final Map<String, String> MAP_REF_VAR = Collections.unmodifiableMap(new HashMap<String, String>(0));
    
    
    
    @SuppressWarnings("unchecked")
	public static Map<String, String> getNumericAttrs(final String agentId, final MBeanServerConnection mbeanServer, final ObjectName on) {
    	return CacheService.getInstance().get(agentId + on, MAP_REF_VAR.getClass(), new Callable<Map<String, String>>() {
    		@Override
    		public Map<String, String> call() throws Exception {    	
    			Map<String, String> names = new HashMap<String, String>();
    			for(Map.Entry<String, Object> entry: JMXHelper.getMBeanAttributeMap(mbeanServer,on, "/").get(on).entrySet()) {
	    			final String key = entry.getKey();
	    			final Object value = entry.getValue();
	    			if(value==null) continue;
	    			if(Number.class.isAssignableFrom(value.getClass())) {
	    				names.put(key.split("/")[0], key);
	    			}
    				
    			}
    			System.out.println("\n\tATTRS for [" + on + "/" + agentId + "]:" + names.toString());
    			return names;
    		}
    	}); 
    }
    
    
    public static final String BASIC_NUMERIC_JMX_ATTRS = "foreach(sk:lk) {domain}.{key:type}.{sk}::{allkeys}->{attr:{lk}}";
    
    public static void traceBasicNumerics(final String agentId, final MBeanServerConnection mbeanServer, final ExpressionResult er, final ObjectName on) {
    	Map<String, String> jkeys = getNumericAttrs(agentId, mbeanServer, on);    	
    	final Map<String, Object> attrs = JMXHelper.getMBeanAttributeMap(mbeanServer, on, "/", jkeys.keySet().toArray(new String[jkeys.size()])).get(on);
    	ExpressionProcessor ep = ExpressionCompiler.getInstance().get(BASIC_NUMERIC_JMX_ATTRS, er);
    	ep.process(agentId, attrs, on, jkeys);    	
    }
    
    
}



//import java.util.regex.*;
//
//Pattern NAME_SEGMENT = Pattern.compile('^(.*?):(.*?)$');
//
//Pattern ATTRIBUTE_SPEC = Pattern.compile('^\\{attr\\[.*?\\]\\}$');
//Pattern DOMAIN = Pattern.compile("\\{domain\\}", Pattern.CASE_INSENSITIVE);
//Pattern KEY_SPEC = Pattern.compile('^\\{key\\[.*?\\]\\}$');
//
////Pattern SPLIT_SPEC = Pattern.compile("(?:^|\\s)\\{([^\\{]:)}(?:\$|\\s)", Pattern.MULTILINE);
////Pattern SPLIT_SPEC = Pattern.compile("(?=[^\\{]*(?:}))", Pattern.MULTILINE);
//Pattern SPLIT_SPEC = Pattern.compile(":(?:^\\{.*?:.*?})", Pattern.MULTILINE);
//
//Pattern QT_SPLIT_SPEC = Pattern.compile("(?:^|\\s)'([^']*?)'(?:\$|\\s)", Pattern.MULTILINE);
//
//nameSegments = [
//    "java.lang.gc:{key:type}{key:name}",
//    "   java.lang.gc   : {key:type}{key:name}  ",
//    "java.lang.gc.{attr:Foo}:{key:type}{key:name}",
//]
//
//samples = [
//        "'Tumblr' is an amazing app",
//        "Tumblr is an amazing 'app'",
//        "Tumblr is an 'amazing' app",
//        "Tumblr is 'awesome' and 'amazing' ",
//        "Tumblr's users' are disappointed ",
//        "Tumblr's 'acquisition' complete but users' loyalty doubtful"
//]
//
//
//samples.each() { 
//    println "SPLIT: ${QT_SPLIT_SPEC.split(it)}";
//}
//
//println "\n========================================================\n";
//
//nameSegments.each() { 
//    println "SPLIT: ${SPLIT_SPEC.split(it)}";
//}
//
//
///*
//nameSegments.each() {
//    println "Testing [$it]";
//    m = NAME_SEGMENT.matcher(it.replace(" ", "").replace("=", ""));
//    if(m.matches()) {
//        println "\tMatched: [${m.group(1)}] : [${m.group(2)}]";
//        
//    } else {
//        println "\tNO MATCH";
//    }
//}
//*/
//
//
//
//
//
//return null;
