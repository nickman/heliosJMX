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
package com.heliosapm.opentsdb;

import javax.management.ObjectName;

import org.cliffc.high_scale_lib.NonBlockingHashMap;

/**
 * <p>Title: TransformCache</p>
 * <p>Description: A cache to store and look up linkages between JMX {@link ObjectName}s and JMX result transformers</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.TransformCache</code></p>
 */

public class TransformCache {

	/** Transforms keyed by object name patterns */
	protected final NonBlockingHashMap<ObjectName, TSDBJMXResultTransformer> patternMatch = new  NonBlockingHashMap<ObjectName, TSDBJMXResultTransformer>(256);
	/** Transforms keyed by concrete (non-pattern) object names */
	protected final NonBlockingHashMap<ObjectName, TSDBJMXResultTransformer> exactMatch = new  NonBlockingHashMap<ObjectName, TSDBJMXResultTransformer>(256);
	

	/**
	 * Registers a transformer
	 * @param objectName The object name that the transformer will be associated to
	 * @param transformer The transformer to register
	 * @param overwrite If true, will overwrite an existing registration, otherwise will throw an exception if an existing registration already exists for the ObjectName
	 */
	public void register(final ObjectName objectName, final TSDBJMXResultTransformer transformer, final boolean overwrite) {
		if(objectName==null) throw new IllegalArgumentException("The passed ObjectName was null");
		if(transformer==null) throw new IllegalArgumentException("The passed transformer was null");
		if(objectName.isPattern()) {
			if(!overwrite && patternMatch.containsKey(objectName)) throw new RuntimeException("A transformer is already registered for [" + objectName + "]");
			patternMatch.put(objectName, transformer);
		} else {
			if(!overwrite && exactMatch.containsKey(objectName)) throw new RuntimeException("A transformer is already registered for [" + objectName + "]");
			exactMatch.put(objectName, transformer);
		}
	}
	
	/**
	 * Registers a transformer
	 * @param objectName The object name that the transformer will be associated to
	 * @param transformer The transformer to register
	 */
	public void register(final ObjectName objectName, final TSDBJMXResultTransformer transformer) {
		register(objectName, transformer, false);
	}
	
	/**
	 * Tries to find a transformer for the passed ObjectName
	 * @param objectName The ObjectName to get a transformer for
	 * @return the matched transformer or null if one was not found
	 */
	public TSDBJMXResultTransformer getTransformer(final ObjectName objectName) {
		if(objectName==null) throw new IllegalArgumentException("The passed ObjectName was null");		
		if(objectName.isPattern()) {
			return patternMatch.get(objectName);
		}
		ObjectName pmatch = null;
		TSDBJMXResultTransformer transformer = exactMatch.get(objectName);
		if(transformer==null) {
			for(final ObjectName pattern: patternMatch.keySet()) {
				if(pattern.apply(objectName)) {
					pmatch = pattern;
					break;
				}
			}
			if(pmatch!=null) {
				transformer = patternMatch.get(pmatch);
				exactMatch.put(objectName, transformer);
			}
		}
		return transformer;
	}
	
}
