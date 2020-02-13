import java.awt.Dimension;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

@SuppressWarnings("serial")
public class CommunicationView extends JPanel {
	
	JTextField sampleRateTextfield;
	JComboBox<String> packetTypeCombobox;
	JComboBox<String> portCombobox;
	JComboBox<String> baudRateCombobox;
	JComboBox<String> portNumberCombobox;
	JButton connectButton;

	/**
	 * Creates the lower-right corner toolbar with UART/TCP/UDP/Test controls and registers their event handlers.
	 */
	public CommunicationView () {
		
		super();
		setLayout(new BoxLayout(this, BoxLayout.X_AXIS));

		// sample rate
		sampleRateTextfield = new JTextField(Integer.toString(CommunicationController.getSampleRate()), 4);
		sampleRateTextfield.addFocusListener(new FocusListener() {
			@Override public void focusLost(FocusEvent fe) {
				try {
					CommunicationController.setSampleRate(Integer.parseInt(sampleRateTextfield.getText().trim()));
				} catch(Exception e) {
					sampleRateTextfield.setText(Integer.toString(CommunicationController.getSampleRate()));
				}
			}
			
			@Override public void focusGained(FocusEvent fe) {
				sampleRateTextfield.selectAll();
			}
		});
		CommunicationController.addSampleRateListener(newSampleRate -> sampleRateTextfield.setText(newSampleRate.toString()));
		
		// packet type
		packetTypeCombobox = new JComboBox<String>(CommunicationController.getPacketTypes());
		packetTypeCombobox.addActionListener(event -> CommunicationController.setPacketType(packetTypeCombobox.getSelectedItem().toString()));
		CommunicationController.addPacketTypeListener(newPacketType -> packetTypeCombobox.setSelectedItem(newPacketType));
		
		// port
		portCombobox = new JComboBox<String>(CommunicationController.getPorts());
		portCombobox.addActionListener(event -> {
			CommunicationController.setPort(portCombobox.getSelectedItem().toString());
		});
		CommunicationController.addPortListener(newPort -> {
			for(int i = 0; i < portCombobox.getItemCount(); i++)
				if(portCombobox.getItemAt(i).equals(newPort)) {
					portCombobox.setSelectedIndex(i);
					return;
				}
			// port is not in the list, so add it
			portCombobox.addItem(newPort);
			portCombobox.setSelectedItem(newPort);
		});
		
		// UART baud rate
		baudRateCombobox = new JComboBox<String>(CommunicationController.getBaudRateDefaults());
		baudRateCombobox.setMaximumRowCount(baudRateCombobox.getItemCount());
		baudRateCombobox.setEditable(true);
		baudRateCombobox.addActionListener(event -> {
			try {
				CommunicationController.setBaudRate(Integer.parseInt(baudRateCombobox.getSelectedItem().toString().trim()));
			} catch(Exception e) {
				String baudRate = Integer.toString(CommunicationController.getBaudRate());
				baudRateCombobox.setSelectedItem(baudRate);
			}
		});
		CommunicationController.addBaudRateListener(newBaudRate -> {
			String baudRate = Integer.toString(newBaudRate);
			baudRateCombobox.setSelectedItem(baudRate);
		});
		
		// TCP/UDP port number
		portNumberCombobox = new JComboBox<String>(CommunicationController.getPortNumberDefaults());
		portNumberCombobox.setMaximumRowCount(portNumberCombobox.getItemCount());
		portNumberCombobox.setEditable(true);
		portNumberCombobox.addActionListener(event -> {
			try {
				String portNumberString = portNumberCombobox.getSelectedItem().toString().trim();
				if(portNumberString.startsWith(":"))
					portNumberString = portNumberString.substring(1); // skip past the leading ":"
				CommunicationController.setPortNumber(Integer.parseInt(portNumberString));
			} catch(Exception e) {
				String portNumberString = ":" + CommunicationController.getPortNumber();
				portNumberCombobox.setSelectedItem(portNumberString);
			}
		});
		CommunicationController.addPortNumberListener(newPortNumber -> {
			String portNumberString = ":" + newPortNumber;
			portNumberCombobox.setSelectedItem(portNumberString);
		});
		
		// connect/disconnect
		connectButton = new JButton("Connect");
		connectButton.addActionListener(event -> {
			packetTypeCombobox.setEnabled(false);
			portCombobox.setEnabled(false);
			baudRateCombobox.setEnabled(false);
			portNumberCombobox.setEnabled(false);
			connectButton.setEnabled(false);
			
			if(connectButton.getText().equals("Connect")) {
				CommunicationController.connect(false);
			} else if(connectButton.getText().equals("Disconnect")) {
				CommunicationController.disconnect();
				Main.hideDataStructureGui();
			} else if(connectButton.getText().equals("Finish")) {
				CommunicationController.finishImportingFile();
				connectButton.setText("Finishing...");
			}
		});
		CommunicationController.addConnectionListener(connected -> {
			if(connected) {
				packetTypeCombobox.setEnabled(false);
				portCombobox.setEnabled(false);
				baudRateCombobox.setEnabled(false);
				portNumberCombobox.setEnabled(false);
				connectButton.setEnabled(true);
				if(CommunicationController.getPort() == Communication.PORT_FILE)
					connectButton.setText("Finish");
				else
					connectButton.setText("Disconnect");
			} else {
				packetTypeCombobox.setEnabled(true);
				portCombobox.setEnabled(true);
				baudRateCombobox.setEnabled(true);
				portNumberCombobox.setEnabled(true);
				connectButton.setEnabled(true);
				connectButton.setText("Connect");
			}
		});
		
		// hide or disable widgets based on the port type
		CommunicationController.addPortListener(newPort -> {
			if(newPort.startsWith(Communication.PORT_UART)) {
				baudRateCombobox.setVisible(true);
				portNumberCombobox.setVisible(false);
				sampleRateTextfield.setEditable(true);
				packetTypeCombobox.setVisible(true);
			} else if(newPort.equals(Communication.PORT_TEST)) {
				baudRateCombobox.setVisible(false);
				portNumberCombobox.setVisible(false);
				sampleRateTextfield.setText("10000");
				sampleRateTextfield.setEditable(false);
				packetTypeCombobox.setVisible(true);
			} else if(newPort.equals(Communication.PORT_FILE)) {
				baudRateCombobox.setVisible(false);
				portNumberCombobox.setVisible(false);
				sampleRateTextfield.setEditable(false);
				packetTypeCombobox.setVisible(false);
			} else {
				baudRateCombobox.setVisible(false);
				portNumberCombobox.setVisible(true);
				sampleRateTextfield.setEditable(true);
				packetTypeCombobox.setVisible(true);
			}
			
			// do not show "File" if we are not currently importing a file
			if(!newPort.equals(Communication.PORT_FILE)) {
				int index = -1;
				for(int i = 0; i < portCombobox.getItemCount(); i++)
					if(portCombobox.getItemAt(i).equals(Communication.PORT_FILE))
						index = i;
				if(index >= 0)
					portCombobox.removeItemAt(index);
			}
		});
		
		// show the components
		add(Box.createHorizontalStrut(5));
		add(new JLabel("Sample Rate (Hz)"));
		add(Box.createHorizontalStrut(5));
		add(sampleRateTextfield);
		add(Box.createHorizontalStrut(5));
		add(packetTypeCombobox);
		add(Box.createHorizontalStrut(5));
		add(portCombobox);
		add(Box.createHorizontalStrut(5));
		add(baudRateCombobox);
		add(portNumberCombobox);
		add(Box.createHorizontalStrut(5));
		add(connectButton);

	}
	
	@Override public Dimension getPreferredSize() {
		
		sampleRateTextfield.setMinimumSize(sampleRateTextfield.getPreferredSize());
		sampleRateTextfield.setMaximumSize(sampleRateTextfield.getPreferredSize());
		
		packetTypeCombobox.setMinimumSize(packetTypeCombobox.getPreferredSize());
		packetTypeCombobox.setMaximumSize(packetTypeCombobox.getPreferredSize());
		
		portCombobox.setMaximumSize(portCombobox.getPreferredSize());
		
		baudRateCombobox.setPreferredSize(null);
		Dimension d = baudRateCombobox.getPreferredSize();
		d.width = (int) (d.width * 0.7);
		baudRateCombobox.setPreferredSize(d);
		baudRateCombobox.setMinimumSize(baudRateCombobox.getPreferredSize());
		baudRateCombobox.setMaximumSize(baudRateCombobox.getPreferredSize());
		
		portNumberCombobox.setPreferredSize(baudRateCombobox.getPreferredSize()); // force same size as baudRateCombobox
		portNumberCombobox.setMinimumSize(baudRateCombobox.getPreferredSize());
		portNumberCombobox.setMaximumSize(baudRateCombobox.getPreferredSize());
		
		revalidate();
		return super.getPreferredSize();
		
	}
	
	@Override public Dimension getMinimumSize() {
		
		return getPreferredSize();
		
	}
	
}
