#!/bin/sh

# Generate 

flutter pub run pigeon \
  --input pigeons/native_http_client_api.dart \
  --dart_out lib/native_http_client_api.dart \
  --java_out android/src/main/java/fr/mohachouch/native_http_client/NativeHttpClient.java \
  --java_package "fr.mohachouch.native_http_client"

flutter format lib/native_http_client_api.dart