package com.netmaster.pro.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import com.netmaster.pro.R

/**
 * Network Status widget — shows connection status and device count on the home screen.
 * Updated via AppWidgetManager on a 30-minute cycle (see network_status_widget_info.xml).
 */
class NetworkStatusWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (widgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, widgetId)
        }
    }

    private fun updateWidget(
        context: Context,
        manager: AppWidgetManager,
        widgetId: Int
    ) {
        val views = RemoteViews(context.packageName, R.layout.network_status_widget)
        views.setTextViewText(R.id.widget_title, "NetMaster Pro")
        views.setTextViewText(R.id.widget_status, context.getString(R.string.app_subtitle))
        manager.updateAppWidget(widgetId, views)
    }
}
