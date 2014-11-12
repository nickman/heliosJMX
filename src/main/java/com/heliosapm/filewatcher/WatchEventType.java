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
package com.heliosapm.filewatcher;

import static java.nio.file.StandardWatchEventKinds.*;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.WatchEvent.Kind;

/**
 * <p>Title: WatchEventType</p>
 * <p>Description: Enumerates all the possible file watcher events that can be handled by the watch service</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.filewatcher.WatchEventType</code></p>
 */

public enum WatchEventType {
	/** A new file event */
	FILE_NEW(FileType.FILE, OpType.NEW, ScriptFileWatcherMXBean.NOTIF_FILE_NEW),
	/** A modified file event */
	FILE_MOD(FileType.FILE, OpType.MOD, ScriptFileWatcherMXBean.NOTIF_FILE_MOD),
	/** A deleted file event */
	FILE_DELETE(FileType.FILE, OpType.DELETE, ScriptFileWatcherMXBean.NOTIF_FILE_DELETE),
	/** A new directory event */
	DIR_NEW(FileType.DIR, OpType.NEW, ScriptFileWatcherMXBean.NOTIF_DIR_NEW),
	/** A modified directory event (doesn't mean much, but it can happen) */
	DIR_MOD(FileType.DIR, OpType.MOD, ScriptFileWatcherMXBean.NOTIF_DIR_MOD),
	/** A deleted directory event */
	DIR_DELETE(FileType.DIR, OpType.DELETE, ScriptFileWatcherMXBean.NOTIF_DIR_DELETE),
	/** A delete event where the file type is not yet known */
	UNKNOWN_DELETE(FileType.UNKNOWN, OpType.DELETE, ScriptFileWatcherMXBean.NOTIF_UNKNOWN_DELETE);
	
	private WatchEventType(final FileType fileType, final OpType opType, final String jmxNotif) {
		this.fileType = fileType;
		this.opType = opType;
		this.jmxNotif = jmxNotif;
	}
	
	/** The event file type (dir or file) */
	public final FileType fileType;
	/** The event op type (new, modified, deleted, unknown) */
	public final OpType opType;
	/** The JMX notification type for this event */
	public final String jmxNotif;
	
	/**
	 * Indicates if this event type has an unknown file type
	 * @return true if this event type has an unknown file type, false otherwise
	 */
	public boolean isUnknown() {
		return fileType==FileType.UNKNOWN;
	}
	
	/**
	 * Indicates if the file type is for a regular file (not a dir)
	 * @return true if the file type is for a regular file, false if it is a dir or unknown.
	 */
	public boolean isFile() {
		return fileType==FileType.FILE;
	}
	
	/**
	 * Indicates if the file type is for a directory
	 * @return true if the file type is for a directory, false if it is a regular file or unknown.
	 */
	public boolean isDirectory() {
		return fileType==FileType.DIR;
	}
	
	/**
	 * Indicates if the op type is a deletion
	 * @return true if the op type is a deletion, false otherwise
	 */
	public boolean isDelete() {
		return opType==OpType.DELETE;
	}
	
	/**
	 * Indicates if the op type is a creation
	 * @return true if the op type is a creation, false otherwise
	 */
	public boolean isNew() {
		return opType==OpType.NEW;
	}
	
	/**
	 * Indicates if the op type is a modification
	 * @return true if the op type is a modification, false otherwise
	 */
	public boolean isMod() {
		return opType==OpType.MOD;
	}
	
	
	
	
	/**
	 * Returns the WatchEventType for the passed file name and path event type
	 * @param fileName The name of the file or directory that triggered the event
	 * @param eventType The type of event
	 * @return the corresponding WatchEventType
	 */
	public static WatchEventType assign(final String fileName, final Kind<Path> eventType) {
		if(fileName==null || fileName.trim().isEmpty()) throw new IllegalArgumentException("The passed file name was null or empty");
		if(eventType==null) throw new IllegalArgumentException("The passed event type was null or empty");
		if(eventType.equals(ENTRY_DELETE)) {
			return UNKNOWN_DELETE;
		}
		File f = new File(fileName);
		if(eventType.equals(ENTRY_CREATE)) {
			return f.isDirectory() ? DIR_NEW : FILE_NEW;
		}
		if(eventType.equals(ENTRY_DELETE)) {
			return f.isDirectory() ? DIR_DELETE : FILE_DELETE;
		}
		if(eventType.equals(ENTRY_MODIFY)) {
			return f.isDirectory() ? DIR_MOD : FILE_MOD;
		}
		throw new IllegalArgumentException("Unsupported Event Type [" + eventType.name() + "]");
	}
	
	
	/**
	 * <p>Title: FileType</p>
	 * <p>Description: Enumerates the file types that can fire an event</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.filewatcher.WatchEventType.FileType</code></p>
	 */
	public enum FileType {
		/** A regular file */
		FILE,
		/** A directory */
		DIR,
		/** Unknown file type which occurs when there is a delete event */
		UNKNOWN;
	}
	
	/**
	 * <p>Title: OpType</p>
	 * <p>Description: Enumerates the file operation event types</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.filewatcher.WatchEventType.OpType</code></p>
	 */
	public enum OpType {
		/** A new file or directory event */
		NEW,
		/** A modified file or directory event */
		MOD,
		/** A deleted file or directory event */
		DELETE;
	}
	
}
