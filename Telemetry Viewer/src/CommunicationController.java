import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import com.fazecast.jSerialComm.SerialPort;

public class CommunicationController {

	static List<Consumer<String>>  portListeners =           new ArrayList<Consumer<String>>();
	static List<Consumer<String>>  packetTypeListeners =     new ArrayList<Consumer<String>>();
	static List<Consumer<Integer>> sampleRateListeners =     new ArrayList<Consumer<Integer>>();
	static List<Consumer<Integer>> baudRateListeners =       new ArrayList<Consumer<Integer>>();
	static List<Consumer<Integer>> portNumberListeners =     new ArrayList<Consumer<Integer>>(); // TCP/UDP port
	static List<Consumer<Boolean>> connectionListeners =     new ArrayList<Consumer<Boolean>>(); // true = connected or listening
	static List<Consumer<Boolean>> connectionLostListeners = new ArrayList<Consumer<Boolean>>(); // true = lost,  false = reserved for future use
	
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
	 * @param newPort    Communication.MODE_UART + ": port name" or .MODE_TCP or .MODE_UDP or .MODE_TEST
	 */
	public static void setPort(String newPort) {
		
		// sanity check
		if(!newPort.startsWith(Communication.PORT_UART + ": ") &&
		   !newPort.equals(Communication.PORT_TCP) &&
		   !newPort.equals(Communication.PORT_UDP) &&
		   !newPort.equals(Communication.PORT_TEST))
			return;
		
		// prepare
		disconnect();
		
		// set and notify
		Communication.port = newPort;
		for(Consumer<String> listener : portListeners)
			listener.accept(Communication.port);
		
	}
	
	/**
	 * @return    The current port (Communication.MODE_UART + ": port name" or .MODE_TCP or .MODE_UDP or .MODE_TEST)
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
		String[] names = new String[ports.length + 3];
		
		for(int i = 0; i < ports.length; i++)
			names[i] = "UART: " + ports[i].getSystemPortName();
		
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
	 * @param newType    Communication.PACKET_TYPE_CSV or .PACKET_TYPE_BINARY
	 */
	public static void setPacketType(String newType) {
		
		// sanity check
		if(!newType.equals(Communication.csvPacket.toString()) &&
		   !newType.equals(Communication.binaryPacket.toString()))
			return;
		
		// prepare
		disconnect();
		Controller.removeAllCharts();
		Controller.removeAllDatasets();
		
		// set and notify
		Communication.packet = newType.equals(Communication.csvPacket.toString()) ? Communication.csvPacket : Communication.binaryPacket;
		Communication.packet.clear();
		for(Consumer<String> listener : packetTypeListeners)
			listener.accept(Communication.packet.toString());
		
	}
	
	/**
	 * @return    The current packet type (Communication.PACKET_TYPE_CSV or .PACKET_TYPE_BINARY)
	 */
	public static String getPacketType() {
		
		return Communication.packet.toString();
		
	}
	
	/**
	 * @return    A String[] of the supported packet types.
	 */
	public static String[] getPacketTypes() {
		
		return new String[] {Communication.csvPacket.toString(), Communication.binaryPacket.toString()};
		
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
	 * Registers a listener that will be notified when the TCP/UDP port number changes, and triggers an event to ensure the GUI is in sync.
	 * 
	 * @param listener    The listener to be notified.
	 */
	public static void addPortNumberListener(Consumer<Integer> listener) {
		
		portNumberListeners.add(listener);
		setPortNumber(Communication.tcpUdpPort);
		
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
		
	}
	
	/**
	 * Registers a listener that will be notified when a connection is unexpectedly lost.
	 * 
	 * @param listener    The listener to be notified.
	 */
	public static void addConnectionLostListener(Consumer<Boolean> listener) {
		
		connectionLostListeners.add(listener);
		
	}
	
	/**
	 * Notifies all registered listeners about the an unexpected loss of connection.
	 */
	private static void notifyConnectionLostListeners() {
		
		for(Consumer<Boolean> listener : connectionLostListeners)
			listener.accept(true);
		
	}
	
	/**
	 * @return    True if a connection exists.
	 */
	public static boolean isConnected() {
		
		if(Communication.port.startsWith(Communication.PORT_UART))  return Communication.uartConnected;
		else if(Communication.port.equals(Communication.PORT_TCP))  return Communication.tcpConnected;
		else if(Communication.port.equals(Communication.PORT_UDP))  return Communication.udpConnected;
		else if(Communication.port.equals(Communication.PORT_TEST)) return Communication.testConnected;
		else                                                        return false;
		
	}
	
	/**
	 * Connects to the DUT.
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
		
	}
	
	/**
	 * Disconnects from the DUT.
	 * 
	 * @param connectionLost    True if the connection was unexpectly lost.
	 */
	public static void disconnect() {
		
		if(Communication.port.startsWith(Communication.PORT_UART + ": "))
			disconnectFromUart();
		else if(Communication.port.equals(Communication.PORT_TCP))
			stopTcpServer();
		else if(Communication.port.equals(Communication.PORT_UDP))
			stopUdpServer();
		else if(Communication.port.equals(Communication.PORT_TEST))
			stopTester();
		
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
					disconnect();
					notifyConnectionLostListeners();
					return;
				}
			}
		}
		
		Communication.uartConnected = true;
		notifyConnectionListeners();
		
		if(parentWindow != null)
			Communication.packet.showDataStructureWindow(parentWindow, false);
		
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
				tcpServer.setSoTimeout(250);
			} catch (Exception e) {
				System.err.println("Unable to start the TCP server. Maybe another program is already using port " + Communication.tcpUdpPort + "?");
				try { tcpServer.close(); } catch(Exception e2) {}
				SwingUtilities.invokeLater(() -> disconnect()); // invokeLater to prevent a deadlock
				notifyConnectionLostListeners();
				return;
			}
			
			Communication.tcpConnected = true;
			notifyConnectionListeners();
			
			if(parentWindow != null) {
				Communication.packet.showDataStructureWindow(parentWindow, false);
				JOptionPane.showMessageDialog(parentWindow, "The TCP server is running. Send telemetry to " + Communication.localIp + " :" + Communication.tcpUdpPort);
			}
			
			// wait for a connection
			while(true) {

				try {
					
					if(Thread.interrupted())
						throw new InterruptedException();
					
					tcpSocket = tcpServer.accept();
					tcpSocket.setSoTimeout(5000); // each valid packet of data must take <5 seconds to arrive
					Communication.packet.startReceivingData(tcpSocket.getInputStream());
					
					System.out.println("TCP connection established with a client at " + tcpSocket.getRemoteSocketAddress() + ".");
//					while(true) {
//						if(Thread.interrupted())
//							throw new InterruptedException(); // never return without first closing the socket and server!
//					}
					// enter an infinite loop that checks for inactivity. if the tcp port is idle for >10 seconds, abandon it so another device can try to connect.
					long previousTimestamp = System.currentTimeMillis();
					int previousSampleNumber = Controller.getSamplesCount();
					while(true) {
						Thread.sleep(1000);
						int sampleNumber = Controller.getSamplesCount();
						long timestamp = System.currentTimeMillis();
						if(sampleNumber > previousSampleNumber) {
							previousSampleNumber = sampleNumber;
							previousTimestamp = timestamp;
						} else if(previousTimestamp < timestamp - Communication.MAX_TCP_IDLE_MILLISECONDS){
							tcpSocket.close();
							Communication.packet.stopReceivingData();
							notifyConnectionLostListeners();
							break;
						}						
					}
					
				} catch(SocketTimeoutException ste) {
					
					// a client never connected, so do nothing and let the loop try again.
					System.out.println("TCP socket timed out while waiting for a connection.");
					
				} catch(IOException ioe) {
					
					// problem while accepting the socket connection, or getting the input stream
					System.err.println("TCP connection failed.");
					try { tcpSocket.close(); } catch(Exception e2) {}
					try { tcpServer.close(); } catch(Exception e2) {}
					SwingUtilities.invokeLater(() -> disconnect()); // invokeLater to prevent a deadlock
					notifyConnectionLostListeners();
					return;
					
				}  catch(InterruptedException ie) {
					
					// thread got interrupted, so exit.
					System.err.println("The TCP Server thread is stopping.");
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
				udpServer.setSoTimeout(250);
				stream = new PipedOutputStream();
				inputStream = new PipedInputStream(stream);
			} catch (Exception e) {
				System.err.println("Unable to start the UDP server.");
				udpServer.close();
				SwingUtilities.invokeLater(() -> disconnect()); // invokeLater to prevent a deadlock
				notifyConnectionLostListeners();
				return;
			}
			
			Communication.udpConnected = true;
			notifyConnectionListeners();
			
			if(parentWindow != null) {
				Communication.packet.showDataStructureWindow(parentWindow, false);
				JOptionPane.showMessageDialog(parentWindow, "The UDP server is running. Send telemetry to " + Communication.localIp + " :" + Communication.tcpUdpPort);
			}
			
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
					
					System.out.println("UDP packet received from a client at " + udpPacket.getAddress().getHostAddress() + ".");
					
				} catch(SocketTimeoutException ste) {
					
					// a client never sent a packet, so do nothing and let the loop try again.
					System.out.println("UDP socket timed out while waiting for a packet.");
					
				} catch(IOException ioe) {
					
					// problem while reading from the socket, or while putting data into the stream
					System.err.println("UDP packet error.");
					try { inputStream.close(); } catch(Exception e) {}
					try { stream.close(); }      catch(Exception e) {}
					try { udpServer.close(); }   catch(Exception e) {}
					SwingUtilities.invokeLater(() -> disconnect()); // invokeLater to prevent a deadlock
					notifyConnectionLostListeners();
					return;
					
				}  catch(InterruptedException ie) {
					
					// thread got interrupted while waiting for a connection, so exit.
					System.err.println("The UDP Server thread is stopping.");
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
		Controller.removeAllCharts();
		Controller.removeAllDatasets();
		
		Communication.testConnected = false;
		notifyConnectionListeners();
		
	}
	
}
