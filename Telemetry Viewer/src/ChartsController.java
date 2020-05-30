import java.awt.Toolkit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ChartsController {
	
	private static List<PositionedChart> charts = Collections.synchronizedList(new ArrayList<PositionedChart>());
	
	private static float dpiScalingFactorJava8 = (int) Math.round((double) Toolkit.getDefaultToolkit().getScreenResolution() / 100.0); // will be reset to 1.0 if using java 9+
	private static float dpiScalingFactorJava9 = 1; // will be updated dynamically if using java 9+
	private static float dpiScalingFactorUser = 1; // may be updated by the user
	
	/**
	 * @return    The display scaling factor. This takes into account the true DPI scaling requested by the OS, plus the user's modification (if any.)
	 */
	public static float getDisplayScalingFactor() {
		
		return dpiScalingFactorUser * dpiScalingFactorJava8 * dpiScalingFactorJava9;
		
	}
	
	/**
	 * @return    The display scaling factor requested by the user.
	 */
	public static float getDisplayScalingFactorUser() {
		
		return dpiScalingFactorUser;
		
	}
	
	/**
	 * @param newFactor    The new display scaling factor specified by the user.
	 */
	public static void setDisplayScalingFactorUser(float newFactor) {
		
		if(newFactor < 1) newFactor = 1;
		if(newFactor > 10) newFactor = 10;
		
		dpiScalingFactorUser = newFactor;
		
	}
	
	/**
	 * @param newFactor    The new display scaling factor specified by the OS if using Java 9+.
	 */
	public static void setDisplayScalingFactorJava9(float newFactor) {
		
		if(newFactor == dpiScalingFactorJava9)
			return;
		
		if(newFactor < 1) newFactor = 1;
		if(newFactor > 10) newFactor = 10;
		
		dpiScalingFactorJava9 = newFactor;
		dpiScalingFactorJava8 = 1; // only use the Java9 scaling factor
		
	}
	
	/**
	 * @return    The display scaling factor specified by the OS if using Java 9+.
	 */
	public static float getDisplayScalingFactorJava9() {
		
		return dpiScalingFactorJava9;
		
	}
	
	/**
	 * @return    An array of Strings, one for each possible chart type.
	 */
	public static String[] getChartTypes() {
		
		return new String[] {
			"Time Domain",
			"Frequency Domain",
			"Histogram",
			"Dial",
			"Quaternion",
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
		
		     if(chartType.equals("Time Domain"))      chart = new OpenGLTimeDomainChart(x1, y1, x2, y2);
		else if(chartType.equals("Frequency Domain")) chart = new OpenGLFrequencyDomainChart(x1, y1, x2, y2);
		else if(chartType.equals("Histogram"))        chart = new OpenGLHistogramChart(x1, y1, x2, y2);
		else if(chartType.equals("Dial"))             chart = new OpenGLDialChart(x1, y1, x2, y2);
		else if(chartType.equals("Quaternion"))       chart = new OpenGLQuaternionChart(x1, y1, x2, y2);
		else if(chartType.equals("Camera"))           chart = new OpenGLCameraChart(x1, y1, x2, y2);
		else if(chartType.equals("Timeline"))         chart = new OpenGLTimelineChart(x1, y1, x2, y2);
		
		if(chart != null)
			ChartsController.addChart(chart);
		
		return chart;
		
	}
	
	/**
	 * @param chart    New chart to insert and display.
	 */
	public static void addChart(PositionedChart chart) {
		
		charts.add(chart);
		updateTileOccupancy(null);
		
	}
	
	/**
	 * Reorders the list of charts so the specified chart will be rendered after all other charts.
	 * 
	 * @param chart    The chart to render last.
	 */
	public static void drawChartLast(PositionedChart chart) {
		
		if(charts.size() < 2)
			return;
		
		Collections.swap(charts, charts.indexOf(chart), charts.size() - 1);
		
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
		charts.remove(chart);
		updateTileOccupancy(null);
		
	}
	
	/**
	 * Removes all charts.
	 */
	public static void removeAllCharts() {
		
		// many a temporary copy of the list because you can't remove from a list that you are iterating over
		List<PositionedChart> list = new ArrayList<PositionedChart>(charts);
		
		for(PositionedChart chart : list)
			removeChart(chart);
		
	}
	
	/**
	 * @return    All charts.
	 */
	public static List<PositionedChart> getCharts() {
		
		return charts;
		
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

		for(PositionedChart chart : charts)
			if(chart.regionOccupied(topLeftX, topLeftY, bottomRightX, bottomRightY))
				return false;
		
		return true;
		
	}
	
	private static boolean[][] tileOccupied = new boolean[SettingsController.getTileColumns()][SettingsController.getTileRows()];
	
	/**
	 * Updates the array that tracks which tiles in the OpenGLChartsRegion are occupied by charts.
	 * 
	 * @param removingChart    If not null, pretend this chart does not exist, so the tiles behind it will be drawn while this chart fades away.
	 */
	public static void updateTileOccupancy(PositionedChart removingChart) {
		
		int columns = SettingsController.getTileColumns();
		int rows = SettingsController.getTileRows();
		
		tileOccupied = new boolean[columns][rows];
		for(PositionedChart chart : getCharts()) {
			for(int x = chart.topLeftX; x <= chart.bottomRightX; x++)
				for(int y = chart.topLeftY; y <= chart.bottomRightY; y++)
					tileOccupied[x][rows - y - 1] = true;
		}
		
		if(removingChart != null)
			for(int x = removingChart.topLeftX; x <= removingChart.bottomRightX; x++)
				for(int y = removingChart.topLeftY; y <= removingChart.bottomRightY; y++)
					tileOccupied[x][rows - y - 1] = false;
		
	}
	
	/**
	 * @return    An array indicating which tiles in the OpenGLChartsRegion are occupied.
	 */
	public static boolean[][] getTileOccupancy() {
		
		return tileOccupied;
		
	}
	
}
