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
package com.heliosapm.jmx;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.heliosapm.filewatcher.ScriptFileWatcher;
import com.heliosapm.jmx.server.JMXMPServer;
import com.heliosapm.script.compilers.groovy.GroovyConsole;
import com.heliosapm.script.compilers.groovy.stdio.SystemStreamRedirector;

import io.hawt.embedded.Main;

/**
 * <p>Title: MainStart</p>
 * <p>Description: Server bootstrap</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.jmx.MainStart</code></p>
 */

public class MainStart {
	
	protected static final String HAWT_WAR = "/home/nwhitehead/.m2/repository/io/hawt/hawtio-web/1.5-redhat-047/hawtio-web-1.5-redhat-047.war";
	protected static final String WIN_HAWT_WAR = "c:\\users\\nwhitehe\\.m2\\repository\\io\\hawt\\hawtio-web\\1.4.19\\hawtio-web-1.4.19.war";
	protected static final Logger LOG = LoggerFactory.getLogger(MainStart.class);

	public static final boolean ISWIN = System.getProperty("os.name").toLowerCase().contains("windows");
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		LOG.info("Starting heliosJMX....");
		SystemStreamRedirector.install();
		final Main main = new Main();
		main.setWar(ISWIN ? WIN_HAWT_WAR : HAWT_WAR);
		main.setPort(9090);
		//main.setContextPath("/");
		System.setProperty("hawtio.authenticationEnabled", "false");
		Thread bootThread = new Thread("HeliosJMXBootStrap") {
			public void run() {
				try {
					main.run();
				} catch (Exception ex) {
					LOG.error("Failed to boot hawt", ex);
				}
			}
		};
		JMXMPServer.getInstance();
		GroovyConsole.getInstance();
		bootThread.start();
		ScriptFileWatcher.main(new String[] {});
	}

}
