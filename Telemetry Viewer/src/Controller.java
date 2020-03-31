import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import javax.swing.SwingUtilities;

/**
 * Handles all non-GUI logic and manages access to the Model (the data).
 */
public class Controller {
	
	/**
	 * @return    The display scaling factor. This takes into account the true DPI scaling requested by the OS, plus the user's modification (if any.)
	 */
	public static float getDisplayScalingFactor() {
		
		return Model.dpiScalingFactorUser * Model.dpiScalingFactorJava8 * Model.dpiScalingFactorJava9;
		
	}
	
	/**
	 * @return    The display scaling factor requested by the user.
	 */
	public static float getDisplayScalingFactorUser() {
		
		return Model.dpiScalingFactorUser;
		
	}
	
	/**
	 * @param newFactor    The new display scaling factor specified by the user.
	 */
	public static void setDisplayScalingFactorUser(float newFactor) {
		
		if(newFactor < 1) newFactor = 1;
		if(newFactor > 10) newFactor = 10;
		
		Model.dpiScalingFactorUser = newFactor;
		
	}
	
	/**
	 * @param newFactor    The new display scaling factor specified by the OS if using Java 9+.
	 */
	public static void setDisplayScalingFactorJava9(float newFactor) {
		
		if(newFactor == Model.dpiScalingFactorJava9)
			return;
		
		if(newFactor < 1) newFactor = 1;
		if(newFactor > 10) newFactor = 10;
		
		Model.dpiScalingFactorJava9 = newFactor;
		Model.dpiScalingFactorJava8 = 1; // only use the Java9 scaling factor
		
	}
	
	/**
	 * @return    The display scaling factor specified by the OS if using Java 9+.
	 */
	public static float getDisplayScalingFactorJava9() {
		
		return Model.dpiScalingFactorJava9;
		
	}
	
	/**
	 * @return    An array of Strings, one for each possible chart type.
	 */
	public static String[] getChartTypes() {
		
		return new String[] {
			"Time Domain Chart",
			"Frequency Domain Chart",
			"Histogram Chart",
			"Dial Chart",
			"Quaternion Chart",
			"Camera",
			"Timeline"
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
		
		     if(chartType.equals("Time Domain Chart"))      chart = new OpenGLTimeDomainChart(x1, y1, x2, y2);
		else if(chartType.equals("Frequency Domain Chart")) chart = new OpenGLFrequencyDomainChart(x1, y1, x2, y2);
		else if(chartType.equals("Histogram Chart"))        chart = new OpenGLHistogramChart(x1, y1, x2, y2);
		else if(chartType.equals("Dial Chart"))             chart = new OpenGLDialChart(x1, y1, x2, y2);
		else if(chartType.equals("Quaternion Chart"))       chart = new OpenGLQuaternionChart(x1, y1, x2, y2);
		else if(chartType.equals("Camera"))                 chart = new OpenGLCameraChart(x1, y1, x2, y2);
		else if(chartType.equals("Timeline"))               chart = new OpenGLTimelineChart(x1, y1, x2, y2);
		
		if(chart != null)
			Controller.addChart(chart);
		
		return chart;
		
	}
	
	/**
	 * @param chart    New chart to insert and display.
	 */
	public static void addChart(PositionedChart chart) {
		
		Model.charts.add(chart);
		
	}
	
	/**
	 * Reorders the list of charts so the specified chart will be rendered after all other charts.
	 * 
	 * @param chart    The chart to render last.
	 */
	public static void drawChartLast(PositionedChart chart) {
		
		if(Model.charts.size() < 2)
			return;
		
		Collections.swap(Model.charts, Model.charts.indexOf(chart), Model.charts.size() - 1);
		
	}
	
	/**
	 * Removes a specific chart.
	 * 
	 * @param chart    Chart to remove.
	 */
	public static void removeChart(PositionedChart chart) {

		if(SettingsController.getBenchmarkedChart() == chart)
			SettingsController.setBenchmarkedChart(null);
		ConfigureView.instance.closeIfUsedFor(chart);
		
		chart.dispose();
		Model.charts.remove(chart);
		
		// invokeLater() in case we're changing chart types, because a new chart will be created immediately after removing this chart
		SwingUtilities.invokeLater(() -> {
			if(CommunicationController.isConnected() && Controller.getCharts().isEmpty() && Communication.packet.dataStructureDefined)
				NotificationsController.showHintUntil("Add a chart by clicking on a tile, or by clicking-and-dragging across multiple tiles.", () -> !Controller.getCharts().isEmpty(), true);
		});
		
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
	 * Imports all samples from a CSV file.
	 * 
	 * @param path    Full path with file name.
	 */
	static void importCsvLogFile(String filepath) {

		CommunicationController.disconnect();
		CommunicationController.setPort(Communication.PORT_FILE);
		CommunicationController.setImportFile(filepath);
		CommunicationController.connect(true);
		
		// check for corresponding camera BIN files
		for(Camera c : DatasetsController.getExistingCameras()) {
			try {
				String files = filepath.substring(0, filepath.length() - 4) + " " + c.name.replaceAll("[^a-zA-Z0-9.-]", "_");
				if(Files.exists(Paths.get(files + ".bin")) && Files.exists(Paths.get(files + ".mjpg")))
					c.importFiles(files);
			} catch(Exception e) {
				e.printStackTrace();
			}
		}
		
	}
	
	/**
	 * Exports all samples to a CSV file.
	 * 
	 * @param filepath    Full path with file name.
	 */
	static void exportCsvLogFile(String filepath) {
		
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
				// ensure active slots don't get flushed to disk
				if(i % 1024 == 0) 
					DatasetsController.dontFlushRangeBeingExported(i, i + DatasetsController.SLOT_SIZE);
				
				logFile.print(i + "," + DatasetsController.getTimestamp(i));
				for(int n = 0; n < datasetsCount; n++)
					logFile.print("," + Float.toString(DatasetsController.getDatasetByIndex(n).getSample(i)));
				logFile.println();
				
			}
			
			DatasetsController.dontFlushRangeBeingExported(-1, -1);
			
			logFile.close();
			
		} catch(Exception e) { }
		
		for(Camera camera : DatasetsController.getExistingCameras())
			camera.exportFiles(filepath.substring(0, filepath.length() - 4));
		
	}
	
	/**
	 * Saves the current state to a file. The state consists of: GUI settings, communication settings, data structure definition, and details for each chart.
	 * 
	 * @param outputFilePath    An absolute path to a .txt file.
	 */
	static void saveLayout(String outputFilePath) {
		
		try {
			
			PrintWriter outputFile = new PrintWriter(new File(outputFilePath), "UTF-8");
			outputFile.println("Telemetry Viewer File Format v0.6");
			outputFile.println("");
			
			outputFile.println("GUI Settings:");
			outputFile.println("");
			outputFile.println("\ttile column count = " +          SettingsController.getTileColumns());
			outputFile.println("\ttile row count = " +             SettingsController.getTileRows());
			outputFile.println("\ttime format = " +                SettingsController.getTimeFormat());
			outputFile.println("\tshow 24-hour time = " +          SettingsController.getTimeFormat24hours());
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
			
			outputFile.println(DatasetsController.getDatasetsCount() + " Data Structure Locations:");
			
			for(Dataset dataset : DatasetsController.getAllDatasets()) {
				
				outputFile.println("");
				outputFile.println("\tlocation = " + dataset.location);
				outputFile.println("\tbinary processor = " + (dataset.processor == null ? "null" : dataset.processor.toString()));
				outputFile.println("\tname = " + dataset.name);
				outputFile.println("\tcolor = " + String.format("0x%02X%02X%02X", dataset.color.getRed(), dataset.color.getGreen(), dataset.color.getBlue()));
				outputFile.println("\tunit = " + dataset.unit);
				outputFile.println("\tconversion factor a = " + dataset.conversionFactorA);
				outputFile.println("\tconversion factor b = " + dataset.conversionFactorB);
				if(dataset.processor != null && dataset.processor.toString().startsWith("Bitfield"))
					for(Bitfield bitfield : dataset.bitfields) {
						outputFile.print("\t[" + bitfield.MSBit + ":" + bitfield.LSBit + "] = " + bitfield.names[0]);
						for(int i = 1; i < bitfield.names.length; i++)
							outputFile.print("," + bitfield.names[i]);
						outputFile.println();
					}
				
			}
			
			outputFile.println("");
			outputFile.println("Checksum:");
			outputFile.println("");
			outputFile.println("\tlocation = " + Communication.packet.getChecksumProcessorLocation());
			outputFile.println("\tchecksum processor = " + (Communication.packet.checksumProcessor == null ? "null" : Communication.packet.checksumProcessor.toString()));
			
			outputFile.println("");
			outputFile.println(Model.charts.size() + " Charts:");
			
			for(PositionedChart chart : Model.charts) {
				
				outputFile.println("");
				outputFile.println("\tchart type = " + chart.toString());
				outputFile.println("\ttop left x = " + chart.topLeftX);
				outputFile.println("\ttop left y = " + chart.topLeftY);
				outputFile.println("\tbottom right x = " + chart.bottomRightX);
				outputFile.println("\tbottom right y = " + chart.bottomRightY);
				
				for(String line : chart.exportChart())
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
	 * @param connect          True to connect, or false to just configure things without connecting to the device.
	 */
	static void openLayout(String inputFilePath, boolean connect) {
		
		CommunicationController.disconnect();
		Controller.removeAllCharts();
		DatasetsController.removeAllDatasets();
		
		QueueOfLines lines = null;
		
		try {
			
			lines = new QueueOfLines(Files.readAllLines(new File(inputFilePath).toPath(), StandardCharsets.UTF_8));
			
			ChartUtils.parseExact(lines.remove(), "Telemetry Viewer File Format v0.6");
			ChartUtils.parseExact(lines.remove(), "");
			
			ChartUtils.parseExact(lines.remove(), "GUI Settings:");
			ChartUtils.parseExact(lines.remove(), "");
			
			int tileColumns           = ChartUtils.parseInteger(lines.remove(), "tile column count = %d");
			int tileRows              = ChartUtils.parseInteger(lines.remove(), "tile row count = %d");
			String timeFormat         = ChartUtils.parseString (lines.remove(), "time format = %s");
			boolean timeFormat24hours = ChartUtils.parseBoolean(lines.remove(), "show 24-hour time = %b");
			boolean tooltipVisibility = ChartUtils.parseBoolean(lines.remove(), "show plot tooltips = %b");
			boolean smoothScrolling   = ChartUtils.parseBoolean(lines.remove(), "smooth scrolling = %b");
			boolean antialiasing      = ChartUtils.parseBoolean(lines.remove(), "opengl antialiasing = %b");
			boolean fpsVisibility     = ChartUtils.parseBoolean(lines.remove(), "show fps and period = %b");
			int chartIndex            = ChartUtils.parseInteger(lines.remove(), "chart index for benchmarks = %d");
			ChartUtils.parseExact(lines.remove(), "");
			
			SettingsController.setTileColumns(tileColumns);
			SettingsController.setTileRows(tileRows);
			SettingsController.setTimeFormat(timeFormat);
			SettingsController.setTimeFormat24hours(timeFormat24hours);
			SettingsController.setTooltipVisibility(tooltipVisibility);
			SettingsController.setSmoothScrolling(smoothScrolling);
			SettingsController.setAntialiasing(antialiasing);
			SettingsController.setFpsVisibility(fpsVisibility);

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
				
				Communication.packet.insertField(location, processor, name, color, unit, conversionFactorA, conversionFactorB);
				
				if(processor != null && processor.toString().startsWith("Bitfield")) {
					List<Bitfield> fields = new ArrayList<Bitfield>();
					String line = lines.remove();
					while(!line.equals("")){
						try {
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
						} catch(Exception e) {
							throw new AssertionError("Text does not start with a bitfield range.");
						}
						line = lines.remove();
					}
					DatasetsController.getDatasetByLocation(location).setBitfields(fields);
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
				Communication.packet.insertChecksum(checksumOffset, processor);
			}
			
			if(connect)
				CommunicationController.connect(true);
			
			Communication.packet.dataStructureDefined = true;

			ChartUtils.parseExact(lines.remove(), "");
			int chartsCount = ChartUtils.parseInteger(lines.remove(), "%d Charts:");
			if(chartsCount == 0)
				return;

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
				
				for(PositionedChart existingChart : getCharts())
					if(existingChart.regionOccupied(topLeftX, topLeftY, bottomRightX, bottomRightY))
						throw new AssertionError("Chart overlaps an existing chart.");
				
				PositionedChart chart = Controller.createAndAddChart(chartType, topLeftX, topLeftY, bottomRightX, bottomRightY);
				if(chart == null) {
					lines.lineNumber -= 4;
					throw new AssertionError("Invalid chart type.");
				}
				chart.importChart(lines);
				
			}
			
			SettingsController.setBenchmarkedChartByIndex(chartIndex);
			
		} catch (IOException ioe) {
			
			NotificationsController.showFailureUntil("Unable to open the layout file.", () -> CommunicationController.isConnected() || !Controller.getCharts().isEmpty(), true);
			NotificationsController.showHintUntil("Start by connecting to a device or opening a file by using the buttons below.", () -> CommunicationController.isConnected() || !Controller.getCharts().isEmpty(), true);
			
		} catch(AssertionError ae) {
		
			CommunicationController.disconnect();
			Controller.removeAllCharts();
			DatasetsController.removeAllDatasets();
			
			NotificationsController.showFailureUntil("<html><center>Error while parsing the layout file:<br>Line " + lines.lineNumber + ": " + ae.getMessage() + "</center></html>", () -> CommunicationController.isConnected() || !Controller.getCharts().isEmpty(), true);
			NotificationsController.showHintUntil("Start by connecting to a device or opening a file by using the buttons below.", () -> CommunicationController.isConnected() || !Controller.getCharts().isEmpty(), true);
		
		}
		
	}
	
	@SuppressWarnings("serial")
	public static class QueueOfLines extends LinkedList <String> {
		
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
