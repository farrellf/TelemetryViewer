import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.function.BiConsumer;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

public class WidgetHistogramYaxisType extends Widget {
	
	JLabel axisTypeLabel;
	JComboBox<String> axisTypeCombobox;
	JLabel maxLabel;
	JLabel minLabel;
	JCheckBox maxCheckbox;
	JCheckBox minCheckbox;
	JTextField maxTextfield;
	JTextField minTextfield;
	
	float relativeFrequencyUpperLimit;
	float relativeFrequencyLowerLimit;
	float relativeFrequencyDefaultMaximum;
	float relativeFrequencyDefaultMinimum;
	
	float frequencyUpperLimit;
	float frequencyLowerLimit;
	float frequencyDefaultMaximum;
	float frequencyDefaultMinimum;
	
	BiConsumer<Boolean, Boolean> axisTypeHandler;
	BiConsumer<Boolean, Float> minHandler;
	BiConsumer<Boolean, Float> maxHandler;
	
	/**
	 * A widget that lets the user specify the y-axis type for a histogram.
	 * 
	 * The axis can show relative frequency, frequency, or both.
	 * If both are shown, the left axis will be relative frequency and the right axis will be frequency.
	 * 
	 * The minimum/maximum values can either be autoscaled or specified.
	 * 
	 * @param relativeFrequencyDefaultMinimum     Default value for axis minimum.
	 * @param relativeFrequencyDefaultMaximum     Default value for axis maximum.
	 * @param relativeFrequencyLowerLimit         Minimum allowed value.
	 * @param relativeFrequencyUpperLimit         Maximum allowed value.
	 * @param frequencyDefaultMinimum             Default value for axis minimum.
	 * @param frequencyDefaultMaximum             Default value for axis maximum.
	 * @param frequencyLowerLimit                 Minimum allowed value.
	 * @param frequencyUpperLimit                 Maximum allowed value.
	 * @param axisTypeEventHandler                Will be notified when the axis type (relative frequency, frequency, or both) changes.
	 * @param minEventHandler                     Will be notified when the minimum changes.
	 * @param maxEventHandler                     Will be notified when the maximum changes.
	 */
	public WidgetHistogramYaxisType(float relativeFrequencyDefaultMinimum, float relativeFrequencyDefaultMaximum, float relativeFrequencyLowerLimit, float relativeFrequencyUpperLimit, float frequencyDefaultMinimum, float frequencyDefaultMaximum, float frequencyLowerLimit, float frequencyUpperLimit, BiConsumer<Boolean, Boolean> axisTypeEventHandler, BiConsumer<Boolean, Float> minEventHandler, BiConsumer<Boolean, Float> maxEventHandler) {
		
		super();
		
		axisTypeLabel = new JLabel("Y-Axis Type: ");
		axisTypeCombobox = new JComboBox<String>(new String[] {"Relative Frequency", "Frequency", "Both"});
		maxLabel = new JLabel("Relative Frequency Maximum: ");
		minLabel = new JLabel("Relative Frequency Minimum: ");
		maxCheckbox = new JCheckBox("Automatic");
		minCheckbox = new JCheckBox("Zero");
		maxCheckbox.setSelected(true);
		minCheckbox.setSelected(true);
		maxTextfield = new JTextField(Float.toString(relativeFrequencyDefaultMaximum));
		minTextfield = new JTextField(Float.toString(relativeFrequencyDefaultMinimum));
		maxTextfield.setEnabled(false);
		minTextfield.setEnabled(false);
		
		this.relativeFrequencyUpperLimit = relativeFrequencyUpperLimit;
		this.relativeFrequencyLowerLimit = relativeFrequencyLowerLimit;
		this.relativeFrequencyDefaultMaximum = relativeFrequencyDefaultMaximum;
		this.relativeFrequencyDefaultMinimum = relativeFrequencyDefaultMinimum;
		this.frequencyUpperLimit = frequencyUpperLimit;
		this.frequencyLowerLimit = frequencyLowerLimit;
		this.frequencyDefaultMaximum = frequencyDefaultMaximum;
		this.frequencyDefaultMinimum = frequencyDefaultMinimum;
		this.axisTypeHandler = axisTypeEventHandler;
		this.minHandler = minEventHandler;
		this.maxHandler = maxEventHandler;
		
		axisTypeCombobox.addActionListener(event -> sanityCheck());
		
		maxCheckbox.addActionListener(event -> sanityCheck());
		
		minCheckbox.addActionListener(event -> sanityCheck());
		
		maxTextfield.addFocusListener(new FocusListener() {
			@Override public void focusLost(FocusEvent fe)   { sanityCheck(); }
			@Override public void focusGained(FocusEvent fe) { maxTextfield.selectAll(); }
		});
		
		minTextfield.addFocusListener(new FocusListener() {
			@Override public void focusLost(FocusEvent fe)   { sanityCheck(); }
			@Override public void focusGained(FocusEvent fe) { minTextfield.selectAll(); }
		});
		
		widgets.put(axisTypeLabel, "");
		widgets.put(axisTypeCombobox, "span 3, growx");
		
		JPanel maxPanel = new JPanel();
		maxPanel.setLayout(new BoxLayout(maxPanel, BoxLayout.X_AXIS));
		maxPanel.add(maxCheckbox);
		maxPanel.add(Box.createHorizontalStrut(Theme.padding));
		maxPanel.add(maxTextfield);
		widgets.put(maxLabel, "");
		widgets.put(maxPanel, "span 3, growx");
		
		JPanel minPanel = new JPanel();
		minPanel.setLayout(new BoxLayout(minPanel, BoxLayout.X_AXIS));
		minPanel.add(minCheckbox);
		minPanel.add(Box.createHorizontalStrut(Theme.padding));
		minPanel.add(minTextfield);
		widgets.put(minLabel, "");
		widgets.put(minPanel, "span 3, growx");
		
		sanityCheck();
		
	}
	
	/**
	 * Ensures the min and max values are within the allowed range, and that minimum < maximum.
	 * Renames/disables the min and max textboxes depending on the axis type and autoscaling.
	 * 
	 * Notifies all handlers.
	 */
	public void sanityCheck() {
		
		String axisType = axisTypeCombobox.getSelectedItem().toString();
		
		// rename labels depending on axis type
		if(axisType.equals("Relative Frequency") || axisType.equals("Both")) {
			maxLabel.setText("Relative Frequency Maximum: ");
			minLabel.setText("Relative Frequency Minimum: ");
		} else if(axisType.equals("Frequency")) {
			maxLabel.setText("Frequency Maximum: ");
			minLabel.setText("Frequency Minimum: ");
		}
		
		axisTypeHandler.accept(axisType.equals("Relative Frequency") || axisType.equals("Both"), axisType.equals("Frequency") || axisType.equals("Both"));
		
		// sanity check the min and max
		try {
			
			float max = Float.parseFloat(maxTextfield.getText().trim());
			float min = Float.parseFloat(minTextfield.getText().trim());
			
			if(maxLabel.getText().equals("Frequency Maximum: ")) {
				max = (float) Math.floor(max);
				min = (float) Math.floor(min);
			}
			
			// clip to limits
			if(maxLabel.getText().equals("Relative Frequency Maximum: ")) {
				if(max > relativeFrequencyUpperLimit) max = relativeFrequencyUpperLimit;
				if(max < relativeFrequencyLowerLimit) max = relativeFrequencyLowerLimit;
				if(min > relativeFrequencyUpperLimit) min = relativeFrequencyUpperLimit;
				if(min < relativeFrequencyLowerLimit) min = relativeFrequencyLowerLimit;
			} else {
				if(max > frequencyUpperLimit) max = frequencyUpperLimit;
				if(max < frequencyLowerLimit) max = frequencyLowerLimit;
				if(min > frequencyUpperLimit) min = frequencyUpperLimit;
				if(min < frequencyLowerLimit) min = frequencyLowerLimit;
			}
			
			// ensure min < max
			if(maxLabel.getText().equals("Relative Frequency Maximum: ")) {
				if(min == max) {
					if(max == relativeFrequencyUpperLimit)
						min = Math.nextDown(min);
					else
						max = Math.nextUp(max);
				} else if(min > max) {
					float temp = max;
					max = min;
					min = temp;
				}
			} else {
				if(min == max) {
					if(max == frequencyUpperLimit)
						min--;
					else
						max++;
				} else if(min > max) {
					float temp = max;
					max = min;
					min = temp;
				}
			}
			
			// update textfields
			maxTextfield.setText(Float.toString(max));
			minTextfield.setText(Float.toString(min));
			maxHandler.accept(maxCheckbox.isSelected(), max);
			minHandler.accept(minCheckbox.isSelected(), min);
			
		} catch(Exception e) {
			
			// one of the textfields doesn't contain a valid number, so reset both to defaults
			if(axisType.equals("Relative Frequency") || axisType.equals("Both")) {
				maxTextfield.setText(Float.toString(relativeFrequencyDefaultMaximum));
				minTextfield.setText(Float.toString(relativeFrequencyDefaultMinimum));
				maxHandler.accept(maxCheckbox.isSelected(), relativeFrequencyDefaultMaximum);
				minHandler.accept(minCheckbox.isSelected(), relativeFrequencyDefaultMinimum);
			} else {
				maxTextfield.setText(Float.toString(frequencyDefaultMaximum));
				minTextfield.setText(Float.toString(frequencyDefaultMinimum));
				maxHandler.accept(maxCheckbox.isSelected(), frequencyDefaultMaximum);
				minHandler.accept(minCheckbox.isSelected(), frequencyDefaultMinimum);
			}
			
		}
		
		// disable textboxes for autoscaled values
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
		boolean yAxisShowsRelativeFrequency = ChartUtils.parseBoolean(lines.remove(), "y-axis shows relative frequency = %b");
		boolean yAxisShowsFrequency         = ChartUtils.parseBoolean(lines.remove(), "y-axis shows frequency = %b");
		boolean yMinimumIsZero              = ChartUtils.parseBoolean(lines.remove(), "y-axis minimum is zero = %b");
		boolean yAutoscaleMax               = ChartUtils.parseBoolean(lines.remove(), "y-axis autoscale maximum = %b");
		float manualMinY                    = ChartUtils.parseFloat  (lines.remove(), "y-axis manual minimum = %f");
		float manualMaxY                    = ChartUtils.parseFloat  (lines.remove(), "y-axis manual maximum = %f");
		
		// update the widget
		String type = (yAxisShowsRelativeFrequency && yAxisShowsFrequency) ? "Both" : yAxisShowsRelativeFrequency ? "Relative Frequency" : "Frequency";
		for(int i = 0; i < axisTypeCombobox.getItemCount(); i++)
			if(axisTypeCombobox.equals(type))
				axisTypeCombobox.setSelectedIndex(i);

		minCheckbox.setSelected(yMinimumIsZero);
		minTextfield.setText(Float.toString(manualMinY));
		
		maxCheckbox.setSelected(yAutoscaleMax);
		maxTextfield.setText(Float.toString(manualMaxY));
		
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
			"y-axis shows relative frequency = " + (axisTypeCombobox.getSelectedIndex() == 0 || axisTypeCombobox.getSelectedIndex() == 2),
			"y-axis shows frequency = " + (axisTypeCombobox.getSelectedIndex() == 1 || axisTypeCombobox.getSelectedIndex() == 2),
			"y-axis minimum is zero = " + minCheckbox.isSelected(),
			"y-axis autoscale maximum = " + maxCheckbox.isSelected(),
			"y-axis manual minimum = " + minTextfield.getText(),
			"y-axis manual maximum = " + maxTextfield.getText()
		};
		
	}

}
