package github.persona_mp3.lib.protocol;

/**
 * Command, Key, Value
 */
public class Request {
	/**
	 * GET, SET, RM
	 */
	public String command;

	public String key;

	public String value;
	/**
	 * Helps to tell if the request recieved is not valid if none of the commands supported 
	 * were found in the request body
	 * */
	public boolean isValid;

	@Override
	public String toString() {
		return String.format("Request {command: %s, key: %s, value: %s}", this.command, this.key, this.value);
	}
}
