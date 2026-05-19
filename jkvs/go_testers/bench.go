// jkvs benchmark — measures throughput and per-request latency.
//
// Run with:
//
//	go run bench.go                                             serial latency, mixed 50/50 GET+SET, uniform keys (default)
//	go run bench.go -mode=write -pipeline=32                   write-only throughput ceiling
//	go run bench.go -mode=read  -pipeline=32                   read-only throughput ceiling
//	go run bench.go -mode=mixed -pipeline=32                   mixed throughput ceiling
//	go run bench.go -zipfian                                    realistic skewed workload — hot keys get most traffic
//	go run bench.go -payload=1024                              larger values (1KB)
//	go run bench.go -pipeline=1 -connections=10 -requests=5000 low-concurrency serial latency
//
// pipeline=1  → true per-request latency (one request in flight per connection at a time)
// pipeline=N  → throughput mode (N requests in flight per connection — latency numbers are queue-inflated, ignore them)

package main

import (
	"bufio"
	"encoding/binary"
	"flag"
	"io"
	"log"
	"math/rand"
	"net"
	"sort"
	"strconv"
	"sync"
	"sync/atomic"
	"time"
)

var (
	flagAddr        = flag.String("addr", "127.0.0.1:9090", "server address")
	flagMode        = flag.String("mode", "mixed", "workload: write | read | mixed")
	flagConnections = flag.Int("connections", 50, "parallel connections")
	flagRequests    = flag.Int("requests", 100_000, "total requests (must divide evenly by connections)")
	flagPayload     = flag.Int("payload", 64, "value size in bytes")
	flagPipeline    = flag.Int("pipeline", 1, "requests in-flight per connection — 1 = serial latency mode")
	flagKeyspace    = flag.Int("keyspace", 10_000, "number of distinct keys")
	flagGetRatio    = flag.Float64("get-ratio", 0.5, "fraction of GETs in mixed mode")
	flagZipfian     = flag.Bool("zipfian", false, "Zipfian key distribution (skewed hot keys) instead of uniform")
	flagWarmup      = flag.Int("warmup", 2_000, "warmup requests before measuring (not counted)")
)

// -- counters --

var (
	cntSet    atomic.Int64
	cntGet    atomic.Int64
	cntFailed atomic.Int64
)

type opKind uint8

const (
	opSet opKind = iota
	opGet
)

type sample struct {
	op  opKind
	lat time.Duration
}

// -- entry point --

func main() {
	flag.Parse()

	if *flagRequests%*flagConnections != 0 {
		log.Fatalf("--requests (%d) must be divisible by --connections (%d)",
			*flagRequests, *flagConnections)
	}

	log.Printf("jkvs bench | mode=%-5s conns=%d reqs=%d payload=%dB pipeline=%d keyspace=%d zipfian=%v",
		*flagMode, *flagConnections, *flagRequests, *flagPayload,
		*flagPipeline, *flagKeyspace, *flagZipfian)

	log.Printf("warmup: seeding %d keys...", *flagKeyspace)
	if err := seed(); err != nil {
		log.Fatalf("seed: %v", err)
	}

	log.Printf("warmup: %d warmup requests...", *flagWarmup)
	if err := warmup(); err != nil {
		log.Fatalf("warmup: %v", err)
	}
	log.Println("warmup done — measuring now")

	perConn := *flagRequests / *flagConnections
	allSamples := make([][]sample, *flagConnections)

	stop := make(chan struct{})
	go monitor(stop)

	start := time.Now()

	var wg sync.WaitGroup
	for i := 0; i < *flagConnections; i++ {
		wg.Add(1)
		go worker(i, perConn, allSamples, &wg)
	}
	wg.Wait()

	elapsed := time.Since(start)
	close(stop)

	report(elapsed, allSamples)
}

// -- worker --

func worker(id, n int, out [][]sample, wg *sync.WaitGroup) {
	defer wg.Done()

	conn, err := net.DialTimeout("tcp", *flagAddr, 5*time.Second)
	if err != nil {
		log.Printf("worker %d: connect: %v", id, err)
		cntFailed.Add(int64(n))
		return
	}
	defer conn.Close()

	rng := rand.New(rand.NewSource(time.Now().UnixNano() + int64(id)*1_000_003))
	var zipf *rand.Zipf
	if *flagZipfian {
		// s=1.1 gives a moderate skew — ~20% of keys get ~80% of traffic
		zipf = rand.NewZipf(rng, 1.1, 1.0, uint64(*flagKeyspace-1))
	}

	payload := alphanumPayload(*flagPayload, rng)

	type entry struct {
		op   opKind
		sent time.Time
	}

	inflight := make(chan entry, *flagPipeline)
	samples := make([]sample, 0, n)
	var localFail atomic.Int64

	var inner sync.WaitGroup
	inner.Add(2)

	// writer — builds and sends requests
	go func() {
		defer inner.Done()
		defer close(inflight)

		bw := bufio.NewWriterSize(conn, 64*1024)
		for i := 0; i < n; i++ {
			op, pkt := nextRequest(rng, zipf, payload)

			_ = conn.SetWriteDeadline(time.Now().Add(5 * time.Second))

			sent := time.Now()
			if _, err := bw.Write(pkt); err != nil {
				localFail.Add(int64(n - i))
				return
			}

			// flush before blocking on inflight — without this the reader
			// waits for data that is still sitting in the bufio buffer
			if bw.Buffered() > 0 {
				if err := bw.Flush(); err != nil {
					localFail.Add(int64(n - i))
					return
				}
			}

			inflight <- entry{op: op, sent: sent}
		}
		_ = bw.Flush()
	}()

	// reader — reads responses, records latency
	go func() {
		defer inner.Done()
		br := bufio.NewReaderSize(conn, 64*1024)
		for e := range inflight {
			_ = conn.SetReadDeadline(time.Now().Add(5 * time.Second))
			if err := readFrame(br); err != nil {
				localFail.Add(1)
				continue
			}
			samples = append(samples, sample{op: e.op, lat: time.Since(e.sent)})
			if e.op == opSet {
				cntSet.Add(1)
			} else {
				cntGet.Add(1)
			}
		}
	}()

	inner.Wait()
	out[id] = samples
	cntFailed.Add(localFail.Load())
}

// nextRequest picks an op and builds a wire-encoded packet
func nextRequest(rng *rand.Rand, zipf *rand.Zipf, payload []byte) (opKind, []byte) {
	var k int
	if zipf != nil {
		k = int(zipf.Uint64())
	} else {
		k = rng.Intn(*flagKeyspace)
	}
	key := keyOf(k)

	var op opKind
	switch *flagMode {
	case "write":
		op = opSet
	case "read":
		op = opGet
	default:
		if rng.Float64() < *flagGetRatio {
			op = opGet
		} else {
			op = opSet
		}
	}

	if op == opSet {
		return opSet, encodeSet(key, payload)
	}
	return opGet, encodeGet(key)
}

// -- warmup helpers --

// seed writes one value per key so GETs always hit existing data
func seed() error {
	conn, err := net.DialTimeout("tcp", *flagAddr, 5*time.Second)
	if err != nil {
		return err
	}
	defer conn.Close()

	rng := rand.New(rand.NewSource(42))
	payload := alphanumPayload(*flagPayload, rng)
	bw := bufio.NewWriter(conn)
	br := bufio.NewReader(conn)

	for i := 0; i < *flagKeyspace; i++ {
		if _, err := bw.Write(encodeSet(keyOf(i), payload)); err != nil {
			return err
		}
		if err := bw.Flush(); err != nil {
			return err
		}
		if err := readFrame(br); err != nil {
			return err
		}
	}
	return nil
}

// warmup sends requests to heat up JIT and OS caches before measuring
func warmup() error {
	conn, err := net.DialTimeout("tcp", *flagAddr, 5*time.Second)
	if err != nil {
		return err
	}
	defer conn.Close()

	rng := rand.New(rand.NewSource(99))
	payload := alphanumPayload(*flagPayload, rng)
	bw := bufio.NewWriter(conn)
	br := bufio.NewReader(conn)

	for i := 0; i < *flagWarmup; i++ {
		var pkt []byte
		if i%2 == 0 {
			pkt = encodeSet(keyOf(rng.Intn(*flagKeyspace)), payload)
		} else {
			pkt = encodeGet(keyOf(rng.Intn(*flagKeyspace)))
		}
		if _, err := bw.Write(pkt); err != nil {
			return err
		}
		if err := bw.Flush(); err != nil {
			return err
		}
		if err := readFrame(br); err != nil {
			return err
		}
	}
	return nil
}

// -- reporting --

func monitor(stop <-chan struct{}) {
	tick := time.NewTicker(time.Second)
	defer tick.Stop()
	var prevSet, prevGet int64
	for {
		select {
		case <-stop:
			return
		case <-tick.C:
			s, g := cntSet.Load(), cntGet.Load()
			ds, dg := s-prevSet, g-prevGet
			prevSet, prevGet = s, g
			switch *flagMode {
			case "write":
				log.Printf("live | set=%5d/s", ds)
			case "read":
				log.Printf("live | get=%5d/s", dg)
			default:
				log.Printf("live | set=%5d/s  get=%5d/s  total=%5d/s", ds, dg, ds+dg)
			}
		}
	}
}

func report(elapsed time.Duration, allSamples [][]sample) {
	var setLats, getLats []time.Duration
	for _, ws := range allSamples {
		for _, s := range ws {
			if s.op == opSet {
				setLats = append(setLats, s.lat)
			} else {
				getLats = append(getLats, s.lat)
			}
		}
	}
	sort.Slice(setLats, func(i, j int) bool { return setLats[i] < setLats[j] })
	sort.Slice(getLats, func(i, j int) bool { return getLats[i] < getLats[j] })

	total := cntSet.Load() + cntGet.Load()

	log.Println("========== RESULTS ==========")
	log.Printf("duration:   %v", elapsed.Round(time.Millisecond))
	log.Printf("success:    %d  failed: %d", total, cntFailed.Load())
	log.Printf("throughput: %.0f req/sec", float64(total)/elapsed.Seconds())
	printPercentiles("SET", setLats)
	printPercentiles("GET", getLats)
}

func printPercentiles(label string, lats []time.Duration) {
	if len(lats) == 0 {
		return
	}
	n := len(lats)
	pct := func(f float64) time.Duration {
		i := int(float64(n)*f) - 1
		if i < 0 {
			i = 0
		}
		return lats[i]
	}
	log.Printf("%s latency (%d samples):", label, n)
	log.Printf("  p50:  %v", pct(0.50))
	log.Printf("  p95:  %v", pct(0.95))
	log.Printf("  p99:  %v", pct(0.99))
	log.Printf("  p999: %v", pct(0.999))
	log.Printf("  max:  %v", lats[n-1])
}

// -- wire protocol --

// encodeSet builds: [4-byte big-endian len][set\r\n<key>\r\n<value>]
func encodeSet(key, value []byte) []byte {
	body := make([]byte, 0, 5+len(key)+2+len(value))
	body = append(body, "set\r\n"...)
	body = append(body, key...)
	body = append(body, "\r\n"...)
	body = append(body, value...)
	return frame(body)
}

// encodeGet builds: [4-byte big-endian len][get\r\n<key>]
func encodeGet(key []byte) []byte {
	body := make([]byte, 0, 4+len(key))
	body = append(body, "get\r\n"...)
	body = append(body, key...)
	return frame(body)
}

func frame(body []byte) []byte {
	out := make([]byte, 4+len(body))
	binary.BigEndian.PutUint32(out[:4], uint32(len(body)))
	copy(out[4:], body)
	return out
}

// readFrame reads and discards one length-prefixed response frame
func readFrame(r *bufio.Reader) error {
	var hdr [4]byte
	if _, err := io.ReadFull(r, hdr[:]); err != nil {
		return err
	}
	_, err := io.CopyN(io.Discard, r, int64(binary.BigEndian.Uint32(hdr[:])))
	return err
}

// -- key / payload helpers --

func keyOf(n int) []byte {
	b := make([]byte, 0, 12)
	b = append(b, 'k')
	b = strconv.AppendInt(b, int64(n), 10)
	return b
}

// alphanumPayload generates a random alphanumeric payload safe for the wire protocol.
// Values must not contain \r\n (protocol delimiter) or spaces (WAL parser delimiter).
func alphanumPayload(size int, rng *rand.Rand) []byte {
	const chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
	b := make([]byte, size)
	for i := range b {
		b[i] = chars[rng.Intn(len(chars))]
	}
	return b
}
