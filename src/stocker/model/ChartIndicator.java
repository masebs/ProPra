package stocker.model;

import java.util.Iterator;
import java.util.LinkedList;

import javax.swing.JComboBox;

import stocker.util.Candle;
import stocker.util.EChartColors;

/**
 * Represents a technical indicator for a chart. An indicator is described by a list of data candles that
 * the indicator is defined on, the values of the indicator, and a separate list of timestamps at which 
 * the candles are given (for more convenience when plotting this indicator to a chart).
 * 
 * @author Marc S. Schneider
 * @see ChartIndicatorSMA
 * @see ChartIndicatorBollingerBands
 */
public abstract class ChartIndicator {
	/**
	 * LinkedList of LinkedList of values for this indicator. Each inner list contains a series of indicator values,
	 * one for each time stamp in {@link #times}. The outer list contains a number of lists representing different
	 * values for this indicator (e.g. the upper and lower bound of a band). This supports an arbitrary number of
	 * values within one indicator.
	 */
	private LinkedList<LinkedList<Double>> values; 

	private LinkedList<Candle> candles; // the candles on which this indicator is calculated
	private LinkedList<Long> times;     // for convenience during plotting; the time stamps are also in the candles
	private boolean isParametrized = false;
	private boolean isActive = true;
	private EChartColors color = EChartColors.BLUE;
	private JComboBox<EChartColors> comboColors = new JComboBox<EChartColors>(EChartColors.values());

	/** 
	 * Constructs a new chart indicator without assigning data candles and without setting parameters.
	 * These need to be set later via {@link #setParameters(int[])} and {@link #setCandles(LinkedList)},
	 * or (for the parameters) interactively with a dialog via {@link #getParametersMessage()} and 
	 * {@link #parametrizeFromTextfields()}.
	 */
	public ChartIndicator() {
		this.candles = new LinkedList<Candle>();
		this.values = new LinkedList<LinkedList<Double>>();
		this.times = new LinkedList<Long>();
	}

	/**
	 * Constructs a new chart indicator with data candles assigned, but without the parameters set. 
	 * The parameters need to be set later via {@link #setParameters(int[])},
	 * or interactively with a dialog via {@link #getParametersMessage()} and 
	 * {@link #parametrizeFromTextfields()}.
	 * @param candles a LinkedList of candles representing the data that this indicator is defined on
	 */
	public ChartIndicator(LinkedList<Candle> candles) {
		this.candles = candles;
		this.values = new LinkedList<LinkedList<Double>>();
		this.times = new LinkedList<Long>();
	}

	/**
	 * Returns all values defined by this indicator. 
	 * @return a LinkedList containing one or several LinkedLists of Double values, one list for each indicator value
	 */
	public LinkedList<LinkedList<Double>> getAllValues() {
		return values;
	}

	/**
	 * Returns one list of values for one of the indicators defined by this ChartIndicator (e.g. a lower Bollinger band 
	 * or a simple moving average).
	 * @param i request the i-th indicator value defined by this ChartIndicator
	 * @return the LinkedList of Double values for the indicator at the requested position, 
	 *         or null if there if there are less than i+1 indicators.
	 */
	public LinkedList<Double> getValues(int i) {
		if (i < values.size()) {
			return values.get(i);
		} else {
			return null;
		}
	}

	/**
	 * Get an iterator for the i-th value list defined by this indicator. 
	 * @param i request an iterator for the i-th indicator value defined by this ChartIndicator
	 * @return an iterator over the LinkedList of Double values for the indicator at the requested position,
	 * 		   or null if there if there are less than i+1 indicators.
	 */
	public Iterator<Double> getValueIterator(int i) {
		if (i < values.size()) {
			return values.get(i).iterator();
		} else {
			return null;
		}
	}
	
	/**
	 * Get an iterator for the time stamps at which this indicator is defined.
	 * @return an iterator of a LinkedList of Long values, representing the times stamps at which this indicator is defined
	 */
	public Iterator<Long> getTimeIterator() {
		return times.iterator();
	}

	/**
	 * Get the number of indicator values that are defined by this indicator, i.e. the number of LinkedList<Double> 
	 * that the LinkedList returned by {@link #getAllValues()} contains. 
	 * @return the number of indicator values defined by this ChartIndicator
	 */
	public int getNrOfValues() {
		return values.size();
	}

	/**
	 * Set the data candles that this indicator is defined on. If candles have been set previously, they
	 * will be replaced. Changes to the indicator values will only happen after {@link #calculate()} 
	 * has been called.
	 * @param candles a LinkedList of data candles that this chart indicator should be defined on
	 */
	public void setCandles(LinkedList<Candle> candles) {
		this.candles = candles;
	}

	/**
	 * Set this indicator's activity flag to active (if active, a chart should draw this indicator).
	 * @param b true to set this indicator active, or false to set it inactive
	 */
	public void setActive(boolean b) {
		this.isActive = b;
	}

	/** 
	 * Query whether this indicator is set to active.
	 * @return true if this indicator is set as active, false otherwise.
	 */
	public boolean isActive() {
		return isActive;
	}

	/**
	 * Returns a String representation of this indicator. To be implemented by the subclasses.
	 * @return a String representation of this indicator
	 */
	@Override
	public abstract String toString();

	/**
	 * Calculate this indicator's values based on the parameters and data candles that have previously been set.
	 * Values at all time stamps will be calculated. If values had been calculated before, they will be recalculated.
	 * To be implemented by the subclasses.
	 */
	public abstract void calculate();

	/** 
	 * Get an object array containing Strings and JTextFields from which the parameters can be requested with a
	 * JOptionPane.showConfirmDialog. To be implemented by the subclasses.
	 * @return an array containing the Strings and JTextFields required to construct a dialog to enter the parameter values
	 */
	public abstract Object[] getParametersMessage();

	/**
	 * Determine the parameters from user input in text fields (to be used in conjunction wih
	 * {@link #getParametersMessage()}). Call this method after the parameter selection dialog has been 
	 * closed / confirmed. To be implemented by the subclasses.
	 */
	public abstract void parametrizeFromTextfields();

	/**
	 * Get a String representing a human-readable name for this type (class) of indicator. 
	 * To be implemented by the subclasses.
	 * @return a String representing a human-readable name for this type (class) of indicator
	 */
	public abstract String getType();
	
	/**
	 * Get the parameters of this indicator as an array. This can be used for serialization.
	 * To be implemented by the subclasses.
	 * @return an array which contains the parameters by which this indicator is defined
	 */
	public abstract int[] getParameters();

	/**
	 * Set the parameters of this indicator to the values given in the argument. This can be used for deserialization.
	 * @param params an array of parameters to be set for this indicator
	 */
	public abstract void setParameters(int[] params);
	
	/**
	 * Returns the color in which the indicator should be drawn.
	 * @return the color in which the indicator should be drawn
	 */
	public EChartColors getColor() {
		return color;
	}
	
	/**
	 * Sets the color in which this indicator should be drawn to the specified color.
	 * @param c the color in which the indicator should be drawn
	 */
	public void setColor(EChartColors c) {
		this.color = c;
		this.comboColors.setSelectedItem(c);
	}
	
	/** 
	 * Returns a combo box with the available colors, the active one pre-selected
	 * @return a combo box with the available colors, the active one pre-selected
	 */
	protected JComboBox<EChartColors> getColorCombo() {
		return comboColors;
	}
	
	/** 
	 * Sets the active indicator color from the combo (to be called after the combo has been used for a parametrization dialog).
	 */
	protected void setColorFromCombo() {
		this.color = comboColors.getItemAt(comboColors.getSelectedIndex());
	}
	
	/**
	 * Add a list of values to the indicator's value list (one list could be e.g. the lower Bollinger Band).
	 * @param valueList the list to be added
	 */
	protected void addValueList(LinkedList<Double> valueList) {
		values.add(valueList);
	}
	
	/**
	 * Replace the value list at the given index by the provided new value list.
	 * @param index the index of the list to be replaced
	 * @param valueList the new value list to be set at index
	 */
	protected void setValuesList(int index, LinkedList<Double> valueList) {
		values.set(index, valueList);
	}
	
	/**
	 * Set this chart indicator as parametrized (to be used by subclasses after all initialization has been done).
	 * @param isParametrized true to set this indicator parametrized, false to set it un-parametrized
	 */
	protected void setParametrized(boolean isParametrized) {
		this.isParametrized = isParametrized;
	}
	
	/**
	 * Returns whether this indicator is parametrized.
	 * @return true if parametrized, false otherwise
	 */
	protected boolean isParametrized() {
		return isParametrized;
	}
	
	/**
	 * Returns the list of data candles that this indicator is based on.
	 * @return the list of data candles that this indicator is based on
	 */
	protected LinkedList<Candle> getCandles() {
		return candles;
	}
	
	/**
	 * Returns the list of times at which this indicator is evaluated.
	 * @return the list of times at which this indicator is evaluated
	 */
	protected LinkedList<Long> getTimes() {
		return times;
	}
}
