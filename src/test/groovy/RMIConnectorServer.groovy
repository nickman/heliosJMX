import javax.management.remote.*;
import java.lang.management.*;
import java.rmi.registry.*;

mbeanServer = ManagementFactory.getPlatformMBeanServer();

surl = new JMXServiceURL("service:jmx:rmi://localhost:8003/jndi/rmi://localhost:8004/jmxrmi");
server = JMXConnectorServerFactory.newJMXConnectorServer(surl, null, mbeanServer);

LocateRegistry.createRegistry(8004);


server.start();
