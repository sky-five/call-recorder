package app.callrecorder.recorder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.CallLog
import android.provider.MediaStore
import app.callrecorder.MainActivity
import app.callrecorder.R
import app.callrecorder.data.FileNames
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

/**
 * Records one call. On Samsung phones, switching on the audio HAL's in-call recording mode
 * (INCALL_RECORDING_MODE / VOICE_RECORDING_MODE) and capturing from VOICE_RECOGNITION gives both
 * sides of the call. Tested on a Galaxy S24 (Android 17, One UI 9). See docs/how-it-works.md.
 */
class RecorderService : Service() {

    private var recorder: MediaRecorder? = null
    private var pfd: ParcelFileDescriptor? = null
    private var uri: Uri? = null
    private var startedAt = 0L
    private var stamp = ""
    private var mode = "call"
    private var source = ""
    private val handler = Handler(Looper.getMainLooper())

    // loudness stats (0..32767), sampled every 250 ms
    private var samples = 0
    private var loudSamples = 0
    private var peak = 0
    private val sampler = object : Runnable {
        override fun run() {
            val r = recorder ?: return
            val a = try { r.maxAmplitude } catch (_: Exception) { 0 }
            samples++
            if (a > 1500) loudSamples++
            if (a > peak) peak = a
            handler.postDelayed(this, 250)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> begin(intent.getStringExtra(EXTRA_MODE) ?: "call")
            ACTION_STOP -> finish()
        }
        return START_NOT_STICKY
    }

    private fun begin(newMode: String) {
        if (recorder != null) return
        mode = newMode
        try {
            startForeground(NOTIF_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } catch (e: Exception) {
            EventLog.add(this, "ERROR cannot start foreground mic service: ${e.javaClass.simpleName}: ${e.message}")
            stopSelf(); return
        }
        startedAt = System.currentTimeMillis()
        stamp = SimpleDateFormat("yyMMdd_HHmmss", Locale.US).format(Date(startedAt))

        if (mode == "call") {
            val audio = getSystemService(AudioManager::class.java)
            audio.setParameters("INCALL_RECORDING_MODE=ON")
            audio.setParameters("VOICE_RECORDING_MODE=ON")
        }

        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, FileNames.readable(if (mode == "test") FileNames.MIC_TEST else FileNames.UNKNOWN, startedAt))
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
            put(MediaStore.Audio.Media.RELATIVE_PATH, "Recordings/Call/")
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val newUri = contentResolver.insert(collection, values)
        if (newUri == null) {
            EventLog.add(this, "ERROR could not create file in Recordings/Call"); cleanup(); return
        }
        uri = newUri
        pfd = contentResolver.openFileDescriptor(newUri, "w")

        val sources = if (mode == "call")
            listOf(MediaRecorder.AudioSource.VOICE_RECOGNITION to "VOICE_RECOGNITION", MediaRecorder.AudioSource.MIC to "MIC")
        else listOf(MediaRecorder.AudioSource.MIC to "MIC")

        for ((src, label) in sources) {
            val r = MediaRecorder(this)
            try {
                r.setAudioSource(src)
                r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                // Mono 48 kHz: the phone gives one mixed call track (stereo was tested: both
                // channels identical), so mono halves the size with no loss.
                r.setAudioSamplingRate(48000)
                r.setAudioChannels(1)
                r.setAudioEncodingBitRate(RecordSettings.quality(this).bitrate)
                r.setOutputFile(pfd!!.fileDescriptor)
                r.setOnErrorListener { _, what, extra -> EventLog.add(this, "ERROR recorder what=$what extra=$extra") }
                r.prepare()
                r.start()
                recorder = r
                source = label
                break
            } catch (e: Exception) {
                EventLog.add(this, "source $label failed: ${e.javaClass.simpleName}: ${e.message}")
                r.release()
            }
        }
        if (recorder == null) { EventLog.add(this, "ERROR no audio source worked"); cleanup(); return }
        samples = 0; loudSamples = 0; peak = 0
        handler.postDelayed(sampler, 250)
        EventLog.add(this, "recording started ($mode, source=$source)")
        if (mode == "test") handler.postDelayed({ finish() }, 10_000)
    }

    private fun finish() {
        val r = recorder ?: run { stopSelf(); return }
        handler.removeCallbacks(sampler)
        var ok = true
        try { r.stop() } catch (e: RuntimeException) { ok = false; EventLog.add(this, "stop failed (too short?): ${e.message}") }
        r.release(); recorder = null
        if (mode == "call") {
            val audio = getSystemService(AudioManager::class.java)
            audio.setParameters("INCALL_RECORDING_MODE=OFF")
            audio.setParameters("VOICE_RECORDING_MODE=OFF")
        }
        try { pfd?.close() } catch (_: Exception) {}
        pfd = null
        val secs = (System.currentTimeMillis() - startedAt) / 1000
        val activity = if (samples > 0) loudSamples * 100 / samples else 0
        val target = uri
        uri = null
        if (!ok || target == null) {
            target?.let { contentResolver.delete(it, null, null) }
            EventLog.add(this, "recording discarded")
            done(); return
        }
        contentResolver.update(target, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
        EventLog.add(this, "recording saved: ${secs}s, source=$source, peak=$peak, loud=$activity% of time")

        if (mode == "call") {
            val start = startedAt
            thread {
                Thread.sleep(2000) // the call log is written just after the call ends
                // Phone calls are in the call log. WhatsApp and other internet calls are not; for those
                // the caller name comes from the app's own call notification (if notification access is on).
                val phone = lookupCaller(start)
                val voip = if (phone == null) CallNotificationWatcher.callDuring(start) else null
                val who = phone?.let { it.second ?: app.callrecorder.ui.Fmt.phone(it.first).ifBlank { FileNames.UNKNOWN } } ?: voip?.name ?: FileNames.INTERNET

                // Settings › Recording rules: drop recordings the user doesn't want kept.
                val drop = app.callrecorder.data.RecordRules.dropReason(
                    app.callrecorder.data.RecordRules.Call(
                        seconds = secs,
                        internet = phone == null,
                        excluded = app.callrecorder.data.AppSettings.isExcluded(this, phone?.first),
                        isContact = phone?.second != null || voip != null,
                    ),
                    app.callrecorder.data.RecordRules.Rules(
                        app.callrecorder.data.AppSettings.recordOnly(this),
                        app.callrecorder.data.AppSettings.internetCalls(this),
                        app.callrecorder.data.AppSettings.minSeconds(this),
                    ),
                )
                if (drop != null) {
                    runCatching { contentResolver.delete(target, null, null) }
                    EventLog.add(this, "recording not kept ($drop)")
                    handler.post { done() }
                    return@thread
                }

                val name = FileNames.readable(who, start, app = voip?.app)
                try {
                    contentResolver.update(target, ContentValues().apply { put(MediaStore.Audio.Media.DISPLAY_NAME, name) }, null, null)
                    EventLog.add(this, "named: $name")
                } catch (e: Exception) { EventLog.add(this, "rename failed: ${e.message}") }
                if (RecordSettings.notifyAfter(this)) savedNotice(who + (voip?.let { " (${it.app})" } ?: ""), secs)
                kotlinx.coroutines.runBlocking { app.callrecorder.data.Retention.runCleanup(this@RecorderService) }
                handler.post { done() }
            }
        } else done()
    }

    /** One quiet notification after a call is saved; tapping it opens the app. */
    private fun savedNotice(who: String, secs: Long) {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(SAVED_CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(SAVED_CHANNEL, "Saved recordings", NotificationManager.IMPORTANCE_LOW).apply {
                description = "A short note after each call is recorded"; setShowBadge(false)
            })
        }
        val open = PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        nm.notify(SAVED_ID, Notification.Builder(this, SAVED_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_rec)
            .setContentTitle("Call recorded")
            .setContentText("$who · ${secs / 60}:${"%02d".format(secs % 60)}")
            .setColor(0xFFA8473F.toInt())
            .setContentIntent(open).setAutoCancel(true)
            .build())
    }

    /** (number, contact name or null) of the phone call that matches this recording, from the call log. */
    private fun lookupCaller(since: Long): Pair<String, String?>? = try {
        contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.DATE),
            // Only a call-log entry that started up to 90 s before (ringing) or 20 s after the recording is this call
            // (an internet call would otherwise borrow the previous phone call's name).
            "${CallLog.Calls.DATE} BETWEEN ? AND ?", arrayOf((since - 90_000).toString(), (since + 20_000).toString()),
            "${CallLog.Calls.DATE} DESC"
        )?.use { c ->
            if (c.moveToFirst()) (c.getString(0) ?: "") to c.getString(1)?.takeIf { it.isNotBlank() } else null
        }
    } catch (e: SecurityException) { EventLog.add(this, "call log permission missing"); null }

    private fun cleanup() {
        recorder?.release(); recorder = null
        try { pfd?.close() } catch (_: Exception) {}
        uri?.let { contentResolver.delete(it, null, null) }
        uri = null
        done()
    }

    private fun done() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun notification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Recording", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shown only while a call is being recorded"
                setShowBadge(false)
            })
        }
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_rec)
            .setContentTitle(if (mode == "call") "Recording call" else "Test recording")
            .setContentText("Saved on this phone only")
            .setColor(0xFFA8473F.toInt())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(true).setUsesChronometer(true)
            .setContentIntent(open)
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        private const val CHANNEL = "recording"
        private const val NOTIF_ID = 7
        private const val SAVED_CHANNEL = "saved"
        private const val SAVED_ID = 9
        private const val ACTION_START = "start"
        private const val ACTION_STOP = "stop"
        private const val EXTRA_MODE = "mode"

        fun start(ctx: Context, mode: String) {
            ctx.startForegroundService(Intent(ctx, RecorderService::class.java).setAction(ACTION_START).putExtra(EXTRA_MODE, mode))
        }

        fun stop(ctx: Context) {
            ctx.startService(Intent(ctx, RecorderService::class.java).setAction(ACTION_STOP))
        }
    }
}
