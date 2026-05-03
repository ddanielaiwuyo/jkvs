#!/bin/env bash
rm -rf target
javac -d target jkvs/Main.java
java -cp target jkvs.Main "$@"
