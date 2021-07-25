// TODO: update this test and write more tests

//import static org.junit.jupiter.api.Assertions.*;
//
//import java.nio.FloatBuffer;
//import java.nio.file.FileAlreadyExistsException;
//import java.nio.file.Files;
//import java.nio.file.Paths;
//import java.util.ArrayList;
//import java.util.List;
//import java.util.Random;
//import java.util.stream.Stream;
//
//import org.junit.jupiter.api.AfterEach;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.params.ParameterizedTest;
//import org.junit.jupiter.params.provider.Arguments;
//import org.junit.jupiter.params.provider.MethodSource;

class StorageTimestampsTest {
	
//	/**
//	 * Possible tests:
//	 * 
//	 * - Can't read sample(s) from empty.
//	 * - Can't read sample(s) in excess of existing data.
//	 * - Can't read negative sample numbers.
//	 * - getFirstTimestamp() is correct.
//	 * - Sample(s) match inserted data.
//	 * - Flushing to disk at different points does not cause problems.
//	 * - Flushing to disk frees up memory.
//	 * - Writing from multiple threads works correctly.
//	 * 
//	 */
//	
//	final int SAMPLE_COUNT = 16 * (int) Math.pow(2, 20); // 16M
//
//	static int[] riskyNumbers() {
//		return new int[] {
//			1,
//			StorageFloats.BLOCK_SIZE - 1,
//			StorageFloats.BLOCK_SIZE,
//			StorageFloats.BLOCK_SIZE + 1,
//			StorageFloats.SLOT_SIZE - 1,
//			StorageFloats.SLOT_SIZE,
//			StorageFloats.SLOT_SIZE + 1,
//			3*StorageFloats.SLOT_SIZE - 1,
//			3*StorageFloats.SLOT_SIZE,
//			3*StorageFloats.SLOT_SIZE + 1,
//			4*StorageFloats.SLOT_SIZE - 1,
//			4*StorageFloats.SLOT_SIZE,
//			4*StorageFloats.SLOT_SIZE + 1,
//		};
//	}
//	
//	ConnectionTelemetry connection;
//	StorageTimestamps DUT;
//	StorageTimestamps.Cache cache;
//	long[] timestamps;
//	
//	@BeforeEach
//	void prepare() {
//		
//		try { Files.createDirectory(Paths.get("cache")); } catch(FileAlreadyExistsException e) {} catch(Exception e) { e.printStackTrace(); }
//		connection = new ConnectionTelemetry("Demo Mode");
//		DUT = new StorageTimestamps(connection);
//		cache = DUT.createCache();
//		
//		timestamps = new long[SAMPLE_COUNT];
//		Random rng = new Random();
//		for(int i = 0; i < SAMPLE_COUNT; i++)
//			timestamps[i] = rng.nextLong();
//		
//		for(int sampleN = 0; sampleN < SAMPLE_COUNT; sampleN++)
//			DUT.appendTimestamp(timestamps[sampleN]);
//		
//	}
//
//	@DisplayName(value = "Individual Timestamps")
//	@ParameterizedTest(name = "Flushing after every {0} samples")
//	void individualTimestamps() {
//
//		for(int sampleN = 0; sampleN < SAMPLE_COUNT; sampleN += 16) {
//			assertTrue(DUT.getTimestamp(sampleN) == timestamps[sampleN]);
//		}
//
//	}
//	
//	@DisplayName(value = "Timestamp Blocks, Varying Block Sizes / Offsets")
//	@ParameterizedTest(name = "Reading {0} bytes at offset {1}")
//	@MethodSource("riskyNumbersPair")
//	void blocksOfTimestamps(int blockSize, int offset) {
//		
//		FloatBuffer buffer = DUT.getTampstamps(offset, offset + blockSize - 1, cache, 0);
//		for(int i = 0; i < blockSize; i++)
//			assertTrue(buffer.get(i) == (float) timestamps[offset + i]);
//		
//	}
//	
//	static Stream<Arguments> riskyNumbersPair() {
//		
//		List<Arguments> list = new ArrayList<Arguments>();
//		for(int x : riskyNumbers())
//			for(int y : riskyNumbers())
//				list.add(Arguments.of(x, y));
//		
//		return list.stream();
//		
//	}
//	
//	@AfterEach
//	void deleteCacheFiles() {
//		
//		DUT.dispose();
//		connection.dispose();
//		
//	}

}
