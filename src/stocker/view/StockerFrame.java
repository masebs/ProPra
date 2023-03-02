package stocker.view;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyVetoException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;

import javax.swing.AbstractButton;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JDesktopPane;
import javax.swing.JFrame;
import javax.swing.JInternalFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JSeparator;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;
import javax.swing.event.InternalFrameAdapter;
import javax.swing.event.InternalFrameEvent;
import com.google.gson.JsonArray;

import stocker.control.StockerControl;
import stocker.model.ChartWatchItem;
import stocker.model.WatchlistItem;
import stocker.util.EChartType;
import stocker.util.JMenuItemFixed;

/**
 * The main frame of the application.
 * 
 * @author Marc S. Schneider
 */
public class StockerFrame extends JFrame {
	private static final long serialVersionUID = 7687506805635328463L;

	private StockerControl control;
	private Watchlist watchlist;
	private ArrayList<StockerChart> chartList = new ArrayList<StockerChart>(10);
	private Point lastChartPos = null;

	private final JDesktopPane desktopPane = new JDesktopPane();
	private final int SESSION_MENU_NR = 3; // the index of the windows menu
	private final int WINDOW_MENU_NR  = 4; // the index of the windows menu
	private ButtonGroup sessionRadioBtnGroup = new ButtonGroup();

	/**
	 * Constructs a new StockerFrame.
	 * @param control the {@link StockerControl} which controls this frame 
	 */
	public StockerFrame(StockerControl control) {
		this.control = control;

		// General stuff regarding main window
		this.setTitle("Stocker - Marc S. Schneider 3254631");
		try {
			for (LookAndFeelInfo laf : UIManager.getInstalledLookAndFeels()) {
				if ("Nimbus".equals(laf.getName())) { // available: Nimbus, Metal, GTK+, CDE/Motif
					UIManager.setLookAndFeel(laf.getClassName());
					break;
				}
			}
		} catch (Exception e) { } // no problem, system LAF will be set in the following lines
		if (!UIManager.getLookAndFeel().getClass().toString().equals("class javax.swing.plaf.nimbus.NimbusLookAndFeel")) {
			// Use system look and feel if the preferred one is not available
			try {
				UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
			} catch (Exception e) {
				System.err.println("Error while setting Swing look and feel!");
				e.printStackTrace();
			}
		}

		this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		this.setExtendedState(JFrame.MAXIMIZED_BOTH); // open maximized first - might be resized by control.restoreSession()

		ImageIcon icon = new ImageIcon("icons/icon.png");
		this.setIconImage(icon.getImage());
		this.createMenu();

		// Create watchlist frame
		this.watchlist = new Watchlist(this, control);
		desktopPane.add(watchlist, BorderLayout.EAST);

		this.add(desktopPane, BorderLayout.CENTER);
		this.setMinimumSize(new Dimension(800, 600));

		this.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				control.shutdown(false);
			}
		});

		watchlist.setSize(550,450);
		watchlist.setLocation(0, 0);
		this.setVisible(true); // setVisible is called by StockerControl after initialization is complete
		watchlist.show();
	}

	///////////
	// Serialization
	//////////
	/**
	 * Asks the watchlist to serialize its contents into a {@link JsonArray}.
	 * @return the {@link JsonArray} containing the serialized watchlist entries
	 */
	public JsonArray serializeWatchlistSymbolsToJson() {
		return watchlist.serializeToJson();
	}

	/**
	 * Asks the watchlist to set its content by deserializing from the given {@link JsonArray} (non-blocking).
	 * @param ja the {@link JsonArray} containing the serialized watchlist data
	 */
	public void deserializeWatchlistSymbolsFromJson(JsonArray ja) {
		new Thread() {
			@Override
			public void run() {
				watchlist.deserializeFromJson(ja);
			}
		}.start();
	}

	/**
	 * Request a new {@link WatchlistItem} to be added to this frame's watchlist. Can be called from anyone not 
	 * having a direct reference to the watchlist, like the search dialog.
	 * @param newitem the new {@link WatchlistItem} to be shown in the watchlist
	 */
	public void addToWatchlist(WatchlistItem newitem) {
		watchlist.addItem(newitem);
	}
	
	/**
	 * Get the {@link Watchlist}. Not used in the Stocker application, but required to satisfy the requirements of 
	 * IStockerTester.
	 */
	public Watchlist getWatchlist() {
		return this.watchlist;
	}

	/**
	 * Open a new chart window. 
	 * @param item the {@link ChartWatchItem} to be shown in the new chart window
	 * @param type the type of chart (an instance of {@link EChartType}), or null to use the default type
	 * @param position the position at which this window should be place, or null to use a default position
	 * @param size the desired size of the new window, or null to use a default size
	 * @return the newly created {@link StockerChart}
	 */
	public StockerChart openChartWindow(ChartWatchItem item, EChartType type, Point position, Dimension size,
			boolean gridlinesShown) {
		// Get the title for the new chart
		String windowName = getUniqueTitle(item.getName() + ", Kerzen, I = " + item.getInterval().toString());

		// Determine chart type (candle or line)
		EChartType charttype; // can't change parameter of outside of this anonymous thread class directly
		if (type == null) {
			charttype = control.getPropertyChartType();
		}
		else {
			charttype = type;
		}
		
		// Create and add new chart window
		StockerChart chart = new StockerChart(StockerFrame.this, item, charttype, windowName, size, gridlinesShown, control);
		chartList.add(chart);
		desktopPane.add(chart, BorderLayout.WEST);
		chart.moveToFront();
		
		// Create an ActionListener for the menu entry for that new chart window
		ActionListener listener = new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				for (JInternalFrame intf : desktopPane.getAllFrames()) {
					if (intf.getTitle() == chart.getTitle()) {
						try {
							intf.setIcon(false);
							intf.moveToFront();
							intf.setSelected(true);
						} catch (PropertyVetoException e2) { }; // doesn't want to do that, so leave it
					}
				}
			}
		};
		// Add menu entry and a listener which removes it again when the chart window is closed 
		addDynamicMenuEntry(WINDOW_MENU_NR, false, chart.getTitle(), listener);
		chart.addInternalFrameListener(new InternalFrameAdapter() {
			@Override
			public void internalFrameClosing(InternalFrameEvent e) {
				StockerFrame.this.removeDynamicMenuEntry(WINDOW_MENU_NR, item.getKey());
				StockerFrame.this.chartList.remove(chart);
			}
		});
		
		// Show as soon as possible (before the initialization)
		if (position == null) { // no position specified, e.g. chart is freshly opened, not restored
			// vary chart position slightly so that multiple new charts are not exactly on top of each other
			if (lastChartPos == null || lastChartPos.x > (getSize().width - chart.getSize().width) / 2 + 60) {
				lastChartPos = new Point((getSize().width - chart.getSize().width) / 2 - 30, (getSize().height - chart.getSize().height) / 2 - 30);
			}
			else {
				lastChartPos = new Point(lastChartPos.x + 10, lastChartPos.y + 10);
			}
			chart.setLocation(lastChartPos);
		}
		else { // chart position was specified -> set it
			chart.setLocation(position);
		}
		
		// now, initialize (in particular, pull data, which might take a while)
		chart.setVisible(true);
		chart.initializeData();
		
		// if initialization was not successful on the first try: start thread for retries
		// called in a usual thread, not by invokeLater, as the only swing operation called is repaint(), which is thread-safe
		new Thread() { 
			@Override
			public void run() {
				int count = 0;
				while (!chart.isInitialized() && !chart.notPrivilegedError && count++ < 5) {
					try {
						Thread.sleep(7000);
					} catch (InterruptedException e) { }
					if (!chart.isInitialized()) { // if still not initialized (maybe background process has completed in the mean time)
						chart.initializeData();
						chart.repaint();
					}
				}
			}
		}.start();
		
		return chart;
	}

	/**
	 * Get all child windows.
	 * @return an array containing all child windows
	 */
	// Used by session management in controller
	public JInternalFrame[] getChildWindows() {
		return desktopPane.getAllFrames();
	}
	
	/**
	 * Get the child window with the specified title.
	 * @param title the title of the child window
	 * @return the child window, or null if a child window with that title does not exist
	 */
	private JInternalFrame getChildWindow(String title) {
		JInternalFrame[] children = desktopPane.getAllFrames();
		for (JInternalFrame f : children) {
			if (f.getTitle().equals(title))
				return f;
		}
		return null;
	}
	
	/**
	 * Determine a unique title based on the given title, by appending a number if another child window
	 * with the same title already exists.
	 * @param title the desired new title
	 * @return a unique version of the desired title (either the original title or a number appended)
	 */
	public String getUniqueTitle(String title) {
		if (getChildWindow(title) != null) {
			title = title + " <2>";
			int i = 3;
			while (getChildWindow(title) != null) {
				title = title.substring(0, title.lastIndexOf('<')) + "<" + i++ + ">";
			}
		}
		return title;
	}
	
	/**
	 * Notifies this main frame that a child window has been renamed (e.g. on interval change). 
	 * The entries in the Windows menu will be adapted to show the new name.
	 * @param titleold the old window title
	 * @param titlenew the new window title
	 */
	public void windowRenamed(String titleold, String titlenew) {
		// if a window is renamed, this renames the menu item in the windows menu (we actually rename it instead of 
		// replacing because then we can keep the listeners)
		JMenu windowMenu = this.getJMenuBar().getMenu(WINDOW_MENU_NR);

		// create array list of all menu items because we need to sort it again after renaming
		ArrayList<JMenuItem> itemList = new ArrayList<JMenuItem>(windowMenu.getItemCount());
		for (int i = 0; i < windowMenu.getItemCount(); i++) {
			JMenuItem mi = windowMenu.getItem(i);
			if (mi != null ) {
				if (!mi.getText().equals("Watchlist")) {
					itemList.add(mi);
				}
				if (mi.getText().equals(titleold)) {
					mi.setText(titlenew); // rename it!
				}
			}
		}
		itemList.sort(new Comparator<JMenuItem>() { // sort JMenuItems alphabetically
			@Override
			public int compare(JMenuItem o1, JMenuItem o2) {
				return o1.getText().compareTo(o2.getText());
			}
		});
		for (JMenuItem mi : itemList) {
			windowMenu.remove(mi); // remove from where it currently is
			windowMenu.add(mi);    // add to the end, so in the end all will be sorted
		}
	}

	/**
	 * Add a new menu entry including an appropriate ActionListener, e.g. for the sessions or the windows menu.
	 * @param menuNr the index of the menu to which the item should be added
	 * @param radioItem true if the menu entry should be a radio button, false otherwise
	 * @param name the text of the menu item
	 * @param listener an {@link ActionListener} to be connected with the menu item
	 * @return the {@link JMenuItem} that has just been added
	 */
	private JMenuItem addDynamicMenuEntry(int menuNr, boolean radioItem, String name, ActionListener listener) {
		JMenuItem item;
		if (radioItem) {
			item = new JRadioButtonMenuItem(name);
			if (menuNr == SESSION_MENU_NR) {
				sessionRadioBtnGroup.add(item);
			}
		}
		else {
			item = new JMenuItem(name);
		}
		item.addActionListener(listener);

		JMenu menu = this.getJMenuBar().getMenu(menuNr);
		menu.add(item); // add new window provisionally (entries will be sorted in a second)

		ArrayList<JMenuItem> itemList = new ArrayList<JMenuItem>(menu.getItemCount());
		for (int i = 0; i < menu.getItemCount(); i++) {
			JMenuItem mi = menu.getItem(i);
			if (mi != null && !(mi instanceof JMenuItemFixed)) {
				itemList.add(mi);
			}
		}
		itemList.sort(new Comparator<JMenuItem>() { // sort JMenuItems alphabetically
			@Override
			public int compare(JMenuItem o1, JMenuItem o2) {
				return o1.getText().compareTo(o2.getText());
			}
		});
		for (JMenuItem mi : itemList) {
			menu.remove(mi); // remove from where it currently is
			menu.add(mi);    // add to the end, so in the end all will be sorted
		}
		return item;
	}
	
	/**
	 * Add a new menu entry for a session.
	 * @param name the name of the session to be added
	 * @return the {@link JRadioButtonMenuItem} that has just been added
	 */
	public JRadioButtonMenuItem addSessionMenuEntry(String name) {
		ActionListener listener = new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (JOptionPane.showConfirmDialog(StockerFrame.this, "Durch das Laden der gespeicherten Sitzung werden alle aktuell offenen\n"
						+ "Chartfenster geschlossen und alle Einträge aus der Watchlist entfernt.\nWirklich die gespeicherte Sitzung laden?",
						"Wirklich laden?", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
					if (JOptionPane.showConfirmDialog(StockerFrame.this, 
						"Aktuelle Sitzung speichern?", "Sitzung speichern?", JOptionPane.YES_NO_OPTION)
							== JOptionPane.YES_OPTION) {
						control.saveCurrentSession();
					}
					control.resetSession();
					String name = ((AbstractButton)e.getSource()).getText();
					control.restoreSession(name);
				}
				else {
					StockerFrame.this.sessionRadioBtnGroup.clearSelection();
				}
			}
		};
		return (JRadioButtonMenuItem) addDynamicMenuEntry(SESSION_MENU_NR, true, name, listener);
	}

	/**
	 * Remove a dynamic menu entry from a menu (e.g. a session or a child window).
	 * @param menuNr the index of the menu from which to remove the item
	 * @param name the text of the menu item
	 * @return the {@link JMenuItem} that has just been added
	 */
	private void removeDynamicMenuEntry(int menuNr, String name) {
		JMenu menu = this.getJMenuBar().getMenu(menuNr);
		for (int i = 0; i < menu.getItemCount(); i++) {
			JMenuItem item = menu.getItem(i);
			if ((item != null) && (item.getText().equals(name))) {
				menu.remove(item);
			}
		}
	}
	
	/**
	 * Removes all chart windows from the menu.
	 */
	public void removeAllChartWindowsFromMenu() {
		JMenu menu = this.getJMenuBar().getMenu(WINDOW_MENU_NR);
		for (int i = 0; i < menu.getItemCount(); i++) {
			JMenuItem item = menu.getItem(i);
			if (item != null && !item.getText().equals("Watchlist")) {
				menu.remove(item);
			}
		}
	}
	
	/**
	 * Should be called whenever a child window is closing. Does some housekeeping like removing the closed window
	 * from the menu.
	 */
	public void onChildWindowClosing(String title) {
		removeDynamicMenuEntry(WINDOW_MENU_NR, title);
	}
	
	/**
	 * Ensure that all child windows have a size larger than the minimum size from the control's properties. 
	 * Enlarge them if they are too small. Should be called after the minimum child minimum size (in the props)
	 * has been changed.
	 */
	public void enforceMinimumChildWindowSize() {
		Dimension d = control.getPropertyMinimumSize();
		JInternalFrame[] farr = getChildWindows();
		for (JInternalFrame f : farr) {
			if (!(f.getTitle().equals("Watchlist"))) { // don't set for watchlist
				if (f.getSize().width < d.width) {
					SwingUtilities.invokeLater(new Thread() {
						@Override
						public void run() {
							f.setSize(new Dimension(d.width, f.getSize().height));
						}
					});
				}
				if (f.getSize().height < d.height) {
					SwingUtilities.invokeLater(new Thread() {
						@Override
						public void run() {
							f.setSize(new Dimension(f.getSize().width, d.height));
						}
					});
				}
				f.setMinimumSize(d); // set new minimum size, no matter wheter increased or decreased
			}
		}
	}
	
	/**
	 * Update the chart-relevant properties in each StockerChart (e.g. on property change).
	 */
	public void updateChartProperties() {
		JInternalFrame[] children = getChildWindows();
		for (JInternalFrame f : children) {
			if (f instanceof StockerChart) {
				((StockerChart) f).updateChartProperties();
			}
		}
	}
	
	/**
	 * Sets the size and position of the watchlist window to the given values (e.g. when restoring a session).
	 * @param size the size (Dimension) of the watchlist window
	 * @param position the position (Point) of the watchlist window
	 */
	public void setWatchlistSizeAndPosition(Dimension size, Point position) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				watchlist.setLocation(position);
				watchlist.setSize(size);
				try {
					watchlist.setIcon(false);
					watchlist.moveToFront();
					watchlist.setSelected(true);
				} catch (PropertyVetoException e) { 
					System.err.println("Watchlist: PropertyVetoException!");
				} // ignore if it doesn't want
			}
		});
	}

	/**
	 *  Private method to create the menu bar and all its content.
	 */
	private void createMenu() {
		// Create Menu Bar
		JMenuBar menubar = new JMenuBar();
		this.setJMenuBar(menubar);
		menubar.setAlignmentX(LEFT_ALIGNMENT);

		// Menu Bar: Datei
		JMenu menu_file = new JMenu("Datei");
		menu_file.setMnemonic(KeyEvent.getExtendedKeyCodeForChar('d'));
		JMenuItem menuItem_exit = new JMenuItem("Beenden");
		menuItem_exit.setMnemonic(KeyEvent.getExtendedKeyCodeForChar('b'));
		menuItem_exit.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				control.shutdown(true);
			}
		});
		menu_file.add(menuItem_exit);
		menubar.add(menu_file);

		// Menu Bar: Suche
		JMenu menu_search = new JMenu("Wert suchen");
		menu_search.setMnemonic(KeyEvent.getExtendedKeyCodeForChar('s'));
		JMenuItem menu_searchStock = new JMenuItem("Suche starten");
		menu_searchStock.setMnemonic(KeyEvent.getExtendedKeyCodeForChar('u'));
		KeyStroke searchStockHotkey = KeyStroke.getKeyStroke(KeyEvent.VK_F, KeyEvent.CTRL_DOWN_MASK);
		menu_searchStock.setAccelerator(searchStockHotkey);
		menu_searchStock.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				control.showSearchDialog();
			}
		});
		menu_search.add(menu_searchStock);
		menubar.add(menu_search);

		// Menu Bar: Einstellungen
		JMenu menu_settings = new JMenu("Einstellungen");
		menu_settings.setMnemonic(KeyEvent.getExtendedKeyCodeForChar('e'));
		JMenuItem menuItem_settings = new JMenuItem("öffnen");
		KeyStroke settingsHotkey = KeyStroke.getKeyStroke(KeyEvent.VK_E, KeyEvent.CTRL_DOWN_MASK);
		menuItem_settings.setAccelerator(settingsHotkey);
		menuItem_settings.setMnemonic(KeyEvent.getExtendedKeyCodeForChar('f'));
		menuItem_settings.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				control.showSettingsDialog();
			}
		});
		menu_settings.add(menuItem_settings);
		menubar.add(menu_settings);

		// Menu Bar: Sessions
		JMenu menu_sessions = new JMenu("Sitzung");
		JMenuItem menuItem_saveSession = new JMenuItemFixed("Aktuelle Sitzung speichern");
		menuItem_saveSession.setMnemonic(KeyEvent.getExtendedKeyCodeForChar('a'));
		KeyStroke saveSessionHotkey = KeyStroke.getKeyStroke(KeyEvent.VK_S, KeyEvent.CTRL_DOWN_MASK);
		menuItem_saveSession.setAccelerator(saveSessionHotkey);
		menuItem_saveSession.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				String curSessionName = control.saveCurrentSession();
				JOptionPane.showMessageDialog(StockerFrame.this, "Die aktuelle Sitzung wurde unter dem Namen \""
						+ curSessionName + "\" gespeichert.", "Hinweis", JOptionPane.INFORMATION_MESSAGE);
			}
		});
		menu_sessions.add(menuItem_saveSession);
		JMenuItem menuItem_saveAsNewSession = new JMenuItemFixed("Unter neuem Namen speichern");
		menuItem_saveAsNewSession.setMnemonic(KeyEvent.getExtendedKeyCodeForChar('n'));
		menuItem_saveAsNewSession.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				String sessionName = JOptionPane.showInputDialog(StockerFrame.this, 
						"Bitte geben Sie einen Namen für die aktuelle Sitzung ein:",
						"Namen eingeben", JOptionPane.QUESTION_MESSAGE);
				if (sessionName != null) { // null = dialog canceled
					if (sessionName.isBlank()) {
						JOptionPane.showMessageDialog(StockerFrame.this, "Ungültiger Sitzungsname, Sitzung nicht gespeichert", 
								"Ungültiger Name", JOptionPane.WARNING_MESSAGE);
					}
					else if (control.verifySessionName(sessionName)) { // a session with this name exists already
						JOptionPane.showMessageDialog(StockerFrame.this, "Der Sitzungsname ist schon vergeben.\n"
								+ "Bitte wählen Sie einen anderen. Sitzung nicht gespeichert", 
								"Ungültiger Name", JOptionPane.WARNING_MESSAGE);
					}
					else {
						control.saveSession(sessionName);
						addSessionMenuEntry(sessionName).setSelected(true);
					}
				}
			}
		});
		menu_sessions.add(menuItem_saveAsNewSession);
		JMenuItem menuItem_removeSession = new JMenuItemFixed("Aktive Sitzung löschen");
		menuItem_removeSession.setMnemonic(KeyEvent.getExtendedKeyCodeForChar('l'));
		menuItem_removeSession.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (control.getSessionCount() <= 1) {
					JOptionPane.showMessageDialog(StockerFrame.this, "Es ist nur eine Sitzung definiert.\nDiese kann nicht entfernt werden.", "Fehler", JOptionPane.ERROR_MESSAGE);
				}
				else if (JOptionPane.showConfirmDialog(StockerFrame.this, "Die aktuell aktive Sitzung wird entfernt.\n"
						+ "Alle Charts werden geschlossen und die Watchlist wird geleert.\nAktive Sitzung löschen?",
						"Wirklich entfernen?", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
					String removedSessionName = control.removeCurrentSession(); 
					removeDynamicMenuEntry(SESSION_MENU_NR, removedSessionName);
					Iterator<AbstractButton> btnIt = StockerFrame.this.sessionRadioBtnGroup.getElements().asIterator();
					while (btnIt.hasNext()) {
						JRadioButtonMenuItem rbm = (JRadioButtonMenuItem) btnIt.next();
						if (rbm.getText().equals(control.getCurrentSessionName())) {
							rbm.setSelected(true);
						}
					}
				}
			}
		});
		menu_sessions.add(menuItem_removeSession);
		JMenuItem menuItem_resetSession = new JMenuItemFixed("Sitzung zurücksetzen");
		menuItem_resetSession.setMnemonic(KeyEvent.getExtendedKeyCodeForChar('z'));
		menuItem_resetSession.setDisplayedMnemonicIndex(8);
		menuItem_resetSession.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (JOptionPane.showConfirmDialog(StockerFrame.this, "Das Zurücksetzen der Sitzung schließt alle Chartfenster\n"
						+ "und entfernt alle Einträge aus der Watchlist.\nWirklich zurücksetzen?",
						"Wirklich zurücksetzen?", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
					control.resetSession(); 
				}
			}
		});
		menu_sessions.add(menuItem_resetSession);
		menu_sessions.add(new JSeparator());
		menubar.add(menu_sessions);
				
		// Menu Bar: Fenster
		JMenu menu_windows = new JMenu("Fenster");
		menu_windows.setMnemonic(KeyEvent.getExtendedKeyCodeForChar('f'));
		JMenuItem menuItem_watchlist = new JMenuItemFixed("Watchlist");
		menuItem_watchlist.setMnemonic(KeyEvent.getExtendedKeyCodeForChar('w'));
		KeyStroke watchlistHotkey = KeyStroke.getKeyStroke(KeyEvent.VK_W, KeyEvent.CTRL_DOWN_MASK);
		menuItem_watchlist.setAccelerator(watchlistHotkey);
		menuItem_watchlist.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				for (JInternalFrame w : desktopPane.getAllFrames()) {
					if (w.getClass() == Watchlist.class) {
						try {
							w.setVisible(true);
							w.setIcon(false);
							w.moveToFront();
							w.setSelected(true);
						} catch (PropertyVetoException e1) {
							// Watchlist doesn't want to get deiconified or selected for whatever reason, so
							// we leave it as it is
						}
					}
				}
			}
		});
		menu_windows.add(menuItem_watchlist);
		menu_windows.add(new JSeparator());
		menubar.add(menu_windows);
	}

}
