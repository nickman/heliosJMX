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
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectStreamException;
import java.lang.reflect.Constructor;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

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
import org.cliffc.high_scale_lib.NonBlockingHashSet;

/**
 * <p>Title: Configuration</p>
 * <p>Description: A configuration container</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.jmx.config.Configuration</code></p>
 */

public class Configuration implements NotificationListener, NotificationFilter, NotificationEmitter, ConfigurationMBean {
	
	/**  */
	private static final long serialVersionUID = 7007249394730138439L;




	/** The standard notifications emitted by a Configuration */
	private static final MBeanNotificationInfo[] NOTIF_INFO = new MBeanNotificationInfo[] {
		new MBeanNotificationInfo(new String[] {NOTIF_CONFIG_ATTR_CHANGE}, AttributeChangeNotification.class.getName(), "Notification emitted when a configuration item is inserted, changed or removed"),
		new MBeanNotificationInfo(new String[] {NOTIF_CONFIG_CHANGE}, Notification.class.getName(), "Notification emitted when a configuration loads an update from a parent"),
		new MBeanNotificationInfo(new String[] {NOTIF_ALL_DEPS_OK}, Notification.class.getName(), "Notification emitted when all declared dependencies are satisfied")
	}; 
	
	
	
	
	/** Notification sequence number provider */
	private static final AtomicLong sequence = new AtomicLong(0L);
	/** A map of property editors keyed by the class the editor is for */
	private static final Map<Class<?>, Constructor<PropertyEditor>> editors = new NonBlockingHashMap<Class<?>, Constructor<PropertyEditor>>();
	
	
	/** The JMX ObjectName of the owner of this configuration */
	final ObjectName objectName;
	/** The internal configuration listener */
	final AtomicReference<InternalConfigurationListener> internalListener = new AtomicReference<InternalConfigurationListener>(null); 
	/** The delegate broadcaster */
	protected final NotificationBroadcasterSupport delegateBroadcaster;
	/** The actual config store */
	protected final Map<String, String> config = new NonBlockingHashMap<String, String>();
	/** The original key/value pairs loaded into this config (i.e. not loaded from any parents */
	protected final Map<String, String> internalConfig = new NonBlockingHashMap<String, String>();
	/** Locally installed property editors for this instance */
	protected final Map<Class<?>, PropertyEditor> localEditors = new NonBlockingHashMap<Class<?>, PropertyEditor>();
	
	/** The keys for dependencies declared by a deployment containing this Configuration */
	protected final Set<String> pendingDependencies = new NonBlockingHashSet<String>();
	/** The types of the dependencies keyed by the dependency key */
	protected final Map<String, Class<?>> dependencies = new NonBlockingHashMap<String, Class<?>>();
	/** Maps known config item types to the config item key */
	private final NonBlockingHashMap<String, Class<?>> configTypeMap = new NonBlockingHashMap<String, Class<?>>();
	
	
	
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
	 * Creates a new temporary holding Configuration.
	 * Don't use this unless you know what you're doing. 
	 */
	public Configuration() {
		this.delegateBroadcaster = null;
		this.objectName = null;
	}
	
	/**
	 * Creates a new Configuration
	 * @param tempConf A temporary holding container
	 * @param objectName The JMX ObjectName of the owner of this configuration
	 * @param delegateBroadcaster The delegate broadcaster
	 */
	public Configuration(final Configuration tempConf, final ObjectName objectName, final NotificationBroadcasterSupport delegateBroadcaster) {
		this(objectName, delegateBroadcaster);
		config.putAll(tempConf.config);
		internalConfig.putAll(tempConf.internalConfig);		
	}
	
//	/**
//	 * Returns the config and internal config hash maps when serialized
//	 * @return the serializable content for this class
//	 * @throws ObjectStreamException thrown on serialization errors
//	 */
//	@SuppressWarnings("rawtypes")
//	Object writeReplace() throws ObjectStreamException {
//		return new HashMap[] {
//				(HashMap) config, (HashMap) internalConfig
//		};
//	}
	
//	private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
//		
//	}
	
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
			final InternalConfigurationListener intListener = internalListener.get();
			if(intListener!=null) {
				intListener.onConfigurationItemChange(key, value);
			}
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
	 * Clears this config.
	 */
	public void clear() {
		internalListener.set(null);
		config.clear();
		dependencies.clear();
		internalConfig.clear();
		pendingDependencies.clear();
		localEditors.clear();
		configTypeMap.clear();
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
	 * Internal triggered put
	 * @param key The config item key
	 * @param value The config item value
	 */
	private void _put(final String key, final String value) {
		if(key==null || key.trim().isEmpty()) throw new IllegalArgumentException("The passed key was null or empty");
		if(value==null || value.trim().isEmpty()) return;
		final String _key = key.trim();
		final String _value = value.trim();
		final String oldValue = config.put(_key, _value);
		if(pendingDependencies.remove(_key)) {
			if(pendingDependencies.isEmpty()) {
				delegateBroadcaster.sendNotification(new Notification(NOTIF_ALL_DEPS_OK, objectName, sequence.incrementAndGet(), System.currentTimeMillis(), "All declared dependencies satisfied for [" + objectName + "]"));
			}
		}
		if(!_value.equals(oldValue)) {
			final long now = System.currentTimeMillis();
			if(oldValue==null) {
				delegateBroadcaster.sendNotification(new AttributeChangeNotification(objectName, sequence.incrementAndGet(), now, String.format("#%s Inserted new config\n%s=%s\n", key, key, value), key, String.class.getName(), oldValue, value));
			} else {
				delegateBroadcaster.sendNotification(new AttributeChangeNotification(objectName, sequence.incrementAndGet(), now, String.format("#%s Updated config\n%s=%s\n", key, key, value), key, String.class.getName(), oldValue, value));
			}		
		}
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.jmx.config.ConfigurationMBean#put(java.lang.String, java.lang.String)
	 */
	@Override
	public void put(final String key, final String value) {
		if(value==null) return;
		validateInsertedStringDependency(key, value); 
		_put(key, value);		
	}
	
	
	/**
	 * Returns the assumed type of the config item value for the specified config item key
	 * @param key The config item key
	 * @return The determined type, or if not specified, simply String.
	 */
	public Class<?> getConfigItemType(final String key) {
		Class<?> clazz = dependencies.get(key);
		if(clazz==null) clazz = configTypeMap.get(key);
		return clazz != null ? clazz : String.class;
	}
	
	/**
	 * Inserts or updates a typed configuration item.
	 * If this operation changes the config, will fire listeners and notifications
	 * @param key The config item key
	 * @param value The config item value
	 */
	public <T> void putTyped(final String key, final T value) {		
		if(value==null) return;
		validateInsertedDependency(key, value);
		if(!(value instanceof String)) {
			configTypeMap.put(key, value.getClass());
		}
		_put(key, toText(value));		
	}
	
	/**
	 * Adds a dependency of the specified type
	 * @param key The key of the dependency
	 * @param type The type that the dependency value must be of to satisfy the dependency
	 */
	public void addDependency(final String key, final Class<?> type) {
		if(key==null || key.trim().isEmpty()) throw new IllegalArgumentException("The passed key was null or empty");
		final String _key = key.trim();		
		dependencies.put(_key, type==null ? Object.class : type);
	}
	
	/**
	 * Returns a config map with the values types as closely as possible
	 * @return a typed config map
	 */
	public Map<String, Object> getTypedConfigMap() {
		Map<String, Object> map = new HashMap<String, Object>(config.size());
		for(final Map.Entry<String, String> es: config.entrySet()) {
			final String key = es.getKey();
			Class<?> type = getConfigItemType(key);
			if(type==String.class) {
				map.put(key, es.getValue());
			} else {
				Object val = get(key, type);
				map.put(key, val);
			}
		}
		return map;
	}
	
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.jmx.config.ConfigurationMBean#isDependencyClosed(java.lang.String)
	 */
	@Override
	public <T> boolean isDependencyClosed(final String key) {
		if(key==null || key.trim().isEmpty()) throw new IllegalArgumentException("The passed key was null or empty");
		final String _key = key.trim();
		if(pendingDependencies.contains(_key)) return false;
		final Class<T> dependencyType = (Class<T>) dependencies.get(_key);
		if(dependencyType==null) return true;
		T value = get(_key, dependencyType);
		return value != null;		
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.jmx.config.ConfigurationMBean#getDependencyKeys()
	 */
	@Override
	public Set<String> getDependencyKeys() {
		return Collections.unmodifiableSet(dependencies.keySet());
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.jmx.config.ConfigurationMBean#getPendingDependencyKeys()
	 */
	@Override
	public Set<String> getPendingDependencyKeys() {
		return Collections.unmodifiableSet(pendingDependencies);
	}
	
	/**
	 * Validates a provided config value when the config key is declared as a dependency
	 * @param key The config key
	 * @param value The config value
	 */
	protected <T> void validateInsertedDependency(final String key, final Object value) {
		final Class<T> dtype = (Class<T>) dependencies.get(key);
		if(dtype==null || !pendingDependencies.contains(key)) return;
		if(!dtype.isInstance(value)) {
			throw new RuntimeException("Incorrect value for dependency config item [" + key + "]. Type was [" + value.getClass().getName() + "] but was expecting [" + dtype.getName() + "]");
		}		
	}
	
	/**
	 * Validates a provided config value when the config key is declared as a dependency
	 * @param key The config key
	 * @param value The config value
	 */
	protected <T> void validateInsertedStringDependency(final String key, final String value) {
		final Class<T> dtype = (Class<T>) dependencies.get(key);
		if(dtype==null || !pendingDependencies.contains(key)) return;
		if(dtype.isInstance(value)) return;  // dep type was satisfied by value
		// try prop editor
		try {
			fromText(value, dtype);
			// works for me			
		} catch (Exception ex) {
			throw new RuntimeException("Incorrect value for dependency config item [" + key + "]. Was expecting [" + dtype.getName() + "] but value [" + value + "] could not be converted by property editor", ex);
		}
	}
	
	
	
	
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
	 * Registers an internal listener
	 * @param listener the listener to register
	 */
	public void registerInternalListener(final InternalConfigurationListener listener) {
		if(listener==null) throw new IllegalArgumentException("The passed InternalConfigurationListener was null");
		internalListener.set(listener);
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

	/**
	 * @return
	 */
	public Set<Map.Entry<String, String>> entrySet() {
		return new HashSet<Map.Entry<String, String>>(new HashMap<String, String>(config).entrySet());
	}


}
