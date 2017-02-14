import java.util.Map;

import javax.swing.JPanel;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.util.Animator;

/**
 * Renders a time-domain line chart, and uses off-screen framebuffers to cache pixels.
 * The range of the y-axis can be constant, or autoscaled by the min and max values from the chart's samples.
 */
@SuppressWarnings("serial")
public class OpenGLTimeDomainChartCached extends PositionedChart {
	
	OpenGLTimeDomainSlice[] slices;
	final int sliceWidth = 32;
	Animator  animator;
	AutoScale autoscale;
	boolean   autoscaleMin;
	boolean   autoscaleMax;
	float     manualMin;
	float     manualMax;
	
	public static ChartFactory getFactory() {
		
		return new ChartFactory() {
			
			WidgetDatasets datasetsWidget;
			WidgetTextfieldInteger sampleCountWidget;
			WidgetTextfieldsOptionalMinMax minMaxWidget;
			
			@Override public String toString() { return "Time Domain Chart (Cached)"; }
			
			@Override public JPanel[] getWidgets() {

				datasetsWidget    = new WidgetDatasets();
				sampleCountWidget = new WidgetTextfieldInteger("Sample Count", 1000, 5, Integer.MAX_VALUE);
				minMaxWidget      = new WidgetTextfieldsOptionalMinMax("Y-Axis", -1.0f, 1.0f, -Float.MAX_VALUE, Float.MAX_VALUE);
	
				JPanel[] widgets = new JPanel[5];
				
				widgets[0] = datasetsWidget;
				widgets[1] = null;
				widgets[2] = sampleCountWidget;
				widgets[3] = null;
				widgets[4] = minMaxWidget;

				return widgets;
				
			}
			
			@Override public int getMinimumSampleCount() {
				return 5;
			}
			
			@Override public PositionedChart createChart(int x1, int y1, int x2, int y2) {

				int sampleCount      = sampleCountWidget.getValue();
				Dataset[] datasets   = datasetsWidget.getDatasets();
				boolean autoscaleMin = minMaxWidget.isMinimumAutomatic();
				float manualMin      = minMaxWidget.getMinimumValue();
				boolean autoscaleMax = minMaxWidget.isMaximumAutomatic();
				float manualMax      = minMaxWidget.getMaximumValue();
				
				if(datasets.length == 0)
					return null;
				
				OpenGLTimeDomainChartCached chart = new OpenGLTimeDomainChartCached(x1, y1, x2, y2, sampleCount, datasets);
				chart.setYaxisRange(autoscaleMin, manualMin, autoscaleMax, manualMax);
				
				return chart;
				
			}
			
			@Override public PositionedChart importChart(int x1, int y1, int x2, int y2, Dataset[] datasets, int sampleCount, String[] lines, int firstLineNumber) {
				
				if(lines.length != 4)
					throw new AssertionError("Line " + firstLineNumber + ": Invalid Time Domain Chart (Cached) configuration section.");
				
				boolean autoscaleMin = (boolean) ChartUtils.parse(firstLineNumber + 0, lines[0], "autoscale minimum = %b");
				float manualMin      =   (float) ChartUtils.parse(firstLineNumber + 1, lines[1], "manual minimum = %f");
				boolean autoscaleMax = (boolean) ChartUtils.parse(firstLineNumber + 2, lines[2], "autoscale maximum = %b");
				float manualMax      =   (float) ChartUtils.parse(firstLineNumber + 3, lines[3], "manual maximum = %f");
				
				OpenGLTimeDomainChartCached chart = new OpenGLTimeDomainChartCached(x1, y1, x2, y2, sampleCount, datasets);
				chart.setYaxisRange(autoscaleMin, manualMin, autoscaleMax, manualMax);
				
				return chart;
				
			}
			
		};
		
	}
	
	@Override public String[] exportChartSettings() {
		
		String[] lines = new String[4];
		
		lines[0] = "autoscale minimum = " + autoscaleMin;
		lines[1] = "manual minimum = " + manualMin;
		lines[2] = "autoscale maximum = " + autoscaleMax;
		lines[3] = "manual maximum = " + manualMax;
		
		return lines;
		
	}
	
	@Override public String toString() {
		
		return "Time Domain Chart (Cached)";
		
	}
	
	public OpenGLTimeDomainChartCached(int x1, int y1, int x2, int y2, int chartDuration, Dataset[] chartInputs) {
		
		super(x1, y1, x2, y2, chartDuration, chartInputs);
		
		slices = new OpenGLTimeDomainSlice[0];
		
		autoscale = new AutoScale(AutoScale.MODE_STICKY, 1, 0.10f);
		autoscaleMin = true;
		autoscaleMax = true;

	}
	
	public void setYaxisRange(boolean autoscaleMinimum, float manualMinimum, boolean autoscaleMaximum, float manualMaximum) {
		
		autoscaleMin = autoscaleMinimum;
		autoscaleMax = autoscaleMaximum;
		manualMin = manualMinimum;
		manualMax = manualMaximum;
		
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
		
		// calculate domain
		int sampleCount = lastSampleNumber + 1;
		int plotMaxX = lastSampleNumber;
		int plotMinX = plotMaxX - (int) (duration * zoomLevel) + 1;
		int minDomain = OpenGLTimeDomainChartCached.getFactory().getMinimumSampleCount() - 1;
		if(plotMaxX - plotMinX < minDomain) plotMinX = plotMaxX - minDomain;
		float domain = plotMaxX - plotMinX;
		
		if(plotMaxX < minDomain)
			return;
				
		// calculate range based on the true range of the *previous* frame (so we can use that cached data)
		float plotMinY = 0;
		float plotMaxY = 0;
		if(slices.length > 0) {
			plotMinY = slices[0].sliceMinY;
			plotMaxY = slices[0].sliceMaxY;
		}
		for(int i = 1; i < slices.length; i++) {
			if(slices[i].sliceMinY < plotMinY) plotMinY = slices[i].sliceMinY;
			if(slices[i].sliceMaxY > plotMaxY) plotMaxY = slices[i].sliceMaxY;
		}

		// ensure range is >0
		if(plotMinY == plotMaxY) {
			float value = plotMinY;
			plotMinY = value - 0.001f;
			plotMaxY = value + 0.001f;
		}
		autoscale.update(plotMinY, plotMaxY);
		plotMaxY = autoscaleMax ? autoscale.getMax() : manualMax;
		plotMinY = autoscaleMin ? autoscale.getMin() : manualMin;
		float plotRange = plotMaxY - plotMinY;
		
		// calculate x and y positions of everything
		String xAxisTitle = "Sample Number";
		String yAxisTitle = datasets[0].unit;
		float xLegendBorderLeft = Theme.tilePadding;
		float yLegendBorderBottom = Theme.tilePadding;
		float yLegendTextBaseline = yLegendBorderBottom + Theme.legendTextPadding;
		float yLegendTextTop = yLegendTextBaseline + FontUtils.legendTextHeight;
		float yLegendBorderTop = yLegendTextTop + Theme.legendTextPadding;
		
		float yXaxisTitleTextBasline = Theme.tilePadding;
		float yXaxisTitleTextTop = yXaxisTitleTextBasline + FontUtils.xAxisTextHeight;
		
		float yXaxisTickTextBaseline = Float.max(yLegendBorderTop + Theme.legendTextPadding, yXaxisTitleTextTop + Theme.legendTextPadding);
		float yXaxisTickTextTop = yXaxisTickTextBaseline + FontUtils.tickTextHeight;
		float yXaxisTickBottom = yXaxisTickTextTop + Theme.tickTextPadding;
		float yXaxisTickTop = yXaxisTickBottom + Theme.tickLength;
		
		float yPlotBottom = yXaxisTickTop;
		float yPlotTop = height - Theme.tilePadding;
		float plotHeight = yPlotTop - yPlotBottom;
		if(plotHeight < 1.0f)
			return;
		
		Map<Float, String> yDivisions = ChartUtils.getYdivisions125(plotHeight, plotMinY, plotMaxY);
		
		float maxYtickTextWidth = 0;
		for(String text : yDivisions.values()) {
			float w = FontUtils.tickTextWidth(text);
			if(w > maxYtickTextWidth)
				maxYtickTextWidth = w;
		}
		
		float xYaxisTitleTextTop = Theme.tilePadding;
		float xYaxisTitleTextBaseline = xYaxisTitleTextTop + FontUtils.yAxisTextHeight;
		
		float xYaxisTickTextLeft = xYaxisTitleTextBaseline + Theme.tickTextPadding;
		float xYaxisTickTextRight = xYaxisTickTextLeft + maxYtickTextWidth;
		float xYaxisTickLeft = xYaxisTickTextRight + Theme.tickTextPadding;
		float xYaxisTickRight = xYaxisTickLeft + Theme.tickLength;
		
		float xPlotLeft = xYaxisTickRight;
		float xPlotRight = width - Theme.tilePadding;
		float plotWidth = xPlotRight - xPlotLeft;
		if(plotWidth < 1.0f)
			return;
		
		Map<Integer, String> xDivisions = ChartUtils.getXdivisions125(plotWidth, plotMinX, plotMaxX);
		
		float xXaxisTitleLeft = xPlotLeft +   (plotWidth  / 2.0f) - (FontUtils.xAxisTextWidth(xAxisTitle) / 2.0f);
		float yYaxisTitleLeft = yPlotBottom + (plotHeight / 2.0f) - (FontUtils.yAxisTextWidth(yAxisTitle) / 2.0f);
		
		// draw plot background
		gl.glBegin(GL2.GL_QUADS);
		gl.glColor4fv(Theme.plotBackgroundColor, 0);
			gl.glVertex2f(xPlotLeft,  yPlotTop);
			gl.glVertex2f(xPlotRight, yPlotTop);
			gl.glVertex2f(xPlotRight, yPlotBottom);
			gl.glVertex2f(xPlotLeft,  yPlotBottom);
		gl.glEnd();
		
		// determine which slices are needed
		int lastSliceIndex  = (int) Math.floor((double) (sampleCount - 1) / (double) domain * Math.floor(plotWidth) / (double) sliceWidth);
		int firstSliceIndex = lastSliceIndex - (int) Math.ceil(plotWidth / sliceWidth);
		if(firstSliceIndex < 0)
			firstSliceIndex = 0;

		// replace the slices array if the number of slices needs to change
		int sliceCount = (int)Math.ceil(plotWidth / sliceWidth) + 1;
		if(slices.length != sliceCount) {
			for(int i = 0; i < slices.length; i++)
				slices[i].freeResources(gl);
			slices = new OpenGLTimeDomainSlice[sliceCount];
			for(int i = 0; i < slices.length; i++)
				slices[i] = new OpenGLTimeDomainSlice(gl);
		}
		
		// update textures if needed, and draw the slices
		for(int i = firstSliceIndex; i <= lastSliceIndex; i++) {		
			slices[i % slices.length].updateSliceTexture(i, sliceWidth, (int) plotHeight, (int) plotWidth, domain, plotMinY, plotMaxY, sampleCount, datasets, xDivisions.keySet(), yDivisions.keySet(), gl);
			int x = (int) (xPlotRight + (i * sliceWidth) - ((double) (sampleCount - 1) / (double) domain * (int) plotWidth));
			int y = (int) yPlotBottom;
			slices[i % slices.length].renderSliceAt(x, y, gl);
		}
		
		// draw over the plot overhang
		gl.glBegin(GL2.GL_QUADS);
		gl.glColor4fv(Theme.backgroundColor, 0);
			gl.glVertex2f(0,         0);
			gl.glVertex2f(0,         height);
			gl.glVertex2f(xPlotLeft, height);
			gl.glVertex2f(xPlotLeft, 0);
			
			gl.glVertex2f(xPlotRight, 0);
			gl.glVertex2f(xPlotRight, height);
			gl.glVertex2f(width,      height);
			gl.glVertex2f(width,      0);
			
			gl.glVertex2f(0,     0);
			gl.glVertex2f(0,     yPlotBottom);
			gl.glVertex2f(width, yPlotBottom);
			gl.glVertex2f(width, 0);
			
			gl.glVertex2f(0,     yPlotTop);
			gl.glVertex2f(0,     height);
			gl.glVertex2f(width, height);
			gl.glVertex2f(width, yPlotTop);
		gl.glEnd();
		
		// redraw perimeter outline
		gl.glBegin(GL2.GL_LINE_LOOP);
		gl.glColor4fv(Theme.perimeterOutlineColor, 0);
			gl.glVertex2f(0,     0);
			gl.glVertex2f(0,     height);
			gl.glVertex2f(width, height);
			gl.glVertex2f(width, 0);
		gl.glEnd();
		
		// draw the vertical division line ticks
		gl.glBegin(GL2.GL_LINES);
		for(Integer xValue : xDivisions.keySet()) {
			float x = (float) (xValue - plotMinX) / domain * plotWidth + xPlotLeft;
			gl.glColor4fv(Theme.tickLinesColor, 0);
			gl.glVertex2f(x, yXaxisTickTop);
			gl.glVertex2f(x, yXaxisTickBottom);
		}
		gl.glEnd();
		
		// draw the vertical division line text
		for(Map.Entry<Integer,String> entry : xDivisions.entrySet()) {
			float x = (float) (entry.getKey() - plotMinX) / domain * plotWidth + xPlotLeft - (FontUtils.tickTextWidth(entry.getValue()) / 2.0f);
			float y = yXaxisTickTextBaseline;
			FontUtils.drawTickText(entry.getValue(), (int) x, (int) y);
		}
		
		// draw the horizontal division line ticks
		gl.glBegin(GL2.GL_LINES);
		for(Float entry : yDivisions.keySet()) {
			float y = (entry - plotMinY) / plotRange * plotHeight + yPlotBottom;
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
		gl.glColor4fv(Theme.legendOutlineColor, 0);
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
		
		// draw the y-axis title, if space is available
		if(yYaxisTitleLeft >= yPlotBottom) {
			float x = xYaxisTitleTextBaseline;
			float y = yYaxisTitleLeft;
			FontUtils.drawYaxisText(yAxisTitle, (int) x, (int) y, 90);
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
