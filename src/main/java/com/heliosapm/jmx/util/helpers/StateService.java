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
package com.heliosapm.jmx.util.helpers;

import org.cliffc.high_scale_lib.NonBlockingHashMap;

/**
 * <p>Title: StateService</p>
 * <p>Description: Singleton service for saving state and providing numerical deltas</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.jmx.util.helpers.StateService</code></p>
 */

public class StateService { 
	/** The singleton instance */
	private static volatile StateService instance = null;
	/** The singleton instance ctor lock */
	private static final Object lock = new Object();
	
	/** The simple state cache  */
	private final NonBlockingHashMap<Object, Object> simpleStateCache = new NonBlockingHashMap<Object, Object>(); 
	/** The cache for long deltas */
	private final NonBlockingHashMap<Object, long[]> longDeltaCache = new NonBlockingHashMap<Object, long[]>(); 
	/** The cache for double deltas */
	private final NonBlockingHashMap<Object, double[]> doubleDeltaCache = new NonBlockingHashMap<Object, double[]>(); 
	
	/**
	 * Acquires the StateService singleton instance
	 * @return the StateService singleton instance
	 */
	public static StateService getInstance() {
		if(instance==null) {
			synchronized(lock) {
				if(instance==null) {
					instance = new StateService();
				}
			}
		}
		return instance;
	}
	
	
	
	/**
	 * Saves a simple keyed state
	 * @param key The key for the saved state
	 * @param value The value for the saved state
	 */
	public void state(final Object key, final Object value) {
		
	}
	
	/**
	 * Creates a new StateService
	 */
	private StateService() {
	}

}
