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
package com.heliosapm.script;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import javax.management.MBeanNotificationInfo;
import javax.management.Notification;
import javax.management.ObjectName;

import com.heliosapm.jmx.config.ConfigurationManager;
import com.heliosapm.jmx.execution.ExecutionSchedule;
import com.heliosapm.jmx.util.helpers.JMXHelper;
import com.heliosapm.jmx.util.helpers.URLHelper;

/**
 * <p>Title: ConfigurationDeployedScript</p>
 * <p>Description: Represents a file watched configuration script</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.script.ConfigurationDeployedScript</code></p>
 */

public class ConfigurationDeployedScript extends AbstractDeployedScript<Map<String, Object>> {
	/** The sub extension of the config script */
	protected final String subExtension;	
	/** The path of the configuration file */
	protected final Path configPath;
	
	
	/** Notification descriptor for event broadcast when a configuration source changes */
	protected static final MBeanNotificationInfo CONFIG_CHANGE_NOTIFICATION = new MBeanNotificationInfo(new String[]{NOTIF_CONFIG_MOD}, Notification.class.getName(), "Configuration source change");
	/** Notification descriptor for event broadcast when a new configuration source is registered */
	protected static final MBeanNotificationInfo CONFIG_NEW_NOTIFICATION = new MBeanNotificationInfo(new String[]{NOTIF_CONFIG_NEW}, Notification.class.getName(), "New configuration source");
	
	/**
	 * Creates a new ConfigurationDeployedScript
	 * @param sourceFile The configuration source file
	 * @param configuration The compiled configuration
	 */
	public ConfigurationDeployedScript(final File sourceFile, final Map<String, Object> configuration) {
		super(sourceFile);
		subExtension = URLHelper.getSubExtension(sourceFile, null);
		executable = configuration;
		config.putAll(configuration);
		configPath = sourceFile.getAbsoluteFile().toPath();
		//sendConfigChangedNotification(true);		
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
	
	
//	public boolean isConfigFor(String deployment) {
//		if(deployment==null || deployment.trim().isEmpty()) return false;
//		final Path dPath = Paths.get(deployment);
//		if(!dPath.toFile().exists()) return false;
//		return false;
//	}
	
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
	@Override
	public void setExecutable(Map<String, Object> executable, long checksum, long timestamp) {
		super.setExecutable(executable, checksum, timestamp);
		this.config.clear();
		this.config.putAll(executable);
		sendConfigChangedNotification(false);
	}
	
	/**
	 * Builds the standard JMX ObjectName for this deployment
	 * @return an ObjectName
	 */
	protected ObjectName buildObjectName() {
		String subExt = URLHelper.getSubExtension(sourceFile, null);
		final StringBuilder b = new StringBuilder(getDomain()).append(":")
				.append("root=").append(rootDir.replace(':', ';')).append(",");
		for(int i = 1; i < pathSegments.length; i++) {
			b.append("d").append(i).append("=").append(pathSegments[i]).append(",");
		}		
		b.append("name=").append(sourceFile.getName().replace("." + extension, "").replace("." + subExt, "")).append(",")
			.append("extension=").append(extension);
		
		if(subExt!=null) {
			b.append(",subextension=").append(subExt);
		} else {
			b.append(",subextension=none");
		}
		return JMXHelper.objectName(b);
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.DeployedScriptMXBean#getListenOnTargets()
	 */
	@Override
	public Set<ObjectName> getListenOnTargets() {
		try {
			final boolean inRoot = sourceFile.getAbsolutePath().equals(rootDir);
			if(inRoot && (shortName.equals("root"))) return Collections.emptySet();
			final boolean inRootChild = sourceFile.getParentFile().getAbsolutePath().equals(rootDir);
			// if inRoot, and shortname not "root" listen on root.*.config
			// if inRootChild, as other plus {parent}.{shortname}.*.config ( and if shortname != pathSegment[1], {parent}.{shortname}.*.config
			
			/*
			 	X/a.js.config  -->  [X/X.*config    and     /a.*.config]  
										\							
										 --->  /root.*.config    
			
			 * 													  
			 */
			
			
			
			if(inRoot) {
				final String onformat = String.format("%s:root=%s,%sextension=config,subextension=*,name=%%s", 
						DeployedScript.CONFIG_DOMAIN, rootDir.replace(':', ';'), ",");				
				return new LinkedHashSet<ObjectName>(Arrays.asList(JMXHelper.objectName(String.format(onformat, "root"))));
			}
			
			
			final String onformat = String.format("%s:root=%s,%sextension=config,subextension=*,name=%%s", 
					DeployedScript.CONFIG_DOMAIN, rootDir.replace(':', ';'), configDirs());
					
			Set<ObjectName> set =  new LinkedHashSet<ObjectName>(Arrays.asList(
					//com.heliosapm.configuration:
						//root=C;\hprojects\heliosJMX\.\src\test\resources\testdir\hotdir,
						//d1=X,
						//d2=Y,
						// name=jmx,
						// extension=config,
						//subextension=properties
					
					JMXHelper.objectName(String.format(onformat, shortName)),
					JMXHelper.objectName(String.format(onformat, pathSegments[pathSegments.length-1]))					
			));
			if(inRootChild) {
				final String ronformat = String.format("%s:root=%s,%sextension=config,subextension=*,name=%%s", 
						DeployedScript.CONFIG_DOMAIN, rootDir.replace(':', ';'), ",");	
				set.add(JMXHelper.objectName(String.format(ronformat, "root")));
			}
			return set;
		} catch (Exception ex) {
			log.error("Failed to get listen on targets", ex);
			throw new RuntimeException("Failed to get listen on targets", ex);
		}
	}
	
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
	 * @see com.heliosapm.script.AbstractDeployedScript#locateConfiguration()
	 */
	@Override
	public Set<ObjectName> locateConfiguration() {
		return Collections.emptySet();
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
	 * @see com.heliosapm.script.DeployedScriptMXBean#getInvocables()
	 */
	@Override
	public Set<String> getInvocables() {
		return getExecutable().keySet();
	}


}
