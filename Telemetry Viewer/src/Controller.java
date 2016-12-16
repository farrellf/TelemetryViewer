import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.JFrame;
import javax.swing.JOptionPane;

import com.fazecast.jSerialComm.SerialPort;

/**
 * Handles all non-GUI logic and manages access to the Model (the data).
 */
public class Controller {
	
	static List<GridChangedListener> gridChangedListeners = new ArrayList<GridChangedListener>();
	static List<SerialPortListener>   serialPortListeners = new ArrayList<SerialPortListener>();
	static volatile SerialPort port;
	
	/**
	 * @return    The display scaling factor. By default, this is the percentage of 100dpi that the screen uses, rounded to an integer.
	 */
	public static float getDisplayScalingFactor() {
		
		return Model.displayScalingFactor;
		
	}
	
	/**
	 * @param newFactor    The new display scaling factor.
	 */
	public static void setDisplayScalingFactor(float newFactor) {
		
		if(newFactor < Model.displayScalingFactorMinimum) newFactor = Model.displayScalingFactorMinimum;
		if(newFactor > Model.displayScalingFactorMaximum) newFactor = Model.displayScalingFactorMaximum;
		
		Model.displayScalingFactor = newFactor;
		
		Theme.displayingScalingFactorChanged(newFactor);
		FontUtils.displayingScalingFactorChanged(newFactor);
		
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
	public static void insertDataset(int location, BinaryFieldProcessor processor, String name, Color color, String unit, float conversionFactorA, float conversionFactorB) {
		
		Model.datasets.put(location, new Dataset(location, processor, name, color, unit, conversionFactorA, conversionFactorB));
		
	}
	
	/**
	 * Removes a specific dataset.
	 * 
	 * @return    true on success, false if nothing existed there.
	 */
	public static boolean removeDataset(int location) {
		
		PositionedChart[] charts = Controller.getCharts().toArray(new PositionedChart[0]);
		for(PositionedChart chart : charts)
			for(Dataset dataset : chart.datasets)
				if(dataset.location == location)
					Controller.removeChart(chart);
		
		Dataset removedDataset = Model.datasets.remove(location);
		
		if(removedDataset == null)
			return false;
		else
			return true;
		
	}
	
	/**
	 * Removes all charts and Datasets.
	 */
	public static void removeAllDatasets() {
		
		Controller.removeAllCharts();
		
		Model.datasets.clear();
		
	}
	
	/**
	 * @return    The Datasets.
	 */
	public static Dataset[] getAllDatasets() {
		
		return Model.datasets.values().toArray(new Dataset[Model.datasets.size()]);
		
	}
	
	/**
	 * Registers a listener that will be notified when the serial port status (connection made or lost) changes.
	 * 
	 * @param listener    The listener to be notified.
	 */
	public static void addSerialPortListener(SerialPortListener listener) {
		
		serialPortListeners.add(listener);
		
	}
	
	static final int SERIAL_CONNECTION_OPENED = 0;
	static final int SERIAL_CONNECTION_CLOSED = 1;
	static final int SERIAL_CONNECTION_LOST   = 2;
	/**
	 * Notifies all registered listeners about a change in the serial port status.
	 * 
	 * @param status    Either SERIAL_CONNECTION_OPENED or SERIAL_CONNECTION_CLOSED or SERIAL_CONNECTION_LOST.
	 */
	static void notifySerialPortListeners(int status) {
		
		for(SerialPortListener listener : serialPortListeners)
			if(status == SERIAL_CONNECTION_OPENED)
				listener.connectionOpened(Model.sampleRate, Model.packet, Model.portName, Model.baudRate);
			else if(status == SERIAL_CONNECTION_CLOSED)
				listener.connectionClosed();
			else if(status == SERIAL_CONNECTION_LOST)
				listener.connectionLost();
		
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
	 * @return    A Packet[] of the supported UART packet types.
	 */
	public static Packet[] getPacketTypes() {
		
		return new Packet[] {Model.csvPacket, Model.binaryPacket};
		
	}
	
	/**
	 * Connects to a serial port and shows a DataStructureWindow if necessary.
	 * 
	 * @param sampleRate      Expected samples per second. (Hertz) This is used for FFTs.
	 * @param packet          One of the Packets from Controller.getPacketTypes()
	 * @param portName        One of the Strings from Controller.getSerialPortNames()
	 * @param baudRate        One of the baud rates from Controller.getBaudRates()
	 * @param parentWindow    If not null, a DataStructureWindow will be shown and centered on this JFrame.
	 */
	public static void connectToSerialPort(int sampleRate, Packet packet, String portName, int baudRate, JFrame parentWindow) { // FIXME make this robust: give up after some time.

		if(port != null)
			port.closePort();
		
		if(portName.equals("Test")) {
			
			Tester.populateDataStructure();
			Tester.startTransmission();
			
			Model.sampleRate = sampleRate;
			Model.packet = null;
			Model.portName = portName;
			Model.baudRate = 9600;
			notifySerialPortListeners(SERIAL_CONNECTION_OPENED);
			
			return;
			
		}
			
		port = SerialPort.getCommPort(portName);
		port.setBaudRate(baudRate);
		if(packet instanceof CsvPacket)
			port.setComPortTimeouts(SerialPort.TIMEOUT_SCANNER, 0, 0);
		else if(packet instanceof BinaryPacket)
			port.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, 0, 0);
		
		// try 3 times before giving up
		if(!port.openPort()) {
			if(!port.openPort()) {
				if(!port.openPort()) {
					notifySerialPortListeners(SERIAL_CONNECTION_LOST);
					return;
				}
			}
		}
		
		Model.sampleRate = sampleRate;
		Model.packet = packet;
		Model.portName = portName;
		Model.baudRate = baudRate;
		
		notifySerialPortListeners(SERIAL_CONNECTION_OPENED);
		
		if(parentWindow != null)
			packet.showDataStructureWindow(parentWindow, false);
		
		packet.startReceivingData(port);
		
	}
	
	/**
	 * Stops the serial port receiver thread, disconnects from the active serial port, and stops logging.
	 */
	public static void disconnectFromSerialPort() {
		
		if(Model.portName.equals("Test")) {
			
			Tester.stopTransmission();
			notifySerialPortListeners(SERIAL_CONNECTION_CLOSED);
			
		} else {		
			
			Model.packet.stopReceivingData();
			
			if(port != null)
				port.closePort();
			
			notifySerialPortListeners(SERIAL_CONNECTION_CLOSED);
			
			port = null;
			
		}
		
	}
	
	/**
	 * @param chart    New chart to insert and display.
	 */
	public static void addChart(PositionedChart chart) {
		
		Model.charts.add(chart);
		
	}
	
	/**
	 * Removes a specific chart.
	 * 
	 * @param chart    Chart to remove.
	 */
	public static void removeChart(PositionedChart chart) {

		Model.charts.remove(chart);
		
	}
	
	/**
	 * Removes all charts.
	 */
	public static void removeAllCharts() {
		
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
	 * A helper function that calculates the sample count of all datasets.
	 * Since datasets may contain different numbers of samples (due to live insertion of new samples), the smallest count is returned to ensure validity.
	 * 
	 * @return    Smallest sample count from the datasets.
	 */
	static int getSamplesCount() {
		
		Dataset[] datasets = Controller.getAllDatasets();
		
		if(datasets.length == 0)
			return 0;
		
		int[] count = new int[datasets.length];
		for(int i = 0; i < datasets.length; i++)
			count[i] = datasets[i].size();
		Arrays.sort(count);
		return count[0];
		
	}
	
	/**
	 * Exports all samples to a CSV file.
	 * 
	 * @param path    Full path with file name.
	 */
	static void exportCsvLogFile(String filepath) {
		
		int datasetsCount = Controller.getDatasetsCount();
		int sampleCount = Controller.getSamplesCount();
		
		try {
			
			PrintWriter logFile = new PrintWriter(filepath, "UTF-8");
			logFile.print("Sample Number (" + Model.sampleRate + " samples per second)");
			for(int i = 0; i < datasetsCount; i++) {
				Dataset d = Controller.getDatasetByIndex(i);
				logFile.print("," + d.name + " (" + d.unit + ")");
			}
			logFile.println();
			
			for(int i = 0; i < sampleCount; i++) {
				logFile.print(i);
				for(int n = 0; n < datasetsCount; n++)
					logFile.print("," + Float.toString(Controller.getDatasetByIndex(n).getSample(i)));
				logFile.println();
			}
			
			logFile.close();
			
		} catch(Exception e) { }
		
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
			outputFile.println("\tpacket type = " + Model.packet);
			outputFile.println("\tsample rate = " + Model.sampleRate);
			outputFile.println("");
			
			outputFile.println(Model.datasets.size() + " Data Structure Locations:");
			
			for(Dataset dataset : Model.datasets.values()) {
				
				int processorIndex = -1;
				BinaryFieldProcessor[] processors = BinaryPacket.getBinaryFieldProcessors();
				for(int i = 0; i < processors.length; i++)
					if(dataset.processor.toString().equals(processors[i].toString()))
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
			
			if(Model.packet.toString().equals("Binary")) {
			
				BinaryPacket packet = (BinaryPacket) Model.packet;
				
				int checksumProcessorIndex = -1;
				BinaryChecksumProcessor[] processors = BinaryPacket.getBinaryChecksumProcessors();
				for(int i = 0; i < processors.length; i++)
					if(packet.checksumProcessor.toString().equals(processors[i].toString()))
						checksumProcessorIndex = i;
				
				outputFile.println("");
				outputFile.println("Checksum:");
				outputFile.println("");
				outputFile.println("\tlocation = " + packet.checksumProcessorOffset);
				outputFile.println("\tchecksum processor index = " + checksumProcessorIndex);
			
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
		
		try {
			
			List<String> lines = Files.readAllLines(new File(inputFilePath).toPath(), StandardCharsets.UTF_8);
			int n = 0;
			
			parse(n, lines.get(n++), "Telemetry Viewer File Format v0.1");
			parse(n, lines.get(n++), "");
			
			parse(n, lines.get(n++), "Grid Settings:");
			parse(n, lines.get(n++), "");
			
			int gridColumns = (int) parse(n, lines.get(n++), "\tcolumn count = %d");
			int gridRows = (int) parse(n, lines.get(n++), "\trow count = %d");
			parse(n, lines.get(n++), "");
			
			Controller.setGridColumns(gridColumns);
			Controller.setGridRows(gridRows);

			parse(n, lines.get(n++), "Serial Port Settings:");
			parse(n, lines.get(n++), "");
			String portName = (String) parse(n, lines.get(n++), "\tport = %s");
			int baudRate = (int) parse(n, lines.get(n++), "\tbaud = %d");
			
			String packetType = (String) parse(n, lines.get(n++), "\tpacket type = %s");
			int sampleRate = (int) parse(n, lines.get(n++), "\tsample rate = %d");
			parse(n, lines.get(n++), "");

			if(packetType.equals(Model.csvPacket.toString()))
				Model.packet = Model.csvPacket;
			else if(packetType.equals(Model.binaryPacket.toString()))
				Model.packet = Model.binaryPacket;
			else
				throw new AssertionError("Line " + n + ": Invalid packet type.");
			
			Model.packet.clear();
			
			int locationsCount = (int) parse(n, lines.get(n++), "%d Data Structure Locations:");

			for(int i = 0; i < locationsCount; i++) {
				
				parse(n, lines.get(n++), "");
				int location = (int) parse(n, lines.get(n++), "\tlocation = %d");
				int processorIndex = (int) parse(n, lines.get(n++), "\tprocessor index = %d");
				String name = (String) parse(n, lines.get(n++), "\tname = %s");
				String colorText = (String) parse(n, lines.get(n++), "\tcolor = 0x%s");
				String unit = (String) parse(n, lines.get(n++), "\tunit = %s");
				float conversionFactorA = (float) parse(n, lines.get(n++), "\tconversion factor a = %f");
				float conversionFactorB = (float) parse(n, lines.get(n++), "\tconversion factor b = %f");
				
				int colorNumber = Integer.parseInt(colorText, 16);
				Color color = new Color(colorNumber);
				BinaryFieldProcessor processor = BinaryPacket.getBinaryFieldProcessors()[processorIndex];
				if(Model.packet == Model.csvPacket)
					Model.csvPacket.insertField(location, name, color, unit, conversionFactorA, conversionFactorB);
				else if(Model.packet == Model.binaryPacket)
					Model.binaryPacket.insertField(location, processor, name, color, unit, conversionFactorA, conversionFactorB);
				
			}
			
			if(Model.packet == Model.binaryPacket) {
				
				parse(n, lines.get(n++), "");
				parse(n, lines.get(n++), "Checksum:");
				parse(n, lines.get(n++), "");
				int checksumOffset = (int) parse(n, lines.get(n++), "\tlocation = %d");
				int checksumIndex = (int) parse(n, lines.get(n++), "\tchecksum processor index = %d");
				
				if(checksumOffset >= 1) {
					BinaryChecksumProcessor processor = BinaryPacket.getBinaryChecksumProcessors()[checksumIndex];
					Model.binaryPacket.insertChecksum(checksumOffset, processor);
				}
			
			}
			
			Controller.connectToSerialPort(sampleRate, Model.packet, portName, baudRate, null);

			parse(n, lines.get(n++), "");
			int chartsCount = (int) parse(n, lines.get(n++), "%d Charts:");

			for(int i = 0; i < chartsCount; i++) {
				
				parse(n, lines.get(n++), "");
				String chartType = (String) parse(n, lines.get(n++), "\tchart type = %s");
				int duration = (int) parse(n, lines.get(n++), "\tduration = %d");
				int topLeftX = (int) parse(n, lines.get(n++), "\ttop left x = %d");
				int topLeftY = (int) parse(n, lines.get(n++), "\ttop left y = %d");
				int bottomRightX = (int) parse(n, lines.get(n++), "\tbottom right x = %d");
				int bottomRightY = (int) parse(n, lines.get(n++), "\tbottom right y = %d");
				int datasetsCount = (int) parse(n, lines.get(n++), "\tdatasets count = %d");
				
				Dataset[] datasets = new Dataset[datasetsCount];
				
				for(int j = 0; j < datasetsCount; j++) {
					
					int location = (int) parse(n, lines.get(n++), "\t\tdataset location = %d");
					datasets[j] = Controller.getDatasetByLocation(location);
					
					if(datasets[j] == null)
						throw new AssertionError("Line " + n + ": Dataset does not exist.");
					
				}
				
				for(ChartDescriptor descriptor : Controller.getChartDescriptors())
					if(descriptor.toString().equals(chartType)) {
						PositionedChart chart = descriptor.createChart(topLeftX, topLeftY, bottomRightX, bottomRightY, duration, datasets);
						Controller.addChart(chart);
						break;
					}
				
			}
			
		} catch (IOException ioe) {
			
			JOptionPane.showMessageDialog(null, "Unable to open the file.", "Error", JOptionPane.ERROR_MESSAGE);
			
		} catch(AssertionError ae) {
		
			JOptionPane.showMessageDialog(null, "Error while parsing the file:\n" + ae.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
		
		}
		
	}
	
	/**
	 * Takes a string of text and attempts to parse it with a format string.
	 * Throws an exception if the text does not match the format string, or if the format string is invalid.
	 * 
	 * @param line            Line number to show in the error message if an error occurs.
	 * @param text            Line of text to parse.
	 * @param formatString    Printf-style format string but with many limitations:
	 *                            1. Only %d %f or %s can be used.
                                  2. A %d or %f can only be at the very beginning or very end. A %s can only be at the very end.
                                  3. There must be a space between %d/%f/%s and the rest of the text.
	 * @return                An Integer if %d was used, a Float if %f was used, a String if %s was used, or null if no format specifier was used.
	 */
	private static Object parse(int line, String text, String formatString) {
		
		// error message line numbers should start at 1 but the argument starts at 0
		line++;
		
		// no format specifier, so just ensure the text matches the formatString exactly
		if(!formatString.contains("%")) {
			if(text.equals(formatString))
				return null;
			else
				throw new AssertionError("Line " + line + ": Text does not match the expected value.");
		}
		
		// starting with %d, so an integer should be at the start of the text
		if(formatString.startsWith("%d")) {
			try {
				String[] tokens = text.split(" ");
				int number = Integer.parseInt(tokens[0]);
				String expectedText = formatString.substring(2);
				String remainingText = "";
				for(int i = 1; i < tokens.length; i++)
					remainingText += " " + tokens[i];
				if(remainingText.equals(expectedText))
					return number;
				else
					throw new AssertionError("Line " + line + ": Text does not match the expected value.");
			} catch(Exception e) {
				throw new AssertionError("Line " + line + ": Text does not start with an integer.");
			}
		}
		
		// starting with %f, so a float should be at the start of the text
		if(formatString.startsWith("%f")) {
			try {
				String[] tokens = text.split(" ");
				float number = Float.parseFloat(tokens[0]);
				String expectedText = formatString.substring(2);
				String remainingText = "";
				for(int i = 1; i < tokens.length; i++)
					remainingText += " " + tokens[i];
				if(remainingText.equals(expectedText))
					return number;
				else
					throw new AssertionError("Line " + line + ": Text does not match the expected value.");
			} catch(Exception e) {
				throw new AssertionError("Line " + line + ": Text does not start with a floating point number.");
			}
		}
		
		// ending with %d, so an integer should be at the end of the text
		if(formatString.endsWith("%d")) {
			try {
				String[] tokens = text.split(" ");
				int number = Integer.parseInt(tokens[tokens.length - 1]);
				String expectedText = formatString.substring(0, formatString.length() - 2);
				String remainingText = "";
				for(int i = 0; i < tokens.length - 1; i++)
					remainingText += tokens[i] + " ";
				if(remainingText.equals(expectedText))
					return number;
				else
					throw new AssertionError("Line " + line + ": Text does not match the expected value.");
			} catch(Exception e) {
				throw new AssertionError("Line " + line + ": Text does not end with an integer.");
			}
		}
		
		// ending with %f, so a float should be at the end of the text
		if(formatString.endsWith("%f")) {
			try {
				String[] tokens = text.split(" ");
				float number = Float.parseFloat(tokens[tokens.length - 1]);
				String expectedText = formatString.substring(0, formatString.length() - 2);
				String remainingText = "";
				for(int i = 0; i < tokens.length - 1; i++)
					remainingText += tokens[i] + " ";
				if(remainingText.equals(expectedText))
					return number;
				else
					throw new AssertionError("Line " + line + ": Text does not match the expected value.");
			} catch(Exception e) {
				throw new AssertionError("Line " + line + ": Text does not end with a floating point number.");
			}
		}
		
		// ending with %s, so a String should be at the end of the text
		if(formatString.endsWith("%s")) {
			try {
				String expectedText = formatString.substring(0, formatString.length() - 2);
				String actualText = text.substring(0, expectedText.length());
				String token = text.substring(expectedText.length()); 
				if(actualText.equals(expectedText))
					return token;
				else
					throw new AssertionError("Line " + line + ": Text does not match the expected value.");
			} catch(Exception e) {
				throw new AssertionError("Line " + line + ": Text does not match the expected value.");
			}
		}
		
		// formatString is not as expected
		throw new AssertionError("Line " + line + ": Source code contains an invalid format string.");
		
	}
	
}
