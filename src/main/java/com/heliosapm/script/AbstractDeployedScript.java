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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import javax.management.ObjectName;

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
	protected T executable = null;
	/** The originating source file */
	protected final File sourceFile;	
	/** The source file extension */
	protected final String extension;
	/** The configuration for this deployment */
	protected final Map<String, Object> config = new ConcurrentHashMap<String, Object>();	
	/** The status of this deployment */
	protected final AtomicReference<DeploymentStatus> status = new AtomicReference<DeploymentStatus>(DeploymentStatus.INIT);
	/** The last mod time  */
	protected final AtomicLong lastModTime = new AtomicLong(System.currentTimeMillis());
	/** The last execute time  */
	protected final AtomicLong lastExecTime = new AtomicLong(-1L);
	/** The last error time  */
	protected final AtomicLong lastErrorTime = new AtomicLong(-1L);
	/** The execution count since the last reset  */
	protected final AtomicLong execCount = new AtomicLong(-1L);
	/** The error count since the last reset  */
	protected final AtomicLong errorCount = new AtomicLong(-1L);

	
	/**
	 * Creates a new AbstractDeployedScript
	 * @param sourceFile The originating source file
	 * @param executable The underlying executable component
	 */
	public AbstractDeployedScript(File sourceFile, final T executable) {
		this.executable = executable;
		this.sourceFile = sourceFile;
		String tmp = URLHelper.getFileExtension(sourceFile);
		if(tmp==null || tmp.trim().isEmpty()) throw new RuntimeException("The source file [" + sourceFile + "] has no extension");
		extension = tmp.toLowerCase();
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
	 * @see com.heliosapm.script.DeployedScript#getExecutable()
	 */
	@Override
	public T getExecutable() {
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
	 * @see com.heliosapm.script.DeployedScript#getSource()
	 */
	@Override
	public byte[] getSource() {
		return URLHelper.getBytesFromURL(URLHelper.toURL(sourceFile));
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
		// TODO Auto-generated method stub
		return null;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.DeployedScript#toJSON()
	 */
	@Override
	public String toJSON() {
		// TODO Auto-generated method stub
		return null;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.DeployedScript#getSchedulePeriod()
	 */
	@Override
	public int getSchedulePeriod() {
		// TODO Auto-generated method stub
		return 0;
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
			newHeader = header;
		} else {
			newHeader = "//";
		}
		
		URLHelper.writeToURL(URLHelper.toURL(sourceFile), (newHeader + "\n" + source).getBytes(), false);

	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.DeployedScript#undeploy()
	 */
	@Override
	public void undeploy() {
		// TODO Auto-generated method stub

	}

}
