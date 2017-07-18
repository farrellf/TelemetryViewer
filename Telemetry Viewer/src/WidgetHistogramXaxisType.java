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
	float defaultMax;
	float defaultMin;
	float defaultCenter;
	BiConsumer<Boolean, Float> minHandler;
	BiConsumer<Boolean, Float> maxHandler;
	BiConsumer<Boolean, Float> centerHandler;
	
	/**
	 * A widget that lets the user specify the x-axis type for a histogram.
	 * 
	 * The axis can be "normal" where the minimum/maximum values can either be autoscaled or specified.
	 * Or the axis can be "locked center" where the user specifies the center value.
	 * In the locked-center mode, the minimum/maximum will autoscale as needed, but in a way that keeps the center of the axis at the specified value.
	 * 
	 * @param defaultMin            Default value for "normal" axis minimum.
	 * @param defaultMax            Default value for "normal" axis maximum.
	 * @param defaultCenter         Default value for "locked center" axis center.
	 * @param lowerLimit            Minimum allowed value.
	 * @param upperLimit            Maximum allowed value.
	 * @param minEventHandler       Will be notified when the minimum changes.
	 * @param maxEventHandler       Will be notified when the maximum changes.
	 * @param centerEventHandler    Will be notified when the center changes.
	 */
	public WidgetHistogramXaxisType(float defaultMin, float defaultMax, float defaultCenter, float lowerLimit, float upperLimit, BiConsumer<Boolean, Float> minEventHandler, BiConsumer<Boolean, Float> maxEventHandler, BiConsumer<Boolean, Float> centerEventHandler) {
		
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
		maxTextfield = new JTextField(Float.toString(defaultMax));
		minTextfield = new JTextField(Float.toString(defaultMin));
		maxTextfield.setEnabled(false);
		minTextfield.setEnabled(false);
		centerTextfield = new JTextField(Float.toString(defaultCenter));
		
		this.upperLimit = upperLimit;
		this.lowerLimit = lowerLimit;
		this.defaultMax = defaultMax;
		this.defaultMin = defaultMin;
		this.defaultCenter = defaultCenter;
		this.minHandler = minEventHandler;
		this.maxHandler = maxEventHandler;
		this.centerHandler = centerEventHandler;
		
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
		
		centerTextfield.addFocusListener(new FocusListener() {
			@Override public void focusLost(FocusEvent fe)   { sanityCheck(); }
			@Override public void focusGained(FocusEvent fe) { centerTextfield.selectAll(); }
		});
		
		setLayout(new GridLayout(3, 2, 10, 10));
		
		axisTypeCombobox.addActionListener(event -> sanityCheck());
		
		sanityCheck();
		
	}
	
	/**
	 * Ensures the min and max values are within the allowed range, and that minimum < maximum.
	 * Ensures the center value is within the allowed range.
	 * Shows/hides/disables widgets based on the selected axis type and autoscale selections.
	 * 
	 * Notifies all handlers.
	 */
	public void sanityCheck() {
		
		// sanity check the min and max
		try {
			
			// clip to limits
			float max = Float.parseFloat(maxTextfield.getText().trim());
			float min = Float.parseFloat(minTextfield.getText().trim());
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
			maxHandler.accept(maxCheckbox.isSelected(), max);
			minHandler.accept(minCheckbox.isSelected(), min);
			
		} catch(Exception e) {
			
			// one of the textfields doesn't contain a valid number, so reset both to defaults
			maxTextfield.setText(Float.toString(defaultMax));
			minTextfield.setText(Float.toString(defaultMin));
			maxHandler.accept(maxCheckbox.isSelected(), defaultMax);
			minHandler.accept(minCheckbox.isSelected(), defaultMin);
			
		}
		
		// sanity check the center value
		try {
			
			// clip to limits
			float center = Float.parseFloat(centerTextfield.getText().trim());
			if(center > upperLimit) center = upperLimit;
			if(center < lowerLimit) center = lowerLimit;
			centerTextfield.setText(Float.toString(center));
			centerHandler.accept(axisTypeCombobox.getSelectedItem().toString().equals("Locked Center"), center);
			
		} catch(Exception e) {
			
			// not a valid number, so reset to default
			centerTextfield.setText(Float.toString(defaultCenter));
			centerHandler.accept(axisTypeCombobox.getSelectedItem().toString().equals("Locked Center"), defaultCenter);
			
		}
		
		// disable textboxes for autoscaled values
		minTextfield.setEnabled(!minCheckbox.isSelected());
		maxTextfield.setEnabled(!maxCheckbox.isSelected());
		
		// redraw depending on the axis type
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
	
	/**
	 * Sets the axis minimum to be either autoscaled or manually scaled, and specifies the manual scale.
	 * This should be called after importing a chart since the widget will not be in sync with the chart's state.
	 * 
	 * @param autoscaleMin    True for autoscaled, false for manually scaled.
	 * @param manualMin       Minimum to use if manually scaled.
	 */
	public void setAxisMin(boolean autoscaleMin, float manualMin) {
		
		minCheckbox.setSelected(autoscaleMin);
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
	
	/**
	 * Sets the axis type.
	 * This should be called after importing a chart since the widget will not be in sync with the chart's state.
	 * 
	 * @param centered    True if "Locked Center" or false if "Normal".
	 * @param center    Center value if the axis type is Locked Center.
	 */
	public void setAxisType(boolean centered, float center) {
		
		axisTypeCombobox.setSelectedItem(centered ? "Locked Center" : "Normal");
		centerTextfield.setText(Float.toString(center));
		
	}

}
