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

import java.lang.management.ManagementFactory;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.heliosapm.jmx.cache.CacheStatistics;
import com.heliosapm.jmx.util.helpers.ConfigurationHelper;
import com.heliosapm.jmx.util.helpers.JMXHelper;
import com.heliosapm.jmx.util.helpers.JMXHelper.MBeanEventHandler;

/**
 * <p>Title: FixtureCache</p>
 * <p>Description: </p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.script.fixtures.FixtureCache</code></p>
 */

public class FixtureCache {
	/** The singleton instance */
	protected static volatile FixtureCache instance = null;
	/** The singleton instance ctor lock */
	protected static Object lock = new Object();
	
	/** The conf property name for the cache spec for the fixture cache */
	public static final String STATE_CACHE_PROP = "com.heliosapm.script.fixtures.simplecachespec";
	/** The number of processors in the current JVM */
	public static final int CORES = ManagementFactory.getOperatingSystemMXBean().getAvailableProcessors();
	/** The default cache spec */
	public static final String STATE_CACHE_DEFAULT_SPEC = 
		"concurrencyLevel=" + CORES + "," + 
		"initialCapacity=256," + 
		"maximumSize=5120," + 
		"weakValues" +
		",recordStats";

	/** The fixture cache  */
	private final Cache<String, FixtureAccessor<?>> fixtureCache = CacheStatistics.getJMXStatisticsEnableCache(CacheBuilder.from(ConfigurationHelper.getSystemThenEnvProperty(STATE_CACHE_PROP, STATE_CACHE_DEFAULT_SPEC)), "fixtures"); 

	
	/**
	 * Acquires and returns the FixtureCache singleton instance
	 * @return the FixtureCache singleton instance
	 */
	public static FixtureCache getInstance() {
		if(instance==null) {
			synchronized(lock) {
				if(instance==null) {
					instance = new FixtureCache();
				}
			}
		}
		return instance;
	}
	/**
	 * Creates a new FixtureCache
	 */
	private FixtureCache() {
	}
	
	/**
	 * Registers the passed fixture in the cache and returns it
	 * @param fixture The fixture to register
	 * @return the registered fixture
	 */
	public <T> FixtureAccessor<T> put(final FixtureAccessor<T> fixture) {
		if(fixture==null) throw new IllegalArgumentException("The passed fixture was null");
		fixtureCache.put(fixture.getFixtureName(), fixture);
		JMXHelper.onMBeanUnregistered(fixture.getObjectName(), new MBeanEventHandler() {
			@Override
			public void onEvent(final MBeanServerConnection connection, final ObjectName objectName, final boolean reg) {
				fixtureCache.invalidate(fixture.getFixtureName());
			}
		});
		return fixture;
	}
	
	/**
	 * Returns the named fixture accessor
	 * @param fixtureName The name of the fixture
	 * @return the named fixture accessor or null if it was not found
	 */
	public <T> FixtureAccessor<T> get(final String fixtureName) {
		return (FixtureAccessor<T>) fixtureCache.getIfPresent(fixtureName);
	}
	

}
