import java.awt.GridLayout;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

@SuppressWarnings("serial")
public class AddChartWindow extends JDialog {
	
	PositionedChart chart;
	
	/**
	 * Shows a dialog box where the user can create a chart by picking its type and configuring various options.
	 *  
	 * @param parentWindow    The JFrame that this dialog box should be tied to (and centered over.)
	 * @param x1              The x-coordinate of a bounding-box corner in the OpenGLChartsRegion grid.
	 * @param y1              The y-coordinate of a bounding-box corner in the OpenGLChartsRegion grid.
	 * @param x2              The x-coordinate of the opposite bounding-box corner in the OpenGLChartsRegion grid.
	 * @param y2              The x-coordinate of the opposite bounding-box corner in the OpenGLChartsRegion grid.
	 */
	public AddChartWindow(JFrame parentWindow, int x1, int y1, int x2, int y2) {
		
		if(Controller.getDatasetsCount() == 0) {
			JOptionPane.showMessageDialog(this, "Error: The packet's data structure must be defined before adding charts.\nUse the controls at the bottom-right corner of the main window to make a connection and define the data structure.", "Error", JOptionPane.ERROR_MESSAGE);
			dispose();
			return;
		}
		
		setTitle("Add Chart");
		setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		
		JPanel windowContents = new JPanel();
		windowContents.setBorder(new EmptyBorder(10, 10, 10, 10));
		windowContents.setLayout(new BoxLayout(windowContents, BoxLayout.Y_AXIS));
		add(windowContents);
		
		JComboBox<String> chartTypeCombobox = new JComboBox<String>(Controller.getChartTypes());
		
		JPanel chartTypePanel = new JPanel();
		chartTypePanel.setLayout(new GridLayout(1, 2, 10, 10));
		chartTypePanel.add(new JLabel("Chart Type: "));
		chartTypePanel.add(chartTypeCombobox);
		
		JPanel doneAndCancelPanel = new JPanel();
		JButton cancelButton = new JButton("Cancel");
		cancelButton.addActionListener(event -> { if(chart != null) Controller.removeChart(chart); dispose(); });
		JButton doneButton = new JButton("Done");
		doneButton.addActionListener(event -> dispose());
		doneAndCancelPanel.setLayout(new GridLayout(1, 3, 10, 10));
		doneAndCancelPanel.add(cancelButton);
		doneAndCancelPanel.add(new JLabel(""));
		doneAndCancelPanel.add(doneButton);
		
		chartTypeCombobox.addActionListener(event -> {
				
			// remove existing chart and widgets, then show the chart type combobox
			if(chart != null)
				Controller.removeChart(chart);
			windowContents.removeAll();
			windowContents.add(chartTypePanel);
			windowContents.add(Box.createVerticalStrut(40));
			
			// create the chart and show it's widgets
			chart = Controller.createAndAddChart(chartTypeCombobox.getSelectedItem().toString(), x1, y1, x2, y2);
			for(JPanel widget : chart.getWidgets()) {
				windowContents.add(widget != null ? widget : Box.createVerticalStrut(10));
				windowContents.add(Box.createVerticalStrut(10));
			}
			
			// leave some room, then show the done and cancel buttons
			windowContents.add(Box.createVerticalStrut(40));
			windowContents.add(doneAndCancelPanel);
			
			// redraw and resize the window
			windowContents.revalidate();
			windowContents.repaint();
			pack();
			setSize(getPreferredSize());
				
		});

		setResizable(false);
		chartTypeCombobox.getActionListeners()[0].actionPerformed(null);
		
		setLocationRelativeTo(parentWindow);
		setModal(true);
		setVisible(true);
		
	}

}
