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
package com.heliosapm.jmx.j9;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.heliosapm.jmx.j9.J9GCGenconLogParser.GCEvent;
import com.heliosapm.jmx.j9.J9GCGenconLogParser.GCFail;
import com.heliosapm.jmx.j9.J9GCGenconLogParser.GCTrigger;
import com.heliosapm.opentsdb.TSDBSubmitter;

/**
 * <p>Title: J9GCGenconFeedReader</p>
 * <p>Description: Processes a Jp GC gencon log</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.jmx.j9.J9GCGenconFeedReader</code></p>
 */

public class J9GCGenconFeedReader implements Runnable {
	
	/** The streaming parser */
	final J9GCGenconLogParser parser;
	/** The gc log input stream */
	final InputStream is;
	/** The gc log reader */
	final InputStreamReader isr;
	/** The gc log buffered reader */
	final BufferedReader reader;
	/** Keep running flag */
	private boolean keepRunning = true;
	/** The running thread */
	private Thread readerThread = null;
	/** In segment flag */
	private boolean inSegment = false;
	/** In segment ender */
	private String endSegment = null;
	/** Indicates if the stream reader should run continuously */
	private boolean continuous = true;
	/** A GCEvent Listener */
	private GCEventListener eventListener = null;
	
	private final List<Closeable> closeables = new ArrayList<Closeable>();
	
	/** A counter for System.gc triggered GC events */
	final AtomicLong sysEvents = new AtomicLong(0L);
	/** A counter for Allocation Failure triggered GC events */
	final AtomicLong afEvents = new AtomicLong(0L);
	/** A counter for Tenured GC failures */
	final AtomicLong tenureFailures = new AtomicLong(0L);
	/** A counter for Flip GC failures */
	final AtomicLong flipFailures = new AtomicLong(0L);
	
	
	/** The System.gc triggered GC event starter */
	public static final String SYS_STARTER = "<sys ";
	/** The System.gc triggered GC event ender */
	public static final String SYS_ENDER = "</sys>";
	/** The Allocation Failure triggered GC event starter */
	public static final String AF_STARTER = "<af ";
	/** The Allocation Failure triggered GC event ener */
	public static final String AF_ENDER = "</af>";
	
	/** Static class logger */
	protected static final Logger LOG = LoggerFactory.getLogger(J9GCGenconLogParser.class);

	
	/**
	 * Creates a new J9GCGenconFeedReader
	 * @param hostName The host name the stream is coming from
	 * @param appName  The name of the app the stream is coming from
	 * @param is The stream to read the gc log from
	 */
	public J9GCGenconFeedReader(final String hostName, final String appName, final InputStream is) {
		this.is = is;
		isr = new InputStreamReader(is);
		reader = new BufferedReader(isr);
		parser = new J9GCGenconLogParser(hostName, appName);
		continuous = true;
	}
	
	/**
	 * Creates a new J9GCGenconFeedReader
	 * @param hostName The host name the stream is coming from
	 * @param appName  The name of the app the stream is coming from
	 * @param fileName The file to read the gc log from
	 */
	public J9GCGenconFeedReader(final String hostName, final String appName, final String fileName) {
		FileInputStream fis = null;
		try {
			File f = new File(fileName.trim());
			if(!f.canRead()) throw new Exception("Cannot read the file [" + fileName + "]");
			fis = new FileInputStream(f);
			closeables.add(fis);
			is = fis;
			isr = new InputStreamReader(is);
			reader = new BufferedReader(isr);
			parser = new J9GCGenconLogParser(hostName, appName);
			continuous = false;
//			LOG.info("Completed file [{}] in [{}] ms.\n\tSystem.gc Triggered Events: {}\n\tAllocation Failure Events: {}", fileName, elapsed, sysEvents.get(), afEvents.get());
		} catch (Exception ex) {
			LOG.error("Failed to parse file [" + fileName + "]", ex);
			throw new RuntimeException(ex);
		} finally {
//			if(fis!=null) try { fis.close(); } catch (Exception x) {/* No Op */}
			//close();
		}
	}
	
	public void synchRun() {
		try {
			run();
		} finally {
			close();
		}
	}
	
	
	
	public void run() {
		readerThread = Thread.currentThread();
		final StringBuilder buff = new StringBuilder();
		while(keepRunning) {
			try {
				String line = null;
				while((line = reader.readLine())!=null) {
					line = line.trim();
					if(!inSegment) {
						if(line.indexOf(AF_STARTER)!=-1) {
							inSegment = true;
							endSegment = AF_ENDER;
							buff.append(line);
						} else if(line.indexOf(SYS_STARTER)!=-1) {
							inSegment = true;
							endSegment = SYS_ENDER;
							buff.append(line);
						} else {
							continue;
						}
					} else {
						buff.append(line);
						if(endSegment.equals(line)) {
							inSegment = false;
							GCEvent gce = null;
							try {
								
								gce = parser.parseContent(buff.toString());
								
								final EnumMap<GCFail, long[]> failMap = gce.getGCFails();
								if(!failMap.isEmpty()) {
									if(failMap.containsKey(GCFail.FLIPPED)) flipFailures.incrementAndGet();
									if(failMap.containsKey(GCFail.TENURED)) tenureFailures.incrementAndGet();
								}
								if(eventListener!=null) eventListener.onGCEvent(gce);
//								System.out.println(gce.toString());
//								gce.toString();
								if(gce.gcTrigger==GCTrigger.SYSTEM_GC) sysEvents.incrementAndGet();
								if(gce.gcTrigger==GCTrigger.ALLOC_FAILURE) afEvents.incrementAndGet();
							} catch (Exception ex) {
								/* No Op ? */
							} finally {
								if(gce != null) gce.reset();
								buff.setLength(0);
							}
						}
					}
				}  // END OF WHILE(LINE READ)
				if(!continuous) {
					keepRunning = false;
					break;
				}					
			} catch (Exception ex) {
				if(keepRunning) {
					if(Thread.interrupted()) Thread.interrupted();
				}
			}
		}
	}
	
	public void close() {
		keepRunning = false;
		if(readerThread!=null && readerThread != Thread.currentThread()) {
			readerThread.interrupt();
		}
		try { reader.close(); } catch (Exception x) {/* No Op */}
		try { isr.close(); } catch (Exception x) {/* No Op */}
		try { is.close(); } catch (Exception x) {/* No Op */}
		for(Closeable closeable: closeables) {
			try { closeable.close(); } catch (Exception x) {/* No Op */}
		}
		closeables.clear();
//		try { parser.close(); } catch (Exception x) {/* No Op */}
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		//final String content = URLHelper.getTextFromURL(URLHelper.toURL(new File(System.getProperty("java.io.tmpdir") + File.separator + "gclog-sample.xml")));
		FileInputStream fis = null;
		TSDBSubmitter tsdb = new TSDBSubmitter("localhost", 4242);
		J9GCGenconFeedReader reader = null;
		final String fileName = System.getProperty("java.io.tmpdir") + File.separator + "noapp.log";
		//final String fileName = "C:\\temp\\gclog-sample.xml";		
		try {
//			for(int i = 0; i < 100; i++) {
//				J9GCGenconFeedReader reader = new J9GCGenconFeedReader("mfthost", "MFT", fileName);
//			}
			
			tsdb.setLogTraces(true);
			tsdb.addRootTag("host", "mfthost").addRootTag("app", "MFT");
			tsdb.connect();
			GCEventTracer tracer = new GCEventTracer(tsdb);
			
			final long start = System.currentTimeMillis();
			reader = new J9GCGenconFeedReader("mfthost", "MFT", fileName);
			reader.setEventListener(tracer);
			reader.synchRun();
			final long elapsed = System.currentTimeMillis() - start;
			LOG.info("Completed file [{}] in [{}] ms.\n\tSystem.gc Triggered Events: {}\n\tAllocation Failure Events: {}\n\tTenure Failures: {}\n\tFlip Failures: {}", fileName, elapsed, reader.sysEvents.get(), reader.afEvents.get(), reader.tenureFailures.get(), reader.flipFailures.get());

			//File file = new File(System.getProperty("java.io.tmpdir") + File.separator + "gc.pipe");
//			File file = new File(System.getProperty("java.io.tmpdir") + File.separator + "noapp.log");
//			fis = new FileInputStream(file);
//			J9GCGenconFeedReader reader = new J9GCGenconFeedReader("mfthost", "MFT", fis);
//			reader.run();
		} catch (Exception ex) {
			ex.printStackTrace(System.err);
		} finally {
			try { fis.close(); } catch (Exception x) {}
			try { tsdb.close(); } catch (Exception x) {}
			try { reader.close(); } catch (Exception x) {}
			System.exit(0);
		}
		


	}

	/**
	 * Sets an event listener
	 * @param eventListener the eventListener to set
	 */
	public void setEventListener(final GCEventListener eventListener) {
		this.eventListener = eventListener;
	}

}
