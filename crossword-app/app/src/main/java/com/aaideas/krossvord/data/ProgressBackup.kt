package com.aaideas.krossvord.data

import android.content.Context
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/**
 * Progress in one small file the player can keep or send to a new phone: solved levels, typed
 * letters, XP, themes, streaks, collection and settings. Works without Google backup (many cheap
 * phones and custom ROMs have none). Premium is not in the file: it comes from Google Play.
 */
object ProgressBackup {
    const val MARK = "acar-soz-progress"
    /** The app's SharedPreferences files that hold progress and settings. */
    val FILES = listOf("progress", "daily_extra", "daily_word", "glass", "app_icon", "packs_dl")

    fun export(context: Context): String {
        val files = FILES.associateWith { name ->
            val all = context.getSharedPreferences(name, Context.MODE_PRIVATE).all
            JsonObject(all.mapNotNull { (k, v) -> encode(v)?.let { k to it } }.toMap())
        }
        return JsonObject(
            mapOf(
                "app" to JsonPrimitive(MARK),
                "version" to JsonPrimitive(1),
                "prefs" to JsonObject(files),
            )
        ).toString()
    }

    /** Replaces the progress with the file's. Returns false (and changes nothing) for a file that is not ours. */
    fun import(context: Context, text: String): Boolean {
        val root = runCatching { kotlinx.serialization.json.Json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return false
        if (root["app"]?.jsonPrimitive?.contentOrNull != MARK) return false
        val prefs = root["prefs"]?.let { runCatching { it.jsonObject }.getOrNull() } ?: return false
        // Decode everything first, so a broken file never leaves half the progress replaced.
        val decoded = prefs.filterKeys { it in FILES }.mapValues { (_, obj) ->
            runCatching { obj.jsonObject.mapValues { (_, v) -> decode(v.jsonObject) } }.getOrNull() ?: return false
        }
        for ((name, values) in decoded) {
            val e = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
            values.forEach { (k, v) ->
                when (v) {
                    is Boolean -> e.putBoolean(k, v)
                    is Int -> e.putInt(k, v)
                    is Long -> e.putLong(k, v)
                    is Float -> e.putFloat(k, v)
                    is String -> e.putString(k, v)
                    is Set<*> -> e.putStringSet(k, v.filterIsInstance<String>().toSet())
                }
            }
            e.commit()
        }
        return true
    }

    private fun encode(v: Any?): JsonObject? = when (v) {
        is Boolean -> typed("b", JsonPrimitive(v))
        is Int -> typed("i", JsonPrimitive(v))
        is Long -> typed("l", JsonPrimitive(v))
        is Float -> typed("f", JsonPrimitive(v))
        is String -> typed("s", JsonPrimitive(v))
        is Set<*> -> typed("set", JsonArray(v.filterIsInstance<String>().sorted().map { JsonPrimitive(it) }))
        else -> null
    }

    private fun typed(t: String, v: kotlinx.serialization.json.JsonElement) = JsonObject(mapOf("t" to JsonPrimitive(t), "v" to v))

    private fun decode(o: JsonObject): Any {
        val v = o.getValue("v")
        return when (o.getValue("t").jsonPrimitive.content) {
            "b" -> v.jsonPrimitive.booleanOrNull ?: v.jsonPrimitive.boolean
            "i" -> v.jsonPrimitive.int
            "l" -> v.jsonPrimitive.long
            "f" -> v.jsonPrimitive.float
            "s" -> v.jsonPrimitive.content
            "set" -> v.jsonArray.map { it.jsonPrimitive.content }.toSet()
            else -> error("unknown type")
        }
    }
}
