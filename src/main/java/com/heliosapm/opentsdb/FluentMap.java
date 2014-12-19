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

import java.util.LinkedHashMap;
import java.util.Stack;

/**
 * <p>Title: FluentMap</p>
 * <p>Description: A fluent style tag map to simplify the creation of metrics</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.FluentMap</code></p>
 */

public class FluentMap extends LinkedHashMap<String, String> {
	/**  */
	private static final long serialVersionUID = -344273571991010543L;
	/** A stack of the keys so we can pop the most recently added */
	private final Stack<String> keys = new Stack<String>();
	
	/**
	 * Creates a new FluentMap
	 */
	public FluentMap() {
		this(6);
	}
	
	/**
	 * Creates a new FluentMap
	 * @param size The initial size of the map
	 */
	public FluentMap(final int size) {
		super(size);
	}
	
	/**
	 * Adds a tag to the map
	 * @param key The key
	 * @param value The value
	 * @return this map
	 */
	public FluentMap append(final String key, final String value) {
		if(key==null || key.trim().isEmpty()) throw new IllegalArgumentException("The passed key was null or empty");
		if(value==null || value.trim().isEmpty()) throw new IllegalArgumentException("The passed value was null or empty");
		put(clean(key), clean(value));
		keys.push(clean(key));
		return this;
	}
	
	public FluentMap pop(final int popCount) {
		for(int i = 0; i < popCount; i++) {
			String key = keys.peek();
			if(remove(key)!=null) {
				keys.pop();
			}
		}
		return this;
	}
	
	public FluentMap pop() {
		return pop(1);
	}
	
	public void clear() {
		super.clear();
		keys.clear();
	}
	
	public FluentMap aclear() {
		this.clear();
		return this;
	}
	
	
	
	
	/**
	 * Cleans the passed stringy
	 * @param cs The stringy to clean
	 * @return the cleaned stringy
	 */
	public static String clean(final CharSequence cs) {
		if(cs==null || cs.toString().trim().isEmpty()) return "";
		String s = cs.toString().trim();
		final int index = s.indexOf('/');
		if(index!=-1) {
			s = s.substring(index+1);
		}
		return s.replace(" ", "_");
	}
	
	
	

}
