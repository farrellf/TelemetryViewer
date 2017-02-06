import java.util.Map;

import javax.swing.JPanel;

import com.jogamp.opengl.GL2;

/**
 * Renders a frequency domain chart, using one of three possible visualizations: Live View, Waveform View, or Waterfall View.
 * 
 * Live View renders a single DFT of the most recent samples. This is a line chart.
 * Waveform View renders a sequence of DFTs as a 2D histogram. This is basically a "long exposure photo" of Live View.
 * Waterfall View renders a sequence of DFTs as a sequence of rows. Each row is one DFT, allowing you to see how the DFTs have changed over time.
 */
@SuppressWarnings("serial")
public class OpenGLFrequencyDomainChart extends PositionedChart {
	
	String    type; // "Live View" or "Waveform View" or "Waterfall View"
	
	AutoScale autoscalePower;
	boolean   autoscaleMinPower;
	boolean   autoscaleMaxPower;
	float     manualMinPower;
	float     manualMaxPower;
	
	int       totalSampleCount;
	int       waveformRowCount;
	OpenGLFrequencyDomainCache cache;
	
	public static ChartFactory getFactory() {
		
		return new ChartFactory() {
			
			WidgetDatasets datasetsWidget;
			WidgetTextfieldsOptionalMinMax minMaxWidget;
			WidgetFrequencyDomainType typeWidget;
			
			@Override public String toString() { return "Frequency Domain Chart"; }
			
			@Override public JPanel[] getWidgets() {

				datasetsWidget = new WidgetDatasets();
				minMaxWidget   = new WidgetTextfieldsOptionalMinMax("Power", 0.001f, 1.0f, Float.MIN_VALUE, Float.MAX_VALUE);
				typeWidget     = new WidgetFrequencyDomainType(1000, 100000, 5, 5000000, 60, 2, 1000);
	
				JPanel[] widgets = new JPanel[5];
				
				widgets[0] = datasetsWidget;
				widgets[1] = null;
				widgets[2] = minMaxWidget;
				widgets[3] = null;
				widgets[4] = typeWidget;

				return widgets;
				
			}
			
			@Override public int getMinimumSampleCount() {
				
				return 5;
				
			}
			
			@Override public PositionedChart createChart(int x1, int y1, int x2, int y2) {

				Dataset[] datasets   = datasetsWidget.getDatasets();
				boolean autoscaleMin = minMaxWidget.isMinimumAutomatic();
				float manualMin      = minMaxWidget.getMinimumValue();
				boolean autoscaleMax = minMaxWidget.isMaximumAutomatic();
				float manualMax      = minMaxWidget.getMaximumValue();
				String type          = typeWidget.getType();
				int sampleCount      = typeWidget.getSampleCount();
				int totalSampleCount = typeWidget.getTotalSampleCount();
				int waveformRowCount = typeWidget.getRowCount();
				
				if(datasets.length == 0)
					return null;
				
				OpenGLFrequencyDomainChart chart = new OpenGLFrequencyDomainChart(x1, y1, x2, y2, sampleCount, datasets);
				chart.setPowerRange(autoscaleMin, manualMin, autoscaleMax, manualMax);
				chart.setType(type, totalSampleCount, waveformRowCount);
				
				return chart;
				
			}
			
			@Override public PositionedChart importChart(int x1, int y1, int x2, int y2, Dataset[] datasets, int sampleCount, String[] lines, int firstLineNumber) {
				
				if(lines.length != 7)
					throw new AssertionError("Line " + firstLineNumber + ": Invalid Frequency Domain Chart configuration section.");
				
				int totalSampleCount =     (int) ChartUtils.parse(firstLineNumber + 0, lines[0], "total sample count = %d");
				boolean autoscaleMin = (boolean) ChartUtils.parse(firstLineNumber + 1, lines[1], "autoscale minimum power = %b");
				float manualMin      =   (float) ChartUtils.parse(firstLineNumber + 2, lines[2], "manual minimum power = %f");
				boolean autoscaleMax = (boolean) ChartUtils.parse(firstLineNumber + 3, lines[3], "autoscale maximum power = %b");
				float manualMax      =   (float) ChartUtils.parse(firstLineNumber + 4, lines[4], "manual maximum power = %f");
				String type          =  (String) ChartUtils.parse(firstLineNumber + 5, lines[5], "type = %s");
				int waveformRowCount =     (int) ChartUtils.parse(firstLineNumber + 6, lines[6], "waveform view row count = %d");
				
				if(!type.equals("Live View") && !type.equals("Waveform View") && !type.equals("Waterfall View"))
					throw new AssertionError("Line " + (firstLineNumber + 5) + ": Invalid Frequency Domain Chart type.");
				
				OpenGLFrequencyDomainChart chart = new OpenGLFrequencyDomainChart(x1, y1, x2, y2, sampleCount, datasets);
				chart.setPowerRange(autoscaleMin, manualMin, autoscaleMax, manualMax);
				chart.setType(type, totalSampleCount, waveformRowCount);
				
				return chart;
				
			}
			
		};
		
	}
	
	@Override public String[] exportChartSettings() {
		
		String[] lines = new String[7];
		
		lines[0] = "total sample count = " + totalSampleCount;
		lines[1] = "autoscale minimum power = " + autoscaleMinPower;
		lines[2] = "manual minimum power = " + manualMinPower;
		lines[3] = "autoscale maximum power = " + autoscaleMaxPower;
		lines[4] = "manual maximum power = " + manualMaxPower;
		lines[5] = "type = " + type;
		lines[6] = "waveform view row count = " + waveformRowCount;
		
		return lines;
		
	}
	
	@Override public String toString() {
		
		return "Frequency Domain Chart";
		
	}
	
	public OpenGLFrequencyDomainChart(int x1, int y1, int x2, int y2, int chartDuration, Dataset[] chartInputs) {
		
		super(x1, y1, x2, y2, chartDuration, chartInputs);
		
		type = "Live View";
		
		autoscalePower = new AutoScale(AutoScale.MODE_EXPONENTIAL, 90, 0.20f);
		autoscaleMinPower = true;
		autoscaleMaxPower = true;
		manualMinPower = 1.0e-30f;
		manualMaxPower = 1.0f;
		
		totalSampleCount = 5;
		waveformRowCount = 60;

	}
	
	public void setPowerRange(boolean autoscaleMinimum, float manualMinimum, boolean autoscaleMaximum, float manualMaximum) {
		
		autoscaleMinPower = autoscaleMinimum;
		autoscaleMaxPower = autoscaleMaximum;
		manualMinPower = manualMinimum;
		manualMaxPower = manualMaximum;
		
	}
	
	public void setType(String typeString, int totalSampleCount, int waveformRowCount) {
		
		if(typeString.equals("Live View") || typeString.equals("Waveform View") || typeString.equals("Waterfall View"))
			type = typeString;
		
		this.totalSampleCount = totalSampleCount;
		
		this.waveformRowCount = waveformRowCount;
		
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
		
		// scale the DFT window size by the current zoom level
		int dftWindowLength = (int) (duration * zoomLevel);
		
		// only draw if we can
		if(lastSampleNumber < duration)
			return; // not enough samples
		if(dftWindowLength < 5)
			return; // zoomed in too much
		
		// calculate the DFTs
		if(cache == null)
			cache = new OpenGLFrequencyDomainCache(gl);
		cache.calculateDfts(lastSampleNumber, dftWindowLength, totalSampleCount, datasets, type);
		
		// calculate the domain
		float plotMinX = cache.getMinHz();
		float plotMaxX = cache.getMaxHz();
		float domain = plotMaxX - plotMinX;
		
		// calculate the range and ensure it's >0
		// for "Waterfall View" the y-axis is time
		// for "Live View" and "Waveform View" the y-axis is power
		float plotMinTime = 0;
		float plotMaxTime = (float) totalSampleCount / (float) Controller.getSampleRate();

		float plotMinPower = cache.getMinPower();
		float plotMaxPower = cache.getMaxPower();
		if(plotMinPower == plotMaxPower) {
			float value = plotMinPower;
			plotMinPower = value - 0.001f;
			plotMaxPower = value + 0.001f;
		}
		autoscalePower.update(plotMinPower, plotMaxPower);
		
		if(!autoscaleMinPower)
			plotMinPower = (float) Math.log10(manualMinPower);
		else if(autoscaleMinPower && !type.equals("Waterfall View"))
			plotMinPower = autoscalePower.getMin();
		
		if(!autoscaleMaxPower)
			plotMaxPower = (float) Math.log10(manualMaxPower);
		else if(autoscaleMaxPower && !type.equals("Waterfall View"))
			plotMaxPower = autoscalePower.getMax();

		float plotMinY = type.equals("Waterfall View") ? plotMinTime : plotMinPower;
		float plotMaxY = type.equals("Waterfall View") ? plotMaxTime : plotMaxPower;
		float plotRange = plotMaxY - plotMinY;
		
		// calculate x and y positions of everything
		String xAxisTitle = "Frequency (Hertz)";
		String yAxisTitle = type.equals("Waterfall View") ? "Time (Seconds)" : "Power (Watts)";
		String dftWindowLengthText = dftWindowLength + " sample rectangular window";
		String dftWindowCountText = (totalSampleCount / dftWindowLength) + " windows (total of " + ((totalSampleCount / dftWindowLength) * dftWindowLength) + " samples)";
		String minPowerText = "Power Range: 1e" + Math.round(plotMinPower);
		String maxPowerText = "1e" + Math.round(plotMaxPower);
		
		float yPowerTextBaseline = Theme.perimeterPadding;
		float yPowerTextTop = yPowerTextBaseline + FontUtils.tickTextHeight;
		float yDftInfoLine2baseline = type.equals("Waveform View") ? Theme.perimeterPadding : yPowerTextTop + Theme.tickTextPadding;
		float yDftInfoLine2top = yDftInfoLine2baseline + FontUtils.tickTextHeight;
		float yDftInfoLine1baseline = type.equals("Live View") ? Theme.perimeterPadding : yDftInfoLine2top + Theme.tickTextPadding;
		float yDftInfoLine1top = yDftInfoLine1baseline + FontUtils.tickTextHeight;
		float xDftInfoLine2left = width - Theme.perimeterPadding - FontUtils.tickTextWidth(dftWindowCountText);
		float xDftInfoLine1left = width - Theme.perimeterPadding - FontUtils.tickTextWidth(dftWindowLengthText);
		float xMaxPowerTextLeft = width - Theme.perimeterPadding - FontUtils.tickTextWidth(maxPowerText);
		float xPowerScaleRight = xMaxPowerTextLeft - Theme.tickTextPadding;
		float xPowerScaleLeft = xPowerScaleRight - (100 * Controller.getDisplayScalingFactor());
		float xMinPowerTextLeft = xPowerScaleLeft - Theme.tickTextPadding - FontUtils.tickTextWidth(minPowerText);
		
		float xLegendBorderLeft = Theme.perimeterPadding;
		float yLegendBorderBottom = Theme.perimeterPadding;
		float yLegendTextBaseline = yLegendBorderBottom + Theme.legendTextPadding;
		float yLegendTextTop = yLegendTextBaseline + FontUtils.legendTextHeight;
		float yLegendBorderTop = yLegendTextTop + Theme.legendTextPadding;
		
		float yXaxisTitleTextBasline = Theme.perimeterPadding;
		float yXaxisTitleTextTop = yXaxisTitleTextBasline + FontUtils.xAxisTextHeight;
		
		float yXaxisTickTextBaseline = Float.max(yLegendBorderTop + Theme.legendTextPadding, yXaxisTitleTextTop + Theme.legendTextPadding);
		yXaxisTickTextBaseline = Float.max(yXaxisTickTextBaseline, yDftInfoLine1top + Theme.legendTextPadding + Theme.legendTextPadding);
		float yXaxisTickTextTop = yXaxisTickTextBaseline + FontUtils.tickTextHeight;
		float yXaxisTickBottom = yXaxisTickTextTop + Theme.tickTextPadding;
		float yXaxisTickTop = (int) (yXaxisTickBottom + Theme.tickLength);
		
		float yPlotBottom = yXaxisTickTop;
		float yPlotTop = (int) (height - Theme.perimeterPadding);
		float plotHeight = yPlotTop - yPlotBottom;
		if(plotHeight < 1.0f)
			return;
		
		Map<Float, String> yDivisions = type.equals("Waterfall View") ? ChartUtils.getYdivisions125(plotHeight, plotMinY, plotMaxY) : ChartUtils.getLogYdivisions(plotHeight, plotMinY, plotMaxY);
		
		float maxYtickTextWidth = 0;
		for(String text : yDivisions.values()) {
			float w = FontUtils.tickTextWidth(text); 
			if(w > maxYtickTextWidth)
				maxYtickTextWidth = w;
		}
		
		float xYaxisTitleTextTop = Theme.perimeterPadding;
		float xYaxisTitleTextBaseline = xYaxisTitleTextTop + FontUtils.yAxisTextHeight;
		
		float xYaxisTickTextLeft = xYaxisTitleTextBaseline + Theme.tickTextPadding;
		float xYaxisTickTextRight = xYaxisTickTextLeft + maxYtickTextWidth;
		float xYaxisTickLeft = xYaxisTickTextRight + Theme.tickTextPadding;
		float xYaxisTickRight = (int) (xYaxisTickLeft + Theme.tickLength);
		
		float xPlotLeft = xYaxisTickRight;
		float xPlotRight = (int) (width - Theme.perimeterPadding);
		float plotWidth = xPlotRight - xPlotLeft;
		if(plotWidth < 1.0f)
			return;
		
		Map<Float, String> xDivisions = ChartUtils.getFloatXdivisions125(plotWidth, plotMinX, plotMaxX);
		
		float yYaxisTitleLeft = yPlotBottom + (plotHeight / 2.0f) - (FontUtils.yAxisTextWidth(yAxisTitle) / 2.0f);
		
		// draw plot background
		gl.glBegin(GL2.GL_QUADS);
		gl.glColor4fv(Theme.plotBackgroundColor, 0);
			gl.glVertex2f(xPlotLeft,  yPlotTop);
			gl.glVertex2f(xPlotRight, yPlotTop);
			gl.glVertex2f(xPlotRight, yPlotBottom);
			gl.glVertex2f(xPlotLeft,  yPlotBottom);
		gl.glEnd();
		
		// draw the vertical division lines
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
		
		// draw the vertical division line text
		for(Map.Entry<Float,String> entry : xDivisions.entrySet()) {
			float x = (entry.getKey() - plotMinX) / domain * plotWidth + xPlotLeft - (FontUtils.tickTextWidth(entry.getValue()) / 2.0f);
			float y = yXaxisTickTextBaseline;
			FontUtils.drawTickText(entry.getValue(), (int) x, (int) y);
		}
		
		// draw the horizontal division lines
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
		
		// draw the horizontal division line text
		for(Map.Entry<Float,String> entry : yDivisions.entrySet()) {
			float x = xYaxisTickTextRight - FontUtils.tickTextWidth(entry.getValue());
			float y = (entry.getKey() - plotMinY) / plotRange * plotHeight + yPlotBottom - (FontUtils.tickTextHeight / 2.0f);
			FontUtils.drawTickText(entry.getValue(), (int) x, (int) y);
		}
		
		// draw the legend
		float xOffset = xLegendBorderLeft + Theme.lineWidth + Theme.legendTextPadding;
		for(Dataset dataset : datasets) {
			gl.glBegin(GL2.GL_QUADS);
			gl.glColor4f(dataset.color.getRed()/255.0f, dataset.color.getGreen()/255.0f, dataset.color.getBlue()/255.0f, 1);
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
		gl.glColor4fv(Theme.legendOutlineColor, 0);
			gl.glVertex2f(xLegendBorderLeft,           yLegendBorderBottom);
			gl.glVertex2f(xLegendBorderLeft,           yLegendBorderTop);
			gl.glVertex2f(xLegendBorderLeft + xOffset, yLegendBorderTop);
			gl.glVertex2f(xLegendBorderLeft + xOffset, yLegendBorderBottom);
		gl.glEnd();
		
		// draw the DFT info text if space is available
		float xDftInfoTextLeft = type.equals("Live View") ? xDftInfoLine1left : Float.min(Float.min(xDftInfoLine1left, xDftInfoLine2left), xMinPowerTextLeft);
		float xLegendTextRight = xLegendBorderLeft + xOffset + Theme.legendTextPadding;
		boolean spaceForDftInfoText = xDftInfoTextLeft > xLegendTextRight;
		if(spaceForDftInfoText) {
			
			FontUtils.drawTickText(dftWindowLengthText, (int) xDftInfoLine1left, (int) yDftInfoLine1baseline);
			
			if(type.equals("Waveform View") || type.equals("Waterfall View"))
				FontUtils.drawTickText(dftWindowCountText, (int) xDftInfoLine2left, (int) yDftInfoLine2baseline);
			
			if(type.equals("Waterfall View")) {
				
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
		float widthXtitle = FontUtils.xAxisTextWidth(xAxisTitle);
		if(xDftInfoTextLeft - xLegendTextRight > widthXtitle) {
			float x = xLegendTextRight + ((xDftInfoTextLeft - xLegendTextRight) / 2.0f) - (widthXtitle / 2.0f);
			float y = yXaxisTitleTextBasline;
			FontUtils.drawXaxisText(xAxisTitle, (int) x, (int) y);
		}
		
		// draw the y-axis title if space is available
		if(yYaxisTitleLeft >= yPlotBottom) {
			float x = xYaxisTitleTextBaseline;
			float y = yYaxisTitleLeft;
			FontUtils.drawYaxisText(yAxisTitle, (int) x, (int) y, 90);
		}
		
		// draw the DFTs
		if(type.equals("Live View"))
			cache.renderLiveView((int) xPlotLeft, (int) yPlotBottom, (int) plotWidth, (int) plotHeight, plotMinPower, plotMaxPower, gl, datasets);
		else if(type.equals("Waveform View"))
			cache.renderWaveformView((int) xPlotLeft, (int) yPlotBottom, (int) plotWidth, (int) plotHeight, plotMinPower, plotMaxPower, gl, datasets, waveformRowCount);
		else if(type.equals("Waterfall View"))
			cache.renderWaterfallView((int) xPlotLeft, (int) yPlotBottom, (int) plotWidth, (int) plotHeight, plotMinPower, plotMaxPower, gl, datasets);

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
