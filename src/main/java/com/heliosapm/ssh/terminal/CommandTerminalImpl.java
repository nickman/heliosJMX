package com.heliosapm.ssh.terminal;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;

import ch.ethz.ssh2.Session;
import ch.ethz.ssh2.StreamGobbler;

/**
	 * <p>Title: CommandTerminalImpl</p>
	 * <p>Description: A wrapper of a session to provide a simplified command terminal</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.ssh.terminal.WrappedSession.CommandTerminalImpl</code></p>
	 */
	public class CommandTerminalImpl implements CommandTerminal {
		/** The underlying session */
		protected Session session;
		/** The underlying wrapped session */
		protected final WrappedSession wrappedSession;
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
		 * @param wrappedSession The wrapped session
		 */
		public CommandTerminalImpl(final WrappedSession wrappedSession) {
			this.wrappedSession = wrappedSession;			
			this.session = wrappedSession.session;
			
			try {				
				terminalOut = session.getStdin();				
				this.session.requestDumbPTY();
				this.session.startShell();
				pbis = new PushbackInputStream(new StreamGobbler(session.getStdout()));
				writeCommand("PS1=" + WrappedSession.PROMPT);
				readUntilPrompt(null);
				try {
					tty = exec("tty").toString().trim();					
				}  catch (Exception x) {
					tty = null;
				}
				this.wrappedSession.connected.set(true);
			} catch (Exception e) {
				throw new RuntimeException("Failed to initialize session shell", e);
			}
		}
		
		public boolean isConnected() {
			return this.wrappedSession.isOpen();
		}
		
		
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.ssh.terminal.CommandTerminal#getResponseStream()
		 */
		public InputStream getResponseStream() {
			return pbis; 
		}
		
		
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.CommandTerminal#close()
		 */
		public void close() {
			try { session.close(); } catch (Exception x) {/* No Op */}
			session = null;
		}
		
		
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.CommandTerminal#exec(java.lang.String[])
		 */
		@Override
		public StringBuilder exec(final String...commands) {
			return execWithDelim(null, commands);
		}
		
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.CommandTerminal#execSplit(java.lang.String[])
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
		 * @see com.heliosapm.opentsdb.CommandTerminal#execWithDelim(java.lang.String, java.lang.String[])
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
		      case WrappedSession.CR:
		      case WrappedSession.LF:
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
				case WrappedSession.CR:
				case WrappedSession.LF:
					if (!eol) {
						if (buff != null) {
							buff.append(cl.toString()).append(WrappedSession.LF);
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
						&& match < WrappedSession.PROMPT.length()
						&& cl.charAt(match) == WrappedSession.PROMPT.charAt(match)) {
					match++;
					if (match == WrappedSession.PROMPT.length()) {
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
			terminalOut.write(WrappedSession.LF);
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