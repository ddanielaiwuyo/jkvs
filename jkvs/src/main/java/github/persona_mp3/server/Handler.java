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
					logger.debug("request after readingFromStream, client has possilbly disonnected");
					return;
				}

				Request request = protocol.parseRequest(rawRequest);
				if (!request.isValid) {
					logger.info("recieved an invalid request: {}", request);
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
			logger.error("an error occured, closing connection. Reason: {}", err.getMessage());
			try {
				logger.info("closing connection");
				conn.close();
				err.printStackTrace();
			} catch (IOException e) {
				// NOTE:: this could also be a SocketException which happens
				// because we tried to close the socket during some IO Bound task
				logger.error("could not close connection. Reason: {}", e.getMessage());
				e.printStackTrace();

				logger.error("Parent error");
				err.printStackTrace();
			}
		}

	}

	String processRequest(Request req) throws IOException {
		String ERR_MSG_RES = "service down, we are sorry";

		if (req.command.equals(JKVStore.SET_COMMAND) || req.command.equals(JKVStore.REMOVE_COMMAND)) {
			WriteRequest wq = new WriteRequest(req.command, req.key, req.value);
			logger.info("received a write request {}", req);
			try {
				store.dropItem(wq);
				logger.info("waiting for response...");
				return wq.result.get();
			} catch (ExecutionException err) {
				logger.error("ExecutionException error occured when processingRequsest. Reason: {}", err.getMessage());
				err.printStackTrace();
				return ERR_MSG_RES;
			} catch (InterruptedException err) {
				logger.error("InterruptedException error occured when processingRequsest. Reason: {}", err.getMessage());
				err.printStackTrace();
				return ERR_MSG_RES;

			} catch (Exception err) {
				logger.fatal("Unexpected error occured when processingRequsest. Reason: {}", err.getMessage());
				err.printStackTrace();
				return ERR_MSG_RES;
			}

		} else if (req.command.equals(JKVStore.GET_COMMAND)) {
			logger.info("received read request. {}", req);
			return store.rawGet(req.key, walFile);
		}

		logger.warn("could not process req. Reason: UNKNOWN. {}", req);
		return null;
	}

	@Override
	public void run() {
		handle(conn);
	}

}
