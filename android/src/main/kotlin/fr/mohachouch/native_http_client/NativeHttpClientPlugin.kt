package fr.mohachouch.native_http_client

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.security.KeyChain
import androidx.annotation.NonNull
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.EventChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.lang.ref.WeakReference
import java.security.KeyStore
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager


class NativeHttpClientPlugin: FlutterPlugin, ActivityAware, NativeHttpClient.NativeHttpClientApi {
  private val mainScope = CoroutineScope(Dispatchers.Main)
  private lateinit var flutterPluginBinding: FlutterPlugin.FlutterPluginBinding

  protected val activity get() = activityReference.get()
  protected val applicationContext get() =
    contextReference.get() ?: activity?.applicationContext

  private var activityReference = WeakReference<Activity>(null)
  private var contextReference = WeakReference<Context>(null)
  private var client: OkHttpClient? = null
  private val channelId = AtomicInteger(0)
  private val mainThreadHandler = Handler(Looper.getMainLooper())

  override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    NativeHttpClient.NativeHttpClientApi.setup(flutterPluginBinding.binaryMessenger, this)
    contextReference = WeakReference(flutterPluginBinding.applicationContext)
    this.flutterPluginBinding = flutterPluginBinding
  }

  override fun onAttachedToActivity(binding: ActivityPluginBinding) {
    activityReference = WeakReference(binding.activity)
  }

  override fun onDetachedFromActivityForConfigChanges() {
    activityReference.clear()
  }

  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
    activityReference = WeakReference(binding.activity)
  }

  override fun onDetachedFromActivity() {
    activityReference.clear()
  }

  override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
    NativeHttpClient.NativeHttpClientApi.setup(binding.binaryMessenger, null)
  }

  override fun askCertificateAlias(result: NativeHttpClient.Result<String>?) {
    KeyChain.choosePrivateKeyAlias(
      activity!!,
      {
        result?.success(it);
      },
      null, null, null, -1, null
    )
  }

  override fun initializeClient(alias: String?, result: NativeHttpClient.Result<Void>?) {
    mainScope.launch {
      withContext(Dispatchers.IO) {
        var clientBuilder = OkHttpClient.Builder()

        if(alias != null){
          clientBuilder = clientBuilder
            .sslSocketFactory(applicationContext!!, alias, "")
        }
        client = clientBuilder
          .build()

        result?.success(null);
      }
    }
  }

  override fun sendRequest(
    url: String,
    method: String,
    body: ByteArray,
    headers: MutableMap<String, String>
  ): String {
    if(client == null){
      throw Exception("client must be set")
    }

    val channelName = "fr.mohachouch.native_http_client/native_http_client/" + channelId.incrementAndGet()
    val eventChannel = EventChannel(flutterPluginBinding.binaryMessenger, channelName)

    val streamHandler =
      object : EventChannel.StreamHandler {
        override fun onListen(arguments: Any?, events: EventChannel.EventSink) {
          try {
            var request = createRequest(url, method, body, headers)

            client!!
              .newCall(request).enqueue(object : Callback {
                override fun onResponse(call: Call, response: Response) {
                  mainThreadHandler.post {
                    events.success(
                      NativeHttpClient.EventMessage.Builder()
                        .setType(NativeHttpClient.EventMessageType.RESPONSE_STARTED)
                        .setResponseStarted(
                          NativeHttpClient.ResponseStarted.Builder()
                            .setStatusCode(response.code.toLong())
                            .setStatusText(response.message)
                            .setHeaders(response.headers.toMultimap())
                            .setIsRedirect(response.isRedirect)
                            .build()
                        )
                        .build()
                        .toMap()
                    )
                  }

                  val data = response.body!!.bytes();

                  mainThreadHandler.post {
                    events.success(
                      NativeHttpClient.EventMessage.Builder()
                        .setType(NativeHttpClient.EventMessageType.READ_COMPLETED)
                        .setReadCompleted(NativeHttpClient.ReadCompleted.Builder().setData(data).build())
                        .build()
                        .toMap()
                    )

                    events.endOfStream()
                  }
                }
                override fun onFailure(call: Call, e: IOException) {
                  mainThreadHandler.post { events.error("NativeHttpClientPlugin", e.toString(), null) }
                }
              })
          } catch (e: Exception) {
            mainThreadHandler.post { events.error("NativeHttpClientPlugin", e.toString(), null) }
          }
        }

        override fun onCancel(arguments: Any?) {}
      }

    eventChannel.setStreamHandler(streamHandler)

    return channelName
  }

  fun createRequest(
    url: String,
    method: String,
    body: ByteArray,
    headers: MutableMap<String, String>
  ): Request {
    var requestBuilder = Request.Builder()
      .url(url);

    for (header in headers) {
      requestBuilder =
        requestBuilder.addHeader(header.key, header.value)
    }

    requestBuilder = if(method == "GET"){
      requestBuilder.get()
    }else{
      requestBuilder.method(method, body.toRequestBody())
    }

    return requestBuilder.build();
  }

  override fun closeClient() {
    client = null;
  }

  override fun dummy(message: NativeHttpClient.EventMessage) {
  }
}

fun OkHttpClient.Builder.sslSocketFactory(applicationContext:Context, alias: String, password:String) : OkHttpClient.Builder {
  val certificateChain = KeyChain.getCertificateChain(applicationContext, alias)!!
  val privateKey = KeyChain.getPrivateKey(applicationContext, alias)!!

  val trustManagerFactory =
    TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
  trustManagerFactory.init(null as KeyStore?)

  val x509KeyManager = CustomX509KeyManager(alias, certificateChain, privateKey)

  val sslContext = SSLContext.getInstance("TLS")
  sslContext.init(arrayOf(x509KeyManager), trustManagerFactory.trustManagers, null)

  return this.sslSocketFactory(sslContext.socketFactory, trustManagerFactory.trustManagers[0] as X509TrustManager);
}