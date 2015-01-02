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
package com.heliosapm.script.compilers.groovy.stdio;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;


/**
 * <p>Title: SystemStreamRedirector</p>
 * <p>Description: Service to redirect system streams for individual threads</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.script.compilers.groovy.stdio.SystemStreamRedirector</code></p>
 */

public class SystemStreamRedirector extends PrintStream {
	private static final PrintStream SYSTEM_OUT = System.out;
	private static final PrintStream SYSTEM_ERR = System.err;
	private static final SystemStreamRedirector outRedirector = new SystemStreamRedirector(SYSTEM_OUT, true);
	private static final SystemStreamRedirector errRedirector = new SystemStreamRedirector(SYSTEM_ERR, false);
	private static InheritableThreadLocal<PrintStream> setOutStream = new InheritableThreadLocal<PrintStream>() {
		protected PrintStream initialValue() {
			return SYSTEM_OUT; 
		}
	};	
	private static InheritableThreadLocal<PrintStream> setErrStream = new InheritableThreadLocal<PrintStream>() {
		protected PrintStream initialValue() {
			return SYSTEM_ERR; 
		}
	};
	
	/** Indicates if this stream replaces StdOut(true) or StdErr(false) */
	private final boolean isStdOut; 
	
	/** Indicates if the system redirector is globally installed */
	private static final AtomicBoolean installed = new AtomicBoolean(false);
	
	/**
	 * Installs the global system redirector if it is not installed already
	 */
	public static void install() {
		if(!installed.get()) {
			System.setOut(outRedirector);
			System.setErr(errRedirector);
			installed.set(true);
		} 
	}
	/**
	 * Uninstalls the global system redirector if it is installed 
	 */	
	public static void uninstall() {
		if(installed.get()) {
			System.setOut(SYSTEM_OUT);
			System.setErr(SYSTEM_ERR);
			installed.set(false);
		} 
	}
	
	/**
	 * Will the real System.out please stand up
	 * @return the real System.out 
	 */
	public static PrintStream out() {
		return SYSTEM_OUT;
	}
	
	/**
	 * Will the real System.err please stand up
	 * @return the real System.err 
	 */
	public static PrintStream err() {
		return SYSTEM_ERR;
	}
	
	
	/**
	 * Determines if either stdout or stderr are redirected
	 * @return true if stdout or stderr is redirected, false otherwise
	 */
	public static boolean isInstalledOnCurrentThread() {
		return (
				!setOutStream.get().equals(SYSTEM_OUT) ||
				!setErrStream.get().equals(SYSTEM_ERR)
		);
	}
	
	/**
	 * Redirects the out and error streams for the current thread
	 * @param outPs The output stream redirect for Standard Out
	 * @param errPs The output stream redirect for Standard Err
	 */
	public static void set(PrintStream outPs, PrintStream errPs) {
		if(outPs==null) {
			throw new RuntimeException("Out PrintStream was null");
		}
		if(errPs==null) {
			errPs = outPs;
		}
		if(!installed.get()) {
			throw new RuntimeException("The SystemRedirector is not installed");			
		} else {
			setOutStream.set(outPs);
			setErrStream.set(errPs);
		}
	}
	
	/**
	 * Redirects the out and error streams for the current thread
	 * @param out The output stream redirect for Standard Out
	 * @param err The output stream redirect for Standard Err
	 */
	public static void set(final OutputStream out, OutputStream err) {
		if(out==null) {
			throw new RuntimeException("Out OutputStream was null");
		}
		if(err==null) {
			err = out;
		}
		if(!installed.get()) {
			throw new RuntimeException("The SystemRedirector is not installed");			
		} else {
			setOutStream.set(new PrintStream(out, true));
			setErrStream.set(new PrintStream(err, true));
		}
	}	
	
	/**
	 * Redirects the out and error streams for the current thread to the same stream
	 * @param ps The output stream redirect for Standard Out and Standard Err
	 */
	public static void set(PrintStream ps) {
		if(ps==null) {
			throw new RuntimeException("Out/Err PrintStream was null");
		}
		if(!installed.get()) {
			throw new RuntimeException("The SystemRedirector is not installed");			
		} else {
			setOutStream.set(ps);
			setErrStream.set(ps);
		}
	}
	
	/**
	 * Redirects the out and error streams for the current thread to the same stream
	 * @param out The output stream redirect for Standard Out and Standard Err
	 */
	public static void set(final OutputStream out) {
		if(out==null) {
			throw new RuntimeException("Out/Err OutputStream was null");
		}
		if(!installed.get()) {
			throw new RuntimeException("The SystemRedirector is not installed");			
		} else {
			setOutStream.set(new PrintStream(out, true));
			setErrStream.set(new PrintStream(out, true));
		}
	}
	
	
	/**
	 * Resets the out and error streams for the current thread to the default
	 */
	public static void reset() {
		setOutStream.set(SYSTEM_OUT);
		setErrStream.set(SYSTEM_ERR);		
	}
	
	
	
	/**
	 * Returns the correct out or err stream for this redirector.
	 * @return a printstream
	 */
	private PrintStream getPrintStream() {
		return isStdOut ? setOutStream.get() : setErrStream.get();
	}

	/**
	 * Creates new SystemStreamRedirector
	 * @param ps The default output this redirector replaces
	 * @param isStdOut true if this stream replaces System.out, false if it replaces System.err
	 */
	private SystemStreamRedirector(PrintStream ps, boolean isStdOut) {
		super(ps);
		this.isStdOut = isStdOut;
	}
	
	public int hashCode() {
		return getPrintStream().hashCode();
	}
	public void write(byte[] b) throws IOException {
		getPrintStream().write(b);
	}
	public boolean equals(Object obj) {
		return getPrintStream().equals(obj);
	}
	public String toString() {
		return getPrintStream().toString();
	}
	public void flush() {
		getPrintStream().flush();
	}
	public void close() {
		getPrintStream().close();
	}
	public boolean checkError() {
		return getPrintStream().checkError();
	}
	public void write(int b) {
		getPrintStream().write(b);
	}
	public void write(byte[] buf, int off, int len) {
		getPrintStream().write(buf, off, len);
	}
	public void print(boolean b) {
		getPrintStream().print(b);
	}
	public void print(char c) {
		getPrintStream().print(c);
	}
	public void print(int i) {
		getPrintStream().print(i);
	}
	public void print(long l) {
		getPrintStream().print(l);
	}
	public void print(float f) {
		getPrintStream().print(f);
	}
	public void print(double d) {
		getPrintStream().print(d);
	}
	public void print(char[] s) {
		getPrintStream().print(s);
	}
	public void print(String s) {
		getPrintStream().print(s);
	}
	public void print(Object obj) {
		getPrintStream().print(obj);
	}
	public void println() {
		getPrintStream().println();
	}
	public void println(boolean x) {
		getPrintStream().println(x);
	}
	public void println(char x) {
		getPrintStream().println(x);
	}
	public void println(int x) {
		getPrintStream().println(x);
	}
	public void println(long x) {
		getPrintStream().println(x);
	}
	public void println(float x) {
		getPrintStream().println(x);
	}
	public void println(double x) {
		getPrintStream().println(x);
	}
	public void println(char[] x) {
		getPrintStream().println(x);
	}
	public void println(String x) {
		getPrintStream().println(x);
	}
	public void println(Object x) {
		getPrintStream().println(x);
	}
	public PrintStream printf(String format, Object... args) {
		return getPrintStream().printf(format, args);
	}
	public PrintStream printf(Locale l, String format, Object... args) {
		return getPrintStream().printf(l, format, args);
	}
	public PrintStream format(String format, Object... args) {
		return getPrintStream().format(format, args);
	}
	public PrintStream format(Locale l, String format, Object... args) {
		return getPrintStream().format(l, format, args);
	}
	public PrintStream append(CharSequence csq) {
		return getPrintStream().append(csq);
	}
	public PrintStream append(CharSequence csq, int start, int end) {
		return getPrintStream().append(csq, start, end);
	}
	public PrintStream append(char c) {
		return getPrintStream().append(c);
	}

}
