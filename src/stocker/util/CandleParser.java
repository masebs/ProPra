package stocker.util;

import java.util.LinkedList;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Service class providing static methods to parse candles from a JsonObject.
 * 
 * @author Marc S. Schneider
 */
public final class CandleParser {

	private CandleParser() {
		// private constructor in order to prevent instantiation
	}

	/**
	 * Parse a LinkedList of candles from the given JsonObject.
	 * @param jo the JsonObject to parse from
	 * @return a LinkedList containing the parsed candles
	 */
	public static LinkedList<Candle> parseCandlesFromJsonObject(JsonObject jo) {
		double[] larr = parseDoubleArrayFromJsonObject(jo, "l");
		double[] harr = parseDoubleArrayFromJsonObject(jo, "h");
		double[] oarr = parseDoubleArrayFromJsonObject(jo, "o");
		double[] carr = parseDoubleArrayFromJsonObject(jo, "c");
		long[] tarr = parseLongArrayFromJsonObject(jo, "t");
	 	//long[] varr = parseLongArrayFromJsonObject(jo, "v"); // problems with some (forex) exchanges which don't report that (and we don't use it anyway)

		int nCandles = tarr.length;
		LinkedList<Candle> candles = new LinkedList<Candle>();

		for (int i = 0; i < nCandles; i++) {
			candles.add(new Candle(tarr[i], larr[i], harr[i], oarr[i], carr[i], 0));
		}

		return candles;
	}
	
	/** 
	 * Parse double arrays from a JsonObject representing a chart indicator.
	 * @param jo the JsonObject to parse from
	 * @param indicator the name of the indicator property in jo which should be read
	 * @return an array containing double arrays for the data values (low, high, open, close, time) and the 
	 *         indicator value
	 */
	// only required for testing with indicator values from finnhub; not used within Stocker
	public static double[][] parseDoubleArraysFromIndicatorJsonObject(JsonObject jo, String indicator) {
		double[] larr = parseDoubleArrayFromJsonObject(jo, "l");
		double[] harr = parseDoubleArrayFromJsonObject(jo, "h");
		double[] oarr = parseDoubleArrayFromJsonObject(jo, "o");
		double[] carr = parseDoubleArrayFromJsonObject(jo, "c");
		double[] tarr = parseDoubleArrayFromJsonObject(jo, "t");
		double[] ind  = parseDoubleArrayFromJsonObject(jo, indicator);

		int nCandles = tarr.length;
		double[][] arrs = new double[6][];
		
		for (int k = 0; k < 6; k++) {
			arrs[k] = new double[nCandles]; 
		}
		
		for (int i = 0; i < nCandles; i++) {
			arrs[0][i] = tarr[i];
			arrs[1][i] = larr[i];
			arrs[2][i] = harr[i];
			arrs[3][i] = oarr[i];
			arrs[4][i] = carr[i];
			arrs[5][i] = ind[i];
		}
			
		return arrs;
	}

	private static double[] parseDoubleArrayFromJsonObject(JsonObject jo, String name) {
		double[] arr = null;
		try {
			JsonArray jarr = jo.get(name).getAsJsonArray();
			arr = new double[jarr.size()];
			for (int i = 0; i < jarr.size(); i++) {
				arr[i] = jarr.get(i).getAsDouble();
			}
		} catch (Exception e) {
			System.err.println("Error: Got exception " + e.getClass() + " while trying to parse json content");
		}
		return arr;
	}

	private static long[] parseLongArrayFromJsonObject(JsonObject jo, String name) {
		long[] arr = null;
		try {
			JsonArray jarr = jo.get(name).getAsJsonArray();
			arr = new long[jarr.size()];
			for (int i = 0; i < jarr.size(); i++) {
				arr[i] = jarr.get(i).getAsLong();
			}
		} catch (Exception e) {
			System.err.println("Error: Got exception " + e.getClass() + " while trying to parse json content");
		}
		return arr;
	}

}
