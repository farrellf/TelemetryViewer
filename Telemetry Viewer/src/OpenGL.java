import java.nio.FloatBuffer;
import java.util.Arrays;

import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GL4;

public class OpenGL {
	
	// buffer for anyone to use
	public static FloatBuffer buffer = Buffers.newDirectFloatBuffer(2048); // hopefully big enough for most things
	
	/**
	 * Draws (x,y) vertices as a GL_TRIANGLE_STRIP.
	 * 
	 * @param gl             The OpenGL context.
	 * @param color          The color, as a float[4].
	 * @param buffer         Vertex buffer containing (x1,y1,x2,y2,...)
	 * @param vertexCount    Number of vertices in the buffer.
	 */
	public static void drawTriangleStrip2D(GL2 gl, float[] color, FloatBuffer buffer, int vertexCount) {
		
//		gl.glColor4fv(color, 0);
//		gl.glVertexPointer(2, GL2.GL_FLOAT, 0, buffer);
//		gl.glDrawArrays(GL2.GL_TRIANGLE_STRIP, 0, vertexCount);
		
		enableXyColorProgram(gl, currentMatrix);
		gl.glUniform4fv(xyColorColorHandle, 1, color, 0);
		gl.glBufferData(GL4.GL_ARRAY_BUFFER, vertexCount * 2 * 4, buffer.position(0), GL4.GL_STATIC_DRAW);
		gl.glDrawArrays(GL2.GL_TRIANGLE_STRIP, 0, vertexCount);
		disableXyColorProgram(gl);
		
	}
	
	/**
	 * Draws (x,y,r,g,b,a) vertices as a GL_TRIANGLE_STRIP.
	 * 
	 * @param gl             The OpenGL context.
	 * @param buffer         Vertex buffer containing (x1,y1,r1,g1,b1,a1,...)
	 * @param vertexCount    Number of vertices in the buffer.
	 */
	public static void drawColoredTriangleStrip2D(GL2 gl, FloatBuffer buffer, int vertexCount) {
		
//		gl.glEnableClientState(GL2.GL_COLOR_ARRAY);
//		gl.glVertexPointer(2, GL2.GL_FLOAT, 6*4, buffer.position(0));
//		gl.glColorPointer(4, GL2.GL_FLOAT, 6*4, buffer.position(2));
//		gl.glDrawArrays(GL2.GL_TRIANGLE_STRIP, 0, vertexCount);
//		gl.glDisableClientState(GL2.GL_COLOR_ARRAY);
		
		enableXyrgbaProgram(gl, currentMatrix);
		gl.glBufferData(GL4.GL_ARRAY_BUFFER, vertexCount * 6 * 4, buffer.position(0), GL4.GL_STATIC_DRAW);
		gl.glDrawArrays(GL2.GL_TRIANGLE_STRIP, 0, vertexCount);
		disableXyrgbaProgram(gl);
		
	}
	
	/**
	 * Draws (x,y,z,u,v,w) vertices as GL_TRIANGLES.
	 * 
	 * @param gl             The OpenGL context.
	 * @param buffer         Vertex buffer containing (x1,y1,z1,u1,v1,w1,...)
	 * @param vertexCount    Number of vertices in the buffer.
	 */
	public static void drawTriangles3D(GL2 gl, FloatBuffer buffer, int vertexCount) {
		
//		gl.glEnableClientState(GL2.GL_NORMAL_ARRAY);
//		gl.glVertexPointer(3, GL2.GL_FLOAT, 6*4, buffer.position(0));
//		gl.glNormalPointer(GL2.GL_FLOAT, 6*4, buffer.position(3));
//		gl.glEnable(GL2.GL_DEPTH_TEST);
//		gl.glClear(GL2.GL_DEPTH_BUFFER_BIT);
//		gl.glDrawArrays(GL2.GL_TRIANGLES, 0, vertexCount);
//		gl.glDisable(GL2.GL_DEPTH_TEST);
//		gl.glDisableClientState(GL2.GL_NORMAL_ARRAY);
		
		enableXyzuvwProgram(gl, currentMatrix);
		gl.glEnable(GL2.GL_DEPTH_TEST);
		gl.glClear(GL2.GL_DEPTH_BUFFER_BIT);
		gl.glBufferData(GL4.GL_ARRAY_BUFFER, vertexCount * 6 * 4, buffer.position(0), GL4.GL_STATIC_DRAW);
		gl.glDrawArrays(GL2.GL_TRIANGLES, 0, vertexCount);
		gl.glDisable(GL2.GL_DEPTH_TEST);
		disableXyzuvwProgram(gl);
		
	}
	
	/**
	 * Draws the vertices contained in "buffer" as a GL_LINE_LOOP.
	 * 
	 * @param gl             The OpenGL context.
	 * @param color          The color, as a float[4].
	 * @param buffer         Vertex buffer containing (x1,y1,x2,y2,...)
	 * @param vertexCount    Number of vertices in the buffer.
	 */
	public static void drawLineLoop2D(GL2 gl, float[] color, FloatBuffer buffer, int vertexCount) {
		
//		gl.glColor4fv(color, 0);
//		gl.glVertexPointer(2, GL2.GL_FLOAT, 0, buffer);
//		gl.glDrawArrays(GL2.GL_LINE_LOOP, 0, vertexCount);
		
		enableXyColorProgram(gl, currentMatrix);
		gl.glUniform4fv(xyColorColorHandle, 1, color, 0);
		gl.glBufferData(GL4.GL_ARRAY_BUFFER, vertexCount * 2 * 4, buffer.position(0), GL4.GL_STATIC_DRAW);
		gl.glDrawArrays(GL2.GL_LINE_LOOP, 0, vertexCount);
		disableXyColorProgram(gl);
		
	}
	
	/**
	 * Draws the vertices contained in "buffer" as a GL_LINE_STRIP.
	 * 
	 * @param gl             The OpenGL context.
	 * @param color          The color, as a float[4].
	 * @param buffer         Vertex buffer containing (x1,y1,x2,y2,...)
	 * @param vertexCount    Number of vertices in the buffer.
	 */
	public static void drawLineStrip2D(GL2 gl, float[] color, FloatBuffer buffer, int vertexCount) {
		
//		gl.glColor4fv(color, 0);
//		gl.glVertexPointer(2, GL2.GL_FLOAT, 0, buffer);
//		gl.glDrawArrays(GL2.GL_LINE_STRIP, 0, vertexCount);
		
		enableXyColorProgram(gl, currentMatrix);
		gl.glUniform4fv(xyColorColorHandle, 1, color, 0);
		gl.glBufferData(GL4.GL_ARRAY_BUFFER, vertexCount * 2 * 4, buffer.position(0), GL4.GL_STATIC_DRAW);
		gl.glDrawArrays(GL2.GL_LINE_STRIP, 0, vertexCount);
		disableXyColorProgram(gl);
		
	}
	
	/**
	 * Draws the vertices contained in "buffer" as GL_POINTS.
	 * 
	 * @param gl             The OpenGL context.
	 * @param color          The color, as a float[4].
	 * @param buffer         Vertex buffer containing (x1,y1,x2,y2,...)
	 * @param vertexCount    Number of vertices in the buffer.
	 */
	public static void drawPoints2D(GL2 gl, float[] color, FloatBuffer buffer, int vertexCount) {
		
//		gl.glColor4fv(color, 0);
//		gl.glVertexPointer(2, GL2.GL_FLOAT, 0, buffer);
//		gl.glDrawArrays(GL2.GL_POINTS, 0, vertexCount);
		
		enableXyColorProgram(gl, currentMatrix);
		gl.glUniform4fv(xyColorColorHandle, 1, color, 0);
		gl.glBufferData(GL4.GL_ARRAY_BUFFER, vertexCount * 2 * 4, buffer.position(0), GL4.GL_STATIC_DRAW);
		gl.glDrawArrays(GL2.GL_POINTS, 0, vertexCount);
		disableXyColorProgram(gl);
		
	}
	
	/**
	 * Draws the vertices contained in "buffer" as GL_LINES.
	 * 
	 * @param gl             The OpenGL context.
	 * @param color          The color, as a float[4].
	 * @param buffer         Vertex buffer containing (x1,y1,x2,y2,...)
	 * @param vertexCount    Number of vertices in the buffer.
	 */
	public static void drawLines2D(GL2 gl, float[] color, FloatBuffer buffer, int vertexCount) {
		
//		gl.glColor4fv(color, 0);
//		gl.glVertexPointer(2, GL2.GL_FLOAT, 0, buffer);
//		gl.glDrawArrays(GL2.GL_LINES, 0, vertexCount);
		
		enableXyColorProgram(gl, currentMatrix);
		gl.glUniform4fv(xyColorColorHandle, 1, color, 0);
		gl.glBufferData(GL4.GL_ARRAY_BUFFER, vertexCount * 2 * 4, buffer.position(0), GL4.GL_STATIC_DRAW);
		gl.glDrawArrays(GL2.GL_LINES, 0, vertexCount);
		disableXyColorProgram(gl);
		
	}
	
	/**
	 * Draws (x,y,r,g,b,a) vertices as GL_LINES.
	 * 
	 * @param gl             The OpenGL context.
	 * @param buffer         Vertex buffer containing (x1,y1,r1,g1,b1,a1,...)
	 * @param vertexCount    Number of vertices in the buffer.
	 */
	public static void drawColoredLines2D(GL2 gl, FloatBuffer buffer, int vertexCount) {
		
//		gl.glEnableClientState(GL2.GL_COLOR_ARRAY);
//		gl.glVertexPointer(2, GL2.GL_FLOAT, 6*4, buffer.position(0));
//		gl.glColorPointer(4, GL2.GL_FLOAT, 6*4, buffer.position(2));
//		gl.glDrawArrays(GL2.GL_LINES, 0, vertexCount);
//		gl.glDisableClientState(GL2.GL_COLOR_ARRAY);
		
		enableXyrgbaProgram(gl, currentMatrix);
		gl.glBufferData(GL4.GL_ARRAY_BUFFER, vertexCount * 6 * 4, buffer.position(0), GL4.GL_STATIC_DRAW);
		gl.glDrawArrays(GL2.GL_LINES, 0, vertexCount);
		disableXyrgbaProgram(gl);
		
	}
	
	/**
	 * Helper function that draws an axis-aligned quad.
	 * 
	 * @param gl            The OpenGL context.
	 * @param color         The color, as a float[4].
	 * @param lowerLeftX    Lower-left x location.
	 * @param lowerLeftY    Lower-left y location.
	 * @param width         Width of the quad.
	 * @param height        Height of the quad.
	 */
	public static void drawBox(GL2 gl, float[] color, float lowerLeftX, float lowerLeftY, float width, float height) {
		
		buffer.rewind();
		buffer.put(lowerLeftX);         buffer.put(lowerLeftY + height);
		buffer.put(lowerLeftX);         buffer.put(lowerLeftY);
		buffer.put(lowerLeftX + width); buffer.put(lowerLeftY + height);
		buffer.put(lowerLeftX + width); buffer.put(lowerLeftY);
		buffer.rewind();
		drawTriangleStrip2D(gl, color, buffer, 4);
		
	}
	
	/**
	 * Helper function that draws the outline of an axis-aligned quad.
	 * 
	 * @param gl            The OpenGL context.
	 * @param color         The color, as a float[4].
	 * @param lowerLeftX    Lower-left x location.
	 * @param lowerLeftY    Lower-left y location.
	 * @param width         Width of the quad.
	 * @param height        Height of the quad.
	 */
	public static void drawBoxOutline(GL2 gl, float[] color, float lowerLeftX, float lowerLeftY, float width, float height) {
		
		buffer.rewind();
		buffer.put(lowerLeftX);         buffer.put(lowerLeftY);
		buffer.put(lowerLeftX);         buffer.put(lowerLeftY + height);
		buffer.put(lowerLeftX + width); buffer.put(lowerLeftY + height);
		buffer.put(lowerLeftX + width); buffer.put(lowerLeftY);
		buffer.rewind();
		drawLineLoop2D(gl, color, buffer, 4);
		
	}
	
	/**
	 * Draws one triangle.
	 * 
	 * @param gl       The OpenGL context.
	 * @param color    The color, as a float[4].
	 * @param x1       First vertex.
	 * @param y1       First vertex.
	 * @param x2       Second vertex.
	 * @param y2       Second vertex.
	 * @param x3       Third vertex.
	 * @param y3       Third vertex.
	 */
	public static void drawTriangle2D(GL2 gl, float[] color, float x1, float y1, float x2, float y2, float x3, float y3) {
		
		buffer.rewind();
		buffer.put(x1); buffer.put(y1);
		buffer.put(x2); buffer.put(y2);
		buffer.put(x3); buffer.put(y3);
		buffer.rewind();
		drawTriangleStrip2D(gl, color, buffer, 3);
		
	}
	
	/**
	 * Helper function that draws a quad.
	 * 
	 * @param gl             The OpenGL context.
	 * @param color          The color, as a float[4].
	 * @param lowerLeftX     Lower-left x location.
	 * @param lowerLeftY     Lower-left y location.
	 * @param upperRightX    Upper-right x location.
	 * @param upperRightY    Upper-right y location.
	 */
	public static void drawQuad2D(GL2 gl, float[] color, float lowerLeftX, float lowerLeftY, float upperRightX, float upperRightY) {
		
		buffer.rewind();
		buffer.put(lowerLeftX);  buffer.put(upperRightY);
		buffer.put(lowerLeftX);  buffer.put(lowerLeftY);
		buffer.put(upperRightX); buffer.put(upperRightY);
		buffer.put(upperRightX); buffer.put(lowerLeftY);
		buffer.rewind();
		drawTriangleStrip2D(gl, color, buffer, 4);
		
	}
	
	/**
	 * Helper function that draws a quad.
	 * 
	 * @param gl             The OpenGL context.
	 * @param color          The color, as a float[4].
	 * @param lowerLeftX     Lower-left x location.
	 * @param lowerLeftY     Lower-left y location.
	 * @param upperRightX    Upper-right x location.
	 * @param upperRightY    Upper-right y location.
	 */
	public static void drawQuadOutline2D(GL2 gl, float[] color, float lowerLeftX, float lowerLeftY, float upperRightX, float upperRightY) {
		
		buffer.rewind();
		buffer.put(lowerLeftX);  buffer.put(upperRightY);
		buffer.put(lowerLeftX);  buffer.put(lowerLeftY);
		buffer.put(upperRightX); buffer.put(lowerLeftY);
		buffer.put(upperRightX); buffer.put(upperRightY);
		buffer.rewind();
		drawLineLoop2D(gl, color, buffer, 4);
		
	}
	
	/**
	 * Creates an off-screen framebuffer and corresponding texture to use with it.
	 * The texture is configured for RGBA uint8, with min/mag filter set to nearest.
	 * 
	 * @param gl         The OpenGL context.
	 * @param fbo        The FBO handle will be saved here.
	 * @param texture    The texture handle will be saved here.
	 */
	public static void createOffscreenFramebuffer(GL2 gl, int[] fbo, int[] texture) {
		
		// create and use a framebuffer
		gl.glGenFramebuffers(1, fbo, 0);
		gl.glBindFramebuffer(GL2.GL_FRAMEBUFFER, fbo[0]);
		
		// create and use a texture
		gl.glGenTextures(1, texture, 0);
		gl.glBindTexture(GL2.GL_TEXTURE_2D, texture[0]);
		gl.glTexImage2D(GL2.GL_TEXTURE_2D, 0, GL2.GL_RGBA, 512, 512, 0, GL2.GL_RGBA, GL2.GL_UNSIGNED_BYTE, null); // dummy 512x512 texture
		gl.glTexParameteri(GL2.GL_TEXTURE_2D, GL2.GL_TEXTURE_MIN_FILTER, GL2.GL_NEAREST);
		gl.glTexParameteri(GL2.GL_TEXTURE_2D, GL2.GL_TEXTURE_MAG_FILTER, GL2.GL_NEAREST);
		gl.glFramebufferTexture2D(GL2.GL_FRAMEBUFFER, GL2.GL_COLOR_ATTACHMENT0, GL2.GL_TEXTURE_2D, texture[0], 0);
		gl.glDrawBuffers(1, new int[] {GL2.GL_COLOR_ATTACHMENT0}, 0);
		
		// check for errors
		if(gl.glCheckFramebufferStatus(GL2.GL_FRAMEBUFFER) != GL2.GL_FRAMEBUFFER_COMPLETE)
			NotificationsController.showFailureForSeconds("Error while creating a framebuffer or texture.", 5, false);
		
		// switch back to the screen framebuffer
		gl.glBindFramebuffer(GL2.GL_FRAMEBUFFER, 0);
		
	}
	
	/**
	 * Saves the current viewport/scissor/point settings, disables the scissor test,
	 * switches to the off-screen framebuffer and replaces the existing texture with a new one,
	 * then applies the new matrix.
	 * 
	 * @param gl                 The OpenGL context.
	 * @param offscreenMatrix    The 4x4 matrix to use.
	 * @param fbo                Handle to the FBO.
	 * @param texture            Handle to the texture.
	 * @param width              Width, in pixels.
	 * @param height             Height, in pixels.
	 */
	public static void startDrawingOffscreen(GL2 gl, float[] offscreenMatrix, int[] fbo, int[] texture, int width, int height) {
		
		// save the viewport/scissor/point settings, modelview matrix, and projection matrix
		gl.glPushAttrib(GL2.GL_VIEWPORT_BIT | GL2.GL_SCISSOR_BIT | GL2.GL_POINT_BIT);

		// switch to the off-screen framebuffer and corresponding texture
		gl.glBindFramebuffer(GL2.GL_FRAMEBUFFER, fbo[0]);
		gl.glBindTexture(GL2.GL_TEXTURE_2D, texture[0]);

		// replace the existing texture
		gl.glTexImage2D(GL2.GL_TEXTURE_2D, 0, GL2.GL_RGBA, width, height, 0, GL2.GL_RGBA, GL2.GL_UNSIGNED_BYTE, null);

		// set the viewport and disable the scissor test
		gl.glViewport(0, 0, width, height);
		gl.glDisable(GL2.GL_SCISSOR_TEST);
		
		// set the matrix
		useMatrix(gl, offscreenMatrix);
		
		// set the blend function
		gl.glBlendFuncSeparate(GL2.GL_SRC_ALPHA, GL2.GL_ONE_MINUS_SRC_ALPHA, GL2.GL_ONE, GL2.GL_ONE_MINUS_SRC_ALPHA);
		
		// clear the texture
		gl.glClearColor(0, 0, 0, 0);
		gl.glClear(GL2.GL_COLOR_BUFFER_BIT);
		
	}
	
	/**
	 * Switches back to the on-screen framebuffer,
	 * applies the on-screen matrix and re-enables the scissor test.
	 * 
	 * @param gl                The OpenGL context.
	 * @param onscreenMatrix    The 4x4 matrix to use.
	 */
	public static void stopDrawingOffscreen(GL2 gl, float[] onscreenMatrix) {
		
		// switch back to the screen framebuffer
		gl.glBindFramebuffer(GL2.GL_FRAMEBUFFER, 0);
		
		// restore old viewport/scissor/point settings
		gl.glPopAttrib();
		
		// set the matrix
		useMatrix(gl, onscreenMatrix);
		
		// enable the scissor test
		gl.glEnable(GL2.GL_SCISSOR_TEST);
		
	}
	
	/**
	 * Helper function that draws a texture onto an axis-aligned quad.
	 * 
	 * @param gl            The OpenGL context.
	 * @param texture       Handle to the texture.
	 * @param lowerLeftX    Lower-left x location.
	 * @param lowerLeftY    Lower-left y location.
	 * @param width         Width of the quad.
	 * @param height        Height of the quad.
	 * @param offset        Used to stretch the texture so its pixels are half-way through the left and right edge of the quad.
	 *                      This is used by the DFT chart so the first and last bins get centered on the left and right edges.
	 */
	public static void drawTexturedBox(GL2 gl, int[] texture, float lowerLeftX, float lowerLeftY, float width, float height, float offset) {
		
		buffer.rewind();
		buffer.put(0 + offset);         buffer.put(1);                   // u,v
		buffer.put(lowerLeftX);         buffer.put(lowerLeftY + height); // x,y
		buffer.put(0 + offset);         buffer.put(0);
		buffer.put(lowerLeftX);         buffer.put(lowerLeftY);
		buffer.put(1 - offset);         buffer.put(1);
		buffer.put(lowerLeftX + width); buffer.put(lowerLeftY + height);
		buffer.put(1 - offset);         buffer.put(0);
		buffer.put(lowerLeftX + width); buffer.put(lowerLeftY);
		buffer.rewind();
		
		// draw a textured quad on screen, with the texture replacing the color and opacity of the quad
		gl.glBlendFunc(GL2.GL_ONE, GL2.GL_ONE_MINUS_SRC_ALPHA);
		gl.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_TEXTURE_ENV_MODE, GL2.GL_REPLACE);
		gl.glDisable(GL2.GL_LIGHTING);
		gl.glEnable(GL2.GL_TEXTURE_2D);
		gl.glBindTexture(GL2.GL_TEXTURE_2D, texture[0]);
		gl.glEnableClientState(GL2.GL_TEXTURE_COORD_ARRAY);
		gl.glTexCoordPointer(2, GL2.GL_FLOAT, 4*4, OpenGL.buffer.position(0));
		gl.glVertexPointer(2, GL2.GL_FLOAT, 4*4, buffer.position(2));
		gl.glDrawArrays(GL2.GL_TRIANGLE_STRIP, 0, 4);
		gl.glDisableClientState(GL2.GL_TEXTURE_COORD_ARRAY);
		gl.glDisable(GL2.GL_TEXTURE_2D);
		
		// restore the normal blend function
		gl.glBlendFunc(GL2.GL_SRC_ALPHA, GL2.GL_ONE_MINUS_SRC_ALPHA);
		
	}
	
	/**
	 * Helper function that draws a texture onto an axis-aligned quad.
	 * 
	 * @param gl            The OpenGL context.
	 * @param texture       Handle to the texture.
	 * @param lowerLeftX    Lower-left x location.
	 * @param lowerLeftY    Lower-left y location.
	 * @param width         Width of the quad.
	 * @param height        Height of the quad.
	 * @param startX        What x value to render at the left edge of the quad.
	 */
	public static void drawTexturedRingBufferBox(GL2 gl, int[] texture, float lowerLeftX, float lowerLeftY, float width, float height, float startX) {
		
		buffer.rewind();
		buffer.put(0 + startX);         buffer.put(1);                   // u,v
		buffer.put(lowerLeftX);         buffer.put(lowerLeftY + height); // x,y
		buffer.put(0 + startX);         buffer.put(0);
		buffer.put(lowerLeftX);         buffer.put(lowerLeftY);
		buffer.put(1 + startX);         buffer.put(1);
		buffer.put(lowerLeftX + width); buffer.put(lowerLeftY + height);
		buffer.put(1 + startX);         buffer.put(0);
		buffer.put(lowerLeftX + width); buffer.put(lowerLeftY);
		buffer.rewind();
		
		// draw a textured quad on screen, with the texture replacing the color and opacity of the quad
		gl.glBlendFunc(GL2.GL_ONE, GL2.GL_ONE_MINUS_SRC_ALPHA);
		gl.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_TEXTURE_ENV_MODE, GL2.GL_REPLACE);
		gl.glDisable(GL2.GL_LIGHTING);
		gl.glEnable(GL2.GL_TEXTURE_2D);
		gl.glBindTexture(GL2.GL_TEXTURE_2D, texture[0]);
		gl.glEnableClientState(GL2.GL_TEXTURE_COORD_ARRAY);
		gl.glTexCoordPointer(2, GL2.GL_FLOAT, 4*4, OpenGL.buffer.position(0));
		gl.glVertexPointer(2, GL2.GL_FLOAT, 4*4, buffer.position(2));
		gl.glDrawArrays(GL2.GL_TRIANGLE_STRIP, 0, 4);
		gl.glDisableClientState(GL2.GL_TEXTURE_COORD_ARRAY);
		gl.glDisable(GL2.GL_TEXTURE_2D);
		
		// restore the normal blend function
		gl.glBlendFunc(GL2.GL_SRC_ALPHA, GL2.GL_ONE_MINUS_SRC_ALPHA);
		
	}
	
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
	
	public static void translateMatrix(float[] matrix, float x, float y, float z) {
		
		// column 3
		matrix[12] += (matrix[0] * x) + (matrix[4] * y) + (matrix[8] * z);
		matrix[13] += (matrix[1] * x) + (matrix[5] * y) + (matrix[9] * z);
		matrix[14] += (matrix[2] * x) + (matrix[6] * y) + (matrix[10] * z);
		matrix[15] += (matrix[3] * x) + (matrix[7] * y) + (matrix[11] * z);
		
	}
	
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
	
	public static void useMatrix(GL2 gl, float[] matrix) {
		
		gl.glMatrixMode(GL2.GL_PROJECTION);
		gl.glLoadIdentity();
		gl.glMatrixMode(GL2.GL_MODELVIEW);
		gl.glLoadMatrixf(matrix, 0);
		
		currentMatrix = matrix;
		
	}
	
	static int[] previousProgram = new int[1];
	static int[] previousVao = new int[1];
	static int[] previousVbo = new int[1];
	
	/*
	 * This program is for rendering 2D objects with a solid color.
	 * One VBO of floats specifies (x1,y1,...) data.
	 * One uniform vec4 specifies the color.
	 * One uniform mat4 specifies the matrix.
	 */
	static int   xyColorProgramHandle = 0;
	static int   xyColorMatrixHandle = 0;
	static int   xyColorColorHandle = 0;
	static int[] xyColorVaoHandle = new int[1];
	static int[] xyColorVboHandle = new int[1];
	
	public static void makeXyColorProgram(GL2 gl) {
		
		// save current state
		gl.glGetIntegerv(GL4.GL_CURRENT_PROGRAM, previousProgram, 0);
		gl.glGetIntegerv(GL4.GL_VERTEX_ARRAY_BINDING, previousVao, 0);
		gl.glGetIntegerv(GL4.GL_ARRAY_BUFFER_BINDING, previousVbo, 0);
		
		// shaders
		String[] vertexShaderCode = new String[] {
			"#version 430\n",
			"layout (location = 0) in vec2 position;\n",
			"uniform mat4 matrix;\n",
			"void main(void) {\n",
			"	gl_Position = matrix * vec4(position.x, position.y, 0.0, 1.0);\n",
			"}\n"
		};
		
		String[] fragmentShaderCode = new String[] {
			"#version 430\n",
			"uniform vec4 rgba;\n",
			"out vec4 color;\n",
			"void main(void) {\n",
			"	color = rgba;\n",
			"}\n"
		};
		
		// compile the vertex shader and check for errors
		int vertexShader = gl.glCreateShader(GL4.GL_VERTEX_SHADER);
		gl.glShaderSource(vertexShader, vertexShaderCode.length, vertexShaderCode, null, 0);
		gl.glCompileShader(vertexShader);
		
		int[] statusCode = new int[1];
		gl.glGetShaderiv(vertexShader, GL4.GL_COMPILE_STATUS, statusCode, 0);
		if(statusCode[0] != GL4.GL_TRUE) {
			int[] length = new int[1];
			gl.glGetShaderiv(vertexShader, GL4.GL_INFO_LOG_LENGTH, length, 0);
			byte[] errorMessage = new byte[length[0]];
			gl.glGetShaderInfoLog(vertexShader, length[0], length, 0, errorMessage, 0);
			NotificationsController.showFailureForSeconds("OpenGL Shader Error: " + new String(errorMessage), 999, false);
		}
		
		// compile the fragment shader and check for errors
		int fragmentShader = gl.glCreateShader(GL4.GL_FRAGMENT_SHADER);
		gl.glShaderSource(fragmentShader, fragmentShaderCode.length, fragmentShaderCode, null, 0);
		gl.glCompileShader(fragmentShader);
		
		gl.glGetShaderiv(fragmentShader, GL4.GL_COMPILE_STATUS, statusCode, 0);
		if(statusCode[0] != GL4.GL_TRUE) {
			int[] length = new int[1];
			gl.glGetShaderiv(fragmentShader, GL4.GL_INFO_LOG_LENGTH, length, 0);
			byte[] errorMessage = new byte[length[0]];
			gl.glGetShaderInfoLog(fragmentShader, length[0], length, 0, errorMessage, 0);
			NotificationsController.showFailureForSeconds("OpenGL Shader Error: " + new String(errorMessage), 999, false);
		}
		
		// link the shaders into a program and check for errors
		xyColorProgramHandle = gl.glCreateProgram();
		gl.glAttachShader(xyColorProgramHandle, vertexShader);
		gl.glAttachShader(xyColorProgramHandle, fragmentShader);
		gl.glLinkProgram(xyColorProgramHandle);
		
		gl.glGetShaderiv(xyColorProgramHandle, GL4.GL_LINK_STATUS, statusCode, 0);
		if(statusCode[0] != GL4.GL_TRUE) {
			int[] length = new int[1];
			gl.glGetShaderiv(xyColorProgramHandle, GL4.GL_INFO_LOG_LENGTH, length, 0);
			byte[] errorMessage = new byte[length[0]];
			gl.glGetShaderInfoLog(xyColorProgramHandle, length[0], length, 0, errorMessage, 0);
			NotificationsController.showFailureForSeconds("OpenGL Shader Error: " + new String(errorMessage), 999, false);
		}
		
		// free resources
		gl.glDeleteShader(vertexShader);
		gl.glDeleteShader(fragmentShader);

		// at least one VAO is required
		gl.glGenVertexArrays(1, xyColorVaoHandle, 0);
		gl.glBindVertexArray(xyColorVaoHandle[0]);
		
		// create a VBO for data
		gl.glGenBuffers(1, xyColorVboHandle, 0);
		gl.glBindBuffer(GL4.GL_ARRAY_BUFFER, xyColorVboHandle[0]);
		
		// use the VBO for (x,y) data
		gl.glVertexAttribPointer(0, 2, GL4.GL_FLOAT, false, 0, 0);
		gl.glEnableVertexAttribArray(0);
		
		// get handles for the uniforms
		xyColorMatrixHandle = gl.glGetUniformLocation(xyColorProgramHandle, "matrix");
		xyColorColorHandle = gl.glGetUniformLocation(xyColorProgramHandle, "rgba");
		
		// restore original state
		disableXyColorProgram(gl);
		
	}
	
	public static void enableXyColorProgram(GL2 gl, float[] matrix) {
		
		// save current state
		gl.glGetIntegerv(GL4.GL_CURRENT_PROGRAM, previousProgram, 0);
		gl.glGetIntegerv(GL4.GL_VERTEX_ARRAY_BINDING, previousVao, 0);
		gl.glGetIntegerv(GL4.GL_ARRAY_BUFFER_BINDING, previousVbo, 0);
		
		gl.glUseProgram(xyColorProgramHandle);
		gl.glBindVertexArray(xyColorVaoHandle[0]);
		gl.glBindBuffer(GL4.GL_ARRAY_BUFFER, xyColorVboHandle[0]);
		
		gl.glUniformMatrix4fv(xyColorMatrixHandle, 1, false, matrix, 0);
		
	}
	
	public static void disableXyColorProgram(GL2 gl) {
		
		gl.glUseProgram(previousProgram[0]);
		gl.glBindVertexArray(previousVao[0]);
		gl.glBindBuffer(GL4.GL_ARRAY_BUFFER, previousVbo[0]);
		
	}
	
	/*
	 * This program is for rendering 2D objects with per-vertex colors.
	 * One VBO of floats specifies (x1,y1,r1,g1,b1,a1,...) data.
	 * One uniform mat4 specifies the matrix.
	 */
	static int   xyrgbaProgramHandle = 0;
	static int   xyrgbaMatrixHandle = 0;
	static int[] xyrgbaVaoHandle = new int[1];
	static int[] xyrgbaVboHandle = new int[1];
	
	public static void makeXyrgbaProgram(GL2 gl) {
		
		// save current state
		gl.glGetIntegerv(GL4.GL_CURRENT_PROGRAM, previousProgram, 0);
		gl.glGetIntegerv(GL4.GL_VERTEX_ARRAY_BINDING, previousVao, 0);
		gl.glGetIntegerv(GL4.GL_ARRAY_BUFFER_BINDING, previousVbo, 0);
		
		// shaders
		String[] vertexShaderCode = new String[] {
			"#version 430\n",
			"layout (location = 0) in vec2 position;\n",
			"layout (location = 1) in vec4 color;\n",
			"out vec4 rgba;\n",
			"uniform mat4 matrix;\n",
			"void main(void) {\n",
			"	gl_Position = matrix * vec4(position.x, position.y, 0.0, 1.0);\n",
			"	rgba = color;\n",
			"}\n"
		};
		
		String[] fragmentShaderCode = new String[] {
			"#version 430\n",
			"in vec4 rgba;\n",
			"out vec4 color;\n",
			"void main(void) {\n",
			"	color = rgba;\n",
			"}\n"
		};
		
		// compile the vertex shader and check for errors
		int vertexShader = gl.glCreateShader(GL4.GL_VERTEX_SHADER);
		gl.glShaderSource(vertexShader, vertexShaderCode.length, vertexShaderCode, null, 0);
		gl.glCompileShader(vertexShader);
		
		int[] statusCode = new int[1];
		gl.glGetShaderiv(vertexShader, GL4.GL_COMPILE_STATUS, statusCode, 0);
		if(statusCode[0] != GL4.GL_TRUE) {
			int[] length = new int[1];
			gl.glGetShaderiv(vertexShader, GL4.GL_INFO_LOG_LENGTH, length, 0);
			byte[] errorMessage = new byte[length[0]];
			gl.glGetShaderInfoLog(vertexShader, length[0], length, 0, errorMessage, 0);
			NotificationsController.showFailureForSeconds("OpenGL Shader Error: " + new String(errorMessage), 999, false);
		}
		
		// compile the fragment shader and check for errors
		int fragmentShader = gl.glCreateShader(GL4.GL_FRAGMENT_SHADER);
		gl.glShaderSource(fragmentShader, fragmentShaderCode.length, fragmentShaderCode, null, 0);
		gl.glCompileShader(fragmentShader);
		
		gl.glGetShaderiv(fragmentShader, GL4.GL_COMPILE_STATUS, statusCode, 0);
		if(statusCode[0] != GL4.GL_TRUE) {
			int[] length = new int[1];
			gl.glGetShaderiv(fragmentShader, GL4.GL_INFO_LOG_LENGTH, length, 0);
			byte[] errorMessage = new byte[length[0]];
			gl.glGetShaderInfoLog(fragmentShader, length[0], length, 0, errorMessage, 0);
			NotificationsController.showFailureForSeconds("OpenGL Shader Error: " + new String(errorMessage), 999, false);
		}
		
		// link the shaders into a program and check for errors
		xyrgbaProgramHandle = gl.glCreateProgram();
		gl.glAttachShader(xyrgbaProgramHandle, vertexShader);
		gl.glAttachShader(xyrgbaProgramHandle, fragmentShader);
		gl.glLinkProgram(xyrgbaProgramHandle);
		
		gl.glGetShaderiv(xyrgbaProgramHandle, GL4.GL_LINK_STATUS, statusCode, 0);
		if(statusCode[0] != GL4.GL_TRUE) {
			int[] length = new int[1];
			gl.glGetShaderiv(xyrgbaProgramHandle, GL4.GL_INFO_LOG_LENGTH, length, 0);
			byte[] errorMessage = new byte[length[0]];
			gl.glGetShaderInfoLog(xyrgbaProgramHandle, length[0], length, 0, errorMessage, 0);
			NotificationsController.showFailureForSeconds("OpenGL Shader Error: " + new String(errorMessage), 999, false);
		}
		
		// free resources
		gl.glDeleteShader(vertexShader);
		gl.glDeleteShader(fragmentShader);

		// at least one VAO is required
		gl.glGenVertexArrays(1, xyrgbaVaoHandle, 0);
		gl.glBindVertexArray(xyrgbaVaoHandle[0]);
		
		// create a VBO for data
		gl.glGenBuffers(1, xyrgbaVboHandle, 0);
		gl.glBindBuffer(GL4.GL_ARRAY_BUFFER, xyrgbaVboHandle[0]);
		
		// use the VBO for (x,y,r,g,b,a) data
		gl.glVertexAttribPointer(0, 2, GL4.GL_FLOAT, false, 6*4, 0);
		gl.glEnableVertexAttribArray(0);
		gl.glVertexAttribPointer(1, 4, GL4.GL_FLOAT, false, 6*4, 2*4);
		gl.glEnableVertexAttribArray(1);
		
		// get handles for the uniforms
		xyrgbaMatrixHandle = gl.glGetUniformLocation(xyColorProgramHandle, "matrix");
		
		// restore original state
		disableXyrgbaProgram(gl);
		
	}
	
	public static void enableXyrgbaProgram(GL2 gl, float[] matrix) {
		
		// save current state
		gl.glGetIntegerv(GL4.GL_CURRENT_PROGRAM, previousProgram, 0);
		gl.glGetIntegerv(GL4.GL_VERTEX_ARRAY_BINDING, previousVao, 0);
		gl.glGetIntegerv(GL4.GL_ARRAY_BUFFER_BINDING, previousVbo, 0);
		
		gl.glUseProgram(xyrgbaProgramHandle);
		gl.glBindVertexArray(xyrgbaVaoHandle[0]);
		gl.glBindBuffer(GL4.GL_ARRAY_BUFFER, xyrgbaVboHandle[0]);
		
		gl.glUniformMatrix4fv(xyrgbaMatrixHandle, 1, false, matrix, 0);
		
	}
	
	public static void disableXyrgbaProgram(GL2 gl) {
		
		gl.glUseProgram(previousProgram[0]);
		gl.glBindVertexArray(previousVao[0]);
		gl.glBindBuffer(GL4.GL_ARRAY_BUFFER, previousVbo[0]);
		
	}

	/*
	 * This program is for rendering 3D objects.
	 * One VBO of floats specifies (x1,y1,z1,u1,v1,w1,...) data.
	 * One uniform mat4 specifies the matrix.
	 */
	static int   xyzuvwProgramHandle = 0;
	static int   xyzuvwMatrixHandle = 0;
	static int[] xyzuvwVaoHandle = new int[1];
	static int[] xyzuvwVboHandle = new int[1];
	
	public static void makeXyzuvwProgram(GL2 gl) {
		
		// save current state
		gl.glGetIntegerv(GL4.GL_CURRENT_PROGRAM, previousProgram, 0);
		gl.glGetIntegerv(GL4.GL_VERTEX_ARRAY_BINDING, previousVao, 0);
		gl.glGetIntegerv(GL4.GL_ARRAY_BUFFER_BINDING, previousVbo, 0);
		
		// shaders
		String[] vertexShaderCode = new String[] {
			"#version 430\n",
			"layout (location = 0) in vec3 position;\n",
			"layout (location = 1) in vec3 normal;\n",
			"out vec3 nor;\n",
			"uniform mat4 matrix;\n",
			"void main(void) {\n",
			"	gl_Position = matrix * vec4(position, 1.0);\n",
			"	nor = normal;\n",
			"}\n"
		};
		
		String[] fragmentShaderCode = new String[] {
			"#version 430\n",
			"in vec3 nor;\n",
			"out vec4 color;\n",
			"uniform mat4 matrix;\n",
			"void main(void) {\n",
			"	vec3 normal = normalize(nor);\n",
			"	vec3 redDirection   = vec3(-1, 0, 0);\n", // red light at the left edge
			"	vec3 greenDirection = vec3( 0, 0, 1);\n", // green light at the top edge
			"	vec3 blueDirection  = vec3( 1, 0, 0);\n", // blue light at the right edge
			"	float redDiffuse   = 0.8 * max(dot(normal, redDirection),   0.0);\n",
			"	float greenDiffuse = 0.8 * max(dot(normal, greenDirection), 0.0);\n",
			"	float blueDiffuse  = 0.8 * max(dot(normal, blueDirection),  0.0);\n",
			"	color = vec4(1.0 * redDiffuse, 1.0 * greenDiffuse, 1.0 * blueDiffuse, 1.0);\n",
			"}\n"
		};
		
		// compile the vertex shader and check for errors
		int vertexShader = gl.glCreateShader(GL4.GL_VERTEX_SHADER);
		gl.glShaderSource(vertexShader, vertexShaderCode.length, vertexShaderCode, null, 0);
		gl.glCompileShader(vertexShader);
		
		int[] statusCode = new int[1];
		gl.glGetShaderiv(vertexShader, GL4.GL_COMPILE_STATUS, statusCode, 0);
		if(statusCode[0] != GL4.GL_TRUE) {
			int[] length = new int[1];
			gl.glGetShaderiv(vertexShader, GL4.GL_INFO_LOG_LENGTH, length, 0);
			byte[] errorMessage = new byte[length[0]];
			gl.glGetShaderInfoLog(vertexShader, length[0], length, 0, errorMessage, 0);
			NotificationsController.showFailureForSeconds("OpenGL Shader Error: " + new String(errorMessage), 999, false);
		}
		
		// compile the fragment shader and check for errors
		int fragmentShader = gl.glCreateShader(GL4.GL_FRAGMENT_SHADER);
		gl.glShaderSource(fragmentShader, fragmentShaderCode.length, fragmentShaderCode, null, 0);
		gl.glCompileShader(fragmentShader);
		
		gl.glGetShaderiv(fragmentShader, GL4.GL_COMPILE_STATUS, statusCode, 0);
		if(statusCode[0] != GL4.GL_TRUE) {
			int[] length = new int[1];
			gl.glGetShaderiv(fragmentShader, GL4.GL_INFO_LOG_LENGTH, length, 0);
			byte[] errorMessage = new byte[length[0]];
			gl.glGetShaderInfoLog(fragmentShader, length[0], length, 0, errorMessage, 0);
			NotificationsController.showFailureForSeconds("OpenGL Shader Error: " + new String(errorMessage), 999, false);
		}
		
		// link the shaders into a program and check for errors
		xyzuvwProgramHandle = gl.glCreateProgram();
		gl.glAttachShader(xyzuvwProgramHandle, vertexShader);
		gl.glAttachShader(xyzuvwProgramHandle, fragmentShader);
		gl.glLinkProgram(xyzuvwProgramHandle);
		
		gl.glGetShaderiv(xyzuvwProgramHandle, GL4.GL_LINK_STATUS, statusCode, 0);
		if(statusCode[0] != GL4.GL_TRUE) {
			int[] length = new int[1];
			gl.glGetShaderiv(xyzuvwProgramHandle, GL4.GL_INFO_LOG_LENGTH, length, 0);
			byte[] errorMessage = new byte[length[0]];
			gl.glGetShaderInfoLog(xyzuvwProgramHandle, length[0], length, 0, errorMessage, 0);
			NotificationsController.showFailureForSeconds("OpenGL Shader Error: " + new String(errorMessage), 999, false);
		}
		
		// free resources
		gl.glDeleteShader(vertexShader);
		gl.glDeleteShader(fragmentShader);

		// at least one VAO is required
		gl.glGenVertexArrays(1, xyzuvwVaoHandle, 0);
		gl.glBindVertexArray(xyzuvwVaoHandle[0]);
		
		// create a VBO for data
		gl.glGenBuffers(1, xyzuvwVboHandle, 0);
		gl.glBindBuffer(GL4.GL_ARRAY_BUFFER, xyzuvwVboHandle[0]);
		
		// use the VBO for (x,y,z,u,v,w) data
		gl.glVertexAttribPointer(0, 3, GL4.GL_FLOAT, false, 6*4, 0);
		gl.glEnableVertexAttribArray(0);
		gl.glVertexAttribPointer(1, 3, GL4.GL_FLOAT, false, 6*4, 3*4);
		gl.glEnableVertexAttribArray(1);
		
		// get handles for the uniforms
		xyzuvwMatrixHandle = gl.glGetUniformLocation(xyzuvwProgramHandle, "matrix");
		
		// restore original state
		disableXyzuvwProgram(gl);
		
	}
	
	public static void enableXyzuvwProgram(GL2 gl, float[] matrix) {
		
		// save current state
		gl.glGetIntegerv(GL4.GL_CURRENT_PROGRAM, previousProgram, 0);
		gl.glGetIntegerv(GL4.GL_VERTEX_ARRAY_BINDING, previousVao, 0);
		gl.glGetIntegerv(GL4.GL_ARRAY_BUFFER_BINDING, previousVbo, 0);
		
		gl.glUseProgram(xyzuvwProgramHandle);
		gl.glBindVertexArray(xyzuvwVaoHandle[0]);
		gl.glBindBuffer(GL4.GL_ARRAY_BUFFER, xyzuvwVboHandle[0]);
		
		gl.glUniformMatrix4fv(xyzuvwMatrixHandle, 1, false, matrix, 0);
		
	}
	
	public static void disableXyzuvwProgram(GL2 gl) {
		
		gl.glUseProgram(previousProgram[0]);
		gl.glBindVertexArray(previousVao[0]);
		gl.glBindBuffer(GL4.GL_ARRAY_BUFFER, previousVbo[0]);
		
	}
	
}
