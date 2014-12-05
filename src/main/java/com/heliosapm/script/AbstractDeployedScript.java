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
package com.heliosapm.script;

import java.io.File;
import java.io.FilenameFilter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import javax.management.AttributeChangeNotification;
import javax.management.MBeanNotificationInfo;
import javax.management.MBeanRegistration;
import javax.management.MBeanServer;
import javax.management.Notification;
import javax.management.NotificationBroadcasterSupport;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectName;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.heliosapm.filewatcher.ScriptFileWatcher;
import com.heliosapm.jmx.config.Configuration;
import com.heliosapm.jmx.config.ConfigurationManager;
import com.heliosapm.jmx.config.InternalConfigurationListener;
import com.heliosapm.jmx.execution.ExecutionSchedule;
import com.heliosapm.jmx.execution.ScheduleType;
import com.heliosapm.jmx.execution.ScheduledExecutionService;
import com.heliosapm.jmx.notif.SharedNotificationExecutor;
import com.heliosapm.jmx.util.helpers.JMXHelper;
import com.heliosapm.jmx.util.helpers.StringHelper;
import com.heliosapm.jmx.util.helpers.URLHelper;

/**
 * <p>Title: AbstractDeployedScript</p>
 * <p>Description: Base class for implementing {@link DeployedScript} extensions</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.script.AbstractDeployedScript</code></p>
 * @param <T> The type of the underlying executable script
 */

public abstract class AbstractDeployedScript<T> extends NotificationBroadcasterSupport implements DeployedScript<T>, MBeanRegistration, InternalConfigurationListener {
	/** Instance logger */
	protected final Logger log;
	/** The underlying executable component */
	protected T executable = null;
	/** The originating source file */
	protected final File sourceFile;
	/** The linked source file */
	protected final File linkedFile;
	/** The deployment short name */
	protected final String shortName;
	
	/** The checksum of the source */
	protected long checksum = -1L;
	/** The last modified timestamp of the source */
	protected long lastModified = -1L;
	
	/** The source file extension */
	protected final String extension;
	/** The root watched directory */
	protected final String rootDir;
	/** The path segments */
	protected final String[] pathSegments;
	/** The configuration for this deployment */
	protected final Configuration config;
	
	/** The watched configuration file */
	protected final AtomicReference<ObjectName> watchedConfig = new AtomicReference<ObjectName>(null);
	/** Flag indicating if config change listener is registered */
	protected final AtomicBoolean watchedConfigListenerRegistered = new AtomicBoolean(false);
	/** The version number of this deployment */
	protected final AtomicInteger version = new AtomicInteger(0);
	
	/** The config change listener */
	protected final NotificationListener configChangeListener = new NotificationListener() {
		@Override
		public void handleNotification(final Notification notification, final Object handback) {
			final Configuration configUpdate = (Configuration)notification.getUserData();
			config.load(configUpdate);
			if(config.areDependenciesReady()) {
				if(getStatus()==DeploymentStatus.NOONFIG) {
					setStatus(DeploymentStatus.READY, "All dependencies satisfied");
				}
			}
		}
	};
	
	/** The execution schedule */
	protected final AtomicReference<ExecutionSchedule> schedule = new AtomicReference<ExecutionSchedule>(ExecutionSchedule.NO_EXEC_SCHEDULE);
	/** The backup execution schedule, saved when execution was paused */
	protected final AtomicReference<ExecutionSchedule> pausedSchedule = new AtomicReference<ExecutionSchedule>(ExecutionSchedule.NO_EXEC_SCHEDULE);

	/** The schedule handle */
	protected final AtomicReference<ScheduledFuture<?>> scheduleHandle = new AtomicReference<ScheduledFuture<?>>(null); 
	/** The executable's execution timeout in ms. Defaults to 0 which is no timeout */
	protected long timeout = 0;
	
	/** The deployment's last set status message */
	protected final AtomicReference<String> lastStatusMessage = new AtomicReference<String>("Initialized");
	
	/** The status of this deployment */
	protected final AtomicReference<DeploymentStatus> status = new AtomicReference<DeploymentStatus>(DeploymentStatus.INIT);
	/** The last mod time  */
	protected final AtomicLong lastModTime = new AtomicLong(System.currentTimeMillis());
	/** The effective timestamp of the current status  */
	protected final AtomicLong statusTime = new AtomicLong(System.currentTimeMillis());
	/** The last execution elapsed time */
	protected final AtomicLong lastExecElapsed = new AtomicLong(-1L);
	
	/** The last execute time  */
	protected final AtomicLong lastExecTime = new AtomicLong(-1L);
	/** The last error time  */
	protected final AtomicLong lastErrorTime = new AtomicLong(-1L);
	/** The execution count since the last reset  */
	protected final AtomicLong execCount = new AtomicLong(0L);
	/** The error count since the last reset  */
	protected final AtomicLong errorCount = new AtomicLong(0L);
	/** Notification sequence number provider */
	protected static final AtomicLong sequence = new AtomicLong(0L);
	/** The deployment's JMX ObjectName */
	protected final ObjectName objectName;
	
	
	/** The descriptors of the JMX notifications emitted by this service */
	private static final MBeanNotificationInfo[] notificationInfos = new MBeanNotificationInfo[] {
		JMXHelper.META_CHANGED_NOTIF,
		new MBeanNotificationInfo(new String[]{NOTIF_STATUS_CHANGE}, AttributeChangeNotification.class.getName(), "JMX notification broadcast when the status of a deployment changes"),
		new MBeanNotificationInfo(new String[]{AttributeChangeNotification.ATTRIBUTE_CHANGE}, Notification.class.getName(), "JMX notification broadcast when the configuration of a deployment changes"),
		new MBeanNotificationInfo(new String[]{NOTIF_RECOMPILE}, Notification.class.getName(), "JMX notification broadcast when a deployment is recompiled")
	};

	/** Pattern to replace the skip entry in a source header */
	public static final Pattern SKIP_REPLACER = Pattern.compile("skip=(.*?),|$|\\s");
	
	
	/** The deployment's insta notifications */
	protected final Set<MBeanNotificationInfo> instanceNotificationInfos = new HashSet<MBeanNotificationInfo>(Arrays.asList(notificationInfos));
	
	/*
	 * Config Update Events:
	 * =====================
	 * On Updated Config (direct mod)
	 * On Updated Config (updated source)
	 * 		broadcast change with config in payload
	 * 
	 * 
	 * 
	 */

	/*
	 * Exe Update Events:
	 * =====================
	 * On Updated Config (direct mod)
	 * On Updated Config (updated source)
	 * 		internal update event
	 * On watched config update
	 * 		load passed config, fire internal updates
	 * On swapped watch file
	 * 		unregister old watched watch
	 * 		register new watch
	 * 		load new watch
	 * 		internal update event
	 * 
	 * 
	 * 
	 * 
	 */
	
	
	/**
	 * Creates a new AbstractDeployedScript
	 * @param sourceFile The originating source file
	 */
	public AbstractDeployedScript(File sourceFile) {
		super(SharedNotificationExecutor.getInstance(), notificationInfos);
		this.sourceFile = sourceFile;		
		shortName = URLHelper.getPlainFileName(sourceFile);
		final Path link = this.sourceFile.toPath();
		Path tmpPath = null;
		if(Files.isSymbolicLink(link)) {
			try {
				tmpPath = Files.readSymbolicLink(link);
			} catch (Exception ex) {
				tmpPath = null;
			}			
		}
		linkedFile = tmpPath==null ? null : tmpPath.toFile().getAbsoluteFile();
		String tmp = URLHelper.getFileExtension(sourceFile);
		if(tmp==null || tmp.trim().isEmpty()) throw new RuntimeException("The source file [" + sourceFile + "] has no extension");
		extension = tmp.toLowerCase();
		rootDir = ScriptFileWatcher.getInstance().getRootDir(sourceFile.getParentFile().getAbsolutePath());
		pathSegments = calcPathSegments();
		log = LoggerFactory.getLogger(StringHelper.fastConcatAndDelim("/", pathSegments) + "/" + sourceFile.getName().replace('.', '_'));
		objectName = buildObjectName();
		if(CONFIG_DOMAIN.equals(objectName.getDomain())) {
			config = null;
		} else {
			config = new Configuration(objectName, this);
			config.registerInternalListener(this);
		}
		checksum = URLHelper.adler32(URLHelper.toURL(sourceFile));
		lastModified = URLHelper.getLastModified(URLHelper.toURL(sourceFile));				
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.DeployedScript#isCommentLine(java.lang.String)
	 */
	@Override
	public boolean isCommentLine(final String text) {
		if(text==null || text.trim().isEmpty()) return false;
		String _text = text.trim();
		return (
			_text.startsWith("//") ||
			(_text.startsWith("/*") && _text.endsWith("*/"))
		);
	}
	
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.jmx.config.InternalConfigurationListener#onConfigurationItemChange(java.lang.String, java.lang.String)
	 */
	@Override
	public void onConfigurationItemChange(final String key, final String value) {
		
	}	
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.jmx.config.InternalConfigurationListener#onDependencyReadinessChange(boolean, java.lang.String)
	 */
	@Override
	public void onDependencyReadinessChange(final boolean ready, final String message) {
		if(ready) {
			setStatus(DeploymentStatus.READY, DeploymentStatus.setOf(DeploymentStatus.NOONFIG), message);
		} else {
			setStatus(DeploymentStatus.NOONFIG, DeploymentStatus.setOf(DeploymentStatus.READY), message);setStatus(DeploymentStatus.READY, DeploymentStatus.setOf(DeploymentStatus.NOONFIG), message);
		}
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.DeployedScript#getWatchedConfiguration()
	 */
	@Override
	public ObjectName getWatchedConfiguration() {
		return watchedConfig.get();
	}
	
	
//	public Set<ObjectName> getListenOnTargets() {
//		try {
//			final String onformat = String.format("%s:root=%s,%sextension=config,subextension=*,name=%%s", 
//					DeployedScript.CONFIG_DOMAIN, rootDir.replace(':', ';'), configDirs());
//			return new LinkedHashSet<ObjectName>(Arrays.asList(
//					//com.heliosapm.configuration:
//						//root=C;\hprojects\heliosJMX\.\src\test\resources\testdir\hotdir,
//						//d1=X,
//						//d2=Y,
//						// name=jmx,
//						// extension=config,
//						//subextension=properties
//					
//					JMXHelper.objectName(String.format(onformat, shortName)),
//					JMXHelper.objectName(String.format(onformat, pathSegments[pathSegments.length-1]))					
//			));
//		} catch (Exception ex) {
//			log.error("Failed to get listen on targets", ex);
//			throw new RuntimeException("Failed to get listen on targets", ex);
//		}
//	}
	
	/**
	 * Compiles the path segment into a set of ObjectName keypairs
	 * @return a set of ObjectName keypairs
	 */
	protected String configDirs() {
		StringBuilder b = new StringBuilder();
		for(int i = 1; i < pathSegments.length; i++) {
			b.append("d").append(i).append("=").append(pathSegments[i]).append(",");
		}
		return b.toString();
	}
	
//	/**
//	 * Builds the standard JMX ObjectName for this deployment
//	 * @return an ObjectName
//	 */
//	protected ObjectName buildObjectName() {
//		return JMXHelper.objectName(new StringBuilder(getDomain()).append(":")
//				.append("path=").append(join("/", pathSegments)).append(",")
//				.append("extension=").append(extension).append(",")
//				.append("name=").append(shortName)			
//				);
//	}
	
	/**
	 * Builds the standard JMX ObjectName for this deployment
	 * @return an ObjectName
	 */
	protected ObjectName buildObjectName() {
		final StringBuilder b = new StringBuilder(getDomain()).append(":")
				.append("root=").append(rootDir.replace(':', ';')).append(",");
		for(int i = 1; i < pathSegments.length; i++) {
			b.append("d").append(i).append("=").append(pathSegments[i]).append(",");
		}		
		b.append("name=").append(shortName).append(",")
			.append("extension=").append(extension);
		return JMXHelper.objectName(b);
	}
	
	
	/**
	 * {@inheritDoc}
	 * @see javax.management.NotificationBroadcasterSupport#getNotificationInfo()
	 */
	@Override
	public MBeanNotificationInfo[] getNotificationInfo() {
		if(instanceNotificationInfos.isEmpty()) {
			return super.getNotificationInfo();			
		}
		return instanceNotificationInfos.toArray(new MBeanNotificationInfo[instanceNotificationInfos.size()]);
	}
	
	/**
	 * Adds a new notification type for this deployment
	 * @param infos The notification infos to add
	 */
	protected void registerNotifications(final MBeanNotificationInfo...infos) {
		if(infos==null || infos.length==0) return;
		if(instanceNotificationInfos.isEmpty()) {
			Collections.addAll(instanceNotificationInfos, notificationInfos);
		}
		int added = 0;
		for(MBeanNotificationInfo info: infos) {
			if(info==null) continue;
			if(instanceNotificationInfos.add(info)) {
				added++;
			}
		}
		if(added>0) {		
			final Notification notif = new Notification(JMXHelper.MBEAN_INFO_CHANGED, objectName, sequence.incrementAndGet(), System.currentTimeMillis(), "Updated MBeanInfo");
			notif.setUserData(JMXHelper.getMBeanInfo(objectName));
			sendNotification(notif);
		}
	}
	
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.DeployedScript#getDomain()
	 */
	@Override
	public String getDomain() {
		return DEPLOYMENT_DOMAIN;
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.DeployedScriptMXBean#getVersion()
	 */
	@Override
	public int getVersion() {		
		return version.get();
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.DeployedScriptMXBean#getPathSegments(int)
	 */
	@Override
	public String[] getPathSegments(final int trim) {
		if(trim==0) return pathSegments.clone();
		final int pLength = pathSegments.length;
		final int absTrim = Math.abs(trim);
		
		if(absTrim > pLength) throw new IllegalArgumentException("The requested trim [" + trim + "] is larger than the path segment [" + pathSegments.length + "]");
		if(absTrim == pLength) return new String[0]; 
		LinkedList<String> psegs = new LinkedList<String>(Arrays.asList(pathSegments));
		for(int i = 0; i < absTrim; i++) {
			if(trim<0) psegs.removeFirst();
			else psegs.removeLast();
		}
		return psegs.toArray(new String[pLength-absTrim]);
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.DeployedScriptMXBean#getPendingDependencies()
	 */
	@Override
	public Map<String, String> getPendingDependencies() {
		return config.getPendingDependencies();
	}
	
	/**
	 * Builds the path segments for this file
	 * @return the path segments for this file
	 */
	protected String[] calcPathSegments() {
		final File rootFile = new File(rootDir); 
		if(rootFile.equals(sourceFile.getParentFile())) {
			return new String[]{rootFile.getName()}; 
		}
		final List<String> segments = new ArrayList<String>();
		segments.add(rootFile.getName());
		Collections.addAll(segments, rootFile.toPath().relativize(sourceFile.getParentFile().toPath()).toString().replace("\\", "/").split("/"));
		return segments.toArray(new String[segments.size()]);
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.DeployedScript#execute()
	 */
	@Override
	public T execute() {
		final long now = System.currentTimeMillis();
		DeploymentStatus ds = getStatus();
		if(ds == DeploymentStatus.NOONFIG) {
			if(config.areDependenciesReady()) {
				ds = getStatus();
			}
		}
		if(ds.canExec) {
			try {			
				final Object ret = doExecute();
				execCount.incrementAndGet();
				lastExecTime.set(now);
				long elapsed = System.currentTimeMillis() - now;
				lastExecElapsed.set(elapsed);
				log.debug("Elapsed: [{}] ms.", elapsed);
				return (T) ret;
			} catch (Exception ex) {
				final long er = errorCount.incrementAndGet();
				lastErrorTime.set(System.currentTimeMillis());			
				log.error("Failed to execute. Error Count: {}", er, ex);
				throw new RuntimeException("Failed to execute deployed script [" + this.getFileName() + "]", ex);
			}
		}
		return null;
	}
	
	/**
	 * {@inheritDoc}
	 * @see java.util.concurrent.Callable#call()
	 */
	@Override
	public T call() throws Exception {	
		return execute();
	}
	
//	/**
//	 * {@inheritDoc}
//	 * @see java.lang.Runnable#run()
//	 */
//	@Override
//	public void run() {
//		execute();
//	}
	
	/**
	 * To be implemented by concrete deployment scripts
	 * @return the return value of the execution
	 * @throws Exception thrown on any error in the execution
	 */
	protected abstract Object doExecute() throws Exception;
	
	/**
	 * Joins an array of strings to a delim separated string
	 * @param delim The delimeter 
	 * @param segs The array to join
	 * @return the joined string
	 */
	protected String join(final String delim, final String[] segs) {
		return Arrays.toString(segs).replace("]", "").replace("[", "").replace(" ", "").replace(",", delim);
	}
	
	/**
	 * Sets the status for this deployment
	 * @param status The status to set to
	 * @return the prior status
	 */
	protected DeploymentStatus setStatus(final DeploymentStatus status) {
		return setStatus(status, null);
	}
	
	/**
	 * Sets the status for this deployment
	 * @param status The status to set to
	 * @param messageFormat An optional message format, ignored if null
	 * @param tokens The message format tokens, ignored if the format is null 
	 * @return the prior status
	 */
	protected DeploymentStatus setStatus(final DeploymentStatus status, final String messageFormat, final Object...tokens) {
		return setStatus(status, null, messageFormat, tokens);
	}
	
	
	/**
	 * Sets the status for this deployment
	 * @param status The status to set to
	 * @param ifStatusIn Optional set of statuses that the current status must be in for the status change to fire.
	 * Ignored if null or empty
	 * @param messageFormat An optional message format, ignored if null
	 * @param tokens The message format tokens, ignored if the format is null 
	 * @return the prior status
	 */
	protected DeploymentStatus setStatus(final DeploymentStatus status, final Set<DeploymentStatus> ifStatusIn, final String messageFormat, final Object...tokens) {
		if(ifStatusIn!=null && !ifStatusIn.isEmpty() && !ifStatusIn.contains(this.status.get())) return this.status.get(); 
		final DeploymentStatus priorStatus = this.status.getAndSet(status);
		if(priorStatus!=status) {
			final long now = System.currentTimeMillis();
			statusTime.set(System.currentTimeMillis());
			if(messageFormat==null) {
				lastStatusMessage.set(String.format("[%s]: Status set to %s", new Date(now), status));
			} else {
				lastStatusMessage.set(String.format("[%s]: Status set to %s", new Date(now), status) + " : " + String.format(messageFormat, tokens));
			}
		}
		return priorStatus;
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.DeployedScript#setExecutable(java.lang.Object, long, long)
	 */
	@Override
	public void setExecutable(final T executable, final long checksum, final long timestamp) {
		if(executable==null) throw new IllegalArgumentException("The passed executable was null");		
		if(this.checksum != checksum || this.lastModified != timestamp) {
			this.executable = executable;
			this.checksum = checksum;
			this.lastModified = timestamp;
			final long now = System.currentTimeMillis();
			lastModTime.set(now);
			final int v = version.incrementAndGet();
			initExcutable();
			final String message = String.format("[%s]: Recompiled Deployment v.%s, [%s]", new Date(now), v, sourceFile.getAbsolutePath());
			lastStatusMessage.set(message);
			sendNotification(new Notification(NOTIF_RECOMPILE, objectName, sequence.incrementAndGet(), now, message));
		}
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.DeployedScript#initExcutable()
	 */
	public void initExcutable() {
		if(getConfiguration().areDependenciesReady()) {
			setStatus(DeploymentStatus.READY);
		} else {
			setStatus(DeploymentStatus.NOONFIG, "Pending Dependencies: %s", getConfiguration().getPendingDependencyKeys().toString());			
		}		
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.DeployedScriptMXBean#getDeploymentClassName()
	 */
	@Override
	public String getDeploymentClassName() {
		if(executable==null) return null;
		return executable.getClass().getName();
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.DeployedScript#setFailedExecutable(java.lang.String, long, long)
	 */
	public void setFailedExecutable(final String errorMessage, long checksum, long timestamp) {
		// for now, if the exe is not null, we set it null to mark it broken.
		// other options that could be supported are:
			// keep, but mark as out-of-sync
		final long now = System.currentTimeMillis();
		lastModTime.set(now);
		final DeploymentStatus prior = status.getAndSet(DeploymentStatus.BROKEN);
		this.executable = null;
		this.checksum = checksum;
		this.lastModified = timestamp;		
		
		final String message = String.format("[%s]: Deployment Recompilation Failed for [%s], Error:", new Date(now), sourceFile.getAbsolutePath(), errorMessage);
		lastStatusMessage.set(message);

		if(prior!=DeploymentStatus.BROKEN) {
			sendStatusChangeNotification(prior, DeploymentStatus.BROKEN, timestamp);
		}
		
	}
	
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.DeployedScriptMXBean#setExecutionSchedule(java.lang.String)
	 */
	@Override
	public void setExecutionSchedule(final String scheduleExpression) {
		if(scheduleExpression==null) throw new IllegalArgumentException("The passed Schedule Expression was null");
		final ExecutionSchedule newSchedule = ExecutionSchedule.getInstance(scheduleExpression, false);
		setExecutionSchedule(newSchedule);
	}
	
	/**
	 * Activates the passed execution schedule for this depoyment
	 * @param newSchedule the new execution schedule for this depoyment
	 */
	public void setExecutionSchedule(final ExecutionSchedule newSchedule) {
		if(newSchedule==null) throw new IllegalArgumentException("The passed ExecutionSchedule was null");
		if(newSchedule.getScheduleType()!= ScheduleType.NONE) {
			pausedSchedule.set(newSchedule);
		}
		final ExecutionSchedule priorSchedule = schedule.getAndSet(newSchedule);
		if(!newSchedule.equals(priorSchedule)) {
			final ScheduledFuture<?> priorFuture = scheduleHandle.get();
			if(priorFuture!=null) {
				priorFuture.cancel(true);				
			}
			scheduleHandle.set(ScheduledExecutionService.getInstance().scheduleDeploymentExecution(this));
		}	
	}
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.DeployedScriptMXBean#pauseScheduledExecutions()
	 */
	@Override
	public void pauseScheduledExecutions() {
		setExecutionSchedule(ExecutionSchedule.NO_EXEC_SCHEDULE);
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.DeployedScriptMXBean#resumeScheduledExecutions()
	 */
	@Override
	public String resumeScheduledExecutions() {
		final ExecutionSchedule es = pausedSchedule.get();
		if(es==null) {
			return ExecutionSchedule.NO_EXEC_SCHEDULE.toString();
		}
		setExecutionSchedule(es);
		return getSchedule();
	}
	
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.DeployedScript#getExecutionSchedule()
	 */
	@Override
	public ExecutionSchedule getExecutionSchedule() {
		return schedule.get();
	}
	

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.DeployedScript#getFileName()
	 */
	@Override
	public String getFileName() {
		return sourceFile.getAbsolutePath();
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.DeployedScriptMXBean#getShortName()
	 */
	@Override
	public String getShortName() {	
		return shortName;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.DeployedScriptMXBean#getLinkedFileName()
	 */
	@Override
	public String getLinkedFileName() {		
		return linkedFile==null ? null : linkedFile.getAbsolutePath();
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.DeployedScript#getExtension()
	 */
	@Override
	public String getExtension() {
		return extension;
	}


	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.DeployedScript#getRoot()
	 */
	@Override
	public String getRoot() {
		return rootDir;
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.DeployedScript#getPathSegments()
	 */
	@Override
	public String[] getPathSegments() {
		return pathSegments;
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.DeployedScript#getExecutable()
	 */
	@Override
	public T getExecutable() {
		if(executable==null) throw new RuntimeException("Executable has not been initialized");
		return executable;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.DeployedScript#getConfiguration()
	 */
	@Override
	public Configuration getConfiguration() {
		return config;
	}
	
	//==============================================================================================================================
	//		Add Configuration Ops 
	//==============================================================================================================================
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.DeployedScript#addConfiguration(java.util.Map)
	 */
	@Override
	public void addConfiguration(final Map<String, Object> config) {
		if(config==null) throw new IllegalArgumentException("The passed config map was null");
		config.putAll(config);
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.DeployedScript#addConfiguration(java.lang.String, java.lang.Object)
	 */
	@Override
	public void addConfiguration(final String key, final Object value) {
		if(key==null || key.trim().isEmpty()) throw new IllegalArgumentException("The passed key was null or empty");
		if(value==null) throw new IllegalArgumentException("The passed value was null");		
		getConfiguration().putTyped(key, value);
//		if(SCHEDULE_KEY.equals(key)) {
//			try {
//				
//			} catch (Exception x) {
//				
//			}
//		}
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.DeployedScriptMXBean#addConfiguration(java.lang.String, java.lang.String)
	 */
	@Override
	public void addConfiguration(final String key, final String value) {
		addConfiguration(key, (Object)value);		
	}
	
	
	//==============================================================================================================================	
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.DeployedScript#callInvocable(java.lang.String)
	 */
	@Override
	public String callInvocable(final String name) {
		if(name==null || name.trim().isEmpty()) throw new IllegalArgumentException("The passed name was null");
		return null;
	}
	
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.DeployedScript#getConfig(java.lang.String, java.lang.Class)
	 */
	@Override
	public <E> E getConfig(String key, Class<E> type) {
		if(key==null || key.trim().isEmpty()) throw new IllegalArgumentException("The passed key was null or empty");
		return (E)getConfiguration().get(key);
	}
	
//	/**
//	 * Triggers a config reload when a config item this deployment depends on changes
//	 * @param dependency The JMX ObjectName of the config item this deployment depends on
//	 * @param changedConfig The new config
//	 */
//	public void triggerConfigChange(final ObjectName dependency, final Map<String, Object> changedConfig) {
//		config.putAll(changedConfig);
////		ConfigurationManager.getInstance().addConfiguration(objectName, config);
//	}
	
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.DeployedScript#getConfig(java.lang.String)
	 */
	@Override
	public Object getConfig(String key) {
		if(key==null || key.trim().isEmpty()) throw new IllegalArgumentException("The passed key was null or empty");
		return getConfiguration().get(key);		
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.DeployedScript#getStatus()
	 */
	@Override
	public DeploymentStatus getStatus() {
		return status.get();
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.DeployedScriptMXBean#returnSourceBytes()
	 */
	@Override
	public byte[] returnSourceBytes() {
		return URLHelper.getBytesFromURL(URLHelper.toURL(sourceFile));
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.DeployedScriptMXBean#returnSource()
	 */
	@Override
	public String returnSource() {
		return URLHelper.getTextFromURL(URLHelper.toURL(sourceFile), 1000, 1000);
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.DeployedScript#getLastModTime()
	 */
	@Override
	public long getLastModTime() {
		return lastModTime.get();
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.DeployedScript#getLastExecTime()
	 */
	@Override
	public long getLastExecTime() {
		return lastExecTime.get();
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.DeployedScript#getLastErrorTime()
	 */
	@Override
	public long getLastErrorTime() {
		return lastErrorTime.get();
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.DeployedScriptMXBean#getLastExecElapsed()
	 */
	@Override
	public long getLastExecElapsed() {
		return lastExecElapsed.get();
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.DeployedScriptMXBean#getStatusMessage()
	 */
	@Override
	public String getStatusMessage() {
		return lastStatusMessage.get();
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.DeployedScriptMXBean#getExecutionTimeout()
	 */
	@Override
	public long getExecutionTimeout() {
		return timeout;
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.DeployedScriptMXBean#setExecutionTimeout(long)
	 */
	@Override
	public void setExecutionTimeout(final long timeout) {
		this.timeout = timeout;
	}
	

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.DeployedScript#getExecutionCount()
	 */
	@Override
	public long getExecutionCount() {
		return execCount.get();
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.DeployedScript#getErrorCount()
	 */
	@Override
	public long getErrorCount() {
		return errorCount.get();
	}


	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.DeployedScript#getObjectName()
	 */
	@Override
	public ObjectName getObjectName() {
		return objectName;
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.DeployedScript#pause()
	 */
	@Override
	public void pause() {
		setStatus(DeploymentStatus.PAUSED);		
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.DeployedScript#toJSON()
	 */
	@Override
	public String toJSON() {
		JSONObject json = new JSONObject();
		json.put("sourceFile", this.sourceFile.getAbsolutePath());
		json.put("extension", this.extension);
		json.put("objectName", this.objectName.toString());
		json.put("status", this.status);
		json.put("statusTime", this.statusTime.get());
		json.put("lastModTime", this.lastModTime.get());
		json.put("lastExecTime", this.lastExecTime.get());
		json.put("lastErrorTime", this.lastErrorTime.get());
		json.put("execCount", this.execCount.get());
		json.put("errorCount", this.errorCount.get());
		JSONObject cfig = new JSONObject();
		for(final String key: getConfiguration().keySet()) {
			final String value = getConfiguration().get(key);
			try { cfig.put(key, value); } catch (Exception x) {/* No Op */}
		}
		json.put("config", cfig);
		return json.toString();
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.DeployedScriptMXBean#getSchedule()
	 */
	@Override
	public String getSchedule() {
		return schedule.get().toString();
	}

	

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.DeployedScript#updateSource(java.lang.String, boolean)
	 */
	@Override
	public void updateSource(final String source, final boolean recompile) {
		String header = URLHelper.getLines(URLHelper.toURL(sourceFile), 1)[0].trim();
		String newHeader = null;
		if(header.startsWith("//")) {
			if(recompile) {
				newHeader = SKIP_REPLACER.matcher(header).replaceAll(ScriptFileWatcher.SKIP_PATTERN + ",");
			} else {
				newHeader = header;
			}
		} else {
			newHeader = recompile ? ("//" + ScriptFileWatcher.SKIP_PATTERN) : "//";  
		}
		URLHelper.writeToURL(URLHelper.toURL(sourceFile), (newHeader + "\n" + source).getBytes(), false);

	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.DeployedScript#undeploy()
	 */
	@Override
	public void undeploy() {
		executable = null;
		getConfiguration().clear();
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.DeployedScript#isExecutable()
	 */
	@Override
	public boolean isExecutable() {
		return status.get().canExec;
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.DeployedScript#isScheduleExecutable()
	 */
	@Override
	public boolean isScheduleExecutable() {
		return status.get().canSchedulerExec;
	}
	
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.DeployedScriptMXBean#executeForString()
	 */
	@Override
	public String executeForString() {		
		Object obj = execute();
		return obj==null ? null : obj.toString();
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.DeployedScriptMXBean#getConfigString(java.lang.String)
	 */
	@Override
	public String getConfigString(final String key) {
		Object obj = getConfig(key);
		return obj==null ? null : obj.toString();
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.DeployedScriptMXBean#getConfigurationMap()
	 */
	@Override
	public Map<String, String> getConfigurationMap() {
		return config.getInternalConfig();
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.DeployedScriptMXBean#getParentConfigurationMap()
	 */
	@Override
	public Map<String, String> getParentConfigurationMap() {
		if(watchedConfig.get()!=null) {
			final Configuration cfg = ConfigurationManager.getInstance().getConfig(watchedConfig.get());
			if(cfg!=null && !cfg.isEmpty()) {
				Map<String, String> smap = cfg.getInternalConfig();
				return smap;
			}
		}
		return Collections.emptyMap();
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.DeployedScriptMXBean#getStatusName()
	 */
	@Override
	public String getStatusName() {		
		return getStatus().name();
	}
	
	
	/**
	 * Callack when a deployment listens on the {pwd}.config because the {shortName}.config
	 * did not exist when this deployment started, but the {pwd}.config was just registered,
	 * so we drop the listener on {pwd}.config and put it on {shortName}.config
	 * @param directConfigObjectName The ObjectName of the {shortName}.config started notification
	 */
	protected void onReplaceWatchedConfiguration(final ObjectName directConfigObjectName) {
		
	}
	
//	/**
//	 * <p>Default implementation for executable, non-config deployments</p>
//	 * {@inheritDoc}
//	 * @see com.heliosapm.script.DeployedScript#getWatchedConfiguration()
//	 */
//	@Override
	
	public void initConfig() {
		watchedConfig.set(findWatchedConfiguration());
		if(watchedConfig.get()!=null) {
			Map<String, String> parentConfig = getParentConfigurationMap();
			if(parentConfig!=null && !parentConfig.isEmpty()) {
				this.getConfiguration().load(parentConfig);
			}
			if(watchedConfigListenerRegistered.compareAndSet(false, true)) {
				try {
					JMXHelper.addNotificationListener(watchedConfig.get(), configChangeListener, new NotificationFilter(){
						/**  */
						private static final long serialVersionUID = -2890751194005498532L;
		
						@Override
						public boolean isNotificationEnabled(final Notification notification) {
							final Object userData = notification.getUserData();
							return (
									notification.getSource().equals(watchedConfig.get())
									&&
									NOTIF_CONFIG_MOD.equals(notification.getType())
									&&
									userData != null
									&& 
									(userData instanceof Configuration)
									&&
									!(((Configuration)userData).isEmpty())
							);
						}
					}, null);
				} catch (Exception ex) {
					try { JMXHelper.removeNotificationListener(watchedConfig.get(), configChangeListener); } catch (Exception x) {/* No Op */}
					log.error("Failed to register configuration listener", ex);
					watchedConfigListenerRegistered.set(false);
				}
			}			
		}
	}
	
	/**
	 * Finds the ObjectName of the configuration MBean to watch
	 * @return the ObjectName of the configuration MBean to watch
	 */
	protected ObjectName findWatchedConfiguration() {
		/*
		 * if extension != config
		 * 		look for {shortName}.config
		 * 		if not found
		 * 			look for {pwd}.config   (error if not found)
		 * 			listen for registration of a future {shortName}.config
		 * 				when notified:
		 * 					stop listening on {pwd}.config and listen on {shortName}.config  
		 * 
		 * else   (we are a config)
		 * 		if(shortName != {pwd})
		 * 			look for {pwd}.config
		 * 		else  (we are the {pwd}.config
		 * 			look for {pwd.parent}.config (error if not found)
		 */
		
		final Hashtable<String, String> keyAttrs = new Hashtable<String, String>(objectName.getKeyPropertyList());
		
		final String pwd = sourceFile.getParentFile().getName();
		
		if(!"config".equals(extension)) {
			// look for {shortName}.config
			keyAttrs.put("extension", "config");				
			ObjectName watchedObjectName = JMXHelper.objectName(CONFIG_DOMAIN, keyAttrs);
			if(JMXHelper.isRegistered(watchedObjectName)) {
				return watchedObjectName;
			}
			// nope. register a listener in case he shows up, then look for {pwd}.config
			final NotificationListener lateComerListener = new NotificationListener() {
				@Override
				public void handleNotification(Notification notification, Object handback) {
					
				}
			};
			JMXHelper.addMBeanRegistrationListener(watchedObjectName, lateComerListener, 1);
			keyAttrs.put("name", pwd);
			watchedObjectName = JMXHelper.objectName(CONFIG_DOMAIN, keyAttrs);
			if(JMXHelper.isRegistered(watchedObjectName)) {
				return watchedObjectName;
			}
			log.warn("Failed to find expected dir watched configuration \n\tfor [" + objectName + "] \n\tat ObjectName [" + watchedObjectName + "]");
			throw new RuntimeException("Failed to find expected dir watched configuration for [" + objectName + "] at ObjectName [" + watchedObjectName + "]");				
		}
		// we're a config
		if(!shortName.equals(pwd)) {
			// we're a script config, so look for {pwd}.config
			keyAttrs.put("name", pwd);
			ObjectName watchedObjectName = JMXHelper.objectName(CONFIG_DOMAIN, keyAttrs);
			if(JMXHelper.isRegistered(watchedObjectName)) {
				return watchedObjectName;
			}
			log.warn("Failed to find expected dir watched configuration \n\tfor [" + objectName + "] \n\tat ObjectName [" + watchedObjectName + "]");
			return null;
			//throw new RuntimeException("Failed to find expected dir watched configuration for [" + objectName + "] at ObjectName [" + watchedObjectName + "]");				
		}
		// we're a {pwd}.connfig, so we need to find {pwd.parent}.config
		// yank the highest d# attribute so we go up one dir
		Integer high = JMXHelper.getHighestKey(objectName, "d");
		if(high==null) {
			return null;
		}
		keyAttrs.remove("d" + JMXHelper.getHighestKey(objectName, "d"));
		if(this.rootDir.equals(sourceFile.getParentFile().getParentFile().getAbsolutePath())) {
			return null;
		}
		// update the name to the {pwd.parent}				
		keyAttrs.put("name", sourceFile.getParentFile().getParentFile().getName());
		ObjectName watchedObjectName = JMXHelper.objectName(CONFIG_DOMAIN, keyAttrs);
		if(JMXHelper.isRegistered(watchedObjectName)) {
			return watchedObjectName;
		}
		log.warn("Failed to find expected parent dir watched configuration \n\tfor [" + objectName + "] \n\tat ObjectName [" + watchedObjectName + "]");
		throw new RuntimeException("Failed to find expected parent dir watched configuration for [" + objectName + "] at ObjectName [" + watchedObjectName + "]");
	}
	
	

	
	/**
	 * Sends a status change notification
	 * @param prior The prior status
	 * @param current The current status
	 * @param timestamp The timestamp
	 */
	protected void sendStatusChangeNotification(final DeploymentStatus prior, final DeploymentStatus current, final long timestamp) {
		final String msg = String.format("[%s] Status change for [%s]: from[%s] to [%s]", new Date(timestamp), objectName, prior, current);
		// last event msg
		sendNotification(new AttributeChangeNotification(objectName, sequence.incrementAndGet(), timestamp, msg, "Status", DeploymentStatus.class.getName(), prior.name(), current.name()));
	}
	
	/**
	 * Sends a configuration change notification when the configuration of the deployment changes
	 * @param jsonConfig The new configuration represented in JSON which is included as the user data in the sent notification
	 * @param timestamp The timestamp
	 */
	protected void sendConfigurationChangeNotification(final String jsonConfig, final long timestamp) {
		final String msg = String.format("[%s] Configuration change", new Date(timestamp));
		// last event msg		
		final Notification notif = new Notification(NOTIF_CONFIG_CHANGE, objectName, sequence.incrementAndGet(), timestamp, msg);
		notif.setUserData(jsonConfig);
		sendNotification(notif);
	}
	
	
//	/**
//	 * Sends a recompilation change notification when the deployment is recompiled
//	 * @param timestamp The timestamp
//	 */
//	protected void sendRecompilationChangeNotification(final long timestamp) {
//		final String msg = String.format("[%s] Recompilation change", new Date(timestamp));
//		// last event msg
//		sendNotification(new Notification(NOTIF_RECOMPILE, objectName, sequence.incrementAndGet(), timestamp, msg));
//	}
	
	
	protected static class ConfigFileFilter implements FilenameFilter {
		static final String ext = "config";
		String plainName = null;
		
		ConfigFileFilter(final String plainName) {
			this.plainName = plainName;
		}
		
		@Override
		public boolean accept(final File dir, final String name) {
			if(ext.equals(URLHelper.getExtension(new File(dir, name)))) {
				return (plainName!=null && plainName.equals(URLHelper.getPlainFileName(name))); 
			}			
			return false;
		}
	}
	
	protected static LinkedHashSet<File> locateConfigFiles(final File sourceFile, final String rootDir, final String[] pathSegments) {
		final LinkedHashSet<File> configs = new LinkedHashSet<File>();
		/*
		 * root config:  root.config, root.*.config
		 * for each dir:  foo.config, foo.*.config, dir.config, dir.*.config
		 * 
		 */
		final String deplName = URLHelper.getPlainFileName(sourceFile);
		final ConfigFileFilter cff = new ConfigFileFilter("root");
		final File root = new File(rootDir);
		for(File f: root.listFiles(cff)) {
			configs.add(f);
		}
		cff.plainName = deplName;
		File subDir = root;
		for(int i = 1; i < pathSegments.length; i++) {
			subDir = new File(subDir, pathSegments[i]);
			cff.plainName = pathSegments[i];
			for(File f: subDir.listFiles(cff)) {
				configs.add(f);
			}
			cff.plainName = deplName;
			for(File f: subDir.listFiles(cff)) {
				configs.add(f);
			}			
		}
		System.out.println(String.format("Config Files for deployment [%s] --> %s", sourceFile, configs.toString()));
		return configs;
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.DeployedScriptMXBean#isConfigFor(java.lang.String)
	 */
	@Override
	public boolean isConfigFor(String deployment) {
		return false;
	}


	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.DeployedScriptMXBean#getChecksum()
	 */
	@Override
	public long getChecksum() {
		return checksum;
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.DeployedScriptMXBean#getLastModified()
	 */
	@Override
	public long getLastModified() {
		return lastModified;
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.DeployedScriptMXBean#getLastModifiedDate()
	 */
	@Override
	public Date getLastModifiedDate() {
		return new Date(lastModified);
	}
	
	
	//================================================================================================================================
	//		MBeanRegistration default implementation
	//================================================================================================================================

	/**
	 * {@inheritDoc}
	 * @see javax.management.MBeanRegistration#preRegister(javax.management.MBeanServer, javax.management.ObjectName)
	 */
	@Override
	public ObjectName preRegister(final MBeanServer server, final ObjectName name) throws Exception {
		return name;
	}

	/**
	 * {@inheritDoc}
	 * @see javax.management.MBeanRegistration#postRegister(java.lang.Boolean)
	 */
	@Override
	public void postRegister(final Boolean registrationDone) {
		//this.watchedConfig.set(findWatchedConfiguration());
	}

	/**
	 * {@inheritDoc}
	 * @see javax.management.MBeanRegistration#preDeregister()
	 */
	@Override
	public void preDeregister() throws Exception {
		/* No Op */
	}

	/**
	 * {@inheritDoc}
	 * @see javax.management.MBeanRegistration#postDeregister()
	 */
	@Override
	public void postDeregister() {
		/* No Op */		
	}

	//================================================================================================================================
	
	
}
