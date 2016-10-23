import java.awt.BorderLayout;
import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.awt.GLCanvas;
import com.jogamp.opengl.util.Animator;

@SuppressWarnings("serial")
public class TimeDomainChartOpenGLCached extends PositionedChart {
	
	public static ChartDescriptor getDescriptor() {
		
		return new ChartDescriptor() {
			
			@Override public String toString()        { return "Time Domain Chart (OpenGL Cached)"; }
			@Override public int getMinimumDuration() { return 2; }
			@Override public int getDefaultDuration() { return 150000; }
			@Override public int getMaximumDuration() { return Integer.MAX_VALUE; }
			@Override public String[] getInputNames() { return null; }
			
			@Override public PositionedChart createChart(int x1, int y1, int x2, int y2, int chartDuration, Dataset[] chartInputs) {
				return new TimeDomainChartOpenGLCached(x1, y1, x2, y2, chartDuration, chartInputs);
			}
			
		};
		
	}
	
	@Override public String toString() {
		
		return "Time Domain Chart (OpenGL Cached)";
		
	}
	
	Animator animator;
	public TimeDomainChartOpenGLCached(int x1, int y1, int x2, int y2, int chartDuration, Dataset[] chartInputs) {
		
		super(x1, y1, x2, y2, chartDuration, chartInputs);
		
		GLCanvas glcanvas = new GLCanvas(new GLCapabilities(GLProfile.get(GLProfile.GL2)));
		glcanvas.addGLEventListener(new GLEventListener() {
			
			RenderSlice[] slices;
			int imageWidth = 0;
			int imageHeight = 0;
			@Override public void reshape(GLAutoDrawable drawable, int x, int y, int w, int h) {
				
				imageWidth = w;
				imageHeight = h;
				
				GL2 gl = drawable.getGL().getGL2();
				
				gl.glMatrixMode(GL2.GL_PROJECTION);
				gl.glLoadIdentity();
				gl.glOrtho(0, imageWidth, 0, imageHeight, -3, 3);
				
			}
			
			@Override public void init(GLAutoDrawable drawable) {
				
				GL2 gl = drawable.getGL().getGL2();
				
				gl.glEnable(GL2.GL_LINE_SMOOTH);
				gl.glEnable(GL2.GL_BLEND);
				gl.glBlendFunc(GL2.GL_SRC_ALPHA, GL2.GL_ONE_MINUS_SRC_ALPHA);
			    gl.glHint(GL2.GL_LINE_SMOOTH_HINT, GL2.GL_DONT_CARE);
				gl.glEnableClientState(GL2.GL_VERTEX_ARRAY);
			    
				gl.glLineWidth(Charts.strokeWidth);
				
				gl.setSwapInterval(1);
				
				slices = new RenderSlice[0];
				
			}
			
			@Override public void dispose(GLAutoDrawable drawable) {
				
			}
			
			float offset = 0;
			boolean moveRight = true;
			
			@Override public void display(GLAutoDrawable drawable) {
				
				long startTime = System.currentTimeMillis();
				
				// calculate domain
				int sampleCount = Controller.getSamplesCount(datasets);
				int plotMaxX = sampleCount - 1;
				int plotMinX = plotMaxX - duration;
				float domain = plotMaxX - plotMinX;
						
				// calculate range
				float plotMaxY = 0;
				float plotMinY = 0;
				if(sampleCount > 0) {
					int firstX = plotMinX >= 0 ? plotMinX : 0;
					plotMaxY = plotMinY = datasets[0].get(firstX);
					for(Dataset dataset : datasets)
						for(int i = firstX; i <= plotMaxX; i++) {
							float value = dataset.get(i);
							if(value > plotMaxY) plotMaxY = value;
							if(value < plotMinY) plotMinY = value;
						}
				}
				
				// FIXME add support for: domain as time instead of sample count, fixed range or auto-range
				// FIXME testing
				plotMaxY =  18000;
				plotMinY = -18000;

				// ensure range is >0
				if(plotMinY == plotMaxY) {
					float value = plotMinY;
					plotMinY = value - 0.001f;
					plotMaxY = value + 0.001f;
				}
				float plotRange = plotMaxY - plotMinY;
				
				// calculate x and y positions of everything (coordinate system has 0,0 at the bottom-left)
				float xLegendBorderLeft = Charts.perimeterPadding;
				float yLegendBorderBottom = Charts.perimeterPadding;
				float yLegendTextBaseline = yLegendBorderBottom + Charts.legendTextPadding;
				float yLegendTextTop = yLegendTextBaseline + Charts.legendFontHeight;
				float yLegendBorderTop = yLegendTextTop + Charts.legendTextPadding;
				
				float yXaxisTitleTextBasline = Charts.perimeterPadding;
				float yXaxisTitleTextTop = yXaxisTitleTextBasline + Charts.xAxisFontHeight;
				
				float yXaxisTickTextBaseline = Float.max(yLegendBorderTop + Charts.legendTextPadding, yXaxisTitleTextTop + Charts.legendTextPadding);
				float yXaxisTickTextTop = yXaxisTickTextBaseline + Charts.tickFontHeight;
				float yXaxisTickBottom = yXaxisTickTextTop + Charts.tickTextPadding;
				float yXaxisTickTop = yXaxisTickBottom + Charts.tickLength;
				
				float yPlotBottom = yXaxisTickTop;
				float yPlotTop = imageHeight - Charts.perimeterPadding;
				float plotHeight = yPlotTop - yPlotBottom;
				if(plotHeight < 1.0f)
					return;
				
				Map<Float, String> yDivisions = getVerticalDivisions(plotHeight, plotMinY, plotMaxY);
				
				float maxYtickTextWidth = 0;
				for(String text : yDivisions.values()) {
					float width = (float) Charts.tickFont.getStringBounds(text, Charts.tickFontFRC).getWidth(); 
					if(width > maxYtickTextWidth)
						maxYtickTextWidth = width;
				}
				
				float xYaxisTitleTextTop = Charts.perimeterPadding;
				float xYaxisTitleTextBaseline = xYaxisTitleTextTop + Charts.yAxisFontHeight;
				
				float xYaxisTickTextLeft = xYaxisTitleTextBaseline + Charts.tickTextPadding;
				float xYaxisTickTextRight = xYaxisTickTextLeft + maxYtickTextWidth;
				float xYaxisTickLeft = xYaxisTickTextRight + Charts.tickTextPadding;
				float xYaxisTickRight = xYaxisTickLeft + Charts.tickLength;
				
				float xPlotLeft = xYaxisTickRight;
				float xPlotRight = imageWidth - Charts.perimeterPadding;
				float plotWidth = xPlotRight - xPlotLeft;
				if(plotWidth < 1.0f)
					return;
				
				Map<Integer, String> xDivisions = getHorizontalDivisions(plotWidth, plotMinX, plotMaxX);
				
				float yYaxisTitleLeft = yPlotBottom + (plotHeight / 2.0f) - (Charts.yAxisFontWidth(datasets[0].unit) / 2.0f);
				float xXaxisTitleLeft = xPlotLeft +   (plotWidth  / 2.0f) - (Charts.xAxisFontWidth("Sample Number")  / 2.0f);
				
				// prepare OpenGL
				GL2 gl = drawable.getGL().getGL2();
				
				gl.glMatrixMode(GL2.GL_MODELVIEW);
				gl.glLoadIdentity();
				
				gl.glClearColor(1.0f, 1.0f, 1.0f, 1.0f);
				gl.glClear(GL2.GL_COLOR_BUFFER_BIT);
				
				// draw plot background
				gl.glBegin(GL2.GL_QUADS);
				gl.glColor3fv(Charts.plotBackgroundColor, 0);
					gl.glVertex2f(xPlotLeft,  yPlotTop);
					gl.glVertex2f(xPlotRight, yPlotTop);
					gl.glVertex2f(xPlotRight, yPlotBottom);
					gl.glVertex2f(xPlotLeft,  yPlotBottom);
				gl.glEnd();
				
				///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
				
				int lastSliceIndex  = (int) Math.floor((double) sampleCount / (double) duration * Math.floor(plotWidth) / (double) Charts.sliceWidth);
				int firstSliceIndex = lastSliceIndex - (int) Math.ceil(plotWidth / Charts.sliceWidth);
				if(firstSliceIndex < 0)
					firstSliceIndex = 0;
				
				int sliceCount = (int)Math.ceil(plotWidth / Charts.sliceWidth) + 1;
				if(slices.length != sliceCount) {
					for(int i = 0; i < slices.length; i++)
						slices[i].freeResources(gl);
					slices = new RenderSlice[sliceCount];
					for(int i = 0; i < slices.length; i++)
						slices[i] = new RenderSlice(gl);
				}
				
				for(int i = firstSliceIndex; i <= lastSliceIndex; i++) {
					slices[i % slices.length].updateSliceTexture(i, Charts.sliceWidth, (int)(plotHeight), (int)(plotWidth), domain, plotMinY, plotMaxY, sampleCount, datasets, xDivisions.keySet(), yDivisions.keySet(), gl);
					int x = (int) (xPlotRight + (i * Charts.sliceWidth) - Math.floor((double) sampleCount / (double) duration * (double) plotWidth));
					int y = (int) yPlotBottom;
					slices[i % slices.length].renderSliceAt(x, y, gl);
				}
				
				// draw over the plot overhang
				gl.glBegin(GL2.GL_QUADS);
				gl.glColor3fv(Charts.backgroundColor, 0);
					gl.glVertex2f(0,         0);
					gl.glVertex2f(0,         imageHeight);
					gl.glVertex2f(xPlotLeft, imageHeight);
					gl.glVertex2f(xPlotLeft, 0);
					
					gl.glVertex2f(xPlotRight, 0);
					gl.glVertex2f(xPlotRight, imageHeight);
					gl.glVertex2f(imageWidth, imageHeight);
					gl.glVertex2f(imageWidth, 0);
				gl.glEnd();
				
				///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
				
				// draw the vertical division lines
				gl.glBegin(GL2.GL_LINES);
				for(Integer xValue : xDivisions.keySet()) {
					float x = (float) (xValue - plotMinX) / domain * plotWidth + xPlotLeft;
//					gl.glColor3fv(Charts.divisionLinesColor, 0);
//					gl.glVertex2f(x, yPlotTop);
//					gl.glVertex2f(x, yPlotBottom);
					gl.glColor3fv(Charts.tickLinesColor, 0);
					gl.glVertex2f(x, yXaxisTickTop);
					gl.glVertex2f(x, yXaxisTickBottom);
				}
				gl.glEnd();
				
				// draw the vertical division line text
				Charts.tickFontRenderer.beginRendering(imageWidth, imageHeight);
				Charts.tickFontRenderer.setColor(Charts.tickFontColor);
				for(Map.Entry<Integer,String> entry : xDivisions.entrySet()) {
					float x = (float) (entry.getKey() - plotMinX) / domain * plotWidth + xPlotLeft - (Charts.tickFontWidth(entry.getValue()) / 2.0f);
					float y = yXaxisTickTextBaseline;
					Charts.tickFontRenderer.draw(entry.getValue(), (int) x, (int) y);
				}
				Charts.tickFontRenderer.endRendering();
				
				// draw the horizontal division lines
				gl.glBegin(GL2.GL_LINES);
				for(Float entry : yDivisions.keySet()) {
					float y = (entry - plotMinY) / plotRange * plotHeight + yPlotBottom;
//					gl.glColor3fv(Charts.divisionLinesColor, 0);
//					gl.glVertex2f(xPlotLeft,  y);
//					gl.glVertex2f(xPlotRight, y);
					gl.glColor3fv(Charts.tickLinesColor, 0);
					gl.glVertex2f(xYaxisTickLeft,  y);
					gl.glVertex2f(xYaxisTickRight, y);
				}
				gl.glEnd();
				
				// draw the horizontal division line text
				Charts.tickFontRenderer.beginRendering(imageWidth, imageHeight);
				Charts.tickFontRenderer.setColor(Charts.tickFontColor);
				for(Map.Entry<Float,String> entry : yDivisions.entrySet()) {
					float x = xYaxisTickTextRight - Charts.tickFontWidth(entry.getValue());
					float y = (entry.getKey() - plotMinY) / plotRange * plotHeight + yPlotBottom - (Charts.tickFontHeight / 2.0f);
					Charts.tickFontRenderer.draw(entry.getValue(), (int) x, (int) y);
				}
				Charts.tickFontRenderer.endRendering();
				
				// draw the legend
				float xOffset = xLegendBorderLeft + Charts.strokeWidth + Charts.legendTextPadding;
				for(Dataset dataset : datasets) {
					gl.glBegin(GL2.GL_QUADS);
					gl.glColor3f(dataset.color.getRed()/255.0f, dataset.color.getGreen()/255.0f, dataset.color.getBlue()/255.0f);
						gl.glVertex2f(xOffset,                           yLegendTextBaseline);
						gl.glVertex2f(xOffset,                           yLegendTextTop);
						gl.glVertex2f(xOffset + Charts.legendFontHeight, yLegendTextTop);
						gl.glVertex2f(xOffset + Charts.legendFontHeight, yLegendTextBaseline);
					gl.glEnd();
					
					xOffset += Charts.legendFontHeight + Charts.legendTextPadding;
					
					Charts.legendFontRenderer.beginRendering(imageWidth, imageHeight);
					Charts.legendFontRenderer.setColor(Charts.legendFontColor);
					Charts.legendFontRenderer.draw(dataset.name, (int) xOffset, (int) yLegendTextBaseline);
					Charts.legendFontRenderer.endRendering();
					xOffset += Charts.legendFontWidth(dataset.name) + 50.0f; // leave 50px after each name
				}
				xOffset -= 49.0f;
				gl.glBegin(GL2.GL_LINE_LOOP);
				gl.glColor3f(0.0f, 0.0f, 0.0f);
					gl.glVertex2f(xLegendBorderLeft,           yLegendBorderBottom);
					gl.glVertex2f(xLegendBorderLeft,           yLegendBorderTop);
					gl.glVertex2f(xLegendBorderLeft + xOffset, yLegendBorderTop);
					gl.glVertex2f(xLegendBorderLeft + xOffset, yLegendBorderBottom);
				gl.glEnd();
				
				// draw the x-axis title, shifting it to the right if the legend gets in the way
				{
					float x = xXaxisTitleLeft > xOffset + Charts.legendTextPadding ? xXaxisTitleLeft : xLegendBorderLeft + xOffset + Charts.legendTextPadding;
					float y = yXaxisTitleTextBasline;
					Charts.xAxisFontRenderer.beginRendering(imageWidth, imageHeight);
					Charts.xAxisFontRenderer.setColor(Charts.xAxisFontColor);
					Charts.xAxisFontRenderer.draw("Sample Number", (int) x, (int) y);
					Charts.xAxisFontRenderer.endRendering();
				}
				
				// draw the y-axis title, if space is available
				if(yYaxisTitleLeft >= yPlotBottom) {
					String text = datasets[0].unit;
					float x = xYaxisTitleTextBaseline;
					float y = yYaxisTitleLeft;
					
					Charts.yAxisFontRenderer.beginRendering(imageWidth, imageHeight);
					gl.glMatrixMode(GL2.GL_MODELVIEW);
					gl.glPushMatrix();
					gl.glTranslatef(x, y, 0.0f);
					gl.glRotatef(90.0f, 0.0f, 0.0f, 1.0f);
					Charts.yAxisFontRenderer.setColor(Charts.yAxisFontColor);
					Charts.yAxisFontRenderer.draw(text, (int) 0, (int) 0);
					Charts.yAxisFontRenderer.endRendering();
					gl.glPopMatrix();
				}
				
//				// draw each dataset
//				for(int i = 0; i < slice.glDataset.length; i++) {
//					
//					int vertexCount = slice.sliceMaxX - slice.sliceMinX + 1;
//					
//					gl.glMatrixMode(GL2.GL_MODELVIEW);
//					gl.glPushMatrix();
//					gl.glLoadIdentity();
//					
//					// adjust so: x = (x - chartMinX) / domain * chartRegionWidth + xChartLeft;
//					gl.glTranslatef(xPlotLeft, 0, 0);
//					gl.glScalef(plotWidth, 1, 1);
//					gl.glScalef(1.0f / domain, 1, 1);
//					gl.glTranslatef(-plotMinX, 0, 0);
//					
//					// adjust so y = (y - chartMinY) / range * chartRegionHeight + yChartBottom;
//					gl.glTranslatef(0, yPlotBottom, 0);
//					gl.glScalef(1, plotHeight, 1);
//					gl.glScalef(1, 1.0f / plotRange, 1);
//					gl.glTranslatef(0, -plotMinY, 0);
//					
//					gl.glColor3fv(slice.glDataset[i].color, 0);
//					gl.glVertexPointer(2, GL2.GL_FLOAT, 0, slice.glDataset[i].buffer);
//					gl.glDrawArrays(GL2.GL_LINE_STRIP, 0, vertexCount);
//					
//					gl.glPopMatrix();
//					
//				}
				
				// draw the plot border
				gl.glBegin(GL2.GL_LINE_LOOP);
				gl.glColor3f(0.0f, 0.0f, 0.0f);
					gl.glVertex2f(xPlotLeft,  yPlotTop);
					gl.glVertex2f(xPlotRight, yPlotTop);
					gl.glVertex2f(xPlotRight, yPlotBottom);
					gl.glVertex2f(xPlotLeft,  yPlotBottom);
				gl.glEnd();
				
				// show period and FPS for testing purposes
				Charts.legendFontRenderer.beginRendering(imageWidth, imageHeight);
				Charts.legendFontRenderer.setColor(Color.RED);
				Charts.legendFontRenderer.draw(String.format("code = %3dms, %2.1fFPS", System.currentTimeMillis() - startTime, animator.getLastFPS()), imageWidth - 350, 20);
				Charts.legendFontRenderer.endRendering();
				
				
				// FIXME vsync test code
				gl.glBegin(GL2.GL_LINE_LOOP);
					gl.glColor3f(0.0f, 0.0f, 0.0f);
					gl.glVertex2f( 50.0f + offset,  50.0f);
					gl.glVertex2f(200.0f + offset, 300.0f);
					gl.glVertex2f(350.0f + offset,  50.0f);
				gl.glEnd();
				
				gl.glFlush();
				
				offset += moveRight ? 5.0f : -5.0f;
				if(offset > imageWidth - 400.0)
					moveRight = false;
				else if(offset < 0.0)
					moveRight = true;
				
			}
			
		});
		
		setLayout(new BorderLayout());
		add(glcanvas, BorderLayout.CENTER);
	
		animator = new Animator(glcanvas);
		animator.setUpdateFPSFrames(1, null);
		animator.start();

	}
	
	/**
	 * Determines the best y values to use for vertical divisions. The 1/2/5 pattern is used (1,2,5,10,20,50,100,200,500...)
	 * 
	 * @param chartRegionHeight    Number of pixels for the y-axis
	 * @param minY                 Y value at the bottom of the chart
	 * @param maxY                 Y value at the top of the chart
	 * @param font                 Division label font (only used to calculate spacing)
	 * @return                     A Map of the y values for each division, keys are Doubles and values are formatted Strings
	 */
	private Map<Float, String> getVerticalDivisions(float chartRegionHeight, float minY, float maxY) {
		
		// calculate the best vertical division size
		float minSpacingBetweenText = 2.0f * Charts.tickFontHeight;
		float maxDivisionsCount = chartRegionHeight / (Charts.tickFontHeight + minSpacingBetweenText) + 1.0f;
		float divisionSize = (maxY - minY) / maxDivisionsCount;
		float closestDivSize1 = (float) Math.pow(10.0, Math.ceil(Math.log10(divisionSize/1.0))) * 1.0f; // closest (10^n)*1 that is >= divisionSize, such as 1,10,100,1000
		float closestDivSize2 = (float) Math.pow(10.0, Math.ceil(Math.log10(divisionSize/2.0))) * 2.0f; // closest (10^n)*2 that is >= divisionSize, such as 2,20,200,2000
		float closestDivSize5 = (float) Math.pow(10.0, Math.ceil(Math.log10(divisionSize/5.0))) * 5.0f; // closest (10^n)*5 that is >= divisionSize, such as 5,50,500,5000
		float error1 = closestDivSize1 - divisionSize;
		float error2 = closestDivSize2 - divisionSize;
		float error5 = closestDivSize5 - divisionSize;
		if(error1 < error2 && error1 < error5)
			divisionSize = closestDivSize1;
		else if(error2 < error1 && error2 < error5)
			divisionSize = closestDivSize2;
		else
			divisionSize= closestDivSize5;
		
		// decide if the numbers should be displayed as integers, or as floats to one significant decimal place
		int precision = 0;
		String format = "";
		if(divisionSize < 0.99) {
			precision = 1;
			float size = divisionSize;
			while(size * (float) Math.pow(10, precision) < 1.0f)
				precision++;
			format = "%." + precision + "f";
		}
		
		// calculate the values for each vertical division
		float firstDivision = maxY - (maxY % divisionSize);
		float lastDivision  = minY - (minY % divisionSize);
		if(firstDivision > maxY)
			firstDivision -= divisionSize;
		if(lastDivision < minY)
			lastDivision += divisionSize;
		int divisionCount = (int) Math.round((firstDivision - lastDivision) / divisionSize) + 1;
		
		Map<Float, String> yValues = new HashMap<Float, String>();
		for(int i = 0; i < divisionCount; i++) {
			float number = firstDivision - (i * divisionSize);
			String text;
			if(precision == 0) {
				text = Integer.toString((int) number);
			} else {
				text = String.format(format, number);
			}
			yValues.put(number, text);
		}
		
		return yValues;
		
	}
	
	/**
	 * Determines the best x values to use for horizontal divisions. The 1/2/5 pattern is used (1,2,5,10,20,50,100,200,500...)
	 * 
	 * @param chartRegionWidth    Number of pixels for the x-axis
	 * @param minX                X value at the left of the chart
	 * @param maxX                X value at the right of the chart
	 * @param font                Division label font (only used to calculate spacing)
	 * @return                    A Map of the x values for each division, keys are Integers and values are formatted Strings
	 */
	private Map<Integer, String> getHorizontalDivisions(float chartRegionWidth, int minX, int maxX) {
		
		// calculate the best horizontal division size
		int textWidth = (int) Float.max(Charts.tickFontWidth(Integer.toString(maxX)), Charts.tickFontWidth(Integer.toString(minX)));
		int minSpacingBetweenText = textWidth;
		int maxDivisionsCount = (int) chartRegionWidth / (textWidth + minSpacingBetweenText) + 1;
		int divisionSize = (maxX - minX) / maxDivisionsCount;
		int closestDivSize1 = (int) Math.pow(10.0, Math.ceil(Math.log10(divisionSize/1.0))) * 1; // closest (10^n)*1 that is >= divisionSize, such as 1,10,100,1000
		int closestDivSize2 = (int) Math.pow(10.0, Math.ceil(Math.log10(divisionSize/2.0))) * 2; // closest (10^n)*2 that is >= divisionSize, such as 2,20,200,2000
		int closestDivSize5 = (int) Math.pow(10.0, Math.ceil(Math.log10(divisionSize/5.0))) * 5; // closest (10^n)*5 that is >= divisionSize, such as 5,50,500,5000
		int error1 = closestDivSize1 - divisionSize;
		int error2 = closestDivSize2 - divisionSize;
		int error5 = closestDivSize5 - divisionSize;
		if(error1 < error2 && error1 < error5)
			divisionSize = closestDivSize1;
		else if(error2 < error1 && error2 < error5)
			divisionSize = closestDivSize2;
		else
			divisionSize= closestDivSize5;
		
		// calculate the values for each horizontal division
		int firstDivision = maxX - (maxX % divisionSize);
		int lastDivision  = minX - (minX % divisionSize);
		if(firstDivision > maxX)
			firstDivision -= divisionSize;
		if(lastDivision < minX)
			lastDivision += divisionSize;
		int divisionCount = ((firstDivision - lastDivision) / divisionSize + 1);
		
		Map<Integer, String> xValues = new HashMap<Integer, String>();
		for(int i = 0; i < divisionCount; i++) {
			int number = lastDivision + (i * divisionSize);
			String text = Integer.toString(number);
			xValues.put(number, text);
		}
		
		return xValues;
		
	}

}
