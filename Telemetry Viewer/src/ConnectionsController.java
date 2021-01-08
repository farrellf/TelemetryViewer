import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ConnectionController manages all Connections, but the bulk of the work is done by the individual Connections.
 */
public class ConnectionsController {
	
	public static volatile boolean importing = false;
	public static volatile boolean exporting = false;
	public static volatile boolean realtimeImporting = true; // false = importing files as fast as possible
	public static volatile boolean previouslyImported = false; // true = the Connections contain imported data
	
	public static List<Connection>                allConnections = new ArrayList<Connection>();
	public static List<ConnectionTelemetry> telemetryConnections = new ArrayList<ConnectionTelemetry>();
	public static List<ConnectionCamera>       cameraConnections = new ArrayList<ConnectionCamera>();
	static {
		addConnection(new ConnectionTelemetry());
	}
	
	private static final String filenameSanitizer = "[^a-zA-Z0-9_\\.\\- ]"; // only allow letters, numbers, underscores, periods, hyphens and spaces.
	
	public static void addConnection(Connection connection) {
		
		allConnections.add(connection);
		if(connection instanceof ConnectionTelemetry)
			telemetryConnections.add((ConnectionTelemetry) connection);
		else if(connection instanceof ConnectionCamera)
			cameraConnections.add((ConnectionCamera) connection);
		
		CommunicationView.instance.redraw();
		
	}
	
	public static void removeConnection(Connection connection) {
		
		connection.dispose();
		allConnections.remove(connection);
		if(connection instanceof ConnectionTelemetry)
			telemetryConnections.remove((ConnectionTelemetry) connection);
		else if(connection instanceof ConnectionCamera)
			cameraConnections.remove((ConnectionCamera) connection);
		
		CommunicationView.instance.redraw();
		
	}
	
	public static void replaceConnection(Connection oldConnection, Connection newConnection) {
		
		oldConnection.dispose();
		int index = allConnections.indexOf(oldConnection);
		allConnections.set(index, newConnection);
		if(oldConnection instanceof ConnectionTelemetry && newConnection instanceof ConnectionTelemetry) {
			index = telemetryConnections.indexOf(oldConnection);
			telemetryConnections.set(index, (ConnectionTelemetry) newConnection);
		} else if(oldConnection instanceof ConnectionCamera && newConnection instanceof ConnectionCamera) {
			index = cameraConnections.indexOf(oldConnection);
			cameraConnections.set(index, (ConnectionCamera) newConnection);
		} else if(oldConnection instanceof ConnectionTelemetry) {
			telemetryConnections.remove(oldConnection);
			cameraConnections.add((ConnectionCamera) newConnection);
		} else if(oldConnection instanceof ConnectionCamera) {
			cameraConnections.remove(oldConnection);
			telemetryConnections.add((ConnectionTelemetry) newConnection);
		}
		
		CommunicationView.instance.redraw();
		
	}
	
	public static void removeAllConnections() {
		
		for(Connection connection : allConnections)
			connection.dispose();
		allConnections.clear();
		telemetryConnections.clear();
		cameraConnections.clear();
		
		CommunicationView.instance.redraw();
		
	}
	
	/**
	 * @return    True if telemetry can be received.
	 */
	public static boolean telemetryPossible() {
		
		for(ConnectionTelemetry connection : telemetryConnections)
			if(connection.connected && connection.dataStructureDefined)
				return true;
		
		for(ConnectionCamera connection : cameraConnections)
			if(connection.connected)
				return true;
		
		return false;
		
	}
	
	/**
	 * @return    True if at least one sample or camera image has been acquired.
	 */
	public static boolean telemetryExists() {
		
		for(Connection connection : ConnectionsController.allConnections)
			if(connection.getSampleCount() > 0)
				return true;
		
		return false;
	}
	
	/**
	 * @return    Timestamp of the first sample or camera image, or Long.MAX_VALUE if no telemetry has been acquired.
	 */
	public static long getFirstTimestamp() {
		
		long timestamp = Long.MAX_VALUE;
		
		for(Connection connection : ConnectionsController.allConnections)
			if(connection.getSampleCount() > 0) {
				long firstTimestamp = connection.getTimestamp(0);
				if(firstTimestamp < timestamp)
					timestamp = firstTimestamp;
			}

		return timestamp;
		
	}
	
	/**
	 * @return    Timestamp of the last sample or camera image, or Long.MIN_VALUE if no telemetry has been acquired.
	 */
	public static long getLastTimestamp() {
		
		long timestamp = Long.MIN_VALUE;
		
		for(Connection connection : ConnectionsController.allConnections)
			if(connection.getSampleCount() > 0) {
				int lastSampleNumber = connection.getSampleCount() - 1;
				long lastTimestamp = connection.getTimestamp(lastSampleNumber);
				if(lastTimestamp > timestamp)
					timestamp = lastTimestamp;
			}
		
		return timestamp;
		
	}
	
	/**
	 * @return    Names of all available connections (UARTs, TCP, UDP, Demo Mode, Stress Test Mode, Cameras, and Network Camera.)
	 */
	public static List<String> getNames() {
		
		List<String> names = new ArrayList<String>();
		names.addAll(ConnectionTelemetry.getNames());
		names.addAll(ConnectionCamera.getNames());
		return names;
		
	}
	
	/**
	 * Imports a settings file, log files, and/or camera files.
	 * The user will be notified if there is a problem with any of the files.
	 * 
	 * @param filepaths    A String[] of absolute file paths.
	 */
	public static void importFiles(String[] filepaths) {
		
		if(importing || exporting) {
			NotificationsController.showFailureForMilliseconds("Unable to import more files while importing or exporting is in progress.", 5000, true);
			return;
		}
		
		// sanity check
		int settingsFileCount = 0;
		int csvFileCount = 0;
		int mkvFileCount = 0;
		int invalidFileCount = 0;
		
		Map<Connection, String> imports = new HashMap<Connection, String>(); // keys are Connections, values are the corresponding files
		
		for(String filepath : filepaths)
			if(filepath.endsWith(".txt"))
				settingsFileCount++;
			else if(filepath.endsWith(".csv"))
				csvFileCount++;
			else if(filepath.endsWith(".mkv"))
				mkvFileCount++;
			else
				invalidFileCount++;
		
		if(invalidFileCount > 0) {
			NotificationsController.showFailureForMilliseconds("Unsupported file type. Only files exported from TelemetryViewer can be imported:\nSettings files (.txt)\nCSV files (.csv)\nCamera files (.mkv)", 5000, true);
			return;
		}
		if(settingsFileCount > 1) {
			NotificationsController.showFailureForMilliseconds("Only one settings file can be opened at a time.", 5000, true);
			return;
		}
		
		// if not importing a settings file, disconnect and remove existing samples/frames
		if(settingsFileCount == 0) {
			for(Connection connection : ConnectionsController.allConnections) {
				connection.disconnect(null);
				connection.removeAllData();
			}
		}
		
		// import the settings file if requested
		if(settingsFileCount == 1) {
			removeAllConnections();
			ChartsController.removeAllCharts();
			for(String filepath : filepaths)
				if(filepath.endsWith(".txt"))
					if(!importSettingsFile(filepath, csvFileCount + mkvFileCount == 0)) {
						ConnectionsController.addConnection(new ConnectionTelemetry());
						return;
					}
		}
		
		for(String filepath : filepaths) {
			if(filepath.endsWith(".csv")) {
				for(int connectionN = 0; connectionN < ConnectionsController.allConnections.size(); connectionN++) {
					Connection connection = ConnectionsController.allConnections.get(connectionN);
					if(filepath.endsWith(" - connection " + connectionN + " - " + connection.name.replaceAll(filenameSanitizer, "") + ".csv"))
						imports.put(connection, filepath);
				}
			} else if(filepath.endsWith(".mkv")) {
				for(int connectionN = 0; connectionN < ConnectionsController.allConnections.size(); connectionN++) {
					Connection connection = ConnectionsController.allConnections.get(connectionN);
					if(filepath.endsWith(" - connection " + connectionN + " - " + connection.name.replaceAll(filenameSanitizer, "") + ".mkv"))
						imports.put(connection, filepath);
				}
			}
		}
		
		if(csvFileCount + mkvFileCount != imports.size()) {
			NotificationsController.showFailureForMilliseconds("Data file does not correspond with an existing connection.", 5000, true);
			return;
		}
		
		boolean importingInProgress = csvFileCount + mkvFileCount > 0;
		if(importingInProgress) {
			
			importing = true;
			realtimeImporting = true;
			CommunicationView.instance.redraw();
			
			long totalByteCount = 0;
			for(String filepath : filepaths)
				if(filepath.endsWith(".csv") || filepath.endsWith(".mkv"))
					try { totalByteCount += Files.size(Paths.get(filepath)); } catch(Exception e) { }
			
			AtomicLong completedByteCount = NotificationsController.showProgressBar("Importing...", totalByteCount);
		
			// import the CSV / MKV files
			long firstTimestamp = Long.MAX_VALUE;
			for(Entry<Connection, String> entry : imports.entrySet()) {
				long timestamp = entry.getKey().readFirstTimestamp(entry.getValue());
				if(timestamp < firstTimestamp)
					firstTimestamp = timestamp;
			}
			if(firstTimestamp != Long.MAX_VALUE)
				for(Entry<Connection, String> entry : imports.entrySet())
					entry.getKey().importDataFile(entry.getValue(), firstTimestamp, completedByteCount);

			// have another thread clean up when importing finishes
			long byteCount = totalByteCount;
			new Thread(() -> {
				while(true) {
					boolean allDone = true;
					for(Connection connection : ConnectionsController.allConnections)
						if(connection.receiverThread != null && connection.receiverThread.isAlive())
							allDone = false;
					if(allDone) {
						previouslyImported = true;
						importing = false;
						realtimeImporting = false;
						CommunicationView.instance.redraw();
						completedByteCount.addAndGet(byteCount); // to ensure it gets marked done
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
	 * @param telemetryToExport      List of ConnectionTelemetrys to export.
	 * @param camerasToExport        List of ConnectionCameras to export.
	 */
	public static void exportFiles(String filepath, boolean exportSettingsFile, List<ConnectionTelemetry> telemetryToExport, List<ConnectionCamera> camerasToExport) {
		
		Thread exportThread = new Thread(() -> {
			
			exporting = true;
			CommunicationView.instance.redraw();
			
			long totalSampleCount = 0;
			if(exportSettingsFile)
				totalSampleCount++;
			for(ConnectionTelemetry connection : telemetryToExport)
				totalSampleCount += connection.getSampleCount();
			for(ConnectionCamera camera : camerasToExport)
				totalSampleCount += camera.getFileSize(); // not equivalent to a sampleCount, but hopefully good enough
			AtomicLong completedSampleCount = NotificationsController.showProgressBar("Exporting...", totalSampleCount);
			
			if(exportSettingsFile) {
				exportSettingsFile(filepath + ".txt");
				completedSampleCount.incrementAndGet();
			}
	
			for(ConnectionTelemetry connection : telemetryToExport) {
				int connectionN = ConnectionsController.allConnections.indexOf(connection);
				String filename = filepath + " - connection " + connectionN + " - " + connection.name.replaceAll(filenameSanitizer, "");
				connection.exportDataFile(filename, completedSampleCount);
			}
	
			for(ConnectionCamera connection : camerasToExport) {
				int connectionN = ConnectionsController.allConnections.indexOf(connection);
				String filename = filepath + " - connection " + connectionN + " - " + connection.name.replaceAll(filenameSanitizer, "");
				connection.exportDataFile(filename, completedSampleCount);
			}
			
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
			file.println("\ttile column count = "           + SettingsController.getTileColumns());
			file.println("\ttile row count = "              + SettingsController.getTileRows());
			file.println("\ttime format = "                 + SettingsController.getTimeFormat());
			file.println("\tshow 24-hour time = "           + SettingsController.getTimeFormat24hours());
			file.println("\tshow hint notifications = "     + SettingsController.getHintNotificationVisibility());
			file.println("\thint notifications color = "    + String.format("0x%02X%02X%02X", SettingsController.getHintNotificationColor().getRed(),
			                                                                                  SettingsController.getHintNotificationColor().getGreen(),
			                                                                                  SettingsController.getHintNotificationColor().getBlue()));
			file.println("\tshow warning notifications = "  + SettingsController.getWarningNotificationVisibility());
			file.println("\twarning notifications color = " + String.format("0x%02X%02X%02X", SettingsController.getWarningNotificationColor().getRed(),
			                                                                                  SettingsController.getWarningNotificationColor().getGreen(),
			                                                                                  SettingsController.getWarningNotificationColor().getBlue()));
			file.println("\tshow failure notifications = "  + SettingsController.getFailureNotificationVisibility());
			file.println("\tfailure notifications color = " + String.format("0x%02X%02X%02X", SettingsController.getFailureNotificationColor().getRed(),
			                                                                                  SettingsController.getFailureNotificationColor().getGreen(),
			                                                                                  SettingsController.getFailureNotificationColor().getBlue()));
			file.println("\tshow verbose notifications = "  + SettingsController.getVerboseNotificationVisibility());
			file.println("\tverbose notifications color = " + String.format("0x%02X%02X%02X", SettingsController.getVerboseNotificationColor().getRed(),
			                                                                                  SettingsController.getVerboseNotificationColor().getGreen(),
			                                                                                  SettingsController.getVerboseNotificationColor().getBlue()));
			file.println("\tshow plot tooltips = "          + SettingsController.getTooltipVisibility());
			file.println("\tsmooth scrolling = "            + SettingsController.getSmoothScrolling());
			file.println("\tshow fps and period = "         + SettingsController.getFpsVisibility());
			file.println("\tbenchmarking = "                + SettingsController.getBenchmarking());
			file.println("\tantialiasing level = "          + SettingsController.getAntialiasingLevel());
			file.println("");
			
			file.println(allConnections.size() + " Connections:");
			file.println("");
			for(Connection connection : allConnections)
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
			
			NotificationsController.showFailureForMilliseconds("Unable to save the settings file.", 5000, false);
			
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
		NotificationsController.removeIfConnectionRelated();
		
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
			boolean hintVisibility    = ChartUtils.parseBoolean(lines.remove(), "show hint notifications = %b");
			String hintColorText      = ChartUtils.parseString (lines.remove(), "hint notifications color = 0x%s");
			boolean warningVisibility = ChartUtils.parseBoolean(lines.remove(), "show warning notifications = %b");
			String warningColorText   = ChartUtils.parseString (lines.remove(), "warning notifications color = 0x%s");
			boolean failureVisibility = ChartUtils.parseBoolean(lines.remove(), "show failure notifications = %b");
			String failureColorText   = ChartUtils.parseString (lines.remove(), "failure notifications color = 0x%s");
			boolean verboseVisibility = ChartUtils.parseBoolean(lines.remove(), "show verbose notifications = %b");
			String verboseColorText   = ChartUtils.parseString (lines.remove(), "verbose notifications color = 0x%s");
			boolean tooltipVisibility = ChartUtils.parseBoolean(lines.remove(), "show plot tooltips = %b");
			boolean smoothScrolling   = ChartUtils.parseBoolean(lines.remove(), "smooth scrolling = %b");
			boolean fpsVisibility     = ChartUtils.parseBoolean(lines.remove(), "show fps and period = %b");
			boolean benchmarking      = ChartUtils.parseBoolean(lines.remove(), "benchmarking = %b");
			int antialiasingLevel     = ChartUtils.parseInteger(lines.remove(), "antialiasing level = %d");
			ChartUtils.parseExact(lines.remove(), "");
			
			Color hintColor    = new Color(Integer.parseInt(hintColorText, 16));
			Color warningColor = new Color(Integer.parseInt(warningColorText, 16));
			Color failureColor = new Color(Integer.parseInt(failureColorText, 16));
			Color verboseColor = new Color(Integer.parseInt(verboseColorText, 16));
			
			SettingsController.setTileColumns(tileColumns);
			SettingsController.setTileRows(tileRows);
			SettingsController.setTimeFormat(timeFormat);
			SettingsController.setTimeFormat24hours(timeFormat24hours);
			SettingsController.setHintNotificationVisibility(hintVisibility);
			SettingsController.setHintNotificationColor(hintColor);
			SettingsController.setWarningNotificationVisibility(warningVisibility);
			SettingsController.setWarningNotificationColor(warningColor);
			SettingsController.setFailureNotificationVisibility(failureVisibility);
			SettingsController.setFailureNotificationColor(failureColor);
			SettingsController.setVerboseNotificationVisibility(verboseVisibility);
			SettingsController.setVerboseNotificationColor(verboseColor);
			SettingsController.setTooltipVisibility(tooltipVisibility);
			SettingsController.setSmoothScrolling(smoothScrolling);
			SettingsController.setFpsVisibility(fpsVisibility);
			SettingsController.setBenchmarking(benchmarking);
			SettingsController.setAntialiasingLevel(antialiasingLevel);

			int connectionsCount = ChartUtils.parseInteger(lines.remove(), "%d Connections:");
			ChartUtils.parseExact(lines.remove(), "");
			
			for(int i = 0; i < connectionsCount; i++) {
				Connection newConnection = lines.peek().trim().equals("connection type = Camera") ? new ConnectionCamera() :
				                                                                                    new ConnectionTelemetry();
				newConnection.importSettings(lines);
				if(connect)
					newConnection.connect(false);
				addConnection(newConnection);
			}
			if(connectionsCount == 0)
				addConnection(new ConnectionTelemetry());

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
			
			return true;
			
		} catch (IOException ioe) {
			
			NotificationsController.showFailureUntil("Unable to open the settings file.", () -> false, true);
			return false;
			
		} catch(AssertionError ae) {
		
			ChartsController.removeAllCharts();
			for(Connection connection : allConnections)
				connection.disconnect(null);
			
			NotificationsController.showFailureUntil("Error while parsing the settings file:\nLine " + lines.lineNumber + ": " + ae.getMessage(), () -> false, true);
			return false;
		
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
