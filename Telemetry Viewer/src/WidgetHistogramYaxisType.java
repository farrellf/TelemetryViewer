import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

/**
 * A widget that lets the user specify the y-axis type for a histogram.
 * 
 * The axis can show relative frequency, frequency, or both.
 * If both are shown, the left axis will be relative frequency and the right axis will be frequency.
 * 
 * The minimum/maximum values can either be automatic or specified.
 */
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
	
	/**
	 * @param relativeFrequencyDefaultMinimum     Default value for axis minimum.
	 * @param relativeFrequencyDefaultMaximum     Default value for axis maximum.
	 * @param relativeFrequencyLowerLimit         Minimum allowed value.
	 * @param relativeFrequencyUpperLimit         Maximum allowed value.
	 * @param frequencyDefaultMinimum             Default value for axis minimum.
	 * @param frequencyDefaultMaximum             Default value for axis maximum.
	 * @param frequencyLowerLimit                 Minimum allowed value.
	 * @param frequencyUpperLimit                 Maximum allowed value.
	 */
	public WidgetHistogramYaxisType(float relativeFrequencyDefaultMinimum, float relativeFrequencyDefaultMaximum, float relativeFrequencyLowerLimit, float relativeFrequencyUpperLimit, float frequencyDefaultMinimum, float frequencyDefaultMaximum, float frequencyLowerLimit, float frequencyUpperLimit) {
		
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
		
		axisTypeCombobox.addActionListener(new ActionListener() {
			@Override public void actionPerformed(ActionEvent ae) {
				if(axisTypeCombobox.getSelectedItem().toString().equals("Relative Frequency") || axisTypeCombobox.getSelectedItem().toString().equals("Both")) {
					maxLabel.setText("Relative Frequency Maximum: ");
					minLabel.setText("Relative Frequency Minimum: ");
				} else if(axisTypeCombobox.getSelectedItem().toString().equals("Frequency")) {
					maxLabel.setText("Frequency Maximum: ");
					minLabel.setText("Frequency Minimum: ");
				}
			}
		});
		
		maxCheckbox.addActionListener(new ActionListener() {
			@Override public void actionPerformed(ActionEvent ae) {
				maxTextfield.setEnabled(!maxCheckbox.isSelected());
			}
		});
		
		minCheckbox.addActionListener(new ActionListener() {
			@Override public void actionPerformed(ActionEvent ae) {
				minTextfield.setEnabled(!minCheckbox.isSelected());
			}
		});
		
		maxTextfield.addFocusListener(new FocusListener() {
			@Override public void focusLost(FocusEvent fe) {
				sanityCheckMinMax();
			}
			
			@Override public void focusGained(FocusEvent e) {
				maxTextfield.selectAll();
			}
		});
		
		minTextfield.addFocusListener(new FocusListener() {
			@Override public void focusLost(FocusEvent fe) {
				sanityCheckMinMax();
			}
			
			@Override public void focusGained(FocusEvent e) {
				minTextfield.selectAll();
			}
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
		
	}
	
	/**
	 * Ensures that minimum < maximum and that they are within limits.
	 */
	private void sanityCheckMinMax() {
		
		try {
			
			maxTextfield.setText(maxTextfield.getText().trim());
			minTextfield.setText(minTextfield.getText().trim());
			
			float max = Float.parseFloat(maxTextfield.getText());
			float min = Float.parseFloat(minTextfield.getText());
			
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
			
		} catch(Exception e) {
			
			// one of the textfields doesn't contain a valid number, so reset both to defaults
			maxTextfield.setText(Float.toString(relativeFrequencyDefaultMaximum));
			minTextfield.setText(Float.toString(relativeFrequencyDefaultMinimum));
			
		}
		
	}
	
	/**
	 * @return    True if relative frequency should be shown.
	 */
	public boolean isRelativeFrequencyShown() {
		
		return axisTypeCombobox.getSelectedItem().toString().equals("Relative Frequency") || axisTypeCombobox.getSelectedItem().toString().equals("Both");
		
	}
	
	/**
	 * @return    True if frequency should be shown.
	 */
	public boolean isFrequencyShown() {
		
		return axisTypeCombobox.getSelectedItem().toString().equals("Frequency") || axisTypeCombobox.getSelectedItem().toString().equals("Both");
		
	}
	
	/**
	 * @return    True if the maximum should be automatic.
	 */
	public boolean isMaximumAutomatic() {
		
		return maxCheckbox.isSelected();
		
	}
	
	/**
	 * @return    True if the minimum should be zero.
	 */
	public boolean isMinimumZero() {
		
		return minCheckbox.isSelected();
		
	}
	
	 /** 
	 * @return    The maximum value for this axis.
	 */
	public float getMaximumValue() {
		
		return Float.parseFloat(maxTextfield.getText());
		
	}
	
	 /** 
	 * @return    The minimum value for this axis.
	 */
	public float getMinimumValue() {
		
		return Float.parseFloat(minTextfield.getText());
		
	}

}
