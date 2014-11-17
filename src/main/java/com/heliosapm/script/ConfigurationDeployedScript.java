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
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import javax.management.ObjectName;

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
	}
	
	
	public boolean isConfigFor(String deployment) {
		if(deployment==null || deployment.trim().isEmpty()) return false;
		final Path dPath = Paths.get(deployment);
		if(!dPath.toFile().exists()) return false;
		return false;
	}
	
	/**
	 * Builds the standard JMX ObjectName for this deployment
	 * @return an ObjectName
	 */
	protected ObjectName buildObjectName() {
		String subExt = URLHelper.getSubExtension(sourceFile, null);
//		final StringBuilder b = new StringBuilder(getDomain()).append(":")
//				.append("path=").append(join("/", pathSegments)).append(",")
//				.append("extension=").append(extension).append(",")
//				.append("name=").append(sourceFile.getName().replace("." + extension, "").replace("." + subExt, ""));
		
		//  com.heliosapm.configuration:path=hotdir/X/Y/Z,extension=config,name=Z,subextension=js
		//  root: /tmp/hotdir
		
		// com.heliosapm.configuration:root=/tmp/hotdir,d1=X,d2=Y,d3=Z,name=Z,extension=config,subextension=js
		final StringBuilder b = new StringBuilder(getDomain()).append(":")
				.append("root=").append(rootDir).append(",");
		for(int i = 1; i < pathSegments.length; i++) {
			b.append("d").append(i).append("=").append(pathSegments[i]).append(",");
		}		
		b.append("name=").append(sourceFile.getName().replace("." + extension, "").replace("." + subExt, "")).append(",")
			.append("extension=").append(extension);
		
		if(subExt!=null) {
			b.append(",subextension=").append(subExt);
		}		
		return JMXHelper.objectName(b);
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
		return "com.heliosapm.configuration";
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
