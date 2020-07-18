import java.awt.Color;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;
import java.util.function.Consumer;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import com.fazecast.jSerialComm.SerialPort;

public class CommunicationController {
	
	public final static String PORT_UART = "UART"; // the DUT sends data over a serial port
	public final static String PORT_TCP  = "TCP";  // the DUT is a TCP client, so we spawn a TCP server
	public final static String PORT_UDP  = "UDP";  // the DUT sends UDP packets, so we listen for them
	public final static String PORT_TEST = "Test"; // dummy mode that generates test waveforms
	public final static String PORT_FILE = "File"; // dummy mode for importing CSV log files
	
	// volatile fields shared with threads
	private static volatile boolean connected = false;
	private static volatile String importFilePath = null;
	
	private static String port = PORT_UART;
	private static String portUsedBeforeImport = null;
	private static Packet packet = PacketCsv.instance;
	private static int    sampleRate = 10000;
	private static int    uartBaudRate = 9600;
	private static int    tcpUdpPort = 8080;
	private final static int MAX_TCP_IDLE_MILLISECONDS = 10000; // if connected but no new samples after than much time, disconnect and wait for a new connection
	private final static int MAX_UDP_PACKET_SIZE = 65507; // 65535 - (8byte UDP header) - (20byte IP header)
	
	private static String localIp = "[Local IP Address Unknown]";
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
	 * Sets the port and updates the GUI.
	 * If a connection currently exists, it will be closed first.
	 * 
	 * @param newPort    Communication.PORT_UART + ": port name" or .PORT_TCP or .PORT_UDP or .PORT_TEST or .PORT_FILE
	 */
	public static void setPort(String newPort) {
		
		// ignore if unchanged
		if(newPort.equals(port))
			return;
		
		// sanity check
		if(!newPort.startsWith(PORT_UART + ": ") &&
		   !newPort.equals(PORT_TCP) &&
		   !newPort.equals(PORT_UDP) &&
		   !newPort.equals(PORT_TEST) &&
		   !newPort.equals(PORT_FILE))
			return;
		
		// prepare
		if(isConnected())
			disconnect(null);
		if(port == PORT_TEST && newPort != PORT_TEST && newPort != PORT_FILE) {
			ChartsController.removeAllCharts();
			packet.reset();
		}
		
		// set
		if(port != PORT_FILE && newPort.equals(PORT_FILE))
			portUsedBeforeImport = port;
		port = newPort;
		CommunicationView.instance.setPort(newPort);
		
	}
	
	/**
	 * @return    The current port (Communication.MODE_UART + ": port name" or .MODE_TCP or .MODE_UDP or .MODE_TEST or .MODE_FILE)
	 */
	public static String getPort() {
		
		return port;
		
	}
	
	/**
	 * Gets names for every supported port (every serial port + TCP + UDP + Test)
	 *  
	 * @return    A String[] of port names.
	 */
	public static String[] getPorts() {
		
		SerialPort[] ports = SerialPort.getCommPorts();
		String[] names = new String[ports.length + 3];
		
		for(int i = 0; i < ports.length; i++)
			names[i] = "UART: " + ports[i].getSystemPortName();
		
		names[names.length - 3] = PORT_TCP;
		names[names.length - 2] = PORT_UDP;
		names[names.length - 1] = PORT_TEST;
		
		port = names[0];
		
		return names;
		
	}
	
	/**
	 * Sets the packet type, empties the packet, and updates the GUI.
	 * If a connection currently exists, it will be closed first.
	 * Any existing charts and datasets will be removed.
	 * 
	 * @param newType    One of the options from getPacketTypes().
	 */
	public static void setPacketType(String newType) {
		
		// ignore if unchanged
		if((newType.equals("CSV") && packet == PacketCsv.instance) || (newType.equals("Binary") && packet == PacketBinary.instance))
			return;
		
		// sanity check
		if(!newType.equals("CSV") &&
		   !newType.equals("Binary"))
			return;
		
		// prepare
		if(isConnected())
			disconnect(null);
		
		// set
		packet = newType.equals("CSV") ? PacketCsv.instance : PacketBinary.instance;
		packet.reset();
		CommunicationView.instance.setPacketType(newType);
		
	}
	
	/**
	 * @return    The current packet type.
	 */
	public static String getPacketType() {
		
		return packet.toString();
		
	}
	
	/**
	 * @return    A String[] of the supported packet types.
	 */
	public static String[] getPacketTypes() {
		
		return new String[] {"CSV", "Binary"};
		
	}
	
	/**
	 * @return    The GUI for defining the data structure.
	 */
	public static JPanel getDataStructureGui() {
		
		return packet.getDataStructureGui();
		
	}
	
	/**
	 * Sets the sample rate, and updates the GUI.
	 * The rate will be clipped to a minimum of 1.
	 * 
	 * @param newRate    Sample rate, in Hertz.
	 */
	public static void setSampleRate(int newRate) {
		
		// ignore if unchanged
		if(newRate == sampleRate)
			return;
		
		// sanity check
		if(newRate < 1)
			newRate = 1;
		
		// set
		sampleRate = newRate;
		CommunicationView.instance.setSampleRate(newRate);
		
	}
	
	/**
	 * @return    The current sample rate, in Hertz.
	 */
	public static int getSampleRate() {
		
		return sampleRate;
		
	}
	
	/**
	 * Sets the UART baud rate, and updates the GUI.
	 * The rate will be clipped to a minimum of 1.
	 * If a connection currently exists, it will be closed first.
	 * 
	 * @param newBaud    Baud rate.
	 */
	public static void setBaudRate(int newBaud) {
		
		// ignore if unchanged
		if(newBaud == uartBaudRate)
			return;
		
		// sanity check
		if(newBaud < 1)
			newBaud = 1;
		
		// prepare
		if(isConnected())
			disconnect(null);
		
		// set
		uartBaudRate = newBaud;
		CommunicationView.instance.setBaudRate(newBaud);
		
	}
	
	/**
	 * @return    The current baud rate.
	 */
	public static int getBaudRate() {
		
		return uartBaudRate;
		
	}
	
	/**
	 * @return    An String[] of default UART baud rates.
	 */
	public static String[] getBaudRateDefaults() {
		
		return new String[] {"9600", "19200", "38400", "57600", "115200", "230400", "460800", "921600", "1000000", "1500000", "2000000", "3000000"};
		
	}
	
	/**
	 * Sets the TCP/UDP port number and updates the GUI.
	 * The number will be clipped if it is outside the 0-65535 range.
	 * If a connection currently exists, it will be closed first.
	 * 
	 * @param newPort    Port number.
	 */
	public static void setPortNumber(int newPort) {
		
		// ignore if unchanged
		if(newPort == tcpUdpPort)
			return;
		
		// sanity check
		if(newPort < 0)
			newPort = 0;
		if(newPort > 65535)
			newPort = 65535;
		
		// prepare
		if(isConnected())
			disconnect(null);
		
		// set
		tcpUdpPort = newPort;
		CommunicationView.instance.setPortNumber(newPort);
		
	}
	
	/**
	 * @return    The current TCP/UDP port number.
	 */
	public static int getPortNumber() {
		
		return tcpUdpPort;
		
	}
	
	/**
	 * @return    A String[] of default TCP/UDP port numbers.
	 */
	public static String[] getPortNumberDefaults() {
		
		return new String[] {":8080"};
		
	}
	
	/**
	 * @return    The possible local IP addresses, with the port number appended.
	 */
	public static String getLoclIpAddress() {
		
		return localIp + ":" + tcpUdpPort;
		
	}
	
	/**
	 * @return    True if currently connected.
	 */
	public static boolean isConnected() {
		
		return connected;
		
	}
	
	/**
	 * @return    True if the data structure has been defined.
	 */
	public static boolean isDataStructureDefined() {
		
		return packet.dataStructureDefined;
		
	}
	
	/**
	 * Connects to the device.
	 * 
	 * @param quiet    If true, don't show the Data Structure GUI and don't show hint notifications.
	 */
	public static void connect(boolean quiet) {
		
		NotificationsController.removeIfConnectionRelated();
		
		packet.dataStructureDefined = false;
		
		     if(port.startsWith(PORT_UART + ": ")) connectToUart(quiet);
		else if(port.equals(PORT_TCP))             startTcpServer(quiet);
		else if(port.equals(PORT_UDP))             startUdpServer(quiet);
		else if(port.equals(PORT_TEST))            conntectToTester(quiet);
		else if(port.equals(PORT_FILE))            connectToFile();
		
	}
	
	/**
	 * Disconnects from the device and removes any connection-related Notifications.
	 * This method blocks until disconnected, so it should not be called directly from a connection thread.
	 * 
	 * @param errorMessage    If not null, show this as a Notification until a connection is made. 
	 */
	public static void disconnect(String errorMessage) {
		
		boolean wasConnected = isConnected();
		connected = false;
		
		NotificationsController.removeIfConnectionRelated();
		if(errorMessage != null)
			NotificationsController.showFailureUntil(errorMessage, () -> false, true);
		CommunicationView.instance.setConnected(false);
		Main.hideDataStructureGui();
		
		if(DatasetsController.getSampleCount() > 0)
			CommunicationView.instance.allowExporting(true);
		
		if(wasConnected) {
			     if(port.startsWith(PORT_UART + ": ")) disconnectFromUart();
			else if(port.equals(PORT_TCP))             stopTcpServer();
			else if(port.equals(PORT_UDP))             stopUdpServer();
			else if(port.equals(PORT_TEST))            disconnectFromTester();
			else if(port.equals(PORT_FILE))            disconnectFromFile();

			SwingUtilities.invokeLater(() -> { // invokeLater so this if() fails when importing a layout that has charts
				if(ChartsController.getCharts().isEmpty() && !CommunicationController.isConnected())
					NotificationsController.showHintUntil("Start by connecting to a device or opening a file by using the buttons below.", () -> !ChartsController.getCharts().isEmpty(), true);
			});
		}
		
	}
	
	private static Thread fileImportThread = null;
	
	/**
	 * Imports samples from a CSV file.
	 */
	private static void connectToFile() {
		
		CommunicationView.instance.allowImporting(false);
		CommunicationView.instance.allowExporting(false);
		DatasetsController.removeAllData();
		importingAllowed = false;
		
		fileImportThread = new Thread(() -> {
			
			try {
				
				// track approximate progress (assuming each char is 1 byte, and EOL is 2 bytes)
				long totalByteCount = Files.size(Paths.get(importFilePath));
				long readByteCount = 0;
				
				// open the file
				Scanner file = new Scanner(new FileInputStream(importFilePath), "UTF-8");
				
				CommunicationView.instance.setConnected(true);
				connected = true;
				
				// sanity checks
				if(!file.hasNextLine()) {
					SwingUtilities.invokeLater(() -> disconnect("The CSV file is empty."));
					file.close();
					return;
				}
				
				String header = file.nextLine();
				readByteCount += header.length() + 2;
				String[] tokens = header.split(",");
				int columnCount = tokens.length;
				if(columnCount != DatasetsController.getDatasetsCount() + 2) {
					SwingUtilities.invokeLater(() -> disconnect("The CSV file header does not match the current data structure."));
					file.close();
					return;
				}
				
				boolean correctColumnLabels = true;
				if(!tokens[0].startsWith("Sample Number"))  correctColumnLabels = false;
				if(!tokens[1].startsWith("UNIX Timestamp")) correctColumnLabels = false;
				for(int i = 0; i < DatasetsController.getDatasetsCount(); i++) {
					Dataset d = DatasetsController.getDatasetByIndex(i);
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
				
				NotificationsController.showProgressBar("Importing...");
				NotificationsController.setProgress(0);
				
				// parse the lines of data
				String line = file.nextLine();
				readByteCount += line.length() + 2;
				long startTimeThread = System.currentTimeMillis();
				long startTimeFile = Long.parseLong(line.split(",")[1]);
				boolean realtimeImporting = true;
				while(true) {
					tokens = line.split(",");
					if(realtimeImporting) {
						if(Thread.interrupted()) {
							realtimeImporting = false;
						} else {
							long delay = (Long.parseLong(tokens[1]) - startTimeFile) - (System.currentTimeMillis() - startTimeThread);
							if(delay > 0)
								try { Thread.sleep(delay); } catch(Exception e) { realtimeImporting = false; }
						}
					} else if(Thread.interrupted()) {
						break; // not real-time, and interrupted again, so abort
					}
					for(int columnN = 2; columnN < columnCount; columnN++)
						DatasetsController.getDatasetByIndex(columnN - 2).addConverted(Float.parseFloat(tokens[columnN]));
					DatasetsController.incrementSampleCountWithTimestamp(Long.parseLong(tokens[1]));
					
					if(file.hasNextLine()) {
						line = file.nextLine();
						readByteCount += line.length() + 2;
						NotificationsController.setProgress((double)readByteCount / (double) totalByteCount);
					} else {
						break;
					}
				}
				
				// done
				NotificationsController.setProgress(-1);
				SwingUtilities.invokeLater(() -> disconnect(null));
				file.close();
				
			} catch (IOException e) {
				NotificationsController.setProgress(-1);
				SwingUtilities.invokeLater(() -> disconnect("Unable to open the CSV Log file."));
			} catch (Exception e) {
				NotificationsController.setProgress(-1);
				SwingUtilities.invokeLater(() -> disconnect("Unable to parse the CSV Log file."));
			}
			
		});
		
		fileImportThread.setPriority(Thread.MAX_PRIORITY);
		fileImportThread.setName("File Import Thread");
		fileImportThread.start();
		
	}
	
	/**
	 * Stops the file import thread, and updates the GUI.
	 */
	private static void disconnectFromFile() {
			
		if(fileImportThread != null && fileImportThread.isAlive()) {
			fileImportThread.interrupt();
			while(fileImportThread.isAlive()); // wait
		}

		CommunicationView.instance.allowImporting(true);
		importingAllowed = true;
		
		// switch back to the original port, so "File" is removed from the port combobox
		setPort(portUsedBeforeImport);
		
	}
	
	/**
	 * Causes the file import thread to finish importing the file as fast as possible (instead of using a real-time playback speed.)
	 */
	public static void finishImportingFile() {
		
		if(fileImportThread != null && fileImportThread.isAlive())
			fileImportThread.interrupt();
		
	}
	
	private static SerialPort uartPort = null;
	
	/**
	 * Connects to a serial port and shows the DataStructureGui if necessary.
	 * 
	 * @param quiet    If true, don't show the Data Structure GUI and don't show hint notifications.
	 */
	private static void connectToUart(boolean quiet) { // FIXME make this robust: give up after some time.

		if(uartPort != null && uartPort.isOpen())
			uartPort.closePort();
			
		uartPort = SerialPort.getCommPort(port.substring(6)); // trim the leading "UART: "
		uartPort.setBaudRate(uartBaudRate);
		if(packet == PacketCsv.instance)
			uartPort.setComPortTimeouts(SerialPort.TIMEOUT_SCANNER, 0, 0);
		else if(packet == PacketBinary.instance)
			uartPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, 0, 0);
		
		// try 3 times before giving up
		if(!uartPort.openPort()) {
			if(!uartPort.openPort()) {
				if(!uartPort.openPort()) {
					disconnect("Unable to connect to " + port + ".");
					return;
				}
			}
		}
		
		CommunicationView.instance.setConnected(true);
		connected = true;

		if(!quiet)
			Main.showDataStructureGui();
		
		packet.startReceivingData(uartPort.getInputStream());
		
	}
	
	/**
	 * Stops the serial port receiver thread and disconnects from the active serial port.
	 */
	private static void disconnectFromUart() {	
		
		packet.stopReceivingData();
		
		if(uartPort != null && uartPort.isOpen())
			uartPort.closePort();
		uartPort = null;
		
	}
	
	static Thread tcpServerThread;
	
	/**
	 * Spawns a TCP server and shows the DataStructureGui if necessary.
	 * 
	 * @param quiet    If true, don't show the Data Structure GUI and don't show hint notifications.
	 */
	private static void startTcpServer(boolean quiet) {
		
		tcpServerThread = new Thread(() -> {
			
			ServerSocket tcpServer = null;
			Socket tcpSocket = null;
			PipedOutputStream stream = null;
			PipedInputStream inputStream = null;
			
			// start the TCP server
			try {
				tcpServer = new ServerSocket(tcpUdpPort);
				tcpServer.setSoTimeout(1000);
				stream = new PipedOutputStream();
				inputStream = new PipedInputStream(stream);
			} catch (Exception e) {
				try { tcpServer.close(); stream.close(); inputStream.close(); } catch(Exception e2) {}
				SwingUtilities.invokeLater(() -> disconnect("Unable to start the TCP server. Make sure another program is not already using port " + tcpUdpPort + "."));
				return;
			}
			
			CommunicationView.instance.setConnected(true);
			connected = true;
			
			if(!quiet)
				Main.showDataStructureGui();
			
			// wait for the data structure to be defined
			while(!packet.dataStructureDefined)
				try {
					Thread.sleep(1);
				} catch(Exception e) {
					// thread got interrupted, so exit.
					NotificationsController.showVerboseForSeconds("The TCP Server thread is stopping.", 5, false);
					try { tcpServer.close(); } catch(Exception e2) {}
					try { stream.close(); } catch(Exception e2) {}
					try { inputStream.close(); } catch(Exception e2) {}
					return;
				}
			
			packet.startReceivingData(inputStream);
			
			// listen for a connection
			while(true) {

				try {
					
					if(Thread.interrupted())
						throw new InterruptedException();
					
					tcpSocket = tcpServer.accept();
					tcpSocket.setSoTimeout(5000); // each valid packet of data must take <5 seconds to arrive
					InputStream is = tcpSocket.getInputStream();

					NotificationsController.showSuccessForSeconds("TCP connection established with a client at " + tcpSocket.getRemoteSocketAddress().toString().substring(1) + ".", 5, true); // trim leading "/" from the IP address
					
					// enter an infinite loop that checks for activity. if the TCP port is idle for >10 seconds, abandon it so another device can try to connect.
					long previousTimestamp = System.currentTimeMillis();
					int previousSampleNumber = DatasetsController.getSampleCount();
					while(true) {
						int byteCount = is.available();
						if(byteCount > 0) {
							byte[] buffer = new byte[byteCount];
							is.read(buffer, 0, byteCount);
							stream.write(buffer, 0, byteCount);
							continue;
						}
						Thread.sleep(1);
						int sampleNumber = DatasetsController.getSampleCount();
						long timestamp = System.currentTimeMillis();
						if(sampleNumber > previousSampleNumber) {
							previousSampleNumber = sampleNumber;
							previousTimestamp = timestamp;
						} else if(previousTimestamp < timestamp - MAX_TCP_IDLE_MILLISECONDS) {
							NotificationsController.showFailureForSeconds("The TCP connection was idle for too long. It has been closed so another device can connect.", 5, true);
							tcpSocket.close();
							break;
						}
					}
					
				} catch(SocketTimeoutException ste) {
					
					// a client never connected, so do nothing and let the loop try again.
					NotificationsController.showVerboseForSeconds("TCP socket timed out while waiting for a connection.", 5, true);
					
				} catch(IOException ioe) {
					
					// problem while accepting the socket connection, or getting the input stream, or reading from the input stream
					try { tcpSocket.close(); } catch(Exception e2) {}
					try { tcpServer.close(); } catch(Exception e2) {}
					SwingUtilities.invokeLater(() -> disconnect("TCP connection failed."));
					return;
					
				}  catch(InterruptedException ie) {
					
					// thread got interrupted, so exit.
					NotificationsController.showVerboseForSeconds("The TCP Server thread is stopping.", 5, false);
					try { tcpSocket.close(); } catch(Exception e2) {}
					try { tcpServer.close(); } catch(Exception e2) {}
					return;
					
				}
			
			}
			
		});
		
		tcpServerThread.setPriority(Thread.MAX_PRIORITY);
		tcpServerThread.setName("TCP Server");
		tcpServerThread.start();
		
	}
	
	/**
	 * Stops the TCP server thread, frees its resources, and notifies any listeners that the connection has been closed.
	 */
	private static void stopTcpServer() {
			
		if(tcpServerThread != null && tcpServerThread.isAlive()) {
			tcpServerThread.interrupt();
			while(tcpServerThread.isAlive()); // wait
		}
		
		packet.stopReceivingData();
		
	}
	
	static Thread udpServerThread;
	
	/**
	 * Spawns a UDP server and shows the DataStructureGUI if necessary.
	 * 
	 * @param quiet    If true, don't show the Data Structure GUI and don't show hint notifications.
	 */
	private static void startUdpServer(boolean quiet) {
		
		udpServerThread = new Thread(() -> {
			
			DatagramSocket udpServer = null;
			PipedOutputStream stream = null;
			PipedInputStream inputStream = null;
			
			// start the UDP server
			try {
				udpServer = new DatagramSocket(tcpUdpPort);
				udpServer.setSoTimeout(1000);
				stream = new PipedOutputStream();
				inputStream = new PipedInputStream(stream);
			} catch (Exception e) {
				try { udpServer.close(); stream.close(); inputStream.close(); } catch(Exception e2) {}
				SwingUtilities.invokeLater(() -> disconnect("Unable to start the UDP server. Make sure another program is not already using port " + tcpUdpPort + "."));
				return;
			}
			
			CommunicationView.instance.setConnected(true);
			connected = true;
			
			if(!quiet)
				SwingUtilities.invokeLater(() -> Main.showDataStructureGui());
			
			// wait for the data structure to be defined
			while(!packet.dataStructureDefined)
				try {
					Thread.sleep(1);
				} catch(Exception e) {
					// thread got interrupted, so exit.
					NotificationsController.showVerboseForSeconds("The TCP Server thread is stopping.", 5, false);
					try { udpServer.close(); } catch(Exception e2) {}
					try { stream.close(); } catch(Exception e2) {}
					try { inputStream.close(); } catch(Exception e2) {}
					return;
				}
			
			packet.startReceivingData(inputStream);
			
			// listen for packets
			byte[] rx_buffer = new byte[MAX_UDP_PACKET_SIZE];
			DatagramPacket udpPacket = new DatagramPacket(rx_buffer, rx_buffer.length);
			while(true) {

				try {
					
					if(Thread.interrupted())
						throw new InterruptedException();
					
					udpServer.receive(udpPacket);
					stream.write(rx_buffer, 0, udpPacket.getLength());
					
//					NotificationsController.showVerbose("UDP packet received from a client at " + udpPacket.getAddress().getHostAddress() + ":" + udpPacket.getPort() + ".");
					
				} catch(SocketTimeoutException ste) {
					
					// a client never sent a packet, so do nothing and let the loop try again.
					NotificationsController.showVerboseForSeconds("UDP socket timed out while waiting for a packet.", 5, true);
					
				} catch(IOException ioe) {
					
					// problem while reading from the socket, or while putting data into the stream
					try { inputStream.close(); } catch(Exception e) {}
					try { stream.close(); }      catch(Exception e) {}
					try { udpServer.close(); }   catch(Exception e) {}
					SwingUtilities.invokeLater(() -> disconnect("UDP packet error."));
					return;
					
				}  catch(InterruptedException ie) {
					
					// thread got interrupted while waiting for a connection, so exit.
					NotificationsController.showVerboseForSeconds("The UDP Server thread is stopping.", 5, false);
					try { inputStream.close(); } catch(Exception e) {}
					try { stream.close(); }      catch(Exception e) {}
					try { udpServer.close(); }   catch(Exception e) {}
					return;
					
				}
			
			}
			
		});
		
		udpServerThread.setPriority(Thread.MAX_PRIORITY);
		udpServerThread.setName("UDP Server");
		udpServerThread.start();
		
	}
	
	/**
	 * Stops the UDP server thread.
	 */
	private static void stopUdpServer() {
		
		if(udpServerThread != null && udpServerThread.isAlive()) {
			udpServerThread.interrupt();
			while(udpServerThread.isAlive()); // wait
		}
		
		packet.stopReceivingData();
		
	}
	
	static Thread testerThread;
	
	/**
	 * Starts transmission of the test data stream, and shows the DataStructureGUI if necessary.
	 * 
	 * @param quiet    If true, don't show the Data Structure GUI and don't show hint notifications.
	 */
	private static void conntectToTester(boolean quiet) {
		
		// force specific settings
		if(!getPacketType().equals("CSV"))
			setPacketType("CSV");
		setSampleRate(10000);
		setBaudRate(9600);
		
		// define the data structure if it is not already defined
		if(DatasetsController.getDatasetsCount() != 4 ||
		   !DatasetsController.getDatasetByIndex(0).name.equals("Waveform A") ||
		   !DatasetsController.getDatasetByIndex(1).name.equals("Waveform B") ||
		   !DatasetsController.getDatasetByIndex(2).name.equals("Waveform C") ||
		   !DatasetsController.getDatasetByIndex(3).name.equals("Sine Wave 1kHz")) {
			
			DatasetsController.removeAllDatasets();
			DatasetsController.insertDataset(0, null, "Waveform A",     Color.RED,   "Volts", 1, 1);
			DatasetsController.insertDataset(1, null, "Waveform B",     Color.GREEN, "Volts", 1, 1);
			DatasetsController.insertDataset(2, null, "Waveform C",     Color.BLUE,  "Volts", 1, 1);
			DatasetsController.insertDataset(3, null, "Sine Wave 1kHz", Color.CYAN,  "Volts", 1, 1);
			
		}

		// "connect" the tester
		// this simulates the transmission of 4 numbers every 100us.
		// the first three numbers are pseudo random, and scaled to form a sort of sawtooth waveform.
		// the fourth number is a 1kHz sine wave.
		// this is used to check for proper autoscaling of charts, etc.
		testerThread = new Thread(() -> {
			
			double counter = 0;
			
			while(true) {
				float scalar = ((System.currentTimeMillis() % 30000) - 15000) / 100.0f;
				float[] newSamples = new float[] {
					(System.nanoTime() / 100 % 100) * scalar * 1.0f / 14000f,
					(System.nanoTime() / 100 % 100) * scalar * 0.8f / 14000f,
					(System.nanoTime() / 100 % 100) * scalar * 0.6f / 14000f
				};
				for(int i = 0; i < 10; i++) {
					DatasetsController.getDatasetByIndex(0).add(newSamples[0]);
					DatasetsController.getDatasetByIndex(1).add(newSamples[1]);
					DatasetsController.getDatasetByIndex(2).add(newSamples[2]);
					DatasetsController.getDatasetByIndex(3).add((float) Math.sin(2 * Math.PI * 1000 * counter));
					counter += 0.0001;
					DatasetsController.incrementSampleCount();
				}
				
				try {
					Thread.sleep(1);
				} catch(InterruptedException e) {
					return; // stop and end this thread if we get interrupted
				}
			}
			
		});
		testerThread.setPriority(Thread.MAX_PRIORITY);
		testerThread.setName("Test Transmitter");
		testerThread.start();
		
		CommunicationView.instance.setConnected(true);
		connected = true;
		
		// show the data structure window
		if(!quiet)
			Main.showDataStructureGui();
		
	}
	
	/**
	 * Stops transmission of the test data stream.
	 */
	private static void disconnectFromTester() {
		
		if(testerThread != null && testerThread.isAlive()) {
			testerThread.interrupt();
			while(testerThread.isAlive()); // wait
		}
		
	}
	
	/**
	 * Imports a settings file, log file, and/or camera files.
	 * The user will be notified if there is a problem with any of the files.
	 * 
	 * @param filepaths    A String[] of absolute file paths.
	 */
	public static void importFiles(String[] filepaths) {
		
		if(!importingAllowed) {
			NotificationsController.showFailureForSeconds("Unable to import more files while importing or exporting is in progress.", 20, true);
			return;
		}
		
		int settingsFileCount = 0;
		int csvFileCount = 0;
		int cameraMjpgFileCount = 0;
		int cameraBinFileCount = 0;
		int invalidFileCount = 0;
		
		// sanity check
		for(String filepath : filepaths)
			if(filepath.endsWith(".txt"))
				settingsFileCount++;
			else if(filepath.endsWith(".csv"))
				csvFileCount++;
			else if(filepath.endsWith(".mjpg"))
				cameraMjpgFileCount++;
			else if(filepath.endsWith(".bin"))
				cameraBinFileCount++;
			else
				invalidFileCount++;
		
		if(invalidFileCount > 0) {
			NotificationsController.showFailureForSeconds("Unsupported file type. Only files exported from TelemetryViewer can be imported:\nSettings files (.txt)\nCSV files (.csv)\nCamera image files (.mjpg)\nCamera image index files (.bin)", 20, true);
			return;
		}
		if(cameraMjpgFileCount != cameraBinFileCount) {
			NotificationsController.showFailureForSeconds("MJPG and BIN files must be imported together.", 10, true);
			return;
		}
		if(csvFileCount > 1) {
			NotificationsController.showFailureForSeconds("Only one CSV file can be opened at a time.", 10, true);
			return;
		}
		if(settingsFileCount > 1) {
			NotificationsController.showFailureForSeconds("Only one settings file can be opened at a time.", 10, true);
			return;
		}
		if(cameraMjpgFileCount > 0)
			for(String filepath : filepaths)
				if(filepath.endsWith(".mjpg") && !Arrays.asList(filepaths).contains(filepath.substring(0, filepath.length() - 4) + "bin")) {
					NotificationsController.showFailureForSeconds("Each MJPG file must have a corresponding BIN file.", 10, true);
					return;
				}
		if(cameraMjpgFileCount > 0 && csvFileCount == 0) {
			NotificationsController.showFailureForSeconds("Camera images can only be imported along with their corresponding CSV file.", 10, true);
			return;
		}
		
		disconnect(null);
		
		// import the settings file if requested
		if(settingsFileCount == 1)
			for(String filepath : filepaths)
				if(filepath.endsWith(".txt"))
					if(!importSettingsFile(filepath, csvFileCount == 0))
						return;
		
		// import the CSV file if requested
		if(csvFileCount == 1)
			for(String filepath : filepaths)
				if(filepath.endsWith(".csv"))
					importCsvFile(filepath);
		
		// import the camera images if requested
		if(cameraMjpgFileCount > 0)
			for(String filepath : filepaths)
				if(filepath.endsWith(".mjpg"))
					importCameraFiles(filepath, filepath.substring(0, filepath.length() - 4) + "bin");
	}
	
	static volatile boolean importingAllowed = true;
	
	/**
	 * Exports data to files. While exporting is in progress, importing/exporting/connecting/disconnecting will be prohibited.
	 * Connecting/disconnecting is prohibited to prevent the data structure from being changed while exporting is in progress.
	 * 
	 * @param filepath              The absolute path, including the part of the filename that will be common to all exported files.
	 * @param exportSettingsFile    If true, export the settings to filepath + ".txt"
	 * @param exportCsvFile         If true, export the samples to filepath + ".csv"
	 * @param exportCameraNames     For each camera name in this List, export images to filepath + camera name + ".mjpg" and corresponding index data to filepath + camera name + ".bin"
	 */
	public static void exportFiles(String filepath, boolean exportSettingsFile, boolean exportCsvFile, List<String> exportCameraNames) {
		
		Thread exportThread = new Thread(() -> {
			
			double fileCount = exportCameraNames.size() + (exportCsvFile ? 1 : 0);
		
			CommunicationView.instance.allowConnecting(false);
			CommunicationView.instance.allowImporting(false);
			CommunicationView.instance.allowExporting(false);
			importingAllowed = false;
			
			NotificationsController.showProgressBar("Exporting...");
			NotificationsController.setProgress(0);
			
			if(exportSettingsFile)
				exportSettingsFile(filepath + ".txt");
	
			if(exportCsvFile)
				exportCsvFile(filepath + ".csv", (progressAmount) -> NotificationsController.setProgress(progressAmount / fileCount));
	
			for(int i = 0; i < exportCameraNames.size(); i++)
				for(Camera camera : DatasetsController.getExistingCameras())
					if(exportCameraNames.get(i).equals(camera.name)) {
						int cameraN = i;
						camera.exportFiles(filepath, (progressAmount) -> NotificationsController.setProgress(exportCsvFile ? (((1 + cameraN) / fileCount) + (progressAmount / fileCount)) :
							                                                                                                     (((cameraN) / fileCount) + (progressAmount / fileCount))));
					}
			
			NotificationsController.setProgress(-1);
	
			CommunicationView.instance.allowConnecting(true);
			CommunicationView.instance.allowImporting(true);
			CommunicationView.instance.allowExporting(true);
			importingAllowed = true;
			
		});
		
		exportThread.setPriority(Thread.MIN_PRIORITY); // exporting is not critical
		exportThread.setName("File Export Thread");
		exportThread.start();
		
	}
	
	/**
	 * Exports all samples to a CSV file.
	 * 
	 * @param filepath           Full path with file name.
	 * @param progressTracker    Consumer<Double> that will be notified as progress is made.
	 */
	static void exportCsvFile(String filepath, Consumer<Double> progressTracker) {
		
		int datasetsCount = DatasetsController.getDatasetsCount();
		int sampleCount = DatasetsController.getSampleCount();
		
		try {
			
			PrintWriter logFile = new PrintWriter(filepath, "UTF-8");
			logFile.print("Sample Number (" + CommunicationController.getSampleRate() + " samples per second),UNIX Timestamp (Milliseconds since 1970-01-01)");
			for(int i = 0; i < datasetsCount; i++) {
				Dataset d = DatasetsController.getDatasetByIndex(i);
				logFile.print("," + d.name + " (" + d.unit + ")");
			}
			logFile.println();
			
			for(int i = 0; i < sampleCount; i++) {
				// ensure active slots don't get flushed to disk, and periodically update the progress tracker
				if(i % 1024 == 0) {
					DatasetsController.dontFlushRangeBeingExported(i, i + DatasetsController.SLOT_SIZE);
					progressTracker.accept((double) i / (double) sampleCount);
				}
				
				logFile.print(i + "," + DatasetsController.getTimestamp(i));
				for(int n = 0; n < datasetsCount; n++)
					logFile.print("," + Float.toString(DatasetsController.getDatasetByIndex(n).getSample(i)));
				logFile.println();
				
			}
			
			DatasetsController.dontFlushRangeBeingExported(-1, -1);
			
			logFile.close();
			
		} catch(Exception e) { }
		
	}
	
	/**
	 * Saves the GUI settings, communication settings, data structure definition, and chart settings.
	 * 
	 * @param outputFilePath    An absolute path to a .txt file.
	 */
	static void exportSettingsFile(String outputFilePath) {
		
		try {
			
			PrintWriter file = new PrintWriter(new File(outputFilePath), "UTF-8");
			file.println("Telemetry Viewer v0.7 Settings");
			file.println("");
			
			file.println("GUI Settings:");
			file.println("");
			file.println("\ttile column count = "          + SettingsController.getTileColumns());
			file.println("\ttile row count = "             + SettingsController.getTileRows());
			file.println("\ttime format = "                + SettingsController.getTimeFormat());
			file.println("\tshow 24-hour time = "          + SettingsController.getTimeFormat24hours());
			file.println("\tshow plot tooltips = "         + SettingsController.getTooltipVisibility());
			file.println("\tsmooth scrolling = "           + SettingsController.getSmoothScrolling());
			file.println("\tshow fps and period = "        + SettingsController.getFpsVisibility());
			file.println("\tchart index for benchmarks = " + SettingsController.getBenchmarkedChartIndex());
			file.println("\tantialiasing level = "         + SettingsController.getAntialiasingLevel());
			file.println("");
			
			file.println("Communication Settings:");
			file.println("");
			file.println("\tport = "                + CommunicationController.getPort());
			file.println("\tuart baud rate = "      + CommunicationController.getBaudRate());
			file.println("\ttcp/udp port number = " + CommunicationController.getPortNumber());
			file.println("\tpacket type = "         + CommunicationController.getPacketType());
			file.println("\tsample rate = "         + CommunicationController.getSampleRate());
			file.println("");
			
			file.println(DatasetsController.getDatasetsCount() + " Data Structure Locations:");
			
			for(Dataset dataset : DatasetsController.getAllDatasets()) {
				
				file.println("");
				file.println("\tlocation = " + dataset.location);
				file.println("\tbinary processor = " + (dataset.processor == null ? "null" : dataset.processor.toString()));
				file.println("\tname = " + dataset.name);
				file.println("\tcolor = " + String.format("0x%02X%02X%02X", dataset.color.getRed(), dataset.color.getGreen(), dataset.color.getBlue()));
				file.println("\tunit = " + dataset.unit);
				file.println("\tconversion factor a = " + dataset.conversionFactorA);
				file.println("\tconversion factor b = " + dataset.conversionFactorB);
				if(dataset.processor != null && dataset.processor.toString().startsWith("Bitfield"))
					for(Dataset.Bitfield bitfield : dataset.bitfields) {
						file.print("\t[" + bitfield.MSBit + ":" + bitfield.LSBit + "] = " + String.format("0x%02X%02X%02X ", bitfield.states[0].color.getRed(), bitfield.states[0].color.getGreen(), bitfield.states[0].color.getBlue()) + bitfield.states[0].name);
						for(int i = 1; i < bitfield.states.length; i++)
							file.print("," + String.format("0x%02X%02X%02X ", bitfield.states[i].color.getRed(), bitfield.states[i].color.getGreen(), bitfield.states[i].color.getBlue()) + bitfield.states[i].name);
						file.println();
					}
				
			}
			
			file.println("");
			file.println("Checksum:");
			file.println("");
			file.println("\tlocation = " + packet.getChecksumProcessorLocation());
			file.println("\tchecksum processor = " + (packet.checksumProcessor == null ? "null" : packet.checksumProcessor.toString()));
			
			file.println("");
			file.println(ChartsController.getCharts().size() + " Charts:");
			
			for(PositionedChart chart : ChartsController.getCharts()) {
				
				file.println("");
				file.println("\tchart type = " + chart.toString());
				file.println("\ttop left x = " + chart.topLeftX);
				file.println("\ttop left y = " + chart.topLeftY);
				file.println("\tbottom right x = " + chart.bottomRightX);
				file.println("\tbottom right y = " + chart.bottomRightY);
				
				for(String line : chart.exportChart())
					file.println("\t" + line);
				
			}
			
			file.close();
			
		} catch (IOException e) {
			
			NotificationsController.showFailureForSeconds("Unable to save the settings file.", 5, false);
			
		}
		
	}

	/**
	 * Opens a file and resets the current state to the state defined in that file.
	 * The state consists of: GUI settings, communication settings, data structure definition, and details for each chart.
	 * 
	 * @param inputFilePath    An absolute path to a .txt file.
	 * @param connect          True to connect, or false to just configure things without connecting to the device.
	 * @return                 True on success, or false on error.
	 */
	private static boolean importSettingsFile(String inputFilePath, boolean connect) {
		
		CommunicationController.disconnect(null);
		ChartsController.removeAllCharts();
		DatasetsController.removeAllDatasets();
		
		QueueOfLines lines = null;
		
		try {
			
			lines = new QueueOfLines(Files.readAllLines(new File(inputFilePath).toPath(), StandardCharsets.UTF_8));
			
			ChartUtils.parseExact(lines.remove(), "Telemetry Viewer v0.7 Settings");
			ChartUtils.parseExact(lines.remove(), "");
			
			ChartUtils.parseExact(lines.remove(), "GUI Settings:");
			ChartUtils.parseExact(lines.remove(), "");
			
			int tileColumns           = ChartUtils.parseInteger(lines.remove(), "tile column count = %d");
			int tileRows              = ChartUtils.parseInteger(lines.remove(), "tile row count = %d");
			String timeFormat         = ChartUtils.parseString (lines.remove(), "time format = %s");
			if(!Arrays.asList(SettingsController.getTimeFormats()).contains(timeFormat))
				throw new AssertionError("Invalid time format.");
			boolean timeFormat24hours = ChartUtils.parseBoolean(lines.remove(), "show 24-hour time = %b");
			boolean tooltipVisibility = ChartUtils.parseBoolean(lines.remove(), "show plot tooltips = %b");
			boolean smoothScrolling   = ChartUtils.parseBoolean(lines.remove(), "smooth scrolling = %b");
			boolean fpsVisibility     = ChartUtils.parseBoolean(lines.remove(), "show fps and period = %b");
			int chartIndex            = ChartUtils.parseInteger(lines.remove(), "chart index for benchmarks = %d");
			int antialiasingLevel     = ChartUtils.parseInteger(lines.remove(), "antialiasing level = %d");
			ChartUtils.parseExact(lines.remove(), "");
			
			SettingsController.setTileColumns(tileColumns);
			SettingsController.setTileRows(tileRows);
			SettingsController.setTimeFormat(timeFormat);
			SettingsController.setTimeFormat24hours(timeFormat24hours);
			SettingsController.setTooltipVisibility(tooltipVisibility);
			SettingsController.setSmoothScrolling(smoothScrolling);
			SettingsController.setFpsVisibility(fpsVisibility);
			SettingsController.setAntialiasingLevel(antialiasingLevel);

			ChartUtils.parseExact(lines.remove(), "Communication Settings:");
			ChartUtils.parseExact(lines.remove(), "");
			
			String portName   = ChartUtils.parseString (lines.remove(), "port = %s");
			int baudRate      = ChartUtils.parseInteger(lines.remove(), "uart baud rate = %d");
			int tcpUdpPort    = ChartUtils.parseInteger(lines.remove(), "tcp/udp port number = %d");
			String packetType = ChartUtils.parseString (lines.remove(), "packet type = %s");
			if(!Arrays.asList(CommunicationController.getPacketTypes()).contains(packetType))
				throw new AssertionError("Invalid packet type.");
			int sampleRate    = ChartUtils.parseInteger(lines.remove(), "sample rate = %d");
			ChartUtils.parseExact(lines.remove(), "");
			
			CommunicationController.setPort(portName);
			CommunicationController.setBaudRate(baudRate);
			CommunicationController.setPortNumber(tcpUdpPort);
			CommunicationController.setPacketType(packetType);
			CommunicationController.setSampleRate(sampleRate);
			
			// connect if requested
			// if not connecting, we are about to import a file, and should indicate this so the charts can detect it if needed
			if(connect)
				CommunicationController.connect(true);
			else
				CommunicationController.setPort(PORT_FILE);
			
			int locationsCount = ChartUtils.parseInteger(lines.remove(), "%d Data Structure Locations:");
			ChartUtils.parseExact(lines.remove(), "");

			for(int i = 0; i < locationsCount; i++) {
				
				int location            = ChartUtils.parseInteger(lines.remove(), "location = %d");
				String processorName    = ChartUtils.parseString (lines.remove(), "binary processor = %s");
				String name             = ChartUtils.parseString (lines.remove(), "name = %s");
				String colorText        = ChartUtils.parseString (lines.remove(), "color = 0x%s");
				String unit             = ChartUtils.parseString (lines.remove(), "unit = %s");
				float conversionFactorA = ChartUtils.parseFloat  (lines.remove(), "conversion factor a = %f");
				float conversionFactorB = ChartUtils.parseFloat  (lines.remove(), "conversion factor b = %f");
				
				Color color = new Color(Integer.parseInt(colorText, 16));
				BinaryFieldProcessor processor = null;
				for(BinaryFieldProcessor p : PacketBinary.getBinaryFieldProcessors())
					if(p.toString().equals(processorName))
						processor = p;
				
				packet.insertField(location, processor, name, color, unit, conversionFactorA, conversionFactorB);
				
				if(processor != null && processor.toString().startsWith("Bitfield")) {
					Dataset dataset = DatasetsController.getDatasetByLocation(location);
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
							throw new AssertionError("Text does not specify a bitfield range.");
						}
						line = lines.remove();
					}
				} else {
					ChartUtils.parseExact(lines.remove(), "");
				}
				
			}
			
			ChartUtils.parseExact(lines.remove(), "Checksum:");
			ChartUtils.parseExact(lines.remove(), "");
			int checksumOffset = ChartUtils.parseInteger(lines.remove(), "location = %d");
			String checksumName  = ChartUtils.parseString(lines.remove(), "checksum processor = %s");
			
			if(checksumOffset >= 1 && !checksumName.equals("null")) {
				BinaryChecksumProcessor processor = null;
				for(BinaryChecksumProcessor p : PacketBinary.getBinaryChecksumProcessors())
					if(p.toString().equals(checksumName))
						processor = p;
				packet.insertChecksum(checksumOffset, processor);
			}
			
			packet.dataStructureDefined = true;

			ChartUtils.parseExact(lines.remove(), "");
			int chartsCount = ChartUtils.parseInteger(lines.remove(), "%d Charts:");
			if(chartsCount == 0) {
				NotificationsController.showHintUntil("Add a chart by clicking on a tile, or by clicking-and-dragging across multiple tiles.", () -> !ChartsController.getCharts().isEmpty(), true);
				return true;
			}

			for(int i = 0; i < chartsCount; i++) {
				
				ChartUtils.parseExact(lines.remove(), "");
				String chartType = ChartUtils.parseString (lines.remove(), "chart type = %s");
				int topLeftX     = ChartUtils.parseInteger(lines.remove(), "top left x = %d");
				int topLeftY     = ChartUtils.parseInteger(lines.remove(), "top left y = %d");
				int bottomRightX = ChartUtils.parseInteger(lines.remove(), "bottom right x = %d");
				int bottomRightY = ChartUtils.parseInteger(lines.remove(), "bottom right y = %d");
				
				if(topLeftX < 0 || topLeftX >= SettingsController.getTileColumns()) {
					lines.lineNumber -= 3;
					throw new AssertionError("Invalid chart position.");
				}
				
				if(topLeftY < 0 || topLeftY >= SettingsController.getTileRows()) {
					lines.lineNumber -= 2;
					throw new AssertionError("Invalid chart position.");
				}
				
				if(bottomRightX < 0 || bottomRightX >= SettingsController.getTileColumns()) {
					lines.lineNumber -= 1;
					throw new AssertionError("Invalid chart position.");
				}
				
				if(bottomRightY < 0 || bottomRightY >= SettingsController.getTileRows())
					throw new AssertionError("Invalid chart position.");
				
				for(PositionedChart existingChart : ChartsController.getCharts())
					if(existingChart.regionOccupied(topLeftX, topLeftY, bottomRightX, bottomRightY))
						throw new AssertionError("Chart overlaps an existing chart.");
				
				PositionedChart chart = ChartsController.createAndAddChart(chartType, topLeftX, topLeftY, bottomRightX, bottomRightY);
				if(chart == null) {
					lines.lineNumber -= 4;
					throw new AssertionError("Invalid chart type.");
				}
				chart.importChart(lines);
				
			}
			
			SettingsController.setBenchmarkedChartByIndex(chartIndex);
			return true;
			
		} catch (IOException ioe) {
			
			NotificationsController.showFailureUntil("Unable to open the settings file.", () -> false, true);
			return false;
			
		} catch(AssertionError ae) {
		
			CommunicationController.disconnect(null);
			ChartsController.removeAllCharts();
			DatasetsController.removeAllDatasets();
			
			NotificationsController.showFailureUntil("Error while parsing the settings file:\nLine " + lines.lineNumber + ": " + ae.getMessage(), () -> false, true);
			return false;
		
		}
		
	}
	
	/**
	 * Imports all samples from a CSV file.
	 * 
	 * @param path    Full path with file name.
	 */
	private static void importCsvFile(String filepath) {

		disconnect(null);
		setPort(PORT_FILE);
		importFilePath = filepath;
		connect(true);
		
	}
	
	/**
	 * Imports all images from a MJPG and corresponding BIN file.
	 * 
	 * @param mjpgFilepath    MJPEG file containing the concatenated JPEG images.
	 * @param binFilepath     BIN file containing index data for those JPEGs.
	 */
	private static void importCameraFiles(String mjpgFilepath, String binFilepath) {
		
		for(Camera camera : DatasetsController.getExistingCameras()) {
			String filesystemFriendlyName = camera.name.replaceAll("[^a-zA-Z0-9.-]", "_");
			if(mjpgFilepath.endsWith(filesystemFriendlyName + ".mjpg")) {
				camera.importFiles(mjpgFilepath, binFilepath);
				return;
			}
		}
		
	}
	
	@SuppressWarnings("serial")
	public static class QueueOfLines extends LinkedList<String> {
		
		int lineNumber = 0;
		
		/**
		 * A Queue<String> that keeps track of how many items have been removed from the Queue.
		 * This is for easily tracking the current line number, so it can be displayed in error messages if necessary.
		 * 
		 * @param lines    The lines of text to insert into the queue. Leading tabs will be removed.
		 */
		public QueueOfLines(List<String> lines) {
			super();
			for(String line : lines)
				if(line.startsWith("\t"))
					add(line.substring(1));
				else
					add(line);
		}
		
		@Override public String remove() {
			lineNumber++;
			try {
				return super.remove();
			} catch(Exception e) {
				throw new AssertionError("Incomplete file. More lines are required.");
			}
		}

	}
	
}
