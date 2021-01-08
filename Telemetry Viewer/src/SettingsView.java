import java.awt.Dimension;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.Hashtable;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JTextField;
import net.miginfocom.swing.MigLayout;

/**
 * The SettingsView and SettingsController classes form the MVC that manage GUI-related settings.
 * Settings can be changed when the user interacts with the GUI or opens a Layout file.
 * This class is the GUI that is optionally shown at the left side of the screen.
 */
@SuppressWarnings("serial")
public class SettingsView extends JPanel {
	
	static SettingsView instance = new SettingsView();
	
	JTextField tileColumnsTextfield;
	JTextField tileRowsTextfield;
	
	JComboBox<String> timeFormatCombobox;
	JCheckBox timeFormat24hoursCheckbox;
	
	JCheckBox hintNotificationsCheckbox;
	JButton   hintNotificationsColorButton;
	JCheckBox warningNotificationsCheckbox;
	JButton   warningNotificationsColorButton;
	JCheckBox failureNotificationsCheckbox;
	JButton   failureNotificationsColorButton;
	JCheckBox verboseNotificationsCheckbox;
	JButton   verboseNotificationsColorButton;
	
	JCheckBox showTooltipsCheckbox;
	JCheckBox enableSmoothScrollingCheckbox;
	JSlider   antialiasingLevelSlider;
	JCheckBox showFpsCheckbox;
	JCheckBox showBenchmarksCheckbox;
	
	/**
	 * Private constructor to enforce singleton usage.
	 */
	private SettingsView() {
		
		super();
		setLayout(new MigLayout("wrap 2, insets" + Theme.padding + " " + Theme.padding + " " + Theme.padding + " 0, gap " + Theme.padding));
		
		// tile columns and rows
		tileColumnsTextfield = new JTextField(Integer.toString(SettingsController.getTileColumns()));
		tileColumnsTextfield.addFocusListener(new FocusListener() {
			@Override public void focusLost(FocusEvent fe) {
				try {
					SettingsController.setTileColumns(Integer.parseInt(tileColumnsTextfield.getText().trim()));
				} catch(Exception e) {
					tileColumnsTextfield.setText(Integer.toString(SettingsController.getTileColumns()));
				}
			}
			
			@Override public void focusGained(FocusEvent fe) {
				tileColumnsTextfield.selectAll();
			}
		});
		
		tileRowsTextfield = new JTextField(Integer.toString(SettingsController.getTileRows()));
		tileRowsTextfield.addFocusListener(new FocusListener() {
			@Override public void focusLost(FocusEvent fe) {
				try {
					SettingsController.setTileRows(Integer.parseInt(tileRowsTextfield.getText().trim()));
				} catch(Exception e) {
					tileRowsTextfield.setText(Integer.toString(SettingsController.getTileRows()));
				}
			}
			
			@Override public void focusGained(FocusEvent fe) {
				tileRowsTextfield.selectAll();
			}
		});
		
		add(new JLabel("Tile Columns: "));
		add(tileColumnsTextfield, "grow x");
		add(new JLabel("Tile Rows: "));
		add(tileRowsTextfield, "grow x");
		add(Box.createVerticalStrut(2 * Theme.padding), "span 2");
		
		// time format
		timeFormatCombobox = new JComboBox<String>(SettingsController.getTimeFormats());
		String format = SettingsController.getTimeFormat();
		for(int i = 0; i < timeFormatCombobox.getItemCount(); i++)
			if(timeFormatCombobox.getItemAt(i).equals(format))
				timeFormatCombobox.setSelectedIndex(i);
		timeFormatCombobox.addActionListener(event -> SettingsController.setTimeFormat(timeFormatCombobox.getSelectedItem().toString()));
		timeFormat24hoursCheckbox = new JCheckBox("Show 24-Hour Time", SettingsController.getTimeFormat24hours());
		timeFormat24hoursCheckbox.addActionListener(event -> SettingsController.setTimeFormat24hours(timeFormat24hoursCheckbox.isSelected()));
		
		add(new JLabel("Time Format: "));
		add(timeFormatCombobox);
		add(timeFormat24hoursCheckbox, "span 2, grow x");
		add(Box.createVerticalStrut(2 * Theme.padding), "span 2");
		
		// notifications
		hintNotificationsCheckbox = new JCheckBox("Show Hint Notifications", SettingsController.getHintNotificationVisibility());
		hintNotificationsCheckbox.addActionListener(event -> SettingsController.setHintNotificationVisibility(hintNotificationsCheckbox.isSelected()));
		hintNotificationsColorButton = new JButton("\u25B2");
		hintNotificationsColorButton.setForeground(SettingsController.getHintNotificationColor());
		hintNotificationsColorButton.addActionListener(event -> SettingsController.setHintNotificationColor(ColorPickerView.getColor("Hint Notifications", SettingsController.getHintNotificationColor(), false)));
		
		add(hintNotificationsCheckbox, "split 2, span 2, grow x");
		add(hintNotificationsColorButton);
		
		warningNotificationsCheckbox = new JCheckBox("Show Warning Notifications", SettingsController.getWarningNotificationVisibility());
		warningNotificationsCheckbox.addActionListener(event -> SettingsController.setWarningNotificationVisibility(warningNotificationsCheckbox.isSelected()));
		warningNotificationsColorButton = new JButton("\u25B2");
		warningNotificationsColorButton.setForeground(SettingsController.getWarningNotificationColor());
		warningNotificationsColorButton.addActionListener(event -> SettingsController.setWarningNotificationColor(ColorPickerView.getColor("Warning Notifications", SettingsController.getWarningNotificationColor(), false)));
		
		add(warningNotificationsCheckbox, "split 2, span 2, grow x");
		add(warningNotificationsColorButton);
		
		failureNotificationsCheckbox = new JCheckBox("Show Failure Notifications", SettingsController.getFailureNotificationVisibility());
		failureNotificationsCheckbox.addActionListener(event -> SettingsController.setFailureNotificationVisibility(failureNotificationsCheckbox.isSelected()));
		failureNotificationsColorButton = new JButton("\u25B2");
		failureNotificationsColorButton.setForeground(SettingsController.getFailureNotificationColor());
		failureNotificationsColorButton.addActionListener(event -> SettingsController.setFailureNotificationColor(ColorPickerView.getColor("Failure Notifications", SettingsController.getFailureNotificationColor(), false)));
		
		add(failureNotificationsCheckbox, "split 2, span 2, grow x");
		add(failureNotificationsColorButton);
		
		verboseNotificationsCheckbox = new JCheckBox("Show Verbose Notifications", SettingsController.getVerboseNotificationVisibility());
		verboseNotificationsCheckbox.addActionListener(event -> SettingsController.setVerboseNotificationVisibility(verboseNotificationsCheckbox.isSelected()));
		verboseNotificationsColorButton = new JButton("\u25B2");
		verboseNotificationsColorButton.setForeground(SettingsController.getVerboseNotificationColor());
		verboseNotificationsColorButton.addActionListener(event -> SettingsController.setVerboseNotificationColor(ColorPickerView.getColor("Verbose Notifications", SettingsController.getVerboseNotificationColor(), false)));
		
		add(verboseNotificationsCheckbox, "split 2, span 2, grow x");
		add(verboseNotificationsColorButton);
		add(Box.createVerticalStrut(2 * Theme.padding), "span 2");
		
		// tooltips
		showTooltipsCheckbox = new JCheckBox("Show Plot Tooltips", SettingsController.getTooltipVisibility());
		showTooltipsCheckbox.addActionListener(event -> SettingsController.setTooltipVisibility(showTooltipsCheckbox.isSelected()));
		
		add(showTooltipsCheckbox, "span 2, grow x");
		
		// logitech smooth scrolling
		enableSmoothScrollingCheckbox = new JCheckBox("Enable Logitech Smooth Scrolling", SettingsController.getSmoothScrolling());
		enableSmoothScrollingCheckbox.addActionListener(event -> SettingsController.setSmoothScrolling(enableSmoothScrollingCheckbox.isSelected()));
		
		add(enableSmoothScrollingCheckbox, "span 2, grow x");
		
		// FPS
		showFpsCheckbox = new JCheckBox("Show FPS and Period", SettingsController.getFpsVisibility());
		showFpsCheckbox.addActionListener(event -> SettingsController.setFpsVisibility(showFpsCheckbox.isSelected()));
		
		add(showFpsCheckbox, "span 2, grow x");
		
		// benchmarking
		showBenchmarksCheckbox = new JCheckBox("Show Benchmarks", SettingsController.getBenchmarking());
		showBenchmarksCheckbox.addActionListener(event -> SettingsController.setBenchmarking(showBenchmarksCheckbox.isSelected()));
		
		add(showBenchmarksCheckbox, "span 2, grow x");
		
		// antialiasing
		antialiasingLevelSlider = new JSlider(0, 5, (int) (Math.log(SettingsController.getAntialiasingLevel()) / Math.log(2)));
		Hashtable<Integer, JLabel> labels = new Hashtable<Integer, JLabel>();
		labels.put(0, new JLabel("1"));
		labels.put(1, new JLabel("2"));
		labels.put(2, new JLabel("4"));
		labels.put(3, new JLabel("8"));
		labels.put(4, new JLabel("16"));
		labels.put(5, new JLabel("32"));
		antialiasingLevelSlider.setLabelTable(labels);
		antialiasingLevelSlider.setMajorTickSpacing(1);
		antialiasingLevelSlider.setPaintTicks(true);
		antialiasingLevelSlider.setPaintLabels(true);
		antialiasingLevelSlider.addChangeListener(event -> SettingsController.setAntialiasingLevel((int) Math.pow(2, antialiasingLevelSlider.getValue())));
		
		add(new JLabel("Antialiasing: "));
		add(antialiasingLevelSlider, "width 1, grow x"); // shrink it horizontally
		
		setVisible(false);
		
	}
	
	private boolean isVisible = true;
	
	/**
	 * Shows or hides this panel.
	 * 
	 * @param visible    True or false.
	 */
	@Override public void setVisible(boolean visible) {
		
		isVisible = visible;
		revalidate();
		
	}
	
	/**
	 * @return    True if the panel is visible.
	 */
	@Override public boolean isVisible() {
		
		return isVisible;
		
	}
	
	@Override public Dimension getPreferredSize() {
		
		Dimension size = super.getPreferredSize();
		if(!isVisible)
			size.width = 0;
		
		return size;
		
	}

}
