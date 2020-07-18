import java.awt.Color;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import com.jogamp.common.nio.Buffers;

/**
 * Defines all of the details about one CSV column or Binary packet field, stores all of its samples, and provides several ways to get the samples.
 */
public class Dataset {
	
	final int location;
	final BinaryFieldProcessor processor;
	String name;
	Color color;
	float[] glColor;
	String unit;
	final float conversionFactorA;
	final float conversionFactorB;
	final float conversionFactor;
	
	boolean isBitfield;
	List<Bitfield> bitfields;

	/**
	 * Samples are stored in an array of Slots. Each Slot contains 1M values, and may be flushed to disk if RAM runs low.
	 * Slots are further divided into "blocks" that cache min/max values.
	 */
	private final int BLOCK_SIZE = 1024;
	class Slot {
		
		private String pathOnDisk = "cache/" + this.toString();
		private volatile boolean inRam = true;
		private volatile boolean flushing = false;
		
		private volatile float[] values = new float[DatasetsController.SLOT_SIZE];
		private volatile float[] minimumValueInBlock = new float[DatasetsController.SLOT_SIZE / BLOCK_SIZE];
		private volatile float[] maximumValueInBlock = new float[DatasetsController.SLOT_SIZE / BLOCK_SIZE];
		
		public void moveToDisk() {
			
			if(!inRam || flushing)
				return;
			
			flushing = true;
			
			new Thread(() -> {
				try {
					ObjectOutputStream stream = new ObjectOutputStream(new FileOutputStream(pathOnDisk));
					stream.writeObject(values);
					stream.writeObject(minimumValueInBlock);
					stream.writeObject(maximumValueInBlock);
					stream.close();
					inRam = false;
					values = null;
					flushing = false;
				} catch(Exception e) {
					e.printStackTrace();
				}
			}).start();
			
		}
		
		private void copyToRam() {
			
			try {
				ObjectInputStream stream = new ObjectInputStream(new FileInputStream(pathOnDisk));
				values = (float[]) stream.readObject();
				minimumValueInBlock = (float[]) stream.readObject();
				maximumValueInBlock = (float[]) stream.readObject();
				stream.close();
				inRam = true;
			} catch(Exception e) {
				e.printStackTrace();
			}
			
		}
		
		public void removeFromDisk() {
			
			try { Files.deleteIfExists(Paths.get(pathOnDisk)); } catch (IOException e) {}
			
		}
		
		public void setValue(int index, float value) {
			
			if(!inRam)
				copyToRam();
			values[index] = value;
			
			int blockN = index / BLOCK_SIZE;
			if(index % BLOCK_SIZE == 0) {
				minimumValueInBlock[blockN] = value;
				maximumValueInBlock[blockN] = value;
			} else {
				if(value < minimumValueInBlock[blockN])
					minimumValueInBlock[blockN] = value;
				if(value > maximumValueInBlock[blockN])
					maximumValueInBlock[blockN] = value;
			}
			
		}
		
		public float getValue(int index) {
			
			if(!inRam)
				copyToRam();
			return values[index];
			
		}
		
		public float getMinimumInBlock(int blockIndex) {
			
			if(!inRam)
				copyToRam();
			return minimumValueInBlock[blockIndex];
			
		}
		
		public float getMaximumInBlock(int blockIndex) {
			
			if(!inRam)
				copyToRam();
			return maximumValueInBlock[blockIndex];
			
		}
		
	}
	Slot[] slots;
	
	/**
	 * Creates a new object that describes one dataset and stores all of its samples.
	 * 
	 * @param location             CSV column number, or Binary byte offset.
	 * @param processor            Data processor for the raw samples in the Binary data packet. (Ignored in CSV mode.)
	 * @param name                 Descriptive name of what the samples represent.
	 * @param color                Color to use when visualizing the samples.
	 * @param unit                 Descriptive name of how the samples are quantified.
	 * @param conversionFactorA    This many unprocessed LSBs...
	 * @param conversionFactorB    ... equals this many units.
	 */
	public Dataset(int location, BinaryFieldProcessor processor, String name, Color color, String unit, float conversionFactorA, float conversionFactorB) {
		
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
		
		slots = new Slot[DatasetsController.SLOT_COUNT];
		
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
	 * Gets one specific sample.
	 * 
	 * @param index    Which sample to obtain.
	 * @return         The sample.
	 */
	public float getSample(int index) {
		
		int slotNumber = index / DatasetsController.SLOT_SIZE;
		int slotIndex  = index % DatasetsController.SLOT_SIZE;
		return slots[slotNumber].getValue(slotIndex);
		
	}
	
	/**
	 * Gets a String representation of one specific sample.
	 * 
	 * @param index    Which sample to obtain.
	 * @return         The sample formatted as a String.
	 */
	public String getSampleAsString(int index) {
		
		float value = getSample(index);
		
		if(isBitfield)
			return "0b" + String.format("%8s", Integer.toBinaryString((int) value)).replace(' ', '0');
		else
			return ChartUtils.formattedNumber(value, 5) + " " + unit;
		
	}
	
	/**
	 * Gets a series of samples as a float[].
	 * 
	 * @param startIndex    Index of the first sample (inclusive.)
	 * @param endIndex      Index of the last sample (inclusive.)
	 * @return              The samples as a float[].
	 */
	public float[] getSamplesArray(int startIndex, int endIndex) {
		
		int sampleCount = endIndex - startIndex + 1;
		float[] samples = new float[sampleCount];
		
		for(int i = 0; i < sampleCount; i++)
			samples[i] = getSample(i + startIndex);
		
		return samples;
		
	}
	
	/**
	 * Gets a series of samples by updating a existing Samples object:
	 * 
	 * The buffer[] is updated with the requested series of samples.
	 * The min/max values are updated to reflect the new series of samples.
	 * The color/name/unit are updated. 
	 * 
	 * @param startIndex    Index of the first sample (inclusive.)
	 * @param endIndex      Index of the last sample (inclusive.)
	 * @param samples       Samples object to be filled with samples.
	 */
	public void getSamples(int startIndex, int endIndex, Samples samples) {
		
		if(samples.color == null)
			samples.color = new float[4];
		samples.color[0] = (float) color.getRed()   / 255.0f;
		samples.color[1] = (float) color.getGreen() / 255.0f;
		samples.color[2] = (float) color.getBlue()  / 255.0f;
		samples.color[3] = 1.0f;
		
		samples.name = name;
		
		samples.unit = unit;
		
		int sampleCount = endIndex - startIndex + 1;
		
		if(samples.buffer == null || samples.buffer.length != sampleCount)
			samples.buffer = new float[sampleCount];
		
		samples.min = getSample(startIndex);
		samples.max = getSample(startIndex);
		
		for(int i = 0; i < sampleCount; i++) {
			float value = getSample(i + startIndex);
			samples.buffer[i] = value;
			if(value < samples.min) samples.min = value;
			if(value > samples.max) samples.max = value;
		}
		
	}
	
	/**
	 * @param value    New raw sample to be converted and then appended to the dataset.
	 */
	public void add(float value) {
		
		value *= conversionFactor;
		
		int currentSize = DatasetsController.getSampleCount();
		int slotNumber = currentSize / DatasetsController.SLOT_SIZE;
		int slotIndex  = currentSize % DatasetsController.SLOT_SIZE;
		if(slotIndex == 0)
			slots[slotNumber] = new Slot();
		slots[slotNumber].setValue(slotIndex, value);
		
		if(isBitfield)
			for(Bitfield bitfield : bitfields)
				bitfield.processValue((int) value, currentSize);
		
	}
	
	/**
	 * @param value    New sample to appended to the dataset. It will be added as-is, not converted.
	 */
	public void addConverted(float value) {
		
		int currentSize = DatasetsController.getSampleCount();
		int slotNumber = currentSize / DatasetsController.SLOT_SIZE;
		int slotIndex  = currentSize % DatasetsController.SLOT_SIZE;
		if(slotIndex == 0)
			slots[slotNumber] = new Slot();
		slots[slotNumber].setValue(slotIndex, value);
		
		if(isBitfield)
			for(Bitfield bitfield : bitfields)
				bitfield.processValue((int) value, currentSize);
		
	}
	
	public MinMax getRange(int firstSampleNumber, int lastSampleNumber) {
		
		MinMax range = new MinMax();
		
		int firstBlock = firstSampleNumber / BLOCK_SIZE;
		int lastBlock = lastSampleNumber / BLOCK_SIZE;
		for(int block = firstBlock; block <= lastBlock; block++) {
			boolean entireBlockInRange = (firstSampleNumber <= block * BLOCK_SIZE) &&
			                             (lastSampleNumber >= (block + 1) * BLOCK_SIZE - 1);
			if(entireBlockInRange) {
				int slotN  = block / (DatasetsController.SLOT_SIZE / BLOCK_SIZE);
				int blockN = block % (DatasetsController.SLOT_SIZE / BLOCK_SIZE);
				float min = slots[slotN].getMinimumInBlock(blockN);
				float max = slots[slotN].getMaximumInBlock(blockN);
				if(min < range.min)
					range.min = min;
				if(max > range.max)
					range.max = max;
			} else {
				int firstSampleInBlock = Integer.max(firstSampleNumber, block * BLOCK_SIZE);
				int lastSampleInBlock = Integer.min(lastSampleNumber, (block + 1) * BLOCK_SIZE - 1);
				for(int sampleN = firstSampleInBlock; sampleN <= lastSampleInBlock; sampleN++) {
					float value = getSample(sampleN);
					if(value < range.min)
						range.min = value;
					if(value > range.max)
						range.max = value;
				}
			}
		}
		
		return range;
		
	}
	
	public FloatBuffer getBuffer(int firstSampleNumber, int lastSampleNumber) {
		
		FloatBuffer buffer = Buffers.newDirectFloatBuffer(lastSampleNumber - firstSampleNumber + 1);
		
		int firstSlot = firstSampleNumber / DatasetsController.SLOT_SIZE;
		int lastSlot = lastSampleNumber / DatasetsController.SLOT_SIZE;
		
		for(int slot = firstSlot; slot <= lastSlot; slot++) {
			
			int slotFirstSampleNumber = slot * DatasetsController.SLOT_SIZE;
			int slotLastSampleNumber = (slot + 1) * DatasetsController.SLOT_SIZE - 1;
			
			int offset = 0;
			if(firstSampleNumber > slotFirstSampleNumber)
				offset = firstSampleNumber - slotFirstSampleNumber;
			
			int length = DatasetsController.SLOT_SIZE;
			if(firstSampleNumber > slotFirstSampleNumber)
				length -= firstSampleNumber - slotFirstSampleNumber;
			if(lastSampleNumber < slotLastSampleNumber)
				length -= slotLastSampleNumber - lastSampleNumber;
			
			slots[slot].getValue(0); // just to ensure it's in RAM
			buffer.put(slots[slot].values, offset, length);
			
		}
		
		buffer.rewind();
		return buffer;
		
	}
	
	public static class MinMax {
		float min;
		float max;
		public MinMax()                     {this.min = Float.MAX_VALUE; this.max = -Float.MAX_VALUE;}
		public MinMax(float min, float max) {this.min = min;             this.max = max;}
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
		
		int previousValue = 0;
		int previousState = 0;
		
		/**
		 * Checks a dataset value to see if this Bitfield has changed state since the previous sample number.
		 * 
		 * @param value           Dataset value.
		 * @param sampleNumber    The current sample number.
		 */
		void processValue(int value, int sampleNumber) {
			
			// don't consider the first sample to be a change of state
			if(sampleNumber == 0) {
				previousValue = value;
				previousState = (value >> LSBit) & bitmask;
				return;
			}
			
			// don't bother testing for a change of state if the dataset value has not changed
			if(value == previousValue)
				return;
			
			// test for a change of state
			int state = (value >> LSBit) & bitmask;
			if(state != previousState) {
				states[state].sampleNumbers.add(sampleNumber);
				previousState = state;
			}
			previousValue = value;
			
		}
		
		/**
		 * @param sampleNumber    Sample number.
		 * @return                State of this bitfield at the specified sample number.
		 */
		int getStateAt(int sampleNumber) {
			int value = (int) Dataset.this.getSample(sampleNumber);
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
			
			String label;                // Example: "Bit 7 = 1" (shown in the PacketBinary.BitfieldPanel.Visualization)
			int value;                   // Example: "1"
			String name;                 // Example: "Some Fault Occurred" (shown on markers on the charts)
			Color color;                 // shown in the PacketBinary.BitfieldPanel
			float[] glColor;             // shown on markers on the charts
			List<Integer> sampleNumbers; // transitioned to the "Some Fault" state at these sample numbers
			Dataset dataset;             // owner of this State
			Bitfield bitfield;           // owner of this State
			
			public State(int value, String label) {
				this.label = label;
				this.value = value;
				this.name = "";
				this.color = Dataset.this.color;
				this.glColor = Dataset.this.glColor;
				sampleNumbers = new ArrayList<Integer>();
				dataset = Dataset.this;
				bitfield = Bitfield.this;
			}
			
			@Override public String toString() {
				return Dataset.this.location + "[" + Bitfield.this.MSBit + ":" + Bitfield.this.LSBit + "]=" + value;
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
