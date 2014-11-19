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
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import javax.management.AttributeChangeNotification;
import javax.management.MBeanNotificationInfo;
import javax.management.MBeanRegistration;
import javax.management.MBeanServer;
import javax.management.Notification;
import javax.management.NotificationBroadcasterSupport;
import javax.management.ObjectName;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.heliosapm.filewatcher.ScriptFileWatcher;
import com.heliosapm.jmx.expr.CodeBuilder;
import com.heliosapm.jmx.notif.SharedNotificationExecutor;
import com.heliosapm.jmx.util.helpers.ConfigurationHelper;
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

public abstract class AbstractDeployedScript<T> extends NotificationBroadcasterSupport implements DeployedScript<T>, MBeanRegistration {
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
	protected final Map<String, Object> config = new ConcurrentHashMap<String, Object>();
	
	/** The schedule time in seconds */
	protected int schedule  = ConfigurationHelper.getIntSystemThenEnvProperty(DEFAULT_SCHEDULE_PROP, DEFAULT_SCHEDULE);
	
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
	
	
	/** The deployment's insta notifications */
	protected final Set<MBeanNotificationInfo> instanceNotificationInfos = new HashSet<MBeanNotificationInfo>(Arrays.asList(notificationInfos));

	
	
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
		checksum = URLHelper.adler32(URLHelper.toURL(sourceFile));
		lastModified = URLHelper.getLastModified(URLHelper.toURL(sourceFile));		
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.DeployedScriptMXBean#getListenOnTargets()
	 */
	@Override
	public Set<ObjectName> getListenOnTargets() {
		try {
			final String onformat = String.format("%s:root=%s,%sextension=config,subextension=*,name=%%s", 
					DeployedScript.CONFIG_DOMAIN, rootDir.replace(':', ';'), configDirs());
			return new LinkedHashSet<ObjectName>(Arrays.asList(
					//com.heliosapm.configuration:
						//root=C;\hprojects\heliosJMX\.\src\test\resources\testdir\hotdir,
						//d1=X,
						//d2=Y,
						// name=jmx,
						// extension=config,
						//subextension=properties
					
					JMXHelper.objectName(String.format(onformat, shortName)),
					JMXHelper.objectName(String.format(onformat, pathSegments[pathSegments.length-1]))					
			));
		} catch (Exception ex) {
			log.error("Failed to get listen on targets", ex);
			throw new RuntimeException("Failed to get listen on targets", ex);
		}
	}
	
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
	
	/**
	 * Builds the standard JMX ObjectName for this deployment
	 * @return an ObjectName
	 */
	protected ObjectName buildObjectName() {
		return JMXHelper.objectName(new StringBuilder(getDomain()).append(":")
				.append("path=").append(join("/", pathSegments)).append(",")
				.append("extension=").append(extension).append(",")
				.append("name=").append(sourceFile.getName())			
				);
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
	public Object execute() {
		final long now = System.currentTimeMillis();
		try {			
			final Object ret = doExecute();
			execCount.incrementAndGet();
			lastExecTime.set(now);
			lastExecElapsed.set(System.currentTimeMillis() - now);			
			return ret;
		} catch (Exception ex) {
			final long er = errorCount.incrementAndGet();
			lastErrorTime.set(System.currentTimeMillis());			
			log.error("Failed to execute. Error Count: {}", er, ex);
			throw new RuntimeException("Failed to execute deployed script [" + this.getFileName() + "]", ex);
		}						
	}
	
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
		final DeploymentStatus priorStatus = this.status.getAndSet(status);
		if(priorStatus!=status) {
			final long now = System.currentTimeMillis();
			statusTime.set(System.currentTimeMillis());
			lastStatusMessage.set(String.format("[%s]: Status set to %s", new Date(now), status));
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
			initExcutable();
			final String message = String.format("[%s]: Recompiled Deployment [%s]", new Date(now), sourceFile.getAbsolutePath());
			lastStatusMessage.set(message);
			sendNotification(new Notification(NOTIF_RECOMPILE, objectName, sequence.incrementAndGet(), now, message));
		}
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.DeployedScript#initExcutable()
	 */
	public void initExcutable() {
		/* No Op */
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
	 * @see com.heliosapm.script.DeployedScript#setSchedulePeriod(java.lang.Object)
	 */
	@Override
	public void setSchedulePeriod(Object period) {
		if(period==null) throw new IllegalArgumentException("The passed period was null");
		if(period instanceof Number) {
			schedule = ((Number)period).intValue();
		} else {
			try {
				schedule = new Double(period.toString().trim()).intValue();
			} catch (Exception ex) {
				throw new IllegalArgumentException("Could not determine period from object [" + period + "]");
			}
		}
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
	public Map<String, Object> getConfiguration() {
		return new HashMap<String, Object>(config);
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
		config.put(key, value);
		if(SCHEDULE_KEY.equals(key)) {
			try {
				
			} catch (Exception x) {
				
			}
		}
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
		return (E)config.get(key);
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.DeployedScript#getConfig(java.lang.String)
	 */
	@Override
	public Object getConfig(String key) {
		if(key==null || key.trim().isEmpty()) throw new IllegalArgumentException("The passed key was null or empty");
		return config.get(key);		
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
		for(Map.Entry<String, Object> entry: config.entrySet()) {
			try { cfig.put(entry.getKey(), entry.getValue()); } catch (Exception x) {/* No Op */}
		}
		json.put("config", cfig);
		return json.toString();
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.DeployedScript#getSchedulePeriod()
	 */
	@Override
	public int getSchedulePeriod() {
		return schedule;
	}
	
	/** Pattern to replace the skip entry in a source header */
	public static final Pattern SKIP_REPLACER = Pattern.compile("skip=(.*?),|$|\\s");

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
		config.clear();
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
		final Map<String, String> map = new HashMap<String, String>(config.size());
		for(Map.Entry<String, Object> entry: config.entrySet()) {
			map.put(entry.getKey(), entry.getValue().toString());
		}
		return map;
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
	 * {@inheritDoc}
	 * @see com.heliosapm.script.DeployedScriptMXBean#setSchedulePeriodInt(int)
	 */
	@Override
	public void setSchedulePeriodInt(final int period) {
		setSchedulePeriod(period);		
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
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.DeployedScriptMXBean#locateConfiguration()
	 */
	@Override
	public Set<ObjectName> locateConfiguration() {
		try {
			final Map<String, Object> configMap = new HashMap<String, Object>();
			final Set<ObjectName> configs = new LinkedHashSet<ObjectName>();
			// com.heliosapm.configuration:root=/tmp/hotdir,d1=X,d2=Y,d3=Z,name=Z,extension=config,subextension=js
			final String deplName = shortName;
			CodeBuilder b = new CodeBuilder("com.heliosapm.configuration:root=", rootDir.replace(':',  ';'), ",extension=config,subextension=*,");
			b.push();
			b.append("name=root");
			Collections.addAll(configs, JMXHelper.query(b.render()));
			b.pop();
			b.push();
			b.append("name=%s", deplName);
			Collections.addAll(configs, JMXHelper.query(b.render()));
			b.pop();
			for(int i = 1; i < pathSegments.length; i++) {
				b.append("d%s=%s,", i, pathSegments[i]);				
				Collections.addAll(configs, JMXHelper.query(b.render() + "name=" + pathSegments[i]));
				Collections.addAll(configs, JMXHelper.query(b.render() + "name=" + deplName));
				log.info("Config search:\n\tSearching for MBean [{}]", b.render());									
			}
			return configs;
		} catch (Exception ex) {
			log.error("Failure locating configuration MBeans", ex);
			throw new RuntimeException("Failure locating configuration MBeans", ex);
		}
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
	
	/**
	 * <p>Title: ConfigFinder</p>
	 * <p>Description: Configuration file finder for a deployment</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.script.AbstractDeployedScript.ConfigFinder</code></p>
	 */
	protected class ConfigFinder extends SimpleFileVisitor<Path> {
		Stack<String> directories = new Stack<String>();
		
		ConfigFinder() {
			Collections.addAll(directories, pathSegments);
		}
		
		@Override
		public FileVisitResult preVisitDirectory(final Path dir, final BasicFileAttributes attrs) throws IOException {
			//dir.
			return super.preVisitDirectory(dir, attrs);
		}
		
		
	}
	
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
		/* No Op */
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
