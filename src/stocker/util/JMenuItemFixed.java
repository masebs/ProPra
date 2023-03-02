package stocker.util;

import javax.swing.JMenuItem;

/**
 * Convenience class which marks a menu item as in-place (not sorted). This makes it possible to distinguish 
 * those menu items which should be sorted (e.g. alphabetically) from those which should always stay fixed on top.
 * 
 * @author Marc S. Schneider
 */
public class JMenuItemFixed extends JMenuItem {
	private static final long serialVersionUID = -7849313389898221854L;
	/**
	 * Flag stating that that this menu item should always stay on top (and not be sorted) if true.
	 */
	public final boolean stayOnTop = true;
	
	/**
	 * Creates a new JMenuItemFixed with the specified text
	 * @param text the text of the new item
	 */
	public JMenuItemFixed(String text) {
		super(text);
	}
}
