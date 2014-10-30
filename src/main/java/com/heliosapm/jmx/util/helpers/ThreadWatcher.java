/**
 * Helios, OpenSource Monitoring
 * Brought to you by the Helios Development Group
 *
 * Copyright 2007, Helios Development Group and individual contributors
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
package com.heliosapm.jmx.util.helpers;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import com.heliosapm.SimpleLogger;
import com.heliosapm.SimpleLogger.SLogger;
import com.heliosapm.jmx.concurrency.JMXManagedScheduler;
import com.heliosapm.jmx.concurrency.JMXManagedThreadPool;

/**
 * <p>Title: ThreadWatcher</p>
 * <p>Description: Watches a thread while it performs some activity and if it does not complete and clear the watcher
 * within a specified period of time, some action will be taken, such as an interrupt.</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.jmx.util.helpers.ThreadWatcher</code></p>
 */

public class ThreadWatcher implements ThreadWatcherMBean {
	/** The singleton instance */
	private static volatile ThreadWatcher instance = null;
	/** The singleton instance ctor lock */
	private static final Object lock = new Object();
	
	
	/** The thread watch scheduler */
	private final JMXManagedScheduler scheduler;
	/** Worker thread pool */
	private final JMXManagedThreadPool workerPool; 
	/** Instance logger */
	private final SLogger log = SimpleLogger.logger(getClass());
	
	/** A shareable static instance of the default timeout action */
	public static final DefaultThreadTimeoutAction DEFAULT_ACTION = new DefaultThreadTimeoutAction();
	
	
	/**
	 * Acquires the ThreadWatcher singleton instance
	 * @return the ThreadWatcher singleton instance
	 */
	public static ThreadWatcher getInstance() {
		if(instance==null) {
			synchronized(lock) {
				if(instance==null) {
					instance = new ThreadWatcher();
				}
			}
		}
		return instance;
	}
	
	

	/**
	 * Creates a new ThreadWatcher
	 */
	private ThreadWatcher() {
		scheduler = new JMXManagedScheduler(SCHEDULER_OBJECT_NAME, "ThreadWatcherScheduler", 2, true);
		workerPool = new JMXManagedThreadPool(WORKER_OBJECT_NAME, "ThreadWatcherWorker", true);
		JMXHelper.registerMBean(this, OBJECT_NAME);
	}
	
	
	/**
	 * Schedules a timeout action on the passed thread.
	 * If the returned Closeable is not closed by the time the timeout expires, 
	 * the timeout action will be fired.
	 * @param thread The thread to watch
	 * @param timeout The timeout period
	 * @param unit The timeout unit. Defaults to {@link TimeUnit#MILLISECONDS} if null.
	 * @param timeoutAction The timeout action to execute if the watched thread goes AWOL. 
	 * Defaults to {@link DefaultThreadTimeoutAction} if null.
	 * @return The Closeable which will cancel the timeout callback when closed.
	 */
	public Closeable watch(final Thread thread, final long timeout, final TimeUnit unit, final ThreadTimeoutAction timeoutAction) {
		if(thread==null) throw new IllegalArgumentException("Passed thread was null");
		final ThreadTimeoutAction _timeoutAction = timeoutAction != null ? timeoutAction : new DefaultThreadTimeoutAction();
		final TimeUnit _unit = unit != null ? unit : TimeUnit.MILLISECONDS;
		final ScheduledFuture<?> handle = scheduler.schedule(new Runnable() {
			public void run() {
				workerPool.execute(new Runnable() {
					public void run() {
						_timeoutAction.onTimeout(thread, timeout, _unit);
					}
				});				
			}
		}, timeout, _unit);
		return new WatcherHandle(handle);
	}
	
	/**
	 * Schedules a timeout action on the calling thread.
	 * If the returned Closeable is not closed by the time the timeout expires, 
	 * the timeout action will be fired.
	 * @param timeout The timeout period
	 * @param unit The timeout unit. Defaults to {@link TimeUnit#MILLISECONDS} if null.
	 * @param timeoutAction The timeout action to execute if the watched thread goes AWOL. 
	 * Defaults to {@link DefaultThreadTimeoutAction} if null.
	 * @return The Closeable which will cancel the timeout callback when closed.
	 */
	public Closeable watch(final long timeout, final TimeUnit unit, final ThreadTimeoutAction timeoutAction) {
		return watch(Thread.currentThread(), timeout,  unit, timeoutAction);
	}
	
	/**
	 * Schedules a default timeout action on the passed thread.
	 * If the returned Closeable is not closed by the time the timeout expires, 
	 * the timeout action will be fired.
	 * @param thread The thread to watch
	 * @param timeout The timeout period
	 * @param unit The timeout unit. Defaults to {@link TimeUnit#MILLISECONDS} if null.
	 * Defaults to {@link DefaultThreadTimeoutAction} if null.
	 * @return The Closeable which will cancel the timeout callback when closed.
	 */
	public Closeable watch(final Thread thread, final long timeout, final TimeUnit unit) {
		return watch(thread, timeout,  unit, DEFAULT_ACTION);
	}
	
	
	/**
	 * Schedules the default timeout action on the calling thread.
	 * If the returned Closeable is not closed by the time the timeout expires, 
	 * the timeout action will be fired.
	 * @param timeout The timeout period
	 * @param unit The timeout unit. Defaults to {@link TimeUnit#MILLISECONDS} if null. 
	 * Defaults to {@link DefaultThreadTimeoutAction} if null.
	 * @return The Closeable which will cancel the timeout callback when closed.
	 */
	public Closeable watch(final long timeout, final TimeUnit unit) {
		return watch(Thread.currentThread(), timeout,  unit, DEFAULT_ACTION);
	}
	
	
	/**
	 * <p>Title: ThreadTimeoutAction</p>
	 * <p>Description: Defines the callback executed when a thread watcher times out.</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.jmx.util.helpers.ThreadWatcher.ThreadTimeoutAction</code></p>
	 */
	public interface ThreadTimeoutAction {
		/**
		 * Callback fired when a thread watch times out
		 * @param watchedThread The watched thread
		 * @param timeout The timeout period that expired
		 * @param unit The unit of the timeout
		 */
		public void onTimeout(Thread watchedThread, long timeout, TimeUnit unit);
	}

	/**
	 * <p>Title: DefaultThreadTimeoutAction</p>
	 * <p>Description: The default timeout action which interrupts the AWOL thread</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.jmx.util.helpers.ThreadWatcher</code></p>
	 */
	public static class DefaultThreadTimeoutAction implements ThreadTimeoutAction {
		@Override
		public void onTimeout(final Thread watchedThread, final long timeout, final TimeUnit unit) {
			if(watchedThread!=null) watchedThread.interrupt();			
		}
	}
	
	/**
	 * <p>Title: WatcherHandle</p>
	 * <p>Description: A handle to the watcher that allows the watched thread to cancel the watch</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.jmx.util.helpers.ThreadWatcher.WatcherHandle</code></p>
	 */
	public class WatcherHandle implements Closeable {
		/** The internal scheduler handle */
		private final ScheduledFuture<?> scheduleHandle;
		
		/**
		 * Creates a new WatcherHandle
		 * @param scheduleHandle The internal scheduler handle
		 */
		private WatcherHandle(final ScheduledFuture<?> scheduleHandle) {
			this.scheduleHandle = scheduleHandle;
		}
		
		/**
		 * <p>Cancels the thread watch</p>
		 * {@inheritDoc}
		 * @see java.io.Closeable#close()
		 */
		@Override
		public void close() throws IOException {
			scheduleHandle.cancel(true);			
		}
	}
}
