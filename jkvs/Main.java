package jkvs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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

	static JKVStore jkvStore = new JKVStore();

	public static void main(String[] stdargs) {
		List<String> args = new ArrayList<>(Arrays.asList(stdargs));

		if (args.size() == 1 && args.get(0).contains("-v")) {
			std.println("jkvs version " + VERSION);
			return;
		}

		if (args.size() > 3) {
			std.eprintln("inavlid usage, kvs <command> <key> <value>");
			return;
		}

		if (args.get(0).equals(JKVStore.GET_COMMAND) || args.get(0).equals(JKVStore.REMOVE_COMMAND)) {
			args.add("");
		}

		Args kv_args = new Args(args.get(0), args.get(1), args.get(2));

		try {
			jkvStore.init();
			parse_command(kv_args);
		} catch (Exception err) {
			std.eprintln(err);
			err.printStackTrace();
			System.exit(1);
		}
	}

	public static void parse_command(Args args) throws Exception {
		switch (args.command) {
			case JKVStore.GET_COMMAND:
				String result = jkvStore.get(args.key);
				std.println(result);
				break;
			case JKVStore.SET_COMMAND:
				jkvStore.set(args.key, args.value);
				break;
			case JKVStore.REMOVE_COMMAND:
				jkvStore.remove(args.key);
				break;

			default:
				std.eprintf("%s not supported\n", args.command);
				std.eprintln(USAGE);
				System.exit(1);
		}
	}
}
