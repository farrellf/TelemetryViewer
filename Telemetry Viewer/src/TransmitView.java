import java.awt.Insets;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;

import net.miginfocom.swing.MigLayout;

@SuppressWarnings("serial")
public class TransmitView extends JPanel {
	
	TransmitController controller;
	
	JComboBox<String> typeCombobox;
	JTextField        dataTextfield;
	JCheckBox         appendCRcheckbox;
	JCheckBox         appendLFcheckbox;
	JCheckBox         repeatCheckbox;
	JTextField        repeatMillisecondsTextfield;
	JButton           saveButton;
	JButton           transmitButton;
	List<JButton>     savedPacketButtons;
	
	/**
	 * A JPanel of widgets that lets the user transmit data.
	 * Data may be entered in text, hex or binary form.
	 * Packets may be saved, appearing as buttons that can be clicked to transmit.
	 */
	public TransmitView(TransmitController controller) {
		
		this.controller = controller;
		
		setLayout(new MigLayout("hidemode 3, fillx, wrap 2, insets " + Theme.padding + ", gap " + Theme.padding));
		
		// which data format the user will provide
		typeCombobox = new JComboBox<String>(new String[] {"Text", "Hex", "Bin"});
		typeCombobox.setSelectedItem(controller.getTransmitType());
		typeCombobox.addActionListener(event -> controller.setTransmitType(typeCombobox.getSelectedItem().toString()));
		
		// data provided by the user
		dataTextfield = new JTextField(controller.getTransmitText(), 10);
		dataTextfield.addKeyListener(new KeyListener() {
			@Override public void keyReleased(KeyEvent e) { controller.setTransmitText(dataTextfield.getText(), e); }
			@Override public void keyTyped(KeyEvent e)    { }
			@Override public void keyPressed(KeyEvent e)  { }
		});
		dataTextfield.addFocusListener(new FocusListener() {
			@Override public void focusLost(FocusEvent e)   { controller.setTransmitText(dataTextfield.getText(), null); }
			@Override public void focusGained(FocusEvent e) { }
		});
		dataTextfield.addActionListener(event -> { controller.setTransmitText(dataTextfield.getText(), null); transmitButton.doClick(); });
		
		// for text mode, \r or \n can be automatically appended
		appendCRcheckbox = new JCheckBox("CR", controller.getAppendCR());
		appendCRcheckbox.addActionListener(event -> controller.setAppendCR(appendCRcheckbox.isSelected()));
		appendLFcheckbox = new JCheckBox("LF", controller.getAppendLF());
		appendLFcheckbox.addActionListener(event -> controller.setAppendLF(appendLFcheckbox.isSelected()));
		
		// transmitted data can be automatically repeated every n milliseconds
		repeatCheckbox = new JCheckBox("Repeat", controller.getRepeats());
		repeatCheckbox.addActionListener(event -> controller.setRepeats(repeatCheckbox.isSelected()));
		repeatMillisecondsTextfield = new JTextField(controller.getRepititionInterval() + " ms", 7);
		ActionListener millisecondsHandler = event -> {
			try {
				String text = repeatMillisecondsTextfield.getText().trim();
				if(text.endsWith("ms"))
					text = text.substring(0, text.length() - 2).trim();
				int milliseconds = Integer.parseInt(text);
				controller.setRepititionInterval(milliseconds);
			} catch(Exception e) {
				controller.setRepititionInterval(0);
			}
		};
		repeatMillisecondsTextfield.addFocusListener(new FocusListener() {
			@Override public void focusLost(FocusEvent fe)   { millisecondsHandler.actionPerformed(null); }
			@Override public void focusGained(FocusEvent fe) { repeatMillisecondsTextfield.selectAll();   }
		});
		repeatMillisecondsTextfield.addActionListener(millisecondsHandler);
		
		// packets can be saved to JButtons
		savedPacketButtons = new ArrayList<JButton>();
		saveButton = new JButton("Save");
		saveButton.addActionListener(event -> {
			TransmitController.SavedPacket data = new TransmitController.SavedPacket();
			String mode = controller.getTransmitType();
			boolean textMode = mode.equals("Text");
			boolean hexMode  = mode.equals("Hex");
			data.bytes = textMode ? ChartUtils.convertTextStringToBytes(controller.getTransmitText(), controller.getAppendLF(), controller.getAppendCR()) :
			             hexMode  ? ChartUtils.convertHexStringToBytes(controller.getTransmitText())  :
			                        ChartUtils.convertBinStringToBytes(controller.getTransmitText());
			data.label = textMode ? "Text: " + ChartUtils.convertBytesToTextString(data.bytes, true) :
			             hexMode  ? "Hex: "  + ChartUtils.convertBytesToHexString(data.bytes) :
			                        "Bin: "  + ChartUtils.convertBytesToBinString(data.bytes);
			controller.savePacket(data);
			controller.setTransmitText("", null);
		});
		
		// transmit button for sending the data once
		transmitButton = new JButton("Transmit");
		transmitButton.addActionListener(event -> {
			String mode = controller.getTransmitType();
			boolean textMode = mode.equals("Text");
			boolean hexMode  = mode.equals("Hex");
			byte[] bytes = textMode ? ChartUtils.convertTextStringToBytes(controller.getTransmitText(), controller.getAppendLF(), controller.getAppendCR()) :
			               hexMode  ? ChartUtils.convertHexStringToBytes(controller.getTransmitText()) :
			                          ChartUtils.convertBinStringToBytes(controller.getTransmitText());
			controller.connection.transmit(bytes);
		});
		
		add(typeCombobox, "grow x");
		add(dataTextfield, "grow x");
		add(appendCRcheckbox, "span 2, split 4");
		add(appendLFcheckbox);
		add(repeatCheckbox);
		add(repeatMillisecondsTextfield, "grow x");
		add(saveButton, "span 2, split 2, grow x");
		add(transmitButton, "grow x");
		
	}
	
	/**
	 * Redraws the list of saved packets.
	 * 
	 * @param savedPackets    The current list of saved packets.
	 */
	public void redrawSavedPackets(List<TransmitController.SavedPacket> savedPackets) {
		
		for(JButton button : savedPacketButtons)
			remove(button);
		savedPacketButtons.clear();
		
		JToggleButton temp = new JToggleButton("_");
		Insets insets = temp.getBorder().getBorderInsets(temp);
		Border narrowBorder = new EmptyBorder(insets.top, Integer.max(insets.top, insets.bottom), insets.bottom, Integer.max(insets.top, insets.bottom));
		
		for(TransmitController.SavedPacket packet : savedPackets) {
			JButton sendButton = new JButton();
			sendButton.setText(packet.label);
			sendButton.setHorizontalAlignment(SwingConstants.LEFT);
			sendButton.addActionListener(clicked -> controller.connection.transmit(packet.bytes));
			JButton removeButton = new JButton(Theme.removeSymbol);
			removeButton.setBorder(narrowBorder);
			removeButton.addActionListener(click -> controller.removePacket(packet));
			savedPacketButtons.add(sendButton);
			savedPacketButtons.add(removeButton);
			if(controller.connection.packetType == ConnectionTelemetry.PacketType.TC66) {
				add(sendButton, "span 2, grow x");
			} else {
				add(sendButton, "span 2, split 2, grow x, width 1:1:"); // setting min/pref width to 1px to ensure this button doesn't widen the panel
				add(removeButton);
			}
		}
		
		revalidate();
		repaint();
		
	}

}
