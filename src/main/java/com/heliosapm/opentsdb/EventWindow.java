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
package com.heliosapm.opentsdb;

/**
 * <p>Title: EventWindow</p>
 * <p>Description: </p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.EventWindow</code></p>
 */

public class EventWindow {

	/**
	 * Creates a new EventWindow
	 */
	public EventWindow() {
		// TODO Auto-generated constructor stub
	}

}

/*
double last = 0;
long lastTime = 0;
double halfLife = 60 * 1000; // 60 seconds for example.

public static double ewma(double sample, long time) {
    double alpha = Math.exp((lastTime - time) / halfLife);
    lastTime = time;
    return last = sample * alpha + last * (1 - alpha); 
}
or you can approximate this to avoid calling Math.exp with

public static double ewma(double sample, long time) {
    long delay = time - lastTime
    double alpha = delay >= halfLife ? 1.0 : delta / halfLife;
    lastTime = time;
    return last = sample * alpha + last * (1 - alpha); 
}
 */ 
