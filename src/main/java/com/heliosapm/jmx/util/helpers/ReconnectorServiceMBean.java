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
package com.heliosapm.jmx.util.helpers;

import javax.management.ObjectName;

/**
 * <p>Title: ReconnectorServiceMBean</p>
 * <p>Description: JMX MBean for the {@link ReconnectorService}</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.jmx.util.helpers.ReconnectorServiceMBean</code></p>
 */

public interface ReconnectorServiceMBean {
	/** The ReconnectorService object name */
	public static final ObjectName OBJECT_NAME = JMXHelper.objectName("com.heliosapm.jmx:service=ReconnectorService");
	/** The ReconnectorService Scheduler's object name */
	public static final ObjectName SCHEDULER_OBJECT_NAME = JMXHelper.objectName("com.heliosapm.jmx:service=ReconnectorService,pool=Scheduler");
	/** The ReconnectorService Worker Pool's object name */
	public static final ObjectName WORKER_OBJECT_NAME = JMXHelper.objectName("com.heliosapm.jmx:service=ReconnectorService,pool=Worker");
	
	/** The default reconnect period, in ms. */
	public static final long DEFAULT_PERIOD = 5000;
	
	/** The notification type root */
	public static final String NOTIF_ROOT = "helios.jmx.reconnector";
	/** The notification type for a registered reconnect */
	public static final String NOTIF_REGISTERED = NOTIF_ROOT + "." + "register";
	/** The notification type for a failed reconnect */
	public static final String NOTIF_FAIL = NOTIF_ROOT + "." + "fail";
	/** The notification type for a completed reconnect */
	public static final String NOTIF_COMPLETE = NOTIF_ROOT + "." + "success";
	
	
	/**
	 * Returns the number of pending reconnects
	 * @return the number of pending reconnects
	 */
	public int getPendingReconnects();

	
}
