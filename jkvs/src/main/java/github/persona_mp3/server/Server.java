package github.persona_mp3.server;

import github.persona_mp3.Std;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.ObjectMapper;

public class Server {
	static Std std = new Std();
	// private static Logger logger = LogManager.getLogger();
	private static Logger logger = LogManager.getLogger(Server.class);

	@Command(name = "ServerConfig")
	static class Config {
		@Option(names = "--addr", description = "ip address to run the server", defaultValue = "localhost")
		public String addr;

		@Option(names = "--port", description = "port to listen on", defaultValue = "9090")
		public int port;

	}

	// Tasks:
	// 1. Config server to read from input stream to match a Request model of
	// Request { String command, String key, String value? }
	// 2. Integrate Jackson into socket stream to parse json directly
	// 3. Bring in jkvlib
	public static void main(String[] args) throws JsonProcessingException {
		Config config = new Config();
		CommandLine cmd = new CommandLine(config);

		cmd.parseArgs(args);

		Object parsedCmd = cmd.getCommand();
		ObjectMapper mapper = new ObjectMapper();
		String _jsonRep = mapper.writeValueAsString(parsedCmd);
		logger.info("json_encoded:: {}", _jsonRep);

		String addr = config.addr;
		int port = config.port;
		logger.info("server config provided: addr: {}, port: {}", addr, port);

		try (
				ServerSocket listener = new ServerSocket(port);) {

			logger.info("tcp-server listening @ {}:{}", addr, port);

			while (true) {
				Socket conn = listener.accept();
				logger.info("accpeted connection from localAddr={}", conn.getClass());

				handleConnection(conn);
			}
		} catch (Exception err) {
			logger.error("An error occured: {}", err.getMessage());
			err.printStackTrace();
		}
	}

	static void handleConnection(Socket conn) throws IOException {
		try (
				BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
				PrintWriter writer = new PrintWriter(conn.getOutputStream());) {
			while (!conn.isClosed() && conn.isConnected()) {
				String request = reader.readLine();
				if (request == null) {
					logger.info("{} has disconnected", conn.getRemoteSocketAddress());
					break;
				}

				logger.info("request from client: {}", request);

				String response = String.format("server:: %s\n", request);
				writer.println(response);
				logger.info("response written to client successfully");

			}
		}
	}

}
