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

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.Future;

import ch.ethz.ssh2.ConnectionMonitor;

/**
 * <p>Title: AsyncCommandTerminal</p>
 * <p>Description: Asynchronous command terminal where commands are executed asynchronously and can be handled in a callback</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.ssh.terminal.AsyncCommandTerminal</code></p>
 */

public interface AsyncCommandTerminal extends Closeable {
	
	/**
	 * Invokes an array of command line executions, passing the results to the provided handler
	 * @param handler The handler the results are passed to
	 * @param commands An array of command line directives
	 * @return a Future representing pending completion of the task
	 * @throws RuntimeException thrown on any IO error
	 */
	public Future<?> exec(AsyncCommandResponseHandler handler, String...commands);
	
	public StringBuilder exec(String cmd) throws IOException;
		
	/**
	 * Closes the command terminal.
	 * Will not throw if already closed.
	 */
	public void close();
	
	/**
	 * Returns the tty of this terminal
	 * @return the tty of this terminal
	 */
	public String getTty();
	
	/**
	 * Indicates if this command terminal is connected
	 * @return true if this command terminal is connected, false otherwise
	 */
	public boolean isConnected();
	
	/**
	 * Returns the registered connection monitor
	 * @return the connectionMonitor
	 */
	public ConnectionMonitor getConnectionMonitor();

	/**
	 * Sets the term's connection monitor
	 * @param connectionMonitor the connectionMonitor to set
	 */
	public void setConnectionMonitor(ConnectionMonitor connectionMonitor);

	

}
