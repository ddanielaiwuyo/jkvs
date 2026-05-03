package jkvs;

import jkvs.lib.*;

class Main {
	static Std std = new Std();

	static class Args {
		public String command;
		public String key;
		public String value;

		public Args(String command, String key, String value) {
			this.command = command;
			this.key = key;
			this.value = value;
		}

		public void debug() {
			std.printf(" command:: %s | key:: %s | value:: %s\n", command, key, value);
		}

	}

	static KVStore kv_store = new KVStore();

	public static void main(String[] args) {
		if (args.length < 3) {
			std.eprintln("inavlid usage, kvs <command> <key> <value>");
			return;
		}

		std.println("initialising new key_value store...");
		kv_store.init();

		Args kv_args = new Args(args[0], args[1], args[2]);
		kv_args.debug();
	}

	public void parse_command(Args args) {
	}
}
