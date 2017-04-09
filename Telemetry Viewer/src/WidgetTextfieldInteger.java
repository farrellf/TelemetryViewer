import java.awt.GridLayout;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.function.Consumer;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

@SuppressWarnings("serial")
public class WidgetTextfieldInteger extends JPanel {
	
	JTextField textfield;
	Consumer<Integer> handler;
	int defaultValue;
	int lowerLimit;
	int upperLimit;
	
	/**
	 * A widget that lets the user specify an integer with a textfield.
	 * 
	 * @param label           Label to show at the left of the textfield.
	 * @param defaultValue    Default value.
	 * @param lowerLimit      Minimum allowed value.
	 * @param upperLimit      Maximum allowed value.
	 * @param eventHandler    Will be notified when the textfield changes.
	 */
	public WidgetTextfieldInteger(String label, int defaultValue, int lowerLimit, int upperLimit, Consumer<Integer> eventHandler) {
		
		super();
		
		handler = eventHandler;
		this.defaultValue = defaultValue;
		this.lowerLimit = lowerLimit;
		this.upperLimit = upperLimit;
		
		setLayout(new GridLayout(1, 2, 10, 10));
		add(new JLabel(label + ": "));
		
		textfield = new JTextField(Integer.toString(defaultValue));
		textfield.addFocusListener(new FocusListener() {
			@Override public void focusLost(FocusEvent fe)   { checkAndNotifyHandler(); }
			@Override public void focusGained(FocusEvent fe) { textfield.selectAll(); }
		});
		
		add(textfield);

		checkAndNotifyHandler();
		
	}
	
	/**
	 * Ensures the number is within the allowed range, then notifies the handlers.
	 */
	private void checkAndNotifyHandler() {
		
		try {
			
			int i = Integer.parseInt(textfield.getText().trim());
			if(i < lowerLimit)
				i = lowerLimit;
			else if(i > upperLimit)
				i = upperLimit;
			
			textfield.setText(Integer.toString(i));
			handler.accept(i);
			
		} catch(Exception e) {
			
			textfield.setText(Integer.toString(defaultValue));
			handler.accept(defaultValue);
			
		}
		
	}
	
	/**
	 * Sets the integer.
	 * This should be called after importing a chart since the widget will not be in sync with the chart's state.
	 * 
	 * @param value    The integer.
	 */
	public void setInteger(int value) {
		
		textfield.setText(Integer.toString(value));
		
	}

}
