import java.awt.Color;
import java.util.Map;

import com.jogamp.opengl.GL2ES3;
import com.jogamp.opengl.GL3;

/**
 * Renders a frequency domain chart, using one of three possible visualizations: Live View, Waveform View, or Waterfall View.
 * 
 * User settings:
 *     Datasets to visualize.
 *     Sample count per DFT.
 *     Power minimum value can be fixed or autoscaled.
 *     Power maximum value can be fixed or autoscaled.
 *     Chart mode:
 *         "Single" renders a single DFT of the most recent samples. This is a line chart.
 *         "Multiple" renders a sequence of DFTs as a 2D histogram. This is basically a "long exposure photo" of Single.
 *         "Waterfall" renders a sequence of DFTs as a sequence of rows. Each row is one DFT, allowing you to see how the DFTs have changed over time.
 *     DFT count for the Multiple and Waterfall modes.
 *     Vertical resolution for Multiple mode.
 *     X-axis title can be displayed.
 *     X-axis scale can be displayed.
 *     Y-axis title can be displayed.
 *     Y-axis scale can be displayed.
 *     Legend can be displayed.
 *     DFT information (window length and type, window count, and waterfall power range) can be displayed.
 */
public class OpenGLFrequencyDomainChart extends PositionedChart {
	
	String chartMode; // "Single" or "Multiple" or "Waterfall"
	boolean singleMode;
	boolean multipleMode;
	boolean waterfallMode;
	int dftCount;
	int waveformRowCount;
	OpenGLFrequencyDomainCache cache;
	
	// plot region
	float xPlotLeft;
	float xPlotRight;
	float plotWidth;
	float yPlotTop;
	float yPlotBottom;
	float plotHeight;
	
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
	
	// dft info
	boolean showDftInfo;
	String dftWindowLengthText;
	float yDftWindowLengthTextBaseline;
	float xDftWindowLenghtTextLeft;
	String dftWindowCountText;
	float yDftWindowCountTextBaseline;
	float xDftWindowCountTextLeft;
	String minPowerText;
	String maxPowerText;
	float yPowerTextBaseline;
	float yPowerTextTop;
	float xMaxPowerTextLeft;
	float xPowerScaleRight;
	float xPowerScaleLeft;
	float xMinPowerTextLeft;
	float xDftInfoTextLeft;
	
	// y-axis title
	boolean showYaxisTitle;
	float xYaxisTitleTextTop;
	float xYaxisTitleTextBaseline;
	String yAxisTitle;
	float yYaxisTitleTextLeft;
	
	// x-axis title
	boolean showXaxisTitle;
	float yXaxisTitleTextBasline;
	float yXaxisTitleTextTop;
	String xAxisTitle;
	float xXaxisTitleTextLeft;
	
	// x-axis scale
	boolean showXaxisScale;
	Map<Float, String> xDivisions;
	float yXaxisTickTextBaseline;
	float yXaxisTickTextTop;
	float yXaxisTickBottom;
	float yXaxisTickTop;
	
	// y-axis scale
	boolean showYaxisScale;
	Map<Float, String> yDivisions;
	float xYaxisTickTextRight;
	float xYaxisTickLeft;
	float xYaxisTickRight;
	AutoScale autoscalePower;
	boolean autoscaleMinPower;
	boolean autoscaleMaxPower;
	float manualMinPower;
	float manualMaxPower;
	
	// constraints
	static final float PowerMinimumDefault = 0.001f;
	static final float PowerMaximumDefault = 1.0f;
	static final float PowerLowerLimit     = Float.MIN_VALUE;
	static final float PowerUpperLimit     = Float.MAX_VALUE;
	
	// control widgets
	WidgetDatasets datasetsAndDurationWidget;
	WidgetTextfieldsOptionalMinMax minMaxWidget;
	WidgetFrequencyDomainType typeWidget;
	WidgetCheckbox showXaxisTitleWidget;
	WidgetCheckbox showXaxisScaleWidget;
	WidgetCheckbox showYaxisTitleWidget;
	WidgetCheckbox showYaxisScaleWidget;
	WidgetCheckbox showLegendWidget;
	WidgetCheckbox showDftInfoWidget;
	
	@Override public String toString() {
		
		return "Frequency Domain";
		
	}
	
	public OpenGLFrequencyDomainChart(int x1, int y1, int x2, int y2) {
		
		super(x1, y1, x2, y2);
		
		autoscalePower = new AutoScale(AutoScale.MODE_EXPONENTIAL, 90, 0.20f);
		
		// create the control widgets and event handlers
		datasetsAndDurationWidget = new WidgetDatasets(newDatasets -> datasets.setNormals(newDatasets),
		                                               null,
		                                               null,
		                                               (newDurationType, newDuration) -> {
		                                                   duration = (int) (long) newDuration;
		                                                   if(duration > 5_000)
		                                                	   duration = 5_000;
		                                                   if(duration < 10)
		                                                	   duration = 10;
		                                                   return (long) duration;
		                                               },
		                                               false,
		                                               null);
		
		minMaxWidget = new WidgetTextfieldsOptionalMinMax("Power",
		                                                  true,
		                                                  PowerMinimumDefault,
		                                                  PowerMaximumDefault,
		                                                  PowerLowerLimit,
		                                                  PowerUpperLimit,
		                                                  (newAutoscaleMinPower, newManualMinPower) -> { autoscaleMinPower = newAutoscaleMinPower; manualMinPower = newManualMinPower; },
		                                                  (newAutoscaleMaxPower, newManualMaxPower) -> { autoscaleMaxPower = newAutoscaleMaxPower; manualMaxPower = newManualMaxPower; });
		
		typeWidget = new WidgetFrequencyDomainType(newChartMode -> {
		                                               chartMode = newChartMode;
		                                               singleMode    = chartMode.equals("Single");
		                                               multipleMode  = chartMode.equals("Multiple");
		                                               waterfallMode = chartMode.equals("Waterfall");
		                                           },
		                                           newDftCount -> dftCount = newDftCount,
		                                           newWaveformRowCount -> waveformRowCount = newWaveformRowCount);
		
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
		
		showDftInfoWidget = new WidgetCheckbox("Show DFT Info",
		                                       true,
		                                       newShowDftInfo -> showDftInfo = newShowDftInfo);

		widgets = new Widget[15];
		
		widgets[0]  = datasetsAndDurationWidget;
		widgets[1]  = null;
		widgets[2]  = minMaxWidget;
		widgets[3]  = null;
		widgets[4]  = typeWidget;
		widgets[5]  = null;
		widgets[6]  = showXaxisTitleWidget;
		widgets[7]  = showXaxisScaleWidget;
		widgets[8]  = null;
		widgets[9]  = showYaxisTitleWidget;
		widgets[10] = showYaxisScaleWidget;
		widgets[11] = null;
		widgets[12] = showLegendWidget;
		widgets[13] = null;
		widgets[14] = showDftInfoWidget;
		
	}
	
	@Override public EventHandler drawChart(GL2ES3 gl, float[] chartMatrix, int width, int height, long endTimestamp, int endSampleNumber, double zoomLevel, int mouseX, int mouseY) {
		
		EventHandler handler = null;
		
		boolean haveDatasets = datasets.hasNormals();
		int datasetsCount = datasets.normalsCount();
		boolean haveTelemetry = haveDatasets && endSampleNumber > 5;
		
		// calculate the DFTs
		if(cache == null)
			cache = new OpenGLFrequencyDomainCache();
		cache.calculateDfts(endSampleNumber, duration, dftCount, datasets, chartMode);
		
		// calculate the domain
		float plotMinX = cache.getMinHz();
		float plotMaxX = cache.getMaxHz();
		float domain = plotMaxX - plotMinX;
		
		// calculate the range and ensure it's >0
		// for "Waterfall View" the y-axis is time
		// for "Live View" and "Waveform View" the y-axis is power
		float sampleRate = haveDatasets ? datasets.connection.sampleRate : 1;
		float plotMinTime = 0;
		float plotMaxTime = (float) (duration * dftCount) / sampleRate;

		float plotMinPower = haveTelemetry ? cache.getMinPower() : -12;
		float plotMaxPower = haveTelemetry ? cache.getMaxPower() : 1;
		if(plotMinPower == plotMaxPower) {
			float value = plotMinPower;
			plotMinPower = value - 0.001f;
			plotMaxPower = value + 0.001f;
		}
		autoscalePower.update(plotMinPower, plotMaxPower);
		
		if(!autoscaleMinPower)
			plotMinPower = (float) Math.log10(manualMinPower);
		else if(autoscaleMinPower && !waterfallMode)
			plotMinPower = autoscalePower.getMin();
		
		if(!autoscaleMaxPower)
			plotMaxPower = (float) Math.log10(manualMaxPower);
		else if(autoscaleMaxPower && !waterfallMode)
			plotMaxPower = autoscalePower.getMax();

		float plotMinY = waterfallMode ? plotMinTime : plotMinPower;
		float plotMaxY = waterfallMode ? plotMaxTime : plotMaxPower;
		float plotRange = plotMaxY - plotMinY;
		
		// calculate x and y positions of everything
		xPlotLeft = Theme.tilePadding;
		xPlotRight = width - Theme.tilePadding;
		plotWidth = xPlotRight - xPlotLeft;
		yPlotTop = height - Theme.tilePadding;
		yPlotBottom = Theme.tilePadding;
		plotHeight = yPlotTop - yPlotBottom;
		
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
				xOffset += OpenGL.mediumTextWidth(gl, datasets.getNormal(i).name) + Theme.legendNamesPadding;
				
				legendMouseoverCoordinates[i][2] = xOffset - Theme.legendNamesPadding + Theme.legendTextPadding;
				legendMouseoverCoordinates[i][3] = yLegendBorderTop;
			}
			
			xLegendBorderRight = xOffset - Theme.legendNamesPadding + Theme.legendTextPadding + (Theme.lineWidth / 2);

			float temp = yLegendBorderTop + Theme.legendTextPadding;
			if(yPlotBottom < temp) {
				yPlotBottom = temp;
				plotHeight = yPlotTop - yPlotBottom;
			}
		}
		
		if(showDftInfo) {
			if(singleMode) {
				
				dftWindowLengthText = cache.getWindowLength() + " sample rectangular window";
				yDftWindowLengthTextBaseline = Theme.tilePadding;
				xDftWindowLenghtTextLeft = width - Theme.tilePadding - OpenGL.smallTextWidth(gl, dftWindowLengthText);
				
				xDftInfoTextLeft = xDftWindowLenghtTextLeft;
				
				float temp = yDftWindowLengthTextBaseline + OpenGL.smallTextHeight + Theme.tickTextPadding;
				if(yPlotBottom < temp) {
					yPlotBottom = temp;
					plotHeight = yPlotTop - yPlotBottom;
				}
				
			} else if(multipleMode) {
				
				int windowCount = cache.getActualWindowCount();
				int windowLength = cache.getWindowLength();
				int trueTotalSampleCount = windowCount * windowLength;
				dftWindowCountText = windowCount + " windows (total of " + trueTotalSampleCount + " samples)";
				yDftWindowCountTextBaseline = Theme.tilePadding;
				xDftWindowCountTextLeft = width - Theme.tilePadding - OpenGL.smallTextWidth(gl, dftWindowCountText);
				
				dftWindowLengthText = windowLength + " sample rectangular window";
				yDftWindowLengthTextBaseline = yDftWindowCountTextBaseline + OpenGL.smallTextHeight + Theme.tickTextPadding;
				xDftWindowLenghtTextLeft = width - Theme.tilePadding - OpenGL.smallTextWidth(gl, dftWindowLengthText);
				
				xDftInfoTextLeft = Float.min(xDftWindowCountTextLeft, xDftWindowLenghtTextLeft);
				
				float temp = yDftWindowLengthTextBaseline + OpenGL.smallTextHeight + Theme.tickTextPadding;
				if(yPlotBottom < temp) {
					yPlotBottom = temp;
					plotHeight = yPlotTop - yPlotBottom;
				}
				
			} else if(waterfallMode) {
				
				minPowerText = "Power Range: 1e" + Math.round(plotMinPower);
				maxPowerText = "1e" + Math.round(plotMaxPower);
				yPowerTextBaseline = Theme.tilePadding;
				yPowerTextTop = yPowerTextBaseline + OpenGL.smallTextHeight;
				xMaxPowerTextLeft = width - Theme.tilePadding - OpenGL.smallTextWidth(gl, maxPowerText);
				xPowerScaleRight = xMaxPowerTextLeft - Theme.tickTextPadding;
				xPowerScaleLeft = xPowerScaleRight - (100 * ChartsController.getDisplayScalingFactor());
				xMinPowerTextLeft = xPowerScaleLeft - Theme.tickTextPadding - OpenGL.smallTextWidth(gl, minPowerText);
				
				int windowCount = cache.getActualWindowCount();
				int windowLength = cache.getWindowLength();
				int trueTotalSampleCount = windowCount * windowLength;
				dftWindowCountText = windowCount + " windows (total of " + trueTotalSampleCount + " samples)";
				yDftWindowCountTextBaseline = yPowerTextTop + Theme.tickTextPadding;
				xDftWindowCountTextLeft = width - Theme.tilePadding - OpenGL.smallTextWidth(gl, dftWindowCountText);
				
				dftWindowLengthText = windowLength + " sample rectangular window";
				yDftWindowLengthTextBaseline = yDftWindowCountTextBaseline + OpenGL.smallTextHeight + Theme.tickTextPadding;
				xDftWindowLenghtTextLeft = width - Theme.tilePadding - OpenGL.smallTextWidth(gl, dftWindowLengthText);
				
				xDftInfoTextLeft = Float.min(xDftWindowCountTextLeft, xDftWindowLenghtTextLeft);
				xDftInfoTextLeft = Float.min(xMinPowerTextLeft, xDftInfoTextLeft);
				
				float temp = yDftWindowLengthTextBaseline + OpenGL.smallTextHeight + Theme.tickTextPadding;
				if(yPlotBottom < temp) {
					yPlotBottom = temp;
					plotHeight = yPlotTop - yPlotBottom;
				}
				
			}
		}
		
		if(showYaxisTitle) {
			xYaxisTitleTextTop = xPlotLeft;
			xYaxisTitleTextBaseline = xYaxisTitleTextTop + OpenGL.largeTextHeight;
			yAxisTitle = waterfallMode ? "Time (Seconds)" : "Power (Watts)";
			yYaxisTitleTextLeft = yPlotBottom + (plotHeight / 2.0f) - (OpenGL.largeTextWidth(gl, yAxisTitle) / 2.0f);
			
			xPlotLeft = xYaxisTitleTextBaseline + Theme.tickTextPadding;
			plotWidth = xPlotRight - xPlotLeft;
		}
		
		if(showXaxisTitle) {
			yXaxisTitleTextBasline = Theme.tilePadding;
			yXaxisTitleTextTop = yXaxisTitleTextBasline + OpenGL.largeTextHeight;
			xAxisTitle = "Frequency (Hertz)";
			
			if(!showLegend && !showDftInfo)
				xXaxisTitleTextLeft = xPlotLeft + (plotWidth / 2.0f) - (OpenGL.largeTextWidth(gl, xAxisTitle)  / 2.0f);
			else if(showLegend && showDftInfo)
				xXaxisTitleTextLeft = xLegendBorderRight + ((xDftInfoTextLeft - xLegendBorderRight) / 2.0f) - (OpenGL.largeTextWidth(gl, xAxisTitle)  / 2.0f);
			else if(showLegend)
				xXaxisTitleTextLeft = xLegendBorderRight + ((width - Theme.tilePadding - xLegendBorderRight)  / 2.0f) - (OpenGL.largeTextWidth(gl, xAxisTitle)  / 2.0f);
			else if(showDftInfo)
				xXaxisTitleTextLeft = xPlotLeft + ((xDftInfoTextLeft - xPlotLeft) / 2.0f) - (OpenGL.largeTextWidth(gl, xAxisTitle)  / 2.0f);
			
			float temp = yXaxisTitleTextTop + Theme.tickTextPadding;
			if(yPlotBottom < temp) {
				yPlotBottom = temp;
				plotHeight = yPlotTop - yPlotBottom;
				if(showYaxisTitle)
					yYaxisTitleTextLeft = yPlotBottom + (plotHeight / 2.0f) - (OpenGL.largeTextWidth(gl, yAxisTitle) / 2.0f);
			}
		}
		
		if(showXaxisScale) {
			yXaxisTickTextBaseline = yPlotBottom;
			yXaxisTickTextTop = yXaxisTickTextBaseline + OpenGL.smallTextHeight;
			yXaxisTickBottom = yXaxisTickTextTop + Theme.tickTextPadding;
			yXaxisTickTop = yXaxisTickBottom + Theme.tickLength;
			
			yPlotBottom = yXaxisTickTop;
			plotHeight = yPlotTop - yPlotBottom;
			if(showYaxisTitle)
				yYaxisTitleTextLeft = yPlotBottom + (plotHeight / 2.0f) - (OpenGL.largeTextWidth(gl, yAxisTitle) / 2.0f);
		}
		
		if(showYaxisScale) {
			yDivisions = waterfallMode ? ChartUtils.getYdivisions125(plotHeight, plotMinY, plotMaxY) : ChartUtils.getLogYdivisions(plotHeight, plotMinY, plotMaxY);
			float maxTextWidth = 0;
			for(String text : yDivisions.values()) {
				float textWidth = OpenGL.smallTextWidth(gl, text);
				if(textWidth > maxTextWidth)
					maxTextWidth = textWidth;
			}
			
			xYaxisTickTextRight = xPlotLeft + maxTextWidth;
			xYaxisTickLeft = xYaxisTickTextRight + Theme.tickTextPadding;
			xYaxisTickRight = xYaxisTickLeft + Theme.tickLength;
			
			xPlotLeft = xYaxisTickRight;
			plotWidth = xPlotRight - xPlotLeft;
			
			if(showXaxisTitle && !showLegend && !showDftInfo)
				xXaxisTitleTextLeft = xPlotLeft + (plotWidth / 2.0f) - (OpenGL.largeTextWidth(gl, xAxisTitle)  / 2.0f);
			else if(showXaxisTitle && showLegend && showDftInfo)
				xXaxisTitleTextLeft = xLegendBorderRight + ((xDftInfoTextLeft - xLegendBorderRight) / 2.0f) - (OpenGL.largeTextWidth(gl, xAxisTitle)  / 2.0f);
			else if(showXaxisTitle && showLegend)
				xXaxisTitleTextLeft = xLegendBorderRight + ((width - Theme.tilePadding - xLegendBorderRight)  / 2.0f) - (OpenGL.largeTextWidth(gl, xAxisTitle)  / 2.0f);
			else if(showXaxisTitle && showDftInfo)
				xXaxisTitleTextLeft = xPlotLeft + ((xDftInfoTextLeft - xPlotLeft) / 2.0f) - (OpenGL.largeTextWidth(gl, xAxisTitle)  / 2.0f);
		}
		
		// get the x divisions now that we know the final plot width
		xDivisions = ChartUtils.getFloatXdivisions125(gl, plotWidth, plotMinX, plotMaxX);
		
		// stop if the plot is too small
		if(plotWidth < 1 || plotHeight < 1)
			return handler;
		
		// draw plot background
		OpenGL.drawQuad2D(gl, Theme.plotBackgroundColor, xPlotLeft, yPlotBottom, xPlotRight, yPlotTop);
		
		// draw the x-axis scale
		if(showXaxisScale) {
			OpenGL.buffer.rewind();
			for(Float xValue : xDivisions.keySet()) {
				float x = (xValue - plotMinX) / domain * plotWidth + xPlotLeft;
				OpenGL.buffer.put(x); OpenGL.buffer.put(yPlotTop);    OpenGL.buffer.put(Theme.divisionLinesColor);
				OpenGL.buffer.put(x); OpenGL.buffer.put(yPlotBottom); OpenGL.buffer.put(Theme.divisionLinesColor);
				
				OpenGL.buffer.put(x); OpenGL.buffer.put(yXaxisTickTop);    OpenGL.buffer.put(Theme.tickLinesColor);
				OpenGL.buffer.put(x); OpenGL.buffer.put(yXaxisTickBottom); OpenGL.buffer.put(Theme.tickLinesColor);
			}
			OpenGL.buffer.rewind();
			int vertexCount = xDivisions.keySet().size() * 4;
			OpenGL.drawLinesXyrgba(gl, GL3.GL_LINES, OpenGL.buffer, vertexCount);
			
			for(Map.Entry<Float,String> entry : xDivisions.entrySet()) {
				float x = (entry.getKey() - plotMinX) / domain * plotWidth + xPlotLeft - (OpenGL.smallTextWidth(gl, entry.getValue()) / 2.0f);
				float y = yXaxisTickTextBaseline;
				OpenGL.drawSmallText(gl, entry.getValue(), (int) x, (int) y, 0);
			}
		}
		
		// draw the y-axis scale
		if(showYaxisScale) {
			OpenGL.buffer.rewind();
			for(Float entry : yDivisions.keySet()) {
				float y = (entry - plotMinY) / plotRange * plotHeight + yPlotBottom;
				OpenGL.buffer.put(xPlotLeft);  OpenGL.buffer.put(y); OpenGL.buffer.put(Theme.divisionLinesColor);
				OpenGL.buffer.put(xPlotRight); OpenGL.buffer.put(y); OpenGL.buffer.put(Theme.divisionLinesColor);
				
				OpenGL.buffer.put(xYaxisTickLeft);  OpenGL.buffer.put(y); OpenGL.buffer.put(Theme.tickLinesColor);
				OpenGL.buffer.put(xYaxisTickRight); OpenGL.buffer.put(y); OpenGL.buffer.put(Theme.tickLinesColor);
			}
			OpenGL.buffer.rewind();
			int vertexCount = yDivisions.keySet().size() * 4;
			OpenGL.drawLinesXyrgba(gl, GL3.GL_LINES, OpenGL.buffer, vertexCount);
			
			for(Map.Entry<Float,String> entry : yDivisions.entrySet()) {
				float x = xYaxisTickTextRight - OpenGL.smallTextWidth(gl, entry.getValue());
				float y = (entry.getKey() - plotMinY) / plotRange * plotHeight + yPlotBottom - (OpenGL.smallTextHeight / 2.0f);
				OpenGL.drawSmallText(gl, entry.getValue(), (int) x, (int) y, 0);
			}
		}
		
		// draw the legend, if space is available
		if(showLegend && haveDatasets && xLegendBorderRight < width - Theme.tilePadding) {
			OpenGL.drawQuad2D(gl, Theme.legendBackgroundColor, xLegendBorderLeft, yLegendBorderBottom, xLegendBorderRight, yLegendBorderTop);
			
			for(int i = 0; i < datasetsCount; i++) {
				Dataset dataset = datasets.getNormal(i);
				if(mouseX >= legendMouseoverCoordinates[i][0] && mouseX <= legendMouseoverCoordinates[i][2] && mouseY >= legendMouseoverCoordinates[i][1] && mouseY <= legendMouseoverCoordinates[i][3]) {
					OpenGL.drawQuadOutline2D(gl, Theme.tickLinesColor, legendMouseoverCoordinates[i][0], legendMouseoverCoordinates[i][1], legendMouseoverCoordinates[i][2], legendMouseoverCoordinates[i][3]);
					handler = EventHandler.onPress(event -> ConfigureView.instance.forDataset(dataset));
				}
				OpenGL.drawQuad2D(gl, dataset.glColor, legendBoxCoordinates[i][0], legendBoxCoordinates[i][1], legendBoxCoordinates[i][2], legendBoxCoordinates[i][3]);
				OpenGL.drawMediumText(gl, dataset.name, (int) xLegendNameLeft[i], (int) yLegendTextBaseline, 0);
			}
		}
		
		// draw the DFT info text if space is available
		boolean spaceForDftInfoText = showLegend ? xDftInfoTextLeft > xLegendBorderRight + Theme.legendTextPadding : xDftInfoTextLeft > 0;
		if(showDftInfo && spaceForDftInfoText && haveDatasets) {
			if(singleMode) {
				
				OpenGL.drawSmallText(gl, dftWindowLengthText, (int) xDftWindowLenghtTextLeft, (int) yDftWindowLengthTextBaseline, 0);
				
			} else if(multipleMode) {
				
				OpenGL.drawSmallText(gl, dftWindowLengthText, (int) xDftWindowLenghtTextLeft, (int) yDftWindowLengthTextBaseline, 0);
				OpenGL.drawSmallText(gl, dftWindowCountText, (int) xDftWindowCountTextLeft, (int) yDftWindowCountTextBaseline, 0);
				
			} else if(waterfallMode) {
				
				OpenGL.drawSmallText(gl, dftWindowLengthText, (int) xDftWindowLenghtTextLeft, (int) yDftWindowLengthTextBaseline, 0);
				OpenGL.drawSmallText(gl, dftWindowCountText, (int) xDftWindowCountTextLeft, (int) yDftWindowCountTextBaseline, 0);
				OpenGL.drawSmallText(gl, minPowerText, (int) xMinPowerTextLeft, (int) yPowerTextBaseline, 0);
				OpenGL.drawSmallText(gl, maxPowerText, (int) xMaxPowerTextLeft, (int) yPowerTextBaseline, 0);
				
				OpenGL.drawQuad2D(gl, Theme.plotBackgroundColor, xPowerScaleLeft, yPowerTextBaseline, xPowerScaleRight, yPowerTextTop);
				
				for(int datasetN = 0; datasetN < datasetsCount; datasetN++) {
					Dataset dataset = datasets.getNormal(datasetN);
					float top = yPowerTextTop - (yPowerTextTop - yPowerTextBaseline) * datasetN / datasetsCount;
					float bottom = top - (yPowerTextTop - yPowerTextBaseline) / datasetsCount;
					float r = dataset.glColor[0];
					float g = dataset.glColor[1];
					float b = dataset.glColor[2];
					OpenGL.buffer.rewind();
					OpenGL.buffer.put(xPowerScaleLeft);  OpenGL.buffer.put(top);    OpenGL.buffer.put(r); OpenGL.buffer.put(g); OpenGL.buffer.put(b); OpenGL.buffer.put(0);
					OpenGL.buffer.put(xPowerScaleLeft);  OpenGL.buffer.put(bottom); OpenGL.buffer.put(r); OpenGL.buffer.put(g); OpenGL.buffer.put(b); OpenGL.buffer.put(0);
					OpenGL.buffer.put(xPowerScaleRight); OpenGL.buffer.put(top);    OpenGL.buffer.put(r); OpenGL.buffer.put(g); OpenGL.buffer.put(b); OpenGL.buffer.put(1);
					OpenGL.buffer.put(xPowerScaleRight); OpenGL.buffer.put(bottom); OpenGL.buffer.put(r); OpenGL.buffer.put(g); OpenGL.buffer.put(b); OpenGL.buffer.put(1);
					OpenGL.buffer.rewind();
					OpenGL.drawTrianglesXYRGBA(gl, GL3.GL_TRIANGLE_STRIP, OpenGL.buffer, 4);
				}
				
				OpenGL.drawQuadOutline2D(gl, Theme.legendBackgroundColor, xPowerScaleLeft, yPowerTextBaseline, xPowerScaleRight, yPowerTextTop);
				
			}
		}
		
		// draw the x-axis title if space is available
		if(showXaxisTitle)
			if((!showLegend && xXaxisTitleTextLeft > xPlotLeft) || (showLegend && xXaxisTitleTextLeft > xLegendBorderRight + Theme.legendTextPadding))
				OpenGL.drawLargeText(gl, xAxisTitle, (int) xXaxisTitleTextLeft, (int) yXaxisTitleTextBasline, 0);
		
		// draw the y-axis title if space is available
		if(showYaxisTitle && yYaxisTitleTextLeft > yPlotBottom)
			OpenGL.drawLargeText(gl, yAxisTitle, (int) xYaxisTitleTextBaseline, (int) yYaxisTitleTextLeft, 90);
		
		
		// draw the DFTs
		if(haveTelemetry) {
			if(singleMode)
				cache.renderSingle(chartMatrix, (int) xPlotLeft, (int) yPlotBottom, (int) plotWidth, (int) plotHeight, plotMinPower, plotMaxPower, gl, datasets.normalDatasets);
			else if(multipleMode)
				cache.renderMultiple(chartMatrix, (int) xPlotLeft, (int) yPlotBottom, (int) plotWidth, (int) plotHeight, plotMinPower, plotMaxPower, gl, datasets.normalDatasets, waveformRowCount);
			else if(waterfallMode)
				cache.renderWaterfall(chartMatrix, (int) xPlotLeft, (int) yPlotBottom, (int) plotWidth, (int) plotHeight, plotMinPower, plotMaxPower, gl, datasets.normalDatasets);
		}
		
		// draw the tooltip if the mouse is in the plot region
		if(haveDatasets && SettingsController.getTooltipVisibility() && mouseX >= xPlotLeft && mouseX <= xPlotRight && mouseY >= yPlotBottom && mouseY <= yPlotTop) {
			// map mouseX to a frequency
			double binSizeHz = cache.getBinSizeHz();
			int binCount = cache.getBinCount();
			int binN = (int) (((float) mouseX - xPlotLeft) / plotWidth * (binCount - 1) + 0.5f);
			if(binN > binCount - 1)
				binN = binCount - 1;
			float frequency = (float) (binN * binSizeHz);
			float anchorX = (frequency - plotMinX) / domain * plotWidth + xPlotLeft;
			
			String[] text = null;
			Color[] colors = null;
			int anchorY = 0;
			
			if(singleMode) {
				// for live view, get the power levels (one per dataset) for the mouseX frequency
				float[] binValues = cache.getPowerLevelsForLiveViewBin(binN);
				if(binValues != null) {
					text = new String[datasetsCount + 1];
					colors = new Color[datasetsCount + 1];
					text[0] = (int) frequency + " Hz";
					colors[0] = new Color(Theme.tooltipBackgroundColor[0], Theme.tooltipBackgroundColor[1], Theme.tooltipBackgroundColor[2], Theme.tooltipBackgroundColor[3]);
					for(int i = 0; i < datasetsCount; i++) {
						text[i + 1] = "1e" + ChartUtils.formattedNumber(binValues[i], 4) + " Watts";
						colors[i + 1] = datasets.getNormal(i).color;
					}
					anchorY = (int) ((binValues[0] - plotMinY) / plotRange * plotHeight + yPlotBottom);
				}
			} else if(multipleMode) {
				// map mouseY to a power bin
				int powerBinN = Math.round(((float) mouseY - yPlotBottom) / plotHeight * waveformRowCount - 0.5f);
				if(powerBinN > waveformRowCount - 1)
					powerBinN = waveformRowCount - 1;
				float minPower = (float) powerBinN / (float) waveformRowCount * (plotMaxPower - plotMinPower) + plotMinPower;
				float maxPower = (float) (powerBinN + 1) / (float) waveformRowCount * (plotMaxPower - plotMinPower) + plotMinPower;
				// for waveform view, get the percentages (one per dataset) for the mouseX frequency and mouseY power range
				int[] waveformCounts = cache.getWaveformCountsForBin(binN, powerBinN);
				if(waveformCounts != null) {
					int windowCount = cache.getActualWindowCount();
					text = new String[datasetsCount + 1];
					colors = new Color[datasetsCount + 1];
					text[0] = (int) frequency + " Hz, 1e" + ChartUtils.formattedNumber(minPower, 4) + " to 1e" + ChartUtils.formattedNumber(maxPower, 4) + " Watts";
					colors[0] = new Color(Theme.tooltipBackgroundColor[0], Theme.tooltipBackgroundColor[1], Theme.tooltipBackgroundColor[2], Theme.tooltipBackgroundColor[3]);
					for(int i = 0; i < datasetsCount; i++) {
						text[i + 1] = waveformCounts[i] + " of " + windowCount + " DFTs (" + ChartUtils.formattedNumber((double) waveformCounts[i] / (double) windowCount * 100.0, 4) + "%)";
						colors[i + 1] = datasets.getNormal(i).color;
					}
					anchorY = (int) (((float) powerBinN + 0.5f) / (float) waveformRowCount * plotHeight + yPlotBottom);
				}
			} else if(waterfallMode) {
				// map mouseY to a time
				int waterfallRowCount = dftCount;
				int waterfallRowN = Math.round(((float) mouseY - yPlotBottom) / plotHeight * waterfallRowCount - 0.5f);
				if(waterfallRowN > waterfallRowCount - 1)
					waterfallRowN = waterfallRowCount - 1;
				int windowLength = cache.getWindowLength();
				int trueLastSampleNumber = endSampleNumber - (endSampleNumber % windowLength);
				int rowLastSampleNumber = trueLastSampleNumber - (waterfallRowN * windowLength) - 1;
				int rowFirstSampleNumber = rowLastSampleNumber - windowLength + 1;
				if(rowFirstSampleNumber >= 0) {
					// for waterfall view, get the power levels (one per dataset) for the mouseX frequency and mouseY time
					float[] binValues = cache.getWaterfallPowerLevelsForBin(binN, waterfallRowN);
					if(binValues != null) {
						text = new String[datasetsCount + 2];
						colors = new Color[datasetsCount + 2];
						float secondsElapsed = ((float) waterfallRowN + 0.5f) / (float) waterfallRowCount * plotMaxTime;
						text[0] = (int) frequency + " Hz, " + ChartUtils.formattedNumber(secondsElapsed, 4) + " Seconds Ago";
						colors[0] = new Color(Theme.tooltipBackgroundColor[0], Theme.tooltipBackgroundColor[1], Theme.tooltipBackgroundColor[2], Theme.tooltipBackgroundColor[3]);
						text[1] = "(Samples " + rowFirstSampleNumber + " to " + rowLastSampleNumber + ")";
						colors[1] = new Color(Theme.tooltipBackgroundColor[0], Theme.tooltipBackgroundColor[1], Theme.tooltipBackgroundColor[2], Theme.tooltipBackgroundColor[3]);
						for(int i = 0; i < datasetsCount; i++) {
							text[i + 2] = "1e" + ChartUtils.formattedNumber(binValues[i], 4) + " Watts";
							colors[i + 2] = datasets.getNormal(i).color;
						}
						anchorY = (int) (((float) waterfallRowN + 0.5f) / (float) waterfallRowCount * plotHeight + yPlotBottom);
					}
				}
			}

			if(text != null && colors != null) {
				if(datasetsCount > 1 && singleMode) {
					OpenGL.buffer.rewind();
					OpenGL.buffer.put(anchorX); OpenGL.buffer.put(yPlotTop);
					OpenGL.buffer.put(anchorX); OpenGL.buffer.put(yPlotBottom);
					OpenGL.buffer.rewind();
					OpenGL.drawLinesXy(gl, GL3.GL_LINES, Theme.tooltipVerticalBarColor, OpenGL.buffer, 2);
					ChartUtils.drawTooltip(gl, text, colors, (int) anchorX, mouseY, xPlotLeft, yPlotTop, xPlotRight, yPlotBottom);
				} else {
					ChartUtils.drawTooltip(gl, text, colors, (int) anchorX, anchorY, xPlotLeft, yPlotTop, xPlotRight, yPlotBottom);
				}
			}
		}

		// draw the plot border
		OpenGL.drawQuadOutline2D(gl, Theme.plotOutlineColor, xPlotLeft, yPlotBottom, xPlotRight, yPlotTop);
		
		return handler;
		
	}
	
	@Override public void disposeGpu(GL2ES3 gl) {
		
		super.disposeGpu(gl);
		if(cache != null)
			cache.freeResources(gl);
		cache = null;
		
	}

}
