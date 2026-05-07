package github.persona_mp3.lib;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.HashMap;
import java.util.Arrays;
import java.nio.file.Paths;
import java.nio.file.*;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import github.persona_mp3.Std;

public class JKVStore {
	Std std = new Std();

	public String VERSION = "0.0.1";

	/// Usage: jkvs get <key>
	public static final String GET_COMMAND = "get";

	/// Usage: jkvs set <key> <value>
	public static final String SET_COMMAND = "set";

	/// Usage: jkvs -V
	public static final String VERSION_COMMAND = "-V";

	/// Usage: jkvs rm <key>
	public static final String REMOVE_COMMAND = "rm";

	private HashMap<String, Long> memoryIndex = new HashMap<>();

	private final Path LOG_DIR = Paths.get("logs");
	private final Path LOG_FILE = LOG_DIR.resolve("log.wal");
	private final Path INDEX_FILE = LOG_DIR.resolve("index");
	private long MAX_SIZE_MB = 1 * 1024;

	private Logger logger = LogManager.getLogger(JKVStore.class);

	JKVLib jkvlib = new JKVLib();

	public void init() throws IOException {
		std.println("initialising store...");

		if (Files.exists(LOG_DIR)
				&& Files.exists(LOG_FILE)
				&& Files.exists(INDEX_FILE)) {
			rebuildStore();
			return;
		} else if (!Files.exists(LOG_DIR)) {
			std.println("Creating log directories");
			Files.createDirectory(LOG_DIR);
		}

	}

	/**
	 * Rebuilds the database, by reading the the index file into the in-memory
	 * hashmap, containting keys and log-pointer offsets<br/>
	 *
	 *
	 * Before each rebuild, it checks the size of the log file, if over a certain
	 * threshold, ie 1MB, it compacts the logs, saves the old logs to the
	 * <bold>past_logs</bold> directory
	 *
	 */
	private void rebuildStore() throws IOException {
		logger.info("rebuliding logs");

		long fileSize = LOG_FILE.toFile().length();
		if (fileSize >= MAX_SIZE_MB) {
			logger.info("log compaction triggered, log-size: {}", fileSize);
			jkvlib.compactLogs();
		}

		memoryIndex = jkvlib.rebuildIndex(INDEX_FILE, " ");
		logger.info("log rebuilt successfully");
		logger.info("{}", memoryIndex);
	}

	public String set(String key, String value) throws IOException {
		logger.debug("set-command: {}::{}",  key , value);
		long logPointer = jkvlib.appendToLogFile(LOG_FILE, SET_COMMAND, key, value);
		jkvlib.appendToIndexFile(INDEX_FILE, key, logPointer);
		memoryIndex.put(key, logPointer);
		return value;
	}

	public String get(String key) throws IOException {
		logger.debug("get-command: {}",  key);
		if (!memoryIndex.containsKey(key)) {
			return null;
		}

		long logPointer = memoryIndex.get(key);

		RandomAccessFile raf = null;

		try {
			raf = new RandomAccessFile(LOG_FILE.toString(), "r");
			raf.seek(logPointer);
			String record = raf.readLine();
			if (record.contains(REMOVE_COMMAND)) {
				std.printf("%s not found\n", key);
				return null;
			}

			String[] parsedLog = record.replaceAll("$\r\n", "").split(" ");

			if (parsedLog.length < 4) {
				std.eprintf("log record parsed to array has unexpected format: %d\n ", parsedLog.length);
				std.eprintf("Log: %s\n", record);
				for (String log : parsedLog) {
					std.eprintf(" %s\n", log);
				}
				throw new RuntimeException("JKVStore.get: Unexpected log format");
			}

			String value = String.join(" ", Arrays.copyOfRange(parsedLog, 2, parsedLog.length - 1));
			return value.replaceAll("\"", "");

		} finally {
			if (raf != null) {
				raf.close();
			}
		}

	}

	public String remove(String key) throws IOException {
		if (!memoryIndex.containsKey(key)) {
			std.printf("%s not found\n", key);
			return null;
		}

		long logPointer = jkvlib.appendToLogFile(LOG_FILE, REMOVE_COMMAND, key, "");
		jkvlib.appendToIndexFile(INDEX_FILE, key, logPointer);

		memoryIndex.put(key, logPointer);
		return key;
	}

}
