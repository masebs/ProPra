package stocker.util;

/**
 * Represents types of charts (different visualization like lines or candles).
 * 
 * @author Marc S. Schneider
 */
public enum EChartType {
	CANDLE("Kerzen"), LINE("Linie");
	
	private String repstring; // string for representation e.g. in combo boxes

	private EChartType(String repstring) {
		this.repstring = repstring;
	}

	/**
	 * Get the duration of this time interval in seconds.
	 * @return the duration of this time interval in seconds
	 */
	@Override
	public String toString() {
		return repstring;
	}

	/**
	 * Get the String which Object.toString() returns. Useful e.g. for serialization or for use with 
	 * {@link #valueOf(String)}.
	 * @return the result of Object's toString() method
	 */
	public String toObjectString() {
		return super.toString();
	}
}
