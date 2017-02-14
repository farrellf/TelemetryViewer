import java.awt.Color;
import java.awt.Font;

import javax.swing.JPanel;

public class Theme {
	
	// charts region
	public static float[] tileColor               = new float[] {0.8f, 0.8f, 0.8f, 1.0f};
	public static float[] tileShadowColor         = new float[] {0.7f, 0.7f, 0.7f, 1.0f};
	public static float[] tileSelectedColor       = new float[] {0.5f, 0.5f, 0.5f, 1.0f};
	public static float   tilePadding             = 5.0f * Controller.getDisplayScalingFactor();
	public static float   tileShadowOffset        = tilePadding / 2;
	public static float[] neutralColor            = new float[] {new JPanel().getBackground().getRed() / 255.0f, new JPanel().getBackground().getGreen() / 255.0f, new JPanel().getBackground().getBlue() / 255.0f, 1.0f};
	public static float[] transparentNeutralColor = new float[] {neutralColor[0], neutralColor[1], neutralColor[2], 0.7f};
	
	// general
	public static float lineWidth                 = 1.0f * Controller.getDisplayScalingFactor();
	public static float pointSize                 = 3.0f * Controller.getDisplayScalingFactor();
	public static float[] perimeterOutlineColor   = new float[] {0.0f, 0.0f, 0.0f, 1.0f};
	public static float[] backgroundColor         = new float[] {1.0f, 1.0f, 1.0f, 1.0f};
	
	// plot
	public static float[] plotOutlineColor        = new float[] {0.0f, 0.0f, 0.0f, 1.0f};
	public static float[] plotBackgroundColor     = new float[] {230.0f/255.0f, 230.0f/255.0f, 230.0f/255.0f, 1.0f};
	public static float[] divisionLinesColor      = new float[] {180.0f/255.0f, 180.0f/255.0f, 180.0f/255.0f, 1.0f};
	public static float[] divisionLinesFadedColor = new float[] {180.0f/255.0f, 180.0f/255.0f, 180.0f/255.0f, 0.0f};
	
	// tick marks
	public static float[] tickLinesColor          = new float[] {0.0f, 0.0f, 0.0f, 1.0f};
	public static float   tickLength              = 6.0f * Controller.getDisplayScalingFactor();
	public static float   tickTextPadding         = 3.0f * Controller.getDisplayScalingFactor();
	public static Font    tickFont                = new Font("Geneva", Font.PLAIN, (int) (12.0 * Controller.getDisplayScalingFactor()));
	public static Color   tickFontColor           = Color.BLACK;
	
	// legend
	public static float[] legendOutlineColor      = new float[] {0.0f, 0.0f, 0.0f, 1.0f};
	public static float   legendTextPadding       = 5.0f * Controller.getDisplayScalingFactor();
	public static float   legendNamesPadding      = 25.0f * Controller.getDisplayScalingFactor();
	public static Font    legendFont              = new Font("Geneva", Font.BOLD, (int) (14.0 * Controller.getDisplayScalingFactor()));
	public static Color   legendFontColor         = Color.BLACK;

	// x-axis title
	public static Font  xAxisFont                 = new Font("Geneva", Font.BOLD, (int) (18.0 * Controller.getDisplayScalingFactor()));
	public static Color xAxisFontColor            = Color.BLACK;
	
	// y-axis title
	public static Font  yAxisFont                 = new Font("Geneva", Font.BOLD, (int) (18.0 * Controller.getDisplayScalingFactor()));
	public static Color yAxisFontColor            = Color.BLACK;
	
	/**
	 * Called by the Controller when the display scaling factor changes.
	 * 
	 * @param newFactor    The new display scaling factor.
	 */
	public static void displayingScalingFactorChanged(float newFactor) {
		
		lineWidth          = 1.0f * newFactor;
		pointSize          = 3.0f * newFactor;
		tilePadding        = 5.0f * newFactor;
		tileShadowOffset   = tilePadding / 2;
		tickLength         = 6.0f * newFactor;
		tickTextPadding    = 3.0f * newFactor;
		tickFont           = new Font("Geneva", Font.PLAIN, (int) (12.0 * newFactor));
		legendTextPadding  = 5.0f * newFactor;
		legendNamesPadding = 25.0f * newFactor;
		legendFont         = new Font("Geneva", Font.BOLD, (int) (14.0 * newFactor));
		xAxisFont          = new Font("Geneva", Font.BOLD, (int) (18.0 * newFactor));
		yAxisFont          = new Font("Geneva", Font.BOLD, (int) (18.0 * newFactor));
		
	}

}
