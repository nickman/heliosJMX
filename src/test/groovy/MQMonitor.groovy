@Grab(group='com.ibm.mq', module='pcf', version='7.0.1.4')
@Grab(group='com.ibm.mq', module='headers', version='7.0.1.4')
@Grab(group='com.ibm.mq', module='jmqi', version='7.0.1.4')
@Grab(group='com.ibm.mq', module='com.ibm.mq', version='7.0.1.4')
@Grab(group='com.ibm.mq', module='commonservices', version='7.0.1.4')
@Grab(group='com.ibm.mq', module='connector', version='7.0.1.4')

import com.ibm.mq.constants.MQConstants;
import static com.ibm.mq.constants.MQConstants.*;
import com.ibm.mq.pcf.*;
import static com.ibm.mq.pcf.CMQC.*;
import java.util.regex.*;
import java.text.SimpleDateFormat;
import java.util.concurrent.atomic.*;
import groovy.transform.*;

@CompileStatic
public class IntOrStringMap {
    final Map<Integer, Object> byInt;
    final Map<String, Object> byStr;
    final SimpleDateFormat SDF = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");    
    
    IntOrStringMap(Enumeration<PCFParameter> result) {
    	this(result, false);
    }

    IntOrStringMap(Enumeration<PCFParameter> result, boolean trim) {
        List<PCFParameter> pcfList = result.toList();
        byInt = new LinkedHashMap<Integer, Object>(pcfList.size());
        byStr = new LinkedHashMap<String, Object>(pcfList.size());
        Set<String> possibleTimes = new HashSet<String>();
        Set<String> possibleDates = new HashSet<String>();
        pcfList.each() { pcfParam ->
        	String key = pcfParam.getParameterName();
        	Object value = pcfParam.getValue();
        	if(trim && value!=null && (value instanceof CharSequence)) {
        		value = value.toString().trim();
        	}
        	if(key!=null) {
        		if(key.endsWith("_DATE")) possibleDates.add(key.replace("_DATE", ""));
        		if(key.endsWith("_TIME")) possibleTimes.add(key.replace("_TIME", ""));
        	} else {
        		key = "${pcfParam.getParameter()}".toString();
        	}
            byInt.put(pcfParam.getParameter(), value);
            if(trim) {
            	byStr.put(key.trim(), value);
        	} else {
        		byStr.put(key, value);
        	}            
        }
        possibleTimes.retainAll(possibleDates);
        possibleDates.retainAll(possibleTimes);
        Set<String> akeys = new HashSet<String>(possibleTimes);
        akeys.addAll(possibleDates);
        akeys.each() { 
            String d = "${it}_DATE".toString(); 
            String t = "${it}_TIME".toString();
            if(byStr.get(t)!=null && byStr.get(d)!=null) {
                String strTs = "${byStr.get(d).toString().trim()} ${byStr.get(t).toString().trim().replace('.', ':')}".toString();
                if(!strTs.trim().isEmpty()) {
                    try {                    
                        Date dt = SDF.parse(strTs);
                        byStr.put("${it}_TS".toString(), dt.getTime());
                    } catch (e) {
                        println "ERR Parsing TS: [$strTs] :  $e";
                    }
                }
            }
        }
    }
    
    public Object put(key, value) {
        if(key!=null) {
            if(key instanceof Number) {
                return byInt.put(((Number)key).intValue(), value);
            } else {
                return byStr.put(key.toString(), value);
            }
        }
        return null;
    }
    
    public Object get(key) {
        if(key==null) return byStr.get(key);
        if(key instanceof Number) return byInt.get(key);
        return byStr.get(key.toString());
    }
    
    public Map each(Closure c) {
        byStr.each() { k, v ->
            c.call(k,v);
        }
        return byStr;
    }
    
    public Map eachByInt(Closure c) {
        byInt.each() { k, v ->
            c.call(k,v);
        }
        return byInt;
    }
    
    public String toString() {
        def b = new StringBuilder();
        byStr.each() { k, v ->
            if(k!=null) {
                String a = "\n\t[${k.trim()}] : [$v]".toString();
                b.append(a);
            }
        }
        return b.toString();
    }
    
        
    public Map<Integer, Object> byInt() { return byInt; }
    public Map<String, Object> byStr() { return byStr; }
    //public leftShift(a) { println "LS: ${a.getClass().getName()}"; return byStr; }
    
}

public class MQMonitorService implements Closeable {
	//==================================================================================
	//      Constants
	//==================================================================================
	/** Queue Status Attributes */
	static final int[] QUEUE_STATUS_ATTRS = [
	    CMQC.MQCA_Q_NAME, CMQC.MQIA_CURRENT_Q_DEPTH,  
	    CMQC.MQIA_OPEN_INPUT_COUNT, CMQC.MQIA_OPEN_OUTPUT_COUNT, 
	    CMQCFC.MQIACF_UNCOMMITTED_MSGS,  CMQCFC.MQIACF_OLDEST_MSG_AGE
	];
	/** Channel Type Decodes */
	static final Map CHANNEL_TYPES = [1:"Sender", 2:"Server", 3:"Receiver", 4:"Requester", 6:"Client Connection", 7:"Server Connection", 8:"Cluster Receiver", 9:"Cluster Sender"];
	/** Channel Status Decodes */
	static final Map CHANNEL_STATUSES = [0:"Inactive", 1:"Binding", 2:"Starting", 3:"Running", 4:"Stopping", 5:"Retrying", 6:"Stopped", 7:"Requesting", 8:"Paused", 13:"Initializing"];
	/** Service Status Decodes */
	static final Map SERVICE_STATUSES = [0:"Stopped", 1:"Starting", 2:"Running", 3:"Stopping", 4:"Retrying"];
	/** QM Status Decodes */
	static final Map MQ_STATUSES = [1:"Starting", 2:"Running", 3:"Quiescing"];
	/** Queue Name Patterns to Skip */
	static final Pattern SKIP_QUEUE = Pattern.compile("SYSTEM\\..*||AMQ\\..*");
	
	public static int perc(part, total) {
	    if(part<1 || total <1) return 0;
	    return part/total*100;
	}
	public static String channelType(int id){
	    return CHANNEL_TYPES.get(id);
	}
	public static String channelStatus(int id){
	    return CHANNEL_STATUSES.get(id);
	}
	public static String serviceStatus(int id){
	    return SERVICE_STATUSES.get(id);
	}
	public static String mqStatus(int id){
	    return MQ_STATUSES.get(id);
	}
	public static int retries = 0;


	public static List<IntOrStringMap> request(agent, type, parameters, trim) {
	    def responses = [];
	    def PCFMessage request = new PCFMessage(type);
	    if(parameters.getClass().isArray()) {
	        parameters.each() { param ->
	            request.addParameter(param);
	        }
	    } else {
	        parameters.each() { name, value ->
	            request.addParameter(name, value);
	        }
	    }
	    Exception lastException = null;
	    for(i in 0..5) {
	    	try {
		    	agent.send(request).each() {
		        	responses.add(new IntOrStringMap(it.getParameters(), trim));
		    	}
		    	if(i>0) retries++;
		    	return responses;
		    } catch (com.ibm.mq.MQException mqx) {
		    	lastException = mqx;
		    	responses.clear();
		    	if(2033 != mqx.getReason()) {
		    		throw mqx;
		    	}
		    	Thread.sleep(500);
		    }	    	
	    }
	    throw lastException;
	}

	public static List<IntOrStringMap> request(agent, type, parameters) {
		return request(agent, type, parameters, false);
	}


	public static int[] getTopicPubSubCounts(topicString, pcf) {
	    int[] pubSubCounts = new int[2];
	    request(pcf, CMQCFC.MQCMD_INQUIRE_TOPIC_STATUS, [(CMQC.MQCA_TOPIC_STRING):topicString.trim()]).each() {
	        pubSubCounts[0] = it.get(CMQC.MQIA_PUB_COUNT);
	        pubSubCounts[1] = it.get(CMQC.MQIA_SUB_COUNT);
	    }
	    return pubSubCounts;
	}




	//==================================================================================
	//      Instance Vars
	//==================================================================================
	/** Connection Channel name */
	String _channelName = null;
	/** Connection host */
	String _host = null;
	/** Connection port */
	int _port = -1;
	/** The PCF Agent */
	PCFMessageAgent pcf = null;
	/** The name of the QM we're connected to */
	String queueManagerName = null;
	/** PCF Date Format based date format */
	SimpleDateFormat SDF = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

	//==================================================================================
	//          Meta-Data Cache
	//==================================================================================
	/** QueueManager MetaData */
	def QMGR_META = [:];

	/** Full Channel Names */
	def CHANNEL_NAMES = new HashSet();
	/** Trimmed Channel Names */
	def TCHANNEL_NAMES = new HashSet();

	/** Channel MetaData keyed by full Channel Name */
	def CHANNEL_META = [:];
	/** Channel MetaData keyed by trimmed Channel Name */
	def TCHANNEL_META = [:];

	/** Full Queue Names */
	def QUEUE_NAMES = new HashSet();
	/** Trimmed Queue Names */
	def TQUEUE_NAMES = new HashSet();

	/** Full Subscription Queue Names keyed by Subscription Name */
	def SUBQUEUE_NAMES = [:];
	/** Trimmed Subscription Queue Names keyed by Subscription Name */
	def TSUBQUEUE_NAMES = [:];


	/** Full Topic Names */
	def TOPIC_NAMES = new HashSet();
	/** Trimmed Topic Names */
	def TTOPIC_NAMES = new HashSet();

	/** Queue MetaData keyed by full Queue Name */
	def QUEUE_META = [:];
	/** Queue MetaData keyed by trimmed Queue Name */
	def TQUEUE_META = [:];
	/** Topic MetaData keyed by full Topic Name */
	def TOPIC_META = [:];
	/** Topic MetaData keyed by trimmed Topic Name */
	def TTOPIC_META = [:];

	/** Topic subscriptions keyed by subscription name, keyed by topic name */
	def SUB_META = [:];
	/** Topic subscriptions keyed by trimmed subscription name, keyed by trimmed topic name */
	def TSUB_META = [:];

	/** The effective time stamp of the meta-data cache */
	final AtomicLong cacheTimestamp = new AtomicLong((Long.MAX_VALUE/100).longValue());
	/** CAS flag on cache population */
	final AtomicBoolean cachePop = new AtomicBoolean(false);

	/** The meta-cache cache timeout in ms. */ 
	long cacheTimeout = 1000 * 60 * 10;  // 10 minutes

	public MQMonitorService(String host, int port, String channelName) {
		_host = host;
		_port = port;
		_channelName = channelName;
	}

	public MQMonitorService connect() {
		pcf = new PCFMessageAgent(_host, _port, _channelName);
		queueManagerName = pcf.getQManagerName();
		println "Connected: $this";
		popMetaCache();

	}

	public void close() {
		if(pcf!=null) {
			try { pcf.disconnect(); } catch (e) {}
			pcf = null;
		}
		queueManagerName = null;
	}

	public String toString() {
		def b = new StringBuilder("MQMonitorService:");
		if(pcf==null) return b.append(" Disconnected");
		return b.append("$queueManagerName@$_host:$_port($_channelName)").toString();
	}

	void clearCache() {
		QMGR_META.clear();
		CHANNEL_NAMES.clear();
		TCHANNEL_NAMES.clear();
		CHANNEL_META.clear();
		TCHANNEL_META.clear();
		QUEUE_NAMES.clear();
		TQUEUE_NAMES.clear();
		SUBQUEUE_NAMES.clear();
		TSUBQUEUE_NAMES.clear();
		TOPIC_NAMES.clear();
		TTOPIC_NAMES.clear();
		QUEUE_META.clear();
		TQUEUE_META.clear();
		TOPIC_META.clear();
		TTOPIC_META.clear();
		SUB_META.clear();
		TSUB_META.clear();		
	}

	public synchronized void popMetaCache() {
		long now = System.currentTimeMillis();
		if(!cachePop.get() || (cacheTimestamp.get() + cacheTimeout) >= now ) {
			cachePop.set(true);
			cacheTimestamp.set(now);
			clearCache();
			loadCache();
		}
	}

	public void loadCache() {
		long start = System.currentTimeMillis();
		loadQMMeta();
		loadChannelMeta();
		loadQueueMeta();
		loadTopicMeta()

		long elapsed = System.currentTimeMillis() - start;
		println "$this Meta-Cache Loaded in $elapsed ms.";
	}

	void loadQMMeta() {
	    QMGR_META.putAll(request(pcf, CMQCFC.MQCMD_INQUIRE_Q_MGR, []).get(0).byStr());	    
	}

	void loadChannelMeta() {
	    request(pcf, CMQCFC.MQCMD_INQUIRE_CHANNEL_NAMES, [(MQConstants.MQCACH_CHANNEL_NAME):"*"]).each() {
	        it.get("MQCACH_CHANNEL_NAMES").each() { chname ->
	            CHANNEL_NAMES.add(chname);
	            TCHANNEL_NAMES.add(chname.trim());
	        }
	    }
	    println "Cached ${TCHANNEL_NAMES.size()} Channel Names"
	    request(pcf, CMQCFC.MQCMD_INQUIRE_CHANNEL, [(MQConstants.MQCACH_CHANNEL_NAME):"*"]).each() {
	   		String chName = it.get("MQCACH_CHANNEL_NAME");
	    	int chType = it.get("MQIACH_CHANNEL_TYPE");
	    	it.byStr().put("MQIACH_CHANNEL_TYPE", channelType(chType));
	    	CHANNEL_META.put(chName, it);
	    	TCHANNEL_META.put(chName.trim(), it);
	    }
	    println "Cached ${TCHANNEL_META.size()} Channel Metadata Entries"
	    
	}	

	void loadQueueMeta() {
	    request(pcf, CMQCFC.MQCMD_INQUIRE_Q_NAMES, [(CMQC.MQCA_Q_NAME):"*"]).each() {
	        it.get("MQCACF_Q_NAMES").each() { qname ->
	            if("SYSTEM.DEAD.LETTER.QUEUE".equals(qname.trim()) || !SKIP_QUEUE.matcher(qname).matches()) {
	                QUEUE_NAMES.add(qname);
	                TQUEUE_NAMES.add(qname.trim());
	            }
	        }
	    }
	    println "Cached ${QUEUE_NAMES.size()} Queue Names";
	    request(pcf, CMQCFC.MQCMD_INQUIRE_Q, [(CMQC.MQCA_Q_NAME):"*", (CMQCFC.MQIACF_Q_ATTRS):[CMQCFC.MQIACF_ALL] as int[]]).each() {
	        def qname = it.get('MQCA_Q_NAME');
	        if(QUEUE_NAMES.contains(qname)) {
	                QUEUE_META.put(qname, it);
	                TQUEUE_META.put(qname.trim(), it);
	        }
	    }
	    println "Cached ${QUEUE_META.size()} Queue Metadata Entries";		
	}

	void loadTopicMeta() {
	    request(pcf, CMQCFC.MQCMD_INQUIRE_TOPIC_NAMES, [(CMQC.MQCA_TOPIC_NAME):"*"]).each() {
	        it.get("MQCACF_TOPIC_NAMES").each() { tname ->
	            if(!tname.startsWith("SYSTEM.")) {
	                TOPIC_NAMES.add(tname);
	                TTOPIC_NAMES.add(tname.trim());            
	            }
	        }
	    }
	    println "Cached ${TOPIC_NAMES.size()} Topic Names";
	    request(pcf, CMQCFC.MQCMD_INQUIRE_TOPIC, [(CMQC.MQCA_TOPIC_NAME):"*"]).each() {
	        def tname = it.get("MQCA_TOPIC_NAME");
	        def tsname = it.get("MQCA_TOPIC_STRING");
	        if(!tname.startsWith("SYSTEM.")) {
	            TOPIC_META.put(tname, it);
	            TTOPIC_META.put(tname.trim(), it);       
	                 
	            int[] counts = getTopicPubSubCounts(tsname, pcf);
	            //println "Topic ${tname.trim()} / $tsname: Pubs: ${counts[0]}, Subs: ${counts[1]}";
	            if(counts[1] > 0) {
	                def subs = [:];
	                request(pcf, CMQCFC.MQCMD_INQUIRE_TOPIC_STATUS, [(CMQC.MQCA_TOPIC_STRING):tsname, (CMQCFC.MQIACF_TOPIC_STATUS_TYPE):CMQCFC.MQIACF_TOPIC_SUB]).each() { sub ->
	                    byte[] subId = sub.get(CMQCFC.MQBACF_SUB_ID);
	                    String subName = null;
	                    String destName = null;
	                    request(pcf, CMQCFC.MQCMD_INQUIRE_SUBSCRIPTION, [(CMQCFC.MQBACF_SUB_ID):subId]).each() {
	                        subName = it.get("MQCACF_SUB_NAME");
	                        destName = it.get("MQCACF_DESTINATION");
	                        SUBQUEUE_NAMES.put(subName, destName);
	                        TSUBQUEUE_NAMES.put(subName.trim(), destName.trim());
	                        //  SUB Q: MQCACF_DESTINATION] : [SYSTEM.MANAGED.DURABLE.52A89FE520020903         ]
	                        //println it;
	                    }
	                    def smap = sub.byStr();
	                    smap.put("MQBACF_SUB_ID", subId);        
	                    smap.put("MQCACF_DESTINATION", destName);     
	                    if(subName!=null) {
	                        subs.put(subName, smap);            
	                    }
	                }
	                SUB_META.put(tsname, subs);
	            }
	        }
	    }    	    
	    int cnt = 0;
	    SUB_META.each() {k,v ->
	        cnt += v.size();        
	    }
	    println "Cached ${TOPIC_META.size()} Topic Metas and $cnt Topic Subscription Metas";	    
	}

	public IntOrStringMap getQueueManagerStats() {
		return request(pcf, CMQCFC.MQCMD_INQUIRE_Q_MGR_STATUS, null, true).iterator().next();
	}

	public getChannelInitiatorStats() {
		return request(pcf, CMQCFC.MQCMD_INQUIRE_CHANNEL_INIT, [(MQConstants.MQCACF_COMMAND_SCOPE):"*"], true).iterator().next();
	}

	public getListenerStats() { 
		return request(pcf, CMQCFC.MQCMD_INQUIRE_LISTENER, [(MQConstants.MQCACH_LISTENER_NAME):"*"], true).iterator().next();
	}


	

}

for(i in 0..30) {
	MQMonitorService mq = null;
	try {
		mq = new MQMonitorService("localhost", 1430, "JBOSS.SVRCONN");
		mq.connect();
		// QMGR_STATUS -> MQIACF_CONNECTION_COUNT
		// QMGR_META -> MQCA_DEAD_LETTER_Q_NAME, MQCA_Q_MGR_DESC, MQCA_Q_MGR_IDENTIFIER, MQCA_REPOSITORY_NAME
	} catch  (e) {
		e.printStackTrace(System.err);
	} finally {
		if(mq!=null) try { mq.close(); println "MQConn Closed" } catch (e) {}	
	}
}


/*
for(i in 0..30) {

	MQMonitorService mq = null;
	try {
		mq = new MQMonitorService("mqserver", (i%2==0 ? 1430 : 1414), "JBOSS.SVRCONN");
		//mq = new MQMonitorService("mqserver", 1414, "JBOSS.SVRCONN");
		mq.connect();
		//Thread.sleep(120000);
	} catch  (e) {
		e.printStackTrace(System.err);
	} finally {
		if(mq!=null) try { mq.close(); println "MQConn Closed" } catch (e) {}	
	}
}	
println "Successful Retries: ${MQMonitorService.retries}";
*/