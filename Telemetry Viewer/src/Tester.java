import java.awt.Color;

public class Tester {
	
	private static Thread transmitter;
	private static double counter = 0;
	
	/**
	 * Simulates the transmission of 3 numbers every 1ms.
	 * The numbers are pseudo random, and scaled to form a sort of sawtooth waveform.
	 * This is used to check for proper autoscaling of charts, etc.
	 */
	@SuppressWarnings("deprecation")
	public static void startTransmission() {
		
		if(transmitter != null)
			transmitter.stop();
		
		transmitter = new Thread(new Runnable() {
			@Override public void run() {
				
				while(true) {
					float scalar = ((System.currentTimeMillis() % 30000) - 15000) / 100.0f;
					float[] newSamples = new float[] {
						(System.nanoTime() % 100) * scalar * 1.0f / 14000f,
						(System.nanoTime() % 100) * scalar * 0.8f / 14000f,
						(System.nanoTime() % 100) * scalar * 0.6f / 14000f
					};
					for(int i = 0; i < 10; i++) {
						Controller.getDatasetByIndex(0).add(newSamples[0]);
						Controller.getDatasetByIndex(1).add(newSamples[1]);
						Controller.getDatasetByIndex(2).add(newSamples[2]);
					}
					for(int i = 0; i < 10; i++) {
						Controller.getDatasetByIndex(3).add((float) Math.sin(2 * Math.PI * 1000 * counter));
						counter += 0.0001;
					}
					
					try { Thread.sleep(1); } catch(Exception e) {} // wait 1ms
				}
				
			}
		});
		transmitter.setName("Test Transmitter");
		transmitter.start();
		
	}
	
	@SuppressWarnings("deprecation")
	public static void stopTransmission() {
		
		if(transmitter != null)
			transmitter.stop();
		
	}
	
	public static void populateDataStructure() {
		
		Controller.removeAllDatasets();
		
		int location = 0;
		BinaryFieldProcessor processor = BinaryPacket.getBinaryFieldProcessors()[0];
		String name = "";
		Color color = null;
		String unit = "Volts";
		float conversionFactorA = 1;
		float conversionFactorB = 1;
		
		location = 0;
		name = "Waveform A";
		color = Color.RED;
		Controller.insertDataset(location, processor, name, color, unit, conversionFactorA, conversionFactorB);
		
		location = 1;
		name = "Waveform B";
		color = Color.GREEN;
		Controller.insertDataset(location, processor, name, color, unit, conversionFactorA, conversionFactorB);
		
		location = 2;
		name = "Waveform C";
		color = Color.BLUE;
		Controller.insertDataset(location, processor, name, color, unit, conversionFactorA, conversionFactorB);
		
		location = 3;
		name = "Sine Wave 1kHz";
		color = Color.CYAN;
		Controller.insertDataset(location, processor, name, color, unit, conversionFactorA, conversionFactorB);
		
	}

}
