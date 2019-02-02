import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Handles all non-GUI logic and manages access to the Model (the data).
 */
public class Controller {
	
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
	 * @return    An array of Strings, one for each possible chart type.
	 */
	public static String[] getChartTypes() {
		
		return new String[] {
			"Time Domain Chart",
			"Time Domain Chart (Cached)",
			"Frequency Domain Chart",
			"Histogram Chart",
			"Dial Chart",
			"Quaternion Chart"
		};
		
	}
	
	/**
	 * Creates a PositionedChart and adds it to the charts list.
	 * 
	 * @param chartType      One of the Strings from Controller.getChartTypes()
	 * @param x1             The x-coordinate of a bounding-box corner in the OpenGLChartsRegion grid.
	 * @param y1             The y-coordinate of a bounding-box corner in the OpenGLChartsRegion grid.
	 * @param x2             The x-coordinate of the opposite bounding-box corner in the OpenGLChartsRegion grid.
	 * @param y2             The x-coordinate of the opposite bounding-box corner in the OpenGLChartsRegion grid.
	 * @return               That chart, or null if chartType is invalid.
	 */
	public static PositionedChart createAndAddChart(String chartType, int x1, int y1, int x2, int y2) {
		
		PositionedChart chart = null;
		
		     if(chartType.equals("Time Domain Chart"))          chart = new OpenGLTimeDomainChart(x1, y1, x2, y2);
		else if(chartType.equals("Time Domain Chart (Cached)")) chart = new OpenGLTimeDomainChartCached(x1, y1, x2, y2);
		else if(chartType.equals("Frequency Domain Chart"))     chart = new OpenGLFrequencyDomainChart(x1, y1, x2, y2);
		else if(chartType.equals("Histogram Chart"))            chart = new OpenGLHistogramChart(x1, y1, x2, y2);
		else if(chartType.equals("Dial Chart"))                 chart = new OpenGLDialChart(x1, y1, x2, y2);
		else if(chartType.equals("Quaternion Chart"))           chart = new OpenGLQuaternionChart(x1, y1, x2, y2);
		
		if(chart != null)
			Controller.addChart(chart);
		
		return chart;
		
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

		if(SettingsController.getBenchmarkedChart() == chart)
			SettingsController.setBenchmarkedChart(null);
		ConfigureView.closeIfUsedFor(chart);
		Model.charts.remove(chart);
		
	}
	
	/**
	 * Removes all charts.
	 */
	public static void removeAllCharts() {
		
		// many a temporary copy of the list because you can't remove from a list that you are iterating over
		List<PositionedChart> list = new ArrayList<PositionedChart>(Model.charts);
		
		for(PositionedChart chart : list)
			removeChart(chart);
		
	}
	
	/**
	 * @return    All charts.
	 */
	public static List<PositionedChart> getCharts() {
		
		return Model.charts;
		
	}
	
	/**
	 * Checks if a region is available in the ChartsRegion.
	 * 
	 * @param x1    The x-coordinate of a bounding-box corner in the OpenGLChartsRegion grid.
	 * @param y1    The y-coordinate of a bounding-box corner in the OpenGLChartsRegion grid.
	 * @param x2    The x-coordinate of the opposite bounding-box corner in the OpenGLChartsRegion grid.
	 * @param y2    The y-coordinate of the opposite bounding-box corner in the OpenGLChartsRegion grid.
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
			logFile.print("Sample Number (" + CommunicationController.getSampleRate() + " samples per second)");
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
	 * Saves the current state to a file. The state consists of: GUI settings, communication settings, data structure definition, and details for each chart.
	 * 
	 * @param outputFilePath    An absolute path to a .txt file.
	 */
	static void saveLayout(String outputFilePath) {
		
		try {
			
			PrintWriter outputFile = new PrintWriter(new File(outputFilePath), "UTF-8");
			outputFile.println("Telemetry Viewer File Format v0.5");
			outputFile.println("");
			
			outputFile.println("GUI Settings:");
			outputFile.println("");
			outputFile.println("\ttile column count = " +          SettingsController.getTileColumns());
			outputFile.println("\ttile row count = " +             SettingsController.getTileRows());
			outputFile.println("\tshow plot tooltips = " +         SettingsController.getTooltipVisibility());
			outputFile.println("\tsmooth scrolling = " +           SettingsController.getSmoothScrolling());
			outputFile.println("\topengl antialiasing = " +        SettingsController.getAntialiasing());
			outputFile.println("\tshow fps and period = " +        SettingsController.getFpsVisibility());
			outputFile.println("\tchart index for benchmarks = " + SettingsController.getBenchmarkedChartIndex());
			outputFile.println("");
			
			outputFile.println("Communication Settings:");
			outputFile.println("");
			outputFile.println("\tport = " +                CommunicationController.getPort());
			outputFile.println("\tuart baud rate = " +      CommunicationController.getBaudRate());
			outputFile.println("\ttcp/udp port number = " + CommunicationController.getPortNumber());
			outputFile.println("\tpacket type = " +         CommunicationController.getPacketType());
			outputFile.println("\tsample rate = " +         CommunicationController.getSampleRate());
			outputFile.println("");
			
			outputFile.println(Model.datasets.size() + " Data Structure Locations:");
			
			for(Dataset dataset : Model.datasets.values()) {
				
				int processorIndex = -1;
				
				if(Communication.packet instanceof BinaryPacket) {
					BinaryFieldProcessor[] processors = BinaryPacket.getBinaryFieldProcessors();
					for(int i = 0; i < processors.length; i++)
						if(dataset.processor.toString().equals(processors[i].toString()))
							processorIndex = i;
				}
				
				outputFile.println("");
				outputFile.println("\tlocation = " + dataset.location);
				outputFile.println("\tprocessor index = " + processorIndex);
				outputFile.println("\tname = " + dataset.name);
				outputFile.println("\tcolor = " + String.format("0x%02X%02X%02X", dataset.color.getRed(), dataset.color.getGreen(), dataset.color.getBlue()));
				outputFile.println("\tunit = " + dataset.unit);
				outputFile.println("\tconversion factor a = " + dataset.conversionFactorA);
				outputFile.println("\tconversion factor b = " + dataset.conversionFactorB);
				if(processorIndex != -1 && dataset.processor.toString().startsWith("Bitfield"))
					for(Bitfield bitfield : dataset.bitfields) {
						outputFile.print("\t[" + bitfield.MSBit + ":" + bitfield.LSBit + "] = " + bitfield.names[0]);
						for(int i = 1; i < bitfield.names.length; i++)
							outputFile.print("," + bitfield.names[i]);
						outputFile.println();
					}
				
			}
			
			if(Communication.packet.toString().equals("Binary")) {
			
				BinaryPacket packet = (BinaryPacket) Communication.packet;
				
				int checksumProcessorIndex = -1;
				BinaryChecksumProcessor[] processors = BinaryPacket.getBinaryChecksumProcessors();
				if(packet.checksumProcessor != null)
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
				outputFile.println("\ttop left x = " + chart.topLeftX);
				outputFile.println("\ttop left y = " + chart.topLeftY);
				outputFile.println("\tbottom right x = " + chart.bottomRightX);
				outputFile.println("\tbottom right y = " + chart.bottomRightY);
				
				String[] additionalLines = chart.exportChart();
				if(additionalLines != null)
					for(String line : additionalLines)
						outputFile.println("\t" + line);
				
			}
			
			outputFile.close();
			
		} catch (IOException e) {
			
			NotificationsController.showFailureForSeconds("Unable to save the layout file.", 5, false);
			
		}
		
	}

	/**
	 * Opens a file and resets the current state to the state defined in that file.
	 * The state consists of: GUI settings, communication settings, data structure definition, and details for each chart.
	 * 
	 * @param inputFilePath    An absolute path to a .txt file.
	 */
	static void openLayout(String inputFilePath) {
		
		CommunicationController.disconnect();
		Controller.removeAllCharts();
		Controller.removeAllDatasets();
		
		try {
			
			List<String> lines = Files.readAllLines(new File(inputFilePath).toPath(), StandardCharsets.UTF_8);
			int n = 0;
			
			ChartUtils.parse(n, lines.get(n++), "Telemetry Viewer File Format v0.5");
			ChartUtils.parse(n, lines.get(n++), "");
			
			ChartUtils.parse(n, lines.get(n++), "GUI Settings:");
			ChartUtils.parse(n, lines.get(n++), "");
			
			int tileColumns =               (int) ChartUtils.parse(n, lines.get(n++), "\ttile column count = %d");
			int tileRows =                  (int) ChartUtils.parse(n, lines.get(n++), "\ttile row count = %d");
			boolean tooltipVisibility = (boolean) ChartUtils.parse(n, lines.get(n++), "\tshow plot tooltips = %b");
			boolean smoothScrolling =   (boolean) ChartUtils.parse(n, lines.get(n++), "\tsmooth scrolling = %b");
			boolean antialiasing =      (boolean) ChartUtils.parse(n, lines.get(n++), "\topengl antialiasing = %b");
			boolean fpsVisibility =     (boolean) ChartUtils.parse(n, lines.get(n++), "\tshow fps and period = %b");
			int chartIndex =                (int) ChartUtils.parse(n, lines.get(n++), "\tchart index for benchmarks = %d");
			ChartUtils.parse(n, lines.get(n++), "");
			
			SettingsController.setTileColumns(tileColumns);
			SettingsController.setTileRows(tileRows);
			SettingsController.setTooltipVisibility(tooltipVisibility);
			SettingsController.setSmoothScrolling(smoothScrolling);
			SettingsController.setAntialiasing(antialiasing);
			SettingsController.setFpsVisibility(fpsVisibility);

			ChartUtils.parse(n, lines.get(n++), "Communication Settings:");
			ChartUtils.parse(n, lines.get(n++), "");
			
			String portName =   (String) ChartUtils.parse(n, lines.get(n++), "\tport = %s");
			int baudRate =         (int) ChartUtils.parse(n, lines.get(n++), "\tuart baud rate = %d");
			int tcpUdpPort =       (int) ChartUtils.parse(n, lines.get(n++), "\ttcp/udp port number = %d");
			String packetType = (String) ChartUtils.parse(n, lines.get(n++), "\tpacket type = %s");
			int sampleRate =       (int) ChartUtils.parse(n, lines.get(n++), "\tsample rate = %d");
			ChartUtils.parse(n, lines.get(n++), "");
			
			if(!Arrays.asList(CommunicationController.getPacketTypes()).contains(packetType))
				throw new AssertionError("Line " + (n - 3) + ": Invalid packet type.");
			
			CommunicationController.setPort(portName);
			CommunicationController.setBaudRate(baudRate);
			CommunicationController.setPortNumber(tcpUdpPort);
			CommunicationController.setPacketType(packetType);
			CommunicationController.setSampleRate(sampleRate);
			
			int locationsCount = (int) ChartUtils.parse(n, lines.get(n++), "%d Data Structure Locations:");

			for(int i = 0; i < locationsCount; i++) {
				
				ChartUtils.parse(n, lines.get(n++), "");
				int location =              (int) ChartUtils.parse(n, lines.get(n++), "\tlocation = %d");
				int processorIndex =        (int) ChartUtils.parse(n, lines.get(n++), "\tprocessor index = %d");
				String name =            (String) ChartUtils.parse(n, lines.get(n++), "\tname = %s");
				String colorText =       (String) ChartUtils.parse(n, lines.get(n++), "\tcolor = 0x%s");
				String unit =            (String) ChartUtils.parse(n, lines.get(n++), "\tunit = %s");
				float conversionFactorA = (float) ChartUtils.parse(n, lines.get(n++), "\tconversion factor a = %f");
				float conversionFactorB = (float) ChartUtils.parse(n, lines.get(n++), "\tconversion factor b = %f");
				
				int colorNumber = Integer.parseInt(colorText, 16);
				Color color = new Color(colorNumber);
				
				BinaryFieldProcessor processor = (processorIndex >= 0) ? BinaryPacket.getBinaryFieldProcessors()[processorIndex] : null;
				if(Communication.packet == Communication.csvPacket)
					Communication.csvPacket.insertField(location, name, color, unit, conversionFactorA, conversionFactorB);
				else
					Communication.binaryPacket.insertField(location, processor, name, color, unit, conversionFactorA, conversionFactorB);
				
				if(processor != null && processor.toString().startsWith("Bitfield")) {
					List<Bitfield> fields = new ArrayList<Bitfield>();
					String line = lines.get(n++);
					while(!line.equals("")){
						line = line.substring(1); // skip past the "\t"
						String bitNumbers = line.split(" ")[0];
						String[] fieldNames = line.substring(bitNumbers.length() + 3).split(","); // skip past "[n:n] = "
						bitNumbers = bitNumbers.substring(1, bitNumbers.length() - 1); // remove [ and ]
						int MSBit = Integer.parseInt(bitNumbers.split(":")[0]);
						int LSBit = Integer.parseInt(bitNumbers.split(":")[1]);
						Bitfield field = new Bitfield(MSBit, LSBit);
						for(int f = 0; f < fieldNames.length; f++) {
							field.textfields[f].setText(fieldNames[f]);
							field.names[f] = fieldNames[f];
						}
						fields.add(field);
						line = lines.get(n++);
					}
					n--; // rewind because of the last empty line
					Controller.getDatasetByLocation(location).setBitfields(fields);
				}
				
			}
			
			if(Communication.packet == Communication.binaryPacket) {
				
				ChartUtils.parse(n, lines.get(n++), "");
				ChartUtils.parse(n, lines.get(n++), "Checksum:");
				ChartUtils.parse(n, lines.get(n++), "");
				int checksumOffset = (int) ChartUtils.parse(n, lines.get(n++), "\tlocation = %d");
				int checksumIndex = (int) ChartUtils.parse(n, lines.get(n++), "\tchecksum processor index = %d");
				
				if(checksumOffset >= 1 && checksumIndex >= 0) {
					BinaryChecksumProcessor processor = BinaryPacket.getBinaryChecksumProcessors()[checksumIndex];
					Communication.binaryPacket.insertChecksum(checksumOffset, processor);
				}
			
			}
			
			CommunicationController.connect(null);

			ChartUtils.parse(n, lines.get(n++), "");
			int chartsCount = (int) ChartUtils.parse(n, lines.get(n++), "%d Charts:");
			if(chartsCount == 0)
				return;
			ChartUtils.parse(n, lines.get(n++), "");

			for(int i = 0; i < chartsCount; i++) {
				
				String chartType = (String) ChartUtils.parse(n, lines.get(n++), "\tchart type = %s");
				int topLeftX     =    (int) ChartUtils.parse(n, lines.get(n++), "\ttop left x = %d");
				int topLeftY     =    (int) ChartUtils.parse(n, lines.get(n++), "\ttop left y = %d");
				int bottomRightX =    (int) ChartUtils.parse(n, lines.get(n++), "\tbottom right x = %d");
				int bottomRightY =    (int) ChartUtils.parse(n, lines.get(n++), "\tbottom right y = %d");
				
				List<String> list = new ArrayList<String>();
				int firstLineNumber = n;
				
				while(true) {
					
					// stop at end of file
					if(n >= lines.size())
						break;
					
					String line = lines.get(n++);
					
					// stop at end of section
					if(line.equals(""))
						break;
					
					list.add(line.substring(1)); // skip past the \t
					
				}
				
				String[] additionalLines = list.toArray(new String[list.size()]);
				
				PositionedChart chart = Controller.createAndAddChart(chartType, topLeftX, topLeftY, bottomRightX, bottomRightY);
				chart.importChart(additionalLines, firstLineNumber);
				
			}
			
			SettingsController.setBenchmarkedChartByIndex(chartIndex);
			
		} catch (IOException ioe) {
			
			NotificationsController.showFailureForSeconds("Unable to open the layout file.", 5, false);
			
		} catch(AssertionError ae) {
		
			CommunicationController.disconnect();
			Controller.removeAllCharts();
			Controller.removeAllDatasets();
			
			NotificationsController.showFailureForSeconds("<html>Error while parsing the layout file.<br>" + ae.getMessage() + "</html>", 5, false);
		
		}
		
	}
	
}
