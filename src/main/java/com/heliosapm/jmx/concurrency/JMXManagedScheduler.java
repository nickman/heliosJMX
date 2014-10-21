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
package com.heliosapm.jmx.concurrency;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import javax.management.ObjectName;

import com.heliosapm.jmx.util.helpers.ConfigurationHelper;
import com.heliosapm.jmx.util.helpers.JMXHelper;

/**
 * <p>Title: JMXManagedScheduler</p>
 * <p>Description: A JMX managed scheduler</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.jmx.concurrency.JMXManagedScheduler</code></p>
 */

public class JMXManagedScheduler extends ScheduledThreadPoolExecutor implements ThreadFactory, RejectedExecutionHandler, UncaughtExceptionHandler, JMXManagedSchedulerMBean {
	/** The JMX ObjectName for this pool's MBean */
	protected final ObjectName objectName;
	/** The pool name */
	protected final String poolName;
	
	/** The count of uncaught exceptions */
	protected final AtomicLong uncaughtExceptionCount = new AtomicLong(0L);
	/** The count of rejected executions where the task queue was full and a new task could not be accepted */
	protected final AtomicLong rejectedExecutionCount = new AtomicLong(0L);
	/** The thread group that threads created for this pool are created in */
	protected final ThreadGroup threadGroup;
	/** The thread factory thread serial number factory */
	protected final AtomicInteger threadSerial = new AtomicInteger(0);
	/** Threadlocal to hold the start time of a given task */
	protected final ThreadLocal<long[]> taskStartTime = new ThreadLocal<long[]>() {
		@Override
		protected long[] initialValue() {
			return new long[1];
		}
	};
	
	/**
	 * Creates a new JMXManagedScheduler and publishes the JMX interface
	 * @param objectName THe ObjectName of this scheduler's MBean
	 * @param poolName The pool name
	 * @param corePoolSize The core pool size
	 */
	public JMXManagedScheduler(ObjectName objectName, String poolName, int corePoolSize) {
		this(objectName, poolName, corePoolSize, true);
	}


	/**
	 * Creates a new JMXManagedScheduler
	 * @param objectName THe ObjectName of this scheduler's MBean
	 * @param poolName The pool name
	 * @param corePoolSize The core pool size
	 * @param publishJMX If true, publishes the JMX interface
	 */
	public JMXManagedScheduler(ObjectName objectName, String poolName, int corePoolSize, boolean publishJMX) {
		super(corePoolSize);
		this.objectName = objectName;
		this.poolName = poolName;
		this.threadGroup = new ThreadGroup(poolName + "ThreadGroup");
		setThreadFactory(this);
		setRejectedExecutionHandler(this);
		if(publishJMX) {
			try {			
				JMXHelper.getHeliosMBeanServer().registerMBean(this, objectName);
			} catch (Exception ex) {
				throw new RuntimeException("Failed to register Scheduler MBean [" + objectName + "]", ex);
			}
		}
		
	}

	/**
	 * Creates a new JMXManagedScheduler and publishes the JMX interface
	 * @param objectName THe ObjectName of this scheduler's MBean
	 * @param poolName The pool name
	 */
	public JMXManagedScheduler(ObjectName objectName, String poolName) {
		this(objectName, poolName, true);
	}
	
	
	/**
	 * Creates a new JMXManagedScheduler
	 * @param objectName THe ObjectName of this scheduler's MBean
	 * @param poolName The pool name
	 * @param publishJMX If true, publishes the JMX interface
	 */
	public JMXManagedScheduler(ObjectName objectName, String poolName, boolean publishJMX) {
		this(
			objectName, 
			poolName,				
			ConfigurationHelper.getIntSystemThenEnvProperty(poolName.toLowerCase() + CONFIG_CORE_SCHEDULER_POOL_SIZE, DEFAULT_CORE_SCHEDULER_POOL_SIZE),
			publishJMX
		);
	}
	


	
	

	/**
	 * {@inheritDoc}
	 * @see java.lang.Thread.UncaughtExceptionHandler#uncaughtException(java.lang.Thread, java.lang.Throwable)
	 */
	@Override
	public void uncaughtException(Thread t, Throwable e) {
		uncaughtExceptionCount.incrementAndGet();
		e.printStackTrace(System.err);
	}

	/**
	 * {@inheritDoc}
	 * @see java.util.concurrent.RejectedExecutionHandler#rejectedExecution(java.lang.Runnable, java.util.concurrent.ThreadPoolExecutor)
	 */
	@Override
	public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
		rejectedExecutionCount.incrementAndGet();
//		log.log(Level.SEVERE, "Submitted execution task [" + r + "] was rejected due to a full task queue", new Throwable());		
	}

	/**
	 * {@inheritDoc}
	 * @see java.util.concurrent.ThreadFactory#newThread(java.lang.Runnable)
	 */
	@Override
	public Thread newThread(Runnable r) {
		Thread t = new Thread(threadGroup, r, poolName + "Thread#" + threadSerial.incrementAndGet());
		t.setDaemon(true);
		return t;
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.jmx.concurrency.JMXManagedSchedulerMBean#getObjectName()
	 */
	@Override
	public ObjectName getObjectName() {
		return objectName;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.jmx.concurrency.JMXManagedSchedulerMBean#getPoolName()
	 */
	@Override
	public String getPoolName() {
		return poolName;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.jmx.concurrency.JMXManagedSchedulerMBean#getUncaughtExceptionCount()
	 */
	@Override
	public long getUncaughtExceptionCount() {
		return uncaughtExceptionCount.get();
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.jmx.concurrency.JMXManagedSchedulerMBean#getRejectedExecutionCount()
	 */
	@Override
	public long getRejectedExecutionCount() {
		return rejectedExecutionCount.get();
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.jmx.concurrency.JMXManagedSchedulerMBean#getExecutingTaskCount()
	 */
	@Override
	public long getExecutingTaskCount() {
		return getTaskCount()-getCompletedTaskCount();
	}
	

}
