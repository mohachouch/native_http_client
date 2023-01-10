import 'dart:async';

import 'package:flutter/services.dart';
import 'package:http/http.dart';
import 'native_http_client_api.dart';

class NativeHttpClient extends BaseClient {
  late final nativeHttpClientApi = NativeHttpClientApi();

  @override
  Future<StreamedResponse> send(BaseRequest request) async {
    final stream = request.finalize();
    final body = await stream.toBytes();

    var channelId = await nativeHttpClientApi.sendRequest(
        request.url.toString(), request.method, body, request.headers);

    final responseCompleter = Completer<ResponseStarted>();
    final responseDataController = StreamController<Uint8List>();

    void raiseException(Exception exception) {
      if (responseCompleter.isCompleted) {
        responseDataController.addError(exception);
      } else {
        responseCompleter.completeError(exception);
      }
      responseDataController.close();
    }

    final e = EventChannel(channelId);
    e.receiveBroadcastStream().listen(
        (e) {
          final event = EventMessage.decode(e as Object);
          switch (event.type) {
            case EventMessageType.responseStarted:
              responseCompleter.complete(event.responseStarted!);
              break;
            case EventMessageType.readCompleted:
              responseDataController.sink.add(event.readCompleted!.data);
              break;
            default:
              throw UnsupportedError('Unexpected event: ${event.type}');
          }
        },
        onDone: responseDataController.close,
        onError: (Object e) {
          final pe = e as PlatformException;
          raiseException(ClientException(pe.message!, request.url));
        });

    final result = await responseCompleter.future;
    final responseHeaders = (result.headers.cast<String, List<Object?>>())
        .map((key, value) => MapEntry(key.toLowerCase(), value.join(',')));

    final contentLengthHeader = responseHeaders['content-length'];

    return StreamedResponse(responseDataController.stream, result.statusCode,
        contentLength: contentLengthHeader == null
            ? null
            : int.tryParse(contentLengthHeader),
        reasonPhrase: result.statusText,
        request: request,
        isRedirect: result.isRedirect,
        headers: responseHeaders);
  }
}
