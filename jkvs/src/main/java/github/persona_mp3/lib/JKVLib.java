package github.persona_mp3.lib;

import java.io.*;
import java.nio.file.*;
import java.util.HashMap;

import java.util.concurrent.ConcurrentHashMap;

import java.util.UUID;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import github.persona_mp3.Std;
import github.persona_mp3.lib.utils.*;

public class JKVLib {
	// Might want to make a Configure class for this later on to extend this ie
	// Configure { String: pastLogs, String: nameFormat ...}
	public Path ARCHIVED_LOGS_DIR = Path.of("archived_logs");
	Std std = new Std();

	private Logger logger = LogManager.getLogger(JKVLib.class);

	public ConcurrentHashMap<String, Long> rebuildIndex(Path indexFile, String delimiter) throws IOException {
		logger.info("Rebuilding index");
		// HashMap<String, Long> memoryIndex = new HashMap<>();
		ConcurrentHashMap<String, Long> memoryIndex = new ConcurrentHashMap<>();

		BufferedReader br = null;
		try {
			br = Files.newBufferedReader(indexFile);
			String log = "";

			while ((log = br.readLine()) != null) {
				// [key, logPointer]
				String[] parsedLog = log.split(delimiter);
				if (parsedLog.length != 2) {
					System.err.printf("Unexpected log format:: Expected two values in pair, got %d\n", parsedLog.length);
					System.err.println(parsedLog);
					throw new RuntimeException("Unexpected log format");
				}

				String key = parsedLog[0];
				Long logPointer = Long.parseLong(parsedLog[1]);

				memoryIndex.put(key, logPointer);
			}

			logger.info("Finished rebuilding index");
			return memoryIndex;
		} finally {
			if (br != null) {
				br.close();
			}
		}

	}

	public long appendToLogFile(Path logFile, String command, String key, String value) throws IOException {
		RandomAccessFile raf = null;
		try {
			raf = new RandomAccessFile(logFile.toString(), "rw");
			// Start at the end of the file to append the new log
			long logPointer = raf.length();
			raf.seek(raf.length());

			// <set> <key> <value> $\r\n
			byte[] content = std.encoder(command, key, value);
			raf.write(content);

			return logPointer;
		} finally {
			if (raf != null) {
				raf.close();
			}
		}
	}

	public void appendToIndexFile(Path indexFile, String key, long logPointer) throws IOException {
		RandomAccessFile raf = null;
		try {
			raf = new RandomAccessFile(indexFile.toString(), "rw");
			raf.seek(raf.length());

			byte[] content = String.format("%s %s\n", key, logPointer).getBytes();
			raf.write(content);
		} finally {
			if (raf != null) {
				raf.close();
			}
		}

	}

	public void compactLogs(Path src, Path index) throws IOException {
		logger.info("compacting logs from src: {} and {}", src, index);

		ConcurrentHashMap<String, String> compactedLogs = parseLog(src);
		if (compactedLogs == null) {
			return;
		}

		if (!Files.exists(ARCHIVED_LOGS_DIR)) {
			logger.info("{} doesn't exist to store old logs, creating one now", ARCHIVED_LOGS_DIR);
			Files.createDirectory(ARCHIVED_LOGS_DIR);
		}

		// Create the top-level-dir to save the name of the log file
		Path dirName = generateLogName("log");
		logger.info("saving logs under {}", dirName);

		if (Files.exists(dirName)) {
			logger.warn("{} already exists! Renaming path");
			dirName = generateLogName("collision");
		}

		Files.createDirectory(dirName);

		// Move the src and index files to that place
		Path srcTarget = dirName.resolve(src.getFileName());
		Files.move(src, srcTarget, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		logger.info("Successfully moved {} to {}", src, srcTarget);

		Path indexTarget = dirName.resolve(index.getFileName());
		Files.move(index, indexTarget, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		logger.info("Successfully moved {} to {}", index, indexTarget);

		logger.info("Deleting old files {} and {}", src, index);
		Files.deleteIfExists(src);
		Files.deleteIfExists(index);
		Files.createFile(src);
		Files.createFile(index);

		logger.info("Now writing compacted logs to {}", src);
		// Start writing to the file
		try (
				RandomAccessFile raf = new RandomAccessFile(src.toString(), "rw");) {

			long offset = raf.getFilePointer();
			for (String key : compactedLogs.keySet()) {
				String record = compactedLogs.get(key);
				String formattedLog = record + "\r\n";
				raf.write(formattedLog.getBytes());
				appendToIndexFile(index, key, offset);
				offset = raf.getFilePointer();
			}

			logger.info("New log size: {}", raf.length());
		}
	}

	private ConcurrentHashMap<String, String> parseLog(Path src) throws IOException {
		final int MIN_LENGTH_OF_PARSED_LOG = 4;
		ConcurrentHashMap<String, String> compactedLogs = new ConcurrentHashMap<String, String>();

		int diff = 0;
		try (
				ReversedReader reader = new ReversedReader(src.toString());) {
			logger.info("reading {}", src);
			String log = "";

			while ((log = reader.readLine()) != null) {
				if (log.isEmpty() || log.isBlank()) {
					continue;
				}

				String[] parsedLog = log.split(" ");
				if (parsedLog.length < MIN_LENGTH_OF_PARSED_LOG) {

					logger.error("Bad log: {}", log);
					throw new RuntimeException(String.format(
							"JKVSLib::compact_log:: Length of parsed log is less than the MIN %d\n\n" +
									"Possible Reason:\n" +
									"1. Logs may have been corrupted\n" +
									"2. Codec may have changed\n" +
									"3. Distributed systems behaving like distributed systems (we don't know the reason)",
							MIN_LENGTH_OF_PARSED_LOG));
				}

				String key = parsedLog[1];
				if (compactedLogs.containsKey(key)) {
					diff += 1;
					logger.debug("{} has already been staged", key);
				}
				compactedLogs.putIfAbsent(key, log);
			}

			if (diff == 0) {
				logger.info("No need for compaction, all values are unique. Diff={}", diff);
				return null;
			}

			return compactedLogs;
		}

	}

	/**
	 * Log names are generated in this format: prefix-uuid_dateStamp
	 */
	private Path generateLogName(String prefix) {
		// using ISO Date format with time
		String logId = UUID.randomUUID().toString();
		LocalDateTime now = LocalDateTime.now();
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
		String logName = String.format("%s-%s_%s", logId, prefix, now.format(formatter));
		return ARCHIVED_LOGS_DIR.resolve(logName);
	}

}
