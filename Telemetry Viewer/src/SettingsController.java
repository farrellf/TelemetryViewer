import java.awt.Color;
import java.text.SimpleDateFormat;
import java.util.List;

import javax.swing.SwingUtilities;

/**
 * The SettingsView and SettingsController classes form the MVC that manage GUI-related settings.
 * Settings can be changed when the user interacts with the GUI or opens a Layout file.
 * This class is the controller and handles all of those events. It decides if changes are acceptable, and notifies the GUI of those changes.
 */
public class SettingsController {

	// grid size for the OpenGLChartsView
	private final static int tileColumnsDefault = 6;
	private final static int tileColumnsMinimum = 1;
	private final static int tileColumnsMaximum = 15;
	private static int tileColumns = tileColumnsDefault;
	
	private final static int tileRowsDefault = 6;
	private final static int tileRowsMinimum = 1;
	private final static int tileRowsMaximum = 15;
	private static int tileRows = tileRowsDefault;
	
	// how the date/time should be drawn
	private static String timeFormat = "Only Time";
	private static boolean timeFormat24hours = false;
	private static boolean timeFormatAsTwoLines = false;
	public static SimpleDateFormat timestampFormatterMilliseconds = new SimpleDateFormat("hh:mm:ss.SSS a");
	public static SimpleDateFormat timestampFormatterSeconds      = new SimpleDateFormat("hh:mm:ss a");
	public static SimpleDateFormat timestampFormatterMinutes      = new SimpleDateFormat("hh:mm a");
	
	// if the various notification levels should be displayed
	private static boolean hintNotificationVisibility = true;
	private static boolean warningNotificationVisibility = true;
	private static boolean failureNotificationVisibility = true;
	private static boolean verboseNotificationVisibility = false;
	private static Color hintColor    = Color.GREEN;
	private static Color warningColor = Color.YELLOW;
	private static Color failureColor = Color.RED;
	private static Color verboseColor = Color.CYAN;
	
	// if plot tooltips should be drawn
	private static boolean tooltipVisibility = true;
	
	// if logitech smooth scrolling should be enabled
	private static boolean smoothScrolling = true;
	
	// OpenGL multisample (MSAA) level, use 1 to disable antialiasing
	private static int antialiasingLevel = 16;
	
	// if the FPS and period should be drawn
	private static boolean fpsVisibility = false;
	
	// which chart to measure for CPU/GPU times, or null to not measure
	private static PositionedChart chartForBenchmarks = null;
	private static boolean awaitingChartForBenchmark = false;
	
	/**
	 * Changes the OpenGLChartsRegion tile column count if it is within the allowed range and would not obscure part of an existing chart.
	 * 
	 * @param value    The new column count.
	 */
	public static void setTileColumns(int value) {
		
		// sanity check
		boolean chartsObscured = false;
		for(PositionedChart chart : ChartsController.getCharts())
			if(chart.regionOccupied(value, 0, tileColumns, tileRows))
				chartsObscured = true;
		
		if(value >= tileColumnsMinimum && value <= tileColumnsMaximum && !chartsObscured)
			tileColumns = value;

		// apply change
		SettingsView.instance.tileColumnsTextfield.setText(Integer.toString(tileColumns));
		SettingsView.instance.tileRowsTextfield.setText(Integer.toString(tileRows));
		ChartsController.updateTileOccupancy(null);
		OpenGLChartsView.instance.tileColumns = tileColumns;
		OpenGLChartsView.instance.tileRows = tileRows;
		
	}
	
	/**
	 * @return    The current OpenGLChartsRegion tile column count.
	 */
	public static int getTileColumns() {
		
		return tileColumns;
		
	}
	
	/**
	 * Changes the OpenGLChartsRegion tile row count if it is within the allowed range and would not obscure part of an existing chart.
	 * 
	 * @param value    The new row count.
	 */
	public static void setTileRows(int value) {
		
		// sanity check
		boolean chartsObscured = false;
		for(PositionedChart chart : ChartsController.getCharts())
			if(chart.regionOccupied(0, value, tileColumns, tileRows))
				chartsObscured = true;
		
		if(value >= tileRowsMinimum && value <= tileRowsMaximum && !chartsObscured)
			tileRows = value;

		// apply change
		SettingsView.instance.tileColumnsTextfield.setText(Integer.toString(tileColumns));
		SettingsView.instance.tileRowsTextfield.setText(Integer.toString(tileRows));
		ChartsController.updateTileOccupancy(null);
		OpenGLChartsView.instance.tileColumns = tileColumns;
		OpenGLChartsView.instance.tileRows = tileRows;
		
	}
	
	/**
	 * @return    The current OpenGLChartsRegion tile row count.
	 */
	public static int getTileRows() {
		
		return tileRows;
		
	}
	
	/**
	 * Changes the format used to display the date and time.
	 * 
	 * @param format    One of the combobox options from SettingsView.timeFormatCombobox.
	 */
	public static void setTimeFormat(String format) {
		
		timeFormat = format;
		
		if(format.equals("Time and YYYY-MM-DD")) {
			timestampFormatterMilliseconds = new SimpleDateFormat(timeFormat24hours ? "kk:mm:ss.SSS\nyyyy-MM-dd" : "hh:mm:ss.SSS a\nyyyy-MM-dd");
			timestampFormatterSeconds      = new SimpleDateFormat(timeFormat24hours ? "kk:mm:ss\nyyyy-MM-dd"     : "hh:mm:ss a\nyyyy-MM-dd");
			timestampFormatterMinutes      = new SimpleDateFormat(timeFormat24hours ? "kk:mm\nyyyy-MM-dd"        : "hh:mm a\nyyyy-MM-dd");
			timeFormatAsTwoLines = true;
			SettingsView.instance.timeFormatCombobox.setSelectedIndex(0);
		} else if(format.equals("Time and MM-DD-YYYY")) {
			timestampFormatterMilliseconds = new SimpleDateFormat(timeFormat24hours ? "kk:mm:ss.SSS\nMM-dd-yyyy" : "hh:mm:ss.SSS a\nMM-dd-yyyy");
			timestampFormatterSeconds      = new SimpleDateFormat(timeFormat24hours ? "kk:mm:ss\nMM-dd-yyyy"     : "hh:mm:ss a\nMM-dd-yyyy");
			timestampFormatterMinutes      = new SimpleDateFormat(timeFormat24hours ? "kk:mm\nMM-dd-yyyy"        : "hh:mm a\nMM-dd-yyyy");
			timeFormatAsTwoLines = true;
			SettingsView.instance.timeFormatCombobox.setSelectedIndex(1);
		} else if(format.equals("Time and DD-MM-YYYY")) {
			timestampFormatterMilliseconds = new SimpleDateFormat(timeFormat24hours ? "kk:mm:ss.SSS\ndd-MM-yyyy" : "hh:mm:ss.SSS a\ndd-MM-yyyy");
			timestampFormatterSeconds      = new SimpleDateFormat(timeFormat24hours ? "kk:mm:ss\ndd-MM-yyyy"     : "hh:mm:ss a\ndd-MM-yyyy");
			timestampFormatterMinutes      = new SimpleDateFormat(timeFormat24hours ? "kk:mm\ndd-MM-yyyy"        : "hh:mm a\ndd-MM-yyyy");
			timeFormatAsTwoLines = true;
			SettingsView.instance.timeFormatCombobox.setSelectedIndex(2);
		} else {
			timestampFormatterMilliseconds = new SimpleDateFormat(timeFormat24hours ? "kk:mm:ss.SSS" : "hh:mm:ss.SSS a");
			timestampFormatterSeconds      = new SimpleDateFormat(timeFormat24hours ? "kk:mm:ss"     : "hh:mm:ss a");
			timestampFormatterMinutes      = new SimpleDateFormat(timeFormat24hours ? "kk:mm"        : "hh:mm a");
			timeFormatAsTwoLines = false;
			SettingsView.instance.timeFormatCombobox.setSelectedIndex(3);
		}
		
	}
	
	/**
	 * @return    The format used to display the date and time.
	 */
	public static String getTimeFormat() {
		
		return timeFormat;
		
	}
	
	/**
	 * @return    The supported time formats, to be shown in the SettingsView drop-down box.
	 */
	public static String[] getTimeFormats() {
		
		return new String[] {"Time and YYYY-MM-DD",
		                     "Time and MM-DD-YYYY",
		                     "Time and DD-MM-YYYY",
		                     "Only Time"};
		
	}
	
	public static boolean isTimeFormatTwoLines() {
		
		return timeFormatAsTwoLines;
		
	}
	
	/**
	 * Converts a timestamp into a String representation with milliseconds resolution.
	 * 
	 * @param timestamp    The timestamp (milliseconds since 1970-01-01.)
	 * @return             Text representation.
	 */
	public static String formatTimestampToMilliseconds(long timestamp) {
		
		return timestampFormatterMilliseconds.format(timestamp);
		
	}
	
	/**
	 * Converts a timestamp into a String representation with seconds resolution (hides milliseconds.)
	 * 
	 * @param timestamp    The timestamp (milliseconds since 1970-01-01.)
	 * @return             Text representation.
	 */
	public static String formatTimestampToSeconds(long timestamp) {
		
		return timestampFormatterSeconds.format(timestamp);
		
	}
	
	/**
	 * Converts a timestamp into a String representation with minutes resolution (hides seconds and milliseconds.)
	 * 
	 * @param timestamp    The timestamp (milliseconds since 1970-01-01.)
	 * @return             Text representation.
	 */
	public static String formatTimestampToMinutes(long timestamp) {
		
		return timestampFormatterMinutes.format(timestamp);
		
	}
	
	/**
	 * Enables or disables 24-hour mode for displayed time.
	 * 
	 * @param value    True for 24-hour mode, false for AM/PM mode.
	 */
	public static void setTimeFormat24hours(boolean value) {
		
		timeFormat24hours = value;
		SettingsView.instance.timeFormat24hoursCheckbox.setSelected(value);
		
		setTimeFormat(timeFormat);
		
	}
	
	/**
	 * @return    True if the displayed time should use a 24-hour clock.
	 */
	public static boolean getTimeFormat24hours() {
		
		return timeFormat24hours;
		
	}
	
	/**
	 * Changes the tooltip visibility.
	 * 
	 * @param value    True to enable, false to disable.
	 */
	public static void setTooltipVisibility(boolean value) {
		
		tooltipVisibility = value;
		SettingsView.instance.showTooltipsCheckbox.setSelected(value);
		
	}
	
	/**
	 * @return    True if tooltips should be drawn.
	 */
	public static boolean getTooltipVisibility() {
		
		return tooltipVisibility;
		
	}
	
	/**
	 * Enables or disables Logitech smooth scrolling.
	 * 
	 * @param value    True to enable, false to disable.
	 */
	public static void setSmoothScrolling(boolean value) {
		
		smoothScrolling = value;
		SettingsView.instance.enableSmoothScrollingCheckbox.setSelected(smoothScrolling);
		Main.mouse.updateScrolling();
		
	}
	
	/**
	 * @return    True if Logitech smooth scrolling is enabled.
	 */
	public static boolean getSmoothScrolling() {
		
		return smoothScrolling;
		
	}
	
	/**
	 * Changes the FPS/period visibility.
	 * 
	 * @param value    True to enable, false to disable.
	 */
	public static void setFpsVisibility(boolean value) {
		
		fpsVisibility = value;
		SettingsView.instance.showFpsCheckbox.setSelected(value);
		
	}
	
	/**
	 * @return    True if FPS/period measurements should be drawn.
	 */
	public static boolean getFpsVisibility() {
		
		return fpsVisibility;
		
	}
	
	/**
	 * Call this function to indicate that the a chart needs to be selected for benchmarking.
	 */
	public static void awaitBenchmarkedChart() {
		
		awaitingChartForBenchmark = true;
		
	}
	
	/**
	 * @return    True if the user is supposed to click on a chart they want to benchmark.
	 */
	public static boolean awaitingBenchmarkedChart() {
		
		return awaitingChartForBenchmark;
		
	}
	
	/**
	 * Changes the chart to be benchmarked.
	 * 
	 * @param chart    The chart to benchmark, or null to disable benchmarking.
	 */
	public static void setBenchmarkedChart(PositionedChart chart) {
		
		chartForBenchmarks = chart;
		awaitingChartForBenchmark = false;

		SettingsView.instance.showBenchmarksCheckbox.setSelected(chart != null);
		SettingsView.instance.showBenchmarksCheckbox.setEnabled(true);
		
	}
	
	/**
	 * Changes the chart to be benchmarked.
	 * 
	 * @param index    The index of the chart to benchmark, or -1 to disable benchmarking.
	 */
	public static void setBenchmarkedChartByIndex(int index) {

		setBenchmarkedChart(index >= 0 ? ChartsController.getCharts().get(index) : null);
		
	}
	
	/**
	 * @return    The chart that is being benchmarked, or null if benchmarking is disabled.
	 */
	public static PositionedChart getBenchmarkedChart() {
		
		return chartForBenchmarks;
		
	}
	
	/**
	 * @return    The index of the chart that is being benchmarked, or -1 if benchmarking is disabled.
	 */
	public static int getBenchmarkedChartIndex() {

		List<PositionedChart> charts = ChartsController.getCharts();
		for(int i = 0; i < charts.size(); i++)
			if(charts.get(i) == chartForBenchmarks)
				return i;
		
		return -1;
		
	}
	
	/**
	 * Sets the OpenGL multisample (MSAA) level.
	 * 
	 * @param level    MSAA level, use 1 to disable antialiasing.
	 */
	public static void setAntialiasingLevel(int level) {
		
		if(antialiasingLevel != level)
			SwingUtilities.invokeLater(() -> OpenGLChartsView.regenerate());
		
		antialiasingLevel = level;
		SettingsView.instance.antialiasingLevelSlider.setValue((int) (Math.log(level) / Math.log(2)));
		
	}
	
	/**
	 * @return    The MSAA level.
	 */
	public static int getAntialiasingLevel() {
		
		return antialiasingLevel;
		
	}
	
	/**
	 * Changes the hint notifications visibility.
	 * 
	 * @param value    True to enable, false to disable.
	 */
	public static void setHintNotificationVisibility(boolean isVisible) {
		
		hintNotificationVisibility = isVisible;
		SettingsView.instance.hintNotificationsCheckbox.setSelected(isVisible);
		if(!isVisible)
			NotificationsController.getNotifications().removeIf(item -> item.level.equals("hint"));
		
	}
	
	/**
	 * @return    True if hint notifications should be displayed.
	 */
	public static boolean getHintNotificationVisibility() {
		
		return hintNotificationVisibility;
		
	}
	
	/**
	 * Changes the hint notifications color.
	 * 
	 * @param color    The new color.
	 */
	public static void setHintNotificationColor(Color color) {
		
		hintColor = color;
		SettingsView.instance.hintNotificationsColorButton.setForeground(color);
		for(NotificationsController.Notification notification : NotificationsController.getNotifications())
			if(notification.level.equals("hint"))
				notification.glColor = new float[] {hintColor.getRed() / 255f, hintColor.getGreen() / 255f, hintColor.getBlue() / 255f, 0.2f};
		
	}
	
	/**
	 * @return    The background color used for hint notifications.
	 */
	public static Color getHintNotificationColor() {
		
		return hintColor;
		
	}
	
	/**
	 * Changes the warning notifications visibility.
	 * 
	 * @param value    True to enable, false to disable.
	 */
	public static void setWarningNotificationVisibility(boolean isVisible) {
		
		warningNotificationVisibility = isVisible;
		SettingsView.instance.warningNotificationsCheckbox.setSelected(isVisible);
		if(!isVisible)
			NotificationsController.getNotifications().removeIf(item -> item.level.equals("warning"));
		
	}
	
	/**
	 * @return    True if warning notifications should be displayed.
	 */
	public static boolean getWarningNotificationVisibility() {
		
		return warningNotificationVisibility;
		
	}
	
	/**
	 * Changes the warning notifications color.
	 * 
	 * @param color    The new color.
	 */
	public static void setWarningNotificationColor(Color color) {
		
		warningColor = color;
		SettingsView.instance.warningNotificationsColorButton.setForeground(color);
		for(NotificationsController.Notification notification : NotificationsController.getNotifications())
			if(notification.level.equals("warning"))
				notification.glColor = new float[] {warningColor.getRed() / 255f, warningColor.getGreen() / 255f, warningColor.getBlue() / 255f, 0.2f};
		
	}
	
	/**
	 * @return    The background color used for warning notifications.
	 */
	public static Color getWarningNotificationColor() {
		
		return warningColor;
		
	}
	
	/**
	 * Changes the failure notifications visibility.
	 * 
	 * @param value    True to enable, false to disable.
	 */
	public static void setFailureNotificationVisibility(boolean isVisible) {
		
		failureNotificationVisibility = isVisible;
		SettingsView.instance.failureNotificationsCheckbox.setSelected(isVisible);
		if(!isVisible)
			NotificationsController.getNotifications().removeIf(item -> item.level.equals("failure"));
		
	}
	
	/**
	 * @return    True if warning notifications should be displayed.
	 */
	public static boolean getFailureNotificationVisibility() {
		
		return failureNotificationVisibility;
		
	}
	
	/**
	 * Changes the failure notifications color.
	 * 
	 * @param color    The new color.
	 */
	public static void setFailureNotificationColor(Color color) {
		
		failureColor = color;
		SettingsView.instance.failureNotificationsColorButton.setForeground(color);
		for(NotificationsController.Notification notification : NotificationsController.getNotifications())
			if(notification.level.equals("failure"))
				notification.glColor = new float[] {failureColor.getRed() / 255f, failureColor.getGreen() / 255f, failureColor.getBlue() / 255f, 0.2f};
		
	}
	
	/**
	 * @return    The background color used for failure notifications.
	 */
	public static Color getFailureNotificationColor() {
		
		return failureColor;
		
	}
	
	/**
	 * Changes the verbose notifications visibility.
	 * 
	 * @param value    True to enable, false to disable.
	 */
	public static void setVerboseNotificationVisibility(boolean isVisible) {
		
		verboseNotificationVisibility = isVisible;
		SettingsView.instance.verboseNotificationsCheckbox.setSelected(isVisible);
		if(!isVisible)
			NotificationsController.getNotifications().removeIf(item -> item.level.equals("verbose"));
		
	}
	
	/**
	 * @return    True if verbose notifications should be displayed.
	 */
	public static boolean getVerboseNotificationVisibility() {
		
		return verboseNotificationVisibility;
		
	}
	
	/**
	 * Changes the verbose notifications color.
	 * 
	 * @param color    The new color.
	 */
	public static void setVerboseNotificationColor(Color color) {
		
		verboseColor = color;
		SettingsView.instance.verboseNotificationsColorButton.setForeground(color);
		for(NotificationsController.Notification notification : NotificationsController.getNotifications())
			if(notification.level.equals("verbose"))
				notification.glColor = new float[] {verboseColor.getRed() / 255f, verboseColor.getGreen() / 255f, verboseColor.getBlue() / 255f, 0.2f};
		
	}
	
	/**
	 * @return    The background color used for verbose notifications.
	 */
	public static Color getVerboseNotificationColor() {
		
		return verboseColor;
		
	}
	
}
