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
package org.helios.ssh.terminal;

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
import java.io.PushbackInputStream;
import java.util.concurrent.atomic.AtomicBoolean;

import org.helios.jmx.remote.CloseListener;
import org.helios.jmx.remote.tunnel.ConnectionWrapper;

import ch.ethz.ssh2.Session;
import ch.ethz.ssh2.StreamGobbler;

/**
 * <p>Title: WrappedSession</p>
 * <p>Description: A wrapped SSH session</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>org.helios.ssh.terminal.WrappedSession</code></p>
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
	protected volatile CommandTerminal commandTerminal = null;
	
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
		if(commandTerminal==null) {
			synchronized(this) {
				if(commandTerminal==null) {
					commandTerminal = new CommandTerminalImpl(this);
				}
			}
		}
		return commandTerminal;
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
	 * <p>Title: CommandTerminalImpl</p>
	 * <p>Description: A wrapper of a session to provide a simplified command terminal</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>org.helios.ssh.terminal.WrappedSession.CommandTerminalImpl</code></p>
	 */
	public class CommandTerminalImpl implements CommandTerminal {
		  /** The underlying session */
		protected Session session;
		/** The underlying wrapped session */
		protected final WrappedSession wsession;
		/** The input stream to read terminal output from */
		protected PushbackInputStream pbis;
		/** The output stream to write to the terminal */
		protected OutputStream terminalOut;
		/** The captured terminal tty */
		protected String tty = null;
		/** The command exit codes */
		protected Integer[] exitCodes = null;
		
		
		

		/**
		 * Creates a new CommandTerminalImpl
		 * @param wsession The wrapped session
		 */
		public CommandTerminalImpl(final WrappedSession wsession) {
			this.wsession = wsession;
			this.session = wsession.session;
			
			try {				
				terminalOut = session.getStdin();				
				this.session.requestDumbPTY();
				this.session.startShell();
				pbis = new PushbackInputStream(new StreamGobbler(session.getStdout()));
				writeCommand("PS1=" + PROMPT);
				readUntilPrompt(null);
				try {
					tty = exec("tty").toString().trim();					
				}  catch (Exception x) {
					tty = null;
				}
				connected.set(true);
			} catch (Exception e) {
				throw new RuntimeException("Failed to initialize session shell", e);
			}
		}
		
		public boolean isConnected() {
			return this.wsession.isOpen();
		}
		
		/**
		 * {@inheritDoc}
		 * @see org.helios.opentsdb.CommandTerminal#close()
		 */
		public void close() {
			try { session.close(); } catch (Exception x) {/* No Op */}
			session = null;
		}
		
		
		/**
		 * {@inheritDoc}
		 * @see org.helios.opentsdb.CommandTerminal#exec(java.lang.String[])
		 */
		@Override
		public StringBuilder exec(final String...commands) {
			return execWithDelim(null, commands);
		}
		
		/**
		 * {@inheritDoc}
		 * @see org.helios.opentsdb.CommandTerminal#execSplit(java.lang.String[])
		 */
		@Override
		public StringBuilder[] execSplit(String...commands) {
			try {
				final StringBuilder[] results = new StringBuilder[commands.length];
				exitCodes = new Integer[commands.length];
				int index = 0;
				for(String command: commands) {
					results[index] = new StringBuilder();
					writeCommand(command);				
					readUntilPrompt(results[index]);
					exitCodes[index] = session.getExitStatus();
				    index++;
				}			
				return results;
			} catch (Exception ex) {
				throw new RuntimeException("Command execution failed", ex);
			}
		}
		
		/**
		 * {@inheritDoc}
		 * @see org.helios.opentsdb.CommandTerminal#execWithDelim(java.lang.String, java.lang.String[])
		 */
		@Override
		public StringBuilder execWithDelim(final String outputDelim, final String... commands) {
			final StringBuilder b = new StringBuilder();
			final StringBuilder[] results = execSplit(commands);
			for(StringBuilder r: results) {
				b.append(r);
				if(outputDelim!=null) {
					b.append(outputDelim);
				}
			}
			return b;
		}
		
		/**
		 * Reads the input stream until the end of the submitted command
		 * @throws IOException thrown on any IO error
		 */
		void skipTillEndOfCommand() throws IOException {
		    boolean eol = false;
		    while (true) {
		      final char ch = (char) pbis.read();
		      switch (ch) {
		      case CR:
		      case LF:
		        eol = true;
		        break;
		      default:
		        if (eol) {
		          pbis.unread(ch);
		          return;
		        }
		      }
		    }
		  }
		
		/**
		 * Reads the input stream until the end of the expected prompt
		 * @param buff The buffer to append into. Content is discarded if null.
		 * @throws IOException thrown on any IO errors
		 */
		void readUntilPrompt(final StringBuilder buff) throws IOException {
			final StringBuilder cl = new StringBuilder();
			boolean eol = true;
			int match = 0;
			while (true) {
				final char ch = (char) pbis.read();
//				if(65535==(int)ch) return;
				switch (ch) {
				case CR:
				case LF:
					if (!eol) {
						if (buff != null) {
							buff.append(cl.toString()).append(LF);
						}
						cl.setLength(0);
					}
					eol = true;
					break;
				default:
					if (eol) {
						eol = false;
					}
					cl.append(ch);
					break;
				}

				if (cl.length() > 0
						&& match < PROMPT.length()
						&& cl.charAt(match) == PROMPT.charAt(match)) {
					match++;
					if (match == PROMPT.length()) {
						return;
					}
				} else {
					match = 0;
				}
			}
		}
		
		/**
		 * Writes a command to the terminal
		 * @param cmd The command to write
		 * @throws IOException thrown on any IO error
		 */
		void writeCommand(final String cmd) throws IOException {
			terminalOut.write(cmd.getBytes());
			terminalOut.write(LF);
			skipTillEndOfCommand();
		}

		/**
		 * Returns the tty of this terminal
		 * @return the tty of this terminal
		 */
		public final String getTty() {
			return tty;
		}

		@Override
		public Integer[] getExitStatuses() {
			return exitCodes;
		}

		
		

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
	 * @param os The output stream to write to
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
	 * @see org.helios.jmx.remote.CloseListener#onClosed(java.io.Closeable)
	 */
	@Override
	public void onClosed(ConnectionWrapper closeable) {
		close();		
	}
	

	
}
