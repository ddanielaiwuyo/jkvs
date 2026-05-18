package main

import (
	"bufio"
	"encoding/binary"
	"log"
	"net"
	"os"
	"strings"
)

func main() {
	conn, err := net.Dial("tcp", "localhost:9090")
	if !checkErr(err) {
		log.Fatal("could not dial server::", err)
	}
	stdinCh := make(chan string)
	go readFromStdin(stdinCh)
	responseCh := make(chan string)
	go readResponse(conn, responseCh)

	for {
		select {
		case req := <-stdinCh:
			go sendRequest(conn, req)
		case res := <-responseCh:
			println(res)
			print("> ")
		}
	}
}

func readFromStdin(stdinCh chan<- string) {
	scanner := bufio.NewScanner(os.Stdin)
	print("> ")
	for scanner.Scan() {
		stdinCh <- scanner.Text()
	}
}

func sendRequest(conn net.Conn, req string) {
	req = strings.Join(strings.Split(req, " "), "\r\n")
	raw := []byte(req)
	header := make([]byte, 4)
	binary.BigEndian.PutUint32(header, uint32(len(raw)))
	header = append(header, raw...)

	if _, err := conn.Write(header); !checkErr(err) {
		log.Fatal("Could not write to sever::", err)
	}

}

func checkErr(err error) bool {
	return err == nil
}

func readResponse(conn net.Conn, resCh chan<- string) {
	buffer := make([]byte, 2024)
	for {
		n, err := conn.Read(buffer)
		if !checkErr(err) {
			log.Fatal("Could not read from server::", err)
		}

		resCh <- string(buffer[:n])
	}
}
