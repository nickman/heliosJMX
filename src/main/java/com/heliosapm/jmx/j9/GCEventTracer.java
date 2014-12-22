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

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import com.heliosapm.jmx.j9.J9GCGenconLogParser.GCEvent;
import com.heliosapm.jmx.j9.J9GCGenconLogParser.GCFail;
import com.heliosapm.jmx.j9.J9GCGenconLogParser.GCPhase;
import com.heliosapm.jmx.j9.J9GCGenconLogParser.MemorySpace;
import com.heliosapm.jmx.j9.J9GCGenconLogParser.Space;
import com.heliosapm.opentsdb.AnnotationBuilder;
import com.heliosapm.opentsdb.FluentMap;
import com.heliosapm.opentsdb.TSDBSubmitter;

/**
 * <p>Title: GCEventTracer</p>
 * <p>Description: </p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.jmx.j9.GCEventTracer</code></p>
 */

public class GCEventTracer implements GCEventListener {
	/** The TSDB event tracer */
	final TSDBSubmitter submitter;
	final FluentMap tags;
	
	final AtomicBoolean gcRunning = new AtomicBoolean(false);
	final AtomicBoolean atStartup = new AtomicBoolean(true);
	
	/**
	 * Creates a new GCEventTracer
	 * @param submitter The TSDB event tracer
	 */
	public GCEventTracer(final TSDBSubmitter submitter) {
		this.submitter = submitter;
		tags = this.submitter.tagMap();
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.jmx.j9.GCEventListener#onGCEnd()
	 */
	@Override
	public void onGCEnd() {
		if(gcRunning.compareAndSet(true, false) || atStartup.compareAndSet(true, false)) {
			tags.aclear();
			submitter.trace(System.currentTimeMillis(), "java.gc", 0, Collections.singletonMap("metric", "GCRunning")); 	
			//submitter.trace(new AnnotationBuilder().);
		}
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.jmx.j9.GCEventListener#onGCEvent(com.heliosapm.jmx.j9.J9GCGenconLogParser.GCEvent)
	 */
	@Override
	public void onGCEvent(final GCEvent event) {  // public void trace(final long timestamp, final String metric, final long value, final Map<String, String> tags) {
		if(gcRunning.compareAndSet(false, true)) {
			tags.aclear();
			submitter.trace(System.currentTimeMillis(), "java.gc", 1, Collections.singletonMap("metric", "GCRunning"));
		}
		tags.aclear()
			.append("gctype", event.getGcType().name().toLowerCase())
			.append("trigger", event.getGcTrigger().name().toLowerCase());
		
		final long ts = event.getTimestamp();
		final Map<GCFail, long[]> gcFails = event.getGCFails();
		if(!gcFails.isEmpty()) {			
			for(Map.Entry<GCFail, long[]> entry: gcFails.entrySet()) {
				final long[] counts = entry.getValue();
				tags.append("failtype", entry.getKey().key);				
				submitter.trace(ts, "java.gc.fail", counts[0], tags.append("metric", "objects"));
				submitter.trace(ts, "java.gc.fail", counts[1], tags.append("metric", "bytes"));
			}
			tags.pop(2);
		}
		submitter.trace(ts, "java.gc", (long)event.getElapsed(), tags.append("metric", "gcElapsed"));
		tags.pop();
		final Map<GCPhase, Map<Space, MemorySpace>> allocs = event.getPhaseAllocations();
		
		for(Map.Entry<GCPhase, Map<Space, MemorySpace>> entry: allocs.entrySet()) {
			final GCPhase phase = entry.getKey();
			final Map<Space, MemorySpace> spaces = entry.getValue();
			tags.append("phase", phase.name().toLowerCase());
			Space space = null;
			MemorySpace ms = null;			
			for(Map.Entry<Space, MemorySpace> sentry: spaces.entrySet()) {
				space = sentry.getKey();
				ms = sentry.getValue();		
				tags.append("space", space.name().toLowerCase());
				submitter.trace(ts, "java.mem", ms.freebytes, tags.append("metric", "bytesFree"));
				submitter.trace(ts, "java.mem", ms.totalbytes, tags.append("metric", "bytesTotal"));
				submitter.trace(ts, "java.mem", ms.percentFree, tags.append("metric", "percentFree"));
				submitter.trace(ts, "java.mem", ms.percentUsed, tags.append("metric", "percentUsed"));				
			}
			tags.pop(2);
		}
		submitter.flush();
	}

}
