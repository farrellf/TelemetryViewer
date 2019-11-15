import java.awt.Color;
import java.awt.Toolkit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Model {
	
	static List<PositionedChart> charts = Collections.synchronizedList(new ArrayList<PositionedChart>());
	
	final static Color lineColorDefault = Color.RED;
	
	static float dpiScalingFactorJava8 = (int) Math.round((double) Toolkit.getDefaultToolkit().getScreenResolution() / 100.0); // will be reset to 1.0 if using java 9+
	static float dpiScalingFactorJava9 = 1; // will be updated dynamically if using java 9+
	static float dpiScalingFactorUser = 1; // may be updated by the user
	
}
