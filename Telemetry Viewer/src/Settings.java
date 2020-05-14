
/**
 * The Settings / SettingsView / SettingsController classes form the MVC that manage GUI-related settings.
 * Settings can be changed when the user interacts with the GUI or opens a Layout file.
 * This class contains the actual settings.
 */
public class Settings {
	
	// tiles in the ChartsRegion where users can position and size the charts
	final static int tileColumnsDefault = 6;
	final static int tileColumnsMinimum = 1;
	final static int tileColumnsMaximum = 15;
	static int tileColumns = tileColumnsDefault;
	
	final static int tileRowsDefault = 6;
	final static int tileRowsMinimum = 1;
	final static int tileRowsMaximum = 15;
	static int tileRows = tileRowsDefault;
	
	// how the date/time should be drawn
	static String timeFormat = "YYYY-MM-DD HH:MM:SS.SSS";
	static boolean timeFormat24hours = false;
	
	// if plot tooltips should be drawn
	static boolean tooltipVisibility = true;
	
	// if logitech smooth scrolling should be enabled
	static boolean smoothScrolling = true;
	
	// OpenGL multisample (MSAA) level, use 1 to disable antialiasing
	static int antialiasingLevel = 16;
	
	// if the FPS and period should be drawn
	static boolean fpsVisibility = false;
	
	// which chart to measure for CPU/GPU times, or null to not measure
	static PositionedChart chartForBenchmarks = null;
	static boolean awaitingChartForBenchmark = false;

}
