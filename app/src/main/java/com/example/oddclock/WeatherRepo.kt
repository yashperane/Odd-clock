package com.example.oddclock

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Fetches temperature + sunrise/sunset from Open-Meteo (free, no API key). */
object WeatherRepo {
    private const val STALE_MS = 30 * 60 * 1000L

    data class Snapshot(val tempC: Float, val code: Int, val sunrise: Long, val sunset: Long, val fetchedAt: Long)

    private fun sp(c: Context) = c.getSharedPreferences("odd_weather", Context.MODE_PRIVATE)

    fun cached(c: Context): Snapshot? {
        val s = sp(c)
        if (!s.contains("fetched")) return null
        return Snapshot(
            s.getFloat("t", 0f), s.getInt("code", 0),
            s.getLong("sr", 0L), s.getLong("ss", 0L), s.getLong("fetched", 0L)
        )
    }

    fun clear(c: Context) = sp(c).edit().clear().apply()

    /** Returns true if fresh data was downloaded. Must be called off the main thread. */
    fun refreshIfStale(c: Context, force: Boolean = false): Boolean {
        val last = sp(c).getLong("fetched", 0L)
        if (!force && System.currentTimeMillis() - last < STALE_MS) return false
        return try {
            fetch(c)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun fetch(c: Context) {
        val lat = Prefs.lat(c)
        val lon = Prefs.lon(c)
        val url = URL(
            "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,weather_code&daily=sunrise,sunset&timezone=auto&forecast_days=1"
        )
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        val body = try {
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
        val j = JSONObject(body)
        val cur = j.getJSONObject("current")
        val daily = j.getJSONObject("daily")
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone(j.optString("timezone", "Asia/Kolkata"))
        val sr = fmt.parse(daily.getJSONArray("sunrise").getString(0))!!.time
        val ss = fmt.parse(daily.getJSONArray("sunset").getString(0))!!.time
        sp(c).edit()
            .putFloat("t", cur.getDouble("temperature_2m").toFloat())
            .putInt("code", cur.getInt("weather_code"))
            .putLong("sr", sr)
            .putLong("ss", ss)
            .putLong("fetched", System.currentTimeMillis())
            .apply()
    }
}
