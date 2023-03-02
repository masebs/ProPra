package stocker.util;

/**
 * Represents a Candle for use in a candlestick chart. Pure data container with public attributes.
 *
 * @author Marc S. Schneider
 */
public class Candle {
	/** 
	 * Holds the corresponding value of the candle. Public, can directly be modified from anywhere.
	 */
	public double low, high, open, close, volume;
	/** 
	 * The unix timestamp for this candle. Public, can directly be modified from anywhere.
	 */
	public long time;
	
	/**
	 * Constructs a new Candle from the values given as parameters.
	 * @param t The unix timestamp of this candle (type long)
	 * @param l The lowest price during this candle's interval (type double)
	 * @param h The highest price during this candle's interval (type double)
	 * @param o The opening price, at the beginning of this candle's interval (type double)
	 * @param c The closing price, at the end of this candle's interval (type double)
	 * @param v The volume of trading for this candle (type double)
	 */
	public Candle(long t, double l, double h, double o, double c, double v) {
		this.time = t;
		this.low = l;
		this.high = h;
		this.open = o;
		this.close = c;
		this.volume = v;
	}
}
