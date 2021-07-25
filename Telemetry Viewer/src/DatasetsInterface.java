import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Charts and exportFiles() must access a connection's datasets through a "DatasetsInterface" which automatically manages caching.
 * 
 * All interactions are thread-safe *if* each thread creates its own DatasetsInterface,
 * therefore the charts and exportFiles() should each create their own DatasetsInterface.
 */
public class DatasetsInterface {
	
	public List<Dataset> normalDatasets = new ArrayList<>();
	public List<Dataset.Bitfield.State> edgeStates = new ArrayList<>();
	public List<Dataset.Bitfield.State> levelStates = new ArrayList<>();
	public ConnectionTelemetry connection = null;
	private Map<Dataset, StorageFloats.Cache> caches = new HashMap<>();
	
	/**
	 * Sets the normal (non-bitfield) datasets that can be subsequently accessed, replacing any existing ones.
	 * 
	 * @param newDatasets    Normal datasets to use.
	 */
	public void setNormals(List<Dataset> newDatasets) {
		
		// update normal datasets
		normalDatasets.clear();
		normalDatasets.addAll(newDatasets);
		
		// update caches
		caches.clear();
		normalDatasets.forEach(dataset -> caches.put(dataset, dataset.createCache()));
		edgeStates.forEach(state -> caches.put(state.dataset, state.dataset.createCache()));
		levelStates.forEach(state -> caches.put(state.dataset, state.dataset.createCache()));
		
		// update the connection
		connection = !normalDatasets.isEmpty() ? normalDatasets.get(0).connection :
		             !edgeStates.isEmpty()     ? edgeStates.get(0).connection :
		             !levelStates.isEmpty()    ? levelStates.get(0).connection :
		                                         null;
		
	}
	
	/**
	 * Sets the bitfield edge states that can be subsequently accessed, replacing any existing ones.
	 * 
	 * @param newEdges    Bitfield edge states to use.
	 */
	public void setEdges(List<Dataset.Bitfield.State> newEdges) {
		
		// update edge datasets
		edgeStates.clear();
		edgeStates.addAll(newEdges);
		
		// update caches
		caches.clear();
		normalDatasets.forEach(dataset -> caches.put(dataset, dataset.createCache()));
		edgeStates.forEach(state -> caches.put(state.dataset, state.dataset.createCache()));
		levelStates.forEach(state -> caches.put(state.dataset, state.dataset.createCache()));
		
		// update the connection
		connection = !normalDatasets.isEmpty() ? normalDatasets.get(0).connection :
		             !edgeStates.isEmpty()     ? edgeStates.get(0).connection :
		             !levelStates.isEmpty()    ? levelStates.get(0).connection :
		                                         null;
		
	}
	
	/**
	 * Sets the bitfield level states that can be subsequently accessed, replacing any existing ones.
	 * 
	 * @param newLevels    Bitfield level states to use.
	 */
	public void setLevels(List<Dataset.Bitfield.State> newLevels) {
		
		// update edge datasets
		levelStates.clear();
		levelStates.addAll(newLevels);
		
		// update caches
		caches.clear();
		normalDatasets.forEach(dataset -> caches.put(dataset, dataset.createCache()));
		edgeStates.forEach(state -> caches.put(state.dataset, state.dataset.createCache()));
		levelStates.forEach(state -> caches.put(state.dataset, state.dataset.createCache()));
		
		// update the connection
		connection = !normalDatasets.isEmpty() ? normalDatasets.get(0).connection :
		             !edgeStates.isEmpty()     ? edgeStates.get(0).connection :
		             !levelStates.isEmpty()    ? levelStates.get(0).connection :
		                                         null;
		
	}
	
	/**
	 * @param datasetN    Index of a normal dataset (this is NOT the dataset location, but the setNormals() list index.)
	 * @return            Corresponding normal dataset.
	 */
	public Dataset getNormal(int datasetN) {
		return normalDatasets.get(datasetN);
	}
	
	/**
	 * @param dataset    Dataset to check for.
	 * @return           True if this object references the Dataset (as a normal/edge/level.)
	 */
	public boolean contains(Dataset dataset) {
		return caches.keySet().contains(dataset);
	}
	
	/**
	 * @return    True if any normal datasets have been selected.
	 */
	public boolean hasNormals() {
		return !normalDatasets.isEmpty();
	}
	
	/**
	 * @return    True if any bitfield edges have been selected.
	 */
	public boolean hasEdges() {
		return !edgeStates.isEmpty();
	}
	
	/**
	 * @return    True if any bitfield levels have been selected.
	 */
	public boolean hasLevels() {
		return !levelStates.isEmpty();
	}
	
	/**
	 * @return    True if any normals/edges/levels have been selected.
	 */
	public boolean hasAnyType() {
		return connection != null;
	}
	
	/**
	 * @return    Number of normal datasets that have been selected.
	 */
	public int normalsCount() {
		return normalDatasets.size();
	}
	
	/**
	 * Gets a sample as a float32.
	 * 
	 * @param dataset         Dataset.
	 * @param sampleNumber    Sample number.
	 * @return                The sample, as a float32.
	 */
	public float getSample(Dataset dataset, int sampleNumber) {
		
		StorageFloats.Cache cache = cacheFor(dataset);
		return dataset.getSample(sampleNumber, cache);
		
	}
	
	/**
	 * Gets a sample as a String.
	 * 
	 * @param dataset         Dataset.
	 * @param sampleNumber    Sample number.
	 * @return                The sample, as a String.
	 */
	public String getSampleAsString(Dataset dataset, int sampleNumber) {
		
		StorageFloats.Cache cache = cacheFor(dataset);
		return dataset.getSampleAsString(sampleNumber, cache);
		
	}
	
	/**
	 * Gets a sequence of samples as a float[].
	 * 
	 * @param dataset            Dataset.
	 * @param minSampleNumber    First sample number, inclusive.
	 * @param maxSampleNumber    Last sample number, inclusive.
	 * @return                   A float[] of the samples.
	 */
	public float[] getSamplesArray(Dataset dataset, int minSampleNumber, int maxSampleNumber) {
		
		StorageFloats.Cache cache = cacheFor(dataset);
		return dataset.getSamplesArray(minSampleNumber, maxSampleNumber, cache);
		
	}

	/**
	 * Gets a sequence of samples as a FloatBuffer.
	 * 
	 * @param dataset            Dataset.
	 * @param minSampleNumber    First sample number, inclusive.
	 * @param maxSampleNumber    Last sample number, inclusive.
	 * @return                   A FloatBuffer of the samples.
	 */
	public FloatBuffer getSamplesBuffer(Dataset dataset, int minSampleNumber, int maxSampleNumber) {
		
		StorageFloats.Cache cache = cacheFor(dataset);
		return dataset.getSamplesBuffer(minSampleNumber, maxSampleNumber, cache);
		
	}
	
	/**
	 * Gets the range (y-axis region) occupied by all of the normal datasets.
	 *  
	 * @param minSampleNumber    Minimum sample number, inclusive.
	 * @param maxSampleNumber    Maximum sample number, inclusive.
	 * @return                   The range, as [0] = minY, [1] = maxY.
	 *                           If there are no normal datasets, [-1, 1] will be returned.
	 *                           If the range is a single value, [value +/- 0.001] will be returned.
	 */
	public float[] getRange(int minSampleNumber, int maxSampleNumber) {
		
		float[] minMax = new float[] {Float.MAX_VALUE, -Float.MAX_VALUE};

		normalDatasets.forEach(dataset -> {
			if(!dataset.isBitfield) {
				StorageFloats.MinMax range = dataset.getRange((int) minSampleNumber, (int) maxSampleNumber, cacheFor(dataset));
				if(range.min < minMax[0])
					minMax[0] = range.min;
				if(range.max > minMax[1])
					minMax[1] = range.max;
			}
		});
		
		if(minMax[0] == Float.MAX_VALUE && minMax[1] == -Float.MAX_VALUE) {
			minMax[0] = -1;
			minMax[1] = 1;
		} else if(minMax[0] == minMax[1]) {
			float value = minMax[0];
			minMax[0] = value - 0.001f;
			minMax[1] = value + 0.001f;
		}
		
		return minMax;
		
	}
	
	/**
	 * Gets the range (y-axis region) occupied by a specific normal dataset.
	 * 
	 * @param dataset            Dataset to check.
	 * @param minSampleNumber    Minimum sample number, inclusive.
	 * @param maxSampleNumber    Maximum sample number, inclusive.
	 * @return                   The range, as [0] = minY, [1] = maxY.
	 *                           If there are no normal datasets, [-1, 1] will be returned.
	 *                           If the range is a single value, [value +/- 0.001] will be returned.
	 */
	public float[] getRange(Dataset dataset, int minSampleNumber, int maxSampleNumber) {
		
		float[] minMax = new float[] {Float.MAX_VALUE, -Float.MAX_VALUE};

		if(!dataset.isBitfield) {
			StorageFloats.MinMax range = dataset.getRange((int) minSampleNumber, (int) maxSampleNumber, cacheFor(dataset));
			if(range.min < minMax[0])
				minMax[0] = range.min;
			if(range.max > minMax[1])
				minMax[1] = range.max;
		}
		
		if(minMax[0] == Float.MAX_VALUE && minMax[1] == -Float.MAX_VALUE) {
			minMax[0] = -1;
			minMax[1] = 1;
		} else if(minMax[0] == minMax[1]) {
			float value = minMax[0];
			minMax[0] = value - 0.001f;
			minMax[1] = value + 0.001f;
		}
		
		return minMax;
		
	}
	
	/**
	 * Iterates over all selected normal datasets.
	 * 
	 * @param consumer    BiConsumer that accepts a dataset and its corresponding samples cache.
	 */
	public void forEachNormal(BiConsumer<Dataset, StorageFloats.Cache> consumer) {
		for(int i = 0; i < normalDatasets.size(); i++) {
			Dataset dataset = normalDatasets.get(i);
			StorageFloats.Cache cache = caches.get(dataset);
			consumer.accept(dataset, cache);
		}
	}
	
	/**
	 * Checks for any bitfield edge events and iterates over them.
	 * 
	 * @param minSampleNumber    First sample number to check, inclusive.
	 * @param maxSampleNumber    Last sample number to check, inclusive.
	 * @param consumer           BiConsumer that accepts a bitfield state and its corresponding edge event sample number.
	 */
	public void forEachEdge(int minSampleNumber, int maxSampleNumber, BiConsumer<Dataset.Bitfield.State, Integer> consumer) {
		
		edgeStates.forEach(state -> {
			state.getEdgeEventsBetween(minSampleNumber, maxSampleNumber, cacheFor(state.dataset)).forEach(eventSampleNumber -> {
				consumer.accept(state, eventSampleNumber);
			});
		});
		
	}
	
	/**
	 * Checks for any bitfield levels and iterates over them.
	 * 
	 * @param minSampleNumber    First sample number to check, inclusive.
	 * @param maxSampleNumber    Last sample number to check, inclusive.
	 * @param consumer           BiConsumer that accepts a bitfield state and its corresponding sample number range ([0] = start, [1] = end.)
	 */
	public void forEachLevel(int minSampleNumber, int maxSampleNumber, BiConsumer<Dataset.Bitfield.State, int[]> consumer) {
		
		levelStates.forEach(state -> {
			state.getLevelsBetween(minSampleNumber, maxSampleNumber, cacheFor(state.dataset)).forEach(range -> {
				consumer.accept(state, range);
			});
		});
		
	}
	
	/**
	 * @param dataset    Dataset (normal/edge/level.)
	 * @return           Corresponding samples cache.
	 */
	private StorageFloats.Cache cacheFor(Dataset dataset) {
		
		return caches.get(dataset);
		
	}
	
}