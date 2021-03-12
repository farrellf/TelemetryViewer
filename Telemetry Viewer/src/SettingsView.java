import java.awt.Dimension;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTextField;
import net.miginfocom.swing.MigLayout;

/**
 * The SettingsView and SettingsController classes form the MVC that manage GUI-related settings.
 * Settings can be changed when the user interacts with the GUI or opens a Settings file.
 * This class is the GUI that is optionally shown at the left side of the screen.
 * The transmit GUIs are also drawn here because it's convenient to have them on the left side of the screen.
 */
@SuppressWarnings("serial")
public class SettingsView extends JPanel {
	
	public static SettingsView instance = new SettingsView();
	
	private boolean isVisible = true;
	private JScrollPane scrollablePanel;
	private JPanel panel;
	private List<JPanel> txGuis = new ArrayList<JPanel>();
	
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
		
		setLayout(new MigLayout("wrap 1, insets 0, filly")); // 1 column, no border
		panel = new JPanel();
		panel.setLayout(new MigLayout("hidemode 3, wrap 2, insets" + Theme.padding + " " + Theme.padding + " " + Theme.padding + " 0, gap " + Theme.padding));
		scrollablePanel = new JScrollPane(panel, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scrollablePanel.setBorder(null);
		scrollablePanel.getVerticalScrollBar().setUnitIncrement(10);
		add(scrollablePanel, "grow");
		
		// tile columns and rows
		tileColumnsTextfield = new JTextField(Integer.toString(SettingsController.getTileColumns()));
		ActionListener updateTileColumns = event -> {
			try {
				SettingsController.setTileColumns(Integer.parseInt(tileColumnsTextfield.getText().trim()));
			} catch(Exception e) {
				tileColumnsTextfield.setText(Integer.toString(SettingsController.getTileColumns()));
			}
		};
		tileColumnsTextfield.addActionListener(updateTileColumns);
		tileColumnsTextfield.addFocusListener(new FocusListener() {
			@Override public void focusLost(FocusEvent fe)   { updateTileColumns.actionPerformed(null); }
			@Override public void focusGained(FocusEvent fe) { tileColumnsTextfield.selectAll();}
		});
		
		tileRowsTextfield = new JTextField(Integer.toString(SettingsController.getTileRows()));
		ActionListener updateTileRows = event -> {
			try {
				SettingsController.setTileRows(Integer.parseInt(tileRowsTextfield.getText().trim()));
			} catch(Exception e) {
				tileRowsTextfield.setText(Integer.toString(SettingsController.getTileRows()));
			}
		};
		tileRowsTextfield.addActionListener(updateTileRows);
		tileRowsTextfield.addFocusListener(new FocusListener() {
			@Override public void focusLost(FocusEvent fe)   { updateTileRows.actionPerformed(null); }
			@Override public void focusGained(FocusEvent fe) { tileRowsTextfield.selectAll(); }
		});
		
		// time format
		timeFormatCombobox = new JComboBox<String>(SettingsController.getTimeFormats());
		String format = SettingsController.getTimeFormat();
		for(int i = 0; i < timeFormatCombobox.getItemCount(); i++)
			if(timeFormatCombobox.getItemAt(i).equals(format))
				timeFormatCombobox.setSelectedIndex(i);
		timeFormatCombobox.addActionListener(event -> SettingsController.setTimeFormat(timeFormatCombobox.getSelectedItem().toString()));
		timeFormat24hoursCheckbox = new JCheckBox("Show 24-Hour Time", SettingsController.getTimeFormat24hours());
		timeFormat24hoursCheckbox.addActionListener(event -> SettingsController.setTimeFormat24hours(timeFormat24hoursCheckbox.isSelected()));
		
		// notifications
		hintNotificationsCheckbox = new JCheckBox("Show Hint Notifications", SettingsController.getHintNotificationVisibility());
		hintNotificationsCheckbox.addActionListener(event -> SettingsController.setHintNotificationVisibility(hintNotificationsCheckbox.isSelected()));
		hintNotificationsColorButton = new JButton("\u25B2");
		hintNotificationsColorButton.setForeground(SettingsController.getHintNotificationColor());
		hintNotificationsColorButton.addActionListener(event -> SettingsController.setHintNotificationColor(ColorPickerView.getColor("Hint Notifications", SettingsController.getHintNotificationColor(), false)));
		
		warningNotificationsCheckbox = new JCheckBox("Show Warning Notifications", SettingsController.getWarningNotificationVisibility());
		warningNotificationsCheckbox.addActionListener(event -> SettingsController.setWarningNotificationVisibility(warningNotificationsCheckbox.isSelected()));
		warningNotificationsColorButton = new JButton("\u25B2");
		warningNotificationsColorButton.setForeground(SettingsController.getWarningNotificationColor());
		warningNotificationsColorButton.addActionListener(event -> SettingsController.setWarningNotificationColor(ColorPickerView.getColor("Warning Notifications", SettingsController.getWarningNotificationColor(), false)));
		
		failureNotificationsCheckbox = new JCheckBox("Show Failure Notifications", SettingsController.getFailureNotificationVisibility());
		failureNotificationsCheckbox.addActionListener(event -> SettingsController.setFailureNotificationVisibility(failureNotificationsCheckbox.isSelected()));
		failureNotificationsColorButton = new JButton("\u25B2");
		failureNotificationsColorButton.setForeground(SettingsController.getFailureNotificationColor());
		failureNotificationsColorButton.addActionListener(event -> SettingsController.setFailureNotificationColor(ColorPickerView.getColor("Failure Notifications", SettingsController.getFailureNotificationColor(), false)));
		
		verboseNotificationsCheckbox = new JCheckBox("Show Verbose Notifications", SettingsController.getVerboseNotificationVisibility());
		verboseNotificationsCheckbox.addActionListener(event -> SettingsController.setVerboseNotificationVisibility(verboseNotificationsCheckbox.isSelected()));
		verboseNotificationsColorButton = new JButton("\u25B2");
		verboseNotificationsColorButton.setForeground(SettingsController.getVerboseNotificationColor());
		verboseNotificationsColorButton.addActionListener(event -> SettingsController.setVerboseNotificationColor(ColorPickerView.getColor("Verbose Notifications", SettingsController.getVerboseNotificationColor(), false)));
		
		// tooltips
		showTooltipsCheckbox = new JCheckBox("Show Plot Tooltips", SettingsController.getTooltipVisibility());
		showTooltipsCheckbox.addActionListener(event -> SettingsController.setTooltipVisibility(showTooltipsCheckbox.isSelected()));
		
		// logitech smooth scrolling
		enableSmoothScrollingCheckbox = new JCheckBox("Enable Logitech Smooth Scrolling", SettingsController.getSmoothScrolling());
		enableSmoothScrollingCheckbox.addActionListener(event -> SettingsController.setSmoothScrolling(enableSmoothScrollingCheckbox.isSelected()));
		
		// FPS
		showFpsCheckbox = new JCheckBox("Show FPS and Period", SettingsController.getFpsVisibility());
		showFpsCheckbox.addActionListener(event -> SettingsController.setFpsVisibility(showFpsCheckbox.isSelected()));
		
		// benchmarking
		showBenchmarksCheckbox = new JCheckBox("Show Benchmarks", SettingsController.getBenchmarking());
		showBenchmarksCheckbox.addActionListener(event -> SettingsController.setBenchmarking(showBenchmarksCheckbox.isSelected()));
		
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
		
		// actually populating the panel is done in setVisible() because the transmit GUIs may change when connections change
		// that also means that setVisible() must be called any time a connection is added/removed/connected/disconnected
		
		setVisible(false);
		
	}
	
	/**
	 * Shows or hides this panel, and repopulates the panel to ensure everything is in sync.
	 * 
	 * @param visible    True or false.
	 */
	@Override public void setVisible(boolean visible) {
		
		// remove all swing widgets
		panel.removeAll();
		txGuis.clear();

		// repopulate the panel
		panel.add(new JLabel("Tile Columns: "));
		panel.add(tileColumnsTextfield, "grow x");
		panel.add(new JLabel("Tile Rows: "));
		panel.add(tileRowsTextfield, "grow x, gapbottom " + 4*Theme.padding);
		
		panel.add(new JLabel("Time Format: "));
		panel.add(timeFormatCombobox);
		panel.add(timeFormat24hoursCheckbox, "span 2, grow x, gapbottom " + 4*Theme.padding);
		
		panel.add(hintNotificationsCheckbox, "split 2, span 2, grow x");
		panel.add(hintNotificationsColorButton);
		panel.add(warningNotificationsCheckbox, "split 2, span 2, grow x");
		panel.add(warningNotificationsColorButton);
		panel.add(failureNotificationsCheckbox, "split 2, span 2, grow x");
		panel.add(failureNotificationsColorButton);
		panel.add(verboseNotificationsCheckbox, "split 2, span 2, grow x");
		panel.add(verboseNotificationsColorButton, "gapbottom " + 4*Theme.padding);
		
		panel.add(showTooltipsCheckbox, "span 2, grow x");
		panel.add(enableSmoothScrollingCheckbox, "span 2, grow x");
		panel.add(showFpsCheckbox, "span 2, grow x");
		panel.add(showBenchmarksCheckbox, "span 2, grow x");
		
		panel.add(new JLabel("Antialiasing: "));
		panel.add(antialiasingLevelSlider, "width 1, grow x, gapbottom " + 4*Theme.padding); // shrink it horizontally
		
		// if visible, also repopulate the panel with transmit GUIs
		if(visible)
			ConnectionsController.telemetryConnections.forEach(connection -> {
				JPanel txGui = connection.getTransmitPanel();
				if(txGui != null) {
					panel.add(txGui, "span 2, gapbottom " + 4*Theme.padding);
					txGuis.add(txGui);
				}
			});
		
		isVisible = visible;
		revalidate();
		repaint();
		
	}
	
	/**
	 * @return    True if the panel is visible.
	 */
	@Override public boolean isVisible() {
		
		return isVisible;
		
	}
	
	/**
	 * Ensures this panel is sized correctly.
	 */
	@Override public Dimension getPreferredSize() {
		
		txGuis.forEach(gui -> gui.setVisible(false));
		panel.setPreferredSize(null);
		scrollablePanel.setPreferredSize(null);
		Dimension emptySize = scrollablePanel.getPreferredSize();
		txGuis.forEach(gui -> gui.setVisible(true));
		Dimension fullSize = scrollablePanel.getPreferredSize();
		Dimension size = new Dimension(Integer.max(emptySize.width, fullSize.width), emptySize.height);
		
		if(!isVisible) {
			
			size.width = 0;
			return size;
			
		} else {
			
			// resize the widgets region if the scrollbar is visible
			if(scrollablePanel.getVerticalScrollBar().isVisible()) {
				size.width += scrollablePanel.getVerticalScrollBar().getPreferredSize().width;
				scrollablePanel.setPreferredSize(size);
				size.width += Theme.padding;
			}
			
			return size;
			
		}
		
	}

}
