import java.awt.Color;
import java.util.Map;

import javax.swing.JPanel;

import com.jogamp.opengl.GL2;

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
		
		return "Histogram Chart";
		
	}
	
	@Override public String[] exportChart() {
		
		String[] lines = new String[20];
		
		lines[0]  = "datasets = " + exportDatasets();
		lines[1]  = "sample count = " + sampleCount;
		lines[2]  = "bin count = " + binCount;
		lines[3]  = "x-axis is centered = " + xAxisIsCentered;
		lines[4]  = "x-axis center value = " + xCenterValue;
		lines[5]  = "x-axis autoscale minimum = " + xAutoscaleMin;
		lines[6]  = "x-axis manual minimum = " + manualMinX;
		lines[7]  = "x-axis autoscale maximum = " + xAutoscaleMax;
		lines[8]  = "x-axis manual maximum = " + manualMaxX;
		lines[9]  = "y-axis shows relative frequency = " + yAxisShowsRelativeFrequency;
		lines[10] = "y-axis shows frequency = " + yAxisShowsFrequency;
		lines[11] = "y-axis minimum is zero = " + yMinimumIsZero;
		lines[12] = "y-axis autoscale maximum = " + yAutoscaleMax;
		lines[13] = "y-axis manual minimum = " + manualMinY;
		lines[14] = "y-axis manual maximum = " + manualMaxY;
		lines[15] = "show x-axis title = " + showXaxisTitle;
		lines[16] = "show x-axis scale = " + showXaxisScale;
		lines[17] = "show y-axis title = " + showYaxisTitle;
		lines[18] = "show y-axis scale = " + showYaxisScale;
		lines[19] = "show legend = " + showLegend;
		
		return lines;
		
	}
	
	@Override public void importChart(String[] lines, int firstLineNumber) {
		
		if(lines.length != 20)
			throw new AssertionError("Line " + firstLineNumber + ": Invalid Histogram Chart configuration section.");

		String datasets             =  (String) ChartUtils.parse(firstLineNumber +  0, lines[0],  "datasets = %s");
		sampleCount                 =     (int) ChartUtils.parse(firstLineNumber +  1, lines[1],  "sample count = %d");
		binCount                    =     (int) ChartUtils.parse(firstLineNumber +  2, lines[2],  "bin count = %d");
		xAxisIsCentered             = (boolean) ChartUtils.parse(firstLineNumber +  3, lines[3],  "x-axis is centered = %b");
		xCenterValue                =   (float) ChartUtils.parse(firstLineNumber +  4, lines[4],  "x-axis center value = %f");
		xAutoscaleMin               = (boolean) ChartUtils.parse(firstLineNumber +  5, lines[5],  "x-axis autoscale minimum = %b");
		manualMinX                  =   (float) ChartUtils.parse(firstLineNumber +  6, lines[6],  "x-axis manual minimum = %f");
		xAutoscaleMax               = (boolean) ChartUtils.parse(firstLineNumber +  7, lines[7],  "x-axis autoscale maximum = %b");
		manualMaxX                  =   (float) ChartUtils.parse(firstLineNumber +  8, lines[8],  "x-axis manual maximum = %f");
		yAxisShowsRelativeFrequency = (boolean) ChartUtils.parse(firstLineNumber +  9, lines[9],  "y-axis shows relative frequency = %b");
		yAxisShowsFrequency         = (boolean) ChartUtils.parse(firstLineNumber + 10, lines[10], "y-axis shows frequency = %b");
		yMinimumIsZero              = (boolean) ChartUtils.parse(firstLineNumber + 11, lines[11], "y-axis minimum is zero = %b");
		yAutoscaleMax               = (boolean) ChartUtils.parse(firstLineNumber + 12, lines[12], "y-axis autoscale maximum = %b");
		manualMinY                  =   (float) ChartUtils.parse(firstLineNumber + 13, lines[13], "y-axis manual minimum = %f");
		manualMaxY                  =   (float) ChartUtils.parse(firstLineNumber + 14, lines[14], "y-axis manual maximum = %f");
		showXaxisTitle              = (boolean) ChartUtils.parse(firstLineNumber + 15, lines[15], "show x-axis title = %b");
		showXaxisScale              = (boolean) ChartUtils.parse(firstLineNumber + 16, lines[16], "show x-axis scale = %b");
		showYaxisTitle              = (boolean) ChartUtils.parse(firstLineNumber + 17, lines[17], "show y-axis title = %b");
		showYaxisScale              = (boolean) ChartUtils.parse(firstLineNumber + 18, lines[18], "show y-axis scale = %b");
		showLegend                  = (boolean) ChartUtils.parse(firstLineNumber + 19, lines[19], "show legend = %b");
		
		importDatasets(firstLineNumber, datasets);
		
		// also need to update bins[][] and samples[] after setting the datasets
		int datasetCount = this.datasets.length;
		bins = new int[datasetCount][binCount];
        samples = new Samples[datasetCount];
        for(int i = 0; i < samples.length; i++)
        	samples[i] = new Samples();
        
		// sync the widgets with the current chart state
		datasetsWidget.setDatasets(this.datasets);
		datasetsWidget.sanityCheck();
		sampleCountWidget.setInteger(sampleCount);
		sampleCountWidget.sanityCheck();
		binCountWidget.setInteger(binCount);
		binCountWidget.sanityCheck();
		xAxisTypeWidget.setAxisType(xAxisIsCentered, xCenterValue);
		xAxisTypeWidget.setAxisMin(xAutoscaleMin, manualMinX);
		xAxisTypeWidget.setAxisMax(xAutoscaleMax, manualMaxX);
		xAxisTypeWidget.sanityCheck();
		yAxisTypeWidget.setAxisType(yAxisShowsRelativeFrequency, yAxisShowsFrequency);
		yAxisTypeWidget.setAxisMin(yMinimumIsZero, manualMinY);
		yAxisTypeWidget.setAxisMax(yAutoscaleMax, manualMaxY);
		yAxisTypeWidget.sanityCheck();
		showXaxisTitleWidget.setChecked(showXaxisTitle);
		showXaxisTitleWidget.sanityCheck();
		showXaxisScaleWidget.setChecked(showXaxisScale);
		showXaxisScaleWidget.sanityCheck();
		showYaxisTitleWidget.setChecked(showYaxisTitle);
		showYaxisTitleWidget.sanityCheck();
		showYaxisScaleWidget.setChecked(showYaxisScale);
		showYaxisScaleWidget.sanityCheck();
		showLegendWidget.setChecked(showLegend);
		showLegendWidget.sanityCheck();
		
	}
	
	@Override public JPanel[] getWidgets() {
		
		JPanel[] widgets = new JPanel[16];
		
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

		return widgets;
		
	}
	
	public OpenGLHistogramChart(int x1, int y1, int x2, int y2) {
		
		super(x1, y1, x2, y2);

		yAutoscaleRelativeFrequency = new AutoScale(AutoScale.MODE_EXPONENTIAL, 30, 0.20f);
		yAutoscaleFrequency = new AutoScale(AutoScale.MODE_EXPONENTIAL, 30, 0.20f);
		
		// create the control widgets and event handlers
		datasetsWidget = new WidgetDatasets(newDatasets -> {datasets = newDatasets;
		                                                    bins = new int[datasets.length][binCount];
		                                                    samples = new Samples[datasets.length];
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
		                                                            	bins = new int[datasets.length][binCount];
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

	}
	
	@Override public void drawChart(GL2 gl, int width, int height, int lastSampleNumber, double zoomLevel, int mouseX, int mouseY) {
		
		// get the samples
		int endIndex = lastSampleNumber;
		int startIndex = endIndex - (int) (sampleCount * zoomLevel) + 1;
		int minDomain = SampleCountMinimum - 1;
		if(endIndex - startIndex < minDomain) startIndex = endIndex - minDomain;
		if(startIndex < 0) startIndex = 0;
		int sampleCount = endIndex - startIndex + 1;
		
		if(sampleCount - 1 < minDomain)
			return;
		
		boolean haveDatasets = datasets != null && datasets.length > 0;
		
		if(haveDatasets)
			for(int datasetN = 0; datasetN < samples.length; datasetN++)
				datasets[datasetN].getSamples(startIndex, endIndex, samples[datasetN]);

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
			for(int datasetN = 0; datasetN < datasets.length; datasetN++)
				for(int binN = 0; binN < binCount; binN++)
					bins[datasetN][binN] = 0;
		
		// fill the bins
		if(haveDatasets) {
			for(int datasetN = 0; datasetN < datasets.length; datasetN++) {
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
			for(int datasetN = 0; datasetN < datasets.length; datasetN++) {
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
			yXaxisTitleTextTop = yXaxisTitleTextBasline + FontUtils.xAxisTextHeight;
			xAxisTitle = haveDatasets ? datasets[0].unit + " (" + sampleCount + " Samples)" : "";
			xXaxisTitleTextLeft = xPlotLeft + (plotWidth  / 2.0f) - (FontUtils.xAxisTextWidth(xAxisTitle)  / 2.0f);
			
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
			yLegendTextTop = yLegendTextBaseline + FontUtils.legendTextHeight;
			yLegendBorderTop = yLegendTextTop + Theme.legendTextPadding;
			
			legendBoxCoordinates = new float[datasets.length][8];
			xLegendNameLeft = new float[datasets.length];
			
			float xOffset = xLegendBorderLeft + (Theme.lineWidth / 2) + Theme.legendTextPadding;
			
			for(int i = 0; i < datasets.length; i++) {
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
			if(showXaxisTitle)
				xXaxisTitleTextLeft = xLegendBorderRight + ((xPlotRight - xLegendBorderRight) / 2) - (FontUtils.xAxisTextWidth(xAxisTitle)  / 2.0f);
			
			float temp = yLegendBorderTop + Theme.legendTextPadding;
			if(yPlotBottom < temp) {
				yPlotBottom = temp;
				plotHeight = yPlotTop - yPlotBottom;
			}
		}
		
		if(showXaxisScale) {
			yXaxisTickTextBaseline = yPlotBottom;
			yXaxisTickTextTop = yXaxisTickTextBaseline + FontUtils.tickTextHeight;
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
			xYaxisLeftTitleTextBaseline = xYaxisLeftTitleTextTop + FontUtils.yAxisTextHeight;
			yAxisLeftTitle = yAxisShowsRelativeFrequency ? "Relative Frequency" : "Frequency";
			yYaxisLeftTitleTextLeft = yPlotBottom + (plotHeight / 2.0f) - (FontUtils.yAxisTextWidth(yAxisLeftTitle) / 2.0f);
			
			xPlotLeft = xYaxisLeftTitleTextBaseline + Theme.tickTextPadding;
			plotWidth = xPlotRight - xPlotLeft;
			
			if(showXaxisTitle && !showLegend)
				xXaxisTitleTextLeft = xPlotLeft + (plotWidth  / 2.0f) - (FontUtils.xAxisTextWidth(xAxisTitle)  / 2.0f);
			
			// the right y-axis is always for Frequency
			if(yAxisShowsRelativeFrequency && yAxisShowsFrequency) {
				xYaxisRightTitleTextTop = xPlotRight;
				xYaxisRightTitleTextBaseline = xYaxisRightTitleTextTop - FontUtils.yAxisTextHeight;
				yAxisRightTitle = "Frequency";
				yYaxisRightTitleTextLeft = yPlotTop - (plotHeight / 2.0f) + (FontUtils.yAxisTextWidth(yAxisRightTitle) / 2.0f);
				
				xPlotRight = xYaxisRightTitleTextBaseline - Theme.tickTextPadding;
				plotWidth = xPlotRight - xPlotLeft;
				
				if(showXaxisTitle && !showLegend)
					xXaxisTitleTextLeft = xPlotLeft + (plotWidth  / 2.0f) - (FontUtils.xAxisTextWidth(xAxisTitle)  / 2.0f);
			}
		}
		
		if(showYaxisScale) {
			// the left y-axis is for Relative Frequency unless only Frequency will be shown
			float maxTextWidth = 0;
			for(String text : yAxisShowsRelativeFrequency ? yDivisionsRelativeFrequency.values() : yDivisionsFrequency.values()) {
				float textWidth = FontUtils.tickTextWidth(text);
				if(textWidth > maxTextWidth)
					maxTextWidth = textWidth;
					
			}
			
			xYaxisLeftTickTextRight = xPlotLeft + maxTextWidth;
			xYaxisLeftTickLeft = xYaxisLeftTickTextRight + Theme.tickTextPadding;
			xYaxisLeftTickRight = xYaxisLeftTickLeft + Theme.tickLength;
			
			xPlotLeft = xYaxisLeftTickRight;
			plotWidth = xPlotRight - xPlotLeft;
			
			if(showXaxisTitle && !showLegend)
				xXaxisTitleTextLeft = xPlotLeft + (plotWidth  / 2.0f) - (FontUtils.xAxisTextWidth(xAxisTitle)  / 2.0f);
			
			// the right y-axis is always for Frequency
			if(yAxisShowsRelativeFrequency && yAxisShowsFrequency) {
				float MaxTextWidth = 0;
				for(String text : yDivisionsFrequency.values()) {
					float textWidth = FontUtils.tickTextWidth(text);
					if(textWidth > MaxTextWidth)
						MaxTextWidth = textWidth;
						
				}
				
				xYaxisRightTickTextLeft = xPlotRight - MaxTextWidth;
				xYaxisRightTickRight = xYaxisRightTickTextLeft - Theme.tickTextPadding;
				xYaxisRightTickLeft = xYaxisRightTickRight - Theme.tickLength;
				
				xPlotRight = xYaxisRightTickLeft;
				plotWidth = xPlotRight - xPlotLeft;
				
				if(showXaxisTitle && !showLegend)
					xXaxisTitleTextLeft = xPlotLeft + (plotWidth  / 2.0f) - (FontUtils.xAxisTextWidth(xAxisTitle)  / 2.0f);
			}
		}
		
		// get the x divisions now that we know the final plot width
		xDivisions = ChartUtils.getFloatXdivisions125(plotWidth, minX, maxX);
		
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
				float x = ((xValue - minX) / range * plotWidth) + xPlotLeft;
				gl.glColor4fv(Theme.divisionLinesColor, 0);
				gl.glVertex2f(x, yPlotTop);
				gl.glVertex2f(x, yPlotBottom);
				gl.glColor4fv(Theme.tickLinesColor, 0);
				gl.glVertex2f(x, yXaxisTickTop);
				gl.glVertex2f(x, yXaxisTickBottom);
			}
			gl.glEnd();
			
			for(Map.Entry<Float,String> entry : xDivisions.entrySet()) {
				float x = ((entry.getKey() - minX) / range * plotWidth) + xPlotLeft - (FontUtils.tickTextWidth(entry.getValue()) / 2.0f);
				float y = yXaxisTickTextBaseline;
				FontUtils.drawTickText(entry.getValue(), (int) x, (int) y);
			}
		}
		
		// draw the y-axis scale
		if(showYaxisScale) {
		
			// draw right y-axis scale if showing both frequency and relative frequency
			if(yAxisShowsFrequency && yAxisShowsRelativeFrequency) {
				
				gl.glBegin(GL2.GL_LINES);
				for(Float entry : yDivisionsFrequency.keySet()) {
					float y = (entry - minYfreq) / yFreqRange * plotHeight + yPlotBottom;
					gl.glColor4fv(Theme.divisionLinesColor, 0);
					gl.glVertex2f(xPlotRight, y);
					gl.glColor4fv(Theme.divisionLinesFadedColor, 0);
					gl.glVertex2f(xPlotLeft,  y);
					gl.glColor4fv(Theme.tickLinesColor, 0);
					gl.glVertex2f(xYaxisRightTickLeft,  y);
					gl.glVertex2f(xYaxisRightTickRight, y);
				}
				gl.glEnd();
	
				for(Map.Entry<Float,String> entry : yDivisionsFrequency.entrySet()) {
					float x = xYaxisRightTickTextLeft;
					float y = (entry.getKey() - minYfreq) / yFreqRange * plotHeight + yPlotBottom - (FontUtils.tickTextHeight / 2.0f);
					FontUtils.drawTickText(entry.getValue(), (int) x, (int) y);
				}
				
			}
			
			// relative frequency is drawn on the left unless only frequency is to be drawn
			if(yAxisShowsRelativeFrequency) {
				
				gl.glBegin(GL2.GL_LINES);
				for(Float entry : yDivisionsRelativeFrequency.keySet()) {
					float y = (entry - minYrelFreq) / yRelFreqRange * plotHeight + yPlotBottom;
					gl.glColor4fv(Theme.divisionLinesColor, 0);
					gl.glVertex2f(xPlotLeft,  y);
					if(yAxisShowsFrequency && yAxisShowsRelativeFrequency) gl.glColor4fv(Theme.divisionLinesFadedColor, 0);
					gl.glVertex2f(xPlotRight, y);
					gl.glColor4fv(Theme.tickLinesColor, 0);
					gl.glVertex2f(xYaxisLeftTickLeft,  y);
					gl.glVertex2f(xYaxisLeftTickRight, y);
				}
				gl.glEnd();
				
				for(Map.Entry<Float,String> entry : yDivisionsRelativeFrequency.entrySet()) {
					float x = xYaxisLeftTickTextRight - FontUtils.tickTextWidth(entry.getValue());
					float y = (entry.getKey() - minYrelFreq) / yRelFreqRange * plotHeight + yPlotBottom - (FontUtils.tickTextHeight / 2.0f);
					FontUtils.drawTickText(entry.getValue(), (int) x, (int) y);
				}
			
			} else {
				
				gl.glBegin(GL2.GL_LINES);
				for(Float entry : yDivisionsFrequency.keySet()) {
					float y = (entry - minYfreq) / yFreqRange * plotHeight + yPlotBottom;
					gl.glColor4fv(Theme.divisionLinesColor, 0);
					gl.glVertex2f(xPlotLeft,  y);
					gl.glVertex2f(xPlotRight, y);
					gl.glColor4fv(Theme.tickLinesColor, 0);
					gl.glVertex2f(xYaxisLeftTickLeft,  y);
					gl.glVertex2f(xYaxisLeftTickRight, y);
				}
				gl.glEnd();
				
				for(Map.Entry<Float,String> entry : yDivisionsFrequency.entrySet()) {
					float x = xYaxisLeftTickTextRight - FontUtils.tickTextWidth(entry.getValue());
					float y = (entry.getKey() - minYfreq) / yFreqRange * plotHeight + yPlotBottom - (FontUtils.tickTextHeight / 2.0f);
					FontUtils.drawTickText(entry.getValue(), (int) x, (int) y);
				}
				
			}
			
		}
		
		// draw the legend, if space is available
		if(showLegend && haveDatasets && xLegendBorderRight < width - Theme.tilePadding) {
			gl.glBegin(GL2.GL_QUADS);
			gl.glColor4fv(Theme.legendBackgroundColor, 0);
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
					
		// draw the x-axis title, if space is available
		if(showXaxisTitle)
			if((!showLegend && xXaxisTitleTextLeft > xPlotLeft) || (showLegend && xXaxisTitleTextLeft > xLegendBorderRight + Theme.legendTextPadding))
				FontUtils.drawXaxisText(xAxisTitle, (int) xXaxisTitleTextLeft, (int) yXaxisTitleTextBasline);
		
		// draw the left y-axis title, if space is available
		if(showYaxisTitle && yYaxisLeftTitleTextLeft >= yPlotBottom)
			FontUtils.drawYaxisText(yAxisLeftTitle, (int) xYaxisLeftTitleTextBaseline, (int) yYaxisLeftTitleTextLeft, 90);
		
		// draw the right y-axis title, if applicable, and if space is available
		if(showYaxisTitle && yAxisShowsRelativeFrequency && yAxisShowsFrequency && yYaxisRightTitleTextLeft <= yPlotTop)
			FontUtils.drawYaxisText(yAxisRightTitle, (int) xYaxisRightTitleTextBaseline, (int) yYaxisRightTitleTextLeft, -90);

		// clip to the plot region
		int[] originalScissorArgs = new int[4];
		gl.glGetIntegerv(GL2.GL_SCISSOR_BOX, originalScissorArgs, 0);
		gl.glScissor(originalScissorArgs[0] + (int) xPlotLeft, originalScissorArgs[1] + (int) yPlotBottom, (int) plotWidth, (int) plotHeight);
		
		// draw the bins
		if(haveDatasets) {
			for(int datasetN = 0; datasetN < datasets.length; datasetN++) {
				
				for(int binN = 0; binN < binCount; binN++) {
					
					float min = minX + (binSize *  binN);      // inclusive
					float max = minX + (binSize * (binN + 1)); // exclusive
					float center = (max + min) / 2f;
					
					float xBarCenter = ((center - minX) / range * plotWidth) + xPlotLeft;
					float yBarTop = ((float) bins[datasetN][binN] - minYfreq) / yFreqRange * plotHeight + yPlotBottom;
					float halfBarWidth = plotWidth / binCount / 2f;
					
					gl.glBegin(GL2.GL_QUADS);
					gl.glColor3f(datasets[datasetN].color.getRed()/255.0f, datasets[datasetN].color.getGreen()/255.0f, datasets[datasetN].color.getBlue()/255.0f);
						gl.glVertex2f(xBarCenter - halfBarWidth, yPlotBottom);
						gl.glVertex2f(xBarCenter - halfBarWidth, yBarTop);
						gl.glVertex2f(xBarCenter + halfBarWidth, yBarTop);
						gl.glVertex2f(xBarCenter + halfBarWidth, yPlotBottom);
					gl.glEnd();
					
				}
			
			}
		}

		// stop clipping to the plot region
		gl.glScissor(originalScissorArgs[0], originalScissorArgs[1], originalScissorArgs[2], originalScissorArgs[3]);
		
		// draw the tooltip if the mouse is in the plot region
		if(haveDatasets && SettingsController.getTooltipVisibility() && mouseX >= xPlotLeft && mouseX <= xPlotRight && mouseY >= yPlotBottom && mouseY <= yPlotTop) {
			int binN = Math.round(((float) mouseX - xPlotLeft) / plotWidth * binCount);
			if(binN > binCount - 1)
				binN = binCount - 1;
			float min = minX + (binSize *  binN);      // inclusive
			float max = minX + (binSize * (binN + 1)); // exclusive
			String[] text = new String[datasets.length + 1];
			Color[] colors = new Color[datasets.length + 1];
			text[0] = ChartUtils.formattedNumber(min, 5) + " to " + ChartUtils.formattedNumber(max, 5) + " " + datasets[0].unit;
			colors[0] = new Color(Theme.tooltipBackgroundColor[0], Theme.tooltipBackgroundColor[1], Theme.tooltipBackgroundColor[2], Theme.tooltipBackgroundColor[3]);
			for(int datasetN = 0; datasetN < datasets.length; datasetN++) {
				text[datasetN + 1] = bins[datasetN][binN] + " samples (" + ChartUtils.formattedNumber((double) bins[datasetN][binN] / (double) sampleCount * 100f, 4) + "%)";
				colors[datasetN + 1] = datasets[datasetN].color;
			}
			float xBarCenter = ((binSize *  binN) + (binSize * (binN + 1))) / 2f / range * plotWidth + xPlotLeft;
			if(datasets.length > 1) {
				gl.glBegin(GL2.GL_LINES);
				gl.glColor4fv(Theme.tooltipVerticalBarColor, 0);
					gl.glVertex2f(xBarCenter, yPlotTop);
					gl.glVertex2f(xBarCenter, yPlotBottom);
				gl.glEnd();
				ChartUtils.drawTooltip(gl, text, colors, (int) xBarCenter, mouseY, xPlotLeft, yPlotTop, xPlotRight, yPlotBottom);
			} else {
				int anchorY = (int) (((float) bins[0][binN] - minYfreq) / yFreqRange * plotHeight + yPlotBottom);
				ChartUtils.drawTooltip(gl, text, colors, (int) xBarCenter, anchorY, xPlotLeft, yPlotTop, xPlotRight, yPlotBottom);				
			}
		}
		
		// draw the plot border
		gl.glBegin(GL2.GL_LINE_LOOP);
		gl.glColor3f(0.0f, 0.0f, 0.0f);
			gl.glVertex2f(xPlotLeft,  yPlotTop);
			gl.glVertex2f(xPlotRight, yPlotTop);
			gl.glVertex2f(xPlotRight, yPlotBottom);
			gl.glVertex2f(xPlotLeft,  yPlotBottom);
		gl.glEnd();
		
	}

}
