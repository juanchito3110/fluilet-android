package com.fluilet.app.core.focusmode

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
import androidx.core.app.NotificationCompat
import com.fluilet.app.MainActivity
import com.fluilet.app.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Servicio en primer plano de la sesión de lectura: mantiene viva la sesión (y su cronómetro de
 * telemetría) aunque el sistema reclame recursos, y muestra una notificación persistente. No es
 * pegajoso: si el proceso muere el texto adaptado (solo en RAM) ya no existe, así que no se relanza.
 */
@AndroidEntryPoint
class FocusSessionService : Service() {

    @Inject lateinit var controller: FocusSessionController

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var tickJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        val notification = buildNotification(controller.state.value)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        if (!controller.state.value.isActive) {
            stopSelf()
            return START_NOT_STICKY
        }

        tickJob?.cancel()
        tickJob = scope.launch {
            while (isActive) {
                delay(TICK_MS)
                controller.tick()
                val state = controller.state.value
                if (!state.isActive) {
                    stopSelf()
                    break
                }
                if (state.elapsedSeconds % NOTIFICATION_REFRESH_SECONDS == 0L) {
                    notificationManager().notify(NOTIFICATION_ID, buildNotification(state))
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(state: FocusSessionState): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val minutes = state.elapsedSeconds / 60
        val seconds = state.elapsedSeconds % 60
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_reading)
            .setContentTitle(getString(R.string.focus_notification_title))
            .setContentText(getString(R.string.focus_notification_text, minutes, seconds))
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.focus_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = getString(R.string.focus_channel_description) }
        notificationManager().createNotificationChannel(channel)
    }

    private fun notificationManager() = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private companion object {
        const val CHANNEL_ID = "focus_session"
        const val NOTIFICATION_ID = 1001
        const val TICK_MS = 1000L
        const val NOTIFICATION_REFRESH_SECONDS = 5L
    }
}
