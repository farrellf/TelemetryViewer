import java.awt.Color;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicLong;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;

import com.fazecast.jSerialComm.SerialPort;
import net.miginfocom.swing.MigLayout;

public class ConnectionTelemetry extends Connection {
	
	static List<String> names = new ArrayList<String>();
	static {
		for(SerialPort port : SerialPort.getCommPorts())
			names.add("UART: " + port.getSystemPortName());
		Collections.sort(names);
		
		names.add("TCP");
		names.add("UDP");
		names.add("Demo Mode");
		names.add("Stress Test Mode");
	}
	
	// reminder: fields are shared with multiple threads, so they must be final or volatile or atomic.
	public final DatasetsController datasets = new DatasetsController(this); // used to store "normal telemetry"
	
	public enum Mode {UART, TCP, UDP, DEMO, STRESS_TEST};
	public volatile Mode mode = Mode.UART;
	
	public volatile int sampleRate = 1000;
	public volatile boolean csvMode = true;
	public volatile int baudRate = 9600; // for UART mode
	public volatile int portNumber = 8080; // for TCP/UDP modes
	
	public volatile boolean dataStructureDefined = false;
	
	private final int MAX_UDP_PACKET_SIZE = 65507; // 65535 - (8byte UDP header) - (20byte IP header)
	private final int MAX_TCP_IDLE_MILLISECONDS = 10000; // if connected but no new samples after than much time, disconnect and wait for a new connection
	
	public volatile static String localIp = "[Local IP Address Unknown]";
	static {
		try { 
			String ips = "";
			Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
			while(interfaces.hasMoreElements()) {
				NetworkInterface ni = interfaces.nextElement();
				Enumeration<InetAddress> addresses = ni.getInetAddresses();
				while(addresses.hasMoreElements()) {
					InetAddress address = addresses.nextElement();
					if(address.isSiteLocalAddress() && !ni.getDisplayName().contains("VMware") && !ni.getDisplayName().contains("VPN"))
						ips += address.getHostAddress() + " or ";
				}
			}
			if(ips.length() > 0)
				localIp = ips.substring(0, ips.length() - 4);
		} catch(Exception e) {}
	}
	
	/**
	 * Prepares, but does not connect to, a connection that can receive "normal telemetry" (a stream of numbers to visualize.)
	 */
	public ConnectionTelemetry() {
		
		name = names.get(0);
		
		// ensure this connection doesn't already exist, but allow multiple TCP/UDP/Demo Mode connections
		for(int i = 1; i < names.size(); i++)
			for(Connection connection : ConnectionsController.allConnections)
				if(name.equals(connection.name) && !name.equals("TCP") && !name.equals("UDP") && !name.equals("Demo Mode"))
					name = getNames().get(i);
		
		if(name.startsWith("UART"))
			mode = Mode.UART;
		else if(name.equals("TCP"))
			mode = Mode.TCP;
		else if(name.equals("UDP"))
			mode = Mode.UDP;
		else if(name.equals("Demo Mode"))
			mode = Mode.DEMO;
		else
			mode = Mode.STRESS_TEST;
		
		// determine the sample rate and CSV/binary mode
		if(mode == Mode.UART || mode == Mode.TCP || mode == Mode.UDP) {
			sampleRate = 1000;
			csvMode = true;
		} else if(mode == Mode.DEMO) {
			sampleRate = 10000;
			csvMode = true;
		} else {
			sampleRate = Integer.MAX_VALUE;
			csvMode = false;
		}
		
	}
	
	/**
	 * Prepares, but does not connect to, a connection that can receive "normal telemetry" (a stream of numbers to visualize.)
	 */
	public ConnectionTelemetry(String name) {
		
		// determine the connection name and mode
		this.name = name;
		if(name.startsWith("UART"))
			mode = Mode.UART;
		else if(name.equals("TCP"))
			mode = Mode.TCP;
		else if(name.equals("UDP"))
			mode = Mode.UDP;
		else if(name.equals("Demo Mode"))
			mode = Mode.DEMO;
		else
			mode = Mode.STRESS_TEST;
		
		// determine the sample rate and CSV/binary mode
		if(mode == Mode.UART || mode == Mode.TCP || mode == Mode.UDP) {
			sampleRate = 1000;
			csvMode = true;
		} else if(mode == Mode.DEMO) {
			sampleRate = 10000;
			csvMode = true;
		} else {
			sampleRate = Integer.MAX_VALUE;
			csvMode = false;
		}
		
	}
	
	/**
	 * @return List of possible telemetry connections to show the user.
	 */
	public static List<String> getNames() {

		return names;
		
	}

	/**
	 * @return    A GUI for controlling this Connection.
	 */
	@Override public JPanel getGui() {

		JPanel panel = new JPanel();
		panel.setLayout(new MigLayout("hidemode 3, gap " + Theme.padding  + ", insets 0 " + Theme.padding + " 0 0"));
			
		// sample rate
		JTextField sampleRateTextfield = new JTextField(sampleRate == Integer.MAX_VALUE ? "maximum Hz" : Integer.toString(sampleRate) + " Hz", 10);
		sampleRateTextfield.setToolTipText("<html>Sample rate, in Hertz.<br>(The number of telemetry packets that will be sent to the PC each second.)<br>If this number is inaccurate, things like the frequency domain chart will be inaccurate.</html>");
		sampleRateTextfield.setMinimumSize(sampleRateTextfield.getPreferredSize());
		sampleRateTextfield.addFocusListener(new FocusListener() {
			@Override public void focusLost(FocusEvent fe) {
				try {
					String text = sampleRateTextfield.getText().trim();
					if(text.endsWith("Hz"))
						text = text.substring(0, text.length() - 2).trim();
					int rate = Integer.parseInt(text);
					if(rate > 0 && rate != sampleRate) {
						sampleRate = rate;
						sampleRateTextfield.setText(rate + " Hz");
					} else if (rate <= 0)
						throw new Exception();
				} catch(Exception e) {
					sampleRateTextfield.setText(sampleRate + " Hz");
				}
				CommunicationView.instance.redraw();
			}
			@Override public void focusGained(FocusEvent fe) {
				sampleRateTextfield.selectAll();
			}
		});
		
		// CSV or binary mode
		JComboBox<String> packetTypeCombobox = new JComboBox<String>(new String[] {"CSV Mode", "Binary Mode"});
		packetTypeCombobox.setMinimumSize(packetTypeCombobox.getPreferredSize());
		if(!csvMode)
			packetTypeCombobox.setSelectedIndex(1);
		packetTypeCombobox.addActionListener(event -> {
			boolean changing = (csvMode && packetTypeCombobox.getSelectedIndex() == 1) || (!csvMode && packetTypeCombobox.getSelectedIndex() == 0);
			if(changing)
				datasets.removeAll();
			csvMode = (packetTypeCombobox.getSelectedIndex() == 0);
			CommunicationView.instance.redraw();
		});
		
		// connections list
		JComboBox<String> connectionNamesCombobox = new JComboBox<String>();
		for(String name : ConnectionsController.getNames())
			connectionNamesCombobox.addItem(name);
		connectionNamesCombobox.setMaximumRowCount(connectionNamesCombobox.getItemCount());
		connectionNamesCombobox.setSelectedItem(name);
		if(!connectionNamesCombobox.getSelectedItem().equals(name)) {
			connectionNamesCombobox.addItem(name);
			connectionNamesCombobox.setSelectedItem(name);
		}
		if(ConnectionsController.importing) {
			String importName = "Importing [" + name + "]";
			connectionNamesCombobox.addItem(importName);
			connectionNamesCombobox.setSelectedItem(importName);
		}
		connectionNamesCombobox.setMinimumSize(connectionNamesCombobox.getPreferredSize());
		connectionNamesCombobox.addActionListener(event -> {
			
			String newConnectionName = connectionNamesCombobox.getSelectedItem().toString();
			if(newConnectionName.equals(name))
				return;
			
			// ignore change if the connection already exists, but allow multiple TCP/UDP/Demo Mode/MJPEG over HTTP connections
			for(Connection connection : ConnectionsController.allConnections)
				if(connection.name.equals(newConnectionName) && !newConnectionName.equals("TCP") && !newConnectionName.equals("UDP") && !newConnectionName.equals("Demo Mode") && !newConnectionName.equals("MJPEG over HTTP")) {
					connectionNamesCombobox.setSelectedItem(name);
					return;
				}
			
			// change connection types if needed
			if(!getNames().contains(newConnectionName)) {
				ConnectionsController.replaceConnection(ConnectionTelemetry.this, new ConnectionCamera(newConnectionName));
				return;
			}

			// if leaving demo mode or stress test mode, reset the data structure
			Mode oldMode = mode;
			if(oldMode == Mode.DEMO || oldMode == Mode.STRESS_TEST)
				datasets.removeAll();
			
			// change to the new connection
			name = newConnectionName;
			if(name.startsWith("UART")) {
				mode = Mode.UART;
				if(oldMode == Mode.DEMO || oldMode == Mode.STRESS_TEST) {
					sampleRate = 1000;
					csvMode = true;
				}
			} else if(name.equals("TCP")) {
				mode = Mode.TCP;
				if(oldMode == Mode.DEMO || oldMode == Mode.STRESS_TEST) {
					sampleRate = 1000;
					csvMode = true;
				}
			} else if(name.equals("UDP")) {
				mode = Mode.UDP;
				if(oldMode == Mode.DEMO || oldMode == Mode.STRESS_TEST) {
					sampleRate = 1000;
					csvMode = true;
				}
			} else if(name.equals("Demo Mode")) {
				mode = Mode.DEMO;
				sampleRate = 10000;
				csvMode = true;
			} else {
				mode = Mode.STRESS_TEST;
				sampleRate = Integer.MAX_VALUE;
				csvMode = false;
			}

			CommunicationView.instance.redraw();
			
		});
		
		// baud rate (only used in UART mode)
		JComboBox<String> baudRateCombobox = new JComboBox<String>(new String[] {"9600 Baud", "19200 Baud", "38400 Baud", "57600 Baud", "115200 Baud", "230400 Baud", "460800 Baud", "921600 Baud", "1000000 Baud", "2000000 Baud"});
		baudRateCombobox.setMaximumRowCount(baudRateCombobox.getItemCount() + 1);
		baudRateCombobox.setMinimumSize(baudRateCombobox.getPreferredSize());
		baudRateCombobox.setMaximumSize(baudRateCombobox.getPreferredSize());
		baudRateCombobox.setEditable(true);
		baudRateCombobox.setSelectedItem(Integer.toString(baudRate) + " Baud");
		baudRateCombobox.addActionListener(event -> {
			try {
				String text = baudRateCombobox.getSelectedItem().toString().trim();
				if(text.endsWith("Baud"))
					text = text.substring(0, text.length() - 4).trim();
				int rate = Integer.parseInt(text);
				if(rate > 0 && rate != baudRate) {
					baudRate = rate;
					baudRateCombobox.setSelectedItem(rate + " Baud");
				} else if(rate <= 0)
					throw new Exception();
			} catch(Exception e) {
				baudRateCombobox.setSelectedItem(baudRate + " Baud");
			}
			CommunicationView.instance.redraw();
		});
		baudRateCombobox.getEditor().getEditorComponent().addFocusListener(new FocusListener() {
			@Override public void focusGained(FocusEvent e) { baudRateCombobox.getEditor().selectAll(); }
			@Override public void focusLost(FocusEvent e) { }
		});
		
		// port number (only used in TCP/UDP modes)
		JComboBox<String> portNumberCombobox = new JComboBox<String>(new String[] {"Port 8080"});
		portNumberCombobox.setMaximumRowCount(portNumberCombobox.getItemCount());
		portNumberCombobox.setMinimumSize(portNumberCombobox.getPreferredSize());
		portNumberCombobox.setMaximumSize(portNumberCombobox.getPreferredSize());
		portNumberCombobox.setEditable(true);
		portNumberCombobox.setSelectedItem("Port " + portNumber);
		portNumberCombobox.addActionListener(event -> {
			try {
				String newPortNumber = portNumberCombobox.getSelectedItem().toString().trim();
				if(newPortNumber.startsWith("Port"))
					newPortNumber = newPortNumber.substring(4).trim(); // skip past the leading "Port"
				int number = Integer.parseInt(newPortNumber);
				if(number >= 0 && number <= 65535 && number != portNumber) {
					portNumber = number;
					portNumberCombobox.setSelectedItem("Port " + number);
				} else if(number < 0 || number > 65535)
					throw new Exception();
			} catch(Exception e) {
				portNumberCombobox.setSelectedItem("Port " + portNumber);
			}
			CommunicationView.instance.redraw();
		});
		portNumberCombobox.getEditor().getEditorComponent().addFocusListener(new FocusListener() {
			@Override public void focusGained(FocusEvent e) { portNumberCombobox.getEditor().selectAll(); }
			@Override public void focusLost(FocusEvent e) { }
		});
		
		// connect/disconnect button
		@SuppressWarnings("serial")
		JButton connectButton = new JButton("Connect") {
			@Override public Dimension getPreferredSize() { // giving this button a fixed size so the GUI lines up nicely
				return new JButton("Disconnect").getPreferredSize();
			}
		};
		if(connected)
			connectButton.setText("Disconnect");
		if(ConnectionsController.importing && ConnectionsController.realtimeImporting)
			connectButton.setText("Finish");
		if(ConnectionsController.importing && !ConnectionsController.realtimeImporting)
			connectButton.setText("Abort");
		
		connectButton.addActionListener(event -> {
			if(connectButton.getText().equals("Connect"))
				connect(true);
			else if(connectButton.getText().equals("Disconnect"))
				disconnect(null);
			else if(connectButton.getText().equals("Finish") || connectButton.getText().equals("Abort"))
				finishImportingFile();
		});
		if(ConnectionsController.importing && ConnectionsController.allConnections.size() > 1)
			connectButton.setVisible(false);
		
		// remove connection button
		JToggleButton temp = new JToggleButton("_");
		Insets insets = temp.getBorder().getBorderInsets(temp);
		Border narrowBorder = new EmptyBorder(insets.top, Integer.max(insets.top, insets.bottom), insets.bottom, Integer.max(insets.top, insets.bottom));
		
		JButton removeButton = new JButton("\uD83D\uDDD9");
		removeButton.setBorder(narrowBorder);
		removeButton.addActionListener(event -> {
			ConnectionsController.removeConnection(ConnectionTelemetry.this);
			CommunicationView.instance.redraw();
		});
		if(ConnectionsController.allConnections.size() < 2 || ConnectionsController.importing)
			removeButton.setVisible(false);
		
		// populate the panel
		if(mode == Mode.UART) {
			panel.add(sampleRateTextfield);
			panel.add(packetTypeCombobox);
			panel.add(baudRateCombobox);
			panel.add(connectionNamesCombobox);
			panel.add(connectButton);
			panel.add(removeButton);
		} else if(mode == Mode.TCP || mode == Mode.UDP) {
			panel.add(sampleRateTextfield);
			panel.add(packetTypeCombobox);
			panel.add(portNumberCombobox);
			panel.add(connectionNamesCombobox);
			panel.add(connectButton);
			panel.add(removeButton);
		} else {
			panel.add(new JLabel(" ")); // dummy to fill row of 6 items
			panel.add(sampleRateTextfield);
			panel.add(packetTypeCombobox);
			panel.add(connectionNamesCombobox);
			panel.add(connectButton);
			panel.add(removeButton);
		}
		
		// disable widgets if appropriate
		sampleRateTextfield.setEnabled(!ConnectionsController.importing && !connected && mode != Mode.DEMO && mode != Mode.STRESS_TEST);
		packetTypeCombobox.setEnabled(!ConnectionsController.importing && !connected && mode != Mode.DEMO && mode != Mode.STRESS_TEST);
		connectionNamesCombobox.setEnabled(!ConnectionsController.importing && !connected);
		baudRateCombobox.setEnabled(!ConnectionsController.importing && !connected);
		portNumberCombobox.setEnabled(!ConnectionsController.importing && !connected);
		connectButton.setEnabled(!ConnectionsController.exporting);
		
		return panel;
		
	}

	@Override public void connect(boolean showGui) {

		if(connected)
			disconnect(null);
		
		NotificationsController.removeIfConnectionRelated();
		
		if(ConnectionsController.previouslyImported) {
			for(Connection connection : ConnectionsController.allConnections)
				connection.removeAllData();
			ConnectionsController.previouslyImported = false;
		}
		
		if(showGui) {
			dataStructureDefined = false;
			CommunicationView.instance.redraw();	
		}
		
		if(mode == Mode.UART)
			connectUart(showGui);
		else if(mode == Mode.TCP)
			connectTcp(showGui);
		else if(mode == Mode.UDP)
			connectUdp(showGui);
		else if(mode == Mode.DEMO)
			connectDemo(showGui);
		else
			connectStressTest(showGui);

	}
	
	private void connectUart(boolean showGui) {
		
		SerialPort uartPort = SerialPort.getCommPort(name.substring(6)); // trim the leading "UART: "
		uartPort.setBaudRate(baudRate);
		uartPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, 0, 0);
		
		// try 3 times before giving up, because some Bluetooth UARTs have trouble connecting
		if(!uartPort.openPort() && !uartPort.openPort() && !uartPort.openPort()) {
			SwingUtilities.invokeLater(() -> disconnect("Unable to connect to " + name.substring(6) + "."));
			return;
		}
		
		connected = true;
		CommunicationView.instance.redraw();
		
		if(showGui)
			Main.showConfigurationGui(csvMode ? new DataStructureCsvView(this) : new DataStructureBinaryView(this));
		
		receiverThread = new Thread(() -> {
			
			InputStream uart = uartPort.getInputStream();
			SharedByteStream stream = new SharedByteStream(ConnectionTelemetry.this);
			startProcessingTelemetry(stream);
			
			// listen for packets
			while(true) {

				try {
					
					if(Thread.interrupted() || !connected)
						throw new InterruptedException();
					
					int length = uart.available();
					if(length < 1) {
						Thread.sleep(1);
					} else {
						byte[] buffer = new byte[length];
						length = uart.read(buffer, 0, length);
						if(length < 0)
							throw new IOException();
						else
							stream.write(buffer, length);
					}
					
				} catch(IOException ioe) {
					
					// an IOException can occur if an InterruptedException occurs while receiving data
					// let this be detected by the connection test in the loop
					if(!connected)
						continue;
					
					// problem while reading from the UART
					stopProcessingTelemetry();
					uartPort.closePort();
					SwingUtilities.invokeLater(() -> disconnect("Error while reading from the UART."));
					return;
					
				}  catch(InterruptedException ie) {
					
					stopProcessingTelemetry();
					uartPort.closePort();
					return;
					
				}
			
			}
			
		});
		
		receiverThread.setPriority(Thread.MAX_PRIORITY);
		receiverThread.setName("UART Receiver Thread");
		receiverThread.start();
		
	}
	
	private void connectTcp(boolean showGui) {
		
		receiverThread = new Thread(() -> {
			
			ServerSocket tcpServer = null;
			Socket tcpSocket = null;
			SharedByteStream stream = new SharedByteStream(ConnectionTelemetry.this);
			
			// start the TCP server
			try {
				tcpServer = new ServerSocket(portNumber);
				tcpServer.setSoTimeout(1000);
			} catch (Exception e) {
				try { tcpServer.close(); } catch(Exception e2) {}
				SwingUtilities.invokeLater(() -> disconnect("Unable to start the TCP server. Make sure another program is not already using port " + portNumber + "."));
				return;
			}
			
			connected = true;
			CommunicationView.instance.redraw();
			
			if(showGui)
				Main.showConfigurationGui(csvMode ? new DataStructureCsvView(this) : new DataStructureBinaryView(this));
			
			startProcessingTelemetry(stream);
			
			// listen for a connection
			while(true) {

				try {
					
					if(Thread.interrupted() || !connected)
						throw new InterruptedException();
					
					tcpSocket = tcpServer.accept();
					tcpSocket.setSoTimeout(5000); // each valid packet of data must take <5 seconds to arrive
					InputStream is = tcpSocket.getInputStream();

					NotificationsController.showVerboseForMilliseconds("TCP connection established with a client at " + tcpSocket.getRemoteSocketAddress().toString().substring(1) + ".", 5000, true); // trim leading "/" from the IP address
					
					// enter an infinite loop that checks for activity. if the TCP port is idle for >10 seconds, abandon it so another device can try to connect.
					long previousTimestamp = System.currentTimeMillis();
					int previousSampleNumber = getSampleCount();
					while(true) {
						int byteCount = is.available();
						if(byteCount > 0) {
							byte[] buffer = new byte[byteCount];
							is.read(buffer, 0, byteCount);
							stream.write(buffer, byteCount);
							continue;
						}
						Thread.sleep(1);
						int sampleNumber = getSampleCount();
						long timestamp = System.currentTimeMillis();
						if(sampleNumber > previousSampleNumber) {
							previousSampleNumber = sampleNumber;
							previousTimestamp = timestamp;
						} else if(previousTimestamp < timestamp - MAX_TCP_IDLE_MILLISECONDS) {
							NotificationsController.showFailureForMilliseconds("The TCP connection was idle for too long. It has been closed so another device can connect.", 5000, true);
							tcpSocket.close();
							break;
						}
					}
					
				} catch(SocketTimeoutException ste) {
					
					// a client never connected, so do nothing and let the loop try again.
					NotificationsController.showVerboseForMilliseconds("TCP socket timed out while waiting for a connection.", 5000, true);
					
				} catch(IOException ioe) {
					
					// an IOException can occur if an InterruptedException occurs while receiving data
					// let this be detected by the connection test in the loop
					if(!connected)
						continue;
					
					// problem while accepting the socket connection, or getting the input stream, or reading from the input stream
					stopProcessingTelemetry();
					try { tcpSocket.close(); } catch(Exception e2) {}
					try { tcpServer.close(); } catch(Exception e2) {}
					SwingUtilities.invokeLater(() -> disconnect("TCP connection failed."));
					return;
					
				}  catch(InterruptedException ie) {
					
					stopProcessingTelemetry();
					try { tcpSocket.close(); } catch(Exception e2) {}
					try { tcpServer.close(); } catch(Exception e2) {}
					return;
					
				}
			
			}
			
		});
		
		receiverThread.setPriority(Thread.MAX_PRIORITY);
		receiverThread.setName("TCP Server");
		receiverThread.start();
		
	}
	
	private void connectUdp(boolean showGui) {
		
		receiverThread = new Thread(() -> {
			
			DatagramSocket udpListener = null;
			SharedByteStream stream = new SharedByteStream(ConnectionTelemetry.this);
			
			// start the UDP listener
			try {
				udpListener = new DatagramSocket(portNumber);
				udpListener.setSoTimeout(1000);
				udpListener.setReceiveBufferSize(67108864); // 64MB
			} catch (Exception e) {
				try { udpListener.close(); } catch(Exception e2) {}
				SwingUtilities.invokeLater(() -> disconnect("Unable to start the UDP listener. Make sure another program is not already using port " + portNumber + "."));
				return;
			}
			
			connected = true;
			CommunicationView.instance.redraw();
			
			if(showGui)
				Main.showConfigurationGui(csvMode ? new DataStructureCsvView(this) : new DataStructureBinaryView(this));
			
			startProcessingTelemetry(stream);
			
			// listen for packets
			byte[] buffer = new byte[MAX_UDP_PACKET_SIZE];
			DatagramPacket udpPacket = new DatagramPacket(buffer, buffer.length);
			while(true) {

				try {
					
					if(Thread.interrupted() || !connected)
						throw new InterruptedException();
					
					udpListener.receive(udpPacket);
					stream.write(buffer, udpPacket.getLength());
					
				} catch(SocketTimeoutException ste) {
					
					// a client never sent a packet, so do nothing and let the loop try again.
					NotificationsController.showDebugMessage("UDP socket timed out while waiting for a packet.");
					
				} catch(IOException ioe) {
					
					// an IOException can occur if an InterruptedException occurs while receiving data
					// let this be detected by the connection test in the loop
					if(!connected)
						continue;
					
					// problem while reading from the socket
					stopProcessingTelemetry();
					try { udpListener.close(); }   catch(Exception e) {}
					SwingUtilities.invokeLater(() -> disconnect("UDP packet error."));
					return;
					
				}  catch(InterruptedException ie) {
					
					stopProcessingTelemetry();
					try { udpListener.close(); }   catch(Exception e) {}
					return;
					
				}
			
			}
			
		});
		
		receiverThread.setPriority(Thread.MAX_PRIORITY);
		receiverThread.setName("UDP Listener Thread");
		receiverThread.start();
		
	}
	
	private void connectDemo(boolean showGui) {
		
		// define the data structure if it is not already defined
		if(datasets.getCount() != 4 ||
		   !datasets.getByIndex(0).name.equals("Low Quality Noise") ||
		   !datasets.getByIndex(1).name.equals("Noisey Sine Wave 100-500Hz") ||
		   !datasets.getByIndex(2).name.equals("Intermittent Sawtooth Wave 100Hz") ||
		   !datasets.getByIndex(3).name.equals("Clean Sine Wave 1kHz")) {
			
			datasets.removeAll();
			datasets.insert(0, null, "Low Quality Noise",                Color.RED,   "Volts", 1, 1);
			datasets.insert(1, null, "Noisey Sine Wave 100-500Hz",       Color.GREEN, "Volts", 1, 1);
			datasets.insert(2, null, "Intermittent Sawtooth Wave 100Hz", Color.BLUE,  "Volts", 1, 1);
			datasets.insert(3, null, "Clean Sine Wave 1kHz",             Color.CYAN,  "Volts", 1, 1);
		}
		
		connected = true;
		CommunicationView.instance.redraw();
		
		if(showGui)
			Main.showConfigurationGui(csvMode ? new DataStructureCsvView(this) : new DataStructureBinaryView(this));

		// simulate the transmission of a telemetry packet every 100us.
		receiverThread = new Thread(() -> {
			
			long startTime = System.currentTimeMillis();
			int startSampleNumber = getSampleCount();
			int sampleNumber = startSampleNumber;
			
			double oscillatingFrequency = 100; // Hz
			boolean oscillatingHigher = true;
			int samplesForCurrentFrequency = (int) Math.round(1.0 / oscillatingFrequency * 10000.0);
			int currentFrequencySampleCount = 0;
			
			while(true) {
				float scalar = ((System.currentTimeMillis() % 30000) - 15000) / 100.0f;
				float lowQualityNoise = (System.nanoTime() / 100 % 100) * scalar * 1.0f / 14000f;
				for(int i = 0; i < 10; i++) {
					datasets.getByIndex(0).setSample(sampleNumber, lowQualityNoise);
					datasets.getByIndex(1).setSample(sampleNumber, (float) (Math.sin(2 * Math.PI * oscillatingFrequency * currentFrequencySampleCount / 10000.0) + 0.07*(Math.random()-0.5)));
					datasets.getByIndex(2).setSample(sampleNumber, (sampleNumber % 10000 < 1000) ? (sampleNumber % 100) / 100f : 0);
					datasets.getByIndex(3).setSample(sampleNumber, (float) Math.sin(2 * Math.PI * 1000 * sampleNumber / 10000.0));
					
					sampleNumber++;
					datasets.incrementSampleCount();

					currentFrequencySampleCount++;
					if(currentFrequencySampleCount == samplesForCurrentFrequency) {
						if(oscillatingFrequency >= 500)
							oscillatingHigher = false;
						else if(oscillatingFrequency <= 100)
							oscillatingHigher = true;
						oscillatingFrequency *= oscillatingHigher ? 1.005 : 0.995;
						samplesForCurrentFrequency = (int) Math.round(1.0 / oscillatingFrequency * 10000.0);
						currentFrequencySampleCount = 0;
					}
				}
				
				try {
					long actualMilliseconds = System.currentTimeMillis() - startTime;
					long expectedMilliseconds = Math.round((sampleNumber - startSampleNumber) / 10.0);
					long sleepMilliseconds = expectedMilliseconds - actualMilliseconds;
					if(sleepMilliseconds >= 1)
						Thread.sleep(sleepMilliseconds);
				} catch(InterruptedException e) {
					return;
				}
			}
			
		});
		
		receiverThread.setPriority(Thread.MAX_PRIORITY);
		receiverThread.setName("Demo Waveform Simulator Thread");
		receiverThread.start();
		
	}
	
	private void connectStressTest(boolean showGui) {
		
		datasets.removeAll();
		
		SettingsController.setTileColumns(6);
		SettingsController.setTileRows(6);
		SettingsController.setTimeFormat("Only Time");
		SettingsController.setTimeFormat24hours(false);
		SettingsController.setHintNotificationVisibility(true);
		SettingsController.setHintNotificationColor(Color.GREEN);
		SettingsController.setWarningNotificationVisibility(true);
		SettingsController.setWarningNotificationColor(Color.YELLOW);
		SettingsController.setFailureNotificationVisibility(true);
		SettingsController.setFailureNotificationColor(Color.RED);
		SettingsController.setVerboseNotificationVisibility(true);
		SettingsController.setVerboseNotificationColor(Color.CYAN);
		SettingsController.setTooltipVisibility(true);
		SettingsController.setSmoothScrolling(true);
		SettingsController.setFpsVisibility(false);
		SettingsController.setAntialiasingLevel(1);
		
		csvMode = false;
		sampleRate = Integer.MAX_VALUE;
		
		DatasetsController.BinaryFieldProcessor processor = null;
		for(DatasetsController.BinaryFieldProcessor p : DatasetsController.binaryFieldProcessors)
			if(p.toString().equals("int16 LSB First"))
				processor = p;
		
		datasets.removeAll();
		datasets.insert(1, processor, "a", Color.RED,   "", 1, 1);
		datasets.insert(3, processor, "b", Color.GREEN, "", 1, 1);
		datasets.insert(5, processor, "c", Color.BLUE,  "", 1, 1);
		datasets.insert(7, processor, "d", Color.CYAN,  "", 1, 1);
		
		DatasetsController.BinaryChecksumProcessor checksumProcessor = null;
		for(DatasetsController.BinaryChecksumProcessor p : DatasetsController.binaryChecksumProcessors)
			if(p.toString().equals("uint16 Checksum LSB First"))
				checksumProcessor = p;
		datasets.insertChecksum(9, checksumProcessor);
		
		dataStructureDefined = true;
		CommunicationView.instance.redraw();
		
		PositionedChart chart = ChartsController.createAndAddChart("Time Domain", 0, 0, 5, 5);
		List<String> chartSettings = new ArrayList<String>();
		chartSettings.add("datasets = connection 0 location 1");
		chartSettings.add("bitfield edge states = ");
		chartSettings.add("bitfield level states = ");
		chartSettings.add("duration type = Samples");
		chartSettings.add("duration = 10000000");
		chartSettings.add("x-axis = Sample Count");
		chartSettings.add("autoscale y-axis minimum = true");
		chartSettings.add("manual y-axis minimum = -1.0");
		chartSettings.add("autoscale y-axis maximum = true");
		chartSettings.add("manual y-axis maximum = 1.0");
		chartSettings.add("show x-axis title = true");
		chartSettings.add("show x-axis scale = true");
		chartSettings.add("show y-axis title = true");
		chartSettings.add("show y-axis scale = true");
		chartSettings.add("show legend = true");
		chartSettings.add("cached mode = true");
		chartSettings.add("trigger mode = Disabled");
		chartSettings.add("trigger affects = This Chart");
		chartSettings.add("trigger type = Rising Edge");
		chartSettings.add("trigger channel = connection 0 location 1");
		chartSettings.add("trigger level = 0");
		chartSettings.add("trigger hysteresis = 0");
		chartSettings.add("trigger pre/post ratio = 20");
		chart.importChart(new ConnectionsController.QueueOfLines(chartSettings));
		
		Main.window.setExtendedState(JFrame.NORMAL);
		
		receiverThread = new Thread(() -> {

			SharedByteStream stream = new SharedByteStream(ConnectionTelemetry.this);
			connected = true;
			CommunicationView.instance.redraw();
			startProcessingTelemetry(stream);

			long bytesSent = 0;
			final int repeat = 300;
			byte[] buffer = new byte[11*repeat]; // sync + 4 int16s + checksum
			ByteBuffer bb = ByteBuffer.wrap(buffer);
			bb.order(ByteOrder.LITTLE_ENDIAN);
			short a = 0;
			short b = 1;
			short c = 2;
			short d = 3;
			long start = System.currentTimeMillis();

			while(true) {

				try {
					
					if(Thread.interrupted() || !connected)
						throw new InterruptedException();
					
					bb.rewind();
					for(int n = 0; n < repeat; n++) {
						bb.put((byte) 0xAA);
						bb.putShort(a);
						bb.putShort(b);
						bb.putShort(c);
						bb.putShort(d);
						bb.putShort((short) (a+b+c+d));
						a++;
						b++;
						c++;
						d++;
					}
					
					stream.write(buffer, buffer.length);
					bytesSent += buffer.length;
					long end = System.currentTimeMillis();
					if(end - start > 3000) {
						String text = String.format("%1.1f Mbps (%1.1f Mpackets/sec)", (bytesSent / (double)(end-start) * 1000.0 * 8.0 / 1000000), (bytesSent / 11 / (double)(end-start) * 1000.0) / 1000000.0);
						NotificationsController.showVerboseForMilliseconds(text, 3000 - Theme.animationMilliseconds, true);
						bytesSent = 0;
						start = System.currentTimeMillis();
					}
					
				}  catch(InterruptedException ie) {
					
					stopProcessingTelemetry();
					return;
					
				}
			
			}
			
		});
		
		receiverThread.setPriority(Thread.MAX_PRIORITY);
		receiverThread.setName("Stress Test Simulator Thread");
		receiverThread.start();
		
	}

	@Override public void importSettings(ConnectionsController.QueueOfLines lines) throws AssertionError {
		
		String type = ChartUtils.parseString(lines.remove(), "connection type = %s");
		if(!type.equals("UART") &&
		   !type.equals("TCP") &&
		   !type.equals("UDP") &&
		   !type.equals("Demo Mode") &&
		   !type.equals("Stress Test Mode"))
			throw new AssertionError("Invalid connection type.");
		
		if(type.equals("UART")) {
			
			String portName = ChartUtils.parseString (lines.remove(), "port = %s");
			if(portName.length() < 1)
				throw new AssertionError("Invalid port.");
			
			int baud = ChartUtils.parseInteger(lines.remove(), "baud rate = %d");
			if(baud < 1)
				throw new AssertionError("Invalid baud rate.");
			
			String packetType = ChartUtils.parseString (lines.remove(), "packet type = %s");
			if(!packetType.equals("CSV") && !packetType.equals("Binary"))
				throw new AssertionError("Invalid packet type.");
			
			int hz = ChartUtils.parseInteger(lines.remove(), "sample rate hz = %d");
			if(hz < 1)
				throw new AssertionError("Invalid sample rate.");
			
			mode = Mode.UART;
			name = "UART: " + portName;
			baudRate = baud;
			csvMode = packetType.equals("CSV");
			sampleRate = hz;
			
		} else if(type.equals("TCP") || type.equals("UDP")) {
			
			int port = ChartUtils.parseInteger(lines.remove(), "server port = %d");
			if(port < 0 || port > 65535)
				throw new AssertionError("Invalid port number.");
			
			String packetType = ChartUtils.parseString (lines.remove(), "packet type = %s");
			if(!packetType.equals("CSV") && !packetType.equals("Binary"))
				throw new AssertionError("Invalid packet type.");
			
			int hz = ChartUtils.parseInteger(lines.remove(), "sample rate hz = %d");
			if(hz < 1)
				throw new AssertionError("Invalid sample rate.");
			
			mode = type.equals("TCP") ? Mode.TCP : Mode.UDP;
			name = type.equals("TCP") ? "TCP" : "UDP";
			portNumber = port;
			csvMode = packetType.equals("CSV");
			sampleRate = hz;
			
		} else if(type.equals("Demo Mode")) {
			
			mode = Mode.DEMO;
			name = "Demo Mode";
			csvMode = true;
			sampleRate = 10000;
			
		} else {
			
			mode = Mode.STRESS_TEST;
			name = "Stress Test Mode";
			csvMode = false;
			sampleRate = Integer.MAX_VALUE;
			
		}
		
		String syncWord = ChartUtils.parseString(lines.remove(), "sync word = %s");
		try {
			datasets.syncWord = (byte) Integer.parseInt(syncWord.substring(2), 16);
		} catch(Exception e) {
			throw new AssertionError("Invalid sync word.");
		}
		int syncWordByteCount = ChartUtils.parseInteger(lines.remove(), "sync word byte count = %d");
		if(syncWordByteCount < 0 || syncWordByteCount > 1)
			throw new AssertionError("Invalud sync word size.");
		datasets.syncWordByteCount = syncWordByteCount;
		
		int datasetsCount = ChartUtils.parseInteger(lines.remove(), "datasets count = %d");
		if(datasetsCount < 1)
			throw new AssertionError("Invalid datasets count.");
		
		ChartUtils.parseExact(lines.remove(), "");

		for(int i = 0; i < datasetsCount; i++) {
			
			int location            = ChartUtils.parseInteger(lines.remove(), "dataset location = %d");
			String processorName    = ChartUtils.parseString (lines.remove(), "binary processor = %s");
			String name             = ChartUtils.parseString (lines.remove(), "name = %s");
			String colorText        = ChartUtils.parseString (lines.remove(), "color = 0x%s");
			String unit             = ChartUtils.parseString (lines.remove(), "unit = %s");
			float conversionFactorA = ChartUtils.parseFloat  (lines.remove(), "conversion factor a = %f");
			float conversionFactorB = ChartUtils.parseFloat  (lines.remove(), "conversion factor b = %f");
			
			Color color = new Color(Integer.parseInt(colorText, 16));
			DatasetsController.BinaryFieldProcessor processor = null;
			for(DatasetsController.BinaryFieldProcessor p : DatasetsController.binaryFieldProcessors)
				if(p.toString().equals(processorName))
					processor = p;
			
			datasets.insert(location, processor, name, color, unit, conversionFactorA, conversionFactorB);
			
			if(processor != null && processor.toString().endsWith("Bitfield")) {
				Dataset dataset = datasets.getByLocation(location);
				String line = lines.remove();
				while(!line.equals("")){
					try {
						String bitNumbers = line.split(" ")[0];
						String[] stateNamesAndColors = line.substring(bitNumbers.length() + 3).split(","); // skip past "[n:n] = "
						bitNumbers = bitNumbers.substring(1, bitNumbers.length() - 1); // remove [ and ]
						int MSBit = Integer.parseInt(bitNumbers.split(":")[0]);
						int LSBit = Integer.parseInt(bitNumbers.split(":")[1]);
						Dataset.Bitfield bitfield = dataset.addBitfield(MSBit, LSBit);
						for(int stateN = 0; stateN < stateNamesAndColors.length; stateN++) {
							Color c = new Color(Integer.parseInt(stateNamesAndColors[stateN].split(" ")[0].substring(2), 16));
							String n = stateNamesAndColors[stateN].substring(9);
							bitfield.states[stateN].color = c;
							bitfield.states[stateN].glColor = new float[] {c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, 1};
							bitfield.states[stateN].name = n;
						}
					} catch(Exception e) {
						throw new AssertionError("Line does not specify a bitfield range.");
					}
					line = lines.remove();
				}
			} else {
				ChartUtils.parseExact(lines.remove(), "");
			}
			
		}
		
		int checksumOffset = ChartUtils.parseInteger(lines.remove(), "checksum location = %d");
		String checksumName = ChartUtils.parseString(lines.remove(), "checksum processor = %s");
		ChartUtils.parseExact(lines.remove(), "");
		
		if(checksumOffset >= 1 && !checksumName.equals("null")) {
			DatasetsController.BinaryChecksumProcessor processor = null;
			for(DatasetsController.BinaryChecksumProcessor p : DatasetsController.binaryChecksumProcessors)
				if(p.toString().equals(checksumName))
					processor = p;
			datasets.insertChecksum(checksumOffset, processor);
		}
		
		dataStructureDefined = true;
		CommunicationView.instance.redraw();

	}

	@Override public void exportSettings(PrintWriter file) {

		if(mode == Mode.UART) {
			
			file.println("\tconnection type = UART");
			file.println("\tport = "           + name.substring(6)); // skip past "UART: "
			file.println("\tbaud rate = "      + baudRate);
			file.println("\tpacket type = "    + (csvMode ? "CSV" : "Binary"));
			file.println("\tsample rate hz = " + sampleRate);
			
		} else if(mode == Mode.TCP || mode == Mode.UDP) {
			
			file.println("\tconnection type = " + ((mode == Mode.TCP) ? "TCP" : "UDP"));
			file.println("\tserver port = "    + portNumber);
			file.println("\tpacket type = "    + (csvMode ? "CSV" : "Binary"));
			file.println("\tsample rate hz = " + sampleRate);
			
		} else if(mode == Mode.DEMO) {
			
			file.println("\tconnection type = Demo Mode");
			
		} else {
			
			file.println("\tconnection type = Stress Test Mode");
			
		}
		
		file.println("\tsync word = " + String.format("0x%0" + Integer.max(2, 2 * datasets.syncWordByteCount) + "X", datasets.syncWord));
		file.println("\tsync word byte count = " + datasets.syncWordByteCount);
		file.println("\tdatasets count = " + datasets.getCount());
		file.println("");
		for(Dataset dataset : datasets.getList()) {
			
			file.println("\t\tdataset location = " + dataset.location);
			file.println("\t\tbinary processor = " + (dataset.processor == null ? "null" : dataset.processor.toString()));
			file.println("\t\tname = " + dataset.name);
			file.println("\t\tcolor = " + String.format("0x%02X%02X%02X", dataset.color.getRed(), dataset.color.getGreen(), dataset.color.getBlue()));
			file.println("\t\tunit = " + dataset.unit);
			file.println("\t\tconversion factor a = " + dataset.conversionFactorA);
			file.println("\t\tconversion factor b = " + dataset.conversionFactorB);
			if(dataset.processor != null && dataset.processor.toString().endsWith("Bitfield"))
				for(Dataset.Bitfield bitfield : dataset.bitfields) {
					file.print("\t\t[" + bitfield.MSBit + ":" + bitfield.LSBit + "] = " + String.format("0x%02X%02X%02X ", bitfield.states[0].color.getRed(), bitfield.states[0].color.getGreen(), bitfield.states[0].color.getBlue()) + bitfield.states[0].name);
					for(int i = 1; i < bitfield.states.length; i++)
						file.print("," + String.format("0x%02X%02X%02X ", bitfield.states[i].color.getRed(), bitfield.states[i].color.getGreen(), bitfield.states[i].color.getBlue()) + bitfield.states[i].name);
					file.println();
				}
			file.println("");
		}
		
		file.println("\t\tchecksum location = " + datasets.getChecksumProcessorOffset());
		file.println("\t\tchecksum processor = " + (datasets.getChecksumProcessor() == null ? "null" : datasets.getChecksumProcessor().toString()));
		file.println("");

	}
	
	@Override public long readFirstTimestamp(String path) {
		
		try {
			
			long timestamp = 0;
			
			Scanner file = new Scanner(new FileInputStream(path), "UTF-8");
			
			String header = file.nextLine();
			if(!header.split(",")[1].startsWith("UNIX Timestamp"))
				throw new Exception();
			
			String line = file.nextLine();
			timestamp = Long.parseLong(line.split(",")[1]);
			
			return timestamp;
			
		} catch(Exception e) {
			
			return Long.MAX_VALUE;
			
		}
		
	}
	
	@Override public long getTimestamp(int sampleNumber) {
		
		return datasets.getTimestamp(sampleNumber);
		
	}
	
	@Override public int getSampleCount() {
		
		return datasets.getSampleCount();
		
	}
	
	@Override public void removeAllData() {
		
		datasets.removeAllData();
		OpenGLChartsView.instance.switchToLiveView();
		
	}

	@Override public void importDataFile(String path, long firstTimestamp, AtomicLong completedByteCount) {

		removeAllData();
		
		receiverThread = new Thread(() -> {
			
			try {
				
				// open the file
				Scanner file = new Scanner(new FileInputStream(path), "UTF-8");
				
				connected = true;
				CommunicationView.instance.redraw();
				
				// sanity checks
				if(!file.hasNextLine()) {
					SwingUtilities.invokeLater(() -> disconnect("The CSV file is empty."));
					file.close();
					return;
				}
				
				String header = file.nextLine();
				completedByteCount.addAndGet((long) (header.length() + 2)); // assuming each char is 1 byte, and EOL is 2 bytes.
				String[] tokens = header.split(",");
				int columnCount = tokens.length;
				if(columnCount != datasets.getCount() + 2) {
					SwingUtilities.invokeLater(() -> disconnect("The CSV file header does not match the current data structure."));
					file.close();
					return;
				}
				
				boolean correctColumnLabels = true;
				if(!tokens[0].startsWith("Sample Number"))  correctColumnLabels = false;
				if(!tokens[1].startsWith("UNIX Timestamp")) correctColumnLabels = false;
				for(int i = 0; i < datasets.getCount(); i++) {
					Dataset d = datasets.getByIndex(i);
					String expectedLabel = d.name + " (" + d.unit + ")";
					if(!tokens[2+i].equals(expectedLabel))
						correctColumnLabels = false;
				}
				if(!correctColumnLabels) {
					SwingUtilities.invokeLater(() -> disconnect("The CSV file header does not match the current data structure."));
					file.close();
					return;
				}

				if(!file.hasNextLine()) {
					SwingUtilities.invokeLater(() -> disconnect("The CSV file does not contain any samples."));
					file.close();
					return;
				}
				
				// parse the lines of data
				String line = file.nextLine();
				completedByteCount.addAndGet((long) (line.length() + 2));
				long startTimeThread = System.currentTimeMillis();
				int sampleNumber = getSampleCount();
				while(true) {
					tokens = line.split(",");
					if(ConnectionsController.realtimeImporting) {
						if(Thread.interrupted()) {
							ConnectionsController.realtimeImporting = false;
						} else {
							long delay = (Long.parseLong(tokens[1]) - firstTimestamp) - (System.currentTimeMillis() - startTimeThread);
							if(delay > 0)
								try { Thread.sleep(delay); } catch(Exception e) { ConnectionsController.realtimeImporting = false; }
						}
					} else if(Thread.interrupted()) {
						break; // not real-time, and interrupted again, so abort
					}
					for(int columnN = 2; columnN < columnCount; columnN++)
						datasets.getByIndex(columnN - 2).setConvertedSample(sampleNumber, Float.parseFloat(tokens[columnN]));
					sampleNumber++;
					datasets.incrementSampleCountWithTimestamp(Long.parseLong(tokens[1]));
					
					if(file.hasNextLine()) {
						line = file.nextLine();
						completedByteCount.addAndGet((long) (line.length() + 2));
					} else {
						break;
					}
				}
				
				// done
				SwingUtilities.invokeLater(() -> disconnect(null));
				file.close();
				
			} catch (IOException e) {
				SwingUtilities.invokeLater(() -> disconnect("Unable to open the CSV Log file."));
			} catch (Exception e) {
				SwingUtilities.invokeLater(() -> disconnect("Unable to parse the CSV Log file."));
			}
			
		});
		
		receiverThread.setPriority(Thread.MAX_PRIORITY);
		receiverThread.setName("CSV File Import Thread");
		receiverThread.start();

	}

	/**
	 * Exports all samples to a CSV file.
	 * 
	 * @param path                  Full path with file name but without the file extension.
	 * @param completedByteCount    Variable to increment as progress is made (this is periodically queried by a progress bar.)
	 */
	@Override public void exportDataFile(String path, AtomicLong completedByteCount) { // FIXME improve exporting to use getValues()!
		
		int datasetsCount = datasets.getCount();
		int sampleCount = getSampleCount();
		
		try {
			
			PrintWriter logFile = new PrintWriter(path + ".csv", "UTF-8");
			logFile.print("Sample Number (" + sampleRate + " samples per second),UNIX Timestamp (Milliseconds since 1970-01-01)");
			for(int i = 0; i < datasetsCount; i++) {
				Dataset d = datasets.getByIndex(i);
				logFile.print("," + d.name + " (" + d.unit + ")");
			}
			logFile.println();
			
			for(int i = 0; i < sampleCount; i++) {
				
				logFile.print(i + "," + datasets.getTimestamp(i));
				for(int n = 0; n < datasetsCount; n++)
					logFile.print("," + Float.toString(datasets.getByIndex(n).getSample(i)));
				logFile.println();
				
				if(i % 1024 == 1023)
					completedByteCount.addAndGet(1024L); // periodically update the progress tracker
				
			}
			
			logFile.close();
			
		} catch(Exception e) { }
		
	}
	
	@Override public void dispose() {
		
		if(connected)
			disconnect(null);
		datasets.dispose();
		
	}
	
	/**
	 * Spawns a new thread that starts processing received telemetry packets.
	 * 
	 * @param stream    Bytes of received telemetry.
	 */
	protected void startProcessingTelemetry(SharedByteStream stream) {
		
		processorThread = new Thread(() -> {
			
			// wait for the data structure to be defined
			while(!dataStructureDefined) {
				try {
					Thread.sleep(1);
				} catch (InterruptedException e) {
					return;
				}
			}
			
			// cache a list of the datasets
			List<Dataset> list = datasets.getList();
			
			// if no telemetry after 100ms, notify the user
			String waitingForTelemetry = mode == Mode.UART ? name.substring(6) + " is connected. Send telemetry." :
			                             mode == Mode.TCP  ? "The TCP server is running. Send telemetry to " + localIp + ":" + portNumber :
			                             mode == Mode.UDP  ? "The UDP listener is running. Send telemetry to " + localIp + ":" + portNumber :
			                                                                    "";
			String receivingTelemetry  = mode == Mode.UART ? name.substring(6) + " is connected and receiving telemetry." :
			                             mode == Mode.TCP  ? "The TCP server is running and receiving telemetry." :
			                             mode == Mode.UDP  ? "The UDP listener is running and receiving telemetry." :
			                                                                    "";
			int oldSampleCount = getSampleCount();
			Timer t = new Timer(100, event -> {
				
				if(mode == Mode.DEMO || mode == Mode.STRESS_TEST)
					return;
				
				if(connected) {
					if(getSampleCount() == oldSampleCount)
						NotificationsController.showHintUntil(waitingForTelemetry, () -> getSampleCount() > oldSampleCount, true);
					else
						NotificationsController.showVerboseForMilliseconds(receivingTelemetry, 5000, true);
				}
				
			});
			t.setRepeats(false);
			t.start();
			
			if(csvMode) {
				
				// prepare for CSV mode
				stream.setPacketSize(0);
				int maxLocation = 0;
				for(Dataset d : list)
					if(d.location > maxLocation)
						maxLocation = d.location;
				float[] numberForLocation = new float[maxLocation + 1];
				String line = null;
				
				while(true) {
					
					try {

						if(Thread.interrupted())
							throw new InterruptedException();
						
						// read and parse each line of text
						line = stream.readLine();
						String[] tokens = line.split(",");
						for(int i = 0; i < numberForLocation.length; i++)
							numberForLocation[i] = Float.parseFloat(tokens[i]);
						int sampleNumber = getSampleCount();
						for(Dataset d : list)
							d.setSample(sampleNumber, numberForLocation[d.location]);
						datasets.incrementSampleCount();
						
					} catch(NumberFormatException | NullPointerException | ArrayIndexOutOfBoundsException e1) {
						
						NotificationsController.showFailureForMilliseconds("A corrupt or incomplete telemetry packet was received:\n\"" + line + "\"", 5000, false);
						
					} catch(InterruptedException e2) {
						
						return;
						
					}
					
				}
				
			} else {
				
				// prepare for binary mode 
				int packetLength = 0; // INCLUDING the sync word and optional checksum
				if(datasets.getChecksumProcessor() != null)
					packetLength = datasets.getChecksumProcessorOffset() + datasets.getChecksumProcessor().getByteCount();
				else
					for(Dataset d : list)
						if(d.location + d.processor.getByteCount() - 1 > packetLength)
							packetLength = d.location + d.processor.getByteCount();
				stream.setPacketSize(packetLength);
				
				// use multiple threads to process incoming data in parallel, with each thread parsing up to 8 blocks at a time
				final int THREAD_COUNT = Runtime.getRuntime().availableProcessors();
				final int MAX_BLOCK_COUNT_PER_THREAD = 8;
				CyclicBarrier allThreadsDone = new CyclicBarrier(THREAD_COUNT + 1);
				Parser[] parsingThreads = new Parser[THREAD_COUNT];
				for(int i = 0; i < THREAD_COUNT; i++)
					parsingThreads[i] = new Parser(list, packetLength, MAX_BLOCK_COUNT_PER_THREAD, allThreadsDone);
				
				while(true) {
					
					try {
						
						if(Thread.interrupted())
							throw new InterruptedException();
						
						// get all received telemetry packets, stopping early if there is a loss of sync or bad checksum
						SharedByteStream.PacketsBuffer packets = stream.readPackets(datasets.syncWord, datasets.syncWordByteCount);
						
						// process the received telemetry packets
						while(packets.count > 0) {
							
							int sampleNumber = getSampleCount();
							boolean blockAligned = sampleNumber % StorageFloats.BLOCK_SIZE == 0;
							int blocksRemaining = packets.count / StorageFloats.BLOCK_SIZE;
							
							if(blockAligned && blocksRemaining >= THREAD_COUNT) {
								
								// use the Parser threads to parse packets in parallel
								int blocksPerThread = Integer.min(blocksRemaining / THREAD_COUNT, MAX_BLOCK_COUNT_PER_THREAD);
								int packetsPerThread = StorageFloats.BLOCK_SIZE * blocksPerThread;
								for(Parser thread : parsingThreads) {
									thread.process(packets.buffer, packets.offset, blocksPerThread, sampleNumber);
									packets.offset += packetLength * packetsPerThread;
									packets.count  -= packetsPerThread;
									sampleNumber   += packetsPerThread;
								}
								allThreadsDone.await();
								allThreadsDone.reset();
								for(int i = 0; i < blocksPerThread * THREAD_COUNT; i++)
									datasets.incrementSampleCountBlock();
								
							} else {
								
								// process packets individually
								for(Dataset dataset : list) {
									float rawNumber = dataset.processor.extractValue(packets.buffer, packets.offset + dataset.location);
									dataset.setSample(sampleNumber, rawNumber);
								}
								datasets.incrementSampleCount();
								packets.count--;
								packets.offset += packetLength;
								
							}

						}
					
					} catch(InterruptedException | BrokenBarrierException e) {
						
						for(Parser thread : parsingThreads)
							thread.dispose();
						return;
						
					}
					
				}
				
			}
			
		});
		
		processorThread.setPriority(Thread.MAX_PRIORITY);
		processorThread.setName("Telemetry Processing Thread");
		processorThread.start();
		
	}
	
	private static class Parser {
		
		private final Thread thread;
		private final CyclicBarrier newData;    // used to indicate when new data is ready to be parsed
		
		private volatile byte[] buffer;         // stream of telemetry packets
		private volatile int offset;            // where in the buffer this object should start parsing
		private volatile int blockCount;        // how many blocks this thread should parse
		private volatile int firstSampleNumber; // which sample number the first packet corresponds to
		
		private final float[][] minimumValue;   // [blockN][datasetN]
		private final float[][] maximumValue;   // [blockN][datasetN]
		
		/**
		 * Configures this object, but does not start to parse any data.
		 * 
		 * @param datasets           List of Datasets that receive the parsed data.
		 * @param packetByteCount    Number of bytes in each packet INCLUDING the sync word and optional checksum.
		 * @param maxBlockCount      Maximum number of blocks that should be parsed by this object.
		 * @param allThreadsDone     Barrier to await on after the data has been parsed.
		 */
		public Parser(List<Dataset> datasets, int packetByteCount, int maxBlockCount, CyclicBarrier allThreadsDone) {
			
			int datasetsCount = datasets.size();
			minimumValue = new float[maxBlockCount][datasetsCount];
			maximumValue = new float[maxBlockCount][datasetsCount];
			
			newData = new CyclicBarrier(2);
			thread = new Thread(() -> {
				
				while(true) {
					
					try {
						
						// wait for data to parse
						newData.await();
						
						float[][] slots = new float[datasetsCount][];
							
						// parse each packet of each block
						for(int blockN = 0; blockN < blockCount; blockN++) {
							
							for(int datasetN = 0; datasetN < datasetsCount; datasetN++)
								slots[datasetN] = datasets.get(datasetN).getSlot(firstSampleNumber + (blockN * StorageFloats.BLOCK_SIZE));
							
							int slotOffset = (firstSampleNumber + (blockN * StorageFloats.BLOCK_SIZE)) % StorageFloats.SLOT_SIZE;
							float[] minVal = minimumValue[blockN];
							float[] maxVal = maximumValue[blockN];
							for(int packetN = 0; packetN < StorageFloats.BLOCK_SIZE; packetN++) {
								
								for(int datasetN = 0; datasetN < datasetsCount; datasetN++) {
									Dataset d = datasets.get(datasetN);
									float f = d.processor.extractValue(buffer, offset + d.location) * d.conversionFactor;
									slots[datasetN][slotOffset] = f;
									if(f < minVal[datasetN])
										minVal[datasetN] = f;
									if (f > maxVal[datasetN])
										maxVal[datasetN] = f;
								}
								
								offset += packetByteCount;
								slotOffset++;
								
							}
						}
						
						// update datasets
						for(int datasetN = 0; datasetN < datasetsCount; datasetN++)
							for(int blockN = 0; blockN < blockCount; blockN++)
								datasets.get(datasetN).setRangeOfBlock(firstSampleNumber + (blockN * StorageFloats.BLOCK_SIZE), minimumValue[blockN][datasetN], maximumValue[blockN][datasetN]);
						
						// done
						allThreadsDone.await();
							
					} catch(InterruptedException | BrokenBarrierException e) {
						return;
					}
				}
				
			});
			
			thread.setPriority(Thread.MAX_PRIORITY);
			thread.setName("Parser Thread");
			thread.start();
			
		}
		
		/**
		 * Instructs this thread to start processing one or more blocks of packets.
		 * Sync words and checksums are NOT tested here, they must be tested prior to this.
		 * 
		 * @param buffer               The byte[] containing telemetry packets.
		 * @param offset               Index into the byte[] where this thread should start processing.
		 * @param blockCount           Number of blocks to parse.
		 * @param firstSampleNumber    Which sample number the first packet corresponds to.
		 */
		public void process(byte[] buffer, int offset, int blockCount, int firstSampleNumber) throws InterruptedException {
			
			this.buffer            = buffer;
			this.offset            = offset;
			this.blockCount        = blockCount;
			this.firstSampleNumber = firstSampleNumber;
			
			try {
				newData.await();
				newData.reset();
			} catch (BrokenBarrierException e) {
				e.printStackTrace();
			}
			
		}
		
		/**
		 * Forces this thread to end. Blocks until done.
		 */
		public void dispose() {
			
			thread.interrupt();
			while(thread.isAlive()); // wait
			
		}
		
	}
	
	/**
	 * Stops the threads that process incoming telemetry packets, blocking until done.
	 */
	protected void stopProcessingTelemetry() {
		
		if(processorThread != null && processorThread.isAlive()) {
			processorThread.interrupt();
			while(processorThread.isAlive()); // wait
		}
		
	}

}
