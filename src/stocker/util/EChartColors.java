package stocker.util;

import java.awt.Color;

/**
 * Defines colors which can be used to draw lines or candles.
 * 
 * @author Marc S. Schneider
 */
public enum EChartColors {
	RED(0xf71616, "rot"), ORANGE(0xf59342, "orange"), YELLOW(0xf2f542, "gelb"), GREEN(0x31ad62, "grün"), 
	CYAN(0x42d6db, "cyan"), BLUE(0x2943f0, "blau"), VIOLET(0x7b3eb0, "lila"), MAGENTA(0xfc17c7, "magenta"),
	BLACK(0x000000, "schwarz"), GRAY(0x757575, "grau");

	private int rgb;          // the RGB value of the color
	private String repstring; // string for representation e.g. in combo boxes

	private EChartColors(int i, String repstring) {
		this.rgb = i;
		this.repstring = repstring;
	}

	/**
	 * Get an RGB representation of this color.
	 * @return an integer containing the RGB representation of this color
	 */
	public String toRGB() {
		return Integer.toString(this.rgb);
	}

	/**
	 * Get a Color object of this color.
	 * @return a Color object with this color
	 */
	public Color toColor() {
		return new Color(rgb);
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
