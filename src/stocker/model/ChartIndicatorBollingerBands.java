package stocker.model;

import java.util.LinkedList;
import java.util.ListIterator;

import javax.swing.JTextField;

import stocker.util.Candle;
import stocker.util.TextfieldIntValidatorOnFocusLost;

/**
 * Provides the Bollinger Bands as a technical indicator for a chart. An indicator is described by a list of data 
 * candles that the indicator is defined on, the values of the indicator, and a separate list of timestamps at which 
 * the candles are given (for more convenience when plotting this indicator to a chart).
 * 
 * @author Marc S. Schneider
 */
public class ChartIndicatorBollingerBands extends ChartIndicator {

	private int nPoints;
	private int m;
	private double f; // needs to be double to support test class (GUI supports int only)
	private LinkedList<Double> lower = new LinkedList<Double>();
	private LinkedList<Double> upper = new LinkedList<Double>();
	private ChartIndicatorSMA indSMA; // for calculation of the Moving Average within this calculation
	private LinkedList<Double> sma = new LinkedList<Double>();
	private JTextField tn, tm, tf;

	/** 
	 * Constructs a new Bollinger Band chart indicator without assigning data candles and without setting parameters.
	 * These need to be set later via {@link #setParameters(int[])} and {@link #setCandles(LinkedList)},
	 * or (for the parameters) interactively with a dialog via {@link #getParametersMessage()} and 
	 * {@link #parametrizeFromTextfields()}.
	 */
	public ChartIndicatorBollingerBands() {
		super();
		addValueList(lower);
		addValueList(sma);
		addValueList(upper);
		initializeTextFields();
	}

	/**
	 * Constructs a new Bollinger Band chart indicator with data candles assigned, but without the parameters set. 
	 * The parameters need to be set later via {@link #setParameters(int[])},
	 * or interactively with a dialog via {@link #getParametersMessage()} and 
	 * {@link #parametrizeFromTextfields()}.
	 * @param candles a LinkedList of candles representing the data that this indicator is defined on
	 */
	public ChartIndicatorBollingerBands(LinkedList<Candle> candles, int nPoints, int m, double f) {
		super(candles);
		this.nPoints = nPoints;
		this.f = f;
		this.m = m;
		setParametrized(true);
		addValueList(lower);
		addValueList(sma);
		addValueList(upper);
		indSMA = new ChartIndicatorSMA(candles, nPoints);
		sma = indSMA.getValues(0);
		calculate();
		initializeTextFields(); // just in case someone needs them later...
	}

	private void initializeTextFields() {
		tn = new JTextField(3);
		tn.setText("20");
		tn.addFocusListener(new TextfieldIntValidatorOnFocusLost(tn, 1, 10000, 20));
		tm = new JTextField(3);
		tm.setText("20");
		tm.addFocusListener(new TextfieldIntValidatorOnFocusLost(tm, 1, 10000, 20));
		tf = new JTextField(3);
		tf.setText("2");
		tf.addFocusListener(new TextfieldIntValidatorOnFocusLost(tf, 1, 1000, 2));
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void calculate() {
		if (!isParametrized()) { // nothing can be calculated unless parameters are set
			return;
		}
		indSMA.calculate(); // calculate the internal SMA 
		LinkedList<Candle> candles = getCandles(); // get Candles (attribute belongs to super class)
		LinkedList<Long> times = getTimes();       // get times (attribute belongs to super class)
		ListIterator<Candle> candleIt = candles.listIterator();
		ListIterator<Double> smaIt = sma.listIterator();
		lower.clear();  // delete any previously present data
		upper.clear();
		times.clear();
		
		for (int i = 0; i < candles.size(); i++) { // iterate over this indicator's data points (times)
			Double v = 0.0;
			double sum = 0.0;
			times.add(candleIt.next().time); // append next timestamp
			smaIt.next();                    // proceed in the internal helper (SMA) indicator
			if (i >= m-1) {
				Candle c = candleIt.previous();
				Double curSMA = smaIt.previous();
				for (int k = 0; k <= m-1; k++) { // calculate sum of squares
					sum += Math.pow(c.close - curSMA, 2);
					if (candleIt.hasPrevious()) {
						c = candleIt.previous();
					}
				}
				if (m > 0) { // calculate square root (only if m > 0)
					v = Math.sqrt(sum / m);
				} else {
					v = 0.0;
				}
				while (candleIt.nextIndex() <= i) {
					candleIt.next();
				}
				smaIt.next();
			}
			lower.add(sma.get(i) - f * v);  // calculate the bands and add them to the result
			upper.add(sma.get(i) + f * v);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void setCandles(LinkedList<Candle> candles) {
		super.setCandles(candles);
		indSMA = new ChartIndicatorSMA(candles, nPoints);
		sma = indSMA.getValues(0);
		setValuesList(1, sma);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Object[] getParametersMessage() {
		Object[] msg = { "n (Anz. Punkte Mittelwert)", tn, "m (Anz. Punkte Stdabw.)", tm, "f (Breite Band)", tf, 
						 "Farbe", super.getColorCombo()};
		return msg;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void parametrizeFromTextfields() {
		// Text field input has been validated using TextfieldIntValidatorOnFocusLost, so no more checks needed
		this.nPoints = Integer.parseInt(tn.getText());
		this.m = Integer.parseInt(tm.getText());
		this.f = Integer.parseInt(tf.getText());
		super.setColorFromCombo();
		setParametrized(true);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString() {
		return new StringBuilder().append("BB(").append(nPoints).append(", ").append(m).append(", ").append(f)
				.append(")").toString();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getType() {
		return "BollingerBands";
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int[] getParameters() {
		int[] arr = new int[3];
		arr[0] = nPoints;
		arr[1] = m;
		arr[2] = (int)Math.round(f);
		return arr;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void setParameters(int[] params) {
		this.nPoints = params[0];
		this.m = params[1];
		this.f = (double)params[2];
		setParametrized(true);
	}
}
