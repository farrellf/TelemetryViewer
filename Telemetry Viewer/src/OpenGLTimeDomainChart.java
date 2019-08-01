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
	
	String xAxisUnit;
	
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
		                                    newDurationType -> xAxisUnit = newDurationType);
		
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
	
	@Override public void drawChart(GL2 gl, int width, int height, int lastSampleNumber, double zoomLevel, int mouseX, int mouseY) {
		
		boolean haveDatasets = datasets != null && datasets.length > 0;
		
		SamplesManager samples = new SamplesManager();
		if(xAxisUnit.equals("Samples"))
			samples.acquireNsamples(lastSampleNumber, (float) zoomLevel, datasets, SampleCountMinimum, durationWidget.getDurationInteger());
		else
			samples.acquireNtimeUnits(lastSampleNumber, (float) zoomLevel, datasets, SampleCountMinimum, durationWidget.getDurationFloat(), xAxisUnit);
		
		// calculate the plot range
		float plotMaxY = samples.maxY;
		float plotMinY = samples.minY;
		autoscale.update(plotMinY, plotMaxY);
		plotMaxY = autoscaleYmax ? autoscale.getMax() : manualYmax;
		plotMinY = autoscaleYmin ? autoscale.getMin() : manualYmin;
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
			xAxisTitle = samples.xAxisTitle;
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
		gl.glBegin(GL2.GL_QUADS);
		gl.glColor4fv(Theme.plotBackgroundColor, 0);
			gl.glVertex2f(xPlotLeft,  yPlotTop);
			gl.glVertex2f(xPlotRight, yPlotTop);
			gl.glVertex2f(xPlotRight, yPlotBottom);
			gl.glVertex2f(xPlotLeft,  yPlotBottom);
		gl.glEnd();
		
		// draw the x-axis scale
		if(showXaxisScale) {
			Map<Float, String> divisions = samples.getXdivisions(plotWidth);
			
			gl.glBegin(GL2.GL_LINES);
			for(Float divisionLocation : divisions.keySet()) {
				float x = divisionLocation + xPlotLeft;
				gl.glColor4fv(Theme.divisionLinesColor, 0);
				gl.glVertex2f(x, yPlotTop);
				gl.glVertex2f(x, yPlotBottom);
				gl.glColor4fv(Theme.tickLinesColor, 0);
				gl.glVertex2f(x, yXaxisTickTop);
				gl.glVertex2f(x, yXaxisTickBottom);
			}
			gl.glEnd();
			
			for(Map.Entry<Float,String> entry : divisions.entrySet()) {
				float x = entry.getKey() + xPlotLeft - (FontUtils.tickTextWidth(entry.getValue()) / 2.0f);
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
			gl.glBegin(GL2.GL_QUADS);
			gl.glColor4fv(Theme.legendBackgroundColor, 0);
				gl.glVertex2f(xLegendBorderLeft,  yLegendBorderBottom);
				gl.glVertex2f(xLegendBorderLeft,  yLegendBorderTop);
				gl.glVertex2f(xLegendBorderRight, yLegendBorderTop);
				gl.glVertex2f(xLegendBorderRight, yLegendBorderBottom);
			gl.glEnd();
			
			for(int i = 0; i < datasets.length; i++) {
				gl.glBegin(GL2.GL_QUADS);
				gl.glColor3f(datasets[i].color.getRed() / 255.0f, datasets[i].color.getGreen() / 255.0f, datasets[i].color.getBlue() / 255.0f);
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
		
		// draw the y-axis title, if space is available
		if(showYaxisTitle && yYaxisTitleTextLeft > yPlotBottom)
			FontUtils.drawYaxisText(yAxisTitle, (int) xYaxisTitleTextBaseline, (int) yYaxisTitleTextLeft, 90);
		
		// clip to the plot region
		int[] originalScissorArgs = new int[4];
		gl.glGetIntegerv(GL2.GL_SCISSOR_BOX, originalScissorArgs, 0);
		gl.glScissor(originalScissorArgs[0] + (int) xPlotLeft, originalScissorArgs[1] + (int) yPlotBottom, (int) plotWidth, (int) plotHeight);
		
		// draw each dataset
		if(haveDatasets && samples.plotSampleCount >= 2) {
			for(int i = 0; i < datasets.length; i++) {
				
				// do not draw bitfields
				if(datasets[i].isBitfield)
					continue;
				
				gl.glMatrixMode(GL2.GL_MODELVIEW);
				gl.glPushMatrix();
				
				// adjust so: x = (x - plotMinX) / domain * plotWidth + xPlotLeft;
				gl.glTranslatef(xPlotLeft, 0, 0);
				gl.glScalef(plotWidth, 1, 1);
				gl.glScalef(1.0f / samples.getPlotDomain(), 1, 1);
				gl.glTranslatef(-samples.getPlotMinX(), 0, 0);
				
				// adjust so y = (y - plotMinY) / plotRange * plotHeight + yPlotBottom;
				gl.glTranslatef(0, yPlotBottom, 0);
				gl.glScalef(1, plotHeight, 1);
				gl.glScalef(1, 1.0f / plotRange, 1);
				gl.glTranslatef(0, -plotMinY, 0);
				
				gl.glColor3f(datasets[i].color.getRed() / 255.0f, datasets[i].color.getGreen() / 255.0f, datasets[i].color.getBlue() / 255.0f);
				gl.glVertexPointer(2, GL2.GL_FLOAT, 0, samples.buffers[i]);
				gl.glDrawArrays(GL2.GL_LINE_STRIP, 0, samples.plotSampleCount);
				
				// also draw points if there are relatively few samples on screen
				if(samples.fewSamplesOnScreen(plotWidth))
					gl.glDrawArrays(GL2.GL_POINTS, 0, samples.plotSampleCount);
				
				gl.glPopMatrix();
				
			}
		}
		
		// draw any bitfield changes
		if(haveDatasets && samples.plotSampleCount >= 2)
			ChartUtils.drawMarkers(gl, samples.getBitfieldEvents(plotWidth), xPlotLeft, yPlotTop, xPlotRight, yPlotBottom);

		// stop clipping to the plot region
		gl.glScissor(originalScissorArgs[0], originalScissorArgs[1], originalScissorArgs[2], originalScissorArgs[3]);
		
		// draw the tooltip if the mouse is in the plot region
		if(datasets.length > 0 && SettingsController.getTooltipVisibility() && mouseX >= xPlotLeft && mouseX <= xPlotRight && mouseY >= yPlotBottom && mouseY <= yPlotTop) {
			SamplesManager.TooltipInfo tooltip = samples.getTooltip(mouseX - (int) xPlotLeft, plotWidth);
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
					gl.glBegin(GL2.GL_LINES);
					gl.glColor4fv(Theme.tooltipVerticalBarColor, 0);
						gl.glVertex2f(anchorX, yPlotTop);
						gl.glVertex2f(anchorX, yPlotBottom);
					gl.glEnd();
					ChartUtils.drawTooltip(gl, text, colors, (int) anchorX, mouseY, xPlotLeft, yPlotTop, xPlotRight, yPlotBottom);
				} else if(anchorX >= 0) {
					int anchorY = (int) ((datasets[0].getSample(tooltip.sampleNumber) - plotMinY) / plotRange * plotHeight + yPlotBottom);
					ChartUtils.drawTooltip(gl, text, colors, (int) anchorX, anchorY, xPlotLeft, yPlotTop, xPlotRight, yPlotBottom);
				}
			}
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
