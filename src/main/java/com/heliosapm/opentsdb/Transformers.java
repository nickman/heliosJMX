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
import java.util.LinkedHashMap;
import java.util.Map;

import javax.management.ObjectName;

import com.heliosapm.SimpleLogger;
import com.heliosapm.SimpleLogger.SLogger;
import com.heliosapm.jmx.util.helpers.JMXHelper;

/**
 * <p>Title: Transformers</p>
 * <p>Description: Some standard transforms</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.Transformers</code></p>
 */

public class Transformers {

	static abstract class AbstractTransformer implements TSDBJMXResultTransformer {
		/** The last collection timestamp */
		protected long lastSampleTimeStamp = -1L;
		/** The object name prefix of the transform map */
		protected final String objNamePrefix;
		/** The base tags */
		protected final Map<String, String> baseTags = new LinkedHashMap<String, String>();
		
		protected final SLogger log;
		
		/**
		 * Creates a new AbstractTransformer
		 * @param baseTags The base tags for this transformer
		 * @param objNamePrefix The object name prefix of the transform map
		 */
		AbstractTransformer(final Map<String, String> baseTags, final String objNamePrefix) {
			log = SimpleLogger.logger(getClass());
			this.objNamePrefix = objNamePrefix==null ? "" : objNamePrefix.trim();
			if(baseTags!=null && !baseTags.isEmpty()) {
				for(Map.Entry<String, String> entry: baseTags.entrySet()) {
					if(entry.getKey()==null || entry.getKey().trim().isEmpty() || entry.getValue()==null || entry.getValue().trim().isEmpty()) continue;
					this.baseTags.put(entry.getKey().trim(), entry.getValue().trim());
				}
			}
		}
		
		/**
		 * Returns the ObjectName filter that determines what traces this transform applies to
		 * @return the ObjectName filter
		 */
		abstract ObjectName getFilter();
		
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.TSDBJMXResultTransformer#transform(javax.management.ObjectName, java.util.Map)
		 */
		@Override
		public Map<ObjectName, Number> transform(final ObjectName objectName, final Map<String, Object> attributes) {
			try {
				if(objectName==null || attributes==null || attributes.isEmpty()) return Collections.emptyMap();
				if(getFilter()!=null) if(!getFilter().apply(objectName)) return Collections.emptyMap();
				
				final Map<ObjectName, Number> results = new HashMap<ObjectName, Number>();
				for(Map.Entry<String, Object> entry: attributes.entrySet()) {
					
				}
				return results;
			} catch (Exception ex) {
				log.loge("Transform callback failed: %s", ex);
				return Collections.emptyMap();
			} finally {
				lastSampleTimeStamp = System.currentTimeMillis();
			}
		}
		
		
	}
	
	/**
	 * <p>Title: GCTransformer</p>
	 * <p>Description: Transformer to format standard GC MXBean collected data</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.opentsdb.GCTransformer</code></p>
	 */
	public static class GCTransformer extends AbstractTransformer {
		/** The filter this transformer applies to */
		public static final ObjectName FILTER = JMXHelper.objectName(ManagementFactory.GARBAGE_COLLECTOR_MXBEAN_DOMAIN_TYPE + ",*");
		
		/**
		 * Creates a new Transformers.GCTransformer
		 * @param baseTags A map of base tags
		 * @param objNamePrefix The object name prefix of the transform map
		 */
		public GCTransformer(final Map<String, String> baseTags, final String objNamePrefix) {
			super(baseTags, objNamePrefix);
		}

		
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.Transformers.AbstractTransformer#getFilter()
		 */
		@Override
		ObjectName getFilter() {
			return FILTER;
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
