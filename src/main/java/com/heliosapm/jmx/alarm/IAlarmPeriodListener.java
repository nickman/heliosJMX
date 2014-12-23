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
package com.heliosapm.jmx.alarm;

/**
 * <p>Title: IAlarmPeriodListener</p>
 * <p>Description: Defines a listener that is notified of alarm events</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.jmx.alarm.IAlarmPeriodListener</code></p>
 */

public interface IAlarmPeriodListener {
	/**
	 * Callback from the AlarmTimer on a period timer event
	 * @param period The period that fired in seconds
	 */
	public void onPeriodFlush(int period);
	
	/**
	 * Returns the periods that the listener is interested in.
	 * If the returned array is a single value of <b>-1</b>, it will subscribe all periods.
	 * @return the periods to notify this listener on
	 */
	public int[] getPeriods();
	
	/**
	 * Callback from the timer passing the adjusted periods that the timer converted this listeners interest periods
	 * @param adjustedPeriods The periods for which the listener was subscribed 
	 */
	public void setAdjustedPeriods(int[] adjustedPeriods);

}
