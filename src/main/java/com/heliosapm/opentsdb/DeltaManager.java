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
package com.heliosapm.opentsdb;

import gnu.trove.map.hash.TObjectDoubleHashMap;
import gnu.trove.map.hash.TObjectLongHashMap;

import com.heliosapm.jmx.util.helpers.ConfigurationHelper;

/**
 * <p>Title: DeltaManager</p>
 * <p>Description: A utility class for managing monotonic deltas.</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.DeltaManager</code></p>
 */

public class DeltaManager {
	/** The singleton instance */
	private static volatile DeltaManager instance = null;
	/** The singleton instance ctor lock */
	private static final Object lock = new Object();
	
	/** The container for tracking long deltas */
	protected final TObjectLongHashMap<java.lang.String> longDeltas;
	/** The container for tracking double deltas */
	protected final TObjectDoubleHashMap<java.lang.String> doubleDeltas;
	
	/** The default initial capacity of the delta container */
	public static final int DELTA_CAPACITY_DEFAULT = 100;	
	/** The default load factor of the delta container */
	public static final float DELTA_LOAD_FACTOR_DEFAULT = 0.5F;
	/** The name of the system property to override the configured initial capacity of the delta container */
	protected static final String DELTA_CAPACITY = "helios.opentrace.deltas.initialcapacity";
	/** The name of the system property to override the configured load capacity of the delta container */
	protected static final String DELTA_LOAD_FACTOR = "helios.opentrace.deltas.loadfactor";
	

	/**
	 * Returns the DeltaManager singleton instance
	 * @return the DeltaManager singleton instance
	 */
	public static DeltaManager getInstance() {
		if(instance==null) {
			synchronized(lock) {
				if(instance==null) {
					instance = new DeltaManager();
				}
			}
		}
		return instance;
	}
	
	/**
	 * Creates a new DeltaManager
	 */
	private DeltaManager() {
		final int initialDeltaCapacity = ConfigurationHelper.getIntSystemThenEnvProperty(DELTA_CAPACITY, DELTA_CAPACITY_DEFAULT);
		final float initialDeltaLoadFactor = ConfigurationHelper.getFloatSystemThenEnvProperty(DELTA_LOAD_FACTOR, DELTA_LOAD_FACTOR_DEFAULT);
		longDeltas = new TObjectLongHashMap<java.lang.String>(initialDeltaCapacity, initialDeltaLoadFactor, Long.MIN_VALUE);
		doubleDeltas = new TObjectDoubleHashMap<java.lang.String>(initialDeltaCapacity, initialDeltaLoadFactor, Double.MIN_NORMAL);
	}
	
	/**
	 * Registers a sample value and returns the delta between this sample and the prior
	 * @param key The delta sample key
	 * @param value The absolute sample value
	 * @return The delta or null if this was the first sample, or the last sample caused a reset
	 */
	public Long deltaLong(final String key, final long value) {
		Long result = null;
		long prior;
		synchronized(longDeltas) {
			prior = longDeltas.put(key, value);
		}
		if(prior!=Long.MIN_VALUE && prior <= value) {
			result = value - prior;
		}
		return result;
	}
	
	/**
	 * Registers a sample value and returns the delta between this sample and the prior
	 * @param key The delta sample key
	 * @param value The absolute sample value
	 * @return The delta or null if this was the first sample, or the last sample caused a reset
	 */
	public Double deltaDouble(final String key, final double value) {
		Double result = null;
		double prior;
		synchronized(doubleDeltas) {
			prior = doubleDeltas.put(key, value);
		}
		if(prior!=Double.MIN_VALUE && prior <= value) {			
			result = value - prior;
		}
		return result;
	}
	

}
