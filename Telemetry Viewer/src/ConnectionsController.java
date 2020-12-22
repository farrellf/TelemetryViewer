import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ConnectionController manages all Connections, but the bulk of the work is done by the individual Connections.
 */
public class ConnectionsController {
	
	public static volatile boolean importing = false;
	public static volatile boolean exporting = false;
	public static volatile boolean realtimeImporting = true; // false = importing files as fast as possible
	
	public static List<ConnectionTelemetry> connections = new ArrayList<ConnectionTelemetry>();
	static {
		connections.add(new ConnectionTelemetry());
	}
	
	/**
	 * @return    Names of all available connections (UARTs, TCP, UDP, Demo Mode, Stress Test Mode, Cameras, and Network Camera.)
	 */
	public static List<String> getNames() {
		
		return ConnectionTelemetry.getNames();
		
	}
	
	/**
	 * Imports a settings file, log files, and/or camera files.
	 * The user will be notified if there is a problem with any of the files.
	 * 
	 * @param filepaths    A String[] of absolute file paths.
	 */
	public static void importFiles(String[] filepaths) {
		
		if(importing || exporting) {
			NotificationsController.showFailureForSeconds("Unable to import more files while importing or exporting is in progress.", 10, true);
			return;
		}
		
		// sanity check
		int settingsFileCount = 0;
		int csvFileCount = 0;
		int cameraMjpgFileCount = 0;
		int cameraBinFileCount = 0;
		int invalidFileCount = 0;
		
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
		
		// remove old data and charts
		for(ConnectionTelemetry connection : connections)
			connection.dispose();
		connections.clear();
		ChartsController.removeAllCharts();
		
		// import the settings file if requested
		if(settingsFileCount == 1)
			for(String filepath : filepaths)
				if(filepath.endsWith(".txt"))
					if(!importSettingsFile(filepath, csvFileCount == 0))
						return;
		
		if(csvFileCount > 0) {
			boolean allConnectionsExist = true;
			for(String filepath : filepaths)
				if(filepath.endsWith(".csv")) {
					boolean found = false;
					for(int connectionN = 0; connectionN < ConnectionsController.connections.size(); connectionN++)
						if(filepath.endsWith(" - connection " + connectionN + " - " + ConnectionsController.connections.get(connectionN).name.replaceAll("[^a-zA-Z0-9_\\.\\- ]", "") + ".csv"))
							found = true;
					if(!found)
						allConnectionsExist = false;
				}
			if(!allConnectionsExist) {
				NotificationsController.showFailureForSeconds("CSV file does not correspond with an existing connection.", 10, true);
				importing = false;
				realtimeImporting = false;
				CommunicationView.instance.redraw();
				return;
			}
		}
		
		boolean importingInProgress = csvFileCount + cameraMjpgFileCount > 0;
		if(importingInProgress) {
			
			importing = true;
			realtimeImporting = true;
			CommunicationView.instance.redraw();
			
			long totalByteCount = 0;
			for(String filepath : filepaths)
				if(filepath.endsWith(".csv") || filepath.endsWith(".mjpg") || filepath.endsWith(".bin"))
					try { totalByteCount += Files.size(Paths.get(filepath)); } catch(Exception e) { }
			
			AtomicLong completedByteCount = NotificationsController.showProgressBar("Importing...", totalByteCount);
		
			// import the CSV files if requested
			if(csvFileCount > 0) {
				long firstTimestamp = Long.MAX_VALUE;
				for(String filepath : filepaths)
					if(filepath.endsWith(".csv"))
						for(int connectionN = 0; connectionN < ConnectionsController.connections.size(); connectionN++)
							if(filepath.endsWith(" - connection " + connectionN + " - " + ConnectionsController.connections.get(connectionN).name.replaceAll("[^a-zA-Z0-9_\\.\\- ]", "") + ".csv")) {
								long timestamp = ConnectionsController.connections.get(connectionN).getFirstTimestamp(filepath);
								if(timestamp < firstTimestamp)
									firstTimestamp = timestamp;
							}
				
				for(String filepath : filepaths)
					if(filepath.endsWith(".csv"))
						for(int connectionN = 0; connectionN < ConnectionsController.connections.size(); connectionN++)
							if(filepath.endsWith(" - connection " + connectionN + " - " + ConnectionsController.connections.get(connectionN).name.replaceAll("[^a-zA-Z0-9_\\.\\- ]", "") + ".csv"))
								ConnectionsController.connections.get(connectionN).importDataFile(filepath, firstTimestamp, completedByteCount);
			}
			
			// import the camera images if requested
			if(cameraMjpgFileCount > 0)
				for(String filepath : filepaths)
					if(filepath.endsWith(".mjpg"))
						importCameraFiles(filepath, filepath.substring(0, filepath.length() - 4) + "bin");

			long byteCount = totalByteCount;
			new Thread(() -> {
				while(true) {
					boolean allDone = true;
					for(ConnectionTelemetry connection : ConnectionsController.connections)
						if(connection.receiverThread.isAlive())
							allDone = false;
					if(allDone) {
						importing = false;
						realtimeImporting = false;
						CommunicationView.instance.redraw();
						completedByteCount.addAndGet(byteCount); // ensure it gets marked done
						return;
					} else {
						try { Thread.sleep(5); } catch(Exception e) { }
					}
				}
			}).start();
			
		}
		
	}
	
	/**
	 * Exports data to files. While exporting is in progress, importing/exporting/connecting/disconnecting will be prohibited.
	 * Connecting/disconnecting is prohibited to prevent the data structure from being changed while exporting is in progress.
	 * 
	 * @param filepath               The absolute path, including the part of the filename that will be common to all exported files.
	 * @param exportSettingsFile     If true, export a settings file.
	 * @param connectionsToExport    List of Connections to export.
	 * @param camerasToExport        List of Cameras to export.
	 */
	public static void exportFiles(String filepath, boolean exportSettingsFile, List<ConnectionTelemetry> connectionsToExport, List<Camera> camerasToExport) {
		
		Thread exportThread = new Thread(() -> {
			
			exporting = true;
			CommunicationView.instance.redraw();
			
			long totalSampleCount = 0;
			if(exportSettingsFile)
				totalSampleCount++;
			for(ConnectionTelemetry connection : connectionsToExport)
				totalSampleCount += connection.datasets.getSampleCount();
			for(Camera camera : camerasToExport)
				totalSampleCount += camera.getFileSize(); // not equivalent to a sampleCount, but hopefully good enough
			AtomicLong completedSampleCount = NotificationsController.showProgressBar("Exporting...", totalSampleCount);
			
			if(exportSettingsFile) {
				exportSettingsFile(filepath + ".txt");
				completedSampleCount.incrementAndGet();
			}
	
			for(ConnectionTelemetry connection : connectionsToExport) {
				int connectionN = ConnectionsController.connections.indexOf(connection);
				connection.exportDataFile(filepath + " - connection " + connectionN + " - " + connection.name.replaceAll("[^a-zA-Z0-9_\\.\\- ]", "") + ".csv", completedSampleCount);
			}
	
			for(Camera camera : camerasToExport)
				camera.exportFiles(filepath, completedSampleCount);
			
			completedSampleCount.addAndGet(totalSampleCount); // ensure it gets marked done
			
			exporting = false;
			CommunicationView.instance.redraw();
			
		});
		
		exportThread.setPriority(Thread.MIN_PRIORITY); // exporting is not critical
		exportThread.setName("File Export Thread");
		exportThread.start();
		
	}
	
	/**
	 * Saves the GUI settings, communication settings, data structure definition, and chart settings.
	 * 
	 * @param outputFilePath    An absolute path to a .txt file.
	 */
	static void exportSettingsFile(String outputFilePath) {
		
		try {
			
			PrintWriter file = new PrintWriter(new File(outputFilePath), "UTF-8");
			file.println("Telemetry Viewer v0.8 Settings");
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
			
			file.println(connections.size() + " Connections:");
			file.println("");
			for(ConnectionTelemetry connection : connections)
				connection.exportSettings(file);
			
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
	 * Changes the current state to match settings specified by a file.
	 * (GUI settings, connection settings, data structure definitions for each connection, and chart settings.)
	 * 
	 * @param path       Path to the settings (.txt) file.
	 * @param connect    True to connect, or false to just configure things without connecting to the device.
	 * @return           True on success, or false on error.
	 */
	private static boolean importSettingsFile(String path, boolean connect) {
		
		QueueOfLines lines = null;
		
		try {

			lines = new QueueOfLines(Files.readAllLines(new File(path).toPath(), StandardCharsets.UTF_8));
			
			ChartUtils.parseExact(lines.remove(), "Telemetry Viewer v0.8 Settings");
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

			int connectionsCount = ChartUtils.parseInteger(lines.remove(), "%d Connections:");
			ChartUtils.parseExact(lines.remove(), "");
			
			for(int i = 0; i < connectionsCount; i++) {
				ConnectionTelemetry newConnection = new ConnectionTelemetry();
				newConnection.importSettings(lines);
				if(connect)
					newConnection.connect(false);
				connections.add(newConnection);
			}
			if(connectionsCount == 0)
				connections.add(new ConnectionTelemetry());

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
		
			ChartsController.removeAllCharts();
			for(ConnectionTelemetry connection : connections)
				connection.disconnect(null);
			
			NotificationsController.showFailureUntil("Error while parsing the settings file:\nLine " + lines.lineNumber + ": " + ae.getMessage(), () -> false, true);
			return false;
		
		}
		
	}
	
	/**
	 * Imports all images from a MJPG and corresponding BIN file.
	 * 
	 * @param mjpgFilepath    MJPEG file containing the concatenated JPEG images.
	 * @param binFilepath     BIN file containing index data for those JPEGs.
	 */
	private static void importCameraFiles(String mjpgFilepath, String binFilepath) {
		
		for(Camera camera : ConnectionsController.connections.get(0).datasets.getExistingCameras()) {
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
			for(String line : lines) {
				while(line.startsWith("\t"))
					line = line.substring(1);
				add(line);
			}
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
