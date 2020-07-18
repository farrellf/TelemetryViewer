import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

import net.miginfocom.swing.MigLayout;

@SuppressWarnings("serial")
public class ConfigureView extends JPanel {
	
	static ConfigureView instance = new ConfigureView();
	
	private static JPanel widgetsPanel;
	private static JPanel buttonsPanel;
	private static JScrollPane scrollableRegion;
	private boolean testSizeAgain = true;
	
	private static PositionedChart activeChart = null;
	
	/**
	 * Private constructor to enforce singleton usage.
	 */
	private ConfigureView() {
		
		super();
		
		widgetsPanel = new JPanel();
		widgetsPanel.setLayout(new MigLayout("hidemode 3, wrap 4, insets 0, gap " + Theme.padding, "[pref][min!][min!][grow]"));
		widgetsPanel.setBorder(new EmptyBorder(Theme.padding, Theme.padding, Theme.padding, Theme.padding));
		scrollableRegion = new JScrollPane(widgetsPanel, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scrollableRegion.setBorder(null);
		buttonsPanel = new JPanel();
		buttonsPanel.setLayout(new MigLayout("insets 0", "[33%!][grow][33%!]")); // 3 equal columns
		buttonsPanel.setBorder(new EmptyBorder(Theme.padding * 2, Theme.padding, Theme.padding, Theme.padding)); // extra padding above
		
		setLayout(new MigLayout("wrap 1, insets 0")); // 1 column, no border
		add(scrollableRegion, "growx");
		add(buttonsPanel, "growx");
		
		setPreferredSize(new Dimension(0, 0));
		
	}
	
	/**
	 * Calculate the preferred size of this panel, taking into account the width of the vertical scroll bar.
	 */
	@Override public Dimension getPreferredSize() {
	
		widgetsPanel.setPreferredSize(null);
		Dimension size = widgetsPanel.getPreferredSize();
		
		// resize the widgets region if the scrollbar is visible
		if(scrollableRegion.getVerticalScrollBar().isVisible())
			size.width += scrollableRegion.getVerticalScrollBar().getPreferredSize().width;
		scrollableRegion.setPreferredSize(size);
		
		// due to the event queue, the scroll bar may be about to appear or disappear, but the above if() can't see the future
		// work around this by triggering another getPreferredSize() at the end of the event queue.
		if(testSizeAgain)
			SwingUtilities.invokeLater(() -> revalidate());
		testSizeAgain = !testSizeAgain;
		
		return super.getPreferredSize();
		
	}
	
	/**
	 * Updates this panel with configuration widgets for an existing chart.
	 * 
	 * @param chart    The chart to configure.
	 */
	public void forExistingChart(PositionedChart chart) {
		
		activeChart = chart;
		
		widgetsPanel.removeAll();
		buttonsPanel.removeAll();

		for(Widget widget : chart.widgets) {
			if(widget == null)
				widgetsPanel.add(Box.createVerticalStrut(Theme.padding), "span 4");
			else
				for(Map.Entry<Component, String> thing : widget.widgets.entrySet())
					widgetsPanel.add(thing.getKey(), thing.getValue());
		}
		
		JButton doneButton = new JButton("Done");
		doneButton.addActionListener(event -> close());
		buttonsPanel.add(doneButton, "growx, cell 2 0");
		
		scrollableRegion.getVerticalScrollBar().setValue(0);

		instance.setPreferredSize(null);
		instance.revalidate();
		instance.repaint();
		
	}
	
	/**
	 * Updates this panel with configuration widgets for a new chart.
	 * 
	 * @param x1    The x-coordinate of a bounding-box corner in the OpenGLChartsRegion grid.
	 * @param y1    The y-coordinate of a bounding-box corner in the OpenGLChartsRegion grid.
	 * @param x2    The x-coordinate of the opposite bounding-box corner in the OpenGLChartsRegion grid.
	 * @param y2    The x-coordinate of the opposite bounding-box corner in the OpenGLChartsRegion grid.
	 */
	public void forNewChart(int x1, int y1, int x2, int y2) {
		
		if(DatasetsController.getDatasetsCount() == 0) {
			JOptionPane.showMessageDialog(null, "Error: The packet's data structure must be defined before adding charts.\nUse the controls at the bottom-right corner of the main window to make a connection and define the data structure.", "Error", JOptionPane.ERROR_MESSAGE);
			close();
			return;
		}
		
		activeChart = null;
		
		widgetsPanel.removeAll();
		buttonsPanel.removeAll();
		
		JPanel chartTypePanel = new JPanel();
		List<JToggleButton> buttons = new ArrayList<JToggleButton>();
		
		ActionListener buttonHandler = event -> {
			
			// make all other buttons toggled off
			JToggleButton clickedButton = buttons.get(buttons.indexOf(event.getSource()));
			for(JToggleButton button : buttons)
				if(button != clickedButton)
					button.setSelected(false);
				
			// remove existing chart and widgets, then show the chart type buttons
			if(activeChart != null)
				ChartsController.removeChart(activeChart);
			widgetsPanel.removeAll();
			widgetsPanel.add(chartTypePanel, "span 4, growx");
			widgetsPanel.add(Box.createVerticalStrut(Theme.padding * 2), "span 4");
			
			// create the chart and show it's widgets
			activeChart = ChartsController.createAndAddChart(clickedButton.getText(), x1, y1, x2, y2);
			for(Widget widget : activeChart.widgets) {
				if(widget == null)
					widgetsPanel.add(Box.createVerticalStrut(Theme.padding), "span 4");
				else
					for(Map.Entry<Component, String> thing : widget.widgets.entrySet())
						widgetsPanel.add(thing.getKey(), thing.getValue());
			}
			
			scrollableRegion.getVerticalScrollBar().setValue(0);
			
			// size the panel as needed
			instance.setPreferredSize(null);
			instance.revalidate();
			instance.repaint();
				
		};
		
		chartTypePanel.setLayout(new GridLayout(0, 2, Theme.padding, Theme.padding));
		for(String chartType : ChartsController.getChartTypes()) {
			JToggleButton button = new JToggleButton(chartType);
			buttons.add(button);
			button.addActionListener(buttonHandler);
			chartTypePanel.add(button);
		}
		
		JButton cancelButton = new JButton("Cancel");
		cancelButton.addActionListener(event -> { ChartsController.removeChart(activeChart); close(); });
		JButton doneButton = new JButton("Done");
		doneButton.addActionListener(event -> close());
		buttonsPanel.add(cancelButton, "growx, cell 0 0");
		buttonsPanel.add(doneButton, "growx, cell 2 0");

		buttons.get(0).doClick();
		
	}
	
	/**
	 * Updates this panel with configuration widgets for a Dataset.
	 * 
	 * @param dataset    The dataset to configure.
	 */
	public void forDataset(Dataset dataset) {
		
		activeChart = null;
		
		JTextField nameTextfield = new JTextField(dataset.name, 15);
		JButton colorButton = new JButton("\u25B2");
		JTextField unitTextfield = new JTextField(dataset.unit, 15);
		
		JButton cancelButton = new JButton("Cancel");
		cancelButton.addActionListener(event -> close());
		JButton applyButton = new JButton("Apply");
		applyButton.addActionListener(event -> {
			dataset.setNameColorUnit(nameTextfield.getText(), colorButton.getForeground(), unitTextfield.getText());
			close();
		});
		buttonsPanel.removeAll();
		buttonsPanel.add(cancelButton, "growx, cell 0 0");
		buttonsPanel.add(applyButton, "growx, cell 2 0");

		ActionListener pressEnterToApply = event -> applyButton.doClick();
		
		nameTextfield.addActionListener(pressEnterToApply);
		nameTextfield.addFocusListener(new FocusListener() {
			@Override public void focusLost(FocusEvent e)   { nameTextfield.setText(nameTextfield.getText().trim()); }
			@Override public void focusGained(FocusEvent e) { nameTextfield.selectAll(); }
		});
		
		colorButton.setForeground(dataset.color);
		colorButton.addActionListener(event -> {
			Color color = JColorChooser.showDialog(this, "Pick a Color for " + nameTextfield.getText(), dataset.color);
			if(color != null)
				colorButton.setForeground(color);
		});
		
		unitTextfield.addActionListener(pressEnterToApply);
		unitTextfield.addFocusListener(new FocusListener() {
			@Override public void focusLost(FocusEvent arg0)   { unitTextfield.setText(unitTextfield.getText().trim()); }
			@Override public void focusGained(FocusEvent arg0) { unitTextfield.selectAll(); }
		});
		unitTextfield.addKeyListener(new KeyListener() {
			@Override public void keyReleased(KeyEvent ke) { unitTextfield.setText(unitTextfield.getText().trim()); }
			@Override public void keyPressed(KeyEvent ke)  { }
			@Override public void keyTyped(KeyEvent ke)    { }
		});
		
		JPanel namePanel = new JPanel(new GridLayout(1, 2, Theme.padding, Theme.padding));
		namePanel.add(new JLabel("Name: "));
		namePanel.add(nameTextfield);
		JPanel colorPanel = new JPanel(new GridLayout(1, 2, Theme.padding, Theme.padding));
		colorPanel.add(new JLabel("Color: "));
		colorPanel.add(colorButton);
		JPanel unitPanel = new JPanel(new GridLayout(1, 2, Theme.padding, Theme.padding));
		unitPanel.add(new JLabel("Unit: "));
		unitPanel.add(unitTextfield);
		widgetsPanel.removeAll();
		widgetsPanel.add(namePanel, "span 4, growx");
		widgetsPanel.add(colorPanel, "span 4, growx");
		widgetsPanel.add(unitPanel, "span 4, growx");
		
		scrollableRegion.getVerticalScrollBar().setValue(0);

		instance.setPreferredSize(null);
		instance.revalidate();
		instance.repaint();
		
	}
	
	/**
	 * Closes the configuration view if it is being used for a specific chart.
	 * 
	 * @param chart    The chart.
	 */
	public void closeIfUsedFor(PositionedChart chart) {
		
		if(activeChart == chart)
			close();
		
	}
	
	/**
	 * Closes the configuration view.
	 */
	public void close() {
		
		activeChart = null;
		instance.setPreferredSize(new Dimension(0, 0));
		instance.revalidate();
		instance.repaint();
		
	}

}
