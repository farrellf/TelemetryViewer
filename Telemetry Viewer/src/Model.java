import java.awt.Color;
import java.awt.Toolkit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class Model {
	
	static Map<Integer, Dataset> datasets = Collections.synchronizedMap(new TreeMap<Integer, Dataset>());
	static List<PositionedChart> charts = Collections.synchronizedList(new ArrayList<PositionedChart>());
	
	final static Color lineColorDefault = Color.RED;
	
	static float displayScalingFactor = (int) Math.round((double) Toolkit.getDefaultToolkit().getScreenResolution() / 100.0);
	final static float displayScalingFactorMinimum =  1.0f * displayScalingFactor;
	final static float displayScalingFactorMaximum = 10.0f;
	
}
