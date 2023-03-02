package stocker.util;

/**
 * Represents a scheme to draw data candles (e.g. red/green or black/white) .
 * 
 * @author Marc S. Schneider
 */
public enum ECandleScheme {
	REDGREEN("rot-grün"), BLACKWHITE("schwarz-weiß");

	private String repstring; // string for representation e.g. in combo boxes

	private ECandleScheme(String repstring) {
		this.repstring = repstring;
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
	 * Get the String which Object.toString() returns. Useful e.g. for serialization.
	 * @return the result of Object's toString() method
	 */
	public String toObjectString() {
		return super.toString();
	}
}
