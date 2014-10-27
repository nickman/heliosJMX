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
package com.heliosapm.jmx.remote.tunnel;

import java.util.concurrent.atomic.AtomicReference;

/**
 * <p>Title: TunnelState</p>
 * <p>Description: Enumerates the state of tunnel based message connection</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.jmx.remote.tunnel.TunnelState</code></p>
 */

public enum TunnelState {
    /** Never connected */
    UNCONNECTED(1),
    /** In the process of Connecting */
    CONNECTING(2),
    /** Tunnel is connected */
    CONNECTED(4),
    /** Tunnel failed unexpectedly */
    FAILED(8),
    /** Tunnel was closed */
    TERMINATED(16);

	
	
	private TunnelState(final int mask) {
		this.mask = mask;
	}
	
	/** The bit mask of this state */
	public final int mask;
	
	
	public boolean isEnabled(final int testMask) {
		return (testMask | mask) == testMask;
	}
	
	public static int mask(final TunnelState...states) {
		if(states==null || states.length==0) return 0;
		int _mask = 0;
		for(TunnelState ts: states) {
			if(ts==null) continue;
			_mask = (_mask | ts.mask);
		}
		return _mask;
	}
	
	public static boolean waitForState(final AtomicReference<TunnelState> state, final long waitTimeMs, final TunnelState...states) {		
		final long endTime = System.currentTimeMillis() + waitTimeMs;
		final int acceptMask = mask(states); if(acceptMask<1) throw new IllegalArgumentException("No states specified");
		while(!state.get().isEnabled(acceptMask)) {
			if(System.currentTimeMillis() > endTime) return false;
			try {
				Thread.currentThread().join(5);
			} catch (InterruptedException e) {
				throw new RuntimeException("Thread interrupted while waiting on TunnelState change");				
			}
		}
		return true;
	}
	

}
