import java.awt.Color;
import java.util.Map;

import com.jogamp.opengl.GL2ES3;
import com.jogamp.opengl.GL3;

/**
 * Renders a Histogram.
 * 
 * User settings:
 *     Datasets to visualize.
 *     Sample count.
 *     Bin count.
 *     X-axis type (normal or locked center.)
 *     For a normal x-axis: minimum value can be fixed or autoscaled.
 *     For a normal x-axis: maximum value can be fixed or autoscaled.
 *     For a locked center x-axis: the center value can be specified.
 *     Y-axis type (relative frequency, frequency, or both.)
 *     Y-axis minimum value can be fixed or autoscaled.
 *     Y-axis maximum value can be fixed or autoscaled.
 *     X-axis title can be displayed.
 *     X-axis scale can be displayed.
 *     Y-axis title can be displayed.
 *     Y-axis scale can be displayed.
 *     Legend can be displayed.
 */
public class OpenGLHistogramChart extends PositionedChart {
	
	Samples[] samples;
	
	int[][] bins; // [datasetN][binN]
	int binCount;
	
	// plot region
	float xPlotLeft;
	float xPlotRight;
	float plotWidth;
	float yPlotTop;
	float yPlotBottom;
	float plotHeight;
	
	// x-axis title
	boolean showXaxisTitle;
	float yXaxisTitleTextBasline;
	float yXaxisTitleTextTop;
	String xAxisTitle;
	float xXaxisTitleTextLeft;
	
	// legend
	boolean showLegend;
	float xLegendBorderLeft;
	float yLegendBorderBottom;
	float yLegendTextBaseline;
	float yLegendTextTop;
	float yLegendBorderTop;
	float[][] legendMouseoverCoordinates;
	float[][] legendBoxCoordinates;
	float[] xLegendNameLeft;
	float xLegendBorderRight;
	
	// x-axis scale
	boolean showXaxisScale;
	Map<Float, String> xDivisions;
	float yXaxisTickTextBaseline;
	float yXaxisTickTextTop;
	float yXaxisTickBottom;
	float yXaxisTickTop;
	
	boolean xAxisIsCentered;
	float xCenterValue;
	boolean xAutoscaleMin;
	boolean xAutoscaleMax;
	float manualMinX;
	float manualMaxX;
	
	// y-axis title
	boolean showYaxisTitle;
	float xYaxisLeftTitleTextTop;
	float xYaxisLeftTitleTextBaseline;
	String yAxisLeftTitle;
	float yYaxisLeftTitleTextLeft;
	float xYaxisRightTitleTextTop;
	float xYaxisRightTitleTextBaseline;
	String yAxisRightTitle;
	float yYaxisRightTitleTextLeft;
	
	// y-axis scale
	boolean showYaxisScale;
	float xYaxisLeftTickTextRight;
	float xYaxisLeftTickLeft;
	float xYaxisLeftTickRight;
	float xYaxisRightTickTextLeft;
	float xYaxisRightTickLeft;
	float xYaxisRightTickRight;
		
	boolean yAxisShowsRelativeFrequency;
	boolean yAxisShowsFrequency;
	AutoScale yAutoscaleRelativeFrequency;
	AutoScale yAutoscaleFrequency;
	boolean yMinimumIsZero;
	boolean yAutoscaleMax;
	float manualMinY; // relative frequency unless only frequency is shown
	float manualMaxY; // relative frequency unless only frequency is shown
	
	// constraints
	static final int SampleCountDefault = 1000;
	static final int SampleCountMinimum = 5;
	static final int SampleCountMaximum = Integer.MAX_VALUE;

	static final int BinCountDefault = 60;
	static final int BinCountMinimum = 2;
	static final int BinCountMaximum = Integer.MAX_VALUE;
	
	static final float xAxisMinimumDefault = -1;
	static final float xAxisMaximumDefault =  1;
	static final float xAxisCenterDefault  =  0;
	static final float xAxisLowerLimit     = -Float.MAX_VALUE;
	static final float xAxisUpperLimit     =  Float.MAX_VALUE;
	
	static final float yAxisRelativeFrequencyMinimumDefault = 0;
	static final float yAxisRelativeFrequencyMaximumDefault = 1;
	static final float yAxisRelativeFrequencyLowerLimit     = 0;
	static final float yAxisRelativeFrequencyUpperLimit     = 1;
	
	static final int yAxisFrequencyMinimumDefault = 0;
	static final int yAxisFrequencyMaximumDefault = 1000;
	static final int yAxisFrequencyLowerLimit     = 0;
	static final int yAxisFrequencyUpperLimit     = Integer.MAX_VALUE;
	
	// control widgets
	WidgetDatasets datasetsWidget;
	WidgetTextfieldInteger sampleCountWidget;
	WidgetTextfieldInteger binCountWidget;
	WidgetHistogramXaxisType xAxisTypeWidget;
	WidgetHistogramYaxisType yAxisTypeWidget;
	WidgetCheckbox showXaxisTitleWidget;
	WidgetCheckbox showXaxisScaleWidget;
	WidgetCheckbox showYaxisTitleWidget;
	WidgetCheckbox showYaxisScaleWidget;
	WidgetCheckbox showLegendWidget;
	
	@Override public String toString() {
		
		return "Histogram";
		
	}
	
	public OpenGLHistogramChart(int x1, int y1, int x2, int y2) {
		
		super(x1, y1, x2, y2);

		yAutoscaleRelativeFrequency = new AutoScale(AutoScale.MODE_EXPONENTIAL, 30, 0.20f);
		yAutoscaleFrequency = new AutoScale(AutoScale.MODE_EXPONENTIAL, 30, 0.20f);
		
		// create the control widgets and event handlers
		datasetsWidget = new WidgetDatasets(newDatasets -> {datasets = newDatasets;
		                                                    bins = new int[datasets.size()][binCount];
		                                                    samples = new Samples[datasets.size()];
		                                                    for(int i = 0; i < samples.length; i++)
		                                                    	samples[i] = new Samples();
		                                                    });
		
		sampleCountWidget = new WidgetTextfieldInteger("Sample Count",
		                                               SampleCountDefault,
		                                               SampleCountMinimum,
		                                               SampleCountMaximum,
		                                               newSampleCount -> sampleCount = newSampleCount);
		
		binCountWidget = new WidgetTextfieldInteger("Bin Count",
		                                            BinCountDefault,
		                                            BinCountMinimum,
		                                            BinCountMaximum,
		                                            newBinCount -> {binCount = newBinCount;
		                                                            if(datasets != null)
		                                                            	bins = new int[datasets.size()][binCount];
		                                                            });
		
		xAxisTypeWidget = new WidgetHistogramXaxisType(xAxisMinimumDefault,
		                                               xAxisMaximumDefault,
		                                               xAxisCenterDefault,
		                                               xAxisLowerLimit,
		                                               xAxisUpperLimit,
		                                               (newXautoscaleMin, newManualMinX) ->     { xAutoscaleMin = newXautoscaleMin; manualMinX = newManualMinX; },
		                                               (newXautoscaleMax, newManualMaxX) ->     { xAutoscaleMax = newXautoscaleMax; manualMaxX = newManualMaxX; },
		                                               (newXaxisIsCentered, newXcenterValue) -> { xAxisIsCentered = newXaxisIsCentered; xCenterValue = newXcenterValue; });
		
		yAxisTypeWidget = new WidgetHistogramYaxisType(yAxisRelativeFrequencyMinimumDefault,
		                                               yAxisRelativeFrequencyMaximumDefault,
		                                               yAxisRelativeFrequencyLowerLimit,
		                                               yAxisRelativeFrequencyUpperLimit,
		                                               yAxisFrequencyMinimumDefault,
		                                               yAxisFrequencyMaximumDefault,
		                                               yAxisFrequencyLowerLimit,
		                                               yAxisFrequencyUpperLimit,
		                                               (newYaxisShowsRelativeFrequency, newYaxisShowsFrequency) -> { yAxisShowsRelativeFrequency = newYaxisShowsRelativeFrequency; yAxisShowsFrequency = newYaxisShowsFrequency; },
		                                               (newYminimumIsZero, newManualMinY) ->                       { yMinimumIsZero = newYminimumIsZero; manualMinY = newManualMinY; },
		                                               (newYautoscaleMax, newManualMaxY) ->                        { yAutoscaleMax = newYautoscaleMax; manualMaxY = newManualMaxY; });
		
		showXaxisTitleWidget = new WidgetCheckbox("Show X-Axis Title",
		                                          true,
		                                          newShowXaxisTitle -> showXaxisTitle = newShowXaxisTitle);
		
		showXaxisScaleWidget = new WidgetCheckbox("Show X-Axis Scale",
		                                          true,
		                                          newShowXaxisScale -> showXaxisScale = newShowXaxisScale);
		
		showYaxisTitleWidget = new WidgetCheckbox("Show Y-Axis Title",
		                                          true,
		                                          newShowYaxisTitle -> showYaxisTitle = newShowYaxisTitle);
		
		showYaxisScaleWidget = new WidgetCheckbox("Show Y-Axis Scale",
		                                          true,
		                                          newShowYaxisScale -> showYaxisScale = newShowYaxisScale);
		
		showLegendWidget = new WidgetCheckbox("Show Legend",
		                                      true,
		                                      newShowLegend -> showLegend = newShowLegend);

		widgets = new Widget[16];
		
		widgets[0]  = datasetsWidget;
		widgets[1]  = null;
		widgets[2]  = sampleCountWidget;
		widgets[3]  = binCountWidget;
		widgets[4]  = null;
		widgets[5]  = xAxisTypeWidget;
		widgets[6]  = null;
		widgets[7]  = yAxisTypeWidget;
		widgets[8]  = null;
		widgets[9]  = showXaxisTitleWidget;
		widgets[10] = showXaxisScaleWidget;
		widgets[11] = null;
		widgets[12] = showYaxisTitleWidget;
		widgets[13] = showYaxisScaleWidget;
		widgets[14] = null;
		widgets[15] = showLegendWidget;
		
	}
	
	@Override public EventHandler drawChart(GL2ES3 gl, float[] chartMatrix, int width, int height, int lastSampleNumber, double zoomLevel, int mouseX, int mouseY) {
		
		EventHandler handler = null;
		
		// get the samples
		int endIndex = lastSampleNumber;
		int startIndex = endIndex - (int) (sampleCount * zoomLevel) + 1;
		int minDomain = SampleCountMinimum - 1;
		if(endIndex - startIndex < minDomain) startIndex = endIndex - minDomain;
		if(startIndex < 0) startIndex = 0;
		int sampleCount = endIndex - startIndex + 1;
		
		if(sampleCount - 1 < minDomain)
			return handler;
		
		boolean haveDatasets = datasets != null && !datasets.isEmpty();
		int datasetsCount = 0;
		
		if(haveDatasets) {
			datasetsCount = datasets.size();
			for(int datasetN = 0; datasetN < datasetsCount; datasetN++)
				datasets.get(datasetN).getSamples(startIndex, endIndex, samples[datasetN]);
		}

		// determine the true x-axis scale
		float trueMinX = xAxisMinimumDefault;
		float trueMaxX = xAxisMaximumDefault;
		if(haveDatasets) {
			trueMinX = samples[0].min;
			trueMaxX = samples[0].max;
			for(int datasetN = 1; datasetN < samples.length; datasetN++) {
				float min = samples[datasetN].min;
				float max = samples[datasetN].max;
				if(min < trueMinX) trueMinX = min;
				if(max > trueMaxX) trueMaxX = max;
			}
		}
		
		// determine the plotted x-axis scale
		float minX = 0;
		float maxX = 0;
		if(xAxisIsCentered) {
			float leftHalf  = (float) Math.abs(xCenterValue - trueMinX);
			float rightHalf = (float) Math.abs(xCenterValue - trueMaxX);
			float half = Float.max(leftHalf, rightHalf);
			minX = xCenterValue - half;
			maxX = Math.nextUp(xCenterValue + half); // increment because the bins are >=min, <max
		} else {
			minX = xAutoscaleMin ? trueMinX : manualMinX;
			maxX = xAutoscaleMax ? Math.nextUp(trueMaxX) : Math.nextUp(manualMaxX); // increment because the bins are >=min, <max
		}
		float range = maxX - minX;
		float binSize = range / (float) binCount;
		
//		for(int i = 0; i < binCount; i++) {
//			int binNumber = i;
//			float binStart = minX + (binSize * i);
//			float binEnd = minX + (binSize * (i + 1));
//			System.out.println(String.format("bin %02d, start = %f, end = %f", binNumber, binStart, binEnd));
//		}
//		System.out.println("");

		// empty the bins
		if(haveDatasets)
			for(int datasetN = 0; datasetN < datasetsCount; datasetN++)
				for(int binN = 0; binN < binCount; binN++)
					bins[datasetN][binN] = 0;
		
		// fill the bins
		if(haveDatasets) {
			for(int datasetN = 0; datasetN < datasetsCount; datasetN++) {
				for(int sampleN = 0; sampleN < samples[datasetN].buffer.length; sampleN++) {
					float sample = samples[datasetN].buffer[sampleN]; 
					if(sample >= minX && sample < maxX) {
						int binN = (int) Math.floor((sample - minX) / range * binCount);
						if(binN == binCount) binN--; // needed because of float math imperfection
						bins[datasetN][binN]++;
					}
				}
			}
		}

		int maxBinSize = 0;
		if(haveDatasets) {
			for(int datasetN = 0; datasetN < datasetsCount; datasetN++) {
				for(int binN = 0; binN < binCount; binN++)
					if(bins[datasetN][binN] > maxBinSize)
						maxBinSize = bins[datasetN][binN];
			}
		}
		
		float trueMaxYfreq = haveDatasets ? maxBinSize : sampleCount;
		float trueMaxYrelFreq = trueMaxYfreq / (float) sampleCount;
		
		// determine the y-axis min and max
		float minYrelFreq = 0;
		float maxYrelFreq = 0;
		float minYfreq = 0;
		float maxYfreq = 0;
		float yRelFreqRange = 0;
		float yFreqRange = 0;
		if(yAxisShowsRelativeFrequency) {
			
			// the range is determined by relative frequency, and frequency is forced to match it
			minYrelFreq = yMinimumIsZero ? 0 : manualMinY;
			yAutoscaleRelativeFrequency.update(minYrelFreq, trueMaxYrelFreq);
			maxYrelFreq = yAutoscaleMax ? yAutoscaleRelativeFrequency.getMax() : manualMaxY;
			minYfreq = minYrelFreq * (float) sampleCount;
			maxYfreq = maxYrelFreq * (float) sampleCount;
			
		} else {
			
			// the range is determined by frequency, and relative frequency is forced to match it
			minYfreq = yMinimumIsZero ? 0 : manualMinY;
			yAutoscaleFrequency.update(minYfreq, trueMaxYfreq);
			maxYfreq = yAutoscaleMax ? yAutoscaleFrequency.getMax() : manualMaxY;
			minYrelFreq = minYfreq / (float) sampleCount;
			maxYrelFreq = maxYfreq / (float) sampleCount;
			
		}
		yRelFreqRange = maxYrelFreq - minYrelFreq;
		yFreqRange = maxYfreq - minYfreq;
		
		// calculate x and y positions of everything
		xPlotLeft = Theme.tilePadding;
		xPlotRight = width - Theme.tilePadding;
		plotWidth = xPlotRight - xPlotLeft;
		yPlotTop = height - Theme.tilePadding;
		yPlotBottom = Theme.tilePadding;
		plotHeight = yPlotTop - yPlotBottom;
		
		if(showXaxisTitle) {
			yXaxisTitleTextBasline = Theme.tilePadding;
			yXaxisTitleTextTop = yXaxisTitleTextBasline + OpenGL.largeTextHeight;
			xAxisTitle = haveDatasets ? datasets.get(0).unit + " (" + sampleCount + " Samples)" : "";
			xXaxisTitleTextLeft = xPlotLeft + (plotWidth  / 2.0f) - (OpenGL.largeTextWidth(gl, xAxisTitle)  / 2.0f);
			
			float temp = yXaxisTitleTextTop + Theme.tickTextPadding;
			if(yPlotBottom < temp) {
				yPlotBottom = temp;
				plotHeight = yPlotTop - yPlotBottom;
			}
		}
		
		if(showLegend && haveDatasets) {
			xLegendBorderLeft = Theme.tilePadding;
			yLegendBorderBottom = Theme.tilePadding;
			yLegendTextBaseline = yLegendBorderBottom + Theme.legendTextPadding;
			yLegendTextTop = yLegendTextBaseline + OpenGL.mediumTextHeight;
			yLegendBorderTop = yLegendTextTop + Theme.legendTextPadding;

			legendMouseoverCoordinates = new float[datasetsCount][4];
			legendBoxCoordinates = new float[datasetsCount][4];
			xLegendNameLeft = new float[datasetsCount];
			
			float xOffset = xLegendBorderLeft + (Theme.lineWidth / 2) + Theme.legendTextPadding;
			
			for(int i = 0; i < datasetsCount; i++) {
				legendMouseoverCoordinates[i][0] = xOffset - Theme.legendTextPadding;
				legendMouseoverCoordinates[i][1] = yLegendBorderBottom;
				
				legendBoxCoordinates[i][0] = xOffset;
				legendBoxCoordinates[i][1] = yLegendTextBaseline;
				legendBoxCoordinates[i][2] = xOffset + OpenGL.mediumTextHeight;
				legendBoxCoordinates[i][3] = yLegendTextTop;
				
				xOffset += OpenGL.mediumTextHeight + Theme.legendTextPadding;
				xLegendNameLeft[i] = xOffset;
				xOffset += OpenGL.mediumTextWidth(gl, datasets.get(i).name) + Theme.legendNamesPadding;
				
				legendMouseoverCoordinates[i][2] = xOffset - Theme.legendNamesPadding + Theme.legendTextPadding;
				legendMouseoverCoordinates[i][3] = yLegendBorderTop;
			}
			
			xLegendBorderRight = xOffset - Theme.legendNamesPadding + Theme.legendTextPadding + (Theme.lineWidth / 2);
			if(showXaxisTitle)
				xXaxisTitleTextLeft = xLegendBorderRight + ((xPlotRight - xLegendBorderRight) / 2) - (OpenGL.largeTextWidth(gl, xAxisTitle)  / 2.0f);
			
			float temp = yLegendBorderTop + Theme.legendTextPadding;
			if(yPlotBottom < temp) {
				yPlotBottom = temp;
				plotHeight = yPlotTop - yPlotBottom;
			}
		}
		
		if(showXaxisScale) {
			yXaxisTickTextBaseline = yPlotBottom;
			yXaxisTickTextTop = yXaxisTickTextBaseline + OpenGL.smallTextHeight;
			yXaxisTickBottom = yXaxisTickTextTop + Theme.tickTextPadding;
			yXaxisTickTop = yXaxisTickBottom + Theme.tickLength;
			
			yPlotBottom = yXaxisTickTop;
			plotHeight = yPlotTop - yPlotBottom;
		}
		
		// get the y divisions now that we know the final plot height
		Map<Float, String> yDivisionsFrequency = ChartUtils.getYdivisions125(plotHeight, minYfreq, maxYfreq);
		Map<Float, String> yDivisionsRelativeFrequency = ChartUtils.getYdivisions125(plotHeight, minYrelFreq, maxYrelFreq);
		
		if(showYaxisTitle) {
			// the left y-axis is for Relative Frequency unless only Frequency will be shown
			xYaxisLeftTitleTextTop = xPlotLeft;
			xYaxisLeftTitleTextBaseline = xYaxisLeftTitleTextTop + OpenGL.largeTextHeight;
			yAxisLeftTitle = yAxisShowsRelativeFrequency ? "Relative Frequency" : "Frequency";
			yYaxisLeftTitleTextLeft = yPlotBottom + (plotHeight / 2.0f) - (OpenGL.largeTextWidth(gl, yAxisLeftTitle) / 2.0f);
			
			xPlotLeft = xYaxisLeftTitleTextBaseline + Theme.tickTextPadding;
			plotWidth = xPlotRight - xPlotLeft;
			
			if(showXaxisTitle && !showLegend)
				xXaxisTitleTextLeft = xPlotLeft + (plotWidth  / 2.0f) - (OpenGL.largeTextWidth(gl, xAxisTitle)  / 2.0f);
			
			// the right y-axis is always for Frequency
			if(yAxisShowsRelativeFrequency && yAxisShowsFrequency) {
				xYaxisRightTitleTextTop = xPlotRight;
				xYaxisRightTitleTextBaseline = xYaxisRightTitleTextTop - OpenGL.largeTextHeight;
				yAxisRightTitle = "Frequency";
				yYaxisRightTitleTextLeft = yPlotTop - (plotHeight / 2.0f) + (OpenGL.largeTextWidth(gl, yAxisRightTitle) / 2.0f);
				
				xPlotRight = xYaxisRightTitleTextBaseline - Theme.tickTextPadding;
				plotWidth = xPlotRight - xPlotLeft;
				
				if(showXaxisTitle && !showLegend)
					xXaxisTitleTextLeft = xPlotLeft + (plotWidth  / 2.0f) - (OpenGL.largeTextWidth(gl, xAxisTitle)  / 2.0f);
			}
		}
		
		if(showYaxisScale) {
			// the left y-axis is for Relative Frequency unless only Frequency will be shown
			float maxTextWidth = 0;
			for(String text : yAxisShowsRelativeFrequency ? yDivisionsRelativeFrequency.values() : yDivisionsFrequency.values()) {
				float textWidth = OpenGL.smallTextWidth(gl, text);
				if(textWidth > maxTextWidth)
					maxTextWidth = textWidth;
					
			}
			
			xYaxisLeftTickTextRight = xPlotLeft + maxTextWidth;
			xYaxisLeftTickLeft = xYaxisLeftTickTextRight + Theme.tickTextPadding;
			xYaxisLeftTickRight = xYaxisLeftTickLeft + Theme.tickLength;
			
			xPlotLeft = xYaxisLeftTickRight;
			plotWidth = xPlotRight - xPlotLeft;
			
			if(showXaxisTitle && !showLegend)
				xXaxisTitleTextLeft = xPlotLeft + (plotWidth  / 2.0f) - (OpenGL.largeTextWidth(gl, xAxisTitle)  / 2.0f);
			
			// the right y-axis is always for Frequency
			if(yAxisShowsRelativeFrequency && yAxisShowsFrequency) {
				float MaxTextWidth = 0;
				for(String text : yDivisionsFrequency.values()) {
					float textWidth = OpenGL.smallTextWidth(gl, text);
					if(textWidth > MaxTextWidth)
						MaxTextWidth = textWidth;
						
				}
				
				xYaxisRightTickTextLeft = xPlotRight - MaxTextWidth;
				xYaxisRightTickRight = xYaxisRightTickTextLeft - Theme.tickTextPadding;
				xYaxisRightTickLeft = xYaxisRightTickRight - Theme.tickLength;
				
				xPlotRight = xYaxisRightTickLeft;
				plotWidth = xPlotRight - xPlotLeft;
				
				if(showXaxisTitle && !showLegend)
					xXaxisTitleTextLeft = xPlotLeft + (plotWidth  / 2.0f) - (OpenGL.largeTextWidth(gl, xAxisTitle)  / 2.0f);
			}
		}
		
		// get the x divisions now that we know the final plot width
		xDivisions = ChartUtils.getFloatXdivisions125(gl, plotWidth, minX, maxX);
		
		// stop if the plot is too small
		if(plotWidth < 1 || plotHeight < 1)
			return handler;
		
		// draw plot background
		OpenGL.drawQuad2D(gl, Theme.plotBackgroundColor, xPlotLeft, yPlotBottom, xPlotRight, yPlotTop);
		
		// draw the x-axis scale
		if(showXaxisScale) {
			OpenGL.buffer.rewind();
			for(Float xValue : xDivisions.keySet()) {
				float x = ((xValue - minX) / range * plotWidth) + xPlotLeft;
				OpenGL.buffer.put(x); OpenGL.buffer.put(yPlotTop);    OpenGL.buffer.put(Theme.divisionLinesColor);
				OpenGL.buffer.put(x); OpenGL.buffer.put(yPlotBottom); OpenGL.buffer.put(Theme.divisionLinesColor);
				
				OpenGL.buffer.put(x); OpenGL.buffer.put(yXaxisTickTop);    OpenGL.buffer.put(Theme.tickLinesColor);
				OpenGL.buffer.put(x); OpenGL.buffer.put(yXaxisTickBottom); OpenGL.buffer.put(Theme.tickLinesColor);
			}
			OpenGL.buffer.rewind();
			int vertexCount = xDivisions.keySet().size() * 4;
			OpenGL.drawLinesXyrgba(gl, GL3.GL_LINES, OpenGL.buffer, vertexCount);
			
			for(Map.Entry<Float,String> entry : xDivisions.entrySet()) {
				float x = ((entry.getKey() - minX) / range * plotWidth) + xPlotLeft - (OpenGL.smallTextWidth(gl, entry.getValue()) / 2.0f);
				float y = yXaxisTickTextBaseline;
				OpenGL.drawSmallText(gl, entry.getValue(), (int) x, (int) y, 0);
			}
		}
		
		// draw the y-axis scale
		if(showYaxisScale) {
		
			// draw right y-axis scale if showing both frequency and relative frequency
			if(yAxisShowsFrequency && yAxisShowsRelativeFrequency) {
				OpenGL.buffer.rewind();
				for(Float entry : yDivisionsFrequency.keySet()) {
					float y = (entry - minYfreq) / yFreqRange * plotHeight + yPlotBottom;
					OpenGL.buffer.put(xPlotRight); OpenGL.buffer.put(y); OpenGL.buffer.put(Theme.divisionLinesColor);
					OpenGL.buffer.put(xPlotLeft);  OpenGL.buffer.put(y); OpenGL.buffer.put(Theme.divisionLinesFadedColor);

					OpenGL.buffer.put(xYaxisRightTickLeft);  OpenGL.buffer.put(y); OpenGL.buffer.put(Theme.tickLinesColor);
					OpenGL.buffer.put(xYaxisRightTickRight); OpenGL.buffer.put(y); OpenGL.buffer.put(Theme.tickLinesColor);
				}
				OpenGL.buffer.rewind();
				int vertexCount = yDivisionsFrequency.keySet().size() * 4;
				OpenGL.drawLinesXyrgba(gl, GL3.GL_LINES, OpenGL.buffer, vertexCount);
	
				for(Map.Entry<Float,String> entry : yDivisionsFrequency.entrySet()) {
					float x = xYaxisRightTickTextLeft;
					float y = (entry.getKey() - minYfreq) / yFreqRange * plotHeight + yPlotBottom - (OpenGL.smallTextHeight / 2.0f);
					OpenGL.drawSmallText(gl, entry.getValue(), (int) x, (int) y, 0);
				}
				
			}
			
			// relative frequency is drawn on the left unless only frequency is to be drawn
			if(yAxisShowsRelativeFrequency) {
				
				OpenGL.buffer.rewind();
				for(Float entry : yDivisionsRelativeFrequency.keySet()) {
					float y = (entry - minYrelFreq) / yRelFreqRange * plotHeight + yPlotBottom;
					OpenGL.buffer.put(xPlotLeft);  OpenGL.buffer.put(y); OpenGL.buffer.put(Theme.divisionLinesColor);
					OpenGL.buffer.put(xPlotRight); OpenGL.buffer.put(y); OpenGL.buffer.put(yAxisShowsFrequency && yAxisShowsRelativeFrequency ? Theme.divisionLinesFadedColor : Theme.divisionLinesColor);

					OpenGL.buffer.put(xYaxisLeftTickLeft);  OpenGL.buffer.put(y); OpenGL.buffer.put(Theme.tickLinesColor);
					OpenGL.buffer.put(xYaxisLeftTickRight); OpenGL.buffer.put(y); OpenGL.buffer.put(Theme.tickLinesColor);
				}
				OpenGL.buffer.rewind();
				int vertexCount = yDivisionsRelativeFrequency.keySet().size() * 4;
				OpenGL.drawLinesXyrgba(gl, GL3.GL_LINES, OpenGL.buffer, vertexCount);
				
				for(Map.Entry<Float,String> entry : yDivisionsRelativeFrequency.entrySet()) {
					float x = xYaxisLeftTickTextRight - OpenGL.smallTextWidth(gl, entry.getValue());
					float y = (entry.getKey() - minYrelFreq) / yRelFreqRange * plotHeight + yPlotBottom - (OpenGL.smallTextHeight / 2.0f);
					OpenGL.drawSmallText(gl, entry.getValue(), (int) x, (int) y, 0);
				}
			
			} else {
				
				OpenGL.buffer.rewind();
				for(Float entry : yDivisionsFrequency.keySet()) {
					float y = (entry - minYfreq) / yFreqRange * plotHeight + yPlotBottom;
					OpenGL.buffer.put(xPlotLeft);  OpenGL.buffer.put(y); OpenGL.buffer.put(Theme.divisionLinesColor);
					OpenGL.buffer.put(xPlotRight); OpenGL.buffer.put(y); OpenGL.buffer.put(Theme.divisionLinesColor);

					OpenGL.buffer.put(xYaxisLeftTickLeft);  OpenGL.buffer.put(y); OpenGL.buffer.put(Theme.tickLinesColor);
					OpenGL.buffer.put(xYaxisLeftTickRight); OpenGL.buffer.put(y); OpenGL.buffer.put(Theme.tickLinesColor);
				}
				OpenGL.buffer.rewind();
				int vertexCount = yDivisionsFrequency.keySet().size() * 4;
				OpenGL.drawLinesXyrgba(gl, GL3.GL_LINES, OpenGL.buffer, vertexCount);
				
				for(Map.Entry<Float,String> entry : yDivisionsFrequency.entrySet()) {
					float x = xYaxisLeftTickTextRight - OpenGL.smallTextWidth(gl, entry.getValue());
					float y = (entry.getKey() - minYfreq) / yFreqRange * plotHeight + yPlotBottom - (OpenGL.smallTextHeight / 2.0f);
					OpenGL.drawSmallText(gl, entry.getValue(), (int) x, (int) y, 0);
				}
				
			}
			
		}
		
		// draw the legend, if space is available
		if(showLegend && haveDatasets && xLegendBorderRight < width - Theme.tilePadding) {
			OpenGL.drawQuad2D(gl, Theme.legendBackgroundColor, xLegendBorderLeft, yLegendBorderBottom, xLegendBorderRight, yLegendBorderTop);
			
			for(int i = 0; i < datasetsCount; i++) {
				Dataset d = datasets.get(i);
				if(mouseX >= legendMouseoverCoordinates[i][0] && mouseX <= legendMouseoverCoordinates[i][2] && mouseY >= legendMouseoverCoordinates[i][1] && mouseY <= legendMouseoverCoordinates[i][3]) {
					OpenGL.drawQuadOutline2D(gl, Theme.tickLinesColor, legendMouseoverCoordinates[i][0], legendMouseoverCoordinates[i][1], legendMouseoverCoordinates[i][2], legendMouseoverCoordinates[i][3]);
					handler = EventHandler.onPress(event -> ConfigureView.instance.forDataset(d));
				}
				OpenGL.drawQuad2D(gl, d.glColor, legendBoxCoordinates[i][0], legendBoxCoordinates[i][1], legendBoxCoordinates[i][2], legendBoxCoordinates[i][3]);
				OpenGL.drawMediumText(gl, d.name, (int) xLegendNameLeft[i], (int) yLegendTextBaseline, 0);
			}
		}
					
		// draw the x-axis title, if space is available
		if(showXaxisTitle)
			if((!showLegend && xXaxisTitleTextLeft > xPlotLeft) || (showLegend && xXaxisTitleTextLeft > xLegendBorderRight + Theme.legendTextPadding))
				OpenGL.drawLargeText(gl, xAxisTitle, (int) xXaxisTitleTextLeft, (int) yXaxisTitleTextBasline, 0);
		
		// draw the left y-axis title, if space is available
		if(showYaxisTitle && yYaxisLeftTitleTextLeft >= yPlotBottom)
			OpenGL.drawLargeText(gl, yAxisLeftTitle, (int) xYaxisLeftTitleTextBaseline, (int) yYaxisLeftTitleTextLeft, 90);
		
		// draw the right y-axis title, if applicable, and if space is available
		if(showYaxisTitle && yAxisShowsRelativeFrequency && yAxisShowsFrequency && yYaxisRightTitleTextLeft <= yPlotTop)
			OpenGL.drawLargeText(gl, yAxisRightTitle, (int) xYaxisRightTitleTextBaseline, (int) yYaxisRightTitleTextLeft, -90);

		// clip to the plot region
		int[] originalScissorArgs = new int[4];
		gl.glGetIntegerv(GL3.GL_SCISSOR_BOX, originalScissorArgs, 0);
		gl.glScissor(originalScissorArgs[0] + (int) xPlotLeft, originalScissorArgs[1] + (int) yPlotBottom, (int) plotWidth, (int) plotHeight);
		
		// draw the bins
		if(haveDatasets) {
			for(int datasetN = 0; datasetN < datasetsCount; datasetN++) {
				
				for(int binN = 0; binN < binCount; binN++) {
					
					float min = minX + (binSize *  binN);      // inclusive
					float max = minX + (binSize * (binN + 1)); // exclusive
					float center = (max + min) / 2f;
					
					float xBarCenter = ((center - minX) / range * plotWidth) + xPlotLeft;
					float yBarTop = ((float) bins[datasetN][binN] - minYfreq) / yFreqRange * plotHeight + yPlotBottom;
					float halfBarWidth = plotWidth / binCount / 2f;
					
					OpenGL.drawQuad2D(gl, datasets.get(datasetN).glColor, xBarCenter - halfBarWidth, yPlotBottom, xBarCenter + halfBarWidth, yBarTop);
					
				}
			
			}
		}

		// stop clipping to the plot region
		gl.glScissor(originalScissorArgs[0], originalScissorArgs[1], originalScissorArgs[2], originalScissorArgs[3]);
		
		// draw the tooltip if the mouse is in the plot region
		if(haveDatasets && SettingsController.getTooltipVisibility() && mouseX >= xPlotLeft && mouseX <= xPlotRight && mouseY >= yPlotBottom && mouseY <= yPlotTop) {
			int binN = (int) Math.floor(((float) mouseX - xPlotLeft) / plotWidth * binCount);
			if(binN > binCount - 1)
				binN = binCount - 1;
			float min = minX + (binSize *  binN);      // inclusive
			float max = minX + (binSize * (binN + 1)); // exclusive
			String[] text = new String[datasetsCount + 1];
			Color[] colors = new Color[datasetsCount + 1];
			text[0] = ChartUtils.formattedNumber(min, 5) + " to " + ChartUtils.formattedNumber(max, 5) + " " + datasets.get(0).unit;
			colors[0] = new Color(Theme.tooltipBackgroundColor[0], Theme.tooltipBackgroundColor[1], Theme.tooltipBackgroundColor[2], Theme.tooltipBackgroundColor[3]);
			for(int datasetN = 0; datasetN < datasetsCount; datasetN++) {
				text[datasetN + 1] = bins[datasetN][binN] + " samples (" + ChartUtils.formattedNumber((double) bins[datasetN][binN] / (double) sampleCount * 100f, 4) + "%)";
				colors[datasetN + 1] = datasets.get(datasetN).color;
			}
			float xBarCenter = ((binSize *  binN) + (binSize * (binN + 1))) / 2f / range * plotWidth + xPlotLeft;
			if(datasetsCount > 1) {
				OpenGL.buffer.rewind();
				OpenGL.buffer.put(xBarCenter); OpenGL.buffer.put(yPlotTop);
				OpenGL.buffer.put(xBarCenter); OpenGL.buffer.put(yPlotBottom);
				OpenGL.buffer.rewind();
				OpenGL.drawLinesXy(gl, GL3.GL_LINES, Theme.tooltipVerticalBarColor, OpenGL.buffer, 2);
				ChartUtils.drawTooltip(gl, text, colors, (int) xBarCenter, mouseY, xPlotLeft, yPlotTop, xPlotRight, yPlotBottom);
			} else {
				int anchorY = (int) (((float) bins[0][binN] - minYfreq) / yFreqRange * plotHeight + yPlotBottom);
				ChartUtils.drawTooltip(gl, text, colors, (int) xBarCenter, anchorY, xPlotLeft, yPlotTop, xPlotRight, yPlotBottom);				
			}
		}
		
		// draw the plot border
		OpenGL.drawQuadOutline2D(gl, Theme.plotOutlineColor, xPlotLeft, yPlotBottom, xPlotRight, yPlotTop);
		
		return handler;
		
	}

}
