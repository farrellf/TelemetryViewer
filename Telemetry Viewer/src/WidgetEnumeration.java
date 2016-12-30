import java.awt.GridLayout;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * A widget that lets the user pick one choice from a drop-down list.
 */
@SuppressWarnings("serial")
public class WidgetEnumeration extends JPanel {
	
	JLabel label;
	JComboBox<String> combobox;
	
	public WidgetEnumeration(String labelText, String[] choices) {
		
		super();
		
		setLayout(new GridLayout(1, 2, 10, 10));
		
		label = new JLabel(labelText + ": ");
		add(label);
		
		combobox = new JComboBox<String>(choices);
		add(combobox);
		
	}
	
	/**
	 * @return    The chosen value.
	 */
	public String getValue() {
		
		return (String) combobox.getSelectedItem();
		
	}

}
