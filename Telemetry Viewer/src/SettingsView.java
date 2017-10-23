import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;

/**
 * The Settings / SettingsView / SettingsController classes form the MVC that manage GUI-related settings.
 * Settings can be changed when the user interacts with the GUI or opens a Layout file.
 * This class is the GUI that is optionally shown at the left side of the screen.
 */
@SuppressWarnings("serial")
public class SettingsView extends JPanel {
	
	Dimension preferredSize;
	final int padding = 10;
	
	public SettingsView() {
		
		super();
		setBorder(new EmptyBorder(padding, padding, padding, padding));
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		
		// tile columns and rows
		JTextField tileColumnsTextfield = new JTextField(Integer.toString(SettingsController.getTileColumns()));
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
		
		JTextField tileRowsTextfield = new JTextField(Integer.toString(SettingsController.getTileRows()));
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
		
		SettingsController.addTileCountListener((columns, rows) -> {
			tileColumnsTextfield.setText(Integer.toString(columns));
			tileRowsTextfield.setText(Integer.toString(rows));
		});
		
		JPanel tileColumnsAndRowsPanel = new JPanel();
		tileColumnsAndRowsPanel.setLayout(new GridLayout(2, 2, 0, padding));
		tileColumnsAndRowsPanel.add(new JLabel("Tile Columns: "));
		tileColumnsAndRowsPanel.add(tileColumnsTextfield);
		tileColumnsAndRowsPanel.add(new JLabel("Tile Rows: "));
		tileColumnsAndRowsPanel.add(tileRowsTextfield);
		tileColumnsAndRowsPanel.setMaximumSize(new Dimension(tileColumnsAndRowsPanel.getMaximumSize().width, tileColumnsAndRowsPanel.getPreferredSize().height));
		tileColumnsAndRowsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		add(tileColumnsAndRowsPanel);
		add(Box.createVerticalStrut(padding));
		
		// tooltips
		JCheckBox showTooltipsCheckbox = new JCheckBox("Show Plot Tooltips", SettingsController.getTooltipVisibility());
		showTooltipsCheckbox.setAlignmentX(Component.LEFT_ALIGNMENT);
		
		SettingsController.addTooltipVisibilityListener(newVisibility -> showTooltipsCheckbox.setSelected(newVisibility));
		
		add(showTooltipsCheckbox);
		add(Box.createVerticalStrut(padding));
		
		// logitech smooth scrolling
		JCheckBox enableSmoothScrollingCheckbox = new JCheckBox("Enable Logitech Smooth Scrolling", SettingsController.getSmoothScrolling());
		enableSmoothScrollingCheckbox.setAlignmentX(Component.LEFT_ALIGNMENT);
		
		SettingsController.addSmoothScrollingListener(newState -> enableSmoothScrollingCheckbox.setSelected(newState));
		
		add(enableSmoothScrollingCheckbox);
		add(Box.createVerticalStrut(padding));
		
		// antialiasing
		JCheckBox enableAntialiasingCheckbox = new JCheckBox("Enable OpenGL Antialiasing", SettingsController.getAntialiasing());
		enableAntialiasingCheckbox.setAlignmentX(Component.LEFT_ALIGNMENT);
		
		SettingsController.addAntialiasingListener(newState -> enableAntialiasingCheckbox.setSelected(newState));
		
		add(enableAntialiasingCheckbox);
		add(Box.createVerticalStrut(padding));
		
		// FPS
		JCheckBox showFpsCheckbox = new JCheckBox("Show FPS and Period", SettingsController.getFpsVisibility());
		showFpsCheckbox.setAlignmentX(Component.LEFT_ALIGNMENT);
		
		SettingsController.addFpsVisibilityListener(newState -> showFpsCheckbox.setSelected(newState));
		
		add(showFpsCheckbox);
		add(Box.createVerticalStrut(padding));
		
		// CPU and GPU times
		JCheckBox showBenchmarksCheckbox = new JCheckBox("Show Chart Benchmarks", SettingsController.getBenchmarkedChart() != null);		
		showBenchmarksCheckbox.setAlignmentX(Component.LEFT_ALIGNMENT);
		
		SettingsController.addBenchmarkedChartListener(newChart -> showBenchmarksCheckbox.setSelected(newChart != null));
		
		add(showBenchmarksCheckbox);
		add(Box.createVerticalStrut(padding));
		
		// save the preferred size so this panel can be resized to hide or show
		preferredSize = getPreferredSize();
		
		setVisible(false);
		
	}
	
	/**
	 * Shows or hides this panel.
	 * 
	 * @param visible    True or false.
	 */
	@Override public void setVisible(boolean visible) {
		
		setPreferredSize(visible ? preferredSize : new Dimension(0, 0));
		revalidate();
		
	}
	
	/**
	 * @return    True if the panel is visible.
	 */
	@Override public boolean isVisible() {
		
		return getPreferredSize().width != 0;
		
	}

}
