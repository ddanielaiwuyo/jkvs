#!/usr/bin/env bash
set -e

mvn package
native-image --no-fallback \
  -H:IncludeResources="log4j2.xml" \
  -jar target/jkvs-1.0-SNAPSHOT.jar \
  jkvs
