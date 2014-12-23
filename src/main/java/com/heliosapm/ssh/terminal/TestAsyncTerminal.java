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
package com.heliosapm.ssh.terminal;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.heliosapm.jmx.j9.GCEventTracer;
import com.heliosapm.jmx.j9.J9GCGenconFeedReader;
import com.heliosapm.jmx.remote.tunnel.TunnelRepository;
import com.heliosapm.opentsdb.TSDBSubmitter;

/**
 * <p>Title: TestAsyncTerminal</p>
 * <p>Description: Quickie tester for Async terminals</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.ssh.terminal.TestAsyncTerminal</code></p>
 */

public class TestAsyncTerminal {
	private static final Logger LOG = LoggerFactory.getLogger(TestAsyncTerminal.class);

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		LOG.info("Testing Async Term");
		AsyncCommandTerminal term  = null;
		try {
			//URL url = new URL("ssh://nwhitehead@localhost:22?kp=helios");
			// http://localhost:4242/#start=5m-ago&m=zimsum:1m-avg:java.mem{trigger=alloc_failure,gctype=global,metric=percentUsed,space=*,phase=postalloc}&o=&yrange=[0:]&key=out%20center%20top%20horiz%20box&wxh=1580x300&autoreload=15
			URL url = new URL("ssh://localhost?kp=helios");
			//URL url = new URL("ssh://njwmintx");
			term = TunnelRepository.getInstance().openAsyncCommandTerminal(url);
			final TSDBSubmitter tsdbSub = new TSDBSubmitter("localhost", 4242)
				.setLogTraces(true)
				.addRootTag("host", "mfthost").addRootTag("app", "MFT");
			tsdbSub.connect();
			final GCEventTracer tracer = new GCEventTracer(tsdbSub);
			DefaultAsyncStreamHandler handler = new DefaultAsyncStreamHandler() {
				@Override
				public void onCommandOutputStream(String command, InputStream is) {
					LOG.info("Starting J9GCGenconFeedReader");
					J9GCGenconFeedReader reader = new J9GCGenconFeedReader("mfthost", "MFT", is);
					reader.setEventListener(tracer);
					reader.synchRun();
				}
			};
//			LOG.info("External DF: {}", term.exec("df -k").toString());
			term.exec(handler, "tail -F /home/nwhitehead/test/gc.log");
			LOG.info("Connected");
			new Thread() {
				public void run() {
					final InputStreamReader isr = new InputStreamReader(System.in);
					BufferedReader br = new BufferedReader(isr);
					while(true) {
						try {
							String line = br.readLine();
							if("exit".equals(line)) {
								System.exit(-1);
							}
						} catch (Exception ex) {/* No Op */}
					}
				}
			}.start();
			Thread.currentThread().join();
		} catch (Exception ex) {
			ex.printStackTrace(System.err);
		} finally {
			try { term.close(); } catch (Exception ex) {}
		}
	}

}


/*
#!/bin/bash
export JAVA_HOME=/usr/lib/jvm/ibm-java-x86_64-60
export JAVACMD=$JAVA_HOME/bin/java
#export GCLOG=-Xverbosegclog:gclog%Y-%m-%d-%H%M%S_%seq_%pid.log,10,500
export GCLOG=-Xverbosegclog:gc.log
#java -Xmx4096m -verbose:gc -Xverbosegclog:./gc.log -Xgcpolicy:gencon GCTest 5 1000
$JAVACMD -Xmx4096m -verbose:gc $GCLOG -Xgcpolicy:gencon GCTest 5 1000

*/