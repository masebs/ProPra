package stocker.util;

import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;

import javax.swing.JTextField;

/**
 * Helper class to validate input to JTextFields as soon as they lose focus. It sets the text in the JTextField to 
 * the minimum or maximum allowed value if the user has entered a value outside of the bounds. It sets a default 
 * value if the user input could not be parsed. This is done immediately after the text field has lost focus.
 * This facilitates processing of the user input as the text field content is ensured to be valid.
 * 
 * @author Marc S. Schneider
 */
public class TextfieldIntValidatorOnFocusLost extends FocusAdapter {

	private JTextField tf;
	private int minvalue, maxvalue, defaultvalue;

	/**
	 * Constructs a new {@link TextfieldIntValidatorOnFocusLost} with the given properties. It does NOT set itself as a 
	 * listener to the JTextField, so this needs to be done after construction.
	 * @param tf the {@link JTextField} that this validator should work on
	 * @param minvalue the minimum value which is allowed as input
	 * @param maxvalue the maximum value which is allowed as input
	 * @param defaultvalue a default value which is used if the user input could not be parsed
	 */
	public TextfieldIntValidatorOnFocusLost(JTextField tf, int minvalue, int maxvalue, int defaultvalue) {
		this.tf = tf;
		this.minvalue = minvalue;
		this.maxvalue = maxvalue;
		this.defaultvalue = defaultvalue;
	}

	/**
	 * Event Listener inherited from {@link FocusAdapter}: When the JTextField loses focus, the user input 
	 * values are checked and corrected if necessary.
	 * @param fe the {@link FocusEvent} that occured at the text field that we are validating 
	 */
	@Override
	public void focusLost(FocusEvent fe) {
		try {
			int val = Integer.parseInt(tf.getText());
			if (val < minvalue) {
				tf.setText(String.valueOf(minvalue));
			} else if (val > maxvalue) {
				tf.setText(String.valueOf(maxvalue));
			}
		} catch (NumberFormatException nfe) {
			// In case of any invalid value, just set the default value
			tf.setText(String.valueOf(defaultvalue));
		}
	}

}
