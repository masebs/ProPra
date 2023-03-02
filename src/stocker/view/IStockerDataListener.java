package stocker.view;

import stocker.control.StockerDataManager;

/**
 * An interface defining an object which is able to receive push data like those from {@link StockerDataManager}.
 * 
 * @author Marc S. Schneider
 */
public interface IStockerDataListener {
	/**
	 * To be called every time when new push updates are available. Should process the new data appropriately.
	 * @param key the key of the item which these data refer to
	 * @param time the unix timestamp for which a new price is reported
	 * @param price the price at the time
	 */
	void onPushUpdate(String key, long time, double price);
}
