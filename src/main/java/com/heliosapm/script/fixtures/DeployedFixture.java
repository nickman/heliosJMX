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
package com.heliosapm.script.fixtures;

import java.io.File;
import java.util.Map;
import java.util.Set;

import com.heliosapm.script.AbstractDeployedScript;
import com.heliosapm.script.DeployedScript;
import com.heliosapm.script.DeployedScriptMXBean;

/**
 * <p>Title: DeployedFixture</p>
 * <p>Description: The wrapper for a deployed fixture</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.script.fixtures.DeployedFixture</code></p>
 * @param <T> The type of objects returned from the fixture
 */

public class DeployedFixture<T> extends AbstractDeployedScript<Fixture<T>> implements DeployedFixtureScriptMBean<T> {
	
	/**
	 * Creates a new DeployedFixture
	 * @param sourceFile The fixture source file
	 * @param executable The fixture executable
	 */
	public DeployedFixture(final File sourceFile, final Fixture<T> executable) {
		super(sourceFile);
		this.executable = executable;
		initExcutable();		
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.DeployedScript#getDomain()
	 */
	@Override
	public String getDomain() {
		return FIXTURE_DOMAIN;
	}
	

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.DeployedScript#invoke(java.lang.String, java.lang.Object[])
	 */
	@Override
	public Object invoke(String name, Object... args) {
		// TODO Auto-generated method stub
		return null;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.DeployedScriptMXBean#getInvocables()
	 */
	@Override
	public Set<String> getInvocables() {
		// TODO Auto-generated method stub
		return null;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.AbstractDeployedScript#doExecute()
	 */
	@Override
	protected Object doExecute() throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.fixtures.Fixture#get(java.util.Map)
	 */
	@Override
	public T get(Map<String, Object> args) {
		return executable.get(args);
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.fixtures.Fixture#get()
	 */
	@Override
	public T get() {
		return get(null);
	}

}
