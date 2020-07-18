import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.function.Consumer;

import javax.swing.JLabel;
import javax.swing.JTextField;

public class WidgetTextfieldInteger extends Widget {
	
	String label;
	JTextField textfield;
	Consumer<Integer> handler;
	int defaultValue;
	int lowerLimit;
	int upperLimit;
	
	/**
	 * A widget that lets the user specify an integer with a textfield.
	 * 
	 * @param textLabel       Label to show at the left of the textfield.
	 * @param defaultValue    Default value.
	 * @param lowerLimit      Minimum allowed value.
	 * @param upperLimit      Maximum allowed value.
	 * @param eventHandler    Will be notified when the textfield changes.
	 */
	public WidgetTextfieldInteger(String textLabel, int defaultValue, int lowerLimit, int upperLimit, Consumer<Integer> eventHandler) {
		
		super();
		
		label = textLabel;
		handler = eventHandler;
		this.defaultValue = defaultValue;
		this.lowerLimit = lowerLimit;
		this.upperLimit = upperLimit;
		
		textfield = new JTextField(Integer.toString(defaultValue));
		textfield.addFocusListener(new FocusListener() {
			@Override public void focusLost(FocusEvent fe)   { sanityCheck(); }
			@Override public void focusGained(FocusEvent fe) { textfield.selectAll(); }
		});
		
		widgets.put(new JLabel(label + ": "), "");
		widgets.put(textfield, "span 3, growx");

		sanityCheck();
		
	}
	
	/**
	 * Ensures the number is within the allowed range, then notifies the handlers.
	 */
	public void sanityCheck() {
		
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
	 * Updates the widget and chart based on settings from a layout file.
	 * 
	 * @param lines    A queue of remaining lines from the layout file.
	 */
	@Override public void importState(CommunicationController.QueueOfLines lines) {

		// parse the text
		int number = ChartUtils.parseInteger(lines.remove(), label.trim().toLowerCase() + " = %d");
		
		// update the widget
		textfield.setText(Integer.toString(number));
		
		// update the chart
		sanityCheck();
		
	}
	
	/**
	 * Saves the current state to one or more lines of text.
	 * 
	 * @return    A String[] where each element is a line of text.
	 */
	@Override public String[] exportState() {
		
		return new String[] {
			label.trim().toLowerCase() + " = " + textfield.getText()
		};
		
	}

}
