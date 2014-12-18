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
package com.heliosapm.jmx.j9;

import java.io.File;
import java.io.StringReader;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import com.heliosapm.jmx.util.helpers.URLHelper;

/**
 * <p>Title: J9GCGenconLogParser</p>
 * <p>Description: </p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.jmx.j9.J9GCGenconLogParser</code></p>
 */

public class J9GCGenconLogParser implements ContentHandler {
	/** Static class logger */
	protected static final Logger LOG = LoggerFactory.getLogger(J9GCGenconLogParser.class);
	
	public static interface ElementHandler {
		public void startElement(final String localName, final Attributes atts, final GCEvent gcEvent) throws Exception;
		public boolean isElement(final String localName);
//		public Stack<String> getSkips();
		
	}
	
	public static final ThreadLocal<SimpleDateFormat> SDF = new ThreadLocal<SimpleDateFormat>() {
		@Override
		protected SimpleDateFormat initialValue() {
			return new SimpleDateFormat("MMM dd HH:mm:ss yyyy");  // Dec 17 06:57:55 2014
		}
	};
	
	public static final Set<String> ELEMENTS = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
			"af", "sys", "nursery", "tenured", "gc", "time"
	)));

	public static final Set<String> HEADERS = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
			"af", "sys"
	)));
	
	private static Map<String, String> attributeMap(final Attributes atts) {
		final int x = atts.getLength();
		Map<String, String> map = new HashMap<String, String>(x);
		for(int i = 0; i < x; i++) {
			map.put(atts.getLocalName(i), atts.getValue(i));
		}
		return map;
	}
	
	public static class GCEvent {
		final Map<GCPhase, Map<Space, MemorySpace>> phaseAllocations = new EnumMap<GCPhase, Map<Space, MemorySpace>>(GCPhase.class);		
		GCType gcType = null;
		GCTrigger gcTrigger = null;
		long id = -1L; 
		long timestamp = -1L;
		
		public GCEvent() {
			for(final GCPhase phase: GCPhase.values()) {
				final Map<Space, MemorySpace> map = new EnumMap<Space, MemorySpace>(Space.class);
				for(final Space space: Space.values()) {
					map.put(space, null);
				}
				phaseAllocations.put(phase, map);
			}
		}
		
	}
	
	private static void memSpace(final GCEvent gcEvent, Map<String, String> atMap, final GCPhase gcPhase, final Space space) {		
		long totalBytes = Long.parseLong(atMap.get("totalbytes"));
		long freeBytes = Long.parseLong(atMap.get("freebytes"));
		int percentFree = Integer.parseInt(atMap.get("percent"));
		final MemorySpace ms = new MemorySpace(totalBytes, freeBytes, percentFree);
		gcEvent.phaseAllocations.get(gcPhase).put(space, ms);				

	}
	
	public static enum Location implements ElementHandler {
		HEADER {
			public void startElement(final String localName, final Attributes atts, final GCEvent gcEvent) throws Exception {
				try {
					 final Map<String, String> atMap = attributeMap(atts);
					 final long id = Long.parseLong(atMap.get("id"));
					 final long time = SDF.get().parse(atMap.get("timestamp")).getTime();
					 gcEvent.id = id;
					 gcEvent.timestamp = time;
					 gcEvent.gcTrigger = GCTrigger.triggerFor(localName);
				} catch (Exception ex) {
					throw new RuntimeException("Failed to process opener", ex);
				}
			}
			public boolean isElement(final String localName) {
				return HEADERS.contains(localName);
			}
		},
		PREN {
			public void startElement(final String localName, final Attributes atts, final GCEvent gcEvent) throws Exception {
				memSpace(gcEvent, attributeMap(atts), GCPhase.PREGC, Space.NURSERY);
			}
			public boolean isElement(final String localName) {
				return "nursery".equals(localName);
			}

		},
		PRET {
			public void startElement(final String localName, final Attributes atts, final GCEvent gcEvent) throws Exception {
				memSpace(gcEvent, attributeMap(atts), GCPhase.PREGC, Space.TENURED);
			}
			public boolean isElement(final String localName) {
				return "tenured".equals(localName);
			}

		},
		GC {
			public void startElement(final String localName, final Attributes atts, final GCEvent gcEvent) throws Exception {
				
			}
			public boolean isElement(final String localName) {
				return "gc".equals(localName);
			}
		},
		POSTGCN {
			public void startElement(final String localName, final Attributes atts, final GCEvent gcEvent) throws Exception {
				memSpace(gcEvent, attributeMap(atts), GCPhase.POSTGC, Space.NURSERY);
			}
			public boolean isElement(final String localName) {
				return "nursery".equals(localName);
			}
		},
		POSTGCT {
			public void startElement(final String localName, final Attributes atts, final GCEvent gcEvent) throws Exception {
				memSpace(gcEvent, attributeMap(atts), GCPhase.POSTGC, Space.TENURED);
			}
			public boolean isElement(final String localName) {
				return "tenured".equals(localName);
			}
		},
		POSTALLOCN {
			public void startElement(final String localName, final Attributes atts, final GCEvent gcEvent) throws Exception {
				memSpace(gcEvent, attributeMap(atts), GCPhase.POSTALLOC, Space.NURSERY);
			}
			public boolean isElement(final String localName) {
				return "nursery".equals(localName);
			}

		},
		POSTALLOCT {
			public void startElement(final String localName, final Attributes atts, final GCEvent gcEvent) throws Exception {
				memSpace(gcEvent, attributeMap(atts), GCPhase.POSTALLOC, Space.TENURED);
			}
			public boolean isElement(final String localName) {
				return "tenured".equals(localName);
			}
		},
		TIME {
			public void startElement(final String localName, final Attributes atts, final GCEvent gcEvent) throws Exception {

			}
			public boolean isElement(final String localName) {
				return "time".equals(localName);
			}
		};
		
		
		public static final Map<Integer, Location> ORD2LOCATION;
		public static final Map<Location, Location> NEXTLOCATION;
		
		static {
			final Location[] locs = values();
			final Map<Integer, Location> lmap = new HashMap<Integer, Location>(locs.length);
			for(Location loc: locs) {
				lmap.put(loc.ordinal(), loc);
			}
			final int last = locs.length-1;
			final EnumMap<Location, Location> enumMap = new EnumMap<Location, Location>(Location.class);
			for(Location loc: locs) {
				if(loc.ordinal()==last) {
					enumMap.put(loc, lmap.get(0));
					continue;					
				}
				enumMap.put(loc, lmap.get(loc.ordinal()+1));
			}
			ORD2LOCATION = Collections.unmodifiableMap(lmap);
			NEXTLOCATION = Collections.unmodifiableMap(enumMap);
			
//			StringBuilder b = new StringBuilder("\nLocation Steps:");
//			for(Location loc: locs) {
//				b.append("\n\t").append(loc.name()).append(" : ").append(loc.next().name());
//			}
//			LOG.info(b.toString());
		}
		
		final Location next() {
			return NEXTLOCATION.get(this);
		}		

		
	}
	
	public static enum GCTrigger {
		ALLOC_FAILURE("af"),
		SYSTEM_GC("sys");
		
		private GCTrigger(final String key) {
			this.key = key;
		}
		public final String key;
		
		public static GCTrigger triggerFor(final String localName) {
			if(ALLOC_FAILURE.key.equals(localName)) return ALLOC_FAILURE;
			if(SYSTEM_GC.key.equals(localName)) return SYSTEM_GC;
			throw new RuntimeException("Unrecognized GCTrigger key: [" + localName + "]"); 
		}
	}
	
	public static enum GCPhase {
		/** The allocation state before the GC ran */
		PREGC,
		/** The allocation state after the GC ran */
		POSTGC,
		/** The allocation state after the memory request that triggered the GC completed */
		POSTALLOC;
		
		public static final Map<GCPhase, GCPhase> NEXTMAP;
		
		static {
			final EnumMap<GCPhase, GCPhase> enumMap = new EnumMap<GCPhase, GCPhase>(GCPhase.class);
			enumMap.put(PREGC, POSTGC);
			enumMap.put(POSTGC, POSTALLOC);
			enumMap.put(POSTALLOC, PREGC);
			NEXTMAP = Collections.unmodifiableMap(enumMap);
		}
		
		final GCPhase next() {
			return NEXTMAP.get(this);
		}		
	}
	
	public static enum GCType {
		GLOBAL("global"),
		SCAVENGER("scavenger");
		
		private GCType(final String key, final String...skips) {
			this.key = key;
			this.skips = skips;
		}
		public final String key;
		public final String[] skips;
		
		
		public static GCType gcTypeFor(final String localName) {
			if(GLOBAL.key.equals(localName)) return GLOBAL;
			if(SCAVENGER.key.equals(localName)) return SCAVENGER;
			throw new RuntimeException("Unrecognized GCType key: [" + localName + "]"); 
		}

	}
	
	
	public static enum Space {
		TENURED("tenured"),
		NURSERY("nursery");
		
		private Space(final String key) {
			this.key = key;
		}
		public final String key;
	}
	
	public static class MemorySpace {
		final long totalbytes;
		final long freebytes;
		final int percentFree;
		final int percentUsed;
		
		/**
		 * Creates a new MemorySpace
		 * @param totalbytes 
		 * @param freebytes
		 * @param percentFree
		 */
		public MemorySpace(final long totalbytes, final long freebytes, final int percentFree) {
			this.totalbytes = totalbytes;
			this.freebytes = freebytes;
			this.percentFree = percentFree;
			this.percentUsed = 100-percentFree;
		}
		
		
	}
	
	
	

	
	protected final XMLReader parser;
	protected final AtomicReference<GCPhase> gcPhase = new AtomicReference<GCPhase>(GCPhase.POSTALLOC);
	protected final AtomicReference<Location> location = new AtomicReference<Location>(Location.values()[0]); 
	protected final GCEvent gcEvent = new GCEvent();
	protected final AtomicBoolean skipEnabled = new AtomicBoolean(false);
	
	/**
	 * Creates a new J9GCGenconLogParser
	 */
	public J9GCGenconLogParser() {
		try {
			parser = org.xml.sax.helpers.XMLReaderFactory.createXMLReader();
			parser.setContentHandler(this);
		} catch (Exception ex) {
			LOG.error("Failed to initialize J9GCGenconLogParser", ex);
			throw new RuntimeException("Failed to initialize J9GCGenconLogParser", ex);
		}
	}
	
	public void parseContent(final String content) {
		try {
			parser.parse(new InputSource(new StringReader(content)));
		} catch (Exception ex) {
			LOG.error("Failed to parse content", ex);
			throw new RuntimeException("Failed to parse content", ex);			
		}
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		LOG.info("J9GCGenconLogParser Test");
		// c:\temp\gclog-sample.xml
		final String content = URLHelper.getTextFromURL(URLHelper.toURL(new File("c:\\temp\\gclog-sample.xml")));
		new J9GCGenconLogParser().parseContent(content);
		
	}
	
	protected GCPhase phase() {
		return gcPhase.getAndSet(gcPhase.get().next());
	}
	
	protected Location nextLocation() {
		final Location loc = location.get();
		location.set(loc.next());
		return loc;
	}
	

	
	/**
	 * {@inheritDoc}
	 * @see org.xml.sax.ContentHandler#startElement(java.lang.String, java.lang.String, java.lang.String, org.xml.sax.Attributes)
	 */
	@Override
	public void startElement(final String uri, final String localName, final String qName, final Attributes atts) throws SAXException {
		if(ELEMENTS.contains(localName) && !skipEnabled.get()) {
			final Location currentLoc = location.get(); 
			if(currentLoc.isElement(localName)) {	
				try {
					currentLoc.startElement(localName, atts, gcEvent);
				} catch (Exception ex) {
					LOG.error("Failed parsing during [" + currentLoc.name() + "]", ex);
				}
				nextLocation();
			}
		}
	}

	/**
	 * {@inheritDoc}
	 * @see org.xml.sax.ContentHandler#endElement(java.lang.String, java.lang.String, java.lang.String)
	 */
	@Override
	public void endElement(final String uri, final String localName, final String qName) throws SAXException {
		//LOG.info("End Element: {}", localName);		
	}

	/**
	 * {@inheritDoc}
	 * @see org.xml.sax.ContentHandler#characters(char[], int, int)
	 */
	@Override
	public void characters(char[] ch, int start, int length) throws SAXException {
		
	}

	/**
	 * {@inheritDoc}
	 * @see org.xml.sax.ContentHandler#ignorableWhitespace(char[], int, int)
	 */
	@Override
	public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
		
	}

	/**
	 * {@inheritDoc}
	 * @see org.xml.sax.ContentHandler#processingInstruction(java.lang.String, java.lang.String)
	 */
	@Override
	public void processingInstruction(String target, String data) throws SAXException {
		LOG.info("Processing Instruction: target:{}, data:{}", target, data);		
	}

	/**
	 * {@inheritDoc}
	 * @see org.xml.sax.ContentHandler#skippedEntity(java.lang.String)
	 */
	@Override
	public void skippedEntity(final String name) throws SAXException {
		LOG.info("Skipped Entity: {}", name);
		
	}

	
	/**
	 * {@inheritDoc}
	 * @see org.xml.sax.ContentHandler#setDocumentLocator(org.xml.sax.Locator)
	 */
	@Override
	public void setDocumentLocator(Locator locator) {
		// TODO Auto-generated method stub
		
	}

	/**
	 * {@inheritDoc}
	 * @see org.xml.sax.ContentHandler#startDocument()
	 */
	@Override
	public void startDocument() throws SAXException {
		LOG.info("Segment Started");
		
	}

	/**
	 * {@inheritDoc}
	 * @see org.xml.sax.ContentHandler#endDocument()
	 */
	@Override
	public void endDocument() throws SAXException {
		LOG.info("Segment Ended");
		
	}

	/**
	 * {@inheritDoc}
	 * @see org.xml.sax.ContentHandler#startPrefixMapping(java.lang.String, java.lang.String)
	 */
	@Override
	public void startPrefixMapping(final String prefix, final String uri) throws SAXException {
		LOG.info("PrefixMapping Started: [{}]:[{}]", prefix, uri);
		
	}

	/**
	 * {@inheritDoc}
	 * @see org.xml.sax.ContentHandler#endPrefixMapping(java.lang.String)
	 */
	@Override
	public void endPrefixMapping(String prefix) throws SAXException {
		LOG.info("PrefixMapping Ended: [{}]", prefix);
		
	}
	
}


/*
<sys id="10" timestamp="Dec 17 06:57:55 2014" intervalms="6729695.678">
<time exclusiveaccessms="0.072" meanexclusiveaccessms="0.072" threads="0" lastthreadtid="0x00002AAE80156200" />
<refs soft="4962" weak="3504" phantom="415" dynamicSoftReferenceThreshold="31" maxSoftReferenceThreshold="32" />
<nursery freebytes="4219359496" totalbytes="4711042048" percent="89" />
<tenured freebytes="10235822464" totalbytes="10468982784" percent="97" >
  <soa freebytes="10131132800" totalbytes="10364293120" percent="97" />
  <loa freebytes="104689664" totalbytes="104689664" percent="100" />
</tenured>
<gc type="global" id="824" totalid="1947" intervalms="900068.972">
  <compaction movecount="5918074" movebytes="477031104" reason="compact on aggressive collection" />
  <classunloading classloaders="0" classes="0" timevmquiescems="0.000" timetakenms="1.728" />
  <finalization objectsqueued="639" />
  <timesms mark="93.596" sweep="8.994" compact="113.475" total="218.010" />
  <nursery freebytes="4442740600" totalbytes="4711042048" percent="94" />
  <tenured freebytes="10240252704" totalbytes="10468982784" percent="97" >
    <soa freebytes="10135563040" totalbytes="10364293120" percent="97" />
    <loa freebytes="104689664" totalbytes="104689664" percent="100" />
  </tenured>
</gc>
<nursery freebytes="4442740600" totalbytes="4711042048" percent="94" />
<tenured freebytes="10240252704" totalbytes="10468982784" percent="97" >
  <soa freebytes="10135563040" totalbytes="10364293120" percent="97" />
  <loa freebytes="104689664" totalbytes="104689664" percent="100" />
</tenured>
<refs soft="4045" weak="2824" phantom="140" dynamicSoftReferenceThreshold="31" maxSoftReferenceThreshold="32" />
<time totalms="218.276" />
</sys>

<!-- 
<af type="nursery" id="1339" timestamp="Dec 17 07:12:53 2014" intervalms="3481.034">
<minimum requested_bytes="10240024" />
<time exclusiveaccessms="0.083" meanexclusiveaccessms="0.083" threads="0" lastthreadtid="0x00002AAE883BD200" />
<refs soft="5795" weak="6667" phantom="763" dynamicSoftReferenceThreshold="31" maxSoftReferenceThreshold="32" />
<nursery freebytes="5061608" totalbytes="4711042048" percent="0" />
<tenured freebytes="9688945128" totalbytes="10468982784" percent="92" >
  <soa freebytes="9584255464" totalbytes="10364293120" percent="92" />
  <loa freebytes="104689664" totalbytes="104689664" percent="100" />
</tenured>
<gc type="scavenger" id="1227" totalid="2051" intervalms="3481.266">
  <flipped objectcount="187778" bytes="30478024" />
  <tenured objectcount="12255" bytes="868856" />
  <finalization objectsqueued="1789" />
  <scavenger tiltratio="89" />
  <nursery freebytes="4679578768" totalbytes="4711042048" percent="99" tenureage="14" />
  <tenured freebytes="9686847976" totalbytes="10468982784" percent="92" >
    <soa freebytes="9582158312" totalbytes="10364293120" percent="92" />
    <loa freebytes="104689664" totalbytes="104689664" percent="100" />
  </tenured>
  <time totalms="10.673" />
</gc>
<nursery freebytes="4669338744" totalbytes="4711042048" percent="99" />
<tenured freebytes="9686847976" totalbytes="10468982784" percent="92" >
  <soa freebytes="9582158312" totalbytes="10364293120" percent="92" />
  <loa freebytes="104689664" totalbytes="104689664" percent="100" />
</tenured>
<refs soft="4070" weak="2965" phantom="157" dynamicSoftReferenceThreshold="31" maxSoftReferenceThreshold="32" />
<time totalms="11.311" />
</af>
*/