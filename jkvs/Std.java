package jkvs;

import java.io.*;
import java.nio.file.*;
import java.util.Comparator;
import java.util.stream.Stream;

public class Std {
	public void println(Object s) {
		System.out.println(s);
	}

	public void debug(Object s) {
		System.out.println(" DEBUG:: " + s);
	}

	public void printf(String fmt, Object... s) {
		System.out.printf(fmt, s);
	}

	public void eprintln(Object s) {
		System.err.println(s);
	}

	public void eprintf(String fmt, Object... s) {
		System.err.printf(fmt, s);
	}

	/// Delimiter used is $\r\n, at the moment this is just for testing purposes and
	/// will most likely evolve
	///
	///
	/// The whole format is encoded as <cmd> <key> " <value> " $\r\n The <value> is
	/// wrappred in quotes because if it contains whitespaces it can be read as a
	/// single value
	public byte[] encoder(String cmd, String key, String value) {
		return String.format("%s %s \"%s\" $\r\n", cmd, key, value).getBytes();
	}

	public void deleteRecursively(Path path) throws IOException {
		if (!Files.exists(path))
			return;

		try (Stream<Path> walk = Files.walk(path)) {
			walk.sorted(Comparator.reverseOrder())
					.forEach(p -> {
						try {
							Files.delete(p);
						} catch (IOException e) {
							throw new RuntimeException("Failed to delete: " + p, e);
						}
					});
		}
	}
}
