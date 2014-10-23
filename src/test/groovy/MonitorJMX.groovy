import java.util.concurrent.*;
import org.jboss.netty.buffer.*;
import java.nio.charset.*;
import javax.management.*;
import javax.management.remote.*;
import com.heliosapm.jmx.util.helpers.*;
import com.heliosapm.opentsdb.*;


// MAX FQNID:  128525

class Config {
    static final String tsdbHost = "pdk-pt-cetsd-01";
    static final int tsdbPort = 8080;
    static final Charset CHARSET = Charset.forName("UTF-8");
    static final BASETAGS = ["host":"pdk-pt-ceas-01", "app":"ECS"];
    //static final JMXServiceURL jmxUrl = new JMXServiceURL("service:jmx:tunnel://pdk-pt-ceas-01:18088/ssh/jmxmp:");
    static final JMXServiceURL jmxUrl = new JMXServiceURL("service:jmx:tunnel://njwmintx:8006/ssh/jmxmp:");
    static final ObjectName BATCHSERVICE = new ObjectName("com.cpex.ecs.gateway.jmx:service=BulkJMXService");
}


jmxConnector = null;
tsdbSubmitter = null;
try {
    tsdbSubmitter = new TSDBSubmitter(Config.tsdbHost, Config.tsdbPort).setTracingDisabled(true).setLogTraces(true).connect();
    Map<String, Script>  execScripts = new HashMap<String, Script>();
    bindings = new Binding();
    jmxConnector = JMXConnectorFactory.connect(Config.jmxUrl);
    println "Connected to $Config.jmxUrl"
    mbeanServer = jmxConnector.getMBeanServerConnection();
    runtime = mbeanServer.getAttribute(JMXHelper.objectName("java.lang:type=Runtime"), "Name");
    println "\n\t=======================\n\tConnect to $runtime\n\t=======================";
    Map<ObjectName, Map<String, Object>> map = mbeanServer.invoke(Config.BATCHSERVICE, "getAttributes", [[".*"], [JMXHelper.objectName("*:*")] as ObjectName[]] as Object[], [List.class.getName(), ObjectName[].class.getName()] as String[]);
    println "Retrieved ${map.size()} ObjectName Attribute Sets";
    map.each() { ok, ov ->
        println "\t[$ok]";
        ov.each() { k, v ->
            //println "\t\t[$k] : [$v]";
        }
    }
    tsdbSubmitter.trace(map);
    tsdbSubmitter.flush();
} finally {
    try { jmxConnector.close(); } catch (e) {}
    try { tsdbSubmitter.close(); } catch (e) {}
}


runExecs = {
    myDir = new File(getClass().protectionDomain.codeSource.location.path).parent;
    scriptDir = new File(myDir, "scripts");
    gs = new GroovyShell();
    scriptDir.listFiles().each() {
        if(it.getName().endsWith(".groovy")) {
            println "Compiling Execlet [$it]....";
            exeScript = gs.parse(it);
            exeScript.setBinding(bindings);
            execScripts.put(it.getAbsolutePath(), exeScript);
        }
    }
}




toLong = { excludes, map ->
    lmap = [:];
    keys = map.keySet();
    keys.each() { k ->
        if(!excludes.contains(k)) {
            try {
                //println "Cleaning [$k]";
                v = new Double(map.get(k).toString());
                lmap.put(k, (long)v);
            } catch (e) {
                excludes.add(k);
                println "Adding Exclude: $k";
            }
        }
    }
    return lmap;
}

clean = { on, key ->
    v = on.getKeyProperty(key);
    index = v.indexOf("/");
    if(index!=-1) {
        v = v.substring(index+1);
    }
    return v.replace(" ", "_");
}



bindings.setProperty("clean", clean);
//runExecs();