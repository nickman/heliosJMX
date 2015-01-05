package com.heliosapm.jmx.expr;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
import com.heliosapm.opentsdb.TSDBSubmitter;
import com.heliosapm.opentsdb.TSDBSubmitterConnection;
import com.heliosapm.opentsdb.TSDBSubmitterImpl.ExpressionResult;

class ExprTest {
	/** Static class logger */
	protected static final Logger log = LoggerFactory.getLogger(ExprTest.class);


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
    	ExprTest etHbase = new ExprTest("service:jmx:attach:///[.*HMaster.*]", "hserval", "HBaseMaster");
    	etHbase.traceJVMStats();
    	ExprTest etLocal = new ExprTest("service:jmx:local:///DefaultDomain", "hserval", "ExprTest");
    	etLocal.traceJVMStats();
    	System.exit(-1);
    	
    }
    
    
    private final JMXConnector jmxConn;
    private final TSDBSubmitterConnection tsdbConn;
    private final TSDBSubmitter tsdbSub;    
    private final ExpressionResult er;
    private MBeanServerConnection mbeanServer = null;
    private String agentId = null;
    private final Set<ObjectName> gcNames;
    private final Set<ObjectName> memPoolNames;
    
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
    }
    
    public void flush() {
    	er.deepFlush();
    }
    
    public void traceJVMStats() {
    	traceBasicNumerics(agentId, mbeanServer, er, CLASSLOADING_ON);
    	traceBasicNumerics(agentId, mbeanServer, er, COMPILATION_ON);
    	for(ObjectName on: gcNames) {
    		traceBasicNumerics(agentId, mbeanServer, er, on);
    	}
    	
    	flush();
    }
    
    public void close() {    
    	try { jmxConn.close(); log.info("Closed JMXConnection"); } catch (Exception x) {/* No Op */}
    	try { tsdbConn.close(); log.info("Closed TSDBSubmitterConnection"); } catch (Exception x) {/* No Op */}    	
    }
    
    static final List<String> REF_VAR = Collections.unmodifiableList(new ArrayList<String>(0));
    
    static final ObjectName CLASSLOADING_ON = JMXHelper.objectName("java.lang:type=ClassLoading");
    
    static final ObjectName COMPILATION_ON = JMXHelper.objectName("java.lang:type=Compilation");
    static final String[] COMPILATION_ATTRS = {"TotalCompilationTime"};
    static final List<String> COMPILATION_ATTR_LIST = Collections.unmodifiableList(new ArrayList<String>(Arrays.asList(COMPILATION_ATTRS)));
    
    
    public static String[] getNumericAttrs(final String agentId, final MBeanServerConnection mbeanServer, final ObjectName on) {
    	return CacheService.getInstance().get(agentId + on, String[].class, new Callable<String[]>() {
    		@Override
    		public String[] call() throws Exception {    			
    			return JMXHelper.getNumericAttributeNames(on, mbeanServer);
    		}
    	}); 
    }
    
    @SuppressWarnings("unchecked")
	public static List<String> getNumericAttrList(final String agentId, final MBeanServerConnection mbeanServer, final ObjectName on) {    	
    	return CacheService.getInstance().get(agentId + on + "List", REF_VAR.getClass(), new Callable<List<String>>() {
    		@Override
    		public List<String> call() throws Exception {    			
    			return new ArrayList<String>(Arrays.asList(getNumericAttrs(agentId, mbeanServer, on)));
    		}
    	}); 
    }
    
    
    public static void traceBasicNumerics(final String agentId, final MBeanServerConnection mbeanServer, final ExpressionResult er, final ObjectName on) {    	    	
    	final Map<String, Object> attrs = JMXHelper.getAttributes(on, mbeanServer, getNumericAttrs(agentId, mbeanServer, on));
    	ExpressionProcessor ep = ExpressionCompiler.getInstance().get("foreach(clAttr) {domain}.classload.{clAttr}::{allkeys}->{attr:{clAttr}}", er);
    	ep.process(agentId, attrs, on, getNumericAttrList(agentId, mbeanServer, on));    	
    }
    
    public static void traceCompilation(final String agentId, final MBeanServerConnection mbeanServer, final ExpressionResult er) {
    	final Map<String, Object> attrs = JMXHelper.getAttributes(COMPILATION_ON, mbeanServer, COMPILATION_ATTRS);
    	ExpressionProcessor ep = ExpressionCompiler.getInstance().get("foreach(clAttr) {domain}.compilation.{clAttr}::{allkeys}->{attr:{clAttr}}", er);
    	ep.process(agentId, attrs, COMPILATION_ON, COMPILATION_ATTR_LIST);    	
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
