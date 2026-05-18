package main

import (
	"bufio"
	"encoding/binary"
	"errors"
	"io"
	"log"
	"net"
	"sync"
	"sync/atomic"
	"time"
)

const (
	connections   = 100          // like redis-benchmark -c 100
	totalRequests = 1_000_000    // like -n 1000000
	target        = "127.0.0.1:9090"
)

var (
	success atomic.Int64
	failed  atomic.Int64
)

func main() {
	log.Printf(
		"starting benchmark -> connections=%d total=%d",
		connections,
		totalRequests,
	)

	start := time.Now()

	requestsPerConn := totalRequests / connections

	wg := sync.WaitGroup{}

	for i := 0; i < connections; i++ {
		wg.Add(1)

		go worker(i, requestsPerConn, &wg)
	}

	wg.Wait()

	duration := time.Since(start)

	totalSuccess := success.Load()
	totalFailed := failed.Load()

	rps := float64(totalSuccess) / duration.Seconds()

	log.Println("============ RESULTS ============")
	log.Println("duration:", duration)
	log.Println("success:", totalSuccess)
	log.Println("failed:", totalFailed)
	log.Printf("rps: %.2f/sec\n", rps)
}


func worker(id int, requests int, wg *sync.WaitGroup) {
	defer wg.Done()

	conn, err := net.DialTimeout(
		"tcp",
		target,
		5*time.Second,
	)

	if err != nil {
		log.Printf("worker %d could not connect: %v\n", id, err)
		failed.Add(int64(requests))
		return
	}
	defer conn.Close()

	reader := bufio.NewReader(conn)
	packet := toPacket("set\r\nresults\r\nodd")
	buffer := make([]byte, 1024) // Allocated outside loop for performance

	for i := 0; i < requests; i++ {

		// 1. Write request
		_, err := conn.Write(packet)
		if err != nil {
			failed.Add(1)
			return // Exit worker on error (no reconnect)
		}

		// 2. Read response
		_, err = reader.Read(buffer)
		if err != nil && !errors.Is(err, io.EOF) {
			failed.Add(1)
			return // Exit worker on error (no reconnect)
		}

		success.Add(1)
	}
}


// func worker(id int, requests int, wg *sync.WaitGroup) {
// 	defer wg.Done()
//
// 	var conn net.Conn
// 	var err error
//
// 	connect := func() error {
// 		conn, err = net.DialTimeout(
// 			"tcp",
// 			target,
// 			5*time.Second,
// 		)
//
// 		return err
// 	}
//
// 	if err := connect(); err != nil {
// 		log.Printf("worker %d connect error: %v\n", id, err)
// 		failed.Add(int64(requests))
// 		return
// 	}
//
// 	defer conn.Close()
//
// 	reader := bufio.NewReader(conn)
//
// 	packet := toPacket("set\r\nresults\r\nodd")
//
// 	for i := 0; i < requests; i++ {
//
// 		// reconnect if needed
// 		if conn == nil {
// 			if err := connect(); err != nil {
// 				failed.Add(1)
// 				continue
// 			}
//
// 			reader = bufio.NewReader(conn)
// 		}
//
// 		_, err := conn.Write(packet)
//
// 		if err != nil {
// 			failed.Add(1)
//
// 			conn.Close()
// 			conn = nil
//
// 			continue
// 		}
//
// 		buffer := make([]byte, 1024)
//
// 		_, err = reader.Read(buffer)
//
// 		if err != nil && !errors.Is(err, io.EOF) {
// 			failed.Add(1)
//
// 			conn.Close()
// 			conn = nil
//
// 			continue
// 		}
//
// 		success.Add(1)
// 	}
// }
// func worker(id int, requests int, wg *sync.WaitGroup) {
// 	defer wg.Done()
//
// 	conn, err := net.DialTimeout(
// 		"tcp",
// 		target,
// 		5*time.Second,
// 	)
//
// 	if err != nil {
// 		log.Printf("worker %d could not connect: %v\n", id, err)
// 		failed.Add(int64(requests))
// 		return
// 	}
//
// 	defer conn.Close()
//
// 	reader := bufio.NewReader(conn)
//
// 	packet := toPacket("set\r\nresults\r\nodd")
//
// 	for i := 0; i < requests; i++ {
//
// 		// write request
// 		if _, err := conn.Write(packet); err != nil {
// 			failed.Add(1)
// 			return
// 		}
//
// 		// read response
// 		buffer := make([]byte, 1024)
//
// 		_, err := reader.Read(buffer)
//
// 		if err != nil && !errors.Is(err, io.EOF) {
// 			failed.Add(1)
// 			return
// 		}
//
// 		success.Add(1)
// 	}
// }

func toPacket(msg string) []byte {
	size := len(msg)

	buffer := make([]byte, 4)

	binary.BigEndian.PutUint32(
		buffer,
		uint32(size),
	)

	buffer = append(buffer, []byte(msg)...)

	return buffer
}

