package com.example.oddclock

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.text.SpannableStringBuilder
import android.text.format.DateFormat
import android.text.style.RelativeSizeSpan
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

object WidgetRenderer {

    private const val DAY = 24L * 60 * 60 * 1000

    /** Core idea: an even minute is never shown. */
    fun oddCalendar(now: Calendar, previous: Boolean): Calendar {
        val c = now.clone() as Calendar
        if (c.get(Calendar.MINUTE) % 2 == 0) c.add(Calendar.MINUTE, if (previous) -1 else 1)
        return c
    }

    private fun dev(s: String): String {
        val sb = StringBuilder()
        for (ch in s) sb.append(if (ch in '0'..'9') ('०' + (ch - '0')) else ch)
        return sb.toString()
    }

    private fun weatherIcon(code: Int, night: Boolean): Int = when {
        code in 0..1 -> if (night) R.drawable.ic_moon else R.drawable.ic_sun
        code in 45..48 -> R.drawable.ic_haze
        code in 51..67 || code in 80..82 || code >= 95 -> R.drawable.ic_rain
        else -> R.drawable.ic_cloud
    }

    fun build(context: Context): RemoteViews {
        val dark = Prefs.dark(context)
        val mr = Prefs.marathi(context)
        val is24 = DateFormat.is24HourFormat(context)
        val nowCal = Calendar.getInstance()
        val nowMs = nowCal.timeInMillis
        val shown = oddCalendar(nowCal, Prefs.previousOdd(context))

        val ink = if (dark) Color.parseColor("#FFFFFF") else Color.parseColor("#14172A")
        val soft = if (dark) Color.parseColor("#A9AEC4") else Color.parseColor("#8A8F9E")

        // ---- time ----
        val core = SimpleDateFormat(if (is24) "HH:mm" else "hh:mm", Locale.US).format(shown.time)
        val sb = SpannableStringBuilder(if (mr) dev(core) else core)
        if (!is24) {
            val start = sb.length
            sb.append(" ").append(if (shown.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM")
            sb.setSpan(RelativeSizeSpan(0.3f), start, sb.length, 0)
        }

        // ---- date ----
        val loc = if (mr) Locale("mr", "IN") else Locale.ENGLISH
        var date = SimpleDateFormat("EEE, MMM d", loc).format(nowCal.time)
        if (mr) date = dev(date)

        // ---- weather + sun ----
        val snap = WeatherRepo.cached(context)
        var night = false
        var progress = 0f
        var sunText = "--:--"
        var tempText = "--°"
        var code = 3
        if (snap != null) {
            val shift = Math.floorDiv(nowMs - snap.sunrise, DAY)
            val sr = snap.sunrise + shift * DAY
            val ss = snap.sunset + shift * DAY
            night = nowMs >= ss
            progress = if (night) 1f else ((nowMs - sr).toFloat() / (ss - sr).toFloat()).coerceIn(0f, 1f)
            val showMs = if (night) sr + DAY else ss
            val tf = SimpleDateFormat(if (is24) "HH:mm" else "h:mm a", Locale.US)
            sunText = tf.format(showMs)
            val f = Prefs.fahrenheit(context)
            val t = if (f) snap.tempC * 9f / 5f + 32f else snap.tempC
            tempText = "${t.roundToInt()}°" + if (f) "F" else "C"
            code = snap.code
            if (mr) {
                sunText = dev(sunText)
                tempText = dev(tempText)
            }
        }

        val rv = RemoteViews(context.packageName, R.layout.widget_odd_clock)
        rv.setInt(R.id.widget_root, "setBackgroundResource",
            if (dark) R.drawable.widget_bg_dark else R.drawable.widget_bg_light)
        rv.setTextViewText(R.id.tv_time, sb)
        rv.setTextColor(R.id.tv_time, ink)
        rv.setTextViewText(R.id.tv_date, date)
        rv.setTextColor(R.id.tv_date, ink)
        rv.setTextViewText(R.id.tv_temp, tempText)
        rv.setTextColor(R.id.tv_temp, ink)
        rv.setImageViewResource(R.id.iv_weather, weatherIcon(code, night))
        rv.setInt(R.id.iv_weather, "setColorFilter", ink)
        rv.setImageViewBitmap(R.id.iv_arc, arcBitmap(progress, dark, night))
        rv.setImageViewResource(R.id.iv_sun, if (night) R.drawable.ic_sunrise else R.drawable.ic_sunset)
        rv.setInt(R.id.iv_sun, "setColorFilter", soft)
        rv.setTextViewText(R.id.tv_sun, sunText)
        rv.setTextColor(R.id.tv_sun, soft)

        val launch = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        rv.setOnClickPendingIntent(
            R.id.widget_root,
            PendingIntent.getActivity(context, 0, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        )
        return rv
    }

    fun updateAll(context: Context) {
        val mgr = AppWidgetManager.getInstance(context)
        val ids = mgr.getAppWidgetIds(ComponentName(context, OddClockWidget::class.java))
        if (ids.isNotEmpty()) mgr.updateAppWidget(ids, build(context))
    }

    /** Half-circle sun path with a glowing dot, like the reference widget. */
    private fun arcBitmap(progress: Float, dark: Boolean, night: Boolean): Bitmap {
        val w = 480
        val h = 250
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val m = 30f
        val stroke = 24f
        val cx = w / 2f
        val r = w / 2f - m
        val cy = m + r
        val rect = RectF(cx - r, cy - r, cx + r, cy + r)

        val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke
            strokeCap = Paint.Cap.ROUND
            color = if (dark) Color.parseColor("#33384F") else Color.parseColor("#FFE9DA")
        }
        cv.drawArc(rect, 180f, 180f, false, track)

        val prog = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke
            strokeCap = Paint.Cap.ROUND
            shader = LinearGradient(
                m, 0f, w - m, 0f,
                Color.parseColor("#FFC49A"), Color.parseColor("#FF6A00"), Shader.TileMode.CLAMP
            )
            alpha = if (night) 110 else 255
        }
        if (progress > 0.001f) cv.drawArc(rect, 180f, 180f * progress, false, prog)

        val ang = Math.PI + Math.PI * progress
        val dx = (cx + r * cos(ang)).toFloat()
        val dy = (cy + r * sin(ang)).toFloat()
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        cv.drawCircle(dx, dy, 21f, ring)
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (night) Color.parseColor("#B7A89C") else Color.parseColor("#FF6A00")
        }
        cv.drawCircle(dx, dy, 13f, dot)
        return bmp
    }
}
