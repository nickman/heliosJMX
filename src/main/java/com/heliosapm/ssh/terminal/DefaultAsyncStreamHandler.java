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

/**
 * <p>Title: DefaultAsyncStreamHandler</p>
 * <p>Description: A basic {@link AsyncStreamHandler} implementation</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.ssh.terminal.DefaultAsyncStreamHandler</code></p>
 */

public class DefaultAsyncStreamHandler extends DefaultAsyncCommandResponseHandler implements AsyncStreamHandler {

	/**
	 * Creates a new DefaultAsyncStreamHandler
	 */
	public DefaultAsyncStreamHandler() {
		super();
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.ssh.terminal.AsyncStreamHandler#onCommandOutputStream(java.lang.String, java.io.InputStream)
	 */
	@Override
	public void onCommandOutputStream(final String command, final InputStream is) {
		InputStreamReader isr = null;
		BufferedReader br = null;
		try {
			isr = new InputStreamReader(is);
			br = new BufferedReader(isr);
			String line = null;
			int lineCtr = 1;
			while((line = br.readLine())!=null) {
				System.out.println("" + lineCtr + ":" + line);
				//log.info("{}: {}", lineCtr, line);
				lineCtr++;
			}
		} catch (Exception ex) {
			log.error("Streaming failure for command [{}]", command, ex);
		} finally {
			if(br!=null) try { br.close(); } catch (Exception x2) {/* No Op */}
			if(isr!=null) try { isr.close(); } catch (Exception x2) {/* No Op */}
		}
	}

}
