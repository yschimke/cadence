package ee.schimke.cadence.platform

import android.util.Log

/**
 * Records a caught failure in Crashlytics when the app was built with it (release CI builds inject
 * the Firebase configuration; other builds have no Firebase classes, so this only logs).
 *
 * Messages are dropped and only exception types and stack frames are sent: network and storage
 * errors can carry signed audio URLs, document paths or tokens.
 */
fun reportNonFatal(error: Throwable) {
  Log.w("Cadence", "Non-fatal failure", error)
  val crashlytics =
    runCatching {
        Class.forName("com.google.firebase.crashlytics.FirebaseCrashlytics")
          .getMethod("getInstance")
          .invoke(null)
      }
      .getOrNull() ?: return
  runCatching {
    crashlytics.javaClass
      .getMethod("recordException", Throwable::class.java)
      .invoke(crashlytics, withoutMessages(error))
  }
}
