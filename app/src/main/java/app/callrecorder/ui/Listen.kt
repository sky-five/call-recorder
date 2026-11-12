package app.callrecorder.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.common.PlaybackParameters
import app.callrecorder.data.Recording

private val SPEEDS = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)

/** Playback preferences that carry over between recordings. */
object PlayPrefs {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE)
    fun speed(ctx: Context) = sp(ctx).getFloat("speed", 1f)
    fun setSpeed(ctx: Context, v: Float) = sp(ctx).edit().putFloat("speed", v).apply()
    fun skipSilence(ctx: Context) = sp(ctx).getBoolean("skipSilence", false)
    fun setSkipSilence(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("skipSilence", v).apply()
    fun boost(ctx: Context) = sp(ctx).getBoolean("boost", true)
    fun setBoost(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("boost", v).apply()
}

@Composable
fun CallCard(r: Recording) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Avatar(r, 88)
            Spacer(Modifier.height(14.dp))
            Text(Fmt.title(r), style = MaterialTheme.typography.headlineSmall)
            Fmt.subtitle(r)?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Spacer(Modifier.height(10.dp))
            Text(
                listOfNotNull(Fmt.app(r), r.direction?.let { if (it.startsWith("out")) "Outgoing" else "Incoming" }, Fmt.duration(r.durationMs)).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(Fmt.full(r.startedAt), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun Actions(r: Recording) {
    val ctx = LocalContext.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (r.number != null) OutlinedButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${r.number}"))) }, Modifier.weight(1f)) {
            Icon(Icons.Filled.Call, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Call")
        }
        OutlinedButton(onClick = { shareAudio(ctx, r) }, Modifier.weight(1f)) {
            Icon(Icons.Filled.Share, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Share")
        }
    }
    Spacer(Modifier.height(8.dp))
    OutlinedButton(onClick = { openInVoiceRecorder(ctx, r) }, Modifier.fillMaxWidth()) {
        Text("Transcript")
    }
}

private fun shareAudio(ctx: Context, r: Recording) {
    val send = Intent(Intent.ACTION_SEND).setType("audio/mp4")
        .putExtra(Intent.EXTRA_STREAM, Uri.parse(r.uri))
        .putExtra(Intent.EXTRA_SUBJECT, "Call with ${Fmt.title(r)} · ${Fmt.full(r.startedAt)}")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    ctx.startActivity(Intent.createChooser(send, "Share recording"))
}

/**
 * Opens the recording inside Samsung Voice Recorder (full player, where Transcribe lives).
 * Voice Recorder opens a single recording from a `voice://recording.id/<MediaStore id>` link.
 * Falls back to a plain "open with" if that isn't available.
 */
private fun openInVoiceRecorder(ctx: Context, r: Recording) {
    val mediaId = runCatching {
        ctx.contentResolver.query(
            android.provider.MediaStore.Audio.Media.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY),
            arrayOf(android.provider.MediaStore.Audio.Media._ID),
            "${android.provider.MediaStore.Audio.Media.DISPLAY_NAME} = ? AND ${android.provider.MediaStore.Audio.Media.RELATIVE_PATH} = ?",
            arrayOf(r.fileName, r.folder.trimEnd('/') + "/"), null,
        )?.use { c -> if (c.moveToFirst()) c.getLong(0) else null }
    }.getOrNull()
    if (mediaId != null) {
        try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("voice://recording.id/$mediaId")).setPackage("com.sec.android.app.voicenote"))
            return
        } catch (_: ActivityNotFoundException) {}
    }
    val view = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(r.uri), "audio/mp4").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        ctx.startActivity(Intent(view).setPackage("com.sec.android.app.voicenote"))
    } catch (_: ActivityNotFoundException) {
        try { ctx.startActivity(Intent.createChooser(view, "Open with")) }
        catch (_: Exception) { Toast.makeText(ctx, "No app can open this file", Toast.LENGTH_SHORT).show() }
    }
}

/** Player pinned to the bottom: seek, ±10 s, speed, skip silence and a volume boost for quiet calls. */
@Composable
fun PlayerBar(r: Recording, p: PlayerState) {
    val ctx = LocalContext.current
    var dragging by remember { mutableStateOf(false) }
    var dragPos by remember { mutableFloatStateOf(0f) }
    var speed by remember { mutableFloatStateOf(PlayPrefs.speed(ctx)) }
    var skip by remember { mutableStateOf(PlayPrefs.skipSilence(ctx)) }
    var boost by remember { mutableStateOf(PlayPrefs.boost(ctx)) }
    val dur = (if (p.duration > 0) p.duration else r.durationMs).coerceAtLeast(1)
    val ear = rememberEarpiece(p)

    LaunchedEffect(speed) { p.player.playbackParameters = PlaybackParameters(speed) }
    LaunchedEffect(skip) { p.player.skipSilenceEnabled = skip }
    // Call recordings are often quiet: LoudnessEnhancer raises playback level without clipping hard.
    DisposableEffect(boost, p.player.audioSessionId) {
        val fx = runCatching { LoudnessEnhancer(p.player.audioSessionId).apply { setTargetGain(if (boost) app.callrecorder.data.AppSettings.boostLevel(ctx).gainMb else 0); enabled = boost } }.getOrNull()
        onDispose { fx?.release() }
    }

    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), tonalElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 8.dp)) {
            Slider(
                value = if (dragging) dragPos else (p.position.toFloat() / dur).coerceIn(0f, 1f),
                onValueChange = { dragging = true; dragPos = it },
                onValueChangeFinished = { p.seek((dragPos * dur).toLong()); dragging = false },
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                val shown = if (dragging) (dragPos * dur).toLong() else p.position
                Text(Fmt.duration(shown), style = MaterialTheme.typography.labelMedium.merge(Mono), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(Fmt.duration(dur), style = MaterialTheme.typography.labelMedium.merge(Mono), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { p.seek(p.position - 10_000) }) { Icon(AppIcons.Back10, "Back 10 seconds") }
                Spacer(Modifier.width(24.dp))
                FilledIconButton(onClick = { p.toggle() }, modifier = Modifier.size(60.dp)) {
                    Icon(if (p.playing) AppIcons.Pause else Icons.Filled.PlayArrow, if (p.playing) "Pause" else "Play", modifier = Modifier.size(30.dp))
                }
                Spacer(Modifier.width(24.dp))
                IconButton(onClick = { p.seek(p.position + 10_000) }) { Icon(AppIcons.Fwd10, "Forward 10 seconds") }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = speed != 1f,
                    onClick = { speed = SPEEDS[(SPEEDS.indexOf(speed).coerceAtLeast(0) + 1) % SPEEDS.size]; PlayPrefs.setSpeed(ctx, speed) },
                    label = { Text("${if (speed % 1f == 0f) speed.toInt().toString() else speed.toString()}×") },
                )
                FilterChip(selected = skip, onClick = { skip = !skip; PlayPrefs.setSkipSilence(ctx, skip) }, label = { Text("Skip silence") })
                FilterChip(selected = boost, onClick = { boost = !boost; PlayPrefs.setBoost(ctx, boost) }, label = { Text("Boost") })
                FilterChip(selected = ear.active, onClick = { ear.forced = !ear.forced }, label = { Text(if (ear.active) "Earpiece" else "Speaker") })
            }
        }
    }
}
