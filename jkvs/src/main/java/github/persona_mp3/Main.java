package github.persona_mp3;

import java.util.ArrayList;
import java.util.Arrays;

import github.persona_mp3.lib.*;

// jkvs::jkvs (refactor) | java -cp target/jkvs-1.0-SNAPSHOT.jar github.persona_mp3.Main

class Main {
	static String VERSION = "0.0.1";
	static String VERSION_COMMAND = "-V";

	static String USAGE = """
				jkvs [command] [-options] ie <key> <value>

				Example
				jkvs set username persona_mp3
				jkvs -V
				jkvs rm username
				jkvs get username
			""";

	static Std std = new Std();
	static JKVStore kvstore = new JKVStore();

	static class Command {
		final String command;
		String key;
		String value;

		public Command(final String command, String key, String value) {
			this.command = command;
			this.key = key;
			this.value = value;
		}

		public String execute(JKVStore kvstore) throws Exception {
			String result = null;
			switch (this.command) {
				case JKVStore.GET_COMMAND:
					return kvstore.get(this.key);

				case JKVStore.SET_COMMAND:
					return kvstore.set(this.key, this.value);

				case JKVStore.REMOVE_COMMAND:
					return kvstore.remove(this.key);

				default:
					std.eprintf("Unknown command: %s\n", this.command);
					std.eprintf(USAGE);
					System.exit(1);
					return result;
			}
		}

		@Override
		public String toString() {
			return String.format("%s   %s, \"%s\" \n", this.command, this.key, this.value);
		}
	}

	public static void main(String[] args) {

		if (args.length < 1) {
			std.eprintf("Invalid usage\n %s\n", USAGE);
			System.exit(1);
		} else if (args.length == 1 && args[0].equals(JKVStore.VERSION_COMMAND)) {
			std.printf("version %s\n", kvstore.VERSION);
			return;
		} else if (args.length == 1 && !args[0].equals(JKVStore.VERSION_COMMAND)) {
			std.eprintf("Invalid usage\n %s\n", USAGE);
			System.exit(1);
		}

		if (args.length > 3) {
			std.eprintf("%s\n", USAGE);
			return;
		}

		ArrayList<String> commands = new ArrayList<>(Arrays.asList(args));
		if (commands.size() <= 2) {
			commands.add("");
		}

		Command cmd = new Command(commands.get(0), commands.get(1), commands.get(2));

		try {
			kvstore.init();

			String result = cmd.execute(kvstore);
			std.println(result);
		} catch (Exception err) {
			std.eprintln("An error occured");
			std.eprintln(err.getMessage());
			err.printStackTrace();
			System.exit(1);
		}

	}

}
