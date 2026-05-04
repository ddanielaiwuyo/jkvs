package jkvs.lib;

import java.util.HashMap;
import java.util.Map;

public class KVStore {

	private HashMap<String, String> values;

	public static final String GET_COMMAND = "get";
	public static final String SET_COMMAND = "set";
	public static final String REMOVE_COMMAND = "rm";
	public static final String VERSION_COMMAND = "-V";

	public void init() {
		this.values = new HashMap<String, String>();
	}

	/// Gets a key from the key-value store. If the key isn't found the store
	/// returns null
	public String get(String key) {
		return values.get(key);
	}

	public String set(String key, String value) {
		return values.put(key, value);
	}

	public String remove(String key) {
		return values.remove(key);
	}

	public void debug() {
		for (String key : values.keySet()) {
			System.out.println("  kv_store debug");
			System.out.printf("   %s :: %s\n", key, values.get(key));
		}
	}
}
