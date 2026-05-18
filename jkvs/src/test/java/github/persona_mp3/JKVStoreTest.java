package github.persona_mp3;

import github.persona_mp3.lib.JKVStore;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static org.junit.jupiter.api.Assertions.*;

public class JKVStoreTest {

    @TempDir
    Path tempDir;

    JKVStore store;

    @BeforeEach
    void setUp() throws Exception {
        store = new JKVStore(tempDir);
        store.init();
    }

    // ---- set + get ----

    @Test
    void setThenGetReturnsValue() throws Exception {
        store.set("name", "alice");
        assertEquals("alice", store.get("name"));
    }

    @Test
    void getMissingKeyReturnsNull() throws Exception {
        assertNull(store.get("ghost"));
    }

    @Test
    void setOverwriteReturnsLatestValue() throws Exception {
        store.set("name", "alice");
        store.set("name", "bob");
        assertEquals("bob", store.get("name"));
    }

    @Test
    void multipleKeysAreIndependent() throws Exception {
        store.set("a", "alpha");
        store.set("b", "beta");
        store.set("c", "gamma");
        assertEquals("alpha", store.get("a"));
        assertEquals("beta", store.get("b"));
        assertEquals("gamma", store.get("c"));
    }

    @Test
    void valueWithSpacesRoundTrips() throws Exception {
        store.set("sentence", "hello world foo");
        assertEquals("hello world foo", store.get("sentence"));
    }

    // ---- remove ----

    @Test
    void removeThenGetReturnsNull() throws Exception {
        store.set("name", "alice");
        store.remove("name");
        assertNull(store.get("name"));
    }

    @Test
    void removeMissingKeyReturnsNull() throws Exception {
        assertNull(store.remove("ghost"));
    }

    @Test
    void removeOneKeyDoesNotAffectOthers() throws Exception {
        store.set("a", "alpha");
        store.set("b", "beta");
        store.remove("a");
        assertNull(store.get("a"));
        assertEquals("beta", store.get("b"));
    }

    // ---- persistence (rebuild from disk) ----

    @Test
    void valuesPersistedAcrossRebuild() throws Exception {
        store.set("name", "alice");
        store.set("city", "lagos");

        JKVStore rebuilt = new JKVStore(tempDir);
        rebuilt.init();
        assertEquals("alice", rebuilt.get("name"));
        assertEquals("lagos", rebuilt.get("city"));
    }

    @Test
    void removedKeyStillGoneAfterRebuild() throws Exception {
        store.set("name", "alice");
        store.remove("name");

        JKVStore rebuilt = new JKVStore(tempDir);
        rebuilt.init();
        assertNull(rebuilt.get("name"));
    }

    @Test
    void overwrittenValueCorrectAfterRebuild() throws Exception {
        store.set("name", "alice");
        store.set("name", "bob");

        JKVStore rebuilt = new JKVStore(tempDir);
        rebuilt.init();
        assertEquals("bob", rebuilt.get("name"));
    }

    // ---- rawGet (async server read path) ----

    @Test
    void rawGetReturnsCorrectValue() throws Exception {
        store.set("name", "alice");

        try (FileChannel ch = FileChannel.open(tempDir.resolve("log.wal"), StandardOpenOption.READ)) {
            assertEquals("alice", store.rawGet("name", ch, ByteBuffer.allocate(512)));
        }
    }

    @Test
    void rawGetReturnsNullForRemovedKey() throws Exception {
        store.set("name", "alice");
        store.remove("name");

        try (FileChannel ch = FileChannel.open(tempDir.resolve("log.wal"), StandardOpenOption.READ)) {
            assertNull(store.rawGet("name", ch, ByteBuffer.allocate(512)));
        }
    }

    @Test
    void rawGetReturnsNullForMissingKey() throws Exception {
        store.set("other", "value"); // ensures WAL exists
        try (FileChannel ch = FileChannel.open(tempDir.resolve("log.wal"), StandardOpenOption.READ)) {
            assertNull(store.rawGet("ghost", ch, ByteBuffer.allocate(512)));
        }
    }

    // ---- log compaction (triggered at startup when WAL >= 1024 bytes) ----

    @Test
    void compactionRemovesDuplicateEntriesAndPreservesLatestValues() throws Exception {
        // Overwrite the same key 60 times — each record is ~24 bytes, total ~1440 bytes > 1024 threshold
        for (int i = 0; i < 60; i++) {
            store.set("counter", "value" + i);
        }
        store.set("city", "lagos");
        store.set("name", "alice");

        Path archivedLogsDir = tempDir.resolve("archived_logs");
        JKVStore rebuilt = new JKVStore(tempDir, archivedLogsDir);
        rebuilt.init(); // WAL exceeds threshold — compaction fires here

        // Latest values survive compaction
        assertEquals("value59", rebuilt.get("counter"));
        assertEquals("lagos", rebuilt.get("city"));
        assertEquals("alice", rebuilt.get("name"));

        // Old WAL was archived
        assertTrue(Files.exists(archivedLogsDir), "archive dir should be created");
        assertTrue(Files.list(archivedLogsDir).findAny().isPresent(), "archive dir should contain old logs");

        // New WAL is smaller — 3 unique keys, not 62 records
        long compactedSize = tempDir.resolve("log.wal").toFile().length();
        assertTrue(compactedSize < 1024, "compacted WAL should be under the threshold, got: " + compactedSize + " bytes");
    }

    @Test
    void compactionPreservesTombstonesForRemovedKeys() throws Exception {
        store.set("name", "alice");
        store.remove("name");
        store.set("survivor", "yes");

        // Bloat the WAL past the threshold using a separate key
        for (int i = 0; i < 60; i++) {
            store.set("filler", "value" + i);
        }

        Path archivedLogsDir = tempDir.resolve("archived_logs");
        JKVStore rebuilt = new JKVStore(tempDir, archivedLogsDir);
        rebuilt.init();

        // Removed key stays gone after compaction
        assertNull(rebuilt.get("name"));
        assertEquals("yes", rebuilt.get("survivor"));
        assertEquals("value59", rebuilt.get("filler"));
    }
}
