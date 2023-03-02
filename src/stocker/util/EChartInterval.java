package stocker.util;

/**
 * Defines time intervals for data series.
 * 
 * @author Marc S. Schneider
 */
public enum EChartInterval {
	I1MIN("1", "1 min", 60), I5MIN("5", "5 min", 5*60), I15MIN("15", "15 min", 15*60), I30MIN("30", "30 min", 30*60),
	I1H("60", "1 h", 60*60), I1DAY("D", "1 Tag", 24L*60L*60L), I1WEEK("W", "1 Woche", 7L*24L*60L*60L), I1MONTH("M", "1 Monat", 30L*24L*60L*60L);

	private String pullstring; // the string required for pull requests
	private String repstring;  // the string for representation e.g. in the property combo box
	private long inSeconds;

	/**
	 * Private constructor; expects different representations of the interval as arguments.
	 * @param pullstring the string which is required for a pull reques for that interval at the data provider
	 * @param repstring the string which is to be used for representation of this interval to the user
	 * @param inSeconds the duration of this interval in seconds (required for some internal calculations)
	 */
	private EChartInterval(String pullstring, String repstring, long inSeconds) {
		this.pullstring = pullstring;
		this.repstring = repstring;
		this.inSeconds = inSeconds;
	}

	/**
	 * Get a human-readable string describing this candle scheme, suitable for direct display to the user.
	 * @return a human-readable string describing this candle scheme
	 */
	@Override
	public String toString() {
		return repstring;
	}

	/**
	 * Get the String which is required to do a pull request to obtain data for this time interval.
	 * @return the String which is required for a pull request
	 */
	public String toPullString() {
		return pullstring;
	}

	/**
	 * Get the String which Object.toString() returns. Useful e.g. for serialization or for use with 
	 * {@link #valueOf(String)}.
	 * @return the result of Object's toString() method
	 */
	public String toObjectString() {
		return super.toString();
	}
	
	/**
	 * Get the duration of this time interval in seconds.
	 * @return the duration of this time interval in seconds
	 */
	public long inSeconds() {
		return this.inSeconds;
	}
}
