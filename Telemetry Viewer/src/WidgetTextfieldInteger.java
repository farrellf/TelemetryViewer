import java.awt.GridLayout;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

/**
 * A widget that lets the user specify an integer with a textfield.
 */
@SuppressWarnings("serial")
public class WidgetTextfieldInteger extends JPanel {
	
	JLabel label;
	JTextField textfield;
	int minimumAllowed;
	int maximumAllowed;
	
	/**
	 * @param labelText       Label to show at the left of the textfield.
	 * @param defaultValue    Default value.
	 * @param lowerLimit      Minimum allowed value.
	 * @param upperLimit      Maximum allowed value.
	 */
	public WidgetTextfieldInteger(String labelText, int defaultValue, int lowerLimit, int upperLimit) {
		
		super();
		minimumAllowed = lowerLimit;
		maximumAllowed = upperLimit;
		
		setLayout(new GridLayout(1, 2, 10, 10));
		
		label = new JLabel(labelText + ": ");
		add(label);
		
		textfield = new JTextField(Integer.toString(defaultValue));
		add(textfield);
		
		textfield.addFocusListener(new FocusListener() {
			
			@Override public void focusLost(FocusEvent fe) {
				
				textfield.setText(textfield.getText().trim());
				try {
					int i = Integer.parseInt(textfield.getText());
					if(i < lowerLimit)
						textfield.setText(Integer.toString(lowerLimit));
					else if(i > upperLimit)
						textfield.setText(Integer.toString(upperLimit));
				} catch(Exception e) {
					textfield.setText(Integer.toString(defaultValue));
				}
				
			}
			
			@Override public void focusGained(FocusEvent e) {
				textfield.selectAll();
			}
			
		});
		
	}
	
	/**
	 * @return    The specified value.
	 */
	public int getValue() {
		
		return Integer.parseInt(textfield.getText());
		
	}

}
