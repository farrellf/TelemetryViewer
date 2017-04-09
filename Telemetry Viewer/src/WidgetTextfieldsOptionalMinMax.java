import java.awt.GridLayout;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.function.BiConsumer;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

@SuppressWarnings("serial")
public class WidgetTextfieldsOptionalMinMax extends JPanel {
	
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
		
		this.upperLimit = upperLimit;
		this.lowerLimit = lowerLimit;
		this.defaultMax = defaultMax;
		this.defaultMin = defaultMin;
		minHandler = minEventHandler;
		maxHandler = maxEventHandler;
		
		maxTextfield = new JTextField(Float.toString(defaultMax));
		maxTextfield.setEnabled(false);
		maxTextfield.addFocusListener(new FocusListener() {
			@Override public void focusLost(FocusEvent fe)   { checkAndNotifyHandlers(); }
			@Override public void focusGained(FocusEvent fe) { maxTextfield.selectAll(); }
		});
		
		maxCheckbox = new JCheckBox("Automatic");
		maxCheckbox.setSelected(true);
		maxCheckbox.addActionListener(event -> checkAndNotifyHandlers());
		
		minTextfield = new JTextField(Float.toString(defaultMin));
		minTextfield.setEnabled(false);
		minTextfield.addFocusListener(new FocusListener() {
			@Override public void focusLost(FocusEvent fe)   { checkAndNotifyHandlers(); }
			@Override public void focusGained(FocusEvent fe) { minTextfield.selectAll(); }
		});
		
		minCheckbox = new JCheckBox("Automatic");
		minCheckbox.setSelected(true);
		minCheckbox.addActionListener(event -> checkAndNotifyHandlers());

		setLayout(new GridLayout(2, 2, 10, 10));
		
		JPanel maxPanel = new JPanel();
		maxPanel.setLayout(new BoxLayout(maxPanel, BoxLayout.X_AXIS));
		maxPanel.add(maxCheckbox);
		maxPanel.add(Box.createHorizontalStrut(10));
		maxPanel.add(maxTextfield);
		
		add(new JLabel(labelPrefix + " Maximum: "));
		add(maxPanel);
		
		JPanel minPanel = new JPanel();
		minPanel.setLayout(new BoxLayout(minPanel, BoxLayout.X_AXIS));
		minPanel.add(minCheckbox);
		minPanel.add(Box.createHorizontalStrut(10));
		minPanel.add(minTextfield);
		
		add(new JLabel(labelPrefix + " Minimum: "));
		add(minPanel);
		
		checkAndNotifyHandlers();
		
	}
	
	/**
	 * Ensures that both values are within the allowed range, and that minimum < maximum.
	 * Disables min/max textfields if they are autoscaled.
	 * Notifies all handlers.
	 */
	private void checkAndNotifyHandlers() {
		
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
	 * Sets the minimum to be either autoscaled or manually scaled, and specifies the manual scale.
	 * This should be called after importing a chart since the widget will not be in sync with the chart's state.
	 * 
	 * @param autoscaleMin    True for autoscaled, false for manually scaled.
	 * @param manualMin       Minimum to use if manually scaled.
	 */
	public void setMin(boolean autoscaleMin, float manualMin) {
		
		minCheckbox.setSelected(autoscaleMin);
		minTextfield.setText(Float.toString(manualMin));
		
	}
	
	/**
	 * Sets the maximum to be either autoscaled or manually scaled, and specifies the manual scale.
	 * This should be called after importing a chart since the widget will not be in sync with the chart's state.
	 * 
	 * @param autoscaleMax    True for autoscaled, false for manually scaled.
	 * @param manualMax       Maximum to use if manually scaled.
	 */
	public void setMax(boolean autoscaleMax, float manualMax) {
		
		maxCheckbox.setSelected(autoscaleMax);
		maxTextfield.setText(Float.toString(manualMax));
		
	}

}
