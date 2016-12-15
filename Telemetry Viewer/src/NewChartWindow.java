import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;

@SuppressWarnings("serial")
public class NewChartWindow extends JFrame {
	
	public NewChartWindow(JFrame parentWindow, int x1, int y1, int x2, int y2) {
		
		if(Controller.getDatasetsCount() == 0) {
			JOptionPane.showMessageDialog(this, "Error: The data structure table must be setup before adding charts. Do this by clicking the Connect button located at the bottom-right corner of the main window.", "Error", JOptionPane.ERROR_MESSAGE);
			dispose();
			return;
		}
		
		setTitle("Add New Chart");
		setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		
		JPanel panel = new JPanel();
		panel.setBorder(new EmptyBorder(10, 10, 10, 10));
		panel.setLayout(new GridLayout(0, 2, 10, 10));
		
		add(panel);
		
		setVisible(true);
		
		JComboBox<ChartDescriptor> chartTypeCombobox = new JComboBox<ChartDescriptor>();	
		for(ChartDescriptor descriptor : Controller.getChartDescriptors())
			chartTypeCombobox.addItem(descriptor);
		
		chartTypeCombobox.addActionListener(new ActionListener() {
			@Override public void actionPerformed(ActionEvent arg0) {
				
				panel.removeAll();
				
				ChartDescriptor chart = (ChartDescriptor) chartTypeCombobox.getSelectedItem();
				
				List<JComboBox<Dataset>> inputComboboxesList = new ArrayList<JComboBox<Dataset>>();
				
				panel.add(new JLabel("Chart Type"));
				panel.add(chartTypeCombobox);
				
				JTextField durationTextfield = new JTextField(Integer.toString(chart.getDefaultDuration()));
				durationTextfield.addFocusListener(new FocusListener() {
					@Override public void focusLost(FocusEvent arg0) {
						try {
							int value = Integer.parseInt(durationTextfield.getText().trim());
							if(value < chart.getMinimumDuration())
								durationTextfield.setText(Integer.toString(chart.getMinimumDuration()));
							else if(value > chart.getMaximumDuration())
								durationTextfield.setText(Integer.toString(chart.getMaximumDuration()));
						} catch(Exception e) {
							durationTextfield.setText(Integer.toString(chart.getDefaultDuration()));
						}
					}
					
					@Override public void focusGained(FocusEvent arg0) {
						durationTextfield.selectAll();
					}
				});
				
				panel.add(new JLabel("Duration (Sample Count)"));
				panel.add(durationTextfield);
				
				if(chart.getInputNames() != null) {
					
					// if the chart type has an inputNames array, it accepts a specific number of inputs, so show drop-down boxes for each of the possible inputs
					for(String inputName : chart.getInputNames()) {
						panel.add(new JLabel(inputName));
						JComboBox<Dataset> inputValue = new JComboBox<Dataset>();
						for(Dataset dataset : Controller.getAllDatasets())
							inputValue.addItem(dataset);
						inputComboboxesList.add(inputValue);
						panel.add(inputValue);
					}
					
					panel.add(new JLabel(" "));
					panel.add(new JLabel(" "));
					
					JButton addChartButton = new JButton("Add Chart");
					addChartButton.addActionListener(new ActionListener() {
						@Override public void actionPerformed(ActionEvent arg0) {
							Dataset[] chartInputs = new Dataset[inputComboboxesList.size()];
							for(int i = 0; i < inputComboboxesList.size(); i++)
								chartInputs[i] = (Dataset) inputComboboxesList.get(i).getSelectedItem();

							int chartDuration = Integer.parseInt(durationTextfield.getText().trim());
							Controller.addChart(chart.createChart(x1, y1, x2, y2, chartDuration, chartInputs));
							dispose();
						}
					});
					
					panel.add(new JLabel(" "));
					panel.add(addChartButton);			
					
				} else {
					
					// if the chart type has a null inputNames array, it accepts 1+ inputs (no limit), so show checkboxes for every possible input
					panel.add(new JLabel("Specify inputs: "));
					Map<JCheckBox, Dataset> inputs = new LinkedHashMap<JCheckBox, Dataset>();
					for(Dataset dataset : Controller.getAllDatasets()) {
						JCheckBox checkbox = new JCheckBox(dataset.name);
						inputs.put(checkbox, dataset);
						
						panel.add(checkbox);
						panel.add(new JLabel(""));
					}
					
					panel.add(new JLabel(" "));
					
					JButton addChartButton = new JButton("Add Chart");
					addChartButton.addActionListener(new ActionListener() {
						@Override public void actionPerformed(ActionEvent arg0) {
							
							// calc how many inputs were chosen
							int inputsCount = 0;
							for(JCheckBox inputPossibility : inputs.keySet())
								if(inputPossibility.isSelected())
									inputsCount++;
							
							if(inputsCount == 0) {
								JOptionPane.showMessageDialog(null, "At least one input is required.", "Error: No Inputs", JOptionPane.ERROR_MESSAGE);
								return;
							}
							
							// get the chosen inputs
							Dataset[] chartInputs = new Dataset[inputsCount];
							int input = 0;
							for(Entry<JCheckBox, Dataset> inputPossibility : inputs.entrySet())
								if(inputPossibility.getKey().isSelected())
									chartInputs[input++] = inputPossibility.getValue();

							int chartDuration = Integer.parseInt(durationTextfield.getText().trim());
							Controller.addChart(chart.createChart(x1, y1, x2, y2, chartDuration, chartInputs));
							dispose();
						}
					});
					
					panel.add(new JLabel(" "));
					panel.add(addChartButton);
					
				}
				
				panel.revalidate();
				panel.repaint();
				setSize(getPreferredSize());
			}
		});
		
		chartTypeCombobox.getActionListeners()[0].actionPerformed(null);
		
		setLocationRelativeTo(parentWindow);
		
	}

}
