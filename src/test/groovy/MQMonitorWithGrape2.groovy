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

//==================================================================================
//      Constants
//==================================================================================
def int[] QUEUE_STATUS_ATTRS = [
    CMQC.MQCA_Q_NAME, CMQC.MQIA_CURRENT_Q_DEPTH,  
    CMQC.MQIA_OPEN_INPUT_COUNT, CMQC.MQIA_OPEN_OUTPUT_COUNT, 
    CMQCFC.MQIACF_UNCOMMITTED_MSGS,  CMQCFC.MQIACF_OLDEST_MSG_AGE
];
def CHANNEL_TYPES = [1:"Sender", 2:"Server", 3:"Receiver", 4:"Requester", 6:"Client Connection", 7:"Server Connection", 8:"Cluster Receiver", 9:"Cluster Sender"];
def CHANNEL_STATUSES = [0:"Inactive", 1:"Binding", 2:"Starting", 3:"Running", 4:"Stopping", 5:"Retrying", 6:"Stopped", 7:"Requesting", 8:"Paused", 13:"Initializing"];
def SERVICE_STATUSES = [0:"Stopped", 1:"Starting", 2:"Running", 3:"Stopping", 4:"Retrying"];
def MQ_STATUSES = [1:"Starting", 2:"Running", 3:"Quiescing"];

def SKIP_QUEUE = Pattern.compile("SYSTEM\\..*||AMQ\\..*");
def SDF = new SimpleDateFormat("yyyy-MM-dd HH.mm.ss");

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




//==================================================================================
//          Utility Methods
//==================================================================================
public int perc(part, total) {
    if(part<1 || total <1) return 0;
    return part/total*100;
}
public String channelType(int id){
    return CHANNEL_TYPES.get(id);
}
public String channelStatus(int id){
    return CHANNEL_STATUSES.get(id);
}
public String serviceStatus(int id){
    return SERVICE_STATUSES.get(id);
}
public String mqStatus(int id){
    return MQ_STATUSES.get(id);
}
xtractTimes = { map ->
    def tkeys = new HashSet();
    def dkeys = new HashSet();
    map.each() { k, v ->
        if(k!=null && k.endsWith("_TIME")) tkeys.add(k.replace("_TIME", ""));
        if(k!=null && k.endsWith("_DATE")) dkeys.add(k.replace("_DATE", ""));
    }
    tkeys.retainAll(dkeys);
    dkeys.retainAll(tkeys);
    def akeys = new HashSet(tkeys);
    akeys.addAll(dkeys);
    akeys.each() { 
        d = "${it}_DATE".toString(); t = "${it}_TIME".toString();
        dt = SDF.parse("${map.get(d).trim()} ${map.get(t).trim()}");
        map.put("${it}_TS", dt);
    }
    return map;
}




public class IntOrStringMap {
    final Map<Integer, Object> byInt;
    final Map<String, Object> byStr;
    def SDF = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");    
    
    IntOrStringMap(result) {
        def pcfList = result.toList();
        byInt = new LinkedHashMap<Integer, Object>(pcfList.size());
        byStr = new LinkedHashMap<String, Object>(pcfList.size());
        pcfList.each() { pcfParam ->
            byInt.put(pcfParam.getParameter(), pcfParam.getValue());
            byStr.put(pcfParam.getParameterName(), pcfParam.getValue());
        }
        xtractTimes(byStr);
    }
    
    public Object put(key, value) {
        if(key!=null) {
            if(key instanceof Number) {
                byInt.put(key, value);
            } else {
                byStr.put(key.toString(), value);
            }
        }
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
    
    
    
    public xtractTimes(map) {
        def tkeys = new HashSet();
        def dkeys = new HashSet();
        def xmap = (map instanceof IntOrStringMap) ? map.byStr() : map;
        xmap.each() { k, v ->
            if(k!=null && k.endsWith("_TIME")) tkeys.add(k.replace("_TIME", ""));
            if(k!=null && k.endsWith("_DATE")) dkeys.add(k.replace("_DATE", ""));
        }
        tkeys.retainAll(dkeys);
        dkeys.retainAll(tkeys);
        def akeys = new HashSet(tkeys);
        akeys.addAll(dkeys);
        akeys.each() { 
            def d = "${it}_DATE".toString(); 
            def t = "${it}_TIME".toString();
            if(xmap.get(t)!=null && xmap.get(d)!=null) {
                String strTs = "${xmap.get(d).trim()} ${xmap.get(t).trim().replace('.', ':')}".toString();
                if(!strTs.trim().isEmpty()) {
                    try {                    
                        def dt = SDF.parse(strTs);
                        xmap.put("${it}_TS", dt);
                    } catch (e) {
                        println "ERR Parsing TS: d:[${xmap.get(d).trim()}], t:[${xmap.get(t).trim()}]  ---> [$strTs]:  $e";
                    }
                }
            }
        }
        return xmap;
    }    
    
    public byInt() { return byInt; }
    public byStr() { return byStr; }
    //public leftShift(a) { println "LS: ${a.getClass().getName()}"; return byStr; }
    
}



public List request(agent, type, parameters) {
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
    agent.send(request).each() {
        responses.add(new IntOrStringMap(it.getParameters()));
    }
    return responses;
}

public int[] getTopicPubSubCounts(topicString, pcf) {
    int[] pubSubCounts = new int[2];
    request(pcf, CMQCFC.MQCMD_INQUIRE_TOPIC_STATUS, [(CMQC.MQCA_TOPIC_STRING):topicString]).each() {
        pubSubCounts[0] = it.get(CMQC.MQIA_PUB_COUNT);
        pubSubCounts[1] = it.get(CMQC.MQIA_SUB_COUNT);
    }
    return pubSubCounts;
}



//==================================================================================
//          Instance Vars
//==================================================================================
def pcf = null;
def qManager = null;


//==================================================================================

try {
    pcf = new PCFMessageAgent("mqserver", 1430, "JBOSS.SVRCONN")
    qManager = pcf.getQManagerName();
    println "Connected to $qManager";


    QMGR_META.putAll(request(pcf, CMQCFC.MQCMD_INQUIRE_Q_MGR, []).get(0).byStr());
    xtractTimes(QMGR_META);

    request(pcf, CMQCFC.MQCMD_INQUIRE_CHANNEL_NAMES, [(MQConstants.MQCACH_CHANNEL_NAME):"*"]).each() {
        it.get("MQCACH_CHANNEL_NAMES").each() { chname ->
            CHANNEL_NAMES.add(chname);
            TCHANNEL_NAMES.add(chname.trim());
        }
    }
    println "Cached ${TCHANNEL_NAMES.size()} Channel Names"

    request(pcf, CMQCFC.MQCMD_INQUIRE_Q_NAMES, [(CMQC.MQCA_Q_NAME):"*"]).each() {
        it.get("MQCACF_Q_NAMES").each() { qname ->
            if(!SKIP_QUEUE.matcher(qname).matches()) {
                QUEUE_NAMES.add(qname);
                TQUEUE_NAMES.add(qname.trim());
            }
        }
    }
    println "Cached ${QUEUE_NAMES.size()} Queue Names";
    request(pcf, CMQCFC.MQCMD_INQUIRE_Q, [(CMQC.MQCA_Q_NAME):"*", (CMQCFC.MQIACF_Q_ATTRS):[CMQCFC.MQIACF_ALL] as int[]]).each() {
        qname = it.get('MQCA_Q_NAME');
        if(QUEUE_NAMES.contains(qname)) {
                xtractTimes(it);
                QUEUE_META.put(qname, it);
                TQUEUE_META.put(qname.trim(), it);
        }
    }
    println "Cached ${QUEUE_META.size()} Queue Metadata Entries";
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
        tname = it.get("MQCA_TOPIC_NAME");
        tsname = it.get("MQCA_TOPIC_STRING");
        if(!tname.startsWith("SYSTEM.")) {
            TOPIC_META.put(tname, it);
            TTOPIC_META.put(tname.trim(), it);       
                 
            int[] counts = getTopicPubSubCounts(tsname, pcf);
            //println "Topic ${tname.trim()} / $tsname: Pubs: ${counts[0]}, Subs: ${counts[1]}";
            if(counts[1] > 0) {
                subs = [:];
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
                    smap = sub.byStr();
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
    println "Cached ${TOPIC_META.size()} Topic Metas";
    int cnt = 0;
    names = [];
    SUB_META.each() {k,v ->
        cnt += v.size();        
        v.keySet().each() {
            names.add("$k:$it");
            println v.get(it);
        }
    }
    println "Cached $cnt Subscription Metas: $names";
    println TSUBQUEUE_NAMES;

    // SUB_META  -->   Map<TopicName, Map<SubName, Meta>>
    //             request(false, pcf, CMQCFC.MQCMD_INQUIRE_TOPIC_STATUS, [(CMQC.MQCA_TOPIC_STRING):topicName.trim(), (CMQCFC.MQIACF_TOPIC_STATUS_TYPE):CMQCFC.MQIACF_TOPIC_SUB]).each() { sub ->
    
    

} finally {
    if(pcf!=null) try { pcf.disconnect(); println "PCF Disconnected"; } catch (e) {
        e.printStackTrace(System.err);
    }
}    


return null;