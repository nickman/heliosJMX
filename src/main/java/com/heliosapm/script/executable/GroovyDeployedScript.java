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
package com.heliosapm.script.executable;

import groovy.lang.Binding;
import groovy.lang.MetaMethod;
import groovy.lang.MetaProperty;
import groovy.lang.Script;

import java.io.File;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.heliosapm.script.AbstractDeployedScript;
import com.heliosapm.script.annotations.Dependencies;
import com.heliosapm.script.annotations.Dependency;
import com.heliosapm.script.annotations.Scheduled;

/**
 * <p>Title: GroovyDeployedScript</p>
 * <p>Description: </p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.script.GroovyDeployedScript</code></p>
 */

public class GroovyDeployedScript extends AbstractDeployedScript<Script> implements DeployedExecutableMXBean {
	/** The script meta methods */
	protected final Map<String, MetaMethod> metaMethods = new ConcurrentHashMap<String, MetaMethod>();
	/** The script meta properties */
	protected final Map<String, MetaProperty> metaProperties = new ConcurrentHashMap<String, MetaProperty>();
	/** The groovy binding */
	protected final Binding binding;

	/**
	 * Creates a new GroovyDeployedScript
	 * @param sourceFile The groovy source file
	 * @param gscript The compiled groovy script
	 */
	public GroovyDeployedScript(File sourceFile, final Script gscript) {
		super(sourceFile);
		executable = gscript;				
		locateConfigFiles(sourceFile, rootDir, pathSegments);
		binding = new Binding();
		binding.setProperty(BINDING_NAME, binding);
		initExcutable();		
	}
	
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.DeployedScript#initExcutable()
	 */
	public void initExcutable() {
		if(executable!=null) {
			try {
				metaMethods.clear();
				for(MetaMethod mm: executable.getMetaClass().getMethods()) {
					metaMethods.put(mm.getName(), mm);
				}
				metaProperties.clear();
				for(MetaProperty mp: executable.getMetaClass().getProperties()) {
					metaProperties.put(mp.getName(), mp);
				}
				updateBindings();
				executable.setBinding(binding);
				config.addDependency(executable.getClass().getAnnotation(Dependencies.class));
				config.addDependency(executable.getClass().getAnnotation(Dependency.class));
				
				final Scheduled scheduled = executable.getClass().getAnnotation(Scheduled.class);
				if(scheduled!=null) {
					this.setExecutionSchedule(scheduled.value());
				}
			} catch (Exception ex) {
				ex.printStackTrace(System.err);
			}
		}		
		super.initExcutable();
	}
	
	/**
	 * Updates the groovy binding from the config
	 */
	protected void updateBindings() {
		for(Map.Entry<String, Object> entry: config.getTypedConfigMap().entrySet()) {
			binding.setProperty(entry.getKey(), entry.getValue());
		}		
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.AbstractDeployedScript#onConfigurationItemChange(java.lang.String, java.lang.String)
	 */
	@Override
	public void onConfigurationItemChange(final String key, final String value) {
		final Object val = config.getTypedConfigMap().get(key);
		binding.setProperty(key, val);
		super.onConfigurationItemChange(key, value);
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.executable.DeployedExecutableMXBean#getDeploymentClassName()
	 */
	@Override
	public String getDeploymentClassName() {
		if(executable==null) return null;
		return executable.getClass().getSuperclass().getName();
	}
	

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.DeployedScript#getInvocables()
	 */
	@Override
	public Set<String> getInvocables() {		
		final Set<String> invNames = new HashSet<String>(metaProperties.size() + metaMethods.size());
		invNames.addAll(metaProperties.keySet());
		invNames.addAll(metaMethods.keySet());
		return invNames;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.AbstractDeployedScript#doExecute()
	 */
	@Override
	public Object doExecute() throws Exception {
		return executable.run();
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.DeployedScript#invoke(java.lang.String, java.lang.Object[])
	 */
	@Override
	public Object invoke(String name, Object... args) {
		return getExecutable().invokeMethod(name, args);
	}

}
