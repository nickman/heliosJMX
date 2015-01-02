/**
 * Helios, OpenSource Monitoring
 * Brought to you by the Helios Development Group
 *
 * Copyright 2007, Helios Development Group and individual contributors
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
package com.heliosapm.jmx.server;

import groovy.lang.GroovyClassLoader;

import javax.management.ObjectName;
import javax.management.remote.JMXServiceURL;
import javax.management.remote.jmxmp.JMXMPConnectorServer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.heliosapm.jmx.util.helpers.JMXHelper;

/**
 * <p>Title: JMXMPServer</p>
 * <p>Description: JMXMP Connection Server</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.jmx.server.JMXMPServer</code></p>
 */

public class JMXMPServer {
	/** Singleton instance */
	private static volatile JMXMPServer instance = null;
	/** Singleton instance ctor lock */
	private static final Object lock = new Object();
	
	/** This service's JMX ObjectName */
	protected final ObjectName objectName;
	/** The JMXMP Connector Server */
	protected final JMXMPConnectorServer connectorServer;
	/** The instance logger */
	protected final Logger log = LoggerFactory.getLogger(getClass());
	
	
	/**
	 * Acquires the JMXMPServer singleton instance
	 * @return the JMXMPServer singleton instance
	 */
	public static JMXMPServer getInstance() {
		if(instance==null) {
			synchronized(lock) {
				if(instance==null) {
					try {
						instance = new JMXMPServer();
					} catch (Exception ex) {/* No Op */}
				}
			}
		}
		return instance;
	}
	
	/**
	 * Creates a new GroovyConsole
	 */
	private JMXMPServer() {		
		try { 
			JMXServiceURL jmxUrl = new JMXServiceURL("service:jmx:jmxmp://0.0.0.0:8006");
			connectorServer = new JMXMPConnectorServer(jmxUrl, null, JMXHelper.getHeliosMBeanServer());			
			objectName = JMXHelper.objectName(getClass().getPackage().getName() + ":service=" + getClass().getSimpleName());
			if(JMXHelper.getHeliosMBeanServer().isRegistered(objectName)) {
				try { JMXHelper.getHeliosMBeanServer().unregisterMBean(objectName); } catch (Exception ex) {/* No Op */}
			}
			JMXHelper.getHeliosMBeanServer().registerMBean(this, objectName);
			
			Thread t = new Thread("JMXMPBootStrapThread") {
				public void run() {
					try {
						connectorServer.start();
					} catch (Exception ex) {
						log.error("Failed to start JMXMP Connector Server", ex);
						instance = null;
					}
				}
			};
			t.setDaemon(true);
			t.start();
			log.info("\n\t============================================\n\tRegistered [" + objectName + "]\n\t============================================\n");
		} catch (Exception ex) {
			throw new RuntimeException("Failed to start and register JMXMPConnectorServer", ex);
		}		
	}
	

}
