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

import java.util.Map;
import java.util.Set;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.heliosapm.jmx.util.helpers.JMXHelper;
import com.heliosapm.jmx.util.helpers.JMXHelper.MBeanEventHandler;
import com.heliosapm.script.DeployedScript;

/**
 * <p>Title: FixtureAccessor</p>
 * <p>Description: Provides a direct invoker to deployed fixtures which are otherwise limited by
 * the MXBean interface which is the only other acces point to fixtures.</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.script.fixtures.FixtureAccessor</code></p>
 * @param <T> The type returned by the wrapped fixture
 */

public class FixtureAccessor<T>  implements FixtureAccessorMBean<T> {
	/** The fixture this accessor invokes against */
	protected final DeployedFixture<T> fixture;
	/** The accessor JMX ObjectName */
	protected final ObjectName objectName;
	/** Instance logger */
	protected final Logger log;
	
	/**
	 * Creates a new FixtureAccessor for the passed fixture
	 * @param fixture the fixture to create the accessor for
	 * @return The created FixtureAccessor
	 */
	public static <T> FixtureAccessor<T> newFixtureAccessor(final DeployedFixture<T> fixture) {
		return new FixtureAccessor<T>(fixture);
	}
	
	
	/**
	 * Creates a new FixtureAccessor
	 * @param fixture The fixture this accessor invokes against
	 */
	public FixtureAccessor(final DeployedFixture<T> fixture) {
		this.fixture = fixture;

//		@Fixture(name="JMXConnector", type=javax.management.remote.JMXConnector.class, params=[
//            @FixtureArg(name="jmxUrl", type=java.lang.String.class)
		
		
		log = LoggerFactory.getLogger(getClass().getName() + "." + fixture.getFixtureName());
		objectName = JMXHelper.objectName(DeployedScript.FIXTURE_DOMAIN + ".invokers:name=" + fixture.getFixtureName() + ",type=" + fixture.getFixtureTypeName());
		JMXHelper.registerMBean(this, objectName);
		log.info("Registered Fixture Invoker [{}]", objectName);
		JMXHelper.onMBeanUnregistered(objectName, new MBeanEventHandler() {			
			public void onEvent(final MBeanServerConnection connection, final ObjectName on, final boolean reg) {
				JMXHelper.unregisterMBean(objectName);
				log.info("Unregistered Fixture Invoker [{}]", objectName);
			}
		});		
		FixtureCache.getInstance().put(this);
	}
	
	/**
	 * Returns a set of the parameters keys
	 * @return a set of the parameters keys
	 */
	public Set<String> getParamKeys() {
		return fixture.getParamKeys();
	}
	
	/**
	 * Returns a map of the parameter types keyed by the parameter name
	 * @return a map of the parameter types keyed by the parameter name
	 */
	public Map<String, Class<?>> getParamTypes() {
		return fixture.getParamTypes();
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.fixtures.Fixture#get(java.util.Map)
	 */
	@Override
	public T get(final Map<String, Object> config) {	
		return fixture.get(config);
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.fixtures.Fixture#get()
	 */
	@Override
	public T get() {
		return fixture.get();
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.fixtures.FixtureAccessorMBean#getObjectName()
	 */
	@Override
	public ObjectName getObjectName() {
		return objectName;
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.fixtures.FixtureAccessorMBean#getFixtureName()
	 */
	@Override
	public String getFixtureName() {
		return fixture.getFixtureName();
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.fixtures.FixtureAccessorMBean#getFixtureType()
	 */
	@Override
	public Class<T> getFixtureType() {
		return (Class<T>) fixture.getFixtureType();
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.fixtures.FixtureAccessorMBean#getFixtureTypeName()
	 */
	@Override
	public String getFixtureTypeName() {
		return fixture.getFixtureTypeName();
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.fixtures.FixtureAccessorMBean#getFixtureObjectName()
	 */
	@Override
	public ObjectName getFixtureObjectName() {		
		return fixture.getObjectName();
	}


	/**
	 * {@inheritDoc}
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("FixtureAccessor [");
		builder.append("name:").append(getFixtureName()).append(", type:").append(getFixtureTypeName());
		builder.append("]");
		return builder.toString();
	}
	
	 

}
