package com.uykutakip.data

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

internal fun json(vararg fields: Pair<String, Any?>) = JSONObject().apply {
    fields.forEach { (name, value) -> put(name, value ?: JSONObject.NULL) }
}

internal fun canonicalJson(value: Any?): String = when (value) {
    is JSONObject -> value.keys().asSequence().toList().sorted().joinToString(",", "{", "}") { JSONObject.quote(it) + ":" + canonicalJson(value.get(it)) }
    is JSONArray -> (0 until value.length()).joinToString(",", "[", "]") { canonicalJson(value.get(it)) }
    is String -> JSONObject.quote(value)
    null, JSONObject.NULL -> "null"
    else -> value.toString()
}

internal fun mcpHash(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }

internal fun JSONObject.withRevision(): JSONObject = apply { put("revision", mcpHash(canonicalJson(this))) }

internal fun guardRevision(current: JSONObject, args: JSONObject, field: String = "expectedRevision") {
    check(args.getString(field) == mcpHash(canonicalJson(current))) { "REVISION_CONFLICT" }
}

internal fun page(records: List<JSONObject>, args: JSONObject): JSONObject {
    val offset = args.optInt("offset", 0)
    val limit = args.optInt("limit", 20)
    require(offset >= 0 && limit in 1..100)
    val rows = JSONArray()
    var bytes = 0
    for (record in records.drop(offset).take(limit)) {
        val size = record.toString().toByteArray(Charsets.UTF_8).size
        require(size <= 124_000) { "RESPONSE_TOO_LARGE" }
        if (bytes + size > 124_000) break
        rows.put(record); bytes += size
    }
    return json("items" to rows, "total" to records.size,
        "nextOffset" to (offset + rows.length()).takeIf { it < records.size })
}
