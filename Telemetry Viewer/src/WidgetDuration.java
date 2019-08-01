import java.awt.GridLayout;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.function.Consumer;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

@SuppressWarnings("serial")
public class WidgetDuration extends Widget {
	
	JTextField textfield;
	JComboBox<String> combobox;
	Consumer<String> handler;
	int defaultSampleCount;
	int lowerLimit;
	int upperLimit;
	String previousType;
	
	int intDuration;
	float floatDuration;
	
	/**
	 * A widget that lets the user specify a duration and pick the duration type (sample count, seconds, etc.) from a drop-down list.
	 * 
	 * @param defaultSampleCount    Default sample count.
	 * @param eventHandler          Will be notified when the duration type or duration value changes.
	 */
	public WidgetDuration(int defaultSampleCount, int lowerLimit, int upperLimit, Consumer<String> eventHandler) {
		
		super();
		
		handler = eventHandler;
		this.defaultSampleCount = defaultSampleCount;
		this.lowerLimit = lowerLimit;
		this.upperLimit = upperLimit;
		
		textfield = new JTextField(Integer.toString((int) defaultSampleCount));
		textfield.addFocusListener(new FocusListener() {
			@Override public void focusLost(FocusEvent fe)   { sanityCheck(); }
			@Override public void focusGained(FocusEvent fe) { textfield.selectAll(); }
		});
		
		combobox = new JComboBox<String>(new String[] {"Samples", "Seconds", "Minutes", "Hours", "Days"});
		combobox.addActionListener(event -> sanityCheck());
		
		JPanel panel = new JPanel(new GridLayout(1, 2, 10, 10));
		panel.add(textfield);
		panel.add(combobox);
		
		setLayout(new GridLayout(1, 2, 10, 10));
		add(new JLabel("Duration: "));
		add(panel);
		
		previousType = "Samples";
		sanityCheck();
		
	}
	
	/**
	 * Ensures the duration is within the allowed range, then notifies the handler.
	 */
	public void sanityCheck() {
		
		String currentType = combobox.getSelectedItem().toString();
		if(!currentType.equals(previousType)) {
			// the type changed, so scale the value to roughly match
			if(previousType.equals("Samples") && currentType.equals("Seconds"))
				textfield.setText(Float.toString(Float.parseFloat(textfield.getText()) / (float) CommunicationController.getSampleRate()));
			else if(previousType.equals("Samples") && currentType.equals("Minutes"))
				textfield.setText(Float.toString(Float.parseFloat(textfield.getText()) / (float) CommunicationController.getSampleRate() / 60f));
			else if(previousType.equals("Samples") && currentType.equals("Hours"))
				textfield.setText(Float.toString(Float.parseFloat(textfield.getText()) / (float) CommunicationController.getSampleRate() / 3600f));
			else if(previousType.equals("Samples") && currentType.equals("Days"))
				textfield.setText(Float.toString(Float.parseFloat(textfield.getText()) / (float) CommunicationController.getSampleRate() / 86400f));
			
			else if(previousType.equals("Seconds") && currentType.equals("Samples"))
				textfield.setText(Integer.toString((int) (Float.parseFloat(textfield.getText()) * (float) CommunicationController.getSampleRate())));
			else if(previousType.equals("Seconds") && currentType.equals("Minutes"))
				textfield.setText(Float.toString(Float.parseFloat(textfield.getText()) / 60f));
			else if(previousType.equals("Seconds") && currentType.equals("Hours"))
				textfield.setText(Float.toString(Float.parseFloat(textfield.getText()) / 3600f));
			else if(previousType.equals("Seconds") && currentType.equals("Days"))
				textfield.setText(Float.toString(Float.parseFloat(textfield.getText()) / 86400f));
			
			else if(previousType.equals("Minutes") && currentType.equals("Samples"))
				textfield.setText(Integer.toString((int) (Float.parseFloat(textfield.getText()) * (float) CommunicationController.getSampleRate() * 60f)));
			else if(previousType.equals("Minutes") && currentType.equals("Seconds"))
				textfield.setText(Float.toString(Float.parseFloat(textfield.getText()) * 60f));
			else if(previousType.equals("Minutes") && currentType.equals("Hours"))
				textfield.setText(Float.toString(Float.parseFloat(textfield.getText()) / 60f));
			else if(previousType.equals("Minutes") && currentType.equals("Days"))
				textfield.setText(Float.toString(Float.parseFloat(textfield.getText()) / 1440f));
			
			else if(previousType.equals("Hours") && currentType.equals("Samples"))
				textfield.setText(Integer.toString((int) (Float.parseFloat(textfield.getText()) * (float) CommunicationController.getSampleRate() * 3600f)));
			else if(previousType.equals("Hours") && currentType.equals("Seconds"))
				textfield.setText(Float.toString(Float.parseFloat(textfield.getText()) * 3600f));
			else if(previousType.equals("Hours") && currentType.equals("Minutes"))
				textfield.setText(Float.toString(Float.parseFloat(textfield.getText()) * 60f));
			else if(previousType.equals("Hours") && currentType.equals("Days"))
				textfield.setText(Float.toString(Float.parseFloat(textfield.getText()) / 24f));
			
			else if(previousType.equals("Days") && currentType.equals("Samples"))
				textfield.setText(Integer.toString((int) (Float.parseFloat(textfield.getText()) * (float) CommunicationController.getSampleRate() * 86400f)));
			else if(previousType.equals("Days") && currentType.equals("Seconds"))
				textfield.setText(Float.toString(Float.parseFloat(textfield.getText()) * 86400f));
			else if(previousType.equals("Days") && currentType.equals("Minutes"))
				textfield.setText(Float.toString(Float.parseFloat(textfield.getText()) * 1440f));
			else if(previousType.equals("Days") && currentType.equals("Hours"))
				textfield.setText(Float.toString(Float.parseFloat(textfield.getText()) * 24f));
		}
		
		try {
			
			if(combobox.getSelectedItem().toString().equals("Samples")) {
				
				int i = Integer.parseInt(textfield.getText().trim());
				if(i < lowerLimit)
					i = (int) lowerLimit;
				else if(i > upperLimit)
					i = (int) upperLimit;
				intDuration = i;
				
				textfield.setText(Integer.toString(i));
				
			} else {
				
				float f = Float.parseFloat(textfield.getText().trim());
				if(f < Float.MIN_VALUE)
					f = Float.MIN_VALUE;
				floatDuration = f;
				
				textfield.setText(Float.toString(f));
				
			}

			handler.accept(combobox.getSelectedItem().toString());
			
		} catch(Exception e) {
			
			combobox.setSelectedItem("Samples");
			textfield.setText(Integer.toString(defaultSampleCount));
			intDuration = defaultSampleCount;
			handler.accept(combobox.getSelectedItem().toString());
			
		}
		
		previousType = combobox.getSelectedItem().toString();
		
	}
	
	/**
	 * @return    The duration, as an integer (a sample count.)
	 */
	public int getDurationInteger() {
		
		return intDuration;
		
	}
	
	/**
	 * @return    The duration, as a float (seconds, minutes, hours or days.)
	 */
	public float getDurationFloat() {
		
		return floatDuration;
		
	}
	
	/**
	 * Updates the widget and chart based on settings from a layout file.
	 * 
	 * @param lines    A queue of remaining lines from the layout file.
	 */
	@Override public void importState(Controller.QueueOfLines lines) {

		// parse the text
		String type = ChartUtils.parseString(lines.remove(), "duration type = %s");
		
		// update the widget
		boolean validEnum = false;
		for(int i = 0; i < combobox.getItemCount(); i++)
			if(combobox.getItemAt(i).equals(type)) {
				combobox.setSelectedIndex(i);
				validEnum = true;
			}
		if(!validEnum)
			throw new AssertionError("Duration type does not match an expected value.");
		
		if(type.equals("Samples")) {
			int sampleCount = ChartUtils.parseInteger(lines.remove(), "duration = %d");
			textfield.setText(Integer.toString(sampleCount));
		} else {
			float duration = ChartUtils.parseFloat(lines.remove(), "duration = %f");
			textfield.setText(Float.toString(duration));
		}
		
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
			"duration type = " + combobox.getSelectedItem().toString(),
			"duration = " + textfield.getText()
		};
		
	}

}
