package ee.schimke.shokz.home

import dev.zacsweers.metro.Inject
import ee.schimke.shokz.platform.Platform

@Inject
class Greeting(private val platform: Platform) {
    fun greet(): String {
        return "Hello, ${platform.name}!"
    }
}