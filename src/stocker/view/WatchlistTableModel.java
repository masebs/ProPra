package stocker.view;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

import javax.swing.SwingUtilities;
import javax.swing.event.TableModelEvent;
import javax.swing.table.AbstractTableModel;

import stocker.model.WatchlistItem;

/** 
 * Table model for the {@link WatchlistTable}. This is a customized version of the {@link AbstractTableModel}.
 * 
 * @author Marc S. Schneider
 */
public class WatchlistTableModel extends AbstractTableModel {

	private static final long serialVersionUID = 8552801053704668025L;

	private int ncols;
	private String[] columns = { "Symbol", "Name", "Zeit", "Kurs", "heute" };
	private ArrayList<String[]> rows = new ArrayList<String[]>(20);
	private HashMap<String, Integer> index = new HashMap<String, Integer>();
	private ArrayList<Color> rowColors = new ArrayList<Color>(20);

	/**
	 * Construct a new WatchlistTableModel.
	 */
	public WatchlistTableModel() {
		this.ncols = columns.length;
	}

	/**
	 * Add a row, consisting of the given columns, to the table.
	 * @param cols the columns of the row to be added
	 */
	public void addRow(String[] cols) {
		// Add row if no other row with the same key is present yet; otherwise ignore
		if (index.get(cols[0]) == null) {
			rows.add(cols);
			index.put(cols[0], rows.size() - 1);
			rowColors.add(Color.WHITE);
			fireTableRowsInserted(rows.size() - 1, rows.size() - 1);
		}
	}

	/**
	 * Update the row which belongs to the {@link WatchlistItem} with the given key by the given columns.
	 * @param key the key of the {@link WatchlistItem}) to be updated
	 * @param cols the new values for the columns
	 */
	public void updateRow(String key, String[] cols) {
		Integer idx = index.get(key);
		if (idx != null) { // if idx == null, then we are not monitoring this key in the watchlist, so we ignore it
			double priceChange = 0.0;
			try {
				priceChange = Double.parseDouble(cols[3].replace(',', '.')) - Double.parseDouble(rows.get(idx)[3].replace(',', '.'));
			} catch (NumberFormatException e) { 
				if (!( rows.get(idx)[3].equals("lade...") || rows.get(idx)[3].equals("keine Daten")
						|| rows.get(idx)[3].equals("keine Berechtigung"))) { // this is to be expected; everything else shouldn't ever happen
					System.err.println("tableModel, updateRow: Number Format Exception!");
					System.err.println(cols[3] + " " + rows.get(idx)[3]);
				}
			}
			rows.set(idx, cols);
			boolean setResetTimer = false;
			if (priceChange > 0.0) {
				rowColors.set(idx, new Color(100, 255, 100, 255));
				setResetTimer = true;
			} else if (priceChange < 0.0) {
				rowColors.set(idx, new Color(255, 100, 100, 255));
				setResetTimer = true;
			}
			SwingUtilities.invokeLater(new Runnable() {
				@Override
				public void run() {
					fireTableRowsUpdated(idx, idx);
				}
			});

			// set a timer which starts a thread to remove the highlighting
			if (setResetTimer) {
				Timer t = new Timer();
				t.schedule(new TimerTask() {
					@Override
					public void run() {
						if(idx < getRowCount()) { // to prevent an AIOOB exception if the row has been deleted in the mean time
							Integer idx = index.get(key);
							if (idx != null) { // if null, item doesn't exist any more, probably removed in the mean time
								rowColors.set(idx, Color.WHITE); // reset row color
							}
						}
						SwingUtilities.invokeLater(new Runnable() {
							@Override
							public void run() {
								fireTableRowsUpdated(index.get(key), index.get(key));
							}
						});
						t.cancel();
					}
				}, 900);
			}
		}
	}

	/** 
	 * Remove the row with the given row number.
	 * @param row the number of the row to be removed
	 */
	public void removeRow(int row) {
		index.remove(rows.get(row)[0]);
		rows.remove(row);
		rowColors.remove(row);
		reindex();
		fireTableRowsDeleted(row, row);
	}

	/**
	 * Re-calculate the index of the model.
	 */
	private void reindex() {
		index.clear();
		for (int i = 0; i < rows.size(); i++) {
			index.put(rows.get(i)[0], i);
		}
	}

	/**
	 * Clear the entire table model.
	 */
	public void clear() {
		rows.clear();
		index.clear();
		rowColors.clear();
		fireTableChanged(new TableModelEvent(this));
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getRowCount() {
		return this.rows.size();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getColumnCount() {
		return this.ncols;
	}

	/**
	 * Returns the names of the table's column with the given index.
	 * @param columnIndex the column to be queried
	 * @return the name of the queried column
	 */
	@Override
	public String getColumnName(int columnIndex) {
		return this.columns[columnIndex];
	}

	/**
	 * Returns the class of the object in the given column (always String).
	 * @return String.class, as all columns contain Strings
	 */
	@Override
	public Class<?> getColumnClass(int columnIndex) {
		return String.class;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isCellEditable(int rowIndex, int columnIndex) {
		return false;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Object getValueAt(int rowIndex, int columnIndex) {
		return rows.get(rowIndex)[columnIndex];
	}

	/**
	 * Returns the value for the cell at columnIndex and rowIndex as a string. 
	 * @param rowIndex the row whose value is requested
	 * @param columnIndex the column whose value is requested
	 * @return
	 */
	public String getValueAtAsString(int rowIndex, int columnIndex) {
		return rows.get(rowIndex)[columnIndex];
	}

	/**
	 * Sets a value at the given row and column.
	 */
	@Override
	public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
		rows.get(rowIndex)[columnIndex] = (String) aValue;
		fireTableRowsUpdated(rowIndex, rowIndex);
	}

	/**
	 * Returns the row number for a given key.
	 * @param key the key (of the {@link WatchlistItem}) for which the row number is requested
	 * @return the row number of the item with the given key, or -1 if there was no such row
	 */
	public int getRowFromKey(String key) {
		for (int i = 0; i < getRowCount(); i++) {
			if (getValueAtAsString(i, 0).equals(key)) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * Returns the {@link Color} in which the row with the given index should be painted.
	 * @param row the row for which the color is queried
	 * @return the {@link Color} of the row with the given index
	 */
	public Color getRowColor(int row) {
		return rowColors.get(row);
	}

}
