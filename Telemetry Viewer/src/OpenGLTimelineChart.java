import java.util.Date;
import java.util.Map;
import com.jogamp.opengl.GL2;

/**
 * Renders the label showing the date, and/or an interactive timeline.
 * 
 * User settings:
 *     The date can be displayed.
 *     The timeline can be displayed.
 */
public class OpenGLTimelineChart extends PositionedChart {
	
	// timeline region
	boolean showTimeline;
	float yTimelineTextBaseline;
	float yTimelineTextTop;
	float yTimelineTickBottom;
	float yTimelineTickTop;
	float xTimelineLeft;
	float xTimelineRight;
	float timelineWidth;
	float yTimelineBottom;
	float yTimelineTop;
	float timelineHeight;
	
	// time marker
	float markerWidth;
	
	// time label region
	boolean showTime;
	float yTimeTop;
	float yTimeBaseline1;
	float yTimeBaseline2; // only used if label uses 2 lines
	float timeHeight;
	float xTimeLeft1;
	float xTimeLeft2; // only used if label uses 2 lines
	float xTimeRight;
	float timeWidth;
	
	// control widgets
	WidgetCheckbox showTimeWidget;
	WidgetCheckbox showTimelineWidget;
	
	@Override public String toString() {
		
		return "Timeline";
		
	}
	
	public OpenGLTimelineChart(int x1, int y1, int x2, int y2) {
		
		super(x1, y1, x2, y2);
		
		showTimeWidget     = new WidgetCheckbox("Show Time",     true, isShown -> showTime = isShown);
		showTimelineWidget = new WidgetCheckbox("Show Timeline", true, isShown -> showTimeline = isShown);

		widgets = new Widget[2];
		widgets[0] = showTimeWidget;
		widgets[1] = showTimelineWidget;
		
	}
	
	@Override public EventHandler drawChart(GL2 gl, float[] chartMatrix, int width, int height, int lastSampleNumber, double zoomLevel, int mouseX, int mouseY) {
		
		EventHandler handler = null;
		
		int trueLastSampleNumber = DatasetsController.getSampleCount() - 1;
		boolean twoLineTimestamps = Theme.timestampFormatter.toPattern().contains("\n");
		
		// x and y locations of the timeline
		yTimelineTextBaseline = Theme.tilePadding;
		yTimelineTextTop = yTimelineTextBaseline + Theme.tickTextHeight;
		if(twoLineTimestamps)
			yTimelineTextTop += 1.3 * Theme.tickTextHeight;
		yTimelineTickBottom = yTimelineTextTop + Theme.tickTextPadding;
		yTimelineTickTop = yTimelineTickBottom + Theme.tickLength;
		xTimelineLeft = Theme.tilePadding;
		xTimelineRight = width - Theme.tilePadding;
		timelineWidth = xTimelineRight - xTimelineLeft;
		yTimelineBottom = yTimelineTickTop;
		yTimelineTop = yTimelineBottom + (Theme.lineWidth * 2);
		timelineHeight = yTimelineTop - yTimelineBottom;
		markerWidth = 6 * Controller.getDisplayScalingFactor();
		
		// draw the time label if enabled, and if space is available
		if(showTime) {
			String timeText = Theme.timestampFormatter.format(new Date(DatasetsController.getTimestamp(lastSampleNumber)));
			String[] timeTextLine = timeText.split("\n");
			boolean useTwoLines = twoLineTimestamps && Theme.xAxisTextWidth(timeText.replace('\n', ' ')) > (width - 2*Theme.tilePadding);
			if(showTimeline)
				yTimeTop = height - Theme.tilePadding; // label at the top of the chart region
			else if(useTwoLines)
				yTimeTop = (height / 2) + (Theme.xAxisTextHeight * 2.3f / 2); // 2 line label centered in the chart region
			else
				yTimeTop = (height / 2) + (Theme.xAxisTextHeight / 2); // 1 line label centered in the chart region
			yTimeBaseline1 = yTimeTop - Theme.xAxisTextHeight;
			yTimeBaseline2 = useTwoLines ? yTimeBaseline1 - (1.3f * Theme.xAxisTextHeight) : yTimeBaseline1;
			timeHeight = useTwoLines ? yTimelineTop - yTimeBaseline2 : yTimelineTop - yTimeBaseline1;
			if(useTwoLines) {
				xTimeLeft1 = (width / 2) - (Theme.xAxisTextWidth(timeTextLine[0]) / 2);
				xTimeLeft2 = (width / 2) - (Theme.xAxisTextWidth(timeTextLine[1]) / 2);
				timeWidth = Float.max(Theme.xAxisTextWidth(timeTextLine[0]), Theme.xAxisTextWidth(timeTextLine[1]));
			} else {
				timeText = timeText.replace('\n', ' ');
				xTimeLeft1 = (width / 2) - (Theme.xAxisTextWidth(timeText) / 2);
				timeWidth = Theme.xAxisTextWidth(timeText);
			}
			boolean roomForTimeLineAndTimestamp = yTimeBaseline2 > yTimelineTop + Theme.tickTextPadding + 2*markerWidth && timeWidth < width - 2*Theme.tilePadding;
			boolean roomForTimestampOnly = yTimeBaseline2 > Theme.tilePadding && timeWidth < width - 2*Theme.tilePadding;
			if((showTimeline && roomForTimeLineAndTimestamp) || (!showTimeline && roomForTimestampOnly)) {
				if(useTwoLines) {
					Theme.drawXaxisText(timeTextLine[0], (int) xTimeLeft1, (int) yTimeBaseline1);
					Theme.drawXaxisText(timeTextLine[1], (int) xTimeLeft2, (int) yTimeBaseline2);
				} else {
					Theme.drawXaxisText(timeText, (int) xTimeLeft1, (int) yTimeBaseline1);
				}
			}
		}
		
		// x and y locations of the live view button
		boolean showLiveViewButton = !OpenGLChartsRegion.instance.isLiveView();
		String buttonText = "\u23ED";
		float xButtonText = width - Theme.tilePadding - Theme.xAxisTextWidth(buttonText) - Theme.legendTextPadding;
		float yButtonText = Theme.tilePadding + Theme.legendTextPadding;
		if(showTimeline)
			yButtonText += yTimelineTop;
		float xButtonLeft = xButtonText - Theme.legendTextPadding;
		float xButtonRight = width - Theme.tilePadding;
		float yButtonBottom = yButtonText - Theme.legendTextPadding;
		float yButtonTop = yButtonBottom + Theme.xAxisTextHeight + 2*Theme.legendTextPadding;
		boolean mouseOverButton = mouseX >= xButtonLeft && mouseX <= xButtonRight && mouseY >= yButtonBottom && mouseY <= yButtonTop;
		
		// draw the timeline if enabled, and if space is available
		if(showTimeline && timelineWidth > 0) {
			
			// get the divisions
			long minTimestamp = DatasetsController.getFirstTimestamp();
			long maxTimestamp = DatasetsController.getTimestamp(trueLastSampleNumber);
			Map<Float, String> divisions = ChartUtils.getTimestampDivisions(timelineWidth, minTimestamp, maxTimestamp);
			
			// draw the tick lines
			OpenGL.buffer.rewind();
			for(Float pixelX : divisions.keySet()) {
				float x = pixelX + xTimelineLeft;
				OpenGL.buffer.put(x); OpenGL.buffer.put(yTimelineTickTop);    OpenGL.buffer.put(Theme.tickLinesColor);
				OpenGL.buffer.put(x); OpenGL.buffer.put(yTimelineTickBottom); OpenGL.buffer.put(Theme.tickLinesColor);
			}
			OpenGL.buffer.rewind();
			int vertexCount = divisions.keySet().size() * 2;
			OpenGL.drawColoredLines2D(gl, OpenGL.buffer, vertexCount);
			
			// draw the tick text
			for(Map.Entry<Float,String> entry : divisions.entrySet()) {
				if(twoLineTimestamps) {
					String text = entry.getValue();
					String[] tickLine = text.split("\n");
					float x1 = entry.getKey() + xTimelineLeft - (Theme.tickTextWidth(tickLine[0]) / 2.0f);
					float x2 = entry.getKey() + xTimelineLeft - (Theme.tickTextWidth(tickLine[1]) / 2.0f);
					float y1 = yTimelineTextBaseline + 1.3f * Theme.tickTextHeight;
					float y2 = yTimelineTextBaseline;
					Theme.drawTickText(tickLine[0], (int) x1, (int) y1);
					Theme.drawTickText(tickLine[1], (int) x2, (int) y2);
				} else {
					float x = entry.getKey() + xTimelineLeft - (Theme.tickTextWidth(entry.getValue()) / 2.0f);
					float y = yTimelineTextBaseline;
					Theme.drawTickText(entry.getValue(), (int) x, (int) y);
				}
			}
			
			// draw the timeline
			OpenGL.drawBox(gl, Theme.tickLinesColor, xTimelineLeft, yTimelineBottom, timelineWidth, timelineHeight);
			
			// draw a marker at the current timestamp
			long currentTimestamp = DatasetsController.getTimestamp(lastSampleNumber);
			float x = (float) (currentTimestamp - minTimestamp) / (float) (maxTimestamp - minTimestamp) * timelineWidth + xTimelineLeft;
			float y = yTimelineTop;
			OpenGL.drawTriangle2D(gl, Theme.tickLinesColor, x, y, x + markerWidth/2, y+markerWidth, x - markerWidth/2, y+markerWidth);
			OpenGL.drawBox(gl, Theme.tickLinesColor, x - markerWidth/2, y+markerWidth, markerWidth, markerWidth);
			
			// draw a tooltip if the mouse is not over the live view button
			if(!mouseOverButton) {
				double mousePercentage = (mouseX - xTimelineLeft) / timelineWidth;
				long mouseTimestamp = minTimestamp + (long) (mousePercentage * (double) (maxTimestamp - minTimestamp));
				int closestSampleNumber = trueLastSampleNumber;
				if(mouseX >= xTimelineLeft && mouseX <= xTimelineRight && mouseY >= 0 && mouseY <= height) {
					for(int sampleN = trueLastSampleNumber - 1; sampleN >= 0; sampleN--) { // FIXME change this to a binary search?
						closestSampleNumber = sampleN;
						if(DatasetsController.getTimestamp(sampleN) < mouseTimestamp)
							break;
					}
					
					int sampleNumber = closestSampleNumber;
					handler = EventHandler.onPressOrDrag(event -> OpenGLChartsRegion.instance.setNonLiveView(sampleNumber));
					
					mouseTimestamp = DatasetsController.getTimestamp(closestSampleNumber);
					float tooltipX = (float) (mouseTimestamp - minTimestamp) / (float) (maxTimestamp - minTimestamp) * timelineWidth + xTimelineLeft;
					String[] text = new String[twoLineTimestamps ? 3 : 2];
					text[0] = "Sample " + closestSampleNumber;
					if(twoLineTimestamps) {
						String[] timestampLine = Theme.tooltipTimestampFormatter.format(new Date(mouseTimestamp)).split("\n");
						text[1] = timestampLine[0];
						text[2] = timestampLine[1];
					} else {
						text[1] = Theme.tooltipTimestampFormatter.format(new Date(mouseTimestamp));
					}
					ChartUtils.drawTooltip(gl, text, null, tooltipX, (yTimelineTop + yTimelineBottom)/2, 0, height, width, 0);
				}
			}
			
		}
		
		// draw a live view button if not in live view
		if(showLiveViewButton) {
			OpenGL.drawBox(gl, Theme.legendBackgroundColor, xButtonLeft, yButtonBottom, (xButtonRight - xButtonLeft), (yButtonTop - yButtonBottom));
			if(mouseOverButton) {
				OpenGL.drawBoxOutline(gl, Theme.tickLinesColor, xButtonLeft, yButtonBottom, (xButtonRight - xButtonLeft), (yButtonTop - yButtonBottom));
				handler = EventHandler.onPress(event -> OpenGLChartsRegion.instance.setLiveView());
			}
			Theme.drawXaxisText(buttonText, (int) xButtonText, (int) yButtonText);
		}
		
		return handler;
		
	}

}
