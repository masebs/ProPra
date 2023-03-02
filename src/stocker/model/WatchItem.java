package stocker.model;

import com.google.gson.JsonObject;

/**
 * Represents a generic item to be watched within Stocker (a stock, a currency, a crypto currency).
 * Subclasses specialize this to items for a particular purpose.
 * 
 * @author Marc S. Schneider
 * @see WatchlistItem
 * @see ChartWatchItem
 */
public abstract class WatchItem {

	private String key;  // the key (e.g. ticker symbol) by which the watched item is identified
	private String name; // a descriptive name for the watched item
	
	/**
	 * Construct a new WatchItem with the given key and name.
	 * @param key the key by which the item is identified, e.g. a ticker symbol
	 * @param name a descriptive name for the watched item
	 */
	public WatchItem(String key, String name) {
		this.key = key;
		this.name = name;
	}

	/**
	 * Get the key for the item represented by this WatchItem.
	 * @return the key values (e.g. a ticker symbol)
	 */
	public String getKey() {
		return key;
	}

	/**
	 * Get the descriptive name for the item represented by this WatchItem.
	 * @return the descriptive name
	 */
	public String getName() {
		return name;
	}

	/**
	 * Serialize the content of this WatchItem into a JsonObject.
	 * @return a JsonObject containing the serialized data
	 */
	public abstract JsonObject serializeToJson();

	/**
	 * Deserialize the content of a WatchItem from a JsonObject into this WatchItem.
	 * @param jo a JsonObject containing serialized WatchItem data
	 */
	public abstract void deserializeFromJson(JsonObject jo);

}
