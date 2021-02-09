import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.Map;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JLabel;
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
	
	private JPanel widgetsPanel;
	private JPanel buttonsPanel;
	private JScrollPane scrollableRegion;
	private boolean testSizeAgain = true;
	
	private PositionedChart activeChart = null;
	private boolean activeChartIsNew = false;
	
	/**
	 * Private constructor to enforce singleton usage.
	 */
	private ConfigureView() {
		
		super();
		
		widgetsPanel = new JPanel();
		widgetsPanel.setLayout(new MigLayout("hidemode 3, wrap 4, insets" + Theme.padding + " " + Theme.padding / 2 + " " + Theme.padding + " " + Theme.padding + ", gap " + Theme.padding, "[pref][min!][min!][grow]"));
		scrollableRegion = new JScrollPane(widgetsPanel, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scrollableRegion.setBorder(null);
		scrollableRegion.getVerticalScrollBar().setUnitIncrement(10);
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
		activeChartIsNew = false;
		
		widgetsPanel.removeAll();
		buttonsPanel.removeAll();

		for(Widget widget : chart.widgets) {
			if(widget == null) {
				widgetsPanel.add(Box.createVerticalStrut(Theme.padding), "span 4");
			} else {
				widget.update();
				for(Map.Entry<Component, String> thing : widget.widgets.entrySet())
					widgetsPanel.add(thing.getKey(), thing.getValue());
			}
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
	 * @param chart    The new chart.
	 */
	public void forNewChart(PositionedChart chart) {
		
		activeChart = chart;
		activeChartIsNew = true;
		
		ActionListener chartTypeHandler = event -> {
			// replace the chart if a different chart type was selected
			JToggleButton clickedButton = (JToggleButton) event.getSource();
			if(!activeChart.toString().equals(clickedButton.getText())) {
				int x1 = activeChart.topLeftX;
				int y1 = activeChart.topLeftY;
				int x2 = activeChart.bottomRightX;
				int y2 = activeChart.bottomRightY;
				ChartsController.removeChart(activeChart);
				PositionedChart newChart = ChartsController.createAndAddChart(clickedButton.getText(), x1, y1, x2, y2);
				instance.forNewChart(newChart);
			}
		};
		
		JPanel chartTypePanel = new JPanel();
		chartTypePanel.setLayout(new GridLayout(0, 2, Theme.padding, Theme.padding));
		for(String chartType : ChartsController.getChartTypes()) {
			JToggleButton button = new JToggleButton(chartType);
			button.setSelected(button.getText().equals(activeChart.toString()));
			button.addActionListener(chartTypeHandler);
			chartTypePanel.add(button);
		}
		
		widgetsPanel.removeAll();
		widgetsPanel.add(chartTypePanel, "span 4, growx");
		widgetsPanel.add(Box.createVerticalStrut(Theme.padding * 2), "span 4");
		for(Widget widget : activeChart.widgets) {
			if(widget == null)
				widgetsPanel.add(Box.createVerticalStrut(Theme.padding), "span 4");
			else
				for(Map.Entry<Component, String> thing : widget.widgets.entrySet())
					widgetsPanel.add(thing.getKey(), thing.getValue());
		}
		
		buttonsPanel.removeAll();
		JButton cancelButton = new JButton("Cancel");
		cancelButton.addActionListener(event -> { ChartsController.removeChart(activeChart); close(); });
		JButton doneButton = new JButton("Done");
		doneButton.addActionListener(event -> close());
		buttonsPanel.add(cancelButton, "growx, cell 0 0");
		buttonsPanel.add(doneButton, "growx, cell 2 0");
		
		scrollableRegion.getVerticalScrollBar().setValue(0);
		
		// size the panel as needed
		instance.setPreferredSize(null);
		instance.revalidate();
		instance.repaint();
		
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
		colorButton.addActionListener(event -> colorButton.setForeground(ColorPickerView.getColor(nameTextfield.getText(), colorButton.getForeground(), true)));
		
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
		
		widgetsPanel.removeAll();
		widgetsPanel.add(new JLabel("Name: "));
		widgetsPanel.add(nameTextfield, "span 3, growx");
		widgetsPanel.add(new JLabel("Color: "));
		widgetsPanel.add(colorButton, "span 3, growx");
		widgetsPanel.add(new JLabel("Unit: "));
		widgetsPanel.add(unitTextfield, "span 3, growx");
		
		scrollableRegion.getVerticalScrollBar().setValue(0);

		instance.setPreferredSize(null);
		instance.revalidate();
		instance.repaint();
		
	}
	
	public void redrawIfUsedFor(PositionedChart chart) {
		
		if(chart != activeChart || activeChart == null)
			return;
		
		if(activeChartIsNew)
			forNewChart(chart);
		else
			forExistingChart(chart);
		
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
