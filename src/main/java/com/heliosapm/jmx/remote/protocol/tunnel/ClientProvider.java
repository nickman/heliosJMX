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
package com.heliosapm.jmx.remote.protocol.tunnel;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Map;
import java.util.Set;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.AttributeNotFoundException;
import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import javax.management.InvalidAttributeValueException;
import javax.management.ListenerNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServerConnection;
import javax.management.NotCompliantMBeanException;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.QueryExp;
import javax.management.ReflectionException;
import javax.management.remote.JMXAddressable;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorProvider;
import javax.management.remote.JMXServiceURL;
import javax.management.remote.MBeanServerForwarder;
import javax.management.remote.tunnel.TunnelConnector;
import javax.security.auth.Subject;

import com.heliosapm.jmx.util.helpers.ReconnectCallback;
import com.heliosapm.jmx.util.helpers.ReconnectorService;

/**
 * <p>Title: ClientProvider</p>
 * <p>Description: JMX Remoting Client Provider for the tunnel protocol</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.jmx.remote.protocol.tunnel.ClientProvider</code></p>
 */
public class ClientProvider implements JMXConnectorProvider {
	
	/** The protocol name */
	public static final String PROTOCOL_NAME = "tunnel";
	
	private static boolean INSTR = false;
	
	public static final String TIMEOUT_FOR_CONNECTED_STATE = "jmx.remote.x.client.connected.state.timeout";	
	
	public static final String AUTO_RECONECT = "jmx.remote.x.client.autoreconnect";
	
	
	/**
	 * {@inheritDoc}
	 * @see javax.management.remote.JMXConnectorProvider#newJMXConnector(javax.management.remote.JMXServiceURL, java.util.Map)
	 */
	@Override
	public JMXConnector newJMXConnector(final JMXServiceURL serviceURL, final Map<String, ?> environment) throws IOException {
		if (!serviceURL.getProtocol().equals(PROTOCOL_NAME)) {
			throw new MalformedURLException("Protocol not [" + PROTOCOL_NAME + "]: " + serviceURL.getProtocol());
		}
		SwapableDelegateJMXConnector delegate = new SwapableDelegateJMXConnector(new TunnelConnector(serviceURL, environment), serviceURL);
		if(environment.containsKey(AUTO_RECONECT)) {
			ReconnectorService.getInstance().autoReconnect(delegate, serviceURL, false, delegate);
		}
	    return delegate;		
	}
	
	public static class SwapableDelegateJMXConnector implements JMXConnector, JMXAddressable, ReconnectCallback<JMXConnector> {
		private JMXConnector delegate;
		private final JMXServiceURL address;
		
		private final SwapableDelegateMBeanServerConnection delegateMBeanServer;
		
		SwapableDelegateJMXConnector(JMXConnector delegate, final JMXServiceURL address) throws IOException {
			this.delegate = delegate;
			this.address = address;
			delegateMBeanServer = new SwapableDelegateMBeanServerConnection(null);
		}
		
		void updateDelegate(JMXConnector delegate) throws IOException {
			this.delegate = delegate;
			delegateMBeanServer.update(delegate.getMBeanServerConnection());
		}

		/**
		 * @throws IOException
		 * @see javax.management.remote.JMXConnector#connect()
		 */
		public void connect() throws IOException {
			delegate.connect();
			delegateMBeanServer.update(delegate.getMBeanServerConnection());
		}

		/**
		 * @param env
		 * @throws IOException
		 * @see javax.management.remote.JMXConnector#connect(java.util.Map)
		 */
		public void connect(Map<String, ?> env) throws IOException {
			delegate.connect(env);
			delegateMBeanServer.update(delegate.getMBeanServerConnection());
		}

		/**
		 * @return
		 * @throws IOException
		 * @see javax.management.remote.JMXConnector#getMBeanServerConnection()
		 */
		public MBeanServerConnection getMBeanServerConnection()
				throws IOException {
			return delegateMBeanServer;
		}

		/**
		 * @param delegationSubject
		 * @return
		 * @throws IOException
		 * @see javax.management.remote.JMXConnector#getMBeanServerConnection(javax.security.auth.Subject)
		 */
		public MBeanServerConnection getMBeanServerConnection(
				Subject delegationSubject) throws IOException {
			return delegate.getMBeanServerConnection(delegationSubject);
		}

		/**
		 * @throws IOException
		 * @see javax.management.remote.JMXConnector#close()
		 */
		public void close() throws IOException {
			delegate.close();
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
			delegate.addConnectionNotificationListener(listener, filter,
					handback);
		}

		/**
		 * @param listener
		 * @throws ListenerNotFoundException
		 * @see javax.management.remote.JMXConnector#removeConnectionNotificationListener(javax.management.NotificationListener)
		 */
		public void removeConnectionNotificationListener(
				NotificationListener listener) throws ListenerNotFoundException {
			delegate.removeConnectionNotificationListener(listener);
		}

		/**
		 * @param l
		 * @param f
		 * @param handback
		 * @throws ListenerNotFoundException
		 * @see javax.management.remote.JMXConnector#removeConnectionNotificationListener(javax.management.NotificationListener, javax.management.NotificationFilter, java.lang.Object)
		 */
		public void removeConnectionNotificationListener(
				NotificationListener l, NotificationFilter f, Object handback)
				throws ListenerNotFoundException {
			delegate.removeConnectionNotificationListener(l, f, handback);
		}

		/**
		 * @return
		 * @throws IOException
		 * @see javax.management.remote.JMXConnector#getConnectionId()
		 */
		public String getConnectionId() throws IOException {
			return delegate.getConnectionId();
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.jmx.util.helpers.ReconnectCallback#onReconnect(java.lang.Object)
		 */
		@Override
		public void onReconnect(JMXConnector t) {
			try {
				updateDelegate(delegate);
			} catch (Exception ex) {
				throw new RuntimeException("Failed to update delegate", ex);
			}
		}

		/**
		 * {@inheritDoc}
		 * @see javax.management.remote.JMXAddressable#getAddress()
		 */
		@Override
		public JMXServiceURL getAddress() {
			return address;
		}
		
	}
	
	public static class SwapableDelegateMBeanServerConnection implements MBeanServerConnection {
		protected MBeanServerConnection delegate;
		
		SwapableDelegateMBeanServerConnection(MBeanServerConnection delegate) {
			this.delegate = delegate;
		}
		
		void update(MBeanServerConnection delegate) {
			this.delegate = delegate;
		}

		/**
		 * @param className
		 * @param name
		 * @return
		 * @throws ReflectionException
		 * @throws InstanceAlreadyExistsException
		 * @throws MBeanRegistrationException
		 * @throws MBeanException
		 * @throws NotCompliantMBeanException
		 * @throws IOException
		 * @see javax.management.MBeanServerConnection#createMBean(java.lang.String, javax.management.ObjectName)
		 */
		public ObjectInstance createMBean(String className, ObjectName name)
				throws ReflectionException, InstanceAlreadyExistsException,
				MBeanRegistrationException, MBeanException,
				NotCompliantMBeanException, IOException {
			return delegate.createMBean(className, name);
		}

		/**
		 * @param className
		 * @param name
		 * @param loaderName
		 * @return
		 * @throws ReflectionException
		 * @throws InstanceAlreadyExistsException
		 * @throws MBeanRegistrationException
		 * @throws MBeanException
		 * @throws NotCompliantMBeanException
		 * @throws InstanceNotFoundException
		 * @throws IOException
		 * @see javax.management.MBeanServerConnection#createMBean(java.lang.String, javax.management.ObjectName, javax.management.ObjectName)
		 */
		public ObjectInstance createMBean(String className, ObjectName name,
				ObjectName loaderName) throws ReflectionException,
				InstanceAlreadyExistsException, MBeanRegistrationException,
				MBeanException, NotCompliantMBeanException,
				InstanceNotFoundException, IOException {
			return delegate.createMBean(className, name, loaderName);
		}

		/**
		 * @param className
		 * @param name
		 * @param params
		 * @param signature
		 * @return
		 * @throws ReflectionException
		 * @throws InstanceAlreadyExistsException
		 * @throws MBeanRegistrationException
		 * @throws MBeanException
		 * @throws NotCompliantMBeanException
		 * @throws IOException
		 * @see javax.management.MBeanServerConnection#createMBean(java.lang.String, javax.management.ObjectName, java.lang.Object[], java.lang.String[])
		 */
		public ObjectInstance createMBean(String className, ObjectName name,
				Object[] params, String[] signature)
				throws ReflectionException, InstanceAlreadyExistsException,
				MBeanRegistrationException, MBeanException,
				NotCompliantMBeanException, IOException {
			return delegate.createMBean(className, name, params, signature);
		}

		/**
		 * @param className
		 * @param name
		 * @param loaderName
		 * @param params
		 * @param signature
		 * @return
		 * @throws ReflectionException
		 * @throws InstanceAlreadyExistsException
		 * @throws MBeanRegistrationException
		 * @throws MBeanException
		 * @throws NotCompliantMBeanException
		 * @throws InstanceNotFoundException
		 * @throws IOException
		 * @see javax.management.MBeanServerConnection#createMBean(java.lang.String, javax.management.ObjectName, javax.management.ObjectName, java.lang.Object[], java.lang.String[])
		 */
		public ObjectInstance createMBean(String className, ObjectName name,
				ObjectName loaderName, Object[] params, String[] signature)
				throws ReflectionException, InstanceAlreadyExistsException,
				MBeanRegistrationException, MBeanException,
				NotCompliantMBeanException, InstanceNotFoundException,
				IOException {
			return delegate.createMBean(className, name, loaderName, params,
					signature);
		}

		/**
		 * @param name
		 * @throws InstanceNotFoundException
		 * @throws MBeanRegistrationException
		 * @throws IOException
		 * @see javax.management.MBeanServerConnection#unregisterMBean(javax.management.ObjectName)
		 */
		public void unregisterMBean(ObjectName name)
				throws InstanceNotFoundException, MBeanRegistrationException,
				IOException {
			delegate.unregisterMBean(name);
		}

		/**
		 * @param name
		 * @return
		 * @throws InstanceNotFoundException
		 * @throws IOException
		 * @see javax.management.MBeanServerConnection#getObjectInstance(javax.management.ObjectName)
		 */
		public ObjectInstance getObjectInstance(ObjectName name)
				throws InstanceNotFoundException, IOException {
			return delegate.getObjectInstance(name);
		}

		/**
		 * @param name
		 * @param query
		 * @return
		 * @throws IOException
		 * @see javax.management.MBeanServerConnection#queryMBeans(javax.management.ObjectName, javax.management.QueryExp)
		 */
		public Set<ObjectInstance> queryMBeans(ObjectName name, QueryExp query)
				throws IOException {
			return delegate.queryMBeans(name, query);
		}

		/**
		 * @param name
		 * @param query
		 * @return
		 * @throws IOException
		 * @see javax.management.MBeanServerConnection#queryNames(javax.management.ObjectName, javax.management.QueryExp)
		 */
		public Set<ObjectName> queryNames(ObjectName name, QueryExp query)
				throws IOException {
			return delegate.queryNames(name, query);
		}

		/**
		 * @param name
		 * @return
		 * @throws IOException
		 * @see javax.management.MBeanServerConnection#isRegistered(javax.management.ObjectName)
		 */
		public boolean isRegistered(ObjectName name) throws IOException {
			return delegate.isRegistered(name);
		}

		/**
		 * @return
		 * @throws IOException
		 * @see javax.management.MBeanServerConnection#getMBeanCount()
		 */
		public Integer getMBeanCount() throws IOException {
			return delegate.getMBeanCount();
		}

		/**
		 * @param name
		 * @param attribute
		 * @return
		 * @throws MBeanException
		 * @throws AttributeNotFoundException
		 * @throws InstanceNotFoundException
		 * @throws ReflectionException
		 * @throws IOException
		 * @see javax.management.MBeanServerConnection#getAttribute(javax.management.ObjectName, java.lang.String)
		 */
		public Object getAttribute(ObjectName name, String attribute)
				throws MBeanException, AttributeNotFoundException,
				InstanceNotFoundException, ReflectionException, IOException {
			return delegate.getAttribute(name, attribute);
		}

		/**
		 * @param name
		 * @param attributes
		 * @return
		 * @throws InstanceNotFoundException
		 * @throws ReflectionException
		 * @throws IOException
		 * @see javax.management.MBeanServerConnection#getAttributes(javax.management.ObjectName, java.lang.String[])
		 */
		public AttributeList getAttributes(ObjectName name, String[] attributes)
				throws InstanceNotFoundException, ReflectionException,
				IOException {
			return delegate.getAttributes(name, attributes);
		}

		/**
		 * @param name
		 * @param attribute
		 * @throws InstanceNotFoundException
		 * @throws AttributeNotFoundException
		 * @throws InvalidAttributeValueException
		 * @throws MBeanException
		 * @throws ReflectionException
		 * @throws IOException
		 * @see javax.management.MBeanServerConnection#setAttribute(javax.management.ObjectName, javax.management.Attribute)
		 */
		public void setAttribute(ObjectName name, Attribute attribute)
				throws InstanceNotFoundException, AttributeNotFoundException,
				InvalidAttributeValueException, MBeanException,
				ReflectionException, IOException {
			delegate.setAttribute(name, attribute);
		}

		/**
		 * @param name
		 * @param attributes
		 * @return
		 * @throws InstanceNotFoundException
		 * @throws ReflectionException
		 * @throws IOException
		 * @see javax.management.MBeanServerConnection#setAttributes(javax.management.ObjectName, javax.management.AttributeList)
		 */
		public AttributeList setAttributes(ObjectName name,
				AttributeList attributes) throws InstanceNotFoundException,
				ReflectionException, IOException {
			return delegate.setAttributes(name, attributes);
		}

		/**
		 * @param name
		 * @param operationName
		 * @param params
		 * @param signature
		 * @return
		 * @throws InstanceNotFoundException
		 * @throws MBeanException
		 * @throws ReflectionException
		 * @throws IOException
		 * @see javax.management.MBeanServerConnection#invoke(javax.management.ObjectName, java.lang.String, java.lang.Object[], java.lang.String[])
		 */
		public Object invoke(ObjectName name, String operationName,
				Object[] params, String[] signature)
				throws InstanceNotFoundException, MBeanException,
				ReflectionException, IOException {
			return delegate.invoke(name, operationName, params, signature);
		}

		/**
		 * @return
		 * @throws IOException
		 * @see javax.management.MBeanServerConnection#getDefaultDomain()
		 */
		public String getDefaultDomain() throws IOException {
			return delegate.getDefaultDomain();
		}

		/**
		 * @return
		 * @throws IOException
		 * @see javax.management.MBeanServerConnection#getDomains()
		 */
		public String[] getDomains() throws IOException {
			return delegate.getDomains();
		}

		/**
		 * @param name
		 * @param listener
		 * @param filter
		 * @param handback
		 * @throws InstanceNotFoundException
		 * @throws IOException
		 * @see javax.management.MBeanServerConnection#addNotificationListener(javax.management.ObjectName, javax.management.NotificationListener, javax.management.NotificationFilter, java.lang.Object)
		 */
		public void addNotificationListener(ObjectName name,
				NotificationListener listener, NotificationFilter filter,
				Object handback) throws InstanceNotFoundException, IOException {
			delegate.addNotificationListener(name, listener, filter, handback);
		}

		/**
		 * @param name
		 * @param listener
		 * @param filter
		 * @param handback
		 * @throws InstanceNotFoundException
		 * @throws IOException
		 * @see javax.management.MBeanServerConnection#addNotificationListener(javax.management.ObjectName, javax.management.ObjectName, javax.management.NotificationFilter, java.lang.Object)
		 */
		public void addNotificationListener(ObjectName name,
				ObjectName listener, NotificationFilter filter, Object handback)
				throws InstanceNotFoundException, IOException {
			delegate.addNotificationListener(name, listener, filter, handback);
		}

		/**
		 * @param name
		 * @param listener
		 * @throws InstanceNotFoundException
		 * @throws ListenerNotFoundException
		 * @throws IOException
		 * @see javax.management.MBeanServerConnection#removeNotificationListener(javax.management.ObjectName, javax.management.ObjectName)
		 */
		public void removeNotificationListener(ObjectName name,
				ObjectName listener) throws InstanceNotFoundException,
				ListenerNotFoundException, IOException {
			delegate.removeNotificationListener(name, listener);
		}

		/**
		 * @param name
		 * @param listener
		 * @param filter
		 * @param handback
		 * @throws InstanceNotFoundException
		 * @throws ListenerNotFoundException
		 * @throws IOException
		 * @see javax.management.MBeanServerConnection#removeNotificationListener(javax.management.ObjectName, javax.management.ObjectName, javax.management.NotificationFilter, java.lang.Object)
		 */
		public void removeNotificationListener(ObjectName name,
				ObjectName listener, NotificationFilter filter, Object handback)
				throws InstanceNotFoundException, ListenerNotFoundException,
				IOException {
			delegate.removeNotificationListener(name, listener, filter,
					handback);
		}

		/**
		 * @param name
		 * @param listener
		 * @throws InstanceNotFoundException
		 * @throws ListenerNotFoundException
		 * @throws IOException
		 * @see javax.management.MBeanServerConnection#removeNotificationListener(javax.management.ObjectName, javax.management.NotificationListener)
		 */
		public void removeNotificationListener(ObjectName name,
				NotificationListener listener)
				throws InstanceNotFoundException, ListenerNotFoundException,
				IOException {
			delegate.removeNotificationListener(name, listener);
		}

		/**
		 * @param name
		 * @param listener
		 * @param filter
		 * @param handback
		 * @throws InstanceNotFoundException
		 * @throws ListenerNotFoundException
		 * @throws IOException
		 * @see javax.management.MBeanServerConnection#removeNotificationListener(javax.management.ObjectName, javax.management.NotificationListener, javax.management.NotificationFilter, java.lang.Object)
		 */
		public void removeNotificationListener(ObjectName name,
				NotificationListener listener, NotificationFilter filter,
				Object handback) throws InstanceNotFoundException,
				ListenerNotFoundException, IOException {
			delegate.removeNotificationListener(name, listener, filter,
					handback);
		}

		/**
		 * @param name
		 * @return
		 * @throws InstanceNotFoundException
		 * @throws IntrospectionException
		 * @throws ReflectionException
		 * @throws IOException
		 * @see javax.management.MBeanServerConnection#getMBeanInfo(javax.management.ObjectName)
		 */
		public MBeanInfo getMBeanInfo(ObjectName name)
				throws InstanceNotFoundException, IntrospectionException,
				ReflectionException, IOException {
			return delegate.getMBeanInfo(name);
		}

		/**
		 * @param name
		 * @param className
		 * @return
		 * @throws InstanceNotFoundException
		 * @throws IOException
		 * @see javax.management.MBeanServerConnection#isInstanceOf(javax.management.ObjectName, java.lang.String)
		 */
		public boolean isInstanceOf(ObjectName name, String className)
				throws InstanceNotFoundException, IOException {
			return delegate.isInstanceOf(name, className);
		}
		
	}
	

//    /**
//     * {@inheritDoc}
//     * @see javax.management.remote.JMXConnectorProvider#newJMXConnector(javax.management.remote.JMXServiceURL, java.util.Map)
//     */
//    @SuppressWarnings({ "unchecked", "resource" })
//	public JMXConnector newJMXConnector(final JMXServiceURL serviceURL, final Map environment) throws IOException {
//		if (!serviceURL.getProtocol().equals(PROTOCOL_NAME)) {
//		    throw new MalformedURLException("Protocol not [" + PROTOCOL_NAME + "]: " +
//						    serviceURL.getProtocol());
//		}
//		Map newenv = SSHTunnelConnector.tunnel(serviceURL, environment);
//		newenv.put(TIMEOUT_FOR_CONNECTED_STATE, 2000);
//		final TunnelHandle th = (TunnelHandle)newenv.remove("TunnelHandle");
//        final JMXConnector connector = WrappedJMXConnector.addressable(JMXConnectorFactory.newJMXConnector((JMXServiceURL)newenv.remove("JMXServiceURL"), newenv), serviceURL);
//        
//        
//        final NotificationListener closeListener = new NotificationListener() {
//        	@Override
//        	public void handleNotification(final Notification n, final Object handback) {
//        		final String type = n.getType();
//        		if(JMXConnectionNotification.CLOSED.equals(type) || JMXConnectionNotification.FAILED.equals(type)) {
//        			((LocalPortForwarderWrapper)th).close();
//        			System.err.println("Connection [" + clean(serviceURL) + "] Closed. Closing Tunnel [" + th + "].....");
//        			if(!INSTR) {
//        				try {
//        					ClassSwapper.getInstance().swapIn(com.sun.jmx.remote.socket.InstrumentedSocketConnection.class);
//        					System.out.println("\n\t============\n\tInstrumented JMX Socket\n\t============");
//        					INSTR = true;
//        				} catch (Exception x) {
//        					x.printStackTrace(System.err);
//        				}
//        			}
//        		}
//        	}
//        };
//        
//        ((LocalPortForwarderWrapper)th).addCloseListener(new CloseListener<LocalPortForwarderWrapper>(){
//        	public void onClosed(LocalPortForwarderWrapper closeable) {
//        		try {
//        			System.err.println("PortForward [" + closeable + "] Closed. Closing Connector [" + connector.getConnectionId() + "].....");
//        			try { connector.removeConnectionNotificationListener(closeListener); } catch (Exception x) {/* No Op */}
//					connector.close();
//				} catch (IOException x) { /* No Op */}
//        	}
//        });
//        connector.addConnectionNotificationListener(closeListener, null, null);
//        ReconnectorService.getInstance().autoReconnect(connector, serviceURL, true, null);
//        return connector;
//    }
    
	/**
	 * Returns a "clean" JMXServiceURL representation so we don't reveal anything we shouldn't
	 * @param serviceURL The JMXServiceURL to clean
	 * @return the cleaned string
	 */
	protected String clean(final JMXServiceURL serviceURL) {
		return String.format("service:jmx:%s://%s:%s/", serviceURL.getProtocol(), serviceURL.getHost(), serviceURL.getPort());
	}
    
}
