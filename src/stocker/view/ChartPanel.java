package stocker.view;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;

import javax.swing.JPanel;

import stocker.model.ChartAlarm;
import stocker.model.ChartIndicator;
import stocker.model.ChartWatchItem;
import stocker.util.Candle;
import stocker.util.ECandleScheme;
import stocker.util.EChartInterval;
import stocker.util.EChartType;

/**
 * A specialized JPanel to draw charts within the Stocker application.
 * 
 * @author Marc S. Schneider
 */
public class ChartPanel extends JPanel {

	// Displays values provided as a {@link stocker.model.ChartWatchItem}.
	// Scales the values to fit into the panel size. Scaling is done in two steps:
	// 1.) in calculateScaledValues(): Scale from data coordinate system to reference system of the panel,
	//     size: (xref, yref) = this.getSize()
	// 2.) in x(), y() (used in the draw methods): From reference system of the panel to the actual drawing 
	//     coordinates of the panel, leaving sufficients margins and room for labels and accounting for the 
	//     inversion of the y (vertical) axis

	private static final long serialVersionUID = -1552462768763963452L;
	
	// basic information and settings
	private StockerChart parent;
	private EChartType chartType;
	private ECandleScheme candleScheme;
	private EChartInterval chartInterval;
	private Color alarmColor;

	// parameters for scaling and drawing (mostly constant)
	private final int maxticksx = 20, nticksy = 15;
	private final int ticklength = 8;
	private final double epsilon = 0.005; // small difference in order to account for roundoff errors and error accumulation
	private final int maxCandles = 100; // the maximum number of candles to be plotted
	private final int decreaseFontSizeX = 800;
	private final int decreaseFontSizeY = 600;
	private int vmargin, hmargin; // set later, dependent on reference size (xref, yref)
	private int cw; // candle width, set later, dependent on reference size (xref)

	// The data candles (or lines) to be drawn 
	private long[] time;     // the original (unscaled) x values
	private double[] yopen;  // the original (unscaled) y values
	private double[] yclose; 
	private double[] ylow;
	private double[] yhigh;
	private long tmin, tmax; // minimum and maximum timestamp
	private double yclosemin, yclosemax, ylowmin, yhighmax; // some minima and maxima required for scaling
	private int[] xs, ysclose, ysopen, yshigh, yslow; // scaled x and y values
	private int yslowmin, yshighmax; // scaled minimum and maximum values
	private int xref = 0, yref = 0;  // reference values used for normalization of coordinates (will be set to panel height and width)
	private int mouseposx = -1, mouseposy = -1;  // current mouse position
	private double xscale, yscale, yminmaxScale; // scale factors which make sure that the chart fits into the panel
	private double yOffsetFactor = 0.0;          // shift everything by this times yref towards the top
	private double[] xtickvalues, ytickvalues;  // values where the ticks at the axes sit
	private int nticksx, xskip;                 // to reduce the number of x ticks to a reasonable count
	private double xspacing, yspacing;          // the spacing of the ticks and labels along the axes

	// The indicator and alarm values to be drawn
	private int[][] xsIndicators;     // scaled x coordinates of indicators 
	private int[][] ysIndicators;     // scaled y coordinates of indicators
	private String[] indicatorNames;  // the names of the indicators
	private String[] indicatorTypes;  // the types of the indicators
	private Color[] indicatorColors;  // the colors of the indicators
	private int[] ysAlarms;           // y values of the alarms (always a horizontal line)
	private String[] alarmNames;      // names of the alarms

	/**
	 * Flag indicating whether grid lines will be drawn (default visibility).
	 */
	boolean drawFullGrid = true; // this and the following booleans: default visibility as these are set by the StockerChart
	/**
	 * Flag indicating whether this panel has already been initialized (default visibility).
	 */
	boolean isInitialized = false;
	/**
	 * Flag indicating if an error occured during initalization of this panel (default visibility).
	 */
	boolean initializeFailed = false;
	/**
	 * Flag indicating if an error due to missing privileges occured during initialization of this panel (default visibility).
	 */
	boolean notPrivilegedError = false;
	
	/**
	 * An image which will be used as a buffer for drawing. It is redrawn after every data change.
	 * When the panel is redrawn by {@link #paintComponent(Graphics)}, only this buffer image will be drawn.
	 * This saves a lot of time when many charts are open, compared to redrawing all the content every time.
	 */
	private BufferedImage image;

	/**
	 * Construct a new {@link ChartPanel} using the provided parameters.
	 * @param parent the parent window (a {@link StockerChart}) of this panel
	 * @param w the {@link ChartWatchItem} to be drawn on this panel. Can later be changed with {@link #setData(ChartWatchItem)}
	 * @param chartType the {@link stocker.util.EChartType} of this chart. May be null, then the default chart type
	 *        will be used. Can be changed later by using {@link #switchChartType(EChartType)}
	 */
	public ChartPanel(StockerChart parent, ChartWatchItem w, EChartType chartType) {
		this.parent = parent;
		this.chartType = chartType;
		if (w.getCandles().size() > 0) {
			setData(w);
		}
		this.setBackground(Color.WHITE);

		addComponentListener(new ComponentAdapter() { // re-calculate reference values on every resize event
			@Override
			public void componentResized(ComponentEvent e) {
				setSizeReferenceParameters();
			}
		});
	}

	/**
	 * Set this panel to show the data within the provided {@link ChartWatchItem}. 
	 * Overrides the previously set data.
	 * @param w the {@link ChartWatchItem} to be drawn on this panel
	 */
	public void setData(ChartWatchItem w) {
		// Fill the internal data structure based on the values from the ChartWatchItem
		int nCandles = w.getCandles().size();
		if (nCandles > 0) { // non-empty candle list in w
			int n;
			if (nCandles > maxCandles) { // include maximum the last maxCandles candles into the plot
				n = maxCandles;
			}
			else {
				n = nCandles;
			}
			time = new long[n];
			yopen = new double[n];
			yclose = new double[n];
			yhigh = new double[n];
			ylow = new double[n];
	
			Iterator<Candle> it = w.getCandles().iterator();
			int count = 0;
			while (count++ < nCandles - maxCandles) {
				it.next();
			}
			int i = 0;
			while (it.hasNext()) {
				Candle c = it.next();
				time[i] = c.time;
				yopen[i] = c.open;
				yclose[i] = c.close;
				yhigh[i] = c.high;
				ylow[i] = c.low;
				i++;
			}
			this.chartInterval = w.getInterval();
			this.isInitialized = true;
			// update the StockerChart's indicators now - otherwise we will run into trouble during setSizeReferenceParameters()
			Iterator<ChartIndicator> indIt = parent.getChartIndicators().iterator();
			while(indIt.hasNext()) { // recalculate all indicators
				ChartIndicator ci = indIt.next();
				ci.setCandles(w.getCandles());
				ci.calculate();
			}
			setSizeReferenceParameters(); // re-calculate reference sizes
			paintImage();                 // redraw image based on the new data
			repaint();
			
		}
		else { // candle list is empty
			this.isInitialized = false;
		}
	}
	
	/**
	 * Sets an update for the data of the latest candle. Only requires close, low and high values as the time and the 
	 * open value do not change once a candle has been created.
	 * @param close update for the latest candle's close value
	 * @param low update for the latest candle's low value
	 * @param high update for the latest candle's high value
	 */
	public void setLatestData(double close, double low, double high) {
		if (isInitialized) {
			int N = time.length-1;
			yclose[N] = close;
			ylow[N] = low;
			yhigh[N] = high;
			setSizeReferenceParameters();
			paintImage();
			repaint();
		}
	}
	
	/**
	 * Switch the chart type drawn on this panel to the provided type.
	 * @param type the {@link stocker.util.EChartType} to be drawn on this panel
	 */
	public void switchChartType(EChartType type) {
		this.chartType = type;
		calculateScaledValues();
		calculateTickValues();
		paintImage();
		repaint();
	}
	
	/**
	 * Get the chart type that is currently drawn on this panel.
	 * @return the {@link stocker.util.EChartType} that is currently set 
	 */
	public EChartType getChartType() {
		return this.chartType;
	}
	
	/**
	 * Set this panel's candle scheme to the given value
	 * @param scheme the {@link ECandleScheme} to be set
	 */
	public void setCandleScheme(ECandleScheme scheme) {
		this.candleScheme = scheme;
	}
	
	/**
	 * Set this panel's alarm color to the given value.
	 * @param c the {@link Color} to be set
	 */
	public void setAlarmColor(Color c) {
		this.alarmColor = c;
	}

	/**
	 * Calculates the positions of the ticks at both axes based on the current scaling.
	 */
	private void calculateTickValues() {
		if (isInitialized) {
			// we calculate and store the tick value for the plot separately only on create and update, not on resize
			// we don't do this in drawTicks() because then the exact tick value would strongly depend on the roundoff errors
			// (round to whole pixels), which in particular shows for large data values like bitcoin
			if (xref > 0) { // otherwise the panel has no size yet and the tick calculation would not work
				nticksx = xs.length;
				xskip = (int)((double)nticksx / (double)maxticksx) + 1;
				yspacing = (double) (yshighmax - yslowmin) / (double) (nticksy - 1); // (double)yref / (double)(nticksy-1);
	
				xtickvalues = new double[nticksx];
				ytickvalues = new double[nticksy];
	
				// range of xs (scaled) is from 0 to xref; range of ys from 0 to yref; ticks are drawn based on the close values
				int i = 0;
				for (double curx = 0.0; curx <= xref + epsilon; curx += xskip*xspacing) {
					xtickvalues[i] = time[xskip*i]; //xmin + (xmax - xmin) * curx / (double) xref;
					i++;
				}
				i = 0;
				for (double cury = yslowmin; cury <= yshighmax + epsilon; cury += yspacing) {
					ytickvalues[i] = yclosemin + (yclosemax - yclosemin) * cury / (double) yref;
					i++;
				}
			}
		}
	}

	/**
	 * Paints the buffer image for this panel. This method needs to be called for a data change to become visible
	 * (calling {@link #repaint()} or {@link #paintComponent(Graphics)} is not sufficient as this will only repaint 
	 * the buffered image).
	 * All actual painting is done here into the buffer image (except for the crosslines, which are drawn in 
	 * {@link #paintComponent(Graphics)} on top of the image, and except for the loading / error messages which might 
	 * already be required before the panel is initialized. 
	 * This method requires the panel to be fully initialized (if it is not, it will do nothing).
	 */
	public void paintImage() {
		if (isInitialized) {
			final GraphicsConfiguration gfxConf = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();
			image =  gfxConf.createCompatibleImage( getWidth(), getHeight());
			
			Graphics2D gimg = image.createGraphics();
			gimg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			
			gimg.setColor(Color.WHITE);
			gimg.setBackground(Color.WHITE);
			gimg.fillRect(0, 0, image.getWidth(), image.getHeight());
			gimg.setColor(Color.BLACK);
			
			drawCoordinateLines(gimg);
			drawTicks(gimg);

			if (chartType == EChartType.LINE) {
				drawDataLine(gimg);
			} else {
				drawDataCandles(gimg);
			}
			drawIndicators(gimg);
			drawAlarms(gimg);
		}
	}
	
	/**
	 * Calls JPanel's paintComponent(Graphics g) method and, if this panel is intialized, draws the buffered image 
	 * plus the crosslines if necessary. If the panel is not yet initalized, an appropriate message will be drawn.
	 */
	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		
		Graphics2D g2D = (Graphics2D) g.create();
		g2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		
		if (isInitialized) {
			g2D.drawImage(image, 0, 0, this);
			
			if (parent.isSelected()) { // only in active window as this is quite expensive
				drawCrosslines(g2D);
			}
		}
		else if (!isInitialized && xref > 0) { // if window is already shown (has a size) but data is not yet initialized
			g2D.setFont(new Font("Sans-Serif", Font.PLAIN, 18));
			if (initializeFailed) {
				if (notPrivilegedError) {
					g2D.drawString("Laden der Daten fehlgeschlagen", 30, 30);
					g2D.drawString("Keine Berechtigung beim Datenlieferanten?", 30, 60);
				}
				else {
					g2D.drawString("Laden der Daten fehlgeschlagen", 30, 30);
					g2D.drawString("Evtl. Datenintervall zu klein, Verbindung unterbrochen", 30, 60);
					g2D.drawString("oder falscher Datenanbieter in den Einstellungen ausgewählt", 30, 90);
				}
			}
			else {
				g2D.drawString("Lade Daten...", 30, 30);
			}
			g2D.dispose();
		}
	}

	/**
	 * Sets the reference width and height of this panel according to the size of the window, and 
	 * triggers the re-calculation of all scales values (which are based to the reference size).
	 */
	public void setSizeReferenceParameters() {
		this.xref = getSize().width;
		this.yref = getSize().height;
		
		calculateScaledValues();
		calculateTickValues();
		calculateScaledIndicators();
		calculateScaledAlarms();

		// set margins relative to panel size, but with a minimum size
		// hmargin: no less than 80 or 110 (depending on window width), no more than 160, dependent on window width
		hmargin = Math.min(Math.max((xref<decreaseFontSizeX ? 80 : 110), (int) (0.105 * this.xref)), 160); 
		// vmargin: 10% of window height, but not less than 40
		vmargin = Math.max(40,  (int) (0.10 * this.yref));
	}

	/** 
	 * Calculates the scaled values for the candle data to be painted. 
	 * Scales from the data coordinate system to the panel reference system with size (xref, yref) = this.getSize().
	 */
	public void calculateScaledValues() {
		// calculate a scaled version of the x and y values (between 0 and xref or yref)
		// assuming that the times are sorted, the others not
		if (isInitialized) {
			// define arrays for the scaled values
			int nx  = time.length;
			int ny  = yclose.length;
			xs      = new int[nx];
			ysclose = new int[ny];
			ysopen  = new int[ny];
			yshigh  = new int[ny];
			yslow   = new int[ny];
	
			// get minimum and maximum time(stamp)
			tmin = time[0];
			tmax = time[nx - 1];
	
			// get minimum and maximum for the scaled y values
			double[] ysorted = yclose.clone();
			Arrays.sort(ysorted);
			yclosemax = ysorted[ny - 1];
			yclosemin = ysorted[0];
	
			ysorted = yhigh.clone();
			Arrays.sort(ysorted);
			yhighmax = ysorted[ny - 1];
	
			ysorted = ylow.clone();
			Arrays.sort(ysorted);
			ylowmin = ysorted[0];
	
			// calculate scaled x values
			for (int i = 0; i < nx; i++) {
				xs[i] = (int) (xref * (double) (time[i] - tmin) / (double) (tmax - tmin));
			}

			// calculate scaled y values
			for (int i = 0; i < ny; i++) {
				ysclose[i] = (int) (yref * (yclose[i] - yclosemin) / (yclosemax - yclosemin));
				ysopen[i]  = (int) (yref * (yopen[i]  - yclosemin) / (yclosemax - yclosemin));
				yshigh[i]  = (int) (yref * (yhigh[i]  - yclosemin) / (yclosemax - yclosemin));
				yslow[i]   = (int) (yref * (ylow[i]   - yclosemin) / (yclosemax - yclosemin));
			}
	
			// scale factors between our chart (panel minus margin) and the panel
			// this ensures that there is enough margin around the chart
			xscale = (double) (xref - 2 * hmargin) / (double) xref;
			yscale = (double) (yref - 2 * vmargin) / (double) yref;
	
			// Additional values which account for the fact that the lowest minimum and the
			// highest maximum are smaller and larger than the smallest and the largest close value 
			// (i.e. we shrink the chart a bit more so that it fits in)
			yminmaxScale = (yclosemax - yclosemin) / (yhighmax - ylowmin);
			yslowmin  = (int) (yref * (ylowmin  - yclosemin) / (yclosemax - yclosemin));
			yshighmax = (int) (yref * (yhighmax - yclosemin) / (yclosemax - yclosemin));
	
			// set candle width dependent on panel width
			cw = (int) (9 * (xref / 1200.0));
			
			// spacing for the x and y values 
			xspacing = (double) xref / (double) (xs.length - 1);
			yspacing = (double) (yshighmax - yslowmin) / (double) (xs.length - 1); 
			
			// determine an appropriate y offset so that the plot is always entirely visible
			// first: define bounds in window coordinate frame (all y values should be within those bounds)
			double yUpperMin = (yref<decreaseFontSizeY ? 0.02*yref : 0.03*yref);
			double yLowerMax = (yref<decreaseFontSizeY ? 0.83*yref : 0.87*yref);
			
			// Determine an y offset factor which ensures that the chart is more or less centered within the panel
			// (by the scaling so far, we have only ensured that it fits in size, but not yet that it actually is in an
			// appropriate vertical position); this is done iteratively until everything fits nicely
			boolean offsetModifiedForUpper = false;
			boolean offsetModifiedForLower = false;
			yOffsetFactor = 0.0;
			// decrease the offset if the highest value is too high
			while (y(yshighmax) < yUpperMin) { 
				yOffsetFactor -= 0.01;
				offsetModifiedForUpper = true;
			}
			// increase the offset if the lowest value is too low; if we were too high previously: decrease scale too!
			while (y(yslowmin) > yLowerMax) {
				if (offsetModifiedForUpper) { // we have previously modified in the other direction, so now we should reduce scale
					yminmaxScale *= 0.98;
				}
				if (y(yslowmin) > yLowerMax) { // if it still doesn't fit after modifying scale, or if scale was not modified
					yOffsetFactor += 0.01;
					offsetModifiedForLower = true;
				}
			}
			// now check the upper bound again in case we have pushed it too high in the previous loop (decrease scale if so)
			while (y(yshighmax) < yUpperMin) {
				if (offsetModifiedForLower) { // we have previously modified in the other direction, so now we should reduce scale
					yminmaxScale *= 0.98;
				}
				yOffsetFactor -= 0.01;
			}
		}
	}

	/** 
	 * Calculates the scaled values for the indicator data to be painted. 
	 * Scales from the data coordinate system to the panel reference system with size (xref, yref) = this.getSize().
	 */
	public void calculateScaledIndicators() {
		if (isInitialized) {
			// Get the indicators and store their scaled versions
			ArrayList<ChartIndicator> indList = parent.getChartIndicators();
			Iterator<ChartIndicator> indIt = indList.iterator();
			
			// we have to count first how many indicators we need in the linear list
			int arraySize = 0;
			while (indIt.hasNext()) { 
				ChartIndicator ci = indIt.next();
				if (ci.isActive()) {
					arraySize += ci.getNrOfValues();
				}
			}
			indIt = indList.iterator(); // reset iterator after counting
			
			// define arrays for scaled indicators
			xsIndicators = new int[arraySize][];
			ysIndicators = new int[arraySize][];
			indicatorNames = new String[arraySize];
			indicatorTypes = new String[arraySize];
			indicatorColors = new Color[arraySize];
			
			// Go through all the indicators and write their scaled versions into the arrays
			int o = 0; // offset; > 0 if there are indicators with multiple values
			int i = 0;
			while (indIt.hasNext()) { // for each active indicator
				ChartIndicator ci = indIt.next();
				if (ci.isActive()) {
					for (int j = 0; j < ci.getNrOfValues(); j++) {
						int nDisplayValues = ysclose.length; // ci.getValues(j).size();
						xsIndicators[i + o + j]    = new int[nDisplayValues];
						ysIndicators[i + o + j]    = new int[nDisplayValues];
						indicatorNames[i + o + j]  = ci.toString();
						indicatorTypes[i + o + j]  = ci.getType();
						indicatorColors[i + o + j] = ci.getColor().toColor();
						Iterator<Long>   timeIt    = ci.getTimeIterator();
						Iterator<Double> valueIt   = ci.getValueIterator(j);
						int nValues = ci.getAllValues().get(j).size();
						int count = 0;
						while (count < nValues - maxCandles) { // skip those which are before the plot range
							valueIt.next();
							timeIt.next();
							count++;
						}
						for (int k = 0; valueIt.hasNext(); k++) { // walk through timesteps of this indicator value and assign scaled values
							xsIndicators[i + o + j][k] = (int) (xref * (double) (timeIt.next() - tmin) / (double) (tmax - tmin));
							ysIndicators[i + o + j][k] = (int) (yref * (valueIt.next() - yclosemin) / (yclosemax - yclosemin));
						}
					}
					i++;
					o += ci.getNrOfValues() - 1; // offset += 0 if indicator has 1 value, += N-1 if N values
				}
			}
		}
	}

	/** 
	 * Calculates the scaled values for the alarms to be painted.
	 * Scales from the data coordinate system to the panel reference system with size (xref, yref) = this.getSize().
	 */
	public void calculateScaledAlarms() {
		// Get the alarms and store their scaled versions
		ArrayList<ChartAlarm> alarmList = parent.getChartAlarms();
		ArrayList<Boolean> alarmActivity = parent.getChartAlarmActivities();
		Iterator<ChartAlarm> alarmIt = alarmList.iterator();
		Iterator<Boolean> alarmActivityIt = alarmActivity.iterator();

		// first, count the active alarms
		int nInd = 0; 
		while (alarmActivityIt.hasNext()) {
			if (alarmActivityIt.next() == true) {
				nInd += 1;
			}
		}

		// then, go through the alarms and write their scaled versions
		ysAlarms = new int[nInd];
		alarmNames = new String[nInd];
		alarmActivityIt = alarmActivity.iterator();
		int i = 0;
		while (alarmIt.hasNext()) {
			ChartAlarm ca = alarmIt.next();
			if (alarmActivityIt.next() == true) { // for each active indicator
				alarmNames[i] = ca.toString();
				ysAlarms[i] = (int) (yref * (ca.getValue() - yclosemin) / (yclosemax - yclosemin));
				i++;
			}
		}
	}

	/** 
	 * Scale an x value to account for the margins of the reference space.
	 * @param x the x coordinate to be scaled
	 */
	private int x(int x) {
		return (int) (Math.round(hmargin + x * xscale));
	}

	/** 
	 * Scale an y value to account for the margins of the reference space and to reflect that the y axis of the 
	 * plot is inverse to the y axis of the panel.
	 * @param y the y coordinate to be scaled
	 */
	private int y(int y) {
		return (int) (Math.round(yref - vmargin - y * yscale * yminmaxScale - yOffsetFactor * yref));
	}
	
	/**
	 * Get data coordinate values from panel coordinate values (inverse coordinate lookup).
	 * @param x the queried x coordinate of the panel
	 * @param y the queried y coordinate of the panel
	 * @return array containing the x and y coordinates in the data coordinate frame
	 */
	public double[] inverseCoordinateLookup(int x, int y) {
		long closestx = 0L;
		double ydata = 0.0;
		
		if (isInitialized) {
			// Step 1: convert from window frame to chart frame (origin where the chart axes meet)
			double xchart = (x - hmargin) / xscale; // from 0 (= first candle) to xref (= last candle)
			double ychart = -(y - yref + vmargin + yOffsetFactor * yref) / (yscale * yminmaxScale);
			
			// Step 2: convert from pixel-based chart frame to data frame
			ydata = yclosemin + ychart / yref * (yclosemax - yclosemin);
			
			// don't return the exact (interpolated) x value, but the closest data time
			double offset = 1.1 * cw/2.0; // return the next candle time already half a candle width before it
			int index = (int) ( (xchart+offset) / xref * (time.length-1));
			index = (index < 0 ? 0 : index);
			index = (index >= time.length ? time.length-1 : index);
			closestx = time[index];
		}
				
		return new double[] { closestx, ydata };
	}

	/**
	 * Get the last closing price from the data drawn on this panel.
	 * @return the close value of the last candle associated with this panel
	 */
	public double getLastPrice() {
		if (yclose != null && yclose.length > 0) {
			return yclose[yclose.length - 1];
		}
		else { // if there is not data, e.g. because data with 0 candles have been pulled and set, return 0.0
			return 0.0;
		}
	}

	/**
	 * Draw the x and y coordinate axes (the lines only).
	 * @param g2Dorig the graphics object
	 */
	private void drawCoordinateLines(Graphics2D g2Dorig) {
		Graphics2D g2D = (Graphics2D) g2Dorig.create();
		
		BasicStroke stroke = new BasicStroke(3.0f);
		g2D.setStroke(stroke);
		g2D.drawLine(x(-cw), y(yslowmin), x(xref + cw), y(yslowmin));
		g2D.drawLine(x(-cw), y(yslowmin), x(-cw), y(yshighmax));
		g2D.drawLine(x(xref + cw), y(yshighmax), x(-cw), y(yshighmax));
		g2D.drawLine(x(xref + cw), y(yslowmin), x(xref + cw), y(yshighmax));
		
		g2D.dispose();
	}

	/**
	 * Draw the ticks and labels along both coordinate axes.
	 * @param g2Dorig the graphics object
	 */
	private void drawTicks(Graphics2D g2Dorig) {
		Graphics2D g2D = (Graphics2D) g2Dorig.create();

		BasicStroke stroke = new BasicStroke(3.0f);
		BasicStroke thinstroke = new BasicStroke(1.0f, 0, 0, 1.0f, new float[] { 10 }, 0);
		g2D.setStroke(stroke);
		g2D.setFont(new Font("Sans-Serif", Font.PLAIN, 20));

		AffineTransform rotTransform = new AffineTransform(); // transform to rotate the date/time labels
		Font font;
		if (xref < decreaseFontSizeX || yref < decreaseFontSizeY) { // rotation angle depends on panel size
			rotTransform.rotate(Math.toRadians(35));
			font = new Font("Sans-Serif", Font.PLAIN, 14);
		} else {
			rotTransform.rotate(Math.toRadians(30));
			font = new Font("Sans-Serif", Font.PLAIN, 20);
		}
		Font rotatedFont = font.deriveFont(rotTransform);
		g2D.setFont(rotatedFont);

		// horizontal (bottom) axis
		int i = 0;
		for (double curx = 0.0; curx <= xref + epsilon; curx += xskip*xspacing) {
			// we count accurately in double, but plot the rounded values; otherwise the error accumulates too much
			int rcurx = (int) Math.round(curx);
			
			// draw tick
			g2D.drawLine(x(rcurx), y(yslowmin), x(rcurx), y(yslowmin - ticklength));
			
			// create date/time label  
			double tickValue = xtickvalues[i];
			ZonedDateTime zdt = ZonedDateTime.ofInstant(Instant.ofEpochSecond((long) tickValue), ZoneId.systemDefault());
			StringBuilder ts = new StringBuilder();
			// use complete date and time for long ranges, date w/o year + time for medium, and only time for short ranges
			if (this.chartInterval == EChartInterval.I1MONTH || this.chartInterval == EChartInterval.I1WEEK) {
				ts.append(zdt.toLocalDateTime().format(parent.getDtfDate()));
			} else if (this.chartInterval == EChartInterval.I1DAY || this.chartInterval == EChartInterval.I1H
					|| this.chartInterval == EChartInterval.I30MIN) {
				ts.append(zdt.toLocalDateTime().format(parent.getDtfDate()).substring(0, 6));
				ts.append(" ");
				ts.append(zdt.toLocalDateTime().format(parent.getDtfTime()));
			}
			else {
				ts.append(zdt.toLocalDateTime().format(parent.getDtfTime()));
			}

			// draw the date/time label under the axis
			g2D.drawString(ts.toString(), x(rcurx - 15), 
					y(yslowmin - ticklength - ((xref<decreaseFontSizeX || yref<decreaseFontSizeY) ? 15 : 20)));

			// draw horizontal gridlines if requested
			if (drawFullGrid) {
				g2D.setStroke(thinstroke);
				g2D.drawLine(x(rcurx), y(yslowmin), x(rcurx), y(yshighmax));
				g2D.setStroke(stroke);
			}
			i++;
		}

		// reset font before proceeding to the vertical axis labels (no rotation any more)
		if (xref < decreaseFontSizeX || yref < decreaseFontSizeY) {
			g2D.setFont(new Font("Sans-Serif", Font.PLAIN, 14));
		} else {
			g2D.setFont(new Font("Sans-Serif", Font.PLAIN, 20));
		}

		// vertical axes (left and right)
		i = 0;
		for (double cury = yslowmin; cury <= yshighmax + epsilon; cury += yspacing) {
			int rcury = (int) Math.round(cury);
			
			// draw ticks
			g2D.drawLine(x(-cw - ticklength), y(rcury), x(-cw), y(rcury));
			g2D.drawLine(x(xref + cw), y(rcury), x(xref + cw + ticklength), y(rcury));
			
			// create label
			double tickValue = ytickvalues[i]; 
			String ts = String.format("%.2f", tickValue);
			
			// draw label on both vertical axes (left and right)
			g2D.drawString(ts, x(-cw - ticklength - ts.length() * ((xref<decreaseFontSizeX || yref<decreaseFontSizeY) ? 11 : 14)), 
					y(rcury - 7)); // left labels
			g2D.drawString(ts, x(xref + cw + ticklength + 5), y(rcury - 7));  // right labels

			// draw horizontal gridlines if requested
			if (drawFullGrid) {
				g2D.setStroke(thinstroke);
				g2D.drawLine(x(-cw), y(rcury), x(xref + cw), y(rcury));
				g2D.setStroke(stroke);
			}
			i++;
		}
		g2D.dispose(); // it was only a copy

	}

	/**
	 * Draw the data as a line (for {@link EChartType}.LINE).
	 * @param g2Dorig the graphics object
	 */
	private void drawDataLine(Graphics2D g2Dorig) {
		Graphics2D g2D = (Graphics2D) g2Dorig.create();
		g2D.setStroke(new BasicStroke(2.0f));
		
		int i = 0;
		int rcurx = 0;
		int roldx = 0;
		for (double curx = 0.0; curx <= xref + epsilon; curx += xspacing) {
			roldx = rcurx;
			rcurx = (int) Math.round(curx);
			if (i > 0) {
				g2D.drawLine(x(roldx), y(ysclose[i - 1]), x(rcurx), y(ysclose[i]));
			}
			i++;
		}
		g2D.dispose();
	}

	/**
	 * Draw the data as candles (for EChartType.CANDLES).
	 * @param g2Dorig the graphics object
	 */
	private void drawDataCandles(Graphics2D g2Dorig) {
		Graphics2D g2D = (Graphics2D) g2Dorig.create();

		BasicStroke strokeWide = new BasicStroke(2.0f);
		BasicStroke strokeNarrow = new BasicStroke(1.0f);
		int i = 0;
		for (double curx = 0.0; curx <= xref + epsilon; curx += xspacing) {
			int rcurx = (int) Math.round(curx);
			
			// set colors based on color scheme and whether the candle represents loss or gain
			if (ysclose[i] >= ysopen[i]) {
				if (candleScheme == ECandleScheme.REDGREEN) {
					g2D.setColor(new Color(0, 170, 30));
				} else {
					g2D.setColor(Color.WHITE);
				}
			} else {
				if (candleScheme == ECandleScheme.REDGREEN) {
					g2D.setColor(new Color(170, 0, 30));
				} else {
					g2D.setColor(Color.BLACK);
				}
			}

			// draw candle body
			g2D.setStroke(strokeNarrow);
			g2D.fillRect(x(rcurx) - cw / 2, y(Math.max(ysclose[i], ysopen[i])), cw,
					Math.abs(y(ysclose[i]) - y(ysopen[i])));
			// for black and white: draw a contour around the candle body
			if (candleScheme == ECandleScheme.BLACKWHITE) {
				g2D.setColor(Color.BLACK);
				g2D.setStroke(strokeWide);
				g2D.drawRect(x(rcurx) - cw / 2, y(Math.max(ysclose[i], ysopen[i])), cw,
						Math.abs(y(ysclose[i]) - y(ysopen[i])));
			}

			// draw the low and high lines below and above the candle
			g2D.setStroke(strokeWide);
			g2D.drawLine(x(rcurx), y(yslow[i]), x(rcurx), y(Math.min(ysclose[i], ysopen[i])));
			g2D.drawLine(x(rcurx), y(Math.max(ysclose[i], ysopen[i])), x(rcurx), y(yshigh[i]));
			i++;
		}
		g2D.dispose();
	}

	/**
	 * Draw the indicators as line(s). If they represent bands, shade the area in between.
	 * @param g2Dorig the graphics object
	 */
	private void drawIndicators(Graphics2D g2Dorig) {
		Graphics2D g2D = (Graphics2D) g2Dorig.create();
		final int alphaLines = 255; // the alpha values for the indicator colors (set below)
		final int alphaArea = 30;
		
		// set font size for the indicator's label dependent on panel size
		if (xref < decreaseFontSizeX || yref < decreaseFontSizeY) {
			g2D.setFont(new Font("Sans-Serif", Font.PLAIN, 14));
		}
		else {
			g2D.setFont(new Font("Sans-Serif", Font.PLAIN, 20));
		}
		BasicStroke stroke = new BasicStroke(1.5f);
		g2D.setStroke(stroke);

		for (int i = 0; i < ysIndicators.length; i++) { // iterate through indicators
			Color colorLines = new Color(indicatorColors[i].getRed(), indicatorColors[i].getGreen(), 
					indicatorColors[i].getBlue(), alphaLines);
			Color colorArea = new Color(indicatorColors[i].getRed(), indicatorColors[i].getGreen(), 
					indicatorColors[i].getBlue(), alphaArea);
			
			int rcurx = 0;
			int roldx = 0;
			int k = 0;
			// Draw this indicator
			for (double curx = 0.0; curx <= xref + epsilon; curx += xspacing) {
				roldx = rcurx;
				rcurx = (int) Math.round(curx);
				if (k > 0) {
					g2D.setColor(colorLines);
					g2D.drawLine(x(roldx), y(ysIndicators[i][k - 1]), x(rcurx), y(ysIndicators[i][k]));
					if (i > 1 && indicatorTypes[i].equals("BollingerBands") && indicatorTypes[i-2].equals("BollingerBands") 
							&& indicatorNames[i].equals(indicatorNames[i-2])) { 
						// for Bollinger bands: Draw shaded area between lines
						g2D.setColor(colorArea);
						g2D.fillPolygon(new int[] {x(roldx), x(roldx), x(rcurx), x(rcurx)}, 
								new int[] {y(ysIndicators[i][k-1]), y(ysIndicators[i-2][k-1]), y(ysIndicators[i-2][k]), y(ysIndicators[i][k])}, 4);
						g2D.setColor(colorLines);
					}
					// draw label on first line per indicator
					if (k == 3 && (i == 0 || !indicatorNames[i].equals(indicatorNames[i-1]))) {
						g2D.drawString(indicatorNames[i], x(rcurx), y(ysIndicators[i][k] + 20));
					}
				}
				k++;
			}
		}
		g2D.dispose();
	}

	/**
	 * Draw the alarms as a line.
	 * @param g2Dorig the graphics object
	 */
	private void drawAlarms(Graphics2D g2Dorig) {
		Graphics2D g2D = (Graphics2D) g2Dorig.create();
		g2D.setColor(alarmColor);
		g2D.setFont(new Font("Sans-Serif", Font.PLAIN, 20));
		BasicStroke stroke = new BasicStroke(2.0f);
		g2D.setStroke(stroke);

		for (int i = 0; i < ysAlarms.length; i++) {
			g2D.drawLine(x(xs[0]), y(ysAlarms[i]), x(xs[xs.length - 1]), y(ysAlarms[i]));
			g2D.drawString(alarmNames[i], x(xs[0] + 10), y(ysAlarms[i] + 10));
		}
		g2D.dispose();
	}

	/**
	 * Draw the crosslines at the current mouse pointer position.
	 * @param g2Dorig the graphics object
	 */
	private void drawCrosslines(Graphics2D g2Dorig) {
		Graphics2D g2D = (Graphics2D) g2Dorig.create();
		if (mouseposx != -1) {
			BasicStroke strokeDashed = new BasicStroke(1.0f, 0, 0, 1.0f, new float[] { 5.0f }, 0.0f);
			g2D.setStroke(strokeDashed);

			g2D.drawLine(mouseposx, 0, mouseposx, yref);
			g2D.drawLine(0, mouseposy, xref, mouseposy);
		}
		g2D.dispose();
	}
	
	/** 
	 * Report the mouse pointer position within this panel and trigger repainting of the crosslines at the
	 * new position. The mouse position should be updated any time the mouse pointer has moved, as long as it 
	 * is within the chart.
	 * @param x the x position of the mouse pointer
	 * @param y the y position of the mouse pointer
	 */
	public void setMousePos(int x, int y) {
		this.mouseposx = x;
		this.mouseposy = y;
		// try to repaint only the surrounding of the crosslines (although this doesn't really help,
		// because the size and shape of the regions seems to cause a complete redraw)
		repaint(mouseposx - 50, 0, 100, yref); 
		repaint(0, mouseposy - 50, xref, 100); 
	}

}
