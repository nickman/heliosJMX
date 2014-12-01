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
package com.heliosapm.jmx.notif;

import java.util.Map;
import java.util.Set;

import javax.management.MBeanNotificationInfo;
import javax.management.MBeanServerNotification;
import javax.management.Notification;
import javax.management.NotificationBroadcasterSupport;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectName;

import org.cliffc.high_scale_lib.NonBlockingHashMap;

import com.heliosapm.jmx.util.helpers.JMXHelper;

/**
 * <p>Title: ProxyNotificationRegistrar</p>
 * <p>Description: Service to accept notification listener registration against MBeans that may not exist yet</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.jmx.notif.ProxyNotificationRegistrar</code></p>
 */

public class ProxyNotificationRegistrar extends NotificationBroadcasterSupport implements NotificationListener, NotificationFilter {

	/** The descriptors of the JMX notifications emitted by this service */
	private static final MBeanNotificationInfo[] notificationInfos = new MBeanNotificationInfo[] {
		JMXHelper.META_CHANGED_NOTIF
//		new MBeanNotificationInfo(new String[]{NOTIF_STATUS_CHANGE}, AttributeChangeNotification.class.getName(), "JMX notification broadcast when the status of a deployment changes"),
//		new MBeanNotificationInfo(new String[]{AttributeChangeNotification.ATTRIBUTE_CHANGE}, Notification.class.getName(), "JMX notification broadcast when the configuration of a deployment changes"),
//		new MBeanNotificationInfo(new String[]{NOTIF_RECOMPILE}, Notification.class.getName(), "JMX notification broadcast when a deployment is recompiled")
	};

	private final Map<ObjectName, Set<ProxyListener>> proxyListeners = new NonBlockingHashMap<ObjectName, Set<ProxyListener>>();
	
	/**
	 * Creates a new ProxyNotificationRegistrar
	 */
	public ProxyNotificationRegistrar() {
		super(SharedNotificationExecutor.getInstance(), notificationInfos);
	}

	
	/**
	 * Adds a notification listener to all registered MBeans matching the passed pattern as well as all future MBeans that may be registered at the time they are registered
	 * @param pattern The pattern of the MBean's ObjectName to match 
	 * @param listenerObjectName The ObjectName of the listener. If this is provided, all listeners (and proxies) will be unregistered when the listening MBean is unregistered 
	 * @param listener The listener to register
	 * @param filter The optional filter to filter notifications
	 * @param handback The optional handback
	 */
	public void addNotificationListener(final ObjectName pattern, final ObjectName listenerObjectName, final NotificationListener listener, final NotificationFilter filter, final Object handback) {
		
	}


	/**
	 * {@inheritDoc}
	 * @see javax.management.NotificationFilter#isNotificationEnabled(javax.management.Notification)
	 */
	@Override
	public boolean isNotificationEnabled(final Notification notification) {
		if(!(notification instanceof MBeanServerNotification)) return false;		
		return true;
	}

	/**
	 * {@inheritDoc}
	 * @see javax.management.NotificationListener#handleNotification(javax.management.Notification, java.lang.Object)
	 */
	@Override
	public void handleNotification(Notification notification, Object handback) {
		if(MBeanServerNotification.REGISTRATION_NOTIFICATION.equals(notification.getType())) {
			// NEW MBEAN
		} else {
			// UNREGISTERED MBEAN
		}		
	}
	
	private class ProxyListener {
		/** The pattern to match against the ObjectName of new MBeans */
		final ObjectName pattern;
		/** The ObjectName of the listener so the registrar can unsubscribe when it is unregistered */
		final ObjectName listenerObjectName;
		
		/** The listener to register */
		final NotificationListener listener;
		/** The filter to register */
		final NotificationFilter filter;
		/** The handback to register */
		final Object handback;
		
		/**
		 * Creates a new ProxyListener
		 * @param pattern The pattern to match against the ObjectName of new MBeans
		 * @param listener The listener to register
		 * @param filter The filter to register
		 * @param handback The handback to register
		 */
		public ProxyListener(final ObjectName pattern, final ObjectName listenerObjectName, final NotificationListener listener, final NotificationFilter filter, final Object handback) {			
			this.pattern = pattern;
			this.listenerObjectName = listenerObjectName;
			this.listener = listener;
			this.filter = filter;
			this.handback = handback;
		}

		/**
		 * {@inheritDoc}
		 * @see java.lang.Object#hashCode()
		 */
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + getOuterType().hashCode();
			result = prime * result
					+ ((filter == null) ? 0 : filter.hashCode());
			result = prime * result
					+ ((handback == null) ? 0 : handback.hashCode());
			result = prime * result
					+ ((listener == null) ? 0 : listener.hashCode());
			result = prime
					* result
					+ ((listenerObjectName == null) ? 0 : listenerObjectName
							.hashCode());
			return result;
		}

		/**
		 * {@inheritDoc}
		 * @see java.lang.Object#equals(java.lang.Object)
		 */
		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (!(obj instanceof ProxyListener))
				return false;
			ProxyListener other = (ProxyListener) obj;
			if (!getOuterType().equals(other.getOuterType()))
				return false;
			if (filter == null) {
				if (other.filter != null)
					return false;
			} else if (!filter.equals(other.filter))
				return false;
			if (handback == null) {
				if (other.handback != null)
					return false;
			} else if (!handback.equals(other.handback))
				return false;
			if (listener == null) {
				if (other.listener != null)
					return false;
			} else if (!listener.equals(other.listener))
				return false;
			if (listenerObjectName == null) {
				if (other.listenerObjectName != null)
					return false;
			} else if (!listenerObjectName.equals(other.listenerObjectName))
				return false;
			return true;
		}

		private ProxyNotificationRegistrar getOuterType() {
			return ProxyNotificationRegistrar.this;
		}
		
		
	}

}
