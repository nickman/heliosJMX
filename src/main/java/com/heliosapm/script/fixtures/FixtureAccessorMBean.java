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

import javax.management.ObjectName;


/**
 * <p>Title: FixtureAccessorMBean</p>
 * <p>Description: Simple MBean interface for {@link FixtureAccessor}s</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.script.fixtures.FixtureAccessorMBean</code></p>
 * @param <T> The type returned by the invoked fixture
 */

public interface FixtureAccessorMBean<T> extends Fixture<T> {
	/**
	 * Returns a set of the parameters keys
	 * @return a set of the parameters keys
	 */
	public Set<String> getParamKeys();
	
	/**
	 * Returns a map of the parameter types keyed by the parameter name
	 * @return a map of the parameter types keyed by the parameter name
	 */
	public Map<String, Class<?>> getParamTypes();
	
	/**
	 * Returns the fixture accessor's ObjectName
	 * @return the fixture accessor's ObjectName
	 */
	public ObjectName getObjectName();
	
	/**
	 * Returns the fixture's ObjectName
	 * @return the fixture's ObjectName
	 */
	public ObjectName getFixtureObjectName();
	
	
	/**
	 * Returns the fixture's name
	 * @return the fixture's name
	 */
	public String getFixtureName();
	
	/**
	 * Returns the fixutre's return type
	 * @return the fixutre's return type
	 */
	public Class<T> getFixtureType();
	
	/**
	 * Returns the fixutre's return type name
	 * @return the fixutre's return type name
	 */
	public String getFixtureTypeName();
	
}
