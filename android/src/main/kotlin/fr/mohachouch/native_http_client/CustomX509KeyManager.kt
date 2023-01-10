package fr.mohachouch.native_http_client

import java.net.Socket
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.X509Certificate
import javax.net.ssl.X509KeyManager

class CustomX509KeyManager(private val alias: String, private val certificateChain: Array<X509Certificate>, private val privateKey: PrivateKey) :
    X509KeyManager {
  override fun getClientAliases(keyType: String?, issuers: Array<Principal>): Array<String> {
    return arrayOf(alias)
  }

  override fun chooseClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, socket: Socket?): String {
    return alias
  }

  override fun getServerAliases(keyType: String?, issuers: Array<Principal>): Array<String> {
    return arrayOf()
  }

  override fun chooseServerAlias(keyType: String?, issuers: Array<Principal>, socket: Socket): String {
    return ""
  }

  override fun getCertificateChain(alias: String?): Array<X509Certificate> {
    return certificateChain
  }

  override fun getPrivateKey(alias: String?): PrivateKey {
    return privateKey
  }
}