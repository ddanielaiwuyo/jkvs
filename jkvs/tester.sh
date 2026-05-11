#!/usr/bin/env bash
HOST="127.0.0.1"
PORT="9090"
startCounter=0
limit=100000

exec 3<>/dev/tcp/$HOST/$PORT

while [ $startCounter -lt $limit ]; do
    key="key_$startCounter"
    value="val_$startCounter"
    
    # Simple text protocol with \r\n delimiters
    # Format: set\r\nkey\r\nvalue\r\n
    printf 'set\r\n%s\r\n%s\r\n' "$key" "$value" >&3
    
    # Read response (assumes server sends response ending with \r\n or \n)
    IFS= read -r response <&3
    
    startCounter=$((startCounter + 1))
done

exec 3<&-
exec 3>&-

echo "Completed $limit requests"

#
# HOST="127.0.0.1"
# PORT="9090"
# startCounter=0
# limit=100000
#
# exec 3<>/dev/tcp/$HOST/$PORT
#
# while [ $startCounter -lt $limit ]; do
#     key="key_$startCounter"
#     value="val_$startCounter"
#     # json=$(printf '{"command":"set","key":"%s","value":"%s"}' "$key" "$key")
# 		req=$(printf 'set\r\n%s\r\n%s' "$key" "$value")
#
#     start_ns=$(date +%s%N)
#
#     # Send JSON with newline terminator
#     echo "$req" >&3  # Changed from echo -n to echo (includes newline)
#
#     # Read response
#     IFS= read -r response <&3
#
#     end_ns=$(date +%s%N)
#     latency_ns=$((end_ns - start_ns))
#
#     startCounter=$((startCounter + 1))
# done
#
# exec 3<&-
# exec 3>&-
