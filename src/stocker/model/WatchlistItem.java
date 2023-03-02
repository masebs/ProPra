package stocker.model;

import java.time.Instant;

import com.google.gson.JsonObject;

/**
 * Represents an item (a stock, for instance) which is meant to be monitored in a watchlist. WatchlistItems are mainly used for 
 * transfer of the relevant data between the data manager and the watchlist.
 * 
 * @author Marc S. Schneider
 */
public class WatchlistItem extends WatchItem {
	private double curPrice;
	private long curTime;
	private long lastQuoteTime;
	private double closeYesterday;

	/** 
	 * Constructs a new WatchlistItem referring to the data with the provided key.
	 * @param key the ticker symbol of the item to be watched
	 * @param name the descriptive name of the item to be watched
	 */
	public WatchlistItem(String key, String name) {
		super(key, name);
		this.curPrice = 0.0;
		this.closeYesterday = 0.0;
		this.curTime = 0L;
		this.lastQuoteTime = 0L;
	}
	
	/** 
	 * Get the current (last reported) price for the represented item.
	 * @return the last available price
	 */
	public double getPrice() {
		return curPrice;
	}
	
	/**
	 * Get the last time stamp for which a price is available.
	 * @return the unix timestamp for the latest available price
	 */
	public long getTime() {
		return curTime;
	}
	
	/**
	 * Get the last time stamp at which a quote has been pulled
	 * @return the unix timestamp at which the last quote has been pulled
	 */
	public long getQuoteTime() {
		return lastQuoteTime;
	}
	
	/**
	 * Get yesterday's close value.
	 * @return yesterday's close value
	 */
	public double getCloseYesterday() {
		return closeYesterday;
	}

	/**
	 * Set the attributes of this WatchlistItem to the given values
	 * @param time the unix timestamp which the following values refer to
	 * @param price the price at the time
	 * @param closeYesterday the closing price at the previous day (required for calculation of daily change)
	 */
	public void setData(long time, double price, double closeYesterday) {
		this.curTime = time;
		this.curPrice = price;
		this.closeYesterday = closeYesterday;
	}
	
	/**
	 * Sets the data of this WatchlistItem just like {@link #setData(long, double, double)}, but in addition sets the
	 * time for the last quote which can later be queried using {@link #getQuoteTime()}.
	 * @param time the unix timestamp which the following values refer to
	 * @param price the price at the time
	 * @param closeYesterday the closing price at the previous day (required for calculation of daily change)
	 */
	public void setQuote(long time, double price, double closeYesterday) {
		setData(time, price, closeYesterday);
		this.lastQuoteTime = Instant.now().getEpochSecond();
	}
	
	/**
	 * Serialize the content of this WatchItem into a JsonObject
	 * @return a JsonObject containing the serialized data
	 */
	public JsonObject serializeToJson() {
		JsonObject jo = new JsonObject();
		jo.addProperty("curPrice", curPrice);
		jo.addProperty("closeYesterday", closeYesterday);
		jo.addProperty("curTime", curTime);
		return jo;
	}

	/**
	 * Deserialize the content of a WatchItem from a JsonObject into this WatchItem
	 * @param jo a JsonObject containing serialized WatchItem data
	 */
	public void deserializeFromJson(JsonObject jo) {
		this.curPrice = jo.get("curPrice").getAsDouble();
		this.closeYesterday = jo.get("closeYesterday").getAsDouble();
		this.curTime = jo.get("curTime").getAsLong();
	}

}
