package github.persona_mp3.lib.protocol;

import github.persona_mp3.lib.JKVStore;

public class Protocol {
	private String DELIMETER = "\r\n"; // inspired frrom Redis RESP

	public Request parseRequest(String raw) {
		Request parsedRequest = new Request();
		String[] req = raw.split("\r\n");
		if (raw.contains(JKVStore.GET_COMMAND) && req.length == 2) {
			parsedRequest.command = JKVStore.GET_COMMAND;
			parsedRequest.key = req[1];
			parsedRequest.isValid = true;
			return parsedRequest;
		} else if (raw.contains(JKVStore.SET_COMMAND) && req.length == 3) {
			parsedRequest.command = JKVStore.SET_COMMAND;
			parsedRequest.key = req[1];
			parsedRequest.value = req[2];
			parsedRequest.isValid = true;
			return parsedRequest;
		} else if (raw.contains(JKVStore.REMOVE_COMMAND) && req.length == 2) {
			parsedRequest.command = JKVStore.REMOVE_COMMAND;
			parsedRequest.key = req[1];
			parsedRequest.isValid = true;
			return parsedRequest;
		}

		parsedRequest.isValid = false;
		return parsedRequest;
	}
}
