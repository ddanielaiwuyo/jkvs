package github.persona_mp3.lib;

import github.persona_mp3.lib.JKVStore;

import java.util.concurrent.BlockingQueue;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class Writer implements Runnable {
	BlockingQueue<JKeyValue> queue; 
	JKVStore store;
	private Logger logger = LogManager.getLogger(Writer.class);

	public Writer(BlockingQueue<JKeyValue> queue, JKVStore store) {
		this.queue = queue;
		this.store = store;
	}

	// 1. Need a way to signal the writer thread to stop spinning indefinitely even when the server is idle
	private void main() throws Exception {
		JKeyValue entry = null;
		while (true) {
			entry = this.queue.take();
			logger.debug("recvd_entry:: {}", entry);
			store.set(entry.key, entry.value);
		}
	}

	@Override
	public void run() {
		try {
			logger.info("starting writer...");
			main();
		} catch (Exception err) {
			logger.fatal("WRITER ERROR OCCURED. Reason: {}", err.getMessage());
			err.printStackTrace();
		}
	}

	public class JKeyValue {
		public String key;
		public String value;

		@Override
		public String toString(){
			return String.format("JKeyValue {key: %s, value: %s}", this.key, this.value);
		}
	}

}
