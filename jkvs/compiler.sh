#!/usr/bin/env bash
set -e

mvn clean compile 

mvn package
native-image --no-fallback \
  -H:IncludeResources="log4j2.xml" \
  -jar target/jkvs-1.0-SNAPSHOT.jar \
  jkvs


echo "BUILDING jkvs-client"
native-image --no-fallback \
  -H:+ReportExceptionStackTraces \
  -H:IncludeResources="log4j2.xml" \
  -jar target/jkvs-client.jar \
  jkvs-client 


echo "BUILDING jkvs-server"
native-image --no-fallback \
  -H:+ReportExceptionStackTraces \
  -H:IncludeResources="log4j2.xml" \
  -jar target/jkvs-server.jar \
  jkvs-server 
