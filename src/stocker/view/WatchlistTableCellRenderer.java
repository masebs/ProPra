package stocker.view;

import java.awt.Color;
import java.awt.Component;

import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;

/**
 * Adapted version of the {@link DefaultTableCellRenderer} in order to support the striped table design and
 * for some other visualization tweaks.
 * 
 * @author Marc S. Schneider
 */
public class WatchlistTableCellRenderer extends DefaultTableCellRenderer {

	private static final long serialVersionUID = 1535354788760769721L;

	/**
	 * Overriding the base class method in order to customize its behaviour. 
	 * {@inheritDoc}
	 */
	@Override
	public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
			int row, int column) {
		if (!isSelected) { // for unselected rows
			WatchlistTableModel tableModel = (WatchlistTableModel) table.getModel();
			Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
			// set stripe pattern
			if (row % 2 == 0) { 
				c.setBackground(Color.WHITE);
			} else {
				c.setBackground(new Color(230, 230, 230, 255));
			}
			// set color of "Kurs" column after sorting or rearranging  
			if (column == table.getColumnModel().getColumnIndex("Kurs")) {
				int sortedRow = ((WatchlistTable) table).getRowSorter().convertRowIndexToModel(row);
				Color rowColor = tableModel.getRowColor(sortedRow);
				if (rowColor != Color.WHITE) {
					c.setBackground(tableModel.getRowColor(sortedRow));
				}
				c.setForeground(Color.BLACK);
			}
			// set "heute" column to red or green in case of loss or gain, respectively
			else if (column == table.getColumnModel().getColumnIndex("heute")) {
				if (((String)value).startsWith("+")) {
					c.setForeground(new Color(0, 100, 0, 255));
				}
				else {
					c.setForeground(new Color(100, 0, 0, 255));
				}
			}
			else {
				c.setForeground(Color.BLACK);
			}
			return c;
		} 
		else { // for selected rows, keep everything on default
			return super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
		}
	}

}
