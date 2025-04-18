package ee.schimke.shokz

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform