package ee.schimke.cadence.platform

/** The same exception chain, keeping types and stack frames but no message text. */
fun withoutMessages(error: Throwable, depth: Int = 0): Throwable {
  val cause =
    error.cause?.takeIf { it !== error && depth < 8 }?.let { withoutMessages(it, depth + 1) }
  return NonFatal(error.javaClass.name, cause).also { it.stackTrace = error.stackTrace }
}

/** Named for the original type, so Crashlytics groups by what actually failed. */
private class NonFatal(type: String, cause: Throwable?) : RuntimeException(type, cause)
