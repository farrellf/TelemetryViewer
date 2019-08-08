import java.awt.Color;

public class Tester {
	
	private static Thread transmitter;
	
	/**
	 * Simulates the transmission of 4 numbers every 100us.
	 * The first three numbers are pseudo random, and scaled to form a sort of sawtooth waveform.
	 * The fourth number is a 1kHz sine wave.
	 * This is used to check for proper autoscaling of charts, etc.
	 */
	public static void startTransmission() {
		
		transmitter = new Thread(new Runnable() {
			@Override public void run() {
				
				double counter = 0;
				
				while(true) {
					
					try {
						
						float scalar = ((System.currentTimeMillis() % 30000) - 15000) / 100.0f;
						float[] newSamples = new float[] {
							(System.nanoTime() / 100 % 100) * scalar * 1.0f / 14000f,
							(System.nanoTime() / 100 % 100) * scalar * 0.8f / 14000f,
							(System.nanoTime() / 100 % 100) * scalar * 0.6f / 14000f
						};
						for(int i = 0; i < 10; i++) {
							DatasetsController.getDatasetByIndex(0).add(newSamples[0]);
							DatasetsController.getDatasetByIndex(1).add(newSamples[1]);
							DatasetsController.getDatasetByIndex(2).add(newSamples[2]);
							DatasetsController.getDatasetByIndex(3).add((float) Math.sin(2 * Math.PI * 1000 * counter));
							counter += 0.0001;
							DatasetsController.incrementSampleCount();
						}
						
						Thread.sleep(1);
						
					} catch(InterruptedException e) {
						
						// stop and end this thread if we get interrupted
						return;
						
					}
				}
				
			}
		});
		transmitter.setPriority(Thread.MAX_PRIORITY);
		transmitter.setName("Test Transmitter");
		transmitter.start();
		
	}
	
	public static void stopTransmission() {
		
		if(transmitter != null && transmitter.isAlive()) {
			transmitter.interrupt();
			while(transmitter.isAlive()); // wait
		}
		
	}
	
	public static void populateDataStructure() {
		
		DatasetsController.removeAllDatasets();
		
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
		DatasetsController.insertDataset(location, processor, name, color, unit, conversionFactorA, conversionFactorB);
		
		location = 1;
		name = "Waveform B";
		color = Color.GREEN;
		DatasetsController.insertDataset(location, processor, name, color, unit, conversionFactorA, conversionFactorB);
		
		location = 2;
		name = "Waveform C";
		color = Color.BLUE;
		DatasetsController.insertDataset(location, processor, name, color, unit, conversionFactorA, conversionFactorB);
		
		location = 3;
		name = "Sine Wave 1kHz";
		color = Color.CYAN;
		DatasetsController.insertDataset(location, processor, name, color, unit, conversionFactorA, conversionFactorB);
		
	}

}
