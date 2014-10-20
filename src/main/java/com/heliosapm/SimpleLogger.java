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
package com.heliosapm;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * <p>Title: SimpleLogger</p>
 * <p>Description: Simple out and err logger. Will hook this up to some logging paclkage at some point.</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.SimpleLogger</code></p>
 */

public class SimpleLogger {
	/** A map of sloggers keyed by the class they were assigned to */
	private static final Map<Class<?>, SLogger> sloggers = new ConcurrentHashMap<Class<?>, SLogger>(); 
	
	/** The out stream */
	private static volatile PrintStream OUT = System.out;
	/** The error stream */
	private static volatile PrintStream ERR = System.err;
	
	/** The default global logger */
	private static final SLogger globalLogger = new SLogger("[global]");
	
	/** Dot parser */
	private static final Pattern DOT_PATTERN = Pattern.compile("\\.");
	
	/**
	 * Creates a new SLogger for the passed class 
	 * @param clazz The class to create the logger for
	 * @return the logger
	 */
	public static SLogger logger(final Class<?> clazz) {
		return logger(clazz, 1);
	}

	
	/**
	 * Creates a new SLogger for the passed class 
	 * @param clazz The class to create the logger for
	 * @param segments The number of segments of the class name to include in the format, counting from right to left
	 * @return the logger
	 */
	public static SLogger logger(final Class<?> clazz, final int segments) {
		if(clazz==null) return globalLogger;
		SLogger logger = sloggers.get(clazz);
		if(logger==null) {
			synchronized(sloggers) {
				logger = sloggers.get(clazz);
				if(logger==null) {
					final String[] pieces = DOT_PATTERN.split(clazz.getName());
					final int seg;
					if(segments < 1 || segments > pieces.length) {
						seg = 1;
					} else {
						seg = segments;
					}
					logger = new SLogger(Arrays.toString(Arrays.copyOfRange(pieces, (pieces.length - seg), (pieces.length))).replace(", ", ".") + ": ");
					sloggers.put(clazz, logger);
				}
			}
		}
		return logger;
	}
	
	/**
	 * <p>Title: SLogger</p>
	 * <p>Description: Super simple logger</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.SimpleLogger.SLogger</code></p>
	 */
	public static class SLogger {
		/** The prefix format */
		private final String fmt;		
		
		/**
		 * Creates a new SLogger
		 * @param fmt The prefix format
		 */
		private SLogger(final String fmt) {
			this.fmt = fmt;
		}
		
		/**
		 * Simple out log
		 * @param format The message format
		 * @param args The message tokens
		 */
		public void log(final Object format, final Object...args) {
			OUT.println(String.format(fmt + format.toString(), args));
		}

		/**
		 * Simple err log
		 * @param format The message format
		 * @param args The message tokens
		 */
		public void loge(final Object format, final Object...args) {
			ERR.println(String.format(fmt + format.toString(), args));
			if(args.length>0 && args[args.length-1] != null && args[args.length-1] instanceof Throwable) {
				((Throwable)args[args.length-1]).printStackTrace(ERR);
			}
		}
		
		/**
		 * Formats a log message and returns it
		 * @param format The message format
		 * @param args The message tokens
		 * @return the formatted message
		 */
		public String format(final Object format, final Object...args) {
			return String.format(fmt + format.toString(), args);
		}
		
	}
	

}
