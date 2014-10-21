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

import java.lang.management.ManagementFactory;
import java.util.Collection;
import java.util.concurrent.Future;

import javax.management.ObjectName;


/**
 * <p>Title: JMXManagedThreadPoolMBean</p>
 * <p>Description: JMX MBean interface for {@link JMXManagedThreadPool}</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.jmx.concurrency.JMXManagedThreadPoolMBean</code></p>
 */
public interface JMXManagedThreadPoolMBean {
	
    /** The metrics HTML table header definition */
    public static final String METRIC_TABLE_HEADER = "<table border='1'><tr><th>Category</th><th>Average</th><th>Count</th><th>Failed</th><th>Last</th><th>Maximum</th><th>Minimum</th><th>95th Percentile</th><th>Samples</th></tr>";

	/** The number of processors available to this JVM */
	public static final int CORES = ManagementFactory.getOperatingSystemMXBean().getAvailableProcessors();
	
	/** The  Config property name suffix to specify the pool's core pool size */
	public static final String CONFIG_CORE_POOL_SIZE = "-corepoolsize";
	/** The  Config property name suffix to specify the pool's maximum pool size */
	public static final String CONFIG_MAX_POOL_SIZE = "-maxpoolsize";
	/** The  Config property name suffix to specify the pool's maximum work queue size */
	public static final String CONFIG_MAX_QUEUE_SIZE = "-queuesize";
	/** The  Config property name suffix to specify the pool's idle thread keep alive time in ms. */
	public static final String CONFIG_KEEP_ALIVE = "-keepalive";
	/** The  Config property name suffix to specify the pool's metric sliding window size */
	public static final String CONFIG_WINDOW_SIZE = "-windowsize";
	/** The  Config property name suffix to specify the pool's metric default percentile */
	public static final String CONFIG_WINDOW_PERCENTILE = "-windowsize";
	/** The  Config property name suffix to specify the number of core threads to prestart */
	public static final String CONFIG_CORE_PRESTART = "-coreprestart";
	
	
	/** The default  pool's core pool size */
	public static final int DEFAULT_CORE_POOL_SIZE = CORES;
	/** The default  pool's maximum pool size */
	public static final int DEFAULT_MAX_POOL_SIZE = CORES*2;
	/** The default  pool's maximum work queue size */
	public static final int DEFAULT_MAX_QUEUE_SIZE = 10000;
	/** The default  pool's idle thread keep alive time in ms. */
	public static final long DEFAULT_KEEP_ALIVE = 60000;
	/** The default  pool's metric sliding window size */
	public static final int DEFAULT_WINDOW_SIZE = 1000;
	/** The default  pool's metric default percentile */
	public static final int DEFAULT_WINDOW_PERCENTILE = 95;
	/** The default  pool's core thread prestart count */
	public static final int DEFAULT_CORE_PRESTART = 1;
	
	
//	/**
//	 * Resets the thread pool's metrics
//	 */
//	public void reset();	
	
	/**
	 * Returns the MBean's ObjectName 
	 * @return the objectName
	 */
	public ObjectName getObjectName();

	/**
	 * Returns the pool name
	 * @return the poolName
	 */
	public String getPoolName();

	/**
	 * Returns the current depth of the work queue
	 * @return the current depth of the work queue
	 */
	public int getQueueDepth();
	
	/**
	 * Returns the current capacity of the work queue
	 * @return the current capacity of the work queue
	 */
	public int getQueueCapacity();
	

	/**
	 * Returns the cummulative count of uncaught exceptions
	 * @return the uncaughtExceptionCount
	 */
	public long getUncaughtExceptionCount();

	/**
	 * Returns the the cummulative count of rejected task exceptions
	 * @return the rejectedExecutionCount
	 */
	public long getRejectedExecutionCount();

//	/**
//	 * Returns a map of the metrics in the metrics tracker, keyed by the metric name 
//	 * @return the metric map
//	 */
//	public Map<String, Long> getMetrics();
//	
//	/**
//	 * Returns an HTML table of the thread pool metrics
//	 * @return an HTML table of the thread pool metrics
//	 */
//	public String getMetricsTable();
	
	/**
	 * Returns this instance
	 * @return this instance
	 */
	public JMXManagedThreadPool getInstance();
	
	/**
	 * Returns the approximate number of threads that are actively executing tasks.
	 * @return the approximate number of threads that are actively executing tasks
	 */
	public int getActiveCount();
	
	/**
	 * Returns the approximate total number of tasks that have completed execution.
	 * @return the approximate total number of tasks that have completed execution
	 */
	public long getCompletedTaskCount();
	
	/**
	 * Returns the approximate total number of tasks that have ever been scheduled for execution.
	 * @return the approximate total number of tasks that have ever been scheduled for execution
	 */
	public long getTaskCount();
	
	/**
	 * Returns the approximate number of currently executing tasks
	 * @return the approximate number of currently executing tasks
	 */
	public long getExecutingTaskCount();
	
	
	/**
	 * Returns the pool's core size
	 * @return the pool's core size
	 */
	public int getCorePoolSize();
	
	/**
	 * Sets the core number of threads. 
	 * This overrides any value set in the constructor. 
	 * If the new value is smaller than the current value, excess existing threads will be terminated when they next become idle. 
	 * If larger, new threads will, if needed, be started to execute any queued tasks. 
	 * @param corePoolSize the pool's new core size
	 */
	public void setCorePoolSize(int corePoolSize);
	
	/**
	 * Sets the maximum allowed number of threads. 
	 * This overrides any value set in the constructor. 
	 * If the new value is smaller than the current value, excess existing threads will be terminated when they next become idle. 
	 * @param maxPoolSize the pool's new core size
	 */
	public void setMaximumPoolSize(int maxPoolSize);
	
	
	
	/**
	 * Returns the pool's maximum size
	 * @return the pool's maximum size
	 */
	public int getMaximumPoolSize();
	
	/**
	 * Returns the keep alive time in ms. for non-core idle threads
	 * @return the keep alive time in ms. for non-core idle threads
	 */
	public long getKeepAliveTime();
	
	/**
	 * Sets the time limit for which threads may remain idle before being terminated. 
	 * If there are more than the core number of threads currently in the pool, 
	 * after waiting this amount of time without processing a task, excess threads will be terminated. 
	 * This overrides any value set in the constructor. 
	 * @param keepAliveTimeMs the time to wait in ms. 
	 * A time value of zero will cause excess threads to terminate immediately after executing tasks.
	 */
	public void setKeepAliveTime(long keepAliveTimeMs);
	
	/**
	 * Returns the largest number of threads that have ever simultaneously been in the pool.
	 * @return the thread count highwater mark
	 */
	public int getLargestPoolSize();

	/**
	 * Returns the current number of threads in the pool.
	 * @return the current number of threads in the pool
	 */
	public int getPoolSize();
	
	
    /**
     * Indicates if the pool is shutdown
     * @return true if the pool is shutdown, false otherwise
     */
    public boolean isShutdown();

    /**
     * Returns true if this executor is in the process of terminating
     * after <tt>shutdown</tt> or <tt>shutdownNow</tt> but has not
     * completely terminated.  This method may be useful for
     * debugging. A return of <tt>true</tt> reported a sufficient
     * period after shutdown may indicate that submitted tasks have
     * ignored or suppressed interruption, causing this executor not
     * to properly terminate.
     * @return true if terminating but not yet terminated
     */
    public boolean isTerminating();

    /**
     * Returns true if all tasks have completed following shut down.
     * @return true if all tasks have completed following shut down
     */
    public boolean isTerminated();
	
	/**
	 *  Tries to remove from the work queue all Future tasks that have been cancelled.
	 */
	public void purge();
	
	
	/**
	 * Waits for all the futures in the passed collection to complete.
	 * The check on each individual future has a small timeout, but the calling thread
	 * will keep looping through the incomplete futures until the specified timeout elapsed.
	 * @param futures A collection of futures to wait for
	 * @param timeout The timeout in ms.
	 * @return true if all the futures completed within the timeout specified, false otherwise
	 */
	public boolean waitForCompletion(Collection<Future<?>> futures, long timeout);	


}
