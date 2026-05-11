# jkvs

A high-performance distributed key-value store with Write-Ahead Logging (WAL), built in Java. Inspired by [PingCAP's Talent Plan](https://github.com/pingcap/talent-plan) (originally for Rust).

## ⚡ Performance

- **15,750 req/s** peak throughput (1M operations in 63 seconds)
- **12,500 req/s** average sustained throughput
- RESP-inspired binary protocol for efficiency
- WAL-based durability with crash recovery

See [BENCHMARKS.md](./BENCHMARKS.md) for detailed performance analysis.

---

## 🚀 Quick Start

### Prerequisites
- **Java 21-25** - Check with `java --version`
- **Python 3.x** - For testing/benchmarking
- **Maven** - For building
- **GraalVM + Native Image** (optional) - For binary builds

### Run the Server
```bash
git clone https://github.com/persona-mp3/jkvs.git
cd jkvs
mvn clean package
java -cp target/jkvs-1.0-SNAPSHOT.jar github.persona_mp3.Main
```

Server starts on `localhost:9090`

### Test with Python Client
```bash
./tester.py
```

---

## 📦 Installation

### 1. Install Java (if needed)
```bash
java --version  # Should show Java 21-25
```

If not installed, use [SDKMAN](https://sdkman.io/install/):
```bash
curl -s "https://get.sdkman.io" | bash
sdk install java 25.0.2-graal
```

### 2. Install Maven
- **Linux/Mac**: [Apache Maven Install Guide](https://maven.apache.org/install.html)
- **Windows**: Download from [maven.apache.org](https://maven.apache.org/download.cgi)

### 3. Install Python (for testing)
```bash
python --version  # Should show Python 3.x
```

Download from [python.org](https://www.python.org/downloads/) if needed.

### 4. Install GraalVM Native Image (optional, for binaries)
```bash
# Via SDKMAN
sdk install java 25.0.2-graal
sdk use java 25.0.2-graal
gu install native-image
```

Or follow [GraalVM Getting Started](https://www.graalvm.org/jdk21/getting-started/)

---

## 🔨 Building

### Build JAR (Quick)
```bash
mvn clean package
```

Run with:
```bash
java -cp target/jkvs-1.0-SNAPSHOT.jar github.persona_mp3.Main
```

### Build Native Binaries (Slower, ~3 minutes)

The `compiler.sh` script builds all executables:
```bash
chmod u+x ./compiler.sh
./compiler.sh
```

This creates:
- `jkvs` - Core library CLI
- `jkvs-server` - Database server
- `jkvs-client` - Client (in development)

**Note**: Native Image compilation takes ~3 minutes and uses significant CPU/RAM as it bakes the JVM into the binary.

#### Manual Native Image Build
```bash
mvn package
native-image --no-fallback \
  -H:IncludeResources="log4j2.xml" \
  -jar target/jkvs-1.0-SNAPSHOT.jar \
  jkvs

chmod u+x jkvs
```

---

## 📖 Usage

### Server Mode

Start the database server:
```bash
./jkvs-server
# Or with JAR:
java -cp target/jkvs-1.0-SNAPSHOT.jar github.persona_mp3.server.Server
```

Server listens on `localhost:9090` by default.

### Client Mode (CLI)

Set a value:
```bash
./jkvs set <key> <value>
# Example:
./jkvs set username persona-mp3
```

Get a value:
```bash
./jkvs get <key>
# Example:
./jkvs get username
# Output: persona-mp3
```

Remove a key:
```bash
./jkvs rm <key>
# Example:
./jkvs rm username
# Output: username (if existed, null otherwise)
```

Show version:
```bash
./jkvs -V
```

### Python Client (Recommended for Testing)

The Python client provides better performance for benchmarking:
```bash
./tester.py
```

Performance: ~15,750 req/s for 1M operations.

See `tester.py` for protocol details and customization.

---

## 🏗️ Architecture

### Protocol

**RESP-Inspired Binary Protocol**:
- 4-byte length prefix (big-endian)
- `\r\n` delimited text commands
- Simple, efficient, easy to parse

**Request Format**:
```
[4 bytes: message length][command\r\nkey\r\nvalue\r\n]
```

**Response Format**:
```
[4 bytes: response length][response\r\n]
```

### Storage

- **Write-Ahead Log (WAL)**: All writes go to append-only log
- **Index**: In-memory index with disk persistence
- **Compaction**: Automatic log compaction when size threshold reached
- **Crash Recovery**: Rebuild state from WAL on restart

### Key Features

- ✅ Durable writes with WAL
- ✅ Crash recovery
- ✅ Log compaction
- ✅ High-performance binary protocol
- ✅ Zero external dependencies (except logging)
- 🚧 Distributed replication (planned)
- 🚧 Range queries (planned)

---

## ⚙️ Configuration

### Enable Debug Logging

Edit `src/main/resources/log4j2.xml`:
```xml
<Root level="info">  <!-- Change "warn" to "info" or "debug" -->
  <AppenderRef ref="Console"/>
</Root>
```

Rebuild after changes:
```bash
mvn clean package
```

### JVM Tuning (for better performance)

```bash
java -XX:+UseG1GC \
     -Xms2G -Xmx2G \
     -XX:MaxGCPauseMillis=50 \
     -cp target/jkvs-1.0-SNAPSHOT.jar \
     github.persona_mp3.Main
```

---

## 📊 Benchmarks

Performance evolution through optimization:

| Configuration | Throughput | Time (100k ops) |
|--------------|------------|-----------------|
| JSON + Bash client | 204 req/s | 491s |
| RESP + Python client | **15,750 req/s** | 6.3s |

**77x improvement** through protocol and client optimization.

See [BENCHMARKS.md](./BENCHMARKS.md) for:
- Detailed performance analysis
- Protocol comparison (JSON vs RESP)
- Optimization journey
- Next steps for further improvements

---

## 🧪 Testing

### Run Benchmark Test
```bash
./tester.py
```

Default: 1,000,000 SET operations

Customize in `tester.py`:
```python
limit = 100000  # Number of operations
HOST = "127.0.0.1"
PORT = 9090
```

### Expected Results
- **100k operations**: ~6-9 seconds
- **1M operations**: ~63-94 seconds
- **Throughput**: 10,000-15,000 req/s

---

## 🛠️ Development

### Project Structure
```
jkvs/
├── src/main/java/github/persona_mp3/
│   ├── lib/           # Core KV store logic
│   ├── server/        # TCP server
│   ├── client/        # CLI client (WIP)
│   └── Main.java      # Entry point
├── tester.py          # Python benchmark client
├── compiler.sh        # Build automation
└── BENCHMARKS.md      # Performance documentation
```

### Why Minimal Dependencies?

Native Image (GraalVM) has challenges with:
- Runtime reflection (e.g., Jackson)
- Dynamic class loading
- Longer build times

Therefore, this project:
- Uses custom utilities (e.g., [ReversedReader](./src/main/java/github/persona_mp3/lib/utils/ReversedReader.java) from Apache Commons)
- Avoids heavy frameworks
- Keeps binary builds fast (~3 min vs 10+ min)

### Building Specific Components

Build server only:
```bash
mvn package
native-image --no-fallback \
  -H:IncludeResources="log4j2.xml" \
  --main-class=github.persona_mp3.server.Server \
  -jar target/jkvs-1.0-SNAPSHOT.jar \
  jkvs-server
```

Build client only:
```bash
mvn package
native-image --no-fallback \
  -H:IncludeResources="log4j2.xml" \
  --main-class=github.persona_mp3.client.Client \
  -jar target/jkvs-1.0-SNAPSHOT.jar \
  jkvs-client
```

---

## 🎯 Roadmap

### Current (v0.1)
- [x] WAL-based storage
- [x] TCP server with binary protocol
- [x] Log compaction
- [x] Crash recovery
- [x] Python benchmark client

### Next (v1.1)
- [ ] JVM GC tuning
- [ ] Async I/O
- [ ] Distributed replication

## 🙏 Acknowledgments

- [PingCAP Talent Plan](https://github.com/pingcap/talent-plan) - Original Rust-based curriculum
- [Apache Commons IO](https://github.com/apache/commons-io) - ReversedReader utility
- [Redis Protocol (RESP)](https://redis.io/docs/reference/protocol-spec/) - Protocol inspiration

---

<!-- ## 🏷️ Git Tags -->
<!---->
<!-- Key milestones are tagged for reference: -->
<!---->
<!-- - `json-protocol` - Initial JSON implementation (~204 req/s) -->
<!-- - `resp-protocol` - RESP-inspired protocol (~15,750 req/s) -->
<!---->
<!-- ```bash -->
<!-- # View specific implementation -->
<!-- git checkout json-protocol -->
<!-- git checkout resp-protocol -->
<!-- ``` -->
