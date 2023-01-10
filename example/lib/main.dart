import 'package:flutter/material.dart';
import 'package:native_http_client/native_http_client_api.dart';
import 'package:native_http_client/native_http_client.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatefulWidget {
  const MyApp({super.key});

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  final nativeHttpClientApi = NativeHttpClientApi();

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(
          title: const Text('Plugin example app'),
        ),
        body: Center(
          child: InkWell(
              onTap: () async {
                NativeHttpClient nativeHttpClient = NativeHttpClient();
                var alias = await nativeHttpClientApi.askCertificateAlias();

                await nativeHttpClientApi.initializeClient(alias);

                var response = await nativeHttpClient.get(Uri.parse("URL"));

                debugPrint("Response : ${response.statusCode}");
              },
              child: const Text('Running on:')),
        ),
      ),
    );
  }
}
