import java.awt.Color;
import java.util.Map;
import com.jogamp.opengl.GL2;

/**
 * Renders a time-domain line chart.
 * 
 * User settings:
 *     Datasets to visualize.
 *     Sample count.
 *     Y-axis minimum value can be fixed or autoscaled.
 *     Y-axis maximum value can be fixed or autoscaled.
 *     X-axis title can be displayed.
 *     X-axis scale can be displayed.
 *     Y-axis title can be displayed.
 *     Y-axis scale can be displayed.
 *     Legend can be displayed.
 */
public class OpenGLTimeDomainChart extends PositionedChart {
	
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
	float yXaxisTickTextBaseline;
	float yXaxisTickTextTop;
	float yXaxisTickBottom;
	float yXaxisTickTop;
	
	// y-axis title
	boolean showYaxisTitle;
	float xYaxisTitleTextTop;
	float xYaxisTitleTextBaseline;
	String yAxisTitle;
	float yYaxisTitleTextLeft;
	
	// y-axis scale
	boolean showYaxisScale;
	Map<Float, String> yDivisions;
	float xYaxisTickTextRight;
	float xYaxisTickLeft;
	float xYaxisTickRight;
	AutoScale autoscale;
	boolean autoscaleYmin;
	boolean autoscaleYmax;
	float manualYmin;
	float manualYmax;
	
	boolean sampleCountMode;
	Plot plot = new Plot();
//	PlotSampleCountCached plot = new PlotSampleCountCached();
	
	// constraints
	static final int SampleCountDefault = 1000;
	static final int SampleCountMinimum = 5;
	static final int SampleCountMaximum = Integer.MAX_VALUE;

	static final float yAxisMinimumDefault = -1.0f;
	static final float yAxisMaximumDefault =  1.0f;
	static final float yAxisLowerLimit     = -Float.MAX_VALUE;
	static final float yAxisUpperLimit     =  Float.MAX_VALUE;
	
	// control widgets
	WidgetDatasets datasetsWidget;
	WidgetDuration durationWidget;
	WidgetTextfieldsOptionalMinMax minMaxWidget;
	WidgetCheckbox showXaxisTitleWidget;
	WidgetCheckbox showXaxisScaleWidget;
	WidgetCheckbox showYaxisTitleWidget;
	WidgetCheckbox showYaxisScaleWidget;
	WidgetCheckbox showLegendWidget;
	
	@Override public String toString() {
		
		return "Time Domain Chart";
		
	}
	
	public OpenGLTimeDomainChart(int x1, int y1, int x2, int y2) {
		
		super(x1, y1, x2, y2);
		
		autoscale = new AutoScale(AutoScale.MODE_EXPONENTIAL, 30, 0.10f);
		
		// create the control widgets and event handlers
		datasetsWidget = new WidgetDatasets(true, newDatasets -> datasets = newDatasets);
		
		durationWidget = new WidgetDuration(SampleCountDefault,
		                                    SampleCountMinimum,
		                                    SampleCountMaximum,
		                                    isSampleCount -> sampleCountMode = isSampleCount);
		
		minMaxWidget = new WidgetTextfieldsOptionalMinMax("Y-Axis",
		                                                  yAxisMinimumDefault,
		                                                  yAxisMaximumDefault,
		                                                  yAxisLowerLimit,
		                                                  yAxisUpperLimit,
		                                                  (newAutoscaleYmin, newManualYmin) -> { autoscaleYmin = newAutoscaleYmin; manualYmin = newManualYmin; },
		                                                  (newAutoscaleYmax, newManualYmax) -> { autoscaleYmax = newAutoscaleYmax; manualYmax = newManualYmax; });
		
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

		widgets = new Widget[13];
		
		widgets[0]  = datasetsWidget;
		widgets[1]  = null;
		widgets[2]  = durationWidget;
		widgets[3]  = null;
		widgets[4]  = minMaxWidget;
		widgets[5]  = null;
		widgets[6]  = showXaxisTitleWidget;
		widgets[7]  = showXaxisScaleWidget;
		widgets[8]  = null;
		widgets[9]  = showYaxisTitleWidget;
		widgets[10] = showYaxisScaleWidget;
		widgets[11] = null;
		widgets[12] = showLegendWidget;
		
	}
	
	@Override public void drawChart(GL2 gl, float[] chartMatrix, int width, int height, int lastSampleNumber, double zoomLevel, int mouseX, int mouseY) {
		
		boolean haveDatasets = datasets != null && datasets.length > 0;
		
		if(sampleCountMode)
			plot.acquireSamples(lastSampleNumber, zoomLevel, datasets, durationWidget.getSampleCount());
		else 
			plot.acquireMilliseconds(lastSampleNumber, zoomLevel, datasets, durationWidget.getMilliseconds());
		
		sampleCount = sampleCountMode ? durationWidget.getSampleCount() :
		                                plot.getPlotSampleCount();
		
		// calculate the plot range
		if(autoscaleYmin || autoscaleYmax) {
			Dataset.MinMax requiredRange = plot.getRequiredRange();
			autoscale.update(requiredRange.min, requiredRange.max);
		}
		float plotMaxY = autoscaleYmax ? autoscale.getMax() : manualYmax;
		float plotMinY = autoscaleYmin ? autoscale.getMin() : manualYmin;
		float plotRange = plotMaxY - plotMinY;
		
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
			xAxisTitle = plot.getTitle();
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
			
			legendBoxCoordinates = new float[datasets.length][4];
			xLegendNameLeft = new float[datasets.length];
			
			float xOffset = xLegendBorderLeft + (Theme.lineWidth / 2) + Theme.legendTextPadding;
			
			for(int i = 0; i < datasets.length; i++){
				legendBoxCoordinates[i][0] = xOffset;
				legendBoxCoordinates[i][1] = yLegendTextBaseline;
				legendBoxCoordinates[i][2] = xOffset + FontUtils.legendTextHeight;
				legendBoxCoordinates[i][3] = yLegendTextTop;
				
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
		
		if(showYaxisTitle) {
			xYaxisTitleTextTop = xPlotLeft;
			xYaxisTitleTextBaseline = xYaxisTitleTextTop + FontUtils.yAxisTextHeight;
			yAxisTitle = haveDatasets ? datasets[0].unit : "";
			yYaxisTitleTextLeft = yPlotBottom + (plotHeight / 2.0f) - (FontUtils.yAxisTextWidth(yAxisTitle) / 2.0f);
			
			xPlotLeft = xYaxisTitleTextBaseline + Theme.tickTextPadding;
			plotWidth = xPlotRight - xPlotLeft;
			
			if(showXaxisTitle && !showLegend)
				xXaxisTitleTextLeft = xPlotLeft + (plotWidth  / 2.0f) - (FontUtils.xAxisTextWidth(xAxisTitle)  / 2.0f);
		}
		
		if(showYaxisScale) {
			yDivisions = ChartUtils.getYdivisions125(plotHeight, plotMinY, plotMaxY);
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
			
			if(showXaxisTitle && !showLegend)
				xXaxisTitleTextLeft = xPlotLeft + (plotWidth  / 2.0f) - (FontUtils.xAxisTextWidth(xAxisTitle)  / 2.0f);
		}
		
		// stop if the plot is too small
		if(plotWidth < 1 || plotHeight < 1)
			return;
		
		// draw plot background
		OpenGL.drawQuad2D(gl, Theme.plotBackgroundColor, xPlotLeft, yPlotBottom, xPlotRight, yPlotTop);
		
		// draw the x-axis scale
		if(showXaxisScale) {
			Map<Float, String> divisions = plot.getXdivisions(plotWidth);
			
			OpenGL.buffer.rewind();
			for(Float divisionLocation : divisions.keySet()) {
				float x = divisionLocation + xPlotLeft;
				OpenGL.buffer.put(x); OpenGL.buffer.put(yPlotTop);    OpenGL.buffer.put(Theme.divisionLinesColor);
				OpenGL.buffer.put(x); OpenGL.buffer.put(yPlotBottom); OpenGL.buffer.put(Theme.divisionLinesColor);
				
				OpenGL.buffer.put(x); OpenGL.buffer.put(yXaxisTickTop);    OpenGL.buffer.put(Theme.tickLinesColor);
				OpenGL.buffer.put(x); OpenGL.buffer.put(yXaxisTickBottom); OpenGL.buffer.put(Theme.tickLinesColor);
			}
			OpenGL.buffer.rewind();
			int vertexCount = divisions.keySet().size() * 4;
			OpenGL.drawColoredLines2D(gl, OpenGL.buffer, vertexCount);
			
			for(Map.Entry<Float,String> entry : divisions.entrySet()) {
				float x = entry.getKey() + xPlotLeft - (FontUtils.tickTextWidth(entry.getValue()) / 2.0f);
				float y = yXaxisTickTextBaseline;
				FontUtils.drawTickText(entry.getValue(), (int) x, (int) y);
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
			OpenGL.drawColoredLines2D(gl, OpenGL.buffer, vertexCount);
			
			for(Map.Entry<Float,String> entry : yDivisions.entrySet()) {
				float x = xYaxisTickTextRight - FontUtils.tickTextWidth(entry.getValue());
				float y = (entry.getKey() - plotMinY) / plotRange * plotHeight + yPlotBottom - (FontUtils.tickTextHeight / 2.0f);
				FontUtils.drawTickText(entry.getValue(), (int) x, (int) y);
			}
		}
		
		// draw the legend, if space is available
		if(showLegend && haveDatasets && xLegendBorderRight < width - Theme.tilePadding) {
			OpenGL.drawQuad2D(gl, Theme.legendBackgroundColor, xLegendBorderLeft, yLegendBorderBottom, xLegendBorderRight, yLegendBorderTop);
			
			for(int i = 0; i < datasets.length; i++) {
				OpenGL.drawQuad2D(gl, datasets[i].glColor, legendBoxCoordinates[i][0], legendBoxCoordinates[i][1], legendBoxCoordinates[i][2], legendBoxCoordinates[i][3]);
				FontUtils.drawLegendText(datasets[i].name, (int) xLegendNameLeft[i], (int) yLegendTextBaseline);
			}
		}
		
		// draw the x-axis title, if space is available
		if(showXaxisTitle)
			if((!showLegend && xXaxisTitleTextLeft > xPlotLeft) || (showLegend && xXaxisTitleTextLeft > xLegendBorderRight + Theme.legendTextPadding))
				FontUtils.drawXaxisText(xAxisTitle, (int) xXaxisTitleTextLeft, (int) yXaxisTitleTextBasline);
		
		// draw the y-axis title, if space is available
		if(showYaxisTitle && yYaxisTitleTextLeft > yPlotBottom)
			FontUtils.drawYaxisText(yAxisTitle, (int) xYaxisTitleTextBaseline, (int) yYaxisTitleTextLeft, 90);
		
		// draw the plot
		plot.draw(gl, chartMatrix, xPlotLeft, yPlotBottom, xPlotRight, yPlotTop, plotMinY, plotMaxY);
		
		// draw the tooltip if the mouse is in the plot region
		if(datasets.length > 0 && SettingsController.getTooltipVisibility() && mouseX >= xPlotLeft && mouseX <= xPlotRight && mouseY >= yPlotBottom && mouseY <= yPlotTop) {
			Plot.TooltipInfo tooltip = plot.getTooltip(mouseX - (int) xPlotLeft, plotWidth);
			if(tooltip.draw) {
				String[] text = new String[datasets.length + 1];
				Color[] colors = new Color[datasets.length + 1];
				text[0] = tooltip.label;
				colors[0] = new Color(Theme.tooltipBackgroundColor[0], Theme.tooltipBackgroundColor[1], Theme.tooltipBackgroundColor[2], Theme.tooltipBackgroundColor[3]);
				for(int i = 0; i < datasets.length; i++) {
					text[i + 1] = datasets[i].getSampleAsString(tooltip.sampleNumber);
					colors[i + 1] = datasets[i].color;
				}
				float anchorX = tooltip.pixelX + xPlotLeft;
				if(anchorX >= 0 && datasets.length > 1) {
					OpenGL.buffer.rewind();
					OpenGL.buffer.put(anchorX); OpenGL.buffer.put(yPlotTop);
					OpenGL.buffer.put(anchorX); OpenGL.buffer.put(yPlotBottom);
					OpenGL.buffer.rewind();
					OpenGL.drawLines2D(gl, Theme.tooltipVerticalBarColor, OpenGL.buffer, 2);
					ChartUtils.drawTooltip(gl, text, colors, anchorX, mouseY, xPlotLeft, yPlotTop, xPlotRight, yPlotBottom);
				} else if(anchorX >= 0) {
					float anchorY = (datasets[0].getSample(tooltip.sampleNumber) - plotMinY) / plotRange * plotHeight + yPlotBottom;
					ChartUtils.drawTooltip(gl, text, colors, anchorX, anchorY, xPlotLeft, yPlotTop, xPlotRight, yPlotBottom);
				}
			}
		}
		
		// draw the plot border
		OpenGL.drawQuadOutline2D(gl, Theme.plotOutlineColor, xPlotLeft, yPlotBottom, xPlotRight, yPlotTop);
		
	}

}
