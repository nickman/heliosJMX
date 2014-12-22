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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>Title: DefaultAsyncCommandResponseHandler</p>
 * <p>Description: A basic {@link AsyncCommandResponseHandler} implementation</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.ssh.terminal.DefaultAsyncCommandResponseHandler</code></p>
 */

public class DefaultAsyncCommandResponseHandler implements AsyncCommandResponseHandler {
	/** Instance logger */
	protected final Logger log = LoggerFactory.getLogger(getClass());
	
	/**
	 * Creates a new DefaultAsyncCommandResponseHandler
	 */
	public DefaultAsyncCommandResponseHandler() {
		
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.ssh.terminal.AsyncCommandResponseHandler#onCommandResponse(java.lang.String, java.lang.Integer, java.lang.String, java.lang.CharSequence)
	 */
	@Override
	public boolean onCommandResponse(final String command, final Integer exitCode, String exitSignal, final CharSequence output) {
		log.info("Command Response for [{}], exit code: [{}], exitSignal: [{}]:\n{}", command, exitCode, exitSignal, output.toString());
		return true;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.ssh.terminal.AsyncCommandResponseHandler#onException(java.lang.String, java.lang.Throwable)
	 */
	@Override
	public boolean onException(final String command, final Throwable error) {
		if(command==null) {
			log.error("General AsyncCommandTerminal Failure", error);
		} else {
			log.error("Exception on issuing command [{}]", command, error);
		}
		return false;
	}

}
