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
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import javax.management.ObjectName;

import org.json.JSONObject;

import com.heliosapm.filewatcher.ScriptFileWatcher;
import com.heliosapm.jmx.util.helpers.ConfigurationHelper;
import com.heliosapm.jmx.util.helpers.JMXHelper;
import com.heliosapm.jmx.util.helpers.URLHelper;

/**
 * <p>Title: AbstractDeployedScript</p>
 * <p>Description: Base class for implementing {@link DeployedScript} extensions</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.script.AbstractDeployedScript</code></p>
 * @param <T> The type of the underlying executable script
 */

public abstract class AbstractDeployedScript<T> implements DeployedScript<T> {
	/** The underlying executable component */
	protected WeakReference<T> executable = null;
	/** The originating source file */
	protected final File sourceFile;	
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
	
	/** The status of this deployment */
	protected final AtomicReference<DeploymentStatus> status = new AtomicReference<DeploymentStatus>(DeploymentStatus.INIT);
	/** The last mod time  */
	protected final AtomicLong lastModTime = new AtomicLong(System.currentTimeMillis());
	/** The effective timestamp of the current status  */
	protected final AtomicLong statusTime = new AtomicLong(System.currentTimeMillis());
	
	/** The last execute time  */
	protected final AtomicLong lastExecTime = new AtomicLong(-1L);
	/** The last error time  */
	protected final AtomicLong lastErrorTime = new AtomicLong(-1L);
	/** The execution count since the last reset  */
	protected final AtomicLong execCount = new AtomicLong(0L);
	/** The error count since the last reset  */
	protected final AtomicLong errorCount = new AtomicLong(0L);
	
	/** The deployment's JMX ObjectName */
	protected final ObjectName objectName;

	
	/**
	 * Creates a new AbstractDeployedScript
	 * @param sourceFile The originating source file
	 */
	public AbstractDeployedScript(File sourceFile) {
//		this.executable = new WeakReference<T>(executable);
		this.sourceFile = sourceFile;
		String tmp = URLHelper.getFileExtension(sourceFile);
		if(tmp==null || tmp.trim().isEmpty()) throw new RuntimeException("The source file [" + sourceFile + "] has no extension");
		extension = tmp.toLowerCase();
		rootDir = ScriptFileWatcher.getInstance().getRootDir(sourceFile.getParentFile().getAbsolutePath());
		pathSegments = calcPathSegments();
		objectName = JMXHelper.objectName(new StringBuilder("com.heliosapm.deployments:")	// TODO: deployments vs. templates vs. config  vs. fixtures
			.append("path=").append(join("/", pathSegments)).append(",")
			.append("extension=").append(extension).append(",")
			.append("name=").append(sourceFile.getName())			
		);
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
			statusTime.set(System.currentTimeMillis());
		}
		return priorStatus;
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.DeployedScript#setExecutable(java.lang.Object)
	 */
	@Override
	public void setExecutable(final T executable) {
		if(executable==null) throw new IllegalArgumentException("The passed executable was null");
		this.executable = new WeakReference<T>(executable);
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
		T t = executable.get();
		if(t==null) {
			try { undeploy(); } catch (Exception x) {/* No Op */}
			throw new RuntimeException("Executable has been gc'ed");
		}
		return t;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.DeployedScript#getConfiguration()
	 */
	@Override
	public Map<String, Object> getConfiguration() {
		return new HashMap<String, Object>(config);
	}
	
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
		executable.enqueue();
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
	 * @see com.heliosapm.script.DeployedScriptMXBean#addConfiguration(java.lang.String, java.lang.String)
	 */
	@Override
	public void addConfiguration(final String key, final String value) {
		addConfiguration(key, (Object)value);		
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

}
