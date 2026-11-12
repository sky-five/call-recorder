package app.callrecorder.recorder

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Internet calls (WhatsApp, Signal…) never appear in the phone's call log, so the only place the
 * caller's name shows up is the app's own "ongoing call" notification. This listener remembers the
 * caller name from those call notifications only; it ignores every other notification and stores
 * nothing on disk.
 */
class CallNotificationWatcher : NotificationListenerService() {

    data class Seen(val app: String, val name: String, val firstSeen: Long, var lastSeen: Long)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val app = APPS[sbn.packageName] ?: return
        val n = sbn.notification
        val isCall = n.category == Notification.CATEGORY_CALL || n.extras.containsKey("android.callType")
        if (!isCall) return
        val name = (n.extras.getCharSequence(Notification.EXTRA_TITLE) ?: n.extras.getCharSequence("android.callPerson"))
            ?.toString()?.trim()?.takeIf { it.isNotEmpty() && !GENERIC.containsMatchIn(it) } ?: return
        val now = System.currentTimeMillis()
        val prev = last
        last = if (prev != null && prev.name == name && prev.app == app && now - prev.lastSeen < 120_000) prev.also { it.lastSeen = now }
        else Seen(app, name, now, now)
    }

    companion object {
        @Volatile private var last: Seen? = null

        private val APPS = mapOf(
            "com.whatsapp" to "WhatsApp", "com.whatsapp.w4b" to "WhatsApp",
            "org.thoughtcrime.securesms" to "Signal", "org.telegram.messenger" to "Telegram",
            "com.google.android.apps.tachyon" to "Meet", "com.facebook.orca" to "Messenger",
            "com.viber.voip" to "Viber", "com.skype.raider" to "Skype", "com.microsoft.teams" to "Teams",
        )
        val APP_LABELS = APPS.values.toSet()
        private val GENERIC = Regex("(?i)^(ongoing|incoming|outgoing|calling|ringing|voice call|video call|whatsapp|signal|telegram)\\b")

        /** The internet call that was showing while [start]..now was being recorded, if any. */
        fun callDuring(start: Long): Seen? = last?.takeIf { it.lastSeen >= start - 60_000 }

        fun isEnabled(ctx: Context): Boolean {
            val flat = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners") ?: return false
            val me = ComponentName(ctx, CallNotificationWatcher::class.java).flattenToString()
            return flat.split(':').any { it.equals(me, ignoreCase = true) }
        }
    }
}
