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
		
		fbHandle = new int[1];
		texHandle = new int[1];
		OpenGL.createOffscreenFramebuffer(gl, fbHandle, texHandle);
		
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
	 * @param chartMatrix       The current 4x4 matrix.
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
	public void updateSliceTexture(float[] chartMatrix, int sliceNumber, int sliceWidth, int sliceHeight, int plotWidth, float domain, float plotMinY, float plotMaxY, int datasetsSize, Dataset[] datasets, Set<Integer> xDivisions, Set<Float> yDivisions, boolean showXaxisScale, boolean showYaxisScale, GL2 gl) {
		
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

		// update glDatasets
		updateSamples(datasets, firstIndex, lastIndex);
		
		float[] offscreenMatrix = new float[16];
		OpenGL.makeOrthoMatrix(offscreenMatrix, 0, sliceWidth, 0, sliceHeight, -1, 1);
		OpenGL.startDrawingOffscreen(gl, offscreenMatrix, fbHandle, texHandle, sliceWidth, sliceHeight);
		
		// draw the plot background
		OpenGL.drawQuad2D(gl, Theme.plotBackgroundColor, 0, 0, sliceWidth, sliceHeight);
		
		// draw the vertical division lines
		if(_xDivisionsSize > 0 && _showXaxisScale) {
			int firstXdiv = firstIndex - (firstIndex % _xDivisionsSize);
			int lastXdiv = lastIndex - (lastIndex % _xDivisionsSize) + _xDivisionsSize;
			OpenGL.buffer.rewind();
			int vertexCount = 0;
			for(int xValue = firstXdiv; xValue <= lastXdiv; xValue += _xDivisionsSize) {
				float x = (float) xValue / domain * (float) plotWidth - (float) (sliceNumber * sliceWidth);
				OpenGL.buffer.put(x); OpenGL.buffer.put(0);
				OpenGL.buffer.put(x); OpenGL.buffer.put(sliceHeight);
				vertexCount += 2;
			}
			OpenGL.buffer.rewind();
			OpenGL.drawLines2D(gl, Theme.divisionLinesColor, OpenGL.buffer, vertexCount);
		}
		
		// draw the horizontal division lines
		if(_showYaxisScale) {
			OpenGL.buffer.rewind();
			int vertexCount = 0;
			for(Float yValue : yDivisions) {
				float y = (yValue - plotMinY) / (plotMaxY - plotMinY) * (float) sliceHeight;
				OpenGL.buffer.put(0);          OpenGL.buffer.put(y);
				OpenGL.buffer.put(sliceWidth); OpenGL.buffer.put(y);
				vertexCount += 2;
			}
			OpenGL.buffer.rewind();
			OpenGL.drawLines2D(gl, Theme.divisionLinesColor, OpenGL.buffer, vertexCount);
		}
		
		// adjust so: x = x / domain * plotWidth - (sliceNumber * sliceWidth)
		// adjust so: y = (y - plotMinY) / range * sliceHeight
		OpenGL.translateMatrix(offscreenMatrix, -(sliceNumber * sliceWidth), 0,                                 0);
		OpenGL.scaleMatrix    (offscreenMatrix, plotWidth/domain,            sliceHeight/(plotMaxY - plotMinY), 1);
		OpenGL.translateMatrix(offscreenMatrix, 0,                           -plotMinY,                         0);
		OpenGL.useMatrix(gl, offscreenMatrix);
		
		// draw each dataset
		for(int i = 0; i < glDataset.length; i++) {
			
			// do not draw bitfields
			if(datasets[i].isBitfield)
				continue;
			
			int vertexCount = sliceMaxX - sliceMinX + 1;
			if(vertexCount < 2)
				break;
			
			OpenGL.drawLineStrip2D(gl, glDataset[i].glColor, glDataset[i].buffer, vertexCount);
			
			// also draw points if there are relatively few samples on screen
			if(_plotWidth / _domain > 2 * Theme.pointSize)
				OpenGL.drawPoints2D(gl, glDataset[i].glColor, glDataset[i].buffer, vertexCount);
			
		}
		
		OpenGL.stopDrawingOffscreen(gl, chartMatrix);
		
	}
	
	public void renderSliceAt(int bottomLeftX, int bottomLeftY, GL2 gl) {
		
		OpenGL.drawTexturedBox(gl, texHandle, bottomLeftX, bottomLeftY, _sliceWidth, _sliceHeight, 0);
		
	}
	
	public void freeResources(GL2 gl) {
		
		gl.glDeleteTextures(1, texHandle, 0);
		gl.glDeleteFramebuffers(1, fbHandle, 0);
		
	}
	
}
