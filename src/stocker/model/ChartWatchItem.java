package stocker.model;

import java.util.LinkedList;

import com.google.gson.JsonObject;

import stocker.util.Candle;
import stocker.util.EChartInterval;

/**
 * Represents an item (a stock, for instance) which is meant to be drawn as a chart. ChartWatchItems are mainly used for 
 * transfer of chart-relevant data between the data manager and the chart panel. The chart panel then uses its own 
 * data format to store the data.
 * 
 * @author Marc S. Schneider
 */
public class ChartWatchItem extends WatchItem {
	
	private EChartInterval interval;
	private LinkedList<Candle> candleList = new LinkedList<Candle>();
		// contains the candles for one resolution, one time period - is overwritten as soon as other data is requested

	/** 
	 * Constructs a new ChartWatchItem referring to the data with the provided key.
	 * The chart interval will be set to 1 day and the start end end time to zero (needs to be set properly later).
	 * @param key the ticker symbol of the item to be watched
	 * @param name the descriptive name of the item to be watched
	 */
	public ChartWatchItem(String key, String name) {
		super(key, name);
		this.interval = EChartInterval.I1DAY;
	}
	
	/** 
	 * Constructs a new ChartWatchItem referring to the data with the provided key.
	 * @param key the ticker symbol of the item to be watched
	 * @param name the descriptive name of the item to be watched
	 * @param interval the {@link stocker.util.EChartInterval} that this chart should have
	 */
	public ChartWatchItem(String key, String name, EChartInterval interval) {
		super(key, name);
		this.interval = interval;
	}
	
	/**
	 * Get the {@link stocker.util.EChartInterval} that this ChartWatchItem currently contains.
	 * @return the current {@link stocker.util.EChartInterval}
	 */
	public EChartInterval getInterval() {
		return interval;
	}
	
	/**
	 * Set the {@link stocker.util.EChartInterval} that this ChartWatchItem should contain from now on.
	 * @param interval the new {@link stocker.util.EChartInterval} 
	 */
	public void setInterval(EChartInterval interval) {
		this.interval = interval;
	}
	
	/**
	 * Get the start time for the data to be contained in a chart for this item.
	 * @return the current start time 
	 */
	public long getStartTime() {
		return candleList.getFirst().time;
	}

	/**
	 * Get the end time for the data to be contained in a chart for this item.
	 * @return the current end time 
	 */
	public long getEndTime() {
		return candleList.getLast().time;
	}
	
	/**
	 * Set the data candles for this ChartWatchItem according to the parameters.
	 * @param candles a LinkedList of candles that represent the data of this item
	 * @param interval the {@link stocker.util.EChartInterval} that this chart should have
	 */
	public void setCandles(LinkedList<Candle> candles, EChartInterval interval) {
		this.candleList = candles;
		this.interval = interval;
	}

	/**
	 * Get the list of data candles that is currently assigned to this item.
	 * @return the current LinkedList of data candles 
	 */
	public LinkedList<Candle> getCandles() {
		return this.candleList;
	}
	
	/**
	 * Append a new data candle according to the parameters to the list of candles and remove the first (oldest) one.
	 * @param time the unix timestamp at which the data are given
	 * @param low the lowest price during the period
	 * @param high the highest price during the period
	 * @param open the opening price (at the begin of the period)
	 * @param close the closing price (at the end of the period)
	 */
	public void appendValues(long time, double low, double high, double open, double close) {
		// append these values to the end and remove the first candle
		candleList.add(new Candle(time, low, high, open, close, 0L));
		candleList.remove(0);
	}

	/** 
	 * {@inheritDoc}
	 */
	@Override
	public JsonObject serializeToJson() {
		JsonObject jo = new JsonObject();
		jo.addProperty("key", getKey());
		jo.addProperty("name", getName());
		jo.addProperty("interval", interval.toObjectString());
		return jo;
	}

	/** 
	 * {@inheritDoc}
	 */
	@Override
	public void deserializeFromJson(JsonObject jo) {
		this.interval = EChartInterval.valueOf(jo.get("interval").getAsString());
	}

}
