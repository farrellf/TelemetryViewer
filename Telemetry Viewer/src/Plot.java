import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL2;

public class Plot {
	
	private FloatBuffer[] buffers; // note: the x values in here are actually "x - plotMinX" to improve float32 precision when x is very large
	private Dataset[] datasets;
	private BitfieldEvents events = new BitfieldEvents();
	
	private int maxSampleNumber;
	private int minSampleNumber;
	private int plotSampleCount;
	private long plotMaxX;   // sample number or unix timestamp
	private long plotMinX;   // sample number or unix timestamp
	private long plotDomain; // sample count  or milliseconds
	private float maxY;
	private float minY;
	private String xAxisTitle = "";
	
	private boolean sampleCountMode;
	private boolean showHours;
	private boolean showMinutes;
	
	/**
	 * Configures this object to manage an x-axis that represents a sample count.
	 * 
	 * @param lastSampleNumber      The sample to render at the right edge of the plot.
	 * @param zoomLevel             Current zoom level. 1.0 = no zoom.
	 * @param datasets              Datasets to acquire from.
	 * @param sampleCountMinimum    Minimum allowed sample count.
	 * @param sampleCount           The sample count to acquire, before applying the zoom factor.
	 */
	public void acquireSamples(int lastSampleNumber, double zoomLevel, Dataset[] datasets, long sampleCount) {
		
		this.datasets = datasets;
		buffers = new FloatBuffer[datasets == null ? 0 : datasets.length];
		xAxisTitle = "Sample Number";
		sampleCountMode = true;
		
		// exit if there are no samples to acquire
		if(lastSampleNumber < 1) {
			maxSampleNumber = -1;
			minSampleNumber = -1;
			plotSampleCount = 0;
			plotMaxX = 0;
			plotMinX = plotMaxX - (int) (sampleCount * zoomLevel) + 1;
			plotDomain = plotMaxX - plotMinX;
			minY = -1;
			maxY = 1;
			return;
		}

		// determine which samples to acquire, and calculate the domain
		maxSampleNumber = lastSampleNumber;
		minSampleNumber = maxSampleNumber - (int) (sampleCount * zoomLevel) + 1;
		
		if(maxSampleNumber - minSampleNumber < 4)
			minSampleNumber = maxSampleNumber - 4;
		
		plotMaxX = maxSampleNumber;
		plotMinX = minSampleNumber;
		plotDomain = plotMaxX - plotMinX;
		
		if(minSampleNumber < 0)
			minSampleNumber = 0;
		
		plotSampleCount = maxSampleNumber - minSampleNumber + 1;
		
		// acquire the samples, and calculate the range
		minY = Float.MAX_VALUE;
		maxY = -Float.MAX_VALUE;
		
		for(int datasetN = 0; datasetN < buffers.length; datasetN++) {
			buffers[datasetN] = Buffers.newDirectFloatBuffer(2 * plotSampleCount);
			buffers[datasetN].rewind();
			
			float previousY = datasets[datasetN].getSample((minSampleNumber > 0) ? minSampleNumber - 1 : minSampleNumber);
			for(int sampleNumber = minSampleNumber; sampleNumber <= maxSampleNumber; sampleNumber++) {
				float y = datasets[datasetN].getSample(sampleNumber);
				buffers[datasetN].put(sampleNumber - plotMinX);
				buffers[datasetN].put(y);
				if(!datasets[datasetN].isBitfield) {
					if(y < minY) minY = y;
					if(y > maxY) maxY = y;
				} else {
					// check bitfields for changes
					if(y != previousY) {
						for(Bitfield bitfield: datasets[datasetN].bitfields) {
							int currentValue = bitfield.getValue((int) y);
							int previousValue = bitfield.getValue((int) previousY);
							if(currentValue != previousValue)
								events.add(sampleNumber, bitfield.names[currentValue], datasets[datasetN].glColor);
						}
					}
					previousY = y;
				}
			}
			buffers[datasetN].rewind();
		}
		
		if(minY == Float.MAX_VALUE && maxY == -Float.MAX_VALUE) {
			minY = -1;
			maxY = 1;
		}
		
		if(minY == maxY) {
			float value = minY;
			minY = value - 0.001f;
			maxY = value + 0.001f;
		}
		
	}
	
	/**
	 * Configures this object to manage an x-axis that represents an amount of time elapsed.
	 * 
	 * @param lastSampleNumber      The sample to render at the right edge of the plot.
	 * @param zoomLevel             Current zoom level. 1.0 = no zoom.
	 * @param datasets              Datasets to acquire from.
	 * @param milliseconds          The number of milliseconds to acquire.
	 */
	public void acquireMilliseconds(int lastSampleNumber, double zoomLevel, Dataset[] datasets, long milliseconds) {
		
		this.datasets = datasets;
		buffers = new FloatBuffer[datasets == null ? 0 : datasets.length];
		xAxisTitle = "Time Elapsed";
		sampleCountMode = false;
		plotDomain = (long) Math.ceil(milliseconds * zoomLevel);
		
		// exit if there are not enough samples to acquire
		if(lastSampleNumber < 1) {
			maxSampleNumber = -1;
			minSampleNumber = -1;
			plotSampleCount = 0;
			plotMaxX = 0;
			plotMinX = plotMaxX - plotDomain;
			if(plotMinX == 0) plotMinX = -1;
			minY = -1;
			maxY = 1;
			return;
		}

		// determine which samples to acquire
		maxSampleNumber = lastSampleNumber;
		minSampleNumber = maxSampleNumber - 1;
		long startTimestamp = DatasetsController.getTimestamp(maxSampleNumber) - plotDomain;
		for(int sampleN = maxSampleNumber - 1; sampleN >= 0; sampleN--) { // FIXME change this to a binary search
			minSampleNumber = sampleN;
			if(DatasetsController.getTimestamp(sampleN) < startTimestamp)
				break;
		}
		
		plotSampleCount = maxSampleNumber - minSampleNumber + 1;
		
		// calculate the domain
		plotMaxX = DatasetsController.getTimestamp(maxSampleNumber);
		plotMinX = plotMaxX - plotDomain;
		if(plotMinX == plotMaxX) {
			// ensure at least 1 millisecond is visible
			plotMinX = plotMaxX - 1;
			plotDomain = plotMaxX - plotMinX;
		}
		
		// acquire the samples and calculate the range
		minY = Float.MAX_VALUE;
		maxY = -Float.MAX_VALUE;
		
		for(int datasetN = 0; datasetN < buffers.length; datasetN++) {
			buffers[datasetN] = Buffers.newDirectFloatBuffer(2 * plotSampleCount);
			buffers[datasetN].rewind();
			
			float previousY = datasets[datasetN].getSample((minSampleNumber > 0) ? minSampleNumber - 1 : minSampleNumber);
			for(int sampleN = minSampleNumber; sampleN <= maxSampleNumber; sampleN++) {
				float x = (float) (DatasetsController.getTimestamp(sampleN) - plotMinX);
				float y = datasets[datasetN].getSample(sampleN);
				buffers[datasetN].put(x);
				buffers[datasetN].put(y);
				if(!datasets[datasetN].isBitfield) {
					if(y < minY) minY = y;
					if(y > maxY) maxY = y;
				} else {
					// check bitfields for changes
					if(y != previousY) {
						for(Bitfield bitfield: datasets[datasetN].bitfields) {
							int currentValue = bitfield.getValue((int) y);
							int previousValue = bitfield.getValue((int) previousY);
							if(currentValue != previousValue)
								events.add(sampleN, bitfield.names[currentValue], datasets[datasetN].glColor);
						}
					}
					previousY = y;
				}
			}
			buffers[datasetN].rewind();
		}
		
		if(minY == Float.MAX_VALUE && maxY == -Float.MAX_VALUE) {
			minY = -1;
			maxY = 1;
		}
		
		if(minY == maxY) {
			float value = minY;
			minY = value - 0.001f;
			maxY = value + 0.001f;
		}
		
		// determine the axis title
		long leftMillisecondsElapsed = plotMinX - DatasetsController.getFirstTimestamp();
		long hours = leftMillisecondsElapsed / 3600000; leftMillisecondsElapsed %= 3600000;
		long minutes = leftMillisecondsElapsed / 60000; leftMillisecondsElapsed %= 60000;
		if(hours != 0)
			showHours = true;
		if(minutes != 0)
			showMinutes = true;
		long rightMillisecondsElapsed = plotMaxX - DatasetsController.getFirstTimestamp();
		hours = rightMillisecondsElapsed / 3600000; rightMillisecondsElapsed %= 3600000;
		minutes = rightMillisecondsElapsed / 60000; rightMillisecondsElapsed %= 60000;
		if(hours != 0)
			showHours = true;
		if(minutes != 0)
			showMinutes = true;
		xAxisTitle = showHours ?   "Time Elapsed (HH:MM:SS.sss)" :
		             showMinutes ? "Time Elapsed (MM:SS.sss)" :
		                           "Time Elapsed (Seconds)";
		
	}
	
	public float getMinY() { return minY; }
	public float getMaxY() { return maxY; }
	public String getTitle() { return xAxisTitle; }
	public int getPlotSampleCount() { return plotSampleCount; }
	
	/**
	 * Renders the plot on screen.
	 * 
	 * @param gl             The OpenGL context.
	 * @param chartMatrix    The current 4x4 matrix.
	 * @param xPlotLeft      Bottom-left corner location, in pixels.
	 * @param yPlotBottom    Bottom-left corner location, in pixels.
	 * @param xPlotRight     Top-right corner location, in pixels.
	 * @param yPlotTop       Top-right corner location, in pixels.
	 * @param plotMinY       Y-axis value at the bottom of the plot.
	 * @param plotMaxY       Y-axis value at the top of the plot.
	 */
	public void draw(GL2 gl, float[] chartMatrix, float xPlotLeft, float yPlotBottom, float xPlotRight, float yPlotTop, float plotMinY, float plotMaxY) {
		
		float plotRange = plotMaxY - plotMinY;
		float plotWidth = xPlotRight - xPlotLeft;
		float plotHeight = yPlotTop - yPlotBottom;
		
		// clip to the plot region
		int[] originalScissorArgs = new int[4];
		gl.glGetIntegerv(GL2.GL_SCISSOR_BOX, originalScissorArgs, 0);
		gl.glScissor(originalScissorArgs[0] + (int) xPlotLeft, originalScissorArgs[1] + (int) yPlotBottom, (int) plotWidth, (int) plotHeight);
		
		float[] plotMatrix = Arrays.copyOf(chartMatrix, 16);
		// adjust so: x = (x - plotMinX) / domain * plotWidth + xPlotLeft;
		// adjust so: y = (y - plotMinY) / plotRange * plotHeight + yPlotBottom;
		// edit: now doing the "x - plotMinX" part before putting data into the buffers, to improve float32 precision when x is very large
		OpenGL.translateMatrix(plotMatrix, xPlotLeft,            yPlotBottom,          0);
		OpenGL.scaleMatrix    (plotMatrix, plotWidth/plotDomain, plotHeight/plotRange, 1);
		OpenGL.translateMatrix(plotMatrix, 0,                    -plotMinY,            0);
		OpenGL.useMatrix(gl, plotMatrix);
		
		// draw each dataset
		if(datasets.length > 0 && plotSampleCount >= 2) {
			for(int i = 0; i < datasets.length; i++) {
				
				// do not draw bitfields
				if(datasets[i].isBitfield)
					continue;
				
				OpenGL.drawLineStrip2D(gl, datasets[i].glColor, buffers[i], plotSampleCount);
				
				// also draw points if there are relatively few samples on screen
				boolean fewSamplesOnScreen;
				if(sampleCountMode) {
					fewSamplesOnScreen = (plotWidth / (float) plotDomain) > (2 * Theme.pointSize);
				} else {
					float occupiedPlotWidthPercentage = (float) (DatasetsController.getTimestamp(maxSampleNumber) - DatasetsController.getTimestamp(minSampleNumber)) / (float) plotDomain;
					float occupiedPlotWidth = plotWidth * occupiedPlotWidthPercentage;
					fewSamplesOnScreen = (occupiedPlotWidth / plotSampleCount) > (2 * Theme.pointSize);
				}
				if(fewSamplesOnScreen)
					OpenGL.drawPoints2D(gl, datasets[i].glColor, buffers[i], plotSampleCount);
				
			}
		}
		
		OpenGL.useMatrix(gl, chartMatrix);
		
		// draw any bitfield changes
		if(datasets.length > 0 && plotSampleCount >= 2) {
			List<BitfieldEvents.EventsAtSampleNumber> list = events.get();
			for(BitfieldEvents.EventsAtSampleNumber event : list)
				if(sampleCountMode)
					event.pixelX = (event.sampleNumber - plotMinX) / (float) plotDomain * plotWidth;
				else
					event.pixelX = (float) (DatasetsController.getTimestamp(event.sampleNumber) - plotMinX) / (float) plotDomain * plotWidth;
			ChartUtils.drawMarkers(gl, list, xPlotLeft, yPlotTop, xPlotRight, yPlotBottom);
		}

		// stop clipping to the plot region
		gl.glScissor(originalScissorArgs[0], originalScissorArgs[1], originalScissorArgs[2], originalScissorArgs[3]);
		
	}
	
	/**
	 * @param plotWidth    The width of the plot region, in pixels.
	 * @return             A Map where each value is a string to draw on screen, and each key is the pixelX location for it (0 = left edge of the plot)
	 */
	public Map<Float, String> getXdivisions(float plotWidth) {
		
		if(sampleCountMode) {
			
			Map<Integer, String> mapOfSampleNumbers = ChartUtils.getXdivisions125(plotWidth, (int) plotMinX, (int) plotMaxX);
			Map<Float, String> mapOfPixelLocations = new HashMap<Float, String>();
			
			for(Map.Entry<Integer, String> entry : mapOfSampleNumbers.entrySet()) {
				float pixel = (float) (entry.getKey() - plotMinX) / (float) plotDomain * plotWidth;
				String text = entry.getValue();
				mapOfPixelLocations.put(pixel, text);
			}
			
			return mapOfPixelLocations;
		
		} else {
			
			Map<Float, String> divisions = new HashMap<Float, String>();
			
			// sanity check
			if(plotWidth < 1)
				return divisions;
			
			// determine how many divisions can fit on screen
			long firstTimestamp = DatasetsController.getFirstTimestamp();
			long hours = 0;
			long minutes = 0;
			long seconds = 0;
			long milliseconds = 0;
			
			long leftMillisecondsElapsed = plotMinX - firstTimestamp;
			boolean negative = leftMillisecondsElapsed < 0;
			if(negative) leftMillisecondsElapsed *= -1;
			hours = leftMillisecondsElapsed / 3600000; leftMillisecondsElapsed %= 3600000;
			minutes = leftMillisecondsElapsed / 60000; leftMillisecondsElapsed %= 60000;
			seconds = leftMillisecondsElapsed / 1000;  leftMillisecondsElapsed %= 1000;
			milliseconds = leftMillisecondsElapsed;
			leftMillisecondsElapsed = plotMinX - firstTimestamp;
			String leftLabel = showHours ?   String.format("%s%02d:%02d:%02d.%03d", negative ? "-" : "", hours, minutes, seconds, milliseconds) :
			                   showMinutes ? String.format("%s%02d:%02d.%03d",      negative ? "-" : "",        minutes, seconds, milliseconds) :
			                                 String.format("%s%02d.%03d",           negative ? "-" : "",                 seconds, milliseconds);

			long rightMillisecondsElapsed = plotMaxX - firstTimestamp;
			hours = rightMillisecondsElapsed / 3600000; rightMillisecondsElapsed %= 3600000;
			minutes = rightMillisecondsElapsed / 60000; rightMillisecondsElapsed %= 60000;
			seconds = rightMillisecondsElapsed / 1000;  rightMillisecondsElapsed %= 1000;
			milliseconds = rightMillisecondsElapsed;
			rightMillisecondsElapsed = plotMaxX - firstTimestamp;
			String rightLabel = showHours ?   String.format("%02d:%02d:%02d.%03d", hours, minutes, seconds, milliseconds) :
			                    showMinutes ? String.format("%02d:%02d.%03d",             minutes, seconds, milliseconds) :
			                                  String.format("%02d.%03d",                           seconds, milliseconds);
			
			float maxLabelWidth = Float.max(FontUtils.tickTextWidth(leftLabel), FontUtils.tickTextWidth(rightLabel));
			float padding = maxLabelWidth / 2f;
			int divisionCount = (int) (plotWidth / (maxLabelWidth + padding));
			
			// determine where the divisions should occur
			long millisecondsOnScreen = plotMaxX - plotMinX;
			long millisecondsPerDivision = (long) Math.ceil((double) millisecondsOnScreen / (double) divisionCount);
			if(millisecondsPerDivision == 0)
				millisecondsPerDivision = 1;
			
			long firstDivisionMillisecondsElapsed = leftMillisecondsElapsed;
			if(millisecondsPerDivision < 1000) {
				// <1s per div, so use 1/2/5/10/20/50/100/200/250/500/1000ms per div, relative to the nearest second
				millisecondsPerDivision = (millisecondsPerDivision <= 1)   ? 1 :
				                          (millisecondsPerDivision <= 2)   ? 2 :
				                          (millisecondsPerDivision <= 5)   ? 5 :
				                          (millisecondsPerDivision <= 10)  ? 10 :
				                          (millisecondsPerDivision <= 20)  ? 20 :
				                          (millisecondsPerDivision <= 50)  ? 50 :
				                          (millisecondsPerDivision <= 100) ? 100 :
				                          (millisecondsPerDivision <= 200) ? 200 :
				                          (millisecondsPerDivision <= 250) ? 250 :
				                          (millisecondsPerDivision <= 500) ? 500 :
				                                                             1000;
				firstDivisionMillisecondsElapsed = (leftMillisecondsElapsed / 1000 * 1000) + (long) Math.ceil((double) (leftMillisecondsElapsed % 1000) / (double) millisecondsPerDivision) * millisecondsPerDivision;
			} else if(millisecondsPerDivision < 60000) {
				// <1m per div, so use 1/2/5/10/15/20/30/60s per div, relative to the nearest minute
				millisecondsPerDivision = (millisecondsPerDivision <= 1000)  ? 1000 :
				                          (millisecondsPerDivision <= 2000)  ? 2000 :
				                          (millisecondsPerDivision <= 5000)  ? 5000 :
				                          (millisecondsPerDivision <= 10000) ? 10000 :
				                          (millisecondsPerDivision <= 15000) ? 15000 :
				                          (millisecondsPerDivision <= 20000) ? 20000 :
				                          (millisecondsPerDivision <= 30000) ? 30000 :
				                                                               60000;
				firstDivisionMillisecondsElapsed = (leftMillisecondsElapsed / 60000 * 60000) + (long) Math.ceil((double) (leftMillisecondsElapsed % 60000) / (double) millisecondsPerDivision) * millisecondsPerDivision;
			} else if(millisecondsPerDivision < 3600000) {
				// <1h per div, so use 1/2/5/10/15/20/30/60m per div, relative to the nearest hour
				millisecondsPerDivision = (millisecondsPerDivision <= 60000)   ? 60000 :
				                          (millisecondsPerDivision <= 120000)  ? 120000 :
				                          (millisecondsPerDivision <= 300000)  ? 300000 :
				                          (millisecondsPerDivision <= 600000)  ? 600000 :
				                          (millisecondsPerDivision <= 900000)  ? 900000 :
				                          (millisecondsPerDivision <= 1200000) ? 1200000 :
				                          (millisecondsPerDivision <= 1800000) ? 1800000 :
				                                                                 3600000;
				firstDivisionMillisecondsElapsed = (leftMillisecondsElapsed / 3600000 * 3600000) + (long) Math.ceil((double) (leftMillisecondsElapsed % 3600000) / (double) millisecondsPerDivision) * millisecondsPerDivision;
			} else if(millisecondsPerDivision < 86400000) {
				// <1d per div, so use 1/2/3/4/6/8/12/24 hours per div, relative to the nearest day
				millisecondsPerDivision = (millisecondsPerDivision <= 3600000)  ? 3600000 :
				                          (millisecondsPerDivision <= 7200000)  ? 7200000 :
				                          (millisecondsPerDivision <= 10800000) ? 10800000 :
				                          (millisecondsPerDivision <= 14400000) ? 14400000 :
				                          (millisecondsPerDivision <= 21600000) ? 21600000 :
				                          (millisecondsPerDivision <= 28800000) ? 28800000 :
				                          (millisecondsPerDivision <= 43200000) ? 43200000 :
				                                                                  86400000;
				firstDivisionMillisecondsElapsed = (leftMillisecondsElapsed / 86400000 * 86400000) + (long) Math.ceil((double) (leftMillisecondsElapsed % 86400000) / (double) millisecondsPerDivision) * millisecondsPerDivision;
			} else {
				// >=1d per div, so use an integer number of days, relative to the nearest day
				if(millisecondsPerDivision != 86400000)
					millisecondsPerDivision += 86400000 - (millisecondsPerDivision % 86400000);
				firstDivisionMillisecondsElapsed = (leftMillisecondsElapsed / 86400000 * 86400000) + (long) Math.ceil((double) (leftMillisecondsElapsed % 86400000) / (double) millisecondsPerDivision) * millisecondsPerDivision;
			}
			
			// populate the Map
			for(int divisionN = 0; divisionN < divisionCount; divisionN++) {
				long millisecondsElapsed = firstDivisionMillisecondsElapsed + (divisionN * millisecondsPerDivision);
				negative = millisecondsElapsed < 0;
				float pixelX = (float) (millisecondsElapsed - leftMillisecondsElapsed) / (float) millisecondsOnScreen * plotWidth;
				if(negative) millisecondsElapsed *= -1;
				hours = millisecondsElapsed / 3600000; millisecondsElapsed %= 3600000;
				minutes = millisecondsElapsed / 60000; millisecondsElapsed %= 60000;
				seconds = millisecondsElapsed / 1000;  millisecondsElapsed %= 1000;
				milliseconds = millisecondsElapsed;
				String label = showHours ?   String.format("%s%02d:%02d:%02d.%03d", negative ? "-" : "", hours, minutes, seconds, milliseconds) :
				               showMinutes ? String.format("%s%02d:%02d.%03d",      negative ? "-" : "",        minutes, seconds, milliseconds) :
				                             String.format("%s%02d.%03d",           negative ? "-" : "",                 seconds, milliseconds);
				if(pixelX <= plotWidth)
					divisions.put(pixelX, label);
				else
					break;
			}
			
			return divisions;
			
		}
		
	}
	
	/**
	 * Checks if a tooltip should be drawn for the mouse's current location.
	 * 
	 * @param mouseX       The mouse's location along the x-axis, in pixels (0 = left edge of the plot)
	 * @param plotWidth    Width of the plot region, in pixels.
	 * @return             An object indicating if the tooltip should be drawn, for what sample number, with what label, and at what location on screen.
	 */
	public TooltipInfo getTooltip(int mouseX, float plotWidth) {
		
		if(plotSampleCount == 0) {
			
			return new TooltipInfo(false, 0, "", 0);
			
		} else if(sampleCountMode) {
			
			long sampleNumber = Math.round((float) mouseX / plotWidth * plotDomain) + plotMinX;
			if(sampleNumber < 0)
				return new TooltipInfo(false, 0, "", 0);
			
			if(sampleNumber > maxSampleNumber)
				sampleNumber = maxSampleNumber;
			
			String label = "Sample " + sampleNumber;
			float pixelX = (float) (sampleNumber - plotMinX) / (float) plotDomain * plotWidth;
			return new TooltipInfo(true, sampleNumber, label, pixelX);
			
		} else {
			
			long mouseTimestamp = (long) Math.round((mouseX / plotWidth) * plotDomain) + plotMinX;
			
			if(mouseTimestamp < DatasetsController.getFirstTimestamp())
				return new TooltipInfo(false, 0, "", 0);
			
			int closestSampleNumberBefore = maxSampleNumber;
			for(int sampleN = maxSampleNumber - 1; sampleN >= 0; sampleN--) { // FIXME change this to a binary search
				closestSampleNumberBefore = sampleN;
				if(DatasetsController.getTimestamp(sampleN) < mouseTimestamp)
					break;
			}
			int closestSampleNumberAfter = closestSampleNumberBefore + 1;
			if(closestSampleNumberAfter > maxSampleNumber)
				closestSampleNumberAfter = maxSampleNumber;

			double beforeError = (double) ((mouseX / plotWidth) * plotDomain) - (double) (DatasetsController.getTimestamp(closestSampleNumberBefore) - plotMinX);
			double afterError = (double) (DatasetsController.getTimestamp(closestSampleNumberAfter) - plotMinX) - (double) ((mouseX / plotWidth) * plotDomain);
			
			int closestSampleNumber = (beforeError < afterError) ? closestSampleNumberBefore : closestSampleNumberAfter;
			long millisecondsElapsed = DatasetsController.getTimestamp(closestSampleNumber) - DatasetsController.getFirstTimestamp();
			long hours = millisecondsElapsed / 3600000; millisecondsElapsed %= 3600000;
			long minutes = millisecondsElapsed / 60000; millisecondsElapsed %= 60000;
			long seconds = millisecondsElapsed / 1000;  millisecondsElapsed %= 1000;
			long milliseconds = millisecondsElapsed;
			
			String time = showHours ?   String.format("%02d:%02d:%02d.%03d", hours, minutes, seconds, milliseconds) :
			              showMinutes ? String.format("%02d:%02d.%03d",             minutes, seconds, milliseconds) :
			                            String.format("%02d.%03d",                           seconds, milliseconds);
			
			String label = "Sample " + closestSampleNumber + " (t = " + time + ")";
			float pixelX = (float) (DatasetsController.getTimestamp(closestSampleNumber) - plotMinX) / (float) plotDomain * plotWidth;
			
			return new TooltipInfo(true, closestSampleNumber, label, pixelX);
			
		}
		
	}
	
	public class TooltipInfo {
		
		public boolean draw;
		public int sampleNumber;
		public String label;
		public float pixelX;
		
		public TooltipInfo(boolean draw, long sampleNumber, String label, float pixelX) {
			this.draw = draw;
			this.sampleNumber = (int) sampleNumber;
			this.label = label;
			this.pixelX = pixelX;
		}
		
	}

}
