package jkvs;

import jkvs.lib.*;

class Main {
	static Std std = new Std();
	static String USAGE = "   Usage:: jkvs <command> <key> <value>";
	static String VERSION = "0.0.0";

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
			std.printf(
					" command:: %s | key:: %s | value:: %s\n",
					command,
					key,
					value);
		}
	}

	static KVStore kv_store = new KVStore();

	public static void main(String[] args) {
		if (args.length == 1 && args[0].contains("-v")) {
			std.println("jkvs version " + VERSION);
			return;
		}

		if (args.length < 3) {
			std.eprintln("inavlid usage, kvs <command> <key> <value>");
			return;
		}

		kv_store.init();

		Args kv_args = new Args(args[0], args[1], args[2]);
		parse_command(kv_args);
	}

	public static void parse_command(Args args) {
		switch (args.command) {
			case KVStore.GET_COMMAND:
				std.println("parse_command:: get");
				String result = kv_store.get(args.key);
				std.println(result);
				break;
			case KVStore.SET_COMMAND:
				std.printf("parse_command:: set %s %s\n", args.key, args.value);
				kv_store.set(args.key, args.value);
				break;
			case KVStore.REMOVE_COMMAND:
				std.printf("parse_command:: rm %s\n", args.key);
				kv_store.remove(args.key);
				break;

			default:
				std.eprintf("%s not supported\n", args.command);
				std.eprintln(USAGE);
				System.exit(1);
		}
	}
}
