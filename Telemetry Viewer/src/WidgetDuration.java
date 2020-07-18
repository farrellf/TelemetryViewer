import java.awt.GridLayout;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.function.Consumer;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

public class WidgetDuration extends Widget {
	
	JTextField textfield;
	JComboBox<String> durationTypeCombobox;
	JComboBox<String> xAxisCombobox;
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
	 * @param eventHandler          Will be notified when the duration or x-axis mode changes. The String will be "Sample Count" or "Timestamps" or "Time Elapsed".
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
		
		durationTypeCombobox = new JComboBox<String>(new String[] {"Samples", "Seconds", "Minutes", "Hours", "Days"});
		durationTypeCombobox.addActionListener(event -> sanityCheck());
		
		xAxisCombobox = new JComboBox<String>(new String[] {"Sample Count", "Timestamps", "Time Elapsed"});
		xAxisCombobox.addActionListener(event -> sanityCheck());
		
		JPanel panel = new JPanel(new GridLayout(1, 2, Theme.padding, Theme.padding));
		panel.add(textfield);
		panel.add(durationTypeCombobox);
		
		widgets.put(new JLabel("Duration: "), "");
		widgets.put(panel, "span 3, growx");
		widgets.put(new JLabel("X-Axis: "), "");
		widgets.put(xAxisCombobox, "span 3, growx");
		
		previousType = "Samples";
		sanityCheck();
		
	}
	
	/**
	 * Ensures the duration is within the allowed range, then notifies the handler.
	 */
	public void sanityCheck() {
		
		// remove event handlers so they don't get called when updating the comboboxes
		for(ActionListener al : durationTypeCombobox.getActionListeners())
			durationTypeCombobox.removeActionListener(al);
		for(ActionListener al : xAxisCombobox.getActionListeners())
			xAxisCombobox.removeActionListener(al);
		
		String currentType = durationTypeCombobox.getSelectedItem().toString();
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
		
		// if duration is a sample count, the x-axis mode must be "Sample Count"
		// if duration is not a sample count, the x-axis mode must be "Time Elapsed" or "Timestamp"
		if(durationTypeCombobox.getSelectedItem().toString().equals("Samples")) {
			xAxisCombobox.setSelectedItem("Sample Count");
			xAxisCombobox.setEnabled(false);
		} else {
			if(xAxisCombobox.getSelectedIndex() == 0)
				xAxisCombobox.setSelectedIndex(1);
			xAxisCombobox.setEnabled(true);
		}
		
		try {
			
			if(durationTypeCombobox.getSelectedItem().toString().equals("Samples")) {
				
				int i = Integer.parseInt(textfield.getText().trim());
				if(i < lowerLimit)
					i = (int) lowerLimit;
				else if(i > upperLimit)
					i = (int) upperLimit;
				intDuration = i;
				
				textfield.setText(Integer.toString(i));
				handler.accept(xAxisCombobox.getSelectedItem().toString());
				
			} else {
				
				float f = Float.parseFloat(textfield.getText().trim());
				if(f < Float.MIN_VALUE)
					f = Float.MIN_VALUE;
				floatDuration = f;
				
				textfield.setText(Float.toString(f));
				handler.accept(xAxisCombobox.getSelectedItem().toString());
				
			}
			
		} catch(Exception e) {
			
			durationTypeCombobox.setSelectedItem("Samples");
			xAxisCombobox.setSelectedItem("Sample Count");
			xAxisCombobox.setEnabled(false);
			textfield.setText(Integer.toString(defaultSampleCount));
			intDuration = defaultSampleCount;
			handler.accept(xAxisCombobox.getSelectedItem().toString());
			
		}
		
		previousType = durationTypeCombobox.getSelectedItem().toString();
		
		// restore the event handlers
		durationTypeCombobox.addActionListener(event -> sanityCheck());
		xAxisCombobox.addActionListener(event -> sanityCheck());
		
	}
	
	/**
	 * @return    The duration, as a sample count.
	 */
	public int getSampleCount() {
		
		return intDuration;
		
	}
	
	/**
	 * @return    The duration, as a float (seconds, minutes, hours or days.)
	 */
	public long getMilliseconds() {
		
		return previousType.equals("Seconds") ? (long) (floatDuration * 1000.0) :
		       previousType.equals("Minutes") ? (long) (floatDuration * 60000.0) :
		       previousType.equals("Hours")   ? (long) (floatDuration * 3600000.0) :
		                                        (long) (floatDuration * 86400000.0);
		
	}
	
	/**
	 * Updates the widget and chart based on settings from a layout file.
	 * 
	 * @param lines    A queue of remaining lines from the layout file.
	 */
	@Override public void importState(CommunicationController.QueueOfLines lines) {

		// parse the text
		String type = ChartUtils.parseString(lines.remove(), "duration type = %s");
		
		// update the widget
		boolean validEnum = false;
		for(int i = 0; i < durationTypeCombobox.getItemCount(); i++)
			if(durationTypeCombobox.getItemAt(i).equals(type)) {
				durationTypeCombobox.setSelectedIndex(i);
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
		
		// parse the x-axis mode text
		String showsAs = ChartUtils.parseString(lines.remove(), "x-axis = %s");
		
		// update the widget
		validEnum = false;
		for(int i = 0; i < xAxisCombobox.getItemCount(); i++)
			if(xAxisCombobox.getItemAt(i).equals(showsAs)) {
				xAxisCombobox.setSelectedIndex(i);
				validEnum = true;
			}
		if(!validEnum)
			throw new AssertionError("X-axis type does not match an expected value.");
		
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
			"duration type = " + durationTypeCombobox.getSelectedItem().toString(),
			"duration = " + textfield.getText(),
			"x-axis = " + xAxisCombobox.getSelectedItem().toString()
		};
		
	}

}
