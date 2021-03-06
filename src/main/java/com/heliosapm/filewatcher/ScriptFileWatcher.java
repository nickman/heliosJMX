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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import javax.management.MBeanNotificationInfo;
import javax.management.Notification;
import javax.management.NotificationBroadcasterSupport;
import javax.management.Query;
import javax.management.QueryExp;
import javax.management.StringValueExp;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;

import org.cliffc.high_scale_lib.NonBlockingHashMap;
import org.cliffc.high_scale_lib.NonBlockingHashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.heliosapm.jmx.cache.CacheStatistics;
import com.heliosapm.jmx.concurrency.JMXManagedThreadPool;
import com.heliosapm.jmx.notif.SharedNotificationExecutor;
import com.heliosapm.jmx.util.helpers.ConfigurationHelper;
import com.heliosapm.jmx.util.helpers.JMXHelper;
import com.heliosapm.jmx.util.helpers.URLHelper;
import com.heliosapm.script.DeployedScript;
import com.heliosapm.script.StateService;



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
		new MBeanNotificationInfo(new String[] {NOTIF_FILE_NEW, NOTIF_FILE_DELETE, NOTIF_FILE_MOD}, Notification.class.getName(), "A file change event"),
		new MBeanNotificationInfo(new String[] {NOTIF_UNKNOWN_DELETE}, Notification.class.getName(), "A deletion of unknown type event")
	};
	
	/** Non script extensions */
	private static final Set<String> NON_SCRIPT_EXTENSIONS = Collections.unmodifiableSet(new LinkedHashSet<String>(Arrays.asList("dir", "config", "fixture", "service")));
	
	
	/** Instance logger */
	protected final Logger log = LoggerFactory.getLogger(getClass());
	/** Flag indicating if service has started */
	protected final AtomicBoolean started = new AtomicBoolean(false);
	/** The file event handler thread pool */
	protected final JMXManagedThreadPool eventHandlerPool; 
	/** The watch key cache */
	protected final Cache<Path, WatchKey> hotDirs; 
	/** The watched templates and watch keys */
	protected final Cache<String, WatchKey> templateWatches;
	/** The watched templates and mappings */
	protected final Cache<String, Set<String>> templateMappings;
	
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
	protected final Set<String> ignoredExtensions = new CopyOnWriteArraySet<String>(Arrays.asList("bak", "swp", "swx", ""));
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
	/** WatchEventType typed event handlers */
	protected final Map<WatchEventType, Set<FileChangeEventHandler>> eventHandlers;
	/** Map of root watched directory keyed by the watched directory */
	protected final Map<File, File> rootDirs = new NonBlockingHashMap<File, File>();
	
	/** Fill in {pwd} config files created. We'll delete them on shutdown if they are zero soze  */
	protected final Set<File> createdConfigFiles = new NonBlockingHashSet<File>();
	
	/** Singleton ctor reentrancy check */
	private static final AtomicBoolean initing = new AtomicBoolean(false); 
	
	/**
	 * Acquires the ScriptFileWatcher singleton instance
	 * @return the ScriptFileWatcher singleton instance
	 */
	public static ScriptFileWatcher getInstance() {
		if(instance==null) {
			synchronized(lock) {
				if(instance==null) {
					if(!initing.compareAndSet(false, true)) {
						throw new RuntimeException("ScriptFileWatcher call to StateService.getInstance(). Programmer Error.");
					}					
					instance = new ScriptFileWatcher();
					instance.scanHotDirsAtStart();
					instance.started.set(true);
					Runtime.getRuntime().addShutdownHook(new Thread(){
						public void run() {
//							instance.cleanFillInFiles();
						}
					});
					
				}
			}
		}
		return instance;
	}
	
	public static void main(String[] args) {
		String initialHotDir = "./src/test/resources/testdir/hotdir"; 
		initialHotDir = Paths.get(initialHotDir).normalize().toFile().getAbsolutePath();
				//System.getProperty("java.io.tmpdir") + File.separator + "hotdir";
		System.setProperty(INITIAL_DIRS_PROP, initialHotDir);
		System.err.println("Initial HotDir:" + initialHotDir);
		JMXConnectorServer server = null;
		try {
			JMXServiceURL surl = new JMXServiceURL("service:jmx:jmxmp://0.0.0.0:8006");
			server = JMXConnectorServerFactory.newJMXConnectorServer(surl, null, JMXHelper.getHeliosMBeanServer());
			server.start();
		} catch (Exception ex) {
			ex.printStackTrace(System.err);			
		}		
		
		try {
			ScriptFileWatcher sfw = getInstance();
		} catch (Exception ex) {
			
		}
		//sfw.addWatchDirectory(initialHotDir);  //System.getProperty("java.io.tmpdir") + File.separator + "hotdir"
		final JMXConnectorServer svr = server;
		new Thread("ExitWatcher") {
			public void run() {
				BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
				while(true) {
					try {
						String s = br.readLine();
						if(s.equalsIgnoreCase("exit")) {
							if(svr!=null) svr.stop();
							instance.cleanFillInFiles();
							System.exit(0);
						}
					} catch (Exception ex) {
						/* No Op */
					}
				}				
			}
		}.start();
		//SystemClock.sleep(10000000);
	}
	
	private void cleanFillInFiles() {
		for(File f: createdConfigFiles) {
			if(f.length()==0) {
				boolean del = f.delete();
				log.info("Deleted zero size config [" + f + "]. Deleted ?:" + del);
			}
		}		
	}
	

	/**
	 * Creates a new ScriptFileWatcher
	 */
	private ScriptFileWatcher() {
		super(SharedNotificationExecutor.getInstance(), notificationInfos);
		keepRunning.set(true);
		try {
			watcher = FileSystems.getDefault().newWatchService();
		} catch (Exception ex) {
			log.error("Failed to create default WatchService", ex);
			throw new RuntimeException("Failed to create default WatchService", ex);
		}
		eventHandlerPool = new JMXManagedThreadPool(THREAD_POOL_OBJECT_NAME, "FileWatcherThreadPool", CORES, CORES * 2, 1024, 60000, 100, 90);		
		hotDirs = CacheStatistics.getJMXStatisticsEnableCache(CacheBuilder.from(ConfigurationHelper.getSystemThenEnvProperty(DIR_CACHE_PROP, DIR_CACHE_DEFAULT_SPEC)), "watchedDirectories");
		// Cache<Path, WatchKey>
		templateWatches = CacheStatistics.getJMXStatisticsEnableCache(CacheBuilder.from(ConfigurationHelper.getSystemThenEnvProperty(TWATCH_CACHE_PROP, DIR_CACHE_DEFAULT_SPEC)), "templateWatches");
		// Cache<Path, Set<File>>
		templateMappings = CacheStatistics.getJMXStatisticsEnableCache(CacheBuilder.from(ConfigurationHelper.getSystemThenEnvProperty(TMAP_CACHE_PROP, DIR_CACHE_DEFAULT_SPEC)), "templateMappings");
		
		scriptManager = StateService.getInstance();
		Map<WatchEventType, Set<FileChangeEventHandler>> tmpHandlerMap = new EnumMap<WatchEventType, Set<FileChangeEventHandler>>(WatchEventType.class);
		for(WatchEventType type: WatchEventType.values()) {
			tmpHandlerMap.put(type, new NonBlockingHashSet<FileChangeEventHandler>());
		}		
		eventHandlers = Collections.unmodifiableMap(tmpHandlerMap);
		Runtime.getRuntime().addShutdownHook(new Thread(){
			public void run() {
				shutdown();
			}
		});
		JMXHelper.registerMBean(OBJECT_NAME, this);
		registerEventHandler(newDirectoryHandler);
		registerEventHandler(newFileHandler);
		registerEventHandler(deletedFileHandler);
		registerEventHandler(deletedDirectoryHandler);
		addDefaultHotDirs();
		startFileEventListener();
		
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.filewatcher.ScriptFileWatcherMXBean#isStarted()
	 */
	@Override
	public boolean isStarted() {
		return started.get();
	}
	
	
	/**
	 * Adds the default hot directories
	 */
	private void addDefaultHotDirs() {
		String[] initialDirs = ConfigurationHelper.getSystemThenEnvPropertyArray(INITIAL_DIRS_PROP, "");
		for(String dir: initialDirs) {
			final File f = new File(dir).getAbsoluteFile();
			if(f.exists() && f.isDirectory()) {
				addWatchDirectory(f.getAbsolutePath(), new FileEvent(f.getAbsolutePath(), ENTRY_CREATE));
			}
		}		
	}
	
	/** The inside event handler for new directories */
	private FileChangeEventHandler newDirectoryHandler = new AbstractFileChangeEventHandler(WatchEventType.DIR_NEW) {
		@Override
		public void eventFired(final FileEvent event) {
			if(!event.exists()) return;
			log.debug("Processing new Dir event for [{}]", event.getFileName());
			addWatchDirectory(event.getFileName(), event);			
		}
	};
	
	/** The inside event handler for new and updated files */
	private FileChangeEventHandler newFileHandler = new AbstractFileChangeEventHandler(WatchEventType.FILE_MOD, WatchEventType.FILE_NEW) {
		@Override
		public void eventFired(final FileEvent event) {
			if(!event.exists()) return;
			if(event.isSymbolicLink() || event.isNewFileEvent()) {
				if(!event.isSymbolicLink()) {
					if(!event.isNewFileEvent()) {
						final Set<String> links = templateMappings.getIfPresent(event.getFileName());
						if(links!=null && !links.isEmpty()) {
							for(String linkedFile: links) {
								//URLHelper.touch(URLHelper.toURL(linkedFile));
								log.info("\n\t---->  Refreshing Linked File [{}]", linkedFile);
							}
						}					
					}
					//return;
				}
			}
			final String name = event.getFileName();
			if(!hotFileNames.contains(name)) {
				synchronized(hotFileNames) {
					if(!hotFileNames.contains(name)) {
						log.debug("Processing new File event for [{}]", event.getFileName());
						addWatchFile(event.getFileName(), event);						
						return;
					}
				}
			} else {
				scriptManager.getDeployedScript(event.getFileName());
			}
			log.debug("Processing modified File event for [{}]", event.getFileName());
		}
	};
	
	/** The inside event handler for deleted files */
	private FileChangeEventHandler deletedFileHandler = new AbstractFileChangeEventHandler(WatchEventType.FILE_DELETE) {
		@Override
		public void eventFired(final FileEvent event) {
			final String name = event.getFileName();			
			if(hotFileNames.contains(name)) {
				synchronized(hotFileNames) {
					if(hotFileNames.contains(name)) {
						log.debug("Processing deleted File event for [{}]", event.getFileName());
						removeWatchFile(event.getFileName(), event);						
						return;
					}
				}
			}			
		}
	};
	
	/** The inside event handler for deleted directories */
	private FileChangeEventHandler deletedDirectoryHandler = new AbstractFileChangeEventHandler(WatchEventType.DIR_DELETE) {
		@Override
		public void eventFired(final FileEvent event) {
			final String name = event.getFileName();			
			if(hotDirNames.contains(name)) {
				synchronized(hotDirNames) {
					if(hotDirNames.contains(name)) {
						log.debug("Processing deleted Directory event for [{}]", event.getFileName());
						removeWatchDirectory(event.getFileName(), event);						
						return;
					}
				}
			}			
		}
	};
	
	
	
	
	/**
	 * Stops the ScriptFileWatcher
	 */
	void shutdown() {
		keepRunning.set(false);
		instance = null;
	}
	
	
	/**
	 * Starts the file change listener
	 */
	public void startFileEventListener() {
		startProcessingThread();
		try {
			watcher = FileSystems.getDefault().newWatchService();			
//			updateWatchers();
			watchKeyThread = new Thread("ScriptFileWatcherWatchKeyThread"){
				WatchKey watchKey = null;
				
				
				//==========================================================================================================
				//		WATCH KEY THREAD
				//==========================================================================================================
				public void run() {
					log.info("Started HotDeployer File Watcher Thread");
					while(keepRunning.get()) {
						try {
							watchKey = watcher.take();
							log.debug("Got watch key for [{}]", watchKey.watchable());							
					    } catch (InterruptedException ie) {
					        interrupted();
					        // check state
					        continue;
					    }
						if(watchKey!=null) {
							for (final WatchEvent<?> event: watchKey.pollEvents()) {
								if(!started.get()) continue;
								WatchEvent.Kind<?> kind = event.kind();
								if (kind == OVERFLOW) {
									log.warn("OVERFLOW OCCURED");
									if(!watchKey.reset()) {
										log.warn("Hot Dir for watch key [", watchKey, "] is no longer valid");
										watchKey.cancel();
										Path dir = (Path)watchKey.watchable();
										hotDirNames.remove(dir.toFile().getAbsolutePath());
										hotDirs.asMap().remove(dir);
									}
						            continue;
								}								
								Path dir = ((Path)watchKey.watchable()).toAbsolutePath();
							    Path activatedPath = Paths.get(dir.toString(), event.context().toString());
							    if(ignoredExtensions.contains(URLHelper.getExtension(activatedPath.toFile(), ""))) {
							    	continue;
							    }
							    enqueueFileEvent(new FileEvent(activatedPath.toFile().getAbsolutePath(), ((WatchEvent<Path>)event).kind()));
							}
						}
						boolean valid = watchKey.reset();
						// FIXME:  This stops polling completely.
					    if (!valid) {
					    	log.warn("Watch Key for [{}] is no longer valid. Polling will stop", watchKey.watchable());
					        //break;
					    }
					}
					// when we reach here, the watch key thread has stopped
					if(keepRunning.get()) {
						log.error("UNEXPECTED STOP ON WATCH KEY THREAD", new Throwable());
					}
				}
				//==========================================================================================================
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
	 * Registers a new event handler
	 * @param handler the handler to register
	 */
	public void registerEventHandler(final FileChangeEventHandler handler) {
		for(WatchEventType type: handler.getSupportedEvents()) {
			eventHandlers.get(type).add(handler);
		}
	}
	
	/**
	 * Unregisters an event handler
	 * @param handler the handler to unregister
	 */
	public void removeEventHandler(final FileChangeEventHandler handler) {
		for(WatchEventType type: handler.getSupportedEvents()) {
			eventHandlers.get(type).remove(handler);
		}
	}
	
	/**
	 * Enqueues a file event, removing any older instances that this instance will replace.
	 * The delay of the enqueued event will be set according to the event type.
	 * @param fe The file event to enqueue
	 * @return the deferred completion is the event is bypasses the queue and is processed async, null otherwise
	 */
	protected Future<?> enqueueFileEvent(final FileEvent fe) {
		if(fe.getEventType().equals(ENTRY_CREATE)) {
			return enqueueFileEvent(1000, fe);
		} else if(fe.getEventType().equals(ENTRY_MODIFY)) {
			return enqueueFileEvent(800, fe);
		} else if(fe.getEventType().equals(ENTRY_DELETE)) {
			return enqueueFileEvent(100, fe);
		}
		return null;
	}
	
	
	/**
	 * Enqueues a file event, removing any older instances that this instance will replace
	 * @param delay The delay to add to the passed file event to give the queue a chance to conflate obsolete events already queued
	 * @param fe The file event to enqueue
	 * @return the deferred completion is the event is bypasses the queue and is processed async, null otherwise
	 */
	protected Future<?> enqueueFileEvent(final long delay, final FileEvent fe) {
		int removes = 0;
		while(processingQueue.remove(fe)) {removes++;}
		fe.addDelay(delay);
		if(delay<1) {
			Future<?> future = processEventAsync(fe);
			log.debug("Async Executed File Event for [{}] and dropped [{}] older versions", fe.toShortString(), removes );
			return future;
		} else {		
			processingQueue.add(fe);
			log.debug("Queued File Event for [{}] and dropped [{}] older versions", fe.toShortString(), removes );
		}
		return null;
	}
	
	
	
	private static boolean isScript(final String extension) {
		if(extension==null || extension.trim().isEmpty()) return false;
		return !NON_SCRIPT_EXTENSIONS.contains(extension.trim().toLowerCase());
	}
	
	/**
	 * Scans the hot dirs looking for files to deploy at startup. 
	 * Since there's no file change events, we need to go and look for them.
	 * Scan order is:<ol>
	 *  <li>Directories only</li>
	 * 	<li>config</li>
	 *  <li>fixture</li>
	 *  <li>service</li>
	 *  <li>script</li>
	 * </ol>
	 */
	protected void scanHotDirsAtStart() {
		final Thread startThread = Thread.currentThread();
		final String threadName = startThread.getName();
		final Set<String> deploymentTypes = new LinkedHashSet<String>(NON_SCRIPT_EXTENSIONS); // "dir", "config", "fixture", "service"
		
		deploymentTypes.add("script");
		final Map<String, AtomicInteger> typeCounts = new LinkedHashMap<String, AtomicInteger>(deploymentTypes.size());
		for(String deploymentType: deploymentTypes) {
			typeCounts.put(deploymentType, new AtomicInteger(0));
		}
				//new String[] {"config", "fixture", "service", "*"};
		try {
			startThread.setName(threadName + "[Startup]");
//			final Set<Future<?>> futures = new LinkedHashSet<Future<?>>(128);
			final Set<String> failedDeployments = new LinkedHashSet<String>(128);
			final long timeout = TimeUnit.MILLISECONDS.convert(ConfigurationHelper.getLongSystemThenEnvProperty(STARTUP_TIMEOUT_PROP, DEFAULT_STARTUP_TIMEOUT), TimeUnit.SECONDS);
			final long startTime = System.currentTimeMillis();
			final long endTime = startTime + timeout;
			int totalDeployments = 0;
			final Set<String> copyOfHotDirNames = new HashSet<String>(hotDirNames);
			int purged = 0;
			for(String hd: copyOfHotDirNames) {
				purged += purgeZeroSizedConfigs(new File(hd));
			}
			log.info("\n\t===============================================\n\tPurged {} zero sized files at start\n\t===============================================\n", purged);
			
			for(String hd: copyOfHotDirNames) {
				addWatchDirectory(hd);
				File rootDir = new File(hd);
				log.info("Added watched dir [{}]", hd);
				fillInConfigs(rootDir);				
			}			
			for(final String deploymentType: deploymentTypes) {
				for(String hotDirPathName: copyOfHotDirNames) {					
					log.info("Deploying [{}]s in [{}]", deploymentType, hotDirPathName);
					try {
						Set<Future<?>> fs = treeScan(new File(hotDirPathName), true, true, deploymentType);
						if(!fs.isEmpty()) {
							log.info("Starting wait on [{}] [{}] task completions", fs.size(), deploymentType);
							final int completed = waitForCompletion(fs, failedDeployments, endTime);
							totalDeployments += completed;
							typeCounts.get(deploymentType).addAndGet(completed);
						}
					} catch (TimeoutException te) {
						log.error("Startup timed out after [{}] ms.", timeout, new Throwable());
						//System.exit(-1);  // to severe ? // can we recover ?						
					}
				}				
			}
			
			final long elapsed = System.currentTimeMillis() - startTime;
			StringBuilder b = new StringBuilder("\n\t========================================================");
			b.append("\n\tStartup Complete in ").append(elapsed).append(" ms.");
			b.append("\n\tSuccessful Deployments: ").append(totalDeployments);
			for(final Map.Entry<String, AtomicInteger> entry: typeCounts.entrySet()) {
				b.append("\n\t\t").append(entry.getKey()).append(" : ").append(entry.getValue().get());
			}
			b.append("\n\tFill In Configs: ").append(createdConfigFiles.size());
			b.append("\n\tFailed Deployments: ").append(failedDeployments.size());
			for(String fail: failedDeployments) {
				b.append("\n\t\t").append(fail);
			}
			b.append("\n\t========================================================\n");
			log.info(b.toString());
			final QueryExp QEXP =  Query.isInstanceOf(new StringValueExp(DeployedScript.class.getName()));
			SharedNotificationExecutor.getInstance().invokeOp(JMXHelper.objectName(DeployedScript.CONFIG_DOMAIN + ":*"), QEXP, "initConfig", true);
			SharedNotificationExecutor.getInstance().invokeOp(JMXHelper.objectName(DeployedScript.FIXTURE_DOMAIN + ":*"), QEXP, "initConfig", true);
			SharedNotificationExecutor.getInstance().invokeOp(JMXHelper.objectName(DeployedScript.SERVICE_DOMAIN + ":*"), QEXP, "initConfig", true);
			SharedNotificationExecutor.getInstance().invokeOp(JMXHelper.objectName(DeployedScript.DEPLOYMENT_DOMAIN + ":*"), QEXP, "initConfig", true);
		} catch (Exception x) {
			x.printStackTrace(System.err);
		} finally {
			startThread.setName(threadName);
		}
	}
	
	private int purgeZeroSizedConfigs(final File dir) {
		int purged = 0;
		for(final File f: dir.listFiles()) {
			if(f.isDirectory()) {
				purged += purgeZeroSizedConfigs(f);
			} else {
				if(f.length()==0) {
					f.delete();
					purged++;
				}
			}
		}
		return purged;
	}
	
	private void fillInConfigs(final File dir) throws Exception {
		File fic = new File(dir, dir.getName() + ".config");
		if(!fic.exists()) fic.createNewFile();
		for(final File f: dir.listFiles()) {
			if(f.isDirectory()) {
				fillInConfigs(f);
			}
		}
	}
	
	
	/**
	 * Waits for the completion of the futures in the passed set
	 * @param futures The set of futures to wait on
	 * @param failedDeployments A set to add failure error messages to
	 * @param endTime The hard end time after which the tasks are considered failed
	 * @return the number of completed tasks
	 * @throws TimeoutException thrown if the tasks are not complete by the end time
	 */
	protected int waitForCompletion(final Set<Future<?>> futures, final Set<String> failedDeployments, final long endTime) throws TimeoutException {
		if(futures.isEmpty()) return 0;
		int totalDeployments = 0;
		for(final Iterator<Future<?>> tasks = futures.iterator(); tasks.hasNext();) {
			final Future<?> f = tasks.next();
			while(true) {
				try {
					f.get(1000, TimeUnit.MILLISECONDS);
					totalDeployments++;
					break;
				} catch (InterruptedException e) {
					if(Thread.interrupted()) Thread.interrupted();						
				} catch (ExecutionException e) {
					e.printStackTrace(System.err);
					failedDeployments.add(e.getMessage());
					break;
				} catch (TimeoutException e) {
					long now = System.currentTimeMillis();
					if(now > endTime) {
						log.error("Startup timed out waiting on task completion");
						throw e;
//						System.exit(-1);  // to severe ? // can we recover ?
					}
				}
			}
		}
		return totalDeployments;
	}
	

	/**
	 * Performs a recursive tree scan
	 * @param dir The directory to scan
	 * @param activateFiles true to activate found scripts
	 * @param noDelay true to enqueue with no delay, false otherwise
	 */
	protected void treeScan(final File dir, final boolean activateFiles, final boolean noDelay) {
		treeScan(dir, activateFiles, noDelay, null);
	}
	
	
	
	
	/**
	 * Performs a recursive tree scan
	 * @param dir The directory to scan
	 * @param activateFiles true to activate found scripts
	 * @param noDelay true to enqueue with no delay, false otherwise
	 * @param extension The extension name to restrict the scan to. Ignored if null
	 * @return A set of the deferred completion results if submitted with <b>noDelay</b>, null otherwise
	 */
	protected Set<Future<?>> treeScan(final File dir, final boolean activateFiles, final boolean noDelay, final String extension) {		
		if(dir==null || !dir.exists() || !dir.isDirectory()) return null;		
		final Set<Future<?>> futures = noDelay ? new HashSet<Future<?>>(128) : null;
		for(final File dirFile: dir.listFiles()) {
			if(createdConfigFiles.contains(dirFile)) continue;
			if(dirFile.isDirectory()) {
				final File dirConfigFile = new File(dirFile, dirFile.getName() + ".config");
				if(!dirConfigFile.exists()) {
					try {
						dirConfigFile.createNewFile();
						createdConfigFiles.add(dirConfigFile);						
//						if(noDelay) {
//							final Future<?> future = enqueueFileEvent(0, new FileEvent(dirConfigFile.getAbsolutePath(), ENTRY_CREATE));
//							future.get();
//							log.info("Deployed Empty Config File [{}]", dirConfigFile.getAbsolutePath());
//						}
					} catch (Exception ex) {
						throw new RuntimeException("Failed to create missing mandatory config file [" + dirConfigFile + "]", ex);
					}
				}
				if(noDelay) {
					if((extension==null || "dir".equals(extension))) {
						futures.add(enqueueFileEvent(0, new FileEvent(dirFile.getAbsolutePath(), ENTRY_CREATE)));
					}
					futures.addAll(treeScan(dirFile, activateFiles, noDelay, extension));
				}
				else enqueueFileEvent(new FileEvent(dirFile.getAbsolutePath(), ENTRY_CREATE));
			} else if((extension==null || !"dir".equals(extension))) {
				final String ext = URLHelper.getExtension(dirFile);
				if(dirFile.length()==0) {
					createdConfigFiles.add(dirFile);
				}
				if(activateFiles && (extension==null || extension.equals(ext) ||  ("script".equals(extension) && isScript(ext)))) {
					if(noDelay) {
						futures.add(enqueueFileEvent(0, new FileEvent(dirFile.getAbsolutePath(), ENTRY_MODIFY)));
					}
					else enqueueFileEvent(new FileEvent(dirFile.getAbsolutePath(), ENTRY_MODIFY));
				}
			}
		}
		return futures;
	}
	
	/**
	 * Starts the processing queue processor thread
	 */
	void startProcessingThread() {
		processingThread = new Thread("FileEventProcessingThread") {
			@Override
			public void run() {
				log.info("Started FileEvent Queue Processor Thread");
				while(keepRunning.get()) {
					try {
						final FileEvent fe = processingQueue.take();
						if(fe!=null) {
							processEventAsync(fe);
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
	 * Asynchronously processes a file event
	 * @param fe The file event to process
	 * @return The future tracking the completion of the task
	 */
	protected Future<?> processEventAsync(final FileEvent fe) {		
		return eventHandlerPool.submit(new Runnable(){
			public void run() {
				log.info("Processing File Event [{}]", fe.toShortString());
				if(new File(fe.fileName).isFile()) {
					log.info("Processing File");
				}
				// if delete, then set file event file type according to isWatched
				if(fe.isDelete()) {
					if(!isWatched(fe.getFileName())) return;
					fe.setDeletedFileType(isWatchedDir(fe.getFileName()));								
				}
				if(inProcess.contains(fe)) {								
					enqueueFileEvent(2000, fe);
				} else {								
					final Set<FileChangeEventHandler> handlers = eventHandlers.get(fe.getWatchEventType());
					if(handlers.isEmpty()) return;
					for(FileChangeEventHandler handler: handlers) {
						handler.eventFired(fe);
					}
					log.info("PROCESSED ------------> [{}]", fe.toShortString());
				}									
			}
		});
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
	 * @param fileEvent An optional file event
	 * @throws IllegalArgumentException thrown if the passed name does not represent an existing and accessible directory
	 */
	public void addWatchDirectory(final String watchDir, final FileEvent fileEvent) {
		if(watchDir==null || watchDir.trim().isEmpty()) throw new IllegalArgumentException("The passed directory name was null or empty");		
		final File f = new File(watchDir.trim()).getAbsoluteFile();
		if(!f.exists()) throw new IllegalArgumentException("The passed directory [" + f + "] does not exist");
		if(!f.isDirectory()) throw new IllegalArgumentException("The passed directory [" + f + "] is *not* a directory");		
		if(hotDirNames.add(f.getAbsolutePath())) {
			Path path = f.getAbsoluteFile().toPath();
			try {
				WatchKey watchKey = path.register(watcher, ENTRY_DELETE, ENTRY_MODIFY, ENTRY_CREATE);
				hotDirs.put(path, watchKey);
				if(fileEvent!=null) sendNotification(fileEvent.toNotification(notificationIdFactory.incrementAndGet()));
				log.info("Added watched deployer directory [{}]", path);
				final String rootDirName = getRootDir(watchDir);
				if(rootDirName!=null) {
					rootDirs.put(f, new File(rootDirName));
				}
				if(started.get()) {
					treeScan(f, true, fileEvent==null ? false : !fileEvent.wasDelayed());
				}
			} catch (Exception ex) {
				hotDirNames.remove(f.getAbsolutePath());
				log.error("Failed to activate directory [{}] for watch services", watchDir, ex);
			}
		} 
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.filewatcher.ScriptFileWatcherMXBean#getRootDir(java.lang.String)
	 */
	@Override
	public String getRootDir(final String watchDir) {
		if(watchDir==null || watchDir.trim().isEmpty()) throw new IllegalArgumentException("The passed directory name was null or empty");
		final File f = new File(watchDir.trim()).getAbsoluteFile();
		if(!f.exists()) throw new IllegalArgumentException("The passed directory [" + f + "] does not exist");
		if(!f.isDirectory()) throw new IllegalArgumentException("The passed directory [" + f + "] is *not* a directory");		
		if(!isWatchedDir(watchDir)) return null;
		File parent = f.getParentFile();
		File watchedParent = null;
		while(parent != null) {
			if(isWatchedDir(parent.getAbsolutePath())) {
				watchedParent = parent;
				parent = parent.getParentFile();
			} else {
				break;
			}
			
		}
		return watchedParent==null ? f.getAbsolutePath() : watchedParent.getAbsolutePath();
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.filewatcher.ScriptFileWatcherMXBean#addWatchDirectory(java.lang.String)
	 */
	@Override
	public void addWatchDirectory(final String watchDir) {
		addWatchDirectory(watchDir, null);
	}
	
	/**
	 * Removes a watched directory from being watched
	 * @param watchDir The name of the directory
	 * @param fileEvent The optional file event
	 */
	public void removeWatchDirectory(final String watchDir, final FileEvent fileEvent) {
		if(watchDir==null || watchDir.trim().isEmpty()) throw new IllegalArgumentException("The passed directory name was null or empty");
		final File f = new File(watchDir.trim()).getAbsoluteFile();
		if(f.exists()) throw new IllegalArgumentException("The passed directory [" + f + "] still exists !");
		if(!hotDirNames.remove(watchDir)) return;
		rootDirs.remove(f);
		final Path path = Paths.get(watchDir);
		final WatchKey watchKey = hotDirs.asMap().remove(path);
		if(watchKey!=null) {
			watchKey.cancel();
			List<WatchEvent<?>> events = null;
			while(!(events=watchKey.pollEvents()).isEmpty()) {
				events.clear();
			}
		}
		if(fileEvent!=null) sendNotification(fileEvent.toNotification(notificationIdFactory.incrementAndGet()));
		log.info("Unwatched directory [{}]", watchDir);
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.filewatcher.ScriptFileWatcherMXBean#removeWatchDirectory(java.lang.String)
	 */
	@Override
	public void removeWatchDirectory(final String watchDir) {
		removeWatchDirectory(watchDir, null);
	}
	
	/**
	 * Marks a file as watched
	 * @param watchFile The file name being watched
	 * @param fileEvent The file event
	 */
	public void addWatchFile(final String watchFile, final FileEvent fileEvent) {
		if(watchFile==null || watchFile.trim().isEmpty()) throw new IllegalArgumentException("The passed file name was null or empty");
		final File f = new File(watchFile.trim()).getAbsoluteFile();
		if(!f.exists()) throw new IllegalArgumentException("The passed file [" + f + "] does not exist");
		if(f.isDirectory()) throw new IllegalArgumentException("The passed file [" + f + "] is *not* a regular file");
		if(shouldIgnore(f.toPath())) {
			log.info("Ignoring file [{}]", watchFile);
			return;
		}
		if(hotFileNames.add(watchFile)) {
			addWatchedTemplate(fileEvent, f);			
		}
		scriptManager.getDeployedScript(fileEvent.getFileName());
		if(fileEvent!=null) sendNotification(fileEvent.toNotification(notificationIdFactory.incrementAndGet()));
		log.info("Added watched file [{}]", watchFile);
	}
	
	
	
	/**
	 * Checks to see if a watched file is actually a symbolic link to a template.
	 * If it is, a watch is placed on the source file and the template mappings are updated.
	 * @param fileEvent The file event that triggered the added watched file
	 * @param file The file that triggered the event
	 */
	protected void addWatchedTemplate(final FileEvent fileEvent, final File file) {
		if(fileEvent.isSymbolicLink()) {
			final Path linkedTo = file.toPath();
			final Path linkedFrom;
			final Path linkedFromDir;
			final String linkedFromDirName;
			final String linkedFromName;
			try {
				linkedFrom = Files.readSymbolicLink(linkedTo);
				linkedFromDir = linkedFrom.getParent();
				linkedFromDirName = linkedFromDir.toFile().getAbsolutePath();
				linkedFromName = linkedFrom.toFile().getAbsolutePath();
				templateWatches.get(linkedFromDirName, new Callable<WatchKey>() {
					@Override
					public WatchKey call() throws Exception {						
						return linkedFromDir.register(watcher, ENTRY_DELETE, ENTRY_MODIFY);
					}
				});				
				templateMappings.get(linkedFromName, new Callable<Set<String>>() {
					@Override
					public Set<String> call() throws Exception {
						return new NonBlockingHashSet<String>();
					}
				}).add(file.getAbsolutePath());
				log.info("Added template symbolic link mapping\n\tfrom [{}]\n\tto [{}]", file.getAbsolutePath(), linkedFromName);
			} catch(Exception x) {
				log.error("Failed to get symbolic link source for [{}]", file, x);
				return;
			}			
		}
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.filewatcher.ScriptFileWatcherMXBean#addWatchFile(java.lang.String)
	 */
	@Override
	public void addWatchFile(final String watchFile) {
		addWatchFile(watchFile, null);
	}
	

	/**
	 * Removes a file from being watched
	 * @param watchFile The file name
	 * @param fileEvent The file event
	 */
	public void removeWatchFile(final String watchFile, final FileEvent fileEvent) {
		if(watchFile==null || watchFile.trim().isEmpty()) throw new IllegalArgumentException("The passed file name was null or empty");
		final File f = new File(watchFile.trim());
		if(f.exists()) throw new IllegalArgumentException("The passed file [" + f + "] still exists !");
		if(hotFileNames.remove(watchFile)) {
			if(fileEvent!=null) sendNotification(fileEvent.toNotification(notificationIdFactory.incrementAndGet()));
			log.info("Unwatched file [{}]", watchFile);
		}
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.filewatcher.ScriptFileWatcherMXBean#removeWatchFile(java.lang.String)
	 */
	@Override
	public void removeWatchFile(final String watchFile) {
		removeWatchFile(watchFile, null);
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
	 * @see com.heliosapm.filewatcher.ScriptFileWatcherMXBean#getProcessingQueueDepth()
	 */
	@Override
	public int getProcessingQueueDepth() {		
		return processingQueue.size();
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.filewatcher.ScriptFileWatcherMXBean#getRootDirs()
	 */
	@Override
	public Map<String, String> getRootDirs() {
		Map<String, String> roots = new HashMap<String, String>(rootDirs.size());
		for(Map.Entry<File, File> entry: rootDirs.entrySet()) {
			roots.put(entry.getKey().getAbsolutePath(), entry.getValue().getAbsolutePath());
		}
		return roots;
		
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
	 * {@inheritDoc}
	 * @see com.heliosapm.filewatcher.ScriptFileWatcherMXBean#getWatchedFiles()
	 */
	@Override
	public Set<String> getWatchedFiles() {
		return Collections.unmodifiableSet(hotFileNames);
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.filewatcher.ScriptFileWatcherMXBean#getWatchedDirectoryCount()
	 */
	@Override
	public int getWatchedDirectoryCount() {
		return hotDirNames.size();
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.filewatcher.ScriptFileWatcherMXBean#getWatchedFileCount()
	 */
	@Override
	public int getWatchedFileCount() {
		return hotFileNames.size();
	}

	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.filewatcher.ScriptFileWatcherMXBean#getWatchKeyThreadState()
	 */
	@Override
	public String getWatchKeyThreadState() {
		return watchKeyThread==null ? "NULL" : watchKeyThread.getState().name();
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.filewatcher.ScriptFileWatcherMXBean#getEventQueueThreadState()
	 */
	@Override
	public String getEventQueueThreadState() {
		return processingThread==null ? "NULL" : processingThread.getState().name();
	}
	
	
	/**
	 * Determines if the passed path should be ignored or not
	 * @param path The path to test
	 * @return true if the passed path should be ignored, false otherwise
	 */
	protected boolean shouldIgnore(final Path path) {
		if(path==null) return true;
		final File f = path.toFile();
		final String extension = URLHelper.getFileExtension(f, "").trim().toLowerCase();
		if(extension.isEmpty() || ignoredExtensions.contains(extension)) return true;
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

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.filewatcher.ScriptFileWatcherMXBean#getWatchedTemplateDirs()
	 */
	@Override
	public Set<String> getWatchedTemplateDirs() {		
		return Collections.unmodifiableSet(templateWatches.asMap().keySet());
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.filewatcher.ScriptFileWatcherMXBean#getWatchedTemplateDirCount()
	 */
	@Override
	public long getWatchedTemplateDirCount() {
		return templateWatches.size();
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.filewatcher.ScriptFileWatcherMXBean#getWatchedTemplates()
	 */
	@Override
	public Set<String> getWatchedTemplates() {
		return Collections.unmodifiableSet(templateMappings.asMap().keySet());
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.filewatcher.ScriptFileWatcherMXBean#getWatchedTemplateCount()
	 */
	@Override
	public long getWatchedTemplateCount() {
		return templateMappings.size();
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.filewatcher.ScriptFileWatcherMXBean#getTemplateLinks(java.lang.String)
	 */
	@Override
	public Set<String> getTemplateLinks(final String templateName) {
		final Set<String> templates = templateMappings.getIfPresent(templateName);
		return Collections.unmodifiableSet(templates != null ? templates : new HashSet<String>(0));
	}


}
