import java.util.Map;

import javax.swing.JPanel;

import com.jogamp.opengl.GL2;

/**
 * Renders a frequency domain chart, using one of three possible visualizations: Live View, Waveform View, or Waterfall View.
 * 
 * User settings:
 *     Datasets to visualize.
 *     Power minimum value can be fixed or autoscaled.
 *     Power maximum value can be fixed or autoscaled.
 *     Chart type:
 *         Live View renders a single DFT of the most recent samples. This is a line chart.
 *         Waveform View renders a sequence of DFTs as a 2D histogram. This is basically a "long exposure photo" of Live View.
 *         Waterfall View renders a sequence of DFTs as a sequence of rows. Each row is one DFT, allowing you to see how the DFTs have changed over time.
 *     Sample count, and total sample count for Waveform and Waterfall views.
 *     Vertical resolution for Waveform View.
 *     X-axis title can be displayed.
 *     X-axis scale can be displayed.
 *     Y-axis title can be displayed.
 *     Y-axis scale can be displayed.
 *     Legend can be displayed.
 *     DFT information (window length and type, window count, and waterfall power range) can be displayed.
 */
public class OpenGLFrequencyDomainChart extends PositionedChart {
	
	String chartType; // "Live View" or "Waveform View" or "Waterfall View"
	int totalSampleCount;
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
	
	static final int SampleCountDefault      = 1000;
	static final int TotalSampleCountDefault = 100000;
	static final int SampleCountLowerLimit   = 5;
	static final int SampleCountUpperLimit   = 5000000;
	
	static final int WaveformRowCountDefault    = 60;
	static final int WaveformRowCountLowerLimit = 2;
	static final int WaveformRowCountUpperLimit = 1000;
	
	// control widgets
	WidgetDatasets datasetsWidget;
	WidgetTextfieldsOptionalMinMax minMaxWidget;
	WidgetFrequencyDomainType typeWidget;
	WidgetCheckbox showXaxisTitleWidget;
	WidgetCheckbox showXaxisScaleWidget;
	WidgetCheckbox showYaxisTitleWidget;
	WidgetCheckbox showYaxisScaleWidget;
	WidgetCheckbox showLegendWidget;
	WidgetCheckbox showDftInfoWidget;
	
	@Override public String toString() {
		
		return "Frequency Domain Chart";
		
	}
	
	@Override public String[] exportChart() {
		
		String[] lines = new String[15];

		lines[0]  = "datasets = " + exportDatasets();
		lines[1]  = "sample count = " + sampleCount;
		lines[2]  = "total sample count = " + totalSampleCount;
		lines[3]  = "autoscale minimum power = " + autoscaleMinPower;
		lines[4]  = "manual minimum power = " + manualMinPower;
		lines[5]  = "autoscale maximum power = " + autoscaleMaxPower;
		lines[6]  = "manual maximum power = " + manualMaxPower;
		lines[7]  = "type = " + chartType;
		lines[8]  = "waveform view row count = " + waveformRowCount;
		lines[9]  = "show x-axis title = " + showXaxisTitle;
		lines[10] = "show x-axis scale = " + showXaxisScale;
		lines[11] = "show y-axis title = " + showYaxisTitle;
		lines[12] = "show y-axis scale = " + showYaxisScale;
		lines[13] = "show legend = " + showLegend;
		lines[14] = "show dft info = " + showDftInfo;
		
		return lines;
		
	}
	
	@Override public void importChart(String[] lines, int firstLineNumber) {
		
		if(lines.length != 15)
			throw new AssertionError("Line " + firstLineNumber + ": Invalid Frequency Domain Chart configuration section.");

		String datasets   =  (String) ChartUtils.parse(firstLineNumber + 0,  lines[0],  "datasets = %s");
		sampleCount       =     (int) ChartUtils.parse(firstLineNumber + 1,  lines[1],  "sample count = %d");
		totalSampleCount  =     (int) ChartUtils.parse(firstLineNumber + 2,  lines[2],  "total sample count = %d");
		autoscaleMinPower = (boolean) ChartUtils.parse(firstLineNumber + 3,  lines[3],  "autoscale minimum power = %b");
		manualMinPower    =   (float) ChartUtils.parse(firstLineNumber + 4,  lines[4],  "manual minimum power = %f");
		autoscaleMaxPower = (boolean) ChartUtils.parse(firstLineNumber + 5,  lines[5],  "autoscale maximum power = %b");
		manualMaxPower    =   (float) ChartUtils.parse(firstLineNumber + 6,  lines[6],  "manual maximum power = %f");
		chartType         =  (String) ChartUtils.parse(firstLineNumber + 7,  lines[7],  "type = %s");
		waveformRowCount  =     (int) ChartUtils.parse(firstLineNumber + 8,  lines[8],  "waveform view row count = %d");
		showXaxisTitle    = (boolean) ChartUtils.parse(firstLineNumber + 9,  lines[9],  "show x-axis title = %b");
		showXaxisScale    = (boolean) ChartUtils.parse(firstLineNumber + 10, lines[10], "show x-axis scale = %b");
		showYaxisTitle    = (boolean) ChartUtils.parse(firstLineNumber + 11, lines[11], "show y-axis title = %b");
		showYaxisScale    = (boolean) ChartUtils.parse(firstLineNumber + 12, lines[12], "show y-axis scale = %b");
		showLegend        = (boolean) ChartUtils.parse(firstLineNumber + 13, lines[13], "show legend = %b");
		showDftInfo       = (boolean) ChartUtils.parse(firstLineNumber + 14, lines[14], "show dft info = %b");
		
		if(!chartType.equals("Live View") && !chartType.equals("Waveform View") && !chartType.equals("Waterfall View"))
			throw new AssertionError("Line " + (firstLineNumber + 5) + ": Invalid Frequency Domain Chart type.");
		
		importDatasets(firstLineNumber, datasets);
		
		// sync the widgets with the current chart state
		datasetsWidget.setDatasets(this.datasets);
		typeWidget.setSampleCount(sampleCount);
		typeWidget.setTotalSampleCount(totalSampleCount);
		typeWidget.setRowCount(waveformRowCount);
		typeWidget.setType(chartType);
		minMaxWidget.setMin(autoscaleMinPower, manualMinPower);
		minMaxWidget.setMax(autoscaleMaxPower, manualMaxPower);
		showXaxisTitleWidget.setChecked(showXaxisTitle);
		showXaxisScaleWidget.setChecked(showXaxisScale);
		showYaxisTitleWidget.setChecked(showYaxisTitle);
		showYaxisScaleWidget.setChecked(showYaxisScale);
		showLegendWidget.setChecked(showLegend);
		showDftInfoWidget.setChecked(showDftInfo);
		
	}
	
	@Override public JPanel[] getWidgets() {

		JPanel[] widgets = new JPanel[15];
		
		widgets[0]  = datasetsWidget;
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

		return widgets;
		
	}
	
	public OpenGLFrequencyDomainChart(int x1, int y1, int x2, int y2) {
		
		super(x1, y1, x2, y2);
		
		autoscalePower = new AutoScale(AutoScale.MODE_EXPONENTIAL, 90, 0.20f);
		
		// create the control widgets and event handlers
		datasetsWidget = new WidgetDatasets(newDatasets -> datasets = newDatasets);
		
		minMaxWidget = new WidgetTextfieldsOptionalMinMax("Power",
		                                                  PowerMinimumDefault,
		                                                  PowerMaximumDefault,
		                                                  PowerLowerLimit,
		                                                  PowerUpperLimit,
		                                                  (newAutoscaleMinPower, newManualMinPower) -> { autoscaleMinPower = newAutoscaleMinPower; manualMinPower = newManualMinPower; },
		                                                  (newAutoscaleMaxPower, newManualMaxPower) -> { autoscaleMaxPower = newAutoscaleMaxPower; manualMaxPower = newManualMaxPower; });
		
		typeWidget = new WidgetFrequencyDomainType(SampleCountDefault,
		                                           TotalSampleCountDefault,
		                                           SampleCountLowerLimit,
		                                           SampleCountUpperLimit,
		                                           WaveformRowCountDefault,
		                                           WaveformRowCountLowerLimit,
		                                           WaveformRowCountUpperLimit,
		                                           newChartType -> chartType = newChartType,
		                                           newSampleCount -> sampleCount = newSampleCount,
		                                           newTotalSampleCount -> totalSampleCount = newTotalSampleCount,
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

	}
	
	@Override public void drawChart(GL2 gl, int width, int height, int lastSampleNumber, double zoomLevel) {
		
		// scale the DFT window size by the current zoom level
		int dftWindowLength = (int) (sampleCount * zoomLevel);
		
		// only draw if we can
		if(lastSampleNumber < sampleCount)
			return; // not enough samples
		if(dftWindowLength < 5)
			return; // zoomed in too much
		
		boolean haveDatasets = datasets != null && datasets.length > 0;
		
		// calculate the DFTs
		if(cache == null)
			cache = new OpenGLFrequencyDomainCache(gl);
		if(haveDatasets)
			cache.calculateDfts(lastSampleNumber, dftWindowLength, totalSampleCount, datasets, chartType);
		
		// calculate the domain
		float plotMinX = haveDatasets ? cache.getMinHz() : 0;
		float plotMaxX = haveDatasets ? cache.getMaxHz() : 1;
		float domain = plotMaxX - plotMinX;
		
		// calculate the range and ensure it's >0
		// for "Waterfall View" the y-axis is time
		// for "Live View" and "Waveform View" the y-axis is power
		float plotMinTime = 0;
		float plotMaxTime = (float) totalSampleCount / (float) Controller.getSampleRate();

		float plotMinPower = haveDatasets ? cache.getMinPower() : -12;
		float plotMaxPower = haveDatasets ? cache.getMaxPower() : 1;
		if(plotMinPower == plotMaxPower) {
			float value = plotMinPower;
			plotMinPower = value - 0.001f;
			plotMaxPower = value + 0.001f;
		}
		autoscalePower.update(plotMinPower, plotMaxPower);
		
		if(!autoscaleMinPower)
			plotMinPower = (float) Math.log10(manualMinPower);
		else if(autoscaleMinPower && !chartType.equals("Waterfall View"))
			plotMinPower = autoscalePower.getMin();
		
		if(!autoscaleMaxPower)
			plotMaxPower = (float) Math.log10(manualMaxPower);
		else if(autoscaleMaxPower && !chartType.equals("Waterfall View"))
			plotMaxPower = autoscalePower.getMax();

		float plotMinY = chartType.equals("Waterfall View") ? plotMinTime : plotMinPower;
		float plotMaxY = chartType.equals("Waterfall View") ? plotMaxTime : plotMaxPower;
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
			yLegendTextTop = yLegendTextBaseline + FontUtils.legendTextHeight;
			yLegendBorderTop = yLegendTextTop + Theme.legendTextPadding;
			
			legendBoxCoordinates = new float[datasets.length][8];
			xLegendNameLeft = new float[datasets.length];
			
			float xOffset = xLegendBorderLeft + (Theme.lineWidth / 2) + Theme.legendTextPadding;
			
			for(int i = 0; i < datasets.length; i++){
				legendBoxCoordinates[i][0] = xOffset;
				legendBoxCoordinates[i][1] = yLegendTextBaseline;
				
				legendBoxCoordinates[i][2] = xOffset;
				legendBoxCoordinates[i][3] = yLegendTextTop;
				
				legendBoxCoordinates[i][4] = xOffset + FontUtils.legendTextHeight;
				legendBoxCoordinates[i][5] = yLegendTextTop;
				
				legendBoxCoordinates[i][6] = xOffset + FontUtils.legendTextHeight;
				legendBoxCoordinates[i][7] = yLegendTextBaseline;
				
				xOffset += FontUtils.legendTextHeight + Theme.legendTextPadding;
				xLegendNameLeft[i] = xOffset;
				xOffset += FontUtils.legendTextWidth(datasets[i].name) + Theme.legendNamesPadding;
			}
			
			xLegendBorderRight = xOffset - Theme.legendNamesPadding + Theme.legendTextPadding + (Theme.lineWidth / 2);

			float temp = yLegendBorderTop + Theme.legendTextPadding;
			if(yPlotBottom < temp) {
				yPlotBottom = temp;
				plotHeight = yPlotTop - yPlotBottom;
			}
		}
		
		if(showDftInfo) {
			if(chartType.equals("Live View")) {
				
				dftWindowLengthText = dftWindowLength + " sample rectangular window";
				yDftWindowLengthTextBaseline = Theme.tilePadding;
				xDftWindowLenghtTextLeft = width - Theme.tilePadding - FontUtils.tickTextWidth(dftWindowLengthText);
				
				xDftInfoTextLeft = xDftWindowLenghtTextLeft;
				
				float temp = yDftWindowLengthTextBaseline + FontUtils.tickTextHeight + Theme.tickTextPadding;
				if(yPlotBottom < temp) {
					yPlotBottom = temp;
					plotHeight = yPlotTop - yPlotBottom;
				}
				
			} else if(chartType.equals("Waveform View")) {
				
				int windowCount = lastSampleNumber >= totalSampleCount ? (totalSampleCount / dftWindowLength) : (lastSampleNumber / dftWindowLength);
				int trueTotalSampleCount = windowCount * dftWindowLength;
				dftWindowCountText = windowCount + " windows (total of " + trueTotalSampleCount + " samples)";
				yDftWindowCountTextBaseline = Theme.tilePadding;
				xDftWindowCountTextLeft = width - Theme.tilePadding - FontUtils.tickTextWidth(dftWindowCountText);
				
				dftWindowLengthText = dftWindowLength + " sample rectangular window";
				yDftWindowLengthTextBaseline = yDftWindowCountTextBaseline + FontUtils.tickTextHeight + Theme.tickTextPadding;
				xDftWindowLenghtTextLeft = width - Theme.tilePadding - FontUtils.tickTextWidth(dftWindowLengthText);
				
				xDftInfoTextLeft = Float.min(xDftWindowCountTextLeft, xDftWindowLenghtTextLeft);
				
				float temp = yDftWindowLengthTextBaseline + FontUtils.tickTextHeight + Theme.tickTextPadding;
				if(yPlotBottom < temp) {
					yPlotBottom = temp;
					plotHeight = yPlotTop - yPlotBottom;
				}
				
			} else if(chartType.equals("Waterfall View")) {
				
				minPowerText = "Power Range: 1e" + Math.round(plotMinPower);
				maxPowerText = "1e" + Math.round(plotMaxPower);
				yPowerTextBaseline = Theme.tilePadding;
				yPowerTextTop = yPowerTextBaseline + FontUtils.tickTextHeight;
				xMaxPowerTextLeft = width - Theme.tilePadding - FontUtils.tickTextWidth(maxPowerText);
				xPowerScaleRight = xMaxPowerTextLeft - Theme.tickTextPadding;
				xPowerScaleLeft = xPowerScaleRight - (100 * Controller.getDisplayScalingFactor());
				xMinPowerTextLeft = xPowerScaleLeft - Theme.tickTextPadding - FontUtils.tickTextWidth(minPowerText);
				
				int windowCount = lastSampleNumber >= totalSampleCount ? (totalSampleCount / dftWindowLength) : (lastSampleNumber / dftWindowLength);
				int trueTotalSampleCount = windowCount * dftWindowLength;
				dftWindowCountText = windowCount + " windows (total of " + trueTotalSampleCount + " samples)";
				yDftWindowCountTextBaseline = yPowerTextTop + Theme.tickTextPadding;
				xDftWindowCountTextLeft = width - Theme.tilePadding - FontUtils.tickTextWidth(dftWindowCountText);
				
				dftWindowLengthText = dftWindowLength + " sample rectangular window";
				yDftWindowLengthTextBaseline = yDftWindowCountTextBaseline + FontUtils.tickTextHeight + Theme.tickTextPadding;
				xDftWindowLenghtTextLeft = width - Theme.tilePadding - FontUtils.tickTextWidth(dftWindowLengthText);
				
				xDftInfoTextLeft = Float.min(xDftWindowCountTextLeft, xDftWindowLenghtTextLeft);
				xDftInfoTextLeft = Float.min(xMinPowerTextLeft, xDftInfoTextLeft);
				
				float temp = yDftWindowLengthTextBaseline + FontUtils.tickTextHeight + Theme.tickTextPadding;
				if(yPlotBottom < temp) {
					yPlotBottom = temp;
					plotHeight = yPlotTop - yPlotBottom;
				}
				
			}
		}
		
		if(showYaxisTitle) {
			xYaxisTitleTextTop = xPlotLeft;
			xYaxisTitleTextBaseline = xYaxisTitleTextTop + FontUtils.yAxisTextHeight;
			yAxisTitle = chartType.equals("Waterfall View") ? "Time (Seconds)" : "Power (Watts)";
			yYaxisTitleTextLeft = yPlotBottom + (plotHeight / 2.0f) - (FontUtils.yAxisTextWidth(yAxisTitle) / 2.0f);
			
			xPlotLeft = xYaxisTitleTextBaseline + Theme.tickTextPadding;
			plotWidth = xPlotRight - xPlotLeft;
		}
		
		if(showXaxisTitle) {
			yXaxisTitleTextBasline = Theme.tilePadding;
			yXaxisTitleTextTop = yXaxisTitleTextBasline + FontUtils.xAxisTextHeight;
			xAxisTitle = "Frequency (Hertz)";
			
			if(!showLegend && !showDftInfo)
				xXaxisTitleTextLeft = xPlotLeft + (plotWidth / 2.0f) - (FontUtils.xAxisTextWidth(xAxisTitle)  / 2.0f);
			else if(showLegend && showDftInfo)
				xXaxisTitleTextLeft = xLegendBorderRight + ((xDftInfoTextLeft - xLegendBorderRight) / 2.0f) - (FontUtils.xAxisTextWidth(xAxisTitle)  / 2.0f);
			else if(showLegend)
				xXaxisTitleTextLeft = xLegendBorderRight + ((width - Theme.tilePadding - xLegendBorderRight)  / 2.0f) - (FontUtils.xAxisTextWidth(xAxisTitle)  / 2.0f);
			else if(showDftInfo)
				xXaxisTitleTextLeft = xPlotLeft + ((xDftInfoTextLeft - xPlotLeft) / 2.0f) - (FontUtils.xAxisTextWidth(xAxisTitle)  / 2.0f);
			
			float temp = yXaxisTitleTextTop + Theme.tickTextPadding;
			if(yPlotBottom < temp) {
				yPlotBottom = temp;
				plotHeight = yPlotTop - yPlotBottom;
				if(showYaxisTitle)
					yYaxisTitleTextLeft = yPlotBottom + (plotHeight / 2.0f) - (FontUtils.yAxisTextWidth(yAxisTitle) / 2.0f);
			}
		}
		
		if(showXaxisScale) {
			yXaxisTickTextBaseline = yPlotBottom;
			yXaxisTickTextTop = yXaxisTickTextBaseline + FontUtils.tickTextHeight;
			yXaxisTickBottom = yXaxisTickTextTop + Theme.tickTextPadding;
			yXaxisTickTop = yXaxisTickBottom + Theme.tickLength;
			
			yPlotBottom = yXaxisTickTop;
			plotHeight = yPlotTop - yPlotBottom;
			if(showYaxisTitle)
				yYaxisTitleTextLeft = yPlotBottom + (plotHeight / 2.0f) - (FontUtils.yAxisTextWidth(yAxisTitle) / 2.0f);
		}
		
		if(showYaxisScale) {
			yDivisions = chartType.equals("Waterfall View") ? ChartUtils.getYdivisions125(plotHeight, plotMinY, plotMaxY) : ChartUtils.getLogYdivisions(plotHeight, plotMinY, plotMaxY);
			float maxTextWidth = 0;
			for(String text : yDivisions.values()) {
				float textWidth = FontUtils.tickTextWidth(text);
				if(textWidth > maxTextWidth)
					maxTextWidth = textWidth;
			}
			
			xYaxisTickTextRight = xPlotLeft + maxTextWidth;
			xYaxisTickLeft = xYaxisTickTextRight + Theme.tickTextPadding;
			xYaxisTickRight = xYaxisTickLeft + Theme.tickLength;
			
			xPlotLeft = xYaxisTickRight;
			plotWidth = xPlotRight - xPlotLeft;
			
			if(showXaxisTitle && !showLegend && !showDftInfo)
				xXaxisTitleTextLeft = xPlotLeft + (plotWidth / 2.0f) - (FontUtils.xAxisTextWidth(xAxisTitle)  / 2.0f);
			else if(showXaxisTitle && showLegend && showDftInfo)
				xXaxisTitleTextLeft = xLegendBorderRight + ((xDftInfoTextLeft - xLegendBorderRight) / 2.0f) - (FontUtils.xAxisTextWidth(xAxisTitle)  / 2.0f);
			else if(showXaxisTitle && showLegend)
				xXaxisTitleTextLeft = xLegendBorderRight + ((width - Theme.tilePadding - xLegendBorderRight)  / 2.0f) - (FontUtils.xAxisTextWidth(xAxisTitle)  / 2.0f);
			else if(showXaxisTitle && showDftInfo)
				xXaxisTitleTextLeft = xPlotLeft + ((xDftInfoTextLeft - xPlotLeft) / 2.0f) - (FontUtils.xAxisTextWidth(xAxisTitle)  / 2.0f);
		}
		
		// get the x divisions now that we know the final plot width
		xDivisions = ChartUtils.getFloatXdivisions125(plotWidth, plotMinX, plotMaxX);
		
		// stop if the plot is too small
		if(plotWidth < 1 || plotHeight < 1)
			return;
		
		// draw plot background
		gl.glBegin(GL2.GL_QUADS);
		gl.glColor4fv(Theme.plotBackgroundColor, 0);
			gl.glVertex2f(xPlotLeft,  yPlotTop);
			gl.glVertex2f(xPlotRight, yPlotTop);
			gl.glVertex2f(xPlotRight, yPlotBottom);
			gl.glVertex2f(xPlotLeft,  yPlotBottom);
		gl.glEnd();
		
		// draw the x-axis scale
		if(showXaxisScale) {
			gl.glBegin(GL2.GL_LINES);
			for(Float xValue : xDivisions.keySet()) {
				float x = (xValue - plotMinX) / domain * plotWidth + xPlotLeft;
				gl.glColor4fv(Theme.divisionLinesColor, 0);
				gl.glVertex2f(x, yPlotTop);
				gl.glVertex2f(x, yPlotBottom);
				gl.glColor4fv(Theme.tickLinesColor, 0);
				gl.glVertex2f(x, yXaxisTickTop);
				gl.glVertex2f(x, yXaxisTickBottom);
			}
			gl.glEnd();
			
			for(Map.Entry<Float,String> entry : xDivisions.entrySet()) {
				float x = (entry.getKey() - plotMinX) / domain * plotWidth + xPlotLeft - (FontUtils.tickTextWidth(entry.getValue()) / 2.0f);
				float y = yXaxisTickTextBaseline;
				FontUtils.drawTickText(entry.getValue(), (int) x, (int) y);
			}
		}
		
		// draw the y-axis scale
		if(showYaxisScale) {
			gl.glBegin(GL2.GL_LINES);
			for(Float entry : yDivisions.keySet()) {
				float y = (entry - plotMinY) / plotRange * plotHeight + yPlotBottom;
				gl.glColor4fv(Theme.divisionLinesColor, 0);
				gl.glVertex2f(xPlotLeft,  y);
				gl.glVertex2f(xPlotRight, y);
				gl.glColor4fv(Theme.tickLinesColor, 0);
				gl.glVertex2f(xYaxisTickLeft,  y);
				gl.glVertex2f(xYaxisTickRight, y);
			}
			gl.glEnd();
			
			for(Map.Entry<Float,String> entry : yDivisions.entrySet()) {
				float x = xYaxisTickTextRight - FontUtils.tickTextWidth(entry.getValue());
				float y = (entry.getKey() - plotMinY) / plotRange * plotHeight + yPlotBottom - (FontUtils.tickTextHeight / 2.0f);
				FontUtils.drawTickText(entry.getValue(), (int) x, (int) y);
			}
		}
		
		// draw the legend, if space is available
		if(showLegend && haveDatasets && xLegendBorderRight < width - Theme.tilePadding) {
			gl.glBegin(GL2.GL_LINE_LOOP);
			gl.glColor4fv(Theme.legendOutlineColor, 0);
				gl.glVertex2f(xLegendBorderLeft,  yLegendBorderBottom);
				gl.glVertex2f(xLegendBorderLeft,  yLegendBorderTop);
				gl.glVertex2f(xLegendBorderRight, yLegendBorderTop);
				gl.glVertex2f(xLegendBorderRight, yLegendBorderBottom);
			gl.glEnd();
			
			for(int i = 0; i < datasets.length; i++) {
				gl.glBegin(GL2.GL_QUADS);
				gl.glColor4f(datasets[i].color.getRed()/255.0f, datasets[i].color.getGreen()/255.0f, datasets[i].color.getBlue()/255.0f, 1);
					gl.glVertex2f(legendBoxCoordinates[i][0], legendBoxCoordinates[i][1]);
					gl.glVertex2f(legendBoxCoordinates[i][2], legendBoxCoordinates[i][3]);
					gl.glVertex2f(legendBoxCoordinates[i][4], legendBoxCoordinates[i][5]);
					gl.glVertex2f(legendBoxCoordinates[i][6], legendBoxCoordinates[i][7]);
				gl.glEnd();
				
				FontUtils.drawLegendText(datasets[i].name, (int) xLegendNameLeft[i], (int) yLegendTextBaseline);
			}
		}
		
		// draw the DFT info text if space is available
		boolean spaceForDftInfoText = showLegend ? xDftInfoTextLeft > xLegendBorderRight + Theme.legendTextPadding : xDftInfoTextLeft > 0;
		if(showDftInfo && spaceForDftInfoText && haveDatasets) {
			if(chartType.equals("Live View")) {
				
				FontUtils.drawTickText(dftWindowLengthText, (int) xDftWindowLenghtTextLeft, (int) yDftWindowLengthTextBaseline);
				
			} else if(chartType.equals("Waveform View")) {
				
				FontUtils.drawTickText(dftWindowLengthText, (int) xDftWindowLenghtTextLeft, (int) yDftWindowLengthTextBaseline);
				FontUtils.drawTickText(dftWindowCountText, (int) xDftWindowCountTextLeft, (int) yDftWindowCountTextBaseline);
				
			} else if(chartType.equals("Waterfall View")) {
				
				FontUtils.drawTickText(dftWindowLengthText, (int) xDftWindowLenghtTextLeft, (int) yDftWindowLengthTextBaseline);
				FontUtils.drawTickText(dftWindowCountText, (int) xDftWindowCountTextLeft, (int) yDftWindowCountTextBaseline);
				FontUtils.drawTickText(minPowerText, (int) xMinPowerTextLeft, (int) yPowerTextBaseline);
				FontUtils.drawTickText(maxPowerText, (int) xMaxPowerTextLeft, (int) yPowerTextBaseline);
				
				gl.glBegin(GL2.GL_QUADS);
				gl.glColor4fv(Theme.plotBackgroundColor, 0);
					gl.glVertex2f(xPowerScaleLeft,  yPowerTextBaseline);
					gl.glVertex2f(xPowerScaleLeft,  yPowerTextTop);
					gl.glVertex2f(xPowerScaleRight, yPowerTextTop);
					gl.glVertex2f(xPowerScaleRight, yPowerTextBaseline);
				gl.glEnd();
				
				for(int dataset = 0; dataset < datasets.length; dataset++) {
					float top = yPowerTextTop - (yPowerTextTop - yPowerTextBaseline) * dataset / datasets.length;
					float bottom = top - (yPowerTextTop - yPowerTextBaseline) / datasets.length;
					float red = datasets[dataset].color.getRed()/255.0f;
					float green = datasets[dataset].color.getGreen()/255.0f;
					float blue = datasets[dataset].color.getBlue()/255.0f;
					gl.glBegin(GL2.GL_QUADS);
						gl.glColor4f(red, green, blue, 0);
						gl.glVertex2f(xPowerScaleLeft,  bottom);
						gl.glVertex2f(xPowerScaleLeft,  top);
						gl.glColor4f(red, green, blue, 1);
						gl.glVertex2f(xPowerScaleRight, top);
						gl.glVertex2f(xPowerScaleRight, bottom);
					gl.glEnd();
				}
				
				gl.glBegin(GL2.GL_LINE_LOOP);
				gl.glColor4fv(Theme.legendOutlineColor, 0);
					gl.glVertex2f(xPowerScaleLeft,  yPowerTextBaseline);
					gl.glVertex2f(xPowerScaleLeft,  yPowerTextTop);
					gl.glVertex2f(xPowerScaleRight, yPowerTextTop);
					gl.glVertex2f(xPowerScaleRight, yPowerTextBaseline);
				gl.glEnd();
				
			}
		}
		
		// draw the x-axis title if space is available
		if(showXaxisTitle)
			if((!showLegend && xXaxisTitleTextLeft > xPlotLeft) || (showLegend && xXaxisTitleTextLeft > xLegendBorderRight + Theme.legendTextPadding))
				FontUtils.drawXaxisText(xAxisTitle, (int) xXaxisTitleTextLeft, (int) yXaxisTitleTextBasline);
		
		// draw the y-axis title if space is available
		if(showYaxisTitle && yYaxisTitleTextLeft > yPlotBottom)
			FontUtils.drawYaxisText(yAxisTitle, (int) xYaxisTitleTextBaseline, (int) yYaxisTitleTextLeft, 90);
		
		
		// draw the DFTs
		if(haveDatasets) {
			if(chartType.equals("Live View"))
				cache.renderLiveView((int) xPlotLeft, (int) yPlotBottom, (int) plotWidth, (int) plotHeight, plotMinPower, plotMaxPower, gl, datasets);
			else if(chartType.equals("Waveform View"))
				cache.renderWaveformView((int) xPlotLeft, (int) yPlotBottom, (int) plotWidth, (int) plotHeight, plotMinPower, plotMaxPower, gl, datasets, waveformRowCount);
			else if(chartType.equals("Waterfall View"))
				cache.renderWaterfallView((int) xPlotLeft, (int) yPlotBottom, (int) plotWidth, (int) plotHeight, plotMinPower, plotMaxPower, gl, datasets);
		}

		// draw the plot border
		gl.glBegin(GL2.GL_LINE_LOOP);
		gl.glColor4fv(Theme.plotOutlineColor, 0);
			gl.glVertex2f(xPlotLeft,  yPlotTop);
			gl.glVertex2f(xPlotRight, yPlotTop);
			gl.glVertex2f(xPlotRight, yPlotBottom);
			gl.glVertex2f(xPlotLeft,  yPlotBottom);
		gl.glEnd();
		
	}

}
