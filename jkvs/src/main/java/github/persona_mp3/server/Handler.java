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
	private static Logger logger = LogManager.getLogger(Server.class);
	private static Protocol protocol = new Protocol();
	private static int MAX_PAYLOAD = 1 * 1024 * 1024;

	static void handle(Socket conn) {
		try (
				// OutputStream writer = conn.getOutputStream();
				DataInputStream reader = new DataInputStream(conn.getInputStream());
				OutputStream writer  =new BufferedOutputStream(conn.getOutputStream(), 8000);
				) {
			
			// So the issue here was that earlier on, the author wanted clients to
			// communicate
			// via a shell, so the server would still read after a single request
			// However, with a larger amount of connections, closing the client doesnt mean
			// the server is done, which is also the puzzlingg thing
			// But I also think the client is teh problem, because it nolonger writes but
			// reads,
			// and the server also reads at the same time, so no-one is writing, but reading
			// from each other. Network Deadlock
			// Also taking a look at the gocode, conn.Read(buffer) reads until the buffer is
			// full, so a simple string will most likely not fill 1024
			conn.setSoTimeout(1000 * 1);
			String rawRequest = "";
			String response = "";
			byte[] rawResponse = null;

			while (conn.isConnected() && !conn.isClosed()) {
				rawRequest = protocol.readFromStream(MAX_PAYLOAD, reader);
				if (rawRequest == null) {
					return;
				}

				Request request = protocol.parseRequest(rawRequest);
				if (!request.isValid) {
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

	static String processRequest(Request req) throws IOException {
		if (req.command.equals(JKVStore.SET_COMMAND) || req.command.equals(JKVStore.REMOVE_COMMAND)) {
			WriteRequest wq = new WriteRequest(req.command, req.key, req.value);
			// todo: implement a poision_pill to tell the thread to stop reading
			// will need to do some sort of Future/await thing here, not sure yet?
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
			return store.get(req.key);
		}

		return null;
	}

	Socket conn;
	static JKVStore store;

	public Handler(Socket conn, JKVStore store) {
		this.conn = conn;
		this.store = store;
	}

	@Override
	public void run() {
		handle(conn);
	}

}
