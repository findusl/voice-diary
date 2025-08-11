package de.lehrbaum.voiry

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform