package jkvs.lib;

import java.util.HashMap;
import java.util.Map;

public class KVStore {
	private Map<String, String> values;

	public void init(){
		this.values = new HashMap<String, String>();
	}

	public String get(String key) {
		return values.get(key);
	}

	public String set(String key, String value) {
		return values.put(key, value);
	}

	public String remove(String key) {
		return values.remove(key);
	}

}
