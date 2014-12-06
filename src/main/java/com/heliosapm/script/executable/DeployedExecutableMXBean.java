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
package com.heliosapm.script.executable;

import java.util.Map;
import java.util.Set;

import com.heliosapm.script.DeployedScript;
import com.heliosapm.script.DeployedScriptMXBean;

/**
 * <p>Title: DeployedExecutableMXBean</p>
 * <p>Description: JMX MXBean interface for {@link DeployedScript}s</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.script.DeployedScriptMXBean</code></p>
 */

public interface DeployedExecutableMXBean extends DeployedScriptMXBean {
	
	/**
	 * Returns a map of pending dependencies as a map of types keyed by the config key
	 * @return a map of pending dependencies as a map of types keyed by the config key
	 */
	public Map<String, String> getPendingDependencies();
	
	
	/**
	 * Returns the execution timeout for this script in ms.
	 * @return the execution timeout in ms.
	 */
	public long getExecutionTimeout();
	
	/**
	 * Sets the execution timeout for this script
	 * @param timeout the timeout in ms.
	 */
	public void setExecutionTimeout(long timeout);
	
	/**
	 * Returns a set of the names of sub-invocables in the executable
	 * @return a set of the names of sub-invocables in the executable
	 */
	public Set<String> getInvocables();
	
	/**
	 * Returns the status name of the deployment
	 * @return the status name of the deployment
	 */
	public String getStatusName();
	
	/**
	 * Returns the elapsed time of the most recent execution in ms.
	 * @return the elapsed time of the most recent execution in ms.
	 */
	public long getLastExecElapsed();
	
	/**
	 * Returns the timestamp of the last execution
	 * @return the timestamp of the last execution
	 */
	public long getLastExecTime();
	
	/**
	 * Returns the timestamp of the last error
	 * @return the timestamp of the last error
	 */
	public long getLastErrorTime();
	
	/**
	 * Returns the total number of executions since the last reset
	 * @return the total number of executions since the last reset
	 */
	public long getExecutionCount();
	
	/**
	 * Returns the total number of errors since the last reset
	 * @return the total number of errors since the last reset
	 */
	public long getErrorCount();
	
	/**
	 * Executes the underlying executable script and returns the result as a string
	 * @return the execution return value
	 */
	public String executeForString();
	
	/**
	 * Returns the deployment's scheduled execution definition, 
	 * as specified in {@link com.heliosapm.jmx.execution.ExecutionSchedule}
	 * @return the scheduled execution definition,
	 */
	public String getSchedule();
	
	/**
	 * Sets the deployment's scheduled execution period
	 * @param scheduleExpression A schedule expression as specified in {@link com.heliosapm.jmx.execution.ExecutionSchedule}
	 */
	public void setExecutionSchedule(String scheduleExpression);
	
	/**
	 * Stops scheduled executions, but remembers the schedule so it can be resumed
	 */
	public void pauseScheduledExecutions();
	
	/**
	 * Resumes scheduled executions
	 * @return the schedule expression that was activated
	 */
	public String resumeScheduledExecutions();
	
	/**
	 * Pauses the deployment, meaning it will not be invoked by the scheduler
	 */
	public void pause();
	
	/**
	 * Resumes a paused executable
	 */
	public void resume();
	
	
//==============================================================================================	
	
	
//	/**
//	 * Initializes the config
//	 */
//	public void initConfig();
//	
//	
//	/**
//	 * Indicates if the deployment can be executed (manually)
//	 * @return true if the deployment can be executed, false otherwise
//	 */
//	public boolean isExecutable();
//	
//	/**
//	 * Indicates if the deployment can be executed by the scheduler
//	 * @return true if the deployment can be executed by the scheduler, false otherwise
//	 */
//	public boolean isScheduleExecutable();
//	/**
//	 * Determines if this configuration is applicable for the passed deployment 
//	 * @param deployment The absolute name of the deployment source file to test for 
//	 * @return true if this configuration is applicable for the passed deployment, false otherwise
//	 */
//	public boolean isConfigFor(String deployment);
	

}
