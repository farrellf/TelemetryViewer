import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * The Settings / SettingsView / SettingsController classes form the MVC that manage GUI-related settings.
 * Settings can be changed when the user interacts with the GUI or opens a Layout file.
 * This class is the controller and handles all of those events. It decides if changes are acceptable, and notifies the GUI of those changes.
 */
public class SettingsController {
	
	static List<BiConsumer<Integer, Integer>> tileCountListeners = new ArrayList<BiConsumer<Integer, Integer>>();
	static List<Consumer<Boolean>> tooltipVisibilityListeners = new ArrayList<Consumer<Boolean>>();
	static List<Consumer<Boolean>> smoothScrollingListeners = new ArrayList<Consumer<Boolean>>();
	static List<Consumer<Boolean>> antialiasingListeners = new ArrayList<Consumer<Boolean>>();
	static List<Consumer<Boolean>> fpsVisibilityListeners = new ArrayList<Consumer<Boolean>>();
	static List<Consumer<PositionedChart>> benchmarkedChartListeners = new ArrayList<Consumer<PositionedChart>>();

	/**
	 * Registers a listener that will be notified when the number of tile rows or columns in the ChartsRegion changes.
	 * 
	 * @param listener    The listener to be notified.
	 */
	public static void addTileCountListener(BiConsumer<Integer, Integer> listener) {
		
		tileCountListeners.add(listener);
		
	}
	
	/**
	 * Notifies all registered listeners about a new ChartsRegion tile count.
	 */
	private static void notifyTileCountListeners() {
		
		for(BiConsumer<Integer, Integer> listener : tileCountListeners)
			listener.accept(Settings.tileColumns, Settings.tileRows);
		
	}
	
	/**
	 * Changes the ChartsRegion tile column count if it is within the allowed range and would not obscure part of an existing chart.
	 * 
	 * @param value    The new column count.
	 */
	public static void setTileColumns(int value) {
		
		boolean chartsObscured = false;
		for(PositionedChart chart : Model.charts)
			if(chart.regionOccupied(value, 0, Settings.tileColumns, Settings.tileRows))
				chartsObscured = true;
		
		if(value >= Settings.tileColumnsMinimum && value <= Settings.tileColumnsMaximum && !chartsObscured)
			Settings.tileColumns = value;

		notifyTileCountListeners();
		
	}
	
	/**
	 * @return    The current ChartsRegion tile column count.
	 */
	public static int getTileColumns() {
		
		return Settings.tileColumns;
		
	}
	
	/**
	 * Changes the ChartsRegion tile row count if it is within the allowed range and would not obscure part of an existing chart.
	 * 
	 * @param value    The new row count.
	 */
	public static void setTileRows(int value) {
		
		boolean chartsObscured = false;
		for(PositionedChart chart : Model.charts)
			if(chart.regionOccupied(0, value, Settings.tileColumns, Settings.tileRows))
				chartsObscured = true;
		
		if(value >= Settings.tileRowsMinimum && value <= Settings.tileRowsMaximum && !chartsObscured)
			Settings.tileRows = value;

		notifyTileCountListeners();
		
	}
	
	/**
	 * @return    The current ChartsRegion tile row count.
	 */
	public static int getTileRows() {
		
		return Settings.tileRows;
		
	}
	
	/**
	 * Registers a listener that will be notified when tooltip visibility changes.
	 * 
	 * @param listener    The listener to be notified.
	 */
	public static void addTooltipVisibilityListener(Consumer<Boolean> listener) {
		
		tooltipVisibilityListeners.add(listener);
		
	}
	
	/**
	 * Notifies all registered listeners about a new tooltip visibility.
	 */
	private static void notifyTooltipVisibilityListeners() {
		
		for(Consumer<Boolean> listener : tooltipVisibilityListeners)
			listener.accept(Settings.tooltipVisibility);
		
	}
	
	/**
	 * Changes the tooltip visibility.
	 * 
	 * @param value    True to enable, false to disable.
	 */
	public static void setTooltipVisibility(boolean value) {
		
		Settings.tooltipVisibility = value;
		notifyTooltipVisibilityListeners();
		
	}
	
	/**
	 * @return    True if tooltips should be drawn.
	 */
	public static boolean getTooltipVisibility() {
		
		return Settings.tooltipVisibility;
		
	}
	
	/**
	 * Registers a listener that will be notified when Logitech smooth scrolling is enabled or disabled.
	 * 
	 * @param listener    The listener to be notified.
	 */
	public static void addSmoothScrollingListener(Consumer<Boolean> listener) {
		
		smoothScrollingListeners.add(listener);
		
	}
	
	/**
	 * Notifies all registered listeners about a new state for Logitech smooth scrolling.
	 */
	private static void notifySmoothScrollingListeners() {
		
		for(Consumer<Boolean> listener : smoothScrollingListeners)
			listener.accept(Settings.smoothScrolling);
		
	}
	
	/**
	 * Enables or disables Logitech smooth scrolling.
	 * 
	 * @param value    True to enable, false to disable.
	 */
	public static void setSmoothScrolling(boolean value) {
		
		Settings.smoothScrolling = value;
		notifySmoothScrollingListeners();
		
	}
	
	/**
	 * @return    True if Logitech smooth scrolling is enabled.
	 */
	public static boolean getSmoothScrolling() {
		
		return Settings.smoothScrolling;
		
	}
	
	/**
	 * Registers a listener that will be notified when antialiasing is enabled or disabled.
	 * 
	 * @param listener    The listener to be notified.
	 */
	public static void addAntialiasingListener(Consumer<Boolean> listener) {
		
		antialiasingListeners.add(listener);
		
	}
	
	/**
	 * Notifies all registered listeners about a new state for antialiasing.
	 */
	private static void notifyAntialiasingListeners() {
		
		for(Consumer<Boolean> listener : antialiasingListeners)
			listener.accept(Settings.antialiasing);
		
	}
	
	/**
	 * Enables or disables antialiasing.
	 * 
	 * @param value    True to enable, false to disable.
	 */
	public static void setAntialiasing(boolean value) {
		
		Settings.antialiasing = value;
		notifyAntialiasingListeners();
		
	}
	
	/**
	 * @return    True if antialiasing is enabled.
	 */
	public static boolean getAntialiasing() {
		
		return Settings.antialiasing;
		
	}
	
	/**
	 * Registers a listener that will be notified when FPS/period visibility changes.
	 * 
	 * @param listener    The listener to be notified.
	 */
	public static void addFpsVisibilityListener(Consumer<Boolean> listener) {
		
		fpsVisibilityListeners.add(listener);
		
	}
	
	/**
	 * Notifies all registered listeners about a new visibility for FPS/period.
	 */
	private static void notifyFpsVisibilityListeners() {
		
		for(Consumer<Boolean> listener : fpsVisibilityListeners)
			listener.accept(Settings.fpsVisibility);
		
	}
	
	/**
	 * Changes the FPS/period visibility.
	 * 
	 * @param value    True to enable, false to disable.
	 */
	public static void setFpsVisibility(boolean value) {
		
		Settings.fpsVisibility = value;
		notifyFpsVisibilityListeners();
		
	}
	
	/**
	 * @return    True if FPS/period measurements should be drawn.
	 */
	public static boolean getFpsVisibility() {
		
		return Settings.fpsVisibility;
		
	}
	
	/**
	 * Registers a listener that will be notified when chart benchmarking changes.
	 * 
	 * @param listener    The listener to be notified.
	 */
	public static void addBenchmarkedChartListener(Consumer<PositionedChart> listener) {
		
		benchmarkedChartListeners.add(listener);
		
	}
	
	/**
	 * Notifies all registered listeners about a new chart that will be benchmarked (or null if benchmarking is disabled.)
	 */
	private static void notifyBenchmarkedChartListeners() {
		
		for(Consumer<PositionedChart> listener : benchmarkedChartListeners)
			listener.accept(Settings.chartForBenchmarks);
		
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
		notifyBenchmarkedChartListeners();
		
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
