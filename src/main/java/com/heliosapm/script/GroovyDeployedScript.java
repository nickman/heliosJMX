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
package com.heliosapm.script;

import groovy.lang.Script;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.Set;

/**
 * <p>Title: GroovyDeployedScript</p>
 * <p>Description: </p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.script.GroovyDeployedScript</code></p>
 */

public class GroovyDeployedScript extends AbstractDeployedScript<Script> {

	/**
	 * Creates a new GroovyDeployedScript
	 * @param sourceFile The groovy source file
	 * @param gscript The compiled groovy script
	 */
	public GroovyDeployedScript(File sourceFile, final Script gscript) {
		super(sourceFile);
		executable = new WeakReference<Script>(gscript);
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.DeployedScript#getInvocables()
	 */
	@Override
	public Set<String> getInvocables() {
		return new HashSet<String>(0);
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.DeployedScript#execute()
	 */
	@Override
	public Object execute() {
		return getExecutable().run();
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
