package stocker.model;

/**
 * Represents an alarm within a chart. An alarm is described by a value (price) at which the alarm will be triggered. 
 * Whether it is triggered will be determined by comparison of the alarm's value with the currently and the previously 
 * reported price.
 *
 * @author Marc S. Schneider
 */
public class ChartAlarm {
	private double value;
	private double lastPrice = 0.0;
	private boolean hasBeenTriggered = false;

	/**
	 * Constructs a new chart alarm with the given value. 
	 * @param value The value (price) at which this alarm should be triggered
	 */
	public ChartAlarm(double value) {
		this.value = value;
	}
	
	/**
	 * Constructs a new chart alarm with the given value and a specified intial lastPrice (which might have been saved 
	 * from a previous run). The alarm will be triggered on the very first push update when the alarm's value
	 * is between the inital lastPrice and the first value being pushed.
	 * @param value the value (price) at which this alarm should be triggered
	 * @param lastPrice the initial lastPrice to be set
	 */
	public ChartAlarm(double value, double lastPrice) {
		this.value = value;
		this.lastPrice = lastPrice;
	}

	/**
	 * Returns the value (price) at which this alarm is triggered.
	 * @return The value (price) at which this alarm is triggered
	 */
	public double getValue() {
		return value;
	}
	
	/**
	 * Returns the last price that this alarm has seen (useful for serialization).
	 * @return The last price that this alarm has seen
	 */
	public double getLastPrice() {
		return lastPrice;
	}
	
	/**
	 * Checks if the alarm is triggered based on comparison of the value with the price seen on the last check and 
	 * the current price.
	 * @param price the currently reported price 
	 * @return If the alarm value has been passed: the (signed) difference between the current and the previous price, i.e.
	 * 		   positive when the alarm value was passed while the price was rising, negative when it was dropping;
	 * 		   or 0.0 if the alarm value has not been passed
	 */
	public double check(double price) {
		double change = 0.0;
		if (lastPrice != 0.0 && !hasBeenTriggered) { // if there is a valid lastPrice and this alarm hasn't been triggered yet
			if (Math.signum(lastPrice-value) != Math.signum(price-value)) {
				change = price - lastPrice;
				hasBeenTriggered = true;
			}
		}
		lastPrice = price;
		return change;
	}

	/**
	 * Returns the trigger value of this alarm as a String representation.
	 * @return a string representation of this alarm, i.e. its trigger value
	 */
	@Override
	public String toString() {
		return String.valueOf(value);
	}

}
