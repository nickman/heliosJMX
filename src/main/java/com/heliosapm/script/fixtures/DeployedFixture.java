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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.heliosapm.script.AbstractDeployedScript;
import com.heliosapm.script.annotations.FixtureArg;
import com.heliosapm.script.compilers.FixtureCompiler.AbstractFixture;

/**
 * <p>Title: DeployedFixture</p>
 * <p>Description: The wrapper for a deployed fixture</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.script.fixtures.DeployedFixture</code></p>
 * @param <T> The type of objects returned from the fixture
 */

public class DeployedFixture<T> extends AbstractDeployedScript<Fixture<T>> implements DeployedFixtureMXBean<T>, Fixture<T> {
	/** The public fixture name */
	protected final String fixtureName;
	/** The fixture's return type */
	protected final Class<T> fixtureType;
	/** The fixture type simple name */
	protected final String fixtureTypeName;
	/** The fixture parameter names and types */
	protected final Map<String, Class<?>> paramNameTypes = new HashMap<String, Class<?>>();
	
	/**
	 * Creates a new DeployedFixture
	 * @param sourceFile The fixture source file
	 * @param executable The fixture executable
	 */
	@SuppressWarnings("unchecked")
	public DeployedFixture(final File sourceFile, final Fixture<T> executable) {
		super(sourceFile);
		this.executable = executable;
		initExcutable();
		final com.heliosapm.script.annotations.Fixture fixtureAnnotation = 
				(com.heliosapm.script.annotations.Fixture) ((AbstractFixture)this.executable).getAnnotation(com.heliosapm.script.annotations.Fixture.class);
		if(fixtureAnnotation!=null) {
			fixtureName = fixtureAnnotation.name();
			fixtureType = (Class<T>) fixtureAnnotation.type();
//			for(FixtureArg farg: fixtureAnnotation) {
//				paramNameTypes.put(farg.name(), farg.type());
//			}
		} else {
			fixtureName = shortName;
			fixtureType = (Class<T>) getFixtureType();
		}
		fixtureTypeName = fixtureType.isPrimitive() ? fixtureType.getName() : fixtureType.getSimpleName();
		FixtureAccessor.newFixtureAccessor(this);
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.fixtures.DeployedFixtureMXBean#getParamKeys()
	 */
	@Override
	public Set<String> getParamKeys() {
		return paramNameTypes.keySet();
	}
	
	/**
	 * Returns a map of the parameter types keyed by the parameter name
	 * @return a map of the parameter types keyed by the parameter name
	 */
	public Map<String, Class<?>> getParamTypes() {
		return Collections.unmodifiableMap(paramNameTypes);
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
	 * Attempts to determine the return type of the fixture
	 * @return the return type of the fixture
	 */
	public Class<?> getFixtureType() {
		if(fixtureType!=null) return fixtureType;
		try {
			try {
				return executable.getClass().getDeclaredMethod("get", Map.class).getReturnType();
			} catch (NoSuchMethodException nse) {
				return executable.getClass().getMethod("get", Map.class).getReturnType();
			}
		} catch (Exception ex) {
			return Object.class;
		}
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.fixtures.DeployedFixtureMXBean#getFixtureTypeName()
	 */
	@Override
	public String getFixtureTypeName() {		
		return fixtureTypeName;
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.fixtures.DeployedFixtureMXBean#getFixtureName()
	 */
	@Override
	public String getFixtureName() {		
		return fixtureName;
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
