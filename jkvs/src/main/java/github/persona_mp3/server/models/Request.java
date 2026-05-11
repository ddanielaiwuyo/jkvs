package github.persona_mp3.server.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.*;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Request {
	public String command;
	public String key;
	public String value;

	@Override
	public String toString() {
		return String.format("Request {command: %s, key: %s, value: %s}", this.command, this.key, this.value);
	}

}
