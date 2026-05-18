package github.persona_mp3;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Proves that sharing a single RandomAccessFile across concurrent readers is unsafe.
 *
 * seek() + readLine() is not atomic — a seek from one virtual thread can move the
 * cursor before another thread's readLine() executes, causing it to read from the
 * wrong offset. This test is expected to FAIL with the current implementation and
 * PASS once rawGet switches to FileChannel positional reads.
 */
public class ConcurrentReadTest {

    private static final int NUM_RECORDS = 20;
    private static final int THREADS_PER_RECORD = 50; // 1000 concurrent reads total

    @Test
    void sharedRAFSeekRacesUnderConcurrentReads(@TempDir Path tempDir) throws Exception {
        Path walPath = tempDir.resolve("test.wal");
        Std std = new Std();

        long[] offsets = new long[NUM_RECORDS];
        String[] keys = new String[NUM_RECORDS];

        try (RandomAccessFile raf = new RandomAccessFile(walPath.toFile(), "rw")) {
            for (int i = 0; i < NUM_RECORDS; i++) {
                keys[i] = "key" + i;
                offsets[i] = raf.length();
                raf.seek(offsets[i]);
                raf.write(std.encoder("set", keys[i], "value" + i));
            }
        }

        RandomAccessFile sharedRaf = new RandomAccessFile(walPath.toFile(), "r");
        AtomicInteger mismatches = new AtomicInteger(0);

        CountDownLatch ready = new CountDownLatch(NUM_RECORDS * THREADS_PER_RECORD);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(NUM_RECORDS * THREADS_PER_RECORD);

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        for (int round = 0; round < THREADS_PER_RECORD; round++) {
            for (int i = 0; i < NUM_RECORDS; i++) {
                final int idx = i;
                executor.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        sharedRaf.seek(offsets[idx]);
                        String line = sharedRaf.readLine();
                        if (line == null || !line.split(" ")[1].equals(keys[idx])) {
                            mismatches.incrementAndGet();
                        }
                    } catch (Exception e) {
                        mismatches.incrementAndGet();
                    } finally {
                        done.countDown();
                    }
                });
            }
        }

        ready.await();
        start.countDown();
        done.await(10, TimeUnit.SECONDS);
        sharedRaf.close();
        executor.shutdown();

        assertEquals(0, mismatches.get(),
                mismatches.get() + "/" + (NUM_RECORDS * THREADS_PER_RECORD) + " reads returned a wrong or null record");
    }
}
