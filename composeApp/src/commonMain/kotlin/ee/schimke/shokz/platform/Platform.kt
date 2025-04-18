package ee.schimke.shokz.platform

import okio.Path

interface Platform {
    fun listRecursively(path: Path): List<Path> = TODO()

    val name: String
}