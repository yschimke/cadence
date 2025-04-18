package ee.schimke.shokz.metro

import ee.schimke.shokz.platform.Platform
import okio.FileSystem

interface AppGraph {
    val platform: Platform

    val fileSystem: FileSystem
}