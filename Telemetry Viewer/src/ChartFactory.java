import javax.swing.JPanel;

/**
 * All charts need to define a static getFactory() method that returns a ChartFactory object.
 * These are used by the AddChartWindow to display chart configuration widgets to the user.
 */
public interface ChartFactory {
	
	/**
	 * @return    Descriptive name of the chart.
	 */
	public String toString();
	
	/**
	 * @return    The widgets that allow the user to configure a chart. Elements of this array can be null to indicate that a gap should be shown there.
	 */
	public JPanel[] getWidgets();
	
	/**
	 * @return    Minimum number of samples allowed.
	 */
	public int getMinimumSampleCount();
	
	/**
	 * Creates the chart based on the state of the configuration widgets from getWidgets().
	 * 
	 * @param x1    The x-coordinate of a bounding-box corner in the OpenGLChartsRegion grid.
	 * @param y1    The y-coordinate of a bounding-box corner in the OpenGLChartsRegion grid.
	 * @param x2    The x-coordinate of the opposite bounding-box corner in the OpenGLChartsRegion grid.
	 * @param y2    The x-coordinate of the opposite bounding-box corner in the OpenGLChartsRegion grid.
	 * @return      The new chart.
	 */
	public PositionedChart createChart(int x1, int y1, int x2, int y2);
	
	/**
	 * Creates a chart based on the relevant lines of text from a layout file.
	 * To reduce code duplication, Controller.openLayout() will have already parsed the settings common to all charts: grid location, datasets and sample count.
	 * 
	 * @param x1                 The x-coordinate of a bounding-box corner in the OpenGLChartsRegion grid.
	 * @param y1                 The y-coordinate of a bounding-box corner in the OpenGLChartsRegion grid.
	 * @param x2                 The x-coordinate of the opposite bounding-box corner in the OpenGLChartsRegion grid.
	 * @param y2                 The x-coordinate of the opposite bounding-box corner in the OpenGLChartsRegion grid.
	 * @param datasets           The datasets to visualize.
	 * @param sampleCount        Number of samples to visualize.
	 * @param lines              Any other lines of unparsed relevant text from the layout file.
	 * @param firstLineNumber    The line number for lines[0], provided so helpful error messages can be generated.
	 * @return                   The new chart.
	 */
	public PositionedChart importChart(int x1, int y1, int x2, int y2, Dataset[] datasets, int sampleCount, String[] lines, int firstLineNumber);

}
