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
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

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
				results.putAll(doTransform(objectName, attributes));
				return results;
			} catch (Exception ex) {
				log.loge("Transform callback failed: %s", ex);
				return Collections.emptyMap();
			} finally {
				lastSampleTimeStamp = System.currentTimeMillis();
			}
		}
		
		protected abstract Map<ObjectName, Number> doTransform(final ObjectName objectName, final Map<String, Object> attributes);
		
		protected String getMetric(final ObjectName objectName) {
			return objectName.getDomain();
		}
		
		protected String[] keyNamePairs(final ObjectName objectName, final String...extras) {
			return defaultKeyNamePairs(objectName, extras);
		}
		
		protected void defaultAttribute(final Map<ObjectName, Number> results, final String attrName, final ObjectName target, final Map<String, Object> attributes) {
			try { results.put(build(getMetric(target), baseTags, keyNamePairs(target, "metric", attrName)), (Number)attributes.get(attrName)); } catch (Exception x) {/* No Op */}
		}
		

		
	}
	
	/**
	 * <p>Title: GCTransformer</p>
	 * <p>Description: Transformer to format standard GC MXBean collected data</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.opentsdb.Transformers.GCTransformer</code></p>
	 */
	public static class GCTransformer extends AbstractTransformer {
		/** The filter this transformer applies to */
		public static final ObjectName FILTER = JMXHelper.objectName(ManagementFactory.GARBAGE_COLLECTOR_MXBEAN_DOMAIN_TYPE + ",*");
		
		/** The number of cores available to the target JVM */
		protected final int cores;
		/** The last collection time for each GC Bean */
		protected final Map<ObjectName, Long> lastCollectTime = new HashMap<ObjectName, Long>(2);
		
		/**
		 * Creates a new Transformers.GCTransformer
		 * @param baseTags A map of base tags
		 * @param objNamePrefix The object name prefix of the transform map
		 * @param cores The number of cores available to the target JVM
		 */
		public GCTransformer(final Map<String, String> baseTags, final String objNamePrefix, final int cores) {
			super(baseTags, objNamePrefix);
			this.cores = cores;
		}
		
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.Transformers.AbstractTransformer#doTransform(javax.management.ObjectName, java.util.Map)
		 */
		protected Map<ObjectName, Number> doTransform(final ObjectName objectName, final Map<String, Object> attributes) {
			final long now = System.currentTimeMillis();
			final Map<ObjectName, Number> map = new HashMap<ObjectName, Number>();
			defaultAttribute(map, "CollectionCount", objectName, attributes);
			defaultAttribute(map, "CollectionTime", objectName, attributes);
			final Long lastTime = lastCollectTime.put(objectName, now);
			try {
				if(lastTime!=null) {
					long elapsed = now - lastTime;
					long collectTime = (Long)attributes.get("CollectTime");
					double percentElapsed = percent(collectTime, elapsed);
					double cpuElapsed = percent(collectTime, elapsed * cores);
					map.put(build(getMetric(objectName), baseTags, keyNamePairs(objectName, "metric", "GCPercentOfElapsed")), percentElapsed); 
					map.put(build(getMetric(objectName), baseTags, keyNamePairs(objectName, "metric", "GCPercentOfCPU")), cpuElapsed);				
				}
			} catch (Exception ex) {
				/* No Op */
			}
			return map;
		}
		
		
		protected String getMetric(final ObjectName objectName) {
			return "java.lang.gc";
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
	 * <p>Title: GCTransformer</p>
	 * <p>Description: Transformer to format standard Memory Pool MXBean collected data</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.opentsdb.Transformers.MemoryPoolTransformer</code></p>
	 */
	public static class MemoryPoolTransformer extends AbstractTransformer {
		/** The filter this transformer applies to */
		public static final ObjectName FILTER = JMXHelper.objectName(ManagementFactory.MEMORY_POOL_MXBEAN_DOMAIN_TYPE + ",*");
		
		/**
		 * Creates a new Transformers.MemoryPoolTransformer
		 * @param baseTags A map of base tags
		 * @param objNamePrefix The object name prefix of the transform map
		 */
		public MemoryPoolTransformer(final Map<String, String> baseTags, final String objNamePrefix) {
			super(baseTags, objNamePrefix);
		}
		
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.Transformers.AbstractTransformer#doTransform(javax.management.ObjectName, java.util.Map)
		 */
		protected Map<ObjectName, Number> doTransform(final ObjectName objectName, final Map<String, Object> attributes) {
			final Map<ObjectName, Number> map = new HashMap<ObjectName, Number>();
			defaultAttribute(map, "Usage.init", objectName, attributes);
			defaultAttribute(map, "Usage.used", objectName, attributes);
			defaultAttribute(map, "Usage.committed", objectName, attributes);
			defaultAttribute(map, "Usage.max", objectName, attributes);
			return map;
		}
		
		
		protected String getMetric(final ObjectName objectName) {
			return "java.lang.gc";
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
	
	/**
	 * Returns the key property list as an array of sequential key name pairs
	 * @param objectName The object name to convert the key name pairs from
	 * @param extras Additional key-value pairs to add i.e. <b><code>nameA, valueA, nameB, valueB.....nameN, valueN</code></b>
	 * @return an array of sequential key name pairs
	 */
	public static String[] defaultKeyNamePairs(final ObjectName objectName, final String...extras) {
		if(extras!=null && extras.length%2!=0) throw new IllegalArgumentException("Invalid number of extra key-name pairs [" + extras.length + "]");
		Set<String> set = new LinkedHashSet<String>();
		for(Map.Entry<String, String> entry: objectName.getKeyPropertyList().entrySet()) {
			set.add(entry.getKey());
			set.add(entry.getValue());
		}
		for(int i = 0; i < extras.length; i++) {
			set.add(extras[i].trim());
			i++;
			set.add(extras[i].trim());
		}
		
		return set.toArray(new String[set.size()]);
	}
		
	/**
	 * Builds an Objectname
	 * @param domain The domain
	 * @param baseTags The base tags
	 * @param keyValuePairs Additional tags as key-value pairs, i.e. <b><code>nameA, valueA, nameB, valueB.....nameN, valueN</code></b>
	 * @return the built ObjectName
	 */
	public static ObjectName build(final String domain, final Map<String, String> baseTags, String...keyValuePairs) {
		if(domain==null || domain.trim().isEmpty()) throw new IllegalArgumentException("Domain was null or empty");
		if(baseTags.isEmpty() && (keyValuePairs==null || keyValuePairs.length==0)) throw new IllegalArgumentException("No base tags or key-value pairs");
		if(keyValuePairs!=null && keyValuePairs.length%2!=0) throw new IllegalArgumentException("Invalid number of key-value pairs [" + keyValuePairs.length + "]");
		final Map<String, String> tags = new LinkedHashMap<String, String>(baseTags);
		for(int i = 0; i < keyValuePairs.length; i++) {
			String key = keyValuePairs[i].trim();
			i++;
			String value = keyValuePairs[i].trim();			
			tags.put(key, value);
		}
		try {
			return new ObjectName(domain, new Hashtable<String, String>(tags));
		} catch (Exception ex) {
			throw new RuntimeException("Failed to build ObjectName from [" + domain + "/" + tags.toString() + "]", ex);
		}
	}
		
		
	
	
	private Transformers() {}

}
