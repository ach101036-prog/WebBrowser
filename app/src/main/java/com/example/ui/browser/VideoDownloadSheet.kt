package com.example.ui.browser

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.browser.DetectedVideo
import com.example.data.browser.VideoResolution
import com.example.ui.theme.CoralPulse
import com.example.ui.theme.CyanElectric
import com.example.ui.theme.CyanElectricContainer
import com.example.ui.theme.MonoBadgeTextStyle
import com.example.ui.util.Formatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoDownloadSheet(
    detectedVideos: List<DetectedVideo>,
    selectedVideo: DetectedVideo,
    selectedResolution: VideoResolution,
    customFileName: String,
    onSelectVideo: (DetectedVideo) -> Unit,
    onSelectResolution: (VideoResolution) -> Unit,
    onFileNameChange: (String) -> Unit,
    onConfirmDownload: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = CyanElectricContainer
                ) {
                    Icon(
                        imageVector = Icons.Rounded.HighQuality,
                        contentDescription = null,
                        tint = CyanElectric,
                        modifier = Modifier
                            .padding(10.dp)
                            .size(24.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.sheet_title),
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = stringResource(R.string.sheet_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (detectedVideos.size > 1) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.choose_stream),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 2.dp)
                    ) {
                        items(detectedVideos, key = { it.id }) { stream ->
                            val isSelected = stream.id == selectedVideo.id
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = if (isSelected) CyanElectricContainer else MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = if (isSelected) CyanElectric else MaterialTheme.colorScheme.outline
                                ),
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.small)
                                    .clickable { onSelectVideo(stream) }
                                    .testTag("stream_chip_${stream.id}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (stream.isPlaying) {
                                        Icon(
                                            imageVector = Icons.Rounded.PlayArrow,
                                            contentDescription = "Playing",
                                            tint = CoralPulse,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Rounded.Movie,
                                            contentDescription = null,
                                            tint = CyanElectric,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                    Text(
                                        text = "${stream.resolutionBadge} • ${stream.title.take(18)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (isSelected) CyanElectric else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Detected Source Info Summary
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Text(
                            text = selectedVideo.resolutionBadge,
                            style = MonoBadgeTextStyle,
                            color = CyanElectric,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = selectedVideo.formatLabel,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val metaParts = buildList {
                            if (selectedVideo.sourceWidth > 0 && selectedVideo.sourceHeight > 0) {
                                add("${selectedVideo.sourceWidth}×${selectedVideo.sourceHeight}")
                            }
                            if (selectedVideo.durationSec > 0.0) {
                                add(Formatters.formatDuration((selectedVideo.durationSec * 1000).toLong()))
                            }
                            if (selectedVideo.estimatedSizeBytes > 0L) {
                                add(Formatters.formatBytes(selectedVideo.estimatedSizeBytes))
                            }
                        }
                        Text(
                            text = if (metaParts.isNotEmpty()) metaParts.joinToString(" • ") else selectedVideo.url,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            OutlinedTextField(
                value = customFileName,
                onValueChange = onFileNameChange,
                label = { Text(stringResource(R.string.filename_label)) },
                singleLine = true,
                suffix = { Text(".mp4", style = MonoBadgeTextStyle) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("download_filename_input")
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.choose_resolution),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                VideoResolution.entries.forEach { res ->
                    val isSelected = res == selectedResolution
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = if (isSelected) CyanElectricContainer else MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) CyanElectric else MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .clickable { onSelectResolution(res) }
                            .testTag("resolution_option_${res.name}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (isSelected) CyanElectric else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = res.label,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (isSelected) CyanElectric else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = res.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface
                            ) {
                                Text(
                                    text = res.shortLabel,
                                    style = MonoBadgeTextStyle,
                                    color = if (isSelected) CyanElectric else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(2.dp))

            Button(
                onClick = onConfirmDownload,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyanElectric,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("confirm_download_mp4_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Download,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${stringResource(R.string.start_background_download)} (${selectedResolution.shortLabel})",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}
