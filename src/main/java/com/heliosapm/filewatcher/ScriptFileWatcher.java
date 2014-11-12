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

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.WatchEvent;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import javax.management.MBeanNotificationInfo;
import javax.management.NotificationBroadcasterSupport;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.heliosapm.jmx.concurrency.JMXManagedThreadPool;
import com.heliosapm.jmx.util.helpers.ConfigurationHelper;
import com.heliosapm.jmx.util.helpers.JMXHelper;
import com.heliosapm.jmx.util.helpers.StateService;
import com.heliosapm.jmx.util.helpers.SystemClock;
import com.heliosapm.jmx.util.helpers.URLHelper;
import com.sun.nio.sctp.Notification;


/**
 * <p>Title: ScriptFileWatcher</p>
 * <p>Description: File watch service to hot deploy/redeploy/undeploy scripts</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.filewatcher.ScriptFileWatcher</code></p>
 */

public class ScriptFileWatcher extends NotificationBroadcasterSupport implements ScriptFileWatcherMXBean {
	
	/** The singleton instance */
	private static volatile ScriptFileWatcher instance = null;
	/** The singleton instance ctor lock */
	private static final Object lock = new Object();

	/** The descriptors of the JMX notifications emitted by this service */
	private static final MBeanNotificationInfo[] notificationInfos = new MBeanNotificationInfo[] {
		new MBeanNotificationInfo(new String[] {NOTIF_DIR_NEW, NOTIF_DIR_DELETE}, Notification.class.getName(), "A directory change event"),
		new MBeanNotificationInfo(new String[] {NOTIF_FILE_NEW, NOTIF_FILE_DELETE, NOTIF_FILE_MOD}, Notification.class.getName(), "A file change event")
	};
	
	
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
	/** The directories being watched */
	protected final Set<String> hotDirNames = new CopyOnWriteArraySet<String>();
	/** The files being watched*/
	protected final Set<String> hotFileNames = new CopyOnWriteArraySet<String>();
	
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
	/** The JMX notification serial number generator */
	protected final AtomicLong notificationIdFactory = new AtomicLong();
	
	
	

	
	
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
	
	public static void main(String[] args) {
		
		ScriptFileWatcher sfw = getInstance();
		sfw.addWatchDirectory("/tmp/hotdir");
		
		SystemClock.sleep(10000000);
	}
	

	/**
	 * Creates a new ScriptFileWatcher
	 */
	private ScriptFileWatcher() {
		super(new JMXManagedThreadPool(NOTIF_THREAD_POOL_OBJECT_NAME, "WatcherNotificationThreadPool", CORES, CORES, 1024, 60000, 100, 90), notificationInfos);
		try {
			watcher = FileSystems.getDefault().newWatchService();
		} catch (Exception ex) {
			log.error("Failed to create default WatchService", ex);
			throw new RuntimeException("Failed to create default WatchService", ex);
		}
		eventHandlerPool = new JMXManagedThreadPool(THREAD_POOL_OBJECT_NAME, "FileWatcherThreadPool", CORES, CORES * 2, 1024, 60000, 100, 90);
		hotDirs = CacheBuilder.from(ConfigurationHelper.getSystemThenEnvProperty(DIR_CACHE_PROP, DIR_CACHE_DEFAULT_SPEC)).build();
		scriptManager = StateService.getInstance();
		Runtime.getRuntime().addShutdownHook(new Thread(){
			public void run() {
				shutdown();
			}
		});
		JMXHelper.registerMBean(OBJECT_NAME, this);
		startFileEventListener();
		try {
			JMXServiceURL surl = new JMXServiceURL("service:jmx:jmxmp://0.0.0.0:8006");
			JMXConnectorServer server = JMXConnectorServerFactory.newJMXConnectorServer(surl, null, JMXHelper.getHeliosMBeanServer());
			server.start();
		} catch (Exception ex) {
			ex.printStackTrace(System.err);
		}
		
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
//			updateWatchers();
			watchKeyThread = new Thread("ScriptFileWatcherWatchKeyThread"){
				WatchKey watchKey = null;
				public void run() {
					log.info("Started HotDeployer File Watcher Thread");
					while(keepRunning.get()) {
						try {
							watchKey = watcher.take();
							log.info("Got watch key for [" + watchKey.watchable() + "]");
							log.debug("File Event Queue:", processingQueue.size());
					    } catch (InterruptedException ie) {
					        interrupted();
					        // check state
					        continue;
					    }
						if(watchKey!=null) {
							for (final WatchEvent<?> event: watchKey.pollEvents()) {
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
								Path dir = ((Path)watchKey.watchable()).toAbsolutePath();
							    Path activatedPath = Paths.get(dir.toString(), event.context().toString());
							    enqueueFileEvent(new FileEvent(activatedPath.toFile().getAbsolutePath(), ((WatchEvent<Path>)event).kind()));
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
	 * {@inheritDoc}
	 * @see com.heliosapm.filewatcher.ScriptFileWatcherMXBean#isWatchedFile(java.lang.String)
	 */
	@Override
	public boolean isWatchedFile(final String fileName) {
		if(fileName==null || fileName.trim().isEmpty()) return false;
		return hotFileNames.contains(fileName.trim());
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.filewatcher.ScriptFileWatcherMXBean#isWatchedDir(java.lang.String)
	 */
	@Override
	public boolean isWatchedDir(final String dirName) {
		if(dirName==null || dirName.trim().isEmpty()) return false;
		return hotDirNames.contains(dirName.trim());
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.filewatcher.ScriptFileWatcherMXBean#isWatched(java.lang.String)
	 */
	@Override
	public boolean isWatched(final String fileName) {
		if(isWatchedDir(fileName)) return true;
		return isWatchedFile(fileName);
	}
	
	
	/**
	 * Enqueues a file event, removing any older instances that this instance will replace.
	 * The delay of the enqueued event will be set according to the event type.
	 * @param fe The file event to enqueue
	 */
	protected void enqueueFileEvent(final FileEvent fe) {
		if(fe.getEventType().equals(ENTRY_CREATE)) {
			enqueueFileEvent(1000, fe);
		} else if(fe.getEventType().equals(ENTRY_MODIFY)) {
			enqueueFileEvent(800, fe);
		} else if(fe.getEventType().equals(ENTRY_DELETE)) {
			enqueueFileEvent(100, fe);
		}
	}
	
	/**
	 * Enqueues a file event, removing any older instances that this instance will replace
	 * @param delay The delay to add to the passed file event to give the queue a chance to conflate obsolete events already queued
	 * @param fe The file event to enqueue
	 */
	protected void enqueueFileEvent(final long delay, final FileEvent fe) {
		int removes = 0;
		while(processingQueue.remove(fe)) {removes++;};
		fe.addDelay(delay);
		processingQueue.add(fe);
		log.debug("Queued File Event for [{}] and dropped [{}] older versions", fe.toShortString(), removes );
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
	
	
	/**
	 * Performs a recursive tree scan
	 * @param dir The directory to scan
	 * @param activateFiles true to activate found scripts
	 */
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
							// if delete, then set file event file type according to isWatched
							if(fe.isDelete()) {
								
							}
							
							
							log.debug("Processing File Event [{}]", fe.getFileName());
							if(inProcess.contains(fe)) {								
								enqueueFileEvent(2000, fe);
							} else {
								log.info("PROCESING ------------> [{}]", fe);
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
				WatchKey watchKey = path.register(watcher, ENTRY_DELETE, ENTRY_MODIFY, ENTRY_CREATE);
				hotDirs.put(path, watchKey);
				log.info("Added watched deployer directory [{}]", path.toFile().getAbsolutePath());
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
				WatchKey watchKey = path.register(watcher, ENTRY_DELETE, ENTRY_MODIFY, ENTRY_CREATE);
				hotDirs.put(path, watchKey);
				log.info("Added watched deployer directory [{}]", path);
			} catch (Exception ex) {
				hotDirNames.remove(f.getAbsolutePath());
				log.error("Failed to activate directory [{}] for watch services", watchDir, ex);
			}
		}
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.filewatcher.ScriptFileWatcherMXBean#addIgnoreExtension(java.lang.String)
	 */
	@Override
	public void addIgnoreExtension(final String extension) {
		if(extension==null || extension.trim().isEmpty()) throw new IllegalArgumentException("The passed extension was null or empty");
		ignoredExtensions.add(extension.trim().toLowerCase());
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.filewatcher.ScriptFileWatcherMXBean#addIgnorePrefix(java.lang.String)
	 */
	@Override
	public void addIgnorePrefix(final String prefix) {
		if(prefix==null || prefix.trim().isEmpty()) throw new IllegalArgumentException("The passed prefix was null or empty");
		ignoredPrefixes.add(prefix.trim().toLowerCase());
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.filewatcher.ScriptFileWatcherMXBean#getIgnorePrefixes()
	 */
	@Override
	public Set<String> getIgnorePrefixes() {
		return Collections.unmodifiableSet(ignoredPrefixes);
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.filewatcher.ScriptFileWatcherMXBean#getIgnoreExtensions()
	 */
	@Override
	public Set<String> getIgnoreExtensions() {
		return Collections.unmodifiableSet(ignoredExtensions);
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.filewatcher.ScriptFileWatcherMXBean#getWatchedDirectories()
	 */
	@Override
	public Set<String> getWatchedDirectories() {
		return Collections.unmodifiableSet(hotDirNames);
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
		if(f.isDirectory()) return !f.exists();
		String[] lines = URLHelper.getLines(URLHelper.toURL(f), 1);
		if(lines.length > 0) {
			if(lines[0]!=null && !lines[0].isEmpty() && lines[0].replace(" ", "").toLowerCase().contains(IGNORE_PATTERN)) return true;
		}
		return false;
	}
	
	protected void handleDelete(final Path inactivated, final Kind<Path> eventType) {
		log.info("Deleted [{}]", inactivated);
		enqueueFileEvent(500, new FileEvent(inactivated.toFile().getAbsolutePath(), eventType));
	}

}
