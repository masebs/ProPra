package stocker.control;

import java.awt.Dimension;
import java.awt.Point;
import java.beans.PropertyVetoException;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;

import javax.swing.JInternalFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import stocker.dialog.ISearchDataReceiver;
import stocker.dialog.StockerPropertyDialog;
import stocker.dialog.StockerSearchDialog;
import stocker.model.ChartAlarm;
import stocker.model.ChartIndicator;
import stocker.model.ChartWatchItem;
import stocker.model.WatchlistItem;
import stocker.view.IStockerDataListener;
import stocker.view.StockerChart;
import stocker.view.StockerFrame;
import stocker.view.Watchlist;
import stocker.util.*;

/**
 * The main controller of the Stocker application. Responsible for startup, shutdown,
 * properties and session management.
 * All data-related communication is outsourced to the {@link StockerDataManager}. 
 * 
 * @author Marc S. Schneider
 */
public class StockerControl {

	private StockerFrame frame;    // the main frame (view)
	private StockerDataManager dm; // the data manager, who builds and maintains the model (and is itself a part of
								   // the controller)

	private JsonObject props = new JsonObject();
	private JsonObject sessions = new JsonObject();
	private String propFilename = "stocker_3254631.json";
	private String sessionFilename = "stocker_3254631_session.json";
	private String currentSessionName = "default";
	private AlarmManager alarmManager;

	/**
	 * Construct a new StockerControl instance. Reads the properties, intializes data manager and main frame
	 * and restores the previous session. 
	 */
	public StockerControl() {
		System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "ERROR"); // disable the verbose logging of the WebSocket client
		
		readProperties(propFilename);

		this.dm = new StockerDataManager(this);
		this.frame = new StockerFrame(this);
		this.alarmManager = new AlarmManager(frame);
		dm.setAlarmManager(alarmManager);

		this.frame.setVisible(true);

		readSessions(sessionFilename);
		restoreSession(currentSessionName);
	}

	/**
	 * Get the main frame of this application
	 * @return the main frame (a {@link StockerFrame})
	 */
	public StockerFrame getFrame() {
		return this.frame;
	}

	/**
	 * Get the number of the currently active data provider from the properties.
	 * @return the index of the currently active data provider, w.r.t. the order that they are defined in the 
	 * properties
	 */
	public int getActiveDataProvider() {
		return props.get("activeDataProvider").getAsInt();
	}

	/**
	 * Set currently active data provider (in the properties) to the given number
	 * @param prov the index of the data provider to be set active, w.r.t. the order that they are defined 
	 * in the properties
	 */
	public void setActiveDataProvider(int prov) {
		props.addProperty("activeDataProvider", prov);
	}

	////////////////////
	// Properties //////
	////////////////////
	/**
	 * Reads the properties for this application from a Json file at the given disk location into the internal
	 * property {@link JsonObject}.
	 * @param filepath the path to the properties file, including file name and file extension
	 */
	public void readProperties(String filepath) {
		Gson gson = new Gson();
		JsonObject newProps = null;

		if (!(new File(filepath).exists())) {
			// if file does not exist: initialize this.propmap with default properties
			initializeProperties();
			writeProperties(filepath);
		} else {
			try {
				String s = Files.readString(Paths.get(filepath));
				newProps = gson.fromJson(s, JsonObject.class);
			} catch (Exception e) {
				JOptionPane.showMessageDialog(frame, "Problem beim Lesen von " + filepath, "Warnung",
						JOptionPane.WARNING_MESSAGE);
			}
			if (newProps != null) {
				this.props = newProps;
			}
		}
	}

	/**
	 * Writes the properties for this application from the internal {@link JsonObject} into a Json file on the disk.
	 * @param filepath the path to the properties file, including file name and file extension 
	 */
	public void writeProperties(String filepath) {
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		try {
			Files.writeString(Paths.get(filepath), gson.toJson(this.props));
		} catch (Exception e) {
			JOptionPane.showMessageDialog(frame, "Problem beim Schreiben von " + filepath, "Warnung",
					JOptionPane.WARNING_MESSAGE);
		}
	}

	/**
	 * Creates a new properties file with default values.
	 */
	public void initializeProperties() {
		// the missing API key will be requested later in restoreSession(), when the main frame is already shown
		JsonObject dp1 = getDataProviderJsonObject("finnhub.io", "https://finnhub.io/api/v1", "wss://ws.finnhub.io", "");
		JsonObject dp2 = getDataProviderJsonObject("Kursdatengenerator", "http://localhost:8080", "ws://localhost:8090",
				"3254631");
		JsonArray dpArr = new JsonArray();
		dpArr.add(dp1);
		dpArr.add(dp2);
		this.props.add("DataProviders", dpArr);

		JsonArray minSize = new JsonArray(2);
		minSize.add(580);
		minSize.add(450);
		this.props.add("MinimumSize", minSize);

		this.props.addProperty("DefaultChartType", EChartType.CANDLE.toObjectString());
		this.props.addProperty("DefaultChartInterval", EChartInterval.I1DAY.toObjectString());
		this.props.addProperty("IndicatorColor", EChartColors.BLUE.toObjectString());
		this.props.addProperty("AlarmColor", EChartColors.RED.toObjectString());

		// from here on: properties in addition to requirements
		this.props.addProperty("CandleScheme", ECandleScheme.REDGREEN.toObjectString());
		this.props.addProperty("activeDataProvider", 0);
		this.props.addProperty("showOnlyUSStocks", false);
	}

	///////
	// Methods for accessing props (used e.g. by DataManager)
	///////
	/**
	 * Returns a JsonObject representing a data provider with the properties provided as parameters.
	 * @param name a descriptive name for the data provider
	 * @param pullURL the URL for pulling requests
	 * @param pushURL the URL for push subscriptions
	 * @param token the api token required to authenticate at the provider
	 * @return a {@link JsonObject} representing the data provider
	 */
	private JsonObject getDataProviderJsonObject(String name, String pullURL, String pushURL, String token) {
		JsonObject dp = new JsonObject();
		dp.add("name", new JsonPrimitive(name));
		dp.add("pullURL", new JsonPrimitive(pullURL));
		dp.add("pushURL", new JsonPrimitive(pushURL));
		dp.add("token", new JsonPrimitive(token));
		return dp;
	}

	/**
	 * Get the URL that is required for pull requests at the current data provider (as contained in the properties).
	 * @return the URL for pull requests
	 */
	public String getPullURL() {
		int activeDataProvider = props.get("activeDataProvider").getAsInt();
		return props.get("DataProviders").getAsJsonArray().get(activeDataProvider).getAsJsonObject().get("pullURL")
				.getAsString();
	}

	/**
	 * Get the URL that is required for push subscriptions at the current data provider (as contained in the properties).
	 * @return the URL for push subscritions
	 */
	public String getPushURL() {
		int activeDataProvider = props.get("activeDataProvider").getAsInt();
		return props.get("DataProviders").getAsJsonArray().get(activeDataProvider).getAsJsonObject().get("pushURL")
				.getAsString();
	}

	/**
	 * Get the API token for authentication at the current data provider (as contained in the properties).
	 * @return the token for authentication at the data provider
	 */
	public String getAPIToken() {
		int activeDataProvider = props.get("activeDataProvider").getAsInt();
		return props.get("DataProviders").getAsJsonArray().get(activeDataProvider).getAsJsonObject().get("token")
				.getAsString();
	}

	////////////
	// Session Management
	////////////
	/**
	 * Saves the session as it currently is into the session list (a {@link JsonObject}).
	 * A session contains all window positions and sizes and all information required to restore the currently 
	 * shown data.
	 * @param name the name with which this session should be saved
	 */
	public void saveSession(String name) {
		JsonObject jo = new JsonObject();
		jo.addProperty("name", name);

		// Serialize the symbols from the watchlist table
		jo.add("watchlist", frame.serializeWatchlistSymbolsToJson());

		// Serialize window sizes and positions
		// First the parent frame
		JsonObject joParent = new JsonObject();
		joParent.addProperty("maximized", frame.getExtendedState());
		joParent.add("size", serializeDimension(frame.getSize()));
		joParent.add("position", serializePoint(frame.getLocation()));
		jo.add("parent", joParent);

		// then the children
		JInternalFrame[] children = frame.getChildWindows();
		JsonArray jaChildren = new JsonArray();
		for (JInternalFrame intFrame : children) {
			JsonObject joIntFrame = new JsonObject();
			joIntFrame.addProperty("title", intFrame.getTitle());
			joIntFrame.add("size", serializeDimension(intFrame.getSize()));
			joIntFrame.add("position", serializePoint(intFrame.getLocation()));
			joIntFrame.addProperty("isMaximized", intFrame.isMaximum());
			joIntFrame.addProperty("isIconified", intFrame.isIcon());
			if (intFrame instanceof StockerChart) { // only for charts
				StockerChart chart = (StockerChart) intFrame;
				ChartWatchItem w = chart.getWatchItem();
				joIntFrame.addProperty("key", w.getKey());
				joIntFrame.addProperty("name", w.getName());
				joIntFrame.addProperty("chartInterval", w.getInterval().toObjectString());
				joIntFrame.addProperty("chartType", chart.getChartType().toObjectString());
				joIntFrame.addProperty("gridlinesShown", chart.getGridlineState());
				JsonArray jaIndicators = new JsonArray();
				ArrayList<ChartIndicator> indicators = chart.getChartIndicators();
				Iterator<ChartIndicator> indIt = indicators.iterator();
				while (indIt.hasNext()) {
					JsonObject joind = new JsonObject();
					JsonArray indParams = new JsonArray();
					ChartIndicator ci = indIt.next();
					joind.addProperty("type", ci.getType());
					joind.addProperty("active", ci.isActive());
					int[] parr = ci.getParameters();
					for (int p : parr) {
						indParams.add(p);
					}
					joind.add("params", indParams);
					joind.addProperty("color", ci.getColor().toObjectString());
					jaIndicators.add(joind);
				}
				joIntFrame.add("indicators", jaIndicators);
			} else {
				joIntFrame.addProperty("key", "");
				joIntFrame.addProperty("chartType", "");
				joIntFrame.addProperty("chartInterval", "");
				joIntFrame.add("indicators", new JsonArray());
			}
			jaChildren.add(joIntFrame);
		}
		jo.add("children", jaChildren);
		
		// Finally, the alarms
		jo.add("alarms", alarmManager.serializeToJson());

		sessions.remove(name);
		sessions.add(name, jo);
		currentSessionName = name;
	}
	
	/**
	 * Saves the session as it currently is, with name of the currently active session, into the session list 
	 * (a {@link JsonObject}). A session contains all window positions and sizes and all information required 
	 * to restore the currently shown data.
	 * @return the name of the current session
	 */
	public String saveCurrentSession() {
		saveSession(currentSessionName);
		return currentSessionName;
	}

	/**
	 * Write the sessions from the sessions JsonObject of this application into a Json file.
	 * @param filepath the full path to the session file, including file name and extension
	 */
	private void writeSessions(String filepath) {
		saveCurrentSession(); // save the current session under its name
		sessions.addProperty("activeSession", currentSessionName);

		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		try {
			Files.writeString(Paths.get(filepath), gson.toJson(this.sessions));
		} catch (Exception e) {
			JOptionPane.showMessageDialog(frame, "Problem beim Schreiben der Session in " + filepath, "Warnung",
					JOptionPane.WARNING_MESSAGE);
		}
	}

	// helper function
	private JsonObject serializePoint(Point p) {
		JsonObject jo = new JsonObject();
		jo.addProperty("x", p.x);
		jo.addProperty("y", p.y);
		return jo;
	}

	// helper function
	private JsonObject serializeDimension(Dimension d) {
		JsonObject jo = new JsonObject();
		jo.addProperty("w", d.width);
		jo.addProperty("h", d.height);
		return jo;
	}

	/**
	 * Read the sessions from a Json file into the sessions JsonObject of this application. In addition, this
	 * asks for an API key for the data provider if no one is available.
	 * @param filepath the full path to the session file, including file name and extension
	 * @return true if reading the sessions was successful, false otherwise
	 */
	private boolean readSessions(String filepath) {
		Gson gson = new Gson();
		JsonObject jo = null;

		if (new File(filepath).exists()) {
			try {
				String s = Files.readString(Paths.get(filepath));
				jo = gson.fromJson(s, JsonObject.class);
			} catch (Exception e) {
				JOptionPane.showMessageDialog(frame, "Problem beim Lesen von " + filepath, "Warnung",
						JOptionPane.WARNING_MESSAGE);
			}
		} else {
			System.out.println("No session file found, starting from scratch");
		}
		
		// if no API key for the data provider is set, ask for it! (this is an appropriate time to do this,
		// because the main frame is already visible, but no data has been tried to pull yet)
		JsonObject currentDataProvider = props.get("DataProviders").getAsJsonArray()
				.get(StockerControl.this.getActiveDataProvider()).getAsJsonObject();
		if (currentDataProvider.get("token").getAsString().isBlank()) {
			String apiKey = JOptionPane.showInputDialog("Für den Standard-Datenanbieter " 
					+ currentDataProvider.get("name").getAsString() + " ist ein API-Key "
					+ "erforderlich.\nBitte hier eingeben:");
			currentDataProvider.addProperty("token", apiKey);
		}

		if (jo != null) {
			this.sessions = jo;
			this.currentSessionName = jo.get("activeSession").getAsString();
			for (String sessionName : jo.keySet()) {
				if (sessionName.equals(currentSessionName)) {
					frame.addSessionMenuEntry(sessionName).setSelected(true);
				}
				else if (!sessionName.equals("activeSession")) {
					frame.addSessionMenuEntry(sessionName);
				}
			}	
			return true;
		} else {
			return false;
		}
	}
	
	/**
	 * Restores the session to the state that was saved under the given name.
	 * The session data is read from this applications {@link JsonObject} for sessions, which has likely been
	 * read previously from a Json file using {@link #readSessions(String)}.
	 * A session contains all window positions and sizes and all information required to restore the currently 
	 * shown data.
	 * @param sessionName the name of the session to be restored
	 */
	public void restoreSession(String sessionName) {
		this.currentSessionName = sessionName;
		// create Thread and let invokeLater in order not to block showing of the windows
		SwingUtilities.invokeLater( new Thread() {
			@Override
			public void run() {
				JsonObject jo = null;
				try {
					jo = sessions.get(sessionName).getAsJsonObject();
				} catch (Exception e) { } // will be handled in the following "if" as jo is null
				if (jo == null) {
					System.out.println("Session " + sessionName + " not found!");
					return;
				}

				// Deserialize window sizes and positions
				// First the parent frame
				JsonObject joParent = jo.get("parent").getAsJsonObject();
				frame.setExtendedState(joParent.get("maximized").getAsInt());
				frame.setSize(deserializeDimension(joParent.get("size").getAsJsonObject()));
				frame.setLocation(deserializePoint(joParent.get("position").getAsJsonObject()));
				
				// then the children
				// use two separate for loops: The outer one creates the children asap without keeping a reference to them,
				// the second one get a reference and completes the setup (this speeds up appearance of the windows; they
				// don't have to wait for the pull requests to complete in order to show up)
				JsonArray jaChildren = jo.get("children").getAsJsonArray();
				for (int i = 0; i < jaChildren.size(); i++) {
					JsonObject child = jaChildren.get(i).getAsJsonObject();
					String title = child.get("title").getAsString();
					Dimension dim = deserializeDimension(child.get("size").getAsJsonObject()); // save for second loop later
					Point location = deserializePoint(child.get("position").getAsJsonObject());
					boolean isMaximized = child.get("isMaximized").getAsBoolean();
					boolean isIconified = child.get("isIconified").getAsBoolean();
					if (title.equals("Watchlist")) { 
						frame.setWatchlistSizeAndPosition(dim, location);
					}
					else { // watchlist is created automatically, but charts need to be added one by one
						// get ChartWatchItem
						String key = child.get("key").getAsString();
						String name = child.get("name").getAsString();
						EChartInterval interval = EChartInterval.valueOf(child.get("chartInterval").getAsString());
						EChartType type = EChartType.valueOf(child.get("chartType").getAsString());
						ChartWatchItem w = new ChartWatchItem(key, name, interval);
						boolean gridlinesShown = child.get("gridlinesShown").getAsBoolean();

						// get indicators
						JsonArray jaIndicators = child.get("indicators").getAsJsonArray();
						ChartIndicator[] ciarr = new ChartIndicator[jaIndicators.size()];
						for (int j = 0; j < jaIndicators.size(); j++) {
							JsonObject joind = jaIndicators.get(j).getAsJsonObject();
							String indtype = joind.get("type").getAsString();
							EChartIndicator eci = EChartIndicator.valueOf(indtype);
							ChartIndicator ci = eci.getIndicator(); // use EChartIndicator as Factory
							ci.setActive(joind.get("active").getAsBoolean());
							JsonArray params = joind.get("params").getAsJsonArray();
							int[] parr = new int[params.size()];
							for (int k = 0; k < params.size(); k++) {
								parr[k] = params.get(k).getAsInt();
							}
							ci.setParameters(parr);
							ci.setColor(EChartColors.valueOf(joind.get("color").getAsString()));
							ciarr[j] = ci;
						}
						
						StockerChart chart = frame.openChartWindow(w, type, location, dim, gridlinesShown); 
						
						try {
							chart.setMaximum(isMaximized);
							chart.setIcon(isIconified);
						} catch (PropertyVetoException e) { } // doesn't want to comply, so leave it as it is
						
						// Thread to wait unit the chart window is properly initalized (data pulled etc), 
						// which might take a while. Then, add chart indicators
						new Thread() {
							public void run() {
								while (!chart.isInitialized()) {
									try {
										Thread.sleep(500);
									} catch (InterruptedException e) { }
								}
								// when ready, schedule adding of the chart indicators to Swing
								SwingUtilities.invokeLater(new Runnable() { 
									@Override
									public void run() {
										for (ChartIndicator ci : ciarr) {
											chart.addChartIndicator(ci);
										}
									}
								});
								
							};
						}.start();
					}
				}
				
				// Deserialize watchlist entries (we call this after the chart stuff so that the thread (called within
				// the method) is enqueued behind the chart stuff and doesn't block appearance of the chart windows
				frame.deserializeWatchlistSymbolsFromJson(jo.get("watchlist").getAsJsonArray());
				
				// Finally, the alarms
				// Do this with a delay and then via Swing so that it's done after the other the other swing threads above 
				// (and so that the chart windows and the watchlist are already registered at the AlarmManager)
				JsonArray jaAlarms = jo.get("alarms").getAsJsonArray();
				new Thread() {
					@Override
					public void run() {
						try {
							Thread.sleep(1000);
						} catch (InterruptedException e) { }
						SwingUtilities.invokeLater(new Runnable() {
							@Override
							public void run() {
								alarmManager.deserializeFromJson(jaAlarms);
							}
						});
					}
				}.start();
			}
		});
	}		
	
	// helper function
	private Point deserializePoint(JsonObject jo) {
		int x = jo.get("x").getAsInt();
		int y = jo.get("y").getAsInt();
		return new Point(x, y);
	}

	// helper function
	private Dimension deserializeDimension(JsonObject jo) {
		int w = jo.get("w").getAsInt();
		int h = jo.get("h").getAsInt();
		return new Dimension(w, h);
	}
	
	/**
	 * Removes the session which is currently active from the internal session list (so it will no longer be 
	 * saved and can no longer be restored).
	 * @return the name of the current session which has just been removed
	 */
	public String removeCurrentSession() {
		String oldSessionName = new String(currentSessionName);
		sessions.remove(currentSessionName);
		Iterator<String> sessionIt = sessions.keySet().iterator();
		currentSessionName = sessionIt.next();
		while (currentSessionName.equals("activeSession")) { // not an actual session name
			currentSessionName = sessionIt.next(); 
		}
		resetSession();
		restoreSession(currentSessionName);
		return oldSessionName;
	}
	
	/**
	 * Resets the current session, i.e. closes all open charts and clears the watchlist.
	 */
	public void resetSession() {
		frame.getWatchlist().clear();
		JInternalFrame[] children = frame.getChildWindows();
		for (JInternalFrame f : children) {
			if (f instanceof StockerChart) {
				f.doDefaultCloseAction();
			}
		}
		frame.removeAllChartWindowsFromMenu();
		alarmManager.clearAllAlarms();
	}
	
	/**
	 * Determines whether the given name is acutally a session name.
	 * @param name the session name to be verified
	 * @return true if it is the name of an actual session, false otherwise
	 */
	public boolean verifySessionName(String name) {
		return sessions.keySet().contains(name);
	}
	
	/**
	 * Returns the number of currently defined sessions.
	 * @return the number of currently defined sessions
	 */
	public int getSessionCount() {
		return sessions.size() - 1; // 1 element is always the activeSession property, so subtract that
	}
	
	/**
	 * Returns the name of the currently active session.
	 * @return the name of the currently active session
	 */
	public String getCurrentSessionName() {
		return currentSessionName;
	}

	//////////
	// Dialog windows
	//////////
	/**
	 * Open a search dialog.
	 */
	public void showSearchDialog() {
		StockerSearchDialog searchDialog = new StockerSearchDialog(frame, this);
		searchDialog.setVisible(true);
	}
	
	/**
	 * Open a properties dialog.
	 */
	public void showSettingsDialog() {
		StockerPropertyDialog pd = new StockerPropertyDialog(frame, this, props);
		pd.setVisible(true);
	}
	
	////////
	// Search (used by search dialog)
	///////
	/**
	 * Put a query for a stock to the data provider. Communication is done non-blocking, so this method
	 * does not return a value, but instead will call back the provided {@link ISearchDataReceiver} as soon as
	 * the search result is ready.
	 * @param query the search string
	 * @param dr the receiver which will be notified and handed over the search result as soon as it is available
	 */
	public void searchStocks(String query, ISearchDataReceiver dr) {
		new Thread() {
			public void run() {
				try {
					dm.searchSymbol(query, dr);
				} catch (StockerDataManagerException e) { // retry after 3 seconds, if that fails: report error
					try {
						Thread.sleep(3000);
						dm.searchSymbol(query, dr);
					} catch (StockerDataManagerException | InterruptedException e2) {
						JOptionPane.showMessageDialog(frame, e2.getMessage(), "Fehler bei der Suche", JOptionPane.ERROR_MESSAGE);
					}
				}
			}
		}.start();

	}
	
	/////////////
	// Wrapper methods for access to data manager (in order to obey Demeter's law)
	/////////////
	/**
	 * Pulls plot data according to the properties of the provider {@link ChartWatchItem}, and writes the 
	 * result into the same {@link ChartWatchItem}.
	 * @param w the {@link ChartWatchItem} which contains information about the data to be pulled; the result
	 *        will be written into that same {@link ChartWatchItem}
	 * @throws StockerDataManagerException
	 */
	public void getPlotData(ChartWatchItem w) throws StockerDataManagerException {
		dm.getPlotData(w);
	}
	
	/**
	 * Get a quote from the data provider for the provided {@link WatchlistItem}.
	 * @param w the {@link WatchlistItem} to be quoted; the result will be written into that same {@link ChartWatchItem}
	 * @throws StockerDataManagerException
	 */
	public void getQuote(WatchlistItem w) throws StockerDataManagerException {
		dm.getQuote(w);
	}
	
	/**
	 * Add a {@link StockerChart} to the data manager's list of listeners (which will be notified in the case
	 * of a relevant data change).
	 * @param c the {@link StockerChart} to be added as a listener
	 */
	public void addChartListenerToDataManager(StockerChart c) {
		dm.addChartListener(c);
	}
	
	/**
	 * Remove a {@link StockerChart} from the data manager's list of listeners (which will be notified in the case
	 * of a relevant data change).
	 * @param c the {@link StockerChart} to be removed from the list of listeners
	 */
	public void removeChartListenerFromDataManager(StockerChart c) {
		dm.removeChartListener(c);
	}
	
	/**
	 * Adds a {@link Watchlist} to the data manager's list of listeners (which will be notified in the case
	 * of a relevant data change).
	 * @param w the {@link Watchlist} to be added as a listener
	 */
	public void addWatchlistListenerToDataManager(Watchlist w) {
		dm.addWatchlistListener(w);
	}
	
	/**
	 * Returns whether the push connection is initialized or not.
	 * @return true if initialized, false otherwise
	 */
	public boolean isPushInitialized() {
		return dm.isPushInitialized();
	}
	
	/**
	 * Subscribe for push notifications for the given symbol. 
	 * @param symbol the ticker symbol for which push notifications are requested
	 */
	public void addSymbolToPush(String symbol) {
		dm.addSymbolToPush(symbol);
	}
	
	/**
	 * Stop push notifications for the given symbol. 
	 * @param symbol the ticker symbol for which push notifications should be stopped
	 */
	public void removeSymbolFromPush(String symbol) {
		dm.removeSymbolFromPush(symbol);
	}
	
	/**
	 * Switch the active data provider to that which is currently set as active in the control's properties.
	 * The switch is actually done by setting the new active data provider in the properties; this method only makes
	 * sure that the change takes effect immediately and that the push subscriptions are moved to the new provider. 
	 */
	public void switchDataProvider() {
		dm.switchDataProvider();
	}
	
	/**
	 * Notifies this StockerControl that a push subscription for the given symbol has failed (usually because the 
	 * push connection is not open any more). The user is notified via a message dialog.
	 * @param symbol the symbol for which the subscription failed
	 */
	public void onPushSubscriptonFailed(String symbol) {
		JOptionPane.showMessageDialog(frame, 
				"Der Wert " + symbol + " konnte nicht für Push-Benachrichtigungen\nangemeldet werden beim Datenlieferanten.\nEvtl. Verbindung prüfen und nochmal neu hinzufügen!", 
				"Keine Push-Daten", JOptionPane.WARNING_MESSAGE);
	}
	
	/**
	 * Returns the watchlist of the application (wrapper for Demeter's law; required for IStockerTester only)
	 * @return the current {@link Watchlist} of the application 
	 */
	public Watchlist getWatchlist() {
		return frame.getWatchlist();
	}
	
	/////////////
	// Wrapper methods for access to alarm manager (in order to obey Demeter's law)
	/////////////
	/**
	 * Registers a listener and does some book keeping which enables to determine how many listeners there
	 * are for each symbol. A listener is usually a Watchlist or a StockerChart. Watchlists should register each
	 * each symbol they represent. StockerCharts represent only one symbol, so they need to register only once.
	 * @param listener the data listener
	 * @param symbol the ticker symbol in which this listener is interested
	 * @return
	 */
	public ArrayList<ChartAlarm> registerAlarmListener(IStockerDataListener listener, String symbol) {
		return alarmManager.registerAlarmListener(listener, symbol);
	}
	
	/**
	 * Unregister an alarm listener (usually a Watchlist or a StockerChart). To be called as soon as the listener
	 * is no longer interested in the symbol and does not need further alarm updates. Usually that is when the 
	 * symbol has been removed from the watchlist or the the chart window has been closed.
	 * @param listener the listener which is to be unregistered
	 * @param symbol the symbol that this listeners was registered for
	 */
	public void unregisterAlarmListener(IStockerDataListener listener, String symbol) {
		alarmManager.unregisterAlarmListener(listener, symbol);
	}
	
	/**
	 * Add an alarm. To be called from or for a StockerChart which has created the alarm. Other charts will be informed
	 * that a new alarm has been added.
	 * @param ca the alarm to be added
	 * @param symbol the symbol that this alarm refers to
	 * @param submittingChart the chart that is submitting this alarm (all other charts will receive an update that 
	 * this alarm has been added)
	 */
	public void addAlarm(ChartAlarm ca, String symbol, StockerChart submittingChart) {
		alarmManager.addAlarm(ca, symbol, submittingChart);
	}
	
	/**
	 * Remove an alarm. To be called from or for a StockerChart which has removed the alarm. Other charts will be informed
	 * that the alarm has been removed.
	 * @param symbol the symbol for which an alarm should be removed
	 * @param value the value of the alarm to be removed
	 * @param submittingChart the chart that is submitting this alarm (all other charts will receive an update that 
	 * this alarm has been removed)
	 */
	public void removeAlarm(String symbol, double value, StockerChart submittingChart) {
		alarmManager.removeAlarm(symbol, value, submittingChart);
	}
	
	/**
	 * Clear all alarms for one symbol. Only used for StockerTesterImpl.
	 * @param symbol the symbol for which the alarms should be cleared
	 */
	public void clearAlarms(String symbol) {
		alarmManager.clearAlarms(symbol);
	}
	
	/**
	 * Clear all alarms for all symbols.
	 */
	public void clearAllAlarms() {
		alarmManager.clearAllAlarms();
	}
	
	/**
	 * Get all alarms for the specified symbol. Only used for StockerTesterImpl.
	 * @param symbol the symbol for which the alarms are requested
	 */
	public ArrayList<ChartAlarm> getAlarmsForSymbol(String symbol) {
		return alarmManager.getAlarmsForSymbol(symbol);
	}
	
	/**
	 * Get the symbols of all registered alarms. Only used for StockerTesterImpl.
	 * @return a Set of Strings representing all symbols for which alarms are registered. 
	 */
	public Set<String> getAlarmSymbols() {
		return alarmManager.getAlarmSymbols();
	}
		
	/////////////
	// Methods for property access (to avoid handing out the whole property JsonObject, which would cause unnecessary 
	// coupling and exposure of implementation)
	/////////////
	/**
	 * Returns the minimum window size as set in the properties
	 * @return the minimum window size as set in the properties
	 */
	public Dimension getPropertyMinimumSize() {
		JsonArray ja = props.get("MinimumSize").getAsJsonArray();
		return new Dimension(ja.get(0).getAsInt(), ja.get(1).getAsInt());
	}
	
	/**
	 * Returns the candle scheme as set in the properties
	 * @return the candle scheme as set in the properties
	 */
	public ECandleScheme getPropertyCandleScheme() {
		return ECandleScheme.valueOf(props.get("CandleScheme").getAsString());
	}
	
	/**
	 * Returns the chart type as set in the properties
	 * @return the chart type as set in the properties
	 */
	public EChartType getPropertyChartType() {
		return EChartType.valueOf(props.get("DefaultChartType").getAsString());
	}
	
	/**
	 * Returns the default indicator color as set in the properties
	 * @return the default indicator color as set in the properties
	 */
	public EChartColors getPropertyDefaultIndicatorColor() {
		return EChartColors.valueOf(props.get("IndicatorColor").getAsString());
	}
	
	/**
	 * Returns the alarm color as set in the properties
	 * @return the alarm color as set in the properties
	 */
	public EChartColors getPropertyAlarmColor() {
		return EChartColors.valueOf(props.get("AlarmColor").getAsString());
	}
	
	/**
	 * Returns whether the flag "showOnlyUSStocks" is set in the properties
	 * @return true if set, false otherwise
	 */
	public boolean getPropertyShowOnlyUSStocks() {
		return props.get("showOnlyUSStocks").getAsBoolean();
	}

	///////////////
	// Shutdown
	///////////////
	/**
	 * Shutdown the application: Stop the push connection, write the properties and session information and exit.
	 */
	public void shutdown(boolean callExit) {
		dm.stopPush();
		writeProperties(propFilename);
		writeSessions(sessionFilename);
		System.out.println("Shutting down...");
		if (callExit) {
			System.exit(0);
		}
	}

}
