import java.awt.Color;
import java.awt.Font;

public class Theme {
	
	// charts region
	public static final float[] gridBackgroundColor     = new float[] {1.0f, 1.0f, 1.0f, 1.0f};
	public static final float[] gridLinesColor          = new float[] {0.0f, 0.0f, 0.0f, 1.0f};
	public static final float[] gridBoxColor            = new float[] {0.5f, 0.5f, 0.5f, 1.0f};
	
	// general
	public static final float strokeWidth               = 1.0f * Controller.getDisplayScalingFactor();
	public static final float perimeterPadding          = 5.0f * Controller.getDisplayScalingFactor();
	public static final float[] perimeterOutlineColor   = new float[] {0.0f, 0.0f, 0.0f, 1.0f};
	public static final float[] backgroundColor         = new float[] {1.0f, 1.0f, 1.0f, 1.0f};
	
	// plot
	public static final float[] plotOutlineColor        = new float[] {0.0f, 0.0f, 0.0f, 1.0f};
	public static final float[] plotBackgroundColor     = new float[] {230.0f/255.0f, 230.0f/255.0f, 230.0f/255.0f, 1.0f};
	public static final float[] divisionLinesColor      = new float[] {180.0f/255.0f, 180.0f/255.0f, 180.0f/255.0f, 1.0f};
	public static final float[] divisionLinesFadedColor = new float[] {180.0f/255.0f, 180.0f/255.0f, 180.0f/255.0f, 0.0f};
	
	// tick marks
	public static final float[] tickLinesColor          = new float[] {0.0f, 0.0f, 0.0f, 1.0f};
	public static final float   tickLength              = 6.0f * Controller.getDisplayScalingFactor();
	public static final float   tickTextPadding         = 3.0f * Controller.getDisplayScalingFactor();
	public static final Font    tickFont                = new Font("Geneva", Font.PLAIN, (int) (12.0 * Controller.getDisplayScalingFactor()));
	public static final Color   tickFontColor           = Color.BLACK;
	
	// legend
	public static final float[] legendOutlineColor      = new float[] {0.0f, 0.0f, 0.0f, 1.0f};
	public static final float   legendTextPadding       = 5.0f * Controller.getDisplayScalingFactor();
	public static final float   legendNamesPadding      = 25.0f * Controller.getDisplayScalingFactor();
	public static final Font    legendFont              = new Font("Geneva", Font.BOLD, (int) (14.0 * Controller.getDisplayScalingFactor()));
	public static final Color   legendFontColor         = Color.BLACK;

	// x-axis title
	public static final Font  xAxisFont                 = new Font("Geneva", Font.BOLD, (int) (18.0 * Controller.getDisplayScalingFactor()));
	public static final Color xAxisFontColor            = Color.BLACK;
	
	// y-axis title
	public static final Font  yAxisFont                 = new Font("Geneva", Font.BOLD, (int) (18.0 * Controller.getDisplayScalingFactor()));
	public static final Color yAxisFontColor            = Color.BLACK;

}
