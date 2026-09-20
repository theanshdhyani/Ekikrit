package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.util.LocalAppStrings
import com.example.ui.util.TtsPlayState

@Composable
fun VoiceAssistBanner(
    isVoiceAssistEnabled: Boolean,
    onToggleVoiceAssist: (Boolean) -> Unit,
    playState: TtsPlayState = TtsPlayState.IDLE,
    spokenNarration: String = "",
    onPlayNarration: () -> Unit = {},
    onStopNarration: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val isPlaying = playState == TtsPlayState.PLAYING
    val defaultNarration = spokenNarration.ifBlank { strings.voiceAssistSummaryDefault }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("voice_assist_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isVoiceAssistEnabled) Color(0xFFFEF3C7) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = BorderStroke(
            1.5.dp,
            if (isVoiceAssistEnabled) Color(0xFFD97706) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .semantics {
                            contentDescription = strings.voiceAssistTitle
                        }
                ) {
                    Surface(
                        color = if (isVoiceAssistEnabled) Color(0xFFD97706) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                        shape = CircleShape,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isVoiceAssistEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeMute,
                                contentDescription = null,
                                tint = if (isVoiceAssistEnabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = strings.voiceAssistTitle,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isVoiceAssistEnabled) Color(0xFF92400E) else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isVoiceAssistEnabled) strings.voiceAssistActiveSubtitle else strings.voiceAssistTapToEnable,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isVoiceAssistEnabled) Color(0xFFB45309) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Switch(
                    checked = isVoiceAssistEnabled,
                    onCheckedChange = onToggleVoiceAssist,
                    modifier = Modifier
                        .testTag("voice_assist_switch")
                        .semantics { role = Role.Switch }
                )
            }

            AnimatedVisibility(
                visible = isVoiceAssistEnabled,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFFFFBEB))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                if (isPlaying) {
                                    onStopNarration()
                                } else {
                                    onPlayNarration()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isPlaying) Color(0xFF059669) else Color(0xFFD97706)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .semantics {
                                    contentDescription = if (isPlaying) strings.voiceAssistPause else strings.voiceAssistListenAloud
                                }
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isPlaying) strings.voiceAssistPlaying else strings.voiceAssistListenAloud,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        if (isPlaying) {
                            VoiceWaveform(modifier = Modifier.weight(1f))
                        } else {
                            Text(
                                text = strings.voiceAssistSpeaksDescription,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF78350F),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Text(
                        text = "🔊 \"$defaultNarration\"",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF92400E)
                    )
                }
            }
        }
    }
}

@Composable
fun VoiceWaveform(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")
    val heights = (0..4).map { index ->
        infiniteTransition.animateFloat(
            initialValue = 6f,
            targetValue = 24f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 350 + (index * 80), easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "wave_$index"
        )
    }

    Row(
        modifier = modifier.height(30.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        heights.forEach { heightAnim ->
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(heightAnim.value.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF059669))
            )
        }
    }
}
