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
import java.io.InputStream;

/**
 * <p>Title: CommandTerminal</p>
 * <p>Description: Defines a session interface for accepting commands and streaming or returning the results.</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.ssh.terminal.CommandTerminal</code></p>
 */

public interface CommandTerminal extends Closeable {
	/**
	 * Invokes an array of command line executions
	 * @param outputDelim The output delimeter written in between each of the output contents of each command
	 * @param commands An array of command line directives
	 * @return A stringy of the results
	 * @throws RuntimeException thrown on any IO error
	 */
	public StringBuilder execWithDelim(String outputDelim, String...commands);
	
	/**
	 * Invokes an array of command line executions with no output delim
	 * @param commands An array of command line directives
	 * @return A stringy of the results
	 * @throws RuntimeException thrown on any IO error
	 */
	public StringBuilder exec(String...commands);
	
	/**
	 * Invokes an array of command line execution directives, 
	 * returning the output of each in a seperate array slot.
	 * @param commands An array of command line directives
	 * @return A stringy array of the results
	 * @throws RuntimeException thrown on any IO error
	 */
	public StringBuilder[] execSplit(String...commands);
	
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
	 * Returns an input stream from which the last command's response can be read
	 * @return the response input stream
	 */
	public InputStream getResponseStream();
	
	/**
	 * Returns the exit status codes of the most recently invoked commands
	 * @return an array of command execution exit status codes
	 */
	public Integer[] getExitStatuses();
	
	/**
	 * Indicates if this command terminal is connected
	 * @return true if this command terminal is connected, false otherwise
	 */
	public boolean isConnected();
	
	
}
