import 'package:pigeon/pigeon.dart';

class ResponseStarted {
  late Map<String?, List<String?>?> headers;
  late int statusCode;
  late String statusText;
  late bool isRedirect;
}

class ReadCompleted {
  late Uint8List data;
}

enum EventMessageType { responseStarted, readCompleted }

/// Encapsulates a message sent from Cronet to the Dart client.
class EventMessage {
  late EventMessageType type;

  // Set if [type] == responseStarted;
  ResponseStarted? responseStarted;

  // Set if [type] == readCompleted;
  ReadCompleted? readCompleted;
}

@HostApi()
abstract class NativeHttpClientApi {
  @async
  String? askCertificateAlias();

  @async
  void initializeClient(String? alias);

  String sendRequest(String url, String method, Uint8List body, Map<String, String> headers);

  void closeClient();

  void dummy(EventMessage message);
}
