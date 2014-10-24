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
package com.heliosapm.jmx.remote.protocol;

import java.io.IOException;
import java.util.Map;

import javax.management.ListenerNotFoundException;
import javax.management.MBeanServerConnection;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.remote.JMXAddressable;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXServiceURL;
import javax.security.auth.Subject;

/**
 * <p>Title: WrappedJMXConnector</p>
 * <p>Description: A wrapper for JMXConnectors that provides an implementation of {@link JMXAddressable} if a connector does not support it natively</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.jmx.remote.protocol.WrappedJMXConnector</code></p>
 */

public class WrappedJMXConnector implements JMXConnector, JMXAddressable {
	/** The wrapped JMXConnector */
	final JMXConnector jmxConnector; 
	/** The connector's known JMXServiceURL */
	final JMXServiceURL jmxServiceURL;
	
	
	/**
	 * Returns an addressable connector. If the passed connector implements {@link JMXAddressable}, it is simple returned.
	 * Otherwise it is wrapped and the {@link JMXAddressable} interface is implemented using the passed service URL.
	 * @param jmxConnector The JMXConnector to wrap
	 * @param jmxServiceURL The connector's known JMXServiceURL
	 * @return a {@link JMXAddressable} implementing JMXConnector
	 */
	public static JMXConnector addressable(final JMXConnector jmxConnector, final JMXServiceURL jmxServiceURL) {
		if(jmxConnector==null) throw new IllegalArgumentException("The passed JMXConnector was null");
		if(jmxConnector instanceof JMXAddressable) return jmxConnector;
		if(jmxServiceURL==null) throw new IllegalArgumentException("The passed JMXConnector does not implement JMXAddressable, but the passed JMXServiceURL was null");
		return new WrappedJMXConnector(jmxConnector, jmxServiceURL);
	}
	
	
	/**
	 * Creates a new WrappedJMXConnector
	 * @param jmxConnector The JMXConnector to wrap
	 * @param jmxServiceURL The connector's known JMXServiceURL
	 */
	private WrappedJMXConnector(final JMXConnector jmxConnector, final JMXServiceURL jmxServiceURL) {		
		this.jmxConnector = jmxConnector;
		this.jmxServiceURL = jmxServiceURL;
	}


	/**
	 * @throws IOException
	 * @see javax.management.remote.JMXConnector#connect()
	 */
	public void connect() throws IOException {
		jmxConnector.connect();
	}


	/**
	 * @param env
	 * @throws IOException
	 * @see javax.management.remote.JMXConnector#connect(java.util.Map)
	 */
	public void connect(Map<String, ?> env) throws IOException {
		jmxConnector.connect(env);
	}


	/**
	 * @return
	 * @throws IOException
	 * @see javax.management.remote.JMXConnector#getMBeanServerConnection()
	 */
	public MBeanServerConnection getMBeanServerConnection() throws IOException {
		return jmxConnector.getMBeanServerConnection();
	}


	/**
	 * @param delegationSubject
	 * @return
	 * @throws IOException
	 * @see javax.management.remote.JMXConnector#getMBeanServerConnection(javax.security.auth.Subject)
	 */
	public MBeanServerConnection getMBeanServerConnection(
			Subject delegationSubject) throws IOException {
		return jmxConnector.getMBeanServerConnection(delegationSubject);
	}


	/**
	 * @throws IOException
	 * @see javax.management.remote.JMXConnector#close()
	 */
	public void close() throws IOException {
		jmxConnector.close();
	}


	/**
	 * @param listener
	 * @param filter
	 * @param handback
	 * @see javax.management.remote.JMXConnector#addConnectionNotificationListener(javax.management.NotificationListener, javax.management.NotificationFilter, java.lang.Object)
	 */
	public void addConnectionNotificationListener(
			NotificationListener listener, NotificationFilter filter,
			Object handback) {
		jmxConnector.addConnectionNotificationListener(listener, filter,
				handback);
	}


	/**
	 * @param listener
	 * @throws ListenerNotFoundException
	 * @see javax.management.remote.JMXConnector#removeConnectionNotificationListener(javax.management.NotificationListener)
	 */
	public void removeConnectionNotificationListener(
			NotificationListener listener) throws ListenerNotFoundException {
		jmxConnector.removeConnectionNotificationListener(listener);
	}


	/**
	 * @param l
	 * @param f
	 * @param handback
	 * @throws ListenerNotFoundException
	 * @see javax.management.remote.JMXConnector#removeConnectionNotificationListener(javax.management.NotificationListener, javax.management.NotificationFilter, java.lang.Object)
	 */
	public void removeConnectionNotificationListener(NotificationListener l,
			NotificationFilter f, Object handback)
			throws ListenerNotFoundException {
		jmxConnector.removeConnectionNotificationListener(l, f, handback);
	}


	/**
	 * @return
	 * @throws IOException
	 * @see javax.management.remote.JMXConnector#getConnectionId()
	 */
	public String getConnectionId() throws IOException {
		return jmxConnector.getConnectionId();
	}


	/**
	 * {@inheritDoc}
	 * @see javax.management.remote.JMXAddressable#getAddress()
	 */
	@Override
	public JMXServiceURL getAddress() {
		return jmxServiceURL;
	}

}
