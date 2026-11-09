package com.example.oddclock

import android.content.Context

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("odd_clock", Context.MODE_PRIVATE)

    fun dark(c: Context) = sp(c).getBoolean("dark", false)
    fun setDark(c: Context, v: Boolean) = sp(c).edit().putBoolean("dark", v).apply()

    fun marathi(c: Context) = sp(c).getBoolean("marathi", false)
    fun setMarathi(c: Context, v: Boolean) = sp(c).edit().putBoolean("marathi", v).apply()

    fun fahrenheit(c: Context) = sp(c).getBoolean("fahrenheit", false)
    fun setFahrenheit(c: Context, v: Boolean) = sp(c).edit().putBoolean("fahrenheit", v).apply()

    /** true = even minute shows the previous odd (24 -> 23), false = next odd (24 -> 25) */
    fun previousOdd(c: Context) = sp(c).getBoolean("prev", false)
    fun setPreviousOdd(c: Context, v: Boolean) = sp(c).edit().putBoolean("prev", v).apply()

    fun city(c: Context) = sp(c).getString("city", "Dehugaon") ?: "Dehugaon"
    fun lat(c: Context) = sp(c).getFloat("lat", 18.72f)
    fun lon(c: Context) = sp(c).getFloat("lon", 73.77f)
    fun setLocation(c: Context, city: String, lat: Float, lon: Float) =
        sp(c).edit().putString("city", city).putFloat("lat", lat).putFloat("lon", lon).apply()
}
