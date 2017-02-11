import java.awt.GridLayout;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * A widget that lets the user pick one dataset from a drop-down list.
 */
@SuppressWarnings("serial")
public class WidgetDataset extends JPanel {
	
	JLabel label;
	JComboBox<Dataset> combobox;

	public WidgetDataset(String labelText) {
		
		super();
		
		setLayout(new GridLayout(1, 2, 10, 10));
		
		label = new JLabel(labelText + ": ");
		add(label);
		
		combobox = new JComboBox<Dataset>(Controller.getAllDatasets());
		add(combobox);
		
	}
	
	/**
	 * @return    The selected dataset, as a one-element array because the charts use arrays.
	 */
	public Dataset[] getDataset() {
		
		return new Dataset[] {(Dataset) combobox.getSelectedItem()};
		
	}

}
