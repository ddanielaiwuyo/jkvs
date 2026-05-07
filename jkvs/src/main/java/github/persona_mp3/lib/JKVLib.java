package github.persona_mp3.lib;

import java.io.*;
import java.nio.file.*;
import java.util.HashMap;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import github.persona_mp3.Std;

public class JKVLib {
	// Might want to make a Configure class for this later on to extend this ie
	// Configure { String: pastLogs, String: nameFormat ...}
	public Path PAST_LOGS_DIR = Path.of("past_logs");
	Std std = new Std();

	private Logger logger = LogManager.getLogger(JKVLib.class);

	public HashMap<String, Long> rebuildIndex(Path indexFile, String delimiter) throws IOException {
		logger.info("Rebuilding index");
		HashMap<String, Long> memoryIndex = new HashMap<>();

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

	public void compactLogs() throws IOException {
		logger.info("compacting logs");
	}

}
