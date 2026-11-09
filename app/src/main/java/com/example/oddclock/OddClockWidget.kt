package com.example.oddclock

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Build

class OddClockWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        WidgetRenderer.updateAll(context)
        scheduleNext(context)
    }

    override fun onEnabled(context: Context) = scheduleNext(context)

    override fun onDisabled(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(tickIntent(context))
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val a = intent.action ?: return
        val handled = a == ACTION_TICK || a == Intent.ACTION_BOOT_COMPLETED ||
            a == Intent.ACTION_TIME_CHANGED || a == Intent.ACTION_TIMEZONE_CHANGED ||
            a == Intent.ACTION_MY_PACKAGE_REPLACED || a == AppWidgetManager.ACTION_APPWIDGET_UPDATE
        if (!handled) return

        if (a != AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            WidgetRenderer.updateAll(context)
            scheduleNext(context)
        }
        // refresh weather in the background (only if the cache is older than 30 min)
        val pending = goAsync()
        Thread {
            try {
                if (WeatherRepo.refreshIfStale(context)) WidgetRenderer.updateAll(context)
            } catch (_: Exception) {
            } finally {
                pending.finish()
            }
        }.start()
    }

    companion object {
        const val ACTION_TICK = "com.example.oddclock.TICK"

        private fun tickIntent(context: Context): PendingIntent {
            val i = Intent(context, OddClockWidget::class.java).setAction(ACTION_TICK)
            return PendingIntent.getBroadcast(
                context, 0, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        fun scheduleNext(context: Context) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val now = System.currentTimeMillis()
            val next = (now / 60000L + 1) * 60000L
            val pi = tickIntent(context)
            try {
                val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
                if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC, next, pi)
                else am.setAndAllowWhileIdle(AlarmManager.RTC, next, pi)
            } catch (e: SecurityException) {
                am.setAndAllowWhileIdle(AlarmManager.RTC, next, pi)
            }
        }
    }
}
