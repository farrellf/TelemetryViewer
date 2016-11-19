import java.awt.Color;
import java.awt.Toolkit;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.JOptionPane;

import com.fazecast.jSerialComm.SerialPort;

/**
 * Handles all non-GUI logic and manages access to the Model (the data).
 */
public class Controller {
	
	static List<GridChangedListener> gridChangedListeners = new ArrayList<GridChangedListener>();
	static List<SerialPortListener>   serialPortListeners = new ArrayList<SerialPortListener>(); 
	static List<ChartListener>             chartListeners = new ArrayList<ChartListener>();
	static SerialPort port;
	static Thread serialPortThread;
	static AtomicBoolean dataStructureDefined = new AtomicBoolean(false);
	
	static PrintWriter logFile;
	
	/**
	 * @return    The percentage of 100dpi that the screen uses. It's currently rounded to an integer, but future plans will make use of floats.
	 */
	public static float getDisplayScalingFactor() {
		
		return (int) Math.round((double) Toolkit.getDefaultToolkit().getScreenResolution() / 100.0);
		
	}
	
	/**
	 * Registers a listener that will be notified when the ChartsRegion grid size (column or row count) changes.
	 * 
	 * @param listener    The listener to be notified.
	 */
	public static void addGridChangedListener(GridChangedListener listener) {
		
		gridChangedListeners.add(listener);
		
	}
	
	/**
	 * Notifies all registered listeners about a new ChartsRegion grid size.
	 */
	private static void notifyGridChangedListeners() {
		
		for(GridChangedListener listener : gridChangedListeners)
			listener.gridChanged(Model.gridColumns, Model.gridRows);
		
	}
	
	/**
	 * Changes the ChartsRegion grid column count if it is within the allowed range and would not obscure part of an existing chart.
	 * 
	 * @param value    The new column count.
	 */
	public static void setGridColumns(int value) {
		
		boolean chartsObscured = false;
		for(PositionedChart chart : Model.charts)
			if(chart.regionOccupied(value, 0, Model.gridColumns, Model.gridRows))
				chartsObscured = true;
		
		if(value >= Model.gridColumnsMinimum && value <= Model.gridColumnsMaximum && !chartsObscured)
			Model.gridColumns = value;

		notifyGridChangedListeners();
		
	}
	
	/**
	 * @return    The current ChartsRegion grid column count.
	 */
	public static int getGridColumns() {
		
		return Model.gridColumns;
		
	}
	
	/**
	 * Changes the ChartsRegion grid row count if it is within the allowed range and would not obscure part of an existing chart.
	 * 
	 * @param value    The new row count.
	 */
	public static void setGridRows(int value) {
		
		boolean chartsObscured = false;
		for(PositionedChart chart : Model.charts)
			if(chart.regionOccupied(0, value, Model.gridColumns, Model.gridRows))
				chartsObscured = true;
		
		if(value >= Model.gridRowsMinimum && value <= Model.gridRowsMaximum && !chartsObscured)
			Model.gridRows = value;

		notifyGridChangedListeners();
		
	}
	
	/**
	 * @return    The current ChartsRegion grid row count.
	 */
	public static int getGridRows() {
		
		return Model.gridRows;
		
	}
	
	/**
	 * @return    An array of ChartDescriptor's, one for each possible chart type.
	 */
	public static ChartDescriptor[] getChartDescriptors() {
		
		return Model.chartDescriptors;
		
	}
	
	/**
	 * @return    The number of CSV columns or Binary elements described in the data structure.
	 */
	public static int getDatasetsCount() {
		
		return Model.datasets.size();
		
	}
	
	/**
	 * @param location    CSV column number, or Binary packet byte offset. Locations may be sparse.
	 * @return            The Dataset.
	 */
	public static Dataset getDatasetByLocation(int location) {
		
		return Model.datasets.get(location);

	}
	
	/**
	 * @param index    An index between 0 and getDatasetsCount()-1, inclusive.
	 * @return         The Dataset.
	 */
	public static Dataset getDatasetByIndex(int index) {
		
		return (Dataset) Model.datasets.values().toArray()[index];
		
	}
	
	/**
	 * Creates and stores a new Dataset. If a Dataset already exists for the same location, the new Dataset will replace it.
	 * 
	 * @param location             CSV column number, or Binary packet byte offset.
	 * @param processor            BinaryProcessor for the raw samples in the Binary packet. (Ignored in CSV mode.)
	 * @param name                 Descriptive name of what the samples represent.
	 * @param color                Color to use when visualizing the samples.
	 * @param unit                 Descriptive name of how the samples are quantified.
	 * @param conversionFactorA    This many unprocessed LSBs...
	 * @param conversionFactorB    ... equals this many units.
	 */
	public static void insertDataset(int location, BinaryProcessor processor, String name, Color color, String unit, float conversionFactorA, float conversionFactorB) {
		
		Model.datasets.put(location, new Dataset(location, processor, name, color, unit, conversionFactorA, conversionFactorB));
		
	}
	
	/**
	 * Removes all charts and Datasets.
	 */
	public static void removeAllDatasets() {
		
		Controller.removeAllPositionedCharts();
		
		Model.datasets.clear();
		
	}
	
	/**
	 * @return    The Datasets.
	 */
	public static Collection<Dataset> getAllDatasets() {
		
		return Model.datasets.values();
		
	}
	
	/**
	 * Registers a listener that will be notified when the serial port status (connection made or lost) changes.
	 * 
	 * @param listener    The listener to be notified.
	 */
	public static void addSerialPortListener(SerialPortListener listener) {
		
		serialPortListeners.add(listener);
		
	}
	
	private static final int SERIAL_CONNECTION_OPENED = 0;
	private static final int SERIAL_CONNECTION_CLOSED = 1;
	/**
	 * Notifies all registered listeners about a change in the serial port status.
	 * 
	 * @param status    Either SERIAL_CONNECTION_OPENED or SERIAL_CONNECTION_CLOSED.
	 */
	private static void notifySerialPortListeners(int status) {
		
		for(SerialPortListener listener : serialPortListeners)
			if(status == SERIAL_CONNECTION_OPENED)
				listener.connectionOpened(Model.sampleRate, Model.packetType, Model.portName, Model.baudRate);
			else if(status == SERIAL_CONNECTION_CLOSED)
				listener.connectionClosed();
		
	}
	
	/**
	 * @return    A String[] of names for all serial ports that were detected at the time of this function call, plus the "Test" dummy serial port.
	 */
	public static String[] getSerialPortNames() {
		
		SerialPort[] ports = SerialPort.getCommPorts();
		
		String[] names = new String[ports.length + 1];
		for(int i = 0; i < ports.length; i++)
			names[i] = ports[i].getSystemPortName();
		
		names[names.length - 1] = "Test";
		
		return names;
		
	}
	
	/**
	 * @return    An int[] of supported UART baud rates.
	 */
	public static int[] getBaudRates() {
		
		return new int[] {9600, 19200, 38400, 57600, 115200, 230400, 460800, 921600, 1000000, 1500000, 2000000, 3000000};
		
	}
	
	/**
	 * @return    A String[] of descriptions for supported UART packet types.
	 */
	public static String[] getPacketTypes() {
		
		return new String[] {"ASCII CSVs", "Binary"};
		
	}
	
	/**
	 * Connects to a serial port and spawns a new thread to process incoming data.
	 * 
	 * @param sampleRate    Expected samples per second. (Hertz) This is used for FFTs.
	 * @param packetType    One of the Strings from Controller.getPacketTypes()
	 * @param portName      One of the Strings from Controller.getSerialPortNames()
	 * @param baudRate      One of the baud rates from Controller.getBaudRates()
	 */
	@SuppressWarnings("deprecation")
	public static void connectToSerialPort(int sampleRate, String packetType, String portName, int baudRate) {
		
		if(portName.equals("Test")) {
			
			Tester.populateDataStructure();
			Tester.startTransmission();
			
			Model.sampleRate = sampleRate;
			Model.packetType = packetType;
			Model.portName = portName;
			Model.baudRate = 9600;
			notifySerialPortListeners(SERIAL_CONNECTION_OPENED);
			
			return;
			
		} else if(packetType.equals("ASCII CSVs")) {
			
			port = SerialPort.getCommPort(portName);
			port.setBaudRate(baudRate);
			port.setComPortTimeouts(SerialPort.TIMEOUT_SCANNER, 0, 0);
			
			if(!port.openPort()) { // try 3 times before giving up
				if(!port.openPort()) {
					if(!port.openPort()) {
						notifySerialPortListeners(SERIAL_CONNECTION_CLOSED);
						return;
					}
				}
			}
			
			Model.sampleRate = sampleRate;
			Model.packetType = packetType;
			Model.portName = portName;
			Model.baudRate = baudRate;
			
			if(serialPortThread != null && serialPortThread.isAlive())
				serialPortThread.stop();
			
			serialPortThread = new Thread(new Runnable() {
				@Override public void run() {
					
					// wait for the data structure to be defined
					while(!dataStructureDefined.get())
						try { Thread.sleep(10); } catch(Exception e) { }
					
					Scanner scanner = new Scanner(port.getInputStream());
					
					while(scanner.hasNextLine()) {
						
						// stop receiving data if the thread has been interrupted
						if(serialPortThread.isInterrupted())
							break;
						
						try {
							
							String line = scanner.nextLine();
							String[] tokens = line.split(",");
							float[] samples = new float[tokens.length];
							for(int i = 0; i < tokens.length; i++)
								samples[i] = Float.parseFloat(tokens[i]);
							Controller.insertSamples(samples);
							
						} catch(Exception e) { }
						
					}
					scanner.close();
					port.closePort();
					port = null;
					notifySerialPortListeners(SERIAL_CONNECTION_CLOSED);
					
				}
			});
			serialPortThread.setPriority(Thread.MAX_PRIORITY);
			serialPortThread.setName("Serial Port Receiver");
			serialPortThread.start();
			
			notifySerialPortListeners(SERIAL_CONNECTION_OPENED);
			return;
			
		} else if(packetType.equals("Binary")) {
			
			port = SerialPort.getCommPort(portName);
			port.setBaudRate(baudRate);
			port.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, 0, 0);
			
			if(!port.openPort()) { // try 3 times before giving up
				if(!port.openPort()) {
					if(!port.openPort()) {
						notifySerialPortListeners(SERIAL_CONNECTION_CLOSED);
						return;
					}
				}
			}
			
			Model.sampleRate = sampleRate;
			Model.packetType = packetType;
			Model.portName = portName;
			Model.baudRate = baudRate;
			
			if(serialPortThread != null && serialPortThread.isAlive())
				serialPortThread.stop();
			
			serialPortThread = new Thread(new Runnable() {
				@Override public void run() {
					
					byte[] rx_buffer = new byte[1024];
					
					// wait for data structure to be defined
					while(!dataStructureDefined.get())
						try { Thread.sleep(10); } catch(Exception e) { }
					
					// get packet size (includes 1 byte sync word)
					int packetSize = Controller.getBinaryPacketSize();
					float[] samples = new float[Controller.getDatasetsCount()];
					
					while(true) {
						
						// stop receiving data if the thread has been interrupted
						if(serialPortThread.isInterrupted()) {
							port.closePort();
							port = null;
							notifySerialPortListeners(SERIAL_CONNECTION_CLOSED);
							return;
						}
						
						// wait for sync byte of 0xAA
						while(rx_buffer[0] != (byte) 0xAA) // the byte cast is required!
							port.readBytes(rx_buffer, 1);
						
						// get rest of packet after the sync word
						port.readBytes(rx_buffer, packetSize - 1 + 2); // -1 for sync word, +2 for checksum
						
						// extract all samples from the packet
						for(int datasetNumber = 0; datasetNumber < samples.length; datasetNumber++) {
							
							Dataset dataset = Controller.getDatasetByIndex(datasetNumber);
							
							byte[] rawData = new byte[dataset.processor.getByteCount()];
							
							int rx_buffer_index = dataset.location - 1; // -1 for the sync word
							for(int i = 0; i < rawData.length; i++)
								rawData[i] = rx_buffer[rx_buffer_index++];
							
							samples[datasetNumber] = dataset.processor.extractValue(rawData);
							
						}
						
						// calculate the sum
						int wordCount = (packetSize - 1) / 2; // -1 for sync word, /2 for 16bit words
						
						int sum = 0;
						int lsb = 0;
						int msb = 0;
						for(int i = 0; i < wordCount; i++) {
							lsb = 0xFF & rx_buffer[i*2];
							msb = 0xFF & rx_buffer[i*2 + 1];
							sum += (msb << 8 | lsb);
						}
						
						// extract the checksum
						lsb = 0xFF & rx_buffer[wordCount*2];
						msb = 0xFF & rx_buffer[wordCount*2 + 1];
						int checksum = (msb << 8 | lsb);
						
						// add samples to the database if the checksum passed
						sum %= 65535;
						if(sum == checksum)
							Controller.insertSamples(samples);
						else
							System.err.println("checksum failed");
						
					}
				}
			});
			serialPortThread.setPriority(Thread.MAX_PRIORITY);
			serialPortThread.setName("Serial Port Receiver");
			serialPortThread.start();
			
			notifySerialPortListeners(SERIAL_CONNECTION_OPENED);
			return;
			
		}
		
	}
	
	/**
	 * Disconnects from the active serial port, stops the data processing thread, and stops logging.
	 */
	public static void disconnectFromSerialPort() {
		
		dataStructureDefined.set(false);
		
		if(Model.portName.equals("Test")) {
			
			Tester.stopTransmission();
			notifySerialPortListeners(SERIAL_CONNECTION_CLOSED);
			
		} else {		
			
			if(serialPortThread != null)
				serialPortThread.interrupt();
			while(port != null)
				try { Thread.sleep(1); } catch(Exception e) { }
			
		}
		
		if(logFile != null) {
			logFile.close();
			logFile = null;
		}
		
	}
	
	/**
	 * Registers a listener that will be notified when a chart is added or removed.
	 * 
	 * @param listener    The listener to be notified.
	 */
	public static void addChartsListener(ChartListener listener) {
		
		chartListeners.add(listener);
		
	}
	
	/**
	 * Notifies all registered listeners about a new chart.
	 */
	private static void notifyChartListenersOfAddition(PositionedChart chart) {
		
		for(ChartListener listener : chartListeners)
			listener.chartAdded(chart);
		
	}
	
	/**
	 * Notifies all registered Listeners about a removed chart.
	 */
	private static void notifyChartListenersOfRemoval(PositionedChart chart) {
		
		for(ChartListener listener : chartListeners)
			listener.chartRemoved(chart);
		
	}
	
	/**
	 * @param chart    New chart to insert and display.
	 */
	public static void addPositionedChart(PositionedChart chart) {
		
		Model.charts.add(chart);
		notifyChartListenersOfAddition(chart);
		
	}
	
	/**
	 * Removes all charts.
	 */
	public static void removeAllPositionedCharts() {
		
		for(PositionedChart chart : Model.charts)
			notifyChartListenersOfRemoval(chart);
		
		Model.charts.clear();
		
	}
	
	public static List<PositionedChart> getCharts() {
		
		return Model.charts;
		
	}
	
	/**
	 * Checks if a region is available in the ChartsRegion.
	 * 
	 * @param x1    The x-coordinate of a bounding-box corner in the ChartsRegion grid.
	 * @param y1    The y-coordinate of a bounding-box corner in the ChartsRegion grid.
	 * @param x2    The x-coordinate of the opposite bounding-box corner in the ChartsRegion grid.
	 * @param y2    The y-coordinate of the opposite bounding-box corner in the ChartsRegion grid.
	 * @return      True if available, false if not.
	 */
	public static boolean gridRegionAvailable(int x1, int y1, int x2, int y2) {
		
		int topLeftX     = x1 < x2 ? x1 : x2;
		int topLeftY     = y1 < y2 ? y1 : y2;
		int bottomRightX = x2 > x1 ? x2 : x1;
		int bottomRightY = y2 > y1 ? y2 : y1;

		for(PositionedChart chart : Model.charts)
			if(chart.regionOccupied(topLeftX, topLeftY, bottomRightX, bottomRightY))
				return false;
		
		return true;
		
	}
	
	/**
	 * @return    The frequency of samples, in Hz.
	 */
	public static int getSampleRate() {
		
		return Model.sampleRate;
		
	}
	
	/**
	 * @param rate    The frequency of samples, in Hz.
	 */
	public static void setSampleRate(int rate) {
		
		Model.sampleRate = rate;
		
	}
	
	/**
	 * @return    The default color to use when defining the data structure.
	 */
	public static Color getDefaultLineColor() {
		
		return Model.lineColorDefault;
		
	}

	/**
	 * A helper function that calculates the sample count of datasets.
	 * Since datasets may contain different numbers of samples (due to live insertion of new samples), the smallest count is returned to ensure validity.
	 * 
	 * @param dataset    The Dataset[].
	 * @return           Smallest sample count from the datasets.
	 */
	static int getSamplesCount(Dataset[] dataset) {
		
		int[] count = new int[dataset.length];
		for(int i = 0; i < dataset.length; i ++)
			count[i] = dataset[i].size();
		Arrays.sort(count);
		return count[0];
		
	}
	
	/**
	 * Inserts one new sample into each of the datasets, and logs the new samples.
	 * 
	 * @param newSamples    A float[] containing one sample for each dataset.
	 */
	static void insertSamples(float[] newSamples) {

		for(int i = 0; i < newSamples.length; i++)
			Controller.getDatasetByIndex(i).add(newSamples[i]); // FIXME should be byLocation, but that breaks Binary mode
		
		if(logFile != null) {
			logFile.print(new SimpleDateFormat("YYYY-MM-dd    HH:mm:ss.SSS").format(new Date()));
			for(int i = 0; i < newSamples.length; i++)
				logFile.print("," + Float.toString(newSamples[i]));
			logFile.println();
		}
		
	}
	
	/**
	 * Starts logging, and allows reception of data from the UART. This should only be called after the data structure has been fully defined.
	 */
	static void startReceivingData() {
		
		if(logFile != null)
			logFile.close();
		try {
			logFile = new PrintWriter("log.csv", "UTF-8");
			logFile.print("PC Timestamp (YYYY-MM-DD    HH:MM:SS.SSS)");
			for(int i = 0; i < Controller.getDatasetsCount(); i++) {
				Dataset d = Controller.getDatasetByIndex(i);
				logFile.print("," + d.name + " (" + d.unit + ")");
			}
			logFile.println();
		} catch(Exception e) { }
		
		dataStructureDefined.set(true);
		
	}
	
	/**
	 * @return    The number of bytes in a complete Binary data packet (including the 0xAA sync word.)
	 */
	static int getBinaryPacketSize() {
		
		Dataset lastDataset = Controller.getDatasetByIndex(Controller.getDatasetsCount() - 1);
		return lastDataset.location + lastDataset.processor.getByteCount();
		
	}
	
	/**
	 * @return    An array of BinaryProcessor's that each describe their data type and can convert raw bytes into a number.
	 */
	static BinaryProcessor[] getBinaryProcessors() {
		
		BinaryProcessor[] processor = new BinaryProcessor[2];
		
		processor[0] = new BinaryProcessor() {
			
			@Override public String toString()                   { return "uint16 LSB First"; }
			@Override public int getByteCount()                  { return 2; }
			@Override public float extractValue(byte[] rawByte) { return (float) ((0xFF & rawByte[1]) << 8 | (0xFF & rawByte[0])); }
			
		};
		
		processor[1] = new BinaryProcessor() {
			
			@Override public String toString()                   { return "uint16 MSB First"; }
			@Override public int getByteCount()                  { return 2; }
			@Override public float extractValue(byte[] rawByte) { return (float) ((0xFF & rawByte[0]) << 8 | (0xFF & rawByte[1])); }
			
		};
		
		return processor;
		
	}
	
	/**
	 * Saves the current state to a file. The state consists of: grid row and column counts, serial port settings, data structure definition, and details for each chart.
	 * 
	 * @param outputFilePath    An absolute path to a .txt file.
	 */
	static void saveLayout(String outputFilePath) {
		
		try {
			
			PrintWriter outputFile = new PrintWriter(new File(outputFilePath), "UTF-8");
			outputFile.println("Telemetry Viewer File Format v0.1");
			outputFile.println("");
			
			outputFile.println("Grid Settings:");
			outputFile.println("");
			outputFile.println("\tcolumn count = " + Model.gridColumns);
			outputFile.println("\trow count = " + Model.gridRows);
			outputFile.println("");
			
			outputFile.println("Serial Port Settings:");
			outputFile.println("");
			outputFile.println("\tport = " + port.getSystemPortName());
			outputFile.println("\tbaud = " + port.getBaudRate());
			outputFile.println("\tpacket type = " + Model.packetType);
			outputFile.println("\tsample rate = " + Model.sampleRate);
			outputFile.println("");
			
			outputFile.println(Model.datasets.size() + " Data Structure Locations:");
			
			for(Dataset dataset : Model.datasets.values()) {
				
				int processorIndex = 0;
				BinaryProcessor[] processors = Controller.getBinaryProcessors();
				for(int i = 0; i < processors.length; i++)
					if(dataset.processor == processors[i])
						processorIndex = i;
				
				outputFile.println("");
				outputFile.println("\tlocation = " + dataset.location);
				outputFile.println("\tprocessor index = " + processorIndex);
				outputFile.println("\tname = " + dataset.name);
				outputFile.println("\tcolor = " + String.format("0x%02X%02X%02X", dataset.color.getRed(), dataset.color.getGreen(), dataset.color.getBlue()));
				outputFile.println("\tunit = " + dataset.unit);
				outputFile.println("\tconversion factor a = " + dataset.conversionFactorA);
				outputFile.println("\tconversion factor b = " + dataset.conversionFactorB);
				
			}
			
			outputFile.println("");
			outputFile.println(Model.charts.size() + " Charts:");
			
			for(PositionedChart chart : Model.charts) {
				
				outputFile.println("");
				outputFile.println("\tchart type = " + chart.toString());
				outputFile.println("\tduration = " + chart.duration);
				outputFile.println("\ttop left x = " + chart.topLeftX);
				outputFile.println("\ttop left y = " + chart.topLeftY);
				outputFile.println("\tbottom right x = " + chart.bottomRightX);
				outputFile.println("\tbottom right y = " + chart.bottomRightY);
				outputFile.println("\tdatasets count = " + chart.datasets.length);
				for(int i = 0; i < chart.datasets.length; i++)
					outputFile.println("\t\tdataset location = " + chart.datasets[i].location);
				
			}
			
			outputFile.close();
			
		} catch (IOException e) {
			
			JOptionPane.showMessageDialog(null, "Unable to save the file.", "Error: Unable to Save the File", JOptionPane.ERROR_MESSAGE);
			
		}
		
	}

	/**
	 * Opens a file and resets the current state to the state defined in that file.
	 * The state consists of: grid row and column counts, serial port settings, data structure definition, and details for each chart.
	 * 
	 * @param inputFilePath    An absolute path to a .txt file.
	 */
	static void openLayout(String inputFilePath) {
		
		Controller.disconnectFromSerialPort();
		Controller.removeAllDatasets();
		
		try {
			
			List<String> lines = Files.readAllLines(new File(inputFilePath).toPath(), StandardCharsets.UTF_8);
			int n = 0;
			
			verify(lines.get(n++).equals("Telemetry Viewer File Format v0.1"));
			verify(lines.get(n++).equals(""));
			
			verify(lines.get(n++).equals("Grid Settings:"));
			verify(lines.get(n++).equals(""));
			
			verify(lines.get(n++).startsWith("\tcolumn count = "));
			int gridColumns = Integer.parseInt(lines.get(n-1).substring(16));
			verify(lines.get(n++).startsWith("\trow count = "));
			int gridRows = Integer.parseInt(lines.get(n-1).substring(13));
			verify(lines.get(n++).equals(""));
			
			Controller.setGridColumns(gridColumns);
			Controller.setGridRows(gridRows);

			verify(lines.get(n++).equals("Serial Port Settings:"));
			verify(lines.get(n++).equals(""));
			verify(lines.get(n++).startsWith("\tport = "));
			String portName = lines.get(n-1).substring(8);
			verify(lines.get(n++).startsWith("\tbaud = "));
			int baudRate = Integer.parseInt(lines.get(n-1).substring(8));
			verify(lines.get(n++).startsWith("\tpacket type = "));
			String packetType = lines.get(n-1).substring(15);
			verify(lines.get(n++).startsWith("\tsample rate = "));
			int sampleRate = Integer.parseInt(lines.get(n-1).substring(15));
			verify(lines.get(n++).equals(""));
			
			Controller.connectToSerialPort(sampleRate, packetType, portName, baudRate);
			
			verify(lines.get(n++).endsWith(" Data Structure Locations:"));
			int locationsCount = Integer.parseInt(lines.get(n-1).split(" ")[0]);

			for(int i = 0; i < locationsCount; i++) {
				
				verify(lines.get(n++).equals(""));
				verify(lines.get(n++).startsWith("\tlocation = "));
				int location = Integer.parseInt(lines.get(n-1).substring(12));
				verify(lines.get(n++).startsWith("\tprocessor index = "));
				int processorIndex = Integer.parseInt(lines.get(n-1).substring(19));
				BinaryProcessor processor = Controller.getBinaryProcessors()[processorIndex];
				verify(lines.get(n++).startsWith("\tname = "));
				String name = lines.get(n-1).substring(8);
				verify(lines.get(n++).startsWith("\tcolor = 0x"));
				int colorNumber = Integer.parseInt(lines.get(n-1).substring(11), 16);
				Color color = new Color(colorNumber);
				verify(lines.get(n++).startsWith("\tunit = "));
				String unit = lines.get(n-1).substring(8);
				verify(lines.get(n++).startsWith("\tconversion factor a = "));
				float conversionFactorA = Float.parseFloat(lines.get(n-1).substring(23));
				verify(lines.get(n++).startsWith("\tconversion factor b = "));
				float conversionFactorB = Float.parseFloat(lines.get(n-1).substring(23));
				
				Controller.insertDataset(location, processor, name, color, unit, conversionFactorA, conversionFactorB);
				
			}
			
			Controller.startReceivingData();

			verify(lines.get(n++).equals(""));
			verify(lines.get(n++).endsWith(" Charts:"));
			int chartsCount = Integer.parseInt(lines.get(n-1).split(" ")[0]);

			for(int i = 0; i < chartsCount; i++) {
				
				verify(lines.get(n++).equals(""));
				verify(lines.get(n++).startsWith("\tchart type = "));
				String chartType = lines.get(n-1).substring(14);
				verify(lines.get(n++).startsWith("\tduration = "));
				int duration = Integer.parseInt(lines.get(n-1).substring(12));
				verify(lines.get(n++).startsWith("\ttop left x = "));
				int topLeftX = Integer.parseInt(lines.get(n-1).substring(14));
				verify(lines.get(n++).startsWith("\ttop left y = "));
				int topLeftY = Integer.parseInt(lines.get(n-1).substring(14));
				verify(lines.get(n++).startsWith("\tbottom right x = "));
				int bottomRightX = Integer.parseInt(lines.get(n-1).substring(18));
				verify(lines.get(n++).startsWith("\tbottom right y = "));
				int bottomRightY = Integer.parseInt(lines.get(n-1).substring(18));
				verify(lines.get(n++).startsWith("\tdatasets count = "));
				int datasetsCount = Integer.parseInt(lines.get(n-1).substring(18));
				
				Dataset[] datasets = new Dataset[datasetsCount];
				
				for(int j = 0; j < datasetsCount; j++) {
					
					verify(lines.get(n++).startsWith("\t\tdataset location = "));
					int location = Integer.parseInt(lines.get(n-1).substring(21));
					datasets[j] = Controller.getDatasetByLocation(location);
					
				}
				
				for(ChartDescriptor descriptor : Controller.getChartDescriptors())
					if(descriptor.toString().equals(chartType)) {
						PositionedChart chart = descriptor.createChart(topLeftX, topLeftY, bottomRightX, bottomRightY, duration, datasets);
						Controller.addPositionedChart(chart);
						break;
					}
				
			}
			
		} catch (IOException e) {
			
			JOptionPane.showMessageDialog(null, "Unable to open the file.", "Error: Unable to Open the File", JOptionPane.ERROR_MESSAGE);
			
		} catch(AssertionError e) {
		
			JOptionPane.showMessageDialog(null, "Unable to parse the file.", "Error: Unable to Parse the File", JOptionPane.ERROR_MESSAGE);
		
		}
		
	}
	
	/**
	 * A helper function that is essentially an "assert" and throws an exception if the assert fails.
	 *  
	 * @param good    If true nothing will happen, if false an AssertionError will be thrown.
	 */
	private static void verify(boolean good) {
		
		if(!good)
			throw new AssertionError();
		
	}
	
}
