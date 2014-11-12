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
package com.heliosapm.filewatcher;

import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.WatchEvent.Kind;
import java.util.Date;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

import com.heliosapm.jmx.util.helpers.SystemClock;

/**
 * <p>Title: FileEvent</p>
 * <p>Description: </p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.filewatcher.FileEvent</code></p>
 */

public class FileEvent implements Delayed {
	/** The filename for which a change was noticed */
	protected final String fileName;
	/** The file change type */
	protected final Kind<Path> eventType;
	/** The original event noticed time */
	protected final long eventTimestamp;
	/** The updateable timestamp  */
	protected long timestamp;
	/** The file type (file or directory) */
	protected EventType.FileType fileType = EventType.FileType.UNKNOWN;  
	
	
	/**
	 * Creates a new FileEvent
	 * @param fileName The filename for which a change was noticed
	 * @param eventType The file change type
	 */
	public FileEvent(final String fileName, final Kind<Path> eventType) {
		this.fileName = fileName;
		this.eventType = eventType;
		eventTimestamp = SystemClock.time();
		timestamp = eventTimestamp; 
		if(!isDelete()) {
			fileType = new File(this.fileName).isDirectory() ? EventType.FileType.DIR : EventType.FileType.FILE;
		}
	}
	
	void setFileType(EventType.FileType type) {
		if(!isDelete() || this.fileType != EventType.FileType.UNKNOWN) throw new RuntimeException();
		this.fileType = fileType;
	}
	
	/**
	 * Adds a delay to the updateable timestamp
	 * @param ms the delay to add
	 */
	void addDelay(long ms) {
		timestamp+= ms;
	}
	
	
	/**
	 * {@inheritDoc}
	 * @see java.lang.Comparable#compareTo(java.lang.Object)
	 */
	@Override
	public int compareTo(Delayed otherDelayed) {
		return this.getDelay(TimeUnit.MILLISECONDS) <= otherDelayed.getDelay(TimeUnit.MILLISECONDS) ? -1 : 1; 
	}
	/**
	 * {@inheritDoc}
	 * @see java.util.concurrent.Delayed#getDelay(java.util.concurrent.TimeUnit)
	 */
	@Override
	public long getDelay(TimeUnit unit) {		
		long delay = unit.convert(timestamp-SystemClock.time(), TimeUnit.MILLISECONDS);
		//System.err.println("DELAY FOR [" + this + "]:" + delay + " [" + unit.name().toLowerCase() + "]");
		return delay;
		
	}


	/**
	 * Returns the sorting timestamp
	 * @return the sorting timestamp
	 */
	public long getTimestamp() {
		return timestamp;
	}


	/**
	 * Updates the sorting timestamp
	 * @param timestamp the sorting timestamp to set
	 */
	public void setTimestamp(long timestamp) {
		this.timestamp = timestamp;
	}


	/**
	 * Returns the filename for which a change was noticed 
	 * @return the filename for which a change was noticed 
	 */
	public String getFileName() {
		return fileName;
	}


	/**
	 * Returns the event change type
	 * @return the event change type
	 */
	public Kind<Path> getEventType() {
		return eventType;
	}


	/**
	 * Returns the original time when the file change was noticed
	 * @return the original time when the file change was noticed
	 */
	public long getEventTimestamp() {
		return eventTimestamp;
	}


	/**
	 * {@inheritDoc}
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((eventType == null) ? 0 : eventType.hashCode());
		result = prime * result
				+ ((fileName == null) ? 0 : fileName.hashCode());
		return result;
	}


	/**
	 * {@inheritDoc}
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		FileEvent other = (FileEvent) obj;
		if (eventType == null) {
			if (other.eventType != null) {
				return false;
			}
		} else if (!eventType.equals(other.eventType)) {
			return false;
		}
		if (fileName == null) {
			if (other.fileName != null) {
				return false;
			}
		} else if (!fileName.equals(other.fileName)) {
			return false;
		}
		return true;
	}
	
	/**
	 * Indicates if this evebt represents a deletion
	 * @return true if this evebt represents a deletion
	 */
	public boolean isDelete() {
		return eventType.equals(ENTRY_DELETE);
	}
	
//	public String getNotifType() {
//		if(eventType.equals(ENTRY_DELETE)) {
//			
//		}
//	}
//	
//	public Notification toNotification(final long notificationId) {
//		Notification notif = new Notification();
//		
//		return notif;
//	}
	
	/**
	 * Returns a short toString with the file name and event type
	 * @return a short toString with the file name and event type
	 */
	public String toShortString() {
		return new StringBuilder("fe:").append(fileName).append("[").append(eventType).append("]").toString();
	}


	/**
	 * {@inheritDoc}
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("FileEvent [fileName=");
		builder.append(fileName);
		builder.append(", eventType=");
		builder.append(eventType);
		builder.append(", eventTimestamp=");
		builder.append(new Date(eventTimestamp));
		builder.append(", timestamp=");
		builder.append(new Date(timestamp));
		builder.append("]");
		return builder.toString();
	}
	
}
