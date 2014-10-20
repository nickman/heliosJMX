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
package ch.ethz.ssh2.channel;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

/**
 * <p>Title: InstrumentedStreamForwarder</p>
 * <p>Description: </p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>org.helios.jmx.remote.tunnel.instrument.InstrumentedStreamForwarder</code></p>
 */

public class InstrumentedStreamForwarder extends Thread {

	OutputStream os;
	InputStream is;
	byte[] buffer = new byte[Channel.CHANNEL_BUFFER_SIZE];
	Channel c;
	StreamForwarder sibling;
	Socket s;
	String mode;

	InstrumentedStreamForwarder(Channel c, StreamForwarder sibling, Socket s, InputStream is, OutputStream os, String mode)
			throws IOException
	{
		this.is = is;
		this.os = os;
		this.mode = mode;
		this.c = c;
		this.sibling = sibling;
		this.s = s;
	}

	@Override
	public void run()
	{
		try
		{
			while (true)
			{
				int len = is.read(buffer);
				if (len <= 0)
					break;
				os.write(buffer, 0, len);
				os.flush();
			}
		}
		catch (IOException ignore)
		{
			try
			{
				c.cm.closeChannel(c, "Closed due to exception in StreamForwarder (" + mode + "): "
						+ ignore.getMessage(), true);
			}
			catch (IOException ignored)
			{
			}
		}
		finally
		{
			try
			{
				os.close();
			}
			catch (IOException ignored)
			{
			}
			try
			{
				is.close();
			}
			catch (IOException ignored)
			{
			}

			if (sibling != null)
			{
				while (sibling.isAlive())
				{
					try
					{
						sibling.join();
					}
					catch (InterruptedException ignored)
					{
					}
				}

				try
				{
					c.cm.closeChannel(c, "StreamForwarder (" + mode + ") is cleaning up the connection", true);
				}
				catch (IOException ignored)
				{
				}

				try
				{
					if (s != null)
						s.close();
				}
				catch (IOException ignored)
				{
				}
			}
		}
	}

}
