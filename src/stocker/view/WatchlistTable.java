package stocker.view;

import java.awt.Color;
import java.awt.Font;
import javax.swing.JTable;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableModel;

/**
 * A table to show the watchlist entries. This is a JTable, slightly adapted to fit the requirements.
 * 
 * @author Marc S. Schneider
 */
public class WatchlistTable extends JTable {

	private static final long serialVersionUID = -4217507558007841282L;
	
	private int previouslySelected = -2;

	/** 
	 * Construct a new WatchlistTable.
	 * @param model the table model to be associated with this table
	 */
	public WatchlistTable(TableModel model) {
		setModel(model);
		setDefaultRenderer(String.class, new WatchlistTableCellRenderer());
		TableColumnModel colMod = getColumnModel();
		colMod.getColumn(0).setPreferredWidth(100);
		colMod.getColumn(1).setPreferredWidth(100);
		setRowSelectionAllowed(true);
		setColumnSelectionAllowed(false);
		getTableHeader().setReorderingAllowed(true);
		setFocusable(false);
		setFont(new Font("Sans-Serif", Font.PLAIN, 14));
		setRowHeight(20);
	}

	/**
	 * Get the table model of this watchlist.
	 * @return the table model of this watchlist
	 */
	public WatchlistTableModel getWatchlistTableModel() {
		return (WatchlistTableModel) getModel();
	}

	/**
	 * Get the {@link Color} of the row with the given index (required for the striped lines).
	 * @param row the row number within the table
	 * @return the {@link Color} at this row
	 */
	public Color getRowColor(int row) {
		return getWatchlistTableModel().getRowColor(row);
	}
	
	/**
	 * Overriden method of JTable in order to make it possible to unselect a single currently selected row 
	 * (without impeding the usual multiselect behaviour).
	 * {@inheritDoc}
	 */
	@Override
	public void changeSelection(int rowIndex, int columnIndex, boolean toggle, boolean extend) {
		int nRowsSelectedBefore = getSelectedRows().length;
		super.changeSelection(rowIndex, columnIndex, toggle, extend);
		if (getSelectedRow() == previouslySelected && getSelectedRows().length == 1 && nRowsSelectedBefore == 1) {
			clearSelection();
			previouslySelected = -2;
		}
		else {
			if (getSelectedRow() != -1) {
				previouslySelected = getSelectedRow();
			}
		}
	}

}
