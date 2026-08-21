package com.example.widgetcalendar

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.net.Uri
import android.widget.RemoteViews
import java.util.Calendar

class CalendarWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { updateWidget(context, appWidgetManager, it) }
        scheduleMidnightAlarm(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { CalendarRepository.removeMonthOffset(context, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val appWidgetManager = AppWidgetManager.getInstance(context)

        when (intent.action) {
            ACTION_PREV_MONTH -> {
                val widgetId = intent.getIntExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID
                )
                android.util.Log.d("WidgetCalendar", "ACTION_PREV_MONTH: widgetId=$widgetId")
                if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    CalendarRepository.shiftMonthOffset(context, widgetId, -1)
                    updateWidget(context, appWidgetManager, widgetId)
                }
            }

            ACTION_NEXT_MONTH -> {
                val widgetId = intent.getIntExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID
                )
                android.util.Log.d("WidgetCalendar", "ACTION_NEXT_MONTH: widgetId=$widgetId")
                if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    CalendarRepository.shiftMonthOffset(context, widgetId, 1)
                    updateWidget(context, appWidgetManager, widgetId)
                }
            }

            ACTION_TODAY -> {
                val widgetId = intent.getIntExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID
                )
                if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    CalendarRepository.setMonthOffset(context, widgetId, 0)
                    updateWidget(context, appWidgetManager, widgetId)
                }
            }

            ACTION_MORE -> {
                context.startActivity(
                    Intent(context, WidgetActionsActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, intent.getIntExtra(
                            AppWidgetManager.EXTRA_APPWIDGET_ID,
                            AppWidgetManager.INVALID_APPWIDGET_ID
                        ))
                    }
                )
            }

            ACTION_IMPORT,
            ACTION_EXPORT -> {
                context.startActivity(
                    Intent(context, ImportExportActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, intent.getIntExtra(
                            AppWidgetManager.EXTRA_APPWIDGET_ID,
                            AppWidgetManager.INVALID_APPWIDGET_ID
                        ))
                        putExtra(
                            ImportExportActivity.EXTRA_MODE,
                            if (intent.action == ACTION_IMPORT) {
                                ImportExportActivity.MODE_IMPORT
                            } else {
                                ImportExportActivity.MODE_EXPORT
                            }
                        )
                    }
                )
            }

            ACTION_DATE_CLICK -> {
                val widgetId = intent.getIntExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID
                )
                val dateMillis = intent.getLongExtra(EXTRA_DATE_MILLIS, -1L)
                if (dateMillis > 0L) {
                    context.startActivity(
                        Intent(context, DateTodosActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                            putExtra(EXTRA_DATE_MILLIS, dateMillis)
                        }
                    )
                }
            }

            ACTION_MIDNIGHT_ALARM -> {
                refreshAllWidgets(context)
                scheduleMidnightAlarm(context)
            }

            ACTION_REMINDER_CHANGED,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                refreshAllWidgets(context)
                scheduleMidnightAlarm(context)
            }
        }
    }

    companion object {
        const val ACTION_PREV_MONTH = "com.example.widgetcalendar.PREV_MONTH"
        const val ACTION_NEXT_MONTH = "com.example.widgetcalendar.NEXT_MONTH"
        const val ACTION_TODAY = "com.example.widgetcalendar.TODAY"
        const val ACTION_MORE = "com.example.widgetcalendar.MORE"
        const val ACTION_IMPORT = "com.example.widgetcalendar.IMPORT"
        const val ACTION_EXPORT = "com.example.widgetcalendar.EXPORT"
        const val ACTION_DATE_CLICK = "com.example.widgetcalendar.DATE_CLICK"
        const val ACTION_REMINDER_CHANGED = "com.example.widgetcalendar.REMINDER_CHANGED"
        const val ACTION_MIDNIGHT_ALARM = "com.example.widgetcalendar.MIDNIGHT_ALARM"
        const val EXTRA_DATE_MILLIS = "extra_date_millis"
        private const val REQUEST_CODE_MIDNIGHT_ALARM = 9999

        fun refreshAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, CalendarWidgetProvider::class.java)
            )
            ids.forEach { updateWidget(context, manager, it) }
        }

        fun refreshWidget(context: Context, appWidgetId: Int) {
            updateWidget(context, AppWidgetManager.getInstance(context), appWidgetId)
        }

        private fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            // Apply the user-selected language so month names format correctly
            val ctx = LanguageHelper.applyLanguage(context)
            val views = RemoteViews(context.packageName, R.layout.widget_calendar)
            val monthTitle = CalendarRepository.getDisplayedMonthTitle(ctx, appWidgetId)
            android.util.Log.d("WidgetCalendar", "updateWidget: widgetId=$appWidgetId, monthTitle='$monthTitle'")
            views.setTextViewText(R.id.tvMonthTitle, monthTitle)

            val serviceIntent = Intent(context, CalendarGridRemoteViewsService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            views.setRemoteAdapter(R.id.gridCalendar, serviceIntent)
            views.setEmptyView(R.id.gridCalendar, R.id.tvEmpty)

            views.setOnClickPendingIntent(
                R.id.btnPrev,
                monthNavPendingIntent(context, appWidgetId, ACTION_PREV_MONTH, 1)
            )
            views.setOnClickPendingIntent(
                R.id.btnNext,
                monthNavPendingIntent(context, appWidgetId, ACTION_NEXT_MONTH, 2)
            )
            views.setOnClickPendingIntent(
                R.id.btnToday,
                monthNavPendingIntent(context, appWidgetId, ACTION_TODAY, 4)
            )
            views.setOnClickPendingIntent(
                R.id.btnMore,
                monthNavPendingIntent(context, appWidgetId, ACTION_MORE, 5)
            )
            views.setPendingIntentTemplate(
                R.id.gridCalendar,
                dateClickTemplatePendingIntent(context, appWidgetId)
            )

            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.gridCalendar)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun monthNavPendingIntent(
            context: Context,
            appWidgetId: Int,
            action: String,
            requestOffset: Int
        ): PendingIntent {
            val intent = Intent(context, CalendarWidgetProvider::class.java).apply {
                this.action = action
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val requestCode = appWidgetId * 100 + requestOffset
            return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun dateClickTemplatePendingIntent(
            context: Context,
            appWidgetId: Int
        ): PendingIntent {
            val intent = Intent(context, CalendarWidgetProvider::class.java).apply {
                action = ACTION_DATE_CLICK
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val requestCode = appWidgetId * 100 + 3
            val mutableFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE
            } else {
                0
            }
            return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or mutableFlag
            )
        }

        fun scheduleMidnightAlarm(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, CalendarWidgetProvider::class.java).apply {
                action = ACTION_MIDNIGHT_ALARM
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE_MIDNIGHT_ALARM,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val nextMidnight = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            alarmManager.set(AlarmManager.RTC, nextMidnight, pendingIntent)
        }
    }
}
