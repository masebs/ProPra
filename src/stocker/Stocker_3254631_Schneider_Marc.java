package stocker;

import javax.swing.SwingUtilities;

import stocker.control.StockerControl;

/**
 * The main class containing the main method which executes the Stocker application.
 * 
 * @author Marc S. Schneider
 */
public class Stocker_3254631_Schneider_Marc {

	/**
	 * The main method for execution of the application.
	 * @param args the command line arguments (not used)
	 */
	public static void main(String[] args) {
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				@SuppressWarnings("unused")
				StockerControl sc = new StockerControl();
			}
		});
	}
}
