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
@SuppressWarnings("serial")
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
	
	public static ChartFactory getFactory() {
		
		return new ChartFactory() {
			
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
			
			@Override public String toString() { return "Histogram Chart"; }
			
			@Override public JPanel[] getWidgets() {

				datasetsWidget       = new WidgetDatasets();
				sampleCountWidget    = new WidgetTextfieldInteger("Sample Count", 1000, 5, Integer.MAX_VALUE);
				binCountWidget       = new WidgetTextfieldInteger("Bin Count", 60, 2, Integer.MAX_VALUE);
				xAxisTypeWidget      = new WidgetHistogramXaxisType(-1.0f, 1.0f, 0.0f, -Float.MAX_VALUE, Float.MAX_VALUE);
				yAxisTypeWidget      = new WidgetHistogramYaxisType(0.0f, 1.0f, 0.0f, 1.0f, 0.0f, 1000.0f, 0.0f, Integer.MAX_VALUE);
				showXaxisTitleWidget = new WidgetCheckbox("Show X-Axis Title", true);
				showXaxisScaleWidget = new WidgetCheckbox("Show X-Axis Scale", true);
				showYaxisTitleWidget = new WidgetCheckbox("Show Y-Axis Title", true);
				showYaxisScaleWidget = new WidgetCheckbox("Show Y-Axis Scale", true);
				showLegendWidget     = new WidgetCheckbox("Show Legend", true);
				
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
			
			@Override public int getMinimumSampleCount() {
				return 5;
			}
			
			@Override public PositionedChart createChart(int x1, int y1, int x2, int y2) {

				int sampleCount                 = sampleCountWidget.getValue();
				Dataset[] datasets              = datasetsWidget.getDatasets();
				int binCount                    = binCountWidget.getValue();
				boolean xAxisIsCentered         = xAxisTypeWidget.isAxisCentered();
				float xCenterValue              = xAxisTypeWidget.getCenterValue();
				boolean xAutoscaleMin           = xAxisTypeWidget.isMinimumAutomatic();
				float xManualMin                = xAxisTypeWidget.getMinimumValue();
				boolean xAutoscaleMax           = xAxisTypeWidget.isMaximumAutomatic();
				float xManualMax                = xAxisTypeWidget.getMaximumValue();
				boolean yShowsRelativeFrequency = yAxisTypeWidget.isRelativeFrequencyShown();
				boolean yShowsFreqency          = yAxisTypeWidget.isFrequencyShown();
				boolean yMinIsZero              = yAxisTypeWidget.isMinimumZero();
				boolean yMaxIsAutomatic         = yAxisTypeWidget.isMaximumAutomatic();
				float yMinimum                  = yAxisTypeWidget.getMinimumValue();
				float yMaximum                  = yAxisTypeWidget.getMaximumValue();
				boolean showXaxisTitle          = showXaxisTitleWidget.isChecked();
				boolean showXaxisScale          = showXaxisScaleWidget.isChecked();
				boolean showYaxisTitle          = showYaxisTitleWidget.isChecked();
				boolean showYaxisScale          = showYaxisScaleWidget.isChecked();
				boolean showLegend              = showLegendWidget.isChecked();
				
				if(datasets.length == 0)
					return null;
				
				OpenGLHistogramChart chart = new OpenGLHistogramChart(x1, y1, x2, y2, sampleCount, datasets);
				chart.setBinCount(binCount);
				chart.setXaxisType(xAxisIsCentered, xCenterValue, xAutoscaleMin, xManualMin, xAutoscaleMax, xManualMax);
				chart.setYaxisType(yShowsRelativeFrequency, yShowsFreqency, yMinIsZero, yMaxIsAutomatic, yMinimum, yMaximum);
				chart.setVisibleRegions(showXaxisTitle, showXaxisScale, showYaxisTitle, showYaxisScale, showLegend);
				
				return chart;
				
			}
			
			@Override public PositionedChart importChart(int x1, int y1, int x2, int y2, Dataset[] datasets, int sampleCount, String[] lines, int firstLineNumber) {
				
				if(lines.length != 18)
					throw new AssertionError("Line " + firstLineNumber + ": Invalid Histogram Chart configuration section.");
				
				int binCount                    =     (int) ChartUtils.parse(firstLineNumber +  0, lines[0],  "bin count = %d");
				boolean xAxisIsCentered         = (boolean) ChartUtils.parse(firstLineNumber +  1, lines[1],  "x-axis is centered = %b");
				float xCenterValue              =   (float) ChartUtils.parse(firstLineNumber +  2, lines[2],  "x-axis center value = %f");
				boolean xAutoscaleMin           = (boolean) ChartUtils.parse(firstLineNumber +  3, lines[3],  "x-axis autoscale minimum = %b");
				float xManualMin                =   (float) ChartUtils.parse(firstLineNumber +  4, lines[4],  "x-axis manual minimum = %f");
				boolean xAutoscaleMax           = (boolean) ChartUtils.parse(firstLineNumber +  5, lines[5],  "x-axis autoscale maximum = %b");
				float xManualMax                =   (float) ChartUtils.parse(firstLineNumber +  6, lines[6],  "x-axis manual maximum = %f");
				boolean yShowsRelativeFrequency = (boolean) ChartUtils.parse(firstLineNumber +  7, lines[7],  "y-axis shows relative frequency = %b");
				boolean yShowsFrequency         = (boolean) ChartUtils.parse(firstLineNumber +  8, lines[8],  "y-axis shows frequency = %b");
				boolean yMinIsZero              = (boolean) ChartUtils.parse(firstLineNumber +  9, lines[9],  "y-axis minimum is zero = %b");
				boolean yMaxIsAutomatic         = (boolean) ChartUtils.parse(firstLineNumber + 10, lines[10], "y-axis autoscale maximum = %b");
				float yMinimum                  =   (float) ChartUtils.parse(firstLineNumber + 11, lines[11], "y-axis manual minimum = %f");
				float yMaximum                  =   (float) ChartUtils.parse(firstLineNumber + 12, lines[12], "y-axis manual maximum = %f");
				boolean showXaxisTitle          = (boolean) ChartUtils.parse(firstLineNumber + 13, lines[13], "show x-axis title = %b");
				boolean showXaxisScale          = (boolean) ChartUtils.parse(firstLineNumber + 14, lines[14], "show x-axis scale = %b");
				boolean showYaxisTitle          = (boolean) ChartUtils.parse(firstLineNumber + 15, lines[15], "show y-axis title = %b");
				boolean showYaxisScale          = (boolean) ChartUtils.parse(firstLineNumber + 16, lines[16], "show y-axis scale = %b");
				boolean showLegend              = (boolean) ChartUtils.parse(firstLineNumber + 17, lines[17], "show legend = %b");
				
				OpenGLHistogramChart chart = new OpenGLHistogramChart(x1, y1, x2, y2, sampleCount, datasets);
				chart.setBinCount(binCount);
				chart.setXaxisType(xAxisIsCentered, xCenterValue, xAutoscaleMin, xManualMin, xAutoscaleMax, xManualMax);
				chart.setYaxisType(yShowsRelativeFrequency, yShowsFrequency, yMinIsZero, yMaxIsAutomatic, yMinimum, yMaximum);
				chart.setVisibleRegions(showXaxisTitle, showXaxisScale, showYaxisTitle, showYaxisScale, showLegend);
				
				return chart;
				
			}
			
		};
		
	}
	
	@Override public String[] exportChartSettings() {
		
		String[] lines = new String[18];
		
		lines[0]  = "bin count = " + binCount;
		lines[1]  = "x-axis is centered = " + xAxisIsCentered;
		lines[2]  = "x-axis center value = " + xCenterValue;
		lines[3]  = "x-axis autoscale minimum = " + xAutoscaleMin;
		lines[4]  = "x-axis manual minimum = " + manualMinX;
		lines[5]  = "x-axis autoscale maximum = " + xAutoscaleMax;
		lines[6]  = "x-axis manual maximum = " + manualMaxX;
		lines[7]  = "y-axis shows relative frequency = " + yAxisShowsRelativeFrequency;
		lines[8]  = "y-axis shows frequency = " + yAxisShowsFrequency;
		lines[9]  = "y-axis minimum is zero = " + yMinimumIsZero;
		lines[10] = "y-axis autoscale maximum = " + yAutoscaleMax;
		lines[11] = "y-axis manual minimum = " + manualMinY;
		lines[12] = "y-axis manual maximum = " + manualMaxY;
		lines[13] = "show x-axis title = " + showXaxisTitle;
		lines[14] = "show x-axis scale = " + showXaxisScale;
		lines[15] = "show y-axis title = " + showYaxisTitle;
		lines[16] = "show y-axis scale = " + showYaxisScale;
		lines[17] = "show legend = " + showLegend;
		
		return lines;
		
	}
	
	@Override public String toString() {
		
		return "Histogram Chart";
		
	}
	
	public OpenGLHistogramChart(int x1, int y1, int x2, int y2, int chartDuration, Dataset[] chartInputs) {
		
		super(x1, y1, x2, y2, chartDuration, chartInputs);
			
		binCount = 60;
		bins = new int[datasets.length][binCount];
		
		samples = new Samples[datasets.length];
		for(int i = 0; i < samples.length; i++)
			samples[i] = new Samples();
		
		xAxisIsCentered = false;
		xCenterValue = 0.0f;
		xAutoscaleMin = true;
		xAutoscaleMax = true;
		manualMinX = -1.0f;
		manualMaxX = 1.0f;
		
		yAxisShowsRelativeFrequency = true;
		yAxisShowsFrequency = true;
		yAutoscaleRelativeFrequency = new AutoScale(AutoScale.MODE_EXPONENTIAL, 30, 0.20f);
		yAutoscaleFrequency = new AutoScale(AutoScale.MODE_EXPONENTIAL, 30, 0.20f);
		yMinimumIsZero = true;
		yAutoscaleMax = true;
		manualMinY = 0.0f; // relative frequency unless only frequency is shown
		manualMaxY = 1.0f; // relative frequency unless only frequency is shown

		showXaxisTitle = true;
		showXaxisScale = true;
		showYaxisTitle = true;
		showYaxisScale = true;
		showLegend = true;

	}
	
	public void setBinCount(int count) {
		
		binCount = count;
		bins = new int[datasets.length][binCount];
		
	}
	
	public void setXaxisType(boolean axisIsCentered, float centerValue, boolean autoscaleMinimum, float manualMinimum, boolean autoscaleMaximum, float manualMaximum) {
		
		xAxisIsCentered = axisIsCentered;
		xCenterValue = centerValue;
		xAutoscaleMin = autoscaleMinimum;
		xAutoscaleMax = autoscaleMaximum;
		manualMinX = manualMinimum;
		manualMaxX = manualMaximum;
		
	}

	public void setYaxisType(boolean axisShowsRelativeFrequency, boolean axisShowsFrequency, boolean minimumIsZero, boolean maximumIsAutomatic, float minimum, float maximum) {
		
		yAxisShowsRelativeFrequency = axisShowsRelativeFrequency;
		yAxisShowsFrequency = axisShowsFrequency;
		yMinimumIsZero = minimumIsZero;
		yAutoscaleMax = maximumIsAutomatic;
		manualMinY = minimum;
		manualMaxY = maximum;
		
	}
	
	public void setVisibleRegions(boolean showXaxisTitle, boolean showXaxisScale, boolean showYaxisTitle, boolean showYaxisScale, boolean showLegend) {
		
		this.showXaxisTitle = showXaxisTitle;
		this.showXaxisScale = showXaxisScale;
		this.showYaxisTitle = showYaxisTitle;
		this.showYaxisScale = showYaxisScale;
		this.showLegend     = showLegend;
		
	}
	
	@Override public void drawChart(GL2 gl, int width, int height, int lastSampleNumber, double zoomLevel) {
		
		// get the samples
		int endIndex = lastSampleNumber;
		int startIndex = endIndex - (int) (duration * zoomLevel) + 1;
		int minDomain = OpenGLHistogramChart.getFactory().getMinimumSampleCount() - 1;
		if(endIndex - startIndex < minDomain) startIndex = endIndex - minDomain;
		if(startIndex < 0) startIndex = 0;
		int sampleCount = endIndex - startIndex + 1;
		
		if(sampleCount - 1 < minDomain)
			return;
		
		for(int datasetN = 0; datasetN < samples.length; datasetN++)
			datasets[datasetN].getSamples(startIndex, endIndex, samples[datasetN]);

		// determine the true x-axis scale
		float trueMinX = 0;
		float trueMaxX = 0;
		trueMinX = samples[0].min;
		trueMaxX = samples[0].max;
		for(int datasetN = 1; datasetN < samples.length; datasetN++) {
			float min = samples[datasetN].min;
			float max = samples[datasetN].max;
			if(min < trueMinX) trueMinX = min;
			if(max > trueMaxX) trueMaxX = max;
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
		for(int datasetN = 0; datasetN < datasets.length; datasetN++)
			for(int binN = 0; binN < binCount; binN++)
				bins[datasetN][binN] = 0;
		
		// fill the bins
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

		int maxBinSize = 0;
		for(int datasetN = 0; datasetN < datasets.length; datasetN++) {
			for(int binN = 0; binN < binCount; binN++)
				if(bins[datasetN][binN] > maxBinSize)
					maxBinSize = bins[datasetN][binN];
		}
		
		float trueMaxYfreq = maxBinSize;
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
			xAxisTitle = datasets[0].unit + " (" + sampleCount + " Samples)";
			xXaxisTitleTextLeft = xPlotLeft + (plotWidth  / 2.0f) - (FontUtils.xAxisTextWidth(xAxisTitle)  / 2.0f);
			
			float temp = yXaxisTitleTextTop + Theme.tickTextPadding;
			if(yPlotBottom < temp) {
				yPlotBottom = temp;
				plotHeight = yPlotTop - yPlotBottom;
			}
		}
		
		if(showLegend) {
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
		if(showLegend && xLegendBorderRight < width - Theme.tilePadding) {
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
					
		// draw the x-axis title, if spcae is available
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

		// stop clipping to the plot region
		gl.glScissor(originalScissorArgs[0], originalScissorArgs[1], originalScissorArgs[2], originalScissorArgs[3]);
		
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
