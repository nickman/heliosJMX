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
package com.heliosapm.script;

import java.util.EnumSet;
import java.util.Set;

/**
 * <p>Title: DeploymentStatus</p>
 * <p>Description: Enumerates the possible statuses of a deployment</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.script.DeploymentStatus</code></p>
 */

public enum DeploymentStatus {
	/** Deployed script is initialized */
	INIT(false, false),
	/** Deployed script is ready to be executed, but is not scheduled */
	READY(true, false),	
	/** Deployed script is paused */
	PAUSED(true, false),
	/** Deployed script cannot connect to remote resource */
	DOWN(false, false),
	/** Deployed script is broken */
	BROKEN(false, false),
	/** Deployed script is ready to be executed and is scheduled */
	UP(true, true),
	/** Deployed script is missing one or more dependent config items */
	NOONFIG(false, false);
	
	private DeploymentStatus(boolean canExec, boolean canSchedulerExec) {
		this.canExec = canExec;
		this.canSchedulerExec = canSchedulerExec;
	}
	
	/** Indicates if this status indicates the script can be executed */
	public final boolean canExec;
	/** Indicates if this status indicates the script is scheduled, or can be scheduled */
	public final boolean canSchedulerExec;
	
	
	/**
	 * Returns an enum set of the specified statuses
	 * @param statuses The statuses to include in the set
	 * @return the set
	 */
	public static Set<DeploymentStatus> setOf(final DeploymentStatus...statuses) {
		final Set<DeploymentStatus> set = EnumSet.noneOf(DeploymentStatus.class);
		if(statuses==null || statuses.length==0) return set;
		for(DeploymentStatus ds: statuses) {
			if(ds==null) continue;
			set.add(ds);
		}
		return set;
		
		
	}
}
