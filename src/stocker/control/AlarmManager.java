package stocker.control;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import stocker.model.ChartAlarm;
import stocker.view.IStockerDataListener;
import stocker.view.StockerChart;
import stocker.view.StockerFrame;

/** 
 * Central alarm manager which registers alarm listeners (Watchlists, StockerCharts) and alarms per symbol.
 * Listens to push updates and informs all stake holders when an alarm is triggered, added or removed.
 * 
 * @author Marc S. Schneider
 */
public class AlarmManager implements IStockerDataListener {
	/**
	 * HashMap storing the alarm lists for different symbols (each list containing all the alarms for one symbol)
	 */
	private HashMap<String, ArrayList<ChartAlarm>> alarms = new HashMap<String, ArrayList<ChartAlarm>>(10);
	/**
	 * Counts the number of listeners (Integer) which are listening to this symbol (String); alarm can be removed from
	 * model if nobody is listening any more
	 */
	private HashMap<String, Integer> listenerCount = new HashMap<String, Integer>();
	/**
	 * Provides an ArrayList with references to all charts which listen to the given symbol (String)
	 */
	private HashMap<String, ArrayList<StockerChart>> listeningCharts = new HashMap<String, ArrayList<StockerChart>>();
	
	private StockerFrame frame;
	
	/**
	 * Construct a new alarm manager.
	 * @param frame the main frame of the application, used as the parent for alarm notifications
	 */
	public AlarmManager(StockerFrame frame) {
		this.frame = frame;
	}
	
	/**
	 * Registers a listener and does some book keeping which enables to determine how many listeners there
	 * are for each symbol. A listener is usually a Watchlist or a StockerChart. Watchlists should register each
	 * each symbol they represent. StockerCharts represent only one symbol, so they need to register only once.
	 * @param listener the data listener
	 * @param symbol the ticker symbol in which this listener is interested
	 * @return
	 */
	public ArrayList<ChartAlarm> registerAlarmListener(IStockerDataListener listener, String symbol) {
		if (listenerCount.get(symbol) == null) { // nobody is currently listening to that symbol
			listenerCount.put(symbol, 1);
			listeningCharts.put(symbol, new ArrayList<StockerChart>(5)); // create and add a new charts list
			alarms.put(symbol, new ArrayList<ChartAlarm>(5));
		}
		else { // there is already someone listening -> increment the listener count 
			listenerCount.put(symbol, listenerCount.get(symbol) + 1);
		}
		if (listener instanceof StockerChart) { // if we are registering a StockerChart, add it to the new list
			listeningCharts.get(symbol).add((StockerChart)listener);
		}
		
		return alarms.get(symbol);
	}
		
	/**
	 * Unregister an alarm listener (usually a Watchlist or a StockerChart). To be called as soon as the listener
	 * is no longer interested in the symbol and does not need further alarm updates. Usually that is when the 
	 * symbol has been removed from the watchlist or the the chart window has been closed.
	 * @param listener the listener which is to be unregistered
	 * @param symbol the symbol that this listeners was registered for
	 */
	public void unregisterAlarmListener(IStockerDataListener listener, String symbol) {
		listenerCount.put(symbol, listenerCount.get(symbol) - 1);
		if (listenerCount.get(symbol) < 1) { // this is the last one listening to that symbol -> Remove all alarms!
			listenerCount.remove(symbol);
			listeningCharts.remove(symbol);
			alarms.remove(symbol);
		}
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
		ArrayList<ChartAlarm> al = alarms.get(symbol);
		boolean addAlarm = true;
		for (ChartAlarm a : al) {
			if (a.getValue() == ca.getValue()) {
				addAlarm = false;
				break;
			}
		}
		if (addAlarm) {
			al.add(ca);
			for (StockerChart sc : listeningCharts.get(symbol)) {
				if (!sc.equals(submittingChart)) {
					sc.addChartAlarm(ca, false);
				}
			}
		}
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
		ArrayList<ChartAlarm> al = alarms.get(symbol);
		for (ChartAlarm a : al) {
			if (a.getValue() == value) {
				al.remove(a);
				for (StockerChart sc : listeningCharts.get(symbol)) {
					if (!sc.equals(submittingChart)) {
						sc.removeChartAlarm(a, false);
					}
				}
				break;
			}
		}
	}
	
	/**
	 * Clear all alarms for one symbol. Only used for StockerTesterImpl.
	 * @param symbol the symbol for which the alarms should be cleared
	 */
	public void clearAlarms(String symbol) {
		alarms.get(symbol).clear();
		alarms.remove(symbol);
	}
	
	/**
	 * Clear all alarms for all symbols.
	 */
	public void clearAllAlarms() {
		alarms.clear();
		listenerCount.clear();   // clear listeners too in order to have a completely clean alarm manager
		listeningCharts.clear(); // (required e.g. for session reset)
	}
	
	/**
	 * Get all alarms for the specified symbol. Only used for StockerTesterImpl.
	 * @param symbol the symbol for which the alarms are requested
	 */
	public ArrayList<ChartAlarm> getAlarmsForSymbol(String symbol) {
		ArrayList<ChartAlarm> al = alarms.get(symbol);
		if (al == null) {
			return new ArrayList<ChartAlarm>();
		}
		else {
			return al;
		}
	}
	
	/**
	 * Get the symbols of all registered alarms. Only used for StockerTesterImpl.
	 * @return a Set of Strings representing all symbols for which alarms are registered. 
	 */
	public Set<String> getAlarmSymbols() {
		return alarms.keySet();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void onPushUpdate(String key, long time, double price) {
		ArrayList<ChartAlarm> al = alarms.get(key);
		if (al != null) { 
			for (int i = 0; i < al.size(); i++) {
				ChartAlarm a = al.get(i);
				double alarmCheck = a.check(price);
				if (alarmCheck != 0.0) {
					al.remove(a); // remove this alarm so it won't be fired again
					SwingUtilities.invokeLater(
					new Thread() {
						@Override
						public void run() {
							JOptionPane.showMessageDialog(frame,
									"Alarm erreicht für " + key + ":\n" + a.getValue() + ", " + (alarmCheck > 0.0 ? "steigend" : "fallend"),
									"Alarm erreicht!", JOptionPane.INFORMATION_MESSAGE);
							// after acknowledging notification: notify all listening StockerCharts
							ArrayList<StockerChart> charts = listeningCharts.get(key);
							for (StockerChart c : charts) {
								c.removeChartAlarm(a, false);
							}
						};
					});
				}
			}
		}
	}
	
	/** 
	 * Serialize the managed alarms into a {@link JsonArray}.
	 * @return a JsonArray containing all information required to restore the alarms at a later time
	 */
	public JsonArray serializeToJson() {
		JsonArray ja = new JsonArray();
		for (String symbol : alarms.keySet()) {
			ArrayList<ChartAlarm> al = alarms.get(symbol);
			JsonArray jaAlarms = new JsonArray();
			for (ChartAlarm a : al) {
				JsonObject joAlarm = new JsonObject();
				joAlarm.addProperty("value", a.getValue());
				joAlarm.addProperty("lastPrice", a.getLastPrice());
				jaAlarms.add(joAlarm);
			}
			JsonObject jo = new JsonObject();
			jo.addProperty("symbol", symbol);
			jo.add("values", jaAlarms);
			ja.add(jo);
		}
		return ja;
	}
	
	/**
	 * Deserializes and restores managed alarms from a {@link JsonArray}.
	 * @param ja the {@link JsonArray} to be read 
	 */
	public void deserializeFromJson(JsonArray ja) {
		for (int i = 0; i < ja.size(); i++) {
			JsonObject jo = ja.get(i).getAsJsonObject();
			String symbol = jo.get("symbol").getAsString();
			JsonArray values = jo.get("values").getAsJsonArray();
			for (int j = 0; j < values.size(); j++) {
				JsonObject joAlarm = values.get(j).getAsJsonObject();
				ChartAlarm ca = new ChartAlarm(joAlarm.get("value").getAsDouble(), joAlarm.get("lastPrice").getAsDouble());
				this.addAlarm(ca, symbol, null);
			}
		}
	}
}
