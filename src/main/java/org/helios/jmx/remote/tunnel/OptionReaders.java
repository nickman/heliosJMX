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
package org.helios.jmx.remote.tunnel;

import java.io.CharArrayWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.net.URL;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.Properties;

import com.heliosapm.jmx.util.helpers.ConfigurationHelper;



/**
 * <p>Title: OptionReaders</p>
 * <p>Description: SSHOption reader classes</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>org.helios.jmx.remote.tunnel.OptionReaders</code></p>
 */

public class OptionReaders {
	
	/** Default charset */
	public static final Charset CHARSET = Charset.defaultCharset();
	
	/** Static shareable string reader instance */
	public static final StringReader STRING_READER = new StringReader();
	/** Static shareable Int reader instance */
	public static final IntReader INT_READER = new IntReader();
	/** Static shareable Char Array reader instance */
	public static final CharArrReader CHAR_ARR_READER = new CharArrReader();
	/** Static shareable Boolean reader instance */
	public static final BooleanReader BOOLEAN_READER = new BooleanReader();
	/** Static shareable File reader instance */
	public static final FileReader FILE_READER = new FileReader();
	
	
	/**
	 * Determines if the passed string represents a readable file
	 * @param source The source to test
	 * @return true if the passed string represents a readable file, false otherwise
	 */
	public static boolean isFile(String source) {
		if(source==null || source.trim().isEmpty()) return false;
		return new File(source.trim()).canRead();
	}
	
	/**
	 * Determines if the passed string represents a valid URL
	 * @param source The source to test
	 * @return true if the passed string represents a valid URL, false otherwise
	 */
	@SuppressWarnings("unused")
	public static boolean isURL(String source) {
		if(source==null || source.trim().isEmpty()) return false;
		try {
			new URL(source.trim());
			return true;
		} catch (Exception x) {
			return false;
		}		
	}
	
	/**
	 * Reads properties from a file 
	 * @param source The file name
	 * @return the read properties or null if reading failed
	 */
	public static Properties getPropertiesFromFile(String source) {
		if(!isFile(source)) return null;
		String sourceName = source.trim().toLowerCase();
		File file = new File(source);
		Properties props = null;
		InputStream is = null;
		try {
			is = new FileInputStream(file);
			props = new Properties();
			if(sourceName.endsWith("xml")) {
				props.loadFromXML(is);
			} else {
					props.load(is);
			}
		} catch (Exception ex) {
			/* No Op */
		} finally {
			if(is!=null) try { is.close(); } catch (Exception x) { /* No Op */ }
		}
		return props;
	}
	
	/**
	 * Reads properties from a URL 
	 * @param source The url
	 * @return the read properties or null if reading failed
	 */
	public static Properties getPropertiesFromURL(String source) {
		if(!isFile(source)) return null;
		String sourceName = source.trim().toLowerCase();
		Properties props = null;
		InputStream is = null;
		try {
			URL url = new URL(source.trim()); 
			is = url.openStream();
			props = new Properties();
			if(sourceName.endsWith("xml")) {
				props.loadFromXML(is);
			} else {
				props.load(is);
			}					
		} catch (Exception ex) {
			/* No Op */
		} finally {
			if(is!=null) try { is.close(); } catch (Exception x) { /* No Op */ }
		}
		return props;
	}
	
	/**
	 * Reads a file and returns the content as a char array 
	 * @param source The file name
	 * @return the read char array or null if reading failed
	 */
	public static char[] getCharArrFromFile(String source) {
		if(!isFile(source)) return null;
		RandomAccessFile raf = null;
		FileChannel fc = null;
		try {
			raf = new RandomAccessFile(source.trim(), "r");
			fc = raf.getChannel();
			MappedByteBuffer mbb = fc.map(MapMode.READ_ONLY, 0, fc.size()).load();			
			return CHARSET.decode(mbb).toString().toCharArray();
		} catch (Exception x) {
			return null;
		} finally {
			if(raf!=null) try { raf.close(); } catch (Exception x) {/* No Op */}
			if(fc!=null) try { fc.close(); } catch (Exception x) {/* No Op */}
		}
	}
	
	/**
	 * Reads the content provided by a URL and returns the content as a char array 
	 * @param source The url
	 * @return the read char array or null if reading failed
	 */
	public static char[] getCharArrFromURL(String source) {
		if(!isURL(source)) return null;
		InputStream is = null;
		InputStreamReader isr = null;
		CharArrayWriter caw = new CharArrayWriter(1024);
		try {
			is = new URL(source.trim()).openStream();
			isr = new InputStreamReader(is, CHARSET);
			char[] buff = new char[1024];
			int charsRead = -1;
			while((charsRead = isr.read(buff))!=-1) {
				caw.write(buff, 0, charsRead);
			}
			caw.flush();
			buff = null;
			return caw.toCharArray();
		} catch (Exception x) {
			return null;
		} finally {
			if(isr!=null) try { isr.close(); } catch (Exception x) {/* No Op */}
			if(is!=null) try { is.close(); } catch (Exception x) {/* No Op */}
			if(caw!=null) try { caw.close(); } catch (Exception x) {/* No Op */}
		}
	}
	
	
		
	
	
	/**
	 * Reads properties in from the passed source
	 * @param source The source name which may be a file or a URL
	 * @return the read properties or null if not found or failed to read
	 */
	public static Properties getProperties(String source) {
		if(isFile(source)) return getPropertiesFromFile(source);
		if(isURL(source)) return getPropertiesFromURL(source);
		return null;
	}
	
	/**
	 * Reads the passed source as a char array
	 * @param source The source name which may be a file or a URL
	 * @return the read content as a char array or null if not found or failed to read
	 */
	public static char[] getCharArr(String source) {
		if(isFile(source)) return getCharArrFromFile(source);
		if(isURL(source)) return getCharArrFromURL(source);
		return null;
	}
	
	
	
	/**
	 * <p>Title: StringReader</p>
	 * <p>Description: String type SSH option reader</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>org.helios.jmx.remote.tunnel.OptionReades.StringReader</code></p>
	 */
	public static class StringReader implements ISSHOptionReader<String> {
		/**
		 * {@inheritDoc}
		 * @see org.helios.jmx.remote.tunnel.ISSHOptionReader#getOption(java.util.Map, java.lang.String, java.lang.Object)
		 */
		@Override
		public String getOption(Map env, String key, String defaultValue) {
			if(env==null || key==null || key.trim().isEmpty()) return defaultValue; 
			Object value = env.get(key);
			if(value==null) {
				String prefix = ConfigurationHelper.getSystemThenEnvProperty(SSHOption.PROPSPREF.propertyName, null);
				value = getOption(prefix + "." + key, null);
			}
			return value==null ? defaultValue : value.toString().trim();
		}

		/**
		 * {@inheritDoc}
		 * @see org.helios.jmx.remote.tunnel.ISSHOptionReader#getOption(java.lang.String, java.lang.Object)
		 */
		@Override
		public String getOption(String key, String defaultValue) {
			if(key==null || key.trim().isEmpty()) return defaultValue;
			String val = ConfigurationHelper.getSystemThenEnvProperty(key, null);
			if(val==null) {
				String prefix = ConfigurationHelper.getSystemThenEnvProperty(SSHOption.PROPSPREF.propertyName, null);
				val = ConfigurationHelper.getSystemThenEnvProperty(prefix + "." + key, null);						
			}
			return val==null ? defaultValue : val.trim();
		}
		
		public Object getRawOption(String key, Object defaultValue) {
			return getOption(key, (String)defaultValue);
		}
		
		public String convert(String value, String defaultValue) {
			if(value==null || value.trim().isEmpty()) return defaultValue;
			return value.trim();
		}
	}
	
	/**
	 * <p>Title: IntReader</p>
	 * <p>Description: Integer value option reader</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>org.helios.jmx.remote.tunnel.OptionReaders.IntReader</code></p>
	 */
	public static class IntReader implements ISSHOptionReader<Integer> {

		/**
		 * {@inheritDoc}
		 * @see org.helios.jmx.remote.tunnel.ISSHOptionReader#getOption(java.util.Map, java.lang.String, java.lang.Object)
		 */
		@Override
		public Integer getOption(Map env, String key, Integer defaultValue) {
			if(env==null || key==null || key.trim().isEmpty()) return defaultValue;
			try {
				String val = STRING_READER.getOption(env, key, null);
				if(val!=null) return Integer.parseInt(val);
			} catch (Exception x) { /* No Op */ }
			return defaultValue;
		}

		/**
		 * {@inheritDoc}
		 * @see org.helios.jmx.remote.tunnel.ISSHOptionReader#getOption(java.lang.String, java.lang.Object)
		 */
		@Override
		public Integer getOption(String key, Integer defaultValue) {
			if(key==null || key.trim().isEmpty()) return defaultValue;
			String val = STRING_READER.getOption(key, null);
			try {
				if(val!=null) return Integer.parseInt(val);
			} catch (Exception x) { /* No Op */ }
			return defaultValue;
		}
		
		public Object getRawOption(String key, Object defaultValue) {
			return getOption(key, (Integer)defaultValue);
		}
		
		public Integer convert(String value, Integer defaultValue) {
			if(value==null || value.trim().isEmpty()) return defaultValue;
			try {
				return Integer.parseInt(value.trim());
			} catch (Exception ex) {
				return defaultValue;
			}
		}
		
		
		
	}
	
	/**
	 * <p>Title: CharArrReader</p>
	 * <p>Description: An option reader for char arrays</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>org.helios.jmx.remote.tunnel.OptionReaders.CharArrReader</code></p>
	 */
	public static class CharArrReader implements ISSHOptionReader<char[]> {

		/**
		 * {@inheritDoc}
		 * @see org.helios.jmx.remote.tunnel.ISSHOptionReader#getOption(java.util.Map, java.lang.String, java.lang.Object)
		 */
		@Override
		public char[] getOption(Map env, String key, char[] defaultValue) {
			if(env==null || key==null || key.trim().isEmpty()) return defaultValue;
			return defaultValue;
		}

		/**
		 * {@inheritDoc}
		 * @see org.helios.jmx.remote.tunnel.ISSHOptionReader#getOption(java.lang.String, java.lang.Object)
		 */
		@Override
		public char[] getOption(String key, char[] defaultValue) {
			if(key==null || key.trim().isEmpty()) return defaultValue;
			char[] chars = getCharArr(key);
			if(chars!=null) return chars;
			return charThis(STRING_READER.getOption(key, null));
		}
		
		public Object getRawOption(String key, Object defaultValue) {
			return getOption(key, (char[])defaultValue);
		}
		
		
		/**
		 * Extracts the char array from the passed value
		 * @param val The string value to get chars from
		 * @return the char array or null if not resolved
		 */
		protected char[] charThis(String val) {
			char[] chars = null;
			if(val!=null) {
				chars = getCharArr(val);
				if(chars!=null) return chars;
				return val.trim().toCharArray();
			}
			return null;			
		}
		
		public char[] convert(String value, char[] defaultValue) {
			if(value==null || value.trim().isEmpty()) return defaultValue;
			char[] chars = OptionReaders.getCharArr(value);
			if(chars==null) return defaultValue;
			return chars;
		}
		
	}
	
	/**
	 * <p>Title: BooleanReader</p>
	 * <p>Description: Boolean type SSH option reader</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>org.helios.jmx.remote.tunnel.OptionReaders.BooleanReader</code></p>
	 */
	public static class BooleanReader implements ISSHOptionReader<Boolean> {

		/**
		 * {@inheritDoc}
		 * @see org.helios.jmx.remote.tunnel.ISSHOptionReader#getOption(java.util.Map, java.lang.String, java.lang.Object)
		 */
		@Override
		public Boolean getOption(Map env, String key, Boolean defaultValue) {
			if(env==null || key==null || key.trim().isEmpty()) return defaultValue; 
			Object value = env.get(key);
			if(value==null) {
				String prefix = ConfigurationHelper.getSystemThenEnvProperty(SSHOption.PROPSPREF.propertyName, null);
				value = getOption(prefix + "." + key, null);
			}
			
			return value==null ? defaultValue : decode(value.toString());
		}
		
		public Object getRawOption(String key, Object defaultValue) {
			return getOption(key, (Boolean)defaultValue);
		}
		
		public Boolean convert(String value, Boolean defaultValue) {
			if(value==null || value.trim().isEmpty()) return defaultValue;
			String v = value.trim().toLowerCase();
			if("true".equals(v) || "yes".equals(v) || "y".equals(v)) return true;
			if("false".equals(v) || "no".equals(v) || "n".equals(v)) return false;
			return defaultValue;
		}
		
		private Boolean decode(String value) {
			String v = value.trim().toLowerCase();
			return "true".equals(v) || "yes".equals(v) || "y".equals(v); 
		}


		/**
		 * {@inheritDoc}
		 * @see org.helios.jmx.remote.tunnel.ISSHOptionReader#getOption(java.lang.String, java.lang.Object)
		 */
		@Override
		public Boolean getOption(String key, Boolean defaultValue) {
			if(key==null || key.trim().isEmpty()) return defaultValue;
			String val = ConfigurationHelper.getSystemThenEnvProperty(key, null);
			if(val==null) {
				String prefix = ConfigurationHelper.getSystemThenEnvProperty(SSHOption.PROPSPREF.propertyName, null);
				val = ConfigurationHelper.getSystemThenEnvProperty(prefix + "." + key, null);						
			}
			return val==null ? defaultValue : decode(val.toString());
		}
	}
	
	/**
	 * <p>Title: FileReader</p>
	 * <p>Description: File type SSH option reader</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>org.helios.jmx.remote.tunnel.OptionReaders.FileReader</code></p>
	 */
	public static class FileReader implements ISSHOptionReader<File> {
		/**
		 * {@inheritDoc}
		 * @see org.helios.jmx.remote.tunnel.ISSHOptionReader#getOption(java.util.Map, java.lang.String, java.lang.Object)
		 */
		@Override
		public File getOption(Map env, String key, File defaultValue) {
			if(env==null || key==null || key.trim().isEmpty()) return defaultValue; 
			Object value = env.get(key);
			if(value==null) {
				String prefix = ConfigurationHelper.getSystemThenEnvProperty(SSHOption.PROPSPREF.propertyName, null);
				value = getOption(prefix + "." + key, null);
			}
			if(value==null) return defaultValue;
			File f = new File(value.toString().trim());
			return f.canRead() ? f : defaultValue;
		}

		/**
		 * {@inheritDoc}
		 * @see org.helios.jmx.remote.tunnel.ISSHOptionReader#getOption(java.lang.String, java.lang.Object)
		 */
		@Override
		public File getOption(String key, File defaultValue) {
			if(key==null || key.trim().isEmpty()) return defaultValue;
			String val = ConfigurationHelper.getSystemThenEnvProperty(key, null);
			if(val==null) {
				String prefix = ConfigurationHelper.getSystemThenEnvProperty(SSHOption.PROPSPREF.propertyName, null);
				val = ConfigurationHelper.getSystemThenEnvProperty(prefix + "." + key, null);						
			}
			if(val==null) return defaultValue;
			File f = new File(val.toString().trim());
			return f.canRead() ? f : defaultValue;
		}
		
		public Object getRawOption(String key, Object defaultValue) {
			return getOption(key, (File)defaultValue);
		}
		
		public File convert(String value, File defaultValue) {
			if(OptionReaders.isFile(value)) return new File(value.trim());
			return defaultValue;
		}
		
	}
	
}
