import javax.swing.JPanel;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.math.Quaternion;

/**
 * Renders a 3D object that is rotated based on a quaternion.
 */
@SuppressWarnings("serial")
public class OpenGLQuaternionChart extends PositionedChart {

	float[] shape; // triangles: u,v,w,x1,y1,z1,x2,y2,z2,x3,y3,z3,...
	
	public static ChartFactory getFactory() {
		
		return new ChartFactory() {
			
			// using four dataset widgets instead of one datasets widget because the order matters
			WidgetDataset q0Widget;
			WidgetDataset q1Widget;
			WidgetDataset q2Widget;
			WidgetDataset q3Widget;
			
			@Override public String toString() { return "Quaternion Chart"; }
			
			@Override public JPanel[] getWidgets() {

				q0Widget = new WidgetDataset("Q0");
				q1Widget = new WidgetDataset("Q1");
				q2Widget = new WidgetDataset("Q2");
				q3Widget = new WidgetDataset("Q3");
				
				JPanel[] widgets = new JPanel[4];
				
				widgets[0] = q0Widget;
				widgets[1] = q1Widget;
				widgets[2] = q2Widget;
				widgets[3] = q3Widget;

				return widgets;
				
			}
			
			@Override public int getMinimumSampleCount() {
				
				return 1;
				
			}
			
			@Override public PositionedChart createChart(int x1, int y1, int x2, int y2) {

				int sampleCount    = 1;
				Dataset q0         = q0Widget.getDataset()[0];
				Dataset q1         = q1Widget.getDataset()[0];
				Dataset q2         = q2Widget.getDataset()[0];
				Dataset q3         = q3Widget.getDataset()[0];
				Dataset[] datasets = {q0, q1, q2, q3};
				
				OpenGLQuaternionChart chart = new OpenGLQuaternionChart(x1, y1, x2, y2, sampleCount, datasets);
				
				return chart;
				
			}
			
			@Override public PositionedChart importChart(int x1, int y1, int x2, int y2, Dataset[] datasets, int sampleCount, String[] lines, int firstLineNumber) {
				
				if(lines.length != 0)
					throw new AssertionError("Line " + firstLineNumber + ": Invalid Quaternion Chart configuration section.");
				
				OpenGLQuaternionChart chart = new OpenGLQuaternionChart(x1, y1, x2, y2, sampleCount, datasets);
				return chart;
				
			}

		};
		
	}
	
	@Override public String[] exportChartSettings() {
		
		String[] lines = new String[0];		
		return lines;
		
	}
	
	@Override public String toString() {
		
		return "Quaternion Chart";
		
	}

	
	public OpenGLQuaternionChart(int x1, int y1, int x2, int y2, int chartDuration, Dataset[] chartInputs) {
		
		super(x1, y1, x2, y2, chartDuration, chartInputs);
		
		shape = ChartUtils.getShapeFromAsciiStl(getClass().getResourceAsStream("monkey.stl"));

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
		
		// don't draw if there are no samples
		if(lastSampleNumber < 1)
			return;

		// get the quaternion values
		float q0 = datasets[0].getSample(lastSampleNumber);
		float q1 = datasets[1].getSample(lastSampleNumber);
		float q2 = datasets[2].getSample(lastSampleNumber);
		float q3 = datasets[3].getSample(lastSampleNumber);
		
		// calculate x and y positions of everything
		float xPlotLeft = Theme.tilePadding;
		float xPlotRight = width - Theme.tilePadding;
		float plotWidth = xPlotRight - xPlotLeft;

		String titleText = String.format("Quaternion (%+1.3f,%+1.3f,%+1.3f,%+1.3f)", q0, q1, q2, q3);
		float yTitleBaseline = Theme.tilePadding;
		float yTitleTop = yTitleBaseline + FontUtils.xAxisTextHeight;
		float xTitleLeft = (width / 2f) - (FontUtils.xAxisTextWidth(titleText) / 2f);
		float xTitleRight = xTitleLeft + FontUtils.xAxisTextWidth(titleText);
		
		float yPlotBottom = yTitleTop + Theme.tickTextPadding;
		float yPlotTop = height - Theme.tilePadding;
		float plotHeight = yPlotTop - yPlotBottom;
		
		// make the plot square so it doesn't stretch the 3D shape
		if(plotWidth > plotHeight) {
			float delta = plotWidth - plotHeight;
			xPlotLeft += delta / 2;
			xPlotRight -= delta / 2;
			plotWidth = xPlotRight - xPlotLeft;
		} else if(plotHeight > plotWidth) {
			float delta = plotHeight - plotWidth;
			yPlotBottom += delta / 2;
			yPlotTop -= delta / 2;
			plotHeight = yPlotTop - yPlotBottom;
		}
		
		// adjust the modelview matrix to map the verticies' local space (-1 to +1) into screen space
		// x = x * (plotWidth / 2) + (plotWidth / 2) + xPlotLeft
		// y = y * (plotHeight / 2) + (plotHeight / 2) + yPlotBottom
		// z = z * (plotHeight / 2) + (plotHeight / 2)
		gl.glMatrixMode(GL2.GL_MODELVIEW);
		gl.glPushMatrix();
		gl.glTranslatef((plotWidth / 2.0f) + xPlotLeft, (plotHeight / 2.0f) + yPlotBottom, (plotHeight / 2.0f));
		gl.glScalef((plotWidth / 2.0f), (plotHeight / 2.0f), (plotHeight / 2.0f));
		

		gl.glRotatef(180, 0, 0, 1); // rotate the camera
		gl.glRotatef(90, 1, 0, 0); // rotate the camera
		
		Quaternion quat = new Quaternion(q1, q2, q3, q0); // x,y,z,w
		float[] quatMatrix = new float[16];
		quat.toMatrix(quatMatrix, 0);
		gl.glMultMatrixf(quatMatrix, 0);

		gl.glRotatef(180, 1, 0, 0); // invert direction of x-axis
		gl.glRotatef(90, 0, 0, 1); // swap x and z axes
		
		// setup the lights
		gl.glEnable(GL2.GL_LIGHTING);
		gl.glEnable(GL2.GL_LIGHT0);
		gl.glEnable(GL2.GL_LIGHT1);
		gl.glEnable(GL2.GL_LIGHT2);
		gl.glLightfv(GL2.GL_LIGHT0, GL2.GL_DIFFUSE,  new float[] {plotWidth/2, 0, 0, 1}, 0); // light0 diffuse color (red) and intensity
		gl.glLightfv(GL2.GL_LIGHT0, GL2.GL_POSITION, new float[] {0, -plotWidth, 0, 1}, 0);  // light0 at the front edge of the plot
		gl.glLightfv(GL2.GL_LIGHT1, GL2.GL_DIFFUSE,  new float[] {0, plotWidth/2, 0, 1}, 0); // light1 diffuse color (green) and intensity
		gl.glLightfv(GL2.GL_LIGHT1, GL2.GL_POSITION, new float[] {plotWidth, 0, 0, 1}, 0);   // light1 at the right edge of the plot
		gl.glLightfv(GL2.GL_LIGHT2, GL2.GL_DIFFUSE,  new float[] {0, 0, plotWidth/2, 1}, 0); // light2 diffuse color (blue) and intensity
		gl.glLightfv(GL2.GL_LIGHT2, GL2.GL_POSITION, new float[] {0, 0, plotWidth, 1}, 0);   // light2 at the top edge of the plot
		
		// draw the 3D shape
		gl.glEnable(GL2.GL_DEPTH_TEST);
		gl.glClear(GL2.GL_DEPTH_BUFFER_BIT);
		gl.glBegin(GL2.GL_TRIANGLES);
		for(int i = 0; i < shape.length / 12; i++) {
			float u = shape[i*12 + 0];
			float v = shape[i*12 + 1];
			float w = shape[i*12 + 2];
			
			float x1 = shape[i*12 + 3];
			float y1 = shape[i*12 + 4];
			float z1 = shape[i*12 + 5];
			
			float x2 = shape[i*12 + 6];
			float y2 = shape[i*12 + 7];
			float z2 = shape[i*12 + 8];
			
			float x3 = shape[i*12 + 9];
			float y3 = shape[i*12 + 10];
			float z3 = shape[i*12 + 11];
			
			gl.glNormal3f(u, v, w);
			gl.glVertex3f(x1, y1, z1);
			gl.glVertex3f(x2, y2, z2);
			gl.glVertex3f(x3, y3, z3);
		}
		gl.glEnd();
		gl.glDisable(GL2.GL_DEPTH_TEST);
		gl.glDisable(GL2.GL_LIGHTING);
		
		gl.glPopMatrix();
				
		// draw the text, on top of a background quad, so the shape can't interfere with it
		gl.glBegin(GL2.GL_QUADS);
		gl.glColor4fv(Theme.backgroundColor, 0);
			gl.glVertex2f(xTitleLeft - Theme.tickTextPadding,  yTitleBaseline - Theme.tickTextPadding);
			gl.glVertex2f(xTitleLeft - Theme.tickTextPadding,  yTitleTop + Theme.tickTextPadding);
			gl.glVertex2f(xTitleRight + Theme.tickTextPadding, yTitleTop + Theme.tickTextPadding);
			gl.glVertex2f(xTitleRight + Theme.tickTextPadding, yTitleBaseline - Theme.tickTextPadding);
		gl.glEnd();
		FontUtils.drawXaxisText(titleText, (int) xTitleLeft, (int) yTitleBaseline);
		
	}

}
