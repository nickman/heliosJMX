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
package com.heliosapm.script.annotations.processors;



import groovy.lang.Script;

import java.util.EnumMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.heliosapm.jmx.remote.tunnel.SSHOption;
import com.heliosapm.jmx.remote.tunnel.SSHTunnelConnector;
import com.heliosapm.jmx.remote.tunnel.TunnelRepository;
import com.heliosapm.script.annotations.SSHOptionValue;
import com.heliosapm.script.annotations.SSHShell;
import com.heliosapm.ssh.terminal.CommandTerminal;

/**
 * <p>Title: GroovyInjectionAnnotationProcessor</p>
 * <p>Description: SSHShell annotation processor for Groovy scripts</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.script.annotations.processors.GroovySSHAnnotationProcessor</code></p>
 */

public class GroovySSHAnnotationProcessor implements AnnotationProcessor<SSHShell, Script> {
	/** Instance logger */
	final Logger log = LoggerFactory.getLogger(getClass());
	
	@Override
	public void process(final Script executable) {
		if(executable==null) throw new IllegalArgumentException("The passed executable was null");
		final SSHShell annotation = executable.getClass().getAnnotation(SSHShell.class);
		if(annotation==null) return;
		
		log.info("Applying annotation [{}] to script [{}]", annotation.getClass().getName(), executable.getClass().getName());
		final SSHOptionValue[] values = annotation.value();
		final Map<SSHOption, Object> sshOptions = new EnumMap<SSHOption, Object>(SSHOption.class);
		for(SSHOptionValue value: values) {
			SSHOption option = value.value();			
			Object typed = option.optionReader.convert(value.optValue(), null);
			if(typed==null) typed = option.defaultValue;
			if(typed==null) continue;
			sshOptions.put(option, typed);
		}
		final CommandTerminal term = TunnelRepository.getInstance().openCommandTerminal(sshOptions);
		
		
	}
	
	
//	protected void processFixture(final Inject inject) {
//		Fixture<?> fixture;
//		fixture = FixtureCache.getInstance().get(inject.name());
//		if(fixture!=null) {
//			log.info("Injecting Fixture [{}] into [{}]", inject.name(), inject.fieldName());
//			executable.getMetaClass().setAttribute(executable, inject.fieldName(), fixture);
//		}
//	}
	
}
 