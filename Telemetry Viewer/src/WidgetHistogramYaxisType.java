import java.awt.GridLayout;
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

@SuppressWarnings("serial")
public class WidgetHistogramYaxisType extends JPanel {
	
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
		
		setLayout(new GridLayout(3, 2, 10, 10));
		
		add(axisTypeLabel);
		add(axisTypeCombobox);
		
		JPanel maxPanel = new JPanel();
		maxPanel.setLayout(new BoxLayout(maxPanel, BoxLayout.X_AXIS));
		maxPanel.add(maxCheckbox);
		maxPanel.add(Box.createHorizontalStrut(10));
		maxPanel.add(maxTextfield);
		add(maxLabel);
		add(maxPanel);
		
		JPanel minPanel = new JPanel();
		minPanel.setLayout(new BoxLayout(minPanel, BoxLayout.X_AXIS));
		minPanel.add(minCheckbox);
		minPanel.add(Box.createHorizontalStrut(10));
		minPanel.add(minTextfield);
		add(minLabel);
		add(minPanel);
		
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
	 * Sets the axis type.
	 * This should be called after importing a chart since the widget will not be in sync with the chart's state.
	 * 
	 * @param relativeFrequency    True if the axis shows Relative Frequency.
	 * @param frequency            True if the axis show Frequency.
	 */
	public void setAxisType(boolean relativeFrequency, boolean frequency) {
		
		String s;
		if(relativeFrequency && frequency)
			s = "Both";
		else if(relativeFrequency)
			s = "Relative Frequency";
		else
			s = "Frequency";
		
		for(int i = 0; i < axisTypeCombobox.getItemCount(); i++)
			if(axisTypeCombobox.equals(s))
				axisTypeCombobox.setSelectedIndex(i);
		
	}
	
	/**
	 * Sets the axis minimum to be either zero or manually scaled, and specifies the manual scale.
	 * This should be called after importing a chart since the widget will not be in sync with the chart's state.
	 * 
	 * @param minIsZero    True for the minimum is zero, false for manually scaled.
	 * @param manualMin    Minimum to use if manually scaled.
	 */
	public void setAxisMin(boolean minIsZero, float manualMin) {
		
		minCheckbox.setSelected(minIsZero);
		minTextfield.setText(Float.toString(manualMin));
		
	}
	
	/**
	 * Sets the axis maximum to be either autoscaled or manually scaled, and specifies the manual scale.
	 * This should be called after importing a chart since the widget will not be in sync with the chart's state.
	 * 
	 * @param autoscaleMax    True for autoscaled, false for manually scaled.
	 * @param manualMax       Maximum to use if manually scaled.
	 */
	public void setAxisMax(boolean autoscaleMax, float manualMax) {
		
		maxCheckbox.setSelected(autoscaleMax);
		maxTextfield.setText(Float.toString(manualMax));
		
	}

}
