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
package com.heliosapm.jmx.config;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import javax.management.MBeanNotificationInfo;
import javax.management.MBeanServerDelegate;
import javax.management.MBeanServerNotification;
import javax.management.Notification;
import javax.management.NotificationBroadcasterSupport;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectName;

import org.cliffc.high_scale_lib.NonBlockingHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.heliosapm.jmx.expr.CodeBuilder;
import com.heliosapm.jmx.notif.SharedNotificationExecutor;
import com.heliosapm.jmx.util.helpers.JMXHelper;
import com.heliosapm.script.ConfigurationDeployedScript;
import com.heliosapm.script.DeployedScript;

/**
 * <p>Title: ConfigurationManager</p>
 * <p>Description: Singleton service to access, track and notify on configuration</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.jmx.config.ConfigurationManager</code></p>
 */

public class ConfigurationManager extends NotificationBroadcasterSupport implements ConfigurationManagerMBean, NotificationListener, NotificationFilter  {
	/** The singleton instance */
	private static volatile ConfigurationManager instance = null;
	/** The singleton instance ctor lock */
	private static final Object lock = new Object();
	/** The descriptors of the JMX notifications emitted by this service */
	private static final MBeanNotificationInfo[] notificationInfos = new MBeanNotificationInfo[] {
		// TODO: add infos
	};
	/** Re-entrancy detector on singleton creation */
	private static final AtomicBoolean initing = new AtomicBoolean(false);
	/** A counter of processed configuration updates */
	protected final AtomicLong configUpdateCount = new AtomicLong(0L);
	
	/** Instance logger */
	protected final Logger log = LoggerFactory.getLogger(getClass());
	/** A map of configurations keyed by the ObjectName of the configuration deployment */
	protected final NonBlockingHashMap<ObjectName, Map<String, Object>> configs = new NonBlockingHashMap<ObjectName, Map<String, Object>>(128); 
	/** A map of configuration change listeners keyed by the ObjectName of the configuration deployment */
	protected final NonBlockingHashMap<ObjectName, NotificationListener> configChangeListeners = new NonBlockingHashMap<ObjectName, NotificationListener>(128); 
	
	/** An empty map to respond to queries for which there is no config */
	private final Map<String, Object> EMPTY_MAP = Collections.unmodifiableMap(new HashMap<String, Object>(0));
	
	
	/**
	 * Acquires the ConfigurationManager singleton instance
	 * @return the ConfigurationManager singleton instance
	 */
	public static ConfigurationManager getInstance() {
		if(instance==null) {
			synchronized(lock) {
				if(instance==null) {
					if(!initing.compareAndSet(false, true)) {
						throw new RuntimeException("Reentrant call to ConfigurationManager.getInstance(). Programmer Error.");
					}
					instance = new ConfigurationManager();
				}
			}
		}
		return instance;
	}

	

	/**
	 * Creates a new ConfigurationManager
	 */
	private ConfigurationManager() {
		super(SharedNotificationExecutor.getInstance(), notificationInfos);
		JMXHelper.registerMBean(this, OBJECT_NAME);
		JMXHelper.addNotificationListener(MBeanServerDelegate.DELEGATE_NAME, this, this, null);
	}
	
	/**
	 * <p>Deleted config MBean event filter</p>
	 * {@inheritDoc}
	 * @see javax.management.NotificationFilter#isNotificationEnabled(javax.management.Notification)
	 */
	@Override
	public boolean isNotificationEnabled(final Notification n) {
		try {
			if(!(n instanceof MBeanServerNotification)) return false;				
			if(!MBeanServerNotification.UNREGISTRATION_NOTIFICATION.equals(n.getType())) return false;
			final MBeanServerNotification msn = (MBeanServerNotification)n;
			return DeployedScript.CONFIG_DOMAIN.equals(msn.getMBeanName().getDomain());
		} catch (Exception ex) {
			return false;
		}
	}
	
	/**
	 * <p>Deleted config MBean event handler</p>
	 * {@inheritDoc}
	 * @see javax.management.NotificationListener#handleNotification(javax.management.Notification, java.lang.Object)
	 */
	@Override
	public void handleNotification(final Notification n, final Object handback) {
		final MBeanServerNotification msn = (MBeanServerNotification)n;
		configs.remove(msn.getMBeanName());
		configChangeListeners.remove(msn.getMBeanName());
	}

	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.jmx.config.ConfigurationManagerMBean#getConfig(javax.management.ObjectName)
	 */
	@Override
	public Map<String, Object> getConfig(final ObjectName configMBean) {
		if(configMBean==null) throw new IllegalArgumentException("The passed configuration ObjectName was null");
		if(!DeployedScript.CONFIG_DOMAIN.equals(configMBean.getDomain())) return EMPTY_MAP;
		Map<String, Object> cfg = configs.get(configMBean);
		if(cfg==null)  return EMPTY_MAP;
		return new HashMap<String, Object>(cfg);
	}

	/**
	 * Adds a new configuration to the config repository
	 * @param objectName The JMX ObjectName of the new configuration deployment
	 * @param config The configuration to add
	 */
	public void addConfiguration(final ObjectName objectName, final Map<String, Object> config) {
		if(objectName==null) throw new IllegalArgumentException("The passed ObjectName was null");
		if(config==null) throw new IllegalArgumentException("The passed configuration map was null");
		if(!DeployedScript.CONFIG_DOMAIN.equals(objectName.getDomain())) throw new RuntimeException("Incorrect domain for configuration [" + objectName.getDomain() + "]");
		if(!ConfigurationDeployedScript.class.getName().equals(JMXHelper.getMBeanInfo(objectName).getClassName())) throw new RuntimeException("MBean registered at [" + objectName + "] is not an instance of " + ConfigurationDeployedScript.class.getName());		
		configs.put(objectName, config);
		final NotificationListener changeListener = new NotificationListener() {
			@Override
			public void handleNotification(final Notification n, final Object handback) {
				try {
					final ObjectName on = (ObjectName)n.getSource();
					final Map<String, Object> changedConfig = (Map<String, Object>)n.getUserData(); 
					configs.put(on, changedConfig);
					configUpdateCount.incrementAndGet();
				} catch (Exception ex) {
					log.error("Failed to handle config change notification [{}]", n.toString(), ex);
				}				
			}			
		};
		final NotificationFilter changeFilter = new NotificationFilter() {
			/**  */
			private static final long serialVersionUID = 8556163060200586442L;
			final ObjectName target = objectName;
			@Override
			public boolean isNotificationEnabled(final Notification n) {
				try {
					final ObjectName on = (ObjectName)n.getSource();
					return (on.equals(target) && DeployedScript.NOTIF_CONFIG_MOD.equals(n.getType())); 
				} catch (Exception ex) {
					log.error("Failed to filter config change notification [{}]", n.toString(), ex);
					return false;
				}				
			}
		};
		JMXHelper.addNotificationListener(objectName, changeListener, changeFilter, null);
		configChangeListeners.put(objectName, changeListener);
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.jmx.config.ConfigurationManagerMBean#getConfigurationCount()
	 */
	@Override
	public int getConfigurationCount() {
		return configs.size();
	}



	/**
	 * Returns the number of processed config updates 
	 * @return the configUpdateCount
	 */
	public long getConfigUpdateCount() {
		return configUpdateCount.get();
	}

//	public Set<ObjectName> locateConfiguration() {
//		try {
//			final Map<String, Object> configMap = new HashMap<String, Object>();
//			final Set<ObjectName> configs = new LinkedHashSet<ObjectName>();
//			// com.heliosapm.configuration:root=/tmp/hotdir,d1=X,d2=Y,d3=Z,name=Z,extension=config,subextension=js
//			final String deplName = getPlainDeploymentName(sourceFile.getName());
//			CodeBuilder b = new CodeBuilder("com.heliosapm.configuration:root=", rootDir.replace(':',  ';'), ",extension=config,subextension=*,");
//			b.push();
//			b.append("name=root");
//			Collections.addAll(configs, JMXHelper.query(b.render()));
//			b.pop();
//			b.push();
//			b.append("name=%s", deplName);
//			Collections.addAll(configs, JMXHelper.query(b.render()));
//			b.pop();
//			for(int i = 1; i < pathSegments.length; i++) {
//				b.append("d%s=%s,", i, pathSegments[i]);				
//				Collections.addAll(configs, JMXHelper.query(b.render() + "name=" + pathSegments[i]));
//				Collections.addAll(configs, JMXHelper.query(b.render() + "name=" + deplName));
//				log.info("Config search:\n\tSearching for MBean [{}]", b.render());									
//			}
//			return configs;
//		} catch (Exception ex) {
//			log.error("Failure locating configuration MBeans", ex);
//			throw new RuntimeException("Failure locating configuration MBeans", ex);
//		}
//	}	
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.jmx.config.ConfigurationManagerMBean#printAllConfig()
	 */
	@Override
	public String printAllConfig() {
		final StringBuilder b = new StringBuilder(2048);
		TreeMap<ObjectName, Map<String, Object>> ordered = new TreeMap<ObjectName, Map<String, Object>>(configs);
		for(Map.Entry<ObjectName, Map<String, Object>> entry: ordered.entrySet()) {
			final ObjectName on = entry.getKey();
			b.append("\n").append(on);
			final TreeMap<String, Object> config = new TreeMap<String, Object>(entry.getValue());
			for(Map.Entry<String, Object> innerEntry: config.entrySet()) {
				b.append("\n\t").append(innerEntry.getKey()).append("=").append(innerEntry.getValue().toString());
			}
		}
		return b.toString();
	}

}
