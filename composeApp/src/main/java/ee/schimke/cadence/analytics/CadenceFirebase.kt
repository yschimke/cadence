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
 * configuration (`google-services.json`); local and PR builds have neither, so [analytics] is null
 * and [liverySink] empty, and nothing is sent.
 */
object CadenceFirebase {
  @Volatile private var instance: FirebaseAnalytics? = null

  /**
   * Sets [Analytics.current] to Firebase if it's available. Call once, from Application.onCreate.
   */
  fun install(context: Context) {
    val firebase = analytics(context) ?: return
    instance = firebase
    Analytics.current = FirebaseCadenceAnalytics(firebase)
  }

  /** Livery's events (`livery_*`), for the widgets' slot planner; empty without Firebase. */
  fun liverySinks(): List<AnalyticsSink> =
    listOfNotNull(instance?.let { FirebaseAnalyticsSink(it) })

  private fun analytics(context: Context): FirebaseAnalytics? = runCatching {
    // Throws NoClassDefFoundError in builds without the SDK.
    Class.forName("com.google.firebase.analytics.FirebaseAnalytics")
    if (FirebaseApp.getApps(context).isEmpty()) return null
    FirebaseAnalytics.getInstance(context)
  }
    .getOrNull()
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
