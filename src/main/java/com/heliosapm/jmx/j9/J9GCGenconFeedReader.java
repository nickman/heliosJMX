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
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;

import com.heliosapm.jmx.j9.J9GCGenconLogParser.GCEvent;
import com.heliosapm.jmx.util.helpers.URLHelper;

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
	
	public static final String SYS_STARTER = "<sys ";
	public static final String SYS_ENDER = "</sys>";
	public static final String AF_STARTER = "<af ";
	public static final String AF_ENDER = "</af>";
	
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
	}
	
	
	public void run() {
		readerThread = Thread.currentThread();
		final StringBuilder buff = new StringBuilder();
		while(keepRunning) {
			try {
				String line = null;
				while((line = reader.readLine())!=null) {
					if(!inSegment) {
						if(line.startsWith(AF_STARTER)) {
							inSegment = true;
							endSegment = AF_ENDER;
							buff.append(line);
						} else if(line.startsWith(SYS_STARTER)) {
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
								System.out.println(gce.toString());
							} catch (Exception ex) {
								
							} finally {
								if(gce != null) gce.reset();
								buff.setLength(0);
							}
						}
					}
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
		if(readerThread!=null) {
			readerThread.interrupt();
		}
		try { reader.close(); } catch (Exception x) {/* No Op */}
		try { isr.close(); } catch (Exception x) {/* No Op */}
		try { is.close(); } catch (Exception x) {/* No Op */}
//		try { parser.close(); } catch (Exception x) {/* No Op */}
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		final String content = URLHelper.getTextFromURL(URLHelper.toURL(new File(System.getProperty("java.io.tmpdir") + File.separator + "gclog-sample.xml")));
		FileInputStream fis = null;
		try {
			File file = new File(System.getProperty("java.io.tmpdir") + File.separator + "gc.pipe");
			fis = new FileInputStream(file);
			J9GCGenconFeedReader reader = new J9GCGenconFeedReader("mfthost", "MFT", fis);
			reader.run();
		} catch (Exception ex) {
			ex.printStackTrace(System.err);
		} finally {
			try { fis.close(); } catch (Exception x) {}
		}
		


	}

}
