package github.persona_mp3.lib.types;

import java.util.concurrent.CompletableFuture;

public class WriteRequest {
	public String command;
	public String key;
	public String value;
	public CompletableFuture<String> result = new CompletableFuture<>();

	public WriteRequest(String command, String key, String value) {
		this.command = command;
		this.key = key;
		this.value = value;
	}
}
