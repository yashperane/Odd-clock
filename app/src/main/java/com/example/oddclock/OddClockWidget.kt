package com.example.oddclock

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.text.format.DateFormat
import android.widget.RemoteViews
import java.util.Calendar

class OddClockWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        render(context, manager, ids)
        scheduleNext(context)
    }

    override fun onEnabled(context: Context) = scheduleNext(context)

    override fun onDisabled(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(tickIntent(context))
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, OddClockWidget::class.java))
            if (ids.isNotEmpty()) {
                render(context, mgr, ids)
                scheduleNext(context)
            }
        }
    }

    private fun render(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        val cal = Calendar.getInstance()
        val is24 = DateFormat.is24HourFormat(context)
        val hour = if (is24) cal.get(Calendar.HOUR_OF_DAY)
                   else (cal.get(Calendar.HOUR).let { if (it == 0) 12 else it })
        var minute = cal.get(Calendar.MINUTE)
        if (minute % 2 == 0) minute += 1          // even -> show next odd number
        val text = String.format("%d:%02d", hour, minute)
        val views = RemoteViews(context.packageName, R.layout.widget_odd_clock)
        views.setTextViewText(R.id.time, text)
        mgr.updateAppWidget(ids, views)
    }

    private fun tickIntent(context: Context): PendingIntent {
        val i = Intent(context, OddClockWidget::class.java).setAction("com.example.oddclock.TICK")
        return PendingIntent.getBroadcast(
            context, 0, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun scheduleNext(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val now = System.currentTimeMillis()
        val next = (now / 60000 + 1) * 60000
        val pi = tickIntent(context)
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (canExact) am.setExactAndAllowWhileIdle(AlarmManager.RTC, next, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC, next, pi)
    }
}
