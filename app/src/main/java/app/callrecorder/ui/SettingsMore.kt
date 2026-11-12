package app.callrecorder.ui

import android.content.Intent
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.callrecorder.data.AppSettings as S

/** A label with a row of single-choice chips. */
@Composable
private fun <T> Choice(title: String, options: List<T>, selected: T, label: (T) -> String, onPick: (T) -> Unit) {
    Text(title, style = MaterialTheme.typography.bodyLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o -> FilterChip(selected = o == selected, onClick = { onPick(o) }, label = { Text(label(o)) }) }
    }
}

@Composable
fun RulesSection() {
    val ctx = LocalContext.current
    var only by remember { mutableStateOf(S.recordOnly(ctx)) }
    var internet by remember { mutableStateOf(S.internetCalls(ctx)) }
    var minLen by remember { mutableStateOf(S.minSeconds(ctx)) }
    var delay by remember { mutableStateOf(S.startDelay(ctx)) }
    var excluded by remember { mutableStateOf(S.excluded(ctx)) }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val uri = res.data?.data ?: return@rememberLauncherForActivityResult
        ctx.contentResolver.query(
            uri, arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME), null, null, null,
        )?.use { c -> if (c.moveToFirst()) S.addExcluded(ctx, c.getString(0) ?: "", c.getString(1) ?: c.getString(0) ?: "") }
        excluded = S.excluded(ctx)
    }

    Section("Who to record") {
        Choice("Record", S.RecordOnly.entries, only, { it.label }) { only = it; S.setRecordOnly(ctx, it) }
        SwitchRow("WhatsApp & internet calls", "Record calls made through apps, not just phone calls.", internet) { internet = it; S.setInternetCalls(ctx, it) }
        Choice("Skip calls shorter than", S.MIN_LENGTHS, minLen, { if (it == 0) "Keep all" else "${it}s" }) { minLen = it; S.setMinSeconds(ctx, it) }
        Choice("Start recording after", S.START_DELAYS, delay, { if (it == 0) "Right away" else "${it}s" }) { delay = it; S.setStartDelay(ctx, it) }
        Text(
            "Android only tells an app who the call was with after it ends, so these rules are applied then. A recording that shouldn't be kept is deleted straight away and never appears.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text("Never record", style = MaterialTheme.typography.bodyLarge)
        if (excluded.isEmpty()) Text("No one yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        excluded.forEach { e ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(e.name, style = MaterialTheme.typography.bodyMedium)
                    Text(Fmt.phone(e.digits), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { S.removeExcluded(ctx, e.digits); excluded = S.excluded(ctx) }) { Text("Remove") }
            }
        }
        OutlinedButton(onClick = { pick.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)) }) { Text("Add contact") }
    }
}

@Composable
fun LibraryOptions() {
    val ctx = LocalContext.current
    var showAll by remember { mutableStateOf(S.mainShowsAll(ctx)) }
    var group by remember { mutableStateOf(S.groupBy(ctx)) }
    var hideShort by remember { mutableStateOf(S.hideShortSeconds(ctx)) }
    Section("Library") {
        SwitchRow("Show unknown & spam in the main list", "Off keeps them in their own section.", showAll) { showAll = it; S.setMainShowsAll(ctx, it) }
        Choice("Group by", S.GroupBy.entries, group, { it.label }) { group = it; S.setGroupBy(ctx, it) }
        Choice("Hide recordings shorter than", S.HIDE_SHORT, hideShort, { if (it == 0) "Show all" else "${it}s" }) { hideShort = it; S.setHideShortSeconds(ctx, it) }
    }
}

@Composable
fun PlaybackOptions() {
    val ctx = LocalContext.current
    var speed by remember { mutableStateOf(PlayPrefs.speed(ctx)) }
    var skip by remember { mutableStateOf(PlayPrefs.skipSilence(ctx)) }
    var boostOn by remember { mutableStateOf(PlayPrefs.boost(ctx)) }
    var boost by remember { mutableStateOf(S.boostLevel(ctx)) }
    var auto by remember { mutableStateOf(S.autoPlay(ctx)) }
    Section("Playback") {
        Choice("Speed", listOf(0.75f, 1f, 1.25f, 1.5f, 2f), speed, { "${if (it % 1f == 0f) it.toInt().toString() else it.toString()}×" }) { speed = it; PlayPrefs.setSpeed(ctx, it) }
        SwitchRow("Skip silence", "Jump over pauses in the conversation.", skip) { skip = it; PlayPrefs.setSkipSilence(ctx, it) }
        SwitchRow("Volume boost", "Call recordings are quiet; this makes them louder.", boostOn) { boostOn = it; PlayPrefs.setBoost(ctx, it) }
        if (boostOn) Choice("Boost strength", S.Boost.entries, boost, { it.label }) { boost = it; S.setBoostLevel(ctx, it) }
        SwitchRow("Start playing when opened", "Open a recording and it plays straight away.", auto) { auto = it; S.setAutoPlay(ctx, it) }
    }
}

@Composable
fun LookOptions() {
    val ctx = LocalContext.current
    Section("Appearance") {
        Choice("Theme", S.Theme.entries, S.theme.value, { it.label }) { S.setTheme(ctx, it) }
    }
}
