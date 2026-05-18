package main

import (
	"bufio"
	"encoding/binary"
	"io"
	"log"
	"math/rand"
	"net"
	"runtime"
	"sort"
	"strconv"
	"sync"
	"sync/atomic"
	"time"
)

const (
	connections   = 100
	totalRequests = 1_000_000
	target        = "127.0.0.1:9090"

	payloadSize = 1024 // 1KB payload

	// pipelineDepth controls how many in-flight requests per connection.
	// 1 = serial (measures round-trip latency, not server throughput).
	// >1 = pipelined: writer fires ahead, reader drains. Use this to find
	// the server's actual ceiling.
	pipelineDepth = 32

	requestTimeout = 5 * time.Second
)

// compile-time check that work divides evenly across connections
const _ = uint(0 - (totalRequests % connections))

var (
	globalSuccess atomic.Int64
	globalFailed  atomic.Int64
)

func main() {
	log.Printf("starting benchmark -> connections=%d total=%d payload=%dB pipeline=%d",
		connections, totalRequests, payloadSize, pipelineDepth,
	)

	start := time.Now()

	requestsPerConn := totalRequests / connections

	done := make(chan struct{})
	go liveRPSMonitor(done)

	// per-worker latency slices avoid the shared-slice data race
	workerLatencies := make([][]time.Duration, connections)

	var wg sync.WaitGroup
	for i := 0; i < connections; i++ {
		wg.Add(1)
		go worker(i, requestsPerConn, workerLatencies, &wg)
	}

	wg.Wait()
	close(done)

	duration := time.Since(start)

	totalSuccess := globalSuccess.Load()
	totalFailed := globalFailed.Load()

	rps := float64(totalSuccess) / duration.Seconds()

	// merge only successful-request latencies; failed requests have no timing
	latencies := make([]time.Duration, 0, totalSuccess)
	for _, ls := range workerLatencies {
		latencies = append(latencies, ls...)
	}

	sort.Slice(latencies, func(i, j int) bool {
		return latencies[i] < latencies[j]
	})

	log.Println("============ RESULTS ============")
	log.Println("duration:", duration)

	log.Println("requests:")
	log.Println(" success:", totalSuccess)
	log.Println(" failed:", totalFailed)

	log.Println("throughput:")
	log.Printf(" rps: %.2f/sec\n", rps)

	if len(latencies) > 0 {
		log.Println("latency:")
		log.Println(" p50:", latencies[int(float64(len(latencies))*0.50)])
		log.Println(" p95:", latencies[int(float64(len(latencies))*0.95)])
		log.Println(" p99:", latencies[int(float64(len(latencies))*0.99)])
		log.Println(" max:", latencies[len(latencies)-1])
	} else {
		log.Println("latency: no successful requests")
	}

	printMemoryStats()
}

func worker(
	id int,
	requests int,
	out [][]time.Duration,
	wg *sync.WaitGroup,
) {
	defer wg.Done()

	conn, err := net.DialTimeout("tcp", target, 5*time.Second)
	if err != nil {
		log.Printf("worker %d connect error: %v\n", id, err)
		globalFailed.Add(int64(requests))
		return
	}
	defer conn.Close()

	payload := randomPayload(payloadSize)

	// inflight carries the send timestamp for each request the writer has put
	// on the wire but the reader hasn't yet matched. Capacity bounds the
	// pipeline depth and provides natural back-pressure on the writer.
	inflight := make(chan time.Time, pipelineDepth)

	localLatencies := make([]time.Duration, 0, requests)
	var localFailed atomic.Int64

	var inner sync.WaitGroup
	inner.Add(2)

	// writer
	go func() {
		defer inner.Done()
		defer close(inflight)

		bw := bufio.NewWriter(conn)
		for i := 0; i < requests; i++ {
			key := makeKey(id, i)
			packet := buildPacket(key, payload)

			_ = conn.SetWriteDeadline(time.Now().Add(requestTimeout))

			sent := time.Now()
			if _, err := bw.Write(packet); err != nil {
				localFailed.Add(1)
				return
			}
			// flush once the pipeline is full enough that batching stops
			// helping, or every request if depth is 1.
			if pipelineDepth <= 1 || bw.Buffered() >= payloadSize*pipelineDepth/2 {
				if err := bw.Flush(); err != nil {
					localFailed.Add(1)
					return
				}
			}
			inflight <- sent
		}
		// final flush for anything still buffered
		_ = bw.Flush()
	}()

	// reader
	go func() {
		defer inner.Done()
		br := bufio.NewReader(conn)

		for sent := range inflight {
			_ = conn.SetReadDeadline(time.Now().Add(requestTimeout))
			if err := readFramed(br); err != nil {
				localFailed.Add(1)
				continue
			}
			localLatencies = append(localLatencies, time.Since(sent))
			globalSuccess.Add(1)
		}
	}()

	inner.Wait()

	out[id] = localLatencies
	globalFailed.Add(localFailed.Load())
}

func readFramed(r *bufio.Reader) error {
	var header [4]byte
	if _, err := io.ReadFull(r, header[:]); err != nil {
		return err
	}
	size := binary.BigEndian.Uint32(header[:])

	// discard the body without allocating a buffer sized to the response
	_, err := io.CopyN(io.Discard, r, int64(size))
	return err
}

func makeKey(id, i int) []byte {
	b := make([]byte, 0, 32)
	b = append(b, "key-"...)
	b = strconv.AppendInt(b, int64(id), 10)
	b = append(b, '-')
	b = strconv.AppendInt(b, int64(i), 10)
	return b
}

func buildPacket(key, value []byte) []byte {
	// format: [len:4][set\r\n][key][\r\n][value]
	bodyLen := len("set\r\n") + len(key) + len("\r\n") + len(value)

	packet := make([]byte, 4+bodyLen)
	binary.BigEndian.PutUint32(packet[:4], uint32(bodyLen))

	n := 4
	n += copy(packet[n:], "set\r\n")
	n += copy(packet[n:], key)
	n += copy(packet[n:], "\r\n")
	copy(packet[n:], value)

	return packet
}

func randomPayload(size int) []byte {
	b := make([]byte, size)
	// math/rand is plenty for a benchmark payload, and can't fail
	r := rand.New(rand.NewSource(time.Now().UnixNano()))
	for i := range b {
		b[i] = byte(r.Intn(256))
	}
	return b
}

func liveRPSMonitor(done <-chan struct{}) {
	ticker := time.NewTicker(time.Second)
	defer ticker.Stop()

	var prev int64
	for {
		select {
		case <-done:
			return
		case <-ticker.C:
			cur := globalSuccess.Load()
			log.Printf("live_rps=%d/sec\n", cur-prev)
			prev = cur
		}
	}
}

func printMemoryStats() {
	var m runtime.MemStats
	runtime.ReadMemStats(&m)

	log.Println("memory:")
	log.Printf(" alloc = %d MB\n", m.Alloc/1024/1024)
	log.Printf(" total_alloc = %d MB\n", m.TotalAlloc/1024/1024)
	log.Printf(" sys = %d MB\n", m.Sys/1024/1024)
	log.Printf(" gc cycles = %d\n", m.NumGC)
}

/*
2026/05/18 12:09:31 starting benchmark -> connections=100 total=1000000 payload=1024B pipeline=32
2026/05/18 12:09:32 live_rps=4636/sec
2026/05/18 12:09:33 live_rps=6624/sec
2026/05/18 12:09:34 live_rps=8245/sec
2026/05/18 12:09:35 live_rps=8443/sec
2026/05/18 12:09:36 live_rps=8331/sec
2026/05/18 12:09:37 live_rps=6808/sec
2026/05/18 12:09:38 live_rps=8191/sec
2026/05/18 12:09:39 live_rps=7650/sec
2026/05/18 12:09:40 live_rps=7620/sec
2026/05/18 12:09:41 live_rps=8305/sec
2026/05/18 12:09:42 live_rps=7644/sec
2026/05/18 12:09:43 live_rps=8519/sec
2026/05/18 12:09:44 live_rps=8460/sec
2026/05/18 12:09:45 live_rps=6521/sec
2026/05/18 12:09:46 live_rps=7153/sec
2026/05/18 12:09:47 live_rps=6614/sec
2026/05/18 12:09:48 live_rps=6858/sec
2026/05/18 12:09:49 live_rps=6148/sec
2026/05/18 12:09:50 live_rps=7034/sec
^[[D2026/05/18 12:09:51 live_rps=6459/sec
2026/05/18 12:09:52 live_rps=6750/sec
2026/05/18 12:09:53 live_rps=6875/sec
2026/05/18 12:09:54 live_rps=6865/sec
2026/05/18 12:09:55 live_rps=7592/sec
2026/05/18 12:09:56 live_rps=6378/sec
2026/05/18 12:09:57 live_rps=6415/sec
2026/05/18 12:09:58 live_rps=6471/sec
2026/05/18 12:09:59 live_rps=6943/sec
2026/05/18 12:10:00 live_rps=6893/sec
2026/05/18 12:10:01 live_rps=6925/sec
2026/05/18 12:10:02 live_rps=5611/sec
2026/05/18 12:10:03 live_rps=4307/sec
2026/05/18 12:10:04 live_rps=6654/sec
2026/05/18 12:10:05 live_rps=6299/sec
2026/05/18 12:10:06 live_rps=6339/sec
2026/05/18 12:10:07 live_rps=6206/sec
2026/05/18 12:10:08 live_rps=6976/sec
2026/05/18 12:10:09 live_rps=6258/sec
2026/05/18 12:10:10 live_rps=6308/sec
2026/05/18 12:10:11 live_rps=6166/sec
2026/05/18 12:10:12 live_rps=6285/sec
2026/05/18 12:10:13 live_rps=6841/sec
2026/05/18 12:10:14 live_rps=6154/sec
2026/05/18 12:10:15 live_rps=6666/sec
2026/05/18 12:10:16 live_rps=7782/sec
2026/05/18 12:10:17 live_rps=6986/sec
2026/05/18 12:10:18 live_rps=6275/sec
2026/05/18 12:10:19 live_rps=6936/sec
2026/05/18 12:10:20 live_rps=7007/sec
2026/05/18 12:10:21 live_rps=5494/sec


2026/05/18 12:10:49 live_rps=6335/sec
2026/05/18 12:10:50 live_rps=6373/sec
2026/05/18 12:10:51 live_rps=6159/sec
2026/05/18 12:10:52 live_rps=5689/sec
2026/05/18 12:10:53 live_rps=5984/sec
2026/05/18 12:10:54 live_rps=6217/sec
2026/05/18 12:10:55 live_rps=6843/sec
2026/05/18 12:10:56 live_rps=6553/sec
2026/05/18 12:10:57 live_rps=6248/sec
2026/05/18 12:10:58 live_rps=5557/sec
2026/05/18 12:10:59 live_rps=6433/sec
2026/05/18 12:11:00 live_rps=6811/sec
2026/05/18 12:11:01 live_rps=6528/sec
2026/05/18 12:11:02 live_rps=6368/sec
2026/05/18 12:11:03 live_rps=6252/sec
2026/05/18 12:11:04 live_rps=6900/sec
2026/05/18 12:11:05 live_rps=6609/sec
2026/05/18 12:11:06 live_rps=6236/sec
2026/05/18 12:11:07 live_rps=6155/sec
2026/05/18 12:11:08 live_rps=6103/sec
2026/05/18 12:11:09 live_rps=6043/sec
2026/05/18 12:11:10 live_rps=6553/sec
2026/05/18 12:11:11 live_rps=5798/sec
2026/05/18 12:11:12 live_rps=6189/sec
2026/05/18 12:11:13 live_rps=6152/sec
2026/05/18 12:11:14 live_rps=6074/sec
2026/05/18 12:11:15 live_rps=6827/sec
2026/05/18 12:11:16 live_rps=6611/sec
2026/05/18 12:11:17 live_rps=6879/sec
2026/05/18 12:11:18 live_rps=5428/sec
2026/05/18 12:11:19 live_rps=6825/sec
2026/05/18 12:11:20 live_rps=6176/sec
2026/05/18 12:11:21 live_rps=7605/sec
2026/05/18 12:11:22 live_rps=6246/sec
2026/05/18 12:11:23 live_rps=6115/sec
2026/05/18 12:11:24 live_rps=7000/sec
2026/05/18 12:11:25 live_rps=6811/sec
2026/05/18 12:11:26 live_rps=6919/sec
2026/05/18 12:11:27 live_rps=6095/sec
2026/05/18 12:11:28 live_rps=6473/sec
2026/05/18 12:11:29 live_rps=7393/sec
2026/05/18 12:11:30 live_rps=5692/sec
2026/05/18 12:11:31 live_rps=6488/sec
2026/05/18 12:11:32 live_rps=7678/sec
2026/05/18 12:11:33 live_rps=6174/sec
2026/05/18 12:11:34 live_rps=6903/sec
2026/05/18 12:11:35 live_rps=5063/sec
2026/05/18 12:11:36 live_rps=6931/sec
2026/05/18 12:11:37 live_rps=4936/sec
2026/05/18 12:11:38 live_rps=6128/sec
2026/05/18 12:11:39 live_rps=7038/sec
2026/05/18 12:11:40 live_rps=6880/sec
2026/05/18 12:11:41 live_rps=6748/sec
2026/05/18 12:11:42 live_rps=6549/sec
2026/05/18 12:11:43 live_rps=6883/sec
2026/05/18 12:11:44 live_rps=6816/sec
2026/05/18 12:11:45 live_rps=6694/sec






26/05/18 12:11:23 live_rps=6115/sec
2026/05/18 12:11:24 live_rps=7000/sec
2026/05/18 12:11:25 live_rps=6811/sec
2026/05/18 12:11:26 live_rps=6919/sec
2026/05/18 12:11:27 live_rps=6095/sec
2026/05/18 12:11:28 live_rps=6473/sec
2026/05/18 12:11:29 live_rps=7393/sec
2026/05/18 12:11:30 live_rps=5692/sec
2026/05/18 12:11:31 live_rps=6488/sec
2026/05/18 12:11:32 live_rps=7678/sec
2026/05/18 12:11:33 live_rps=6174/sec
2026/05/18 12:11:34 live_rps=6903/sec
2026/05/18 12:11:35 live_rps=5063/sec
2026/05/18 12:11:36 live_rps=6931/sec
2026/05/18 12:11:37 live_rps=4936/sec
2026/05/18 12:11:38 live_rps=6128/sec
2026/05/18 12:11:39 live_rps=7038/sec
2026/05/18 12:11:40 live_rps=6880/sec
2026/05/18 12:11:41 live_rps=6748/sec
2026/05/18 12:11:42 live_rps=6549/sec
2026/05/18 12:11:43 live_rps=6883/sec
2026/05/18 12:11:44 live_rps=6816/sec
2026/05/18 12:11:45 live_rps=6694/sec
2026/05/18 12:11:46 live_rps=6824/sec
2026/05/18 12:11:47 live_rps=6681/sec
2026/05/18 12:11:48 live_rps=7261/sec
2026/05/18 12:11:49 live_rps=6561/sec
2026/05/18 12:11:50 live_rps=5890/sec
2026/05/18 12:11:51 live_rps=6982/sec
2026/05/18 12:11:52 live_rps=6638/sec
2026/05/18 12:11:53 live_rps=6317/sec
2026/05/18 12:11:54 live_rps=6225/sec
2026/05/18 12:11:55 live_rps=5942/sec
2026/05/18 12:11:56 live_rps=6034/sec
2026/05/18 12:11:57 live_rps=6833/sec
2026/05/18 12:11:58 live_rps=6748/sec
2026/05/18 12:11:59 live_rps=6467/sec
2026/05/18 12:12:00 live_rps=6732/sec
2026/05/18 12:12:01 live_rps=6232/sec
2026/05/18 12:12:02 live_rps=6035/sec
2026/05/18 12:12:03 live_rps=6140/sec
2026/05/18 12:12:04 live_rps=7022/sec
2026/05/18 12:12:05 ============ RESULTS ============
2026/05/18 12:12:05 duration: 2m33.404686734s
2026/05/18 12:12:05 requests:
2026/05/18 12:12:05  success: 1000000
2026/05/18 12:12:05  failed: 0
2026/05/18 12:12:05 throughput:
2026/05/18 12:12:05  rps: 6518.71/sec
2026/05/18 12:12:05 latency:
2026/05/18 12:12:05  p50: 508.109292ms
2026/05/18 12:12:05  p95: 645.577542ms
2026/05/18 12:12:05  p99: 783.87283ms
2026/05/18 12:12:05  max: 1.005616596s
2026/05/18 12:12:05 memory:
2026/05/18 12:12:05  alloc = 15 MB
2026/05/18 12:12:05  total_alloc = 1178 MB
2026/05/18 12:12:05  sys = 43 MB
*/
