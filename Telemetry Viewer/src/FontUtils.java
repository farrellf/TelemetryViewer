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
	
	private static final Queue<PositionedText> tickTextQueue    = new LinkedList<PositionedText>();
	private static final TextRenderer          tickTextRenderer = new TextRenderer(Theme.tickFont, true, true);
	private static final FontRenderContext     tickTextFRC      = tickTextRenderer.getFontRenderContext(); 
	public  static final float                 tickTextHeight   = Theme.tickFont.createGlyphVector(tickTextFRC, "Test").getPixelBounds(tickTextFRC, 0, 0).height;
	public  static       float                 tickTextWidth(String text) { return (float) Theme.tickFont.getStringBounds(text, tickTextFRC).getWidth(); }
	
	private static final Queue<PositionedText> legendTextQueue    = new LinkedList<PositionedText>();
	private static final TextRenderer          legendTextRenderer = new TextRenderer(Theme.legendFont, true, true);
	private static final FontRenderContext     legendTextFRC      = legendTextRenderer.getFontRenderContext(); 
	public  static final float                 legendTextHeight   = Theme.legendFont.createGlyphVector(legendTextFRC, "Test").getPixelBounds(legendTextFRC, 0, 0).height;
	public  static       float                 legendTextWidth(String text) { return (float) Theme.legendFont.getStringBounds(text, legendTextFRC).getWidth(); }
	
	private static final Queue<PositionedText> xAxisTextQueue    = new LinkedList<PositionedText>();
	private static final TextRenderer          xAxisTextRenderer = new TextRenderer(Theme.xAxisFont, true, true);
	private static final FontRenderContext     xAxisTextFRC      = xAxisTextRenderer.getFontRenderContext(); 
	public  static final float                 xAxisTextHeight   = Theme.xAxisFont.createGlyphVector(xAxisTextFRC, "Test").getPixelBounds(xAxisTextFRC, 0, 0).height;
	public  static       float                 xAxisTextWidth(String text) { return (float) Theme.xAxisFont.getStringBounds(text, xAxisTextFRC).getWidth(); }
	
	private static final Queue<PositionedText> yAxisTextQueue    = new LinkedList<PositionedText>();
	private static final TextRenderer          yAxisTextRenderer = new TextRenderer(Theme.yAxisFont, true, true);
	private static final FontRenderContext     yAxisTextFRC      = yAxisTextRenderer.getFontRenderContext(); 
	public  static final float                 yAxisTextHeight   = Theme.yAxisFont.createGlyphVector(yAxisTextFRC, "Test").getPixelBounds(yAxisTextFRC, 0, 0).height;
	public  static       float                 yAxisTextWidth(String text) { return (float) Theme.yAxisFont.getStringBounds(text, yAxisTextFRC).getWidth(); }
	
	/**
	 * Saves the location of the chart's lower-left corner in the GLcanvas. This needs to be called before using any of the draw*text() methods.
	 * 
	 * @param xOffset    The x location of the lower-left corner, in pixels.
	 * @param yOffset    The y location of the lower-left corner, in pixels.
	 */
	static void setOffsets(int xOffset, int yOffset) {
		FontUtils.xOffset = xOffset;
		FontUtils.yOffset = yOffset;
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
	
	static void drawQueuedText(GL2 gl, int width, int height) {
		
		tickTextRenderer.beginRendering(width, height);
		tickTextRenderer.setColor(Theme.tickFontColor);
		while(!tickTextQueue.isEmpty()) {
			PositionedText pt = tickTextQueue.remove();
			tickTextRenderer.draw(pt.text, pt.x, pt.y);
		}
		tickTextRenderer.endRendering();
		
		legendTextRenderer.beginRendering(width, height);
		legendTextRenderer.setColor(Theme.legendFontColor);
		while(!legendTextQueue.isEmpty()) {
			PositionedText pt = legendTextQueue.remove();
			legendTextRenderer.draw(pt.text, pt.x, pt.y);
		}
		legendTextRenderer.endRendering();
		
		xAxisTextRenderer.beginRendering(width, height);
		xAxisTextRenderer.setColor(Theme.xAxisFontColor);
		while(!xAxisTextQueue.isEmpty()) {
			PositionedText pt = xAxisTextQueue.remove();
			xAxisTextRenderer.draw(pt.text, pt.x, pt.y);
		}
		xAxisTextRenderer.endRendering();
		
		while(!yAxisTextQueue.isEmpty()) {
			PositionedText pt = yAxisTextQueue.remove();
			yAxisTextRenderer.beginRendering(width, height);
			gl.glMatrixMode(GL2.GL_MODELVIEW);
			gl.glPushMatrix();
			gl.glTranslatef(pt.x, pt.y, 0);
			gl.glRotatef(pt.degrees, 0.0f, 0.0f, 1.0f);
			yAxisTextRenderer.setColor(Theme.yAxisFontColor);
			yAxisTextRenderer.draw(pt.text, 0, 0);
			yAxisTextRenderer.endRendering();
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
