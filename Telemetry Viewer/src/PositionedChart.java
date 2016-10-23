import java.awt.Color;
import javax.swing.BorderFactory;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

@SuppressWarnings("serial")
public class PositionedChart extends JPanel {
	
	// grid coordinates, not pixels
	int topLeftX;
	int topLeftY;
	int bottomRightX;
	int bottomRightY;
	
	int duration;
	Dataset[] datasets;
	
	public PositionedChart(int x1, int y1, int x2, int y2, int chartDuration, Dataset[] chartInputs) {
		
		super();
		
		topLeftX     = x1 < x2 ? x1 : x2;
		topLeftY     = y1 < y2 ? y1 : y2;
		bottomRightX = x2 > x1 ? x2 : x1;
		bottomRightY = y2 > y1 ? y2 : y1;

		duration = chartDuration;
		datasets = chartInputs;
		
		setBorder(BorderFactory.createLineBorder(Color.BLACK));
			
	}
	
	public void reposition(int columnWidth, int rowHeight) {
		
		int x = topLeftX * columnWidth;
		int y = topLeftY * rowHeight;
		int width = (bottomRightX - topLeftX + 1) * columnWidth;
		int height = (bottomRightY - topLeftY + 1) * rowHeight;
		setBounds(x, y, width, height);
		
	}
	
	public boolean regionOccupied(int startX, int startY, int endX, int endY) {

		for(int x = startX; x <= endX; x++)
			for(int y = startY; y <= endY; y++)
				if(x >= topLeftX && x <= bottomRightX && y >= topLeftY && y <= bottomRightY)
					return true;
		
		return false;
		
	}
	
	public void reconnectDatasets() {
		
		try {
			for(int i = 0; i < datasets.length; i++)
				datasets[i] = Controller.getDatasetByLocation(datasets[i].location);
		} catch(Exception e) {
			JOptionPane.showMessageDialog(this, "The data structure has significantly changed so the charts will be removed. New charts can be added as usual.", "Notice: Data Structure Changed Significantly", JOptionPane.WARNING_MESSAGE);
			Controller.removeAllPositionedCharts();
		}

	}
	
}