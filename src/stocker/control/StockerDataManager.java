package stocker.control;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.Instant;
import java.util.LinkedList;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import stocker.dialog.ISearchDataReceiver;
import stocker.model.ChartWatchItem;
import stocker.model.WatchlistItem;
import stocker.util.Candle;
import stocker.util.CandleParser;
import stocker.util.EChartInterval;
import stocker.util.StockerDataManagerException;
import stocker.view.StockerChart;
import stocker.view.Watchlist;

/**
 * Handles all the communication with the data provider. Can logically be viewed as a part of the controller or,
 * depending on the point of view, as a part of the model of the application.
 * 
 * @author Marc S. Schneider
 */
public class StockerDataManager implements IPushReceiver {

	private StockerControl control;
	private WSPushClient pushClient;
	private volatile boolean pushInitialized = false;
	private volatile boolean stopConnectThread = false;
	
	private LinkedList<Watchlist> listeningWatchlists;
	private LinkedList<StockerChart> listeningCharts;
	private AlarmManager alarmManager;
	private LinkedList<String> pushSymbols;
	
	private final int minCandles = 250; // minimum number of candles to be pulled

	/** 
	 * Construct a new StockerDataManager.
	 * @param control a reference to the {@link StockerControl} that this data manager is meant to service
	 */
	public StockerDataManager(StockerControl control) {
		this.control = control;
		this.listeningWatchlists = new LinkedList<Watchlist>();
		this.listeningCharts = new LinkedList<StockerChart>();
		this.pushSymbols = new LinkedList<String>();
		
		// do the push initialization in a separate thread so it won't block main window appearance
		new Thread() {
			@Override
			public void run() {
				try {
					initializePush();
				} catch (StockerDataManagerException e) { 
					System.err.println("Problem while initalizing push connection: " + e.getMessage());
				}
			};
		}.start();
	}

	/**
	 * Adds a {@link Watchlist} to the data manager's list of listeners (which will be notified in the case
	 * of a relevant data change).
	 * @param w the {@link Watchlist} to be added as a listener
	 */
	public void addWatchlistListener(Watchlist w) {
		this.listeningWatchlists.add(w);
	}
	
	/**
	 * Remove a {@link Watchlist} from the data manager's list of listeners (which will be notified in the case
	 * of a relevant data change).
	 * @param w the {@link Watchlist} to be removed from the list of listeners
	 */
	public void removeWatchlistListener(Watchlist w) {
		this.listeningWatchlists.remove(w);
	}
	
	/**
	 * Add a {@link StockerChart} to the data manager's list of listeners (which will be notified in the case
	 * of a relevant data change).
	 * @param c the {@link StockerChart} to be added as a listener
	 */
	public void addChartListener(StockerChart c) {
		this.listeningCharts.add(c);
	}
	
	/**
	 * Remove a {@link StockerChart} from the data manager's list of listeners (which will be notified in the case
	 * of a relevant data change).
	 * @param c the {@link StockerChart} to be removed from the list of listeners
	 */
	public void removeChartListener(StockerChart c) {
		this.listeningCharts.remove(c);
	}
	
	public void setAlarmManager(AlarmManager am) {
		this.alarmManager = am;
	}
	
	////////////////////
	// Searching for symbols
	////////////////////
	/**
	 * Perform a search request at the data provider to find items containing the searchString.
	 * @param searchString the string that is looked for
	 * @param dr the data receiver to be notified and handed over the search result as soon as it is available
	 * @throws StockerDataManagerException 
	 */
	public void searchSymbol(String searchString, ISearchDataReceiver dr) throws StockerDataManagerException {
		String qString = searchString.replace(" ", "%20");
		String allowedChars = "^[a-zA-Z0-9\\.\\-_:+%/]";
		if (!qString.matches(allowedChars.concat("*$"))) {
			throw new StockerDataManagerException("Ungültiges Zeichen in der Suchanfrage: " + qString.replaceAll(allowedChars, ""));
		}
		StringBuilder sb = new StringBuilder();
		sb.append(control.getPullURL()).append("/").append("search").append("/");
		sb.append("?q=").append(qString).append("&token=").append(control.getAPIToken());
		String query = sb.toString();
		System.out.println("query = " + query);

		try {
			JsonObject jo = httpRequest(query);
			int count = jo.get("count").getAsInt();
			JsonArray data = jo.get("result").getAsJsonArray();
			String[][] result = new String[count][2];

			for (int i = 0; i < count; i++) {
				JsonObject item = data.get(i).getAsJsonObject();
				result[i][0] = item.get("description").getAsString();
				result[i][1] = item.get("symbol").getAsString();
			}
			dr.searchDataReady(result);
		} catch (StockerDataManagerException e) {
			throw new StockerDataManagerException("Fehler bei der Suchanfrage: " + e.getMessage());
		}
	}

	////////////////////
	// Let data be pushed
	////////////////////
	/**
	 * Initialize the websocket connection for push notifications.
	 * @throws StockerDataManagerException
	 */
	private void initializePush() throws StockerDataManagerException {
		URI uri = null;
		try {
			while (control.getAPIToken().isBlank()) {
				System.out.println("initializePush: Waiting for API key");
				try {
					Thread.sleep(5000L);
				} catch (InterruptedException e) { }
			}
			String url = new StringBuilder().append(control.getPushURL()).append("/?token=")
					.append(control.getAPIToken()).toString();
			System.out.println("dm, initializePush: URL = " + url);
			uri = new URI(url);
		} catch (URISyntaxException e) {
			throw new StockerDataManagerException("Fehler beim Initialisieren der Push-Verbindung zu " + control.getPullURL() + ":\n" + e.getMessage());
		}
		this.pushClient = new WSPushClient(uri, this);
		try {
			pushClient.connectBlocking(); // we can use connectBlocking as this should be done in separate thread anyway
		} catch (InterruptedException e) {	}
		
		if (pushClient.isConnected()) {
			pushInitialized = true;
		}
		
		// do this in another thread which we can cancel in case it's an infinite loop (e.g. because of wrong provider URL)
		// as soon as we have another property update
		final URI reconnectURI = uri;
		new Thread() {
			@Override
			public void run() {
				System.out.println("Reconnect thread, stopConnect: " + stopConnectThread);
				while (!pushClient.isConnected() && !stopConnectThread) {
					System.out.println("(Re)trying to connect push...");
					try {
						Thread.sleep(5000L);
						pushClient.closeBlocking();  // close gracefully - likely with no effect
						pushClient = new WSPushClient(reconnectURI, StockerDataManager.this); // Clients are not reusable, so get a new one
						if (pushClient.connectBlocking()) {
							pushInitialized = true;
							System.out.println("Reconnect thread, SUCCESS!");
						}
					} catch (InterruptedException iex) { 
						System.out.println("Reconnect thread, EXCEPTION!");
					}
				}
			};
		}.start();
	}

	/**
	 * Subscribe for push notifications for the given symbol. 
	 * @param symbol the ticker symbol for which push notifications are requested
	 * @see #pushMessageIncoming(String)
	 */
	public void addSymbolToPush(String symbol) {
		new Thread() { // this is neither GUI-related nor time-critical, so do it entirely in a separate thread
			@Override
			public void run() {
				boolean alreadySubscribed = pushSymbols.contains(symbol);
				pushSymbols.add(symbol); // we add it even if it's already there, so that it is known that it's now used one more time
				
				if (!alreadySubscribed) { // we send the request do the data provider only if we aren't subscribed yet
					while (!pushInitialized) { // if initialization on construction has failed: try again now!
						System.out.println("add symbol to push: (re)trying...");
						try {
							Thread.sleep(5000); // wait until push connection has been initialized (done in constructor)
						} catch (InterruptedException e) { }
					}
					
					String query = new StringBuilder().append("{\n\"type\": \"subscribe\",\n\"symbol\": \"").append(symbol)
							.append("\"\n}").toString();
					try {
						pushClient.send(query);
						System.out.println("Added to push: " + query);
					} catch (Exception e) { // likely because connection doesn't exist anymore
						// Notify controller (which should show a warning message to the user)
						control.onPushSubscriptonFailed(symbol);
					}
				}
			}
		}.start();
	}

	/**
	 * Stop push notifications for the given symbol. 
	 * @param symbol the ticker symbol for which push notifications should be stopped
	 */
	public void removeSymbolFromPush(String symbol) {
		pushSymbols.remove(symbol);
		if (pushInitialized && !pushSymbols.contains(symbol)) {
			// remove only if it's actually no longer in the push symbols. It might have been in there multiple
			// times because several plots and the watchlist have added it. In that case, someone still needs it.
			String query = new StringBuilder().append("{\n\"type\": \"unsubscribe\",\n\"symbol\": \"").append(symbol)
					.append("\"\n}").toString();
			try {
				this.pushClient.send(query);
				System.out.println("Removed from push: " + query);
			} catch (Exception e) { // possibly because push is not connected any more due to whatever reason
				System.out.println("Symbol " + symbol + " couldn't be removed from push: Maybe push connection is interrupted?");
				// There is no need to notify the user via JOptionPane as there is no action required.
				// When the push connection is re-established, we will only re-subscribe to this symbol 
				// if a listener (watchlist or chart) is present.
			}
		}
	}

	/**
	 * Returns whether the push connection is initialized or not.
	 * @return true if initialized, false otherwise
	 */
	public boolean isPushInitialized() {
		return pushInitialized;
	}
	
	/**
	 * Gracefully close the push connection to the data provider (e.g. if the application is shut down or 
	 * if the data provider is about to be changed).
	 */
	public void stopPush() { 
		if (pushClient != null && pushClient.isConnected()) {
			// Unsubscribe from all symbols (probably not really necessary)
			LinkedList<String> pushSymbolsCopy = new LinkedList<String>(pushSymbols);
			for (String s : pushSymbolsCopy) {
				removeSymbolFromPush(s);
			}
			// Close connection
			pushClient.close();
		}
	}

	/**
	 * Receives and parses a push message from a websocket client and notifies all registered listeners (of type
	 * {@link Watchlist} or {@link StockerChart}) of the change. The listeners are informed no matter
	 * whether this information concerns them or not (i.e. they have to check on their side whether they
	 * are acutally interested in messages for the symbol that this message refers to).
	 * @param message the received message
	 */
	@Override
	public void pushMessageIncoming(String message) {
		JsonObject jo = null;
		JsonObject data = null;
		try {
			jo = JsonParser.parseString(message).getAsJsonObject();
			data = (JsonObject) ((JsonArray) jo.get("data")).get(0);
		} catch (Exception e) { // No point in throwing an error as this is only called by WSPushClient
			if (e.getMessage() != null) { 
				System.err.println("Error while parsing push message: " + e.getMessage());
			}
			return;
		}

		//System.out.println(data);
		String symbol = data.get("s").getAsString();
		long time = data.get("t").getAsLong() / 1000; // real time data is in ms instead of s!
		double price = data.get("p").getAsDouble();
		
		for (Watchlist w : listeningWatchlists) {
			w.onPushUpdate(symbol, time, price);
		}
		
		for (StockerChart w : listeningCharts) {
			w.onPushUpdate(symbol, time, price);
		}
		
		alarmManager.onPushUpdate(symbol, time, price);
	}
	
	/**
	 * Used by the websocket client to notify this data manager that something went wrong.
	 */
	@Override
	public void websocketConnectionClosedWithError() {
		System.out.println("Push Connection closed with error, trying to connect again...");
		
		// Try to reconnect (non-blocking in a separate thread)
		new Thread() {
			@Override
			public void run() {
				pushInitialized = false;
				try {
					initializePush();
					System.out.println("push established!");
				} catch (Exception e) {
					// called from WSPushClient only, so there is no point in throwing an error
					System.out.println("Push connection could not be initialized: " + e.getMessage());
				}
				// re-subscribe to all previously active push symbols
				System.out.println("push initialized: " + pushInitialized);
		
				while (!pushInitialized) { // wait until initializePush() has a new connection established
					try {
						Thread.sleep(5000);
					} catch (InterruptedException e) { }
				}
				LinkedList<String> pushSymbolsCopy = new LinkedList<String>(pushSymbols); 
				for (String s : pushSymbolsCopy) { // remove first to be sure that no subscription is present any more 
					removeSymbolFromPush(s);
				}
				for (String s : pushSymbolsCopy) { // re-subscribe
					addSymbolToPush(s);
				}
			};
		}.start();
	}
	
	/**
	 * Switch the active data provider to that which is currently set as active in the control's properties.
	 * The switch is actually done by setting the new active data provider in the properties; this method only makes
	 * sure that the change takes effect and that the push subscriptions are moved to the new provider. 
	 * Tell all listening watchlists and charts to pull new data; unsubscribe from the old provider, 
	 * end push connection, create new connection, subscribe to the new provider.
	 */
	public void switchDataProvider() {
		this.pushInitialized = false;
		
		// Ask watchlist(s) to pull new quotes
		for (Watchlist wl : listeningWatchlists) {
			wl.getNewQuotes(); // thread-safe
		}
		
		// Ask charts to pull new data 
		new Thread() { // only uses thread-safe methods
			public void run() {
				for (StockerChart c : listeningCharts) {
					c.resetData();
					c.initializeData(); // ask the chart to initialize itself again (will then get data from the new provider)
					c.repaint();
				}
			};
		}.start();
		
		// End push subscriptions at the old provider and register with the new (non-blocking in separate thread)
		new Thread() { // independent from GUI / Swing, hence thread-safe
			@Override
			public void run() {
				stopConnectThread = true;
				try {
					Thread.sleep(6000); // give any other running connection thread from initializePush() some time to quit
				} catch (InterruptedException e2) { } 
				stopConnectThread = false;
				LinkedList<String> pushSymbolsCopy = new LinkedList<String>(pushSymbols); 
				for (String s : pushSymbolsCopy) { 
					removeSymbolFromPush(s);
				}
				try {
					pushClient.closeBlocking();
				} catch (InterruptedException e1) { }
				try {
					initializePush();
				} catch (StockerDataManagerException e) {
					e.printStackTrace();
				}
				while(!pushInitialized) {
					try {
						Thread.sleep(5000);
					} catch (InterruptedException e) { }
				}
				for (String s : pushSymbolsCopy) {
					addSymbolToPush(s);
				}
			}
		}.start();
		
	}

	////////////////////
	// Pulling data ////
	////////////////////
	/**
	 * Pulls plot data according to the properties of the provider {@link ChartWatchItem}, and writes the 
	 * result into the same {@link ChartWatchItem}.
	 * @param w the {@link ChartWatchItem} which contains information about the data to be pulled; the result
	 *        will be written into that same {@link ChartWatchItem}
	 * @throws StockerDataManagerException
	 */
	public void getPlotData(ChartWatchItem w) throws StockerDataManagerException {
		// Calculate start and end times
		long timeTo = Instant.now().getEpochSecond();
		long timeFrom = 0L;
		switch (w.getInterval()) {
		// we want to show a certain past period, but we load a bit more because we will
		// later cut out the weekends where no data is available (and we want some reserve for the indicator calculation)
		// However, finnhub will not provide more than 500 candles, so the interval shouldn't be too long either!
		case I1MONTH: // pull last 300 months (approximately)
			timeFrom = timeTo - 300L * 30L*24L*60L*60L;
			break;
		case I1WEEK:  // pull last 300 weeks
			timeFrom = timeTo - 300L * 7L*24L*60L*60L;
			break;
		case I1DAY:   // pull last 450 days
			timeFrom = timeTo - 450L * 24L*60L*60L;
			break;
		case I1H:     // pull last 450 hours
			timeFrom = timeTo - 450L * 60L*60L;
			break;
		case I30MIN:  // pull last 225 hours
			timeFrom = timeTo - 225L * 60L*60L;
			break;
		case I15MIN:  // pull last 115 hours
			timeFrom = timeTo - 115L * 60L*60L;
			break;
		case I5MIN:   // pull last 2250 minutes = 37.5 h
			timeFrom = timeTo - 2250L * 60L;
			break;
		case I1MIN:   // pull last 450 minutes
			timeFrom = timeTo - 450L * 60L;
			break;
		}

		pullData(w, control.getPullURL(), w.getKey(), w.getInterval(), timeFrom, timeTo, control.getAPIToken());
		System.out.println(w.getKey() + ": Got " + w.getCandles().size() + " candles, timeFrom = " + timeFrom + ", timeTo = " + timeTo);
		
		// if not enough candles and interval smaller than "day", re-pull from an earlier time
		if (w.getCandles().size() < minCandles && w.getInterval().inSeconds() < 60L*60L*23L) { 
			timeFrom -= (long)(1.8*(timeTo-timeFrom)); // pull twice the interval we haven't got enough
			pullData(w, control.getPullURL(), w.getKey(), w.getInterval(), Math.max(timeFrom, 0L), timeTo, control.getAPIToken());
			System.out.println("Re-pulled larger time frame: " + Math.max(timeFrom, 0L) + ", size: " + w.getCandles().size());
			int i = 0;
			while (w.getCandles().size() < minCandles && i++ < 2) { // if still not enough candles
				timeFrom -= 60L*60L*24L; // subtract a whole day (e.g. a weekend day)
				pullData(w, control.getPullURL(), w.getKey(), w.getInterval(), Math.max(timeFrom, 0L), timeTo, control.getAPIToken());
				System.out.println("Re-pulled larger time frame: " + Math.max(timeFrom, 0L) + ", size: " + w.getCandles().size());
			}
		}
	}
	
	/**
	 * Pulls data via REST (writes the result into the same {@link WatchItem} w).
	 * @param w
	 * @param source
	 * @param symbol
	 * @param interval
	 * @param from
	 * @param to
	 * @param token
	 * @throws StockerDataManagerException
	 */
	private void pullData(ChartWatchItem w, String source, String symbol, EChartInterval interval,
			long from, long to, String token) throws StockerDataManagerException {
		// this assumes that a WatchItem for the pulled data already exists!
		StringBuilder sb = new StringBuilder();
		String category = "stock"; // no need to distinguish for crypto or forex
		sb.append(source).append("/").append(category.toString().toLowerCase()).append("/").append("candle");
		sb.append("?symbol=").append(symbol).append("&resolution=").append(interval.toPullString());
		sb.append("&from=").append(from).append("&to=").append(to).append("&token=").append(token);
		String query = sb.toString();
		System.out.println("pull data: query = " + query);

		JsonObject jo = httpRequest(query);

		String s = jo.get("s").getAsString();
		if (!s.equals("ok")) {
			throw new StockerDataManagerException("Problem while pulling data: Server reported: " + s);
		}

		LinkedList<Candle> candles = CandleParser.parseCandlesFromJsonObject(jo);
		w.setCandles(candles, interval);
	};
	
	/**
	 * Get a quote from the data provider for the provided {@link WatchlistItem}.
	 * @param w the {@link WatchlistItem} to be quoted; the result will be written into that same {@link ChartWatchItem}
	 * @throws StockerDataManagerException
	 */
	public void getQuote(WatchlistItem w) throws StockerDataManagerException {
		StringBuilder sb = new StringBuilder();
		sb.append(control.getPullURL()).append("/").append("quote");
		sb.append("?symbol=").append(w.getKey()).append("&token=").append(control.getAPIToken());
		String query = sb.toString();
		System.out.println("pull quote: query = " + query);

		JsonObject jo = httpRequest(query);
		
		double c = jo.get("c").getAsDouble();
		if (c == 0.0) {
			throw new StockerDataManagerException("Problem while pulling quote");
		}
		
		long time = jo.get("t").getAsLong();
		double price = jo.get("c").getAsDouble(); // here: c = current price, not close
		double closeYesterday = jo.get("pc").getAsDouble(); // pc = previous close
		w.setQuote(time, price, closeYesterday);
	}

	/**
	 * Perform an actual HTTP request.
	 * @param query
	 * @return
	 * @throws StockerDataManagerException
	 */
	private JsonObject httpRequest(String query) throws StockerDataManagerException {
		String data = "";
		try {
			HttpURLConnection conn = (HttpURLConnection) new URL(query).openConnection();
			conn.setRequestMethod("GET");
			conn.connect();
			int code = conn.getResponseCode();
			if (code >= 400) {
				throw new StockerDataManagerException("HTTP-Anfrage fehlgeschlagen, Status: " + code);
			}
			try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
				data = in.readLine();
				if (data.equals("<!DOCTYPE html>")) {
					throw new StockerDataManagerException("Fehler: HTML-Daten empfangen, möglicherweise falsche Parameter bei Anfrage");
				}
			}
		} catch (IOException ex) {
			throw new StockerDataManagerException("IOException: " + ex.getMessage());
		}

		JsonObject jo = null;
		try {
			JsonElement el = JsonParser.parseString(data);
			if (el.isJsonObject()) {
				jo = el.getAsJsonObject();
			} else { // if it's not an object, it's (likely) an array
				JsonArray jarr = JsonParser.parseString(data).getAsJsonArray();
				jo = new JsonObject();
				jo.add("", jarr); // pack the array into an object (beacause this is expected as return value)
			}
		} catch (JsonParseException | IllegalStateException e) {
			throw new StockerDataManagerException("Error while reading JSON file: " + e.getMessage());
		}
		return jo;
	}

}
