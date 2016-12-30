import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

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

/**
 * A dialog box where the user can create a chart by picking its type and configuring various options.
 */
@SuppressWarnings("serial")
public class AddChartWindow extends JDialog {
	
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
		
		JPanel panel = new JPanel();
		panel.setBorder(new EmptyBorder(10, 10, 10, 10));
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		add(panel);
		
		JComboBox<ChartFactory> chartTypeCombobox = new JComboBox<ChartFactory>();	
		for(ChartFactory factory : Controller.getChartFactories())
			chartTypeCombobox.addItem(factory);
		
		JPanel chartTypePanel = new JPanel();
		chartTypePanel.setLayout(new GridLayout(1, 2, 10, 10));
		chartTypePanel.add(new JLabel("Chart Type: "));
		chartTypePanel.add(chartTypeCombobox);
		
		setResizable(false);
		
		chartTypeCombobox.addActionListener(new ActionListener() {
			@Override public void actionPerformed(ActionEvent ae) {
				
				ChartFactory factory = (ChartFactory) chartTypeCombobox.getSelectedItem();
				
				// remove existing widgets, then show the chart type combobox
				panel.removeAll();
				panel.add(chartTypePanel);
				panel.add(Box.createVerticalStrut(40));
				
				// show the chart-specific widgets
				for(JPanel widget : factory.getWidgets()) {
					panel.add(widget != null ? widget : Box.createVerticalStrut(10));
					panel.add(Box.createVerticalStrut(10));
				}
				
				// leave some room after
				panel.add(Box.createVerticalStrut(40));
				
				// show the add chart and cancel buttons
				JPanel addAndCancelButtons = new JPanel();
				JButton cancelButton = new JButton("Cancel");
				JButton addChartButton = new JButton("Add Chart");
				
				addAndCancelButtons.setLayout(new GridLayout(1, 3, 10, 10));
				addAndCancelButtons.add(cancelButton);
				addAndCancelButtons.add(new JLabel(""));
				addAndCancelButtons.add(addChartButton);
				
				cancelButton.addActionListener(new ActionListener() {
					@Override public void actionPerformed(ActionEvent e) {
						dispose();
					}
				});
				
				addChartButton.addActionListener(new ActionListener() {
					@Override public void actionPerformed(ActionEvent e) {
						
						PositionedChart chart = factory.createChart(x1, y1, x2, y2);
						
						if(chart == null) {
							JOptionPane.showMessageDialog(AddChartWindow.this, "Error: No dataset selected.", "Error", JOptionPane.ERROR_MESSAGE);
							return;
						}
						
						Controller.addChart(chart);
						dispose();
						
					}
				});
				
				panel.add(addAndCancelButtons);
				
				// resize the window
				panel.revalidate();
				panel.repaint();
				setSize(getPreferredSize());
			}
		});

		pack();
		chartTypeCombobox.getActionListeners()[0].actionPerformed(null);
		
		setLocationRelativeTo(parentWindow);
		setModal(true);
		setVisible(true);
		
	}

}
