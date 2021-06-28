package nabu.protocols.ftp.client;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.jws.WebParam;
import javax.jws.WebResult;
import javax.jws.WebService;
import javax.validation.constraints.NotNull;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPClientConfig;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPFileFilter;
import org.apache.commons.net.ftp.FTPReply;

import be.nabu.libs.services.api.ExecutionContext;
import be.nabu.libs.services.api.Transactionable;
import nabu.protocols.ftp.client.types.FTPConnectionDetails;

@WebService
public class Services {
	
	private ExecutionContext executionContext;
	
	@WebResult(name = "connection")
	// the control defaults to 300s (5 minutes)
	public FTPConnectionDetails connect(@WebParam(name = "transactionId") String transactionId, 
			@WebParam(name = "host") String host,
			@WebParam(name = "port") Integer port,
			@WebParam(name = "username") String username, 
			@WebParam(name = "password") String password) throws SocketException, IOException {
		String key = UUID.randomUUID().toString().replace("-", "");
		
		FTPClient client = new FTPClient();
		FTPClientConfig configuration = new FTPClientConfig();
		// additional options go here! for example the server timezone
		client.configure(configuration);
		FTPTransactionable transactionable = new FTPTransactionable(key, client);
		executionContext.getTransactionContext().push(transactionId, transactionable);

		client.connect(host, port == null ? 21 : port);
		if (!FTPReply.isPositiveCompletion(client.getReplyCode())) {
			client.disconnect();
			throw new IOException("Could not connect to the ftp server: [" + client.getReplyCode() + "] " + client.getReplyString());
		}
		
		client.login(username, password);
		// check out https://commons.apache.org/proper/commons-net/apidocs/org/apache/commons/net/ftp/FTPClient.html
		// but basically this sends a NOOP keep alive on the control channel while the data channel is busy, not all methods support this!
		client.setControlKeepAliveTimeout(300);

		return new FTPConnectionDetails(key, client.isConnected());
	}
	
	private FTPClient retrieve(String id) {
		for (String transactionId : executionContext.getTransactionContext()) {
			Transactionable transactionable = executionContext.getTransactionContext().get(transactionId, id);
			if (transactionable instanceof FTPTransactionable) {
				return ((FTPTransactionable) transactionable).getClient();
			}
		}
		return null;
	}
	
	public void write(@WebParam(name = "connectionId") @NotNull String connectionId, @WebParam(name = "name") String name, @WebParam(name = "stream") InputStream input) throws IOException {
		if (connectionId == null) {
			throw new IllegalArgumentException("You must send a valid ftp connection id");
		}
		FTPClient client = retrieve(connectionId);
		if (client == null) {
			throw new IllegalStateException("No such ftp connection found");
		}
		client.setFileType(FTPClient.BINARY_FILE_TYPE);
		client.setFileTransferMode(FTPClient.STREAM_TRANSFER_MODE);
		client.enterLocalPassiveMode();
		if (!client.storeFile(name, input)) {
			throw new IOException("Could not write file: [" + client.getReplyCode() + "] " + client.getReplyString());
		}
	}
	
	@WebResult(name = "entries")
	public List<FTPFileEntry> list(@WebParam(name = "connectionId") @NotNull String connectionId, @WebParam(name = "path") String path, @WebParam(name = "regex") final String regex) throws IOException {
		if (connectionId == null) {
			throw new IllegalArgumentException("You must send a valid ftp connection id");
		}
		FTPClient client = retrieve(connectionId);
		if (path == null) {
			path = "/";
		}
		FTPFile[] listFiles = client.listFiles(path, new FTPFileFilter() {
			@Override
			public boolean accept(FTPFile arg0) {
				return regex == null || arg0.getName().matches(regex);
			}
		});
		List<FTPFileEntry> entries = new ArrayList<FTPFileEntry>();
		if (listFiles != null) {
			for (FTPFile file : listFiles) {
				FTPFileEntry entry = new FTPFileEntry();
				entry.setPath(path);
				entry.setName(file.getName());
				if (file.getType() == FTPFile.DIRECTORY_TYPE) {
					entry.setFile(false);
				}
				else {
					entry.setFile(true);
					entry.setSize(file.getSize());
					if (file.getTimestamp() != null) {
						entry.setLastModified(file.getTimestamp().getTime());
					}
				}
				entries.add(entry);
			}
		}
		return entries;
	}
}
