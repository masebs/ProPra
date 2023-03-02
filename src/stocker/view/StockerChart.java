package stocker.view;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.Iterator;

import javax.swing.AbstractButton;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JInternalFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JSeparator;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.border.BevelBorder;
import javax.swing.event.InternalFrameAdapter;
import javax.swing.event.InternalFrameEvent;

import stocker.control.StockerControl;
import stocker.control.StockerDataManager;
import stocker.dialog.StockerAlarmDialog;
import stocker.dialog.StockerIndicatorDialog;
import stocker.model.ChartAlarm;
import stocker.model.ChartIndicator;
import stocker.model.ChartWatchItem;
import stocker.util.Candle;
import stocker.util.EChartInterval;
import stocker.util.EChartType;
import stocker.util.StockerDataManagerException;

/**
 * An internal frame showing data from a {@link ChartWatchItem} in a chart.
 * 
 * @author Marc S. Schneider
 */
public class StockerChart extends JInternalFrame implements IStockerDataListener, ActionListener {

	private static final long serialVersionUID = 4474624613394916525L;

	// basics and window content
	private StockerFrame parent;
	private StockerControl control;
	private ChartWatchItem w;
	private ChartPanel panel;
	private JPanel controlPanel;
	private JPanel statusBar;
	private JLabel statusMousePos;
	private JLabel statusLastPrice;
	private JMenu menuIndicators;
	private JMenu menuAlarms;
	private final int flowLayoutGaps = 5;

	// some status information
	private boolean isInitialized;
	private long lastPushUpdate;
	private Dimension previousSize; // in order to restore original size after maximizing
	private Point previousLocation; // in order to restore original location after maximizing
	private DateTimeFormatter dtfDate, dtfTime; // formatter for date and time, e.g. provided to the ChartPanel 
	
	// List containing the indicators and alarms defined in this StockerChart (and some related stuff)
	private ArrayList<ChartIndicator> chartIndicators;
	private ArrayList<JCheckBoxMenuItem> indicatorMenuItems;
	private ArrayList<ChartAlarm> chartAlarms;
	private ArrayList<Boolean> chartAlarmsActiveFlag;
	private ArrayList<JCheckBoxMenuItem> alarmMenuItems;
	
	/**
	 * Flag which indicates whether an error due to missing privileges occured while pulling data. Can be used to 
	 * prevent further attempts if true.
	 */
	boolean notPrivilegedError = false; // default visibility

	/**
	 * Constructs a new StockerChart.
	 * @param parent the parent window (a {@link StockerFrame})
	 * @param w the {@link ChartWatchItem} that this chart refers to
	 * @param chartType the type of chart to be created 
	 * @param windowName the title of this chart window
	 * @param size the dimension that this chart window should initially have
	 * @param control the {@link StockerControl} which controls this chart
	 */
	public StockerChart(StockerFrame parent, ChartWatchItem w, EChartType chartType, String windowName,
			Dimension size, boolean gridlinesShown, StockerControl control) {
		super(windowName, true, true, true, true);
		this.parent = parent;
		this.w = w;
		this.control = control;
		this.isInitialized = false;
		this.lastPushUpdate = Instant.now().getEpochSecond();
		this.chartIndicators = new ArrayList<ChartIndicator>(5);
		this.indicatorMenuItems = new ArrayList<JCheckBoxMenuItem>(5);
		this.chartAlarms = new ArrayList<ChartAlarm>(5);
		this.chartAlarmsActiveFlag = new ArrayList<Boolean>(5);
		this.alarmMenuItems = new ArrayList<JCheckBoxMenuItem>(5);
		
		// register at the data manager
		control.addChartListenerToDataManager(this);
		if (control.isPushInitialized()) {
			control.addSymbolToPush(w.getKey());
		}
		
		if (size != null) {
			setPreferredSize(size);
			setSize(size);
		}
		else {
			setPreferredSize(new Dimension(1000, 700));
		}
		previousSize = size;
		previousLocation = getLocation();
		setMinimumSize(control.getPropertyMinimumSize());
		this.setLayout(new BoxLayout(this.getContentPane(), BoxLayout.Y_AXIS));

		controlPanel = new JPanel();
		controlPanel.setLayout(new FlowLayout(FlowLayout.LEADING, flowLayoutGaps, flowLayoutGaps));

		this.dtfDate = new DateTimeFormatterBuilder().padNext(2, '0').appendValue(ChronoField.DAY_OF_MONTH)
				.appendLiteral('.').padNext(2, '0').appendValue(ChronoField.MONTH_OF_YEAR).appendLiteral(".")
				.appendValue(ChronoField.YEAR).toFormatter();
		this.dtfTime = new DateTimeFormatterBuilder().padNext(2, '0').appendValue(ChronoField.HOUR_OF_DAY)
				.appendLiteral(':').padNext(2, '0').appendValue(ChronoField.MINUTE_OF_HOUR).toFormatter();

		// Initialize ChartPanel
		// data will be initialized in initializeData, so no candles in w yet!
		panel = new ChartPanel(this, w, chartType);
		updateChartProperties(); // get chart-relevant properties from the props
		panel.drawFullGrid = gridlinesShown;
		add(panel);

		// Create other window content
		createMenu(chartType);
		
		statusBar = new JPanel();
		statusBar.setBorder(new BevelBorder(BevelBorder.LOWERED));
		statusBar.setPreferredSize(new Dimension(getWidth(), 25));
		statusBar.setLayout(new BorderLayout());
		JLabel statusSymbol = new JLabel(" " + w.getKey());
		Font statusFont = statusSymbol.getFont().deriveFont(14.0f);
		statusSymbol.setFont(statusFont);
		statusSymbol.setSize(new Dimension(getWidth() / 3, 20));
		statusSymbol.setHorizontalAlignment(SwingConstants.LEFT);
		statusBar.add(statusSymbol, BorderLayout.WEST);
		if (isInitialized) {
			statusLastPrice = new JLabel(String.format("Letzter Kurs: %.2f", panel.getLastPrice()));
		}
		else {
			statusLastPrice = new JLabel("Lade Daten...");
		}
		statusLastPrice.setFont(statusFont);
		statusLastPrice.setSize(new Dimension(getWidth() / 3, 20));
		statusLastPrice.setHorizontalAlignment(SwingConstants.CENTER);
		statusBar.add(statusLastPrice, BorderLayout.CENTER);
		statusMousePos = new JLabel("Cursor: ");
		statusMousePos.setFont(statusFont);
		statusMousePos.setSize(new Dimension(getWidth() / 3, 20));
		statusMousePos.setHorizontalAlignment(SwingConstants.RIGHT);
		statusBar.add(statusMousePos, BorderLayout.EAST);
		add(statusBar);

		// rescale and repaint panel when resized
		this.addComponentListener(new ComponentAdapter() { 
			@Override
			public void componentResized(ComponentEvent e) {
				adaptChartPanelSize();
				panel.setSizeReferenceParameters();
				panel.paintImage();
				if (!isMaximum) { // save values for restoring after maximizing
					previousSize = getSize(); 
					previousLocation = getLocation();
				}
			}
		});
		
		// property change listener, which rescales if maximized state has changed
		// (really crappy behaviour of Swing, so this is quite messy)
		this.addPropertyChangeListener(new PropertyChangeListener() {
			@Override
			public void propertyChange(PropertyChangeEvent e) {
				if (JInternalFrame.IS_MAXIMUM_PROPERTY.equals(e.getPropertyName())) {
					if ((boolean)e.getNewValue() == true) { // has just been maximized
						statusBar.setSize(new Dimension(getWidth(), 25)); // needs a fixed size when maximized, no idea why
					}
					else { // when no longer maximized: Set preferred size again
						statusBar.setPreferredSize(new Dimension(getWidth(), 25));
						setPreferredSize(previousSize);
						setSize(previousSize);
						setLocation(previousLocation);
					}
					new Thread() { // need to to the rescaling with some delay because of the fancy enlarge / shrink animations
						public void run() {
							try {
								Thread.sleep(500);
							} catch (InterruptedException e) { }
							SwingUtilities.invokeLater(new Runnable() {
								public void run() {
									statusBar.setSize(new Dimension(getWidth(), 25)); // needs a fixed size when maximized, no idea why
									panel.setSize(getWidth(), getHeight()-83);
									adaptChartPanelSize();
									panel.setSizeReferenceParameters();
									panel.paintImage();
									repaint();
								};
							});

						};
					}.start();
				}
			}
		});
		
		// On closing: remove ourselves from the data manager's list of listeners and unsubscribe from push
		this.addInternalFrameListener(new InternalFrameAdapter() {
			@Override
			public void internalFrameClosing(InternalFrameEvent e) {
				control.removeChartListenerFromDataManager(StockerChart.this);
				control.removeSymbolFromPush(w.getKey());
				control.unregisterAlarmListener(StockerChart.this, w.getKey());
				parent.onChildWindowClosing(StockerChart.this.getTitle());
			}
		});

		// when mouse has left panel: reset mouse position in panel so crosslines are not drawn 
		// when pointer is outside of panel
		panel.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseExited(MouseEvent e) {
				panel.setMousePos(-1, -1);
				super.mouseExited(e);
			}
		});

		// read and save the current mouse position whenever the mouse moves within the panel
		panel.addMouseMotionListener(new MouseMotionAdapter() {
			@Override
			public void mouseMoved(MouseEvent e) {
				onMouseMove(e.getPoint());
			}
		});
		
		// Register ourselves as listener at the AlarmManager (for this particular symbol = key)
		ArrayList<ChartAlarm> al = control.registerAlarmListener(this, w.getKey());
		for (ChartAlarm a : al) {
			addChartAlarm(a, false);
		}
		
		pack();
	}
	
	/**
	 * Initialize the data within this chart, i.e. pull the historic data from the data provider.
	 */
	public void initializeData() {
		// Use a SwingWorker to pull the data in background, then complete the setup (in done()) as soon as 
		// the data is available
		(new SwingWorker<ChartWatchItem, Object>() {
			@Override
			protected ChartWatchItem doInBackground() throws Exception {
				try {
					control.getPlotData(w);
					StockerChart.this.isInitialized = true;
					panel.initializeFailed = false;
					panel.notPrivilegedError = false;
				} catch (StockerDataManagerException e) {
					if (e.getMessage().contains("403")) {
						StockerChart.this.notPrivilegedError = true;
						panel.notPrivilegedError = true;
						StockerChart.this.notPrivilegedError = true;
					}
					panel.initializeFailed = true;
					StockerChart.this.isInitialized = false;
				}
				return w;
			}

			@Override
			protected void done() {
				if (StockerChart.this.isInitialized) {
					panel.setData(w);
					statusLastPrice.setText(String.format("Letzter Kurs: %.2f", panel.getLastPrice()));
				}
			}
		}).execute();
	}

	/**
	 * Check whether this {@link StockerChart} has already been initialized (i.e. populated with data).
	 * @return true if initialized, false otherwise
	 */
	public boolean isInitialized() {
		return isInitialized;
	}
	
	/**
	 * Reset all data in this {@link StockerChart} (e.g. because the data interval has changed and a new initialization 
	 * with data pull is required).
	 */
	public void resetData() {
		w = new ChartWatchItem(w.getKey(), w.getName());
		panel.setData(w);
		isInitialized = false;
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public void setSize(Dimension d) { // overriden in order to be able to resize on increased mininum window size (in the properties)
		super.setSize(d);
		if (panel != null) { // if already initialized
			adaptChartPanelSize();
			panel.setSizeReferenceParameters();
			repaint();
		}
	}

	/**
	 * Adapts the size of the {@link ChartPanel} in case the window size has changed, so that the proportions of the 
	 * panel are right (and the control elements stay visible).
	 */
	protected void adaptChartPanelSize() {
		Component[] controlComps = controlPanel.getComponents();
		int controlCompWidth = 0;
		for (Component c : controlComps) {
			controlCompWidth += c.getSize().width;
		}
		controlCompWidth += (controlComps.length + 1) * flowLayoutGaps + 7; // add horizontal distances and padding left
																			// and right
		int panelHeight = (getSize().width <= controlCompWidth ? getSize().height - 130 : getSize().height - 55);
		int panelWidth = (int) (0.95 * getSize().width);
		panel.setPreferredSize(new Dimension(panelWidth, panelHeight));
	}

	/**
	 * Trigger asynchronous data update (run in background), e.g. after switching intervals or plot types.
	 */
	public void updateData() {
		panel.isInitialized = false;
		panel.repaint(); // paint the "loading..." message
		this.isInitialized = false;
		
		// trigger asynchronous data pull in a separate thread
		new Thread() {
			@Override
			public void run() {
				try {
					control.getPlotData(w);
				} catch (StockerDataManagerException e) {
					panel.initializeFailed = true;
					panel.repaint();
					return;
				}
				panel.initializeFailed = false;
				panel.setData(w);
				statusLastPrice.setText(String.format("Letzter Kurs: %.2f", panel.getLastPrice()));
				StockerChart.this.isInitialized = true;
			}
		}.start();
	}

	/**
	 * Called by {@link StockerDataManager} whenever a push update is available. Updates data for the watchlist
	 * as well as for charts and their indicators.
	 */
	@Override
	public void onPushUpdate(String key, long time, double price) {
		// Only if it's our key, and only if the last update is more than 5 seconds ago (in order to avoid too much repainting)
		if (key.equals(w.getKey()) && Instant.now().getEpochSecond() - lastPushUpdate > 5) {
			lastPushUpdate = Instant.now().getEpochSecond();
			
			if (isInitialized && w.getCandles().size() > 0) {
				// A new candle is created and filled step by step with the pushed data until it's "full"
				boolean newCandle = false;
				statusLastPrice.setText(String.format("Letzter Kurs: %.2f", price));
				Candle last = w.getCandles().getLast();
				
				// start new candle if the old one is "full"
				if (time >= last.time + w.getInterval().inSeconds()) { 
					newCandle = true;
					// start new candle
					double pc = last.close;
					w.appendValues(time, pc, pc, pc, pc);
					last = w.getCandles().getLast();
					last.open = price;
					
					// Update indicators for the completed candle
					Iterator<ChartIndicator> indIt = StockerChart.this.chartIndicators.iterator();
					while (indIt.hasNext()) { // update indicators 
						ChartIndicator ci = indIt.next();
						ci.setCandles(w.getCandles());
						ci.calculate();
					}
				}
				
				last.close = price;
				if (price < last.low) {
					last.low = price;
				}
				else if (price > last.high) {
					last.high = price;
				}
					
				if (newCandle) {
					panel.setData(w); // set data completely if a new candle was added 
				}
				else {
					panel.setLatestData(last.close, last.low, last.high); // only modify the last data in the panel, without rescaling etc.
				}
			}
		}
	}

	/**
	 * Mouse Listener method, required to tell the {@link ChartPanel} of this StockerChart the current
	 * mouse position, so that the crosslines can be drawn.
	 * @param p the current mouse position
	 */
	public void onMouseMove(Point p) {
		double[] pos = panel.inverseCoordinateLookup(p.x, p.y);

		if (pos[0] == 0.0) {
			statusMousePos.setText(new StringBuilder().append("Cursor: ").append("   ").toString());
		}
		else {
			ZonedDateTime zdt = ZonedDateTime.ofInstant(Instant.ofEpochSecond((long) Math.round(pos[0])),
					ZoneId.systemDefault());
			statusMousePos.setText(new StringBuilder().append("Cursor: ").append(dtfDate.format(zdt)).append(" ")
					.append(dtfTime.format(zdt)).append("  ").append(String.format("%.2f", pos[1])).append(" ").toString());
		}
		panel.setMousePos(p.x, p.y);
	}
	
	/**
	 * Action listener method, reacting to all clicks on buttons and menu items.
	 * @param e the action event describing the action to be processed
	 */
	@Override
	public void actionPerformed(ActionEvent e) {
		String btntext = ((AbstractButton) e.getSource()).getText();
		String titlenew = "";
		switch (btntext) {
		case "Kerzen":
			panel.switchChartType(EChartType.CANDLE);
			titlenew = parent.getUniqueTitle(this.getTitle().replace("Linie", "Kerzen"));
			parent.windowRenamed(getTitle(), titlenew);
			this.setTitle(titlenew);
			break;
		case "Linie":
			panel.switchChartType(EChartType.LINE);
			titlenew = parent.getUniqueTitle(this.getTitle().replace("Kerzen", "Linie"));
			parent.windowRenamed(getTitle(), titlenew);
			this.setTitle(titlenew);
			break;
		case "1 min":
		case "5 min":
		case "15 min":
		case "30 min":
		case "1 h":
			w.setInterval(EChartInterval.valueOf("I" + btntext.replace(" ", "").toUpperCase()));
			titlenew = parent.getUniqueTitle(
					this.getTitle().substring(0, this.getTitle().indexOf("I = ")) + "I = " + w.getInterval().toString());
			parent.windowRenamed(getTitle(), titlenew);
			this.setTitle(titlenew);
			updateData();
			break;
		case "1 Tag":
			w.setInterval(EChartInterval.valueOf("I1DAY"));
			titlenew = parent.getUniqueTitle(
					this.getTitle().substring(0, this.getTitle().indexOf("I = ")) + "I = " + w.getInterval().toString());
			parent.windowRenamed(getTitle(), titlenew);
			this.setTitle(titlenew);
			updateData();
			break;
		case "1 Woche":
			w.setInterval(EChartInterval.valueOf("I1WEEK"));
			titlenew = parent.getUniqueTitle(
					this.getTitle().substring(0, this.getTitle().indexOf("I = ")) + "I = " + w.getInterval().toString());
			parent.windowRenamed(getTitle(), titlenew);
			this.setTitle(titlenew);
			updateData();
			break;
		case "1 Monat":
			w.setInterval(EChartInterval.valueOf("I1MONTH"));
			titlenew = parent.getUniqueTitle(
					this.getTitle().substring(0, this.getTitle().indexOf("I = ")) + "I = " + w.getInterval().toString());
			parent.windowRenamed(getTitle(), titlenew);
			this.setTitle(titlenew);
			updateData();
			break;
		case "Indikatoren verwalten...":
			new StockerIndicatorDialog(this, control.getPropertyDefaultIndicatorColor());
			break;
		case "Alarme verwalten...":
			new StockerAlarmDialog(this);
			break;
		case "Gitterlinien":
			panel.drawFullGrid = !panel.drawFullGrid;
			panel.paintImage();
			panel.repaint();
			break;
		}
	}

	/** 
	 * Get a {@link DateTimeFormatter} object which is configured for suitable formatting of dates for plotting.
	 * @return a {@link DateTimeFormatter} object to be used for formatting 
	 */
	public DateTimeFormatter getDtfDate() {
		return this.dtfDate;
	}

	/** 
	 * Get a {@link DateTimeFormatter} object which is configured for suitable formatting of times for plotting.
	 * @return a {@link DateTimeFormatter} object to be used for formatting 
	 */
	public DateTimeFormatter getDtfTime() {
		return this.dtfTime;
	}

	/** 
	 * Get the {@link ChartWatchItem} that this chart is built on.
	 * @return
	 */
	public ChartWatchItem getWatchItem() {
		return this.w;
	}

	/**
	 * Get this chart's {@link stocker.util.EChartType}.
	 * @return this chart's {@link stocker.util.EChartType}
	 */
	public EChartType getChartType() {
		return panel.getChartType();
	}
	
	/**
	 * Get information on whether the gridlines are currently shown in this chart.
	 * @return true if gridlines are shown, false otherwise
	 */
	public boolean getGridlineState() {
		return panel.drawFullGrid;
	}

	//////////////
	// Indicator management
	//////////////
	/**
	 * Adds the provided {@link ChartIndicator} to this chart.
	 * @param ci the {@link ChartIndicator} to be added
	 */
	public void addChartIndicator(ChartIndicator ci) {
		// first, check if it's already available (if it is, don't add it again, just show the other one)
		ChartIndicator same = null;
		for (int i = 0; i < chartIndicators.size(); i++) {
			if (chartIndicators.get(i).toString().equals(ci.toString())) {
				same = chartIndicators.get(i);
				break;
			}
		}
		// if this indicator is not there yet, add it
		if (same == null) { 
			ci.setCandles(this.w.getCandles());
			ci.calculate();
			this.chartIndicators.add(ci);
			JCheckBoxMenuItem cbmi = new JCheckBoxMenuItem(ci.toString());
			cbmi.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					JCheckBoxMenuItem cbmi = (JCheckBoxMenuItem) e.getSource();
					// search for the right indicator
					ChartIndicator ci = null;
					for (int i = 0; i < chartIndicators.size(); i++) {
						if (chartIndicators.get(i).toString().equals(cbmi.getText())) {
							ci = chartIndicators.get(i);
						}
					}
					if (ci != null) {
						if (cbmi.isSelected()) {
							ci.setActive(true);
						} else {
							ci.setActive(false);
						}
						panel.calculateScaledIndicators();
						panel.paintImage();
						panel.repaint();
					} else { // DEBUG
						System.err.println("Action Listener Menu: Indicator not found");
					}
				}
			});
			menuIndicators.add(cbmi);
			indicatorMenuItems.add(cbmi); // list in order to be able to remove it later
			cbmi.setSelected(ci.isActive());
			panel.calculateScaledIndicators();
			panel.paintImage();
			panel.repaint();
		} 
		else { // same != null; i.e. if this indicator is already there, activate it
			for (int i = 0; i < indicatorMenuItems.size(); i++) { // set check in menu
				if (indicatorMenuItems.get(i).getText().equals(ci.toString())) {
					indicatorMenuItems.get(i).setSelected(true);
					break;
				}
			}
			same.setActive(true);
			panel.calculateScaledIndicators();
			panel.paintImage();
			panel.repaint();
		}
	}

	/**
	 * Removes the provided {@link ChartIndicator} from this chart.
	 * @param ci the {@link ChartIndicator} to be removed
	 */
	public void removeChartIndicator(ChartIndicator ci) {
		ci.setActive(false);
		this.chartIndicators.remove(ci);
		for (int i = 0; i < indicatorMenuItems.size(); i++) { // remove menu entry
			if (indicatorMenuItems.get(i).getText().equals(ci.toString())) {
				menuIndicators.remove(indicatorMenuItems.get(i));
				indicatorMenuItems.remove(i);
				break;
			}
		}
		panel.calculateScaledIndicators();
		panel.paintImage();
		panel.repaint();
	}

	/**
	 * Get an {@link ArrayList} with all chart indicators.
	 * @return an {@link ArrayList} containing all chart indicators
	 */
	public ArrayList<ChartIndicator> getChartIndicators() {
		return this.chartIndicators;
	}

	/**
	 * Updates the chart with the relevant application-wide properties, which are pulled from the controller.
	 * Relevant properties are the candle scheme and the colors for alarms. Should be called whenever
	 * the application-wide properties have change (in particular, on confirming changes in the property dialog).
	 */
	public void updateChartProperties() {
		panel.setCandleScheme(control.getPropertyCandleScheme());
		panel.setAlarmColor(control.getPropertyAlarmColor().toColor());
		if (isInitialized) {
			panel.paintImage();
			panel.repaint();
		}
	}

	//////////////
	// Alarm management
	//////////////
	/**
	 * Adds the provided {@link ChartAlarm} to this chart.
	 * @param ca the {@link ChartAlarm} to be added
	 * @param addToModel determines whether this alarm should be added to the central alarm model (true) or whether it should
	 * be kept local (false)
	 */
	public void addChartAlarm(ChartAlarm ca, boolean addToModel) {
		JCheckBoxMenuItem cbmi = new JCheckBoxMenuItem(ca.toString());
		cbmi.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				JCheckBoxMenuItem cbmi = (JCheckBoxMenuItem) e.getSource();
				// search for the right alarm
				int idx = -1;
				for (int i = 0; i < chartAlarms.size(); i++) {
					if (chartAlarms.get(i).toString().equals(cbmi.getText())) {
						idx = i;
					}
				}
				if (idx != -1) {
					if (cbmi.isSelected()) {
						chartAlarmsActiveFlag.set(idx, true);
					} else {
						chartAlarmsActiveFlag.set(idx, false);
					}
					panel.calculateScaledAlarms();
					panel.paintImage();
					panel.repaint();
				}
			}
		});
		menuAlarms.add(cbmi);
		alarmMenuItems.add(cbmi); // list in order to be able to remove it later
		cbmi.setSelected(true);

		chartAlarms.add(ca);
		chartAlarmsActiveFlag.add(true);
		if (addToModel) {
			control.addAlarm(ca, w.getKey(), this);
		}
		panel.calculateScaledAlarms();
		panel.paintImage();
		panel.repaint();
	}

	/**
	 * Removes the provided {@link ChartAlarm} from this chart.
	 * @param ca the {@link ChartAlarm} to be removed
	 * @param removeFromModel if true, remove this alarm also from the central {@link stocker.control.AlarmManager}; 
	 *        if false, only remove locally in this chart 
	 */
	public void removeChartAlarm(ChartAlarm ca, boolean removeFromModel) {
		int idx = chartAlarms.indexOf(ca);
		if (idx > -1) { // if alarm is actually contained (it should always be, except for some tests)
			chartAlarmsActiveFlag.remove(idx);
			chartAlarms.remove(ca);
			for (int i = 0; i < alarmMenuItems.size(); i++) {
				if (alarmMenuItems.get(i).getText().equals(ca.toString())) {
					menuAlarms.remove(alarmMenuItems.get(i));
					alarmMenuItems.remove(i);
					break;
				}
			}
			panel.calculateScaledAlarms();
			panel.paintImage();
			panel.repaint();
			if (removeFromModel) {
				control.removeAlarm(w.getKey(), ca.getValue(), this);
			}
		}
	}

	/**
	 * Get an {@link ArrayList} with all chart alarms.
	 * @return an {@link ArrayList} containing all chart alarms
	 */
	public ArrayList<ChartAlarm> getChartAlarms() {
		return this.chartAlarms;
	}
	
	/**
	 * Get an {@link ArrayList} with this chart's chart alarm activity.
	 * @return an {@link ArrayList} containing all chart alarm activity flags
	 */
	public ArrayList<Boolean> getChartAlarmActivities() {
		return this.chartAlarmsActiveFlag;
	}

	/**
	 * Create menu.
	 * @param type the chart type
	 */
	private void createMenu(EChartType type) {
		JMenuBar menubar = new JMenuBar();
		JMenu menuChartType = new JMenu("Charttyp");
		menuChartType.setMnemonic(KeyEvent.VK_C);
		ButtonGroup bgChartType = new ButtonGroup();
		JRadioButtonMenuItem menuItCandles = new JRadioButtonMenuItem("Kerzen");
		bgChartType.add(menuItCandles);
		menuItCandles.setMnemonic(KeyEvent.VK_K);
		menuItCandles.addActionListener(this);
		JRadioButtonMenuItem menuItLine = new JRadioButtonMenuItem("Linie");
		bgChartType.add(menuItLine);
		menuItLine.setMnemonic(KeyEvent.VK_L);
		menuItLine.addActionListener(this);
		menuChartType.add(menuItCandles);
		menuChartType.add(menuItLine);
		menubar.add(menuChartType);
		if (type.equals(EChartType.CANDLE)) {
			menuItCandles.setSelected(true);
		} else {
			menuItLine.setSelected(true);
		}
		JMenu menuInterval = new JMenu("Intervall");
		menuInterval.setMnemonic(KeyEvent.VK_I);
		ButtonGroup bgChartInterval = new ButtonGroup();
		String[] intervals = new String[] { "1 min", "5 min", "15 min", "30 min", "1 h", "1 Tag", "1 Woche", "1 Monat" };
		for (String s : intervals) {
			JRadioButtonMenuItem it = new JRadioButtonMenuItem(s);
			bgChartInterval.add(it);
			it.addActionListener(this);
			menuInterval.add(it);
			if (w.getInterval().toString().equals(s)) {
				it.setSelected(true);
			}
		}
		menubar.add(menuInterval);
		menuIndicators = new JMenu("Indikatoren");
		menuIndicators.setMnemonic(KeyEvent.VK_N);
		JMenuItem menuIndSet = new JMenuItem("Indikatoren verwalten...");
		menuIndSet.setMnemonic(KeyEvent.VK_V);
		KeyStroke indicatorHotkey = KeyStroke.getKeyStroke(KeyEvent.VK_I, KeyEvent.CTRL_DOWN_MASK);
		menuIndSet.setAccelerator(indicatorHotkey);
		menuIndSet.addActionListener(this);
		menuIndicators.add(menuIndSet);
		menuIndicators.add(new JSeparator());
		menubar.add(menuIndicators);
		menuAlarms = new JMenu("Alarme");
		menuAlarms.setMnemonic(KeyEvent.VK_A);
		JMenuItem menuAlarmsSet = new JMenuItem("Alarme verwalten...");
		menuAlarmsSet.setMnemonic(KeyEvent.VK_V);
		KeyStroke alarmHotkey = KeyStroke.getKeyStroke(KeyEvent.VK_A, KeyEvent.CTRL_DOWN_MASK);
		menuAlarmsSet.setAccelerator(alarmHotkey);
		menuAlarmsSet.addActionListener(this);
		menuAlarms.add(menuAlarmsSet);
		menuAlarms.add(new JSeparator());
		menubar.add(menuAlarms);

		menubar.setAlignmentX(LEFT_ALIGNMENT);
		this.setJMenuBar(menubar);

		JCheckBox checkGridlines = new JCheckBox("Gitterlinien");
		checkGridlines.setMnemonic(KeyEvent.VK_L);
		checkGridlines.setSelected(panel.drawFullGrid);
		checkGridlines.addActionListener(this);
		menubar.add(checkGridlines);
	}

}
