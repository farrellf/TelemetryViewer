/**
 * Objects that want to be notified when a chart is added or removed must implement this interface.
 */
public interface ChartListener {

	/**
	 * Called when a new chart has been created.
	 * 
	 * @param chart    The new chart.
	 */
	public void chartAdded(PositionedChart chart);
	
	/**
	 * Called when a chart is being removed.
	 * 
	 * @param chart    The chart being removed.
	 */
	public void chartRemoved(PositionedChart chart);
	
}
