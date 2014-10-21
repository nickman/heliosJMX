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

import java.lang.management.ManagementFactory;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.management.ObjectName;

import com.heliosapm.jmx.util.helpers.JMXHelper;

/**
 * <p>Title: Transformers</p>
 * <p>Description: Some standard transforms</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.Transformers</code></p>
 */

public class Transformers {

	/**
	 * <p>Title: GCTransformer</p>
	 * <p>Description: Transformer to format standard GC MXBean collected data</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.opentsdb.GCTransformer</code></p>
	 */
	public static class GCTransformer implements TSDBJMXResultTransformer {
		/** The last collection timestamp */
		protected long lastSampleTimeStamp = -1L;
		
		/** The filter this transformer applies to */
		public static final ObjectName FILTER = JMXHelper.objectName(ManagementFactory.GARBAGE_COLLECTOR_MXBEAN_DOMAIN_TYPE + ",*");
		
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.TSDBJMXResultTransformer#transform(javax.management.ObjectName, java.util.Map)
		 */
		@Override
		public Map<ObjectName, Number> transform(final ObjectName objectName, final Map<String, Object> attributes) {
			if(objectName==null || attributes==null || attributes.isEmpty()) return Collections.emptyMap();
			if(!FILTER.apply(objectName)) return Collections.emptyMap();
			String objNamePrefix = "";
			Map<ObjectName, Number> results = new HashMap<ObjectName, Number>();
			for(Map.Entry<String, Object> entry: attributes.entrySet()) {
				
			}
			return results;
		}
	}
	
	
	/**
	 * Calcs a percentage
	 * @param amt The partial amount
	 * @param total The total amount
	 * @return The percentage that the amount is of the total
	 */
	public static double percent(final double amt, final double total) {
		if(amt==0 || total==0) return 0d;
		return amt/total*100;
	}
		
		
		
		
	
	
	private Transformers() {}

}
