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
package com.heliosapm.jmx.config;

import java.beans.PropertyEditor;
import java.beans.PropertyEditorManager;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import javax.management.AttributeChangeNotification;
import javax.management.ListenerNotFoundException;
import javax.management.MBeanNotificationInfo;
import javax.management.Notification;
import javax.management.NotificationBroadcasterSupport;
import javax.management.NotificationEmitter;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectName;

import org.cliffc.high_scale_lib.NonBlockingHashMap;

/**
 * <p>Title: Configuration</p>
 * <p>Description: A configuration container</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.jmx.config.Configuration</code></p>
 */

public class Configuration implements NotificationListener, NotificationFilter, NotificationEmitter, ConfigurationMBean {
	
	/** The standard notifications emitted by a Configuration */
	private static final MBeanNotificationInfo[] NOTIF_INFO = new MBeanNotificationInfo[] {
		new MBeanNotificationInfo(new String[] {"com.heliosapm.jmx.config.attr.change"}, AttributeChangeNotification.class.getName(), "Notification emitted when a configuration item is inserted, changed or removed"),
		new MBeanNotificationInfo(new String[] {"com.heliosapm.jmx.config.attr.load"}, Notification.class.getName(), "Notification emitted when a configuration loads an update from a parent")
	}; 
	/** Notification sequence number provider */
	private static final AtomicLong sequence = new AtomicLong(0L);
	/** A map of property editors keyed by the class the editor is for */
	private static final Map<Class<?>, Constructor<PropertyEditor>> editors = new NonBlockingHashMap<Class<?>, Constructor<PropertyEditor>>();
	
	
	/** The JMX ObjectName of the owner of this configuration */
	final ObjectName objectName;
	/** The delegate broadcaster */
	protected final NotificationBroadcasterSupport delegateBroadcaster;
	/** The actual config store */
	protected final Map<String, String> config = new NonBlockingHashMap<String, String>();
	/** The original key/value pairs loaded into this config (i.e. not loaded from any parents */
	protected final Map<String, String> internalConfig = new NonBlockingHashMap<String, String>();
	
	
	/**
	 * Creates a new Configuration
	 * @param objectName The JMX ObjectName of the owner of this configuration
	 * @param delegateBroadcaster The delegate broadcaster
	 */
	public Configuration(final ObjectName objectName, final NotificationBroadcasterSupport delegateBroadcaster) {
		this.delegateBroadcaster = delegateBroadcaster;
		this.objectName = objectName;
	}
	
	/**
	 * Internal load that fires no listeners or notifications.
	 * Intended for intial load or refresh. Clears the current config and internal config.
	 * @param content The content to load into this configuration
	 */
	public void internalLoad(final Map<String, String> content) {
		if(content==null) throw new IllegalArgumentException("The passed content was null");
		config.clear();
		internalConfig.clear();
		final Map<String, String> trimmed = trim(content);
		config.putAll(trimmed);
		internalConfig.putAll(trimmed);
	}
	
	/**
	 * Merges the passed content into this configuration.
	 * Any additions or updates will trigger listeners and notification emissions.
	 * @param content The new content to load
	 */
	public void load(final Map<String, String> content) {
		if(content==null || content.isEmpty()) return;
		final Map<String, String> trimmed = trim(content);
		final LinkedList<Notification> toSend = new LinkedList<Notification>();
		final long now = System.currentTimeMillis();
		for(final Map.Entry<String, String> entry: trimmed.entrySet()) {
			final String key = entry.getKey();
			final String value = entry.getValue();
			final String oldValue = config.put(key, value);
			if(value.equals(oldValue)) continue;
			if(oldValue==null) {
				toSend.add(new AttributeChangeNotification(objectName, sequence.incrementAndGet(), now, String.format("#%s Inserted new config\n%s=%s\n", key, key, value), key, String.class.getName(), oldValue, value));
			} else {
				toSend.add(new AttributeChangeNotification(objectName, sequence.incrementAndGet(), now, String.format("#%s Updated config\n%s=%s\n", key, key, value), key, String.class.getName(), oldValue, value));
			}
		}
		if(!toSend.isEmpty()) {
			delegateBroadcaster.sendNotification(configChangeNotification());
			for(Notification n: toSend) {
				delegateBroadcaster.sendNotification(n);
			}
		}
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.jmx.config.ConfigurationMBean#get(java.lang.String)
	 */
	@Override
	public String get(final String key) {
		if(key==null || key.trim().isEmpty()) throw new IllegalArgumentException("The passed key was null or empty");
		return config.get(key);
	}
	
	/**
	 * Returns the typed value for the passed config key
	 * @param key The config item key
	 * @param type The type to convert to
	 * @return the typed config value
	 */
	public <T> T get(final String key, final Class<T> type) {
		return fromText(get(key), type);
	}

	/**
	 * @param key
	 * @param value
	 * @return
	 * @see java.util.Map#put(java.lang.Object, java.lang.Object)
	 */
	public String put(String key, String value) {
		return config.put(key, value);
	}
	
	/** Locally installed property editors for this instance */
	protected final Map<Class<?>, PropertyEditor> localEditors = new NonBlockingHashMap<Class<?>, PropertyEditor>();
	
	/**
	 * Converts the passed text to an instance of the passed type.
	 * The operation is thread safe.
	 * @param text The text to convert
	 * @param type The type to convert to 
	 * @return the converted object or null if the passed test was null 
	 */
	protected <T> T fromText(final String text, final Class<T> type) {
		if(text==null) return null;
		PropertyEditor pe = localEditors.get(type);
		if(pe==null) {
			pe = getEditor(type);
			localEditors.put(type, pe);
		}
		synchronized(pe) {
			pe.setAsText(text);
			return (T)pe.getValue();
		} 
	}
	
	/**
	 * Renders the passed object into text formatted to ensure reversability back into an object
	 * through the type's property editor
	 * @param value The value to render
	 * @return the rendered text
	 */
	protected String toText(final Object value) {
		final Class<?> type = value.getClass();
		PropertyEditor pe = localEditors.get(type);
		if(pe==null) {
			pe = getEditor(type);
			localEditors.put(type, pe);
		}
		synchronized(pe) {
			pe.setValue(value);
			return pe.getAsText();
		}		
	}
	
	
	/**
	 * Acquires a new PropertyEditor instance for the passed class
	 * @param clazz The class to get a PropertyEditor for
	 * @return the property editor
	 */
	public static PropertyEditor getEditor(final Class<?> clazz) {
		if(clazz==null) throw new IllegalArgumentException("The passed Class was null");
		Constructor<PropertyEditor> pec = editors.get(clazz);
		if(pec==null) {
			synchronized(editors) {
				pec = editors.get(clazz);
				if(pec==null) {
					PropertyEditor pe = PropertyEditorManager.findEditor(clazz);
					if(pe==null) throw new RuntimeException("No property editor found for class [" + clazz.getName() + "]");
					try {
						pec = (Constructor<PropertyEditor>) pe.getClass().getConstructor();
					} catch (Exception ex) {
						throw new RuntimeException("Failed to get property editor constructor for class [" + clazz.getName() + "]", ex);
					}
					editors.put(clazz, pec);
				}
			}
		}
		try {
			return pec.newInstance();
		} catch (Exception e) {
			throw new RuntimeException("Failed to instantiate property editor from class [" + pec.getName() + "]", e);
		}
	}
	
	
	/**
	 * Creates a summary config change notification
	 * @return a summary config change notification
	 */
	protected Notification configChangeNotification() {
		final Notification n = new Notification(NOTIF_CONFIG_CHANGE, objectName, sequence.incrementAndGet(), System.currentTimeMillis(), "Confguration Update for [" + objectName + "]");
		n.setUserData(config);
		return n;
	}
	
	/**
	 * Returns a map of all the pairs in the passed map with the keys and values trimmed.
	 * Any entries with an empty key, an empty value or null value are discarded. 
	 * @param map The map to trim
	 * @return the trimmed map
	 */
	private static Map<String, String> trim(final Map<String, String> map) {
		if(map==null) return Collections.EMPTY_MAP;
		final Map<String, String> trimmedMap = new HashMap<String, String>(map.size());
		for(final Map.Entry<String, String> entry: map.entrySet()) {
			if(entry.getKey().trim().isEmpty() || entry.getValue()==null || entry.getValue().trim().isEmpty()) continue;
			trimmedMap.put(entry.getKey().trim(), entry.getValue().trim());
		}
		return trimmedMap;
	}

	/**
	 * {@inheritDoc}
	 * @see javax.management.NotificationFilter#isNotificationEnabled(javax.management.Notification)
	 */
	@Override
	public boolean isNotificationEnabled(final Notification notification) {
		return false;
	}

	/**
	 * {@inheritDoc}
	 * @see javax.management.NotificationListener#handleNotification(javax.management.Notification, java.lang.Object)
	 */
	@Override
	public void handleNotification(final Notification notification, final Object handback) {
		// TODO Auto-generated method stub

	}

	/**
	 * {@inheritDoc}
	 * @see javax.management.NotificationBroadcaster#addNotificationListener(javax.management.NotificationListener, javax.management.NotificationFilter, java.lang.Object)
	 */
	@Override
	public void addNotificationListener(final NotificationListener listener, final NotificationFilter filter, final Object handback) throws IllegalArgumentException {
		delegateBroadcaster.addNotificationListener(listener, filter, handback);		
	}

	/**
	 * {@inheritDoc}
	 * @see javax.management.NotificationBroadcaster#removeNotificationListener(javax.management.NotificationListener)
	 */
	@Override
	public void removeNotificationListener(final NotificationListener listener) throws ListenerNotFoundException {
		delegateBroadcaster.removeNotificationListener(listener);		
	}
	
	/**
	 * {@inheritDoc}
	 * @see javax.management.NotificationEmitter#removeNotificationListener(javax.management.NotificationListener, javax.management.NotificationFilter, java.lang.Object)
	 */
	@Override
	public void removeNotificationListener(NotificationListener listener, NotificationFilter filter, Object handback) throws ListenerNotFoundException {
		delegateBroadcaster.removeNotificationListener(listener, filter, handback);		
	}
	

	/**
	 * {@inheritDoc}
	 * @see javax.management.NotificationBroadcaster#getNotificationInfo()
	 */
	@Override
	public MBeanNotificationInfo[] getNotificationInfo() {
		return NOTIF_INFO;
	}

	/**
	 * Returns the number of items in the configuration
	 * @return the number of items in the configuration
	 */
	public int size() {
		return config.size();
	}

	/**
	 * Indicates if the passed value is a config key
	 * @param key The object to test
	 * @return true if the passed value is a config key, false otherwise
	 */
	public boolean containsKey(Object key) {
		return config.containsKey(key);
	}

	/**
	 * Indicates if the passed value is a config value
	 * @param value The object to test
	 * @return true if the passed value is a config value, false otherwise
	 */
	public boolean containsValue(Object value) {
		return config.containsValue(value);
	}

	/**
	 * @return
	 * @see java.util.Map#keySet()
	 */
	public Set<String> keySet() {
		return config.keySet();
	}

	/**
	 * @return
	 * @see java.util.Map#values()
	 */
	public Collection<String> values() {
		return config.values();
	}


}
