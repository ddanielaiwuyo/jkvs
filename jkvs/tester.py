#!/usr/bin/env python3
import socket
import struct

HOST = "127.0.0.1"
PORT = 9090

sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
sock.connect((HOST, PORT))

for i in range(1000000):
    key = f"key_{i}"
    value = f"val_{i}"
    
    # Build message
    message = f"set\r\n{key}\r\n{value}\r\n".encode()
    
    # Send 4-byte length prefix (big-endian) + message
    length = len(message)
    sock.sendall(struct.pack('>I', length) + message)
    
    # Read 4-byte response length
    resp_len_bytes = sock.recv(4)
    resp_len = struct.unpack('>I', resp_len_bytes)[0]
    
    # Read response
    response = sock.recv(resp_len)

sock.close()
print("Completed 1000000 requests")
