package ee.schimke.cadence.net

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

/**
 * Shared Ktor [HttpClient] instances for JVM and Android backed by one [OkHttpClient] connection
 * pool and dispatcher.
 */
internal object SharedHttpClient {
  val okHttp: OkHttpClient by lazy {
    OkHttpClient.Builder()
      .connectTimeout(30, TimeUnit.SECONDS)
      .readTimeout(60, TimeUnit.SECONDS)
      .build()
  }

  /** General-purpose Ktor client sharing [okHttp]'s connection pool. */
  val client: HttpClient by lazy { HttpClient(OkHttp) { engine { preconfigured = okHttp } } }

  /**
   * Non-redirecting Ktor client sharing [okHttp]'s connection pool, used when authenticated
   * requests must never forward credentials across redirects or when public downloads validate each
   * redirect hop explicitly.
   */
  val noRedirectsClient: HttpClient by lazy {
    val noRedirectOkHttp =
      okHttp.newBuilder().followRedirects(false).followSslRedirects(false).build()
    HttpClient(OkHttp) {
      followRedirects = false
      engine { preconfigured = noRedirectOkHttp }
    }
  }
}
