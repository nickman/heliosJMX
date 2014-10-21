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
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.management.ObjectName;

/**
 * <p>Title: JMXManagedSchedulerMBean</p>
 * <p>Description: JMX MBean interface for {@link JMXManagedScheduler}</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>org.helios.jmx.concurrency.JMXManagedSchedulerMBean</code></p>
 */

public interface JMXManagedSchedulerMBean {
	/** The number of processors available to this JVM */
	public static final int CORES = ManagementFactory.getOperatingSystemMXBean().getAvailableProcessors();
	
	/** The  Config property name suffix to specify the pool's core pool size */
	public static final String CONFIG_CORE_SCHEDULER_POOL_SIZE = "-schedulerpoolsize";
	/** The default  pool's core pool size */
	public static final int DEFAULT_CORE_SCHEDULER_POOL_SIZE = CORES;
	

	/**
	 * Returns the scheduler's JMX ObjectName
	 * @return the JMX ObjectName
	 */
	public ObjectName getObjectName();

	/**
	 * Returns the scheduler's pool name
	 * @return the pool name
	 */
	public String getPoolName();
	
	/**
	 * Returns the count of uncaught exceptions
	 * @return the uncaught exception count
	 */
	public long getUncaughtExceptionCount();

	/**
	 * Returns the count of rejected executions
	 * @return the rejected execution count
	 */
	public long getRejectedExecutionCount();
	
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
	 * Creates and executes a ScheduledFuture that becomes enabled after the given delay.
	 * @param callable the function to execute
	 * @param delay the time from now to delay execution
	 * @param unit the time unit of the delay parameter 
	 * @return a ScheduledFuture that can be used to extract result or cancel
	 */
	public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit);
	
	/**
	 * Creates and executes a one-shot action that becomes enabled after the given delay.
	 * @param runnable the task to execute
	 * @param delay the time from now to delay execution
	 * @param unit the time unit of the delay parameter 
	 * @return a ScheduledFuture representing pending completion of the task and whose get() method will return null upon completion
	 */
	public ScheduledFuture<?> schedule(Runnable runnable, long delay, TimeUnit unit);
	
	/**
	 * Creates and executes a periodic action that becomes enabled first after the given initial delay, and subsequently with the given period; that is executions will commence after initialDelay then initialDelay+period, then initialDelay + 2 * period, and so on.
	 * @param command  the task to execute
	 * @param initialDelay the time to delay first execution
	 * @param period the period between successive executions
	 * @param unit the time unit of the initialDelay and period parameters 
	 * @return a ScheduledFuture representing pending completion of the task, and whose get() method will throw an exception upon cancellation
	 */
	public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit);
	
	/**
	 * Creates and executes a periodic action that becomes enabled first after the given initial delay, and subsequently with the given delay between the termination of one execution and the commencement of the next.
	 * @param command  the task to execute
	 * @param initialDelay the time to delay first execution
	 * @param delay the delay between the termination of one execution and the commencement of the next
	 * @param unit the time unit of the initialDelay and period parameters 
	 * @return a ScheduledFuture representing pending completion of the task, and whose get() method will throw an exception upon cancellation
	 */
	public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit);
	
	
	

}
