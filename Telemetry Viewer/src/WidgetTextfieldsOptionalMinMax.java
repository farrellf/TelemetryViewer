import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.function.BiConsumer;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

public class WidgetTextfieldsOptionalMinMax extends Widget {
	
	String prefix;
	JCheckBox maxCheckbox;
	JCheckBox minCheckbox;
	JTextField maxTextfield;
	JTextField minTextfield;
	float upperLimit;
	float lowerLimit;
	float defaultMax;
	float defaultMin;
	BiConsumer<Boolean, Float> minHandler;
	BiConsumer<Boolean, Float> maxHandler;
	
	/**
	 * A widget that lets the user make minimum and maximum values be fixed or autoscaled.
	 * 
	 * @param labelPrefix        Text to show before the "Minimum" or "Maximum" label.
	 * @param defaultMin         Default value for minimum.
	 * @param defaultMax         Default value for maximum.
	 * @param lowerLimit         Minimum allowed value.
	 * @param upperLimit         Maximum allowed value.
	 * @param minEventHandler    Will be notified when the minimum changes.
	 * @param maxEventHandler    Will be notified when the maximum changes.
	 */
	public WidgetTextfieldsOptionalMinMax(String labelPrefix, float defaultMin, float defaultMax, float lowerLimit, float upperLimit, BiConsumer<Boolean, Float> minEventHandler, BiConsumer<Boolean, Float> maxEventHandler) {
		
		super();
		
		prefix = labelPrefix;
		this.upperLimit = upperLimit;
		this.lowerLimit = lowerLimit;
		this.defaultMax = defaultMax;
		this.defaultMin = defaultMin;
		minHandler = minEventHandler;
		maxHandler = maxEventHandler;
		
		maxTextfield = new JTextField(Float.toString(defaultMax));
		maxTextfield.setEnabled(false);
		maxTextfield.addFocusListener(new FocusListener() {
			@Override public void focusLost(FocusEvent fe)   { sanityCheck(); }
			@Override public void focusGained(FocusEvent fe) { maxTextfield.selectAll(); }
		});
		
		maxCheckbox = new JCheckBox("Automatic");
		maxCheckbox.setSelected(true);
		maxCheckbox.addActionListener(event -> sanityCheck());
		
		minTextfield = new JTextField(Float.toString(defaultMin));
		minTextfield.setEnabled(false);
		minTextfield.addFocusListener(new FocusListener() {
			@Override public void focusLost(FocusEvent fe)   { sanityCheck(); }
			@Override public void focusGained(FocusEvent fe) { minTextfield.selectAll(); }
		});
		
		minCheckbox = new JCheckBox("Automatic");
		minCheckbox.setSelected(true);
		minCheckbox.addActionListener(event -> sanityCheck());
		
		JPanel maxPanel = new JPanel();
		maxPanel.setLayout(new BoxLayout(maxPanel, BoxLayout.X_AXIS));
		maxPanel.add(maxCheckbox);
		maxPanel.add(Box.createHorizontalStrut(Theme.padding));
		maxPanel.add(maxTextfield);
		
		widgets.put(new JLabel(labelPrefix + " Maximum: "), "");
		widgets.put(maxPanel, "span 3, growx");
		
		JPanel minPanel = new JPanel();
		minPanel.setLayout(new BoxLayout(minPanel, BoxLayout.X_AXIS));
		minPanel.add(minCheckbox);
		minPanel.add(Box.createHorizontalStrut(Theme.padding));
		minPanel.add(minTextfield);
		
		widgets.put(new JLabel(labelPrefix + " Minimum: "), "");
		widgets.put(minPanel, "span 3, growx");
		
		sanityCheck();
		
	}
	
	/**
	 * Ensures that both values are within the allowed range, and that minimum < maximum.
	 * Disables min/max textfields if they are autoscaled.
	 * 
	 * Notifies all handlers.
	 */
	public void sanityCheck() {
		
		try {
			
			float min = Float.parseFloat(minTextfield.getText().trim());
			float max = Float.parseFloat(maxTextfield.getText().trim());
			
			// clip to limits
			if(min > upperLimit) min = upperLimit;
			if(min < lowerLimit) min = lowerLimit;
			if(max > upperLimit) max = upperLimit;
			if(max < lowerLimit) max = lowerLimit;
			
			// ensure min < max
			if(min == max) {
				if(max == upperLimit)
					min = Math.nextDown(min);
				else
					max = Math.nextUp(max);
			} else if(min > max) {
				float temp = max;
				max = min;
				min = temp;
			}
			
			// update textfields
			minTextfield.setText(Float.toString(min));
			maxTextfield.setText(Float.toString(max));
			
			minHandler.accept(minCheckbox.isSelected(), min);
			maxHandler.accept(maxCheckbox.isSelected(), max);
			
		} catch(Exception e) {
			
			// one of the textfields doesn't contain a valid number, so reset both to defaults
			minTextfield.setText(Float.toString(defaultMin));
			maxTextfield.setText(Float.toString(defaultMax));
			
			minHandler.accept(minCheckbox.isSelected(), defaultMin);
			maxHandler.accept(maxCheckbox.isSelected(), defaultMax);
			
		}
		
		maxTextfield.setEnabled(!maxCheckbox.isSelected());
		minTextfield.setEnabled(!minCheckbox.isSelected());
		
	}
	
	/**
	 * Updates the widget and chart based on settings from a layout file.
	 * 
	 * @param lines    A queue of remaining lines from the layout file.
	 */
	@Override public void importState(CommunicationController.QueueOfLines lines) {

		// parse the text
		boolean autoscaleMin = ChartUtils.parseBoolean(lines.remove(), "autoscale " + prefix.trim().toLowerCase() + " minimum = %b");
		float manualMin      = ChartUtils.parseFloat  (lines.remove(), "manual " + prefix.trim().toLowerCase() + " minimum = %f");
		boolean autoscaleMax = ChartUtils.parseBoolean(lines.remove(), "autoscale " + prefix.trim().toLowerCase() + " maximum = %b");
		float manualMax      = ChartUtils.parseFloat  (lines.remove(), "manual " + prefix.trim().toLowerCase() + " maximum = %f");
		
		// update the widget
		minCheckbox.setSelected(autoscaleMin);
		minTextfield.setText(Float.toString(manualMin));
		maxCheckbox.setSelected(autoscaleMax);
		maxTextfield.setText(Float.toString(manualMax));
		
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
			"autoscale " + prefix.trim().toLowerCase() + " minimum = " + minCheckbox.isSelected(),
			"manual " + prefix.trim().toLowerCase() + " minimum = " + minTextfield.getText(),
			"autoscale " + prefix.trim().toLowerCase() + " maximum = " + maxCheckbox.isSelected(),
			"manual " + prefix.trim().toLowerCase() + " maximum = " + maxTextfield.getText()
		};
		
	}

}
