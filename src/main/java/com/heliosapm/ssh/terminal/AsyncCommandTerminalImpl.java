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
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.ethz.ssh2.ConnectionMonitor;
import ch.ethz.ssh2.Session;

/**
 * <p>Title: AsyncCommandTerminalImpl</p>
 * <p>Description: Default implementation for AsyncCommandTerminal</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.ssh.terminal.AsyncCommandTerminalImpl</code></p>
 */

public class AsyncCommandTerminalImpl implements AsyncCommandTerminal, ConnectionMonitor {
	/** Instance logger */
	final Logger log = LoggerFactory.getLogger(getClass());
	/** The wrapped ssh session */
	final WrappedSession wrappedSession;
	/** The async call thread pool */
	protected final ExecutorService executor;
	/** The underlying session */
	protected final Session session;
	
	/** The terminal output input stream */
	protected PushbackInputStream pbis;
	/** The output stream to write to the terminal */
	protected OutputStream terminalOut;
	/** The captured terminal tty */
	protected String tty = null;
	/** A connection monitor for the term's underlying connection */
	protected ConnectionMonitor connectionMonitor = null;
	/** The currently executing stream processor thread */
	protected final AtomicReference<Thread> asyncStreamProcessor = new AtomicReference<Thread>(null); 

	
	/**
	 * Creates a new AsyncCommandTerminalImpl
	 * @param wrappedSession The underlying session this term is executing over
	 * @param executor The async call thread pool
//	 * @param handler The handler for the async responses
	 */
	public AsyncCommandTerminalImpl(final WrappedSession wrappedSession, final ExecutorService executor) { //, final AsyncCommandResponseHandler handler) {
		this.executor = executor!=null ? executor : Executors.newFixedThreadPool(1, new ThreadFactory() {
			@Override
			public Thread newThread(Runnable r) {				
				Thread t = new Thread(r);
				t.setDaemon(true);
				return t;
			}
		});
		this.wrappedSession = wrappedSession;
		this.session = wrappedSession.session;
		this.wrappedSession.conn.addConnectionMonitor(this);
		try {
			this.executor.submit(new Runnable() {
				public void run() {
					try {				
						terminalOut = session.getStdin();				
						session.requestDumbPTY();
						session.startShell();
						final InputStream errStream = session.getStderr();
						final Thread t = new Thread() {
							public void run() {
								InputStreamReader isr = null;
								BufferedReader br = null;
								try {
//									 isr = new InputStreamReader(errStream);
//									 br = new BufferedReader(isr);
//									 String line = null;
//									 while((line = br.readLine())!=null) {
//										 System.err.println("ERRSTREAM:" + line);
//									 }
									while(true) {
										log.info("\n\tSTATE:{}\n", session.getState());
										Thread.sleep(3000);
									}
								} catch (Exception ex) {
									log.error("ErrStreamReader Error:", ex);
								} finally {
									if(br!=null) try { br.close(); } catch (Exception x) {/* No Op */}
									if(isr!=null) try { isr.close(); } catch (Exception x) {/* No Op */}
									if(errStream!=null) try { errStream.close(); } catch (Exception x) {/* No Op */}
									
								}
								
							}
						};
						t.setDaemon(true);
						t.start();
						//pbis = new PushbackInputStream(new StreamGobbler(session.getStdout()));
						pbis = new PushbackInputStream(session.getStdout());
						writeCommand("PS1=" + WrappedSession.PROMPT);
						readUntilPrompt(null);
						try {
							tty = exec("tty").toString().trim();					
						}  catch (Exception x) {
							tty = null;
						}
						wrappedSession.connected.set(true);
					} catch (Exception e) {
						throw new RuntimeException("Failed to initialize session shell", e);
					}				
				}
			}).get();
		} catch (Exception ex) {
			throw new RuntimeException(ex);
			
		}
		
//		term = new CommandTerminalImpl(this.wrappedSession);
//		this.handler = handler;
//		if(handler instanceof AsyncStreamHandler) {
//			streamHandler = (AsyncStreamHandler)handler;
//		} else {
//			streamHandler = null;
//		}
	}
	
	/** 
	 * The format for setting the streaming execution thread.
	 * Values are: <ol>
	 * 	<li>The host name being executed against</li>
	 *  <li>The port connected to</li>
	 *  <li>The tty of the terminal</li>
	 *  <li>The command being executed</li>
	 * </ol>
	 *  */
	public static final String STREAMING_THREAD_FORMAT = "AsyncTerminal -> %s:%s(%s)/[%s]";
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.ssh.terminal.AsyncCommandTerminal#exec(com.heliosapm.ssh.terminal.AsyncCommandResponseHandler, java.lang.String[])
	 */
	@Override
	public Future<?> exec(final AsyncCommandResponseHandler handler, final String... commands) {
		if(handler==null) throw new IllegalArgumentException("The passed handler was null");
		if(commands==null || commands.length==0) throw new IllegalArgumentException("No commands provided");
		return executor.submit(new Runnable() {
			public void run() {
				final boolean streaming = (handler instanceof AsyncStreamHandler);
				final int streamer = streaming ? commands.length-1 : -1;
				try {
					for(int index = 0; index < commands.length; index++ ) {
						if(index==streamer) {
							final String threadName = Thread.currentThread().getName();
							try {
								Thread.currentThread().setName(String.format(STREAMING_THREAD_FORMAT, 
										wrappedSession.conn.getHostname(),
										wrappedSession.conn.getPort(),
										tty,
										commands[index]
								));
								asyncStreamProcessor.set(Thread.currentThread());
								execAsync(commands[index]);
								log.info("Starting stream processor [{}]", Thread.currentThread().getName());
								((AsyncStreamHandler)handler).onCommandOutputStream(commands[index], pbis);
								Integer exitStatus = null;
								String exitSignal = null;
								try { exitStatus = session.getExitStatus(); } catch (Exception x) {/* No Op */}
								try { exitSignal = session.getExitSignal(); } catch (Exception x) {/* No Op */}
									
								if(!handler.onCommandResponse(commands[index], exitStatus, exitSignal, "")) { 
									break;
								}
								
							} catch (Exception ex) {
								handler.onException(commands[index], ex);
							} finally {
								log.info("Stream processor stopped [{}]", Thread.currentThread().getName());
								asyncStreamProcessor.set(null);
								Thread.currentThread().setName(threadName);
							}
						} else {
							try {
								StringBuilder result = exec(commands[index]);
								Integer exitStatus = null;
								String exitSignal = null;
								try { exitStatus = session.getExitStatus(); } catch (Exception x) {/* No Op */}
								try { exitSignal = session.getExitSignal(); } catch (Exception x) {/* No Op */}
									
								if(!handler.onCommandResponse(commands[index], exitStatus, exitSignal, result)) {  // FIXME
									break;
								}
							} catch (Exception ex) {
								if(!handler.onException(commands[index], ex)) {
									break;
								}
							}
						}
					}
				} catch (Throwable t) {
					if(t instanceof InterruptedException) {
						if(Thread.interrupted()) Thread.interrupted();
					}
				}
			}
		});
	}

	public StringBuilder exec(final String cmd) throws IOException {
		writeCommand(cmd);			
		StringBuilder b = new StringBuilder();
		readUntilPrompt(b);
		return b;
	}
	
	/**
	 * Executes a remote command, reading no response
	 * @param cmd The command to execute
	 * @throws IOException thrown on any IO error
	 */
	public void execAsync(final String cmd) throws IOException {
		writeCommand(cmd);
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.ssh.terminal.AsyncCommandTerminal#close()
	 */
	@Override
	public void close() {
		if(pbis!=null) try { 
			pbis.close();
			log.info("AsyncStreamProcessor InputStream Closed");
		} catch (Exception x) {/* No Op */}
		try { wrappedSession.close(); } catch (Exception x) {/* No Op */}
		final Thread t = asyncStreamProcessor.getAndSet(null);
		if(t!=null) {
			t.interrupt();
		}		
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.ssh.terminal.AsyncCommandTerminal#getTty()
	 */
	@Override
	public String getTty() {
		return tty;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.ssh.terminal.AsyncCommandTerminal#isConnected()
	 */
	@Override
	public boolean isConnected() {
		return wrappedSession.isOpen();
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
//			if(65535==(int)ch) return;
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
	 * Returns the registered connection monitor
	 * @return the connectionMonitor
	 */
	public ConnectionMonitor getConnectionMonitor() {
		return connectionMonitor;
	}

	/**
	 * Sets the term's connection monitor
	 * @param connectionMonitor the connectionMonitor to set
	 */
	public void setConnectionMonitor(final ConnectionMonitor connectionMonitor) {
		if(connectionMonitor==null) throw new IllegalArgumentException("The passed connection monitor was null");
		this.connectionMonitor = connectionMonitor;
		if(wrappedSession != null) {
			if(wrappedSession.conn!=null) {
				if(wrappedSession.conn.isOpen()) {
					wrappedSession.conn.addConnectionMonitor(connectionMonitor);
				}
			}
		}
	}

	/**
	 * {@inheritDoc}
	 * @see ch.ethz.ssh2.ConnectionMonitor#connectionLost(java.lang.Throwable)
	 */
	@Override
	public void connectionLost(final Throwable reason) {
		try {
			if(reason != null) {
				log.warn("The connection was lost", reason);
			} else {
				log.info("The connection was closed");
				close();
			}
		} finally {
			if(connectionMonitor!=null) {
				connectionMonitor.connectionLost(reason);
			}
		}
		
	}
	



}
