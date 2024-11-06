/*
* Copyright (C) 2021 Alexander Verbruggen
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU Lesser General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public License
* along with this program. If not, see <https://www.gnu.org/licenses/>.
*/

package nabu.protocols.ftp.client;

import java.io.IOException;

import javax.xml.bind.annotation.XmlTransient;

import org.apache.commons.net.ftp.FTPClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import be.nabu.libs.services.api.Transactionable;

public class FTPTransactionable implements Transactionable {

	private Logger logger = LoggerFactory.getLogger(getClass());
	private String id;
	private FTPClient client;
	private String host;
	private int port;
	private boolean closed;

	public FTPTransactionable(String id, FTPClient client) {
		this.id = id;
		this.client = client;
	}
	
	@Override
	public String getId() {
		return id;
	}

	@Override
	public void start() {
		// do nothing
	}

	@Override
	public void commit() {
		try {
			closed = true;
			if (client.isConnected()) {
				try {
					client.logout();
				}
				catch (Exception e) {
					// don't care
					logger.warn("Could not log out from the ftp client", e);
				}
				client.disconnect();
			}
		}
		catch (IOException e) {
			logger.warn("Could not close ftp connection properly", e);
		}
	}

	@Override
	public void rollback() {
		try {
			closed = true;
			if (client.isConnected()) {
				try {
					client.logout();
				}
				catch (Exception e) {
					// don't care
					logger.warn("Could not log out from the ftp client", e);
				}
				client.disconnect();
			}
		}
		catch (IOException e) {
			logger.warn("Could not close ftp connection properly", e);
		}		
	}

	@XmlTransient
	public FTPClient getClient() {
		return client;
	}

	public boolean isClosed() {
		return closed;
	}

	public String getHost() {
		return host;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public void setId(String id) {
		this.id = id;
	}

	public void setClosed(boolean closed) {
		this.closed = closed;
	}

}
