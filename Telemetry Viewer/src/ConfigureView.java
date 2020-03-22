import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;

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
		widgetsPanel.setLayout(new MigLayout("wrap 1, gap 0, insets 0")); // 1 column, no border
		scrollableRegion = new JScrollPane(widgetsPanel, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scrollableRegion.setBorder(null);
		buttonsPanel = new JPanel();
		buttonsPanel.setLayout(new MigLayout("insets " + (Theme.padding * 2) + " 0 " + Theme.padding + " 0", "[33%!][grow][33%!]")); // border above and below the buttons, 3 equal columns
		
		setLayout(new MigLayout("wrap 1, insets " + Theme.padding + " " + Theme.padding + " 0 " + Theme.padding)); // 1 column, border around the top/left/right
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
			size.width += scrollableRegion.getVerticalScrollBar().getPreferredSize().width + Theme.padding;
		widgetsPanel.setPreferredSize(size);
		
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
			widgetsPanel.add(widget != null ? widget : Box.createVerticalStrut(Theme.padding), "growx");
			widgetsPanel.add(Box.createVerticalStrut(Theme.padding));
		}
		
		JButton doneButton = new JButton("Done");
		doneButton.addActionListener(event -> close());
		buttonsPanel.add(doneButton, "growx, cell 2 0");

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
				Controller.removeChart(activeChart);
			widgetsPanel.removeAll();
			widgetsPanel.add(chartTypePanel, "growx");
			widgetsPanel.add(Box.createVerticalStrut(Theme.padding * 3));
			
			// create the chart and show it's widgets
			activeChart = Controller.createAndAddChart(clickedButton.getText(), x1, y1, x2, y2);
			for(Widget widget : activeChart.widgets) {
				widgetsPanel.add(widget != null ? widget : Box.createVerticalStrut(Theme.padding), "growx");
				widgetsPanel.add(Box.createVerticalStrut(Theme.padding));
			}
			
			// size the panel as needed
			instance.setPreferredSize(null);
			instance.revalidate();
			instance.repaint();
				
		};
		
		chartTypePanel.setLayout(new GridLayout(0, 2, Theme.padding, Theme.padding));
		for(String chartType : Controller.getChartTypes()) {
			JToggleButton button = new JToggleButton(chartType);
			buttons.add(button);
			button.addActionListener(buttonHandler);
			chartTypePanel.add(button);
		}
		
		JButton cancelButton = new JButton("Cancel");
		cancelButton.addActionListener(event -> { Controller.removeChart(activeChart); close(); });
		JButton doneButton = new JButton("Done");
		doneButton.addActionListener(event -> close());
		buttonsPanel.add(cancelButton, "growx, cell 0 0");
		buttonsPanel.add(doneButton, "growx, cell 2 0");

		buttons.get(0).doClick();
		
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
