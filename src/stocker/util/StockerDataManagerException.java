package stocker.util;

/**
 * Exception which is thrown by {@link stocker.control.StockerDataManager} if something goes wrong during data retrieval.
 * Information on what went wrong can be found with {@link #getMessage()}.
 *  
 * @author Marc S. Schneider
 */
public class StockerDataManagerException extends Exception {

	private static final long serialVersionUID = 3248562539501456455L;

	/**
	 * Constructs a new {@link StockerDataManagerException} with the provided error message.
	 * @param message the error message to be included in this exception
	 */
	public StockerDataManagerException(String message) {
		super(message);
	}
}
