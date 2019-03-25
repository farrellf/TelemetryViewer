import java.awt.Color;
import java.awt.Toolkit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Model {
	
	static List<PositionedChart> charts = Collections.synchronizedList(new ArrayList<PositionedChart>());
	
	final static Color lineColorDefault = Color.RED;
	
	static float displayScalingFactor = (int) Math.round((double) Toolkit.getDefaultToolkit().getScreenResolution() / 100.0);
	final static float displayScalingFactorMinimum =  1.0f * displayScalingFactor;
	final static float displayScalingFactorMaximum = 10.0f;
	
}
