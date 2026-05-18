package main

import (
	"bufio"
	"encoding/binary"
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

const (
	connections   = 50
	totalRequests = 100_000
	target        = "127.0.0.1:9090"

	payloadSize   = 1024
	pipelineDepth = 32

	// fraction of requests that are GETs (rest are SETs)
	getRatio = 0.5

	// size of the key space the workload draws from; GETs sample from keys
	// known to have been set earlier so we measure hits, not misses.
	keyspaceSize = 10_000

	requestTimeout = 5 * time.Second
)

const _ = uint(0 - (totalRequests % connections))

var (
	successSet atomic.Int64
	successGet atomic.Int64
	failed     atomic.Int64
)

type opKind uint8

const (
	opSet opKind = iota
	opGet
)

type sample struct {
	kind    opKind
	latency time.Duration
}

func main() {
	log.Printf("starting mixed benchmark -> connections=%d total=%d payload=%dB pipeline=%d get_ratio=%.2f keyspace=%d",
		connections, totalRequests, payloadSize, pipelineDepth, getRatio, keyspaceSize,
	)

	// Pre-populate the keyspace with one SET per key so GETs hit existing
	// data. This isolates the read path from "key doesn't exist" branches.
	if err := prepopulate(); err != nil {
		log.Fatalf("prepopulate failed: %v", err)
	}
	log.Printf("prepopulated %d keys", keyspaceSize)

	start := time.Now()
	done := make(chan struct{})
	go liveMonitor(done)

	requestsPerConn := totalRequests / connections
	workerSamples := make([][]sample, connections)

	var wg sync.WaitGroup
	for i := 0; i < connections; i++ {
		wg.Add(1)
		go worker(i, requestsPerConn, workerSamples, &wg)
	}
	wg.Wait()
	close(done)

	duration := time.Since(start)

	// merge samples and split by op for separate percentile reporting
	var setLats, getLats []time.Duration
	for _, ws := range workerSamples {
		for _, s := range ws {
			if s.kind == opSet {
				setLats = append(setLats, s.latency)
			} else {
				getLats = append(getLats, s.latency)
			}
		}
	}
	sort.Slice(setLats, func(i, j int) bool { return setLats[i] < setLats[j] })
	sort.Slice(getLats, func(i, j int) bool { return getLats[i] < getLats[j] })

	totalSuccess := successSet.Load() + successGet.Load()
	rps := float64(totalSuccess) / duration.Seconds()

	log.Println("============ RESULTS ============")
	log.Println("duration:", duration)
	log.Println("requests:")
	log.Println(" success_set:", successSet.Load())
	log.Println(" success_get:", successGet.Load())
	log.Println(" failed:", failed.Load())
	log.Printf(" rps: %.2f/sec\n", rps)
	reportLatency("SET", setLats)
	reportLatency("GET", getLats)
}

func reportLatency(name string, lats []time.Duration) {
	if len(lats) == 0 {
		log.Printf("%s latency: no samples", name)
		return
	}
	log.Printf("%s latency:", name)
	log.Println(" p50:", lats[int(float64(len(lats))*0.50)])
	log.Println(" p95:", lats[int(float64(len(lats))*0.95)])
	log.Println(" p99:", lats[int(float64(len(lats))*0.99)])
	log.Println(" max:", lats[len(lats)-1])
}

// prepopulate writes one value per key in the keyspace using a single
// connection. Quick, sequential, no benchmarking concerns.
func prepopulate() error {
	conn, err := net.DialTimeout("tcp", target, 5*time.Second)
	if err != nil {
		return err
	}
	defer conn.Close()

	bw := bufio.NewWriter(conn)
	br := bufio.NewReader(conn)
	payload := randomPayload(payloadSize)

	for k := 0; k < keyspaceSize; k++ {
		key := keyspaceKey(k)
		packet := buildSetPacket(key, payload)
		if _, err := bw.Write(packet); err != nil {
			return err
		}
		if err := bw.Flush(); err != nil {
			return err
		}
		if err := readFramed(br); err != nil {
			return err
		}
	}
	return nil
}

func worker(
	id int,
	requests int,
	out [][]sample,
	wg *sync.WaitGroup,
) {
	defer wg.Done()

	conn, err := net.DialTimeout("tcp", target, 5*time.Second)
	if err != nil {
		log.Printf("worker %d connect error: %v\n", id, err)
		failed.Add(int64(requests))
		return
	}
	defer conn.Close()

	payload := randomPayload(payloadSize)
	r := rand.New(rand.NewSource(time.Now().UnixNano() + int64(id)))

	// inflight pairs each in-flight request with its op kind and send time
	type pending struct {
		kind opKind
		sent time.Time
	}
	inflight := make(chan pending, pipelineDepth)

	samples := make([]sample, 0, requests)
	var localFailed atomic.Int64

	var inner sync.WaitGroup
	inner.Add(2)

	go func() {
		defer inner.Done()
		defer close(inflight)

		bw := bufio.NewWriter(conn)
		for i := 0; i < requests; i++ {
			var (
				packet []byte
				kind   opKind
			)
			if r.Float64() < getRatio {
				kind = opGet
				key := keyspaceKey(r.Intn(keyspaceSize))
				packet = buildGetPacket(key)
			} else {
				kind = opSet
				key := keyspaceKey(r.Intn(keyspaceSize))
				packet = buildSetPacket(key, payload)
			}

			_ = conn.SetWriteDeadline(time.Now().Add(requestTimeout))

			sent := time.Now()
			if _, err := bw.Write(packet); err != nil {
				localFailed.Add(1)
				return
			}
			if pipelineDepth <= 1 || bw.Buffered() >= payloadSize*pipelineDepth/2 {
				if err := bw.Flush(); err != nil {
					localFailed.Add(1)
					return
				}
			}
			inflight <- pending{kind: kind, sent: sent}
		}
		_ = bw.Flush()
	}()

	go func() {
		defer inner.Done()
		br := bufio.NewReader(conn)
		for p := range inflight {
			_ = conn.SetReadDeadline(time.Now().Add(requestTimeout))
			if err := readFramed(br); err != nil {
				localFailed.Add(1)
				continue
			}
			samples = append(samples, sample{
				kind:    p.kind,
				latency: time.Since(p.sent),
			})
			if p.kind == opSet {
				successSet.Add(1)
			} else {
				successGet.Add(1)
			}
		}
	}()

	inner.Wait()
	out[id] = samples
	failed.Add(localFailed.Load())
}

func readFramed(r *bufio.Reader) error {
	var header [4]byte
	if _, err := io.ReadFull(r, header[:]); err != nil {
		return err
	}
	size := binary.BigEndian.Uint32(header[:])
	_, err := io.CopyN(io.Discard, r, int64(size))
	return err
}

// keys are drawn from a fixed pool so GETs and SETs touch the same keyspace
func keyspaceKey(n int) []byte {
	b := make([]byte, 0, 16)
	b = append(b, "key-"...)
	b = strconv.AppendInt(b, int64(n), 10)
	return b
}

func buildSetPacket(key, value []byte) []byte {
	// [len:4][set\r\n][key][\r\n][value]
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

func buildGetPacket(key []byte) []byte {
	// [len:4][get\r\n][key]
	bodyLen := len("get\r\n") + len(key)
	packet := make([]byte, 4+bodyLen)
	binary.BigEndian.PutUint32(packet[:4], uint32(bodyLen))
	n := 4
	n += copy(packet[n:], "get\r\n")
	copy(packet[n:], key)
	return packet
}

func randomPayload(size int) []byte {
	b := make([]byte, size)
	r := rand.New(rand.NewSource(time.Now().UnixNano()))
	const alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
	for i := range b {
		b[i] = alphabet[r.Intn(len(alphabet))]
	}
	return b
}

func liveMonitor(done <-chan struct{}) {
	ticker := time.NewTicker(time.Second)
	defer ticker.Stop()
	var prevSet, prevGet int64
	for {
		select {
		case <-done:
			return
		case <-ticker.C:
			s := successSet.Load()
			g := successGet.Load()
			log.Printf("live: set=%d/sec get=%d/sec total=%d/sec",
				s-prevSet, g-prevGet, (s-prevSet)+(g-prevGet))
			prevSet, prevGet = s, g
		}
	}
}
