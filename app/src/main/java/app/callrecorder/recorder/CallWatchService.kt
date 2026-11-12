package app.callrecorder.recorder

import android.accessibilityservice.AccessibilityService
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.view.accessibility.AccessibilityEvent

/**
 * Accessibility service used only to keep Call Recorder alive in the background and to be
 * allowed to start microphone capture when a call begins. It ignores screen content.
 */
class CallWatchService : AccessibilityService() {

    private var telephony: TelephonyManager? = null
    private var lastState = TelephonyManager.CALL_STATE_IDLE
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val startRec = Runnable { RecorderService.start(this, "call") }

    private val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
        override fun onCallStateChanged(state: Int) {
            if (state == lastState) return
            EventLog.add(this@CallWatchService, "call state ${name(lastState)} -> ${name(state)}")
            when (state) {
                TelephonyManager.CALL_STATE_OFFHOOK -> if (RecordSettings.autoRecord(this@CallWatchService)) {
                    val delay = app.callrecorder.data.AppSettings.startDelay(this@CallWatchService) * 1000L
                    handler.postDelayed(startRec, delay)
                } else EventLog.add(this@CallWatchService, "auto-record is off; not recording")
                TelephonyManager.CALL_STATE_IDLE -> { handler.removeCallbacks(startRec); RecorderService.stop(this@CallWatchService) }
            }
            lastState = state
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val tm = getSystemService(TelephonyManager::class.java)
        try {
            tm.registerTelephonyCallback(mainExecutor, callback)
            telephony = tm
            EventLog.add(this, "watcher connected")
        } catch (e: SecurityException) {
            EventLog.add(this, "watcher: phone permission missing (${e.message})")
        }
    }

    override fun onDestroy() {
        telephony?.unregisterTelephonyCallback(callback)
        EventLog.add(this, "watcher stopped")
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    private fun name(s: Int) = when (s) {
        TelephonyManager.CALL_STATE_IDLE -> "IDLE"
        TelephonyManager.CALL_STATE_RINGING -> "RINGING"
        TelephonyManager.CALL_STATE_OFFHOOK -> "OFFHOOK"
        else -> s.toString()
    }
}
