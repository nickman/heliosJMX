import javax.management.*;
import javax.management.remote.*;
import java.lang.management.*;
import java.rmi.registry.*;
import com.heliosapm.jmx.batch.*;
 
class ConnectionNotificationListener implements NotificationListener, NotificationFilter {
    public void handleNotification(Notification notification, Object handback) {
        println "\tConnection Event [${notification.getConnectionId()}]: ${notification.getType()}";
    }
    public boolean isNotificationEnabled(Notification notification) {
        return (notification instanceof JMXConnectionNotification);
    }
}

server = null;
mbeanServer = null;
on = null;
try {
    mbeanServer = ManagementFactory.getPlatformMBeanServer();
    pid = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
    surl = new JMXServiceURL("service:jmx:jmxmp://0.0.0.0:8006");
    on = new ObjectName("jmx.remoting:service=JMXConnectorServer,protocol=JMXMP,port=8006");
    bulkService = new ObjectName("com.heliosapm.jmx:service=BulkJMXService");
    server = JMXConnectorServerFactory.newJMXConnectorServer(surl, null, mbeanServer);
    mbeanServer.registerMBean(server, on);
    if(!mbeanServer.isRegistered(bulkService)) {
        BulkJMXService.getInstance();
    }
    if(mbeanServer.isRegistered(bulkService)) {
        println "BulkJMXService registered at $bulkService";
    }
    
    listener = new ConnectionNotificationListener();
    mbeanServer.addNotificationListener(on, listener, listener, null);
    server.start();
    println "JMXMP ConnectorServer started on [$surl]. PID: $pid";
    Thread.currentThread().join();
} catch (e) {
    try { server.stop(); } catch (ex) {} 
    try { mbeanServer.unregisterMBean(on); } catch (ex) {}
}    
