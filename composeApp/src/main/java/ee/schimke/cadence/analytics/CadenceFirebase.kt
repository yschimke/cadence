package ee.schimke.cadence.analytics

import android.content.Context
import android.os.Bundle
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import ee.schimke.cadence.platform.Analytics
import ee.schimke.livery.client.AnalyticsSink
import ee.schimke.livery.firebase.FirebaseAnalyticsSink

/**
 * Firebase Analytics, when this build has it. Release builds from CI carry the Firebase SDK and
 * configuration (`google-services.json`); local and PR builds have neither, so nothing is installed
 * and [liverySinks] is empty.
 *
 * No Firebase type appears in this object's fields or signatures: it is loaded in every build, and
 * a missing class there fails when the object is (reflection over its methods, as Robolectric does,
 * included). Everything that names Firebase is in [FirebaseBackend], loaded only once the SDK is
 * known to be there.
 */
object CadenceFirebase {
  @Volatile private var sinks: List<AnalyticsSink> = emptyList()

  /**
   * Sets [Analytics.current] to Firebase if it's available. Call once, from Application.onCreate.
   */
  fun install(context: Context) {
    if (!sdkPresent()) return
    val backend = runCatching { FirebaseBackend.create(context) }.getOrNull() ?: return
    sinks = backend.liverySinks
    Analytics.current = backend.analytics
  }

  /** Livery's events (`livery_*`), for the widgets' slot planner; empty without Firebase. */
  fun liverySinks(): List<AnalyticsSink> = sinks

  private fun sdkPresent(): Boolean = runCatching {
    Class.forName("com.google.firebase.analytics.FirebaseAnalytics")
  }.isSuccess
}

private class FirebaseBackend(val analytics: Analytics, val liverySinks: List<AnalyticsSink>) {
  companion object {
    /** Null when the app has no Firebase configuration. */
    fun create(context: Context): FirebaseBackend? {
      if (FirebaseApp.getApps(context).isEmpty()) return null
      val firebase = FirebaseAnalytics.getInstance(context)
      return FirebaseBackend(
        FirebaseCadenceAnalytics(firebase),
        listOf(FirebaseAnalyticsSink(firebase)),
      )
    }
  }
}

private class FirebaseCadenceAnalytics(private val firebase: FirebaseAnalytics) : Analytics {
  override fun event(name: String, params: Map<String, Any>) {
    firebase.logEvent(name.take(40), bundle(params))
  }

  override fun screen(name: String) {
    firebase.logEvent(
      FirebaseAnalytics.Event.SCREEN_VIEW,
      bundle(
        mapOf(
          FirebaseAnalytics.Param.SCREEN_NAME to name,
          FirebaseAnalytics.Param.SCREEN_CLASS to name,
        )
      ),
    )
  }

  private fun bundle(params: Map<String, Any>) =
    Bundle().apply {
      for ((key, value) in params.entries.take(25)) {
        val k = key.take(40)
        when (value) {
          is Boolean -> putLong(k, if (value) 1 else 0)
          is Int -> putLong(k, value.toLong())
          is Long -> putLong(k, value)
          is Float -> putDouble(k, value.toDouble())
          is Double -> putDouble(k, value)
          else -> putString(k, value.toString().take(100))
        }
      }
    }
}
