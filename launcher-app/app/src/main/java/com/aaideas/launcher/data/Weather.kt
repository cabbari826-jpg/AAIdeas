package com.aaideas.launcher.data

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.LocationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.roundToInt

data class WeatherNow(val city: String, val temp: Int, val max: Int, val min: Int, val text: String)

/**
 * Weather card data from Open-Meteo (no key). Prototype only: Open-Meteo's free tier
 * is for non-commercial use, so the store version needs a paid plan or another source.
 */
object Weather {
    @SuppressLint("MissingPermission")
    suspend fun load(context: Context): WeatherNow? = withContext(Dispatchers.IO) {
        runCatching {
            val lm = context.getSystemService(LocationManager::class.java)
            val loc = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER, LocationManager.GPS_PROVIDER)
                .mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
                .maxByOrNull { it.time } ?: return@runCatching null
            val lat = "%.2f".format(Locale.US, loc.latitude)
            val lon = "%.2f".format(Locale.US, loc.longitude)
            val url = URL("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,weather_code&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=1")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000; conn.readTimeout = 10_000
            val body = try { conn.inputStream.bufferedReader().readText() } finally { conn.disconnect() }
            val root = Json.parseToJsonElement(body).jsonObject
            val cur = root["current"]!!.jsonObject
            val daily = root["daily"]!!.jsonObject
            val city = runCatching {
                @Suppress("DEPRECATION")
                Geocoder(context, Locale("ru")).getFromLocation(loc.latitude, loc.longitude, 1)?.firstOrNull()?.locality
            }.getOrNull() ?: ""
            WeatherNow(
                city = city,
                temp = cur["temperature_2m"]!!.jsonPrimitive.double.roundToInt(),
                max = daily["temperature_2m_max"]!!.jsonArray[0].jsonPrimitive.double.roundToInt(),
                min = daily["temperature_2m_min"]!!.jsonArray[0].jsonPrimitive.double.roundToInt(),
                text = describe(cur["weather_code"]!!.jsonPrimitive.int),
            )
        }.getOrNull()
    }

    /** WMO weather codes in Russian. */
    fun describe(code: Int): String = when (code) {
        0 -> "Ясно"
        1, 2 -> "Малооблачно"
        3 -> "Облачно"
        45, 48 -> "Туман"
        in 51..57 -> "Морось"
        in 61..67, in 80..82 -> "Дождь"
        in 71..77, 85, 86 -> "Снег"
        in 95..99 -> "Гроза"
        else -> "—"
    }
}

@Suppress("unused")
private fun JsonObject.str(k: String) = this[k]?.jsonPrimitive?.content
