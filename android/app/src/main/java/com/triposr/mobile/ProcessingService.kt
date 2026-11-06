package com.triposr.mobile

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps the process (and CPU) alive while a model is being generated,
 * so the work continues when the user leaves the app or turns the screen off. The work itself
 * runs in AppState; this service only shows the progress notification.
 */
class ProcessingService : Service() {
    companion object {
        private const val CHANNEL_PROGRESS = "progress"
        // A channel's importance can't change once created, so the pop-up version has a new id.
        private const val CHANNEL_DONE = "done_alert"
        private const val ID_PROGRESS = 1
        private const val ID_DONE = 2

        fun start(ctx: Context) {
            runCatching { ctx.startForegroundService(Intent(ctx, ProcessingService::class.java)) }
                .onFailure { CrashLog.error("start foreground service", it) }
        }

        /** Called when the job ends (success, error or cancel). */
        fun finished(ctx: Context) {
            val job = AppState.job
            val nm = ctx.getSystemService(NotificationManager::class.java)
            ensureChannels(ctx)
            if (job != null && (job.done || job.error != null)) {
                val text = if (job.done) tr("המודל מוכן! לחץ לצפייה") else tr("שגיאה: {0}", job.error)
                nm.notify(ID_DONE, Notification.Builder(ctx, CHANNEL_DONE)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle("Makeo 3D")
                    .setContentText(text)
                    .setCategory(Notification.CATEGORY_STATUS)
                    .setContentIntent(openApp(ctx, if (job.done) ACTION_RESULT else null))
                    .setAutoCancel(true)
                    .build())
            }
            ctx.stopService(Intent(ctx, ProcessingService::class.java))
        }

        const val ACTION_RESULT = "com.triposr.mobile.RESULT"

        private fun openApp(ctx: Context, action: String? = null) = PendingIntent.getActivity(ctx, if (action == null) 0 else 1,
            Intent(ctx, MainActivity::class.java).setAction(action).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        private fun ensureChannels(ctx: Context) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL_PROGRESS, tr("יצירת מודל"), NotificationManager.IMPORTANCE_LOW))
            nm.deleteNotificationChannel("done")
            // High importance: pops up on top of the screen with sound and vibration.
            nm.createNotificationChannel(NotificationChannel(CHANNEL_DONE, tr("מודל מוכן"), NotificationManager.IMPORTANCE_HIGH)
                .apply { enableVibration(true) })
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var ticker: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannels(this)
        startForeground(ID_PROGRESS, progressNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        if (wakeLock == null) {
            wakeLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TripoSR:processing")
                .apply { acquire(60 * 60 * 1000L) }
        }
        if (ticker == null) {
            ticker = AppState.scope.launch {
                while (true) {
                    delay(700)
                    if (!AppState.isRunning) break
                    getSystemService(NotificationManager::class.java).notify(ID_PROGRESS, progressNotification())
                }
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun progressNotification(): Notification {
        val job = AppState.job
        val pct = ((job?.overall ?: 0f) * 100).toInt()
        return Notification.Builder(this, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(tr("יוצר מודל תלת־ממד… {0}%", pct))
            .setContentText(job?.status ?: "")
            .setProgress(100, pct, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp(this))
            .build()
    }

    override fun onDestroy() {
        ticker?.cancel()
        wakeLock?.takeIf { it.isHeld }?.release()
        super.onDestroy()
    }
}
