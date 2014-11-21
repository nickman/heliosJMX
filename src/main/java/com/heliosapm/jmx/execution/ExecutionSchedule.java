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
package com.heliosapm.jmx.execution;

import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledFuture;
import java.util.regex.Pattern;

import org.cliffc.high_scale_lib.NonBlockingHashMap;

/**
 * <p>Title: ExecutionSchedule</p>
 * <p>Description: </p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.jmx.execution.ExecutionSchedule</code></p>
 */

public class ExecutionSchedule {
	/** The schedule type */
	final ScheduleType scheduleType;
	/** The period if the type is fixed delay or fixed rate */
	final int period;
	/** The cron expression if the  type is cron */
	final String cron;
	
	/** A cache of execution schedule instances keyed by the expression */
	private static final NonBlockingHashMap<String, ExecutionSchedule> instances = new NonBlockingHashMap<String, ExecutionSchedule>();
	
	/** The fixed rate expression matcher */
	public static final Pattern RATE_EXPR = Pattern.compile("r(\\d+)", Pattern.CASE_INSENSITIVE);
	/** The fixed delay expression matcher */
	public static final Pattern DELAY_EXPR = Pattern.compile("d(\\d+)", Pattern.CASE_INSENSITIVE);
	/** The cron expression matcher (with thanks to <a href="http://stackoverflow.com/users/895295/leo">Leo</a> */
	public static final Pattern CRON_EXPR = Pattern.compile("^\\s*($|#|\\w+\\s*=|(\\?|\\*|(?:[0-5]?\\d)(?:(?:-|\\/|\\,)(?:[0-5]?\\d))?(?:,(?:[0-5]?\\d)(?:(?:-|\\/|\\,)(?:[0-5]?\\d))?)*)\\s+(\\?|\\*|(?:[0-5]?\\d)(?:(?:-|\\/|\\,)(?:[0-5]?\\d))?(?:,(?:[0-5]?\\d)(?:(?:-|\\/|\\,)(?:[0-5]?\\d))?)*)\\s+(\\?|\\*|(?:[01]?\\d|2[0-3])(?:(?:-|\\/|\\,)(?:[01]?\\d|2[0-3]))?(?:,(?:[01]?\\d|2[0-3])(?:(?:-|\\/|\\,)(?:[01]?\\d|2[0-3]))?)*)\\s+(\\?|\\*|(?:0?[1-9]|[12]\\d|3[01])(?:(?:-|\\/|\\,)(?:0?[1-9]|[12]\\d|3[01]))?(?:,(?:0?[1-9]|[12]\\d|3[01])(?:(?:-|\\/|\\,)(?:0?[1-9]|[12]\\d|3[01]))?)*)\\s+(\\?|\\*|(?:[1-9]|1[012])(?:(?:-|\\/|\\,)(?:[1-9]|1[012]))?(?:L|W)?(?:,(?:[1-9]|1[012])(?:(?:-|\\/|\\,)(?:[1-9]|1[012]))?(?:L|W)?)*|\\?|\\*|(?:JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC)(?:(?:-)(?:JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC))?(?:,(?:JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC)(?:(?:-)(?:JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC))?)*)\\s+(\\?|\\*|(?:[0-6])(?:(?:-|\\/|\\,|#)(?:[0-6]))?(?:L)?(?:,(?:[0-6])(?:(?:-|\\/|\\,|#)(?:[0-6]))?(?:L)?)*|\\?|\\*|(?:MON|TUE|WED|THU|FRI|SAT|SUN)(?:(?:-)(?:MON|TUE|WED|THU|FRI|SAT|SUN))?(?:,(?:MON|TUE|WED|THU|FRI|SAT|SUN)(?:(?:-)(?:MON|TUE|WED|THU|FRI|SAT|SUN))?)*)(|\\s)+(\\?|\\*|(?:|\\d{4})(?:(?:-|\\/|\\,)(?:|\\d{4}))?(?:,(?:|\\d{4})(?:(?:-|\\/|\\,)(?:|\\d{4}))?)*))$", Pattern.CASE_INSENSITIVE);
	
	/** 
	 * Creates a new ExecutionSchedule from the passed expression where the understood expressions are:<ul>
	 * 	<li>A fixed <b>rate</b> schedule in seconds as <b><code>"r&lt;number of seconds&gt;"</code></b>. e.g. <b><code>r10</code></b> for every 10 seconds.</li>
	 *  <li>A fixed <b>delay</b> schedule in seconds as <b><code>"d&lt;number of seconds&gt;"</code></b>. e.g. <b><code>d10</code></b> for every 10 seconds.</li>
	 *  <li>A cron expression. e.g. <b><code>"0 0 14-6 ? * FRI-MON"</code></b>.</li>
	 * </ul>
	 * <p>An empty (but not null) string implies {@link ScheduleType#NONE}.
	 * @param scheduleExpression The schedule expression
	 * @param defaultToNone If true, defaults the schedule to {@link ScheduleType#NONE} if the schedule expression is invalid.
	 * Otherwise throws an exception.
	 * @return The execution schedule
	 */
	public static ExecutionSchedule getInstance(final String scheduleExpression, final boolean defaultToNone) {
		if(scheduleExpression==null) throw new IllegalArgumentException("The passed Schedule Expression was null");
		final String _scheduleExpression = scheduleExpression.trim();
		ExecutionSchedule es = instances.get(_scheduleExpression);
		if(es==null) {
			synchronized(instances) {
				es = instances.get(_scheduleExpression);
				if(es==null) {
					es = new ExecutionSchedule(scheduleExpression, defaultToNone);
				}
			}
		}
		return es;
	}

	/**
	 * Creates a new ExecutionSchedule from the passed expression where the understood expressions are:<ul>
	 * 	<li>A fixed <b>rate</b> schedule in seconds as <b><code>"r&lt;number of seconds&gt;"</code></b>. e.g. <b><code>r10</code></b> for every 10 seconds.</li>
	 *  <li>A fixed <b>delay</b> schedule in seconds as <b><code>"d&lt;number of seconds&gt;"</code></b>. e.g. <b><code>d10</code></b> for every 10 seconds.</li>
	 *  <li>A cron expression. e.g. <b><code>"0 0 14-6 ? * FRI-MON"</code></b>.</li>
	 * </ul>
	 * <p>An empty (but not null) string implies {@link ScheduleType#NONE}.
	 * @param scheduleExpression The schedule expression
	 */
	private ExecutionSchedule(final String scheduleExpression, final boolean defaultToNone) {
		if(scheduleExpression==null) throw new IllegalArgumentException("The passed Schedule Expression was null");
		
		if(scheduleExpression.isEmpty()) {
			scheduleType = ScheduleType.NONE;
			cron = null;
			period = -1;			
		} else {
			if(RATE_EXPR.matcher(scheduleExpression).matches()) {
				scheduleType = ScheduleType.FIXED_RATE;
				cron = null;
				period = Integer.parseInt(scheduleExpression.substring(1));
			} else if(DELAY_EXPR.matcher(scheduleExpression).matches()) {
				scheduleType = ScheduleType.FIXED_DELAY;
				cron = null;
				period = Integer.parseInt(scheduleExpression.substring(1));
			} else if(CRON_EXPR.matcher(scheduleExpression).matches()) {
				scheduleType = ScheduleType.CRON;
				cron = scheduleExpression;
				period = -1;
			} else {
				if(defaultToNone) {
					scheduleType = ScheduleType.NONE;
					cron = null;
					period = -1;								
				} else {
					throw new IllegalArgumentException("Failed to interpret schedule expression [" + scheduleExpression + "]");
				}
			}
		}
	}
	
	
	/**
	 * {@inheritDoc}
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		switch(scheduleType) {
			case CRON:
				return "cron: " + cron;
			case FIXED_DELAY:
				return "fixed delay:" + period + " sec.";
			case FIXED_RATE:
				return "fixed rate:" + period + " sec.";
			case NONE:
				return "None";
			default:
				return "None";
		}
	}
	

}
