package ee.schimke.cadence.platform

/**
 * Product analytics: which screens people use and what they do in them. The Android app sets
 * [current] to Firebase Analytics in release builds (CI injects the Firebase configuration); other
 * builds, desktop, web and tests keep [None], which drops everything.
 *
 * Names and parameters follow Firebase's rules: snake_case, at most 40 characters, string values of
 * at most 100 characters. Never put personal data in them: no titles, file names or addresses.
 */
interface Analytics {
  fun event(name: String, params: Map<String, Any> = emptyMap())

  /** A screen the user navigated to, by its route's name (`Home`, `ManageSync`, …). */
  fun screen(name: String)

  object None : Analytics {
    override fun event(name: String, params: Map<String, Any>) = Unit

    override fun screen(name: String) = Unit
  }

  companion object {
    @Volatile var current: Analytics = None
  }
}
