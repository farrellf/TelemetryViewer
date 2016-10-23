/**
 * All charts should have a static getDescriptor() method that returns an object that implements this interface.
 * The NewChartWindow uses this information to create new charts and to display appropriate options for the user.
 */
public interface ChartDescriptor {

	/**
	 * @return   The chart's name, such as "Line Chart" etc.
	 */
	public String toString();
	
	/**
	 * @return    A String[] of names for each possible input, or null if 1+ inputs are allowed.
	 */
	public String[] getInputNames();
	
	/**
	 * @return    The minimum number of samples allowed.
	 */
	public int getMinimumDuration();
	
	/**
	 * @return    The maximum number of samples allowed.
	 */
	public int getMaximumDuration();
	
	/** 
	 * @return    The default number of samples.
	 */
	public int getDefaultDuration();
	
	/**
	 * Creates a new chart.
	 * 
	 * @param x1               The x-coordinate of a bounding-box corner in the ChartsRegion. This is a grid location, not a pixel.
	 * @param y1               The y-coordinate of a bounding-box corner in the ChartsRegion. This is a grid location, not a pixel.
	 * @param x2               The x-coordinate of the opposite bounding-box corner in the ChartsRegion. This is a grid location, not a pixel.
	 * @param y2               The y-coordinate of the opposite bounding-box corner in the ChartsRegion. This is a grid location, not a pixel.
	 * @param chartDuration    How many samples make up the domain.
	 * @param chartInputs      The Datasets to be visualized. 
	 * @return                 The new chart.
	 */
	public PositionedChart createChart(int x1, int y1, int x2, int y2, int chartDuration, Dataset[] chartInputs);
	
}
