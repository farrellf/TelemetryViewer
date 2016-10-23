import java.awt.Graphics;
import java.awt.Image;
import java.awt.image.BufferedImage;

import javax.swing.SwingUtilities;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

@SuppressWarnings("serial")
public class TimeDomainChart extends PositionedChart {
	
	Image chartImage;
	
	public static ChartDescriptor getDescriptor() {
		
		return new ChartDescriptor() {
			
			@Override public String toString()        { return "Time Domain Chart"; }
			@Override public int getMinimumDuration() { return 10; }
			@Override public int getDefaultDuration() { return 1000; }
			@Override public int getMaximumDuration() { return Integer.MAX_VALUE; }
			@Override public String[] getInputNames() { return null; }
			
			@Override public PositionedChart createChart(int x1, int y1, int x2, int y2, int chartDuration, Dataset[] chartInputs) {
				return new TimeDomainChart(x1, y1, x2, y2, chartDuration, chartInputs);
			}
			
		};
		
	}
	
	@Override public String toString() {
		
		return "Time Domain Chart";
		
	}

	public TimeDomainChart(int x1, int y1, int x2, int y2, int chartDuration, Dataset[] chartInputs) {
		
		super(x1, y1, x2, y2, chartDuration, chartInputs);
		
		// spawn a thread that draws the chart
		Thread thread = new Thread(new Runnable() {
			@Override public void run() {
				
				while(true) {
					
					long startTime = System.currentTimeMillis();

					// draw the chart
					int chartWidth = getWidth();
					int chartHeight = getHeight();
					if(chartWidth < 1) chartWidth = 1;
					if(chartHeight < 1) chartHeight = 1;
					
					String chartTitle = "";
					String xAxisTitle = "Sample Number";
					String yAxisTitle = datasets[0].unit;
					
					BufferedImage image = getTimeDomainChart(chartTitle, xAxisTitle, yAxisTitle, chartWidth, chartHeight);
					
					SwingUtilities.invokeLater(new Runnable() {
						@Override public void run() {
							
							// free resources of old image
							if(chartImage != null)
								chartImage.flush();
							
							// paint new image
							chartImage = image;
							repaint();
							
						}
					});
					
					// end this thread if we are no longer visible (the user clicked the "Reset" button)
					if(!isShowing())
						break;
					
					// wait if needed before drawing a new frame
					long timeToNextFrame = startTime + Controller.getTargetFramePeriod() - System.currentTimeMillis();
					if(timeToNextFrame <= 0)
						continue;
					else
						try{ Thread.sleep(timeToNextFrame); } catch(Exception e) {}
					
				}
				
			}
			
		});
		String inputNames = "";
		for(int i = 0; i < datasets.length; i++)
			inputNames += datasets[i].name + ", ";
		thread.setName(String.format("TimeDomainChart of: %s", inputNames));
		thread.start();

	}
	
	private BufferedImage getTimeDomainChart(String chartTitle, String xAxisTitle, String yAxisTitle, int chartWidth, int chartHeight) {
		
		XYSeriesCollection seriesCollection = new XYSeriesCollection();
		JFreeChart lineGraph = ChartFactory.createXYLineChart(chartTitle, xAxisTitle, yAxisTitle, seriesCollection);
		
		// calculate domain
		int sampleCount = Controller.getSamplesCount(datasets);
		int maxX = sampleCount - 1;
		int minX = maxX - duration;
		if(minX < 0) minX = 0;
		
		for(int i = 0; i < datasets.length; i++) {
			
			XYSeries series = new XYSeries(datasets[i].name);
			for(int x = minX; x <= maxX; x++) {
				double y = datasets[i].get(x);
				series.add(x, y);
			}
			
			seriesCollection.addSeries(series);
			lineGraph.getXYPlot().getRenderer().setSeriesPaint(i, datasets[i].color);
			
		}

		lineGraph.getXYPlot().getDomainAxis().setRange(minX, maxX + 2); // +2 to prevent a range of (0,0)
		lineGraph.getTitle().setFont(Model.chartTitleFont);
		
		return lineGraph.createBufferedImage(chartWidth, chartHeight);
		
	}
	
	@Override protected void paintComponent(Graphics g) {
		
		super.paintComponent(g);
		
		if(chartImage != null)
			g.drawImage(chartImage, 0, 0, null);

	}
	
}
