/*
 * Simplified version of Apache Commons IO ReversedLinesFileReader
 *
 * Original work licensed under Apache License 2.0
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Modifications:
 * - Removed builder pattern
 * - Removed Apache Commons dependencies
 * - Simplified encoding handling (UTF-8 only)
 * - Reduced complexity for learning purposes
 */

package jkvs.utils;

import java.io.Closeable;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;

public class SimpleReversedReader implements Closeable {

	private static final int DEFAULT_BLOCK_SIZE = 4096;

	private final RandomAccessFile file;
	private final int blockSize;
	private final long fileLength;

	private long position; // current file pointer (moving backwards)
	private byte[] buffer;
	private int bufferPos;

	public SimpleReversedReader(String path) throws IOException {
		this.file = new RandomAccessFile(path, "r");
		this.blockSize = DEFAULT_BLOCK_SIZE;
		this.fileLength = file.length();

		this.position = fileLength;
		loadBlock();
	}

	private void loadBlock() throws IOException {
		if (position <= 0) {
			buffer = null;
			return;
		}

		int size = (int) Math.min(blockSize, position);
		long start = position - size;

		buffer = new byte[size];
		file.seek(start);
		file.readFully(buffer);

		bufferPos = size - 1;
		position = start;
	}

	public String readLine() throws IOException {
		if (buffer == null)
			return null;

		byte[] lineBuffer = new byte[128];
		int lineLen = 0;

		while (true) {

			if (buffer == null) {
				if (lineLen == 0)
					return null;

				return reverseToString(lineBuffer, lineLen);
			}

			while (bufferPos >= 0) {
				byte b = buffer[bufferPos--];

				if (b == '\n') {
					if (bufferPos >= 0 && buffer[bufferPos] == '\r') {
						bufferPos--;
					} else if (bufferPos < 0) {
						loadBlock();
						if (buffer != null && bufferPos >= 0 && buffer[bufferPos] == '\r') {
							bufferPos--;
						}
					}
					return reverseToString(lineBuffer, lineLen);
				}

				// if (b == '\n') {
				// // skip '\r' if present before '\n'
				// if (bufferPos >= 0 && buffer[bufferPos] == '\r') {
				// bufferPos--;
				// }
				// return reverseToString(lineBuffer, lineLen);
				// }

				// grow buffer if needed
				if (lineLen == lineBuffer.length) {
					byte[] newBuf = new byte[lineBuffer.length * 2];
					System.arraycopy(lineBuffer, 0, newBuf, 0, lineBuffer.length);
					lineBuffer = newBuf;
				}

				lineBuffer[lineLen++] = b;
			}

			loadBlock();
		}
	}

	private String reverseToString(byte[] data, int len) {
		byte[] result = new byte[len];

		for (int i = 0; i < len; i++) {
			result[i] = data[len - 1 - i];
		}

		return new String(result, StandardCharsets.UTF_8);
	}

	@Override
	public void close() throws IOException {
		file.close();
	}
}

// package jkvs.utils;
//
// import java.io.Closeable;
// import java.io.IOException;
// import java.io.RandomAccessFile;
// import java.nio.charset.StandardCharsets;
// import java.util.Arrays;
//
// public class SimpleReversedReader implements Closeable {
//
// private static final int DEFAULT_BLOCK_SIZE = 4096;
//
// private final RandomAccessFile file;
// private final int blockSize;
// private final long fileLength;
//
// private long currentBlock = -1;
// private byte[] leftover = null;
// private byte[] buffer;
//
// private int bufferPos;
//
// public SimpleReversedReader(String path) throws IOException {
// this.file = new RandomAccessFile(path, "r");
// this.blockSize = DEFAULT_BLOCK_SIZE;
// this.fileLength = file.length();
//
// this.currentBlock = (fileLength + blockSize - 1) / blockSize;
// loadBlock();
// }
//
// private void loadBlock() throws IOException {
// if (currentBlock <= 0) {
// buffer = null;
// return;
// }
//
// long start = (currentBlock - 1) * blockSize;
// int length = (int) Math.min(blockSize, fileLength - start);
//
// byte[] newBuffer = new byte[length];
//
// file.seek(start);
// file.readFully(newBuffer);
//
// // attach leftover from previous block
// if (leftover != null) {
// byte[] combined = new byte[newBuffer.length + leftover.length];
// System.arraycopy(newBuffer, 0, combined, 0, newBuffer.length);
// System.arraycopy(leftover, 0, combined, newBuffer.length, leftover.length);
// buffer = combined;
// } else {
// buffer = newBuffer;
// }
//
// bufferPos = buffer.length - 1;
// leftover = null;
// currentBlock--;
// }
//
// public String readLine() throws IOException {
// if (buffer == null) {
// return null;
// }
//
// StringBuilder line = new StringBuilder();
//
// while (true) {
//
// // 🔒 Safety guard BEFORE accessing buffer
// if (buffer == null) {
// if (leftover != null && leftover.length > 0) {
// String result = new String(leftover, StandardCharsets.UTF_8);
// leftover = null;
// return result;
// }
// return null;
// }
//
// while (bufferPos >= 0) {
// byte b = buffer[bufferPos--];
//
// if (b == '\n') {
// return line.reverse().toString();
// }
//
// if (b != '\r') {
// line.append((char) b);
// }
// }
//
// // 🧠 Save what we've read so far BEFORE loading next block
// byte[] currentLineBytes =
// line.reverse().toString().getBytes(StandardCharsets.UTF_8);
//
// // Combine with previous leftover (important fix)
// if (leftover != null) {
// byte[] combined = new byte[currentLineBytes.length + leftover.length];
// System.arraycopy(currentLineBytes, 0, combined, 0, currentLineBytes.length);
// System.arraycopy(leftover, 0, combined, currentLineBytes.length,
// leftover.length);
// leftover = combined;
// } else {
// leftover = currentLineBytes;
// }
//
// line.setLength(0);
//
// // 📦 Load next block
// loadBlock();
//
// // 🛑 Start of file reached
// if (buffer == null) {
// if (leftover != null && leftover.length > 0) {
// String result = new String(leftover, StandardCharsets.UTF_8);
// leftover = null;
// return result;
// }
// return null;
// }
//
// // 🔄 Restore partial line WITHOUT reversing again
// line.append(new String(leftover, StandardCharsets.UTF_8));
// leftover = null;
// }
// }
// // public String readLine() throws IOException {
// // if (buffer == null)
// // return null;
// //
// // StringBuilder line = new StringBuilder();
// //
// // while (true) {
// // while (bufferPos >= 0) {
// // byte b = buffer[bufferPos--];
// //
// // if (b == '\n') {
// // return line.reverse().toString();
// // }
// //
// // if (b != '\r') { // ignore Windows CR
// // line.append((char) b);
// // }
// // }
// //
// // // reached start of buffer
// // leftover = line.reverse().toString().getBytes(StandardCharsets.UTF_8);
// // line.setLength(0);
// //
// // loadBlock();
// //
// // if (buffer == null) {
// // // start of file
// // if (leftover != null && leftover.length > 0) {
// // return new String(leftover, StandardCharsets.UTF_8);
// // }
// // return null;
// // }
// //
// // // restore line content
// // System.out.printf("inner-lib::pointerException?? %s\n",
// leftover.toString());
// // line.append(new String(leftover, StandardCharsets.UTF_8)).reverse();
// // }
// // }
//
// @Override
// public void close() throws IOException {
// file.close();
// }
// }
