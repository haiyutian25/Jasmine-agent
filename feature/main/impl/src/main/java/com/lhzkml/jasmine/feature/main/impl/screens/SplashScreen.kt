package com.lhzkml.jasmine.feature.main.impl.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhzkml.jasmine.core.ui.theme.CssVariables
import com.lhzkml.jasmine.feature.main.impl.R
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    currentTheme: CssVariables,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Typewriter state tracking
    val phrases = listOf(
        stringResource(R.string.splash_phrase_1),
        stringResource(R.string.splash_phrase_2),
        stringResource(R.string.splash_phrase_3),
        stringResource(R.string.splash_phrase_4)
    )

    var phraseIndex by remember { mutableIntStateOf(0) }
    var displayedText by remember { mutableStateOf("") }
    var isTypingComplete by remember { mutableStateOf(false) }
    var isAllCompleted by remember { mutableStateOf(false) }

    // Cursor blink animation
    val infiniteTransition = rememberInfiniteTransition(label = "splash_cursor")
    val cursorAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 480, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursor_blink"
    )

    // Pulse & Rotation animations for glyph ring
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glyph_pulse"
    )

    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring_rotation"
    )

    // Typewriter engine loop
    LaunchedEffect(Unit) {
        delay(350)
        for (i in phrases.indices) {
            phraseIndex = i
            val currentPhrase = phrases[i]
            displayedText = ""
            isTypingComplete = false

            // Type each char with slight humanized cadence
            for (charIndex in 1..currentPhrase.length) {
                displayedText = currentPhrase.substring(0, charIndex)
                val typingDelay = if (i == phrases.lastIndex) 42L else 28L
                delay(typingDelay)
            }
            isTypingComplete = true

            // Pause between phrases (longer pause on the last phrase before auto-completion)
            if (i < phrases.lastIndex) {
                delay(400)
            } else {
                isAllCompleted = true
                delay(850)
                onFinish()
            }
        }
    }

    val progressValue by animateFloatAsState(
        targetValue = (phraseIndex + 1f) / phrases.size.toFloat(),
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "splash_progress"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(currentTheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .drawBehind {
                // Subtle ambient radial glow behind central glyph
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            currentTheme.primary.copy(alpha = 0.08f),
                            Color.Transparent
                        ),
                        center = Offset(size.width / 2f, size.height * 0.38f),
                        radius = size.width * 0.65f
                    )
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Skip Button in top-right corner
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 18.dp, end = 20.dp)
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(currentTheme.radiusSm))
                    .background(currentTheme.subtleSurface)
                    .border(1.dp, currentTheme.border, RoundedCornerShape(currentTheme.radiusSm))
                    .clickable { onFinish() }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .testTag("splash_skip_btn"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = if (isAllCompleted) stringResource(R.string.splash_enter) else stringResource(R.string.splash_skip),
                    fontSize = 11.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    color = currentTheme.mutedForeground
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = stringResource(R.string.splash_cd_skip),
                    tint = currentTheme.mutedForeground,
                    modifier = Modifier.size(12.dp)
                )
            }
        }

        // Center Content Block
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .widthIn(max = 480.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Dynamic Morphing Glyph & Orbiting Dashed Ring
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .scale(pulseScale),
                contentAlignment = Alignment.Center
            ) {
                // Outer Orbiting Decorative Gradient Ring
                Box(
                    modifier = Modifier
                        .size(92.dp)
                        .rotate(rotationAngle)
                        .border(
                            width = 1.5.dp,
                            brush = Brush.sweepGradient(
                                colors = listOf(
                                    currentTheme.primary,
                                    currentTheme.primary.copy(alpha = 0.1f),
                                    currentTheme.primary.copy(alpha = 0.6f),
                                    currentTheme.primary
                                )
                            ),
                            shape = CircleShape
                        )
                )

                // Central Monogram Base
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(RoundedCornerShape(currentTheme.radiusMd))
                        .background(currentTheme.card)
                        .border(1.dp, currentTheme.border, RoundedCornerShape(currentTheme.radiusMd)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "HW",
                        fontFamily = FontFamily.Serif,
                        fontStyle = FontStyle.Italic,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                        color = currentTheme.foreground
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // 2. Micro Tag / Subtitle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(currentTheme.radiusSm))
                    .background(currentTheme.subtleSurface)
                    .border(1.dp, currentTheme.border, RoundedCornerShape(currentTheme.radiusSm))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Terminal,
                    contentDescription = null,
                    tint = currentTheme.primary,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = stringResource(R.string.splash_bootloader),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = currentTheme.mutedForeground,
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 3. Main Dynamic Typewriter Display Terminal
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(currentTheme.radiusMd))
                    .background(currentTheme.card)
                    .border(1.dp, currentTheme.border, RoundedCornerShape(currentTheme.radiusMd))
                    .padding(horizontal = 20.dp, vertical = 22.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.splash_phase_format, phraseIndex + 1, phrases.size),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = currentTheme.primary,
                            letterSpacing = 0.8.sp
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    // Token-purist status dot: typing = dimmed accent,
                                    // complete = solid accent (no off-palette green).
                                    .background(
                                        if (isTypingComplete) currentTheme.primary
                                        else currentTheme.primary.copy(alpha = 0.45f)
                                    )
                            )
                            Text(
                                text = if (phraseIndex == phrases.lastIndex) stringResource(R.string.splash_ready) else stringResource(R.string.splash_exec),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = currentTheme.mutedForeground
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Typewriter Line with blinking cursor
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.height(34.dp)
                    ) {
                        if (phraseIndex == phrases.lastIndex) {
                            // High-craft Serif for the final brand phrase
                            Text(
                                text = displayedText,
                                fontFamily = FontFamily.Serif,
                                fontStyle = FontStyle.Italic,
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp,
                                color = currentTheme.foreground
                            )
                        } else {
                            Text(
                                text = displayedText,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp,
                                color = currentTheme.foreground,
                                lineHeight = 20.sp
                            )
                        }

                        // Blinking Terminal Block Cursor
                        Text(
                            text = "▌",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = if (phraseIndex == phrases.lastIndex) 20.sp else 14.sp,
                            color = currentTheme.primary,
                            modifier = Modifier
                                .padding(start = 3.dp)
                                .alpha(cursorAlpha)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Minimal Step Progress Bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(CircleShape)
                            .background(currentTheme.subtleSurface)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progressValue)
                                .height(3.dp)
                                .clip(CircleShape)
                                .background(currentTheme.primary)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // 4. Subtle Bottom Prompt Button / Status
            AnimatedVisibility(
                visible = isAllCompleted,
                enter = fadeIn(tween(300)),
                exit = fadeOut(tween(150))
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(currentTheme.radiusSm))
                        .background(currentTheme.primary)
                        .clickable { onFinish() }
                        .padding(horizontal = 22.dp, vertical = 10.dp)
                        .testTag("splash_enter_btn"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = currentTheme.primaryForeground,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stringResource(R.string.splash_launch_canvas),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = currentTheme.primaryForeground
                    )
                }
            }
        }
    }
}
