package app.callrecorder.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.callrecorder.data.Prefs
import app.callrecorder.recorder.CallWatchService
import app.callrecorder.recorder.EventLog
import app.callrecorder.recorder.RecorderService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private val NEEDED = arrayOf(
    Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CALL_LOG,
    Manifest.permission.READ_CONTACTS, Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.READ_MEDIA_AUDIO,
)

fun missingPermissions(ctx: Context) = NEEDED.filter { ctx.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }

fun watcherOn(ctx: Context): Boolean {
    val enabled = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
    val me = ComponentName(ctx, CallWatchService::class.java).flattenToString()
    return enabled.split(':').any { it.equals(me, ignoreCase = true) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onPickFolder: () -> Unit) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    var showLog by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { tick++ }
    LifecycleResumeEffect(Unit) { tick++; onPauseOrDispose { } }
    LaunchedEffect(showLog) { while (showLog) { delay(1500); tick++ } }

    val miss = remember(tick) { missingPermissions(ctx) }
    val watcher = remember(tick) { watcherOn(ctx) }
    val folder = remember(tick) { Prefs.treeUri(ctx)?.let { android.provider.DocumentsContract.getTreeDocumentId(it).substringAfter(':') } }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Settings") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } })
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Section("Recording") {
                StatusRow("Permissions", miss.isEmpty(), if (miss.isEmpty()) "All granted" else "${miss.size} missing")
                if (miss.isNotEmpty()) FilledTonalButton(onClick = { launcher.launch(NEEDED) }) { Text("Grant permissions") }
                StatusRow("Call watcher (Accessibility)", watcher, if (watcher) "On" else "Off")
                if (!watcher) {
                    Text(
                        "Turn on Accessibility › Installed apps › Call Recorder. It only watches the call state; it doesn't read your screen.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FilledTonalButton(onClick = { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) { Text("Open Accessibility settings") }
                }
                OutlinedButton(onClick = { RecorderService.start(ctx, "test") }, enabled = miss.isEmpty()) { Text("10-second microphone test") }
            }

            RecordingOptions(tick)

            RulesSection()

            LibraryOptions()

            PlaybackOptions()

            LookOptions()

            Section("Library folder") {
                Text(folder ?: "Not chosen", style = MaterialTheme.typography.bodyLarge)
                Text("Read-only. Subfolders are included.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = onPickFolder) { Text(if (folder == null) "Choose folder" else "Change folder") }
            }

            StorageSection()

            TidySection()

            Section("Privacy") {
                Text(
                    "This app has no internet permission, so Android blocks it from sending anything off your phone. Recordings, transcripts and the database stay on this device.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Section("Diagnostics") {
                TextButton(onClick = { showLog = !showLog }) { Text(if (showLog) "Hide activity log" else "Show activity log") }
                if (showLog) {
                    val log = remember(tick) { EventLog.read(ctx, 80) }
                    log.forEach { Text(it, fontFamily = FontFamily.Monospace, fontSize = 11.sp) }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
internal fun Section(title: String, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
private fun StatusRow(label: String, ok: Boolean, detail: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(if (ok) "●" else "○", color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
        Spacer(Modifier.size(10.dp))
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.width(8.dp))
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}


@Composable
private fun StorageSection() {
    val ctx = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val dao = remember { app.callrecorder.data.AppDb.get(ctx).recordings() }
    val total by dao.totalBytes().collectAsStateWithLifecycle(initialValue = 0L)
    val count by dao.fileCount().collectAsStateWithLifecycle(initialValue = 0)
    var keepDays by remember { mutableIntStateOf(app.callrecorder.data.Retention.days(ctx)) }
    var pending by remember { mutableStateOf<Pair<Int, Int>?>(null) } // days to count affected

    Section("Storage") {
        StatLine("Call recordings", "${bytes(total)} · $count files")
        Spacer(Modifier.height(4.dp))
        Text("Delete recordings after", style = MaterialTheme.typography.bodyLarge)
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            app.callrecorder.data.Retention.CHOICES.forEach { d ->
                androidx.compose.material3.FilterChip(
                    selected = keepDays == d,
                    onClick = {
                        if (d == 0) { app.callrecorder.data.Retention.setDays(ctx, 0); keepDays = 0 }
                        else scope.launch { pending = d to app.callrecorder.data.Retention.wouldDelete(ctx, d).size }
                    },
                    label = { Text(if (d == 0) "Never" else if (d == 365) "1 year" else "$d days") },
                )
            }
        }
        Text(
            "Applies to recordings made by Call Recorder, except starred ones. Recordings from other apps are read-only here and are never deleted.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    pending?.let { (d, n) ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Delete recordings older than $d days?") },
            text = { Text(if (n == 0) "Nothing is that old yet. From now on, recordings and their transcripts are permanently deleted once they pass $d days." else "$n recording${if (n == 1) "" else "s"} will be permanently deleted now, with their transcripts, and more as they pass $d days. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    app.callrecorder.data.Retention.setDays(ctx, d); keepDays = d; pending = null
                    scope.launch { app.callrecorder.data.Retention.runCleanup(ctx) }
                }) { Text("Turn on", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun bytes(b: Long): String = when {
    b >= 1_000_000_000 -> "%.1f GB".format(b / 1e9)
    b >= 1_000_000 -> "%.1f MB".format(b / 1e6)
    else -> "%.0f KB".format(b / 1e3)
}


@Composable
private fun TidySection() {
    val ctx = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var pending by remember { mutableStateOf<List<app.callrecorder.data.Tidy.Plan>>(emptyList()) }
    var waiting by remember { mutableStateOf<List<app.callrecorder.data.Tidy.Plan>>(emptyList()) }

    fun finish(msg: String) {
        scope.launch {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { app.callrecorder.data.Scanner(ctx).scan() } }
            android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        if (res.resultCode == android.app.Activity.RESULT_OK) {
            val left = app.callrecorder.data.Tidy.apply(ctx, waiting)
            finish(if (left.isEmpty()) "File names tidied" else "${left.size} couldn't be renamed")
        }
        waiting = emptyList()
    }

    Section("File names") {
        Text(
            "New recordings are saved as \"Jane Doe – 2026-11-11 8.21 PM\". Older recordings made by this app can be renamed the same way. Files from other apps are left alone.",
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedButton(onClick = { scope.launch { pending = app.callrecorder.data.Tidy.plan(ctx); if (pending.isEmpty()) android.widget.Toast.makeText(ctx, "Nothing to rename", android.widget.Toast.LENGTH_SHORT).show() } }) {
            Text("Tidy up file names")
        }
    }

    if (pending.isNotEmpty()) androidx.compose.material3.AlertDialog(
        onDismissRequest = { pending = emptyList() },
        title = { Text("Rename ${pending.size} recording${if (pending.size == 1) "" else "s"}?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                pending.take(20).forEach { Text("${it.from}\n→ ${it.to}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 4.dp)) }
                if (pending.size > 20) Text("…and ${pending.size - 20} more", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val plans = pending; pending = emptyList()
                val left = app.callrecorder.data.Tidy.apply(ctx, plans)
                if (left.isEmpty()) finish("File names tidied")
                else {
                    // Files from an older install need Android's one-time OK before this app may rename them.
                    waiting = left
                    val req = android.provider.MediaStore.createWriteRequest(ctx.contentResolver, left.map { it.uri })
                    consent.launch(androidx.activity.result.IntentSenderRequest.Builder(req.intentSender).build())
                }
            }) { Text("Rename") }
        },
        dismissButton = { TextButton(onClick = { pending = emptyList() }) { Text("Cancel") } },
    )
}


@Composable
private fun RecordingOptions(tick: Int) {
    val ctx = LocalContext.current
    var auto by remember { mutableStateOf(app.callrecorder.recorder.RecordSettings.autoRecord(ctx)) }
    var quality by remember { mutableStateOf(app.callrecorder.recorder.RecordSettings.quality(ctx)) }
    var notify by remember { mutableStateOf(app.callrecorder.recorder.RecordSettings.notifyAfter(ctx)) }
    val voipNames = remember(tick) { app.callrecorder.recorder.CallNotificationWatcher.isEnabled(ctx) }

    Section("Recording options") {
        SwitchRow("Record calls automatically", "Turn off to pause recording without changing anything else.", auto) {
            auto = it; app.callrecorder.recorder.RecordSettings.setAutoRecord(ctx, it)
        }
        Text("Quality", style = MaterialTheme.typography.bodyLarge)
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            app.callrecorder.recorder.RecordSettings.Quality.entries.forEach { q ->
                androidx.compose.material3.FilterChip(
                    selected = quality == q,
                    onClick = { quality = q; app.callrecorder.recorder.RecordSettings.setQuality(ctx, q) },
                    label = { Text("${q.label} · ${q.bitrate / 1000} kbps") },
                )
            }
        }
        Text(
            "About ${"%.1f".format(quality.bitrate / 8.0 * 60 / 1_000_000)} MB per minute. Calls carry phone-quality audio, so High is plenty.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SwitchRow("Notify after each recording", "One quiet note when a call is saved.", notify) {
            notify = it; app.callrecorder.recorder.RecordSettings.setNotifyAfter(ctx, it)
        }
        StatusRow("Names for WhatsApp & internet calls", voipNames, if (voipNames) "On" else "Off")
        Text(
            "Internet calls aren't in the phone's call log. With notification access, the app reads the caller's name from the call notification only (nothing else is read or kept), so those recordings get a name and photo.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!voipNames) FilledTonalButton(onClick = { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }) { Text("Allow notification access") }
    }
}

@Composable
internal fun SwitchRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        androidx.compose.material3.Switch(checked = checked, onCheckedChange = onChange)
    }
}
