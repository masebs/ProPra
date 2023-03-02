package stocker.control;

import java.net.URI;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

/**
 * A {@link WebSocketClient} to receive push updates for the registered symbols.
 * 
 * @author Marc S. Schneider
 */
public class WSPushClient extends WebSocketClient {

	private boolean isConnected = false;
	private IPushReceiver receiver;

	/**
	 * Construct a new WSPushClient.
	 * @param uri the URI to connect to
	 * @param dm the receiver which should be notified when a message is pushed
	 */
	public WSPushClient(URI uri, IPushReceiver dm) {
		super(uri);
		this.receiver = dm;
	}

	/**
	 * Called after an opening handshake has been performed and the given websocket is ready to be written on.
	 * @param handshakedata The handshake of the websocket instance
	 */
	@Override
	public void onOpen(ServerHandshake handshakedata) {
		this.isConnected = true;
		System.out.println("WSPushClient: Connection open");
	}

	/**
	 * Callback for string messages received from the remote host.
	 * @param message The UTF-8 decoded message that was received
	 */
	@Override
	public void onMessage(String message) {
		if (message == null)
			System.out.println("null message!");
		if (!message.contains("\"type\":\"ping\"")) {
			receiver.pushMessageIncoming(message);
		}
	}

	/**
	 * Called after the websocket connection has been closed.
	 * Parameters:
	 * @param code The codes can be looked up here: CloseFrame 
	 * @param reason Additional information string
	 * @param remote Returns whether or not the closing of the connection was initiated by the remote host
	 */
	@Override
	public void onClose(int code, String reason, boolean remote) {
		System.out.println(
				"Connection was closed by " + (remote ? "Server" : "Client") + " Code: " + code + " Reason: " + reason);
		isConnected = false;
	}

	/**
	 * Called when an errors occurs. If an error causes the websocket connection to fail, {@link #onClose(int, String, boolean)} 
	 * will be called additionally. This method will be called primarily because of IO or protocol errors. 
	 * If the given exception is an RuntimeException that probably means that you encountered a bug.
	 * @param ex The exception causing this error
	 */
	@Override
	public void onError(Exception ex) {
		if (ex != null) {
			System.err.println("Error in WSPushClient: " + ex.getClass() + ": " + ex.getMessage());
			if (ex.getMessage() != null && ex.getMessage().contains("Socket closed")) {
				receiver.websocketConnectionClosedWithError();
			}
		} else {
			System.err.println("Unknown error in WSPushClient");
		}
	}

	/**
	 * Checks whether the client is currently connected.
	 * @return true if connected, false otherwise
	 */
	public boolean isConnected() {
		return this.isConnected;
	}
}
