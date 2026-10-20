package com.gathin.tvgo.flow

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.gathin.tvgo.ui.MainActivity

/**
 * Keeps tvgo running while a job talks to the TV and the user is in another app: without it the phone cuts the adb
 * connection (and may stop the app) mid-run. The work itself stays in the ViewModel; this only holds the process in
 * the foreground and shows the job's progress as an ongoing notification.
 */
class RunService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val n = build(this, last)
        if (Build.VERSION.SDK_INT >= 29) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        else startForeground(ID, n)
        return START_NOT_STICKY
    }

    /** What the notification shows: [done] of [total] steps (total 0 = no bar); [attention] asks the user to open tvgo. */
    data class Progress(val title: String, val text: String, val done: Int = 0, val total: Int = 0, val attention: Boolean = false)

    companion object {
        private const val ID = 1
        private const val CHANNEL = "run"
        private const val ASK_ID = 2
        private const val ASK_CHANNEL = "ask"
        private var last = Progress("tvgo", "Working")
        private var running = false

        /** Start (or update) the service; called from the foreground when a job begins. */
        fun start(ctx: Context, p: Progress) {
            last = p
            if (running) return update(ctx, p)
            running = runCatching { ctx.startForegroundService(Intent(ctx, RunService::class.java)) }.isSuccess
        }

        fun update(ctx: Context, p: Progress) {
            last = p
            if (running) manager(ctx).notify(ID, build(ctx, p))
            if (p.attention) manager(ctx).notify(ASK_ID, build(ctx, p, ASK_CHANNEL))
            else manager(ctx).cancel(ASK_ID)
        }

        fun stop(ctx: Context) {
            running = false
            manager(ctx).cancel(ASK_ID)
            ctx.stopService(Intent(ctx, RunService::class.java))
        }

        private fun manager(ctx: Context): NotificationManager {
            val m = ctx.getSystemService(NotificationManager::class.java)
            if (m.getNotificationChannel(CHANNEL) == null) {
                m.createNotificationChannel(NotificationChannel(CHANNEL, "Work on the TV", NotificationManager.IMPORTANCE_LOW))
                m.createNotificationChannel(NotificationChannel(ASK_CHANNEL, "Open tvgo to go on", NotificationManager.IMPORTANCE_HIGH))
            }
            return m
        }

        private fun build(ctx: Context, p: Progress, channel: String = CHANNEL): Notification {
            manager(ctx)
            val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT), PendingIntent.FLAG_IMMUTABLE)
            val b = Notification.Builder(ctx, channel)
                .setSmallIcon(android.R.drawable.ic_menu_manage)
                .setContentTitle(p.title)
                .setContentText(p.text)
                .setStyle(Notification.BigTextStyle().bigText(p.text))
                .setContentIntent(open)
                .setOnlyAlertOnce(channel == CHANNEL)
                .setOngoing(channel == CHANNEL)
                .setAutoCancel(channel != CHANNEL)
            if (channel == CHANNEL && p.total > 0) {
                if (Build.VERSION.SDK_INT >= 36) {
                    // One segment per step, filled as each one checks out (Android 16's live progress style).
                    b.setStyle(Notification.ProgressStyle()
                        .setProgressSegments(List(p.total) { Notification.ProgressStyle.Segment(1) })
                        .setProgress(p.done))
                    b.setRequestPromotedOngoing(true) // a Live Update: shown on the lock screen and status bar chip
                } else b.setProgress(p.total, p.done, false)
            } else if (channel == CHANNEL) b.setProgress(0, 0, true)
            return b.build()
        }
    }
}
