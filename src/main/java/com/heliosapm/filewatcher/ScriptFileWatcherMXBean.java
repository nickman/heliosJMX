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
package com.heliosapm.filewatcher;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.util.Map;
import java.util.Set;

import javax.management.ObjectName;

import com.heliosapm.jmx.util.helpers.JMXHelper;

/**
 * <p>Title: ScriptFileWatcherMXBean</p>
 * <p>Description: MXBean interface for {@link ScriptFileWatcher}</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.filewatcher.ScriptFileWatcherMXBean</code></p>
 */

public interface ScriptFileWatcherMXBean {
	/** The MBean ObjectName for the file watcher */
	public static final ObjectName OBJECT_NAME = JMXHelper.objectName("com.heliosapm.filewatcher:service=ScriptWatcher");

	/** The MBean ObjectName for the file watcher's thread pool */
	public static final ObjectName THREAD_POOL_OBJECT_NAME = JMXHelper.objectName("com.heliosapm.filewatcher:service=ThreadPool");

	/** The number of CPU cores available to the JVM */
	public static final int CORES = ManagementFactory.getOperatingSystemMXBean().getAvailableProcessors();
	
	
	/** The conf property name for the startup watched dirs */
	public static final String INITIAL_DIRS_PROP = "com.heliosapm.filewatcher.initialdirs";
	
	
	/** The conf property name for the cache spec for the watched directories */
	public static final String DIR_CACHE_PROP = "com.heliosapm.filewatcher.dircachespec";
	
	/** The default cache spec */
	public static final String DIR_CACHE_DEFAULT_SPEC = 
		"concurrencyLevel=" + CORES + "," + 
		"initialCapacity=256," + 
		"recordStats";
	
	/** If this pattern is in the first line of a file, the watcher will ignore it */
	public static final String IGNORE_PATTERN = "ignore=true";
	
	/** If this pattern is in the first line of a file, the watcher will ignore the file if it is already deployed */
	public static final String SKIP_PATTERN = "skip=true";
	
	
	/** The JMX notification type root */
	public static final String NOTIF_ROOT = "filewatcher.event";
	/** The JMX notification type for new directories */
	public static final String NOTIF_DIR_NEW = NOTIF_ROOT + ".dir.new";
	/** The JMX notification type for deleted directories */
	public static final String NOTIF_DIR_DELETE = NOTIF_ROOT + ".dir.delete";
	/** The JMX notification type for modified directories */
	public static final String NOTIF_DIR_MOD = NOTIF_ROOT + ".dir.mod";	
	/** The JMX notification type for new files */
	public static final String NOTIF_FILE_NEW = NOTIF_ROOT + ".file.new";
	/** The JMX notification type for deleted files */
	public static final String NOTIF_FILE_DELETE = NOTIF_ROOT + ".file.delete";
	/** The JMX notification type for modified files */
	public static final String NOTIF_FILE_MOD = NOTIF_ROOT + ".file.mod";
	/** The JMX notification type for deletions of unknown type */
	public static final String NOTIF_UNKNOWN_DELETE = NOTIF_ROOT + ".unknown.delete";
	
	/**
	 * Returns the registered ignored prefixes
	 * @return the registered ignored prefixes
	 */
	public Set<String> getIgnorePrefixes();

	/**
	 * Returns the registered ignored extensions
	 * @return the registered ignored extensions
	 */
	public Set<String> getIgnoreExtensions();
	
	/**
	 * Adds a directory to watch
	 * @param watchDir the name of a directory to watch
	 * @throws IllegalArgumentException thrown if the passed name does not represent an existing and accessible directory
	 */
	public void addWatchDirectory(String watchDir);
	
	/**
	 * Removes a watched directory from being watched
	 * @param watchDir The name of the directory to un-watch
	 */
	public void removeWatchDirectory(String watchDir);
	
	/**
	 * Adds a file to watch
	 * @param watchFile the name of a file to watch
	 * @throws IllegalArgumentException thrown if the passed name does not represent an existing and accessible regular file (not a dir)
	 */
	public void addWatchFile(String watchFile);
	
	/**
	 * Unwatches a watched file
	 * @param watchFile The file name to unwatch
	 */
	public void removeWatchFile(String watchFile);
	
	/**
	 * Adds a file or directory name prefix to ignore
	 * @param prefix a prefix to ignore
	 */
	public void addIgnorePrefix(final String prefix);
	
	/**
	 * Adds an extension to ignore
	 * @param extension an extension to ignore
	 */
	public void addIgnoreExtension(final String extension); 
	
	/**
	 * Returns a set of the absolute directory names that are being watched
	 * @return a set of the absolute directory names that are being watched
	 */
	public Set<String> getWatchedDirectories();
	
	/**
	 * Returns a set of the absolute file names that are being watched
	 * @return a set of the absolute file names that are being watched
	 */
	public Set<String> getWatchedFiles();
	
	
	/**
	 * Determines if the passed filename is a watched file
	 * @param fileName The file name to test
	 * @return true if the passed name is a watched file, false otherwise
	 */
	public boolean isWatchedFile(final String fileName);
	
	/**
	 * Determines if the passed filename is a watched directory
	 * @param dirName The directory name to test
	 * @return true if the passed name is a watched directory, false otherwise
	 */
	public boolean isWatchedDir(final String dirName);
	
	
	/**
	 * Returns the number of watched directories
	 * @return the number of watched directories
	 */
	public int getWatchedDirectoryCount();
	
	/**
	 * Returns the number of watched files
	 * @return the number of watched files
	 */
	public int getWatchedFileCount();
	
	/**
	 * Determinbes if the passed file name is a watched file or directory
	 * @param fileName The name to test
	 * @return true if the passed name is a watched directory or file, false otherwise
	 */
	public boolean isWatched(final String fileName);
	
	/**
	 * Returns the number of events in the processing queue
	 * @return the number of pending events
	 */
	public int getProcessingQueueDepth();
	
	/**
	 * Returns the state of the watch key processing thread
	 * @return the state of the watch key processing thread
	 */
	public String getWatchKeyThreadState();
	
	/**
	 * Returns the state of the file event queue processing thread
	 * @return the state of the file event queue processing thread
	 */
	public String getEventQueueThreadState();
	
	/**
	 * Finds the root watched directory for the passed directory name
	 * @param watchDir The name of the watched directory to find the watched root for
	 * @return The root watched directory, or null if the passed directory is not watched or if the 
	 * passed directory is the root (i.e. the parent of the directory is not watched)
	 */
	public String getRootDir(String watchDir);
	
	/**
	 * Returns a map of root watched directories keyed by each non-root watched directory
	 * @return a map of root watched directories 
	 */
	public Map<String, String> getRootDirs();
	
	
}
