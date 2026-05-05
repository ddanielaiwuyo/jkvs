package jkvs.lib;

import java.io.*;
import java.nio.file.*;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import jkvs.Std;

public class KVStore {

	private HashMap<String, String> values;
	private final Path LOG_DIR = Paths.get("logs");
	private final Path INDEX_FILE = LOG_DIR.resolve("index.txt");
	private final Path WAL_FILE = LOG_DIR.resolve("log.wal");

	public static final String GET_COMMAND = "get";
	public static final String SET_COMMAND = "set";
	public static final String REMOVE_COMMAND = "rm";
	public static final String VERSION_COMMAND = "-V";

	Std std = new Std();
	KVLib kvlib = new KVLib();

	public void init() throws IOException {
		this.values = new HashMap<String, String>();

		if (Files.exists(LOG_DIR)) {
			return;
		}
		std.println("creating log directories");
		Files.createDirectories(LOG_DIR);
	}

	public String set(String key, String value) throws IOException {
		long log_pointer = kvlib.append_to_log(WAL_FILE, SET_COMMAND, key, value);
		kvlib.append_to_index(INDEX_FILE, key, log_pointer);

		std.println("written to index file");
		return values.put(key, value);
	}

	/// Returns null when record for key is not found
	public String get(String key) throws IOException {

		try (RandomAccessFile raf = new RandomAccessFile(WAL_FILE.toString(), "r")) {
			HashMap<String, Long> memory_index = kvlib.rebuild_index(INDEX_FILE, " ");
			if (!memory_index.containsKey(key)) {
				return null;
			}

			Long log_pointer = memory_index.get(key);
			raf.seek(log_pointer);
			String record = raf.readLine();

			std.println(memory_index);
			if (record.contains(REMOVE_COMMAND)) {
				std.println("not found");
				return null;
			}

			// todo: This is fragile, but aslong as the encoder stays predictable and
			// correct, this is safe
			String[] parsed = record.replaceAll("$\r\n", "").split(" ");
			if (parsed.length < 4) {
				std.eprintf("log record formatted to array has unexpected format: %d\n ", parsed.length);
				for (String value : parsed) {
					std.eprintf(" %s\n", value);
				}
				throw new RuntimeException("KVStore::get:: Unexpected log format error");
			}

			return parsed[2].replaceAll("\"", "");
		} catch (Exception err) {
			std.eprintln("KVStore::get:: Error");
			throw new RuntimeException(err);
		}

	}

	public String remove(String key) throws IOException {
		try {
			HashMap<String, Long> index = kvlib.rebuild_index(INDEX_FILE, " ");
			std.println("  rebuilt index -> ");
			std.println(index);

			if (!index.containsKey(key)) {
				std.printf("%s doesn't exist\n", key);
				return null;
			}

			long log_pointer = kvlib.append_to_log(WAL_FILE, REMOVE_COMMAND, key, "");
			kvlib.append_to_index(INDEX_FILE, key, log_pointer);
			// how do we actually want to remove an entry from the log file?
			// because if we manually delete a value from the log file on every rm
			// command, we have to always re-write whole data to io, which is slow?
			// Or when someones does a get, do we just check if a 'rm' command exists for
			// that key?
			// set userrname persona
			// set langugae java
			// set language rust
			// rm language
			//
			// get language
			// -> buffer the whole file, and search each line,
			// -> if rm && language exists, return null
			//
			// -> but what if set language was done again, then we'd just have to read the
			// whole file
			// -> so we can internally have a code of 1 as deleted, and 0 as exists, im not
			// sure
			//
			// So we can read the file backwards, to avoid that, if we see a rm <key> first,
			// we return null
			// otherwise we just return the value, but since we already have an index, 
			// we just build it, and check the last entry for that key

		} catch (Exception err) {
			throw new RuntimeException(err);
		}

		return values.remove(key);
	}

	public void debug() {
		for (String key : values.keySet()) {
			System.out.println("  kv_store debug");
			System.out.printf("   %s :: %s\n", key, values.get(key));
		}
	}
}
