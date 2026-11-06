package com.triposr.mobile

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.widget.RemoteViews
import java.io.File

/** Home-screen widget: shows the latest model's picture and a "create 3D model" button. */
class CreateWidget : AppWidgetProvider() {
    companion object {
        const val ACTION_CREATE = "com.triposr.mobile.CREATE"
        const val ACTION_PROJECTS = "com.triposr.mobile.PROJECTS"

        fun refresh(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, CreateWidget::class.java))
            if (ids.isNotEmpty()) update(ctx, mgr, ids)
        }

        private fun update(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
            val views = RemoteViews(ctx.packageName, R.layout.widget_create)
            fun open(action: String, code: Int) = PendingIntent.getActivity(ctx, code,
                Intent(ctx, MainActivity::class.java).setAction(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            views.setOnClickPendingIntent(R.id.widget_create, open(ACTION_CREATE, 1))
            views.setOnClickPendingIntent(R.id.widget_thumb, open(ACTION_PROJECTS, 2))

            val dirs = File(ctx.filesDir, "projects").listFiles()?.sortedByDescending { it.name }
            val thumb = dirs?.firstNotNullOfOrNull { d ->
                File(d, "thumb.png").takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.absolutePath) }
            }
            if (thumb != null) views.setImageViewBitmap(R.id.widget_thumb, thumb)
            views.setTextViewText(R.id.widget_subtitle,
                if (dirs.isNullOrEmpty()) tr("תמונה ← מודל תלת־ממד") else tr("{0} מודלים שמורים", dirs.size))
            mgr.updateAppWidget(ids, views)
        }
    }

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = update(ctx, mgr, ids)
}
