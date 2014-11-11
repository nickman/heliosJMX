/**
 * Helios, OpenSource Monitoring
 * Brought to you by the Helios Development Group
 *
 * Copyright 2014, Helios Development Group and individual contributors
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

import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.management.ObjectName;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.heliosapm.jmx.concurrency.JMXManagedThreadPool;
import com.heliosapm.jmx.util.helpers.ConfigurationHelper;
import com.heliosapm.jmx.util.helpers.JMXHelper;
import com.heliosapm.jmx.util.helpers.StateService;
import com.heliosapm.jmx.util.helpers.URLHelper;


/**
 * <p>Title: ScriptFileWatcher</p>
 * <p>Description: </p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.filewatcher.ScriptFileWatcher</code></p>
 */

public class ScriptFileWatcher {
	
	/** The singleton instance */
	private static volatile ScriptFileWatcher instance = null;
	/** The singleton instance ctor lock */
	private static final Object lock = new Object();
	
	/** The MBean ObjectName for the file watcher's thread pool */
	public static final ObjectName OBJECT_NAME = JMXHelper.objectName("com.heliosapm.filewatcher:service=ThreadPool");

	/** The number of CPU cores available to the JVM */
	public static final int CORES = ManagementFactory.getOperatingSystemMXBean().getAvailableProcessors();
	
	/** The conf property name for the cache spec for the watched directories */
	public static final String DIR_CACHE_PROP = "com.heliosapm.filewatcher.dircachespec";
	
	/** The default cache spec */
	public static final String DIR_CACHE_DEFAULT_SPEC = 
		"concurrencyLevel=" + CORES + "," + 
		"initialCapacity=256," + 
		"recordStats";
	
	/** If this pattern is in the first line of a file, the watcher will ignore it */
	public static final String IGNORE_PATTERN = "ignore=true";


	
	/** Instance logger */
	protected final Logger log = LoggerFactory.getLogger(getClass());
	/** The file event handler thread pool */
	protected final JMXManagedThreadPool eventHandlerPool; 
	/** The watch key cache */
	protected final Cache<Path, WatchKey> hotDirs; 
	/** The keep running flag */
	protected final AtomicBoolean keepRunning = new AtomicBoolean(false);
	/** The processing delay queue that ensures the same file is not processed concurrently for two different events */
	protected final DelayQueue<FileEvent> processingQueue = new DelayQueue<FileEvent>();
	/** A set of file events that are in process */
	protected Set<FileEvent> inProcess = new CopyOnWriteArraySet<FileEvent>();
	/** The top level directories to scan */
	protected final Set<String> hotDirNames = new CopyOnWriteArraySet<String>();
	/** Extensions to ignore */
	protected final Set<String> ignoredExtensions = new CopyOnWriteArraySet<String>(Arrays.asList("bak"));
	/** Prefixes to ignore */
	protected final Set<String> ignoredPrefixes = new CopyOnWriteArraySet<String>(Arrays.asList("."));	
	/** The script cache and compiler */
	protected final StateService scriptManager;
	/** The watchkey polling thread */
	protected Thread watchKeyThread = null;
	/** The processingQueue handling thread */
	protected Thread processingThread = null;
	/** The watch service */
	protected WatchService watcher = null;
	
	
	
	

	
	
	/**
	 * Acquires the ScriptFileWatcher singleton instance
	 * @return the ScriptFileWatcher singleton instance
	 */
	public static ScriptFileWatcher getInstance() {
		if(instance==null) {
			synchronized(lock) {
				if(instance==null) {
					instance = new ScriptFileWatcher();
				}
			}
		}
		return instance;
	}

	/**
	 * Creates a new ScriptFileWatcher
	 */
	private ScriptFileWatcher() {		
		eventHandlerPool = new JMXManagedThreadPool(OBJECT_NAME, "FileWatcherThreadPool", CORES, CORES * 2, 1024, 50000, 100, 90);
		hotDirs = CacheBuilder.from(ConfigurationHelper.getSystemThenEnvProperty(DIR_CACHE_PROP, DIR_CACHE_DEFAULT_SPEC)).build();
		scriptManager = StateService.getInstance();
		Runtime.getRuntime().addShutdownHook(new Thread(){
			public void run() {
				shutdown();
			}
		});
	}
	
	/**
	 * Stops the ScriptFileWatcher
	 */
	void shutdown() {
		
		instance = null;
	}
	
	
	/**
	 * Starts the file change listener
	 */
	public void startFileEventListener() {
		startProcessingThread();
		try {
			watcher = FileSystems.getDefault().newWatchService();
			scanHotDirsAtStart();
			updateWatchers();
			
			
			
			watchKeyThread = new Thread("ScriptFileWatcherWatchKeyThread"){
				WatchKey watchKey = null;
				public void run() {
					log.info("Started HotDeployer File Watcher Thread");
					while(keepRunning.get()) {
						try {
							watchKey = watcher.take();
							log.debug("Got watch key for [" + watchKey.watchable() + "]");
							log.debug("File Event Queue:", processingQueue.size());
					    } catch (InterruptedException ie) {
					        interrupted();
					        // check state
					        continue;
					    }
						if(watchKey!=null) {
							for (WatchEvent<?> event: watchKey.pollEvents()) {
								WatchEvent.Kind<?> kind = event.kind();
								if (kind == OVERFLOW) {
									log.warn("OVERFLOW OCCURED");
									if(!watchKey.reset()) {
										log.info("Hot Dir for watch key [", watchKey, "] is no longer valid");
										watchKey.cancel();
										Path dir = (Path)watchKey.watchable();
										hotDirNames.remove(dir.toFile().getAbsolutePath());
										hotDirs.asMap().remove(dir);
									}
						            continue;
								}								
								WatchEvent<Path> ev = (WatchEvent<Path>)event;
								Path dir = (Path)watchKey.watchable();
								
							    Path activatedPath = Paths.get(dir.toString(), ev.context().toString());
							    if(!shouldIgnore(activatedPath)) {
							    	if(activatedPath.toFile().isDirectory()) {
							    		addWatchDirectory(activatedPath.toFile().getAbsolutePath());
							    	} else {
							    		enqueueFileEvent(500, new FileEvent(activatedPath.toFile().getAbsolutePath(), ev.kind()));
							    	}
							    }
							}
						}
						boolean valid = watchKey.reset();
						// FIXME:  This stops polling completely.
					    if (!valid) {
					    	log.warn("Watch Key for [" , watchKey , "] is no longer valid. Polling will stop");
					        break;
					    }
					}
				}
			};
			watchKeyThread.setDaemon(true);
			keepRunning.set(true);
			watchKeyThread.start();			
		} catch (Exception ex) {
			log.error("Failed to start hot deployer", ex);
		}
	}
	
	/**
	 * Enqueues a file event, removing any older instances that this instance will replace
	 * @param delay The delay to add to the passed file event to give the queue a chance to conflate obsolete events already queued
	 * @param fe The file event to enqueue
	 */
	protected void enqueueFileEvent(long delay, FileEvent fe) {
		int removes = 0;
		while(processingQueue.remove(fe)) {removes++;};
		fe.addDelay(delay);
		processingQueue.add(fe);
		log.debug("Queued File Event for [{}] and dropped [{}] older versions", fe.getFileName(), removes );
	}
	
	/**
	 * Scans the hot dirs looking for files to deploy at startup. 
	 * Since there's no file change events, we need to go and look for them.
	 */
	protected void scanHotDirsAtStart() {
		for(String hotDirPathName: hotDirNames) {
			treeScan(new File(hotDirPathName), true);
		}
	}	
	
	
	protected void treeScan(final File dir, final boolean activateFiles) {
		if(dir==null || !dir.exists() || !dir.isDirectory() || shouldIgnore(dir.getAbsoluteFile().toPath())) return;		
		for(final File dirFile: dir.listFiles()) {
			if(dirFile.isDirectory()) {
				addWatchDirectory(dirFile.getAbsolutePath());
				treeScan(dirFile, activateFiles);
			} else {
				if(activateFiles) {
					// compile  // TODO:  need cache of tracked files, how they link to compiled scripts and the compile call
				}
			}
		}
	}
	
	/**
	 * Starts the processing queue processor thread
	 */
	void startProcessingThread() {
		processingThread = new Thread("SpringHotDeployerProcessingThread") {
			@Override
			public void run() {
				log.info("Started HotDeployer Queue Processor Thread");
				while(keepRunning.get()) {
					try {
						final FileEvent fe = processingQueue.take();						
						if(fe!=null) {
							log.debug("Processing File Event [{}]", fe.getFileName());
							if(inProcess.contains(fe)) {								
								enqueueFileEvent(2000, fe);
							}
						}
					} catch (Exception e) {
						if(interrupted()) interrupted();
					}
				}
			}
		};
		processingThread.setDaemon(true);
		processingThread.start();
	}
	
	/**
	 * Scans the hot diretory names and registers a watcher for any unwatched names,
	 * then removes any registered watchers that are no longer in the hot diretory names set 
	 * @throws IOException thrown on IO exceptions related to paths
	 */
	protected synchronized void updateWatchers() throws IOException {
		Map<Path, WatchKey> hotDirSnapshot = new HashMap<Path, WatchKey>(hotDirs.asMap());
		for(String fn: hotDirNames) {
			Path path = Paths.get(fn);
			if(hotDirs.asMap().containsKey(path)) {
				hotDirSnapshot.remove(path);
			} else {
				WatchKey watchKey = path.register(watcher, ENTRY_DELETE, ENTRY_MODIFY);
				hotDirs.put(path, watchKey);
				log.info("Added watched deployer directory [", path, "]");
			}
		}
		for(Map.Entry<Path, WatchKey> remove: hotDirSnapshot.entrySet()) {
			remove.getValue().cancel();
			log.info("Cancelled watch on deployer directory [", remove.getKey(), "]");
		}
		hotDirSnapshot.clear();
	}
	
	

	
	/**
	 * Adds a directory to watch
	 * @param watchDir the name of a directory to watch
	 * @throws IllegalArgumentException thrown if the passed name does not represent an existing and accessible directory
	 */
	public void addWatchDirectory(final String watchDir) {
		if(watchDir==null || watchDir.trim().isEmpty()) throw new IllegalArgumentException("The passed directory name was null or empty");
		final File f = new File(watchDir.trim());
		if(!f.exists()) throw new IllegalArgumentException("The passed directory [" + f + "] does not exist");
		if(!f.isDirectory()) throw new IllegalArgumentException("The passed directory [" + f + "] is *not* a directory");		
		if(hotDirNames.add(f.getAbsolutePath())) {
			Path path = f.getAbsoluteFile().toPath();
			try {
				WatchKey watchKey = path.register(watcher, ENTRY_DELETE, ENTRY_MODIFY);
				hotDirs.put(path, watchKey);
				log.info("Added watched deployer directory [", path, "]");
			} catch (Exception ex) {
				hotDirNames.remove(f.getAbsolutePath());
				log.error("Failed to activate directory [{}] for watch services", watchDir, ex);
			}
		}
	}
	
	/**
	 * Adds an extension to ignore
	 * @param extension an extension to ignore
	 */
	public void addIgnoreExtension(final String extension) {
		if(extension==null || extension.trim().isEmpty()) throw new IllegalArgumentException("The passed extension was null or empty");
		ignoredExtensions.add(extension.trim().toLowerCase());
	}
	
	/**
	 * Adds a file or directory name prefix to ignore
	 * @param prefix a prefix to ignore
	 */
	public void addIgnorePrefix(final String prefix) {
		if(prefix==null || prefix.trim().isEmpty()) throw new IllegalArgumentException("The passed prefix was null or empty");
		ignoredPrefixes.add(prefix.trim().toLowerCase());
	}
	
	/**
	 * Determines if the passed path should be ignored or not
	 * @param path The path to test
	 * @return true if the passed path should be ignored, false otherwise
	 */
	protected boolean shouldIgnore(final Path path) {
		if(path==null) return true;
		final File f = path.toFile();
		if(ignoredExtensions.contains(URLHelper.getFileExtension(f, "").trim().toLowerCase())) return true;
		final String name = f.getName().toLowerCase();
		for(final String pref: ignoredPrefixes) {
			if(name.startsWith(pref)) return true;
		}
		if(f.isDirectory()) return f.exists();
		String[] lines = URLHelper.getLines(URLHelper.toURL(f), 1);
		if(lines.length > 0) {
			if(lines[0]!=null && !lines[0].isEmpty() && lines[0].replace(" ", "").toLowerCase().contains(IGNORE_PATTERN)) return true;
		}
		return false;
	}

}
