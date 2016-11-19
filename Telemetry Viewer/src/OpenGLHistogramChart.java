import java.util.Map;

import com.jogamp.opengl.GL2;

/**
 * Renders a Histogram.
 */
@SuppressWarnings("serial")
public class OpenGLHistogramChart extends PositionedChart {
	
	Samples[] samples;
	int[][] bins;
	AutoScale autoscale;
	
	int binCount;
	
	public final int XSCALE_AUTOSCALE = 0;
	public final int XSCALE_FIXED = 1;
	public final int XSCALE_CENTERED = 2;
	int xScale;
	float fixedMinX;
	float fixedMaxX;
	float fixedCenterX;
	
	public final int YSCALE_AUTOSCALE = 0;
	public final int YSCALE_FIXED_FREQUENCY = 1;
	public final int YSCALE_FIXED_RELATIVE_FREQUENCY = 2;
	int yScale;
	float fixedYscaleFrequency;
	float fixedYscaleRelativeFrequency;
	
	boolean yAxisShowsFrequency;
	boolean yAxisShowsRelativeFrequency;
	
	public static ChartDescriptor getDescriptor() {
		
		return new ChartDescriptor() {
			
			@Override public String toString()        { return "Histogram Chart"; }
			@Override public int getMinimumDuration() { return 2; }
			@Override public int getDefaultDuration() { return 150000; }
			@Override public int getMaximumDuration() { return Integer.MAX_VALUE; }
			@Override public String[] getInputNames() { return null; }
			
			@Override public PositionedChart createChart(int x1, int y1, int x2, int y2, int chartDuration, Dataset[] chartInputs) {
				return new OpenGLHistogramChart(x1, y1, x2, y2, chartDuration, chartInputs);
			}
			
		};
		
	}
	
	@Override public String toString() {
		
		return "Histogram Chart";
		
	}
	
	public OpenGLHistogramChart(int x1, int y1, int x2, int y2, int chartDuration, Dataset[] chartInputs) {
		
		super(x1, y1, x2, y2, chartDuration, chartInputs);
			
		binCount = 60;
		
		samples = new Samples[datasets.length];
		for(int i = 0; i < samples.length; i++)
			samples[i] = new Samples();
		
		bins = new int[datasets.length][binCount];
		
		autoscale = new AutoScale(AutoScale.MODE_EXPONENTIAL, 30, 0.20f);
	
		xScale = XSCALE_AUTOSCALE;

//		xScale = XSCALE_CENTERED;
//		fixedCenterX = 0;	

//		xScale = XSCALE_FIXED;
//		fixedMinX = -1.5f;
//		fixedMaxX =  1.5f;
		
		yScale = YSCALE_AUTOSCALE;
		
//		yScale = YSCALE_FIXED_FREQUENCY;
//		fixedYscaleFrequency = 25000;
		
//		yScale = YSCALE_FIXED_RELATIVE_FREQUENCY;
//		fixedYscaleRelativeFrequency = 0.35f;

		yAxisShowsFrequency = true;
		yAxisShowsRelativeFrequency = true;

	}
	
	@Override public void drawChart(GL2 gl, int width, int height) {
		
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
		int endIndex = Controller.getSamplesCount(datasets) - 1;
		int startIndex = endIndex - duration + 1;
		if(startIndex < 0)
			startIndex = 0;
		
		int sampleCount = endIndex - startIndex + 1;
		if(sampleCount < 2)
			return;
		
		for(int datasetN = 0; datasetN < samples.length; datasetN++)
			datasets[datasetN].getSamples(startIndex, endIndex, samples[datasetN]);

		// determine the x axis scale
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
		
		float minX = 0;
		float maxX = 0;
		if(xScale == XSCALE_AUTOSCALE) {
			minX = trueMinX;
			maxX = Math.nextUp(trueMaxX); // increment because the bins are >=min, <max
		} else if(xScale == XSCALE_FIXED) {
			minX = fixedMinX;
			maxX = Math.nextUp(fixedMaxX); // increment because the bins are >=min, <max
		} else if(xScale == XSCALE_CENTERED) {
			float leftHalf  = (float) Math.abs(fixedCenterX - trueMinX);
			float rightHalf = (float) Math.abs(fixedCenterX - trueMaxX);
			float half = Float.max(leftHalf, rightHalf);
			minX = fixedCenterX - half;
			maxX = Math.nextUp(fixedCenterX + half); // increment because the bins are >=min, <max
		}
		float range = maxX - minX;
		float binSize = range / (float) binCount;

		// reset and fill the bins
		for(int datasetN = 0; datasetN < datasets.length; datasetN++)
			for(int binN = 0; binN < binCount; binN++)
				bins[datasetN][binN] = 0;
			
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
		
		float minYfreq = 0;
		float maxYfreq = maxBinSize; // frequency, not relative frequency
		float maxYrelFreq = maxYfreq / (float) sampleCount;
		
		if(yScale == YSCALE_AUTOSCALE) {
			autoscale.update(minYfreq, maxYfreq);
			maxYfreq = autoscale.getMax();
			maxYrelFreq = maxYfreq / (float) sampleCount;
		} else if(yScale == YSCALE_FIXED_FREQUENCY) {
			maxYfreq = fixedYscaleFrequency;
			maxYrelFreq = maxYfreq / (float) sampleCount;
		} else if(yScale == YSCALE_FIXED_RELATIVE_FREQUENCY) {
			maxYfreq = fixedYscaleRelativeFrequency * sampleCount;
			maxYrelFreq = fixedYscaleRelativeFrequency;
		}
		
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
		Map<Float, String> yDivisionsRelativeFrequency = ChartUtils.getYdivisions125(plotHeight, minYfreq, maxYrelFreq);
		
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
				float y = entry / maxYfreq * plotHeight + yPlotBottom;
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
				float y = entry.getKey() / maxYfreq * plotHeight + yPlotBottom - (FontUtils.tickTextHeight / 2.0f);
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
				float y = entry / maxYrelFreq * plotHeight + yPlotBottom;
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
				float y = entry.getKey() / maxYrelFreq * plotHeight + yPlotBottom - (FontUtils.tickTextHeight / 2.0f);
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
				float y = entry / maxYfreq * plotHeight + yPlotBottom;
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
				float y = entry.getKey() / maxYfreq * plotHeight + yPlotBottom - (FontUtils.tickTextHeight / 2.0f);
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
		float xOffset = xLegendBorderLeft + Theme.strokeWidth + Theme.legendTextPadding;
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
		
		// draw the bins
		for(int datasetN = 0; datasetN < datasets.length; datasetN++) {
			
			for(int binN = 0; binN < binCount; binN++) {
				
				float min = minX + (binSize *  binN);      // inclusive
				float max = minX + (binSize * (binN + 1)); // exclusive
				float center = (max + min) / 2f;
				
				float xBarCenter = ((center - minX) / range * plotWidth) + xPlotLeft;
				float yBarTop = (float) bins[datasetN][binN] / maxYfreq * plotHeight + yPlotBottom;
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
