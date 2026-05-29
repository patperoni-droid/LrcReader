package com.patrick.lrcreader.ui.sync

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrick.lrcreader.core.sync.SmpSyncManifest
import com.patrick.lrcreader.core.sync.SmpSyncManifestComparator
import com.patrick.lrcreader.core.sync.SmpSyncManifestGenerator
import com.patrick.lrcreader.core.sync.SmpSyncPlanSummary
import com.patrick.lrcreader.core.sync.SmpSyncPlanSummaryLine
import com.patrick.lrcreader.core.sync.SmpSyncPlanSummaryLineKind
import com.patrick.lrcreader.core.sync.SmpSyncPlanSummarySeverity
import com.patrick.lrcreader.core.sync.SmpSyncPlanSummarizer
import com.patrick.lrcreader.core.sync.SmpSyncSongEntry
import com.patrick.lrcreader.exo.BuildConfig
import com.patrick.lrcreader.exo.R
import kotlinx.coroutines.launch

@Composable
fun SmpSyncDebugScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var localManifest by remember { mutableStateOf<SmpSyncManifest?>(null) }
    var comparedManifest by remember { mutableStateOf<SmpSyncManifest?>(null) }
    var summary by remember { mutableStateOf<SmpSyncPlanSummary?>(null) }
    var isGenerating by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun generateLocalManifest(compareAfterGenerate: Boolean) {
        if (isGenerating) return
        scope.launch {
            isGenerating = true
            errorMessage = null
            runCatching {
                val generated = SmpSyncManifestGenerator().generate(
                    context = context.applicationContext,
                    appVersion = BuildConfig.VERSION_NAME,
                    deviceId = null
                )
                localManifest = generated
                if (compareAfterGenerate) {
                    val fixture = buildDryRunTargetFixture(generated)
                    val plan = SmpSyncManifestComparator().compare(
                        source = generated,
                        target = fixture
                    )
                    comparedManifest = fixture
                    summary = SmpSyncPlanSummarizer().summarize(plan)
                }
            }.onFailure { error ->
                errorMessage = error.message
                    ?: context.getString(R.string.smp_sync_debug_error)
            }
            isGenerating = false
        }
    }

    val backgroundBrush = Brush.verticalGradient(
        listOf(
            Color(0xFF121212),
            Color(0xFF171717),
            Color(0xFF101010)
        )
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(onClick = onBack) {
            Text(
                text = stringResource(R.string.common_back_arrow),
                color = Color(0xFFB0BEC5)
            )
        }

        Text(
            text = stringResource(R.string.smp_sync_debug_title),
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = stringResource(R.string.smp_sync_debug_subtitle),
            color = Color(0xFFB0BEC5),
            fontSize = 13.sp
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1B1B)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { generateLocalManifest(compareAfterGenerate = false) },
                        enabled = !isGenerating,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = stringResource(R.string.smp_sync_debug_generate_manifest))
                    }
                    Button(
                        onClick = { generateLocalManifest(compareAfterGenerate = true) },
                        enabled = !isGenerating,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = stringResource(R.string.smp_sync_debug_compare_fixture))
                    }
                }

                Button(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(R.string.smp_sync_debug_sync_disabled))
                }

                if (isGenerating) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFF90CAF9)
                        )
                        Text(
                            text = stringResource(R.string.smp_sync_debug_generating),
                            color = Color(0xFFE0E0E0)
                        )
                    }
                }

                errorMessage?.let { message ->
                    Text(
                        text = message,
                        color = Color(0xFFFFAB91),
                        fontSize = 13.sp
                    )
                }
            }
        }

        localManifest?.let { manifest ->
            ManifestStatsCard(
                title = stringResource(R.string.smp_sync_debug_local_manifest),
                manifest = manifest
            )
        }

        comparedManifest?.let { manifest ->
            ManifestStatsCard(
                title = stringResource(R.string.smp_sync_debug_fixture_manifest),
                manifest = manifest
            )
        }

        SummaryCard(summary = summary)
    }
}

@Composable
private fun ManifestStatsCard(
    title: String,
    manifest: SmpSyncManifest
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF181818)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(
                    R.string.smp_sync_debug_manifest_counts,
                    manifest.songs.size,
                    manifest.playlists.size,
                    manifest.families.size
                ),
                color = Color(0xFFCFD8DC),
                fontSize = 13.sp
            )
            Text(
                text = stringResource(R.string.smp_sync_debug_generated_at, manifest.generatedAt),
                color = Color(0xFF8FA3AD),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun SummaryCard(summary: SmpSyncPlanSummary?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF181818)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.smp_sync_debug_summary_title),
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )
            if (summary == null) {
                Text(
                    text = stringResource(R.string.smp_sync_debug_no_summary),
                    color = Color(0xFFB0BEC5),
                    fontSize = 13.sp
                )
                return@Column
            }

            summary.lines.forEach { line ->
                SummaryLine(line)
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.smp_sync_debug_dry_run_only),
                color = Color(0xFF90CAF9),
                fontSize = 12.sp,
                textAlign = TextAlign.Start
            )
        }
    }
}

@Composable
private fun SummaryLine(line: SmpSyncPlanSummaryLine) {
    val color = when (line.severity) {
        SmpSyncPlanSummarySeverity.INFO -> Color(0xFFCFD8DC)
        SmpSyncPlanSummarySeverity.ACTION -> Color(0xFFA5D6A7)
        SmpSyncPlanSummarySeverity.WARNING -> Color(0xFFFFCC80)
    }
    Text(
        text = summaryLineText(line),
        color = color,
        fontSize = 14.sp
    )
}

@Composable
private fun summaryLineText(line: SmpSyncPlanSummaryLine): String {
    return when (line.kind) {
        SmpSyncPlanSummaryLineKind.SONGS_IDENTICAL ->
            stringResource(R.string.smp_sync_debug_line_songs_identical, line.count)
        SmpSyncPlanSummaryLineKind.SONGS_ABSENT_ON_B ->
            stringResource(R.string.smp_sync_debug_line_songs_absent_on_b, line.count)
        SmpSyncPlanSummaryLineKind.SONGS_MODIFIED_ON_A ->
            stringResource(R.string.smp_sync_debug_line_songs_modified_on_a, line.count)
        SmpSyncPlanSummaryLineKind.SONGS_MODIFIED_ON_B ->
            stringResource(R.string.smp_sync_debug_line_songs_modified_on_b, line.count)
        SmpSyncPlanSummaryLineKind.POSSIBLE_CONFLICTS ->
            stringResource(R.string.smp_sync_debug_line_conflicts, line.count)
        SmpSyncPlanSummaryLineKind.PLAYLISTS_DIFFERENT ->
            stringResource(R.string.smp_sync_debug_line_playlists_different, line.count)
        SmpSyncPlanSummaryLineKind.FAMILIES_DIFFERENT ->
            stringResource(R.string.smp_sync_debug_line_families_different, line.count)
        SmpSyncPlanSummaryLineKind.BROKEN_REFERENCES ->
            stringResource(R.string.smp_sync_debug_line_broken_references, line.count)
        SmpSyncPlanSummaryLineKind.SONGS_ABSENT_ON_A ->
            stringResource(R.string.smp_sync_debug_line_songs_absent_on_a, line.count)
        SmpSyncPlanSummaryLineKind.NO_AUTOMATIC_DELETION ->
            stringResource(R.string.smp_sync_debug_line_no_auto_delete)
    }
}

private fun buildDryRunTargetFixture(source: SmpSyncManifest): SmpSyncManifest {
    val targetSongs = when {
        source.songs.isEmpty() -> listOf(
            SmpSyncSongEntry(
                songId = "fixture_only_on_backup",
                title = "Fixture backup only",
                fullSongHash = "fixture-backup-only"
            )
        )
        source.songs.size == 1 -> emptyList()
        else -> source.songs.dropLast(1).mapIndexed { index, song ->
            if (index == 0) {
                song.copy(fullSongHash = "${song.fullSongHash}:backup")
            } else {
                song
            }
        }
    }

    val targetPlaylists = source.playlists.mapIndexed { index, playlist ->
        if (index == 0) {
            playlist.copy(fullPlaylistHash = "${playlist.fullPlaylistHash}:backup")
        } else {
            playlist
        }
    }

    val targetFamilies = source.families.mapIndexed { index, family ->
        if (index == 0) {
            family.copy(hash = "${family.hash}:backup")
        } else {
            family
        }
    }

    return source.copy(
        deviceId = "fixture-backup",
        generatedAt = source.generatedAt,
        songs = targetSongs,
        playlists = targetPlaylists,
        families = targetFamilies
    )
}
