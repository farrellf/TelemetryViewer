import com.jogamp.opengl.GL2;
import com.jogamp.opengl.math.Quaternion;

/**
 * Renders a 3D object that is rotated based on a quaternion.
 * 
 * User settings:
 *     Four quaternion datasets.
 *     The quaternion (as text) can be displayed.
 */
public class OpenGLQuaternionChart extends PositionedChart {

	float[] shape; // triangles: u,v,w,x1,y1,z1,x2,y2,z2,x3,y3,z3,...
	
	// plot region
	float xPlotLeft;
	float xPlotRight;
	float plotWidth;
	float yPlotTop;
	float yPlotBottom;
	float plotHeight;
	
	// text label
	boolean showTextLabel;
	String textLabel;
	float yTextLabelBaseline;
	float yTextLabelTop;
	float xTextLabelLeft;
	float xTextLabelRight;
	
	// control widgets
	WidgetDatasets datasetsWidget;
	WidgetCheckbox showTextLabelWidget;
	
	@Override public String toString() {
		
		return "Quaternion Chart";
		
	}
	
	public OpenGLQuaternionChart(int x1, int y1, int x2, int y2) {
		
		super(x1, y1, x2, y2);
		
		sampleCount = 1;
		datasets = new Dataset[4];
		
		shape = ChartUtils.getShapeFromAsciiStl(getClass().getResourceAsStream("monkey.stl"));
		
		// create the control widgets and event handlers
		datasetsWidget = new WidgetDatasets(4, new String[] {"Q0", "Q1", "Q2", "Q3"}, false, newDatasets -> datasets = newDatasets);
		
		showTextLabelWidget = new WidgetCheckbox("Show Text Label",
		                                         true,
		                                         newShowTextLabel -> showTextLabel = newShowTextLabel);

		widgets = new Widget[3];
		
		widgets[0] = datasetsWidget;
		widgets[1] = null;
		widgets[2] = showTextLabelWidget;
		
	}
	
	@Override public void drawChart(GL2 gl, int width, int height, int lastSampleNumber, double zoomLevel, int mouseX, int mouseY) {
		
		// don't draw if there are no samples
		if(lastSampleNumber < 1)
			return;

		// get the quaternion values
		float q0 = datasets[0].getSample(lastSampleNumber);
		float q1 = datasets[1].getSample(lastSampleNumber);
		float q2 = datasets[2].getSample(lastSampleNumber);
		float q3 = datasets[3].getSample(lastSampleNumber);
		
		// calculate x and y positions of everything
		xPlotLeft = Theme.tilePadding;
		xPlotRight = width - Theme.tilePadding;
		plotWidth = xPlotRight - xPlotLeft;
		yPlotBottom = Theme.tilePadding;
		yPlotTop = height - Theme.tilePadding;
		plotHeight = yPlotTop - yPlotBottom;

		if(showTextLabel) {
			textLabel = String.format("Quaternion (%+1.3f,%+1.3f,%+1.3f,%+1.3f)", q0, q1, q2, q3);
			yTextLabelBaseline = Theme.tilePadding;
			yTextLabelTop = yTextLabelBaseline + FontUtils.xAxisTextHeight;
			xTextLabelLeft = (width / 2f) - (FontUtils.xAxisTextWidth(textLabel) / 2f);
			xTextLabelRight = xTextLabelLeft + FontUtils.xAxisTextWidth(textLabel);
		
			yPlotBottom = yTextLabelTop + Theme.tickTextPadding;
			yPlotTop = height - Theme.tilePadding;
			plotHeight = yPlotTop - yPlotBottom;
		}
		
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
		
		// adjust the modelview matrix to map the vertices' local space (-1 to +1) into screen space
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

		// draw the text, on top of a background quad, if there is room
		if(showTextLabel && FontUtils.xAxisTextWidth(textLabel) < width - Theme.tilePadding * 2) {
			gl.glBegin(GL2.GL_QUADS);
			gl.glColor4fv(Theme.tileShadowColor, 0);
				gl.glVertex2f(xTextLabelLeft - Theme.tickTextPadding,  yTextLabelBaseline - Theme.tickTextPadding);
				gl.glVertex2f(xTextLabelLeft - Theme.tickTextPadding,  yTextLabelTop + Theme.tickTextPadding);
				gl.glVertex2f(xTextLabelRight + Theme.tickTextPadding, yTextLabelTop + Theme.tickTextPadding);
				gl.glVertex2f(xTextLabelRight + Theme.tickTextPadding, yTextLabelBaseline - Theme.tickTextPadding);
			gl.glEnd();
			FontUtils.drawXaxisText(textLabel, (int) xTextLabelLeft, (int) yTextLabelBaseline);
		}
		
	}

}
