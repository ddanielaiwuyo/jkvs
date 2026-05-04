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

		std.println("initialising log directory..\n");
		Files.createDirectories(LOG_DIR);
	}

	public String set(String key, String value) throws IOException {
		long log_pointer = kvlib.append_to_log(WAL_FILE, SET_COMMAND, key, value);
		kvlib.append_to_index(INDEX_FILE, key, log_pointer);

		std.println("written to index file");
		return values.put(key, value);
	}

	/// Returns null when record for key is not found
	public String get(String key) {
		return values.get(key);
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
			kvlib.append_to_index(INDEX_FILE, REMOVE_COMMAND, log_pointer);

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
