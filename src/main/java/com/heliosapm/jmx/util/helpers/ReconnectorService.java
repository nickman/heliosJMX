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
package com.heliosapm.jmx.util.helpers;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import javax.management.MBeanNotificationInfo;
import javax.management.Notification;
import javax.management.NotificationBroadcasterSupport;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.remote.JMXAddressable;
import javax.management.remote.JMXConnectionNotification;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import org.cliffc.high_scale_lib.NonBlockingHashMap;

import com.heliosapm.SimpleLogger;
import com.heliosapm.SimpleLogger.SLogger;
import com.heliosapm.jmx.concurrency.JMXManagedScheduler;
import com.heliosapm.jmx.concurrency.JMXManagedThreadPool;

/**
 * <p>Title: ReconnectorService</p>
 * <p>Description: Background service for periodically executing reconnect attempts</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.jmx.util.helpers.ReconnectorService</code></p>
 */

public class ReconnectorService extends NotificationBroadcasterSupport implements ReconnectorServiceMBean {
	/** The singleton instance */
	private static volatile ReconnectorService instance = null;
	/** The singleton instance ctor lock */
	private static final Object lock = new Object();
	
	
	
	private static final MBeanNotificationInfo[] NOTIFS = new MBeanNotificationInfo[]{
		new MBeanNotificationInfo(new String[]{NOTIF_REGISTERED, NOTIF_FAIL, NOTIF_COMPLETE}, Notification.class.getName(), "A ReconnectorService Event: registered, fail or success")
	}; 
	
	/** A set of pending reconnectables */
	private final Map<Reconnectable, AtomicLong> reconnectables = new NonBlockingHashMap<Reconnectable, AtomicLong>();
	
	/** The reconnect scheduler */
	private final JMXManagedScheduler scheduler;
	/** Worker thread pool */
	private final JMXManagedThreadPool workerPool; 
	/** Instance logger */
	private final SLogger log = SimpleLogger.logger(getClass());
	
	/** Hashcode supplier for reconnectors */
	private final AtomicInteger reconSerial = new AtomicInteger();
	/** Notif sequence number supplier */
	private final AtomicLong notifSerial = new AtomicLong();
	
	/**
	 * Acquires the ReconnectorService singleton instance
	 * @return the ReconnectorService singleton instance
	 */
	public static ReconnectorService getInstance() {
		if(instance==null) {
			synchronized(lock) {
				if(instance==null) {
					instance = new ReconnectorService();
				}
			}
		}
		return instance;
	}
	
	/**
	 * Creates a new ReconnectorService
	 */
	private ReconnectorService() {
		super(Executors.newFixedThreadPool(2, new ThreadFactory(){
			final AtomicInteger serial = new AtomicInteger();
			@Override
			public Thread newThread(Runnable r) {
				Thread t = new Thread(r, "ReconnectorServiceNotifThread#" + serial.incrementAndGet());
				t.setDaemon(true);
				return t;
			}
		}), NOTIFS);
		scheduler = new JMXManagedScheduler(SCHEDULER_OBJECT_NAME, "ReconnectorServiceScheduler", 2, true);
		workerPool = new JMXManagedThreadPool(WORKER_OBJECT_NAME, "ReconnectorServiceWorker", true);
		JMXHelper.registerMBean(this, OBJECT_NAME);
	}
	
	/**
	 * Attaches a connection failure notification listener to the passed JMXConnector which will automatically
	 * register the disconnected connector with the reconnect service
	 * @param connector The connector to auto-reconnect
	 * @param serviceURL The JMXServiceURL which can be null if the connector implements {@link JMXAddressable}
	 * @param alsoOnClosed If true, the notification listener will be triggered on close as well as fail, otherwise it will only be triggered on fail.
	 * @param callback The optional callback to get the connector when it is reconnected
	 */
	public void autoReconnect(final JMXConnector connector, final JMXServiceURL serviceURL, final boolean alsoOnClosed, final ReconnectCallback<JMXConnector> callback) {
		if(connector==null) throw new IllegalArgumentException("The passed connector was null");
		if(!(connector instanceof JMXAddressable) && serviceURL==null) throw new IllegalArgumentException("The passed connector does not implement JMXAddressable and the passed JMXServiceURL was null");
		final JMXServiceURL jmxServiceURL = serviceURL!=null ? serviceURL : ((JMXAddressable)connector).getAddress();
		connector.addConnectionNotificationListener(new NotificationListener() {
			public void handleNotification(final Notification n, final Object handback) {
				register(connector, null, callback);
			}
		}, new NotificationFilter() {
			/**  */
			private static final long serialVersionUID = -3880149039195415789L;

			public boolean isNotificationEnabled(final Notification n) {
				return JMXConnectionNotification.FAILED.equals(n.getType()) 
						|| (alsoOnClosed && JMXConnectionNotification.FAILED.equals(n.getType()));
			}
		}, jmxServiceURL);
	}
	
	/**
	 * Registers a JMXConnector to be reconnected
	 * @param jmxConnector The JMXConnector to reconnect
	 * @param name An optional decorative name for this reconnect
	 * @param callback An optional callback that will pass the successfully connected JMXConnector
	 */
	public void register(final JMXConnector jmxConnector, final String name, final ReconnectCallback<JMXConnector> callback) {
		if(jmxConnector==null) throw new IllegalArgumentException("The passed JMXConnector was null");
		if(jmxConnector instanceof JMXAddressable) {
			JMXAddressable jax = (JMXAddressable)jmxConnector;
			if("tunnel".equalsIgnoreCase(jax.getAddress().getProtocol())) {
				register(jax.getAddress(), null, name, callback);
				return;
			}
		}
		final Reconnectable reconnectable = new Reconnectable() {
			final int hash = reconSerial.incrementAndGet();
			@Override
			public boolean reconnect() {
				try {
					jmxConnector.connect();
					log.log("Reconnected JMXConnector [%s]", jmxConnector);
					if(callback!=null) {
						workerPool.execute(new Runnable(){
							public void run() {
								callback.onReconnect(jmxConnector);
							}
						});
					}			
					sendNotification(NOTIF_COMPLETE, "Reconnected JMXConnector [" + clean(((JMXAddressable)jmxConnector).getAddress()) + "]", jmxConnector);
					return true;
				} catch(Exception ex) {
					log.loge("Failed to reconnect JMXConnector [%s] - %s", jmxConnector, ex.toString());
					return false;
				}				
			}
			@Override
			public String getId() {
				return name!=null ? name : "JMXConnector [" + ((JMXAddressable)jmxConnector).getAddress() + "]"; 
			}
			@Override
			public void run() {
				if(!reconnect()) {
					final long failedAttempts = reconnectables.get(this).incrementAndGet();
					schedule(this);
					sendNotification(NOTIF_FAIL, "Failed reconnect on JMXConnector [" + clean(((JMXAddressable)jmxConnector).getAddress()) + "]. Failed Attempt #" + failedAttempts, null);
				} else {
					reconnectables.remove(this);
				}
			}
			public int hashCode() {
				return hash * 31;
			}
			
		};
		sendNotification(NOTIF_REGISTERED, "Registered reconnect for [" + clean(((JMXAddressable)jmxConnector).getAddress()) + "]", null);
		schedule(reconnectable);
		reconnectables.put(reconnectable, new AtomicLong(0));
		
	}
	
	/**
	 * Schedules the reconnectable for a reconnect attempt
	 * @param reconnectable The reconnectable to schedule
	 */
	protected void schedule(final Reconnectable reconnectable) {
		scheduler.schedule(new Runnable(){
			public void run() {
				workerPool.execute(reconnectable);
			}
		}, DEFAULT_PERIOD, TimeUnit.MILLISECONDS);
	}
	
	/**
	 * Registers a JMXServiceURL to be connected
	 * @param jmxServiceURL The JMXServiceURL to connect
	 * @param env The optional environment map which may be needed to connect
	 * @param name An optional decorative name for this connect
	 * @param callback An optional callback that will pass the successfully connected JMXConnector
	 */
	public void register(final JMXServiceURL jmxServiceURL, final Map<String, Object> env, final String name, final ReconnectCallback<JMXConnector> callback) {
		if(jmxServiceURL==null) throw new IllegalArgumentException("The passed JMXServiceURL was null");
		final Reconnectable reconnectable = new Reconnectable() {
			final int hash = reconSerial.incrementAndGet();
			@SuppressWarnings("resource")
			@Override
			public boolean reconnect() {
				try {
					final JMXConnector connector = JMXConnectorFactory.connect(jmxServiceURL, env);
					if(callback!=null) {
						workerPool.execute(new Runnable(){
							public void run() {
								callback.onReconnect(connector);
							}
						});
					}
					sendNotification(NOTIF_COMPLETE, "Reconnected JMXConnector [" + clean(jmxServiceURL) + "]", connector);
					log.log("Connected JMXConnector to [%s]", jmxServiceURL);
					return true;
				} catch(Exception ex) {
					log.loge("Failed to connect to [%s] - %s", jmxServiceURL, ex);
					return false;
				}				
			}
			@Override
			public String getId() {
				return name!=null ? name : jmxServiceURL.toString(); 
			}
			@Override
			public void run() {
				if(!reconnect()) {
					final long failedAttempts = reconnectables.get(this).incrementAndGet();
					schedule(this);
					sendNotification(NOTIF_FAIL, "Failed reconnect on JMXConnector [" + clean(jmxServiceURL) + "]. Failed Attempt #" + failedAttempts, null);
				} else {
					reconnectables.remove(this);
				}
			}
			public int hashCode() {
				return hash * 31;
			}
		};		
		sendNotification(NOTIF_REGISTERED, "Registered reconnect for [" + clean(jmxServiceURL) + "]", null);
		schedule(reconnectable);
		reconnectables.put(reconnectable, new AtomicLong(0));
	}

	/**
	 * Dispatches a JMX notification
	 * @param type The notification type
	 * @param message The notification message
	 * @param userData The optional user data to attach
	 */
	protected void sendNotification(final String type, final String message, final Object userData) {
		final Notification n = new Notification(type, OBJECT_NAME, notifSerial.incrementAndGet(), System.currentTimeMillis(), message);
		if(userData!=null) {
			n.setUserData(userData);
		}
		sendNotification(n);
	}
	
	/**
	 * Returns a "clean" JMXServiceURL representation so we don't reveal anything we shouldn't
	 * @param serviceURL The JMXServiceURL to clean
	 * @return the cleaned string
	 */
	protected String clean(final JMXServiceURL serviceURL) {
		return String.format("service:jmx:%s://%s:%s/", serviceURL.getProtocol(), serviceURL.getHost(), serviceURL.getPort());
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.jmx.util.helpers.ReconnectorServiceMBean#getPendingReconnects()
	 */
	public int getPendingReconnects() {
		return reconnectables.size();
	}
}
