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
package com.heliosapm.opentsdb;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.json.JSONObject;

/**
 * <p>Title: AnnotationBuilder</p>
 * <p>Description: </p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.AnnotationBuilder</code></p>
 */

public class AnnotationBuilder {
	/*
	 * startTime: int  (req)
	 * endTime: int
	 * tsuid: String
	 * description: String
	 * notes: String
	 * custom: Map<String, String>
	 * 

Example GET Request

http://localhost:4242/api/annotation?start_time=1369141261&tsuid=000001000001000001

Example POST Request

{
  "startTime":"1369141261",
  "tsuid":"000001000001000001",
  "description": "Testing Annotations",
  "notes": "These would be details about the event, the description is just a summary",
  "custom": {
      "owner": "jdoe",
      "dept": "ops"
  }
}


Example Response

{
    "tsuid": "000001000001000001",
    "description": "Testing Annotations",
    "notes": "These would be details about the event, the description is just a summary",
    "custom": {
        "owner": "jdoe",
        "dept": "ops"
    },
    "endTime": 0,
    "startTime": 1369141261
}

Query 3 - Specific Time Series

What if you want a specific timeseries? You have to include every tag and coresponding value.

m=sum:cpu.system{host=web1,dc=lax}

This will return the data from timeseries #6 only.

[
    {
        "metric": "cpu.system",
        "tags": {
            "dc": "lax",
            "host": "web1"
        },
        "aggregated_tags": [],
        "tsuids": [
            "0102050101"
        ],
        "dps": {
            "1346846400": 15.199999809265137
        }
    }
]


	 * 
	 * 
	 */
	
	/** Unix epoch timestamp, in seconds, marking the time when the annotation event should be recorded */
	protected final int startTime;
	/** An optional Unix epoch timestamp end time for the event if it has completed or been resolved */
	protected int endTime = -1;
	/** A TSUID if the annotation is associated with a timeseries. This may be null or empty if the note was for a global event */
	protected String tsuid = null;
	/** A brief description of the event */
	protected StringBuilder description = null;
	/** Detailed notes about the event */
	protected StringBuilder notes = null;
	/** A key/value map to store custom fields and values */
	protected Map<String, String> custom = new LinkedHashMap<String, String>();
	
	
	/**
	 * Creates a new AnnotationBuilder
	 * @param startTime The annotation start time in seconds (Unix epoch timestamp)
	 */
	public AnnotationBuilder(final int startTime) {
		if(startTime < 1) throw new IllegalArgumentException("Invalid start time:" + startTime);
		this.startTime = startTime;
	}
	
	/**
	 * Creates a new AnnotationBuilder with a start time of current
	 */
	public AnnotationBuilder() {
		this((int)TimeUnit.SECONDS.convert(System.currentTimeMillis(), TimeUnit.MILLISECONDS));
	}

	/**
	 * Sets the annotation end time
	 * @param endTime the endTime to set
	 * @return this AnnotationBuilder
	 */
	public final AnnotationBuilder setEndTime(final int endTime) {
		if(endTime < 1) throw new IllegalArgumentException("Invalid end time:" + endTime);
		this.endTime = endTime;		
		return this;
	}

	/**
	 * Sets the annotation tsduid
	 * @param tsuid the tsuid to set
	 * @return this AnnotationBuilder
	 */
	public final AnnotationBuilder setTSUID(final String tsuid) {
		if(tsuid==null || tsuid.trim().isEmpty()) throw new IllegalArgumentException("The passed TSUID was null or empty");
		this.tsuid = tsuid;
		return this;
	}

	/**
	 * Appends a description fragment to the annotation
	 * @param description the description fragment to append
	 * @return this AnnotationBuilder
	 */
	public final AnnotationBuilder setDescription(final String description) {
		if(description==null || description.trim().isEmpty()) throw new IllegalArgumentException("The passed description fragment was null or empty");
		if(this.description == null) this.description = new StringBuilder();
		else this.description.append("\n");
		this.description.append(description.trim());		
		return this;
	}

	/**
	 * Appends a note to the annotation
	 * @param notes the notes to append to the annotation
	 * @return this AnnotationBuilder
	 */
	public final AnnotationBuilder setNotes(final String notes) {
		if(notes==null || notes.trim().isEmpty()) throw new IllegalArgumentException("The passed note was null or empty");
		if(this.notes==null) this.notes = new StringBuilder();
		else this.notes.append("\n");
		this.notes.append(notes.trim());		
		return this;
	}

	/**
	 * Adds a custom key/value pair
	 * @param key The cutom key
	 * @param value The cutom value
	 * @return this AnnotationBuilder
	 */
	public final AnnotationBuilder setCustom(final String key, final String value) {
		if(key==null || key.trim().isEmpty()) throw new IllegalArgumentException("The passed key was null or empty");
		if(value==null || value.trim().isEmpty()) throw new IllegalArgumentException("The passed value was null or empty");
		this.custom.put(key.trim(), value.trim());		
		return this;
	}
	
	/**
	 * Builds and returns the annotation
	 * @return the built annotation
	 */
	public final TSDBAnnotation build() {
		return new TSDBAnnotation(this);
	}
	
	
	
	/**
	 * <p>Title: TSDBAnnotation</p>
	 * <p>Description: A representation of an OpenTSDB Annotation which will serialize into the correct JSON to be HTTP Posted</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.opentsdb.AnnotationBuilder.TSDBAnnotation</code></p>
	 */
	public static class TSDBAnnotation { 
		/** Unix epoch timestamp, in seconds, marking the time when the annotation event should be recorded */
		protected final int startTime;
		/** An optional Unix epoch timestamp end time for the event if it has completed or been resolved */
		protected final int endTime;
		/** A TSUID if the annotation is associated with a timeseries. This may be null or empty if the note was for a global event */
		protected final String tsuid;
		/** A brief description of the event */
		protected final String description;
		/** Detailed notes about the event */
		protected final String notes;
		/** A key/value map to store custom fields and values */
		protected final Map<String, String> custom;
		
		/**
		 * Creates a new TSDBAnnotation
		 * @param annotationBuilder The anotation builder
		 */
		TSDBAnnotation(final AnnotationBuilder annotationBuilder) {
			startTime = annotationBuilder.startTime;
			endTime = annotationBuilder.endTime;
			tsuid = annotationBuilder.tsuid == null ? null : annotationBuilder.tsuid.trim(); 
			description = annotationBuilder.description == null ? null : annotationBuilder.description.toString().trim();  
			notes = annotationBuilder.notes == null ? null : annotationBuilder.notes.toString().trim();
			custom = annotationBuilder.custom.isEmpty() ? null : new LinkedHashMap<String, String>(annotationBuilder.custom);
		}
		
		/**
		 * Generates the JSON representation for this annotation
		 * @param indent The pretty print indent
		 * @return the JSON representation for this annotation
		 */
		public String toJSON(final int indent) {
			final JSONObject json = new JSONObject();
			json.put("startTime", startTime);
			if(endTime!=-1) json.put("endTime", endTime);
			if(tsuid!=null) json.put("tsuid", tsuid.trim());
			if(description!=null) json.put("description", description.trim());
			if(notes!=null) json.put("notes", notes.trim());
			if(custom!=null && !custom.isEmpty()) {
				final JSONObject cmap = new JSONObject();
				for(Map.Entry<String, String> c: custom.entrySet()) {
					cmap.put(c.getKey().trim(), c.getValue().trim());
				}
				json.put("custom", cmap);
			}
			return json.toString(indent);
		}
		
		/**
		 * Generates the JSON representation for this annotation with a zero indent
		 * @return the JSON representation for this annotation
		 */
		public String toJSON() {
			return toJSON(0);
		}
		

		/**
		 * {@inheritDoc}
		 * @see java.lang.Object#toString()
		 */
		@Override
		public String toString() {
			return toJSON(2);
		}
		
	}

}
