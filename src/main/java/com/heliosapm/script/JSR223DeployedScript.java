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
import javax.script.SimpleBindings;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;

/**
 * <p>Title: JSR223DeployedScript</p>
 * <p>Description: A deployment for JSR233 scripted deployments</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.script.JSR223DeployedScript</code></p>
 */

public class JSR223DeployedScript extends AbstractDeployedScript<CompiledScript> {

	
	static {
		ContextFactory.initGlobal(new ContextFactory() {
            protected Context makeContext() {
                Context cx = super.makeContext();
                cx.setOptimizationLevel(9);
                return cx;
            }
        });		
	}
	
	/**
	 * Creates a new JSR223DeployedScript
	 * @param sourceFile The source file
	 * @param cs The compiled script
	 */
	public JSR223DeployedScript(final File sourceFile, final CompiledScript cs) {
		super(sourceFile);
		executable = cs;
		initExcutable();		
		locateConfigFiles(sourceFile, rootDir, pathSegments);
		
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.AbstractDeployedScript#initExcutable()
	 */
	@Override
	public void initExcutable() {
		if(executable!=null) {
			executable.getEngine().setBindings(config, ScriptContext.ENGINE_SCOPE);
			setStatus(DeploymentStatus.READY);
		}
		log.info("Output: [{}]", executable.getEngine().getFactory().getOutputStatement("foo"));
		super.initExcutable();
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.AbstractDeployedScript#onConfigurationItemChange(java.lang.String, java.lang.String)
	 */
	@Override
	public final void onConfigurationItemChange(final String key, final String value) {
		// none required.
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
	 * @see com.heliosapm.script.AbstractDeployedScript#doExecute()
	 */
	@Override
	public Object doExecute() throws Exception {
		return getExecutable().eval();
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
