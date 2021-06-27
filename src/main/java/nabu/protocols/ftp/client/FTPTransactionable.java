package nabu.protocols.ftp.client;

import java.io.IOException;

import org.apache.commons.net.ftp.FTPClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import be.nabu.libs.services.api.Transactionable;

public class FTPTransactionable implements Transactionable {

	private Logger logger = LoggerFactory.getLogger(getClass());
	private String id;
	private FTPClient client;
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

	public FTPClient getClient() {
		return client;
	}

	public boolean isClosed() {
		return closed;
	}

}
