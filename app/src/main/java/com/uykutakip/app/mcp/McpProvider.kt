package com.uykutakip.app.mcp

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.util.Base64
import com.uykutakip.app.BuildConfig
import com.uykutakip.app.TrackerApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

class McpProvider : ContentProvider() {
    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val uid = Binder.getCallingUid()
        val result = try {
            check(uid == 2000 || uid == android.os.Process.myUid()) { "FORBIDDEN" }
            require(method == "rpc" && arg != null && arg.length <= 24000)
            val bytes = Base64.decode(arg, Base64.DEFAULT)
            require(bytes.size <= 16000)
            val envelope = JSONObject(String(bytes, Charsets.UTF_8))
            val toolName = envelope.getString("tool")
            val args = envelope.getJSONObject("arguments")
            val ctx = requireNotNull(context)
            val access = McpAccessStore(ctx)
            val contract = McpContract(ctx)
            if (toolName == "pair_access") {
                check(access.enabled && args.length() == 0 && McpPairingWindow.consume()) { "PAIRING_CLOSED" }
                return Bundle().apply { putString("payload", Base64.encodeToString(
                    JSONObject().put("ok", true).put("data", JSONObject().put("token", access.token)).toString().toByteArray(Charsets.UTF_8), Base64.NO_WRAP)) }
            }
            val tool = contract.tool(toolName)
            contract.validate(tool, args)
            val data = if (toolName == "app_status") JSONObject()
                .put("apiVersion", 1).put("version", BuildConfig.VERSION_NAME)
                .put("timezone", java.time.ZoneId.systemDefault().id).put("nowMillis", System.currentTimeMillis())
                .put("enabled", access.enabled).put("writes", access.writes)
                .put("privateAccess", access.privateAccess).put("deletes", access.deletes)
            else {
                check(access.enabled && access.accepts(envelope.optString("token", ""))) { "UNAUTHORIZED" }
                if (tool.optBoolean("write")) check(access.writes) { "WRITE_DISABLED" }
                if (tool.optBoolean("private") || args.optString("kind", "") == "private") check(access.privateAccess) { "PRIVATE_DISABLED" }
                if (tool.optBoolean("destructive")) check(access.deletes) { "DELETE_DISABLED" }
                val identity = Binder.clearCallingIdentity()
                try {
                    runBlocking(Dispatchers.IO) {
                        withTimeout(15000) {
                            val app = ctx.applicationContext as TrackerApplication
                            app.dataReady.await()
                            app.container.mcpRepository.execute(toolName, args, tool.optBoolean("write"))
                        }
                    }
                } finally { Binder.restoreCallingIdentity(identity) }
            }
            JSONObject().put("ok", true).put("data", data)
        } catch (error: Exception) {
            val safe = setOf("FORBIDDEN", "UNAUTHORIZED", "WRITE_DISABLED", "PRIVATE_DISABLED", "DELETE_DISABLED",
                "REVISION_CONFLICT", "REQUEST_ID_CONFLICT", "PREVIEW_REQUIRED", "ALREADY_ACTIVE", "UNKNOWN_TOOL", "PAIRING_CLOSED", "RESPONSE_TOO_LARGE")
            val code = error.message?.takeIf { it in safe } ?: if (error is IllegalArgumentException || error is org.json.JSONException || error is java.time.DateTimeException) "INVALID_ARGUMENT" else "OPERATION_FAILED"
            val message = when (code) {
                "REVISION_CONFLICT" -> "Record changed. Read it again and review the change before using a new requestId."
                "REQUEST_ID_CONFLICT" -> "This requestId already belongs to different arguments. Identical retries must reuse the original arguments."
                "UNAUTHORIZED" -> "MCP access is disabled or the key is invalid. Pair again from the app settings."
                "INVALID_ARGUMENT" -> "Check the required fields for this action, field types, lengths and dates in the tool description."
                "RESPONSE_TOO_LARGE" -> "Use a smaller page or batch."
                "OPERATION_FAILED" -> "Operation failed. If the connection was interrupted, retry the exact requestId and arguments."
                else -> "Check the application's access settings and current record state."
            }
            JSONObject().put("ok", false).put("error", JSONObject().put("code", code)
                .put("message", message))
        }
        var raw = result.toString().toByteArray(Charsets.UTF_8)
        if (raw.size > 128000) raw = "{\"ok\":false,\"error\":{\"code\":\"RESPONSE_TOO_LARGE\",\"message\":\"Use a smaller page or batch.\"}}".toByteArray()
        return Bundle().apply { putString("payload", Base64.encodeToString(raw, Base64.NO_WRAP)) }
    }

    override fun getType(uri: Uri): String? = null
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = throw UnsupportedOperationException()
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
}
