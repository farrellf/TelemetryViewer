import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

import javax.swing.JPanel;

import com.jogamp.opengl.GL2;

/**
 * Renders a time-domain line chart, and uses off-screen framebuffers to cache pixels.
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
public class OpenGLTimeDomainChartCached extends PositionedChart {
	
	OpenGLTimeDomainSlice[] slices;
	final int sliceWidth = 32;

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
	Map<Integer, String> xDivisions;
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
	WidgetTextfieldInteger sampleCountWidget;
	WidgetTextfieldsOptionalMinMax minMaxWidget;
	WidgetCheckbox showXaxisTitleWidget;
	WidgetCheckbox showXaxisScaleWidget;
	WidgetCheckbox showYaxisTitleWidget;
	WidgetCheckbox showYaxisScaleWidget;
	WidgetCheckbox showLegendWidget;
	
	@Override public String toString() {
		
		return "Time Domain Chart (Cached)";
		
	}
	
	@Override public String[] exportChart() {
		
		String[] lines = new String[11];
		
		lines[0]  = "datasets = " + exportDatasets();
		lines[1]  = "sample count = " + sampleCount;
		lines[2]  = "autoscale y-axis minimum = " + autoscaleYmin;
		lines[3]  = "manual y-axis minimum = " + manualYmin;
		lines[4]  = "autoscale y-axis maximum = " + autoscaleYmax;
		lines[5]  = "manual y-axis maximum = " + manualYmax;
		lines[6]  = "show x-axis title = " + showXaxisTitle;
		lines[7]  = "show x-axis scale = " + showXaxisScale;
		lines[8]  = "show y-axis title = " + showYaxisTitle;
		lines[9]  = "show y-axis scale = " + showYaxisScale;
		lines[10] = "show legend = " + showLegend;
		
		return lines;
		
	}
	
	@Override public void importChart(String[] lines, int firstLineNumber) {
	
		if(lines.length != 11)
			throw new AssertionError("Line " + firstLineNumber + ": Invalid Time Domain Chart configuration section.");
		
		String datasets =  (String) ChartUtils.parse(firstLineNumber + 0,  lines[0],  "datasets = %s");
		sampleCount     =     (int) ChartUtils.parse(firstLineNumber + 1,  lines[1],  "sample count = %d");
		autoscaleYmin   = (boolean) ChartUtils.parse(firstLineNumber + 2,  lines[2],  "autoscale y-axis minimum = %b");
		manualYmin      =   (float) ChartUtils.parse(firstLineNumber + 3,  lines[3],  "manual y-axis minimum = %f");
		autoscaleYmax   = (boolean) ChartUtils.parse(firstLineNumber + 4,  lines[4],  "autoscale y-axis maximum = %b");
		manualYmax      =   (float) ChartUtils.parse(firstLineNumber + 5,  lines[5],  "manual y-axis maximum = %f");
		showXaxisTitle  = (boolean) ChartUtils.parse(firstLineNumber + 6,  lines[6],  "show x-axis title = %b");
		showXaxisScale  = (boolean) ChartUtils.parse(firstLineNumber + 7,  lines[7],  "show x-axis scale = %b");
		showYaxisTitle  = (boolean) ChartUtils.parse(firstLineNumber + 8,  lines[8],  "show y-axis title = %b");
		showYaxisScale  = (boolean) ChartUtils.parse(firstLineNumber + 9,  lines[9],  "show y-axis scale = %b");
		showLegend      = (boolean) ChartUtils.parse(firstLineNumber + 10, lines[10], "show legend = %b");
		
		importDatasets(firstLineNumber, datasets);
		
		// sync the widgets with the current chart state
		datasetsWidget.setDatasets(this.datasets);
		datasetsWidget.sanityCheck();
		sampleCountWidget.setInteger(sampleCount);
		sampleCountWidget.sanityCheck();
		minMaxWidget.setMin(autoscaleYmin, manualYmin);
		minMaxWidget.setMax(autoscaleYmax, manualYmax);
		minMaxWidget.sanityCheck();
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
		
		JPanel[] widgets = new JPanel[13];
		
		widgets[0]  = datasetsWidget;
		widgets[1]  = null;
		widgets[2]  = sampleCountWidget;
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

		return widgets;
		
	}
	
	public OpenGLTimeDomainChartCached(int x1, int y1, int x2, int y2) {
		
		super(x1, y1, x2, y2);
		
		slices = new OpenGLTimeDomainSlice[0];
		autoscale = new AutoScale(AutoScale.MODE_STICKY, 1, 0.10f);
		
		// create the control widgets and event handlers
		datasetsWidget = new WidgetDatasets(true, newDatasets -> datasets = newDatasets);
		
		sampleCountWidget = new WidgetTextfieldInteger("Sample Count",
		                                               SampleCountDefault,
		                                               SampleCountMinimum,
		                                               SampleCountMaximum,
		                                               newSampleCount -> sampleCount = newSampleCount);
		
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

	}
	
	@Override public void drawChart(GL2 gl, int width, int height, int lastSampleNumber, double zoomLevel, int mouseX, int mouseY) {

		// calculate domain
		int totalSampleCount = lastSampleNumber + 1;
		int plotMaxX = lastSampleNumber;
		int plotMinX = plotMaxX - (int) (sampleCount * zoomLevel) + 1;
		int minDomain = SampleCountMinimum - 1;
		if(plotMaxX - plotMinX < minDomain) plotMinX = plotMaxX - minDomain;
		float domain = plotMaxX - plotMinX;
		
		if(plotMaxX < minDomain)
			return;
		
		boolean haveDatasets = datasets != null && datasets.length > 0;
				
		// calculate range based on the true range of the *previous* frame (so we can use that cached data)
		float plotMinY = -1;
		float plotMaxY =  1;
		if(haveDatasets) {
			if(slices.length > 0) {
				plotMinY = slices[0].sliceMinY;
				plotMaxY = slices[0].sliceMaxY;
			}
			for(int i = 1; i < slices.length; i++) {
				if(slices[i].sliceMinY < plotMinY) plotMinY = slices[i].sliceMinY;
				if(slices[i].sliceMaxY > plotMaxY) plotMaxY = slices[i].sliceMaxY;
			}
		}

		// ensure range is >0
		if(plotMinY == plotMaxY) {
			float value = plotMinY;
			plotMinY = value - 0.001f;
			plotMaxY = value + 0.001f;
		}
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
			xAxisTitle = "Sample Number";
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
		} else {
			yDivisions = new HashMap<Float, String>();
		}
	
		// get the x divisions now that we know the final plot width
		xDivisions = ChartUtils.getXdivisions125(plotWidth, plotMinX, plotMaxX);
		
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
			for(Integer xValue : xDivisions.keySet()) {
				float x = (float) (xValue - plotMinX) / domain * plotWidth + xPlotLeft;
				gl.glColor4fv(Theme.tickLinesColor, 0);
				gl.glVertex2f(x, yXaxisTickTop);
				gl.glVertex2f(x, yXaxisTickBottom);
			}
			gl.glEnd();
			
			for(Map.Entry<Integer,String> entry : xDivisions.entrySet()) {
				float x = (float) (entry.getKey() - plotMinX) / domain * plotWidth + xPlotLeft - (FontUtils.tickTextWidth(entry.getValue()) / 2.0f);
				float y = yXaxisTickTextBaseline;
				FontUtils.drawTickText(entry.getValue(), (int) x, (int) y);
			}
		}
		
		// draw the y-axis scale
		if(showYaxisScale) {
			gl.glBegin(GL2.GL_LINES);
			for(Float entry : yDivisions.keySet()) {
				float y = (entry - plotMinY) / plotRange * plotHeight + yPlotBottom;
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
			
			if(slices.length > 0 && slices[0] != null && slices[0].glDataset != null && slices[0].glDataset.length == datasets.length) {
				for(int i = 0; i < datasets.length; i++) {
					gl.glBegin(GL2.GL_QUADS);
					gl.glColor4fv(slices[0].glDataset[i].color, 0);
						gl.glVertex2f(legendBoxCoordinates[i][0], legendBoxCoordinates[i][1]);
						gl.glVertex2f(legendBoxCoordinates[i][2], legendBoxCoordinates[i][3]);
						gl.glVertex2f(legendBoxCoordinates[i][4], legendBoxCoordinates[i][5]);
						gl.glVertex2f(legendBoxCoordinates[i][6], legendBoxCoordinates[i][7]);
					gl.glEnd();
					
					FontUtils.drawLegendText(slices[0].glDataset[i].name, (int) xLegendNameLeft[i], (int) yLegendTextBaseline);
				}
			}
		}
		
		// draw the x-axis title, if space is available
		if(showXaxisTitle)
			if((!showLegend && xXaxisTitleTextLeft > xPlotLeft) || (showLegend && xXaxisTitleTextLeft > xLegendBorderRight + Theme.legendTextPadding))
				FontUtils.drawXaxisText(xAxisTitle, (int) xXaxisTitleTextLeft, (int) yXaxisTitleTextBasline);
		
		// draw the y-axis title, if space is available
		if(showYaxisTitle && yYaxisTitleTextLeft > yPlotBottom)
			FontUtils.drawYaxisText(yAxisTitle, (int) xYaxisTitleTextBaseline, (int) yYaxisTitleTextLeft, 90);
		
		// determine which slices are needed
		int lastSliceIndex  = (int) Math.floor((double) (totalSampleCount - 1) / (double) domain * Math.floor(plotWidth) / (double) sliceWidth);
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
		
		// clip to the plot region
		int[] originalScissorArgs = new int[4];
		gl.glGetIntegerv(GL2.GL_SCISSOR_BOX, originalScissorArgs, 0);
		gl.glScissor(originalScissorArgs[0] + (int) xPlotLeft, originalScissorArgs[1] + (int) yPlotBottom, (int) plotWidth, (int) plotHeight);
		
		// update textures if needed, and draw the slices
		if(haveDatasets) {
			for(int i = firstSliceIndex; i <= lastSliceIndex; i++) {		
				slices[i % slices.length].updateSliceTexture(i, sliceWidth, (int) plotHeight, (int) plotWidth, domain, plotMinY, plotMaxY, totalSampleCount, datasets, xDivisions.keySet(), yDivisions.keySet(), showXaxisScale, showYaxisScale, gl);
				int x = (int) (xPlotRight + (i * sliceWidth) - ((double) (totalSampleCount - 1) / (double) domain * (int) plotWidth));
				int y = (int) yPlotBottom;
				slices[i % slices.length].renderSliceAt(x, y, gl);
			}
		}
		
		// draw any bitfield changes
		if(haveDatasets && totalSampleCount >= 2) {
			BitfieldEvents events = new BitfieldEvents();
			for(Dataset dataset : datasets)
				dataset.appendBitfieldEvents(events, plotMinX > 0 ? plotMinX : 0, plotMaxX);
			ChartUtils.drawMarkers(gl, events.get(), (float) plotMinX, (float) plotMaxX, xPlotLeft, yPlotTop, xPlotRight, yPlotBottom);
		}
		
		// stop clipping to the plot region
		gl.glScissor(originalScissorArgs[0], originalScissorArgs[1], originalScissorArgs[2], originalScissorArgs[3]);
		
		// draw the tooltip if the mouse is in the plot region
		if(datasets.length > 0 && SettingsController.getTooltipVisibility() && mouseX >= xPlotLeft && mouseX <= xPlotRight && mouseY >= yPlotBottom && mouseY <= yPlotTop) {
			int sampleNumber = Math.round(((float) mouseX - xPlotLeft) / plotWidth * domain + plotMinX);
			if(sampleNumber >= 0) {
				String[] text = new String[datasets.length + 1];
				Color[] colors = new Color[datasets.length + 1];
				text[0] = "Sample " + sampleNumber;
				colors[0] = new Color(Theme.tooltipBackgroundColor[0], Theme.tooltipBackgroundColor[1], Theme.tooltipBackgroundColor[2], Theme.tooltipBackgroundColor[3]);
				for(int i = 0; i < datasets.length; i++) {
					text[i + 1] = datasets[i].getSampleAsString(sampleNumber);
					colors[i + 1] = datasets[i].color;
				}
				float anchorX = ((float) sampleNumber - plotMinX) / domain * plotWidth + xPlotLeft;
				if(datasets.length > 1) {
					gl.glBegin(GL2.GL_LINES);
					gl.glColor4fv(Theme.tooltipVerticalBarColor, 0);
						gl.glVertex2f(anchorX, yPlotTop);
						gl.glVertex2f(anchorX, yPlotBottom);
					gl.glEnd();
					ChartUtils.drawTooltip(gl, text, colors, (int) anchorX, mouseY, xPlotLeft, yPlotTop, xPlotRight, yPlotBottom);
				} else {
					int anchorY = (int) ((datasets[0].getSample(sampleNumber) - plotMinY) / plotRange * plotHeight + yPlotBottom);
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
