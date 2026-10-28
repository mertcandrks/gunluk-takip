package com.uykutakip.app.mcp

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

internal class McpContract(context: Context) {
    private val root = JSONObject(context.assets.open("mcp_contract.json").bufferedReader().use { it.readText() })
    val tools = root.getJSONArray("tools")
    fun tool(name: String): JSONObject = (0 until tools.length()).map { tools.getJSONObject(it) }
        .firstOrNull { it.getString("name") == name } ?: error("UNKNOWN_TOOL")

    fun validate(tool: JSONObject, args: JSONObject) {
        val names = tool.getJSONArray("arguments")
        val allowed = (0 until names.length()).map { names.getString(it) }.toSet()
        require(args.keys().asSequence().all { it in allowed })
        tool.optJSONArray("required")?.let { required -> for (i in 0 until required.length()) require(args.has(required.getString(i))) }
        if (tool.optBoolean("write")) require(args.has("requestId"))
        for (name in allowed) if (args.has(name)) validateValue(args.get(name), root.getJSONObject("properties").getJSONObject(name))
        for ((field, options) in listOf("action" to "actions", "kind" to "kinds")) {
            if (args.has(field) && tool.has(options)) require((0 until tool.getJSONArray(options).length()).any { tool.getJSONArray(options).getString(it) == args.getString(field) })
        }
        for (field in listOf("from", "to", "day")) if (args.has(field)) java.time.LocalDate.parse(args.getString(field))
        if (args.has("from") && args.has("to")) require(args.getString("from") <= args.getString("to"))
    }

    private fun validateValue(value: Any, schema: JSONObject) {
        when (schema.getString("type")) {
            "string" -> {
                require(value is String)
                require(value.length >= schema.optInt("minLength", 0) && value.length <= schema.optInt("maxLength", Int.MAX_VALUE))
                if (schema.has("pattern")) require(Regex(schema.getString("pattern")).matches(value))
            }
            "boolean" -> require(value is Boolean)
            "integer" -> {
                require(value is Number)
                val number = value.toDouble()
                require(number.isFinite() && number == value.toLong().toDouble())
                if (schema.has("minimum")) require(number >= schema.getDouble("minimum"))
                if (schema.has("maximum")) require(number <= schema.getDouble("maximum"))
            }
            "array" -> {
                require(value is JSONArray && value.length() >= schema.optInt("minItems", 0) && value.length() <= schema.optInt("maxItems", 100))
                for (i in 0 until value.length()) validateValue(value.get(i), schema.getJSONObject("items"))
            }
            "object" -> {
                require(value is JSONObject)
                val properties = schema.getJSONObject("properties")
                require(value.keys().asSequence().all { properties.has(it) })
                schema.optJSONArray("required")?.let { for (i in 0 until it.length()) require(value.has(it.getString(i))) }
                value.keys().forEach { validateValue(value.get(it), properties.getJSONObject(it)) }
            }
            else -> error("Unsupported contract type")
        }
        schema.optJSONArray("enum")?.let { choices -> require((0 until choices.length()).any { choices.get(it).toString() == value.toString() }) }
    }
}
