package github.persona_mp3.lib.protocol;

import github.persona_mp3.lib.JKVStore;

import java.net.Socket;
import java.io.DataInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class Protocol {
	private String DELIMETER = "\r\n"; // inspired frrom Redis RESP
	private static Logger logger = LogManager.getLogger(Protocol.class);

	public Request parseRequest(String raw) {
		Request parsedRequest = new Request(null, null, null);
		String[] req = raw.split(DELIMETER);
		// we ignore the other commands
		if (raw.contains(JKVStore.GET_COMMAND) && req.length >= 2) {
			parsedRequest.command = JKVStore.GET_COMMAND;
			parsedRequest.key = req[1];
			parsedRequest.isValid = true;
			return parsedRequest;
		} else if (raw.contains(JKVStore.SET_COMMAND) && req.length >= 3) {
			parsedRequest.command = JKVStore.SET_COMMAND;
			parsedRequest.key = req[1];
			parsedRequest.value = req[2];
			parsedRequest.isValid = true;
			return parsedRequest;
		} else if (raw.contains(JKVStore.REMOVE_COMMAND) && req.length >= 2) {
			parsedRequest.command = JKVStore.REMOVE_COMMAND;
			parsedRequest.key = req[1];
			parsedRequest.isValid = true;
			return parsedRequest;
		}

		parsedRequest.isValid = false;
		return parsedRequest;
	}

	public byte[] encodeRequest(Request req) {
		String str = String.format("%s\r\n%s\r\n%s", req.command, req.key, req.value);
		byte[] raw = str.getBytes();
		logger.debug("encoded request:  {}", req.toString());
		// encode raw.length into 4bytes as bigEndian
		ByteBuffer buffer = ByteBuffer.allocate(4 + raw.length);
		buffer.order(ByteOrder.BIG_ENDIAN);
		buffer.putInt(raw.length);
		buffer.put(raw);
		return buffer.array();
	}

	// public byte[] encodeResponse(String response) {
	// 	byte[] raw = String.format("%s\r\n", response).getBytes();
	// 	ByteBuffer buffer = ByteBuffer.allocate(4 + raw.length);
	// 	buffer.order(ByteOrder.BIG_ENDIAN);
	// 	buffer.putInt(raw.length);
	// 	buffer.put(raw);
	// 	return buffer.array();
	// }

	public byte[] encodeResponse(String response) {
		byte[] raw = (response + "\r\n").getBytes(StandardCharsets.UTF_8);
		ByteBuffer buffer = ByteBuffer.allocate(4 + raw.length);
		buffer.order(ByteOrder.BIG_ENDIAN);
		buffer.putInt(raw.length);
		buffer.put(raw);
		return buffer.array();
	}

	public String readPacket(int maxPayload, Socket conn) throws Exception {
		int packetSize = 0;
		DataInputStream reader = new DataInputStream(conn.getInputStream());
		packetSize = reader.readInt();
		logger.info("packet size: {}", packetSize);
		if (packetSize >= maxPayload) {
			logger.warn("{} sent over max payload. Recvd={}, MaxPayload={}", conn.getRemoteSocketAddress(), packetSize,
					maxPayload);
			// writer.println("payload too large\r\n");
			return null;
		}
		// todo: refactor to allow buffer resuse
		byte[] buffer = new byte[packetSize];
		reader.readFully(buffer);
		String raw = new String(buffer);
		logger.debug("decoded response: {}", raw);
		return raw;
	}

	public String readFromStream(int maxPayload, DataInputStream reader) throws Exception {
		int packetSize = 0;
		// System.out.println("reading---from---stream");
		packetSize = reader.readInt();
		logger.info("packet size: {}", packetSize);
		if (packetSize >= maxPayload) {
			logger.warn("recvd max payload. {}, accepted limit={}", packetSize, maxPayload);
			// System.out.println("max_maxy_parload " + packetSize);
			// writer.println("payload too large\r\n");
			return null;
		}
		
		// System.out.print("read from stream");
		byte[] buffer = new byte[packetSize];

		// System.out.print("waiting for BUFFER");
		reader.readFully(buffer);
		// System.out.println("buffer for BUFFER");
		String raw = new String(buffer);
		logger.debug("decoded response: {}", raw);
		// System.out.println("REQUEST|REQUEST :::: " + raw);
		return raw;
	}
}
