package github.persona_mp3.lib;

import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.io.IOException;

import github.persona_mp3.Std;

public class AsyncLib {
	Std std = new Std();

	RandomAccessFile wal;
	RandomAccessFile index;

	public AsyncLib(RandomAccessFile wal, RandomAccessFile index) {
		this.wal = wal;
		this.index = index;
	}

	// fn append_to_log(raf: &mut RandomAccessFile.Writer, cmd_key_value...String)
	// -> Result<f32, IOError>
	/**
	 * Callers are responsible for updating the memoryIndex
	 * */
	public long appendToLog(String command, String key, String value) throws IOException {
		// where we start writing the log
		long logPointer = wal.length();
		wal.seek(logPointer);
		// <set> <key> <value> $\r\n
		byte[] content = std.encoder(command, key, value);
		wal.write(content);
		return logPointer;
	}

	// fn append_to_index(key: String, value:String) -> Result<(), IOError>
	/**
	 * Callers are responsible for updating the memoryIndex
	 * */
	public void appendToIndex(String key, long logPointer) throws IOException {
		// move to the end of the file
		long eof = index.length();
		index.seek(eof);
		byte[] content = String.format("%s %s\n", key, logPointer).getBytes(StandardCharsets.UTF_8);
		index.write(content);
	}
}
