import java.util.Arrays;
import java.util.Set;

import com.jogamp.opengl.GL2;

public class OpenGLTimeDomainSlice {

	public int sliceMinX;
	public int sliceMaxX;
	
	public float sliceMinY;
	public float sliceMaxY;
	
	public SamplesGL[] glDataset;
	
	int[] fbHandle;
	int[] texHandle;
	
	public OpenGLTimeDomainSlice(GL2 gl) {
		
		// create and use a framebuffer
		fbHandle = new int[1];
		gl.glGenFramebuffers(1, fbHandle, 0);
		gl.glBindFramebuffer(GL2.GL_FRAMEBUFFER, fbHandle[0]);
		
		// create and use a texture
		texHandle = new int[1];
		gl.glGenTextures(1, texHandle, 0);
		gl.glBindTexture(GL2.GL_TEXTURE_2D, texHandle[0]);
		gl.glTexImage2D(GL2.GL_TEXTURE_2D, 0, GL2.GL_RGB, 512, 512, 0, GL2.GL_RGB, GL2.GL_UNSIGNED_BYTE, null); // dummy 512x512 texture
		gl.glTexParameteri(GL2.GL_TEXTURE_2D, GL2.GL_TEXTURE_MIN_FILTER, GL2.GL_LINEAR);
		gl.glTexParameteri(GL2.GL_TEXTURE_2D, GL2.GL_TEXTURE_MAG_FILTER, GL2.GL_LINEAR);
		gl.glFramebufferTexture2D(GL2.GL_FRAMEBUFFER, GL2.GL_COLOR_ATTACHMENT0, GL2.GL_TEXTURE_2D, texHandle[0], 0);
		gl.glDrawBuffers(1, new int[] {GL2.GL_COLOR_ATTACHMENT0}, 0);
		
		// check for errors
		if(gl.glCheckFramebufferStatus(GL2.GL_FRAMEBUFFER) != GL2.GL_FRAMEBUFFER_COMPLETE)
			NotificationsController.showFailureForSeconds("Error while creating a time domain slice's framebuffer or texture.", 10, false);
		
		// switch back to the screen framebuffer
		gl.glBindFramebuffer(GL2.GL_FRAMEBUFFER, 0);
		
	}
	
	public void updateSamples(Dataset[] dataset, int minX, int maxX) {
		
		sliceMinX = minX;
		sliceMaxX = maxX;
		
		if(glDataset == null || glDataset.length != dataset.length)
			glDataset = new SamplesGL[dataset.length];
		
		sliceMinY = Float.MAX_VALUE;
		sliceMaxY = -Float.MAX_VALUE;
		
		for(int i = 0; i < dataset.length; i++) {
			if(glDataset[i] == null)
				glDataset[i] = new SamplesGL();
			dataset[i].getGLsamples(minX, maxX, glDataset[i]);
			if(!dataset[i].isBitfield) {
				if(glDataset[i].min < sliceMinY) sliceMinY = glDataset[i].min;
				if(glDataset[i].max > sliceMaxY) sliceMaxY = glDataset[i].max;
			}
		}
		
		if(sliceMinY == Float.MAX_VALUE && sliceMaxY == -Float.MAX_VALUE) {
			sliceMinY = -1;
			sliceMaxY = 1;
		}
		
	}
	
	int _sliceNumber = 0;
	int _sliceWidth = 0;
	int _sliceHeight = 0;
	int _plotWidth = 0;
	float _domain = 0;
	float _plotMinY = Float.MAX_VALUE;
	float _plotMaxY = Float.MIN_VALUE;
	Dataset[] _datasets = null;
	int _xDivisionsSize = 0;
	int _firstIndex = 0;
	int _lastIndex = 0;
	boolean _showXaxisScale = true;
	boolean _showYaxisScale = true;
	
	/**
	 * Updates the slice texture if needed.
	 * 
	 * @param sliceNumber       Which slice this texture represents.
	 * @param sliceWidth        Pixel width of this slice.
	 * @param sliceHeight       Pixel height of this slice.
	 * @param plotWidth         Pixel width of the entire plot region.
	 * @param domain            Total number of samples shown on screen.
	 * @param plotMinY          Y value at the bottom of this slice.
	 * @param plotMaxY          Y value at the top of this slice.
	 * @param datasetsSize      How many samples are in the datasets.
	 * @param datasets          The datasets to be visualized.
	 * @param xDivisions        Where the x division lines need to be drawn.
	 * @param yDivisions        Where the y division lines need to be drawn.
	 * @param showXaxisScale    If the x division lines need to be drawn.
	 * @param showYaxisScale    If the y division lines need to be drawn.
	 * @param gl                The OpenGL context.
	 */
	public void updateSliceTexture(int sliceNumber, int sliceWidth, int sliceHeight, int plotWidth, float domain, float plotMinY, float plotMaxY, int datasetsSize, Dataset[] datasets, Set<Integer> xDivisions, Set<Float> yDivisions, boolean showXaxisScale, boolean showYaxisScale, GL2 gl) {
		
		// determine which x values need to be plotted
		int firstIndex = (int) Math.floor((double) (sliceNumber * sliceWidth)    * ((double) domain / (double) plotWidth));
		int lastIndex  = (int) Math.ceil((double) ((sliceNumber+1) * sliceWidth) * ((double) domain / (double) plotWidth));
		if(lastIndex == firstIndex)
			lastIndex++;
		if(lastIndex > datasetsSize - 1)
			lastIndex = datasetsSize - 1;
		
		// determine if this slice needs to be redrawn
		boolean redrawNeeded = false;
		if(_sliceNumber != sliceNumber) {
			_sliceNumber = sliceNumber;
			redrawNeeded = true;
		}
		if(_sliceWidth != sliceWidth) {
			_sliceWidth = sliceWidth;
			redrawNeeded = true;
		}
		if(_sliceHeight != sliceHeight) {
			_sliceHeight = sliceHeight;
			redrawNeeded = true;
		}
		if(_plotWidth != plotWidth) {
			_plotWidth = plotWidth;
			redrawNeeded = true;
		}
		if(_domain != domain) {
			_domain = domain;
			redrawNeeded = true;
		}
		if(_plotMinY != plotMinY) {
			_plotMinY = plotMinY;
			redrawNeeded = true;
		}
		if(_plotMaxY != plotMaxY) {
			_plotMaxY = plotMaxY;
			redrawNeeded = true;
		}
		if(_datasets != datasets) {
			_datasets = datasets;
			redrawNeeded = true;
		}
		if(xDivisions.size() > 1) {
			Integer[] divs = xDivisions.toArray(new Integer[1]);
			Arrays.sort(divs);
			int xDivisionSize = Math.abs(divs[1] - divs[0]);
			if(_xDivisionsSize != xDivisionSize) {
				_xDivisionsSize = xDivisionSize;
				redrawNeeded = true;
			}
		}
		if(_firstIndex != firstIndex) {
			_firstIndex = firstIndex;
			redrawNeeded = true;
		}
		if(_lastIndex != lastIndex) {
			_lastIndex = lastIndex;
			redrawNeeded = true;
		}
		if(_showXaxisScale != showXaxisScale) {
			_showXaxisScale = showXaxisScale;
			redrawNeeded = true;
		}
		if(_showYaxisScale != showYaxisScale) {
			_showYaxisScale = showYaxisScale;
			redrawNeeded = true;
		}
		
		if(!redrawNeeded) {
			return;
		}

//		System.out.println(String.format("SLICE REDRAWN: sliceNumber = %d, sliceWidth = %d, sliceHeight = %d, plotWidth = %d, domain = %f, plotMinY = %f, plotMaxY = %f, firstIndex = %d, lastIndex = %d", sliceNumber, sliceWidth, sliceHeight, plotWidth, domain, plotMinY, plotMaxY, firstIndex, lastIndex));

		// save the viewport, scissor test, modelview matrix, and projection matrix
		gl.glPushAttrib(GL2.GL_VIEWPORT_BIT | GL2.GL_SCISSOR_BIT);
		gl.glMatrixMode(GL2.GL_MODELVIEW);
		gl.glPushMatrix();
		gl.glMatrixMode(GL2.GL_PROJECTION);
		gl.glPushMatrix();
		
		// update glDatasets
		updateSamples(datasets, firstIndex, lastIndex);
		
		// switch to the off-screen framebuffer and corresponding texture
		gl.glBindFramebuffer(GL2.GL_FRAMEBUFFER, fbHandle[0]);
		gl.glBindTexture(GL2.GL_TEXTURE_2D, texHandle[0]);

		// replace the existing texture
		gl.glTexImage2D(GL2.GL_TEXTURE_2D, 0, GL2.GL_RGB, sliceWidth, sliceHeight, 0, GL2.GL_RGB, GL2.GL_UNSIGNED_BYTE, null);

		// set the viewport and disable the scissor test
		gl.glViewport(0, 0, sliceWidth, sliceHeight);
		gl.glDisable(GL2.GL_SCISSOR_TEST);
		
		// set the projection matrix
		gl.glMatrixMode(GL2.GL_PROJECTION);
		gl.glLoadIdentity();
		gl.glOrtho(0, sliceWidth, 0, sliceHeight, -3, 3);
		
		// set the modelview matrix
		gl.glMatrixMode(GL2.GL_MODELVIEW);
		gl.glLoadIdentity();
		
		// draw the plot background
		gl.glBegin(GL2.GL_QUADS);
		gl.glColor3fv(Theme.plotBackgroundColor, 0);
			gl.glVertex2f(0,          0);
			gl.glVertex2f(0,          sliceHeight);
			gl.glVertex2f(sliceWidth, sliceHeight);
			gl.glVertex2f(sliceWidth, 0);
		gl.glEnd();
		
		// draw the vertical division lines
		if(_xDivisionsSize > 0 && _showXaxisScale) {
			int firstXdiv = firstIndex - (firstIndex % _xDivisionsSize);
			int lastXdiv = lastIndex - (lastIndex % _xDivisionsSize) + _xDivisionsSize;
			gl.glBegin(GL2.GL_LINES);
			for(int xValue = firstXdiv; xValue <= lastXdiv; xValue += _xDivisionsSize) {
				float x = (float) xValue / domain * (float) plotWidth - (float) (sliceNumber * sliceWidth);
				gl.glColor3fv(Theme.divisionLinesColor, 0);
				gl.glVertex2f(x, 0);
				gl.glVertex2f(x, sliceHeight);
			}
			gl.glEnd();
		}
		
		// draw the horizontal division lines
		if(_showYaxisScale) {
			gl.glBegin(GL2.GL_LINES);
			for(Float yValue : yDivisions) {
				float y = (yValue - plotMinY) / (plotMaxY - plotMinY) * (float) sliceHeight;
				gl.glColor3fv(Theme.divisionLinesColor, 0);
				gl.glVertex2f(0,  y);
				gl.glVertex2f(sliceWidth, y);
			}
			gl.glEnd();
		}
		
		// draw each dataset
		for(int i = 0; i < glDataset.length; i++) {
			
			// do not draw bitfields
			if(datasets[i].isBitfield)
				continue;
			
			int vertexCount = sliceMaxX - sliceMinX + 1;
			if(vertexCount < 2)
				break;
			
			gl.glMatrixMode(GL2.GL_MODELVIEW);
			gl.glPushMatrix();
			gl.glLoadIdentity();
			
			// adjust so x = x / domain * plotWidth - (sliceNumber * sliceWidth)
			gl.glTranslatef(-(sliceNumber * sliceWidth), 0, 0);
			gl.glScalef(plotWidth, 1, 1);
			gl.glScalef(1.0f / domain, 1, 1);
			
			// adjust so y = (y - plotMinY) / range * sliceHeight
			gl.glScalef(1, sliceHeight, 1);
			gl.glScalef(1, 1.0f / (plotMaxY - plotMinY), 1);
			gl.glTranslatef(0, -plotMinY, 0);
			
			gl.glColor3fv(glDataset[i].color, 0);
			gl.glVertexPointer(2, GL2.GL_FLOAT, 0, glDataset[i].buffer);
			gl.glDrawArrays(GL2.GL_LINE_STRIP, 0, vertexCount);
			
			// also draw points if there are relatively few samples on screen
			if(_plotWidth / _domain > 2 * Theme.pointSize)
				gl.glDrawArrays(GL2.GL_POINTS, 0, vertexCount);
			
			gl.glPopMatrix();
			
		}
		
		// switch back to the screen framebuffer
		gl.glBindFramebuffer(GL2.GL_FRAMEBUFFER, 0);
		
		// restore old viewport and scissor test state, projection matrix, and modelview matrix
		gl.glPopAttrib();
		gl.glMatrixMode(GL2.GL_PROJECTION);
		gl.glPopMatrix();
		gl.glMatrixMode(GL2.GL_MODELVIEW);
		gl.glPopMatrix();
		
	}
	
	public void renderSliceAt(int bottomLeftX, int bottomLeftY, GL2 gl) {
		
		// draw a textured quad with the slice texture
		gl.glColor3f(1, 1, 1);
		gl.glDisable(GL2.GL_LIGHTING);
		gl.glBindTexture(GL2.GL_TEXTURE_2D, texHandle[0]);
		gl.glEnable(GL2.GL_TEXTURE_2D);
		gl.glBegin(GL2.GL_QUADS);
			gl.glTexCoord2f(0, 0);
			gl.glVertex2f(bottomLeftX, bottomLeftY);
			
			gl.glTexCoord2f(0, 1);
			gl.glVertex2f(bottomLeftX, bottomLeftY + _sliceHeight);
			
			gl.glTexCoord2f(1, 1);
			gl.glVertex2f(bottomLeftX + _sliceWidth, bottomLeftY + _sliceHeight);
			
			gl.glTexCoord2f(1, 0);
			gl.glVertex2f(bottomLeftX + _sliceWidth, bottomLeftY);
		gl.glEnd();
		gl.glDisable(GL2.GL_TEXTURE_2D);
		
	}
	
	public void freeResources(GL2 gl) {
		
		gl.glDeleteTextures(1, texHandle, 0);
		gl.glDeleteFramebuffers(1, fbHandle, 0);
		
	}
	
}
