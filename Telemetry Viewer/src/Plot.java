import java.util.List;
import java.util.Map;

import com.jogamp.opengl.GL2ES3;

public abstract class Plot {
	
	List<Dataset> datasets;
	List<Dataset.Bitfield.State> bitfieldEdges;
	List<Dataset.Bitfield.State> bitfieldLevels;
	int maxSampleNumber;
	int minSampleNumber;
	int plotSampleCount;
	long plotMaxX;     // sample number or unix timestamp
	long plotMinX;     // sample number or unix timestamp
	long plotDomain;   // sample count  or milliseconds
	float samplesMinY; // of the samples, not necessarily of the plot
	float samplesMaxY; // of the samples, not necessarily of the plot
	String xAxisTitle = "";
	BitfieldEvents events;
	boolean cachedMode;
	
	/**
	 * Step 1: (Required) Calculate the domain and range of the plot.
	 * 
	 * @param lastSampleNumber    The sample to render at the right edge of the plot.
	 * @param zoomLevel           Current zoom level. 1.0 = no zoom.
	 * @param datasets            Normal datasets to acquire from.
	 * @param bitfieldEdges       Bitfield states to show edge events from.
	 * @param bitfieldLevels      Bitfield states to show levels from.
	 * @param duration            The sample count, before applying the zoom factor.
	 * @param cachedMode          True to enable the cache.
	 * @param showTimestamps      True if the x-axis shows timestamps, false if the x-axis shows sample count or elapsed time.
	 */
	abstract void initialize(int lastSampleNumber, double zoomLevel, List<Dataset> datasets, List<Dataset.Bitfield.State> bitfieldEdges, List<Dataset.Bitfield.State> bitfieldLevels, long duration, boolean cachedMode, boolean showTimestamps);
	
	/**
	 * Step 2: Get the required range, assuming you want to see all samples on screen.
	 * 
	 * @return    The minimum and maximum Y-axis values.
	 */
	final Dataset.MinMax getRange() { return new Dataset.MinMax(samplesMinY, samplesMaxY); }
	
	/**
	 * Step 3: Get the x-axis title.
	 * 
	 * @return    The x-axis title.
	 */
	final String getTitle() { return xAxisTitle; }
	
	/**
	 * Step 4: Get the x-axis divisions.
	 * 
	 * @param gl           The OpenGL context.
	 * @param plotWidth    The width of the plot region, in pixels.
	 * @return             A Map where each value is a string to draw on screen, and each key is the pixelX location for it (0 = left edge of the plot)
	 */
	abstract Map<Float, String> getXdivisions(GL2ES3 gl, float plotWidth);
	
	/**
	 * Step 5: Acquire the samples.
	 * If you will call draw(), you must call this before it.
	 * 
	 * @param plotMinY      Y-axis value at the bottom of the plot.
	 * @param plotMaxY      Y-axis value at the top of the plot.
	 * @param plotWidth     Width of the plot region, in pixels.
	 * @param plotHeight    Height of the plot region, in pixels.
	 */
	final void acquireSamples(float plotMinY, float plotMaxY, int plotWidth, int plotHeight) {
		
		if(plotSampleCount < 2)
			return;
		
		if(cachedMode)
			acquireSamplesCachedMode(plotMinY, plotMaxY, plotWidth, plotHeight);
		else
			acquireSamplesNonCachedMode(plotMinY, plotMaxY, plotWidth, plotHeight);
		
	}
	abstract void acquireSamplesCachedMode   (float plotMinY, float plotMaxY, int plotWidth, int plotHeight);
	abstract void acquireSamplesNonCachedMode(float plotMinY, float plotMaxY, int plotWidth, int plotHeight);
	
	/**
	 * Step 6: Render the plot on screen.
	 * 
	 * @param gl             The OpenGL context.
	 * @param chartMatrix    The current 4x4 matrix.
	 * @param xPlotLeft      Bottom-left corner location, in pixels.
	 * @param yPlotBottom    Bottom-left corner location, in pixels.
	 * @param plotWidth      Width of the plot region, in pixels.
	 * @param plotHeight     Height of the plot region, in pixels.
	 * @param plotMinY       Y-axis value at the bottom of the plot.
	 * @param plotMaxY       Y-axis value at the top of the plot.
	 */
	final void draw(GL2ES3 gl, float[] chartMatrix, int xPlotLeft, int yPlotBottom, int plotWidth, int plotHeight, float plotMinY, float plotMaxY) {
		
		if(plotSampleCount < 2)
			return;
		
		if(cachedMode)
			drawCachedMode(gl, chartMatrix, xPlotLeft, yPlotBottom, plotWidth, plotHeight, plotMinY, plotMaxY);
		else
			drawNonCachedMode(gl, chartMatrix, xPlotLeft, yPlotBottom, plotWidth, plotHeight, plotMinY, plotMaxY);
		
	}
	abstract void drawCachedMode   (GL2ES3 gl, float[] chartMatrix, int xPlotLeft, int yPlotBottom, int plotWidth, int plotHeight, float plotMinY, float plotMaxY);
	abstract void drawNonCachedMode(GL2ES3 gl, float[] chartMatrix, int xPlotLeft, int yPlotBottom, int plotWidth, int plotHeight, float plotMinY, float plotMaxY);
	
	/**
	 * Step 7: Check if a tooltip should be drawn for the mouse's current location.
	 * 
	 * @param mouseX       The mouse's location along the x-axis, in pixels (0 = left edge of the plot)
	 * @param plotWidth    Width of the plot region, in pixels.
	 * @return             An object indicating if the tooltip should be drawn, for what sample number, with what label, and at what location on screen.
	 */
	abstract TooltipInfo getTooltip(int mouseX, float plotWidth);
	
	final int getPlotSampleCount() { return plotSampleCount; }
	
	static class TooltipInfo {
		
		boolean draw;
		int sampleNumber;
		String label;
		float pixelX;
		
		TooltipInfo(boolean draw, long sampleNumber, String label, float pixelX) {
			this.draw = draw;
			this.sampleNumber = (int) sampleNumber;
			this.label = label;
			this.pixelX = pixelX;
		}
		
	}
	
	abstract public void freeResources(GL2ES3 gl);

}
