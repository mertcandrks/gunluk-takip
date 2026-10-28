package com.uykutakip.app.sync

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

data class PcSyncCredentials(val host: String, val code: String, val serviceId: String = "")

object PcSyncQrPayload {
    private const val SCHEME = "gunluktakip"
    private const val AUTHORITY = "sync"
    private const val VERSION = "1"
    private const val PORT = "47832"

    fun parse(raw: String): PcSyncCredentials? = runCatching {
        val uri = URI(raw.trim())
        require(uri.scheme.equals(SCHEME, ignoreCase = true))
        require(uri.host.equals(AUTHORITY, ignoreCase = true))
        val values = uri.rawQuery.orEmpty()
            .split('&')
            .filter(String::isNotBlank)
            .associate { part ->
                val separator = part.indexOf('=')
                require(separator > 0)
                decode(part.substring(0, separator)) to decode(part.substring(separator + 1))
            }
        require(values["v"] == VERSION)
        require(values["port"] == PORT)
        val host = values.getValue("host").trim()
        val code = values.getValue("code")
        require(host.isNotBlank() && host.length <= 253)
        require(host.none { it.isWhitespace() || it.isISOControl() || it == '/' || it == '\\' })
        require(code.length == 8 && code.all(Char::isDigit))
        val serviceId = values["id"].orEmpty()
        require(serviceId.isEmpty() || serviceId.matches(Regex("[a-f0-9]{32}")))
        PcSyncCredentials(host, code, serviceId)
    }.getOrNull()

    private fun decode(value: String): String = URLDecoder.decode(value, StandardCharsets.UTF_8.name())
}
