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

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RMISocketFactory;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;

import com.heliosapm.SimpleLogger;
import com.heliosapm.SimpleLogger.SLogger;
import com.heliosapm.jmx.batch.BulkJMXService;

/**
 * <p>Title: TestServers</p>
 * <p>Description: Starts a handful of test JMX Connector servers</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.jmx.remote.service.TestServers</code></p>
 */

public class TestServers {
	protected JMXConnectorServer connectorServer = null;
	protected Registry rmiRegistry = null;
	
	static final SLogger LOG = SimpleLogger.logger(TestServers.class);
	
	public static MBeanServer server = ManagementFactory.getPlatformMBeanServer();
	/**
	 * Creates a new TestServers
	 */
	public TestServers(String serviceURL, String bindingInterface, int rmiRegistryPort, final String rmiBindingInterface) {
		try {
			JMXServiceURL surl = new JMXServiceURL(serviceURL);
			if(rmiBindingInterface!=null) {
				InetAddress iaddr = InetAddress.getLoopbackAddress();
				LOG.log("Loopback:%s/%s", iaddr.getHostAddress(), iaddr.getHostName());
				if(iaddr.getHostAddress().equals(rmiBindingInterface) || iaddr.getHostName().toLowerCase().equals(rmiBindingInterface.toLowerCase())) {
					rmiRegistry = LocateRegistry.createRegistry(rmiRegistryPort);
				} else {
					RMISocketFactory rsf = new RMISocketFactory() {
						@Override
						public Socket createSocket(String host, int port) throws IOException {
							LOG.log("Creating Socket for [%s:%s]", host, port);
							return new Socket(host, port);
						}
						@Override
						public ServerSocket createServerSocket(int port) throws IOException {
							LOG.log("Creating ServerSocket on [%s]", port);
							return new ServerSocket(port, 100, InetAddress.getByName(rmiBindingInterface));
						}
					 };
					 rmiRegistry = LocateRegistry.createRegistry(rmiRegistryPort, rsf, rsf);					
				}
				//rmiRegistry = LocateRegistry.getRegistry(rmiBindingInterface, rmiRegistryPort);
				LOG.log("RMIRegistry: [%s]", rmiRegistry);
			}
			connectorServer = JMXConnectorServerFactory.newJMXConnectorServer(surl, null, server);
			ObjectName on = new ObjectName(String.format("%s:service=JMXConnectorServer,protocol=%s,sshHost=%s,sshPort=%s", getClass().getPackage().getName(), surl.getProtocol(), surl.getHost(), surl.getPort()));
			server.registerMBean(connectorServer, on);
			connectorServer.start();
			LOG.log("Started [%s]", on);
		} catch (Exception ex) {
			String msg = LOG.format("Failed to start JMXConnectorServer at [%s]", serviceURL);
			throw new RuntimeException(msg, ex);
		}
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		LOG.log("Starting Test Servers.....");
//		String rmiHost = System.getProperty("java.rmi.server.hostname", "127.0.0.1");
//		new TestServers("service:jmx:rmi://localhost:8003/jndi/rmi://localhost:8004/jmxrmi", "localhost", 8004, "localhost");
//		new TestServers("service:jmx:rmi://localhost:8005/jndi/rmi://localhost:8009/jmxrmi", "localhost", 8009, "0.0.0.0");
//		// service:jmx:rmi://njwmintx:8005/jndi/rmi://njwmintx:8009/jmxrmi
//		new TestServers("service:jmx:jmxmp://localhost:8007", "localhost", -1, null);
//		// service:jmx:jmxmp://njwmintx:8007
//		new TestServers("service:jmx:jmxmp://0.0.0.0:8008", "0.0.0.0", -1, null);
//		//"service:jmx:jmxmp://njwmintx:8008
		JMXConnectorServer connector = null;
		try {
			JMXServiceURL jmxUrl = new JMXServiceURL("service:jmx:jmxmp://localhost:8007");
			connector = JMXConnectorServerFactory.newJMXConnectorServer(jmxUrl, null, ManagementFactory.getPlatformMBeanServer());
			BulkJMXService.getInstance();
			connector.start();
			LOG.log("Connector started at [%s]", jmxUrl);
			Thread.currentThread().join();
		} catch (Exception x) {
			x.printStackTrace(System.err);
		} finally {
			if(connector!=null) try { connector.stop(); } catch (Exception x) {}
		}
		
	}
	


	

}
