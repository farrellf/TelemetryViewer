import java.nio.FloatBuffer;
import java.security.InvalidParameterException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.jogamp.common.nio.Buffers;

public class SamplesManager {
	
	// publicly-exposed state
	public FloatBuffer[] buffers;
	public int maxSampleNumber;
	public int minSampleNumber;
	public int plotSampleCount;
	public float maxY;
	public float minY;
	public String xAxisTitle = "";
	public BitfieldEvents events = new BitfieldEvents();
	
	// state for time-elapsed modes
	private float floatPlotMaxX;
	private float floatPlotMinX;
	private float floatPlotDomain;
	private String abbreviatedUnit;
	private float millisecondsPerDuration;
	
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
				buffers[datasetN].put(sampleNumber);
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
	 * @param sampleCountMinimum    Minimum allowed sample count.
	 * @param duration              The number of seconds/minutes/hours/days to acquire.
	 * @param unit                  "Seconds" or "Minutes" or "Hours" or "Days"
	 */
	public void acquireNtimeUnits(int lastSampleNumber, float zoomLevel, Dataset[] datasets, int sampleCountMinimum, float duration, String unit) {
		
		if(unit.equals("Seconds")) {
			millisecondsPerDuration = 1000;
			abbreviatedUnit = "s";
		} else if(unit.equals("Minutes")) {
			millisecondsPerDuration = 60000;
			abbreviatedUnit = "m";
		} else if(unit.equals("Hours")) {
			millisecondsPerDuration = 3600000;
			abbreviatedUnit = "h";
		} else if(unit.equals("Days")) {
			millisecondsPerDuration = 86400000;
			abbreviatedUnit = "d";
		} else {
			throw new InvalidParameterException("Invalid unit.");
		}
		
		buffers = new FloatBuffer[datasets == null ? 0 : datasets.length];
		
		// exit if there are no samples to acquire
		if(lastSampleNumber < 1) {
			maxSampleNumber = -1;
			minSampleNumber = -1;
			plotSampleCount = 0;
			floatPlotMaxX = 0;
			floatPlotMinX = floatPlotMaxX - (duration * zoomLevel);
			floatPlotDomain = floatPlotMaxX - floatPlotMinX;
			minY = -1;
			maxY = 1;
			xAxisTitle = unit;
			sampleNumberMode = false;
			return;
		}

		// determine which samples to acquire
		maxSampleNumber = lastSampleNumber;
		minSampleNumber = maxSampleNumber;
		long startTimestamp = DatasetsController.getTimestamp(maxSampleNumber) - (long) (duration * millisecondsPerDuration * zoomLevel);
		for(int i = maxSampleNumber - 1; i >= 0; i--) { // FIXME change this to a binary search
			minSampleNumber = i;
			if(DatasetsController.getTimestamp(minSampleNumber) < startTimestamp)
				break;
		}
		
		if(maxSampleNumber - minSampleNumber < sampleCountMinimum - 1)
			minSampleNumber = maxSampleNumber - sampleCountMinimum + 1;
		
		if(minSampleNumber < 0)
			minSampleNumber = 0;
		
		plotSampleCount = maxSampleNumber - minSampleNumber + 1;
		
		// calculate the domain
		long firstTimestamp = DatasetsController.getTimestamp(0);
		floatPlotMaxX = (DatasetsController.getTimestamp(maxSampleNumber) - firstTimestamp) / millisecondsPerDuration;
		floatPlotMinX = floatPlotMaxX - (duration * zoomLevel);
		if((DatasetsController.getTimestamp(minSampleNumber + 1) - firstTimestamp) / millisecondsPerDuration > floatPlotMinX)
			floatPlotMinX = (DatasetsController.getTimestamp(minSampleNumber) - firstTimestamp) / millisecondsPerDuration; // prevent zooming in too far
		floatPlotDomain = floatPlotMaxX - floatPlotMinX;
		
		// acquire the samples and calculate the range
		minY = Float.MAX_VALUE;
		maxY = -Float.MAX_VALUE;
		
		for(int datasetN = 0; datasetN < buffers.length; datasetN++) {
			buffers[datasetN] = Buffers.newDirectFloatBuffer(2 * plotSampleCount);
			buffers[datasetN].rewind();
			
			float previousY = datasets[datasetN].getSample((minSampleNumber > 0) ? minSampleNumber - 1 : minSampleNumber);
			for(int sampleNumber = minSampleNumber; sampleNumber <= maxSampleNumber; sampleNumber++) {
				float y = datasets[datasetN].getSample(sampleNumber);
				buffers[datasetN].put((DatasetsController.getTimestamp(sampleNumber) - firstTimestamp) / millisecondsPerDuration);
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
		
		// indicate time-elapsed mode
		xAxisTitle = unit;
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
			return ((DatasetsController.getTimestamp(sampleNumber) - DatasetsController.getTimestamp(0)) / millisecondsPerDuration - floatPlotMinX) / floatPlotDomain * plotWidth;
		
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
			
			Map<Float, String> mapOfXvalues = ChartUtils.getFloatXdivisions125(plotWidth, floatPlotMinX, floatPlotMaxX);
			Map<Float, String> mapOfPixelLocations = new HashMap<Float, String>();
			
			for(Map.Entry<Float, String> entry : mapOfXvalues.entrySet()) {
				float pixel = (entry.getKey() - floatPlotMinX) / floatPlotDomain * plotWidth;
				String text = entry.getValue();
				mapOfPixelLocations.put(pixel, text);
			}
			
			return mapOfPixelLocations;
			
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
			
			float occupiedPlotWidthPercentage = (DatasetsController.getTimestamp(maxSampleNumber) - DatasetsController.getTimestamp(minSampleNumber)) / millisecondsPerDuration / floatPlotDomain;
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
			
			int sampleNumber = Math.round((float) mouseX / plotWidth * intPlotDomain + intPlotMinX);
			if(sampleNumber < 0)
				return new TooltipInfo(false, 0, "", 0);
			
			if(sampleNumber > maxSampleNumber)
				sampleNumber = maxSampleNumber;
			
			String label = "Sample " + sampleNumber;
			float pixelX = (float) (sampleNumber - intPlotMinX) / (float) intPlotDomain * plotWidth;
			return new TooltipInfo(true, sampleNumber, label, pixelX);
			
		} else {
			
			float xValue = (mouseX / plotWidth) * floatPlotDomain + floatPlotMinX;
			
			if(xValue < 0)
				return new TooltipInfo(false, 0, "", 0);
			
			int closestSampleNumberBeforeXvalue = maxSampleNumber;
			for(int i = maxSampleNumber - 1; i >= 0; i--) { // FIXME change this to a binary search
				closestSampleNumberBeforeXvalue = i;
				if(((DatasetsController.getTimestamp(i) - DatasetsController.getTimestamp(0)) / millisecondsPerDuration) < xValue)
					break;
			}
			int closestSampleNumberAfterXvalue = closestSampleNumberBeforeXvalue + 1;
			if(closestSampleNumberAfterXvalue > maxSampleNumber)
				closestSampleNumberAfterXvalue = maxSampleNumber;
			
			float beforeError = xValue - (DatasetsController.getTimestamp(closestSampleNumberBeforeXvalue) - DatasetsController.getTimestamp(0)) / millisecondsPerDuration;
			float afterError = (DatasetsController.getTimestamp(closestSampleNumberAfterXvalue) - DatasetsController.getTimestamp(0)) / millisecondsPerDuration - xValue;
			
			int sampleNumber = (beforeError < afterError) ? closestSampleNumberBeforeXvalue : closestSampleNumberAfterXvalue;
			String label = "Sample " + sampleNumber + " (t = " + String.format("%.3f",((DatasetsController.getTimestamp(sampleNumber) - DatasetsController.getTimestamp(0)) / millisecondsPerDuration)) + abbreviatedUnit + ")";
			float x = (DatasetsController.getTimestamp(sampleNumber) - DatasetsController.getTimestamp(0)) / millisecondsPerDuration;
			float pixelX = (x - floatPlotMinX) / floatPlotDomain * plotWidth;
			
			return new TooltipInfo(true, sampleNumber, label, pixelX);
			
		}
		
	}
	
	public float getPlotMaxX()   { return sampleNumberMode ? intPlotMaxX   : floatPlotMaxX; }
	public float getPlotMinX()   { return sampleNumberMode ? intPlotMinX   : floatPlotMinX; }
	public float getPlotDomain() { return sampleNumberMode ? intPlotDomain : floatPlotDomain; }
	
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
