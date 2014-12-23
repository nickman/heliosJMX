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
package com.heliosapm.jmx.alarm;

/**
 * <p>Title: AlarmType</p>
 * <p>Description: Enumerates the supported alarm window types</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.jmx.alarm.AlarmType</code></p>
 */

public enum AlarmType {
	VALUE,
	VALUES,
	TIME,
	EXP;
}

/*
 * Params: window size, threshold, duration
 * Trip:  (spin out async event with metric name and sample history)
 * 		X number of consecutive samples over threshold
 * 		N out last X consecutive samples over threshold
 * 		Elapsed time with value over threshold
 * 		Exponentially Weighted value over threshold
 * Alarm State:  Open / Closed / Paused / NoData
 * Pause on sample end (e.g. GCEnd)
 * Clear and resume on sample start (e.g. GCStart)
 * Config alarm windows from file
 * 
 * 
 * Alarms
======

BasicValueThreshold:
====================
	Retains last n samples that are >|< warn threshold
	When a sample is processed:
		OK:
			if >|< warn threshold, append
				if accumulated samples = n, switch to WARN|CRITICAL
			else clear samples
		WARN
			append, discard oldest
			when n consecutive values are OK, switch to OK
			when n consecutive values are CRITICAL, switch to CRITICAL
		CRITICAL
			append, discard oldest
			when n consecutive values are OK, switch to OK
			when n consecutive values are WARN, switch to WARN


 * 
 * 
 */
