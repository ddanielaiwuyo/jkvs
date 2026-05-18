package github.persona_mp3.server;

import github.persona_mp3.lib.JKVStore;
import github.persona_mp3.lib.types.WriteRequest;
import github.persona_mp3.lib.protocol.Protocol;
import github.persona_mp3.lib.protocol.Request;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.io.*;
import java.net.Socket;

import java.util.concurrent.ExecutionException;

public class Handler implements Runnable {
	private Logger logger = LogManager.getLogger(Server.class);
	private Protocol protocol = new Protocol();
	private int MAX_PAYLOAD = 1 * 1024 * 1024;
	private RandomAccessFile walFile;
	Socket conn;
	JKVStore store;

	public Handler(Socket conn, JKVStore store, RandomAccessFile readOnlyFile) {
		this.conn = conn;
		this.store = store;
		this.walFile = readOnlyFile;
	}

	void handle(Socket conn) {
		try (
				DataInputStream reader = new DataInputStream(conn.getInputStream());
				OutputStream writer = new BufferedOutputStream(conn.getOutputStream(), 8000);) {

			String rawRequest = "";
			String response = "";
			byte[] rawResponse = null;

			while (conn.isConnected() && !conn.isClosed()) {
				rawRequest = protocol.readFromStream(MAX_PAYLOAD, reader);
				if (rawRequest == null) {
					logger.debug("request is null? {}", rawRequest);
					return;
				}

				Request request = protocol.parseRequest(rawRequest);
				if (!request.isValid) {
					logger.debug("request isnt valid -> {}", request);
					rawResponse = protocol.encodeResponse("what do you mean?");
					writer.write(rawResponse);
					writer.flush();
					continue;
				}

				response = processRequest(request);
				rawResponse = protocol.encodeResponse(response);
				writer.write(rawResponse);
				writer.flush();

			}
		} catch (Exception err) {
			try {
				conn.close();
				// logger.error("an error occured, closing conn" );
			} catch (Exception e) {
				logger.error("could not close connection::", e.getMessage());
				e.printStackTrace();
			}
		}

	}

	String processRequest(Request req) throws IOException {
		if (req.command.equals(JKVStore.SET_COMMAND) || req.command.equals(JKVStore.REMOVE_COMMAND)) {
			WriteRequest wq = new WriteRequest(req.command, req.key, req.value);
			// todo: implement a poision_pill to tell the thread to stop reading
			// will need to do some sort of Future/await thing here, not sure yet?
			logger.info("write_request:: {}", req);
			try {
				store.dropItem(wq);
				logger.info("waiting for response...");
				return wq.result.get();
				// return "response_response";
			} catch (ExecutionException err) {
				logger.error("ExecutionException error occured when processingRequsest\nReason: {}", err.getMessage());
				err.printStackTrace();
				return "service is down, we are sorry ";
			} catch (InterruptedException err) {
				logger.error("InterruptedException rror occured when processingRequsest\nReason: {}", err.getMessage());
				err.printStackTrace();
				return "service is down, we are sorry ";

			} catch (Exception err) {
				logger.fatal("Unexpected error occured when processingRequsest\nReason: {}", err.getMessage());
				err.printStackTrace();
				return "service is down, we are sorry ";
			}
		} else if (req.command.equals(JKVStore.GET_COMMAND)) {
			logger.info("read_request:: {}", req);
			return store.rawGet(req.key, walFile);
		}

		logger.warn("could not process req. Reason: NOT_SUPPORTED. {}", req);
		return null;
	}

	@Override
	public void run() {
		handle(conn);
	}

}
