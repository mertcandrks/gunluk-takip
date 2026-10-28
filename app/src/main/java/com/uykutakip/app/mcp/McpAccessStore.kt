package com.uykutakip.app.mcp

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

class McpAccessStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("mcp_access", Context.MODE_PRIVATE)
    var enabled: Boolean
        get() = prefs.getBoolean("enabled", false)
        set(value) { check(prefs.edit().putBoolean("enabled", value).commit()) }
    var writes: Boolean
        get() = prefs.getBoolean("writes", false)
        set(value) { check(prefs.edit().putBoolean("writes", value).commit()) }
    var privateAccess: Boolean
        get() = prefs.getBoolean("private", false)
        set(value) { check(prefs.edit().putBoolean("private", value).commit()) }
    var deletes: Boolean
        get() = prefs.getBoolean("deletes", false)
        set(value) { check(prefs.edit().putBoolean("deletes", value).commit()) }
    val token: String get() = prefs.getString("token", "").orEmpty()
    fun rotate(): String {
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val token = Base64.encodeToString(bytes, Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING)
        check(prefs.edit().putString("token", token).commit())
        return token
    }
    fun accepts(value: String): Boolean = value.length in 40..128 && token.isNotEmpty() &&
        MessageDigest.isEqual(value.toByteArray(Charsets.UTF_8), token.toByteArray(Charsets.UTF_8))
}
