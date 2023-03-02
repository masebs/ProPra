package stocker.control;

/**
 * An interface defining an object which is able to receive messages from a {@link WSPushClient}.
 * 
 * @author Marc S. Schneider
 */
public interface IPushReceiver {
	/**
	 * Accepts a new push message which has just arrived at the {@link WSPushClient}.
	 * @param message the received message
	 */
	public void pushMessageIncoming(String message);
	
	/**
	 * Notifies the receiver that an error occured in the {@link WSPushClient} and the connection was closed
	 */
	public void websocketConnectionClosedWithError();
}
