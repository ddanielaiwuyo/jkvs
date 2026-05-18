package github.persona_mp3.server;

import github.persona_mp3.lib.JKVStore;
import github.persona_mp3.lib.types.WriteRequest;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.channels.FileChannel;

import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import picocli.CommandLine;

/**
 * TCP server that accepts client connections.
 *
 * <p>
 * By default, the server listens on {@code localhost:9090}. Connections have a
 * socket timeout of 1 minute; to extend this (for example, when connecting via
 * a REPL),
 * start the server with the {@code --repl-mode} flag.
 *
 * <h2>Concurrency Model</h2>
 *
 * The server uses a two-tier threading model:
 *
 * <ul>
 * <li><b>Level-1 threads</b> — Each incoming connection is handled on its own
 * virtual thread. No pooling is used, since virtual threads are far cheaper
 * than platform threads, per-connection work is short-lived, and idle
 * connections are bounded by the socket timeout. They are also refered to as
 * readers
 * <li><b>0-Level thread</b> — A single OS (platform) thread acts as the writer,
 * serializing all outbound writes.
 * </ul>
 *
 * <p>
 * For additional server configuration available via the CLI, see
 * {@link Config}.
 */
public class Server {
	private static JKVStore store = new JKVStore();
	private static Logger logger = LogManager.getLogger(Server.class);
	private static final int MAX_BACKLOG = 1000;
	// todo(persona) because of repl settings. We should leave it at 15s, but when
	// load testing
	// we'd want at most 5s. Include this in cofig-options to something like
	// jkvs-server --repl-mode will setimeout to 1min by default or what
	// user-preferred
	private static final int CONN_TIMEOUT = 15 * 1000;

	public static void main(String[] args) {
		Config config = new Config();
		CommandLine cmd = new CommandLine(config);
		cmd.parseArgs(args);
		logger.info("initialising database");

		try (ServerSocket listener = new ServerSocket(config.port, MAX_BACKLOG)) {

			// Spawned for each new client. Since these are virtual threads, they are
			// lightweight
			// and less taxing than platform OSThreads
			ExecutorService clientExecutor = Executors.newVirtualThreadPerTaskExecutor();

			// All writeRequests involving <set> and <rm> are dropped here by clients
			// to be picked up by the writer thread
			BlockingQueue<WriteRequest> queue = new LinkedBlockingQueue<>();

			// Single writer thread that handles all write bount tasks to the database and
			// inMemoryIndex
			ExecutorService writerThread = Executors.newSingleThreadExecutor();

			// THe WAL for level-1s to access. This is safe since none of the level-1s write to the file
			FileChannel readOnlyFile = store.async_init(queue, writerThread);
			logger.info("starting server at tcp::{}:{}", config.addr, config.port);

			while (true) {
				try {
					Socket conn = listener.accept();
					conn.setSoTimeout(CONN_TIMEOUT);
					logger.info("accepted connection from {}", conn.getRemoteSocketAddress());
					Handler handler = new Handler(conn, store, readOnlyFile);

					clientExecutor.submit(handler);

				} catch (SocketTimeoutException err) {
					logger.warn("disconnected client. Reason: IDLE");
					err.printStackTrace();
				} catch (IOException err) {
					logger.error("IOException occured in acceptLoop. Reason: {}", err.getMessage());
					err.printStackTrace();
				} catch (Exception err) {
					logger.error("Unxexpected error occured. Reason: {}", err.getMessage());
					err.printStackTrace();
				}
			}

		} catch (IOException err) {
			logger.error("IO error occured. Reason: {}", err.getMessage());
			err.printStackTrace();
		} catch (SecurityException err) {
			logger.error("SecurityException error occured. Reason: {}", err.getMessage());
			err.printStackTrace();
		} catch (IllegalArgumentException err) {
			logger.error("IllegalArgumentException. Reason: {}", err.getMessage());
			err.printStackTrace();
		}
	}
}
