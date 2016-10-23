import java.awt.Graphics;
import java.awt.Image;
import java.awt.image.BufferedImage;

import javax.swing.SwingUtilities;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.LogAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

/**
 * A line chart of a Fourier transform.
 */
@SuppressWarnings("serial")
public class FrequencyDomainChart extends PositionedChart {
	
	Image chartImage;
	
	public static ChartDescriptor getDescriptor() {
		
		return new ChartDescriptor() {
			
			@Override public String toString()        { return "Frequency Domain Chart"; }
			@Override public int getMinimumDuration() { return 10; }
			@Override public int getDefaultDuration() { return 1000; }
			@Override public int getMaximumDuration() { return Integer.MAX_VALUE; }
			@Override public String[] getInputNames() { return new String[] {"Data"}; }
			
			@Override public PositionedChart createChart(int x1, int y1, int x2, int y2, int chartDuration, Dataset[] chartInputs) {
				return new FrequencyDomainChart(x1, y1, x2, y2, chartDuration, chartInputs);
			}
			
		};
		
	}
	
	@Override public String toString() {
		
		return "Frequency Domain Chart";
		
	}

	public FrequencyDomainChart(int x1, int y1, int x2, int y2, int chartDuration, Dataset[] chartInputs) {
		
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
					String xAxisTitle = "Frequency (Hz)";
					String yAxisTitle = "Power (W)";
					
					BufferedImage image = getFrequencyDomainChart(chartTitle, xAxisTitle, yAxisTitle, chartWidth, chartHeight);

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
		thread.setName(String.format("FrequencyDomainChart of: %s", inputNames));
		thread.start();

	}
	
	private BufferedImage getFrequencyDomainChart(String chartTitle, String xAxisTitle, String yAxisTitle, int chartWidth, int chartHeight) {
		
		int maxX = Controller.getSamplesCount(datasets) - 1;
		int minX = maxX - duration;
		if(minX < 0) minX = 0;
		
		// bin size (in Hertz) is the reciprocal of the window size (in seconds)
		// example: 500ms window -> 1/0.5 = 2 Hz bin size
		double samplesPerSecond = Controller.getSampleRate();
		double sampleCount = maxX - minX + 1;
		double binSizeHz = 1.0 / (sampleCount / samplesPerSecond);
		
		// maximum frequency range (in Hertz) is from 0 to the sample rate (in Hertz), divided by 2
		// example: sampling at 1kHz -> 0 Hz to 1000/2 = 500 Hz
		double maxFrequencyHz = samplesPerSecond / 2.0;
		
		// calc the DFT, assuming the samples are in Volts, and assuming the load is a unit load (1 ohm)
		XYSeries powerLevels = new XYSeries("DFT of " + datasets[0].name);
		double realV;
		double imaginaryV;
		double powerW;
		for(double frequencyHz = 0; frequencyHz <= maxFrequencyHz; frequencyHz += binSizeHz) {
			realV = 0.0;
			imaginaryV = 0.0;
			for(int x = minX; x <= maxX; x++) {
				double sample = datasets[0].get(x);
				double timeSec   = (double) x / samplesPerSecond;
				realV      += sample * Math.cos(2.0 * Math.PI * frequencyHz * timeSec);
				imaginaryV += sample * Math.sin(2.0 * Math.PI * frequencyHz * timeSec);
			}
			realV      /= sampleCount;
			imaginaryV /= sampleCount;
			powerW = (realV * realV) + (imaginaryV * imaginaryV);
			powerW *= 2; // because DFT is from -Fs to +Fs
			powerLevels.add(frequencyHz, powerW);
		}
		
		XYSeriesCollection seriesCollection = new XYSeriesCollection(powerLevels);
		JFreeChart frequencyDomainChart = ChartFactory.createXYLineChart(chartTitle, xAxisTitle, yAxisTitle, seriesCollection);
//		frequencyDomainChart.removeLegend();
		frequencyDomainChart.getXYPlot().getRenderer().setSeriesPaint(0, datasets[0].color);
		LogAxis verticalAxis = new LogAxis("Power (W)");
		verticalAxis.setLabelFont(frequencyDomainChart.getXYPlot().getDomainAxis().getLabelFont());
		verticalAxis.setTickLabelFont(frequencyDomainChart.getXYPlot().getDomainAxis().getTickLabelFont());
		verticalAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());
//		verticalAxis.setRange(Math.pow(10.0, -8.0), Math.pow(10.0, 2.0));
		frequencyDomainChart.getXYPlot().setRangeAxis(verticalAxis);
		frequencyDomainChart.getXYPlot().getDomainAxis().setRange(0.0, maxFrequencyHz);
		frequencyDomainChart.getTitle().setFont(Model.chartTitleFont);
		
		return frequencyDomainChart.createBufferedImage(chartWidth, chartHeight);
		
	}
	
	@Override protected void paintComponent(Graphics g) {
		
		super.paintComponent(g);
		
		if(chartImage != null)
			g.drawImage(chartImage, 0, 0, null);

	}
	
}