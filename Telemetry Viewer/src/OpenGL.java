import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL2ES3;
import com.jogamp.opengl.GL3;

public class OpenGL {
	
	public static FloatBuffer buffer = Buffers.newDirectFloatBuffer(32768); // for anyone to use, hopefully big enough for most things
	
	/**
	 * Draws a buffer of (y1,y2,...) vertices as GL_POINTS.
	 * X values are auto-generated.
	 * 
	 * @param gl             The OpenGL context.
	 * @param color          The color, as a float[] {r,g,b,a}.
	 * @param buffer         Vertex buffer containing (y1,y2,...)
	 * @param vertexCount    Number of vertices in the buffer.
	 * @param xOffset        How much empty space is to the left of the first vertex. (used when plotMinX < 0)
	 */
	public static void drawPointsY(GL2ES3 gl, float[] color, FloatBuffer buffer, int vertexCount, int xOffset) {
		
		// send data to the GPU
		gl.glUseProgram(PointsY.programHandle);
		gl.glBindVertexArray(PointsY.vaoHandle);
		gl.glBindBuffer(GL3.GL_ARRAY_BUFFER, PointsY.vboHandle);
		gl.glBufferData(GL3.GL_ARRAY_BUFFER, vertexCount * 4, buffer.position(0), GL3.GL_DYNAMIC_DRAW);
		gl.glUniformMatrix4fv(PointsY.matrixHandle, 1, false, currentMatrix, 0);
		gl.glUniform4fv(PointsY.colorHandle, 1, color, 0);
		gl.glUniform1i(PointsY.xOffsetHandle, xOffset);
		gl.glUniform1f(PointsY.pointWidthHandle, Theme.pointWidth);
		int[] viewportDimensions = new int[4]; // x,y,w,h
		gl.glGetIntegerv(GL3.GL_VIEWPORT, viewportDimensions, 0);
		gl.glUniform1f(PointsY.widthPixelsHandle,  viewportDimensions[2]);
		gl.glUniform1f(PointsY.heightPixelsHandle, viewportDimensions[3]);

		// draw
		gl.glDrawArrays(GL3.GL_POINTS, 0, vertexCount);
		
	}
	
	/**
	 * Draws a buffer of (x,y) vertices as GL_POINTS.
	 * 
	 * @param gl             The OpenGL context.
	 * @param color          The color, as a float[] {r,g,b,a}.
	 * @param buffer         Vertex buffer containing (x1,y1,x2,y2,...)
	 * @param vertexCount    Number of vertices in the buffer.
	 */
	public static void drawPointsXy(GL2ES3 gl, float[] color, FloatBuffer buffer, int vertexCount) {
		
		// send data to the GPU
		gl.glUseProgram(PointsXY.programHandle);
		gl.glBindVertexArray(PointsXY.vaoHandle);
		gl.glBindBuffer(GL3.GL_ARRAY_BUFFER, PointsXY.vboHandle);
		gl.glBufferData(GL3.GL_ARRAY_BUFFER, vertexCount * 2 * 4, buffer.position(0), GL3.GL_DYNAMIC_DRAW);
		gl.glUniformMatrix4fv(PointsXY.matrixHandle, 1, false, currentMatrix, 0);
		gl.glUniform4fv(PointsXY.colorHandle, 1, color, 0);
		gl.glUniform1f(PointsXY.pointWidthHandle, Theme.pointWidth);
		int[] viewportDimensions = new int[4]; // x,y,w,h
		gl.glGetIntegerv(GL3.GL_VIEWPORT, viewportDimensions, 0);
		gl.glUniform1f(PointsXY.widthPixelsHandle,  viewportDimensions[2]);
		gl.glUniform1f(PointsXY.heightPixelsHandle, viewportDimensions[3]);

		// draw
		gl.glDrawArrays(GL3.GL_POINTS, 0, vertexCount);
		
	}
	
	/**
	 * Draws two buffers of (x1,x2,...) (y1,y2,...) vertices as GL_POINTS.
	 * 
	 * @param gl             The OpenGL context.
	 * @param color          The color, as a float[] {r,g,b,a}.
	 * @param bufferX        Vertex buffer containing (x1,x2,...)
	 * @param bufferY        Vertex buffer containing (y1,y2,...)
	 * @param vertexCount    Number of vertices in the buffer.
	 */
	public static void drawPointsX_Y(GL2ES3 gl, float[] color, FloatBuffer bufferX, FloatBuffer bufferY, int vertexCount) {
		
		// send data to the GPU
		gl.glUseProgram(PointsX_Y.programHandle);
		gl.glBindVertexArray(PointsX_Y.vaoHandle);
		gl.glBindBuffer(GL3.GL_ARRAY_BUFFER, PointsX_Y.vboXhandle);
		gl.glBufferData(GL3.GL_ARRAY_BUFFER, vertexCount * 4, bufferX.position(0), GL3.GL_DYNAMIC_DRAW);
		gl.glBindBuffer(GL3.GL_ARRAY_BUFFER, PointsX_Y.vboYhandle);
		gl.glBufferData(GL3.GL_ARRAY_BUFFER, vertexCount * 4, bufferY.position(0), GL3.GL_DYNAMIC_DRAW);
		gl.glUniformMatrix4fv(PointsX_Y.matrixHandle, 1, false, currentMatrix, 0);
		gl.glUniform4fv(PointsX_Y.colorHandle, 1, color, 0);
		gl.glUniform1f(PointsX_Y.pointWidthHandle, Theme.pointWidth);
		int[] viewportDimensions = new int[4]; // x,y,w,h
		gl.glGetIntegerv(GL3.GL_VIEWPORT, viewportDimensions, 0);
		gl.glUniform1f(PointsX_Y.widthPixelsHandle,  viewportDimensions[2]);
		gl.glUniform1f(PointsX_Y.heightPixelsHandle, viewportDimensions[3]);
		
		// draw
		gl.glDrawArrays(GL3.GL_POINTS, 0, vertexCount);
		
	}
	
	/**
	 * Draws a buffer of (y1,y2,...) vertices as GL_LINES or GL_LINE_STRIP or GL_LINE_LOOP.
	 * X values are auto-generated.
	 * 
	 * @param gl             The OpenGL context.
	 * @param lineType       GL_LINES or GL_LINE_STRIP or GL_LINE_LOOP.
	 * @param color          The color, as a float[] {r,g,b,a}.
	 * @param buffer         Vertex buffer containing (y1,y2,...)
	 * @param vertexCount    Number of vertices in the buffer.
	 * @param xOffset        How much empty space is to the left of the first vertex. (used when plotMinX < 0)
	 */
	public static void drawLinesY(GL2ES3 gl, int lineType, float[] color, FloatBuffer buffer, int vertexCount, int xOffset) {
		
		// send data to the GPU
		if(Theme.lineWidth == 1) {
			gl.glUseProgram(ThinLinesY.programHandle);
			gl.glBindVertexArray(ThinLinesY.vaoHandle);
			gl.glBindBuffer(GL3.GL_ARRAY_BUFFER, ThinLinesY.vboHandle);
			gl.glBufferData(GL3.GL_ARRAY_BUFFER, vertexCount * 4, buffer.position(0), GL3.GL_DYNAMIC_DRAW);
			gl.glUniformMatrix4fv(ThinLinesY.matrixHandle, 1, false, currentMatrix, 0);
			gl.glUniform4fv(ThinLinesY.colorHandle, 1, color, 0);
			gl.glUniform1i(ThinLinesY.xOffsetHandle, xOffset);
		} else {
			gl.glUseProgram(ThickLinesY.programHandle);
			gl.glBindVertexArray(ThickLinesY.vaoHandle);
			gl.glBindBuffer(GL3.GL_ARRAY_BUFFER, ThickLinesY.vboHandle);
			gl.glBufferData(GL3.GL_ARRAY_BUFFER, vertexCount * 4, buffer.position(0), GL3.GL_DYNAMIC_DRAW);
			gl.glUniformMatrix4fv(ThickLinesY.matrixHandle, 1, false, currentMatrix, 0);
			gl.glUniform4fv(ThickLinesY.colorHandle, 1, color, 0);
			gl.glUniform1i(ThickLinesY.xOffsetHandle, xOffset);
			gl.glUniform1f(ThickLinesY.lineWidthHandle, Theme.lineWidth);
			int[] viewportDimensions = new int[4]; // x,y,w,h
			gl.glGetIntegerv(GL3.GL_VIEWPORT, viewportDimensions, 0);
			gl.glUniform1f(ThickLinesY.widthPixelsHandle,  viewportDimensions[2]);
			gl.glUniform1f(ThickLinesY.heightPixelsHandle, viewportDimensions[3]);
		}

		// draw
		gl.glDrawArrays(lineType, 0, vertexCount);
		
	}
	
	/**
	 * Draws a buffer of (x,y) vertices as GL_LINES or GL_LINE_STRIP or GL_LINE_LOOP.
	 * 
	 * @param gl             The OpenGL context.
	 * @param lineType       GL_LINES or GL_LINE_STRIP or GL_LINE_LOOP.
	 * @param color          The color, as a float[] {r,g,b,a}.
	 * @param buffer         Vertex buffer containing (x1,y1,x2,y2,...)
	 * @param vertexCount    Number of vertices in the buffer.
	 */
	public static void drawLinesXy(GL2ES3 gl, int lineType, float[] color, FloatBuffer buffer, int vertexCount) {
		
		// send data to the GPU
		if(Theme.lineWidth == 1) {
			gl.glUseProgram(ThinLinesXY.programHandle);
			gl.glBindVertexArray(ThinLinesXY.vaoHandle);
			gl.glBindBuffer(GL3.GL_ARRAY_BUFFER, ThinLinesXY.vboHandle);
			gl.glBufferData(GL3.GL_ARRAY_BUFFER, vertexCount * 2 * 4, buffer.position(0), GL3.GL_DYNAMIC_DRAW);
			gl.glUniformMatrix4fv(ThinLinesXY.matrixHandle, 1, false, currentMatrix, 0);
			gl.glUniform4fv(ThinLinesXY.colorHandle, 1, color, 0);
		} else {
			gl.glUseProgram(ThickLinesXY.programHandle);
			gl.glBindVertexArray(ThickLinesXY.vaoHandle);
			gl.glBindBuffer(GL3.GL_ARRAY_BUFFER, ThickLinesXY.vboHandle);
			gl.glBufferData(GL3.GL_ARRAY_BUFFER, vertexCount * 2 * 4, buffer.position(0), GL3.GL_DYNAMIC_DRAW);
			gl.glUniformMatrix4fv(ThickLinesXY.matrixHandle, 1, false, currentMatrix, 0);
			gl.glUniform4fv(ThickLinesXY.colorHandle, 1, color, 0);
			gl.glUniform1f(ThickLinesXY.lineWidthHandle, Theme.lineWidth);
			int[] viewportDimensions = new int[4]; // x,y,w,h
			gl.glGetIntegerv(GL3.GL_VIEWPORT, viewportDimensions, 0);
			gl.glUniform1f(ThickLinesXY.widthPixelsHandle,  viewportDimensions[2]);
			gl.glUniform1f(ThickLinesXY.heightPixelsHandle, viewportDimensions[3]);
		}
		
		// draw
		gl.glDrawArrays(lineType, 0, vertexCount);
		
	}
	
	/**
	 * Draws a buffer of (x,y,r,g,b,a,...) vertices as GL_LINES or GL_LINE_STRIP or GL_LINE_LOOP.
	 * 
	 * @param gl             The OpenGL context.
	 * @param lineType       GL_LINES or GL_LINE_STRIP or GL_LINE_LOOP.
	 * @param buffer         Vertex buffer containing (x1,y1,r1,g1,b1,a1,...)
	 * @param vertexCount    Number of vertices in the buffer.
	 */
	public static void drawLinesXyrgba(GL2ES3 gl, int lineType, FloatBuffer buffer, int vertexCount) {
		
		// send data to the GPU
		if(Theme.lineWidth == 1) {
			gl.glUseProgram(ThinLinesXYRGBA.programHandle);
			gl.glBindVertexArray(ThinLinesXYRGBA.vaoHandle);
			gl.glBindBuffer(GL3.GL_ARRAY_BUFFER, ThinLinesXYRGBA.vboHandle);
			gl.glBufferData(GL3.GL_ARRAY_BUFFER, vertexCount * 6 * 4, buffer.position(0), GL3.GL_DYNAMIC_DRAW);
			gl.glUniformMatrix4fv(ThinLinesXYRGBA.matrixHandle, 1, false, currentMatrix, 0);
		} else {
			gl.glUseProgram(ThickLinesXYRGBA.programHandle);
			gl.glBindVertexArray(ThickLinesXYRGBA.vaoHandle);
			gl.glBindBuffer(GL3.GL_ARRAY_BUFFER, ThickLinesXYRGBA.vboHandle);
			gl.glBufferData(GL3.GL_ARRAY_BUFFER, vertexCount * 6 * 4, buffer.position(0), GL3.GL_DYNAMIC_DRAW);
			gl.glUniformMatrix4fv(ThickLinesXYRGBA.matrixHandle, 1, false, currentMatrix, 0);
			gl.glUniform1f(ThickLinesXYRGBA.lineWidthHandle, Theme.lineWidth);
			int[] viewportDimensions = new int[4]; // x,y,w,h
			gl.glGetIntegerv(GL3.GL_VIEWPORT, viewportDimensions, 0);
			gl.glUniform1f(ThickLinesXYRGBA.widthPixelsHandle,  viewportDimensions[2]);
			gl.glUniform1f(ThickLinesXYRGBA.heightPixelsHandle, viewportDimensions[3]);
		}
		
		// draw
		gl.glDrawArrays(lineType, 0, vertexCount);
		
	}
	
	/**
	 * Draws two buffers of (x1,x2,... ) (y1,y2,...) vertices as GL_LINES or GL_LINE_STRIP or GL_LINE_LOOP.
	 * 
	 * @param gl             The OpenGL context.
	 * @param lineType       GL_LINES or GL_LINE_STRIP or GL_LINE_LOOP.
	 * @param color          The color, as a float[] {r,g,b,a}.
	 * @param bufferX        Vertex buffer containing (x1,x2,...)
	 * @param bufferY        Vertex buffer containing (y1,y2,...)
	 * @param vertexCount    Number of vertices in the buffers.
	 */
	public static void drawLinesX_Y(GL2ES3 gl, int lineType, float[] color, FloatBuffer bufferX, FloatBuffer bufferY, int vertexCount) {
		
		// send data to the gpu
		if(Theme.lineWidth == 1) {
			gl.glUseProgram(ThinLinesX_Y.programHandle);
			gl.glBindVertexArray(ThinLinesX_Y.vaoHandle);
			gl.glBindBuffer(GL3.GL_ARRAY_BUFFER, ThinLinesX_Y.vboXhandle);
			gl.glBufferData(GL3.GL_ARRAY_BUFFER, vertexCount * 4, bufferX.position(0), GL3.GL_DYNAMIC_DRAW);
			gl.glBindBuffer(GL3.GL_ARRAY_BUFFER, ThinLinesX_Y.vboYhandle);
			gl.glBufferData(GL3.GL_ARRAY_BUFFER, vertexCount * 4, bufferY.position(0), GL3.GL_DYNAMIC_DRAW);
			gl.glUniformMatrix4fv(ThinLinesX_Y.matrixHandle, 1, false, currentMatrix, 0);
			gl.glUniform4fv(ThinLinesX_Y.colorHandle, 1, color, 0);
		} else {
			gl.glUseProgram(ThickLinesX_Y.programHandle);
			gl.glBindVertexArray(ThickLinesX_Y.vaoHandle);
			gl.glBindBuffer(GL3.GL_ARRAY_BUFFER, ThickLinesX_Y.vboXhandle);
			gl.glBufferData(GL3.GL_ARRAY_BUFFER, vertexCount * 4, bufferX.position(0), GL3.GL_DYNAMIC_DRAW);
			gl.glBindBuffer(GL3.GL_ARRAY_BUFFER, ThickLinesX_Y.vboYhandle);
			gl.glBufferData(GL3.GL_ARRAY_BUFFER, vertexCount * 4, bufferY.position(0), GL3.GL_DYNAMIC_DRAW);
			gl.glUniformMatrix4fv(ThickLinesX_Y.matrixHandle, 1, false, currentMatrix, 0);
			gl.glUniform4fv(ThickLinesX_Y.colorHandle, 1, color, 0);
			gl.glUniform1f(ThickLinesX_Y.lineWidthHandle, Theme.lineWidth);
			int[] viewportDimensions = new int[4]; // x,y,w,h
			gl.glGetIntegerv(GL3.GL_VIEWPORT, viewportDimensions, 0);
			gl.glUniform1f(ThickLinesX_Y.widthPixelsHandle,  viewportDimensions[2]);
			gl.glUniform1f(ThickLinesX_Y.heightPixelsHandle, viewportDimensions[3]);
		}
		
		// draw
		gl.glDrawArrays(lineType, 0, vertexCount);
		
	}
	
	/**
	 * Draws a buffer of (x,y) vertices as GL_TRIANGLES or GL_TRIANGLE_STRIP or GL_TRIANGLE_FAN.
	 * 
	 * @param gl              The OpenGL context.
	 * @param triangleType    GL_TRIANGLES or GL_TRIANGLE_STRIP or GL_TRIANGLE_FAN.
	 * @param color           The color, as a float[] {r,g,b,a}.
	 * @param buffer          Vertex buffer containing (x1,y1,x2,y2,...)
	 * @param vertexCount     Number of vertices in the buffer.
	 */
	public static void drawTrianglesXY(GL2ES3 gl, int triangleType, float[] color, FloatBuffer buffer, int vertexCount) {
		
		// send data to the GPU
		gl.glUseProgram(TrianglesXY.programHandle);
		gl.glBindVertexArray(TrianglesXY.vaoHandle);
		gl.glBindBuffer(GL3.GL_ARRAY_BUFFER, TrianglesXY.vboHandle);
		gl.glBufferData(GL3.GL_ARRAY_BUFFER, vertexCount * 2 * 4, buffer.position(0), GL3.GL_DYNAMIC_DRAW);
		gl.glUniformMatrix4fv(TrianglesXY.matrixHandle, 1, false, currentMatrix, 0);
		gl.glUniform4fv(TrianglesXY.colorHandle, 1, color, 0);
		
		// draw
		gl.glDrawArrays(triangleType, 0, vertexCount);
		
	}
	
	/**
	 * Draws a buffer of (x,y,r,g,b,a) vertices as GL_TRIANGLES or GL_TRIANGLE_STRIP or GL_TRIANGLE_FAN.
	 * 
	 * @param gl              The OpenGL context.
	 * @param triangleType    GL_TRIANGLES or GL_TRIANGLE_STRIP or GL_TRIANGLE_FAN.
	 * @param buffer          Vertex buffer containing (x1,y1,r1,g1,b1,a1,...)
	 * @param vertexCount     Number of vertices in the buffer.
	 */
	public static void drawTrianglesXYRGBA(GL2ES3 gl, int triangleType, FloatBuffer buffer, int vertexCount) {
		
		// send data to the GPU
		gl.glUseProgram(ThinLinesXYRGBA.programHandle);
		gl.glBindVertexArray(ThinLinesXYRGBA.vaoHandle);
		gl.glBindBuffer(GL3.GL_ARRAY_BUFFER, ThinLinesXYRGBA.vboHandle);
		gl.glBufferData(GL3.GL_ARRAY_BUFFER, vertexCount * 6 * 4, buffer.position(0), GL3.GL_DYNAMIC_DRAW);
		gl.glUniformMatrix4fv(ThinLinesXYRGBA.matrixHandle, 1, false, currentMatrix, 0);
		
		// draw
		gl.glDrawArrays(triangleType, 0, vertexCount);
		
	}
	
	/**
	 * Draws a buffer of (x,y,z,u,v,w) vertices as GL_TRIANGLES or GL_TRIANGLE_STRIP or GL_TRIANGLE_FAN.
	 * 
	 * @param gl              The OpenGL context.
	 * @param triangleType    GL_TRIANGLES or GL_TRIANGLE_STRIP or GL_TRIANGLE_FAN.
	 * @param buffer          Vertex buffer containing (x1,y1,z1,u1,v1,w1,...)
	 * @param vertexCount     Number of vertices in the buffer.
	 */
	public static void drawTrianglesXYZUVW(GL2ES3 gl, int triangleType, FloatBuffer buffer, int vertexCount) {
		
		// send data to the GPU
		gl.glUseProgram(TrianglesXYZUVW.programHandle);
		gl.glBindVertexArray(TrianglesXYZUVW.vaoHandle);
		gl.glBindBuffer(GL3.GL_ARRAY_BUFFER, TrianglesXYZUVW.vboHandle);
		gl.glBufferData(GL3.GL_ARRAY_BUFFER, vertexCount * 6 * 4, buffer.position(0), GL3.GL_DYNAMIC_DRAW);
		gl.glUniformMatrix4fv(TrianglesXYZUVW.matrixHandle, 1, false, currentMatrix, 0);
		
		// draw
		gl.glEnable(GL3.GL_DEPTH_TEST);
		gl.glClear(GL3.GL_DEPTH_BUFFER_BIT);
		gl.glDrawArrays(triangleType, 0, vertexCount);
		gl.glDisable(GL3.GL_DEPTH_TEST);
		
	}
	
	/**
	 * Helper function that draws a 2D triangle.
	 * 
	 * @param gl       The OpenGL context.
	 * @param color    The color, as a float[] {r,g,b,a}.
	 * @param x1       First vertex.
	 * @param y1       First vertex.
	 * @param x2       Second vertex.
	 * @param y2       Second vertex.
	 * @param x3       Third vertex.
	 * @param y3       Third vertex.
	 */
	public static void drawTriangle2D(GL2ES3 gl, float[] color, float x1, float y1, float x2, float y2, float x3, float y3) {
		
		buffer.rewind();
		buffer.put(x1); buffer.put(y1);
		buffer.put(x2); buffer.put(y2);
		buffer.put(x3); buffer.put(y3);
		buffer.rewind();
		drawTrianglesXY(gl, GL3.GL_TRIANGLE_STRIP, color, buffer, 3);
		
	}
	
	/**
	 * Draws a buffer of (x,y,s,t) vertices as a GL_TRIANGLE_STRIP.
	 * 
	 * @param gl               The OpenGL context.
	 * @param buffer           Vertex buffer containing (x1,y1,s1,t1,...)
	 * @param textureHandle    Texture to draw on the triangles.
	 * @param isFboTexture     True if the texture is an off-screen frame buffer, false if it's just a normal texture.
	 * @param vertexCount      Number of vertices in the buffer.
	 */
	public static void drawTriangleStripTextured2D(GL2ES3 gl, FloatBuffer buffer, int textureHandle, boolean isFboTexture, int vertexCount) {
		
		// send data to the GPU
		if(isFboTexture && SettingsController.getAntialiasingLevel() > 1) {
			gl.glUseProgram(TrianglesXYSTmultisample.programHandle);
			gl.glBindVertexArray(TrianglesXYSTmultisample.vaoHandle);
			gl.glBindBuffer(GL3.GL_ARRAY_BUFFER, TrianglesXYSTmultisample.vboHandle);
			gl.glBufferData(GL3.GL_ARRAY_BUFFER, vertexCount * 4 * 4, buffer.position(0), GL3.GL_DYNAMIC_DRAW);
			gl.glUniformMatrix4fv(TrianglesXYSTmultisample.matrixHandle, 1, false, currentMatrix, 0);
			gl.glBindTexture(GL3.GL_TEXTURE_2D_MULTISAMPLE, textureHandle);
		} else {
			gl.glUseProgram(TrianglesXYST.programHandle);
			gl.glBindVertexArray(TrianglesXYST.vaoHandle);
			gl.glBindBuffer(GL3.GL_ARRAY_BUFFER, TrianglesXYST.vboHandle);
			gl.glBufferData(GL3.GL_ARRAY_BUFFER, vertexCount * 4 * 4, buffer.position(0), GL3.GL_STATIC_DRAW);
			gl.glUniformMatrix4fv(TrianglesXYST.matrixHandle, 1, false, currentMatrix, 0);
			gl.glBindTexture(GL3.GL_TEXTURE_2D, textureHandle);
		}
		
		// draw
		gl.glBlendFunc(GL3.GL_ONE, GL3.GL_ONE_MINUS_SRC_ALPHA);
		gl.glDrawArrays(GL3.GL_TRIANGLE_STRIP, 0, vertexCount);
		gl.glBlendFunc(GL3.GL_SRC_ALPHA, GL3.GL_ONE_MINUS_SRC_ALPHA);
		
	}
	
	/**
	 * Helper function that draws a color-filled axis-aligned quad.
	 * 
	 * @param gl            The OpenGL context.
	 * @param color         The color, as a float[] {r,g,b,a}.
	 * @param lowerLeftX    Lower-left x location.
	 * @param lowerLeftY    Lower-left y location.
	 * @param width         Width of the quad.
	 * @param height        Height of the quad.
	 */
	public static void drawBox(GL2ES3 gl, float[] color, float lowerLeftX, float lowerLeftY, float width, float height) {
		
		buffer.rewind();
		buffer.put(lowerLeftX);         buffer.put(lowerLeftY + height);
		buffer.put(lowerLeftX);         buffer.put(lowerLeftY);
		buffer.put(lowerLeftX + width); buffer.put(lowerLeftY + height);
		buffer.put(lowerLeftX + width); buffer.put(lowerLeftY);
		buffer.rewind();
		drawTrianglesXY(gl, GL3.GL_TRIANGLE_STRIP, color, buffer, 4);
		
	}
	
	/**
	 * Helper function that draws the outline of an axis-aligned quad.
	 * 
	 * @param gl            The OpenGL context.
	 * @param color         The color, as a float[] {r,g,b,a}.
	 * @param lowerLeftX    Lower-left x location.
	 * @param lowerLeftY    Lower-left y location.
	 * @param width         Width of the quad.
	 * @param height        Height of the quad.
	 */
	public static void drawBoxOutline(GL2ES3 gl, float[] color, float lowerLeftX, float lowerLeftY, float width, float height) {
		
		buffer.rewind();
		buffer.put(lowerLeftX);         buffer.put(lowerLeftY);
		buffer.put(lowerLeftX);         buffer.put(lowerLeftY + height);
		buffer.put(lowerLeftX + width); buffer.put(lowerLeftY + height);
		buffer.put(lowerLeftX + width); buffer.put(lowerLeftY);
		buffer.rewind();
		drawLinesXy(gl, GL3.GL_LINE_LOOP, color, buffer, 4);
		
	}
	
	/**
	 * Helper function that draws a color-filled 2D quad.
	 * 
	 * @param gl             The OpenGL context.
	 * @param color          The color, as a float[] {r,g,b,a}.
	 * @param lowerLeftX     Lower-left x location.
	 * @param lowerLeftY     Lower-left y location.
	 * @param upperRightX    Upper-right x location.
	 * @param upperRightY    Upper-right y location.
	 */
	public static void drawQuad2D(GL2ES3 gl, float[] color, float lowerLeftX, float lowerLeftY, float upperRightX, float upperRightY) {
		
		buffer.rewind();
		buffer.put(lowerLeftX);  buffer.put(upperRightY);
		buffer.put(lowerLeftX);  buffer.put(lowerLeftY);
		buffer.put(upperRightX); buffer.put(upperRightY);
		buffer.put(upperRightX); buffer.put(lowerLeftY);
		buffer.rewind();
		drawTrianglesXY(gl, GL3.GL_TRIANGLE_STRIP, color, buffer, 4);
		
	}
	
	/**
	 * Helper function that draws the outline of a 2D quad.
	 * 
	 * @param gl             The OpenGL context.
	 * @param color          The color, as a float[] {r,g,b,a}.
	 * @param lowerLeftX     Lower-left x location.
	 * @param lowerLeftY     Lower-left y location.
	 * @param upperRightX    Upper-right x location.
	 * @param upperRightY    Upper-right y location.
	 */
	public static void drawQuadOutline2D(GL2ES3 gl, float[] color, float lowerLeftX, float lowerLeftY, float upperRightX, float upperRightY) {
		
		buffer.rewind();
		buffer.put(lowerLeftX);  buffer.put(upperRightY);
		buffer.put(lowerLeftX);  buffer.put(lowerLeftY);
		buffer.put(upperRightX); buffer.put(lowerLeftY);
		buffer.put(upperRightX); buffer.put(upperRightY);
		buffer.rewind();
		drawLinesXy(gl, GL3.GL_LINE_LOOP, color, buffer, 4);
		
	}
	
	/**
	 * Draws text with the small font.
	 * 
	 * @param gl         The OpenGL context.
	 * @param text       Text to draw.
	 * @param x          Lower-left corner, in pixels.
	 * @param y          Lower-left corner, in pixels.
	 * @param degrees    Amount to rotate counter-clockwise, pivoting around (x,y).
	 */
	public static void drawSmallText(GL2ES3 gl, String text, int x, int y, float degrees) {
		
		if(text.length() > buffer.capacity() / 5)
			return;
		
		y -= smallFontBaselineOffset;
		
		// modify the matrix if rotating
		float[] rotatedMatrix = null;
		if(degrees != 0) {
			rotatedMatrix = Arrays.copyOf(currentMatrix, currentMatrix.length);
			translateMatrix(rotatedMatrix, x, y, 0);
			rotateMatrix(rotatedMatrix, degrees, 0, 0, 1);
			translateMatrix(rotatedMatrix, -x, -y, 0);
		}
		
		// calculate the (x,y,s,t,w) "vertex" for each character
		buffer.rewind();
		for(int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			int charIndex = c - firstAsciiChar;
			int atlasX = 0;
			int atlasY = 0;
			int atlasWidth = 0;
			if(charIndex < smallFontAsciiCharWidthLUT.length && charIndex >= 0) {
				atlasX = (charIndex % smallFontCharsPerRow) * smallFontMaxCharWidth;
				atlasY = (charIndex / smallFontCharsPerRow) * smallFontMaxCharHeight;
				atlasWidth = smallFontAsciiCharWidthLUT[charIndex];
			} else {
				int[] details = nonAsciiCharMap.get(c);
				if(details != null) {
					atlasX = details[0];
					atlasY = details[1];
					atlasWidth = details[2];
				} else {
					// character not in the texture atlas. so check for any other missing characters, then regenerate the atlas, then call this function again
					for(int j = 0; j < text.length(); j++) {
						char c2 = text.charAt(j);
						if((c2 > lastAsciiChar || c2 < firstAsciiChar) && !nonAsciiCharMap.containsKey(c2))
							nonAsciiCharMap.put(c2, new int[9]);
					}
					updateFontTextures(gl);
					drawSmallText(gl, text, x, y, degrees);
					return;
				}
			}
			buffer.put(x);
			buffer.put(y);
			buffer.put(atlasX);
			buffer.put(atlasY);
			buffer.put(atlasWidth);
			x += atlasWidth;
		}
		buffer.rewind();
		
		// send data to the GPU
		gl.glUseProgram(FontRenderer.programHandle);
		gl.glBindVertexArray(FontRenderer.vaoHandle);
		gl.glBindBuffer(GL3.GL_ARRAY_BUFFER, FontRenderer.vboHandle);
		gl.glBufferData(GL3.GL_ARRAY_BUFFER, text.length() * 5 * 4, buffer.position(0), GL3.GL_DYNAMIC_DRAW);
		gl.glUniformMatrix4fv(FontRenderer.matrixHandle, 1, false, degrees == 0 ? currentMatrix : rotatedMatrix, 0);
		gl.glUniform1f(FontRenderer.lineHeightHandle, smallFontMaxCharHeight);
		gl.glBindTexture(GL3.GL_TEXTURE_2D, FontRenderer.smallFontTextureHandle[0]);

		// draw "points" (which become textured quads)
		gl.glDrawArrays(GL3.GL_POINTS, 0, text.length());
		
	}
	
	/**
	 * Draws text with the medium font.
	 * 
	 * @param gl         The OpenGL context.
	 * @param text       Text to draw.
	 * @param x          Lower-left corner, in pixels.
	 * @param y          Lower-left corner, in pixels.
	 * @param degrees    Amount to rotate counter-clockwise, pivoting around (x,y).
	 */
	public static void drawMediumText(GL2ES3 gl, String text, int x, int y, float degrees) {
		
		if(text.length() > buffer.capacity() / 5)
			return;
		
		y -= mediumFontBaselineOffset;
		
		// modify the matrix if rotating
		float[] rotatedMatrix = null;
		if(degrees != 0) {
			rotatedMatrix = Arrays.copyOf(currentMatrix, currentMatrix.length);
			translateMatrix(rotatedMatrix, x, y, 0);
			rotateMatrix(rotatedMatrix, degrees, 0, 0, 1);
			translateMatrix(rotatedMatrix, -x, -y, 0);
		}
		
		// calculate the (x,y,s,t,w) "vertex" for each character
		buffer.rewind();
		for(int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			int charIndex = c - firstAsciiChar;
			int atlasX = 0;
			int atlasY = 0;
			int atlasWidth = 0;
			if(charIndex < mediumFontAsciiCharWidthLUT.length && charIndex >= 0) {
				atlasX = (charIndex % mediumFontCharsPerRow) * mediumFontMaxCharWidth;
				atlasY = (charIndex / mediumFontCharsPerRow) * mediumFontMaxCharHeight;
				atlasWidth = mediumFontAsciiCharWidthLUT[charIndex];
			} else {
				int[] details = nonAsciiCharMap.get(c);
				if(details != null) {
					atlasX = details[3];
					atlasY = details[4];
					atlasWidth = details[5];
				} else {
					// character not in the texture atlas. so check for any other missing characters, then regenerate the atlas, then call this function again
					for(int j = 0; j < text.length(); j++) {
						char c2 = text.charAt(j);
						if((c2 > lastAsciiChar || c2 < firstAsciiChar) && !nonAsciiCharMap.containsKey(c2))
							nonAsciiCharMap.put(c2, new int[9]);
					}
					updateFontTextures(gl);
					drawSmallText(gl, text, x, y, degrees);
					return;
				}
			}
			buffer.put(x);
			buffer.put(y);
			buffer.put(atlasX);
			buffer.put(atlasY);
			buffer.put(atlasWidth);
			x += atlasWidth;
		}
		buffer.rewind();
		
		// send data to the GPU
		gl.glUseProgram(FontRenderer.programHandle);
		gl.glBindVertexArray(FontRenderer.vaoHandle);
		gl.glBindBuffer(GL3.GL_ARRAY_BUFFER, FontRenderer.vboHandle);
		gl.glBufferData(GL3.GL_ARRAY_BUFFER, text.length() * 5 * 4, buffer.position(0), GL3.GL_DYNAMIC_DRAW);
		gl.glUniformMatrix4fv(FontRenderer.matrixHandle, 1, false, degrees == 0 ? currentMatrix : rotatedMatrix, 0);
		gl.glUniform1f(FontRenderer.lineHeightHandle, mediumFontMaxCharHeight);
		gl.glBindTexture(GL3.GL_TEXTURE_2D, FontRenderer.mediumFontTextureHandle[0]);

		// draw "points" (which become textured quads)
		gl.glDrawArrays(GL3.GL_POINTS, 0, text.length());
		
	}
	
	/**
	 * Draws text with the large font.
	 * 
	 * @param gl         The OpenGL context.
	 * @param text       Text to draw.
	 * @param x          Lower-left corner, in pixels.
	 * @param y          Lower-left corner, in pixels.
	 * @param degrees    Amount to rotate counter-clockwise, pivoting around (x,y).
	 */
	public static void drawLargeText(GL2ES3 gl, String text, int x, int y, float degrees) {
		
		if(text.length() > buffer.capacity() / 5)
			return;
		
		y -= largeFontBaselineOffset;
		
		// modify the matrix if rotating
		float[] rotatedMatrix = null;
		if(degrees != 0) {
			rotatedMatrix = Arrays.copyOf(currentMatrix, currentMatrix.length);
			translateMatrix(rotatedMatrix, x, y, 0);
			rotateMatrix(rotatedMatrix, degrees, 0, 0, 1);
			translateMatrix(rotatedMatrix, -x, -y, 0);
		}
		
		// calculate the (x,y,s,t,w) "vertex" for each character
		buffer.rewind();
		for(int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			int charIndex = c - firstAsciiChar;
			int atlasX = 0;
			int atlasY = 0;
			int atlasWidth = 0;
			if(charIndex < largeFontAsciiCharWidthLUT.length && charIndex >= 0) {
				atlasX = (charIndex % largeFontCharsPerRow) * largeFontMaxCharWidth;
				atlasY = (charIndex / largeFontCharsPerRow) * largeFontMaxCharHeight;
				atlasWidth = largeFontAsciiCharWidthLUT[charIndex];
			} else {
				int[] details = nonAsciiCharMap.get(c);
				if(details != null) {
					atlasX = details[6];
					atlasY = details[7];
					atlasWidth = details[8];
				} else {
					// character not in the texture atlas. so check for any other missing characters, then regenerate the atlas, then call this function again
					for(int j = 0; j < text.length(); j++) {
						char c2 = text.charAt(j);
						if((c2 > lastAsciiChar || c2 < firstAsciiChar) && !nonAsciiCharMap.containsKey(c2))
							nonAsciiCharMap.put(c2, new int[9]);
					}
					updateFontTextures(gl);
					drawSmallText(gl, text, x, y, degrees);
					return;
				}
			}
			buffer.put(x);
			buffer.put(y);
			buffer.put(atlasX);
			buffer.put(atlasY);
			buffer.put(atlasWidth);
			x += atlasWidth;
		}
		buffer.rewind();
		
		// send data to the GPU
		gl.glUseProgram(FontRenderer.programHandle);
		gl.glBindVertexArray(FontRenderer.vaoHandle);
		gl.glBindBuffer(GL3.GL_ARRAY_BUFFER, FontRenderer.vboHandle);
		gl.glBufferData(GL3.GL_ARRAY_BUFFER, text.length() * 5 * 4, buffer.position(0), GL3.GL_DYNAMIC_DRAW);
		gl.glUniformMatrix4fv(FontRenderer.matrixHandle, 1, false, degrees == 0 ? currentMatrix : rotatedMatrix, 0);
		gl.glUniform1f(FontRenderer.lineHeightHandle, largeFontMaxCharHeight);
		gl.glBindTexture(GL3.GL_TEXTURE_2D, FontRenderer.largeFontTextureHandle[0]);

		// draw "points" (which become textured quads)
		gl.glDrawArrays(GL3.GL_POINTS, 0, text.length());
		
	}
	
	/**
	 * @param gl      The OpenGL context.
	 * @param text    The text.
	 * @return        Width, in pixels.
	 */
	public static float smallTextWidth(GL2ES3 gl, String text) {
		
		float width = 0;
		for(int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			int charIndex = c - firstAsciiChar;
			if(charIndex < smallFontAsciiCharWidthLUT.length && charIndex >= 0) {
				width += smallFontAsciiCharWidthLUT[charIndex];
			} else {
				int[] details = nonAsciiCharMap.get(c);
				if(details != null) {
					width += details[2];
				} else {
					// character not in the texture atlas. so check for any other missing characters, then regenerate the atlas, then call this function again
					for(int j = 0; j < text.length(); j++) {
						char c2 = text.charAt(j);
						if((c2 > lastAsciiChar || c2 < firstAsciiChar) && !nonAsciiCharMap.containsKey(c2))
							nonAsciiCharMap.put(c2, new int[9]);
					}
					updateFontTextures(gl);
					return smallTextWidth(gl, text);
				}
			}
			
		}
		return width;
		
	}
	
	/**
	 * @param gl      The OpenGL context.
	 * @param text    The text.
	 * @return        Width, in pixels.
	 */
	public static float mediumTextWidth(GL2ES3 gl, String text) {
		
		float width = 0;
		for(int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			int charIndex = c - firstAsciiChar;
			if(charIndex < mediumFontAsciiCharWidthLUT.length && charIndex >= 0) {
				width += mediumFontAsciiCharWidthLUT[charIndex];
			} else {
				int[] details = nonAsciiCharMap.get(c);
				if(details != null) {
					width += details[5];
				} else {
					// character not in the texture atlas. so check for any other missing characters, then regenerate the atlas, then call this function again
					for(int j = 0; j < text.length(); j++) {
						char c2 = text.charAt(j);
						if((c2 > lastAsciiChar || c2 < firstAsciiChar) && !nonAsciiCharMap.containsKey(c2))
							nonAsciiCharMap.put(c2, new int[9]);
					}
					updateFontTextures(gl);
					return mediumTextWidth(gl, text);
				}
			}
			
		}
		return width;
		
	}
	
	/**
	 * @param gl      The OpenGL context.
	 * @param text    The text.
	 * @return        Width, in pixels.
	 */
	public static float largeTextWidth(GL2ES3 gl, String text) {
		
		float width = 0;
		for(int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			int charIndex = c - firstAsciiChar;
			if(charIndex < largeFontAsciiCharWidthLUT.length && charIndex >= 0) {
				width += largeFontAsciiCharWidthLUT[charIndex];
			} else {
				int[] details = nonAsciiCharMap.get(c);
				if(details != null) {
					width += details[8];
				} else {
					// character not in the texture atlas. so check for any other missing characters, then regenerate the atlas, then call this function again
					for(int j = 0; j < text.length(); j++) {
						char c2 = text.charAt(j);
						if((c2 > lastAsciiChar || c2 < firstAsciiChar) && !nonAsciiCharMap.containsKey(c2))
							nonAsciiCharMap.put(c2, new int[9]);
					}
					updateFontTextures(gl);
					return largeTextWidth(gl, text);
				}
			}
			
		}
		return width;
		
	}
	
	/**
	 * Draws new texture atlases for each font and updates the variables/GPU accordingly.
	 * 
	 * @param gl    The OpenGL context.
	 */
	public static void updateFontTextures(GL2ES3 gl) {
		
		int asciiCharCount = lastAsciiChar - firstAsciiChar + 1;
		int nonAsciiCharCount = nonAsciiCharMap.size();
		int charCount = asciiCharCount + nonAsciiCharCount;
		
		int textureWidth = 128;
		int maxCharWidth = 100;
		int maxCharHeight = 100;
		BufferedImage image = null;
		Graphics2D g = null;
		int baselineOffset = 0;
		int x = 0;
		int y = 0;
		ByteBuffer buffer = null;
		
		// prepare for small font texture
		while(maxCharWidth * maxCharHeight * charCount > textureWidth * textureWidth) {
			textureWidth *= 2;
			image = new BufferedImage(textureWidth, textureWidth, BufferedImage.TYPE_4BYTE_ABGR);
			g = image.createGraphics();
			g.setColor(Color.BLACK);
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setFont(Theme.smallFont);
			FontMetrics fm = g.getFontMetrics();
			maxCharWidth = fm.getMaxAdvance();
			maxCharHeight = fm.getMaxAscent() + fm.getMaxDescent();
			baselineOffset = fm.getMaxDescent();
			int charsPerRow = textureWidth / maxCharWidth;
			smallFontMaxCharWidth = maxCharWidth;
			smallFontMaxCharHeight = maxCharHeight;
			smallFontCharsPerRow = charsPerRow;
			smallFontAsciiCharWidthLUT = new int[asciiCharCount];
			smallTextHeight = fm.getAscent() - baselineOffset;
			smallFontBaselineOffset = baselineOffset;
		}
		
		// draw the small font texture atlas
		x = 0;
		y = maxCharHeight;
		for(char c = firstAsciiChar; c <= lastAsciiChar; c++) {
			String s = new String(c + "");
			int charWidth = (int) Math.ceil(Theme.smallFont.getStringBounds(s, g.getFontRenderContext()).getWidth());
			smallFontAsciiCharWidthLUT[c - firstAsciiChar] = charWidth;
			g.drawString(s, x, y-baselineOffset);
			x += maxCharWidth;
			if(x + maxCharWidth > textureWidth) {
				x = 0;
				y += maxCharHeight;
			}
		}
		for(Map.Entry<Character, int[]> nonAsciiChar : nonAsciiCharMap.entrySet()) {
			char c = nonAsciiChar.getKey();
			int[] details = nonAsciiChar.getValue();
			String s = new String(c + "");
			int charWidth = (int) Math.ceil(Theme.smallFont.getStringBounds(s, g.getFontRenderContext()).getWidth());
			details[0] = x;
			details[1] = y - maxCharHeight;
			details[2] = charWidth;
			g.drawString(s, x, y-baselineOffset);
			x += maxCharWidth;
			if(x + maxCharWidth > textureWidth) {
				x = 0;
				y += maxCharHeight;
			}
		}
		
		// send the small font texture to the GPU
		buffer = Buffers.newDirectByteBuffer(((DataBufferByte) image.getRaster().getDataBuffer()).getData());
		writeTexture(gl, FontRenderer.smallFontTextureHandle, textureWidth, textureWidth, GL3.GL_RGBA, GL3.GL_UNSIGNED_BYTE, buffer); // actually ABGR, so swizzling it in the fragment shader
		
		// prepare for medium font texture
		textureWidth = 128;
		maxCharWidth = 100;
		maxCharHeight = 100;
		while(maxCharWidth * maxCharHeight * charCount > textureWidth * textureWidth) {
			textureWidth *= 2;
			image = new BufferedImage(textureWidth, textureWidth, BufferedImage.TYPE_4BYTE_ABGR);
			g = image.createGraphics();
			g.setColor(Color.BLACK);
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setFont(Theme.mediumFont);
			FontMetrics fm = g.getFontMetrics();
			maxCharWidth = fm.getMaxAdvance();
			maxCharHeight = fm.getMaxAscent() + fm.getMaxDescent();
			baselineOffset = fm.getMaxDescent();
			int charsPerRow = textureWidth / maxCharWidth;
			mediumFontMaxCharWidth = maxCharWidth;
			mediumFontMaxCharHeight = maxCharHeight;
			mediumFontCharsPerRow = charsPerRow;
			mediumFontAsciiCharWidthLUT = new int[asciiCharCount];
			mediumTextHeight = fm.getAscent() - baselineOffset;
			mediumFontBaselineOffset = baselineOffset;
		}
		
		// draw the medium font texture atlas
		x = 0;
		y = maxCharHeight;
		for(char c = firstAsciiChar; c <= lastAsciiChar; c++) {
			String s = new String(c + "");
			int charWidth = (int) Math.ceil(Theme.mediumFont.getStringBounds(s, g.getFontRenderContext()).getWidth());
			mediumFontAsciiCharWidthLUT[c - firstAsciiChar] = charWidth;
			g.drawString(s, x, y-baselineOffset);
			x += maxCharWidth;
			if(x + maxCharWidth > textureWidth) {
				x = 0;
				y += maxCharHeight;
			}
		}
		for(Map.Entry<Character, int[]> nonAsciiChar : nonAsciiCharMap.entrySet()) {
			char c = nonAsciiChar.getKey();
			int[] details = nonAsciiChar.getValue();
			String s = new String(c + "");
			int charWidth = (int) Math.ceil(Theme.mediumFont.getStringBounds(s, g.getFontRenderContext()).getWidth());
			details[3] = x;
			details[4] = y - maxCharHeight;
			details[5] = charWidth;
			g.drawString(s, x, y-baselineOffset);
			x += maxCharWidth;
			if(x + maxCharWidth > textureWidth) {
				x = 0;
				y += maxCharHeight;
			}
		}
		
		// send the medium font texture to the GPU
		buffer = Buffers.newDirectByteBuffer(((DataBufferByte) image.getRaster().getDataBuffer()).getData());
		writeTexture(gl, FontRenderer.mediumFontTextureHandle, textureWidth, textureWidth, GL3.GL_RGBA, GL3.GL_UNSIGNED_BYTE, buffer); // actually ABGR, so swizzling it in the fragment shader
		
		// prepare for large font texture
		textureWidth = 128;
		maxCharWidth = 100;
		maxCharHeight = 100;
		while(maxCharWidth * maxCharHeight * charCount > textureWidth * textureWidth) {
			textureWidth *= 2;
			image = new BufferedImage(textureWidth, textureWidth, BufferedImage.TYPE_4BYTE_ABGR);
			g = image.createGraphics();
			g.setColor(Color.BLACK);
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setFont(Theme.largeFont);
			FontMetrics fm = g.getFontMetrics();
			maxCharWidth = fm.getMaxAdvance();
			maxCharHeight = fm.getMaxAscent() + fm.getMaxDescent();
			baselineOffset = fm.getMaxDescent();
			int charsPerRow = textureWidth / maxCharWidth;
			largeFontMaxCharWidth = maxCharWidth;
			largeFontMaxCharHeight = maxCharHeight;
			largeFontCharsPerRow = charsPerRow;
			largeFontAsciiCharWidthLUT = new int[asciiCharCount];
			largeTextHeight = fm.getAscent() - baselineOffset;
			largeFontBaselineOffset = baselineOffset;
		}
		
		// draw the large font texture atlas
		x = 0;
		y = maxCharHeight;
		for(char c = firstAsciiChar; c <= lastAsciiChar; c++) {
			String s = new String(c + "");
			int charWidth = (int) Math.ceil(Theme.largeFont.getStringBounds(s, g.getFontRenderContext()).getWidth());
			largeFontAsciiCharWidthLUT[c - firstAsciiChar] = charWidth;
			g.drawString(s, x, y-baselineOffset);
			x += maxCharWidth;
			if(x + maxCharWidth > textureWidth) {
				x = 0;
				y += maxCharHeight;
			}
		}
		for(Map.Entry<Character, int[]> nonAsciiChar : nonAsciiCharMap.entrySet()) {
			char c = nonAsciiChar.getKey();
			int[] details = nonAsciiChar.getValue();
			String s = new String(c + "");
			int charWidth = (int) Math.ceil(Theme.largeFont.getStringBounds(s, g.getFontRenderContext()).getWidth());
			details[6] = x;
			details[7] = y - maxCharHeight;
			details[8] = charWidth;
			g.drawString(s, x, y-baselineOffset);
			x += maxCharWidth;
			if(x + maxCharWidth > textureWidth) {
				x = 0;
				y += maxCharHeight;
			}
		}
		
		// send the large font texture to the GPU
		buffer = Buffers.newDirectByteBuffer(((DataBufferByte) image.getRaster().getDataBuffer()).getData());
		writeTexture(gl, FontRenderer.largeFontTextureHandle, textureWidth, textureWidth, GL3.GL_RGBA, GL3.GL_UNSIGNED_BYTE, buffer); // actually ABGR, so swizzling it in the fragment shader
		
//		JFrame window = new JFrame("Large Font Atlas");
//		window.add(new JLabel(new ImageIcon(image)));
//		window.pack();
//		window.setVisible(true);
		
	}
	
	/**
	 * Creates an empty non-multisampled texture.
	 * 
	 * @param gl               The OpenGL context.
	 * @param textureHandle    The texture handle will be saved here.
	 * @param width            Width, in pixels.
	 * @param height           Height, in pixels.
	 * @param pixelFormat      GL_RGB or GL_BGR or GL_RGBA.
	 * @param pixelType        GL_UNSIGNED_BYTE or GL_FLOAT.
	 * @param antialias        True for linear filtering, or false for nearest filtering.
	 */
	public static void createTexture(GL2ES3 gl, int[] textureHandle, int width, int height, int pixelFormat, int pixelType, boolean antialias) {
		
		gl.glGenTextures(1, textureHandle, 0);
		gl.glBindTexture(GL3.GL_TEXTURE_2D, textureHandle[0]);
		gl.glTexImage2D(GL3.GL_TEXTURE_2D, 0, pixelFormat == GL3.GL_RGBA ? GL3.GL_RGBA : GL3.GL_RGB, width, height, 0, pixelFormat, pixelType, null);
		if(antialias) {
			gl.glTexParameteri(GL3.GL_TEXTURE_2D, GL3.GL_TEXTURE_MIN_FILTER, GL3.GL_LINEAR);
			gl.glTexParameteri(GL3.GL_TEXTURE_2D, GL3.GL_TEXTURE_MAG_FILTER, GL3.GL_LINEAR);
		} else {
			gl.glTexParameteri(GL3.GL_TEXTURE_2D, GL3.GL_TEXTURE_MIN_FILTER, GL3.GL_NEAREST);
			gl.glTexParameteri(GL3.GL_TEXTURE_2D, GL3.GL_TEXTURE_MAG_FILTER, GL3.GL_NEAREST);
		}
		
	}
	
	/**
	 * Replaces the contents of a non-multisampled texture.
	 * 
	 * @param gl               The OpenGL context.
	 * @param textureHandle    Handle to the texture.
	 * @param width            Width, in pixels.
	 * @param height           Height, in pixels.
	 * @param pixelFormat      GL_RGB or GL_BGR or GL_RGBA.
	 * @param pixelType        GL_UNSIGNED_BYTE or GL_FLOAT.
	 * @param pixels           ByteBuffer of pixel data.
	 */
	public static void writeTexture(GL2ES3 gl, int[] textureHandle, int width, int height, int pixelFormat, int pixelType, ByteBuffer pixels) {

		gl.glBindTexture(GL3.GL_TEXTURE_2D, textureHandle[0]);
		gl.glTexImage2D(GL3.GL_TEXTURE_2D, 0, pixelFormat == GL3.GL_RGBA ? GL3.GL_RGBA : GL3.GL_RGB, width, height, 0, pixelFormat, pixelType, pixels);
		
	}
	
	/**
	 * Creates an off-screen framebuffer and corresponding multisample texture to use with it.
	 * The texture is configured for RGBA uint8, with min/mag filter set to nearest.
	 * 
	 * @param gl               The OpenGL context.
	 * @param fboHandle        The FBO handle will be saved here.
	 * @param textureHandle    The texture handle will be saved here.
	 */
	public static void createOffscreenFramebuffer(GL2ES3 gl, int[] fboHandle, int[] textureHandle) {
		
		// create and use a framebuffer
		gl.glGenFramebuffers(1, fboHandle, 0);
		gl.glBindFramebuffer(GL3.GL_FRAMEBUFFER, fboHandle[0]);
		
		// create and use a texture
		gl.glGenTextures(1, textureHandle, 0);
		if(SettingsController.getAntialiasingLevel() > 1) {
			gl.glBindTexture(GL3.GL_TEXTURE_2D_MULTISAMPLE, textureHandle[0]);
			gl.glTexImage2DMultisample(GL3.GL_TEXTURE_2D_MULTISAMPLE, SettingsController.getAntialiasingLevel(), GL3.GL_RGBA, 512, 512, true);
			gl.glFramebufferTexture2D(GL3.GL_FRAMEBUFFER, GL3.GL_COLOR_ATTACHMENT0, GL3.GL_TEXTURE_2D_MULTISAMPLE, textureHandle[0], 0);
		} else {
			gl.glBindTexture(GL3.GL_TEXTURE_2D, textureHandle[0]);
			gl.glTexImage2D(GL3.GL_TEXTURE_2D, 0, GL3.GL_RGBA, 512, 512, 0, GL3.GL_RGBA, GL3.GL_UNSIGNED_BYTE, null); // dummy 512x512 texture
			gl.glTexParameteri(GL3.GL_TEXTURE_2D, GL3.GL_TEXTURE_MIN_FILTER, GL3.GL_NEAREST);
			gl.glTexParameteri(GL3.GL_TEXTURE_2D, GL3.GL_TEXTURE_MAG_FILTER, GL3.GL_NEAREST);
			gl.glFramebufferTexture2D(GL3.GL_FRAMEBUFFER, GL3.GL_COLOR_ATTACHMENT0, GL3.GL_TEXTURE_2D, textureHandle[0], 0);
		}
		gl.glDrawBuffers(1, new int[] {GL3.GL_COLOR_ATTACHMENT0}, 0);
		
		// check for errors
		if(gl.glCheckFramebufferStatus(GL3.GL_FRAMEBUFFER) != GL3.GL_FRAMEBUFFER_COMPLETE)
			NotificationsController.showFailureForSeconds("OpenGL Error: unable to create the framebuffer or texture.", 999, false);
		
		// switch back to the screen framebuffer
		gl.glBindFramebuffer(GL3.GL_FRAMEBUFFER, 0);
		
	}
	
	private static int[] onscreenViewport = new int[4]; // x,y,w,h
	private static int[] onscreenScissor  = new int[4]; // x,y,w,h
	
	/**
	 * Saves the current viewport/scissor/point settings, disables the scissor test,
	 * switches to the off-screen framebuffer and replaces the existing texture with a new one,
	 * then applies the new matrix.
	 * 
	 * @param gl                 The OpenGL context.
	 * @param offscreenMatrix    The 4x4 matrix to use.
	 * @param fboHandle          Handle to the FBO.
	 * @param textureHandle      Handle to the texture.
	 * @param width              Width, in pixels.
	 * @param height             Height, in pixels.
	 */
	public static void startDrawingOffscreen(GL2ES3 gl, float[] offscreenMatrix, int[] fboHandle, int[] textureHandle, int width, int height) {
		
		// save the on-screen viewport and scissor settings
		gl.glGetIntegerv(GL3.GL_VIEWPORT, onscreenViewport, 0);
		gl.glGetIntegerv(GL3.GL_SCISSOR_BOX, onscreenScissor, 0);

		// switch to the off-screen framebuffer and corresponding texture
		gl.glBindFramebuffer(GL3.GL_FRAMEBUFFER, fboHandle[0]);
		if(SettingsController.getAntialiasingLevel() > 1)
			gl.glBindTexture(GL3.GL_TEXTURE_2D_MULTISAMPLE, textureHandle[0]);
		else
			gl.glBindTexture(GL3.GL_TEXTURE_2D, textureHandle[0]);

		// replace the existing texture
		if(SettingsController.getAntialiasingLevel() > 1)
			gl.glTexImage2DMultisample(GL3.GL_TEXTURE_2D_MULTISAMPLE, SettingsController.getAntialiasingLevel(), GL3.GL_RGBA, width, height, true);
		else
			gl.glTexImage2D(GL3.GL_TEXTURE_2D, 0, GL3.GL_RGBA, width, height, 0, GL3.GL_RGBA, GL3.GL_UNSIGNED_BYTE, null);

		// set the viewport and disable the scissor test
		gl.glViewport(0, 0, width, height);
		gl.glDisable(GL3.GL_SCISSOR_TEST);
		
		// set the matrix
		useMatrix(gl, offscreenMatrix);
		
		// set the blend function
		gl.glBlendFuncSeparate(GL3.GL_SRC_ALPHA, GL3.GL_ONE_MINUS_SRC_ALPHA, GL3.GL_ONE, GL3.GL_ONE_MINUS_SRC_ALPHA);
		
		// clear the texture
		gl.glClearColor(0, 0, 0, 0);
		gl.glClear(GL3.GL_COLOR_BUFFER_BIT);
		
	}
	
	/**
	 * Saves the current viewport/scissor/point settings, disables the scissor test,
	 * switches to the off-screen framebuffer and applies the new matrix.
	 * 
	 * @param gl                 The OpenGL context.
	 * @param offscreenMatrix    The 4x4 matrix to use.
	 * @param fboHandle          Handle to the FBO.
	 * @param textureHandle      Handle to the texture.
	 * @param width              Width, in pixels.
	 * @param height             Height, in pixels.
	 */
	public static void continueDrawingOffscreen(GL2ES3 gl, float[] offscreenMatrix, int[] fboHandle, int[] textureHandle, int width, int height) {
		
		// save the on-screen viewport and scissor settings
		gl.glGetIntegerv(GL3.GL_VIEWPORT, onscreenViewport, 0);
		gl.glGetIntegerv(GL3.GL_SCISSOR_BOX, onscreenScissor, 0);

		// switch to the off-screen framebuffer and corresponding texture
		gl.glBindFramebuffer(GL3.GL_FRAMEBUFFER, fboHandle[0]);
		if(SettingsController.getAntialiasingLevel() > 1)
			gl.glBindTexture(GL3.GL_TEXTURE_2D_MULTISAMPLE, textureHandle[0]);
		else
			gl.glBindTexture(GL3.GL_TEXTURE_2D, textureHandle[0]);

		// set the viewport and disable the scissor test
		gl.glViewport(0, 0, width, height);
		gl.glDisable(GL3.GL_SCISSOR_TEST);
		
		// set the matrix
		useMatrix(gl, offscreenMatrix);
		
		// set the blend function
		gl.glBlendFuncSeparate(GL3.GL_SRC_ALPHA, GL3.GL_ONE_MINUS_SRC_ALPHA, GL3.GL_ONE, GL3.GL_ONE_MINUS_SRC_ALPHA);
		
	}
	
	/**
	 * Switches back to the on-screen framebuffer, applies the on-screen matrix, and re-enables the scissor test.
	 * 
	 * @param gl                The OpenGL context.
	 * @param onscreenMatrix    The 4x4 matrix to use.
	 */
	public static void stopDrawingOffscreen(GL2ES3 gl, float[] onscreenMatrix) {
		
		// switch back to the screen framebuffer
		gl.glBindFramebuffer(GL3.GL_FRAMEBUFFER, 0);
		
		// restore the on-screen viewport and scissor settings
		gl.glViewport(onscreenViewport[0], onscreenViewport[1], onscreenViewport[2], onscreenViewport[3]);
		gl.glScissor(onscreenScissor[0], onscreenScissor[1], onscreenScissor[2], onscreenScissor[3]);
		
		// enable the scissor test
		gl.glEnable(GL3.GL_SCISSOR_TEST);
		
		// set the matrix
		useMatrix(gl, onscreenMatrix);
		
	}
	
	/**
	 * Helper function that draws a texture onto an axis-aligned quad.
	 * 
	 * @param gl               The OpenGL context.
	 * @param textureHandle    Handle to the texture.
	 * @param isFboTexture     True if the texture is an off-screen frame buffer, false if it's just a normal texture.
	 * @param lowerLeftX       Lower-left x location.
	 * @param lowerLeftY       Lower-left y location.
	 * @param width            Width of the quad.
	 * @param height           Height of the quad.
	 * @param offset           Used to stretch the texture so its pixels are half-way through the left and right edge of the quad.
	 *                         (This is used by the DFT chart so the first and last bins get centered on the left and right edges.)
	 * @param rotateTexture    If true, rotate the texture 90 degrees clockwise, and don't use any offset.
	 */
	public static void drawTexturedBox(GL2ES3 gl, int[] textureHandle, boolean isFboTexture, float lowerLeftX, float lowerLeftY, float width, float height, float offset, boolean rotateTexture) {
		
		if(rotateTexture) {
			buffer.rewind();
			buffer.put(lowerLeftX);         buffer.put(lowerLeftY + height); // x,y
			buffer.put(1);                  buffer.put(1);                   // u,v
			buffer.put(lowerLeftX);         buffer.put(lowerLeftY);
			buffer.put(0);                  buffer.put(1);
			buffer.put(lowerLeftX + width); buffer.put(lowerLeftY + height);
			buffer.put(1);                  buffer.put(0);
			buffer.put(lowerLeftX + width); buffer.put(lowerLeftY);
			buffer.put(0);                  buffer.put(0);
			buffer.rewind();
		} else {
			buffer.rewind();
			buffer.put(lowerLeftX);         buffer.put(lowerLeftY + height); // x,y
			buffer.put(0 + offset);         buffer.put(1);                   // u,v
			buffer.put(lowerLeftX);         buffer.put(lowerLeftY);
			buffer.put(0 + offset);         buffer.put(0);
			buffer.put(lowerLeftX + width); buffer.put(lowerLeftY + height);
			buffer.put(1 - offset);         buffer.put(1);
			buffer.put(lowerLeftX + width); buffer.put(lowerLeftY);
			buffer.put(1 - offset);         buffer.put(0);
			buffer.rewind();
		}
		drawTriangleStripTextured2D(gl, buffer, textureHandle[0], isFboTexture, 4);
		
	}
	
	/**
	 * Helper function that draws a ring-buffer texture onto an axis-aligned quad.
	 * 
	 * @param gl               The OpenGL context.
	 * @param textureHandle    Handle to the texture.
	 * @param lowerLeftX       Lower-left x location.
	 * @param lowerLeftY       Lower-left y location.
	 * @param width            Width of the quad.
	 * @param height           Height of the quad.
	 * @param startX           The texture's x value to render at the left edge of the quad.
	 */
	public static void drawRingbufferTexturedBox(GL2ES3 gl, int[] textureHandle, float lowerLeftX, float lowerLeftY, float width, float height, float startX) {

		buffer.rewind();
		buffer.put(lowerLeftX);         buffer.put(lowerLeftY + height); // x,y
		buffer.put(0 + startX);         buffer.put(1);                   // u,v
		buffer.put(lowerLeftX);         buffer.put(lowerLeftY);
		buffer.put(0 + startX);         buffer.put(0);
		buffer.put(lowerLeftX + width); buffer.put(lowerLeftY + height);
		buffer.put(1 + startX);         buffer.put(1);
		buffer.put(lowerLeftX + width); buffer.put(lowerLeftY);
		buffer.put(1 + startX);         buffer.put(0);
		buffer.rewind();
		drawTriangleStripTextured2D(gl, buffer, textureHandle[0], true, 4);
		
	}
	
	/**
	 * Fills a float[16] with an orthographic projection matrix.
	 * 
	 * @param matrix    The float[16] to fill.
	 * @param minX      Minimum x value.
	 * @param maxX      Maximum x value.
	 * @param minY      Minimum y value.
	 * @param maxY      Maximum y value.
	 * @param minZ      Minimum z value.
	 * @param maxZ      Maximum z value.
	 */
	public static void makeOrthoMatrix(float[] matrix, float minX, float maxX, float minY, float maxY, float minZ, float maxZ) {
		
		// column 0
		matrix[0] = 2f / (maxX - minX);
		matrix[1] = 0f;
		matrix[2] = 0f;
		matrix[3] = 0f;
		
		// column 1
		matrix[4] = 0f;
		matrix[5] = 2f / (maxY - minY);
		matrix[6] = 0f;
		matrix[7] = 0f;
		
		// column 2
		matrix[8] = 0f;
		matrix[9] = 0f;
		matrix[10] = -2f / (maxZ - minZ);
		matrix[11] = 0f;
		
		// column 3
		matrix[12] = -1f * (maxX + minX) / (maxX - minX);
		matrix[13] = -1f * (maxY + minY) / (maxY - minY);
		matrix[14] = -1f * (maxZ + minZ) / (maxZ - minZ);
		matrix[15] = 1f;
		
	}
	
	/**
	 * Translates an existing matrix.
	 * 
	 * @param matrix    The float[16] matrix.
	 * @param x         Amount to translate along the x-axis.
	 * @param y         Amount to translate along the y-axis.
	 * @param z         Amount to translate along the z-axis.
	 */
	public static void translateMatrix(float[] matrix, float x, float y, float z) {
		
		// column 3
		matrix[12] += (matrix[0] * x) + (matrix[4] * y) + (matrix[8] * z);
		matrix[13] += (matrix[1] * x) + (matrix[5] * y) + (matrix[9] * z);
		matrix[14] += (matrix[2] * x) + (matrix[6] * y) + (matrix[10] * z);
		matrix[15] += (matrix[3] * x) + (matrix[7] * y) + (matrix[11] * z);
		
	}
	
	/**
	 * Scales an existing matrix.
	 * 
	 * @param matrix    The float[16] matrix.
	 * @param x         Amount to scale along the x-axis.
	 * @param y         Amount to scale along the y-axis.
	 * @param z         Amount to scale along the z-axis.
	 */
	public static void scaleMatrix(float[] matrix, float x, float y, float z) {
		
		// column 0
		matrix[0] *= x;
		matrix[1] *= x;
		matrix[2] *= x;
		matrix[3] *= x;
		
		// column 1
		matrix[4] *= y;
		matrix[5] *= y;
		matrix[6] *= y;
		matrix[7] *= y;
		
		// column 2
		matrix[8]  *= z;
		matrix[9]  *= z;
		matrix[10] *= z;
		matrix[11] *= z;
		
	}
	
	/**
	 * Rotates an existing matrix.
	 * 
	 * @param matrix     The float[16] matrix.
	 * @param degrees    Rotation amount, in degrees.
	 * @param x          Specifies the axis of rotation, must be a unit vector.
	 * @param y          Specifies the axis of rotation, must be a unit vector.
	 * @param z          Specifies the axis of rotation, must be a unit vector.
	 */
	public static void rotateMatrix(float[] matrix, float degrees, float x, float y, float z) {
		
		// assuming (x,y,z) is a unit vector
		
		float c = (float) Math.cos(degrees * Math.PI / 180.0);
		float s = (float) Math.sin(degrees * Math.PI / 180.0);
		float omc = 1 - c;
		
		float[] rotate   = new float[] {x * x * omc + c,       y * x * omc + z * s,   x * z * omc - y * s,   0,
		                                x * y * omc - z * s,   y * y * omc + c,       y * z * omc + x * s,   0,
		                                x * z * omc + y * s,   y * z * omc - x * s,   z * z * omc + c,       0,
		                                0,                     0,                     0,                     1};
		
		multiplyMatrix(matrix, rotate);
		
	}
	
	/**
	 * Replaces the "left" matrix with left * right.
	 * 
	 * @param left     The "left" float[16] matrix.
	 * @param right    The "right" float[16] matrix.
	 */
	public static void multiplyMatrix(float[] left, float[] right) {
		
		float[] original = Arrays.copyOf(left, 16);
		
		// column 0
		left[0]  = (original[0]  * right[0]) +
		           (original[4]  * right[1]) +
		           (original[8]  * right[2]) +
		           (original[12] * right[3]);
		
		left[1]  = (original[1]  * right[0]) +
		           (original[5]  * right[1]) +
		           (original[9]  * right[2]) +
		           (original[13] * right[3]);
		
		left[2]  = (original[2]  * right[0]) +
		           (original[6]  * right[1]) +
		           (original[10] * right[2]) +
		           (original[14] * right[3]);
		
		left[3]  = (original[3]  * right[0]) +
		           (original[7]  * right[1]) +
		           (original[11] * right[2]) +
		           (original[15] * right[3]);
		// column 1
		left[4]  = (original[0]  * right[4]) +
		           (original[4]  * right[5]) +
		           (original[8]  * right[6]) +
		           (original[12] * right[7]);
		
		left[5]  = (original[1]  * right[4]) +
		           (original[5]  * right[5]) +
		           (original[9]  * right[6]) +
		           (original[13] * right[7]);
		
		left[6]  = (original[2]  * right[4]) +
		           (original[6]  * right[5]) +
		           (original[10] * right[6]) +
		           (original[14] * right[7]);
		
		left[7]  = (original[3]  * right[4]) +
		           (original[7]  * right[5]) +
		           (original[11] * right[6]) +
		           (original[15] * right[7]);
		
		// column 2
		left[8]  = (original[0]  * right[8]) +
		           (original[4]  * right[9]) +
		           (original[8]  * right[10]) +
		           (original[12] * right[11]);
	
		left[9]  = (original[1]  * right[8]) +
		           (original[5]  * right[9]) +
		           (original[9]  * right[10]) +
		           (original[13] * right[11]);
		
		left[10] = (original[2]  * right[8]) +
		           (original[6]  * right[9]) +
		           (original[10] * right[10]) +
		           (original[14] * right[11]);
		
		left[11] = (original[3]  * right[8]) +
		           (original[7]  * right[9]) +
		           (original[11] * right[10]) +
		           (original[15] * right[11]);
		
		// column 3
		left[12] = (original[0]  * right[12]) +
		           (original[4]  * right[13]) +
		           (original[8]  * right[14]) +
		           (original[12] * right[15]);
	
		left[13] = (original[1]  * right[12]) +
		           (original[5]  * right[13]) +
		           (original[9]  * right[14]) +
		           (original[13] * right[15]);
		
		left[14] = (original[2]  * right[12]) +
		           (original[6]  * right[13]) +
		           (original[10] * right[14]) +
		           (original[14] * right[15]);
		
		left[15] = (original[3]  * right[12]) +
		           (original[7]  * right[13]) +
		           (original[11] * right[14]) +
		           (original[15] * right[15]);
		
	}
	
	static float[] currentMatrix = null;
	
	/**
	 * Saves a matrix to be used by subsequent draw calls.
	 * 
	 * @param gl        The OpenGL context.
	 * @param matrix    The float[16] matrix.
	 */
	public static void useMatrix(GL2ES3 gl, float[] matrix) {
		
		currentMatrix = matrix;
		
	}
	
	private static class ThinLinesY {
		static int programHandle;
		static int colorHandle;
		static int xOffsetHandle;
		static int matrixHandle;
		static int vaoHandle;
		static int vboHandle;
	}
	
	private static class ThickLinesY {
		static int programHandle;
		static int colorHandle;
		static int xOffsetHandle;
		static int matrixHandle;
		static int lineWidthHandle;
		static int widthPixelsHandle;
		static int heightPixelsHandle;
		static int vaoHandle;
		static int vboHandle;
	}
	
	private static class ThinLinesXY {
		static int programHandle;
		static int colorHandle;
		static int matrixHandle;
		static int vaoHandle;
		static int vboHandle;
	}
	
	private static class ThickLinesXY {
		static int programHandle;
		static int colorHandle;
		static int matrixHandle;
		static int lineWidthHandle;
		static int widthPixelsHandle;
		static int heightPixelsHandle;
		static int vaoHandle;
		static int vboHandle;
	}
	
	private static class ThinLinesXYRGBA {
		static int programHandle;
		static int matrixHandle;
		static int vaoHandle;
		static int vboHandle;
	}
	
	private static class ThickLinesXYRGBA {
		static int programHandle;
		static int matrixHandle;
		static int lineWidthHandle;
		static int widthPixelsHandle;
		static int heightPixelsHandle;
		static int vaoHandle;
		static int vboHandle;
	}
	
	private static class ThinLinesX_Y {
		static int programHandle;
		static int colorHandle;
		static int matrixHandle;
		static int vaoHandle;
		static int vboXhandle;
		static int vboYhandle;
	}
	
	private static class ThickLinesX_Y {
		static int programHandle;
		static int colorHandle;
		static int matrixHandle;
		static int lineWidthHandle;
		static int widthPixelsHandle;
		static int heightPixelsHandle;
		static int vaoHandle;
		static int vboXhandle;
		static int vboYhandle;
	}
	
	private static class PointsY {
		static int programHandle;
		static int colorHandle;
		static int xOffsetHandle;
		static int matrixHandle;
		static int pointWidthHandle;
		static int widthPixelsHandle;
		static int heightPixelsHandle;
		static int vaoHandle;
		static int vboHandle;
	}
	
	private static class PointsXY {
		static int programHandle;
		static int colorHandle;
		static int matrixHandle;
		static int pointWidthHandle;
		static int widthPixelsHandle;
		static int heightPixelsHandle;
		static int vaoHandle;
		static int vboHandle;
	}
	
	private static class PointsX_Y {
		static int programHandle;
		static int colorHandle;
		static int matrixHandle;
		static int pointWidthHandle;
		static int widthPixelsHandle;
		static int heightPixelsHandle;
		static int vaoHandle;
		static int vboXhandle;
		static int vboYhandle;
	}
	
	private static class TrianglesXY {
		static int programHandle;
		static int colorHandle;
		static int matrixHandle;
		static int vaoHandle;
		static int vboHandle;
	}
	
	private static class TrianglesXYZUVW {
		static int programHandle;
		static int matrixHandle;
		static int vaoHandle;
		static int vboHandle;
	}
	
	private static class TrianglesXYST {
		static int programHandle;
		static int matrixHandle;
		static int vaoHandle;
		static int vboHandle;
	}
	
	private static class TrianglesXYSTmultisample {
		static int programHandle;
		static int matrixHandle;
		static int vaoHandle;
		static int vboHandle;
	}
	
	private static class FontRenderer {
		static int programHandle;
		static int matrixHandle;
		static int lineHeightHandle;
		static int[] smallFontTextureHandle = new int[1];
		static int[] mediumFontTextureHandle = new int[1];
		static int[] largeFontTextureHandle = new int[1];
		static int vaoHandle;
		static int vboHandle;
	}
	private static int[] smallFontAsciiCharWidthLUT;
	private static int   smallFontMaxCharWidth;
	private static int   smallFontMaxCharHeight;
	private static int   smallFontCharsPerRow;
	private static int   smallFontBaselineOffset;
	
	private static int[] mediumFontAsciiCharWidthLUT;
	private static int   mediumFontMaxCharWidth;
	private static int   mediumFontMaxCharHeight;
	private static int   mediumFontCharsPerRow;
	private static int   mediumFontBaselineOffset;
	
	private static int[] largeFontAsciiCharWidthLUT;
	private static int   largeFontMaxCharWidth;
	private static int   largeFontMaxCharHeight;
	private static int   largeFontCharsPerRow;
	private static int   largeFontBaselineOffset;
	
	// texture atlases contain the printable ASCII characters, plus any characters in this map
	// each map value is an int[] containing:
	// [0] =  smallFontAtlasX, [1] =  smallFontAtlasY, [2] =  smallFontAltasWidth
	// [3] = mediumFontAtlasX, [4] = mediumFontAtlasY, [5] = mediumFontAltasWidth
	// [6] =  largeFontAtlasX, [7] =  largeFontAtlasY, [8] =  largeFontAltasWidth
	private static Map<Character, int[]> nonAsciiCharMap = new HashMap<Character, int[]>();
	private static char firstAsciiChar = ' ';
	private static char lastAsciiChar  = '~';
	
	public static int smallTextHeight;
	public static int mediumTextHeight;
	public static int largeTextHeight;
	
	/**
	 * Compiles all GLSL programs, and fills the corresponding static objects with their handles.
	 * 
	 * There is a long list of programs because most of the computational work is shifted into the shaders whenever possible.
	 * Also, to allow drawing of very thick lines or points, variants are made that use geometry shaders to generate the required shapes.
	 * 
	 * @param gl    The OpenGL context.
	 */
	public static void makeAllPrograms(GL2ES3 gl) {
		
		String versionLine = gl.isGL3() ? "#version 150\n" : "#version 310 es\n";
		
		String[] vertexShaderVboY = new String[] {
			versionLine,
			"#ifdef GL_ES\n" +
			"precision mediump float;\n" +
			"precision mediump int;\n" +
			"#endif\n" +
			"in float y;\n",
			"uniform mat4 matrix;\n",
			"uniform int xOffset;\n",
			"void main(void) {\n",
			"	gl_Position = matrix * vec4(gl_VertexID + xOffset, y, 0, 1);\n",
			"}\n"
		};
		String[] vertexShaderVboXvboY = new String[] {
			versionLine,
			"#ifdef GL_ES\n" +
			"precision mediump float;\n" + 
			"precision mediump int;\n" +
			"#endif\n" +
			"in float x;\n",
			"in float y;\n",
			"uniform mat4 matrix;\n",
			"void main(void) {\n",
			"	gl_Position = matrix * vec4(x, y, 0, 1);\n",
			"}\n"
		};
		String[] vertexShaderVboXy = new String[] {
			versionLine,
			"#ifdef GL_ES\n" +
			"precision mediump float;\n" + 
			"precision mediump int;\n" +
			"#endif\n" +
			"in vec2 xy;\n",
			"uniform mat4 matrix;\n",
			"void main(void) {\n",
			"	gl_Position = matrix * vec4(xy, 0, 1);\n",
			"}\n"
		};
		String[] vertexShaderVboXyst = new String[] {
			versionLine,
			"#ifdef GL_ES\n" +
			"precision mediump float;\n" + 
			"precision mediump int;\n" +
			"#endif\n" +
			"in vec2 xy;\n",
			"in vec2 st;\n",
			"out vec2 texCoord;\n",
			"uniform mat4 matrix;\n",
			"void main(void) {\n",
			"	gl_Position = matrix * vec4(xy, 0, 1);\n",
			"	texCoord = st;\n",
			"}\n"
		};
		String[] vertexShaderVboXyrgba = new String[] {
			versionLine,
			"#ifdef GL_ES\n" +
			"precision mediump float;\n" + 
			"precision mediump int;\n" +
			"#endif\n" +
			"in vec2 xy;\n",
			"in vec4 rgba;\n",
			"out vec4 color;\n",
			"uniform mat4 matrix;\n",
			"void main(void) {\n",
			"	gl_Position = matrix * vec4(xy, 0, 1);\n",
			"	color = rgba;\n",
			"}\n"
		};
		String[] vertexShaderVboXyzuvw = new String[] {
			versionLine,
			"#ifdef GL_ES\n" +
			"precision mediump float;\n" + 
			"precision mediump int;\n" +
			"#endif\n" +
			"in vec3 xyz;\n",
			"in vec3 uvw;\n",
			"out vec3 normal;\n",
			"uniform mat4 matrix;\n",
			"void main(void) {\n",
			"	gl_Position = matrix * vec4(xyz, 1);\n",
			"	normal = uvw;\n",
			"}\n"
		};
		String[] vertexShaderFontRenderer = new String[] {
			versionLine,
			"#ifdef GL_ES\n" +
			"precision mediump float;\n" + 
			"precision mediump int;\n" +
			"#endif\n" +
			"in vec2 xy;\n", // location of lower-left corner of a character, in pixels
			"in vec3 stw;\n", // font texture atlas (s,t) and width, in pixels
			"out vec3 atlas;\n",
			"void main(void) {\n",
			"	gl_Position = vec4(xy, 0, 1);\n",
			"	atlas = stw;\n",
			"}\n"
		};
		String[] geometryShaderThickLines = new String[] {
			versionLine,
			"#ifdef GL_ES\n" +
			"#extension GL_EXT_geometry_shader : require\n" +
			"precision mediump float;\n" + 
			"precision mediump int;\n" +
			"#endif\n" +
			"layout (lines) in;\n",
			"layout (triangle_strip, max_vertices = 4) out;\n",
			"uniform float lineWidth;\n",
			"uniform float widthPixels;\n",
			"uniform float heightPixels;\n",
			"void main(void) {\n",
			"	float deltaX = (gl_in[1].gl_Position.x - gl_in[0].gl_Position.x) * widthPixels;\n",
			"	float deltaY = (gl_in[1].gl_Position.y - gl_in[0].gl_Position.y) * heightPixels;\n",
			"	float n = lineWidth / sqrt(deltaX*deltaX + deltaY*deltaY);\n",
			"	float shiftX = n * deltaY / widthPixels;\n",
			"	float shiftY = n * deltaX / heightPixels;\n",
			"	gl_Position = vec4(gl_in[0].gl_Position.x + shiftX, gl_in[0].gl_Position.y - shiftY, 0, 1);    EmitVertex();\n",
			"	gl_Position = vec4(gl_in[0].gl_Position.x - shiftX, gl_in[0].gl_Position.y + shiftY, 0, 1);    EmitVertex();\n",
			"	gl_Position = vec4(gl_in[1].gl_Position.x + shiftX, gl_in[1].gl_Position.y - shiftY, 0, 1);    EmitVertex();\n",
			"	gl_Position = vec4(gl_in[1].gl_Position.x - shiftX, gl_in[1].gl_Position.y + shiftY, 0, 1);    EmitVertex();\n",
			"	EndPrimitive();\n",
			"}\n"
		};
		String[] geometryShaderThickColoredLines = new String[] {
			versionLine,
			"#ifdef GL_ES\n" +
			"#extension GL_EXT_geometry_shader : require\n" +
			"precision mediump float;\n" + 
			"precision mediump int;\n" +
			"#endif\n" +
			"layout (lines) in;\n",
			"layout (triangle_strip, max_vertices = 4) out;\n",
			"uniform float lineWidth;\n",
			"uniform float widthPixels;\n",
			"uniform float heightPixels;\n",
			"in vec4 color[2];\n",
			"out vec4 rgba;\n",
			"void main(void) {\n",
			"	float deltaX = gl_in[1].gl_Position.x - gl_in[0].gl_Position.x;\n",
			"	float deltaY = gl_in[1].gl_Position.y - gl_in[0].gl_Position.y;\n",
			"	float n = lineWidth / sqrt(deltaX*deltaX + deltaY*deltaY);\n",
			"	float shiftX = n / widthPixels * deltaY;\n",
			"	float shiftY = n / heightPixels * deltaX;\n",
			"	gl_Position = vec4(gl_in[0].gl_Position.x + shiftX, gl_in[0].gl_Position.y - shiftY, 0, 1);    rgba = color[0];    EmitVertex();\n",
			"	gl_Position = vec4(gl_in[0].gl_Position.x - shiftX, gl_in[0].gl_Position.y + shiftY, 0, 1);    rgba = color[0];    EmitVertex();\n",
			"	gl_Position = vec4(gl_in[1].gl_Position.x + shiftX, gl_in[1].gl_Position.y - shiftY, 0, 1);    rgba = color[1];    EmitVertex();\n",
			"	gl_Position = vec4(gl_in[1].gl_Position.x - shiftX, gl_in[1].gl_Position.y + shiftY, 0, 1);    rgba = color[1];    EmitVertex();\n",
			"	EndPrimitive();\n",
			"}\n"
		};
		String[] geometryShaderThickPoints = new String[] {
			versionLine,
			"#ifdef GL_ES\n" +
			"#extension GL_EXT_geometry_shader : require\n" +
			"precision mediump float;\n" + 
			"precision mediump int;\n" +
			"#endif\n" +
			"layout (points) in;\n",
			"layout (triangle_strip, max_vertices = 4) out;\n",
			"uniform float pointWidth;\n",
			"uniform float widthPixels;\n",
			"uniform float heightPixels;\n",
			"flat out vec4 center;\n",
			"void main(void) {\n",
			"	float shiftX = pointWidth / widthPixels;\n",
			"	float shiftY = pointWidth / heightPixels;\n",
			"	center = vec4((gl_in[0].gl_Position.x + 1.0) / 2.0 * widthPixels, (gl_in[0].gl_Position.y + 1.0) / 2.0 * heightPixels, 0, 1);\n",
			"	gl_Position = vec4(gl_in[0].gl_Position.x - shiftX, gl_in[0].gl_Position.y + shiftY, 0, 1);    EmitVertex();\n",
			"	gl_Position = vec4(gl_in[0].gl_Position.x - shiftX, gl_in[0].gl_Position.y - shiftY, 0, 1);    EmitVertex();\n",
			"	gl_Position = vec4(gl_in[0].gl_Position.x + shiftX, gl_in[0].gl_Position.y + shiftY, 0, 1);    EmitVertex();\n",
			"	gl_Position = vec4(gl_in[0].gl_Position.x + shiftX, gl_in[0].gl_Position.y - shiftY, 0, 1);    EmitVertex();\n",
			"	EndPrimitive();\n",
			"}\n"
		};
		String[] geometryShaderFontRenderer = new String[] {
			versionLine,
			"#ifdef GL_ES\n" +
			"#extension GL_EXT_geometry_shader : require\n" +
			"precision mediump float;\n" +
			"precision mediump int;\n" +
			"#endif\n" +
			"layout (points) in;\n",
			"layout (triangle_strip, max_vertices = 4) out;\n",
			"in vec3 atlas[1];\n",
			"out vec2 texCoord;\n",
			"uniform mat4 matrix;\n",
			"uniform float lineHeight;\n",
			"void main(void) {\n",
			"	gl_Position = matrix * vec4(gl_in[0].gl_Position.x + atlas[0].z, gl_in[0].gl_Position.y,              0, 1);    texCoord = vec2(atlas[0].x+atlas[0].z, atlas[0].y + lineHeight);    EmitVertex();\n",
			"	gl_Position = matrix * vec4(gl_in[0].gl_Position.x + atlas[0].z, gl_in[0].gl_Position.y + lineHeight, 0, 1);    texCoord = vec2(atlas[0].x+atlas[0].z, atlas[0].y);                 EmitVertex();\n",
			"	gl_Position = matrix * vec4(gl_in[0].gl_Position.x,              gl_in[0].gl_Position.y,              0, 1);    texCoord = vec2(atlas[0].x, atlas[0].y + lineHeight);               EmitVertex();\n",
			"	gl_Position = matrix * vec4(gl_in[0].gl_Position.x,              gl_in[0].gl_Position.y + lineHeight, 0, 1);    texCoord = vec2(atlas[0].x, atlas[0].y);                            EmitVertex();\n",
			"	EndPrimitive();\n",
			"}\n"
		};
		String[] fragmentShaderUniformColor = new String[] {
			versionLine,
			"#ifdef GL_ES\n" +
			"precision mediump float;\n" + 
			"precision mediump int;\n" +
			"#endif\n" +
			"uniform vec4 rgba;\n",
			"out vec4 fragColor;\n",
			"void main(void) {\n",
			"	fragColor = rgba;\n",
			"}\n"
		};
		String[] fragmentShaderVaryingColor = new String[] {
			versionLine,
			"#ifdef GL_ES\n" +
			"precision mediump float;\n" + 
			"precision mediump int;\n" +
			"#endif\n" +
			"in vec4 color;\n",
			"out vec4 fragColor;\n",
			"void main(void) {\n",
			"	fragColor = color;\n",
			"}\n"
		};
		String[] fragmentShaderVaryingColorFromGeom = new String[] {
			versionLine,
			"#ifdef GL_ES\n" +
			"precision mediump float;\n" + 
			"precision mediump int;\n" +
			"#endif\n" +
			"in vec4 rgba;\n",
			"out vec4 fragColor;\n",
			"void main(void) {\n",
			"	fragColor = rgba;\n",
			"}\n"
		};
		String[] fragmentShaderUniformColorPoints = new String[] {
			versionLine,
			"#ifdef GL_ES\n" +
			"precision mediump float;\n" + 
			"precision mediump int;\n" +
			"#endif\n" +
			"uniform vec4 rgba;\n",
			"uniform float pointWidth;\n",
			"flat in vec4 center;\n",
			"out vec4 fragColor;\n",
			"void main(void) {\n",
			"	float d = distance(center, gl_FragCoord) - (pointWidth / 2.0);\n",
			"	d = clamp(d, 0.0, 1.0);\n",
			"	fragColor = vec4(rgba.rgb, 1.0 - d);\n",
			"}\n"
		};
		String[] fragmentShaderTex2D = new String[] {
			versionLine,
			"#ifdef GL_ES\n" +
			"precision mediump float;\n" + 
			"precision mediump int;\n" + 
			"precision mediump sampler2D;\n" +
			"#endif\n" +
			"in vec2 texCoord;\n",
			"uniform sampler2D tex;\n",
			"out vec4 fragColor;\n",
			"void main(void) {\n",
			"	fragColor = texture(tex, texCoord);\n",
			"}\n"
		};
		int msaaLevel = SettingsController.getAntialiasingLevel();
		String[] fragmentShaderTex2DMS = new String[] {
			versionLine,
			"#ifdef GL_ES\n" +
			"#extension ARB_texture_multisample : require\n" +
			"precision mediump float;\n" + 
			"precision mediump int;\n" +
			"precision mediump sampler2DMS;\n" +
			"#endif\n" +
			"in vec2 texCoord;\n",
			"uniform sampler2DMS tex;\n",
			"out vec4 fragColor;\n",
			"void main(void) {\n",
			"	vec2 texSize = vec2(textureSize(tex));",
			"	ivec2 texel = ivec2(texSize * texCoord);\n",
			"	texel.x %= textureSize(tex).x;\n",
			"	vec4 temp = vec4(0, 0, 0, 0);\n",
			"	for(int i = 0; i < " + msaaLevel  +"; i++)\n",
			"		temp += texelFetch(tex, texel, i);\n",
			"	temp /= " + msaaLevel + ".0;\n",
			"	fragColor = temp;\n",
			"}\n"
		};
		String[] fragmentShaderLights3D = new String[] {
			versionLine,
			"#ifdef GL_ES\n" +
			"precision mediump float;\n" + 
			"precision mediump int;\n" +
			"#endif\n" +
			"in vec3 normal;\n",
			"uniform mat4 matrix;\n",
			"out vec4 fragColor;\n",
			"void main(void) {\n",
			"	vec3 nor = normalize(normal);\n",
			"	vec3 redDirection   = vec3(-1, 0, 0);\n", // red light at the left edge
			"	vec3 greenDirection = vec3( 0, 0, 1);\n", // green light at the top edge
			"	vec3 blueDirection  = vec3( 1, 0, 0);\n", // blue light at the right edge
			"	float redDiffuse   = 0.8 * max(dot(nor, redDirection),   0.0);\n",
			"	float greenDiffuse = 0.8 * max(dot(nor, greenDirection), 0.0);\n",
			"	float blueDiffuse  = 0.8 * max(dot(nor, blueDirection),  0.0);\n",
			"	fragColor = vec4(redDiffuse, greenDiffuse, blueDiffuse, 1);\n",
			"}\n"
		};
		String[] fragmentShaderFontRenderer = new String[] {
			versionLine,
			"#ifdef GL_ES\n" +
			"precision mediump float;\n" + 
			"precision mediump int;\n" + 
			"precision mediump sampler2D;\n" +
			"#endif\n" +
			"in vec2 texCoord;\n",
			"uniform sampler2D tex;\n",
			"out vec4 fragColor;\n",
			"void main(void) {\n",
			"	fragColor = texelFetch(tex, ivec2(texCoord), 0).abgr;\n", // swizzling ABGR texture into RGBA
			"}\n"
		};
		
		int[] handle = new int[1];
		int index = 0;
		
		/*
		 * "ThinLinesY" is for rendering line charts when the line thickness is 1px and when x coordinates are auto-generated.
		 * 
		 * One VBO of floats specifies (y1,y2,...) data.
		 * X-coordinates are automatically generated.
		 * One uniform mat4 specifies the matrix.
		 * One uniform vec4 specifies the color.
		 * One uniform int  specifies the x-offset if the lines don't start at the left edge of the plot.
		 */
		ThinLinesY.programHandle = makeProgram(gl, vertexShaderVboY, null, fragmentShaderUniformColor);
		
		// VAO
		gl.glGenVertexArrays(1, handle, 0);
		gl.glBindVertexArray(handle[0]);
		ThinLinesY.vaoHandle = handle[0];
		
		// VBO
		gl.glGenBuffers(1, handle, 0);
		gl.glBindBuffer(GL3.GL_ARRAY_BUFFER, handle[0]);
		ThinLinesY.vboHandle = handle[0];
		
		// use the VBO for (y1,y2,...) data
		index = gl.glGetAttribLocation(ThinLinesY.programHandle, "y");
		gl.glVertexAttribPointer(index, 1, GL3.GL_FLOAT, false, 0, 0);
		gl.glEnableVertexAttribArray(index);
		
		// get handles for the uniforms
		ThinLinesY.matrixHandle  = gl.glGetUniformLocation(ThinLinesY.programHandle, "matrix");
		ThinLinesY.colorHandle   = gl.glGetUniformLocation(ThinLinesY.programHandle, "rgba");
		ThinLinesY.xOffsetHandle = gl.glGetUniformLocation(ThinLinesY.programHandle, "xOffset");
		
		/*
		 * "ThickLinesY" is for rendering line charts when the line thickness !=1px and when the x coordinates are auto-generated.
		 * 
		 * One VBO of floats specifies (y1,y2,...) data.
		 * X-coordinates are automatically generated.
		 * One uniform mat4  specifies the matrix.
		 * One uniform vec4  specifies the color.
		 * One uniform int   specifies the x-offset if the lines don't start at the left edge of the plot.
		 * One uniform float specifies the line thickness as pixels.
		 * Two uniform floats specify the framebuffer size so the geometry shader can convert from NDCs into pixels.
		 */
		ThickLinesY.programHandle = makeProgram(gl, vertexShaderVboY, geometryShaderThickLines, fragmentShaderUniformColor);
		
		// VAO
		gl.glGenVertexArrays(1, handle, 0);
		gl.glBindVertexArray(handle[0]);
		ThickLinesY.vaoHandle = handle[0];
		
		// VBO
		gl.glGenBuffers(1, handle, 0);
		gl.glBindBuffer(GL3.GL_ARRAY_BUFFER, handle[0]);
		ThickLinesY.vboHandle = handle[0];
		
		// use the VBO for (y1,y2,...) data
		index = gl.glGetAttribLocation(ThickLinesY.programHandle, "y");
		gl.glVertexAttribPointer(index, 1, GL3.GL_FLOAT, false, 0, 0);
		gl.glEnableVertexAttribArray(index);
		
		// get handles for the uniforms
		ThickLinesY.matrixHandle       = gl.glGetUniformLocation(ThickLinesY.programHandle, "matrix");
		ThickLinesY.colorHandle        = gl.glGetUniformLocation(ThickLinesY.programHandle, "rgba");
		ThickLinesY.xOffsetHandle      = gl.glGetUniformLocation(ThickLinesY.programHandle, "xOffset");
		ThickLinesY.lineWidthHandle    = gl.glGetUniformLocation(ThickLinesY.programHandle, "lineWidth");
		ThickLinesY.widthPixelsHandle  = gl.glGetUniformLocation(ThickLinesY.programHandle, "widthPixels");
		ThickLinesY.heightPixelsHandle = gl.glGetUniformLocation(ThickLinesY.programHandle, "heightPixels");
		
		/*
		 * "ThinLinesXY" is for rendering lines when the line thickness is 1px.
		 * 
		 * One VBO of floats specifies (x1,y1,x2,y2,...) data.
		 * One uniform mat4 specifies the matrix.
		 * One uniform vec4 specifies the color.
		 */
		ThinLinesXY.programHandle = makeProgram(gl, vertexShaderVboXy, null, fragmentShaderUniformColor);
		
		// VAO
		gl.glGenVertexArrays(1, handle, 0);
		gl.glBindVertexArray(handle[0]);
		ThinLinesXY.vaoHandle = handle[0];
		
		// VBO
		gl.glGenBuffers(1, handle, 0);
		gl.glBindBuffer(GL3.GL_ARRAY_BUFFER, handle[0]);
		ThinLinesXY.vboHandle = handle[0];
		
		// use the VBO for (x,y) data
		index = gl.glGetAttribLocation(ThinLinesXY.programHandle, "xy");
		gl.glVertexAttribPointer(index, 2, GL3.GL_FLOAT, false, 0, 0);
		gl.glEnableVertexAttribArray(index);
		
		// get handles for the uniforms
		ThinLinesXY.matrixHandle = gl.glGetUniformLocation(ThinLinesXY.programHandle, "matrix");
		ThinLinesXY.colorHandle  = gl.glGetUniformLocation(ThinLinesXY.programHandle, "rgba");
		
		/*
		 * "ThickLinesXY" is for rendering lines when the line thickness !=1px.
		 * 
		 * One VBO of floats specifies (x1,y1,x2,y2,...) data.
		 * One uniform mat4  specifies the matrix.
		 * One uniform vec4  specifies the color.
		 * One uniform float specifies the line thickness as pixels.
		 * Two uniform floats specify the framebuffer size so the geometry shader can convert from NDCs into pixels.
		 */
		ThickLinesXY.programHandle = makeProgram(gl, vertexShaderVboXy, geometryShaderThickLines, fragmentShaderUniformColor);
		
		// VAO
		gl.glGenVertexArrays(1, handle, 0);
		gl.glBindVertexArray(handle[0]);
		ThickLinesXY.vaoHandle = handle[0];
		
		// VBO
		gl.glGenBuffers(1, handle, 0);
		gl.glBindBuffer(GL3.GL_ARRAY_BUFFER, handle[0]);
		ThickLinesXY.vboHandle = handle[0];
		
		// use the VBO for (x,y) data
		index = gl.glGetAttribLocation(ThickLinesXY.programHandle, "xy");
		gl.glVertexAttribPointer(index, 2, GL3.GL_FLOAT, false, 0, 0);
		gl.glEnableVertexAttribArray(index);
		
		// get handles for the uniforms
		ThickLinesXY.matrixHandle       = gl.glGetUniformLocation(ThickLinesXY.programHandle, "matrix");
		ThickLinesXY.colorHandle        = gl.glGetUniformLocation(ThickLinesXY.programHandle, "rgba");
		ThickLinesXY.lineWidthHandle    = gl.glGetUniformLocation(ThickLinesXY.programHandle, "lineWidth");
		ThickLinesXY.widthPixelsHandle  = gl.glGetUniformLocation(ThickLinesXY.programHandle, "widthPixels");
		ThickLinesXY.heightPixelsHandle = gl.glGetUniformLocation(ThickLinesXY.programHandle, "heightPixels");
		
		/*
		 * "ThinLinesXYRGBA" is for rendering lines with per-vertex colors when the line thickness is 1px.
		 * This can also be used for rendering triangles with per-vertex colors.
		 * One VBO of floats specifies (x1,y1,r1,g1,b1,a1,...) data.
		 * One uniform mat4 specifies the matrix.
		 */
		ThinLinesXYRGBA.programHandle = makeProgram(gl, vertexShaderVboXyrgba, null, fragmentShaderVaryingColor);
		
		// VAO
		gl.glGenVertexArrays(1, handle, 0);
		gl.glBindVertexArray(handle[0]);
		ThinLinesXYRGBA.vaoHandle = handle[0];
		
		// VBO
		gl.glGenBuffers(1, handle, 0);
		gl.glBindBuffer(GL3.GL_ARRAY_BUFFER, handle[0]);
		ThinLinesXYRGBA.vboHandle = handle[0];
		
		// use the VBO for (x,y,r,g,b,a) data
		index = gl.glGetAttribLocation(ThinLinesXYRGBA.programHandle, "xy");
		gl.glVertexAttribPointer(index, 2, GL3.GL_FLOAT, false, 6*4, 0);
		gl.glEnableVertexAttribArray(index);
		index = gl.glGetAttribLocation(ThinLinesXYRGBA.programHandle, "rgba");
		gl.glVertexAttribPointer(index, 4, GL3.GL_FLOAT, false, 6*4, 2*4);
		gl.glEnableVertexAttribArray(index);
		
		// get handle for the uniform
		ThinLinesXYRGBA.matrixHandle = gl.glGetUniformLocation(ThinLinesXYRGBA.programHandle, "matrix");
		
		/*
		 * "ThickLinesXYRGBA" is for rendering lines with per-vertex colors when the line thickness !=1px.
		 * One VBO of floats  specifies (x1,y1,r1,g1,b1,a1,...) data.
		 * One uniform mat4   specifies the matrix.
		 * One uniform float  specifies the line thickness as pixels.
		 * Two uniform floats specify the framebuffer size so the geometry shader can convert from NDCs into pixels.
		 */
		ThickLinesXYRGBA.programHandle = makeProgram(gl, vertexShaderVboXyrgba, geometryShaderThickColoredLines, fragmentShaderVaryingColorFromGeom);
		
		// VAO
		gl.glGenVertexArrays(1, handle, 0);
		gl.glBindVertexArray(handle[0]);
		ThickLinesXYRGBA.vaoHandle = handle[0];
		
		// VBO
		gl.glGenBuffers(1, handle, 0);
		gl.glBindBuffer(GL3.GL_ARRAY_BUFFER, handle[0]);
		ThickLinesXYRGBA.vboHandle = handle[0];
		
		// use the VBO for (x,y,r,g,b,a) data
		index = gl.glGetAttribLocation(ThickLinesXYRGBA.programHandle, "xy");
		gl.glVertexAttribPointer(index, 2, GL3.GL_FLOAT, false, 6*4, 0);
		gl.glEnableVertexAttribArray(index);
		index = gl.glGetAttribLocation(ThickLinesXYRGBA.programHandle, "rgba");
		gl.glVertexAttribPointer(index, 4, GL3.GL_FLOAT, false, 6*4, 2*4);
		gl.glEnableVertexAttribArray(index);
		
		// get handles for the uniforms
		ThickLinesXYRGBA.matrixHandle       = gl.glGetUniformLocation(ThickLinesXYRGBA.programHandle, "matrix");
		ThickLinesXYRGBA.lineWidthHandle    = gl.glGetUniformLocation(ThickLinesXYRGBA.programHandle, "lineWidth");
		ThickLinesXYRGBA.widthPixelsHandle  = gl.glGetUniformLocation(ThickLinesXYRGBA.programHandle, "widthPixels");
		ThickLinesXYRGBA.heightPixelsHandle = gl.glGetUniformLocation(ThickLinesXYRGBA.programHandle, "heightPixels");
		
		/*
		 * "ThinLinesX_Y" is for rendering line charts when the line thickness is 1px and separate VBOs are use for X and Y values.
		 * 
		 * One VBO of floats specifies relative timestamps (x1,x2,...)
		 * One VBO of floats specifies values (y1,y2,...)
		 * One uniform mat4 specifies the matrix.
		 * One uniform vec4 specifies the color.
		 */
		ThinLinesX_Y.programHandle = makeProgram(gl, vertexShaderVboXvboY, null, fragmentShaderUniformColor);
		
		// VAO
		gl.glGenVertexArrays(1, handle, 0);
		gl.glBindVertexArray(handle[0]);
		ThinLinesX_Y.vaoHandle = handle[0];
		
		// first VBO
		gl.glGenBuffers(1, handle, 0);
		gl.glBindBuffer(GL3.GL_ARRAY_BUFFER, handle[0]);
		ThinLinesX_Y.vboXhandle = handle[0];
		
		// use the first VBO for x-axis values
		index = gl.glGetAttribLocation(ThinLinesX_Y.programHandle, "x");
		gl.glVertexAttribPointer(index, 1, GL3.GL_FLOAT, false, 0, 0);
		gl.glEnableVertexAttribArray(index);
		
		// second VBO
		gl.glGenBuffers(1, handle, 0);
		gl.glBindBuffer(GL3.GL_ARRAY_BUFFER, handle[0]);
		ThinLinesX_Y.vboYhandle = handle[0];
		
		// use the second VBO for y-axis values
		index = gl.glGetAttribLocation(ThinLinesX_Y.programHandle, "y");
		gl.glVertexAttribPointer(index, 1, GL3.GL_FLOAT, false, 0, 0);
		gl.glEnableVertexAttribArray(index);
		
		// get handles for the uniforms
		ThinLinesX_Y.matrixHandle = gl.glGetUniformLocation(ThinLinesX_Y.programHandle, "matrix");
		ThinLinesX_Y.colorHandle  = gl.glGetUniformLocation(ThinLinesX_Y.programHandle, "rgba");
		
		/*
		 * "ThickLinesX_Y" is for rendering timestamped line charts when the line thickness !=1px.
		 * 
		 * One VBO of floats specifies relative timestamps (x1,x2,...)
		 * One VBO of floats specifies values (y1,y2,...)
		 * One uniform mat4  specifies the matrix.
		 * One uniform vec4  specifies the color.
		 * One uniform float specifies the line thickness as pixels.
		 * Two uniform floats specify the framebuffer size so the geometry shader can convert from NDCs into pixels.
		 */
		ThickLinesX_Y.programHandle = makeProgram(gl, vertexShaderVboXvboY, geometryShaderThickLines, fragmentShaderUniformColor);
		
		// VAO
		gl.glGenVertexArrays(1, handle, 0);
		gl.glBindVertexArray(handle[0]);
		ThickLinesX_Y.vaoHandle = handle[0];
		
		// first VBO
		gl.glGenBuffers(1, handle, 0);
		gl.glBindBuffer(GL3.GL_ARRAY_BUFFER, handle[0]);
		ThickLinesX_Y.vboXhandle = handle[0];
		
		// use the first VBO for x-axis values
		index = gl.glGetAttribLocation(ThickLinesX_Y.programHandle, "x");
		gl.glVertexAttribPointer(index, 1, GL3.GL_FLOAT, false, 0, 0);
		gl.glEnableVertexAttribArray(index);
		
		// second VBO
		gl.glGenBuffers(1, handle, 0);
		gl.glBindBuffer(GL3.GL_ARRAY_BUFFER, handle[0]);
		ThickLinesX_Y.vboYhandle = handle[0];
		
		// use the second VBO for y-axis values
		index = gl.glGetAttribLocation(ThickLinesX_Y.programHandle, "y");
		gl.glVertexAttribPointer(index, 1, GL3.GL_FLOAT, false, 0, 0);
		gl.glEnableVertexAttribArray(index);
		
		// get handles for the uniforms
		ThickLinesX_Y.matrixHandle       = gl.glGetUniformLocation(ThickLinesX_Y.programHandle, "matrix");
		ThickLinesX_Y.colorHandle        = gl.glGetUniformLocation(ThickLinesX_Y.programHandle, "rgba");
		ThickLinesX_Y.lineWidthHandle    = gl.glGetUniformLocation(ThickLinesX_Y.programHandle, "lineWidth");
		ThickLinesX_Y.widthPixelsHandle  = gl.glGetUniformLocation(ThickLinesX_Y.programHandle, "widthPixels");
		ThickLinesX_Y.heightPixelsHandle = gl.glGetUniformLocation(ThickLinesX_Y.programHandle, "heightPixels");
		
		/*
		 * "PointsY" is for rendering points when the x coordinates are auto-generated.
		 * 
		 * One VBO of floats specifies (y1,y2,...) data.
		 * X-coordinates are automatically generated.
		 * One uniform mat4  specifies the matrix.
		 * One uniform vec4  specifies the color.
		 * One uniform int   specifies the x-offset if the lines don't start at the left edge of the plot.
		 * One uniform float specifies the point thickness as pixels.
		 * Two uniform floats specify the framebuffer size so the geometry shader can convert from NDCs into pixels.
		 */
		PointsY.programHandle = makeProgram(gl, vertexShaderVboY, geometryShaderThickPoints, fragmentShaderUniformColorPoints);
		
		// VAO
		gl.glGenVertexArrays(1, handle, 0);
		gl.glBindVertexArray(handle[0]);
		PointsY.vaoHandle = handle[0];
		
		// VBO
		gl.glGenBuffers(1, handle, 0);
		gl.glBindBuffer(GL3.GL_ARRAY_BUFFER, handle[0]);
		PointsY.vboHandle = handle[0];
		
		// use the VBO for (y1,y2,...) data
		index = gl.glGetAttribLocation(PointsY.programHandle, "y");
		gl.glVertexAttribPointer(index, 1, GL3.GL_FLOAT, false, 0, 0);
		gl.glEnableVertexAttribArray(index);
		
		// get handles for the uniforms
		PointsY.matrixHandle       = gl.glGetUniformLocation(PointsY.programHandle, "matrix");
		PointsY.colorHandle        = gl.glGetUniformLocation(PointsY.programHandle, "rgba");
		PointsY.xOffsetHandle      = gl.glGetUniformLocation(PointsY.programHandle, "xOffset");
		PointsY.pointWidthHandle   = gl.glGetUniformLocation(PointsY.programHandle, "pointWidth");
		PointsY.widthPixelsHandle  = gl.glGetUniformLocation(PointsY.programHandle, "widthPixels");
		PointsY.heightPixelsHandle = gl.glGetUniformLocation(PointsY.programHandle, "heightPixels");
		
		/*
		 * "PointsXY" is for rendering points.
		 * 
		 * One VBO of floats specifies (x1,y1,x2,y2,...) data.
		 * One uniform mat4  specifies the matrix.
		 * One uniform vec4  specifies the color.
		 * One uniform float specifies the point thickness as pixels.
		 * Two uniform floats specify the framebuffer size so the geometry shader can convert from NDCs into pixels.
		 */
		PointsXY.programHandle = makeProgram(gl, vertexShaderVboXy, geometryShaderThickPoints, fragmentShaderUniformColorPoints);
		
		// VAO
		gl.glGenVertexArrays(1, handle, 0);
		gl.glBindVertexArray(handle[0]);
		PointsXY.vaoHandle = handle[0];
		
		// VBO
		gl.glGenBuffers(1, handle, 0);
		gl.glBindBuffer(GL3.GL_ARRAY_BUFFER, handle[0]);
		PointsXY.vboHandle = handle[0];
		
		// use the VBO for (x,y) data
		index = gl.glGetAttribLocation(PointsXY.programHandle, "xy");
		gl.glVertexAttribPointer(index, 2, GL3.GL_FLOAT, false, 0, 0);
		gl.glEnableVertexAttribArray(index);
		
		// get handles for the uniforms
		PointsXY.matrixHandle       = gl.glGetUniformLocation(PointsXY.programHandle, "matrix");
		PointsXY.colorHandle        = gl.glGetUniformLocation(PointsXY.programHandle, "rgba");
		PointsXY.pointWidthHandle   = gl.glGetUniformLocation(PointsXY.programHandle, "pointWidth");
		PointsXY.widthPixelsHandle  = gl.glGetUniformLocation(PointsXY.programHandle, "widthPixels");
		PointsXY.heightPixelsHandle = gl.glGetUniformLocation(PointsXY.programHandle, "heightPixels");
		
		/*
		 * "PointsX_Y" is for rendering points when separate VBOs are use for X and Y values.
		 * 
		 * One VBO of floats specifies (x1,x2,...) data.
		 * One VBO of floats specifies (y1,y2,...) data.
		 * One uniform mat4  specifies the matrix.
		 * One uniform vec4  specifies the color.
		 * One uniform float specifies the point thickness as pixels.
		 * Two uniform floats specify the framebuffer size so the geometry shader can convert from NDCs into pixels.
		 */
		PointsX_Y.programHandle = makeProgram(gl, vertexShaderVboXvboY, geometryShaderThickPoints, fragmentShaderUniformColorPoints);
		
		// VAO
		gl.glGenVertexArrays(1, handle, 0);
		gl.glBindVertexArray(handle[0]);
		PointsX_Y.vaoHandle = handle[0];
		
		// first VBO
		gl.glGenBuffers(1, handle, 0);
		gl.glBindBuffer(GL3.GL_ARRAY_BUFFER, handle[0]);
		PointsX_Y.vboXhandle = handle[0];
		
		// use the first VBO for x-axis values
		index = gl.glGetAttribLocation(PointsX_Y.programHandle, "x");
		gl.glVertexAttribPointer(index, 1, GL3.GL_FLOAT, false, 0, 0);
		gl.glEnableVertexAttribArray(index);
		
		// second VBO
		gl.glGenBuffers(1, handle, 0);
		gl.glBindBuffer(GL3.GL_ARRAY_BUFFER, handle[0]);
		PointsX_Y.vboYhandle = handle[0];
		
		// use the second VBO for y-axis values
		index = gl.glGetAttribLocation(PointsX_Y.programHandle, "y");
		gl.glVertexAttribPointer(index, 1, GL3.GL_FLOAT, false, 0, 0);
		gl.glEnableVertexAttribArray(index);
		
		// get handles for the uniforms
		PointsX_Y.matrixHandle       = gl.glGetUniformLocation(PointsX_Y.programHandle, "matrix");
		PointsX_Y.colorHandle        = gl.glGetUniformLocation(PointsX_Y.programHandle, "rgba");
		PointsX_Y.pointWidthHandle   = gl.glGetUniformLocation(PointsX_Y.programHandle, "pointWidth");
		PointsX_Y.widthPixelsHandle  = gl.glGetUniformLocation(PointsX_Y.programHandle, "widthPixels");
		PointsX_Y.heightPixelsHandle = gl.glGetUniformLocation(PointsX_Y.programHandle, "heightPixels");
		
		/*
		 * "TrianglesXY" is for rendering 2D triangles with a solid color.
		 * One VBO of floats specifies (x1,y1,x2,y2,...) data.
		 * One uniform mat4 specifies the matrix.
		 * One uniform vec4 specifies the color.
		 */
		TrianglesXY.programHandle = makeProgram(gl, vertexShaderVboXy, null, fragmentShaderUniformColor);
		
		// VAO
		gl.glGenVertexArrays(1, handle, 0);
		gl.glBindVertexArray(handle[0]);
		TrianglesXY.vaoHandle = handle[0];
		
		// VBO
		gl.glGenBuffers(1, handle, 0);
		gl.glBindBuffer(GL3.GL_ARRAY_BUFFER, handle[0]);
		TrianglesXY.vboHandle = handle[0];
		
		// use the VBO for (x,y) data
		index = gl.glGetAttribLocation(TrianglesXY.programHandle, "xy");
		gl.glVertexAttribPointer(index, 2, GL3.GL_FLOAT, false, 0, 0);
		gl.glEnableVertexAttribArray(index);
		
		// get handles for the uniforms
		TrianglesXY.matrixHandle = gl.glGetUniformLocation(TrianglesXY.programHandle, "matrix");
		TrianglesXY.colorHandle  = gl.glGetUniformLocation(TrianglesXY.programHandle, "rgba");
		
		/*
		 * "TrianglesXYZUVW" is for rendering 3D objects.
		 * One VBO of floats specifies (x1,y1,z1,u1,v1,w1,...) data.
		 * One uniform mat4 specifies the matrix.
		 */
		TrianglesXYZUVW.programHandle = makeProgram(gl, vertexShaderVboXyzuvw, null, fragmentShaderLights3D);
		
		// VAO
		gl.glGenVertexArrays(1, handle, 0);
		gl.glBindVertexArray(handle[0]);
		TrianglesXYZUVW.vaoHandle = handle[0];
		
		// VBO
		gl.glGenBuffers(1, handle, 0);
		gl.glBindBuffer(GL3.GL_ARRAY_BUFFER, handle[0]);
		TrianglesXYZUVW.vboHandle = handle[0];
		
		// use the VBO for (x,y,z,u,v,w) data
		index = gl.glGetAttribLocation(TrianglesXYZUVW.programHandle, "xyz");
		gl.glVertexAttribPointer(index, 3, GL3.GL_FLOAT, false, 6*4, 0);
		gl.glEnableVertexAttribArray(index);
		index = gl.glGetAttribLocation(TrianglesXYZUVW.programHandle, "uvw");
		gl.glVertexAttribPointer(index, 3, GL3.GL_FLOAT, false, 6*4, 3*4);
		gl.glEnableVertexAttribArray(index);
		
		// get handle for the uniform
		TrianglesXYZUVW.matrixHandle = gl.glGetUniformLocation(TrianglesXYZUVW.programHandle, "matrix");
		
		/*
		 * "TrianglesXYST" is for rendering 2D triangles with a regular (not multisample) texture.
		 * One VBO of floats specifies (x1,y1,s1,t1,...) data.
		 * One uniform mat4 specifies the matrix.
		 */
		TrianglesXYST.programHandle = makeProgram(gl, vertexShaderVboXyst, null, fragmentShaderTex2D);
		
		// VAO
		gl.glGenVertexArrays(1, handle, 0);
		gl.glBindVertexArray(handle[0]);
		TrianglesXYST.vaoHandle = handle[0];
		
		// VBO
		gl.glGenBuffers(1, handle, 0);
		gl.glBindBuffer(GL3.GL_ARRAY_BUFFER, handle[0]);
		TrianglesXYST.vboHandle = handle[0];
		
		// use the VBO for (x,y,s,t) data
		index = gl.glGetAttribLocation(TrianglesXYST.programHandle, "xy");
		gl.glVertexAttribPointer(index, 2, GL3.GL_FLOAT, false, 4*4, 0);
		gl.glEnableVertexAttribArray(index);
		index = gl.glGetAttribLocation(TrianglesXYST.programHandle, "st");
		gl.glVertexAttribPointer(index, 2, GL3.GL_FLOAT, false, 4*4, 2*4);
		gl.glEnableVertexAttribArray(index);
		
		// get handles for the uniforms
		TrianglesXYST.matrixHandle = gl.glGetUniformLocation(TrianglesXYST.programHandle, "matrix");
		
		/*
		 * "TrianglesXYSTmultisample" is for rendering 2D triangles with a multisample texture.
		 * One VBO of floats specifies (x1,y1,s1,t1,...) data.
		 * One uniform mat4 specifies the matrix.
		 */
		if(SettingsController.getAntialiasingLevel() > 1) {
			
			TrianglesXYSTmultisample.programHandle = makeProgram(gl, vertexShaderVboXyst, null, fragmentShaderTex2DMS);
			
			// VAO
			gl.glGenVertexArrays(1, handle, 0);
			gl.glBindVertexArray(handle[0]);
			TrianglesXYSTmultisample.vaoHandle = handle[0];
			
			// VBO
			gl.glGenBuffers(1, handle, 0);
			gl.glBindBuffer(GL3.GL_ARRAY_BUFFER, handle[0]);
			TrianglesXYSTmultisample.vboHandle = handle[0];
			
			// use the VBO for (x,y,s,t) data
			index = gl.glGetAttribLocation(TrianglesXYSTmultisample.programHandle, "xy");
			gl.glVertexAttribPointer(index, 2, GL3.GL_FLOAT, false, 4*4, 0);
			gl.glEnableVertexAttribArray(index);
			index = gl.glGetAttribLocation(TrianglesXYSTmultisample.programHandle, "st");
			gl.glVertexAttribPointer(index, 2, GL3.GL_FLOAT, false, 4*4, 2*4);
			gl.glEnableVertexAttribArray(index);
			
			// get handles for the uniforms
			TrianglesXYSTmultisample.matrixHandle = gl.glGetUniformLocation(TrianglesXYSTmultisample.programHandle, "matrix");
			
		}
		
		/*
		 * "FontRenderer" is for drawing 2D text.
		 * 
		 * One VBO of floats specifies (x,y,s1,t1,sWidth,...) character location and texture atlas data.
		 * One uniform mat4  specifies the matrix.
		 * One uniform float specifies the line height.
		 */
		FontRenderer.programHandle = makeProgram(gl, vertexShaderFontRenderer, geometryShaderFontRenderer, fragmentShaderFontRenderer);
		
		// VAO
		gl.glGenVertexArrays(1, handle, 0);
		gl.glBindVertexArray(handle[0]);
		FontRenderer.vaoHandle = handle[0];
		
		// VBO
		gl.glGenBuffers(1, handle, 0);
		gl.glBindBuffer(GL3.GL_ARRAY_BUFFER, handle[0]);
		FontRenderer.vboHandle = handle[0];
		
		// use the VBO for (x,y,s,t,w) data
		index = gl.glGetAttribLocation(FontRenderer.programHandle, "xy");
		gl.glVertexAttribPointer(index, 2, GL3.GL_FLOAT, false, 5*4, 0);
		gl.glEnableVertexAttribArray(index);
		index = gl.glGetAttribLocation(FontRenderer.programHandle, "stw");
		gl.glVertexAttribPointer(index, 3, GL3.GL_FLOAT, false, 5*4, 2*4);
		gl.glEnableVertexAttribArray(index);
		
		// get handles for the uniforms
		FontRenderer.matrixHandle     = gl.glGetUniformLocation(FontRenderer.programHandle, "matrix");
		FontRenderer.lineHeightHandle = gl.glGetUniformLocation(FontRenderer.programHandle, "lineHeight");
		
		// create the textures
		createTexture(gl, handle, 512, 512, GL3.GL_RGBA, GL3.GL_UNSIGNED_BYTE, false);
		FontRenderer.smallFontTextureHandle[0] = handle[0];
		createTexture(gl, handle, 512, 512, GL3.GL_RGBA, GL3.GL_UNSIGNED_BYTE, false);
		FontRenderer.mediumFontTextureHandle[0] = handle[0];
		createTexture(gl, handle, 512, 512, GL3.GL_RGBA, GL3.GL_UNSIGNED_BYTE, false);
		FontRenderer.largeFontTextureHandle[0] = handle[0];
		
	}
	
	/**
	 * Compiles vertex / geometry / fragment shaders, then links them into a program.
	 * If an error occurs, the user will be notified.
	 * 
	 * @param gl                    The OpenGL context.
	 * @param vertexShaderCode      Vertex shader source code.
	 * @param geometryShaderCode    Geometry shader source code, or null if not using one.
	 * @param fragmentShaderCode    Fragment shader source code.
	 * @return                      Handle to the program.
	 */
	public static int makeProgram(GL2ES3 gl, String[] vertexShaderCode, String[] geometryShaderCode, String[] fragmentShaderCode) {
		
		// compile the vertex shader and check for errors
		int vertexShader = gl.glCreateShader(GL3.GL_VERTEX_SHADER);
		gl.glShaderSource(vertexShader, vertexShaderCode.length, vertexShaderCode, null, 0);
		gl.glCompileShader(vertexShader);
		
		int[] statusCode = new int[1];
		gl.glGetShaderiv(vertexShader, GL3.GL_COMPILE_STATUS, statusCode, 0);
		if(statusCode[0] != GL3.GL_TRUE) {
			int[] length = new int[1];
			gl.glGetShaderiv(vertexShader, GL3.GL_INFO_LOG_LENGTH, length, 0);
			if(length[0] > 0) {
				byte[] errorMessage = new byte[length[0]];
				gl.glGetShaderInfoLog(vertexShader, length[0], length, 0, errorMessage, 0);
				NotificationsController.showFailureForSeconds("GLSL Vertex Shader Error:\n" + new String(errorMessage).trim(), 999, false);
			}
		}
		
		// compile the geometry shader and check for errors
		int geometryShader = 0;
		if(geometryShaderCode != null) {
			geometryShader = gl.glCreateShader(GL3.GL_GEOMETRY_SHADER);
			gl.glShaderSource(geometryShader, geometryShaderCode.length, geometryShaderCode, null, 0);
			gl.glCompileShader(geometryShader);
			
			gl.glGetShaderiv(geometryShader, GL3.GL_COMPILE_STATUS, statusCode, 0);
			if(statusCode[0] != GL3.GL_TRUE) {
				int[] length = new int[1];
				gl.glGetShaderiv(geometryShader, GL3.GL_INFO_LOG_LENGTH, length, 0);
				if(length[0] > 0) {
					byte[] errorMessage = new byte[length[0]];
					gl.glGetShaderInfoLog(geometryShader, length[0], length, 0, errorMessage, 0);
					NotificationsController.showFailureForSeconds("GLSL Geometry Shader Error:\n" + new String(errorMessage).trim(), 999, false);
				}
			}
		}
		
		// compile the fragment shader and check for errors
		int fragmentShader = gl.glCreateShader(GL3.GL_FRAGMENT_SHADER);
		gl.glShaderSource(fragmentShader, fragmentShaderCode.length, fragmentShaderCode, null, 0);
		gl.glCompileShader(fragmentShader);
		
		gl.glGetShaderiv(fragmentShader, GL3.GL_COMPILE_STATUS, statusCode, 0);
		if(statusCode[0] != GL3.GL_TRUE) {
			int[] length = new int[1];
			gl.glGetShaderiv(fragmentShader, GL3.GL_INFO_LOG_LENGTH, length, 0);
			if(length[0] > 0) {
				byte[] errorMessage = new byte[length[0]];
				gl.glGetShaderInfoLog(fragmentShader, length[0], length, 0, errorMessage, 0);
				NotificationsController.showFailureForSeconds("GLSL Fragment Shader Error:\n" + new String(errorMessage).trim(), 999, false);
			}
		}
		
		// link the shaders into a program and check for errors
		int handle = gl.glCreateProgram();
		gl.glAttachShader(handle, vertexShader);
		if(geometryShaderCode != null)
			gl.glAttachShader(handle, geometryShader);
		gl.glAttachShader(handle, fragmentShader);
		gl.glLinkProgram(handle);
		
		gl.glGetProgramiv(handle, GL3.GL_LINK_STATUS, statusCode, 0);
		if(statusCode[0] != GL3.GL_TRUE) {
			int[] length = new int[1];
			gl.glGetProgramiv(handle, GL3.GL_INFO_LOG_LENGTH, length, 0);
			if(length[0] > 0) {
				byte[] errorMessage = new byte[length[0]];
				gl.glGetProgramInfoLog(handle, length[0], length, 0, errorMessage, 0);
				NotificationsController.showFailureForSeconds("GLSL Shader Program Error:\n" + new String(errorMessage).trim(), 999, false);
			}
		}
		
		// free resources
		gl.glDeleteShader(vertexShader);
		if(geometryShaderCode != null)
			gl.glDeleteShader(geometryShader);
		gl.glDeleteShader(fragmentShader);
		
		return handle;
		
	}
	
}
