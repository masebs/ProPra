package stocker.dialog;

import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;

import stocker.control.StockerControl;
import stocker.model.ChartWatchItem;
import stocker.model.WatchlistItem;
import stocker.view.StockerFrame;

/** 
 * A dialog for the search for an investment item. User can enter a search text, results from the data provider
 * will be requested and shown, and the user can select to add an item to the watchlist or to open a chart.
 *
 * @author Marc S. Schneider
 */
public class StockerSearchDialog extends JDialog implements ActionListener, ISearchDataReceiver {

	private static final long serialVersionUID = 9189242885990536427L;
	private StockerFrame parent;
	private StockerControl control;
	private JTextField searchText;
	private JTable resultTable;
	private DefaultTableModel resultTableModel;

	/**
	 * Construct a new search dialog.
	 * @param parent the parent window (i.e. the main frame, a {@link StockerFrame})
	 * @param control the {@link StockerControl} which is controlling this application
	 */
	public StockerSearchDialog(StockerFrame parent, StockerControl control) { 
		super(parent/* , Dialog.ModalityType.APPLICATION_MODAL */);
		this.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
		this.parent = parent;
		this.control = control;
		this.setTitle("Werte suchen");

		getContentPane().setLayout(new BoxLayout(getContentPane(), BoxLayout.Y_AXIS));
		
		this.setPreferredSize(new Dimension(350,350));
		this.setMinimumSize(new Dimension(300,250));

		JPanel centerPanel = new JPanel();
		centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
		this.searchText = new JTextField("Suche nach...");
		searchText.setPreferredSize(new Dimension(200, 30));
		JButton btnSearch = new JButton("Suchen");
		JPanel searchLinePanel = new JPanel();
		searchLinePanel.setLayout(new FlowLayout());
		searchLinePanel.add(searchText);
		searchLinePanel.add(btnSearch);
		searchLinePanel.setPreferredSize(new Dimension(300, 50));
		centerPanel.add(searchLinePanel);
		btnSearch.addActionListener(this);
		searchText.addActionListener(new ActionListener() { // start search after enter key pressed in text field
			@Override
			public void actionPerformed(ActionEvent e) {
				btnSearch.doClick();
			}
		});

		this.resultTableModel = new DefaultTableModel(0, 2);
		this.resultTable = new JTable(resultTableModel);
		resultTable.setDefaultEditor(Object.class, null);
		resultTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<DefaultTableModel>(resultTableModel);
		resultTable.setRowSorter(sorter);

		JScrollPane scrollPane = new JScrollPane(resultTable);
		scrollPane.setPreferredSize(new Dimension(300, 200));
		centerPanel.add(scrollPane);
		add(centerPanel);

		JButton btnAdd = new JButton("In die Watchlist");
		JButton btnChart = new JButton("Chart öffnen");
		JButton btnClose = new JButton("Schließen");
		JPanel lowerBtnPanel = new JPanel();
		lowerBtnPanel.setLayout(new FlowLayout());
		lowerBtnPanel.add(btnAdd);
		lowerBtnPanel.add(btnChart);
		lowerBtnPanel.add(btnClose);
		add(lowerBtnPanel);
		btnAdd.addActionListener(this);
		btnChart.addActionListener(this);
		btnClose.addActionListener(this);

		resultTableModel.setColumnIdentifiers(new String[] { "Name", "Symbol" });

		searchText.select(0, searchText.getText().length());
		addWindowListener(new WindowAdapter() { // set focus to text field as soon as dialog is open
			public void windowOpened(WindowEvent e) {
				searchText.requestFocus();
			}
		});

		pack();
		setLocationRelativeTo(parent);
	}

	/**
	 * Action handler which reacts on any button presses.
	 */
	@Override
	public void actionPerformed(ActionEvent e) {
		if (e.getActionCommand().equals("Suchen")) {
			control.searchStocks(searchText.getText(), this);
			resultTableModel.setRowCount(0);
			resultTableModel.addRow(new String[] {"suche...", "suche..."});
		} else if (e.getActionCommand().equals("In die Watchlist")) {
			int[] selectedRows = this.resultTable.getSelectedRows();
			if (selectedRows.length > 0) {
				for (int row : selectedRows) {
					String displayname = (String) this.resultTable.getValueAt(row, 0);
					String key = (String) this.resultTable.getValueAt(row, 1);
					WatchlistItem newitem = new WatchlistItem(key, displayname);
					parent.addToWatchlist(newitem);
				}
			} else {
				JOptionPane.showMessageDialog(this, "Zum Hinzufügen, bitte Werte aus der Ergebnisliste auswählen");
			}
		} else if (e.getActionCommand().equals("Chart öffnen")) {
			int[] selectedRows = this.resultTable.getSelectedRows();
			if (selectedRows.length > 0) {
				for (int row : selectedRows) {
					String displayname = (String) this.resultTable.getValueAt(row, 0);
					String key = (String) this.resultTable.getValueAt(row, 1);
					ChartWatchItem newitem = new ChartWatchItem(key, displayname);
					parent.openChartWindow(newitem, null, null, null, true); 
				}
			} else {
				JOptionPane.showMessageDialog(this, "Zum Hinzufügen, bitte Werte aus der Ergebnisliste auswählen");
			}
		} else if (e.getActionCommand().equals("Schließen")) {
			this.setVisible(false);
			this.dispose();
		}

	}
	
	/**
	 * Called by the data manager as soon as the search data is ready. Displays the data to the user.
	 */
	@Override
	public void searchDataReady(String[][] data) { 
		// Belongs to Interface IDataReceiver: handle incoming search data
		// data[i][0]: name, data[i][1]: symbol
		String[][] displaydata;
		resultTableModel.setRowCount(0);
		// if only NYSE/NASDAQ stocks should be shown, filter out any symbols with a dot (usually marking another exchange)
		if (control.getPropertyShowOnlyUSStocks()) {
			ArrayList<Integer> idxList = new ArrayList<Integer>();
			for (int i = 0; i < data.length; i++) {
				if (!data[i][1].contains(".")) {
					idxList.add(i);
				}
			}
			displaydata = new String[idxList.size()][2];
			for (int i = 0; i < idxList.size(); i++) {
				displaydata[i] = data[idxList.get(i)];
			}
		} else { // all exchanges requested, so just show all results
			displaydata = data;
		}
			
		// add the data to the table model of the JTable
		if (displaydata.length > 0) { 
			for (String[] s : displaydata) {
				resultTableModel.addRow(s);
			}
		}

	}
}
