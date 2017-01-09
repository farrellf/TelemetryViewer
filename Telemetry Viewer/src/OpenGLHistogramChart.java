import java.util.Map;

import javax.swing.JPanel;

import com.jogamp.opengl.GL2;

/**
 * Renders a Histogram.
 */
@SuppressWarnings("serial")
public class OpenGLHistogramChart extends PositionedChart {
	
	Samples[] samples;
	
	int[][] bins;
	int binCount;

	boolean xAxisIsCentered;
	float xCenterValue;
	boolean xAutoscaleMin;
	boolean xAutoscaleMax;
	float manualMinX;
	float manualMaxX;
		
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
			
			@Override public String toString() { return "Histogram Chart"; }
			
			@Override public JPanel[] getWidgets() {

				datasetsWidget    = new WidgetDatasets();
				sampleCountWidget = new WidgetTextfieldInteger("Sample Count", 1000, 5, Integer.MAX_VALUE);
				binCountWidget    = new WidgetTextfieldInteger("Bin Count", 60, 2, Integer.MAX_VALUE);
				xAxisTypeWidget   = new WidgetHistogramXaxisType(-1.0f, 1.0f, 0.0f, -Float.MAX_VALUE, Float.MAX_VALUE);
				yAxisTypeWidget   = new WidgetHistogramYaxisType(0.0f, 1.0f, 0.0f, 1.0f, 0.0f, 1000.0f, 0.0f, Integer.MAX_VALUE);
	
				JPanel[] widgets = new JPanel[8];
				
				widgets[0] = datasetsWidget;
				widgets[1] = null;
				widgets[2] = sampleCountWidget;
				widgets[3] = binCountWidget;
				widgets[4] = null;
				widgets[5] = xAxisTypeWidget;
				widgets[6] = null;
				widgets[7] = yAxisTypeWidget;

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
				
				if(datasets.length == 0)
					return null;
				
				OpenGLHistogramChart chart = new OpenGLHistogramChart(x1, y1, x2, y2, sampleCount, datasets);
				chart.setBinCount(binCount);
				chart.setXaxisType(xAxisIsCentered, xCenterValue, xAutoscaleMin, xManualMin, xAutoscaleMax, xManualMax);
				chart.setYaxisType(yShowsRelativeFrequency, yShowsFreqency, yMinIsZero, yMaxIsAutomatic, yMinimum, yMaximum);
				
				return chart;
				
			}
			
			@Override public PositionedChart importChart(int x1, int y1, int x2, int y2, Dataset[] datasets, int sampleCount, String[] lines, int firstLineNumber) {
				
				if(lines.length != 13)
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
				
				OpenGLHistogramChart chart = new OpenGLHistogramChart(x1, y1, x2, y2, sampleCount, datasets);
				chart.setBinCount(binCount);
				chart.setXaxisType(xAxisIsCentered, xCenterValue, xAutoscaleMin, xManualMin, xAutoscaleMax, xManualMax);
				chart.setYaxisType(yShowsRelativeFrequency, yShowsFrequency, yMinIsZero, yMaxIsAutomatic, yMinimum, yMaximum);
				
				return chart;
				
			}
			
		};
		
	}
	
	@Override public String[] exportChartSettings() {
		
		String[] lines = new String[13];
		
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
	
	@Override public void drawChart(GL2 gl, int width, int height, int lastSampleNumber, double zoomLevel) {
		
		// draw background
		gl.glBegin(GL2.GL_QUADS);
		gl.glColor4fv(Theme.backgroundColor, 0);
			gl.glVertex2f(0,     0);
			gl.glVertex2f(0,     height);
			gl.glVertex2f(width, height);
			gl.glVertex2f(width, 0);
		gl.glEnd();
		
		// draw perimeter outline
		gl.glBegin(GL2.GL_LINE_LOOP);
		gl.glColor4fv(Theme.perimeterOutlineColor, 0);
			gl.glVertex2f(0,     0);
			gl.glVertex2f(0,     height);
			gl.glVertex2f(width, height);
			gl.glVertex2f(width, 0);
		gl.glEnd();
		
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
		
		// calculate x and y positions of everything (coordinate system has 0,0 at the bottom-left)
		float xLegendBorderLeft = Theme.perimeterPadding;
		float yLegendBorderBottom = Theme.perimeterPadding;
		float yLegendTextBaseline = yLegendBorderBottom + Theme.legendTextPadding;
		float yLegendTextTop = yLegendTextBaseline + FontUtils.legendTextHeight;
		float yLegendBorderTop = yLegendTextTop + Theme.legendTextPadding;
		
		float yXaxisTitleTextBasline = Theme.perimeterPadding;
		float yXaxisTitleTextTop = yXaxisTitleTextBasline + FontUtils.xAxisTextHeight;
		
		float yXaxisTickTextBaseline = Float.max(yLegendBorderTop + Theme.legendTextPadding, yXaxisTitleTextTop + Theme.legendTextPadding);
		float yXaxisTickTextTop = yXaxisTickTextBaseline + FontUtils.tickTextHeight;
		float yXaxisTickBottom = yXaxisTickTextTop + Theme.tickTextPadding;
		float yXaxisTickTop = yXaxisTickBottom + Theme.tickLength;
		
		float yPlotBottom = yXaxisTickTop;
		float yPlotTop = height - Theme.perimeterPadding;
		float plotHeight = yPlotTop - yPlotBottom;
		if(plotHeight < 1.0f)
			return;
		
		Map<Float, String> yDivisionsFrequency = ChartUtils.getYdivisions125(plotHeight, minYfreq, maxYfreq);
		Map<Float, String> yDivisionsRelativeFrequency = ChartUtils.getYdivisions125(plotHeight, minYrelFreq, maxYrelFreq);
		
		float maxYfrequencyTickTextWidth = 0;
		for(String text : yDivisionsFrequency.values()) {
			float w = FontUtils.tickTextWidth(text); 
			if(w > maxYfrequencyTickTextWidth)
				maxYfrequencyTickTextWidth = w;
		}
		
		float maxYrelativeFrequencyTickTextWidth = 0;
		for(String text : yDivisionsRelativeFrequency.values()) {
			float w = FontUtils.tickTextWidth(text); 
			if(w > maxYrelativeFrequencyTickTextWidth)
				maxYrelativeFrequencyTickTextWidth = w;
		}
		
		float xLeftYaxisTitleTextTop = Theme.perimeterPadding;
		float xLeftYaxisTitleTextBaseline = xLeftYaxisTitleTextTop + FontUtils.yAxisTextHeight;
		
		float xLeftYaxisTickTextLeft = xLeftYaxisTitleTextBaseline + Theme.tickTextPadding;
		float xLeftYaxisTickTextRight = (yAxisShowsFrequency && !yAxisShowsRelativeFrequency) ? xLeftYaxisTickTextLeft + maxYfrequencyTickTextWidth : xLeftYaxisTickTextLeft + maxYrelativeFrequencyTickTextWidth;
		float xLeftYaxisTickLeft = xLeftYaxisTickTextRight + Theme.tickTextPadding;
		float xLeftYaxisTickRight = xLeftYaxisTickLeft + Theme.tickLength;
		
		float xRightYaxisTitleTextTop = width - Theme.perimeterPadding;
		float xRightYaxisTitleTextBaseline = xRightYaxisTitleTextTop - FontUtils.yAxisTextHeight;
		
		float xRightYaxisTickTextRight = xRightYaxisTitleTextBaseline - Theme.tickTextPadding;
		float xRightYaxisTickTextLeft = xRightYaxisTickTextRight - maxYfrequencyTickTextWidth;
		float xRightYaxisTickRight = xRightYaxisTickTextLeft - Theme.tickTextPadding;
		float xRightYaxisTickLeft = xRightYaxisTickRight - Theme.tickLength;
		
		float xPlotLeft = xLeftYaxisTickRight;
		float xPlotRight = (yAxisShowsFrequency && yAxisShowsRelativeFrequency) ? xRightYaxisTickLeft : width - Theme.perimeterPadding;
		float plotWidth = xPlotRight - xPlotLeft;
		if(plotWidth < 1.0f)
			return;
		
		Map<Float, String> xDivisions = ChartUtils.getFloatXdivisions125(plotWidth, minX, maxX);
		
		String xAxisTitle = datasets[0].unit + " (" + sampleCount + " Samples)";
		String relativeFrequencyYaxisTitle = "Relative Frequency";
		String frequencyYaxisTitle = "Frequency";
		float yLeftYaxisTitleLeft = (yAxisShowsFrequency && !yAxisShowsRelativeFrequency) ? yPlotBottom + (plotHeight / 2.0f) - (FontUtils.yAxisTextWidth(frequencyYaxisTitle) / 2.0f) : yPlotBottom + (plotHeight / 2.0f) - (FontUtils.yAxisTextWidth(relativeFrequencyYaxisTitle) / 2.0f);
		float yRightYaxisTitleLeft = yPlotBottom + (plotHeight / 2.0f) + (FontUtils.yAxisTextWidth(frequencyYaxisTitle) / 2.0f);
		float xXaxisTitleLeft = xPlotLeft +   (plotWidth  / 2.0f) - (FontUtils.xAxisTextWidth(xAxisTitle)  / 2.0f);

		// draw plot background
		gl.glBegin(GL2.GL_QUADS);
		gl.glColor4fv(Theme.plotBackgroundColor, 0);
			gl.glVertex2f(xPlotLeft,  yPlotTop);
			gl.glVertex2f(xPlotRight, yPlotTop);
			gl.glVertex2f(xPlotRight, yPlotBottom);
			gl.glVertex2f(xPlotLeft,  yPlotBottom);
		gl.glEnd();
		
		// draw x division lines
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
		
		// draw x division line text
		for(Map.Entry<Float,String> entry : xDivisions.entrySet()) {
			float x = ((entry.getKey() - minX) / range * plotWidth) + xPlotLeft - (FontUtils.tickTextWidth(entry.getValue()) / 2.0f);
			float y = yXaxisTickTextBaseline;
			FontUtils.drawTickText(entry.getValue(), (int) x, (int) y);
		}
		
		// draw right y axis text if showing both frequency and relative frequency
		if(yAxisShowsFrequency && yAxisShowsRelativeFrequency) {
			
			// draw right y division lines
			gl.glBegin(GL2.GL_LINES);
			for(Float entry : yDivisionsFrequency.keySet()) {
				float y = (entry - minYfreq) / yFreqRange * plotHeight + yPlotBottom;
				gl.glColor4fv(Theme.divisionLinesColor, 0);
				gl.glVertex2f(xPlotRight, y);
				gl.glColor4fv(Theme.divisionLinesFadedColor, 0);
				gl.glVertex2f(xPlotLeft,  y);
				gl.glColor4fv(Theme.tickLinesColor, 0);
				gl.glVertex2f(xRightYaxisTickLeft,  y);
				gl.glVertex2f(xRightYaxisTickRight, y);
			}
			gl.glEnd();
			
			// draw right y division line text
			for(Map.Entry<Float,String> entry : yDivisionsFrequency.entrySet()) {
				float x = xRightYaxisTickTextLeft;
				float y = (entry.getKey() - minYfreq) / yFreqRange * plotHeight + yPlotBottom - (FontUtils.tickTextHeight / 2.0f);
				FontUtils.drawTickText(entry.getValue(), (int) x, (int) y);
			}
			
			// draw right y-axis title, if space is available
			if(yRightYaxisTitleLeft <= yPlotTop) {
				float x = xRightYaxisTitleTextBaseline;
				float y = yRightYaxisTitleLeft;
				FontUtils.drawYaxisText(frequencyYaxisTitle, (int) x, (int) y, -90);
			}
			
		}
		
		// relative frequency is drawn on the left unless only frequency is to be drawn
		if(yAxisShowsRelativeFrequency) {
			
			// draw left y division lines
			gl.glBegin(GL2.GL_LINES);
			for(Float entry : yDivisionsRelativeFrequency.keySet()) {
				float y = (entry - minYrelFreq) / yRelFreqRange * plotHeight + yPlotBottom;
				gl.glColor4fv(Theme.divisionLinesColor, 0);
				gl.glVertex2f(xPlotLeft,  y);
				if(yAxisShowsFrequency && yAxisShowsRelativeFrequency) gl.glColor4fv(Theme.divisionLinesFadedColor, 0);
				gl.glVertex2f(xPlotRight, y);
				gl.glColor4fv(Theme.tickLinesColor, 0);
				gl.glVertex2f(xLeftYaxisTickLeft,  y);
				gl.glVertex2f(xLeftYaxisTickRight, y);
			}
			gl.glEnd();
			
			// draw left y division line text
			for(Map.Entry<Float,String> entry : yDivisionsRelativeFrequency.entrySet()) {
				float x = xLeftYaxisTickTextRight - FontUtils.tickTextWidth(entry.getValue());
				float y = (entry.getKey() - minYrelFreq) / yRelFreqRange * plotHeight + yPlotBottom - (FontUtils.tickTextHeight / 2.0f);
				FontUtils.drawTickText(entry.getValue(), (int) x, (int) y);
			}
			
			// draw the left y-axis title, if space is available
			if(yLeftYaxisTitleLeft >= yPlotBottom) {
				float x = xLeftYaxisTitleTextBaseline;
				float y = yLeftYaxisTitleLeft;
				FontUtils.drawYaxisText(relativeFrequencyYaxisTitle, (int) x, (int) y, 90);
			}
		
		} else {
			
			// draw left y division lines
			gl.glBegin(GL2.GL_LINES);
			for(Float entry : yDivisionsFrequency.keySet()) {
				float y = (entry - minYfreq) / yFreqRange * plotHeight + yPlotBottom;
				gl.glColor4fv(Theme.divisionLinesColor, 0);
				gl.glVertex2f(xPlotLeft,  y);
				gl.glVertex2f(xPlotRight, y);
				gl.glColor4fv(Theme.tickLinesColor, 0);
				gl.glVertex2f(xLeftYaxisTickLeft,  y);
				gl.glVertex2f(xLeftYaxisTickRight, y);
			}
			gl.glEnd();
			
			// draw left y division line text
			for(Map.Entry<Float,String> entry : yDivisionsFrequency.entrySet()) {
				float x = xLeftYaxisTickTextRight - FontUtils.tickTextWidth(entry.getValue());
				float y = (entry.getKey() - minYfreq) / yFreqRange * plotHeight + yPlotBottom - (FontUtils.tickTextHeight / 2.0f);
				FontUtils.drawTickText(entry.getValue(), (int) x, (int) y);
			}
			
			// draw the left y-axis title, if space is available
			if(yLeftYaxisTitleLeft >= yPlotBottom) {
				float x = xLeftYaxisTitleTextBaseline;
				float y = yLeftYaxisTitleLeft;
				FontUtils.drawYaxisText(frequencyYaxisTitle, (int) x, (int) y, 90);
			}
			
		}
		
		// draw legend
		float xOffset = xLegendBorderLeft + Theme.lineWidth + Theme.legendTextPadding;
		for(Dataset dataset : datasets) {
			gl.glBegin(GL2.GL_QUADS);
			gl.glColor3f(dataset.color.getRed()/255.0f, dataset.color.getGreen()/255.0f, dataset.color.getBlue()/255.0f);
				gl.glVertex2f(xOffset,                              yLegendTextBaseline);
				gl.glVertex2f(xOffset,                              yLegendTextTop);
				gl.glVertex2f(xOffset + FontUtils.legendTextHeight, yLegendTextTop);
				gl.glVertex2f(xOffset + FontUtils.legendTextHeight, yLegendTextBaseline);
			gl.glEnd();
			
			xOffset += FontUtils.legendTextHeight + Theme.legendTextPadding;
			
			FontUtils.drawLegendText(dataset.name, (int) xOffset, (int) yLegendTextBaseline);
			xOffset += FontUtils.legendTextWidth(dataset.name) + Theme.legendNamesPadding;
		}
		xOffset -= Theme.legendNamesPadding;
		gl.glBegin(GL2.GL_LINE_LOOP);
		gl.glColor3f(0.0f, 0.0f, 0.0f);
			gl.glVertex2f(xLegendBorderLeft,           yLegendBorderBottom);
			gl.glVertex2f(xLegendBorderLeft,           yLegendBorderTop);
			gl.glVertex2f(xLegendBorderLeft + xOffset, yLegendBorderTop);
			gl.glVertex2f(xLegendBorderLeft + xOffset, yLegendBorderBottom);
		gl.glEnd();
		
		// draw the x-axis title, shifting it to the right if the legend gets in the way
		{
			float x = xXaxisTitleLeft > xOffset + Theme.legendTextPadding ? xXaxisTitleLeft : xLegendBorderLeft + xOffset + Theme.legendTextPadding;
			float y = yXaxisTitleTextBasline;
			FontUtils.drawXaxisText(xAxisTitle, (int) x, (int) y);
		}
		
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
