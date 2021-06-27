package nabu.protocols.ftp.client.types;

public class FTPConnectionDetails {
	
	private boolean connected;
	// a unique id to reference this connection
	private String connectionId;

	public FTPConnectionDetails() {
		// auto
	}
	public FTPConnectionDetails(String connectionId, boolean connected) {
		this.connectionId = connectionId;
		this.connected = connected;
	}

	public boolean isConnected() {
		return connected;
	}

	public void setConnected(boolean connected) {
		this.connected = connected;
	}
	public String getConnectionId() {
		return connectionId;
	}
	public void setConnectionId(String connectionId) {
		this.connectionId = connectionId;
	}
	
}
