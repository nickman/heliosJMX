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
import java.lang.ref.WeakReference;
import java.util.Set;

import javax.script.CompiledScript;
import javax.script.Invocable;
import javax.script.ScriptContext;

/**
 * <p>Title: JavaxScriptDeployedScript</p>
 * <p>Description: </p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.script.JavaxScriptDeployedScript</code></p>
 */

public class JavaxScriptDeployedScript extends AbstractDeployedScript<CompiledScript> {

	/**
	 * Creates a new JavaxScriptDeployedScript
	 * @param sourceFile The source file
	 * @param cs The compiled script
	 */
	public JavaxScriptDeployedScript(final File sourceFile, final CompiledScript cs) {
		super(sourceFile);
		executable = new WeakReference<CompiledScript>(cs);
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.DeployedScript#getInvocables()
	 */
	@Override
	public Set<String> getInvocables() {
		return getExecutable().getEngine().getContext().getBindings(ScriptContext.ENGINE_SCOPE).keySet();		
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.DeployedScript#execute()
	 */
	@Override
	public Object execute() {
		try {
			return getExecutable().eval();
		} catch (Exception ex) {
			throw new RuntimeException("Failed to execute deployed script [" + this.getFileName() + "]", ex);
		}
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.DeployedScript#invoke(java.lang.String, java.lang.Object[])
	 */
	@Override
	public Object invoke(String name, Object... args) {
		try {
			Invocable inv = (Invocable)getExecutable();
			return inv.invokeFunction(name, args);
		} catch (Exception ex) {
			throw new RuntimeException("Failed to execute invocable [" + name + "] in script [" + this.getFileName() + "]", ex);
		}
	}

}
