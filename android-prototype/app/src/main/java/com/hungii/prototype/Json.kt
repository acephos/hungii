package com.hungii.prototype

import org.json.JSONArray
import org.json.JSONObject

// Android's optString turns JSON null into "null"; these helpers keep missing values absent.
// Earlier versions also stored that text, so a literal "null" is treated as absent too.

internal fun JSONObject.stringOrNull(name: String): String? {
    if (isNull(name)) return null
    return optString(name).takeIf { it.isNotBlank() && it != "null" }
}

internal fun JSONObject.doubleOrNull(name: String): Double? {
    if (isNull(name)) return null
    return (opt(name) as? Number)?.toDouble()?.takeIf(Double::isFinite)
}

/** Money is shown only when it is a finite, non-negative amount. */
internal fun JSONObject.moneyOrNull(name: String): Double? = doubleOrNull(name)?.takeIf { it >= 0 }

internal fun JSONObject.intOrNull(name: String): Int? = if (isNull(name)) null else (opt(name) as? Number)?.toInt()

internal fun JSONObject.longOrNull(name: String): Long? = if (isNull(name)) null else (opt(name) as? Number)?.toLong()

internal fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

internal fun JSONArray?.strings(): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { optString(it).takeIf(String::isNotEmpty) }
}

internal fun Any?.orJsonNull(): Any = this ?: JSONObject.NULL
