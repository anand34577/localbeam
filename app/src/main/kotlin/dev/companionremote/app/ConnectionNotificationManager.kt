package dev.companionremote.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/** Owns the ongoing connection status notification without owning the socket. */
class ConnectionNotificationManager(context: Context) {
    private val appContext = context.applicationContext
    private val notificationManager = appContext.getSystemService(NotificationManager::class.java)

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Remote connection",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply { description = "Keeps the remote easy to reopen" },
            )
        }
    }

    fun update(state: ConnectionState, deviceName: String?) {
        if (deviceName.isNullOrBlank() || state == ConnectionState.Disconnected) {
            cancel()
            return
        }
        val openIntent = MainActivityIntent.open(appContext)
        val reconnectIntent = MainActivityIntent.reconnect(appContext)
        if (state == ConnectionState.Disconnected) return
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(appContext, CHANNEL_ID)
        } else {
            Notification.Builder(appContext)
        }
        builder
            .setSmallIcon(dev.companionremote.app.R.drawable.ic_launcher_foreground)
            .setContentTitle("LocalBeam remote")
            .setContentText("Tap to open the remote")
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_STATUS)
            .addAction(Notification.Action.Builder(null, "Reconnect", reconnectIntent).build())
        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    fun cancel() = notificationManager.cancel(NOTIFICATION_ID)

    private companion object {
        const val CHANNEL_ID = "remote_connection"
        const val NOTIFICATION_ID = 4127
    }
}

/** Intent helpers kept here so the notification never needs a reference to the ViewModel. */
object MainActivityIntent {
    private const val ACTION_OPEN = "dev.companionremote.app.OPEN_REMOTE"
    private const val ACTION_RECONNECT = "dev.companionremote.app.RECONNECT_REMOTE"

    fun open(context: Context): PendingIntent = pending(context, ACTION_OPEN, 1)

    fun reconnect(context: Context): PendingIntent = pending(context, ACTION_RECONNECT, 2)

    private fun pending(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            this.action = action
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

enum class MediaAction { Rewind, PlayPause, FastForward, Stop }
