package app.callrecorder.ui

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.callrecorder.data.AppDb
import app.callrecorder.data.Prefs
import app.callrecorder.data.Recording
import app.callrecorder.data.Scanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import androidx.compose.ui.graphics.asImageBitmap

private enum class Range(val label: String, val days: Long?) { ALL("All", null), TODAY("Today", 0), WEEK("7 days", 6), MONTH("30 days", 29) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(onOpen: (Long) -> Unit, onSettings: () -> Unit, screened: Boolean = false, onScreened: () -> Unit = {}, onBack: () -> Unit = {}) {
    val ctx = LocalContext.current
    val dao = remember { AppDb.get(ctx).recordings() }
    val all by dao.library().collectAsStateWithLifecycle(initialValue = null)
    var tree by remember { mutableStateOf(Prefs.treeUri(ctx)) }
    var query by rememberSaveable { mutableStateOf("") }
    var range by rememberSaveable { mutableStateOf(Range.ALL) }
    var refreshing by remember { mutableStateOf(false) }
    var scanTick by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf(setOf<Long>()) }
    val selecting = selected.isNotEmpty()
    var confirmDelete by remember { mutableStateOf(false) }
    var pendingTrash by remember { mutableStateOf<List<Recording>>(emptyList()) }
    androidx.activity.compose.BackHandler(enabled = selecting) { selected = emptySet() }

    // Automatic scans (on open/return) run quietly; only a pull-down shows the spinner.
    fun rescan(showSpinner: Boolean = false) {
        if (Prefs.treeUri(ctx) == null) return
        scope.launch {
            if (showSpinner) refreshing = true
            withContext(Dispatchers.IO) { runCatching { Scanner(ctx).scan() } }
            refreshing = false
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            Prefs.setTreeUri(ctx, uri); tree = uri; scanTick++
        }
    }
    LaunchedEffect(scanTick) { rescan() }

    // Android shows its own "move to Recycle bin" confirmation; files stay restorable for 30 days.
    val trashLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        val rows = pendingTrash; pendingTrash = emptyList()
        if (res.resultCode == android.app.Activity.RESULT_OK) {
            scope.launch {
                rows.forEach { dao.markMissing(it.id) }
                selected = emptySet()
                android.widget.Toast.makeText(ctx, "Moved ${rows.size} to Recycle bin", android.widget.Toast.LENGTH_SHORT).show()
                rescan()
            }
        }
    }

    fun deleteSelected(rows: List<Recording>) {
        scope.launch {
            val found = withContext(Dispatchers.IO) { rows.map { it to app.callrecorder.data.Media.uriOf(ctx, it) } }
            val trashable = found.filter { it.second != null }
            val hiddenOnly = found.filter { it.second == null }.map { it.first }
            // Files in folders Android doesn't index (e.g. ones with .nomedia) can't be trashed by another app: hide them instead.
            hiddenOnly.forEach { dao.setHidden(it.id, true) }
            if (hiddenOnly.isNotEmpty()) android.widget.Toast.makeText(ctx, "${hiddenOnly.size} hidden instead (their folder can't be cleaned by other apps)", android.widget.Toast.LENGTH_LONG).show()
            if (trashable.isEmpty()) { selected = emptySet(); return@launch }
            pendingTrash = trashable.map { it.first }
            val req = android.provider.MediaStore.createTrashRequest(ctx.contentResolver, trashable.map { it.second!! }, true)
            trashLauncher.launch(androidx.activity.result.IntentSenderRequest.Builder(req.intentSender).build())
        }
    }

    LifecycleResumeEffect(Unit) { rescan(); onPauseOrDispose { } }

    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            if (selecting) TopAppBar(
                title = { Text("${selected.size} selected") },
                navigationIcon = { IconButton(onClick = { selected = emptySet() }) { Icon(Icons.Filled.Close, "Cancel selection") } },
                actions = {
                    TextButton(onClick = {
                        val l = all ?: emptyList()
                        selected = l.filter { isScreened(it) == screened }.map { it.id }.toSet()
                    }) { Text("All") }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Delete") }
                },
            ) else LargeTopAppBar(
                title = { Text(if (screened) "Unknown & spam" else "Recordings") },
                navigationIcon = { if (screened) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { if (!screened) IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, "Settings") } },
                scrollBehavior = scroll,
            )
        },
    ) { pad ->
        if (tree == null) {
            EmptyState(Modifier.padding(pad)) { picker.launch(initialCallFolder()) }
            return@Scaffold
        }
        val list = all
        // Library settings (read each time the screen shows, so changes apply on return)
        val showAll = app.callrecorder.data.AppSettings.mainShowsAll(ctx)
        val hideShortMs = app.callrecorder.data.AppSettings.hideShortSeconds(ctx) * 1000L
        val byPerson = app.callrecorder.data.AppSettings.groupBy(ctx) == app.callrecorder.data.AppSettings.GroupBy.PERSON
        val visible = remember(list, hideShortMs) { (list ?: emptyList()).filter { hideShortMs == 0L || it.durationMs >= hideShortMs } }
        val (hiddenSet, mainSet) = remember(visible, showAll) {
            if (showAll && !screened) emptyList<Recording>() to visible else visible.partition { isScreened(it) }
        }
        val pool = if (screened) hiddenSet else mainSet
        val filtered = remember(pool, query, range) { filter(pool, query, range) }
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = { rescan(showSpinner = true) }, modifier = Modifier.padding(pad).fillMaxSize()) {
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                item {
                    SearchField(query) { query = it }
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(Range.entries) { r -> FilterChip(selected = range == r, onClick = { range = r }, label = { Text(r.label) }) }
                    }
                    Spacer(Modifier.height(4.dp))
                    if (!screened && hiddenSet.isNotEmpty()) ScreenedEntry(hiddenSet.size, onScreened)
                }
                if (list != null && filtered.isEmpty()) {
                    item {
                        Text(
                            if (pool.isEmpty()) (if (screened) "Nothing here." else "No recordings found yet. Pull down to refresh.") else "Nothing matches.",
                            Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                val groups: Map<String, List<Recording>> =
                    if (byPerson) filtered.groupBy { Fmt.title(it) } // keeps newest-first order of first appearance
                    else filtered.groupBy { Fmt.dayHeader(Fmt.date(it.startedAt)) }
                groups.forEach { (header, rows) ->
                    item(key = "h$header") {
                        Text(
                            if (byPerson) "$header · ${rows.size}" else header, style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 6.dp),
                        )
                    }
                    items(rows, key = { it.id }) { r ->
                        RecordingRow(
                            r, selected = r.id in selected,
                            onClick = { if (selecting) selected = if (r.id in selected) selected - r.id else selected + r.id else onOpen(r.id) },
                            onLongClick = { selected = selected + r.id },
                        )
                    }
                }
            }
        }
    }
    if (confirmDelete) DeleteDialog(selected.size, onConfirm = {
        confirmDelete = false
        deleteSelected((all ?: emptyList()).filter { it.id in selected })
    }, onDismiss = { confirmDelete = false })
}

@Composable
private fun DeleteDialog(count: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete $count recording${if (count == 1) "" else "s"}?") },
        text = { Text("They go to your phone's Recycle bin and can be restored there for 30 days.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Unknown numbers, hidden numbers and spam-labelled calls live in their own list. */
fun isScreened(r: Recording): Boolean {
    val name = r.contactName
    return name == null || name.contains("spam", ignoreCase = true)
}

@Composable
private fun ScreenedEntry(count: Int, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer).clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text("Unknown callers & spam", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text("$count", style = MaterialTheme.typography.bodyLarge.merge(Mono), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(4.dp))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun filter(rows: List<Recording>, q: String, range: Range): List<Recording> {
    val needle = q.trim().lowercase()
    val digits = needle.filter(Char::isDigit)
    val from = range.days?.let { LocalDate.now().minusDays(it) }
    return rows.filter { r ->
        (from == null || !Fmt.date(r.startedAt).isBefore(from)) &&
            (needle.isEmpty() ||
                r.contactName?.lowercase()?.contains(needle) == true ||
                (digits.length >= 3 && r.number?.filter(Char::isDigit)?.contains(digits) == true) ||
                r.fileName.lowercase().contains(needle))
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit) {
    TextField(
        value = value, onValueChange = onChange, singleLine = true,
        placeholder = { Text("Search name or number") },
        leadingIcon = { Icon(Icons.Filled.Search, null) },
        trailingIcon = { if (value.isNotEmpty()) IconButton(onClick = { onChange("") }) { Icon(Icons.Filled.Clear, "Clear") } },
        shape = RoundedCornerShape(28.dp),
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

private val photoCache = android.util.LruCache<String, androidx.compose.ui.graphics.ImageBitmap>(64)

@Composable
fun Avatar(r: Recording, size: Int = 44) {
    val ctx = LocalContext.current
    val initial = r.contactName?.takeUnless { it.contains("spam", true) }?.firstOrNull { it.isLetter() }?.uppercaseChar()
    val photo by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(r.contactPhoto?.let { photoCache.get(it) }, r.contactPhoto) {
        val u = r.contactPhoto ?: return@produceState
        if (value != null) return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching {
                ctx.contentResolver.openInputStream(Uri.parse(u))?.use { android.graphics.BitmapFactory.decodeStream(it) }
                    ?.asImageBitmap()?.also { photoCache.put(u, it) }
            }.getOrNull()
        }
    }
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        val img = photo
        if (img != null) androidx.compose.foundation.Image(img, null, Modifier.size(size.dp), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
        else if (initial != null) Text(initial.toString(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        else Icon(Icons.Filled.Phone, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size((size * 0.45).dp))
    }
}

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun RecordingRow(r: Recording, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Check, "Selected", tint = MaterialTheme.colorScheme.onPrimary)
        } else Avatar(r)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(Fmt.title(r), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val meta = buildList {
                if (r.app != "phone") add(Fmt.app(r))
                add(Fmt.time(r.startedAt))
                r.direction?.let { add(if (it.startsWith("out")) "Outgoing" else "Incoming") }
            }.joinToString(" · ")
            Text(meta, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(Fmt.duration(r.durationMs), style = MaterialTheme.typography.bodyMedium.merge(Mono), color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (r.starred) Icon(Icons.Filled.Star, "Starred", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp).padding(top = 2.dp))
        }
    }
}

@Composable
fun StatusPill(text: String) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(50), modifier = Modifier.padding(top = 4.dp)) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
    }
}

@Composable
private fun EmptyState(modifier: Modifier, onPick: () -> Unit) {
    Column(modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(AppIcons.Folder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(16.dp))
        Text("Show your call recordings", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "Pick the Recordings › Call folder. The app gets read-only access to it, so it can list every call recording in there, including subfolders, without changing them.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onPick) { Text("Choose folder") }
    }
}

fun initialCallFolder(): Uri = DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Recordings/Call")
