import java.io.FileInputStream;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.function.Consumer;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import com.fazecast.jSerialComm.SerialPort;

public class CommunicationController {

	static List<Consumer<String>>  portListeners = new ArrayList<>();
	static List<Consumer<String>>  packetTypeListeners = new ArrayList<>();
	static List<Consumer<Integer>> sampleRateListeners = new ArrayList<>();
	static List<Consumer<Integer>> baudRateListeners = new ArrayList<>();
	static List<Consumer<InetAddress>> ipListeners = new ArrayList<>();
	static List<Consumer<Integer>> portNumberListeners = new ArrayList<>(); // TCP/UDP port
	static List<Consumer<Boolean>> connectionListeners = new ArrayList<>(); // true = connected or listening
	
	/**
	 * Registers a listener that will be notified when the port changes, and triggers an event to ensure the GUI is in sync.
	 * 
	 * @param listener    The listener to be notified.
	 */
	public static void addPortListener(Consumer<String> listener) {
		
		portListeners.add(listener);
		setPort(Communication.port);
		
	}
	
	/**
	 * Changes the port and notifies any listeners.
	 * If a connection currently exists, it will be closed first.
	 * 
	 * @param newPort    Communication.PORT_UART + ": port name" or .PORT_TCP or .PORT_UDP or .PORT_TEST or .PORT_FILE
	 */
	public static void setPort(String newPort) {
		
		// sanity check
		if(!newPort.startsWith(Communication.PORT_UART + ": ") &&
		   !newPort.equals(Communication.PORT_IP) &&
		   !newPort.equals(Communication.PORT_TCP) &&
		   !newPort.equals(Communication.PORT_UDP) &&
		   !newPort.equals(Communication.PORT_TEST) &&
		   !newPort.equals(Communication.PORT_FILE))
			return;
		
		// prepare
		if(isConnected())
			disconnect();
		
		// set and notify
		if(!Communication.port.equals(Communication.PORT_FILE) && newPort.equals(Communication.PORT_FILE))
			Communication.portUsedBeforeImport = Communication.port;
		Communication.port = newPort;
		for(Consumer<String> listener : portListeners)
			listener.accept(Communication.port);
		
	}

	public static void setIp(String newIp) {
		if (isConnected())
			disconnect();

		try {
			Communication.serverIp = InetAddress.getByName(newIp);
		} catch (Exception ignored) {
			return;
		}

		for (Consumer<InetAddress> listener : ipListeners)
			listener.accept(Communication.serverIp);
	}

	/**
	 * @return    The current port (Communication.MODE_UART + ": port name" or .MODE_TCP or .MODE_UDP or .MODE_TEST or .MODE_FILE)
	 */
	public static String getPort() {
		
		return Communication.port;
		
	}
	
	/**
	 * Gets names for every supported port (every serial port + TCP + UDP + Test)
	 * These should be listed in a dropdown box for the user to choose from.
	 *  
	 * @return    A String[] of port names.
	 */
	public static String[] getPorts() {
		
		SerialPort[] ports = SerialPort.getCommPorts();
		String[] names = new String[ports.length + 4];
		
		for(int i = 0; i < ports.length; i++)
			names[i] = "UART: " + ports[i].getSystemPortName();
		
		names[names.length - 4] = Communication.PORT_IP;
		names[names.length - 3] = Communication.PORT_TCP;
		names[names.length - 2] = Communication.PORT_UDP;
		names[names.length - 1] = Communication.PORT_TEST;
		
		Communication.port = names[0];
		
		return names;
		
	}
	
	/**
	 * Registers a listener that will be notified when the packet type changes, and triggers an event to ensure the GUI is in sync.
	 * 
	 * @param listener    The listener to be notified.
	 */
	public static void addPacketTypeListener(Consumer<String> listener) {
		
		packetTypeListeners.add(listener);
		setPacketType(Communication.packet.toString());
		
	}
	
	/**
	 * Changes the packet type, empties the packet, and notifies any listeners.
	 * If a connection currently exists, it will be closed first.
	 * Any existing charts and datasets will be removed.
	 * 
	 * @param newType    One of the options from getPacketTypes().
	 */
	public static void setPacketType(String newType) {
		
		// sanity check
		if(!newType.equals("CSV") &&
		   !newType.equals("Binary"))
			return;
		
		// prepare
		if(isConnected())
			disconnect();
		Controller.removeAllCharts();
		DatasetsController.removeAllDatasets();
		
		// set and notify
		Communication.packet = newType.equals("CSV") ? new CsvPacket() : new BinaryPacket();
		for(Consumer<String> listener : packetTypeListeners)
			listener.accept(Communication.packet.toString());
		
	}
	
	/**
	 * @return    The current packet type.
	 */
	public static String getPacketType() {
		
		return Communication.packet.toString();
		
	}
	
	/**
	 * @return    A String[] of the supported packet types.
	 */
	public static String[] getPacketTypes() {
		
		return new String[] {"CSV", "Binary"};
		
	}
	
	/**
	 * Registers a listener that will be notified when the sample rate changes, and triggers an event to ensure the GUI is in sync.
	 * 
	 * @param listener    The listener to be notified.
	 */
	public static void addSampleRateListener(Consumer<Integer> listener) {
		
		sampleRateListeners.add(listener);
		setSampleRate(Communication.sampleRate);
		
	}
	
	/**
	 * Changes the sample rate, and notifies any listeners. The rate will be clipped to a minimum of 1.
	 * 
	 * @param newRate    Sample rate, in Hertz.
	 */
	public static void setSampleRate(int newRate) {
		
		// sanity check
		if(newRate < 1)
			newRate = 1;
		
		// set and notify
		Communication.sampleRate = newRate;
		for(Consumer<Integer> listener : sampleRateListeners)
			listener.accept(Communication.sampleRate);
		
	}
	
	/**
	 * @return    The current sample rate, in Hertz.
	 */
	public static int getSampleRate() {
		
		return Communication.sampleRate;
		
	}
	
	/**
	 * Registers a listener that will be notified when the UART baud rate changes, and triggers an event to ensure the GUI is in sync.
	 * 
	 * @param listener    The listener to be notified.
	 */
	public static void addBaudRateListener(Consumer<Integer> listener) {
		
		baudRateListeners.add(listener);
		setBaudRate(Communication.uartBaudRate);
		
	}
	
	/**
	 * Changes the UART baud rate, and notifies any listeners. The rate will be clipped to a minimum of 1.
	 * If a connection currently exists, it will be closed first.
	 * 
	 * @param newBaud    Baud rate.
	 */
	public static void setBaudRate(int newBaud) {
		
		// sanity check
		if(newBaud < 1)
			newBaud = 1;
		
		// prepare
		if(isConnected())
			disconnect();
		
		// set and notify
		Communication.uartBaudRate = newBaud;		
		for(Consumer<Integer> listener : baudRateListeners)
			listener.accept(Communication.uartBaudRate);
		
	}
	
	/**
	 * @return    The current baud rate.
	 */
	public static int getBaudRate() {
		
		return Communication.uartBaudRate;
		
	}
	
	/**
	 * @return    An String[] of default UART baud rates.
	 */
	public static String[] getBaudRateDefaults() {
		
		return new String[] {"9600", "19200", "38400", "57600", "115200", "230400", "460800", "921600", "1000000", "1500000", "2000000", "3000000"};
		
	}
	
	/**
	 * Specifies the CSV log file to import.
	 * 
	 * @param path    The CSV log file.
	 */
	public static void setImportFile(String path) {
		
		Communication.importFilePath = path;
		
	}
	
	/**
	 * Registers a listener that will be notified when the TCP/UDP port number changes, and triggers an event to ensure the GUI is in sync.
	 * 
	 * @param listener    The listener to be notified.
	 */
	public static void addPortNumberListener(Consumer<Integer> listener) {
		
		portNumberListeners.add(listener);
		setPortNumber(Communication.tcpUdpPort);
		
	}

	public static void addIpAddressListener(Consumer<InetAddress> listener) {
		ipListeners.add(listener);
		setIp(Communication.serverIp.toString());
	}

	/**
	 * Changes the TCP/UDP port number, and notifies any listeners. The number will be clipped if it is outside the 0-65535 range.
	 * If a connection currently exists, it will be closed first.
	 * 
	 * @param newPort    Port number.
	 */
	public static void setPortNumber(int newPort) {
		
		// sanity check
		if(newPort < 0)
			newPort = 0;
		if(newPort > 65535)
			newPort = 65535;
		
		// prepare
		if(isConnected())
			disconnect();
		
		// set and notify
		Communication.tcpUdpPort = newPort;		
		for(Consumer<Integer> listener : portNumberListeners)
			listener.accept(Communication.tcpUdpPort);
		
	}
	
	/**
	 * @return    The current TCP/UDP port number.
	 */
	public static int getPortNumber() {
		
		return Communication.tcpUdpPort;
		
	}
	
	/**
	 * @return    A String[] of default TCP/UDP port numbers.
	 */
	public static String[] getPortNumberDefaults() {
		
		return new String[] {":8080"};
		
	}
	
	/**
	 * Registers a listener that will be notified when a connection is made or closed, and triggers an event to ensure the GUI is in sync.
	 * 
	 * @param listener    The listener to be notified.
	 */
	public static void addConnectionListener(Consumer<Boolean> listener) {
		
		connectionListeners.add(listener);
		notifyConnectionListeners();
		
	}
	
	/**
	 * Notifies all registered listeners about the connection state.
	 */
	private static void notifyConnectionListeners() {
		
		for(Consumer<Boolean> listener : connectionListeners)
			if(Communication.port.startsWith(Communication.PORT_UART))  listener.accept(Communication.uartConnected);
			else if(Communication.port.equals(Communication.PORT_TCP))  listener.accept(Communication.tcpConnected);
			else if(Communication.port.equals(Communication.PORT_UDP))  listener.accept(Communication.udpConnected);
			else if(Communication.port.equals(Communication.PORT_TEST)) listener.accept(Communication.testConnected);
			else if(Communication.port.equals(Communication.PORT_FILE)) listener.accept(Communication.fileConnected);
			else if (Communication.port.equals(Communication.PORT_IP))  listener.accept(Communication.ipConnected);
		
	}
	
	/**
	 * @return    True if a connection exists.
	 */
	public static boolean isConnected() {
		
		if(Communication.port.startsWith(Communication.PORT_UART))  return Communication.uartConnected;
		else if(Communication.port.equals(Communication.PORT_TCP))  return Communication.tcpConnected;
		else if(Communication.port.equals(Communication.PORT_UDP))  return Communication.udpConnected;
		else if(Communication.port.equals(Communication.PORT_TEST)) return Communication.testConnected;
		else if(Communication.port.equals(Communication.PORT_FILE)) return Communication.fileConnected;
		else if(Communication.port.equals(Communication.PORT_IP)) return Communication.ipConnected;
		else                                                        return false;
		
	}
	
	/**
	 * Connects to the device.
	 * 
	 * @param parentWindow    If not null, a DataStructureWindow will be shown and centered on this JFrame.
	 */
	public static void connect(JFrame parentWindow) {
		
		if(Communication.port.startsWith(Communication.PORT_UART + ": "))
			connectToUart(parentWindow);
		else if(Communication.port.equals(Communication.PORT_TCP))
			startTcpServer(parentWindow);
		else if(Communication.port.equals(Communication.PORT_UDP))
			startUdpServer(parentWindow);
		else if(Communication.port.equals(Communication.PORT_TEST))
			startTester(parentWindow);
		else if(Communication.port.equals(Communication.PORT_FILE))
			connectToFile();
		else if (Communication.port.equals(Communication.PORT_IP))
			connectToIp(parentWindow);
		
		SwingUtilities.invokeLater(() -> { // invokeLater so this if() fails when importing a layout that has charts
			if(Controller.getCharts().isEmpty() && isConnected())
				NotificationsController.showHintUntil("Add a chart by clicking on a tile, or by clicking-and-dragging across multiple tiles.", () -> !Controller.getCharts().isEmpty(), true);
		});
		
	}
	
	/**
	 * Disconnects from the device and removes any visible Notifications.
	 */
	public static void disconnect() {
		
		boolean wasConnected = isConnected();
		
		if(Communication.port.startsWith(Communication.PORT_UART + ": "))
			disconnectFromUart();
		else if(Communication.port.equals(Communication.PORT_TCP))
			stopTcpServer();
		else if(Communication.port.equals(Communication.PORT_UDP))
			stopUdpServer();
		else if(Communication.port.equals(Communication.PORT_TEST))
			stopTester();
		else if(Communication.port.equals(Communication.PORT_FILE))
			disconnectFromFile();
		else if (Communication.port.equals(Communication.PORT_IP))
			disconnectFromIp();
		
		NotificationsController.removeAll();

		if(wasConnected) {
			SwingUtilities.invokeLater(() -> { // invokeLater so this if() fails when importing a layout that has charts
				if(Controller.getCharts().isEmpty() && !CommunicationController.isConnected())
					NotificationsController.showHintUntil("Start by connecting to a device or opening a file by using the buttons below.", () -> CommunicationController.isConnected() || !Controller.getCharts().isEmpty(), true);
			});
		}
		
	}
	
	private static Thread fileImportThread = null;
	
	/**
	 * Imports samples from a CSV Log file.
	 */
	private static void connectToFile() {
		
		DatasetsController.removeAllData();
		
		fileImportThread = new Thread(() -> {
			
			try {
				
				// open the file
				Scanner file = new Scanner(new FileInputStream(Communication.importFilePath), "UTF-8");
				
				// sanity checks
				if(!file.hasNextLine()) {
					SwingUtilities.invokeLater(() -> {
						disconnect();
						NotificationsController.showFailureUntil("CSV Log file is empty.", () -> false, true);
						NotificationsController.showHintUntil("Start by connecting to a device or opening a file by using the buttons below.", () -> CommunicationController.isConnected() || !Controller.getCharts().isEmpty(), true);
					});
					file.close();
					return;
				}
				
				String header = file.nextLine();
				String[] tokens = header.split(",");
				int columnCount = tokens.length;
				if(columnCount != DatasetsController.getDatasetsCount() + 2) {
					SwingUtilities.invokeLater(() -> {
						disconnect();
						NotificationsController.showFailureUntil("CSV Log file header does not match the current data structure.", () -> false, true);
						NotificationsController.showHintUntil("Start by connecting to a device or opening a file by using the buttons below.", () -> CommunicationController.isConnected() || !Controller.getCharts().isEmpty(), true);
					});
					file.close();
					return;
				}
				
				boolean correctColumnLabels = true;
				if(!tokens[0].startsWith("Sample Number"))
					correctColumnLabels = false;
				if(!tokens[1].startsWith("UNIX Timestamp"))
					correctColumnLabels = false;
				for(int i = 0; i < DatasetsController.getDatasetsCount(); i++) {
					Dataset d = DatasetsController.getDatasetByIndex(i);
					String expectedLabel = d.name + " (" + d.unit + ")";
					if(!tokens[2+i].equals(expectedLabel))
						correctColumnLabels = false;
				}
				if(!correctColumnLabels) {
					SwingUtilities.invokeLater(() -> {
						disconnect();
						NotificationsController.showFailureUntil("CSV Log file header does not match the current data structure.", () -> false, true);
						NotificationsController.showHintUntil("Start by connecting to a device or opening a file by using the buttons below.", () -> CommunicationController.isConnected() || !Controller.getCharts().isEmpty(), true);
					});
					file.close();
					return;
				}

				if(!file.hasNextLine()) {
					SwingUtilities.invokeLater(() -> {
						disconnect();
						NotificationsController.showFailureUntil("CSV Log file does not contain any samples.", () -> false, true);
						NotificationsController.showHintUntil("Start by connecting to a device or opening a file by using the buttons below.", () -> CommunicationController.isConnected() || !Controller.getCharts().isEmpty(), true);
					});
					file.close();
					return;
				}

				SwingUtilities.invokeLater(() -> {
					Communication.fileConnected = true;
					notifyConnectionListeners();
				});
				
				// parse and input the lines of data
				String line = file.nextLine();
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
					}
					for(int columnN = 2; columnN < columnCount; columnN++)
						DatasetsController.getDatasetByIndex(columnN - 2).addConverted(Float.parseFloat(tokens[columnN]));
					DatasetsController.incrementSampleCountWithTimestamp(Long.parseLong(tokens[1]));
					
					if(file.hasNextLine())
						line = file.nextLine();
					else
						break;
				}
				
				// done
				SwingUtilities.invokeLater(() -> {
					disconnect();
				});
				file.close();
				
			} catch (IOException e) {
				SwingUtilities.invokeLater(() -> {
					disconnect();
					NotificationsController.showFailureUntil("Unable to open the CSV Log file.", () -> false, true);
					NotificationsController.showHintUntil("Start by connecting to a device or opening a file by using the buttons below.", () -> CommunicationController.isConnected() || !Controller.getCharts().isEmpty(), true);	
				});
			} catch (Exception e) {
				SwingUtilities.invokeLater(() -> {
					disconnect();
					NotificationsController.showFailureUntil("Unable to parse the CSV Log file.", () -> false, true);
				NotificationsController.showHintUntil("Start by connecting to a device or opening a file by using the buttons below.", () -> CommunicationController.isConnected() || !Controller.getCharts().isEmpty(), true);
				});
			}
			
		});
		
		fileImportThread.start();
		
	}
	
	/**
	 * Stops the file import thread, and notifies any listeners that the connection has been closed.
	 */
	private static void disconnectFromFile() {
			
		if(fileImportThread != null && fileImportThread.isAlive()) {
			fileImportThread.interrupt();
			while(fileImportThread.isAlive()); // wait
		}

		Communication.fileConnected = false;
		notifyConnectionListeners();
		
		// switch back to the original port, so "File" is removed from the combobox
		setPort(Communication.portUsedBeforeImport);
		
	}
	
	/**
	 * Causes the file import thread to finish importing the file as fast as possible (instead of using a real-time playback speed.)
	 */
	public static void finishImportingFile() {
		
		if(fileImportThread != null && fileImportThread.isAlive())
			fileImportThread.interrupt();
		
	}

	private static Socket tcpClient;

	private static void connectToIp(JFrame parentWindow) {
		try {
			if (tcpClient != null && tcpClient.isConnected())
				tcpClient.close();

			tcpClient = new Socket(Communication.serverIp, Communication.tcpUdpPort);

			while (!tcpClient.isConnected())
				Thread.sleep(50);

			Communication.ipConnected = true;
			notifyConnectionListeners();

			if(parentWindow != null)
				Communication.packet.showDataStructureWindow(parentWindow, false);

			Communication.packet.startReceivingData(tcpClient.getInputStream());

			NotificationsController.showSuccessForSeconds("Connected!", 3, true);
		} catch (Exception e) {
			NotificationsController.showFailureForSeconds("Failed to connect: " + e.getMessage(), 15, true);
		}
	}
	/**
	 * Stops the serial port receiver thread and disconnects from the active serial port.
	 */
	private static void disconnectFromIp() {

		if(Communication.packet != null)
			Communication.packet.stopReceivingData();

		try {
			if(tcpClient != null && tcpClient.isConnected())
				tcpClient.close();
		} catch (Exception ignored) {}

		tcpClient = null;

		Communication.ipConnected = false;

		notifyConnectionListeners();
	}

	private static SerialPort uartPort = null;
	
	/**
	 * Connects to a serial port and shows a DataStructureWindow if necessary.
	 * 
	 * @param parentWindow    If not null, a DataStructureWindow will be shown and centered on this JFrame.
	 */
	private static void connectToUart(JFrame parentWindow) { // FIXME make this robust: give up after some time.

		if(uartPort != null && uartPort.isOpen())
			uartPort.closePort();
			
		uartPort = SerialPort.getCommPort(Communication.port.substring(6)); // trim the leading "UART: "
		uartPort.setBaudRate(Communication.uartBaudRate);
		if(Communication.packet instanceof CsvPacket)
			uartPort.setComPortTimeouts(SerialPort.TIMEOUT_SCANNER, 0, 0);
		else if(Communication.packet instanceof BinaryPacket)
			uartPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, 0, 0);
		
		// try 3 times before giving up
		if(!uartPort.openPort()) {
			if(!uartPort.openPort()) {
				if(!uartPort.openPort()) {
					NotificationsController.showFailureForSeconds("Unable to connect to " + Communication.port + ".", 5, false);
					disconnect();
					return;
				}
			}
		}
		
		Communication.uartConnected = true;
		notifyConnectionListeners();

		if(parentWindow != null)
			Communication.packet.showDataStructureWindow(parentWindow, false);
		
		int oldSampleCount = DatasetsController.getSampleCount();
		Timer t = new Timer(100, event -> {
			if(DatasetsController.getSampleCount() == oldSampleCount)
				NotificationsController.showSuccessUntil(Communication.port.substring(6) + " is connected. Send telemetry.", () -> DatasetsController.getSampleCount() > oldSampleCount, true); // trim the leading "UART: "
			else
				NotificationsController.showVerboseForSeconds(Communication.port.substring(6) + " is connected and receiving telemetry.", 5, true);
		});
		t.setRepeats(false);
		t.start();

		Communication.packet.startReceivingData(uartPort.getInputStream());
		
	}
	
	/**
	 * Stops the serial port receiver thread and disconnects from the active serial port.
	 */
	private static void disconnectFromUart() {	
		
		if(Communication.packet != null)
			Communication.packet.stopReceivingData();
		
		if(uartPort != null && uartPort.isOpen())
			uartPort.closePort();
		uartPort = null;
		
		Communication.uartConnected = false;
		notifyConnectionListeners();
		
	}
	
	static Thread tcpServerThread;
	
	/**
	 * Spawns a TCP server and shows a DataStructureWindow if necessary.
	 * 
	 * @param parentWindow    If not null, a DataStructureWindow will be shown and centered on this JFrame.
	 */
	private static void startTcpServer(JFrame parentWindow) {
		
		tcpServerThread = new Thread(() -> {
			
			ServerSocket tcpServer = null;
			Socket tcpSocket = null;
			
			// start the TCP server
			try {
				tcpServer = new ServerSocket(Communication.tcpUdpPort);
				tcpServer.setSoTimeout(1000);
			} catch (Exception e) {
				NotificationsController.showFailureForSeconds("Unable to start the TCP server. Make sure another program is not already using port " + Communication.tcpUdpPort + ".", 5, false);
				try { tcpServer.close(); } catch(Exception e2) {}
				SwingUtilities.invokeLater(() -> disconnect()); // invokeLater to prevent a deadlock
				return;
			}
			
			Communication.tcpConnected = true;
			notifyConnectionListeners();
			
			if(parentWindow != null)
				Communication.packet.showDataStructureWindow(parentWindow, false);
			
			int oldSampleCount = DatasetsController.getSampleCount();
			Timer t = new Timer(100, event -> {
				if(DatasetsController.getSampleCount() == oldSampleCount)
					NotificationsController.showSuccessUntil("The TCP server is running. Send telemetry to " + Communication.localIp + ":" + Communication.tcpUdpPort, () -> DatasetsController.getSampleCount() > oldSampleCount, true);
				else
					NotificationsController.showVerboseForSeconds("The TCP server is running and receiving telemetry.", 5, true);
			});
			t.setRepeats(false);
			t.start();
			
			// wait for a connection
			while(true) {

				try {
					
					if(Thread.interrupted())
						throw new InterruptedException();
					
					tcpSocket = tcpServer.accept();
					tcpSocket.setSoTimeout(5000); // each valid packet of data must take <5 seconds to arrive
					Communication.packet.startReceivingData(tcpSocket.getInputStream());

					NotificationsController.showSuccessForSeconds("TCP connection established with a client at " + tcpSocket.getRemoteSocketAddress().toString().substring(1) + ".", 5, true); // trim leading "/" from the IP address
					
					// enter an infinite loop that checks for inactivity. if the TCP port is idle for >10 seconds, abandon it so another device can try to connect.
					long previousTimestamp = System.currentTimeMillis();
					int previousSampleNumber = DatasetsController.getSampleCount();
					while(true) {
						Thread.sleep(1000);
						int sampleNumber = DatasetsController.getSampleCount();
						long timestamp = System.currentTimeMillis();
						if(sampleNumber > previousSampleNumber) {
							previousSampleNumber = sampleNumber;
							previousTimestamp = timestamp;
						} else if(previousTimestamp < timestamp - Communication.MAX_TCP_IDLE_MILLISECONDS) {
							NotificationsController.showFailureForSeconds("The TCP connection was idle for too long. It has been closed so another device can connect.", 5, true);
							tcpSocket.close();
							Communication.packet.stopReceivingData();
							break;
						}
					}
					
				} catch(SocketTimeoutException ste) {
					
					// a client never connected, so do nothing and let the loop try again.
					NotificationsController.showVerboseForSeconds("TCP socket timed out while waiting for a connection.", 5, true);
					
				} catch(IOException ioe) {
					
					// problem while accepting the socket connection, or getting the input stream
					NotificationsController.showFailureForSeconds("TCP connection failed.", 5, false);
					try { tcpSocket.close(); } catch(Exception e2) {}
					try { tcpServer.close(); } catch(Exception e2) {}
					SwingUtilities.invokeLater(() -> disconnect()); // invokeLater to prevent a deadlock
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
		
		Communication.packet.stopReceivingData();

		Communication.tcpConnected = false;
		notifyConnectionListeners();
		
	}
	
	static Thread udpServerThread;
	
	/**
	 * Spawns a UDP server and shows a DataStructureWindow if necessary.
	 * 
	 * @param parentWindow    If not null, a DataStructureWindow will be shown and centered on this JFrame.
	 */
	private static void startUdpServer(JFrame parentWindow) {
		
		udpServerThread = new Thread(() -> {
			
			DatagramSocket udpServer = null;
			PipedOutputStream stream = null;
			PipedInputStream inputStream = null;
			
			// start the UDP server
			try {
				udpServer = new DatagramSocket(Communication.tcpUdpPort);
				udpServer.setSoTimeout(1000);
				stream = new PipedOutputStream();
				inputStream = new PipedInputStream(stream);
			} catch (Exception e) {
				NotificationsController.showFailureForSeconds("Unable to start the UDP server. Make sure another program is not already using port " + Communication.tcpUdpPort + ".", 5, false);
				try { udpServer.close(); stream.close(); inputStream.close(); } catch(Exception e2) {}
				SwingUtilities.invokeLater(() -> disconnect()); // invokeLater to prevent a deadlock
				return;
			}
			
			Communication.udpConnected = true;
			notifyConnectionListeners();
			
			if(parentWindow != null)
				Communication.packet.showDataStructureWindow(parentWindow, false);
				
			int oldSampleCount = DatasetsController.getSampleCount();
			Timer t = new Timer(100, event -> {
				if(DatasetsController.getSampleCount() == oldSampleCount)
					NotificationsController.showSuccessUntil("The UDP server is running. Send telemetry to " + Communication.localIp + ":" + Communication.tcpUdpPort, () -> DatasetsController.getSampleCount() > oldSampleCount, true);
				else
					NotificationsController.showVerboseForSeconds("The UDP server is running and receiving telemetry.", 5, true);
			});
			t.setRepeats(false);
			t.start();
			
			Communication.packet.startReceivingData(inputStream);
			
			// listen for packets
			byte[] rx_buffer = new byte[Communication.MAX_UDP_PACKET_SIZE];
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
					NotificationsController.showFailureForSeconds("UDP packet error.", 5, false);
					try { inputStream.close(); } catch(Exception e) {}
					try { stream.close(); }      catch(Exception e) {}
					try { udpServer.close(); }   catch(Exception e) {}
					SwingUtilities.invokeLater(() -> disconnect()); // invokeLater to prevent a deadlock
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
		
		Communication.packet.stopReceivingData();
		
		Communication.udpConnected = false;
		notifyConnectionListeners();
		
	}
	
	/**
	 * Starts transmission of the test data stream.
	 * 
	 * @param parentWindow    If not null, a DataStructureWindow will be shown and centered on this JFrame.
	 */
	private static void startTester(JFrame parentWindow) {
		
		setSampleRate(10000);
		setBaudRate(9600);
		
		Tester.populateDataStructure();
		Tester.startTransmission();

		Communication.testConnected = true;
		notifyConnectionListeners();
		
		if(parentWindow != null)
			Communication.packet.showDataStructureWindow(parentWindow, true);
		
	}
	
	/**
	 * Stops transmission of the test data stream.
	 */
	private static void stopTester() {
		
		Tester.stopTransmission();
		
		Communication.testConnected = false;
		notifyConnectionListeners();
		
	}
	
}
