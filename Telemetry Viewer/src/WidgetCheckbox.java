import java.awt.GridLayout;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * A widget that lets the user check or uncheck a checkbox.
 */
@SuppressWarnings("serial")
public class WidgetCheckbox extends JPanel {

	JCheckBox checkbox;
	
	/**
	 * @param labelText    Label to show at the right of the checkbox.
	 * @param isChecked    True to default to checked.
	 */
	public WidgetCheckbox(String labelText, boolean isChecked) {
		
		super();
		
		setLayout(new GridLayout(1, 2, 10, 10));
		
		checkbox = new JCheckBox(labelText);
		checkbox.setSelected(isChecked);
		
		add(new JLabel());
		add(checkbox);
		
	}
	
	/**
	 * @return    True if the checkbox is checked.
	 */
	public boolean isChecked() {
		
		return checkbox.isSelected();
		
	}

}
