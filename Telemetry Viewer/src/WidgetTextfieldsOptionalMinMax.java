import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

/**
 * A widget that lets the user make minimum and maximum values be automatic or specified. 
 */
@SuppressWarnings("serial")
public class WidgetTextfieldsOptionalMinMax extends JPanel {
	
	JLabel maxLabel;
	JLabel minLabel;
	JCheckBox maxCheckbox;
	JCheckBox minCheckbox;
	JTextField maxTextfield;
	JTextField minTextfield;
	float upperLimit;
	float lowerLimit;
	float defaultMaximum;
	float defaultMinimum;
	
	/**
	 * @param labelPrefix       Text to show before "Maximum" or "Minimum"
	 * @param defaultMinimum    Default value for minimum.
	 * @param defaultMaximum    Default value for maximum.
	 * @param lowerLimit        Minimum allowed value.
	 * @param upperLimit        Maximum allowed value.
	 */
	public WidgetTextfieldsOptionalMinMax(String labelPrefix, float defaultMinimum, float defaultMaximum, float lowerLimit, float upperLimit) {
		
		super();
		
		maxLabel = new JLabel(labelPrefix + " Maximum: ");
		minLabel = new JLabel(labelPrefix + " Minimum: ");
		maxCheckbox = new JCheckBox("Automatic");
		minCheckbox = new JCheckBox("Automatic");
		maxCheckbox.setSelected(true);
		minCheckbox.setSelected(true);
		maxTextfield = new JTextField(Float.toString(defaultMaximum));
		minTextfield = new JTextField(Float.toString(defaultMinimum));
		maxTextfield.setEnabled(false);
		minTextfield.setEnabled(false);
		this.upperLimit = upperLimit;
		this.lowerLimit = lowerLimit;
		this.defaultMaximum = defaultMaximum;
		this.defaultMinimum = defaultMinimum;
		
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
		
		setLayout(new GridLayout(2, 2, 10, 10));
		
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
	 * Ensures that minimum < maximum.
	 */
	private void sanityCheckMinMax() {
		
		try {
			
			maxTextfield.setText(maxTextfield.getText().trim());
			minTextfield.setText(minTextfield.getText().trim());
			
			float max = Float.parseFloat(maxTextfield.getText());
			float min = Float.parseFloat(minTextfield.getText());
			
			// clip to limits
			if(max > upperLimit) max = upperLimit;
			if(max < lowerLimit) max = lowerLimit;
			if(min > upperLimit) min = upperLimit;
			if(min < lowerLimit) min = lowerLimit;
			
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
			maxTextfield.setText(Float.toString(max));
			minTextfield.setText(Float.toString(min));
			
		} catch(Exception e) {
			
			// one of the textfields doesn't contain a valid number, so reset both to defaults
			maxTextfield.setText(Float.toString(defaultMaximum));
			minTextfield.setText(Float.toString(defaultMinimum));
			
		}
		
	}
	
	/**
	 * @return    True if the maximum should be automatic.
	 */
	public boolean isMaximumAutomatic() {
		
		return maxCheckbox.isSelected();
		
	}
	
	/**
	 * @return    True if the minimum should be automatic.
	 */
	public boolean isMinimumAutomatic() {
		
		return minCheckbox.isSelected();
		
	}
	
	 /** 
	 * @return    The maximum value.
	 */
	public float getMaximumValue() {
		
		return Float.parseFloat(maxTextfield.getText());
		
	}
	
	 /** 
	 * @return    The minimum value.
	 */
	public float getMinimumValue() {
		
		return Float.parseFloat(minTextfield.getText());
		
	}

}
