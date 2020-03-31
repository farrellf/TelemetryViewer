import java.text.SimpleDateFormat;
import java.util.List;

/**
 * The Settings / SettingsView / SettingsController classes form the MVC that manage GUI-related settings.
 * Settings can be changed when the user interacts with the GUI or opens a Layout file.
 * This class is the controller and handles all of those events. It decides if changes are acceptable, and notifies the GUI of those changes.
 */
public class SettingsController {
	
	/**
	 * Changes the OpenGLChartsRegion tile column count if it is within the allowed range and would not obscure part of an existing chart.
	 * 
	 * @param value    The new column count.
	 */
	public static void setTileColumns(int value) {
		
		// sanity check
		boolean chartsObscured = false;
		for(PositionedChart chart : Model.charts)
			if(chart.regionOccupied(value, 0, Settings.tileColumns, Settings.tileRows))
				chartsObscured = true;
		
		if(value >= Settings.tileColumnsMinimum && value <= Settings.tileColumnsMaximum && !chartsObscured)
			Settings.tileColumns = value;

		// apply change
		SettingsView.instance.tileColumnsTextfield.setText(Integer.toString(Settings.tileColumns));
		SettingsView.instance.tileRowsTextfield.setText(Integer.toString(Settings.tileRows));
		OpenGLChartsRegion.instance.tileColumns = Settings.tileColumns;
		OpenGLChartsRegion.instance.tileRows = Settings.tileRows;
		
	}
	
	/**
	 * @return    The current OpenGLChartsRegion tile column count.
	 */
	public static int getTileColumns() {
		
		return Settings.tileColumns;
		
	}
	
	/**
	 * Changes the OpenGLChartsRegion tile row count if it is within the allowed range and would not obscure part of an existing chart.
	 * 
	 * @param value    The new row count.
	 */
	public static void setTileRows(int value) {
		
		// sanity check
		boolean chartsObscured = false;
		for(PositionedChart chart : Model.charts)
			if(chart.regionOccupied(0, value, Settings.tileColumns, Settings.tileRows))
				chartsObscured = true;
		
		if(value >= Settings.tileRowsMinimum && value <= Settings.tileRowsMaximum && !chartsObscured)
			Settings.tileRows = value;

		// apply change
		SettingsView.instance.tileColumnsTextfield.setText(Integer.toString(Settings.tileColumns));
		SettingsView.instance.tileRowsTextfield.setText(Integer.toString(Settings.tileRows));
		OpenGLChartsRegion.instance.tileColumns = Settings.tileColumns;
		OpenGLChartsRegion.instance.tileRows = Settings.tileRows;
		
	}
	
	/**
	 * @return    The current OpenGLChartsRegion tile row count.
	 */
	public static int getTileRows() {
		
		return Settings.tileRows;
		
	}
	
	/**
	 * Changes the format used to display the date and time.
	 * Tooltips will always show the time with millisecond resolution.
	 * 
	 * @param format    One of the combobox options from SettingsView.timeFormatCombobox.
	 */
	public static void setTimeFormat(String format) {
		
		Settings.timeFormat = format;
		
		if(format.equals("YYYY-MM-DD HH:MM:SS.SSS")) {
			Theme.timestampFormatter        = new SimpleDateFormat(Settings.timeFormat24hours ? "yyyy-MM-dd\nkk:mm:ss.SSS" : "yyyy-MM-dd\nhh:mm:ss.SSS a");
			Theme.tooltipTimestampFormatter = new SimpleDateFormat(Settings.timeFormat24hours ? "yyyy-MM-dd\nkk:mm:ss.SSS" : "yyyy-MM-dd\nhh:mm:ss.SSS a");
			SettingsView.instance.timeFormatCombobox.setSelectedIndex(0);
		} else if(format.equals("YYYY-MM-DD HH:MM:SS")) {
			Theme.timestampFormatter        = new SimpleDateFormat(Settings.timeFormat24hours ? "yyyy-MM-dd\nkk:mm:ss"     : "yyyy-MM-dd\nhh:mm:ss a");
			Theme.tooltipTimestampFormatter = new SimpleDateFormat(Settings.timeFormat24hours ? "yyyy-MM-dd\nkk:mm:ss.SSS" : "yyyy-MM-dd\nhh:mm:ss.SSS a");
			SettingsView.instance.timeFormatCombobox.setSelectedIndex(1);
		} else if(format.equals("YYYY-MM-DD HH:MM")) {
			Theme.timestampFormatter        = new SimpleDateFormat(Settings.timeFormat24hours ? "yyyy-MM-dd\nkk:mm"        : "yyyy-MM-dd\nhh:mm a");
			Theme.tooltipTimestampFormatter = new SimpleDateFormat(Settings.timeFormat24hours ? "yyyy-MM-dd\nkk:mm:ss.SSS" : "yyyy-MM-dd\nhh:mm:ss.SSS a");
			SettingsView.instance.timeFormatCombobox.setSelectedIndex(2);
		} else if(format.equals("MM-DD-YYYY HH:MM:SS.SSS")) {
			Theme.timestampFormatter        = new SimpleDateFormat(Settings.timeFormat24hours ? "MM-dd-yyyy\nkk:mm:ss.SSS" : "MM-dd-yyyy\nhh:mm:ss.SSS a");
			Theme.tooltipTimestampFormatter = new SimpleDateFormat(Settings.timeFormat24hours ? "MM-dd-yyyy\nkk:mm:ss.SSS" : "MM-dd-yyyy\nhh:mm:ss.SSS a");
			SettingsView.instance.timeFormatCombobox.setSelectedIndex(3);
		} else if(format.equals("MM-DD-YYYY HH:MM:SS")) {
			Theme.timestampFormatter        = new SimpleDateFormat(Settings.timeFormat24hours ? "MM-dd-yyyy\nkk:mm:ss"     : "MM-dd-yyyy\nhh:mm:ss a");
			Theme.tooltipTimestampFormatter = new SimpleDateFormat(Settings.timeFormat24hours ? "MM-dd-yyyy\nkk:mm:ss.SSS" : "MM-dd-yyyy\nhh:mm:ss.SSS a");
			SettingsView.instance.timeFormatCombobox.setSelectedIndex(4);
		} else if(format.equals("MM-DD-YYYY HH:MM")) {
			Theme.timestampFormatter        = new SimpleDateFormat(Settings.timeFormat24hours ? "MM-dd-yyyy\nkk:mm"        : "MM-dd-yyyy\nhh:mm a");
			Theme.tooltipTimestampFormatter = new SimpleDateFormat(Settings.timeFormat24hours ? "MM-dd-yyyy\nkk:mm:ss.SSS" : "MM-dd-yyyy\nhh:mm:ss.SSS a");
			SettingsView.instance.timeFormatCombobox.setSelectedIndex(5);
		} else if(format.equals("DD-MM-YYYY HH:MM:SS.SSS")) {
			Theme.timestampFormatter        = new SimpleDateFormat(Settings.timeFormat24hours ? "dd-MM-yyyy\nkk:mm:ss.SSS" : "dd-MM-yyyy\nhh:mm:ss.SSS a");
			Theme.tooltipTimestampFormatter = new SimpleDateFormat(Settings.timeFormat24hours ? "dd-MM-yyyy\nkk:mm:ss.SSS" : "dd-MM-yyyy\nhh:mm:ss.SSS a");
			SettingsView.instance.timeFormatCombobox.setSelectedIndex(6);
		} else if(format.equals("DD-MM-YYYY HH:MM:SS")) {
			Theme.timestampFormatter        = new SimpleDateFormat(Settings.timeFormat24hours ? "dd-MM-yyyy\nkk:mm:ss"     : "dd-MM-yyyy\nhh:mm:ss a");
			Theme.tooltipTimestampFormatter = new SimpleDateFormat(Settings.timeFormat24hours ? "dd-MM-yyyy\nkk:mm:ss.SSS" : "dd-MM-yyyy\nhh:mm:ss.SSS a");
			SettingsView.instance.timeFormatCombobox.setSelectedIndex(7);
		} else if(format.equals("DD-MM-YYYY HH:MM")) {
			Theme.timestampFormatter        = new SimpleDateFormat(Settings.timeFormat24hours ? "dd-MM-yyyy\nkk:mm"        : "dd-MM-yyyy\nhh:mm a");
			Theme.tooltipTimestampFormatter = new SimpleDateFormat(Settings.timeFormat24hours ? "dd-MM-yyyy\nkk:mm:ss.SSS" : "dd-MM-yyyy\nhh:mm:ss.SSS a");
			SettingsView.instance.timeFormatCombobox.setSelectedIndex(8);
		} else if(format.equals("HH:MM:SS.SSS")) {
			Theme.timestampFormatter        = new SimpleDateFormat(Settings.timeFormat24hours ? "kk:mm:ss.SSS"            : "hh:mm:ss.SSS a");
			Theme.tooltipTimestampFormatter = new SimpleDateFormat(Settings.timeFormat24hours ? "kk:mm:ss.SSS"            : "hh:mm:ss.SSS a");
			SettingsView.instance.timeFormatCombobox.setSelectedIndex(9);
		} else if(format.equals("HH:MM:SS")) {
			Theme.timestampFormatter        = new SimpleDateFormat(Settings.timeFormat24hours ? "kk:mm:ss"                : "hh:mm:ss a");
			Theme.tooltipTimestampFormatter = new SimpleDateFormat(Settings.timeFormat24hours ? "kk:mm:ss.SSS"            : "hh:mm:ss.SSS a");
			SettingsView.instance.timeFormatCombobox.setSelectedIndex(10);
		} else if(format.equals("HH:MM")) {
			Theme.timestampFormatter        = new SimpleDateFormat(Settings.timeFormat24hours ? "kk:mm"                   : "hh:mm a");
			Theme.tooltipTimestampFormatter = new SimpleDateFormat(Settings.timeFormat24hours ? "kk:mm:ss.SSS"            : "hh:mm:ss.SSS a");
			SettingsView.instance.timeFormatCombobox.setSelectedIndex(11);
		} else {
			Settings.timeFormat = "YYYY-MM-DD HH:MM:SS.SSS";
			Theme.timestampFormatter        = new SimpleDateFormat(Settings.timeFormat24hours ? "yyyy-MM-dd\nkk:mm:ss.SSS" : "yyyy-MM-dd\nhh:mm:ss.SSS a");
			Theme.tooltipTimestampFormatter = new SimpleDateFormat(Settings.timeFormat24hours ? "yyyy-MM-dd\nkk:mm:ss.SSS" : "yyyy-MM-dd\nhh:mm:ss.SSS a");
			SettingsView.instance.timeFormatCombobox.setSelectedIndex(0);
		}
		
	}
	
	/**
	 * @return    The format used to display the date and time.
	 */
	public static String getTimeFormat() {
		
		return Settings.timeFormat;
		
	}
	
	/**
	 * Enables or disables 24-hour mode for displayed time.
	 * 
	 * @param value    True for 24-hour mode, false for AM/PM mode.
	 */
	public static void setTimeFormat24hours(boolean value) {
		
		Settings.timeFormat24hours = value;
		SettingsView.instance.timeFormat24hoursCheckbox.setSelected(value);
		
		setTimeFormat(Settings.timeFormat);
		
	}
	
	/**
	 * @return    True if the displayed time should use a 24-hour clock.
	 */
	public static boolean getTimeFormat24hours() {
		
		return Settings.timeFormat24hours;
		
	}
	
	/**
	 * Changes the tooltip visibility.
	 * 
	 * @param value    True to enable, false to disable.
	 */
	public static void setTooltipVisibility(boolean value) {
		
		Settings.tooltipVisibility = value;
		SettingsView.instance.showTooltipsCheckbox.setSelected(value);
		
	}
	
	/**
	 * @return    True if tooltips should be drawn.
	 */
	public static boolean getTooltipVisibility() {
		
		return Settings.tooltipVisibility;
		
	}
	
	/**
	 * Enables or disables Logitech smooth scrolling.
	 * 
	 * @param value    True to enable, false to disable.
	 */
	public static void setSmoothScrolling(boolean value) {
		
		Settings.smoothScrolling = value;
		SettingsView.instance.enableSmoothScrollingCheckbox.setSelected(Settings.smoothScrolling);
		Main.mouse.updateScrolling();
		
	}
	
	/**
	 * @return    True if Logitech smooth scrolling is enabled.
	 */
	public static boolean getSmoothScrolling() {
		
		return Settings.smoothScrolling;
		
	}
	
	/**
	 * Enables or disables antialiasing.
	 * 
	 * @param value    True to enable, false to disable.
	 */
	public static void setAntialiasing(boolean value) {
		
		Settings.antialiasing = value;
		SettingsView.instance.enableAntialiasingCheckbox.setSelected(value);
		
	}
	
	/**
	 * @return    True if antialiasing is enabled.
	 */
	public static boolean getAntialiasing() {
		
		return Settings.antialiasing;
		
	}
	
	/**
	 * Changes the FPS/period visibility.
	 * 
	 * @param value    True to enable, false to disable.
	 */
	public static void setFpsVisibility(boolean value) {
		
		Settings.fpsVisibility = value;
		SettingsView.instance.showFpsCheckbox.setSelected(value);
		
	}
	
	/**
	 * @return    True if FPS/period measurements should be drawn.
	 */
	public static boolean getFpsVisibility() {
		
		return Settings.fpsVisibility;
		
	}
	
	/**
	 * Call this function to indicate that the a chart needs to be selected for benchmarking.
	 */
	public static void awaitBenchmarkedChart() {
		
		Settings.awaitingChartForBenchmark = true;
		
	}
	
	/**
	 * @return    True if the user is supposed to click on a chart they want to benchmark.
	 */
	public static boolean awaitingBenchmarkedChart() {
		
		return Settings.awaitingChartForBenchmark;
		
	}
	
	/**
	 * Changes the chart to be benchmarked.
	 * 
	 * @param chart    The chart to benchmark, or null to disable benchmarking.
	 */
	public static void setBenchmarkedChart(PositionedChart chart) {
		
		Settings.chartForBenchmarks = chart;
		Settings.awaitingChartForBenchmark = false;

		SettingsView.instance.showBenchmarksCheckbox.setSelected(chart != null);
		SettingsView.instance.showBenchmarksCheckbox.setEnabled(true);
		
	}
	
	/**
	 * Changes the chart to be benchmarked.
	 * 
	 * @param index    The index of the chart to benchmark, or -1 to disable benchmarking.
	 */
	public static void setBenchmarkedChartByIndex(int index) {

		setBenchmarkedChart(index >= 0 ? Controller.getCharts().get(index) : null);
		
	}
	
	/**
	 * @return    The chart that is being benchmarked, or null if benchmarking is disabled.
	 */
	public static PositionedChart getBenchmarkedChart() {
		
		return Settings.chartForBenchmarks;
		
	}
	
	/**
	 * @return    The index of the chart that is being benchmarked, or -1 if benchmarking is disabled.
	 */
	public static int getBenchmarkedChartIndex() {

		List<PositionedChart> charts = Controller.getCharts();
		for(int i = 0; i < charts.size(); i++)
			if(charts.get(i) == Settings.chartForBenchmarks)
				return i;
		
		return -1;
		
	}
	
}
