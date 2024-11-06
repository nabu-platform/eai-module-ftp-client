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

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.time.Duration;
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

import be.nabu.libs.resources.api.Resource;
import be.nabu.libs.resources.api.ResourceProperties;
import be.nabu.libs.resources.impl.ResourcePropertiesImpl;
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
			@WebParam(name = "password") String password,
			@WebParam(name = "controlEncoding") Charset controlEncoding) throws SocketException, IOException {
		String key = UUID.randomUUID().toString().replace("-", "");
		
		FTPClient client = new FTPClient();
		FTPClientConfig configuration = new FTPClientConfig();
		// additional options go here! for example the server timezone
		client.configure(configuration);
		FTPTransactionable transactionable = new FTPTransactionable(key, client);
		transactionable.setHost(host);
		transactionable.setPort(port == null ? 21 : port);
		executionContext.getTransactionContext().push(transactionId, transactionable);

		if (controlEncoding != null) {
			client.setControlEncoding(controlEncoding.name());
		}
		else {
			client.setAutodetectUTF8(true);
		}
		
		client.connect(host, port == null ? 21 : port);
		if (!FTPReply.isPositiveCompletion(client.getReplyCode())) {
			client.disconnect();
			throw new IOException("Could not connect to the ftp server: [" + client.getReplyCode() + "] " + client.getReplyString());
		}
		
		client.login(username, password);
		// check out https://commons.apache.org/proper/commons-net/apidocs/org/apache/commons/net/ftp/FTPClient.html
		// but basically this sends a NOOP keep alive on the control channel while the data channel is busy, not all methods support this!
		client.setControlKeepAliveTimeout(Duration.ofMillis(300));
//		client.setControlKeepAliveTimeout(300);

		return new FTPConnectionDetails(key, client.isConnected());
	}
	
	private FTPTransactionable retrieve(String id) {
		for (String transactionId : executionContext.getTransactionContext()) {
			Transactionable transactionable = executionContext.getTransactionContext().get(transactionId, id);
			if (transactionable instanceof FTPTransactionable) {
				return (FTPTransactionable) transactionable;
			}
		}
		return null;
	}
	
	private String getPath(URI uri, FTPTransactionable transactionable) {
		if (uri == null) {
			return "/";
		}
		if (uri.getScheme() != null && !"ftp".equalsIgnoreCase(uri.getScheme())) {
			throw new IllegalArgumentException("Only scheme ftp is supported");
		}
		if (uri.getHost() != null && !uri.getHost().equalsIgnoreCase(transactionable.getHost())) {
			throw new IllegalArgumentException("The host of the URI does not match the current connection: " + uri.getHost() + " != " + transactionable.getHost());
		}
		if (uri.getPort() > 0 && uri.getPort() != transactionable.getPort()) {
			throw new IllegalArgumentException("The port of the URI does not match the current connection: " + uri.getPort() + " != " + transactionable.getPort());
		}
		// only check the port if the host is available
		else if (uri.getHost() != null && uri.getPort() < 0 && uri.getPort() != 22) {
			throw new IllegalArgumentException("The port of the URI does not match the current connection: 22 != " + transactionable.getPort());
		}
		return uri.getPath() == null ? "/" : uri.getPath();
	}
	
	public void write(@WebParam(name = "connectionId") @NotNull String connectionId, @WebParam(name = "uri") URI uri, @WebParam(name = "stream") InputStream input) throws IOException {
		if (connectionId == null) {
			throw new IllegalArgumentException("You must send a valid ftp connection id");
		}
		FTPTransactionable transactionable = retrieve(connectionId);
		if (transactionable == null) {
			throw new IllegalStateException("No such ftp connection found");
		}
		FTPClient client = transactionable.getClient();
		client.setFileType(FTPClient.BINARY_FILE_TYPE);
		client.setFileTransferMode(FTPClient.STREAM_TRANSFER_MODE);
		client.enterLocalPassiveMode();
		if (!client.storeFile(getPath(uri, transactionable), input)) {
			throw new IOException("Could not write file: [" + client.getReplyCode() + "] " + client.getReplyString());
		}
	}
	
	private ResourceProperties toProperties(String path, FTPFile file, FTPTransactionable transactionable) {
		// replace double slashes
		path = path.replaceAll("[/]{2,}", "/");
		ResourcePropertiesImpl properties = new ResourcePropertiesImpl();
		try {
			if (!path.startsWith("/")) {
				path = "/" + path;
			}
			properties.setUri(new URI("ftp", null, transactionable.getHost(), transactionable.getPort(), path, null, null));
		}
		catch (URISyntaxException e) {
			throw new RuntimeException(e);
		}
		properties.setName(path.replaceAll("^.*?/([^/]+)$", "$1"));
		if (file.isDirectory()) {
			properties.setContentType(Resource.CONTENT_TYPE_DIRECTORY);
		}
		else {
			properties.setContentType(URLConnection.guessContentTypeFromName(properties.getName()));
			properties.setSize(file.getSize());
		}
		properties.setLastModified(file.getTimestamp().getTime());
		return properties;
	}
	
	@WebResult(name = "entries")
	public List<ResourceProperties> list(@WebParam(name = "connectionId") @NotNull String connectionId, @WebParam(name = "uri") URI uri, @WebParam(name = "regex") final String regex) throws IOException {
		if (connectionId == null) {
			throw new IllegalArgumentException("You must send a valid ftp connection id");
		}
		FTPTransactionable transactionable = retrieve(connectionId);
		if (transactionable == null) {
			throw new IllegalStateException("No such ftp connection found");
		}
		FTPClient client = transactionable.getClient();
		String path = getPath(uri, transactionable);
		FTPFile[] listFiles = client.listFiles(path, new FTPFileFilter() {
			@Override
			public boolean accept(FTPFile arg0) {
				return regex == null || arg0.getName().matches(regex);
			}
		});
		List<ResourceProperties> entries = new ArrayList<ResourceProperties>();
		if (listFiles != null) {
			for (FTPFile file : listFiles) {
				String childPath = path == null ? file.getName() : path + "/" + file.getName();
				entries.add(toProperties(childPath, file, transactionable));
			}
		}
		return entries;
	}
	
	@WebResult(name = "stream")
	public InputStream read(@WebParam(name = "connectionId") @NotNull String connectionId, @NotNull @WebParam(name = "uri") URI uri) throws IOException {
		if (connectionId == null) {
			throw new IllegalArgumentException("You must send a valid ftp connection id");
		}
		FTPTransactionable transactionable = retrieve(connectionId);
		if (transactionable == null) {
			throw new IllegalStateException("No such ftp connection found");
		}
		String path = getPath(uri, transactionable);
		return new BufferedInputStream(transactionable.getClient().retrieveFileStream(path));
	}
	
	public void delete(@WebParam(name = "connectionId") @NotNull String connectionId, @NotNull @WebParam(name = "uri") URI uri) throws IOException {
		if (connectionId == null) {
			throw new IllegalArgumentException("You must send a valid ftp connection id");
		}
		FTPTransactionable transactionable = retrieve(connectionId);
		if (transactionable == null) {
			throw new IllegalStateException("No such ftp connection found");
		}
		String path = getPath(uri, transactionable);
		if (path.endsWith("/")) {
			transactionable.getClient().removeDirectory(path);
		}
		else {
			transactionable.getClient().deleteFile(path);
		}
	}
	
}
