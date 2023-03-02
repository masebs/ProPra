package stocker.view;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JInternalFrame;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import javax.swing.SwingUtilities;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableRowSorter;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import stocker.control.StockerControl;
import stocker.model.ChartWatchItem;
import stocker.model.WatchlistItem;
import stocker.util.StockerDataManagerException;

/** 
 * An internal frame showing current data of a {@link WatchlistItem} in a table.
 * 
 * @author Marc S. Schneider
 */
public class Watchlist extends JInternalFrame implements IStockerDataListener {
	private static final long serialVersionUID = -7914767591355948230L;

	private StockerFrame parent;
	private StockerControl control;
	
	private WatchlistTableModel tableModel;
	private WatchlistTable table;
	private HashMap<String, WatchlistItem> itemMap;
	private ArrayList<String> keyList; // required for iterating over keys (what the HashMap doesn't support)
	
	/**
	 * Construct a new {@link Watchlist}.
	 * @param parent the parent window (i.e. the main frame, a {@link StockerFrame})
	 * @param control the {@link StockerControl} which is controlling this application
	 */
	public Watchlist(StockerFrame parent, StockerControl control) {
		super("Watchlist", true, true, true, true);
		this.setDefaultCloseOperation(HIDE_ON_CLOSE);
		this.parent = parent;
		this.control = control;
		this.itemMap = new HashMap<String, WatchlistItem>();
		this.keyList = new ArrayList<String>(10);
		
		setMinimumSize(new Dimension(420,300));

		JMenuBar menubar = new JMenuBar();
		this.setJMenuBar(menubar);
		JButton addBtn = new JButton("Wert hinzufügen");
		addBtn.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				control.showSearchDialog();
			}
		});
		menubar.add(addBtn);
		JButton openChartBtn = new JButton("Chart öffnen");
		openChartBtn.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				int[] selectedRows = table.getSelectedRows();
				if (selectedRows.length > 0) {
					for (int viewrow : selectedRows) {
						int row = table.getRowSorter().convertRowIndexToModel(viewrow);
						openChartWindow(row);
					}
				} 
				else {
					JOptionPane.showMessageDialog(parent,
							"Zeile in der Tabelle markieren,\num den zugehörigen Chart zu öffnen!", "Hinweis",
							JOptionPane.INFORMATION_MESSAGE);
				}
			}
		});
		menubar.add(openChartBtn);
		JButton deleteBtn = new JButton("Markierte entfernen");
		deleteBtn.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				// we need to go via the keys because we need to keep track of the rows while
				// some are already deleted
				int[] selectedRows = table.getSelectedRows();
				String[] keys = new String[selectedRows.length];
				for (int i = 0; i < selectedRows.length; i++) {
					keys[i] = tableModel.getValueAtAsString(table.getRowSorter().convertRowIndexToModel(selectedRows[i]), 0);
				}
				if (selectedRows.length > 0) {
					for (int i = 0; i < keys.length; i++) {
						removeFromWatchlist(table.getWatchlistTableModel().getRowFromKey(keys[i]));
					}
				}
			}
		});
		menubar.add(deleteBtn);

		// Set up table model
		tableModel = new WatchlistTableModel();
		table = new WatchlistTable(this.tableModel);
		table.setBounds(30, 40, 200, 300);
		TableRowSorter<WatchlistTableModel> sorter = new TableRowSorter<WatchlistTableModel>(tableModel);
		table.setRowSorter(sorter);
		List<RowSorter.SortKey> sortKeys = new ArrayList<RowSorter.SortKey>(1);
		sortKeys.add(new RowSorter.SortKey(0, SortOrder.ASCENDING));
		// we use a custom comparator for the two double columns (otherwise the values would be sorted alphabetically, not numerically)
		sorter.setSortKeys(sortKeys);
		Comparator<String> doubleComparator = new Comparator<String>() {
			@Override
			public int compare(String o1, String o2) { // tries to convert the entries to Double, then compares
				try {
					Double d1 = Double.parseDouble(o1);
					Double d2 = Double.parseDouble(o2);
					if (d1 > d2) {
						return 1;
					}
					else {
						return -1;
					}
				}
				catch (Exception e) { // this is likely because there is a string (like "keine Daten") in the table 
					return 1; 		  // -> always keep on top (or bottom, don't really care)
				}
			}
		};
		sorter.setComparator(3, doubleComparator);
		sorter.setComparator(4, doubleComparator);
		tableModel.addTableModelListener(new TableModelListener() {
			@Override
			public void tableChanged(TableModelEvent e) {
				table.repaint();
			}
		});

		JScrollPane sp = new JScrollPane(table);
		add(sp);

		// Add stuff for right-click actions in the table
		JPopupMenu popupMenu = new JPopupMenu();
		JMenuItem openChartItem = new JMenuItem("Chart öffnen");
		openChartItem.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				openChartBtn.doClick();
			}
		});
		popupMenu.add(openChartItem);
		JMenuItem deleteItem = new JMenuItem("Entfernen");
		deleteItem.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				deleteBtn.doClick();
			}
		});
		popupMenu.add(deleteItem);
		table.setComponentPopupMenu(popupMenu);
		popupMenu.addPopupMenuListener(new PopupMenuListener() {
			@Override
			public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
				SwingUtilities.invokeLater(new Runnable() {
					@Override
					public void run() {
						int rowAtPoint = table.rowAtPoint(SwingUtilities.convertPoint(popupMenu, new Point(0, 0), table));
						int[] sel = table.getSelectedRows();
						boolean previouslySelected = false;
						for (int i : sel) {
							if (i == rowAtPoint) {
								previouslySelected = true; // rowAtPoint is already selected, so keep it and everything else selected
								break;
							}
						}
						if (!previouslySelected) { // only if click was on a previously unselected row 
							table.setRowSelectionInterval(rowAtPoint, rowAtPoint);
						}
					}
				});
			}

			@Override
			public void popupMenuWillBecomeInvisible(PopupMenuEvent e) { }

			@Override
			public void popupMenuCanceled(PopupMenuEvent e) { }
		});

		// Add mouse listener for double click in the table (to open chart)
		table.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if (e.getClickCount() == 2) { // it's a double click
					if (table.getSelectedRow() != -1) {
						int row = table.getRowSorter().convertRowIndexToModel(table.getSelectedRow());
						openChartWindow(row);
					}
				}
			}
		});

		/////////////////
		pack();
		setVisible(true);

		// Register at the data manager and schedule update for a bit later
		control.addWatchlistListenerToDataManager(this);
	}

	/**
	 * Add a new item to this watchlist (automatically triggers pulling a quote and subscription to push).
	 * @param newitem the item to be added
	 */
	public void addItem(WatchlistItem newitem) { // possibly called outside of EDT (during deserialization!)
		// if already contained, do nothing
		if (keyList.contains(newitem.getKey())) {
			return;
		}
		
		// add to the watchlist's data administration
		keyList.add(newitem.getKey());
		itemMap.put(newitem.getKey(), newitem);
		
		// Register us at listener for that symbol at the alarm manager
		control.registerAlarmListener(this, newitem.getKey());
		
		// try to pull the quote (on failure, we will later show that in the table)
		new Thread() {
			public void run() {
				// add a dummy row for the time being (do it in invokeLater as we might be running in an independent thread)
				// (addItem itself might be running outside of EDT, so pulling this out of the thread is not an option)
				SwingUtilities.invokeLater(new Runnable() {
					@Override
					public void run() {
						tableModel.addRow(new String[] { newitem.getKey(), newitem.getName(), "lade...", "lade...", "lade..." });
					}
				});
				getQuoteForItem(newitem);
				control.addSymbolToPush(newitem.getKey());
			};
		}.start();
	}
	
	/**
	 * Pull a quote for the given item, and write the result into the same item.
	 * @param w the {@link WatchlistItem} for which a quote is requested
	 */
	public void getQuoteForItem(WatchlistItem w) {
		boolean dataPullSuccess, notPrivilegedError;
		try {
			control.getQuote(w);
			dataPullSuccess = true;
			notPrivilegedError = false;
		} catch (StockerDataManagerException e) {
			if (e.getMessage().contains("403")) {
				notPrivilegedError = true;
			}
			else {
				notPrivilegedError = false;
			}
			dataPullSuccess = false;
		}
		// now, set the table data with invokeLater in order to be thread-safe (using final variables)
		final boolean writeWatchlist = dataPullSuccess;
		final boolean writeNotPrivilegedError = notPrivilegedError;
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				if (writeWatchlist) {
					updateItem(w.getKey(), w.getTime(), w.getPrice());
				}
				else {
					if (writeNotPrivilegedError) {
						updateItem(w.getKey(), -1L, -1.0); // error -1 = not privileged
					}
					else {
						updateItem(w.getKey(), -2L, -2.0); // error -2 = other error on data pull
					}
				}
			}
		});
	}
	
	/**
	 * Pulls a new quote for all watched items, e.g. when switching data provider. This is done non-blocking 
	 * in separate threads.
	 */
	public void getNewQuotes() {
		for (String key : keyList) {
			new Thread() { // these are all independent from each other and from GUI, so we can start them all at once
				@Override  
				public void run() {
					getQuoteForItem(itemMap.get(key));
				};
			}.start();
		}
	}

	/**
	 * Update the item with the given key to the given time and price.
	 * @param key the key of the {@link WatchlistItem} to be updated
	 * @param time the unix timestamp at which the latest price is valid
	 * @param price the latest price
	 */
	public void updateItem(String key, long time, double price) {
		WatchlistItem w = itemMap.get(key);
		
		if (w == null) {
			return; // silently ignore when we don't have that value any more (reason is most likely that
					// a push update has arrived while the query for unsubsription was still ongoing)
		}
		
		if (time >= 0L) { // otherwise an error code is reported with time
			w.setData(time, price, w.getCloseYesterday());
			Double changeToday = Math.round(
					(w.getPrice() - w.getCloseYesterday()) / w.getCloseYesterday() * 100 * 100 ) / 100.0;
			String changeTodayString = (changeToday >= 0.0 ? "+" : "") + changeToday.toString();
			
			tableModel.updateRow(key, new String[] { w.getKey(), w.getName(), getDisplayTimeFromTimestamp(w.getTime()),
							getMoneyStringFromDouble(w.getPrice()), changeTodayString });
		}
		else { // time < 0, so there was an error
			if (time == -1L) { // not privileged
				tableModel.updateRow(key,
					new String[] { w.getKey(), w.getName(), "keine Berechtigung", "keine Berechtigung", "keine Berechtigung"});
			}
			else {
				tableModel.updateRow(key, new String[] { w.getKey(), w.getName(), "keine Daten", "keine Daten", "keine Daten"});
			}
		}
		
		// Pull a quote from time to time so that we don't miss the change of day
		// (effectively, this only updates closeYesterday after the data provider's day has changed)
		if (w.getQuoteTime() > 0L && Instant.now().getEpochSecond() - w.getQuoteTime() > 600) { // pull a quote every 10 minutes
			try {
				control.getQuote(w);
			} catch (StockerDataManagerException e) { } // silently ignore when failed, noone will notice unless day has actually changed
			//System.out.println("Re-pulled quote for " + w.getKey());
		}
	}

	/**
	 * Remove the item at the given postion (index) from the watchlist.
	 * @param index the position (index) of the item to be removed
	 */
	public void removeFromWatchlist(int index) {
		if (index > -1) {
			String key = tableModel.getValueAtAsString(index, 0); // get the key of the watchitem to be deleted
			tableModel.removeRow(index);
			itemMap.remove(key);
			keyList.remove(key);
			table.repaint();
			control.removeSymbolFromPush(key);
			control.unregisterAlarmListener(this, key);
		}
	}
	
	/**
	 * Remove the item with the given key from the watchlist. Not used within Stocker, but required to satisfy the
	 * requirements from IStockerTester.
	 * @param key the key of the item to be removed
	 */
	public void removeFromWatchlist(String key) {
		removeFromWatchlist(tableModel.getRowFromKey(key));
	}
	
	/**
	 * Get an array of all keys within the watchlist. Not used within Stocker, but required to satisfy the
	 * requirements from IStockerTester.
	 * @return an array containing the keys of all items in the watchlist
	 */
	public String[] getAllKeys() {
		int rowcount = tableModel.getRowCount();
		String[] keys = new String[rowcount];
		for (int i = 0; i < rowcount; i++) {
			keys[i] = tableModel.getValueAtAsString(i, 0);
		}
		return keys;
	}
	
	/**
	 * Clears the watchlist, i.e. removes all items.
	 */
	public void clear() {
		for (String key : keyList) {
			control.removeSymbolFromPush(key);
		}
		keyList.clear();
		itemMap.clear();
		tableModel.clear();
	}
	
	/**
	 * Create a {@link ChartWatchItem} from the {@link WatchlistItem} at the given index and ask the 
	 * main frame to open a {@link StockerChart} for this item
	 * @param row the row in the {@link WatchlistTable} for which a chart is requested
	 */
	private void openChartWindow(int row) {
		String key = tableModel.getValueAtAsString(row, 0);
		WatchlistItem w = itemMap.get(key);
		ChartWatchItem cwi = new ChartWatchItem(w.getKey(), w.getName());
		parent.openChartWindow(cwi, null, null, null, true);
	}

	/**
	 * Convert a unix timestamp into a human-readable time string.
	 * @param t the unix timestamp
	 * @return a human-readable time string
	 */
	private String getDisplayTimeFromTimestamp(long t) {
		return DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault()).format(Instant.ofEpochSecond(t));
	}

	/**
	 * Properly format a double value for printing.
	 * @param d the double value to be formatted
	 * @return the formatted String
	 */
	private String getMoneyStringFromDouble(double d) {
		return String.format("%.2f", d);
	}

	/**
	 * Receive a push update for the given key with the given values.
	 */
	@Override
	public void onPushUpdate(String key, long time, double price) {
		updateItem(key, time, price);
	}

	/**
	 * Serialize the contents of this watchlist into a {@link JsonArray} so that it can be reconstructed 
	 * in the future.
	 * @return a {@link JsonArray} containing the serialized watchlist content
	 */
	public JsonArray serializeToJson() {
		JsonArray ja = new JsonArray();
		Iterator<String> it = keyList.iterator();
		while (it.hasNext()) {
			WatchlistItem w = itemMap.get(it.next());
			JsonObject joItem = new JsonObject();
			joItem.addProperty("key", w.getKey());
			joItem.addProperty("name", w.getName());
			ja.add(joItem);
		}
		return ja;
	}

	/**
	 * Populate this watchlist by deserializing the content of the given {@link JsonArray}.
	 * @param ja a {@link JsonArray} containing serialized watchlist data
	 */
	public void deserializeFromJson(JsonArray ja) {
		for (int i = 0; i < ja.size(); i++) {
			JsonObject joItem = ja.get(i).getAsJsonObject();
			String key = joItem.get("key").getAsString();
			String name = joItem.get("name").getAsString();
			WatchlistItem w = new WatchlistItem(key, name);
			this.addItem(w);
		}
	}
}
