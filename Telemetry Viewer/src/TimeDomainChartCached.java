import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.image.VolatileImage;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.SwingUtilities;

/**
 * This class generates a time-domain line chart and caches the old data (as pixels) to speed up rendering.
 * Some GPU-acceleration is also used by means of the VolatileImage class.
 * 
 * Since a cache is used, this class will not be very fast if the cache frequently needs to be flushed and regenerated.
 * This can happen if:
 *     - The chart scrolls by extremely fast
 *     - The range (y-axis) changes frequently
 *     - The chart image size changes frequently
 *     - The domain (x-axis) division size changes
 *
 * Implementation Notes:
 *     - The cache is in the form of a VolatileImage[] which is used as a ring buffer. 
 *     - Each image is called a slice. The index of each slice is calculated assuming an infinite cache,
 *       and that index modulo the array length obtains the actual index for the VolatileImage[] ring buffer.
 */
@SuppressWarnings("serial")
public class TimeDomainChartCached extends PositionedChart {
	
	VolatileImage image1;
	VolatileImage image2;
	AtomicBoolean paintImage1;
	final boolean drawPartialLastSlice = true;
	
	public static ChartDescriptor getDescriptor() {
		
		return new ChartDescriptor() {
			
			@Override public String toString()        { return "Time Domain Chart (Cached)"; }
			@Override public int getMinimumDuration() { return 10; }
			@Override public int getDefaultDuration() { return 1000; }
			@Override public int getMaximumDuration() { return Integer.MAX_VALUE; }
			@Override public String[] getInputNames() { return null; }
			
			@Override public PositionedChart createChart(int x1, int y1, int x2, int y2, int chartDuration, Dataset[] chartInputs) {
				return new TimeDomainChartCached(x1, y1, x2, y2, chartDuration, chartInputs);
			}
			
		};
		
	}
	
	@Override public String toString() {
		
		return "Time Domain Chart (Cached)";
		
	}

	/**
	 * Constructor, creates a new CachedTimeDomainChart.
	 * 
	 * @param chartDuration           How many samples make up the domain (x-axis.)
	 * @param displayScalingFactor    Determines how big the fonts and line strokes are. 1 = 100%, 2 = 200%, etc.
	 */
	public TimeDomainChartCached(int x1, int y1, int x2, int y2, int chartDuration, Dataset[] chartDatasets) {
		
		super(x1, y1, x2, y2, chartDuration, chartDatasets);
		
		paintImage1 = new AtomicBoolean(true);
		
		// spawn a thread that draws the chart
		Thread thread = new Thread(new Runnable() {
			
			VolatileImage[] slices;
			
			// internal state tracking
			int    previousImageWidth;
			int    previousImageHeight;
			double previousMinY;
			double previousMaxY;
			double previousXdivisionSpacing;
			int    lastRenderedSlice;
			
			// settings
			int displayScalingFactor;
			Font tickFont;
			Font xAxisFont;
			Font yAxisFont;
			Font legendFont;
			int tickFontHeight;
			int xAxisFontHeight;
			int yAxisFontHeight;
			int legendFontHeight;
			FontRenderContext frc;
			
			@Override public void run() {
				
				slices = new VolatileImage[0];
				
				displayScalingFactor = (int) Controller.getDisplayScalingFactor();
				
				tickFont   = new Font("Geneva", Font.PLAIN, 12 * displayScalingFactor);
				xAxisFont  = new Font("Geneva", Font.BOLD,  20 * displayScalingFactor);
				yAxisFont  = new Font("Geneva", Font.BOLD,  20 * displayScalingFactor);
				legendFont = new Font("Geneva", Font.BOLD,  14 * displayScalingFactor);
				
				frc = new FontRenderContext(null, true, true);
				tickFontHeight   =   tickFont.createGlyphVector(frc, "Test").getPixelBounds(frc, 0, 0).height;
				xAxisFontHeight  =  xAxisFont.createGlyphVector(frc, "Test").getPixelBounds(frc, 0, 0).height;
				yAxisFontHeight  =  yAxisFont.createGlyphVector(frc, "Test").getPixelBounds(frc, 0, 0).height;
				legendFontHeight = legendFont.createGlyphVector(frc, "Test").getPixelBounds(frc, 0, 0).height;
				
				previousImageWidth = 0; 
				previousImageHeight = 0;
				previousMinY = 0;       
				previousMaxY = 0;
				previousXdivisionSpacing = 0;
				lastRenderedSlice = -1;
				
				while(true) {
					
					long startTime = System.currentTimeMillis();

					// draw the chart
					int chartWidth = getWidth();
					int chartHeight = getHeight();
					if(chartWidth < 1) chartWidth = 1;
					if(chartHeight < 1) chartHeight = 1;
					
					generateChart(chartWidth, chartHeight);
					
					// end this thread if we are no longer visible (the user clicked the "Reset" button)
					if(!isShowing())
						break;
					
					// wait if needed before drawing a new frame
					long timeToNextFrame = startTime + Controller.getTargetFramePeriod() - System.currentTimeMillis();
					if(timeToNextFrame <= 0)
						continue;
					else
						try{ Thread.sleep(timeToNextFrame); } catch(Exception e) {}
					
				}
				
			}
			
			/**
			 * Determines the best y values to use for vertical divisions. The 1/2/5 pattern is used (1,2,5,10,20,50,100,200,500...)
			 * 
			 * @param chartRegionHeight    Number of pixels for the y-axis
			 * @param minY                 Y value at the bottom of the chart
			 * @param maxY                 Y value at the top of the chart
			 * @param font                 Division label font (only used to calculate spacing)
			 * @return                     A Map of the y values for each division, keys are Doubles and values are formatted Strings
			 */
			private Map<Double, String> getVerticalDivisions(int chartRegionHeight, double minY, double maxY) {
				
				// calculate the best vertical division size
				int minSpacingBetweenText = 2 * tickFontHeight;
				int maxDivisionsCount = chartRegionHeight / (tickFontHeight + minSpacingBetweenText) + 1;
				double divisionSize = (maxY - minY) / maxDivisionsCount;
				double closestDivSize1 = Math.pow(10.0, Math.ceil(Math.log10(divisionSize/1.0))) * 1.0; // closest (10^n)*1 that is >= divisionSize, such as 1,10,100,1000
				double closestDivSize2 = Math.pow(10.0, Math.ceil(Math.log10(divisionSize/2.0))) * 2.0; // closest (10^n)*2 that is >= divisionSize, such as 2,20,200,2000
				double closestDivSize5 = Math.pow(10.0, Math.ceil(Math.log10(divisionSize/5.0))) * 5.0; // closest (10^n)*5 that is >= divisionSize, such as 5,50,500,5000
				double error1 = closestDivSize1 - divisionSize;
				double error2 = closestDivSize2 - divisionSize;
				double error5 = closestDivSize5 - divisionSize;
				if(error1 < error2 && error1 < error5)
					divisionSize = closestDivSize1;
				else if(error2 < error1 && error2 < error5)
					divisionSize = closestDivSize2;
				else
					divisionSize= closestDivSize5;
				
				// decide if the numbers should be displayed as integers, or as floats to one significant decimal place
				int precision = 0;
				String format = "";
				if(divisionSize < 0.99) {
					precision = 1;
					double size = divisionSize;
					while(size * Math.pow(10, precision) < 1.0)
						precision++;
					format = "%." + precision + "f";
				}
				
				// calculate the values for each vertical division
				double firstDivision = maxY - (maxY % divisionSize);
				double lastDivision  = minY - (minY % divisionSize);
				if(firstDivision > maxY)
					firstDivision -= divisionSize;
				if(lastDivision < minY)
					lastDivision += divisionSize;
				int divisionCount = (int) Math.round((firstDivision - lastDivision) / divisionSize) + 1;
				
				Map<Double, String> yValues = new HashMap<Double, String>();
				for(int i = 0; i < divisionCount; i++) {
					double number = firstDivision - (i * divisionSize);
					String text;
					if(precision == 0) {
						text = Integer.toString((int) number);
					} else {
						text = String.format(format, number);
					}
					yValues.put(number, text);
				}
				
				return yValues;
				
			}
			
			/**
			 * Determines the best x values to use for horizontal divisions. The 1/2/5 pattern is used (1,2,5,10,20,50,100,200,500...)
			 * 
			 * @param chartRegionWidth    Number of pixels for the x-axis
			 * @param minX                X value at the left of the chart
			 * @param maxX                X value at the right of the chart
			 * @param font                Division label font (only used to calculate spacing)
			 * @return                    A Map of the x values for each division, keys are Integers and values are formatted Strings
			 */
			private Map<Integer, String> getHorizontalDivisions(int chartRegionWidth, int minX, int maxX) {
				
				// calculate the best horizontal division size
				int textWidth = (int) Double.max(tickFont.getStringBounds(Integer.toString(maxX), frc).getWidth(), tickFont.getStringBounds(Integer.toString(minX), frc).getWidth());
				int minSpacingBetweenText = textWidth;
				int maxDivisionsCount = chartRegionWidth / (textWidth + minSpacingBetweenText) + 1;
				int divisionSize = (maxX - minX) / maxDivisionsCount;
				int closestDivSize1 = (int) Math.pow(10.0, Math.ceil(Math.log10(divisionSize/1.0))) * 1; // closest (10^n)*1 that is >= divisionSize, such as 1,10,100,1000
				int closestDivSize2 = (int) Math.pow(10.0, Math.ceil(Math.log10(divisionSize/2.0))) * 2; // closest (10^n)*2 that is >= divisionSize, such as 2,20,200,2000
				int closestDivSize5 = (int) Math.pow(10.0, Math.ceil(Math.log10(divisionSize/5.0))) * 5; // closest (10^n)*5 that is >= divisionSize, such as 5,50,500,5000
				int error1 = closestDivSize1 - divisionSize;
				int error2 = closestDivSize2 - divisionSize;
				int error5 = closestDivSize5 - divisionSize;
				if(error1 < error2 && error1 < error5)
					divisionSize = closestDivSize1;
				else if(error2 < error1 && error2 < error5)
					divisionSize = closestDivSize2;
				else
					divisionSize= closestDivSize5;
				
				// calculate the values for each horizontal division
				int firstDivision = maxX - (maxX % divisionSize);
				int lastDivision  = minX - (minX % divisionSize);
				if(firstDivision > maxX)
					firstDivision -= divisionSize;
				if(lastDivision < minX)
					lastDivision += divisionSize;
				int divisionCount = ((firstDivision - lastDivision) / divisionSize + 1);
				
				Map<Integer, String> xValues = new HashMap<Integer, String>();
				for(int i = 0; i < divisionCount; i++) {
					int number = lastDivision + (i * divisionSize);
					String text = Integer.toString(number);
					xValues.put(number, text);
				}
				
				return xValues;
				
			}
			
			/**
			 * Generates a new chart image.
			 * 
			 * @param imageWidth     Width, in pixels.
			 * @param imageHeight    Height, in pixels.
			 */
			public void generateChart(int imageWidth, int imageHeight) {
				
//				long startTime = System.currentTimeMillis();
				
				// settings
				final int sliceWidth = 32; // pixels
				final int tickLength = 6 * displayScalingFactor; // pixels
				final int tickTextPadding = 3 * displayScalingFactor; // pixels
				final double rangeHysteresis = 0.10; // 10%
				final int legendTextPadding = 5 * displayScalingFactor;
				final int strokeWidth = displayScalingFactor;
				final int imagePadding = 5 * displayScalingFactor;
				
				// calculate domain
				int sampleCount = Controller.getSamplesCount(datasets);
				int maxX = sampleCount - 1;
				int minX = maxX - duration;
				
				// calculate min/max y values
				double maxY = 0.0;
				double minY = 0.0;
				if(sampleCount > 0) {
					int firstX = minX >= 0 ? minX : 0;
					maxY = minY = datasets[0].get(firstX);
					for(Dataset dataset : datasets)
						for(int i = firstX; i <= maxX; i++) {
							double value = dataset.get(i);
							if(value > maxY) maxY = value;
							if(value < minY) minY = value;
						}
				}

				// ensure the range is >0
				if(minY == maxY) {
					double value = minY;
					minY = value - 0.001;
					maxY = value + 0.001;
				}
				
				// apply hysteresis to min/max y values
				boolean newMaxYisOutsideOldRange = maxY < Math.min(previousMaxY, previousMinY) || maxY > Math.max(previousMaxY, previousMinY);
				boolean newMinYisOutsideOldRange = minY < Math.min(previousMaxY, previousMinY) || minY > Math.max(previousMaxY, previousMinY);
				
				boolean newMaxYisFarAway = Math.abs(Math.max(previousMaxY, previousMinY) - maxY) > 1.5 * rangeHysteresis * Math.abs(previousMaxY - previousMinY);
				boolean newMinYisFarAway = Math.abs(Math.min(previousMaxY, previousMinY) - minY) > 1.5 * rangeHysteresis * Math.abs(previousMaxY - previousMinY);
				
				boolean newMaxYisTooClose = false;//Math.abs(Math.max(previousMaxY, previousMinY) - maxY) < 0.5 * rangeHysteresis * Math.abs(previousMaxY - previousMinY);
				boolean newMinYisTooClose = false;//Math.abs(Math.min(previousMaxY, previousMinY) - minY) < 0.5 * rangeHysteresis * Math.abs(previousMaxY - previousMinY);
				
				if(newMaxYisOutsideOldRange || newMinYisOutsideOldRange || newMaxYisFarAway || newMinYisFarAway || newMaxYisTooClose || newMinYisTooClose) {
					// reset range, centered on the current true range + hysteresis
					maxY += Math.abs(maxY - minY) * rangeHysteresis;
					minY -= Math.abs(maxY - minY) * rangeHysteresis;	
				} else {
					// old range is fine, use the old range
					maxY = previousMaxY;
					minY = previousMinY;
				}
				
				double range = maxY - minY;
				
				// calculate x and y positions of everything
				int xStartOfLegendBorder = imagePadding;
				int yEndOfLegendBorder = imageHeight - imagePadding;
				int yStartOfLegendBorder = yEndOfLegendBorder - strokeWidth - legendTextPadding - legendFontHeight - legendTextPadding - strokeWidth;
				int yStartOfLegend = yStartOfLegendBorder + strokeWidth + legendTextPadding;
				int yBaselineOfLegend = yStartOfLegend + legendFontHeight;
				
				int yEndOfTopPadding = imagePadding;
				int yStartOfChartBorder = yEndOfTopPadding;
				int yStartOfChart = yStartOfChartBorder + strokeWidth;
				
				int yEndOfImage = imageHeight - 1;
				int yBaselineOfXaxisTitle = yEndOfImage - imagePadding;
				int yBaselineOfXaxisDivisionLabels = yBaselineOfXaxisTitle - xAxisFontHeight - xAxisFontHeight; // use font height also as padding amount
				if(yBaselineOfXaxisDivisionLabels > yStartOfLegendBorder - legendTextPadding - legendTextPadding)
					yBaselineOfXaxisDivisionLabels = yStartOfLegendBorder - legendTextPadding - legendTextPadding;
				
				int yEndOfXaxisTickMarks = yBaselineOfXaxisDivisionLabels - tickFontHeight - tickTextPadding;
				int yStartOfXaxisTickMarks = yEndOfXaxisTickMarks - tickLength;
				int yEndofChartBorder = yStartOfXaxisTickMarks;
				int yEndOfChart = yEndofChartBorder - strokeWidth;
				
				int graphHeight = yEndOfChart - yStartOfChart + 1;
				if(graphHeight < 1)
					return;
				
				Map<Double, String> yDivisions = getVerticalDivisions(graphHeight, minY, maxY);
				
				int maxYtickLabelWidth = 0;
				for(String text : yDivisions.values()) {
					int width = (int) tickFont.getStringBounds(text, frc).getWidth(); 
					if(width > maxYtickLabelWidth)
						maxYtickLabelWidth = width;
				}
				
				int xEndOfLeftPadding = imagePadding;
				int xBaselineOfYaxisTitle = xEndOfLeftPadding + yAxisFontHeight;
				int xLeftSideOfYaxisDivisionLabels = xBaselineOfYaxisTitle + yAxisFontHeight; // use font height also as padding amount
				int xRightSideOfYaxisDivisionLabels = xLeftSideOfYaxisDivisionLabels + maxYtickLabelWidth;
				int xStartOfYaxisTickMarks = xRightSideOfYaxisDivisionLabels + tickTextPadding;
				int xEndOfYaxisTickMarks = xStartOfYaxisTickMarks + tickLength;
				int xStartOfChartBorder = xEndOfYaxisTickMarks;
				int xStartOfChart = xStartOfChartBorder + strokeWidth;
				
				int xEndOfImage = imageWidth - 1;
				int xEndOfChartBorder = xEndOfImage - imagePadding;
				int xEndOfChart = xEndOfChartBorder - strokeWidth;
				
				int graphWidth = xEndOfChart - xStartOfChart + 1;
				if(graphWidth < 1)
					return;
				
				Map<Integer, String> xDivisions = getHorizontalDivisions(graphWidth, minX, maxX);
				
				int yStartOfYaxisTitle = yEndOfChart - (graphHeight / 2) + ((int) yAxisFont.getStringBounds(datasets[0].unit, frc).getWidth() / 2);
				int xStartOfXaxisTitle = xStartOfChart + (graphWidth / 2) - ((int) xAxisFont.getStringBounds("Sample Number", frc).getWidth() / 2);
				
				// delete both images if the image size has changed
				if(imageWidth != previousImageWidth || imageHeight != previousImageHeight) {
					if(image1 != null)
						image1.flush();
					image1 = null;
					
					if(image2 != null)
						image2.flush();
					image2 = null;
				}
				
				// draw on the image that is not scheduled to be painted
				VolatileImage chartImage;
				if(paintImage1.get()) {
					if(image2 == null)
						image2 = createVolatileImage(imageWidth, imageHeight);
					chartImage = image2;
				} else {
					if(image1 == null)
						image1 = createVolatileImage(imageWidth, imageHeight);
					chartImage = image1;
				}
				
				Graphics2D chart = chartImage.createGraphics();
				chart.setStroke(new BasicStroke(strokeWidth));
				chart.setRenderingHint(RenderingHints.KEY_ANTIALIASING,   RenderingHints.VALUE_ANTIALIAS_ON);
				chart.setRenderingHint(RenderingHints.KEY_INTERPOLATION,  RenderingHints.VALUE_INTERPOLATION_BICUBIC);
				
				// determine the last slice that can be rendered
				int lastSliceIndex  = (int) Math.floor((double) sampleCount / (double) duration * (double) graphWidth / (double) sliceWidth) - 1;
				if(drawPartialLastSlice)
					lastSliceIndex++;
				
				// determine if the x division size changed
				boolean xDivisionSpacingChanged = false;
				if(xDivisions.size() > 1) {
					Integer[] divs = xDivisions.keySet().toArray(new Integer[1]);
					int currentXdivisionSpacing = Math.abs(divs[1] - divs[0]);
					if(currentXdivisionSpacing != previousXdivisionSpacing) {
						xDivisionSpacingChanged = true;
						previousXdivisionSpacing = currentXdivisionSpacing;
					}
				}
				
				// we need to erase all slices and redraw them if the image size changed, or if the range (y-axis) changed or if the x-axis division spacing changed
				if(previousImageWidth != imageWidth || previousImageHeight != imageHeight || previousMinY != minY || previousMaxY != maxY || xDivisionSpacingChanged) {
					
					previousImageWidth  = imageWidth;
					previousImageHeight = imageHeight;
					
					previousMinY = minY;
					previousMaxY = maxY;
					
					int slicesCount = (int) Math.ceil(graphWidth / sliceWidth) + 1;
					if(drawPartialLastSlice)
						slicesCount++;
					slices = new VolatileImage[slicesCount];
					
					lastRenderedSlice = lastSliceIndex - slicesCount;
					if(lastRenderedSlice < -1)
						lastRenderedSlice = -1;
					
//					System.err.println("slices flushed");
					
				}

				// generate any new slices
				for(int sliceNumber = lastRenderedSlice + 1; sliceNumber <= lastSliceIndex; sliceNumber++) {
					
					VolatileImage sliceImage;
					
					// reuse existing slice image object, or create a new one if it doesn't exist
					int rbIndex = sliceNumber % slices.length;
					if(slices[rbIndex] == null) {
						slices[rbIndex] = createVolatileImage(sliceWidth, graphHeight);
						if(slices[rbIndex] == null) return;
						sliceImage = slices[rbIndex];
					} else {
						sliceImage = slices[rbIndex];
					}
					
					Graphics2D slice = (Graphics2D) sliceImage.createGraphics();
					slice.setStroke(new BasicStroke(strokeWidth)); // for display scaling
					slice.setRenderingHint(RenderingHints.KEY_ANTIALIASING,   RenderingHints.VALUE_ANTIALIAS_ON);
					slice.setRenderingHint(RenderingHints.KEY_INTERPOLATION,  RenderingHints.VALUE_INTERPOLATION_BICUBIC);
					
					// draw gray background
					slice.setColor(new Color(230, 230, 230));
					slice.fillRect(0, 0, sliceWidth, graphHeight);
					
					// determine which samples need to be drawn in this slice
					int firstIndex = (int) Math.floor((double) (sliceNumber * sliceWidth)    * ((double) duration / (double) graphWidth));
					int lastIndex  = (int) Math.ceil((double) ((sliceNumber+1) * sliceWidth) * ((double) duration / (double) graphWidth));
					if(lastIndex == firstIndex)
						lastIndex++;
					
					if(lastIndex > sampleCount - 1){
						lastIndex = sampleCount - 1;
					}
					
					// draw horizontal division lines
					slice.setColor(Color.LIGHT_GRAY);
					for(Double entry : yDivisions.keySet()) {
						
						int y = graphHeight - (int) ((entry - minY) / range * (double) graphHeight);
						slice.drawLine(0, y, sliceWidth, y);
						
					}
					
					// draw vertical division lines
					slice.setColor(Color.LIGHT_GRAY);
					for(Integer entry : xDivisions.keySet()) {
						
						double x = (double) entry * (double) graphWidth / (double) duration - (sliceNumber * sliceWidth);
						Line2D.Double line = new Line2D.Double(x, 0.0, x, graphHeight);
						slice.draw(line);
						
					}
					
					// draw a path for each dataset
					for(Dataset dataset : datasets) {
						
						Path2D.Double path = new Path2D.Double();
						
						double x = 0;
						double y = 0;
						
						// start the path at the first point for this slice
						if(firstIndex <= lastIndex) {
							x = (double) firstIndex * (double) graphWidth / (double) duration - (sliceNumber * sliceWidth);
							y = (double) graphHeight - (dataset.get(firstIndex) - minY) / range * (double) graphHeight;
							path.moveTo(x, y);
						}
						
						for(int i = firstIndex + 1; i < lastIndex; i++) {
							x = (double) i * (double) graphWidth / (double) duration - (sliceNumber * sliceWidth);
							y = (double) graphHeight - (dataset.get(i) - minY) / range * (double) graphHeight;
							path.lineTo(x, y);
						}
						
						slice.setColor(dataset.color);
						slice.draw(path);
						
					}
					
					// free resources
					slice.dispose();
					
					lastRenderedSlice = sliceNumber;
					
				}
				
				if(drawPartialLastSlice)
					lastRenderedSlice--;
				
				// draw chart background
				chart.setColor(new Color(230, 230, 230));
				chart.fillRect(xStartOfChartBorder, yStartOfChartBorder, (xEndOfChartBorder - xStartOfChartBorder), (yEndofChartBorder - yStartOfChartBorder));

				// draw slices on screen
				int pixelCount = (int) Math.floor((double) sampleCount / (double) duration * (double) graphWidth); // what sliceCount would be if slices were 1px wide
				int xOffset = pixelCount - ((lastSliceIndex + 1) * sliceWidth);
				
				int lastSlice  = lastSliceIndex;
				int firstSlice = lastSlice - (graphWidth / sliceWidth);
				if(drawPartialLastSlice)
					firstSlice--;
				if(firstSlice < 0)
					firstSlice = 0;

				for(int currentSlice = firstSlice; currentSlice <= lastSlice; currentSlice++) {
					int x = xStartOfChart - xOffset + graphWidth - ((lastSlice - currentSlice + 1) * sliceWidth);
					int y = yStartOfChart;
					chart.drawImage(slices[currentSlice % slices.length], x, y, null);
				}

				// draw black outline around chart
				chart.setColor(Color.BLACK);
				chart.drawRect(xStartOfChartBorder, yStartOfChartBorder, (xEndOfChartBorder - xStartOfChartBorder), (yEndofChartBorder - yStartOfChartBorder));
				
				// draw white background
				chart.setColor(Color.WHITE);
				chart.fillRect(0, 0, imageWidth, yStartOfChartBorder - 1); // top
				chart.fillRect(0, 0, xStartOfChartBorder - 1, imageHeight); // left
				chart.fillRect(0, yEndofChartBorder + 1, imageWidth, (yEndOfImage - yEndofChartBorder)); // bottom
				chart.fillRect(xEndOfChartBorder + 1, 0, (xEndOfImage - xEndOfChartBorder), imageHeight); // right
				
				// draw legend
				int x = xStartOfLegendBorder + strokeWidth + legendTextPadding;
				chart.setFont(legendFont);
				for(Dataset dataset : datasets) {
					chart.setColor(dataset.color);
					chart.fillRect(x, yStartOfLegend, legendFontHeight, legendFontHeight);
					x += legendFontHeight + legendTextPadding;
					chart.setColor(Color.BLACK);
					chart.drawString(dataset.name, x, yBaselineOfLegend);
					x += (int) legendFont.getStringBounds(dataset.name, frc).getWidth() + 50; // leave 50px after each name
				}
				x -= 49;
				chart.drawRect(xStartOfLegendBorder, yStartOfLegendBorder, x, (yEndOfLegendBorder - yStartOfLegendBorder));
				
				// draw y-axis label if there is room for it
				chart.setColor(Color.BLACK);
				chart.setFont(yAxisFont);
				if(yStartOfYaxisTitle < yEndOfChart) {
					String yAxisText = datasets[0].unit;
					AffineTransform original = chart.getTransform();
					chart.rotate(-Math.PI / 2.0, xBaselineOfYaxisTitle, yStartOfYaxisTitle);
					chart.drawString(yAxisText, xBaselineOfYaxisTitle, yStartOfYaxisTitle);
					chart.setTransform(original);
				}
				
				// draw x-axis label, shifting it to the right if the legend gets in the way
				chart.setColor(Color.BLACK);
				chart.setFont(xAxisFont);
				if(xStartOfXaxisTitle < x + legendTextPadding + legendTextPadding)
					xStartOfXaxisTitle = x + legendTextPadding + legendTextPadding;
				chart.drawString("Sample Number", xStartOfXaxisTitle, yBaselineOfXaxisTitle);
				
				// draw range tick marks and text
				chart.setColor(Color.BLACK);
				chart.setFont(tickFont);
				for(Map.Entry<Double,String> entry : yDivisions.entrySet()) {
					
					int tickY = yEndOfChart + 1 - (int) ((entry.getKey() - minY) / range * (double) graphHeight);
					chart.drawLine(xStartOfYaxisTickMarks, tickY, xEndOfYaxisTickMarks, tickY);
					
					String text = entry.getValue();
					int textWidth = (int) tickFont.getStringBounds(text, frc).getWidth();
					int textX = xStartOfYaxisTickMarks - tickTextPadding - textWidth;
					int textY = tickY + (tickFontHeight / 2);
					chart.drawString(text, textX, textY);
					
				}
				
				// draw domain tick marks and text
				chart.setColor(Color.BLACK);
				chart.setFont(tickFont);
				for(Map.Entry<Integer,String> entry : xDivisions.entrySet()) {
					
					int tickX = xStartOfChart + 1 + (int) ((entry.getKey() - minX) / (double) (maxX - minX) * (double) graphWidth);
					chart.drawLine(tickX, yStartOfXaxisTickMarks, tickX, yEndOfXaxisTickMarks);
					
					String text = entry.getValue();
					int textWidth = (int) tickFont.getStringBounds(text, frc).getWidth();
					int textX = tickX - (textWidth / 2);
					int textY = yEndOfXaxisTickMarks + tickTextPadding + tickFontHeight;
					chart.drawString(text, textX, textY);
					
				}
								
				// free resources
				chart.dispose();
								
				// schedule a repaint of this panel
				SwingUtilities.invokeLater(new Runnable() {
					@Override public void run() {
						repaint();
					}
				});
				
				if(!paintImage1.compareAndSet(true, false))
					paintImage1.set(true);
				
//				System.out.println((System.currentTimeMillis() - startTime) + "ms");
				
			}
			
		});
		String inputNames = "";
		for(Dataset dataset : datasets)
			inputNames += dataset.name + ", ";
		thread.setName(String.format("TimeDomainChartCached of: %s", inputNames));
		thread.start();

	}
	
	@Override protected void paintComponent(Graphics g) {
		
		super.paintComponent(g);
		
		if(paintImage1.get() == true && image1 != null) {
			g.drawImage(image1, 0, 0, null);
		} else if(paintImage1.get() == false && image2 != null){
			g.drawImage(image2, 0, 0, null);
		}

	}
	
}
