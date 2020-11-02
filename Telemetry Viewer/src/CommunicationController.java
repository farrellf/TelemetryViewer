import java.awt.Color;
import java.io.File;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.function.Consumer;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import com.fazecast.jSerialComm.SerialPort;

/**
 * CommunicationController receives a stream of telemetry (via UART/TCP/UDP/File) and if checksums are used, discards bad packets.
 * CommunicationController and DatasetsController then processes the received telemetry (extracting numbers/bools/enums, and storing them in memory or on disk.)
 * ChartsController reads telemetry from memory or disk, and visualizes it on screen.
 */
public class CommunicationController {
	
	public final static String PORT_UART             = "UART";             // telemetry received from a serial port
	public final static String PORT_TCP              = "TCP";              // telemetry received from a TCP client (we are the server)
	public final static String PORT_UDP              = "UDP";              // telemetry received from UDP packets (we listen for them)
	public final static String PORT_DEMO_MODE        = "Demo Mode";        // dummy mode that generates test waveforms
	public final static String PORT_STRESS_TEST_MODE = "Stress Test Mode"; // dummy mode that floods the PC with simulated telemetry as fast as possible
	public final static String PORT_FILE             = "File";             // telemetry imported from a CSV log file
	
	// volatile fields shared with threads
	private static Thread receiverThread;
	private static Thread processorThread;
	private static volatile boolean connected = false;
	private static volatile String importFilePath = null;
	private static volatile boolean dataStructureDefined = false;
	public static final byte syncWord = (byte) 0xAA;
	
	private static String port = PORT_UART;
	private static String portUsedBeforeImport = null;
	private static boolean csvMode = true; // false = binary mode
	private static int sampleRate = 10000;
	private static int uartBaudRate = 9600;
	private static int tcpUdpPort = 8080;
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
	 * @param newPort    One of the options from getPorts() or CommunicationController.PORT_FILE.
	 */
	public static void setPort(String newPort) {
		
		// ignore if unchanged
		if(newPort.equals(port))
			return;
		
		// sanity check
		if(!newPort.startsWith(PORT_UART + ": ") &&
		   !newPort.equals(PORT_TCP) &&
		   !newPort.equals(PORT_UDP) &&
		   !newPort.equals(PORT_DEMO_MODE) &&
		   !newPort.equals(PORT_STRESS_TEST_MODE) &&
		   !newPort.equals(PORT_FILE))
			return;
		
		// prepare
		if(isConnected())
			disconnect(null);
		
		// if leaving a test mode, reset the data structure
		if(port == PORT_DEMO_MODE && newPort != PORT_DEMO_MODE)
			DatasetsController.removeAllDatasets();
		if(port == PORT_STRESS_TEST_MODE && newPort != PORT_STRESS_TEST_MODE)
			DatasetsController.removeAllDatasets();
		
		// set and update the GUI
		if(port != PORT_FILE && newPort.equals(PORT_FILE))
			portUsedBeforeImport = port;
		port = newPort;
		CommunicationView.instance.setPort(newPort);
		
	}
	
	/**
	 * @return    The current port (one of the options from getPorts(), or CommunicationController.PORT_FILE.)
	 */
	public static String getPort() {
		
		return port;
		
	}
	
	/**
	 * Gets names for every supported port (every UART + TCP + UDP + Demo Mode + Stress Test Mode.)
	 *  
	 * @return    A String[] of port names.
	 */
	public static String[] getPorts() {
		
		SerialPort[] ports = SerialPort.getCommPorts();
		String[] names = new String[ports.length + 4];
		
		for(int i = 0; i < ports.length; i++)
			names[i] = "UART: " + ports[i].getSystemPortName();
		
		names[names.length - 4] = PORT_TCP;
		names[names.length - 3] = PORT_UDP;
		names[names.length - 2] = PORT_DEMO_MODE;
		names[names.length - 1] = PORT_STRESS_TEST_MODE;
		
		port = names[0];
		
		return names;
		
	}
	
	/**
	 * Sets the packet type and updates the GUI.
	 * If a connection currently exists, it will be closed first.
	 * If the packet type is changing, the data structure will be reset (which also removes all charts.)
	 * 
	 * @param isCsvMode    True for CSV mode, or false for Binary mode.
	 */
	public static void setPacketTypeCsv(boolean isCsvMode) {
		
		// ignore if unchanged
		if((isCsvMode && csvMode) || (!isCsvMode && !csvMode))
			return;
		
		// prepare
		if(isConnected())
			disconnect(null);
		
		// set and update the GUI
		csvMode = isCsvMode;
		DatasetsController.removeAllDatasets();
		CommunicationView.instance.setPacketTypeCsv(isCsvMode);
		
	}
	
	/**
	 * @return    True for CSV mode, or false for binary mode.
	 */
	public static boolean isPacketTypeCsv() {
		
		return csvMode;
		
	}
	
	/**
	 * @return    The GUI for defining the data structure.
	 */
	public static JPanel getDataStructureGui() {
		
		return csvMode ? DataStructureCsvView.getUpdatedGui() : DataStructureBinaryView.getUpdatedGui();
		
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
	public static String getLocalIpAddress() {
		
		return localIp + ":" + tcpUdpPort;
		
	}
	
	/**
	 * @return    True if currently connected.
	 */
	public static boolean isConnected() {
		
		return connected;
		
	}
	
	/**
	 * Specifies if the data structure has been defined, which would allow incoming data to be processed.
	 * 
	 * @param isDefined    True if the data structure has been defined.
	 */
	public static void setDataStructureDefined(boolean isDefined) {
		
		dataStructureDefined = isDefined;
		
	}
	
	/**
	 * @return    True if the data structure has been defined.
	 */
	public static boolean isDataStructureDefined() {
		
		return dataStructureDefined;
		
	}
	
	/**
	 * Spawns a new thread that starts processing received telemetry packets.
	 * 
	 * @param stream    Bytes of received telemetry.
	 */
	private static void startProcessingTelemetry(SharedByteStream stream) {
		
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
			List<Dataset> datasets = DatasetsController.getDatasetsList();
			
			// if no telemetry after 100ms, notify the user
			String waitingForTelemetry = getPort().startsWith(PORT_UART) ? getPort().substring(6) + " is connected. Send telemetry." :
			                             getPort().equals(PORT_TCP)      ? "The TCP server is running. Send telemetry to " + getLocalIpAddress() :
			                             getPort().equals(PORT_UDP)      ? "The UDP listener is running. Send telemetry to " + getLocalIpAddress() : "";
			String receivingTelemetry  = getPort().startsWith(PORT_UART) ? getPort().substring(6) + " is connected and receiving telemetry." :
			                             getPort().equals(PORT_TCP)      ? "The TCP server is running and receiving telemetry." :
			                             getPort().equals(PORT_UDP)      ? "The UDP listener is running and receiving telemetry." : "";
			int oldSampleCount = DatasetsController.getSampleCount();
			Timer t = new Timer(100, event -> {
				
				if(getPort().equals(PORT_DEMO_MODE) || getPort().equals(PORT_STRESS_TEST_MODE))
					return;
				
				if(isConnected()) {
					if(DatasetsController.getSampleCount() == oldSampleCount)
						NotificationsController.showHintUntil(waitingForTelemetry, () -> DatasetsController.getSampleCount() > oldSampleCount, true);
					else
						NotificationsController.showVerboseForSeconds(receivingTelemetry, 5, true);
				}
				
			});
			t.setRepeats(false);
			t.start();
			
			if(csvMode) {
				
				// prepare for CSV mode
				stream.setPacketSize(0);
				int maxLocation = 0;
				for(Dataset d : datasets)
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
						int sampleNumber = DatasetsController.getSampleCount();
						for(Dataset d : datasets)
							d.setSample(sampleNumber, numberForLocation[d.location]);
						DatasetsController.incrementSampleCount();
						
					} catch(NumberFormatException | NullPointerException | ArrayIndexOutOfBoundsException e1) {
						
						NotificationsController.showFailureForSeconds("A corrupt or incomplete telemetry packet was received:\n\"" + line + "\"", 5, false);
						
					} catch(InterruptedException e2) {
						
						return;
						
					}
					
				}
				
			} else {
				
				// prepare for binary mode 
				int packetLength = 0; // INCLUDING the sync word and optional checksum
				if(DatasetsController.checksumProcessor != null)
					packetLength = DatasetsController.checksumProcessorOffset + DatasetsController.checksumProcessor.getByteCount();
				else
					for(Dataset d : datasets)
						if(d.location + d.processor.getByteCount() - 1 > packetLength)
							packetLength = d.location + d.processor.getByteCount();
				stream.setPacketSize(packetLength);
				
				// use multiple threads to process incoming data in parallel, with each thread parsing up to 8 blocks at a time
				final int THREAD_COUNT = Runtime.getRuntime().availableProcessors();
				final int MAX_BLOCK_COUNT_PER_THREAD = 8;
				CyclicBarrier allThreadsDone = new CyclicBarrier(THREAD_COUNT + 1);
				Parser[] parsingThreads = new Parser[THREAD_COUNT];
				for(int i = 0; i < THREAD_COUNT; i++)
					parsingThreads[i] = new Parser(datasets, packetLength, MAX_BLOCK_COUNT_PER_THREAD, allThreadsDone);
				
				while(true) {
					
					try {
						
						if(Thread.interrupted())
							throw new InterruptedException();
						
						// get all received telemetry packets, stopping early if there is a loss of sync or bad checksum
						SharedByteStream.PacketsBuffer packets = stream.readPackets(syncWord);
						
						// process the received telemetry packets
						while(packets.count > 0) {
							
							int sampleNumber = DatasetsController.getSampleCount();
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
									DatasetsController.incrementSampleCountBlock();
								
							} else {
								
								// process packets individually
								for(Dataset dataset : datasets) {
									float rawNumber = dataset.processor.extractValue(packets.buffer, packets.offset + dataset.location);
									dataset.setSample(sampleNumber, rawNumber);
								}
								DatasetsController.incrementSampleCount();
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
			
			minimumValue = new float[maxBlockCount][datasets.size()];
			maximumValue = new float[maxBlockCount][datasets.size()];
			
			newData = new CyclicBarrier(2);
			thread = new Thread(() -> {
				
				while(true) {
					
					try {
						
						// wait for data to parse
						newData.await();
						
						float[][] slots = new float[datasets.size()][];
							
						// parse each packet of each block
						for(int blockN = 0; blockN < blockCount; blockN++) {
							
							for(int datasetN = 0; datasetN < datasets.size(); datasetN++)
								slots[datasetN] = datasets.get(datasetN).getSlot(firstSampleNumber + (blockN * StorageFloats.BLOCK_SIZE));
							
							int slotOffset = (firstSampleNumber + (blockN * StorageFloats.BLOCK_SIZE)) % StorageFloats.SLOT_SIZE;
							float[] minVal = minimumValue[blockN];
							float[] maxVal = maximumValue[blockN];
							for(int packetN = 0; packetN < StorageFloats.BLOCK_SIZE; packetN++) {
								
								for(int datasetN = 0; datasetN < datasets.size(); datasetN++) {
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
						for(int datasetN = 0; datasetN < datasets.size(); datasetN++)
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
	private static void stopProcessingTelemetry() {
		
		if(processorThread != null && processorThread.isAlive()) {
			processorThread.interrupt();
			while(processorThread.isAlive()); // wait
		}
		
	}
	
	/**
	 * Connects to the device.
	 * 
	 * @param quiet    If true, don't show the Data Structure GUI and don't show hint notifications.
	 */
	public static void connect(boolean quiet) {
		
		NotificationsController.removeIfConnectionRelated();
		
		dataStructureDefined = false;
		
		     if(port.startsWith(PORT_UART + ": "))  connectToUart(quiet);
		else if(port.equals(PORT_TCP))              startTcpServer(quiet);
		else if(port.equals(PORT_UDP))              startUdpListener(quiet);
		else if(port.equals(PORT_DEMO_MODE))        startDemo(quiet);
		else if(port.equals(PORT_STRESS_TEST_MODE)) startStressTest();
		else if(port.equals(PORT_FILE))             importCsvFile();
		
	}
	
	/**
	 * Disconnects from the device and removes any connection-related Notifications.
	 * This method blocks until disconnected, so it should not be called directly from the receiver thread.
	 * 
	 * @param errorMessage    If not null, show this as a Notification until a connection is attempted. 
	 */
	public static void disconnect(String errorMessage) {
		
		boolean wasConnected = isConnected();
		
		NotificationsController.removeIfConnectionRelated();
		if(errorMessage != null)
			NotificationsController.showFailureUntil(errorMessage, () -> false, true);
		CommunicationView.instance.setConnected(false);
		Main.hideDataStructureGui();
		
		if(DatasetsController.getSampleCount() > 0)
			CommunicationView.instance.allowExporting(true);
		
		if(wasConnected) {
			
			// tell the receiver thread to terminate by setting the boolean AND interrupting the thread because
			// interrupting the thread might generate an IOException, but we don't want to report that as an error
			connected = false;
			if(receiverThread.isAlive()) {
				receiverThread.interrupt();
				while(receiverThread.isAlive()); // wait
			}
			
			if(port.equals(PORT_FILE)) {
				CommunicationView.instance.allowImporting(true);
				importingAllowed = true;
				setPort(portUsedBeforeImport); // switch back to the original port, so "File" is removed from the port combobox
			}

			SwingUtilities.invokeLater(() -> { // invokeLater so this if() fails when importing a layout that has charts
				if(ChartsController.getCharts().isEmpty() && !CommunicationController.isConnected())
					NotificationsController.showHintUntil("Start by connecting to a device or opening a file by using the buttons below.", () -> !ChartsController.getCharts().isEmpty(), true);
			});
			
		}
		
	}
	
	/**
	 * Imports samples from a CSV file.
	 */
	private static void importCsvFile() {
		
		CommunicationView.instance.allowImporting(false);
		CommunicationView.instance.allowExporting(false);
		DatasetsController.removeAllData();
		importingAllowed = false;
		
		receiverThread = new Thread(() -> {
			
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
				int sampleNumber = DatasetsController.getSampleCount();
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
						DatasetsController.getDatasetByIndex(columnN - 2).setConvertedSample(sampleNumber, Float.parseFloat(tokens[columnN]));
					sampleNumber++;
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
		
		receiverThread.setPriority(Thread.MAX_PRIORITY);
		receiverThread.setName("File Import Thread");
		receiverThread.start();
		
	}
	
	/**
	 * Causes the file import thread to finish importing the file as fast as possible (instead of using a real-time playback speed.)
	 */
	public static void finishImportingFile() {
		
		if(receiverThread != null && receiverThread.isAlive())
			receiverThread.interrupt();
		
	}
	
	/**
	 * Connects to a serial port and shows the DataStructureGui if necessary.
	 * 
	 * @param quiet    If true, don't show the Data Structure GUI and don't show hint notifications.
	 */
	private static void connectToUart(boolean quiet) {
			
		SerialPort uartPort = SerialPort.getCommPort(port.substring(6)); // trim the leading "UART: "
		uartPort.setBaudRate(uartBaudRate);
		uartPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, 0, 0);
		
		// try 3 times before giving up, because some Bluetooth UARTs have trouble connecting
		if(!uartPort.openPort() && !uartPort.openPort() && !uartPort.openPort()) {
			SwingUtilities.invokeLater(() -> disconnect("Unable to connect to " + port + "."));
			return;
		}
		
		CommunicationView.instance.setConnected(true);
		connected = true;

		if(!quiet)
			Main.showDataStructureGui();
		
		receiverThread = new Thread(() -> {
			
			InputStream uart = uartPort.getInputStream();
			SharedByteStream stream = new SharedByteStream();
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
	
	/**
	 * Spawns a TCP server and shows the DataStructureGui if necessary.
	 * 
	 * @param quiet    If true, don't show the Data Structure GUI and don't show hint notifications.
	 */
	private static void startTcpServer(boolean quiet) {
		
		receiverThread = new Thread(() -> {
			
			ServerSocket tcpServer = null;
			Socket tcpSocket = null;
			SharedByteStream stream = new SharedByteStream();
			
			// start the TCP server
			try {
				tcpServer = new ServerSocket(tcpUdpPort);
				tcpServer.setSoTimeout(1000);
			} catch (Exception e) {
				try { tcpServer.close(); } catch(Exception e2) {}
				SwingUtilities.invokeLater(() -> disconnect("Unable to start the TCP server. Make sure another program is not already using port " + tcpUdpPort + "."));
				return;
			}
			
			CommunicationView.instance.setConnected(true);
			connected = true;
			
			if(!quiet)
				Main.showDataStructureGui();
			
			startProcessingTelemetry(stream);
			
			// listen for a connection
			while(true) {

				try {
					
					if(Thread.interrupted() || !connected)
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
							stream.write(buffer, byteCount);
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
	
	/**
	 * Listens for UDP packets and shows the DataStructureGUI if necessary.
	 * 
	 * @param quiet    If true, don't show the Data Structure GUI and don't show hint notifications.
	 */
	private static void startUdpListener(boolean quiet) {
		
		receiverThread = new Thread(() -> {
			
			DatagramSocket udpServer = null;
			SharedByteStream stream = new SharedByteStream();
			
			// start the UDP server
			try {
				udpServer = new DatagramSocket(tcpUdpPort);
				udpServer.setSoTimeout(1000);
				udpServer.setReceiveBufferSize(67108864); // 64MB
			} catch (Exception e) {
				try { udpServer.close(); } catch(Exception e2) {}
				SwingUtilities.invokeLater(() -> disconnect("Unable to start the UDP server. Make sure another program is not already using port " + tcpUdpPort + "."));
				return;
			}
			
			CommunicationView.instance.setConnected(true);
			connected = true;
			
			if(!quiet)
				Main.showDataStructureGui();
			
			startProcessingTelemetry(stream);
			
			// listen for packets
			byte[] buffer = new byte[MAX_UDP_PACKET_SIZE];
			DatagramPacket udpPacket = new DatagramPacket(buffer, buffer.length);
			while(true) {

				try {
					
					if(Thread.interrupted() || !connected)
						throw new InterruptedException();
					
					udpServer.receive(udpPacket);
					stream.write(buffer, udpPacket.getLength());
					
//					NotificationsController.showVerbose("UDP packet received from a client at " + udpPacket.getAddress().getHostAddress() + ":" + udpPacket.getPort() + ".");
					
				} catch(SocketTimeoutException ste) {
					
					// a client never sent a packet, so do nothing and let the loop try again.
					NotificationsController.showVerboseForSeconds("UDP socket timed out while waiting for a packet.", 5, true);
					
				} catch(IOException ioe) {
					
					// an IOException can occur if an InterruptedException occurs while receiving data
					// let this be detected by the connection test in the loop
					if(!connected)
						continue;
					
					// problem while reading from the socket
					stopProcessingTelemetry();
					try { udpServer.close(); }   catch(Exception e) {}
					SwingUtilities.invokeLater(() -> disconnect("UDP packet error."));
					return;
					
				}  catch(InterruptedException ie) {
					
					stopProcessingTelemetry();
					try { udpServer.close(); }   catch(Exception e) {}
					return;
					
				}
			
			}
			
		});
		
		receiverThread.setPriority(Thread.MAX_PRIORITY);
		receiverThread.setName("UDP Listener Thread");
		receiverThread.start();
		
	}
	
	/**
	 * Floods the PC with simulated telemetry as fast as possible.
	 * This is for performance testing during development.
	 * For most computers the bottleneck will be memory bandwidth, but the CPU/GPU will also be heavily taxed by this test.
	 */
	private static void startStressTest() {
		
		CommunicationController.disconnect(null);
		ChartsController.removeAllCharts();
		DatasetsController.removeAllDatasets();
		
		SettingsController.setTileColumns(6);
		SettingsController.setTileRows(6);
		SettingsController.setTimeFormat("Only Time");
		SettingsController.setTimeFormat24hours(false);
		SettingsController.setTooltipVisibility(true);
		SettingsController.setSmoothScrolling(true);
		SettingsController.setFpsVisibility(false);
		SettingsController.setAntialiasingLevel(1);
		
		CommunicationController.setPort(PORT_STRESS_TEST_MODE);
		CommunicationController.setBaudRate(9600);
		CommunicationController.setPortNumber(8080);
		CommunicationController.setPacketTypeCsv(false);
		CommunicationController.setSampleRate(10000);
		
		DatasetsController.BinaryFieldProcessor processor = null;
		for(DatasetsController.BinaryFieldProcessor p : DatasetsController.binaryFieldProcessors)
			if(p.toString().equals("int16 LSB First"))
				processor = p;
		
		DatasetsController.removeAllDatasets();
		DatasetsController.insertDataset(1, processor, "a", Color.RED,   "", 1, 1);
		DatasetsController.insertDataset(3, processor, "b", Color.GREEN, "", 1, 1);
		DatasetsController.insertDataset(5, processor, "c", Color.BLUE,  "", 1, 1);
		DatasetsController.insertDataset(7, processor, "d", Color.CYAN,  "", 1, 1);
		
		DatasetsController.BinaryChecksumProcessor checksumProcessor = null;
		for(DatasetsController.BinaryChecksumProcessor p : DatasetsController.binaryChecksumProcessors)
			if(p.toString().equals("uint16 Checksum LSB First"))
				checksumProcessor = p;
		DatasetsController.insertChecksum(9, checksumProcessor);
		
		dataStructureDefined = true;
		
		PositionedChart chart = ChartsController.createAndAddChart("Time Domain", 0, 0, 5, 5);
		List<String> chartSettings = new ArrayList<String>();
		chartSettings.add("normal datasets = 1");
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
		chart.importChart(new QueueOfLines(chartSettings));
		
//		SettingsController.setBenchmarkedChart(chart);
		
		Main.window.setExtendedState(JFrame.NORMAL);
		
		receiverThread = new Thread(() -> {

			SharedByteStream stream = new SharedByteStream();
			CommunicationView.instance.setConnected(true);
			connected = true;
			startProcessingTelemetry(stream);

			long bytesSent = 0;
			final int repeat = 300;
			byte[] buff = new byte[11*repeat]; // sync + 4 int16s + checksum
			short a = 0;
			short b = 1;
			short c = 2;
			short d = 3;
			long start = System.currentTimeMillis();

			while(true) {

				try {
					
					if(Thread.interrupted() || !connected)
						throw new InterruptedException();

					int i = 0;
					short checksum = 0;
					
					for(int n = 0; n < repeat; n++) {
						buff[i++] = (byte) 0xAA;
						buff[i++] = (byte) (a >> 0);
						buff[i++] = (byte) (a >> 8);
						buff[i++] = (byte) (b >> 0);
						buff[i++] = (byte) (b >> 8);
						buff[i++] = (byte) (c >> 0);
						buff[i++] = (byte) (c >> 8);
						buff[i++] = (byte) (d >> 0);
						buff[i++] = (byte) (d >> 8);
						
						checksum = 0; // note: the checksum is a uint16, but java does not support unsigned math, so "& 0xFF" is used below to work around that
						checksum += (buff[i-7] << 8) | (buff[i-8] & 0xFF);
						checksum += (buff[i-5] << 8) | (buff[i-6] & 0xFF);
						checksum += (buff[i-3] << 8) | (buff[i-4] & 0xFF);
						checksum += (buff[i-1] << 8) | (buff[i-2] & 0xFF);
						buff[i++] = (byte) ((checksum >> 0) & 0xFF);
						buff[i++] = (byte) ((checksum >> 8) & 0xFF);
					
						a++;
						b++;
						c++;
						d++;
					}
					
					stream.write(buff, buff.length);
					bytesSent += buff.length;
					if(bytesSent % (500000*buff.length) == 0) {
						long end = System.currentTimeMillis();
						System.out.println(String.format("%1.1f Mbps, %d pps", (bytesSent / (double)(end-start) * 1000.0 * 8.0 / 1000000), (int)(bytesSent / 11 / (double)(end-start) * 1000.0)));
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
	
	/**
	 * Starts transmission of a test data stream, and shows the DataStructureGUI if necessary.
	 * 
	 * @param quiet    If true, don't show the Data Structure GUI and don't show hint notifications.
	 */
	private static void startDemo(boolean quiet) {
		
		// force specific settings
		setPacketTypeCsv(true);
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

		// simulate the transmission of 4 numbers every 100us.
		// the first three numbers are pseudo random, and scaled to form a sort of sawtooth waveform.
		// the fourth number is a 1kHz sine wave.
		receiverThread = new Thread(() -> {
			
			double counter = 0;
			int sampleNumber = DatasetsController.getSampleCount();
			
			while(true) {
				float scalar = ((System.currentTimeMillis() % 30000) - 15000) / 100.0f;
				float[] newSamples = new float[] {
					(System.nanoTime() / 100 % 100) * scalar * 1.0f / 14000f,
					(System.nanoTime() / 100 % 100) * scalar * 0.8f / 14000f,
					(System.nanoTime() / 100 % 100) * scalar * 0.6f / 14000f
				};
				for(int i = 0; i < 10; i++) {
					DatasetsController.getDatasetByIndex(0).setSample(sampleNumber, newSamples[0]);
					DatasetsController.getDatasetByIndex(1).setSample(sampleNumber, newSamples[1]);
					DatasetsController.getDatasetByIndex(2).setSample(sampleNumber, newSamples[2]);
					DatasetsController.getDatasetByIndex(3).setSample(sampleNumber, (float) Math.sin(2 * Math.PI * 1000 * counter));
					counter += 0.0001;
					sampleNumber++;
					DatasetsController.incrementSampleCount();
				}
				
				try {
					Thread.sleep(1);
				} catch(InterruptedException e) {
					return;
				}
			}
			
		});
		
		receiverThread.setPriority(Thread.MAX_PRIORITY);
		receiverThread.setName("Demo Waveform Simulator Thread");
		receiverThread.start();
		
		CommunicationView.instance.setConnected(true);
		connected = true;
		
		// show the data structure window
		if(!quiet)
			Main.showDataStructureGui();
		
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
	static void exportCsvFile(String filepath, Consumer<Double> progressTracker) { // FIXME improve exporting to use getValues()!
		
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
				// periodically update the progress tracker
				if(i % 1024 == 0)
					progressTracker.accept((double) i / (double) sampleCount);
				
				logFile.print(i + "," + DatasetsController.getTimestamp(i));
				for(int n = 0; n < datasetsCount; n++)
					logFile.print("," + Float.toString(DatasetsController.getDatasetByIndex(n).getSample(i)));
				logFile.println();
				
			}
			
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
			file.println("\tpacket type = "         + (CommunicationController.isPacketTypeCsv() ? "CSV" : "Binary"));
			file.println("\tsample rate = "         + CommunicationController.getSampleRate());
			file.println("");
			
			file.println(DatasetsController.getDatasetsCount() + " Data Structure Locations:");
			
			for(Dataset dataset : DatasetsController.getDatasetsList()) {
				
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
			file.println("\tlocation = " + DatasetsController.checksumProcessorOffset);
			file.println("\tchecksum processor = " + (DatasetsController.checksumProcessor == null ? "null" : DatasetsController.checksumProcessor.toString()));
			
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
			if(!packetType.equals("CSV") && !packetType.equals("Binary"))
				throw new AssertionError("Invalid packet type.");
			int sampleRate    = ChartUtils.parseInteger(lines.remove(), "sample rate = %d");
			ChartUtils.parseExact(lines.remove(), "");
			
			CommunicationController.setPort(portName);
			CommunicationController.setBaudRate(baudRate);
			CommunicationController.setPortNumber(tcpUdpPort);
			CommunicationController.setPacketTypeCsv(packetType.equals("CSV"));
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
				DatasetsController.BinaryFieldProcessor processor = null;
				for(DatasetsController.BinaryFieldProcessor p : DatasetsController.binaryFieldProcessors)
					if(p.toString().equals(processorName))
						processor = p;
				
				DatasetsController.insertDataset(location, processor, name, color, unit, conversionFactorA, conversionFactorB);
				
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
				DatasetsController.BinaryChecksumProcessor processor = null;
				for(DatasetsController.BinaryChecksumProcessor p : DatasetsController.binaryChecksumProcessors)
					if(p.toString().equals(checksumName))
						processor = p;
				DatasetsController.insertChecksum(checksumOffset, processor);
			}
			
			dataStructureDefined = true;

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
