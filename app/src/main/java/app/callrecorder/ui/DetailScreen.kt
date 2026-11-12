package app.callrecorder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import app.callrecorder.data.AppDb
import app.callrecorder.data.Recording
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Small wrapper so screens can drive one ExoPlayer and read its position. */
class PlayerState(val player: ExoPlayer) {
    var playing by mutableStateOf(false)
    var position by mutableLongStateOf(0L)
    var duration by mutableLongStateOf(0L)
    fun toggle() { if (player.isPlaying) player.pause() else { if (player.playbackState == Player.STATE_ENDED) player.seekTo(0); player.play() } }
    fun seek(ms: Long) { player.seekTo(ms.coerceIn(0, duration.coerceAtLeast(0))); position = player.currentPosition }
}

@Composable
fun rememberPlayer(uri: String): PlayerState {
    val ctx = LocalContext.current
    val state = remember(uri) {
        val p = ExoPlayer.Builder(ctx).build().apply { setMediaItem(MediaItem.fromUri(uri)); prepare() }
        PlayerState(p)
    }
    DisposableEffect(state) {
        val l = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { state.playing = isPlaying }
            override fun onPlaybackStateChanged(s: Int) { state.duration = state.player.duration.coerceAtLeast(0) }
        }
        state.player.addListener(l)
        onDispose { state.player.removeListener(l); state.player.release() }
    }
    LaunchedEffect(state) { while (true) { state.position = state.player.currentPosition; delay(100) } }
    return state
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(id: Long, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val dao = remember { AppDb.get(ctx).recordings() }
    val rec by dao.observe(id).collectAsStateWithLifecycle(initialValue = null)
    val copies by dao.copiesOf(id).collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    var confirmHide by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var showFile by remember { mutableStateOf(false) }

    val r = rec
    if (r == null) { Scaffold { Spacer(Modifier.padding(it)) }; return }
    val player = rememberPlayer(r.uri)
    // Resume where playback was left; remember the spot when leaving.
    LaunchedEffect(r.id, player.duration) {
        if (player.duration > 0 && player.position == 0L && r.positionMs in 5_000 until player.duration - 5_000) player.seek(r.positionMs)
        if (player.duration > 0 && app.callrecorder.data.AppSettings.autoPlay(ctx) && !player.playing) player.player.play()
    }
    DisposableEffect(r.id) {
        onDispose {
            val pos = player.player.currentPosition
            val end = player.duration
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                dao.setPosition(r.id, if (end > 0 && pos > end - 5_000) 0 else pos)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(Fmt.title(r), style = MaterialTheme.typography.titleLarge)
                        Text(
                            listOfNotNull(Fmt.app(r).takeIf { r.app != "phone" }, r.direction?.let { if (it.startsWith("out")) "Outgoing" else "Incoming" }, Fmt.full(r.startedAt)).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { scope.launch { dao.setStarred(r.id, !r.starred) } }) {
                        Icon(if (r.starred) Icons.Filled.Star else AppIcons.StarOutline, if (r.starred) "Unstar" else "Star",
                            tint = if (r.starred) MaterialTheme.colorScheme.primary else LocalContentColor.current)
                    }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("File details") }, onClick = { menu = false; showFile = true })
                        DropdownMenuItem(text = { Text("Hide from list") }, onClick = { menu = false; confirmHide = true })
                        if (r.source == "OWN") DropdownMenuItem(text = { Text("Delete", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; confirmDelete = true })
                    }
                },
            )
        },
        bottomBar = { PlayerBar(r, player) },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(8.dp))
            CallCard(r)
            Spacer(Modifier.height(16.dp))
            Actions(r)
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showFile) AlertDialog(
        onDismissRequest = { showFile = false },
        title = { Text("File") },
        text = {
            Column {
                FileLine(r, primary = true)
                copies.forEach { HorizontalDivider(Modifier.padding(vertical = 6.dp)); FileLine(it, primary = false) }
            }
        },
        confirmButton = { TextButton(onClick = { showFile = false }) { Text("Close") } },
    )

    if (confirmHide) AlertDialog(
        onDismissRequest = { confirmHide = false },
        title = { Text("Hide this recording?") },
        text = { Text("It disappears from the list. The audio file stays exactly where it is.") },
        confirmButton = { TextButton(onClick = { confirmHide = false; scope.launch { dao.setHidden(id, true); onBack() } }) { Text("Hide") } },
        dismissButton = { TextButton(onClick = { confirmHide = false }) { Text("Cancel") } },
    )

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete this recording?") },
        text = { Text("The audio file and its transcript are permanently removed from your phone. This can't be undone.") },
        confirmButton = {
            TextButton(onClick = {
                confirmDelete = false
                scope.launch {
                    if (app.callrecorder.data.Retention.deleteOwn(ctx, r)) onBack()
                    else android.widget.Toast.makeText(ctx, "Couldn't delete this file", android.widget.Toast.LENGTH_SHORT).show()
                }
            }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
    )
}

@Composable
private fun FileLine(r: Recording, primary: Boolean) {
    Column {
        Text(r.fileName, style = MaterialTheme.typography.bodyMedium)
        Text(
            "${r.folder} · ${Fmt.source(r)} · ${"%.1f".format(r.sizeBytes / 1024.0)} KB" + if (primary) "" else " · same audio",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

