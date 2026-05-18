package github.persona_mp3.client;

import github.persona_mp3.lib.protocol.Request;
import github.persona_mp3.lib.protocol.Protocol;
import github.persona_mp3.lib.JKVStore;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.io.OutputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.IOException;

import java.net.Socket;

/**
 * Writer OWNS the outputStream. It only takes the connection to
 * check if the connection has been closed, to make sure all resources ie
 * stdin, gets closed after the client disconnects. Writer is not responsible
 * for closing the connnection
 */
public class Writer implements Runnable {
	OutputStream stream;
	Socket conn;

	public Writer(OutputStream stream, Socket conn) {
		this.stream = stream;
		this.conn = conn;
	}

	private Protocol protocol = new Protocol();
	private Logger logger = LogManager.getLogger(Writer.class);

	public void main() throws IOException {
		String input = "";
		String msg = "";
		try (BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));) {

			println("stdin ready");
			while ((input = stdin.readLine()) != null && this.conn.isConnected() && !this.conn.isClosed()) {
				Request req = parseInput(input);
				if (req == null) {
					msg = String.format("Command: %s is not supported. If you want to see manual, enter /help", input);
					eprintln(msg);
					continue;
				}

				byte[] data = protocol.encodeRequest(req);
				stream.write(data);
				stream.flush();

			}
		}
	}

	private Request parseInput(String input) {
		String[] parsedInput = input.split(" ");
		if (parsedInput.length <= 1) {
			return null;
		}

		Request req = new Request(null, null, null);
		switch (parsedInput.length) {
			case 3:
				if (parsedInput[0].equals(JKVStore.SET_COMMAND)) {
					req.command = JKVStore.SET_COMMAND;
					req.key = parsedInput[1];
					req.value = parsedInput[2];
					return req;
				}
				break;

			case 2:
				if (parsedInput[0].equals(JKVStore.GET_COMMAND)) {
					req.command = JKVStore.GET_COMMAND;
					req.key = parsedInput[1];
					return req;
				} else if (parsedInput[0].equals(JKVStore.REMOVE_COMMAND)) {
					req.command = JKVStore.REMOVE_COMMAND;
					req.key = parsedInput[1];
					return req;
				}

		}

		logger.debug("did not undestand input >> {}", input);

		return null;
	}

	@Override
	public void run() {
		logger.info("WriterThread running...");
		try {
			main();
		} catch (Exception err) {
			logger.error("WriterThread error occured. Reason: {}", err.getMessage());
			err.printStackTrace();
		}
	}

	private void println(Object s) {
		System.out.println(s);
	}

	private void eprintln(Object s) {
		System.err.println(s);
	}
}
