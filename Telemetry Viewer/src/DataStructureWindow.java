import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.font.FontRenderContext;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;

/**
 * The window that the user uses to specify details about the CSV columns or Binary element byte offsets and types.
 */
@SuppressWarnings("serial")
public class DataStructureWindow extends JDialog {
	
	JPanel dataEntryPanel;
	JPanel tablePanel;
	
	JTextField locationTextfield;
	JComboBox<BinaryProcessor> datatypeCombobox;
	JTextField nameTextfield;
	JButton    colorButton;
	JTextField unitTextfield;
	JTextField conversionFactorATextfield;
	JTextField conversionFactorBTextfield;
	JLabel     unitLabel;
	
	JButton addButton;
	JButton resetButton;
	JButton doneButton;
	
	JTable dataStructureTable;
	JScrollPane scrollableDataStructureTable;
	
	/**
	 * Creates a new window where the user can define the CSV or Binary data structure.
	 * 
	 * @param parentWindow    Which window to center this DataStructureWindow over.
	 * @param packetType      One of the strings from Controller.getPacketTypes()
	 * @param testMode        If true, the user will only be able to view, not edit, the data structure.
	 */
	public DataStructureWindow(JFrame parentWindow, String packetType, boolean testMode) {
		
		super();
		
		setTitle(testMode ? "Data Structure (Not Editable in Test Mode)" : "Data Structure");
		setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		setLayout(new BorderLayout());
		
		ActionListener pressEnterToAddRow = new ActionListener() {
			@Override public void actionPerformed(ActionEvent e) {
				addButton.doClick();
			}
		};
		
		locationTextfield = new JTextField(packetType.equals("Binary") ? "1" : "0", 3);
		locationTextfield.addFocusListener(new FocusListener() {
			@Override public void focusLost(FocusEvent fe) {
				try {
					locationTextfield.setText(locationTextfield.getText().trim());
					int i = Integer.parseInt(locationTextfield.getText());
					if(packetType.equals("Binary") && i == 0)
						throw new Exception();
				} catch(Exception e) {
					locationTextfield.setText(packetType.equals("Binary") ? "1" : "0");
				}
			}
			
			@Override public void focusGained(FocusEvent fe) {
				locationTextfield.selectAll();
			}
		});
		if(packetType.equals("ASCII CSVs"))
			locationTextfield.setEnabled(false);
		
		// only used for the Binary packet type
		datatypeCombobox = new JComboBox<BinaryProcessor>();
		for(BinaryProcessor processor : Controller.getBinaryProcessors())
			datatypeCombobox.addItem(processor);
		
		nameTextfield = new JTextField("", 15);
		nameTextfield.addActionListener(pressEnterToAddRow);
		nameTextfield.addFocusListener(new FocusListener() {
			@Override public void focusLost(FocusEvent e) {
				nameTextfield.setText(nameTextfield.getText().trim());
			}
			
			@Override public void focusGained(FocusEvent e) {
				nameTextfield.selectAll();
			}
		});
		
		colorButton = new JButton("\u25B2");
		colorButton.setForeground(Controller.getDefaultLineColor());
		colorButton.addActionListener(new ActionListener() {
			@Override public void actionPerformed(ActionEvent e) {
				Color color = JColorChooser.showDialog(DataStructureWindow.this, "Pick a Color for " + nameTextfield.getText(), Color.BLACK);
				if(color != null)
					colorButton.setForeground(color);
			}
		});
		
		unitTextfield = new JTextField("", 15);
		unitTextfield.addActionListener(pressEnterToAddRow);
		unitTextfield.addFocusListener(new FocusListener() {
			@Override public void focusLost(FocusEvent arg0) {
				unitTextfield.setText(unitTextfield.getText().trim());
				unitLabel.setText(unitTextfield.getText());
			}
			
			@Override public void focusGained(FocusEvent arg0) {
				unitTextfield.selectAll();
			}
		});
		unitTextfield.addKeyListener(new KeyListener() {
			@Override public void keyReleased(KeyEvent ke) {
				unitTextfield.setText(unitTextfield.getText().trim());
				unitLabel.setText(unitTextfield.getText());
			}
			@Override public void keyPressed(KeyEvent ke) { }
			@Override public void keyTyped(KeyEvent ke) { }
		});
		
		conversionFactorATextfield = new JTextField("1.0", 4);
		conversionFactorATextfield.addActionListener(pressEnterToAddRow);
		conversionFactorATextfield.addFocusListener(new FocusListener() {
			@Override public void focusLost(FocusEvent arg0) {
				try {
					conversionFactorATextfield.setText(conversionFactorATextfield.getText().trim());
					double value = Double.parseDouble(conversionFactorATextfield.getText());
					if(value == 0.0 || value == Double.NaN || value == Double.POSITIVE_INFINITY || value == Double.NEGATIVE_INFINITY) throw new Exception();
				} catch(Exception e) {
					conversionFactorATextfield.setText("1.0");
				}
			}
			
			@Override public void focusGained(FocusEvent arg0) {
				conversionFactorATextfield.selectAll();
			}
		});
		
		conversionFactorBTextfield = new JTextField("1.0", 4);
		conversionFactorBTextfield.addActionListener(pressEnterToAddRow);
		conversionFactorBTextfield.addFocusListener(new FocusListener() {
			@Override public void focusLost(FocusEvent arg0) {
				try {
					conversionFactorBTextfield.setText(conversionFactorBTextfield.getText().trim());
					double value = Double.parseDouble(conversionFactorBTextfield.getText());
					if(value == 0.0 || value == Double.NaN || value == Double.POSITIVE_INFINITY || value == Double.NEGATIVE_INFINITY) throw new Exception();
				} catch(Exception e) {
					conversionFactorBTextfield.setText("1.0");
				}
			}
			
			@Override public void focusGained(FocusEvent arg0) {
				conversionFactorBTextfield.selectAll();
			}
		});
		
		unitLabel = new JLabel("_______________");
		unitLabel.setMinimumSize(unitLabel.getPreferredSize());
		unitLabel.setPreferredSize(unitLabel.getPreferredSize());
		unitLabel.setHorizontalAlignment(JLabel.LEFT);
		unitLabel.setText("");		
		
		addButton = new JButton("Add");
		addButton.addActionListener(new ActionListener() {
			@Override public void actionPerformed(ActionEvent e) {
				int location = Integer.parseInt(locationTextfield.getText());
				BinaryProcessor processor = (BinaryProcessor) datatypeCombobox.getSelectedItem();
				String name = nameTextfield.getText().trim();
				Color color = colorButton.getForeground();
				String unit = unitTextfield.getText();
				float conversionFactorA = Float.parseFloat(conversionFactorATextfield.getText());
				float conversionFactorB = Float.parseFloat(conversionFactorBTextfield.getText());
				
				if(name.equals("")) {
					JOptionPane.showMessageDialog(tablePanel, "A name is required.", "Error: Name Required", JOptionPane.ERROR_MESSAGE);
				} else {
					Controller.insertDataset(location, processor, name, color, unit, conversionFactorA, conversionFactorB);
					dataStructureTable.revalidate();
					dataStructureTable.repaint();
					int newLocation = packetType.equals("ASCII CSVs") ? location + 1 : location + processor.getByteCount();
					locationTextfield.setText(Integer.toString(newLocation));
					nameTextfield.requestFocus();
					nameTextfield.selectAll();
				}
			}
		});
		
		resetButton = new JButton("Reset");
		resetButton.addActionListener(new ActionListener() {
			@Override public void actionPerformed(ActionEvent arg0) {
				Controller.removeAllDatasets();
				dataStructureTable.revalidate();
				dataStructureTable.repaint();
				locationTextfield.setText(packetType.equals("Binary") ? "1" : "0");
				nameTextfield.requestFocus();
			}
		});
		
		doneButton = new JButton("Done");
		doneButton.addActionListener(new ActionListener() {
			@Override public void actionPerformed(ActionEvent e) {
				if(Controller.getDatasetsCount() == 0) {
					JOptionPane.showMessageDialog(tablePanel, "At least one entry is required.", "Error: No Entries", JOptionPane.ERROR_MESSAGE);
				} else {
					Controller.startReceivingData();
					dispose();
				}
			}
		});
		
		dataEntryPanel = new JPanel();
		dataEntryPanel.setBorder(new EmptyBorder(5, 5, 0, 5));
		if(packetType.equals("ASCII CSVs"))
			dataEntryPanel.add(new JLabel("Column Number"));
		else if(packetType.equals("Binary"))
			dataEntryPanel.add(new JLabel("Byte Offset"));
		dataEntryPanel.add(locationTextfield);
		dataEntryPanel.add(Box.createHorizontalStrut(20));
		if(packetType.equals("Binary")) {
			dataEntryPanel.add(datatypeCombobox);
			dataEntryPanel.add(Box.createHorizontalStrut(20));
		}
		dataEntryPanel.add(new JLabel("Name"));
		dataEntryPanel.add(nameTextfield);
		dataEntryPanel.add(Box.createHorizontalStrut(20));
		dataEntryPanel.add(new JLabel("Color"));
		dataEntryPanel.add(colorButton);
		dataEntryPanel.add(Box.createHorizontalStrut(20));
		dataEntryPanel.add(new JLabel("Unit"));
		dataEntryPanel.add(unitTextfield);
		dataEntryPanel.add(Box.createHorizontalStrut(80));
		dataEntryPanel.add(conversionFactorATextfield);
		dataEntryPanel.add(new JLabel(" LSBs = "));
		dataEntryPanel.add(conversionFactorBTextfield);
		dataEntryPanel.add(unitLabel);
		dataEntryPanel.add(Box.createHorizontalStrut(80));
		dataEntryPanel.add(addButton);		
		dataEntryPanel.add(Box.createHorizontalStrut(20));
		dataEntryPanel.add(resetButton);
		dataEntryPanel.add(Box.createHorizontalStrut(20));
		dataEntryPanel.add(doneButton);
		
		dataStructureTable = new JTable(new AbstractTableModel() {
			@Override public String getColumnName(int column) {
				if(column == 0)
					return packetType.equals("ASCII CSVs") ? "Column Number" : "Byte Offset, Data Type";
				else if(column == 1)
					return "Name";
				else if(column == 2)
					return "Color";
				else if(column == 3)
					return "Unit";
				else if(column == 4)
					return "Conversion Ratio";
				else
					return "Error";
			}
			
			@Override public Object getValueAt(int row, int column) {
				
				if(row == 0 && packetType.equals("Binary")) {
					
					if(column  == 0)
						return "0, Sync Word";
					else if(column == 1)
						return "0xAA";
					else if(column == 2)
						return "";
					else if(column == 3)
						return "";
					else if(column == 4)
						return "";
					else
						return null;
					
				}
				
				if(packetType.equals("Binary"))
					row--;
				
				Dataset dataset = Controller.getDatasetByIndex(row);
				
				if(column  == 0)
					return packetType.equals("ASCII CSVs") ? dataset.location : dataset.location + ", " + dataset.processor.toString();
				else if(column == 1)
					return dataset.name;
				else if(column == 2)
					return "<html><font color=\"rgb(" + dataset.color.getRed() + "," + dataset.color.getGreen() + "," + dataset.color.getBlue() + ")\">\u25B2</font></html>";
				else if(column == 3)
					return dataset.unit;
				else if(column == 4)
					return String.format("%3.3f LSBs = %3.3f %s", dataset.conversionFactorA, dataset.conversionFactorB, dataset.unit);
				else
					return null;
			}
			
			@Override public int getRowCount() {
				
				int count = Controller.getDatasetsCount();
				
				if(packetType.equals("Binary"))
					return count + 1;
				else
					return count;
				
			}
			
			@Override public int getColumnCount() {
				return 5;
			}
		});
		scrollableDataStructureTable = new JScrollPane(dataStructureTable);
		
		tablePanel = new JPanel(new GridLayout(1, 1));
		tablePanel.setBorder(new EmptyBorder(5, 5, 5, 5));
		tablePanel.add(scrollableDataStructureTable, BorderLayout.CENTER);
		dataStructureTable.setRowHeight((int) tablePanel.getFont().getStringBounds("Abcdefghijklmnopqrstuvwxyz", new FontRenderContext(null, true, true)).getHeight()); // fix display scaling issue
		
		add(dataEntryPanel, BorderLayout.NORTH);
		add(tablePanel, BorderLayout.CENTER);
		
		pack();
		setMinimumSize(new Dimension(getPreferredSize().width, 500));
		setLocationRelativeTo(parentWindow);
		
		nameTextfield.requestFocus();
		
		if(testMode) {
			locationTextfield.setEnabled(false);
			datatypeCombobox.setEnabled(false);
			nameTextfield.setEnabled(false);
			colorButton.setEnabled(false);
			unitTextfield.setEnabled(false);
			conversionFactorATextfield.setEnabled(false);
			conversionFactorBTextfield.setEnabled(false);
			addButton.setEnabled(false);
			resetButton.setEnabled(false);
		}
		
		setModal(true);
		setVisible(true);
		
	}

}
