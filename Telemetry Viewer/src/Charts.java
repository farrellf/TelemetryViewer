import java.awt.Color;
import java.awt.Font;
import java.awt.font.FontRenderContext;

import com.jogamp.opengl.util.awt.TextRenderer;

public class Charts {
	
	// outside of the chart region (behind the axis titles, etc.)
	public static final float[] backgroundColor = new float[] {1.0f, 1.0f, 1.0f};
	
	// inside the chart region (behind the plotted lines/bars/etc.)
	public static final float[] plotBackgroundColor = new float[] {230.0f/255.0f, 230.0f/255.0f, 230.0f/255.0f};
	
	// x and y divisions drawn inside the chart region
	public static final float[] divisionLinesColor = new float[] {180.0f/255.0f, 180.0f/255.0f, 180.0f/255.0f};
	
	// tick marks between the division lines and their corresponding text labels
	public static final float[] tickLinesColor = new float[] {0.0f, 0.0f, 0.0f};
	
	public static final float strokeWidth       = 1.0f * Controller.getDisplayScalingFactor();
	public static final float perimeterPadding  = 5.0f * Controller.getDisplayScalingFactor();
	public static final float tickLength        = 6.0f * Controller.getDisplayScalingFactor();
	public static final float tickTextPadding   = 3.0f * Controller.getDisplayScalingFactor();
	public static final float legendTextPadding = 5.0f * Controller.getDisplayScalingFactor();
	
	// text labels of each division line
	public static final Font              tickFont         = new Font("Geneva", Font.PLAIN, (int) (12.0 * Controller.getDisplayScalingFactor()));
	public static final Color             tickFontColor    = Color.BLACK;
	public static final TextRenderer      tickFontRenderer = new TextRenderer(tickFont, true, true);
	public static final FontRenderContext tickFontFRC      = tickFontRenderer.getFontRenderContext(); 
	public static final float             tickFontHeight   = tickFont.createGlyphVector(tickFontFRC, "Test").getPixelBounds(tickFontFRC, 0, 0).height;
	public static       float             tickFontWidth(String text) { return (float) tickFont.getStringBounds(text, tickFontFRC).getWidth(); }
	
	// text labels in the legend
	public static final Font              legendFont         = new Font("Geneva", Font.BOLD, (int) (14.0 * Controller.getDisplayScalingFactor()));
	public static final Color             legendFontColor    = Color.BLACK;
	public static final TextRenderer      legendFontRenderer = new TextRenderer(legendFont, true, true);
	public static final FontRenderContext legendFontFRC      = legendFontRenderer.getFontRenderContext(); 
	public static final float             legendFontHeight   = legendFont.createGlyphVector(legendFontFRC, "Test").getPixelBounds(legendFontFRC, 0, 0).height;
	public static       float             legendFontWidth(String text) { return (float) legendFont.getStringBounds(text, legendFontFRC).getWidth(); }
	
	// x-axis title
	public static final Font              xAxisFont         = new Font("Geneva", Font.BOLD, (int) (20.0 * Controller.getDisplayScalingFactor()));
	public static final Color             xAxisFontColor    = Color.BLACK;
	public static final TextRenderer      xAxisFontRenderer = new TextRenderer(xAxisFont, true, true);
	public static final FontRenderContext xAxisFontFRC      = xAxisFontRenderer.getFontRenderContext(); 
	public static final float             xAxisFontHeight   = xAxisFont.createGlyphVector(xAxisFontFRC, "Test").getPixelBounds(xAxisFontFRC, 0, 0).height;
	public static       float             xAxisFontWidth(String text) { return (float) xAxisFont.getStringBounds(text, xAxisFontFRC).getWidth(); }
	
	// y-axis title
	public static final Font              yAxisFont         = new Font("Geneva", Font.BOLD, (int) (20.0 * Controller.getDisplayScalingFactor()));
	public static final Color             yAxisFontColor    = Color.BLACK;
	public static final TextRenderer      yAxisFontRenderer = new TextRenderer(yAxisFont, true, true);
	public static final FontRenderContext yAxisFontFRC      = yAxisFontRenderer.getFontRenderContext(); 
	public static final float             yAxisFontHeight   = yAxisFont.createGlyphVector(yAxisFontFRC, "Test").getPixelBounds(yAxisFontFRC, 0, 0).height;
	public static       float             yAxisFontWidth(String text) { return (float) yAxisFont.getStringBounds(text, yAxisFontFRC).getWidth(); }
	
	public static final int sliceWidth = 32;

}
