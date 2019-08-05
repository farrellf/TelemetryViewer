import java.nio.FloatBuffer;
import java.security.InvalidParameterException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.jogamp.common.nio.Buffers;

public class SamplesManager {
	
	// publicly-exposed state
	public FloatBuffer[] buffers; // note: the x values in here are actually "x - plotMinX" to improve float32 precision when x is very large
	public int maxSampleNumber;
	public int minSampleNumber;
	public int plotSampleCount;
	public float maxY;
	public float minY;
	public String xAxisTitle = "";
	public BitfieldEvents events = new BitfieldEvents();
	
	// state for time-elapsed modes
	private long plotMaxXtimestamp;
	private long plotMinXtimestamp;
	private long plotDomainMilliseconds;
	private long millisecondsPerDuration;
	private boolean showHours;
	private boolean showMinutes;
	
	// state for sample count mode
	private int intPlotMaxX;
	private int intPlotMinX;
	private int intPlotDomain;
	private boolean sampleNumberMode;
	
	/**
	 * Configures this object to manage an x-axis that represents a sample count.
	 * 
	 * @param lastSampleNumber      The sample to render at the right edge of the plot.
	 * @param zoomLevel             Current zoom level. 1.0 = no zoom.
	 * @param datasets              Datasets to acquire from.
	 * @param sampleCountMinimum    Minimum allowed sample count.
	 * @param sampleCount           The sample count to acquire, before applying the zoom factor.
	 */
	public void acquireNsamples(int lastSampleNumber, float zoomLevel, Dataset[] datasets, int sampleCountMinimum, int sampleCount) {
		
		buffers = new FloatBuffer[datasets == null ? 0 : datasets.length];
		
		// exit if there are no samples to acquire
		if(lastSampleNumber < 1) {
			maxSampleNumber = -1;
			minSampleNumber = -1;
			plotSampleCount = 0;
			intPlotMaxX = 0;
			intPlotMinX = intPlotMaxX - (int) (sampleCount * zoomLevel) + 1;
			intPlotDomain = intPlotMaxX - intPlotMinX;
			minY = -1;
			maxY = 1;
			xAxisTitle = "Sample Number";
			sampleNumberMode = true;
			return;
		}

		// determine which samples to acquire, and calculate the domain
		maxSampleNumber = lastSampleNumber;
		minSampleNumber = maxSampleNumber - (int) (sampleCount * zoomLevel) + 1;
		
		if(maxSampleNumber - minSampleNumber < sampleCountMinimum - 1)
			minSampleNumber = maxSampleNumber - sampleCountMinimum + 1;
		
		intPlotMaxX = maxSampleNumber;
		intPlotMinX = minSampleNumber;
		intPlotDomain = intPlotMaxX - intPlotMinX;
		
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
				buffers[datasetN].put(sampleNumber - intPlotMinX);
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
								events.add(sampleNumber, bitfield.names[currentValue], datasets[datasetN].color);
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
		
		// indicate sample number mode
		xAxisTitle = "Sample Number";
		sampleNumberMode = true;
		
	}
	
	/**
	 * Configures this object to manage an x-axis that represents an amount of time elapsed.
	 * 
	 * @param lastSampleNumber      The sample to render at the right edge of the plot.
	 * @param zoomLevel             Current zoom level. 1.0 = no zoom.
	 * @param datasets              Datasets to acquire from.
	 * @param duration              The number of seconds/minutes/hours/days to acquire.
	 * @param unit                  "Seconds" or "Minutes" or "Hours" or "Days"
	 */
	public void acquireNtimeUnits(int lastSampleNumber, double zoomLevel, Dataset[] datasets, double duration, String unit) {
		
		if(unit.equals("Seconds"))
			millisecondsPerDuration = 1000;
		else if(unit.equals("Minutes"))
			millisecondsPerDuration = 60000;
		else if(unit.equals("Hours"))
			millisecondsPerDuration = 3600000;
		else if(unit.equals("Days"))
			millisecondsPerDuration = 86400000;
		else
			throw new InvalidParameterException("Invalid unit.");
		
		plotDomainMilliseconds = (long) Math.ceil(duration * zoomLevel * millisecondsPerDuration);
		
		buffers = new FloatBuffer[datasets == null ? 0 : datasets.length];
		
		// exit if there are not enough samples to acquire
		if(lastSampleNumber < 1) {
			maxSampleNumber = -1;
			minSampleNumber = -1;
			plotSampleCount = 0;
			plotMaxXtimestamp = 0;
			plotMinXtimestamp = plotMaxXtimestamp - plotDomainMilliseconds;
			if(plotMinXtimestamp == 0) plotMinXtimestamp = -1;
			minY = -1;
			maxY = 1;
			xAxisTitle = unit;
			sampleNumberMode = false;
			return;
		}

		// determine which samples to acquire
		maxSampleNumber = lastSampleNumber;
		minSampleNumber = maxSampleNumber - 1;
		long startTimestamp = DatasetsController.getTimestamp(maxSampleNumber) - plotDomainMilliseconds;
		for(int sampleN = maxSampleNumber - 1; sampleN >= 0; sampleN--) { // FIXME change this to a binary search
			minSampleNumber = sampleN;
			if(DatasetsController.getTimestamp(sampleN) < startTimestamp)
				break;
		}
		
		plotSampleCount = maxSampleNumber - minSampleNumber + 1;
		
		// calculate the domain
		plotMaxXtimestamp = DatasetsController.getTimestamp(maxSampleNumber);
		plotMinXtimestamp = plotMaxXtimestamp - plotDomainMilliseconds;
		if(plotMinXtimestamp == plotMaxXtimestamp) {
			// ensure at least 1 millisecond is visible
			plotMinXtimestamp = plotMaxXtimestamp - 1;
			plotDomainMilliseconds = plotMaxXtimestamp - plotMinXtimestamp;
		}
		
		// acquire the samples and calculate the range
		minY = Float.MAX_VALUE;
		maxY = -Float.MAX_VALUE;
		
		for(int datasetN = 0; datasetN < buffers.length; datasetN++) {
			buffers[datasetN] = Buffers.newDirectFloatBuffer(2 * plotSampleCount);
			buffers[datasetN].rewind();
			
			float previousY = datasets[datasetN].getSample((minSampleNumber > 0) ? minSampleNumber - 1 : minSampleNumber);
			for(int sampleN = minSampleNumber; sampleN <= maxSampleNumber; sampleN++) {
				float x = (float) (DatasetsController.getTimestamp(sampleN) - plotMinXtimestamp) / (float) millisecondsPerDuration;
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
								events.add(sampleN, bitfield.names[currentValue], datasets[datasetN].color);
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
		long leftMillisecondsElapsed = plotMinXtimestamp - DatasetsController.getTimestamp(0);
		long hours = leftMillisecondsElapsed / 3600000; leftMillisecondsElapsed %= 3600000;
		long minutes = leftMillisecondsElapsed / 60000; leftMillisecondsElapsed %= 60000;
		if(hours != 0)
			showHours = true;
		if(minutes != 0)
			showMinutes = true;
		long rightMillisecondsElapsed = plotMaxXtimestamp - DatasetsController.getTimestamp(0);
		hours = rightMillisecondsElapsed / 3600000; rightMillisecondsElapsed %= 3600000;
		minutes = rightMillisecondsElapsed / 60000; rightMillisecondsElapsed %= 60000;
		if(hours != 0)
			showHours = true;
		if(minutes != 0)
			showMinutes = true;
		xAxisTitle = showHours ?   "Time Elapsed (HH:MM:SS.sss)" :
		             showMinutes ? "Time Elapsed (MM:SS.sss)" :
		                           "Time Elapsed (Seconds)";
		
		// indicate time-elapsed mode
		sampleNumberMode = false;
		
	}
	
	public List<BitfieldEvents.EventsAtSampleNumber> getBitfieldEvents(float plotWidth) {
		
		List<BitfieldEvents.EventsAtSampleNumber> list = events.get();
		for(BitfieldEvents.EventsAtSampleNumber event : list)
			event.pixelX = convertSampleNumberToPixelX(event.sampleNumber, plotWidth);
		return list;
		
	}
	
	private float convertSampleNumberToPixelX(int sampleNumber, float plotWidth) {
		
		if(sampleNumberMode)
			return (sampleNumber - intPlotMinX) / (float) intPlotDomain * plotWidth;
		else
			return (float) (DatasetsController.getTimestamp(sampleNumber) - plotMinXtimestamp) / (float) plotDomainMilliseconds * plotWidth;
		
	}
	
	/**
	 * @param plotWidth    The width of the plot region, in pixels.
	 * @return             A Map where each value is a string to draw on screen, and each key is the pixelX location for it (0 = left edge of the plot)
	 */
	public Map<Float, String> getXdivisions(float plotWidth) {
		
		if(sampleNumberMode) {
			
			Map<Integer, String> mapOfSampleNumbers = ChartUtils.getXdivisions125(plotWidth, intPlotMinX, intPlotMaxX);
			Map<Float, String> mapOfPixelLocations = new HashMap<Float, String>();
			
			for(Map.Entry<Integer, String> entry : mapOfSampleNumbers.entrySet()) {
				float pixel = (float) (entry.getKey() - intPlotMinX) / (float) intPlotDomain * plotWidth;
				String text = entry.getValue();
				mapOfPixelLocations.put(pixel, text);
			}
			
			return mapOfPixelLocations;
			
		} else {
			
			return getTimeAxisDivisions(plotWidth, plotMinXtimestamp, plotMaxXtimestamp);
			
		}
		
	}
	
	/**
	 * @param plotWidth    Width of the plot region, in pixels.
	 * @return             True if there are few samples being drawn, so points should be drawn at the vertex locations.
	 */
	public boolean fewSamplesOnScreen(float plotWidth) {
		
		if(plotSampleCount == 0) {
			
			return true;
			
		} else if(sampleNumberMode) {
			
			return (plotWidth / (float) intPlotDomain) > (2 * Theme.pointSize);
			
		} else {
			
			float occupiedPlotWidthPercentage = (float) (DatasetsController.getTimestamp(maxSampleNumber) - DatasetsController.getTimestamp(minSampleNumber)) / (float) plotDomainMilliseconds;
			float occupiedPlotWidth = plotWidth * occupiedPlotWidthPercentage;
			return (occupiedPlotWidth / plotSampleCount) > (2 * Theme.pointSize);
			
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
			
		} else if(sampleNumberMode) {
			
			int sampleNumber = Math.round((float) mouseX / plotWidth * intPlotDomain) + intPlotMinX;
			if(sampleNumber < 0)
				return new TooltipInfo(false, 0, "", 0);
			
			if(sampleNumber > maxSampleNumber)
				sampleNumber = maxSampleNumber;
			
			String label = "Sample " + sampleNumber;
			float pixelX = (float) (sampleNumber - intPlotMinX) / (float) intPlotDomain * plotWidth;
			return new TooltipInfo(true, sampleNumber, label, pixelX);
			
		} else {
			
			long mouseTimestamp = (long) Math.round((mouseX / plotWidth) * plotDomainMilliseconds) + plotMinXtimestamp;
			
			if(mouseTimestamp < DatasetsController.getTimestamp(0))
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
			
			long beforeError = mouseTimestamp - DatasetsController.getTimestamp(closestSampleNumberBefore);
			long afterError = DatasetsController.getTimestamp(closestSampleNumberAfter) - mouseTimestamp;
			
			int closestSampleNumber = (beforeError < afterError) ? closestSampleNumberBefore : closestSampleNumberAfter;
			long millisecondsElapsed = DatasetsController.getTimestamp(closestSampleNumber) - DatasetsController.getTimestamp(0);
			long hours = millisecondsElapsed / 3600000; millisecondsElapsed %= 3600000;
			long minutes = millisecondsElapsed / 60000; millisecondsElapsed %= 60000;
			long seconds = millisecondsElapsed / 1000;  millisecondsElapsed %= 1000;
			long milliseconds = millisecondsElapsed;
			
			String time = showHours ?   String.format("%02d:%02d:%02d.%03d", hours, minutes, seconds, milliseconds) :
			              showMinutes ? String.format("%02d:%02d.%03d",             minutes, seconds, milliseconds) :
			                            String.format("%02d.%03d",                           seconds, milliseconds);
			
			String label = "Sample " + closestSampleNumber + " (t = " + time + ")";
			float pixelX = convertSampleNumberToPixelX(closestSampleNumber, plotWidth);
			
			return new TooltipInfo(true, closestSampleNumber, label, pixelX);
			
		}
		
	}
	
	/**
	 * Determines the best values to use for time divisions. The 1/2/5 pattern is used (.1,.2,.5,1,2,5,10,20,50...)
	 * 
	 * @param plotWidth           Number of pixels for the x-axis.
	 * @param minimumTimestamp    Timestamp value at the left of the plot.
	 * @param maximumTimestamp    Timestamp value at the right of the plot.
	 * @return                    A Map of the divisions:
	 *                                Keys are Float pixelX locations relative to the left edge of the plot.
	 *                                Values are user-friendly Strings to draw on screen.
	 */
	public Map<Float, String> getTimeAxisDivisions(float plotWidth, long minimumTimestamp, long maximumTimestamp) {
		
		Map<Float, String> divisions = new HashMap<Float, String>();
		
		// sanity check
		if(plotWidth < 1)
			return divisions;
		
		// determine how many divisions can fit on screen
		long firstTimestamp = (minSampleNumber < 0) ? 0 : DatasetsController.getTimestamp(0);
		long hours = 0;
		long minutes = 0;
		long seconds = 0;
		long milliseconds = 0;
		
		long leftMillisecondsElapsed = minimumTimestamp - firstTimestamp;
		boolean negative = leftMillisecondsElapsed < 0;
		if(negative) leftMillisecondsElapsed *= -1;
		hours = leftMillisecondsElapsed / 3600000; leftMillisecondsElapsed %= 3600000;
		minutes = leftMillisecondsElapsed / 60000; leftMillisecondsElapsed %= 60000;
		seconds = leftMillisecondsElapsed / 1000;  leftMillisecondsElapsed %= 1000;
		milliseconds = leftMillisecondsElapsed;
		leftMillisecondsElapsed = minimumTimestamp - firstTimestamp;
		String leftLabel = showHours ?   String.format("%s%02d:%02d:%02d.%03d", negative ? "-" : "", hours, minutes, seconds, milliseconds) :
		                   showMinutes ? String.format("%s%02d:%02d.%03d",      negative ? "-" : "",        minutes, seconds, milliseconds) :
		                                 String.format("%s%02d.%03d",           negative ? "-" : "",                 seconds, milliseconds);

		long rightMillisecondsElapsed = maximumTimestamp - firstTimestamp;
		hours = rightMillisecondsElapsed / 3600000; rightMillisecondsElapsed %= 3600000;
		minutes = rightMillisecondsElapsed / 60000; rightMillisecondsElapsed %= 60000;
		seconds = rightMillisecondsElapsed / 1000;  rightMillisecondsElapsed %= 1000;
		milliseconds = rightMillisecondsElapsed;
		rightMillisecondsElapsed = maximumTimestamp - firstTimestamp;
		String rightLabel = showHours ?   String.format("%02d:%02d:%02d.%03d", hours, minutes, seconds, milliseconds) :
		                    showMinutes ? String.format("%02d:%02d.%03d",             minutes, seconds, milliseconds) :
		                                  String.format("%02d.%03d",                           seconds, milliseconds);
		
		float maxLabelWidth = Float.max(FontUtils.tickTextWidth(leftLabel), FontUtils.tickTextWidth(rightLabel));
		float padding = maxLabelWidth / 2f;
		int divisionCount = (int) (plotWidth / (maxLabelWidth + padding));
		
		// determine where the divisions should occur
		long millisecondsOnScreen = maximumTimestamp - minimumTimestamp;
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
	
	public float getPlotDomain() { return sampleNumberMode ? intPlotDomain : (float) plotDomainMilliseconds / (float) millisecondsPerDuration; }
	
	public class TooltipInfo {
		
		public boolean draw;
		public int sampleNumber;
		public String label;
		public float pixelX;
		
		public TooltipInfo(boolean draw, int sampleNumber, String label, float pixelX) {
			this.draw = draw;
			this.sampleNumber = sampleNumber;
			this.label = label;
			this.pixelX = pixelX;
		}
		
	}

}
