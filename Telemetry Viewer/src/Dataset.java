import java.awt.Color;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Defines all of the details about one CSV column or Binary packet field, stores all of its samples, and provides several ways to get the samples.
 */
public class Dataset {
	
	final ConnectionTelemetry connection;
	final DatasetsController controller;
	final int location;
	final DatasetsController.BinaryFieldProcessor processor;
	String name;
	Color color;
	float[] glColor;
	String unit;
	final float conversionFactorA;
	final float conversionFactorB;
	final float conversionFactor;
	
	boolean isBitfield;
	List<Bitfield> bitfields;
	
	StorageFloats floats;
	
	/**
	 * Creates a new object that describes one dataset and stores all of its samples.
	 * 
	 * @param connection           Which connection this Dataset belongs to.
	 * @param location             CSV column number, or Binary byte offset.
	 * @param processor            Data processor for the raw samples in the Binary data packet. (Ignored in CSV mode.)
	 * @param name                 Descriptive name of what the samples represent.
	 * @param color                Color to use when visualizing the samples.
	 * @param unit                 Descriptive name of how the samples are quantified.
	 * @param conversionFactorA    This many unprocessed LSBs...
	 * @param conversionFactorB    ... equals this many units.
	 */
	public Dataset(ConnectionTelemetry connection, int location, DatasetsController.BinaryFieldProcessor processor, String name, Color color, String unit, float conversionFactorA, float conversionFactorB) {
		
		this.connection        = connection;
		this.controller        = connection.datasets;
		this.location          = location;
		this.processor         = processor;
		this.name              = name;
		this.color             = color;
		this.glColor           = new float[] {color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f, 1f};
		this.unit              = unit;
		this.conversionFactorA = conversionFactorA;
		this.conversionFactorB = conversionFactorB;
		this.conversionFactor  = conversionFactorB / conversionFactorA;
		this.isBitfield        = false;
		this.bitfields         = new ArrayList<Bitfield>();
		
		floats = new StorageFloats(connection);
		
	}
	
	/**
	 * Updates the name, color and unit of this Dataset.
	 * 
	 * @param name     The new name.
	 * @param color    The new color.
	 * @param unit     The new unit.
	 */
	public void setNameColorUnit(String name, Color color, String unit) {
		this.name    = name;
		this.color   = color;
		this.glColor = new float[] {color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f, 1f};
		this.unit    = unit;
	}
	
	/**
	 * Configures this Dataset to store Bitfields, and appends a new Bitfield object to it.
	 * 
	 * @param MSBit    Most-significant-bit occupied by the Bitfield.
	 * @param LSBit    Least-significant-bit occupied by the Bitfield.
	 * @return         The new Bitfield object.
	 */
	public Bitfield addBitfield(int MSBit, int LSBit) {
		
		isBitfield = true;
		Bitfield bitfield = new Bitfield(MSBit, LSBit);
		bitfields.add(bitfield);
		return bitfield;
		
	}
	
	/**
	 * @return    List of Bitfields.
	 */
	public List<Bitfield> getBitfields() {
		
		return bitfields;
		
	}
	
	/**
	 * @return    The name of this dataset.
	 */
	@Override public String toString() {
		
		return name;
		
	}
	
	/**
	 * @return    A place to cache samples.
	 */
	public StorageFloats.Cache createCache() {
		
		return floats.createCache();
		
	}
	
	/**
	 * Gets one sample, as a float.
	 * 
	 * @param sampleNumber    Sample number to obtain.
	 * @param cache           Place to cache samples.
	 * @return                The sample, as a float.
	 */
	public float getSample(int sampleNumber, StorageFloats.Cache cache) {
		
		return floats.getSample(sampleNumber, cache);
		
	}
	
	/**
	 * Gets one sample, as a String.
	 * 
	 * @param sampleNumber    Sample number to obtain.
	 * @param cache           Place to cache samples.
	 * @return                The sample, formatted as a String.
	 */
	public String getSampleAsString(int sampleNumber, StorageFloats.Cache cache) {
		
		float value = getSample(sampleNumber, cache);
		
		if(isBitfield)
			return "0b" + String.format("%8s", Integer.toBinaryString((int) value)).replace(' ', '0');
		else
			return ChartUtils.formattedNumber(value, 5) + " " + unit;
		
	}
	
	/**
	 * Gets a sequence of samples, as a FloatBuffer.
	 * 
	 * @param firstSampleNumber    First sample number to obtain, inclusive.
	 * @param lastSampleNumber     Last sample number to obtain, inclusive.
	 * @param cache                Place to cache samples.
	 * @return                     The samples, as a FloatBuffer, positioned at the first sample number.
	 */
	public FloatBuffer getSamplesBuffer(int firstSampleNumber, int lastSampleNumber, StorageFloats.Cache cache) {
		
		return floats.getSamplesBuffer(firstSampleNumber, lastSampleNumber, cache);
		
	}
	
	/**
	 * Gets a sequence of samples, as a float[].
	 * 
	 * @param firstSampleNumber    First sample number to obtain, inclusive.
	 * @param lastSampleNumber     Last sample number to obtain, inclusive.
	 * @param cache                Place to cache samples.
	 * @return                     The samples, as a float[].
	 */
	public float[] getSamplesArray(int firstSampleNumber, int lastSampleNumber, StorageFloats.Cache cache) {
		
		float[] array = new float[lastSampleNumber - firstSampleNumber + 1];
		floats.getSamplesBuffer(firstSampleNumber, lastSampleNumber, cache).get(array);
		return array;
		
	}
	
	/**
	 * Converts and appends a new sample to the dataset.
	 * 
	 * @param sampleNumber    Which sample number to populate.
	 * @param value           New sample to be converted and then written into the dataset.
	 */
	public void setSample(int sampleNumber, float value) {
		
		setConvertedSample(sampleNumber, value * conversionFactor);
		
	}
	
	/**
	 * Appends a new sample to the dataset.
	 * 
	 * @param sampleNumber    Which sample number to populate.
	 * @param value           New sample to be written into the dataset. It will be written as-is, NOT converted.
	 */
	public void setConvertedSample(int sampleNumber, float value) {
		
		floats.setValue(sampleNumber, value);
		
	}
	
	/**
	 * Obtains the samples buffer so that multiple Parser threads may write directly into it (in parallel.)
	 * 
	 * @param sampleNumber    The sample number whose buffer is wanted.
	 * @return                Corresponding buffer.
	 */
	public synchronized float[] getSlot(int sampleNumber) {
		
		return floats.getSlot(sampleNumber);
		
	}
	
	/**
	 * Specifies the minimum and maximum values found in a block.
	 * This method must be called AFTER any Parser threads have populated a block, but BEFORE the sample count has been incremented.
	 * 
	 * @param firstSampleNumber    First sample number of the block.
	 * @param minValue             Minimum value in the block.
	 * @param maxValue             Maximum value in the block.
	 */
	public synchronized void setRangeOfBlock(int firstSampleNumber, float minValue, float maxValue) {
		
		floats.setRangeOfBlock(firstSampleNumber, minValue, maxValue);
		
	}
	
	/**
	 * Gets the minimum and maximum of a sequence of samples.
	 * 
	 * @param firstSampleNumber    First sample number to consider, inclusive.
	 * @param lastSampleNumber     Last sample number to consider, inclusive.
	 * @param cache                Place to cache samples.
	 * @return                     A MinMax object, which has "min" and "max" fields.
	 */
	public StorageFloats.MinMax getRange(int firstSampleNumber, int lastSampleNumber, StorageFloats.Cache cache) {
	
		return floats.getRange(firstSampleNumber, lastSampleNumber, cache);
		
	}
	
	/**
	 * Describes one bitfield, which has 2^n states.
	 * Each Dataset can contain zero or more Bitfields.
	 */
	public class Bitfield implements Comparable<Bitfield> {
		
		final int MSBit;
		final int LSBit;
		final int bitmask; // (raw dataset value >> LSBit) & bitmask = bitfield state
		final State[] states;
		final Dataset dataset;
		
		public Bitfield(int MSBit, int LSBit) {
			
			this.MSBit = MSBit;
			this.LSBit = LSBit;
			
			int statesCount = (int) Math.pow(2, MSBit - LSBit + 1);
			bitmask = statesCount - 1;
			states = new State[statesCount];
			for(int i = 0; i < statesCount; i++)
				states[i] = new State(i, (MSBit != LSBit) ? "Bits [" + MSBit + ":" + LSBit + "] = " + i :
				                                            "Bit " + MSBit + " = " + i);
			
			dataset = Dataset.this;
			
		}
		
		/**
		 * @param sampleNumber    Sample number.
		 * @param cache           Place to cache samples.
		 * @return                State of this bitfield at the specified sample number.
		 */
		int getStateAt(int sampleNumber, StorageFloats.Cache cache) {
			int value = (int) Dataset.this.getSample(sampleNumber, cache);
			int state = (value >> LSBit) & bitmask;
			return state;
		}

		/**
		 * For sorting a Collection of Bitfields so the fields occupying less-significant bits come first.
		 */
		@Override public int compareTo(Bitfield other) {

			if(this.dataset == other.dataset)
				return this.MSBit - other.LSBit;
			else
				return this.dataset.location - other.dataset.location;
			
		}
		
		/**
		 * Describes one possible state (value) of the Bitfield.
		 */
		public class State implements Comparable<State> {
			
			String label;                            // Example: "Bit 7 = 1" (shown in the PacketBinary.BitfieldPanel.Visualization)
			int value;                               // Example: "1"
			String name;                             // Example: "Some Fault Occurred" (shown on markers on the charts)
			Color color;                             // shown in the PacketBinary.BitfieldPanel
			float[] glColor;                         // shown on markers on the charts
			ConnectionTelemetry connection;          // owner of this State
			Dataset dataset;                         // owner of this State
			Bitfield bitfield;                       // owner of this State
			
			List<Integer> edgesCache = new ArrayList<Integer>(); // cache of the sample numbers for each transition to this state
			int lastSampleNumberInCache = -1;
			
			public State(int value, String label) {
				this.label = label;
				this.value = value;
				this.name = "";
				this.color = Dataset.this.color;
				this.glColor = Dataset.this.glColor;
				connection = Dataset.this.connection;
				dataset = Dataset.this;
				bitfield = Bitfield.this;
			}
			
			@Override public String toString() {
				return "connection " + ConnectionsController.allConnections.indexOf(dataset.connection) + " location " + Dataset.this.location + " [" + Bitfield.this.MSBit + ":" + Bitfield.this.LSBit + "] = " + value;
			}
			
			/**
			 * Updates the edges cache if necessary.
			 * 
			 * @param maxSampleNumber    Ensure the cache has all edge events that occurred until at least this sample number.
			 * @param samplesCache       Place to cache raw dataset samples.
			 */
			private void updateEdgesCache(int maxSampleNumber, StorageFloats.Cache samplesCache) {
				
				if(maxSampleNumber <= lastSampleNumberInCache || maxSampleNumber == 0)
					return;
				
				int minSampleNumber = lastSampleNumberInCache;
				if(minSampleNumber < 0)
					minSampleNumber = 0;
				
				int previousValue = (int) dataset.getSample(minSampleNumber, samplesCache);
				int previousState = (previousValue >> bitfield.LSBit) & bitfield.bitmask;
				
				for(int sampleNumber = minSampleNumber + 1; sampleNumber <= maxSampleNumber; sampleNumber++) {
					int currentValue = (int) dataset.getSample(sampleNumber, samplesCache);
					if(currentValue != previousValue) {
						int currentState = (currentValue >> bitfield.LSBit) & bitfield.bitmask;
						if(currentState != previousState && currentState == value)
							edgesCache.add(sampleNumber);
						previousState = currentState;
					}
					previousValue = currentValue;
				}
				
				lastSampleNumberInCache = maxSampleNumber;
				
			}
			
			/**
			 * Gets a List of sample numbers for when this Bitfield transitioned to this State.
			 * 
			 * @param minimumSampleNumber    First sample number to test, inclusive.
			 * @param maximumSampleNumber    Last sample number to test, inclusive.
			 * @param samplesCache           Place to cache raw dataset samples.
			 * @return                       List of sample numbers for when this Bitfield State occurred.
			 */
			public List<Integer> getEdgeEventsBetween(int minimumSampleNumber, int maximumSampleNumber, StorageFloats.Cache samplesCache) {
				
				updateEdgesCache(maximumSampleNumber, samplesCache);
				
				List<Integer> edges = new ArrayList<Integer>();
				for(int sampleNumber : edgesCache) {
					if(sampleNumber >= minimumSampleNumber && sampleNumber <= maximumSampleNumber)
						edges.add(sampleNumber);
					else if(sampleNumber > maximumSampleNumber)
						break;
				}
				
				return edges;
				
			}
			
			/**
			 * Gets a List of ranges for when this Bitfield State existed.
			 * 
			 * @param minimumSampleNumber    First sample number to test, inclusive.
			 * @param maximumSampleNumber    Last sample number to test, inclusive.
			 * @param samplesCache           Place to cache raw dataset samples.
			 * @return                       List of ranges ([0] minSampleNumber, [1] maxSampleNumber) for when this Bitfield State existed.
			 */
			public List<int[]> getLevelsBetween(int minimumSampleNumber, int maximumSampleNumber, StorageFloats.Cache samplesCache) {
				
				updateEdgesCache(maximumSampleNumber, samplesCache);
				
				List<int[]> levels = new ArrayList<int[]>();
				List<Integer> edges = getEdgeEventsBetween(minimumSampleNumber, maximumSampleNumber - 1, samplesCache);
				
				if(edges.isEmpty() || edges.get(0) != minimumSampleNumber) {
					int firstValue = (int) dataset.getSample(minimumSampleNumber, samplesCache);
					int firstState = (firstValue >> bitfield.LSBit) & bitfield.bitmask;
					if(firstState == value)
						edges.add(0, minimumSampleNumber);
				}
				
				for(int i = 0; i < edges.size(); i++) {
					int levelBegin = edges.get(i);
					int levelEnd = i+1 < edges.size() ? edges.get(i+1) : maximumSampleNumber; // "worse case scenario"
					// check if level ended earlier
					if(levelEnd - levelBegin > 1)
						for(State s : bitfield.states) {
							if(s == this)
								continue;
							List<Integer> edgesOfOtherState = s.getEdgeEventsBetween(levelBegin + 1, levelEnd - 1, samplesCache);
							if(!edgesOfOtherState.isEmpty())
								levelEnd = edgesOfOtherState.get(0);
						}
					levels.add(new int[] {levelBegin, levelEnd});
				}
				
				return levels;
				
			}
			
			/**
			 * For sorting a collections of States so earlier datasets come first, and smaller values come first.
			 */
			@Override public int compareTo(State other) {

				if(this.bitfield == other.bitfield)
					return this.value - other.value;
				else if(this.dataset == other.dataset)
					return this.bitfield.MSBit - other.bitfield.MSBit;
				else
					return this.dataset.location - other.dataset.location;
				
			}
			
		}

	}
	
}
