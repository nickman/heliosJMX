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
package com.heliosapm.jmx.remote.service;

import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.management.MBeanServerConnection;
import javax.management.MBeanServerInvocationHandler;
import javax.management.ObjectName;
import javax.management.Query;
import javax.management.StringValueExp;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import com.heliosapm.SimpleLogger;
import com.heliosapm.SimpleLogger.SLogger;
import com.heliosapm.jmx.batch.BulkJMXServiceMBean;
import com.heliosapm.jmx.util.helpers.JMXHelper;
import com.heliosapm.opentsdb.TSDBSubmitter;

/**
 * <p>Title: TestClient</p>
 * <p>Description: </p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.jmx.remote.service.TestClient</code></p>
 */

public class TestClient {
	JMXServiceURL serviceURL = null;
	JMXConnector jmxConnector = null;
	MBeanServerConnection conn = null;
	
	static final SLogger LOG = SimpleLogger.logger(TestClient.class);
	
	
	/**
	 * Creates a new TestClient
	 */
	public TestClient(String url) {		
		try {
			serviceURL = new JMXServiceURL(url);
			jmxConnector = JMXConnectorFactory.connect(serviceURL);
			LOG.log("Connected.");
			conn = jmxConnector.getMBeanServerConnection();
			final String runtime = conn.getAttribute(new ObjectName(ManagementFactory.RUNTIME_MXBEAN_NAME), "Name").toString();
			LOG.log("Runtime: [%s]", runtime);
//			final String PID = runtime.split("@")[0];
			
//			final TunnelManager tm = TunnelManager.getInstance();
//			SSHConnectionConfiguration config = SSHConnectionConfiguration.
//					newBuilder("tpsolaris", "nwhitehe")
//					.setUserPassword("mysol!1")
//					.setKeyExchangeTimeout(0)
//					.setVerifyHosts(false)
//					.build();
//			SSHConnectionConfiguration config = SSHConnectionConfiguration.
//					newBuilder("10.12.114.48", "nwhitehe")
//					.setUserPassword("jer1029")
//					.setKeyExchangeTimeout(2000)
//					.setConnectTimeout(2000)
//					.setVerifyHosts(false)
//					.build();
//			
//			LOG.log("SSHConfig:\n%s", config);
//			final String PID = "3766"; //runtime.split("@")[0];
//			ExtendedConnection conn = tm.getConnection(config);
//			conn.fullAuth();
//			LOG.log("Full Authed.");
//			CommandTerminal ct = conn.createCommandTerminal();
//			LOG.log("Command Terminal Created.");
//			StringBuilder[] results = ct.execSplit("ps -ef | grep " + PID + " | grep -v grep");
//			for(StringBuilder s: results) {
//				LOG.log("Command Result:\n\t%s", s);
//			}
			
			jmxConnector.close();
			
		} catch (Exception ex) {
			String msg = LOG.format("Failed to connect client with JMXServiceURL [%s]", url);
			throw new RuntimeException(msg, ex);
		}
	}

	/**
	 * @param args
	 */
	@SuppressWarnings("unused")
	public static void main(String[] args) {
		LOG.log("Test Client");
		//new TestClient("service:jmx:rmi://tpsolaris:8005/jndi/rmi://njwmintx:8009/jmxrmi");
//		String URL_TEMPLATE = "service:jmx:tunnel://%s:%s/ssh/jmxmp:u=%s,p=%s,h=%s,sk=%s";
//		LOG.log("UserName and Pass Test, SSH Host Specified");
//		new TestClient(String.format(URL_TEMPLATE,
//			//8006,"nwhitehe", "mysol!1", "tpsolaris", 8006, false			
//			"tpsolaris", 8006,"nwhitehe", "mysol!1", "tpsolaris", false
//		));
//		TunnelRepository.getInstance().purge();
//		LOG.log("=============================================");
//		URL_TEMPLATE = "service:jmx:tunnel://%s:%s/ssh/jmxmp:u=%s,p=%s,sk=%s";
//		LOG.log("UserName and Pass Test, No SSH Host");
//		new TestClient(String.format(URL_TEMPLATE,
//			//8006,"nwhitehe", "mysol!1", "tpsolaris", 8006, false			
//			"tpsolaris", 8006,"nwhitehe", "mysol!1", false
//		));
//		TunnelRepository.getInstance().purge();
//		LOG.log("=============================================");
//		URL_TEMPLATE = "service:jmx:tunnel://%s:%s/ssh/jmxmp:pref=tpsol";
//		LOG.log("Default ssh props file, User Name, No Passphrase Key");
//		new TestClient(String.format(URL_TEMPLATE,
//			//8006,"nwhitehe", "mysol!1", "tpsolaris", 8006, false			
//			"tpsolaris", 8006
//		));
		
		LOG.log("=============================================");
//		URL sshUrl = URLHelper.toURL("ssh://nwhitehe@pdk-pt-ceas-01:22");
//		LOG.log("SSHURL: [%s], Port: [%s]", sshUrl, sshUrl.getPort());
//		CommandTerminal ct = TunnelRepository.getInstance().openCommandTerminal(sshUrl);
//		LOG.log(ct.exec("ls -l"));
		//new TestClient("service:jmx:tunnel://pdk-pt-ceas-01:17083/ssh/jmxmp:");   //:pr=C:/ProdMonitors/ssh.properties,pref=pdk-ecs,h=pdk-pt-ceas-01,p=XXXX
		JMXConnector connector = null;
		MBeanServerConnection conn = null;
		TSDBSubmitter tsdbSubmitter = null;
//		//JMXMPConnectorServer server  = new JMXMPConnectorServer(ManagementFactory.getPlatformMBeanServer());
//		final ObjectName CLIENT_TIMER = JMXHelper.objectName("com.onexchange.jmx.remote:service=Timer");
		try {
////			JMXConnectorServerFactory.newJMXConnectorServer(new JMXServiceURL("service:jmx:jmxmp://0.0.0.0:8010"), null, ManagementFactory.getPlatformMBeanServer()).start();
//			JMXServiceURL surl = new JMXServiceURL("service:jmx:tunnel://pdk-pt-ceas-01:18088/ssh/jmxmp:");
//			final CountDownLatch latch = new CountDownLatch(1);
////			JMXServiceURL surl = new JMXServiceURL("service:jmx:tunnel://njwmintx:8006/ssh/jmxmp:");
////			JMXServiceURL surl = new JMXServiceURL("service:jmx:tunnel://tpsolaris:8006/ssh/jmxmp:u=nwhitehe,p=mysol!1");
//		    connector = JMXConnectorFactory.connect(surl);
//		    connector.addConnectionNotificationListener(new NotificationListener(){
//		    	/**
//		    	 * {@inheritDoc}
//		    	 * @see javax.management.NotificationListener#handleNotification(javax.management.Notification, java.lang.Object)
//		    	 */
//		    	@Override
//		    	public void handleNotification(Notification notification, Object handback) {
//		    		LOG.loge("Notification at [%s]. Notif:\n%s", new Date(), notification);
//		    		latch.countDown();
//		    	}
//		    }, null, null);
//		    conn = connector.getMBeanServerConnection();
//		    String runtime = conn.getAttribute(new ObjectName(ManagementFactory.RUNTIME_MXBEAN_NAME), "Name").toString();
//		    LOG.log("Connected at [%s]. Runtime: [%s]", new Date(), runtime);
//		    if(!conn.isRegistered(CLIENT_TIMER)) {
//		    	conn.createMBean("javax.management.timer.Timer", CLIENT_TIMER);
//		    	conn.invoke(CLIENT_TIMER, "start", new Object[]{}, new String[]{});
//		    }
//		    TimerMBean timer = MBeanServerInvocationHandler.newProxyInstance(conn, CLIENT_TIMER, TimerMBean.class, true);
//		    int timerId =  	timer.addNotification("jmx.remote.keepalive", "keepalive", null, new Date(System.currentTimeMillis()+10000), 10000, Long.MAX_VALUE, true);
//		    conn.addNotificationListener(CLIENT_TIMER, new NotificationListener(){
//		    	public void handleNotification(Notification n, Object handback) {
//		    		LOG.log("Timer Notif [type: %s, class: %s, msg: %s, seq: %s, src: %s]", 
//		    			n.getType(),
//		    			n.getClass().getName(),
//		    			n.getMessage(),
//		    			n.getSequenceNumber(),
//		    			n.getSource()
//		    		);
//		    	}
//		    }, null, null);
//		    latch.await();
//		    LOG.loge("DISCONNECTED");
//		    Thread.currentThread().join(2000);
//		    LOG.log("Attempting Reconnect....");
//		    try {
//		    	connector.close();
//		    } catch (Exception x) {/* No Op */}
//		    connector = JMXConnectorFactory.connect(surl);
//		    conn = connector.getMBeanServerConnection();
//		    runtime = conn.getAttribute(new ObjectName(ManagementFactory.RUNTIME_MXBEAN_NAME), "Name").toString();
//		    LOG.log("Connected at [%s]. Runtime: [%s]", new Date(), runtime);
			JMXServiceURL jmxUrl = new JMXServiceURL("service:jmx:tunnel://tpsolaris:8006/ssh/jmxmp:k=/home/nwhitehead/.ssh/np_dsa,u=nwhitehe");
//			JMXServiceURL jmxUrl = new JMXServiceURL("service:jmx:tunnel://njwmintx:8006/ssh/jmxmp:");
//			JMXServiceURL jmxUrl = new JMXServiceURL("service:jmx:jmxmp://localhost:8007");
//			JMXServiceURL jmxUrl = new JMXServiceURL("service:jmx:tunnel://pdk-pt-ceas-01:18088/ssh/jmxmp:");
//			JMXServiceURL jmxUrl = new JMXServiceURL("service:jmx:tunnel://pdk-pt-ceas-01:18089/ssh/jmxmp:");
			tsdbSubmitter = new TSDBSubmitter("localhost", 4242).setTracingDisabled(true).setLogTraces(true).connect();
//			tsdbSubmitter = new TSDBSubmitter("opentsdb", 8080).setTracingDisabled(true).setLogTraces(false).connect();
		    connector = JMXConnectorFactory.connect(jmxUrl);
		    conn = connector.getMBeanServerConnection();
		    String runtime = (String)conn.getAttribute(JMXHelper.objectName("java.lang:type=Runtime"), "Name");
		    LOG.log("Connected to [%s] - JMX Domain [%s]", runtime, conn.getDefaultDomain());
		    Set<ObjectName> bulks = conn.queryNames(null, Query.isInstanceOf(new StringValueExp("com.heliosapm.jmx.batch.BulkJMXServiceMBean")));
//		    Set<ObjectName> bulks = conn.queryNames(null, Query.isInstanceOf(new StringValueExp("com.theice.bec.jmx.remoting.BulkJMXService")));
		    
		    if(bulks.isEmpty()) {
		    	throw new RuntimeException("Bulk JMX Service Not Installed");
		    }
		    final ObjectName BULK_ON = bulks.iterator().next(); 
		    BulkJMXServiceMBean bulkService = MBeanServerInvocationHandler.newProxyInstance(conn, BULK_ON, BulkJMXServiceMBean.class, false);
//		    LOG.log("Warmup");
//		    for(int i = 0; i < 1000; i++) {
//		    	bulkService.getAttributes(new ArrayList<String>(Arrays.asList(".*")), JMXHelper.ALL_MBEANS_FILTER);
//		    	if(i>0) if(i%100==0) System.out.print(".");
////		    	if(i%5000==0) System.out.println(".");
//		    }
//		    System.out.println(".");
//		    LOG.log("Warmup Complete");
		    int dcount = conn.getDomains().length;
		    Map<ObjectName, Map<String, Object>> map = new HashMap<ObjectName, Map<String, Object>>(dcount);
		    Map<ObjectName, String[]> attrNames = new HashMap<ObjectName, String[]>(dcount);
		    int totalAttrs = 0;
		    long start = System.currentTimeMillis();
		    for(ObjectName on: conn.queryNames(null, null)) {
		    	String[] names = JMXHelper.getAttributeNames(on, conn);
		    	attrNames.put(on, names);
		    	totalAttrs += names.length;
		    }
		    long elapsed = System.currentTimeMillis() - start;
		    LOG.log("Retrieved [%s] Attribute Names for [%s] MBeans in [%s] ms.", totalAttrs, attrNames.size(), elapsed);
		    totalAttrs = 0;
		    start = System.currentTimeMillis();
		    for(Map.Entry<ObjectName, String[]> entry: attrNames.entrySet()) {
		    	
	    		final ObjectName on = entry.getKey();
	    		final String[] attrs = entry.getValue();		    	
		    	try {
		    		map.put(on, JMXHelper.getAttributes(entry.getKey(), conn, attrs));
		    		totalAttrs += attrs.length;
		    	} catch (Exception x) {
		    		Map<String, Object> av = new HashMap<String, Object>(attrs.length);
		    		Set<String> remaps = new HashSet<String>();
		    		int afails = 0;
		    		for(String s: attrs) {
		    			try {
		    				av.put(s, JMXHelper.getAttribute(conn, s));
		    				remaps.add(s);
		    				totalAttrs++;
		    			} catch (Exception xy) {
		    				afails++;
		    				LOG.loge("Unretrievable Attribute [%s]/[%s]", on, s);
		    			}
		    		}
		    		if(!av.isEmpty()) {
		    			map.put(on, av);
		    		}		    	
		    		if(afails>0) {
		    			entry.setValue(remaps.toArray(new String[remaps.size()]));
		    		}
		    	}
		    }
		    
//		    Map<ObjectName, Map<String, Object>> map = bulkService.getAttributes(new ArrayList<String>(Arrays.asList(".*")), JMXHelper.ALL_MBEANS_FILTER);
		    elapsed = System.currentTimeMillis() - start;
		    LOG.log("Retrieved [%s] Attribute Values for [%s] MBeans in [%s] ms.", totalAttrs, map.size(), elapsed);
//		    for(Map.Entry<ObjectName, Map<String, Object>> entry: map.entrySet()) {
//		    	StringBuilder b = new StringBuilder("\n\t").append(entry.getKey());
//		    	for(String k: entry.getValue().keySet()) {
//		    		b.append("\n\t\t").append(k);
//		    	}
//		    	LOG.log(b);
//		    }
		    tsdbSubmitter.trace(map);
		    LOG.log("Flush: %s", Arrays.toString(tsdbSubmitter.flush()));
		    LOG.log("===============================================================");
		    for(Map.Entry<ObjectName, String[]> entry: attrNames.entrySet()) {		    
	    		final ObjectName on = entry.getKey();
	    		final String[] attrs = entry.getValue();		    	
	    		tsdbSubmitter.trace(on, "yahoometric", JMXHelper.getAttributes(on, conn, attrs), "*");
		    }		    
//		    Thread.currentThread().join();
		} catch (Exception ex) {
			ex.printStackTrace(System.err);
		} finally {
		    if(connector!=null) try { connector.close(); } catch(Exception x) {/* No Op */}
		    if(tsdbSubmitter!=null) try { tsdbSubmitter.close(); } catch(Exception x) {/* No Op */}
		}		
		
		//pref=
		
		// tpsol.tunnel.propfile=/home/nwhitehead/.ssh/jmx.tssh
		
		
//		new TestClient("service:jmx:tunnel://pdk-pt-ceas-01:17083/ssh/jmxmp:pr=C:/ProdMonitors/ssh.properties,pref=pdk-ecs,h=pdk-pt-ceas-01,p=XXXX");
//		new TestClient("service:jmx:tunnel://pdk-pt-ceas-01:17082/ssh/jmxmp:pr=C:/ProdMonitors/ssh.properties,pref=pdk-ecs,h=pdk-pt-ceas-01,p=XXXX");
		

	}


}
