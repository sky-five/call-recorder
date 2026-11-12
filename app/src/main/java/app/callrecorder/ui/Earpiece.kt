package app.callrecorder.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.PowerManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C

/**
 * Plays on the loudspeaker by default. Raising the phone to the ear (proximity sensor) switches to
 * the earpiece and turns the screen off, like a phone call; the Earpiece chip forces it on.
 * Headphones/Bluetooth take priority and disable the automatic switch.
 */
class EarpieceState {
    var forced by mutableStateOf(false)
    var nearEar by mutableStateOf(false)
    val active get() = forced || nearEar
}

@Composable
fun rememberEarpiece(p: PlayerState): EarpieceState {
    val ctx = LocalContext.current
    val state = remember { EarpieceState() }
    val audio = remember { ctx.getSystemService(AudioManager::class.java) }

    // Proximity sensor + screen-off while held to the ear (only while playing).
    DisposableEffect(p.playing) {
        if (!p.playing || hasHeadset(audio)) { state.nearEar = false; return@DisposableEffect onDispose { } }
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val prox = sm.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        val pm = ctx.getSystemService(PowerManager::class.java)
        val lock = if (pm.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK))
            pm.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "callrecorder:ear") else null
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                state.nearEar = e.values[0] < minOf(5f, prox?.maximumRange ?: 5f)
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) = Unit
        }
        if (prox != null) sm.registerListener(listener, prox, SensorManager.SENSOR_DELAY_NORMAL)
        lock?.acquire(4 * 60 * 60 * 1000L)
        onDispose {
            sm.unregisterListener(listener)
            if (lock?.isHeld == true) lock.release()
            state.nearEar = false
        }
    }

    // Route audio: earpiece = voice-call style output; otherwise normal media on the speaker.
    DisposableEffect(state.active) {
        if (state.active && !hasHeadset(audio)) {
            audio.mode = AudioManager.MODE_IN_COMMUNICATION
            audio.availableCommunicationDevices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }
                ?.let { audio.setCommunicationDevice(it) }
            p.player.setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_VOICE_COMMUNICATION).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(), false,
            )
        } else {
            p.player.setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(), false,
            )
        }
        onDispose {
            if (state.active) {
                audio.clearCommunicationDevice()
                audio.mode = AudioManager.MODE_NORMAL
            }
        }
    }
    return state
}

private fun hasHeadset(audio: AudioManager) = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
    it.type in setOf(
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
    )
}
