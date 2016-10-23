import java.awt.Color;
import java.awt.Font;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class Model {
	
	// the grid is the area in the ChartsRegion where users can position and size the charts
	final static int gridColumnsDefault = 8;
	final static int gridColumnsMinimum = 1;
	final static int gridColumnsMaximum = 12;
	static int gridColumns = gridColumnsDefault;
	
	final static int gridRowsDefault = 8;
	final static int gridRowsMinimum = 1;
	final static int gridRowsMaximum = 12;
	static int gridRows = gridRowsDefault;
	
	// serial port state
	static int sampleRate = 1000;
	static String packetType = "";
	static String portName = "";
	static int baudRate = 0;
	
	final static Color lineColorDefault = Color.RED;
	
	final static Font chartTitleFont = new Font("Arial", Font.PLAIN, 18);
	
	// attributes of the possible charts
	static ChartDescriptor[] chartDescriptors = new ChartDescriptor[] {
		TimeDomainChart.getDescriptor(),
		TimeDomainChartCached.getDescriptor(),
		TimeDomainChartOpenGL.getDescriptor(),
		TimeDomainChartOpenGLCached.getDescriptor(),
		FrequencyDomainChart.getDescriptor(),
		HistogramChart.getDescriptor(),
		StatisticsChart.getDescriptor()
	};

	static Map<Integer, Dataset> datasets = Collections.synchronizedMap(new TreeMap<Integer, Dataset>());
	
	static List<PositionedChart> charts = Collections.synchronizedList(new ArrayList<PositionedChart>());
	
}
