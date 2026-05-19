# jkvs

A persistent key-value store written in Java. Uses an append-only WAL for durability, an in-memory `ConcurrentHashMap` for reads, and a single writer thread to serialize all mutations.

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

**Concurrency model**

- **Reader threads** — one virtual thread per connection, unbounded. Reads are pure in-memory and never touch disk.
- **Writer thread** — a single platform thread owns all WAL and index file I/O. No locks needed on the write path.
- **WAL** — append-only log (`logs/log.wal`). Compacted at startup when it exceeds 1MB.

## Building

Requires JDK 21+ and Maven.

```bash
mvn package -DskipTests
```

Produces `target/jkvs-server.jar` and `target/jkvs-client.jar`.

## Running the server

```bash
java -jar target/jkvs-server.jar
# listens on localhost:9090 by default

java -jar target/jkvs-server.jar --port 9091 --addr 0.0.0.0
```

## Interacting with the server

Use the interactive REPL in `go_testers/`:

```bash
cd go_testers
go run test_client_repl.go
```

Commands at the `>` prompt:

```
> set name alice
> get name
alice
> rm name
> get name
(empty)
```

The wire protocol is `[4-byte big-endian length][command\r\nkey\r\nvalue]`. Commands: `set`, `get`, `rm`.

## Benchmarking

```bash
cd go_testers

# default: 50 connections, 100k requests, 64B values, mixed 50/50 GET+SET
go run bench.go

# write-only throughput ceiling
go run bench.go -mode=write -pipeline=32

# read-only
go run bench.go -mode=read -pipeline=32

# realistic skewed workload
go run bench.go -zipfian
```

### Results (localhost, pipeline=1)

| | Throughput | GET p50 | SET p50 |
|---|---|---|---|
| **current** | ~300k req/sec | ~300µs | ~500µs |

## Performance notes

Reads are served entirely from the in-memory `ConcurrentHashMap<String, String>` — no disk I/O on the GET path. This is the key design decision that drives throughput.

An earlier version stored WAL offsets in the map (Bitcask-style) and resolved values via `FileChannel` positional reads on every GET. Despite file handles staying open and the OS page cache being warm, that approach hit ~30k req/sec due to two compounding problems:

1. **Virtual thread pinning** — `FileChannel` I/O in Java does not participate in the virtual thread scheduler's non-blocking I/O path. Every GET pinned its carrier thread for the duration of the syscall, collapsing effective parallelism from 50 connections down to the number of CPU cores.

2. **Cache thrashing** — reads scattered across random WAL offsets produced a 73% L1 cache miss rate. The current build runs at ~18%.

Both were confirmed with `perf stat`:

| Metric | old (FileChannel) | new (HashMap) |
|---|---|---|
| task-clock | 52,119 ms | 6,285 ms |
| cache miss rate | 73% | 18% |
| LLC-load-misses | 90M | 1M |

## Running tests

```bash
mvn test
```
