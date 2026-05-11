package github.persona_mp3.server.models;

import github.persona_mp3.lib.JKVStore;

public class Protocol {
	private String DELIMETER = "\r\n"; // inspired frrom Redis RESP

	public class Request {
		public String command;
		public String key;
		public String value;
		public boolean valid;

		@Override
		public String toString() {
			return String.format("Request {command: %s, key: %s, value: %s}", this.command, this.key, this.value);
		}
	}

	public Request parseRequest(String raw) {
		Request parsedRequest = new Request();
		String[] req = raw.split(DELIMETER);
		if (raw.contains(JKVStore.GET_COMMAND)) {
			parsedRequest.command = JKVStore.GET_COMMAND;
			parsedRequest.key = req[1];
			parsedRequest.valid = true;
			return parsedRequest;
		} else if (raw.contains(JKVStore.SET_COMMAND)) {
			parsedRequest.command = JKVStore.SET_COMMAND;
			parsedRequest.key = req[1];
			parsedRequest.value = req[2];
			parsedRequest.valid = true;
			return parsedRequest;
		} else if (raw.contains(JKVStore.REMOVE_COMMAND)) {
			parsedRequest.command = JKVStore.REMOVE_COMMAND;
			parsedRequest.key = req[1];
			parsedRequest.valid = true;
			return parsedRequest;
		}

		parsedRequest.valid = false;
		return parsedRequest;
	}
}
