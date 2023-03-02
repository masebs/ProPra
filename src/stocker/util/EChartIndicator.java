package stocker.util;

import stocker.model.ChartIndicator;
import stocker.model.ChartIndicatorBollingerBands;
import stocker.model.ChartIndicatorSMA;

/**
 * Defines types of chart indicators (subclasses of {@link ChartIndicator}) and provides a method to obtain a matching object.
 * Needs to be extended if new chart indicator types become available in order to make them available to Stocker.
 * 
 * @author Marc S. Schneider
 */
public enum EChartIndicator {
	SMA("Gleitender Mittelwert"), BollingerBands("Bollinger-Bänder");

	private String repstring;

	private EChartIndicator(String repstring) {
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

	/**
	 * Get a {@link stocker.model.ChartIndicator} object of a type matching this enum's value. This makes it 
	 * possible to use this enum as a factory for ChartIndicators. New indicator types can be made available 
	 * to Stocker by adding them here.
	 * @return a {@link stocker.model.ChartIndicator} object of a type matching this enum's value
	 */
	public ChartIndicator getIndicator() {
		ChartIndicator ci = null;
		if (this.name().equals("SMA")) {
			ci = new ChartIndicatorSMA();
		} else if (this.name().equals("BollingerBands")) {
			ci = new ChartIndicatorBollingerBands();
		}
		return ci;
	}

}
