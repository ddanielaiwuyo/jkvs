package github.persona_mp3.server;

import github.persona_mp3.lib.JKVStore;
import github.persona_mp3.lib.protocol.Protocol;
import github.persona_mp3.lib.protocol.Request;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

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

		try (
				ServerSocket listener = new ServerSocket(port);) {

			store.init();
			logger.info("tcp-server listening tcp://{}:{}", addr, port);

			while (true) {
				Socket conn = listener.accept();
				logger.info("accpeted connection from localAddr={}", conn.getRemoteSocketAddress());

				handleConn(conn);
			}
		} catch (Exception err) {
			logger.error("An error occured: {}", err.getMessage());
			err.printStackTrace();
		}
	}

	/**
	 * [header-length(4bytes)][content]
	 */
	static void handleConn(Socket conn) {
		String addr = conn.getRemoteSocketAddress().toString();
		try (
				DataInputStream reader = new DataInputStream(conn.getInputStream());
				PrintWriter writer = new PrintWriter(conn.getOutputStream(), true);) {

			while (conn.isConnected() && !conn.isClosed()) {
				int packetSize = reader.readInt(); // by default reads in bigEndian
				logger.info("header-size: {}", packetSize);

				if (packetSize >= MAX_PAYLOAD_MB) {
					logger.warn("{} sent over max payload. Recvd={}, MaxPayload={}", addr, packetSize, MAX_PAYLOAD_MB);
					writer.println("payload too large\r\n");
					return;
				}

				byte[] buffer = new byte[packetSize];
				reader.readFully(buffer);
				String raw = new String(buffer);
				logger.info("recvd:: raw::{}", raw);

				Request request = protocol.parseRequest(raw);

				if (!request.isValid) {
					logger.info("request recvd is not valid");
					writer.println("invalid request\r\n");
					return;
				}

				String response = processRequest(request);
				writer.println(String.format("%s\r\n", response));
				logger.info("wrote response to client");

			}
		} catch (EOFException err) {
			logger.warn("Client has disconnected: {}", err.getMessage());
			return;
		} catch (SocketException err) {
			logger.warn("Client forcefully disconnected: {}", err.getMessage());
			return;

		} catch (Exception err) {
			logger.error("Unexpected error while handling conn addr={}, reason: {}", addr, err.getMessage());
			err.printStackTrace();
			return;
		}

	}

	static String processRequest(Request req) throws IOException {
		String response = "";
		if (req.command == null) {
			response = "invalid command";
			return response;
		}
		switch (req.command) {
			case JKVStore.GET_COMMAND:
				response = store.get(req.key);
				return response;

			case JKVStore.SET_COMMAND:
				response = store.set(req.key, req.value);
				return response;

			case JKVStore.REMOVE_COMMAND:
				response = store.remove(req.key);
				return response;
		}

		response = "unknown command";
		return response;
	}
}
