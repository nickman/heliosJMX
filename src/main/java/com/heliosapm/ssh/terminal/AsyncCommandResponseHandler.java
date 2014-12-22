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

/**
 * <p>Title: AsyncCommandResponseHandler</p>
 * <p>Description: AsyncHandler that is called back with the original command, the exit code and the output of the command.</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.ssh.terminal.AsyncCommandResponseHandler</code></p>
 */

public interface AsyncCommandResponseHandler {
	/**
	 * Handles the completion of an asynchronously executed terminal command
	 * @param command The original command that was executed
	 * @param exitCode The exit code of the command which may be null
	 * @param exitSignal The name of the signal by which the process on the remote side was stopped
	 * @param output The output of the command
	 * @return true if the remaining commands in the command array should be executed, false to halt processing
	 */
	public boolean onCommandResponse(String command, Integer exitCode, String exitSignal, CharSequence output);
	
	/**
	 * Callback when a command execution fails
	 * @param command The command that resulted in an exception, or null if the failure was a general interface error
	 * @param error The throwable thrown from the failure
	 * @return true to continue, false to cease processing an remaining un-executed commands
	 */
	public boolean onException(String command, Throwable error);
}
