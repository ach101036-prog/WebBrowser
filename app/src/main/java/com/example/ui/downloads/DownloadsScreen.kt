package com.example.ui.downloads

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.browser.UrlStreamClassifier
import com.example.data.local.DownloadedVideoEntity
import com.example.service.ActiveDownloadJob
import com.example.service.JobStatus
import com.example.ui.theme.CoralPulse
import com.example.ui.theme.CoralPulseContainer
import com.example.ui.theme.CyanElectric
import com.example.ui.theme.CyanElectricContainer
import com.example.ui.theme.EmeraldReady
import com.example.ui.theme.MonoBadgeTextStyle
import com.example.ui.util.Formatters

@Composable
fun DownloadsScreen(
    uiState: DownloadsUiState,
    onSearchQueryChange: (String) -> Unit,
    onCancelJob: (String) -> Unit,
    onDismissJob: (String) -> Unit,
    onPlayVideo: (DownloadedVideoEntity) -> Unit,
    onDeleteVideo: (DownloadedVideoEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var pendingDelete by remember { mutableStateOf<DownloadedVideoEntity?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp)
            .widthIn(max = 840.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(Modifier.height(4.dp))
        Column {
            Text(
                text = stringResource(R.string.downloads_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = stringResource(R.string.downloads_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (uiState.savedVideos.isNotEmpty() || uiState.searchQuery.isNotEmpty()) {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text(stringResource(R.string.search_downloads_placeholder)) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("downloads_search_input")
            )
        }

        if (uiState.activeJobs.isEmpty() && uiState.savedVideos.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_empty_downloads),
                        contentDescription = "No downloads yet",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(220.dp, 165.dp)
                            .clip(MaterialTheme.shapes.large)
                    )
                    Text(
                        text = stringResource(R.string.downloads_empty_title),
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = stringResource(R.string.downloads_empty_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 20.dp)
            ) {
                if (uiState.activeJobs.isNotEmpty()) {
                    item(key = "header_jobs") {
                        Text(
                            text = "FFmpeg Background Queue",
                            style = MaterialTheme.typography.labelLarge,
                            color = CyanElectric
                        )
                    }
                    items(uiState.activeJobs, key = { "job_${it.jobId}" }) { job ->
                        ActiveJobCard(
                            job = job,
                            onCancel = { onCancelJob(job.jobId) },
                            onDismiss = { onDismissJob(job.jobId) }
                        )
                    }
                }

                if (uiState.savedVideos.isNotEmpty()) {
                    item(key = "header_saved") {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Saved MP4 Videos (${uiState.savedVideos.size})",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    items(uiState.savedVideos, key = { it.id }) { video ->
                        SavedVideoCard(
                            video = video,
                            onPlay = { onPlayVideo(video) },
                            onShare = {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "video/mp4"
                                    putExtra(Intent.EXTRA_STREAM, Uri.parse(video.contentUri))
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share MP4"))
                            },
                            onDelete = { pendingDelete = video }
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.confirm_delete_title)) },
            text = { Text(stringResource(R.string.confirm_delete_body)) },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteVideo(target)
                        pendingDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CoralPulse)
                ) {
                    Text(stringResource(R.string.delete_video))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun ActiveJobCard(
    job: ActiveDownloadJob,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    val borderColor = when (job.status) {
        JobStatus.RUNNING, JobStatus.QUEUED -> CyanElectric
        JobStatus.COMPLETED -> EmeraldReady
        JobStatus.FAILED -> CoralPulse
        JobStatus.CANCELLED -> MaterialTheme.colorScheme.outline
    }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, borderColor.copy(alpha = 0.7f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("active_job_card_${job.jobId}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (job.status == JobStatus.FAILED) CoralPulseContainer else CyanElectricContainer
                ) {
                    Text(
                        text = "${job.resolution.shortLabel} MP4",
                        style = MonoBadgeTextStyle,
                        color = if (job.status == JobStatus.FAILED) CoralPulse else CyanElectric,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Text(
                    text = job.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (job.isActive) {
                    TextButton(
                        onClick = onCancel,
                        modifier = Modifier.testTag("cancel_job_button_${job.jobId}")
                    ) {
                        Text("Cancel", color = CoralPulse)
                    }
                } else {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = when (job.status) {
                                JobStatus.COMPLETED -> Icons.Rounded.CheckCircle
                                JobStatus.FAILED -> Icons.Rounded.ErrorOutline
                                else -> Icons.Rounded.Close
                            },
                            contentDescription = "Dismiss job",
                            tint = when (job.status) {
                                JobStatus.COMPLETED -> EmeraldReady
                                JobStatus.FAILED -> CoralPulse
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }

            if (job.isActive) {
                LinearProgressIndicator(
                    progress = { job.progress.coerceIn(0.02f, 1f) },
                    color = CyanElectric,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = job.stage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (job.isActive) {
                        "${(job.progress * 100).toInt()}% • ${job.detail}"
                    } else {
                        job.detail
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SavedVideoCard(
    video: DownloadedVideoEntity,
    onPlay: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onPlay)
            .testTag("saved_video_card_${video.id}")
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = CyanElectricContainer,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "Play MP4",
                        tint = CyanElectric,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = "${video.resolutionLabel} MP4",
                            style = MonoBadgeTextStyle,
                            color = CyanElectric,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = Formatters.formatBytes(video.fileSizeBytes),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (video.durationMs > 0L) {
                        Text(
                            text = "• ${Formatters.formatDuration(video.durationMs)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    text = "${UrlStreamClassifier.extractDomain(video.sourcePageUrl)} • ${Formatters.formatDateTime(video.createdAtMillis)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onShare,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("share_video_${video.id}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Share,
                        contentDescription = stringResource(R.string.share_video),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("delete_video_${video.id}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = stringResource(R.string.delete_video),
                        tint = CoralPulse
                    )
                }
            }
        }
    }
}
