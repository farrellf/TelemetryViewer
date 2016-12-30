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
 * A widget that lets the user specify the x-axis type for a histogram.
 * 
 * The axis can be "normal" where the minimum/maximum values can either be automatic or specified.
 * Or the axis can be "locked center" where the user specifies the center value.
 * In the locked-center mode, the minimum/maximum will autoscale as needed, but in a way that keeps the center of the axis at the specified value.
 */
@SuppressWarnings("serial")
public class WidgetHistogramXaxisType extends JPanel {
	
	JLabel axisTypeLabel;
	JComboBox<String> axisTypeCombobox;
	JLabel maxLabel;
	JLabel minLabel;
	JLabel centerLabel;
	JCheckBox maxCheckbox;
	JCheckBox minCheckbox;
	JTextField maxTextfield;
	JTextField minTextfield;
	JTextField centerTextfield;
	float upperLimit;
	float lowerLimit;
	float defaultMaximum;
	float defaultMinimum;
	float defaultCentered;
	
	/**
	 * @param defaultMinimum     Default value for "normal" axis minimum.
	 * @param defaultMaximum     Default value for "normal" axis maximum.
	 * @param defaultCentered    Default value for "locked center" axis center.
	 * @param lowerLimit         Minimum allowed value.
	 * @param upperLimit         Maximum allowed value.
	 */
	public WidgetHistogramXaxisType(float defaultMinimum, float defaultMaximum, float defaultCentered, float lowerLimit, float upperLimit) {
		
		super();
		
		axisTypeLabel = new JLabel("X-Axis Type: ");
		axisTypeCombobox = new JComboBox<String>(new String[] {"Normal", "Locked Center"});
		maxLabel = new JLabel("X-Axis Maximum: ");
		minLabel = new JLabel("X-Axis Minimum: ");
		centerLabel = new JLabel("X-Axis Center: ");
		maxCheckbox = new JCheckBox("Automatic");
		minCheckbox = new JCheckBox("Automatic");
		maxCheckbox.setSelected(true);
		minCheckbox.setSelected(true);
		maxTextfield = new JTextField(Float.toString(defaultMaximum));
		minTextfield = new JTextField(Float.toString(defaultMinimum));
		maxTextfield.setEnabled(false);
		minTextfield.setEnabled(false);
		centerTextfield = new JTextField(Float.toString(defaultCentered));
		this.upperLimit = upperLimit;
		this.lowerLimit = lowerLimit;
		this.defaultMaximum = defaultMaximum;
		this.defaultMinimum = defaultMinimum;
		this.defaultCentered = defaultCentered;
		
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
		
		centerTextfield.addFocusListener(new FocusListener() {
			@Override public void focusLost(FocusEvent fe) {
				try {
					
					// clip to limits
					centerTextfield.setText(centerTextfield.getText().trim());
					float center = Float.parseFloat(centerTextfield.getText());
					if(center > upperLimit) center = upperLimit;
					if(center < lowerLimit) center = lowerLimit;
					centerTextfield.setText(Float.toString(center));
					
				} catch(Exception e) {
					
					// not a valid number, so reset to default
					centerTextfield.setText(Float.toString(defaultCentered));
					
				}
			}
			
			@Override public void focusGained(FocusEvent e) {
				centerTextfield.selectAll();
			}
		});
		
		setLayout(new GridLayout(3, 2, 10, 10));
		
		axisTypeCombobox.addActionListener(new ActionListener() {
			@Override public void actionPerformed(ActionEvent e) {

				if(axisTypeCombobox.getSelectedItem().toString().equals("Normal")) {
					
					removeAll();
					
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
					
					revalidate();
					repaint();
					
				} else if(axisTypeCombobox.getSelectedItem().toString().equals("Locked Center")) {
					
					removeAll();
					
					add(axisTypeLabel);
					add(axisTypeCombobox);
					
					add(centerLabel);
					add(centerTextfield);

					// adding invisible stuff to ensure size does not change
					JLabel dummyLabel = new JLabel(" ");
					JPanel dummyPanel = new JPanel();
					dummyPanel.setLayout(new BoxLayout(dummyPanel, BoxLayout.X_AXIS));
					dummyPanel.add(new JCheckBox(" "));
					dummyPanel.add(Box.createHorizontalStrut(10));
					dummyPanel.add(new JTextField());
					dummyPanel.setVisible(false);
					add(dummyLabel);
					add(dummyPanel);
					
					revalidate();
					repaint();
					
				}
				
			}
		});
		
		axisTypeCombobox.getActionListeners()[0].actionPerformed(null);
		
	}
	
	/**
	 * Ensures that minimum < maximum.
	 */
	private void sanityCheckMinMax() {
		
		try {
			
			// clip to limits
			maxTextfield.setText(maxTextfield.getText().trim());
			minTextfield.setText(minTextfield.getText().trim());
			float max = Float.parseFloat(maxTextfield.getText());
			float min = Float.parseFloat(minTextfield.getText());
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
	 * Checks if the axis mode is "normal" or "locked center"
	 * 
	 * @return    True if the axis mode is "locked center"
	 */
	public boolean isAxisCentered() {
		
		return axisTypeCombobox.getSelectedItem().toString().equals("Locked Center");
		
	}
	
	/**
	 * Gets the axis center value. This should only be called if isAxisCentered() returns true.
	 * 
	 * @return    The center value for this axis.
	 */
	public float getCenterValue() {
		
		return Float.parseFloat(centerTextfield.getText());
		
	}
	
	/**
	 * Checks if the axis maximum should be automatic. This should only be called if isAxisCentered() returns false.
	 * 
	 * @return    True if the maximum should be automatic.
	 */
	public boolean isMaximumAutomatic() {
		
		return maxCheckbox.isSelected();
		
	}
	
	/**
	 * Checks if the axis minimum should be automatic. This should only be called if isAxisCentered() returns false.
	 * 
	 * @return    True if the minimum should be automatic.
	 */
	public boolean isMinimumAutomatic() {
		
		return minCheckbox.isSelected();
		
	}
	
	/**
	 * Gets the axis maximum value. This should only be called if isAxisCentered() returns false and isMaximumAutomatic() returns false.
	 * 
	 * @return    The maximum value for this axis.
	 */
	public float getMaximumValue() {
		
		return Float.parseFloat(maxTextfield.getText());
		
	}
	
	/**
	 * Gets the axis minimum value. This should only be called if isAxisCentered() returns false and isMinimumAutomatic() returns false.
	 * 
	 * @return    The minimum value for this axis.
	 */
	public float getMinimumValue() {
		
		return Float.parseFloat(minTextfield.getText());
		
	}

}
