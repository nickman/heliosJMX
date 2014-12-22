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

import static ch.ethz.ssh2.ChannelCondition.CLOSED;
import static ch.ethz.ssh2.ChannelCondition.EOF;
import static ch.ethz.ssh2.ChannelCondition.EXIT_SIGNAL;
import static ch.ethz.ssh2.ChannelCondition.EXIT_STATUS;
import static ch.ethz.ssh2.ChannelCondition.STDERR_DATA;
import static ch.ethz.ssh2.ChannelCondition.STDOUT_DATA;
import static ch.ethz.ssh2.ChannelCondition.TIMEOUT;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicBoolean;

import ch.ethz.ssh2.Session;

import com.heliosapm.jmx.remote.CloseListener;
import com.heliosapm.jmx.remote.tunnel.ConnectionWrapper;

/**
 * <p>Title: WrappedSession</p>
 * <p>Description: A wrapped SSH session</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.ssh.terminal.WrappedSession</code></p>
 */

public class WrappedSession implements CloseListener<ConnectionWrapper> {
	/** The delegate session */
	protected Session session = null;
	/** The parent connection */
	protected final ConnectionWrapper conn;
	/** Indicates if this session is open */
	protected final AtomicBoolean connected = new AtomicBoolean(false);
	
	/** The command timeout in ms. */
	protected long commandTimeout = 1000;
	/** The size of the output stream buffer in bytes */
	protected int outputBufferSize = 1024;
	/** The size of the error stream buffer in bytes */
	protected int errorBufferSize = 128;
	
	
	
	/** The command terminal for this wrapped session */
	protected CommandTerminal commandTerminal = null;
	/** The async command terminal for this wrapped session */
	protected AsyncCommandTerminal asyncCommandTerminal = null;
	
	/** Flag to indicate if a terminal has been assigned for this session */
	protected final AtomicBoolean terminalAssigned = new AtomicBoolean(false);
	
	/** The bit mask of all channel conditions */
	public static final int ALL_CONDITIONS = CLOSED | EOF | EXIT_SIGNAL | EXIT_STATUS | STDERR_DATA | STDOUT_DATA | TIMEOUT;
	
	public static final int[] ALL_CONDITION_VALUES = new int[] {CLOSED , EOF , EXIT_SIGNAL , EXIT_STATUS , STDERR_DATA , STDOUT_DATA , TIMEOUT};

	public static String PROMPT = "GO1GO2GO3";
	public final static char LF = '\n';
	public final static char CR = '\r';	
	
	/**
	 * Creates a new WrappedSession
	 * @param session The delegate session
	 * @param conn The parent connection
	 */
	public WrappedSession(final Session session, final ConnectionWrapper conn) {
		this.session = session;
		this.conn = conn;
		this.conn.addCloseListener(this);
	}
	
	
	
	/**
	 * Opens the command terminal for this session. Only one instance will be created.
	 * @return the command terminal
	 */
	public CommandTerminal openCommandTerminal() {
		if(terminalAssigned.compareAndSet(false, true)) {
			try {
				return new CommandTerminalImpl(this);
			} catch (Exception ex) {
				terminalAssigned.set(false);
				throw new RuntimeException("Failed to create CommandTerminal", ex);
			}
		}
		throw new IllegalStateException("Session has already assigned session");
	}
	
	/**
	 * Opens an async command terminal for this session. Only one instance will be created.
	 * @return the async command terminal
	 */
	public AsyncCommandTerminal openAsyncCommandTerminal() {
		if(terminalAssigned.compareAndSet(false, true)) {
			try {
				return new AsyncCommandTerminalImpl(this, null);
			} catch (Exception ex) {
				terminalAssigned.set(false);
				throw new RuntimeException("Failed to create AsyncCommandTerminal", ex);
			}
		}
		throw new IllegalStateException("Session has already assigned session");		
	}
	
	/**
	 * Reconnects this session
	 * @return true if the session was reconnected, false otherwise
	 */
	public boolean reconnect() {
		if(!connected.get()) {
			try {
				session = conn.openSession();
				connected.set(true);
			} catch (Exception ex) {
				connected.set(false);
			}
		}
		return connected.get();
	}
	
	/**
	 * Indicates if this session is open
	 * @return true if this session is open, false otherwise
	 */
	public boolean isOpen() {
		return connected.get();
	}
	

	/**
	 * @throws IOException
	 * @see ch.ethz.ssh2.Session#requestDumbPTY()
	 */
	public void requestDumbPTY() throws IOException {
		session.requestDumbPTY();
	}

	/**
	 * @return
	 * @see java.lang.Object#hashCode()
	 */
	public int hashCode() {
		return session.hashCode();
	}

	/**
	 * @param term
	 * @throws IOException
	 * @see ch.ethz.ssh2.Session#requestPTY(java.lang.String)
	 */
	public void requestPTY(String term) throws IOException {
		session.requestPTY(term);
	}

	/**
	 * @param term
	 * @param term_width_characters
	 * @param term_height_characters
	 * @param term_width_pixels
	 * @param term_height_pixels
	 * @param terminal_modes
	 * @throws IOException
	 * @see ch.ethz.ssh2.Session#requestPTY(java.lang.String, int, int, int, int, byte[])
	 */
	public void requestPTY(String term, int term_width_characters,
			int term_height_characters, int term_width_pixels,
			int term_height_pixels, byte[] terminal_modes) throws IOException {
		session.requestPTY(term, term_width_characters, term_height_characters,
				term_width_pixels, term_height_pixels, terminal_modes);
	}

	/**
	 * @param obj
	 * @return
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	public boolean equals(Object obj) {
		return session.equals(obj);
	}

	/**
	 * @param term_width_characters
	 * @param term_height_characters
	 * @param term_width_pixels
	 * @param term_height_pixels
	 * @throws IOException
	 * @see ch.ethz.ssh2.Session#requestWindowChange(int, int, int, int)
	 */
	public void requestWindowChange(int term_width_characters,
			int term_height_characters, int term_width_pixels,
			int term_height_pixels) throws IOException {
		session.requestWindowChange(term_width_characters,
				term_height_characters, term_width_pixels, term_height_pixels);
	}

	/**
	 * @param hostname
	 * @param port
	 * @param cookie
	 * @param singleConnection
	 * @throws IOException
	 * @see ch.ethz.ssh2.Session#requestX11Forwarding(java.lang.String, int, byte[], boolean)
	 */
	public void requestX11Forwarding(String hostname, int port, byte[] cookie,
			boolean singleConnection) throws IOException {
		session.requestX11Forwarding(hostname, port, cookie, singleConnection);
	}

	/**
	 * @param cmd
	 * @throws IOException
	 */
	public void execCommand(String cmd) throws IOException {
		session.execCommand(cmd, null);
	}
	  
	/**
	 * @param cmd
	 * @param charsetName
	 * @throws IOException
	 */
	public void execCommand(final String cmd, final String charsetName) throws IOException {
		session.execCommand(cmd, charsetName);
	}
	
	
	
	/**
	 * Copies all readable bytes from the input stream to the output stream
	 * @param is The input stream to read from
	 * @param osx The output stream to write to
	 * @param bufferSize the buffer size in bytes
	 * @return The total number of bytes transferred
	 * @throws IOException Thrown on any I/O error in the transfer
	 */
	public static int[] pipe(final InputStream is, final OutputStream osx, final int bufferSize) throws IOException {
		OutputStream os = osx != null ? osx : new ByteArrayOutputStream(bufferSize);
		byte[] buffer = new byte[bufferSize];
		int bytesCopied = 0;
		int totalBytesCopied = 0;
		int[] result = new int[2];
		boolean firstRead = false;
		while((bytesCopied = is.read(buffer))!= -1) {
			if(!firstRead) {
				firstRead = true;
			} else {
				if(is.available()<1) {
					result[0] = totalBytesCopied;
					result[1] = 2; 
					return result;					
				}
			}
			totalBytesCopied += bytesCopied;
			os.write(buffer, 0, bytesCopied);
			if(bytesCopied < bufferSize) {
				result[0] = totalBytesCopied;
				result[1] = 1;
				if(osx==null) {
					System.out.println("INIT OUTPUT:\n" + new String(((ByteArrayOutputStream)os).toByteArray()) + "\n---DONE" );
				}
				return result;
			}
		}
		result[0] = totalBytesCopied;
		result[1] = 0; 		
		return result;
	}

	/**
	 * Copies all readable bytes from the input stream to the output stream using a 1024 byte buffer
	 * @param is The input stream to read from
	 * @param os The output stream to write to
	 * @return The total number of bytes transferred
	 * @throws IOException Thrown on any I/O error in the transfer
	 */
	public static int[] pipe(final InputStream is, final OutputStream os) throws IOException {
		return pipe(is, os, 1024);
	}

	
	/**
	 * @return
	 * @see java.lang.Object#toString()
	 */
	public String toString() {
		return session.toString();
	}

	
	/**
	 * @throws IOException
	 * @see ch.ethz.ssh2.Session#startShell()
	 */
	public void startShell() throws IOException {
		session.startShell();
	}

	/**
	 * @param name
	 * @throws IOException
	 * @see ch.ethz.ssh2.Session#startSubSystem(java.lang.String)
	 */
	public void startSubSystem(String name) throws IOException {
		session.startSubSystem(name);
	}

	/**
	 * @return
	 * @see ch.ethz.ssh2.Session#getState()
	 */
	public int getState() {
		return session.getState();
	}

	/**
	 * @return
	 * @see ch.ethz.ssh2.Session#getStdout()
	 */
	public InputStream getStdout() {
		return session.getStdout();
	}

	/**
	 * @return
	 * @see ch.ethz.ssh2.Session#getStderr()
	 */
	public InputStream getStderr() {
		return session.getStderr();
	}

	/**
	 * @return
	 * @see ch.ethz.ssh2.Session#getStdin()
	 */
	public OutputStream getStdin() {
		return session.getStdin();
	}

	/**
	 * @param timeout
	 * @return
	 * @throws IOException
	 * @deprecated
	 * @see ch.ethz.ssh2.Session#waitUntilDataAvailable(long)
	 */
	public int waitUntilDataAvailable(long timeout) throws IOException {
		return session.waitUntilDataAvailable(timeout);
	}

	/**
	 * @param condition_set
	 * @param timeout
	 * @return
	 * @throws IOException
	 * @see ch.ethz.ssh2.Session#waitForCondition(int, long)
	 */
	public int waitForCondition(int condition_set, long timeout)
			throws IOException {
		return session.waitForCondition(condition_set, timeout);
	}

	/**
	 * @return
	 * @see ch.ethz.ssh2.Session#getExitStatus()
	 */
	public Integer getExitStatus() {
		return session.getExitStatus();
	}

	/**
	 * @return
	 * @see ch.ethz.ssh2.Session#getExitSignal()
	 */
	public String getExitSignal() {
		return session.getExitSignal();
	}

	/**
	 * 
	 * @see ch.ethz.ssh2.Session#close()
	 */
	public void close() {
		try { session.close(); } catch (Exception x) {/* No Op */}
		connected.set(false);
	}

	/**
	 * Returns the command timeout in ms.
	 * @return the command timeout in ms.
	 */
	public final long getCommandTimeout() {
		return commandTimeout;
	}

	/**
	 * Sets the command timeout in ms.
	 * @param commandTimeout the command timeout in ms.
	 */
	public final void setCommandTimeout(long commandTimeout) {
		this.commandTimeout = commandTimeout;
	}



	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.jmx.remote.CloseListener#onClosed(java.io.Closeable)
	 */
	@Override
	public void onClosed(ConnectionWrapper closeable) {
		close();		
	}
	

	
}
