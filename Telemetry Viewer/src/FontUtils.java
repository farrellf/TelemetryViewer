import java.awt.font.FontRenderContext;
import java.util.LinkedList;
import java.util.Queue;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.util.awt.TextRenderer;

/**
 * A helper class for font rendering. Example usage:
 * 
 * 1. Call FontUtils.setOffsets() to set the bottom-left corner of where the chart is located in the GLcanvas.
 * 2. The chart can then queue font rendering with calls to the FontUtils.draw*text() methods.
 * 3. After the chart has been drawn with OpenGL, call FontUtils.drawQueuedText() to render text on top of the chart.
 * 
 * The *TextHeight fields and *TextWidth() methods can be used to get the size of text in pixels.
 */
public class FontUtils {
	
	private static int xOffset = 0;
	private static int yOffset = 0;
	private static int canvasWidth = 0;
	private static int canvasHeight = 0;
	private static boolean displayScalingChanged = false;
	
	private static final Queue<PositionedText> tickTextQueue    = new LinkedList<PositionedText>();
	private static       TextRenderer          tickTextRenderer = new TextRenderer(Theme.tickFont, true, true);
	private static       FontRenderContext     tickTextFRC      = tickTextRenderer.getFontRenderContext();
	public  static       float                 tickTextHeight   = Theme.tickFont.createGlyphVector(tickTextFRC, "Test").getPixelBounds(tickTextFRC, 0, 0).height;
	public  static       float                 tickTextWidth(String text) { return (float) Theme.tickFont.getStringBounds(text, tickTextFRC).getWidth(); }
	
	private static final Queue<PositionedText> legendTextQueue    = new LinkedList<PositionedText>();
	private static       TextRenderer          legendTextRenderer = new TextRenderer(Theme.legendFont, true, true);
	private static       FontRenderContext     legendTextFRC      = legendTextRenderer.getFontRenderContext();
	public  static       float                 legendTextHeight   = Theme.legendFont.createGlyphVector(legendTextFRC, "Test").getPixelBounds(legendTextFRC, 0, 0).height;
	public  static       float                 legendTextWidth(String text) { return (float) Theme.legendFont.getStringBounds(text, legendTextFRC).getWidth(); }
	
	private static final Queue<PositionedText> xAxisTextQueue    = new LinkedList<PositionedText>();
	private static       TextRenderer          xAxisTextRenderer = new TextRenderer(Theme.xAxisFont, true, true);
	private static       FontRenderContext     xAxisTextFRC      = xAxisTextRenderer.getFontRenderContext();
	public  static       float                 xAxisTextHeight   = Theme.xAxisFont.createGlyphVector(xAxisTextFRC, "Test").getPixelBounds(xAxisTextFRC, 0, 0).height;
	public  static       float                 xAxisTextWidth(String text) { return (float) Theme.xAxisFont.getStringBounds(text, xAxisTextFRC).getWidth(); }
	
	private static final Queue<PositionedText> yAxisTextQueue    = new LinkedList<PositionedText>();
	private static       TextRenderer          yAxisTextRenderer = new TextRenderer(Theme.yAxisFont, true, true);
	private static       FontRenderContext     yAxisTextFRC      = yAxisTextRenderer.getFontRenderContext();
	public  static       float                 yAxisTextHeight   = Theme.yAxisFont.createGlyphVector(yAxisTextFRC, "Test").getPixelBounds(yAxisTextFRC, 0, 0).height;
	public  static       float                 yAxisTextWidth(String text) { return (float) Theme.yAxisFont.getStringBounds(text, yAxisTextFRC).getWidth(); }
	
	/**
	 * Called by the Controller when the display scaling factor changes.
	 * 
	 * @param newFactor    The new display scaling factor.
	 */
	public static void displayingScalingFactorChanged(float newFactor) {
		
		displayScalingChanged = true;
		
	}
	
	/**
	 * Saves the location of the chart's lower-left corner in the GLcanvas, and the size of the canvas. This needs to be called before using any of the draw*text() methods.
	 * 
	 * @param xOffset    The x location of the lower-left corner, in pixels.
	 * @param yOffset    The y location of the lower-left corner, in pixels.
	 * @param width      The canvas width, in pixels.
	 * @param height     The canvas height, in pixels.
	 */
	static void setOffsets(int xOffset, int yOffset, int width, int height) {
		FontUtils.xOffset = xOffset;
		FontUtils.yOffset = yOffset;
		FontUtils.canvasWidth = width;
		FontUtils.canvasHeight = height;
	}
	
	static void drawMarkerText(String text, int x, int y) {
		tickTextRenderer.beginRendering(canvasWidth, canvasHeight);
		tickTextRenderer.setColor(Theme.tickFontColor);
		tickTextRenderer.draw(text, x + xOffset, y + yOffset);
		tickTextRenderer.endRendering();
	}
		
	static void drawTickText(String text, int x, int y) {
		tickTextQueue.add(new PositionedText(text, x + xOffset, y + yOffset, 0));
	}
	
	static void drawLegendText(String text, int x, int y) {
		legendTextQueue.add(new PositionedText(text, x + xOffset, y + yOffset, 0));
	}
	
	static void drawXaxisText(String text, int x, int y) {
		xAxisTextQueue.add(new PositionedText(text, x + xOffset, y + yOffset, 0));
	}
	
	static void drawYaxisText(String text, int x, int y, float degrees) {
		yAxisTextQueue.add(new PositionedText(text, x + xOffset, y + yOffset, degrees));
	}
	
	static void drawQueuedText(GL2 gl) {
		
		if(displayScalingChanged) {
			
			tickTextRenderer   = new TextRenderer(Theme.tickFont, true, true);
			tickTextFRC        = tickTextRenderer.getFontRenderContext();
			tickTextHeight     = Theme.tickFont.createGlyphVector(tickTextFRC, "Test").getPixelBounds(tickTextFRC, 0, 0).height;
			
			legendTextRenderer = new TextRenderer(Theme.legendFont, true, true);
			legendTextFRC      = legendTextRenderer.getFontRenderContext();
			legendTextHeight   = Theme.legendFont.createGlyphVector(legendTextFRC, "Test").getPixelBounds(legendTextFRC, 0, 0).height;
			
			xAxisTextRenderer  = new TextRenderer(Theme.xAxisFont, true, true);
			xAxisTextFRC       = xAxisTextRenderer.getFontRenderContext(); 
			xAxisTextHeight    = Theme.xAxisFont.createGlyphVector(xAxisTextFRC, "Test").getPixelBounds(xAxisTextFRC, 0, 0).height;
			
			yAxisTextRenderer  = new TextRenderer(Theme.yAxisFont, true, true);
			yAxisTextFRC       = yAxisTextRenderer.getFontRenderContext();
			yAxisTextHeight    = Theme.yAxisFont.createGlyphVector(yAxisTextFRC, "Test").getPixelBounds(yAxisTextFRC, 0, 0).height;
			
			displayScalingChanged = false;
			
		}
		
		tickTextRenderer.beginRendering(canvasWidth, canvasHeight);
		tickTextRenderer.setColor(Theme.tickFontColor);
		while(!tickTextQueue.isEmpty()) {
			PositionedText pt = tickTextQueue.remove();
			tickTextRenderer.draw(pt.text, pt.x, pt.y);
		}
		tickTextRenderer.endRendering();
		
		legendTextRenderer.beginRendering(canvasWidth, canvasHeight);
		legendTextRenderer.setColor(Theme.legendFontColor);
		while(!legendTextQueue.isEmpty()) {
			PositionedText pt = legendTextQueue.remove();
			legendTextRenderer.draw(pt.text, pt.x, pt.y);
		}
		legendTextRenderer.endRendering();
		
		xAxisTextRenderer.beginRendering(canvasWidth, canvasHeight);
		xAxisTextRenderer.setColor(Theme.xAxisFontColor);
		while(!xAxisTextQueue.isEmpty()) {
			PositionedText pt = xAxisTextQueue.remove();
			xAxisTextRenderer.draw(pt.text, pt.x, pt.y);
		}
		xAxisTextRenderer.endRendering();
		
		while(!yAxisTextQueue.isEmpty()) {
			PositionedText pt = yAxisTextQueue.remove();
			yAxisTextRenderer.beginRendering(canvasWidth, canvasHeight);
			gl.glMatrixMode(GL2.GL_MODELVIEW);
			gl.glPushMatrix();
			gl.glTranslatef(pt.x, pt.y, 0);
			gl.glRotatef(pt.degrees, 0.0f, 0.0f, 1.0f);
			yAxisTextRenderer.setColor(Theme.yAxisFontColor);
			yAxisTextRenderer.draw(pt.text, 0, 0);
			yAxisTextRenderer.endRendering();
			yAxisTextRenderer.flush();
			gl.glPopMatrix();
		}
			
	}

	private static class PositionedText {
		String text;
		int x;
		int y;
		float degrees;
		public PositionedText(String text, int x, int y, float degrees) {
			this.text = text;
			this.x = x;
			this.y = y;
			this.degrees = degrees;
		}
	}

}
