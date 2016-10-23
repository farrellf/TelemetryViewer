import java.awt.Graphics;
import java.awt.Image;
import java.awt.image.BufferedImage;

import javax.swing.SwingUtilities;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.statistics.HistogramDataset;
import org.jfree.data.statistics.HistogramType;

@SuppressWarnings("serial")
public class HistogramChart extends PositionedChart {
	
	Image chartImage;
	
	public static ChartDescriptor getDescriptor() {
		
		return new ChartDescriptor() {
			
			@Override public String toString()        { return "Histogram"; }
			@Override public int getMinimumDuration() { return 10; }
			@Override public int getDefaultDuration() { return 1000; }
			@Override public int getMaximumDuration() { return Integer.MAX_VALUE; }
			@Override public String[] getInputNames() { return new String[] {"Data"}; }
			
			@Override public PositionedChart createChart(int x1, int y1, int x2, int y2, int chartDuration, Dataset[] chartInputs) {
				return new HistogramChart(x1, y1, x2, y2, chartDuration, chartInputs);
			}
			
		};
		
	}
	
	@Override public String toString() {
		
		return "Histogram";
		
	}

	public HistogramChart(int x1, int y1, int x2, int y2, int chartDuration, Dataset[] chartInputs) {
		
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
					String xAxisTitle = datasets[0].unit + " (" + chartDuration + " Samples of " + datasets[0].name + ")";
					String yAxisTitle = "Relative Frequency";
										
					BufferedImage image = getHistogram(chartTitle, xAxisTitle, yAxisTitle, chartWidth, chartHeight);
					
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
		thread.setName(String.format("HistogramChart of: %s", inputNames));
		thread.start();

	}
	
	private BufferedImage getHistogram(String chartTitle, String xAxisTitle, String yAxisTitle, int chartWidth, int chartHeight) {
		
//		// create the histogram bins
//		double idealVoltage = 50.0;
//		double voltageRange = 100.0;
//		int binCount = 50;
//		double binWidth = voltageRange / (double) binCount;
//
//		SimpleHistogramDataset histogramDataset = new SimpleHistogramDataset("Histogram");
//		for(int i = binCount/2; i > 0; i--) {
//			double left = idealVoltage - (i * binWidth);
//			double right =idealVoltage - ((i-1) * binWidth);
//			histogramDataset.addBin(new SimpleHistogramBin(left, right, true, false));
//		}
//		for(int i = 0; i < binCount/2; i++) {
//			double left = idealVoltage + (i * binWidth);
//			double right = idealVoltage + ((i+1) * binWidth);
//			histogramDataset.addBin(new SimpleHistogramBin(left, right, true, false));
//		}
//		
//		// populate the histogram bins
//		for(int i = 0; i < dataset[0].getItemCount(); i++)
//			try {
//				histogramDataset.addObservation((double) dataset[0].getY(i));
//			} catch(Exception e) {
//				// occurs if the value doesn't fit in one of the bins
//			}
		
		int sampleCount = Controller.getSamplesCount(datasets);
		int maxX = sampleCount - 1;
		int minX = maxX - duration;
		if(minX < 0) minX = 0;
		
		double[] samples = new double[maxX - minX + 1];
		for(int i = 0; i < samples.length; i++)
			samples[i] = datasets[0].get(i + minX);
		
		if(samples.length == 0)
			return null;			
		
		HistogramDataset histogramDataset = new HistogramDataset();
		histogramDataset.addSeries(datasets[0].name, samples, 50);
		histogramDataset.setType(HistogramType.RELATIVE_FREQUENCY);

		// setup the histogram
		final JFreeChart histogram = ChartFactory.createHistogram(chartTitle, xAxisTitle, yAxisTitle, histogramDataset, PlotOrientation.VERTICAL, false, false, false);
		histogram.getXYPlot().getRenderer().setSeriesPaint(0, datasets[0].color);
//		histogram.getXYPlot().getRangeAxis().setStandardTickUnits(NumberAxis.createIntegerTickUnits());
		histogram.getTitle().setFont(Model.chartTitleFont);

		return histogram.createBufferedImage(chartWidth, chartHeight);
		
	}
	
	@Override protected void paintComponent(Graphics g) {
		
		super.paintComponent(g);
		
		if(chartImage != null)
			g.drawImage(chartImage, 0, 0, null);

	}
	
}