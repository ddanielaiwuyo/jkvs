package github.persona_mp3.server;

import github.persona_mp3.lib.JKVStore;
import github.persona_mp3.lib.types.WriteRequest;
import github.persona_mp3.lib.protocol.Protocol;
import github.persona_mp3.lib.protocol.Request;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ExecutionException;

import picocli.CommandLine;

public class Server {
	static JKVStore store = new JKVStore();
	private static Logger logger = LogManager.getLogger(Server.class);
	private static Protocol protocol = new Protocol();

	private static int MAX_PAYLOAD_MB = 1 * 1024 * 1024;

	public static void main(String[] args) throws IOException {
		Config config = new Config();
		CommandLine cmd = new CommandLine(config);

		cmd.parseArgs(args);

		String addr = config.addr;
		int port = config.port;
		logger.info("Initialising database");

		/** Creates virtual threads in a threadpool */
		// ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT,
		// Thread.ofVirtual().factory());

		try (
				ServerSocket listener = new ServerSocket(port);) {

			// store.init();
			// BlockingQueue<WriteRequest> queue = new LinkedBlockingQueue<>();
			ExecutorService clientExecutor = Executors.newVirtualThreadPerTaskExecutor();
			BlockingQueue<WriteRequest> queue = new LinkedBlockingQueue<>();
			ExecutorService writerThread = Executors.newSingleThreadExecutor();
			store.async_init(queue, writerThread);

			logger.info("tcp-server listening tcp://{}:{}", addr, port);

			while (true) {
				try {
					// ConnectionHandler handler = new ConnectionHandler(conn, queue);
					Socket conn = listener.accept();
					Handler handler = new Handler(conn, store);

					clientExecutor.submit(handler);
					logger.info("accepted connection from localAddr={}", conn.getRemoteSocketAddress());
				} catch (Exception err) {
					logger.warn("did we close the connection????:: {}", err.getMessage());
					err.printStackTrace();
				}
				// Thread connThread = new Thread(handler);
				// connThread.start();
				// handleConn(conn);
			}
		} catch (Exception err) {
			logger.error("An error occured: {}", err.getMessage());
			err.printStackTrace();
		}
	}

	// static class ConnectionHandler implements Runnable {
	// Socket conn;
	// // Only contains write-requests
	// BlockingQueue<WriteRequest> queue;
	// // Connection handler should also take a blocking queue they can
	// // drop write-requests to
	//
	// public ConnectionHandler(Socket conn, BlockingQueue<WriteRequest> queue) {
	// this.conn = conn;
	// this.queue = queue;
	// }
	//
	// @Override
	// public void run() {
	// System.out.println("DO YOU KNOW WHO ELSE HAS A RUN METHOD?");
	// logger.debug("running connectionHandlerThread. ThreadId={}");
	// handleConn(this.conn, this.queue);
	// logger.debug("running done");
	// System.out.println("DONE_DONE_DONe");
	// }
	// }

	/**
	 * [header-length(4bytes)][content]
	 */

	// static void handleConn(Socket conn, BlockingQueue<WriteRequest> queue) {
		//
		// System.out.println("GOT EM_GOT_EMM");
		// String addr = conn.getRemoteSocketAddress().toString();
		// try (
		// 		// Socket socket = conn;
		// 		OutputStream writer = conn.getOutputStream();
		// 		DataInputStream reader = new DataInputStream(conn.getInputStream());) {
		//
		// 	String rawRequest = "";
		// 	String response = "";
		// 	byte[] rawResponse = null;
		//
		// 	while (conn.isConnected() && !conn.isClosed()) {
		// 		// rawRequest = protocol.readFromStream(MAX_PAYLOAD_MB, reader);
		// 		rawRequest = protocol.readPacket(MAX_PAYLOAD_MB, conn);
		// 		if (rawRequest == null) {
		// 			logger.debug("nothing more to read from client");
		// 			return;
		// 		}
		//
		// 		logger.debug("request_parsed:: {}", rawRequest);
		// 		Request request = protocol.parseRequest(rawRequest);
		//
		// 		if (!request.isValid) {
		// 			logger.info("request recvd is not valid");
		// 			rawResponse = protocol.encodeResponse("what do you mean?");
		// 			writer.write(rawResponse);
		// 			continue;
		// 		}
		//
		// 		response = processRequest(request);
		// 		rawResponse = protocol.encodeResponse(response);
		//
		// 		writer.write(rawResponse);
		// 		logger.info("wrote response , {} to client", response);
		//
		// 	}
		// 	System.out.println("broken outta hot loop");
		//
		// } catch (EOFException err) {
		// 	logger.warn("Client has disconnected: {}", err.getMessage());
		// 	err.printStackTrace();
		// } catch (SocketException err) {
		// 	logger.warn("Client forcefully disconnected: {}", err.getMessage());
		// 	err.printStackTrace();
		//
		// } catch (Exception err) {
		// 	logger.error("Unexpected error while handling conn addr={}, reason: {}", addr, err.getMessage());
		// 	err.printStackTrace();
		// }
		//
		// System.out.println("WHAT?");
	// }

	// static String processRequest(Request req) throws IOException {
	// String response = "";
	// if (req.command == null) {
	// response = "invalid command";
	// return response;
	// }
	// switch (req.command) {
	// case JKVStore.GET_COMMAND:
	// response = store.get(req.key);
	// return response;
	//
	// case JKVStore.SET_COMMAND:
	// response = store.set(req.key, req.value);
	// return response;
	//
	// case JKVStore.REMOVE_COMMAND:
	// response = store.remove(req.key);
	// return response;
	// }
	//
	// response = "unknown command";
	// return response;
	// }
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

		return "";
	}
}
