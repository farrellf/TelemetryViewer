import java.awt.GridLayout;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

@SuppressWarnings("serial")
public class ConfigureView extends JPanel {
	
	static ConfigureView instance = new ConfigureView();
	private static PositionedChart activeChart = null;
	
	/**
	 * Creates an empty panel that can be used for configuring charts.
	 */
	private ConfigureView() {
		
		super();
		
	}
	
	/**
	 * Updates this panel with configuration widgets for an existing chart.
	 * 
	 * @param chart    The chart to configure.
	 */
	public static void existingChart(PositionedChart chart) {
		
		activeChart = chart;
		
		// start with an empty panel
		instance.removeAll();
		JPanel contents = new JPanel();
		contents.setBorder(new EmptyBorder(10, 10, 10, 10));
		contents.setLayout(new BoxLayout(contents, BoxLayout.Y_AXIS));
		instance.add(contents);

		// show the widgets
		for(Widget widget : chart.widgets) {
			contents.add(widget != null ? widget : Box.createVerticalStrut(10));
			contents.add(Box.createVerticalStrut(10));
		}
		
		contents.add(Box.createVerticalStrut(40));
		
		// show the done button at the bottom
		JButton doneButton = new JButton("Done");
		doneButton.addActionListener(event -> close());
		
		JPanel doneButtonPanel = new JPanel();
		doneButtonPanel.setLayout(new GridLayout(1, 3, 10, 10));
		doneButtonPanel.add(new JLabel(""));
		doneButtonPanel.add(new JLabel(""));
		doneButtonPanel.add(doneButton);
		contents.add(doneButtonPanel);
		
		// size this panel as needed
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
	public static void newChart(int x1, int y1, int x2, int y2) {
		
		if(Controller.getDatasetsCount() == 0) {
			JOptionPane.showMessageDialog(null, "Error: The packet's data structure must be defined before adding charts.\nUse the controls at the bottom-right corner of the main window to make a connection and define the data structure.", "Error", JOptionPane.ERROR_MESSAGE);
			close();
			return;
		}
		
		activeChart = null;
		
		// start with an empty panel
		instance.removeAll();
		JPanel contents = new JPanel();
		contents.setBorder(new EmptyBorder(10, 10, 10, 10));
		contents.setLayout(new BoxLayout(contents, BoxLayout.Y_AXIS));
		instance.add(contents);
		
		JComboBox<String> chartTypeCombobox = new JComboBox<String>(Controller.getChartTypes());
		
		JPanel chartTypePanel = new JPanel();
		chartTypePanel.setLayout(new GridLayout(1, 2, 10, 10));
		chartTypePanel.add(new JLabel("Chart Type: "));
		chartTypePanel.add(chartTypeCombobox);
		
		JButton cancelButton = new JButton("Cancel");
		cancelButton.addActionListener(event -> { Controller.removeChart(activeChart); close(); });
		JButton doneButton = new JButton("Done");
		doneButton.addActionListener(event -> close());
		JPanel doneAndCancelPanel = new JPanel();
		doneAndCancelPanel.setLayout(new GridLayout(1, 3, 10, 10));
		doneAndCancelPanel.add(cancelButton);
		doneAndCancelPanel.add(new JLabel(""));
		doneAndCancelPanel.add(doneButton);
		
		chartTypeCombobox.addActionListener(event -> {
				
			// remove existing chart and widgets, then show the chart type combobox
			if(activeChart != null)
				Controller.removeChart(activeChart);
			contents.removeAll();
			instance.add(contents);
			contents.add(chartTypePanel);
			contents.add(Box.createVerticalStrut(40));
			
			// create the chart and show it's widgets
			activeChart = Controller.createAndAddChart(chartTypeCombobox.getSelectedItem().toString(), x1, y1, x2, y2);
			for(Widget widget : activeChart.widgets) {
				contents.add(widget != null ? widget : Box.createVerticalStrut(10));
				contents.add(Box.createVerticalStrut(10));
			}
			
			// leave some room, then show the done and cancel buttons
			contents.add(Box.createVerticalStrut(40));
			contents.add(doneAndCancelPanel);
			
			// size the panel as needed
			instance.setPreferredSize(null);
			instance.revalidate();
			instance.repaint();
				
		});

		chartTypeCombobox.getActionListeners()[0].actionPerformed(null);
		
	}
	
	/**
	 * Closes the configuration view if it is being used for a specific chart.
	 * 
	 * @param chart    The chart.
	 */
	public static void closeIfUsedFor(PositionedChart chart) {
		
		if(activeChart == chart)
			close();
		
	}
	
	/**
	 * Closes the configuration view.
	 */
	public static void close() {
		
		activeChart = null;
		instance.removeAll();
		instance.setPreferredSize(null);
		instance.revalidate();
		instance.repaint();
		
	}

}
