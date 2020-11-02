import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.font.FontRenderContext;
import java.util.Collections;
import java.util.List;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;

import net.miginfocom.swing.MigLayout;

@SuppressWarnings("serial")
public class DataStructureBinaryView extends JPanel {
	
	private static DataStructureBinaryView instance = new DataStructureBinaryView();
	
	/**
	 * Updates the GUI to reflect the current state.
	 * 
	 * @return    The JPanel GUI.
	 */
	public static JPanel getUpdatedGui() {
		
		// reset the GUI
		if(DatasetsController.getFirstAvailableLocation() != -1)
			instance.offsetTextfield.setText(Integer.toString(DatasetsController.getFirstAvailableLocation()));
		instance.processorCombobox.setSelectedIndex(0);
		instance.nameTextfield.setText("");
		instance.colorButton.setForeground(Theme.defaultDatasetColor);
		instance.unitTextfield.setText("");
		instance.conversionFactorAtextfield.setText("1.0");
		instance.conversionFactorBtextfield.setText("1.0");
		instance.unitLabel.setText("");
		if(instance.bitfieldDefinitionInProgress) {
			for(Component c : instance.getComponents())
				if(c instanceof BitfieldPanel) {
					instance.remove(c);
					break;
				}
			instance.add(instance.scrollableDataStructureTable, "grow, span, cell 0 2");
			instance.bitfieldDefinitionInProgress = false;
		}
		
		SwingUtilities.invokeLater(() -> instance.updateGui(true)); // invokeLater to ensure focus isn't taken away
		
		return instance;
		
	}
	
	JLabel            dsdLabel;
	JTextField        offsetTextfield;
	JComboBox<Object> processorCombobox;
	JTextField        nameTextfield;
	JButton           colorButton;
	JTextField        unitTextfield;
	JTextField        conversionFactorAtextfield;
	JTextField        conversionFactorBtextfield;
	JLabel            unitLabel;
	JButton           addButton;
	JButton           doneButton;
	
	JTable dataStructureTable;
	JScrollPane scrollableDataStructureTable;
	
	boolean bitfieldDefinitionInProgress;
	
	/**
	 * Private constructor to enforce singleton usage.
	 */
	private DataStructureBinaryView() {
		
		super();
		
		// all JTextFields let the user press enter to add the field
		ActionListener pressEnterToAddField = event -> addButton.doClick();
		
		// offset (first byte) of the field
		offsetTextfield = new JTextField(Integer.toString(DatasetsController.getFirstAvailableLocation()), 3);
		offsetTextfield.addActionListener(pressEnterToAddField);
		offsetTextfield.addFocusListener(new FocusListener() {
			@Override public void focusLost(FocusEvent fe) {
				try {
					offsetTextfield.setText(offsetTextfield.getText().trim());
					Integer.parseInt(offsetTextfield.getText());
				} catch(Exception e) {
					offsetTextfield.setText(Integer.toString(DatasetsController.getFirstAvailableLocation()));
					if(DatasetsController.getFirstAvailableLocation() == -1)
						addButton.setEnabled(false);
				}
			}
			@Override public void focusGained(FocusEvent fe) {
				offsetTextfield.selectAll();
			}
		});

		// processor of the field (the processor converts raw bytes into numbers, or evaluates checksums) 
		processorCombobox = new JComboBox<Object>();
		for(DatasetsController.BinaryFieldProcessor processor : DatasetsController.binaryFieldProcessors)
			processorCombobox.addItem(processor);
		for(DatasetsController.BinaryChecksumProcessor processor : DatasetsController.binaryChecksumProcessors)
			processorCombobox.addItem(processor);
		processorCombobox.addActionListener(event -> updateGui(true));
		
		// name of the field
		nameTextfield = new JTextField("", 15);
		nameTextfield.addActionListener(pressEnterToAddField);
		nameTextfield.addFocusListener(new FocusListener() {
			@Override public void focusLost(FocusEvent e)   { nameTextfield.setText(nameTextfield.getText().trim()); }
			@Override public void focusGained(FocusEvent e) { nameTextfield.selectAll(); }
		});
		
		// color of the field
		colorButton = new JButton("\u25B2");
		colorButton.setForeground(Theme.defaultDatasetColor);
		colorButton.addActionListener(event -> colorButton.setForeground(ColorPickerView.getColor(nameTextfield.getText(), colorButton.getForeground())));
		
		// unit of the field
		unitTextfield = new JTextField("", 15);
		unitTextfield.addActionListener(pressEnterToAddField);
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
		
		// conversion ratio of the field as "x = x [Units]"
		conversionFactorAtextfield = new JTextField("1.0", 4);
		conversionFactorAtextfield.addActionListener(pressEnterToAddField);
		conversionFactorAtextfield.addFocusListener(new FocusListener() {
			@Override public void focusLost(FocusEvent arg0) {
				try {
					conversionFactorAtextfield.setText(conversionFactorAtextfield.getText().trim());
					double value = Double.parseDouble(conversionFactorAtextfield.getText());
					if(value == 0.0 || value == Double.NaN || value == Double.POSITIVE_INFINITY || value == Double.NEGATIVE_INFINITY) throw new Exception();
				} catch(Exception e) {
					conversionFactorAtextfield.setText("1.0");
				}
			}
			@Override public void focusGained(FocusEvent arg0) {
				conversionFactorAtextfield.selectAll();
			}
		});
		
		conversionFactorBtextfield = new JTextField("1.0", 4);
		conversionFactorBtextfield.addActionListener(pressEnterToAddField);
		conversionFactorBtextfield.addFocusListener(new FocusListener() {
			@Override public void focusLost(FocusEvent arg0) {
				try {
					conversionFactorBtextfield.setText(conversionFactorBtextfield.getText().trim());
					double value = Double.parseDouble(conversionFactorBtextfield.getText());
					if(value == 0.0 || value == Double.NaN || value == Double.POSITIVE_INFINITY || value == Double.NEGATIVE_INFINITY) throw new Exception();
				} catch(Exception e) {
					conversionFactorBtextfield.setText("1.0");
				}
			}
			@Override public void focusGained(FocusEvent arg0) {
				conversionFactorBtextfield.selectAll();
			}
		});
		
		unitLabel = new JLabel("", JLabel.LEFT) {
			@Override public Dimension getMinimumSize()   { return unitTextfield.getPreferredSize(); }
			@Override public Dimension getPreferredSize() { return unitTextfield.getPreferredSize(); }
		};	
		
		// add button to insert the new field into the data structure (if possible)
		addButton = new JButton("Add");
		addButton.addActionListener(event -> {
				
			int location = Integer.parseInt(offsetTextfield.getText());
			Object processor = processorCombobox.getSelectedItem();
			String name = nameTextfield.getText().trim();
			Color color = colorButton.getForeground();
			String unit = unitTextfield.getText();
			float conversionFactorA = Float.parseFloat(conversionFactorAtextfield.getText());
			float conversionFactorB = Float.parseFloat(conversionFactorBtextfield.getText());
			
			if(processor instanceof DatasetsController.BinaryFieldProcessor) {
				
				if(name.equals("")) {
					JOptionPane.showMessageDialog(instance, "A name is required.", "Error: Name Required", JOptionPane.ERROR_MESSAGE);
					return;
				}
				
				String errorMessage = DatasetsController.insertDataset(location, (DatasetsController.BinaryFieldProcessor) processor, name, color, unit, conversionFactorA, conversionFactorB);
				if(errorMessage != null)
					JOptionPane.showMessageDialog(instance, errorMessage, "Error", JOptionPane.ERROR_MESSAGE);
				
				if(((DatasetsController.BinaryFieldProcessor) processor).toString().startsWith("Bitfield") && errorMessage == null) {

					BitfieldPanel bitfieldPanel = new BitfieldPanel(((DatasetsController.BinaryFieldProcessor) processor).getByteCount() * 8, DatasetsController.getDatasetByLocation(location));
					
					remove(scrollableDataStructureTable);
					add(bitfieldPanel, "grow, span, cell 0 2");
					bitfieldDefinitionInProgress = true;
					
				}
				
			} else if(processor instanceof DatasetsController.BinaryChecksumProcessor) {
				
				String errorMessage = DatasetsController.insertChecksum(location, (DatasetsController.BinaryChecksumProcessor) processor);
				if(errorMessage != null)
					JOptionPane.showMessageDialog(instance, errorMessage, "Error", JOptionPane.ERROR_MESSAGE);
				
			}
			
			updateGui(true);
			
		});
		
		// done button for when the data structure is complete
		doneButton = new JButton("Done");
		doneButton.addActionListener(event -> {
			if(DatasetsController.getDatasetsCount() == 0) {
				JOptionPane.showMessageDialog(instance, "Error: Define at least one field, or disconnect.", "Error", JOptionPane.ERROR_MESSAGE);
			} else {
				CommunicationController.setDataStructureDefined(true);
				if(ChartsController.getCharts().isEmpty())
					NotificationsController.showHintUntil("Add a chart by clicking on a tile, or by clicking-and-dragging across multiple tiles.", () -> !ChartsController.getCharts().isEmpty(), true);
				Main.hideDataStructureGui();
			}
		});
		
		// table to visualize the data structure
		dataStructureTable = new JTable(new AbstractTableModel() {
			@Override public String getColumnName(int column) {
				if(column == 0)      return "Byte Offset, Data Type";
				else if(column == 1) return "Name";
				else if(column == 2) return "Color";
				else if(column == 3) return "Unit";
				else if(column == 4) return "Conversion Ratio";
				else if(column == 5) return "";
				else                 return "Error";
			}
			
			@Override public Object getValueAt(int row, int column) {
				// the first row is always the sync word
				if(row == 0) {
					if(column == 0)      return "0, [Sync Word]";
					else if(column == 1) return String.format("0x%02X", CommunicationController.syncWord);
					else                 return "";
				}
				
				// subsequent rows are the fields
				row--;
				if(row < DatasetsController.getDatasetsCount()) {
					Dataset dataset = DatasetsController.getDatasetByIndex(row);
					if(column == 0)      return dataset.location + ", " + dataset.processor.toString();
					else if(column == 1) return dataset.name;
					else if(column == 2) return "<html><font color=\"rgb(" + dataset.color.getRed() + "," + dataset.color.getGreen() + "," + dataset.color.getBlue() + ")\">\u25B2</font></html>";
					else if(column == 3) return dataset.isBitfield ? "" : dataset.unit;
					else if(column == 4) return dataset.isBitfield ? "" : String.format("%3.3f = %3.3f %s", dataset.conversionFactorA, dataset.conversionFactorB, dataset.unit);
					else                 return "";
				}
				
				// last row is the checksum if it exists
				if(DatasetsController.checksumProcessor != null) {
					if(column == 0)      return DatasetsController.checksumProcessorOffset + ", [Checksum]";
					else if(column == 1) return DatasetsController.checksumProcessor.toString();
					else                 return "";
				}
				
				// this should never happen
				return "";
			}
			
			@Override public int getRowCount() {
				int count = 1; // the syncWord
				count += DatasetsController.getDatasetsCount();
				if(DatasetsController.checksumProcessor != null)
					count++;
				
				return count;
			}
			
			@Override public int getColumnCount() {
				return 6;
			}
			
		});
		dataStructureTable.setRowHeight((int) (dataStructureTable.getFont().getStringBounds("Abcdefghijklmnopqrstuvwxyz", new FontRenderContext(null, true, true)).getHeight() * 1.5));
		dataStructureTable.getColumn("").setCellRenderer(new TableCellRenderer() {
			@Override public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
				JButton b = new JButton("Remove");
				if(CommunicationController.getPort().equals(CommunicationController.PORT_DEMO_MODE))
					b.setEnabled(false);
				return row != 0 ? b : new JLabel("");
			}
		});
		dataStructureTable.addMouseListener(new MouseListener() {
			
			// ask the user to confirm
			@Override public void mousePressed(MouseEvent e) {
				if(CommunicationController.getPort().equals(CommunicationController.PORT_DEMO_MODE))
					return;
				int datasetNumber = dataStructureTable.getSelectedRow() - 1; // -1 because of the syncword
				if(datasetNumber < 0) {
					// syncword clicked, do nothing
				} else if(datasetNumber < DatasetsController.getDatasetsCount()) {
					// remove button for a dataset was clicked
					Dataset dataset = DatasetsController.getDatasetByIndex(datasetNumber);
					String title = "Remove " + dataset.name + "?";
					String message = "<html>Remove the " + dataset.name + " dataset?";
					if(DatasetsController.getSampleCount() > 0)
						message += "<br>WARNING: This will also remove all acquired samples from EVERY dataset!</html>";
					boolean remove = JOptionPane.showConfirmDialog(instance, message, title, JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
					if(remove) {
						offsetTextfield.setText(Integer.toString(dataset.location));
						for(ActionListener al : processorCombobox.getActionListeners())
							processorCombobox.removeActionListener(al);
						for(int i = 0; i < processorCombobox.getItemCount(); i++)
							if(processorCombobox.getItemAt(i).toString().equals(dataset.processor.toString()))
								processorCombobox.setSelectedIndex(i);
						processorCombobox.addActionListener(event -> updateGui(true));
						nameTextfield.setText(dataset.name);
						colorButton.setForeground(dataset.color);
						unitTextfield.setText(dataset.unit);
						unitLabel.setText(dataset.unit);
						conversionFactorAtextfield.setText(Float.toString(dataset.conversionFactorA));
						conversionFactorBtextfield.setText(Float.toString(dataset.conversionFactorB));
						DatasetsController.removeAllData();
						DatasetsController.removeDataset(dataset.location);
					}
				} else {
					// remove button for the checksum was clicked
					String title = "Remove checksum?";
					String message = "<html>Remove the " + DatasetsController.checksumProcessor + "?";
					if(DatasetsController.getSampleCount() > 0)
						message += "<br>WARNING: This will also remove all acquired samples from EVERY dataset!</html>";
					boolean remove = JOptionPane.showConfirmDialog(instance, message, title, JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
					if(remove) {
						offsetTextfield.setText(Integer.toString(DatasetsController.checksumProcessorOffset));
						processorCombobox.setSelectedItem(DatasetsController.checksumProcessor);
						DatasetsController.removeAllData();
						DatasetsController.removeChecksum();
					}
				}
				dataStructureTable.clearSelection();
				updateGui(false);
			}
			
			// clear the selection again, in case the user click-and-dragged over the table
			@Override public void mouseReleased(MouseEvent e) {
				dataStructureTable.clearSelection();
				updateGui(false);
			}
			
			@Override public void mouseExited(MouseEvent e) { }
			@Override public void mouseEntered(MouseEvent e) { }
			@Override public void mouseClicked(MouseEvent e) { }
		});
		
		// layout the panel
		Font titleFont = getFont().deriveFont(Font.BOLD, getFont().getSize() * 1.4f);
		setLayout(new MigLayout("fill, gap " + Theme.padding, Theme.padding + "[][][][][][][][][][][][][][][][][][]push[][][]" + Theme.padding, "[][][100%]0"));
		dsdLabel = new JLabel("Data Structure Definition:");
		dsdLabel.setFont(titleFont);
		add(dsdLabel, "grow, span");
		add(new JLabel("Byte Offset"));
		add(offsetTextfield);
		add(Box.createHorizontalStrut(Theme.padding));
		add(processorCombobox);
		add(Box.createHorizontalStrut(Theme.padding));
		add(new JLabel("Name"));
		add(nameTextfield);
		add(Box.createHorizontalStrut(Theme.padding));
		add(new JLabel("Color"));
		add(colorButton);
		add(Box.createHorizontalStrut(Theme.padding));
		add(new JLabel("Unit"));
		add(unitTextfield);
		add(Box.createHorizontalStrut(Theme.padding * 4));
		add(conversionFactorAtextfield);
		add(new JLabel(" = "));
		add(conversionFactorBtextfield);
		add(unitLabel);
		add(Box.createHorizontalStrut(Theme.padding * 4));
		add(addButton);
		add(doneButton, "wrap");
		scrollableDataStructureTable = new JScrollPane(dataStructureTable);
		add(scrollableDataStructureTable, "grow, span");
		
		updateGui(true);
		setMinimumSize(new Dimension(getPreferredSize().width, 500));
		setVisible(true);
		
	}
	
	/**
	 * Updates the GUI to reflect the current state (enables/disables/configures widgets as needed.)
	 */
	private void updateGui(boolean updateOffsetNumber) {
		
		if(CommunicationController.getPort().equals(CommunicationController.PORT_DEMO_MODE)) {
			
			dsdLabel.setText("Data Structure Definition: (Not Editable in Test Mode)");
			offsetTextfield.setEnabled(false);
			processorCombobox.setEnabled(false);
			nameTextfield.setEnabled(false);
			colorButton.setEnabled(false);
			unitTextfield.setEnabled(false);
			conversionFactorAtextfield.setEnabled(false);
			conversionFactorBtextfield.setEnabled(false);
			addButton.setEnabled(false);
			doneButton.setEnabled(true);
			SwingUtilities.invokeLater(() -> { // invokeLater to ensure a following revalidate/repaint does not take focus away
				doneButton.requestFocus();
			});
     		
		} else if(bitfieldDefinitionInProgress) {
			
			dsdLabel.setText("Data Structure Definition:");
			offsetTextfield.setEnabled(false);
			processorCombobox.setEnabled(false);
			nameTextfield.setEnabled(false);
			colorButton.setEnabled(false);
			unitTextfield.setEnabled(false);
			conversionFactorAtextfield.setEnabled(false);
			conversionFactorBtextfield.setEnabled(false);
			addButton.setEnabled(false);
			doneButton.setEnabled(false);
			unitTextfield.setText("");
			unitLabel.setText("");
			conversionFactorAtextfield.setText("1.0");
			conversionFactorBtextfield.setText("1.0");
			
		} else if(DatasetsController.getFirstAvailableLocation() == -1) {
			
			dsdLabel.setText("Data Structure Definition:");
			offsetTextfield.setEnabled(false);
			processorCombobox.setEnabled(false);
			nameTextfield.setEnabled(false);
			colorButton.setEnabled(false);
			unitTextfield.setEnabled(false);
			conversionFactorAtextfield.setEnabled(false);
			conversionFactorBtextfield.setEnabled(false);
			addButton.setEnabled(false);
			doneButton.setEnabled(true);
			SwingUtilities.invokeLater(() -> { // invokeLater to ensure a following revalidate/repaint does not take focus away
				doneButton.requestFocus();
			});
     		
		} else if(processorCombobox.getSelectedItem().toString().startsWith("Bitfield")) {
			
			dsdLabel.setText("Data Structure Definition:");
			offsetTextfield.setEnabled(true);
			processorCombobox.setEnabled(true);
			nameTextfield.setEnabled(true);
			colorButton.setEnabled(true);
			unitTextfield.setEnabled(false);
			conversionFactorAtextfield.setEnabled(false);
			conversionFactorBtextfield.setEnabled(false);
			addButton.setEnabled(true);
			doneButton.setEnabled(true);
			unitTextfield.setText("");
			unitLabel.setText("");
			conversionFactorAtextfield.setText("1.0");
			conversionFactorBtextfield.setText("1.0");
			if(updateOffsetNumber)
				offsetTextfield.setText(Integer.toString(DatasetsController.getFirstAvailableLocation()));
			SwingUtilities.invokeLater(() -> { // invokeLater to ensure a following revalidate/repaint does not take focus away
				nameTextfield.requestFocus();
				nameTextfield.selectAll();
			});
			
		} else if(processorCombobox.getSelectedItem() instanceof DatasetsController.BinaryFieldProcessor) {
			
			dsdLabel.setText("Data Structure Definition:");
			offsetTextfield.setEnabled(true);
			processorCombobox.setEnabled(true);
			nameTextfield.setEnabled(true);
			colorButton.setEnabled(true);
			unitTextfield.setEnabled(true);
			conversionFactorAtextfield.setEnabled(true);
			conversionFactorBtextfield.setEnabled(true);
			addButton.setEnabled(true);
			doneButton.setEnabled(true);
			if(updateOffsetNumber) {
				offsetTextfield.setText(Integer.toString(DatasetsController.getFirstAvailableLocation()));
				if(processorCombobox.getSelectedItem().toString().startsWith("Bitfield") || processorCombobox.getSelectedItem() instanceof DatasetsController.BinaryChecksumProcessor)
					processorCombobox.setSelectedIndex(0);
			}
			SwingUtilities.invokeLater(() -> { // invokeLater to ensure a following revalidate/repaint does not take focus away
				nameTextfield.requestFocus();
				nameTextfield.selectAll();
			});
			
			
		} else if(processorCombobox.getSelectedItem() instanceof DatasetsController.BinaryChecksumProcessor) {
			
			dsdLabel.setText("Data Structure Definition:");
			offsetTextfield.setEnabled(true);
			processorCombobox.setEnabled(true);
			nameTextfield.setEnabled(false);
			colorButton.setEnabled(false);
			unitTextfield.setEnabled(false);
			conversionFactorAtextfield.setEnabled(false);
			conversionFactorBtextfield.setEnabled(false);
			addButton.setEnabled(true);
			doneButton.setEnabled(true);
			nameTextfield.setText("");
			unitTextfield.setText("");
			unitLabel.setText("");
			conversionFactorAtextfield.setText("1.0");
			conversionFactorBtextfield.setText("1.0");
			SwingUtilities.invokeLater(() -> { // invokeLater to ensure a following revalidate/repaint does not take focus away
				nameTextfield.requestFocus();
				nameTextfield.selectAll();
			});
			
		}
		
		dataStructureTable.revalidate();
		dataStructureTable.repaint();
		revalidate();
		repaint();
		
	}

	/**
	 * The GUI for defining a Bitfield Dataset.
	 */
	private static class BitfieldPanel extends JPanel {
		
		JPanel widgets = new JPanel(new MigLayout("wrap 3, gap " + Theme.padding, "[pref][pref][grow]"));
		Dataset dataset;
		
		public BitfieldPanel(int bitCount, Dataset dataset) {
			
			super();
			setLayout(new BorderLayout());
			
			JButton doneButton = new JButton("Done With Bitfield");
			doneButton.addActionListener(event -> {
				instance.remove(this);
				instance.add(instance.scrollableDataStructureTable, "grow, span, cell 0 2");
				instance.bitfieldDefinitionInProgress = false;
				instance.updateGui(true);
			});
			JPanel bottom = new JPanel();
			bottom.setLayout(new FlowLayout(FlowLayout.RIGHT, 0, 0));
			bottom.setBorder(new EmptyBorder(Theme.padding, 0, 0, 0));
			bottom.add(doneButton);
			
			add(new Visualization(bitCount), BorderLayout.NORTH);
			add(new JScrollPane(widgets), BorderLayout.CENTER);
			add(bottom, BorderLayout.SOUTH);
			
			this.dataset = dataset;
			
		}
		
		/**
		 * The user added a new bitfield, so update and redraw the panel.
		 * 
		 * @param MSBit    The most-significant bit of the bitfield.
		 * @param LSBit    The least-significant bit of the bitfield.
		 */
		public void addField(int MSBit, int LSBit) {
			
			dataset.addBitfield(MSBit, LSBit);
			
			List<Dataset.Bitfield> bitfields = dataset.getBitfields();
			Collections.sort(bitfields);
			
			// update the GUI
			widgets.removeAll();
			widgets.add(new JLabel("<html><b>State&nbsp;&nbsp;&nbsp;</b></html>"));
			widgets.add(new JLabel("<html><b>Color&nbsp;&nbsp;&nbsp;</b></html>"));
			widgets.add(new JLabel("<html><b>Name&nbsp;&nbsp;&nbsp;</b></html>"));
			
			for(Dataset.Bitfield bitfield : bitfields) {
				for(Dataset.Bitfield.State state : bitfield.states) {
					JTextField textfield = new JTextField(state.name);
					textfield.addKeyListener(new KeyListener() {
						@Override public void keyTyped(KeyEvent e)    { }
						@Override public void keyPressed(KeyEvent e)  { }
						@Override public void keyReleased(KeyEvent e) {
							textfield.setText(textfield.getText().replace(',', ' ')); // no commas allowed, because they will break import/export logic
							state.name = textfield.getText();
						}
					});
					textfield.addFocusListener(new FocusListener() {
						@Override public void focusLost(FocusEvent e)   { }
						@Override public void focusGained(FocusEvent e) {
							textfield.selectAll();
						}
					});
					
					JButton colorButton = new JButton("\u25B2");
					colorButton.setForeground(state.color);
					colorButton.addActionListener(event -> {
						Color color = ColorPickerView.getColor(state.name, colorButton.getForeground());
						colorButton.setForeground(color);
						state.color = color;
						state.glColor = new float[] {color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f, 1};
					});
					
					widgets.add(new JLabel(state.label));
					widgets.add(colorButton);
					widgets.add(textfield, "growx"); // stretch to fill column width
				}
				widgets.add(new JLabel(" "));
				widgets.add(new JLabel(" "));
				widgets.add(new JLabel(" "));
			}
			widgets.remove(widgets.getComponentCount() - 1);
			widgets.remove(widgets.getComponentCount() - 1);
			widgets.remove(widgets.getComponentCount() - 1);
			
			revalidate();
			repaint();
			
		}
		
		/**
		 * This panel contains the "tiles" that represent each bit in the bitfield.
		 * The user clicks or click-and-drags on the bits to specify a range for each bitfield.
		 */
		private class Visualization extends JPanel {
			
			int bitCount;
			
			final int padding = Theme.padding * 2; // space inside each button, between the edges and the text label
			final int spacing = Theme.padding; // space between each button
			final Color tileColor = new Color(Theme.tileColor[0], Theme.tileColor[1], Theme.tileColor[2], Theme.tileColor[3]);
			final Color tileSelectedColor = new Color(Theme.tileSelectedColor[0], Theme.tileSelectedColor[1], Theme.tileSelectedColor[2], Theme.tileSelectedColor[3]);
			
			int maxTextWidth;
			int textHeight;
			int buttonWidth;
			int buttonHeight;
			
			int firstBit = -1;
			int lastBit = -1;
			
			public Visualization(int bitCount) {
				
				super();
				this.bitCount = bitCount;
				
				// determine the required size of this panel
				maxTextWidth = getFontMetrics(getFont()).stringWidth(bitCount - 1 + "");
				textHeight = getFontMetrics(getFont()).getAscent();
				buttonWidth = padding + maxTextWidth + padding;
				buttonHeight = padding + textHeight + padding;
				int totalWidth = (bitCount * buttonWidth) + (spacing * bitCount);
				int totalHeight = buttonHeight + spacing;
				Dimension size = new Dimension(totalWidth, totalHeight);
				setMinimumSize(size);
				setPreferredSize(size);
				setMaximumSize(size);
				
				addMouseListener(new MouseListener() {
					
					@Override public void mousePressed(MouseEvent e) {
						firstBit = -1;
						lastBit = -1;
						
						// if the user clicked on a button, mark it as the first and last bit *if* this bit is not already being used
						int x = e.getX();
						int y = e.getY();
						for(int i = 0; i < bitCount; i++) {
							int minX = i * (spacing + buttonWidth);
							int maxX = minX + buttonWidth;
							int minY = 0;
							int maxY = minY + buttonHeight;
							if(x >= minX && x <= maxX && y >= minY && y <= maxY) {
								int bit = bitCount - 1 - i;
								boolean bitAvailable = true;
								for(Dataset.Bitfield bitfield : dataset.getBitfields())
									if(bit >= bitfield.LSBit && bit <= bitfield.MSBit)
										bitAvailable = false;
								if(bitAvailable) {
									firstBit = bit;
									lastBit = bit;
								}
							}
						}
						repaint();
					}
					
					@Override public void mouseReleased(MouseEvent e) {
						if(firstBit == -1 || lastBit == -1)
							return;
						
						// the user released the mouse, so add the field
						addField(Integer.max(firstBit, lastBit), Integer.min(firstBit, lastBit));
						firstBit = -1;
						lastBit = -1;
						repaint();
					}
					
					@Override public void mouseExited(MouseEvent e) { }
					@Override public void mouseEntered(MouseEvent e) { }
					@Override public void mouseClicked(MouseEvent e) { }
				});
				
				addMouseMotionListener(new MouseMotionListener() {
					
					@Override public void mouseMoved(MouseEvent e) { }
					
					@Override public void mouseDragged(MouseEvent e) {
						if(firstBit == -1 || lastBit == -1)
							return;
						
						// the user moved the mouse, so update the proposed bit range *if* the entire range is available
						int x = e.getX();
						int y = e.getY();
						for(int i = 0; i < bitCount; i++) {
							int minX = i * (spacing + buttonWidth);
							int maxX = minX + buttonWidth;
							int minY = 0;
							int maxY = minY + buttonHeight;
							if(x >= minX && x <= maxX && y >= minY && y <= maxY) {
								int bit = bitCount - 1 - i;
								boolean bitRangeAvailable = true;
								for(int b = Integer.min(bit, firstBit); b <= Integer.max(bit, firstBit); b++)
									for(Dataset.Bitfield bitfield : dataset.getBitfields())
										if(b >= bitfield.LSBit && b <= bitfield.MSBit)
											bitRangeAvailable = false;
								if(bitRangeAvailable)
									lastBit = bit;
							}
						}
						repaint();
					}
				});
				
			}
			
			@Override protected void paintComponent(Graphics g) {
				
				super.paintComponent(g);
				
				// draw the background
				g.setColor(getBackground());
				g.fillRect(0, 0, getWidth(), getHeight());
				
				// draw each button
				g.setColor(tileColor);
				for(int i = 0; i < bitCount; i++) {
					int x = i * (spacing + buttonWidth);
					int y = 0;
					g.fillRect(x, y, buttonWidth, buttonHeight);
				}
				
				// draw existing fields
				g.setColor(getBackground());
				for(Dataset.Bitfield bitfield : dataset.getBitfields()) {
					int width = padding + maxTextWidth + padding + (bitfield.MSBit - bitfield.LSBit) * (spacing + buttonWidth);
					int height = padding + textHeight + padding;
					int x = (bitCount - 1 - bitfield.MSBit) * (spacing + buttonWidth);
					int y = 0;
					g.fillRect(x, y, width, height);
				}
				
				// draw the proposed new field
				if(firstBit >= 0 && lastBit >= 0) {
					g.setColor(tileSelectedColor);
					int MSBit = Integer.max(firstBit, lastBit);
					int LSBit = Integer.min(firstBit, lastBit);
					int width = padding + maxTextWidth + padding + (MSBit - LSBit) * (spacing + buttonWidth);
					int height = padding + textHeight + padding;
					int x = (bitCount - 1 - MSBit) * (spacing + buttonWidth);
					int y = 0;
					g.fillRect(x, y, width, height);
				}
				
				// draw each text label
				for(int i = 0; i < bitCount; i++) {
					g.setColor(Color.BLACK);
					for(Dataset.Bitfield bitfield : dataset.getBitfields())
						if((bitCount - 1 - i) >= bitfield.LSBit && (bitCount - 1 - i) <= bitfield.MSBit)
							g.setColor(Color.LIGHT_GRAY);
					int x = i * (spacing + buttonWidth) + padding;
					int y = padding + textHeight;
					String text = bitCount - 1 - i + "";
					x += (maxTextWidth - getFontMetrics(getFont()).stringWidth(text)) / 2; // adjust x to center the text
					g.drawString(text, x, y);
				}
				
			}
		
		}
		
	}
}
