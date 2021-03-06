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
package com.heliosapm.script.configuration;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Hashtable;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import javax.management.MBeanNotificationInfo;
import javax.management.Notification;
import javax.management.ObjectName;

import com.heliosapm.jmx.config.Configuration;
import com.heliosapm.jmx.config.ConfigurationManager;
import com.heliosapm.jmx.util.helpers.JMXHelper;
import com.heliosapm.jmx.util.helpers.URLHelper;
import com.heliosapm.script.AbstractDeployedScript;
import com.heliosapm.script.DeployedScript;

/**
 * <p>Title: ConfigurationDeployedScript</p>
 * <p>Description: Represents a file watched configuration script</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.script.ConfigurationDeployedScript</code></p>
 */

public class ConfigurationDeployedScript extends AbstractDeployedScript<Configuration> {
	/** The path of the configuration file */
	protected final Path configPath;
	
	
	/** Notification descriptor for event broadcast when a configuration source changes */
	protected static final MBeanNotificationInfo CONFIG_CHANGE_NOTIFICATION = new MBeanNotificationInfo(new String[]{NOTIF_CONFIG_MOD}, Notification.class.getName(), "Configuration source change");
	/** Notification descriptor for event broadcast when a new configuration source is registered */
	protected static final MBeanNotificationInfo CONFIG_NEW_NOTIFICATION = new MBeanNotificationInfo(new String[]{NOTIF_CONFIG_NEW}, Notification.class.getName(), "New configuration source");
	
	/**
	 * Creates a new ConfigurationDeployedScript
	 * @param sourceFile The configuration source file
	 * @param tmpConfig The temporary compiled configuration
	 */
	public ConfigurationDeployedScript(final File sourceFile, final Configuration tmpConfig) {
		super(sourceFile);
		executable = new Configuration(tmpConfig, objectName, this);		
		configPath = sourceFile.getAbsoluteFile().toPath();
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.AbstractDeployedScript#postRegister(java.lang.Boolean)
	 */
	@Override
	public void postRegister(final Boolean registrationDone) {
		super.postRegister(registrationDone);
		registerNotifications(CONFIG_CHANGE_NOTIFICATION, CONFIG_NEW_NOTIFICATION);
		ConfigurationManager.getInstance().addConfiguration(objectName, executable);
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.DeployedScript#isCommentLine(java.lang.String)
	 */
	@Override
	public boolean isCommentLine(final String text) {
		if(text==null || text.trim().isEmpty()) return false;
		String _text = text.trim();
		return (
			_text.startsWith("#")			
		);
	}

	
//	public boolean isConfigFor(String deployment) {
//		if(deployment==null || deployment.trim().isEmpty()) return false;
//		final Path dPath = Paths.get(deployment);
//		if(!dPath.toFile().exists()) return false;
//		return false;
//	}
	
	protected void closeOldExecutable(Configuration executable) {/* No Op */};
	
	/**
	 * Sends a configuration notification
	 * @param isnew The current configuration
	 */
	protected void sendConfigChangedNotification(final boolean isnew) {
		final Notification notif = new Notification((isnew ? NOTIF_CONFIG_NEW : NOTIF_CONFIG_MOD), objectName, sequence.incrementAndGet(), System.currentTimeMillis(), (isnew ? "New Configuration" : "Configuration Changed"));
		notif.setUserData(executable);
		sendNotification(notif);
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.jmx.config.InternalConfigurationListener#onConfigurationItemChange(java.lang.String, java.lang.String)
	 */
	@Override
	public void onConfigurationItemChange(String key, String value) {
		/* No Op */
	}		
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.DeployedScript#addConfiguration(java.util.Map)
	 */
	@Override
	public void addConfiguration(final Map<String, Object> config) {
		super.addConfiguration(config);
		sendConfigChangedNotification(false);
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.DeployedScript#addConfiguration(java.lang.String, java.lang.Object)
	 */
	@Override
	public void addConfiguration(final String key, final Object value)  {
		super.addConfiguration(key, value);
		sendConfigChangedNotification(false);
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.AbstractDeployedScript#addConfiguration(java.lang.String, java.lang.String)
	 */
	@Override
	public void addConfiguration(String key, String value) {
		addConfiguration(key, (Object)value);				
	}
	
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.AbstractDeployedScript#setExecutable(java.lang.Object, long, long)
	 */
	// FIXME:  Accept all configs during initial load, or updates of the underlying source for this config.
	//          .... but updates from parent config should not override values declared in this config.
	@Override
	public void setExecutable(final Configuration executable, final long checksum, final long timestamp) {
		ConfigurationManager.getInstance().addConfiguration(objectName, executable);
		super.setExecutable(executable, checksum, timestamp);
		
//		this.config.clear();
//		this.config.putAll(executable);
		sendConfigChangedNotification(false);
	}
	
	
	
//	/**
//	 * <p>Default implementation for executable, non-config deployments</p>
//	 * {@inheritDoc}
//	 * @see com.heliosapm.script.DeployedScript#getWatchedConfiguration()
//	 */
//	@Override
//	public ObjectName getWatchedConfiguration() {
//		// if this is NOT {pwd}.config, look for {pwd}.config
//		// else look for {parent}/{pwd}.config
//		final String parentDirName = sourceFile.getParentFile().getName();
//		final Hashtable<String, String> keyAttrs = new Hashtable<String, String>(objectName.getKeyPropertyList());
//		final boolean isPwd = shortName.equals(parentDirName);
//		if(!isPwd) {
//			// ====================================
//			// We're in a custom depl config
//			// ====================================			
//			keyAttrs.put("extension", "config");				
//			ObjectName watchedObjectName = JMXHelper.objectName(CONFIG_DOMAIN, keyAttrs);
//			if(JMXHelper.isRegistered(watchedObjectName)) {
//				return watchedObjectName;
//			}
//			throw new RuntimeException("Failed to find default watched configuration [" + watchedObjectName + "] for config [" + objectName + "]");
//		}
//		// ====================================
//		// We're in a pwd depl config
//		// ====================================
//		int highestDir = DeploymentType.getHighestDir(objectName);
//		String pwd = keyAttrs.remove("d" + highestDir);
//		keyAttrs.put("name", pwd);
//		ObjectName watchedObjectName = JMXHelper.objectName(CONFIG_DOMAIN, keyAttrs);
//		if(!JMXHelper.isRegistered(watchedObjectName)) {
//			throw new RuntimeException("Failed to find default watched configuration [" + watchedObjectName + "] for deployment [" + objectName + "]");
//		}
//		return watchedObjectName;
//	}
	
	
//	/**
//	 * Builds the standard JMX ObjectName for this deployment
//	 * @return an ObjectName
//	 */
//	protected ObjectName buildObjectName() {		
//		final StringBuilder b = new StringBuilder(getDomain()).append(":")
//				.append("root=").append(rootDir.replace(':', ';')).append(",");
//		for(int i = 1; i < pathSegments.length; i++) {
//			b.append("d").append(i).append("=").append(pathSegments[i]).append(",");
//		}		
//		b.append("name=").append(URLHelper.getPlainFileName(sourceFile)).append(",")
//			.append("extension=").append(extension);
//		
//		return JMXHelper.objectName(b);
//	}
	
	
	/**
	 * Compiles the path segment into a set of ObjectName keypairs
	 * @return a set of ObjectName keypairs
	 */
	protected String configDirs() {
		if(pathSegments.length<=2) return ",";
		StringBuilder b = new StringBuilder();		
		for(int i = 1; i < pathSegments.length-1; i++) {
			b.append("d").append(i).append("=").append(pathSegments[i]).append(",");
		}
		return b.toString();
	}
	
	
	
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.DeployedScript#getDomain()
	 */
	@Override
	public String getDomain() {
		return CONFIG_DOMAIN;
	}
	
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.AbstractDeployedScript#doExecute()
	 */
	@Override
	public Object doExecute() {
		return null;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.DeployedScript#invoke(java.lang.String, java.lang.Object[])
	 */
	@Override
	public Object invoke(String name, Object... args) {
		return getExecutable().get(name);
	}


	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.DeployedScript#getConfiguration()
	 */
	@Override
	public Configuration getConfiguration() {
		return executable;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.executable.DeployedExecutableMXBean#getConfigurationMap()
	 */
	@Override
	public Map<String, String> getConfigurationMap() {
		return executable.getInternalConfig();
	}
	
}
