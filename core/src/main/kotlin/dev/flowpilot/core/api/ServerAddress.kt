package dev.flowpilot.core.api

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Turns what someone types in the address field into a server base URL. */
object ServerAddress {
    /** The port `opencode service start` listens on unless `opencode service set port` changed it. */
    const val DEFAULT_PORT = 49374

    sealed interface Result {
        data class Ok(val baseUrl: String) : Result
        /** The address points at the phone itself (localhost, 0.0.0.0), which can never reach the computer. */
        data object Loopback : Result
        data object Invalid : Result
    }

    private val loopback = setOf("localhost", "127.0.0.1", "0.0.0.0", "::1", "::")

    /**
     * Adds `http://` when the scheme is missing, and the service's default port when an `http` address has none,
     * so `192.168.1.20` becomes `http://192.168.1.20:49374`.
     */
    fun normalize(input: String): Result {
        val text = input.trim()
        if (text.isEmpty()) return Result.Invalid
        val withScheme = if (text.contains("://")) text else "http://$text"
        val url = withScheme.toHttpUrlOrNull() ?: return Result.Invalid
        if (url.host in loopback || url.host.startsWith("127.")) return Result.Loopback
        val authority = withScheme.substringAfter("://").substringBefore('/').substringBefore('?').substringBefore('#').substringAfterLast('@')
        val hasPort = if (authority.startsWith("[")) authority.substringAfter("]").startsWith(":") else authority.contains(':')
        val b = url.newBuilder().query(null).fragment(null)
        if (!hasPort && url.scheme == "http") b.port(DEFAULT_PORT)
        return Result.Ok(b.build().toString().trimEnd('/'))
    }
}
