# jkvs

A persistent key-value store written in Java. Uses an append-only WAL for durability, an in-memory `ConcurrentHashMap` for reads, and a single writer thread to serialize all mutations. Inspired by [PingCAP's Talent Plan](https://github.com/pingcap/talent-plan).

## Performance

~300k req/sec on localhost (50 connections, pipeline=1, mixed GET+SET):

| | Throughput | GET p50 | SET p50 |
|---|---|---|---|
| **current** | ~300k req/sec | ~300µs | ~500µs |

See [jkvs/README.md](./jkvs/README.md) for the full performance breakdown and `perf` analysis.

---

## Quick Start

### Prerequisites

- **Java 21+** — `java --version`
- **Maven** — `mvn --version`
- **Go 1.21+** — for the REPL and benchmarks

### Build

```bash
cd jkvs
mvn package -DskipTests
```

### Run the server

```bash
java -jar jkvs/target/jkvs-server.jar
# listens on localhost:9090

java -jar jkvs/target/jkvs-server.jar --port 9091 --addr 0.0.0.0
```

### Interact with the server

```bash
cd jkvs/go_testers
go run test_client_repl.go
```

```
> set name alice
> get name
alice
> rm name
> get name
(empty)
```

---

## Architecture

```
client connections (virtual threads)
        │
        ├── GET  →  ConcurrentHashMap lookup  (no disk I/O)
        │
        └── SET/RM  →  BlockingQueue  →  single writer thread
                                               │
                                         WAL append + index update
```

- **Reader threads** — one virtual thread per connection. Reads are pure in-memory, never touch disk.
- **Writer thread** — single platform thread owns all WAL and index file I/O.
- **WAL** — append-only log, compacted at startup when it exceeds 1MB.

### Protocol

4-byte big-endian length prefix followed by `\r\n`-delimited text:

```
[4 bytes: length][command\r\nkey\r\nvalue]
```

Commands: `set`, `get`, `rm`.

---

## Benchmarking

```bash
cd jkvs/go_testers

# default: 50 connections, 100k requests, mixed 50/50 GET+SET
go run bench.go

# throughput ceiling
go run bench.go -mode=write -pipeline=32

# skewed hot-key workload
go run bench.go -zipfian
```

---

## Building Native Binaries (optional)

Requires GraalVM with `native-image`. Install via [SDKMAN](https://sdkman.io):

```bash
sdk install java 25.0.2-graal
sdk use java 25.0.2-graal
gu install native-image
```

Then:

```bash
chmod u+x jkvs/compiler.sh
cd jkvs && ./compiler.sh
```

Produces `jkvs-server` and `jkvs-client` native binaries (~3 min build).

---

## Configuration

**Change log level** — edit `jkvs/src/main/resources/log4j2.xml`:

```xml
<Root level="info">  <!-- info | debug | warn -->
```

**JVM flags for throughput:**

```bash
java -XX:+UseG1GC -Xms2G -Xmx2G -XX:MaxGCPauseMillis=50 \
     -jar jkvs/target/jkvs-server.jar
```

---

## Testing

```bash
cd jkvs && mvn test
```

---

## Project Structure

```
jkvs/
├── jkvs/
│   ├── src/main/java/github/persona_mp3/
│   │   ├── lib/        # core KV store, WAL, compaction
│   │   ├── server/     # TCP server + handler
│   │   └── client/     # CLI client
│   ├── go_testers/
│   │   ├── test_client_repl.go   # interactive REPL
│   │   ├── bench.go              # latency + throughput benchmark
│   │   └── benchmark.go         # simple throughput benchmark
│   └── README.md       # detailed architecture and perf notes
└── README.md
```

---

## Roadmap

- [x] WAL-based storage
- [x] TCP server with binary protocol
- [x] Log compaction
- [x] Crash recovery
- [x] In-memory value index (no disk reads on GET path)
- [ ] Group commit (batch WAL fsyncs)
- [ ] Distributed replication

---

## Acknowledgements

- [PingCAP Talent Plan](https://github.com/pingcap/talent-plan) — original Rust-based curriculum
- [Apache Commons IO](https://github.com/apache/commons-io) — ReversedReader utility
- [Redis Protocol (RESP)](https://redis.io/docs/reference/protocol-spec/) — protocol inspiration
