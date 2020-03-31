import java.awt.Color;
import java.awt.Font;
import java.text.SimpleDateFormat;
import javax.swing.JPanel;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.util.awt.TextRenderer;


/**
 * All GUI-related colors, element spacing, and font handling code is managed by this class.
 * Colors are specified in float[4]{r,g,b,a} format when used with OpenGL, or specified as Color objects when used with Fonts.
 * Element spacing is specified in true pixels (pre-multiplied by the display scaling factor.)
 */
public class Theme {

	// general swing
	public static Color jpanelColor = new JPanel().getBackground();
	public static int   padding     = Integer.parseInt(System.getProperty("java.version").split("\\.")[0]) >= 9 ? 5 : (int) (5 * Controller.getDisplayScalingFactor());
	
	// general opengl
	public static float lineWidth = 1.0f;
	public static float pointSize = 3.0f;
	
	// charts region
	public static float[] tileColor               = new float[] {0.8f, 0.8f, 0.8f, 1.0f};
	public static float[] tileShadowColor         = new float[] {0.7f, 0.7f, 0.7f, 1.0f};
	public static float[] tileSelectedColor       = new float[] {0.5f, 0.5f, 0.5f, 1.0f};
	public static float   tilePadding             = 5.0f;
	public static float   tileShadowOffset        = tilePadding / 2;
	public static float[] neutralColor            = new float[] {jpanelColor.getRed() / 255.0f, jpanelColor.getGreen() / 255.0f, jpanelColor.getBlue() / 255.0f, 1.0f};
	public static float[] transparentNeutralColor = new float[] {neutralColor[0], neutralColor[1], neutralColor[2], 0.7f};
	
	// plot region
	public static float[] plotOutlineColor        = new float[] {0.0f, 0.0f, 0.0f, 1.0f};
	public static float[] plotBackgroundColor     = neutralColor;
	public static float[] divisionLinesColor      = new float[] {0.7f, 0.7f, 0.7f, 1.0f};
	public static float[] divisionLinesFadedColor = new float[] {0.7f, 0.7f, 0.7f, 0.0f};
	
	// tooltips in the plot region
	public static float[] tooltipBackgroundColor  = new float[] {1, 1, 1, 1};
	public static float[] tooltipBorderColor      = new float[] {0, 0, 0, 1};
	public static float[] tooltipVerticalBarColor = new float[] {0.0f, 0.0f, 0.0f, 1.0f};
	public static float   tooltipTextPadding      = 5.0f;
	
	// tick marks surrounding the plot region
	public static float[]      tickLinesColor   = new float[] {0.0f, 0.0f, 0.0f, 1.0f};
	public static float        tickLength       = 6.0f;
	public static float        tickTextPadding  = 3.0f;
	public static Font         tickFont         = new Font("Geneva", Font.PLAIN, 12);
	public static Color        tickFontColor    = Color.BLACK;
	public static TextRenderer tickTextRenderer = null;
	public static float        tickTextHeight   = 0;
	public static float        tickTextWidth(String text) { return (float) tickFont.getStringBounds(text, tickTextRenderer.getFontRenderContext()).getWidth(); }
	
	// legend
	public static float[]      legendBackgroundColor = tileShadowColor;
	public static float        legendTextPadding     = 5.0f;
	public static float        legendNamesPadding    = 25.0f;
	public static Font         legendFont            = new Font("Geneva", Font.BOLD, 14);
	public static Color        legendFontColor       = Color.BLACK;
	public static TextRenderer legendTextRenderer    = null;
	public static float        legendTextHeight      = 0;
	public static float        legendTextWidth(String text) { return (float) legendFont.getStringBounds(text, legendTextRenderer.getFontRenderContext()).getWidth(); }

	// x-axis title
	public static Font         xAxisFont         = new Font("Geneva", Font.BOLD, 18);
	public static Color        xAxisFontColor    = Color.BLACK;
	public static TextRenderer xAxisTextRenderer = null;
	public static float        xAxisTextHeight   = 0;
	public static float        xAxisTextWidth(String text) { return (float) xAxisFont.getStringBounds(text, xAxisTextRenderer.getFontRenderContext()).getWidth(); }
	
	// y-axis title
	public static Font         yAxisFont         = new Font("Geneva", Font.BOLD, 18);
	public static Color        yAxisFontColor    = Color.BLACK;
	public static TextRenderer yAxisTextRenderer = null;
	public static float        yAxisTextHeight   = 0;
	public static float        yAxisTextWidth(String text) { return (float) yAxisFont.getStringBounds(text, yAxisTextRenderer.getFontRenderContext()).getWidth(); }
	
	// display format for timestamps
	public static SimpleDateFormat timestampFormatter        = new SimpleDateFormat("yyyy-MM-dd\nhh:mm:ss.SSS a");
	public static SimpleDateFormat tooltipTimestampFormatter = new SimpleDateFormat("yyyy-MM-dd\nhh:mm:ss.SSS a");
	
	// canvas and chart details used during text rendering
	private static int chartXoffset = 0;
	private static int chartYoffset = 0;
	private static int width  = 0;
	private static int height = 0;
	private static int framesSinceFlush = 0;
	
	/**
	 * This method must be called when the OpenGL context is initialized,
	 * and any time the display scaling factor changes.
	 * 
	 * @param displayScalingFactor    The display scaling factor.
	 */
	public static void initialize(float displayScalingFactor) {
			
		lineWidth          = 1.0f * displayScalingFactor;
		pointSize          = 3.0f * displayScalingFactor;
		
		tilePadding        = 5.0f * displayScalingFactor;
		tileShadowOffset   = tilePadding / 2;
		
		tooltipTextPadding = 5.0f * displayScalingFactor;
		
		tickLength         = 6.0f * displayScalingFactor;
		tickTextPadding    = 3.0f * displayScalingFactor;
		tickFont           = new Font("Geneva", Font.PLAIN, (int) (12.0 * displayScalingFactor));
		tickTextRenderer   = new TextRenderer(tickFont, true, true);
		tickTextHeight     = tickFont.createGlyphVector(tickTextRenderer.getFontRenderContext(), "Test").getPixelBounds(tickTextRenderer.getFontRenderContext(), 0, 0).height;
		
		legendTextPadding  = 5.0f * displayScalingFactor;
		legendNamesPadding = 25.0f * displayScalingFactor;
		legendFont         = new Font("Geneva", Font.BOLD, (int) (14.0 * displayScalingFactor));
		legendTextRenderer = new TextRenderer(legendFont, true, true);
		legendTextHeight   = legendFont.createGlyphVector(legendTextRenderer.getFontRenderContext(), "Test").getPixelBounds(legendTextRenderer.getFontRenderContext(), 0, 0).height;
		
		xAxisFont          = new Font("Geneva", Font.BOLD, (int) (18.0 * displayScalingFactor));
		xAxisTextRenderer  = new TextRenderer(xAxisFont, true, true);
		xAxisTextHeight    = xAxisFont.createGlyphVector(xAxisTextRenderer.getFontRenderContext(), "Test").getPixelBounds(xAxisTextRenderer.getFontRenderContext(), 0, 0).height;
		
		yAxisFont          = new Font("Geneva", Font.BOLD, (int) (18.0 * displayScalingFactor));
		yAxisTextRenderer  = new TextRenderer(yAxisFont, true, true);
		yAxisTextHeight    = yAxisFont.createGlyphVector(yAxisTextRenderer.getFontRenderContext(), "Test").getPixelBounds(yAxisTextRenderer.getFontRenderContext(), 0, 0).height;
		
	}
	
	/**
	 * This method should be called once per frame.
	 * Works around a memory leak in TextRenderer by replacing them periodically.
	 */
	public static void fixMemoryLeak() {
		framesSinceFlush++;
		if(framesSinceFlush >= 18000) { // 5 minutes of 60hz
			tickTextRenderer   = new TextRenderer(Theme.tickFont, true, true);
			legendTextRenderer = new TextRenderer(Theme.legendFont, true, true);
			xAxisTextRenderer  = new TextRenderer(Theme.xAxisFont, true, true);
			yAxisTextRenderer  = new TextRenderer(Theme.yAxisFont, true, true);
			framesSinceFlush = 0;
		}
	}
	
	/**
	 * Saves the location of the chart's lower-left corner in the GLcanvas, and the size of the canvas. This needs to be called before using any of the draw*text() methods.
	 * 
	 * @param xOffset         The x location of the chart's lower-left corner, in pixels.
	 * @param yOffset         The y location of the chart's lower-left corner, in pixels.
	 * @param canvasWidth     The canvas width, in pixels.
	 * @param canvasHeight    The canvas height, in pixels.
	 */
	public static void setChartOffset(int xOffset, int yOffset, int canvasWidth, int canvasHeight) {
		chartXoffset = xOffset;
		chartYoffset = yOffset;
		width = canvasWidth;
		height = canvasHeight;
	}
	
	/**
	 * Draws horizontal text, using the tick font and color.
	 * 
	 * @param text    The text to draw.
	 * @param x       The x location, in pixels, relative to the chart's lower-left corner.
	 * @param y       The y location, in pixels, relative to the chart's lower-left corner.
	 */
	public static void drawTickText(String text, int x, int y) {
		tickTextRenderer.beginRendering(width, height);
		tickTextRenderer.setColor(tickFontColor);
		tickTextRenderer.draw(text, x + chartXoffset, y + chartYoffset);
		tickTextRenderer.endRendering();
	}
	
	/**
	 * Draws horizontal text, using the legend font and color.
	 * 
	 * @param text    The text to draw.
	 * @param x       The x location, in pixels, relative to the chart's lower-left corner.
	 * @param y       The y location, in pixels, relative to the chart's lower-left corner.
	 */
	public static void drawLegendText(String text, int x, int y) {
		legendTextRenderer.beginRendering(width, height);
		legendTextRenderer.setColor(legendFontColor);
		legendTextRenderer.draw(text, x + chartXoffset, y + chartYoffset);
		legendTextRenderer.endRendering();
	}
	
	/**
	 * Draws horizontal text, using the x-axis title font and color.
	 * 
	 * @param text    The text to draw.
	 * @param x       The x location, in pixels, relative to the chart's lower-left corner.
	 * @param y       The y location, in pixels, relative to the chart's lower-left corner.
	 */
	public static void drawXaxisText(String text, int x, int y) {
		xAxisTextRenderer.beginRendering(width, height);
		xAxisTextRenderer.setColor(xAxisFontColor);
		xAxisTextRenderer.draw(text, x + chartXoffset, y + chartYoffset);
		xAxisTextRenderer.endRendering();
	}
	
	/**
	 * Draws rotated text, using the y-axis title font and color.
	 * 
	 * @param gl         The OpenGL context.
	 * @param text       The text to draw.
	 * @param x          The x location, in pixels, relative to the chart's lower-left corner.
	 * @param y          The y location, in pixels, relative to the chart's lower-left corner.
	 * @param degrees    The rotation angle.
	 */
	public static void drawYaxisText(GL2 gl, String text, int x, int y, float degrees) {
		yAxisTextRenderer.beginRendering(width, height);
		gl.glMatrixMode(GL2.GL_MODELVIEW);
		gl.glPushMatrix();
		gl.glTranslatef(x + chartXoffset, y + chartYoffset, 0);
		gl.glRotatef(degrees, 0.0f, 0.0f, 1.0f);
		yAxisTextRenderer.setColor(yAxisFontColor);
		yAxisTextRenderer.draw(text, 0, 0);
		yAxisTextRenderer.endRendering();
		yAxisTextRenderer.flush();
		gl.glPopMatrix();
	}

}
