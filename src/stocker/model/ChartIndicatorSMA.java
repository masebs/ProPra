package stocker.model;

import java.util.LinkedList;
import java.util.ListIterator;

import javax.swing.JTextField;

import stocker.util.Candle;
import stocker.util.TextfieldIntValidatorOnFocusLost;

/**
 * Provides the Simple Moving Average as a technical indicator for a chart. An indicator is described by a 
 * list of data candles that the indicator is defined on, the values of the indicator, and a separate list 
 * of timestamps at which the candles are given (for more convenience when plotting this indicator to a chart).
 * 
 * @author Marc S. Schneider
 */
public class ChartIndicatorSMA extends ChartIndicator {

	private int nPoints;
	private LinkedList<Double> valuesSMA = new LinkedList<Double>();
	private JTextField tn;

	/** 
	 * Constructs a new Simple Moving Average chart indicator without assigning data candles and without setting 
	 * parameters. These need to be set later via {@link #setParameters(int[])} and {@link #setCandles(LinkedList)},
	 * or (for the parameters) interactively with a dialog via {@link #getParametersMessage()} and 
	 * {@link #parametrizeFromTextfields()}.
	 */
	public ChartIndicatorSMA() {
		super();
		addValueList(valuesSMA);
		initializeTextFields();
	}

	/**
	 * Constructs a new Simple Moving Average chart indicator with data candles assigned, but without the 
	 * parameters set. The parameters need to be set later via {@link #setParameters(int[])},
	 * or interactively with a dialog via {@link #getParametersMessage()} and 
	 * {@link #parametrizeFromTextfields()}.
	 * @param candles A LinkedList of candles representing the data that this indicator is defined on
	 * @param nPoints the number of recent points over which the average is calculated
	 */
	public ChartIndicatorSMA(LinkedList<Candle> candles, int nPoints) {
		super(candles);
		this.nPoints = nPoints;
		addValueList(valuesSMA);
		calculate();
		initializeTextFields(); // just in case someone needs them later...
	}

	private void initializeTextFields() {
		tn = new JTextField(3);
		tn.setText("20");
		tn.addFocusListener(new TextfieldIntValidatorOnFocusLost(tn, 1, 10000, 20));
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void calculate() {
		LinkedList<Candle> candles = getCandles();
		LinkedList<Long> times = getTimes();
		ListIterator<Candle> candleIt = candles.listIterator();
		
		valuesSMA.clear();
		times.clear();
		for (int i = 0; i < candles.size(); i++) { // iterate over this indicator's data points (times)
			Double v = 0.0;
			double sum = 0.0;
			times.add(candleIt.next().time); // add next timestamp
			if (i >= nPoints - 1) { // calculate only if we are at least npoints-1 from the start
				Candle c = candleIt.previous();
				for (int k = 0; k < nPoints; k++) { // calculate sum
					sum += c.close;
					if (candleIt.hasPrevious()) { // it won't have a previous for i = k = nPoints-1
						c = candleIt.previous();
					}
				}
				if (nPoints > 0) { // divide by n if n > 0
					v = sum / nPoints;
				} else {
					v = 0.0;
				}
				while (candleIt.nextIndex() <= i) {
					candleIt.next();
				}
			}
			valuesSMA.add(v);  // add value to results list
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Object[] getParametersMessage() {
		Object[] msg = { "n (Anz. Punkte Mittelwert)", tn, "Farbe", super.getColorCombo()};
		return msg;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void parametrizeFromTextfields() {
		// Text field input has been validated using TextfieldIntValidatorOnFocusLost, so no more checks needed
		this.nPoints = Integer.parseInt(tn.getText());
		super.setColorFromCombo();
		setParametrized(true);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString() {
		return new StringBuilder().append("GD(").append(nPoints).append(")").toString();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getType() {
		return "SMA";
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int[] getParameters() {
		int[] arr = new int[1];
		arr[0] = nPoints;
		return arr;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void setParameters(int[] params) {
		this.nPoints = params[0];
		setParametrized(true);
	}
}
