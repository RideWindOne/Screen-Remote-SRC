package com.screen.remote.android.feature.session.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.screen.remote.android.R
import com.screen.remote.android.core.common.constants.AppColors
import com.screen.remote.android.core.i18n.SessionTexts
import com.screen.remote.android.core.i18n.TextPair

internal data class WelcomeFeature(
    val icon: ImageVector,
    val title: String,
    val body: String,
)

internal data class WelcomePage(
    val icon: ImageVector,
    val eyebrow: String,
    val title: String,
    val description: String,
    val accent: Color,
    val features: List<WelcomeFeature>,
)

private fun onboardingText(text: TextPair): String = text.get()

@Composable
fun SessionOnboardingBackground(
    showLogo: Boolean,
    modifier: Modifier = Modifier,
) {
    val iconScale = remember { Animatable(0.86f) }
    val iconAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        iconAlpha.animateTo(1f, animationSpec = tween(durationMillis = 260))
    }
    LaunchedEffect(Unit) {
        iconScale.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing),
        )
    }

    Box(
        modifier = modifier.background(onboardingBackgroundColor()),
        contentAlignment = Alignment.Center,
    ) {
        if (showLogo) {
            Image(
                painter = painterResource(R.mipmap.ic_launcher),
                contentDescription = null,
                modifier =
                    Modifier
                        .size(92.dp)
                        .graphicsLayer {
                            scaleX = iconScale.value
                            scaleY = iconScale.value
                            alpha = iconAlpha.value
                        }.clip(RoundedCornerShape(22.dp)),
            )
        }
    }
}

@Composable
fun FirstSessionWelcomeCard(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val pages = remember(isDark) { welcomePages(isDark) }
    SessionOnboardingPager(
        pages = pages,
        actionText = onboardingText(SessionTexts.ONBOARDING_SKIP),
        swipeHint = onboardingText(SessionTexts.ONBOARDING_SWIPE_HINT),
        onDismiss = onDismiss,
        modifier = modifier,
    )
}

@Composable
internal fun SessionOnboardingPager(
    pages: List<WelcomePage>,
    actionText: String,
    swipeHint: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val pagerState = rememberPagerState(pageCount = pages::size)

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent()
                        }
                    }
                }
                .safeDrawingPadding()
                .padding(vertical = 12.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .fillMaxHeight(0.66f)
                        .heightIn(min = 320.dp),
                pageSpacing = 0.dp,
                beyondViewportPageCount = 1,
            ) { pageIndex ->
                WelcomePageCard(
                    page = pages[pageIndex],
                    pageNumber = pageIndex + 1,
                    pageCount = pages.size,
                    isDark = isDark,
                )
            }
        }

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, top = 8.dp, end = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                pages.indices.forEach { index ->
                    val selected = index == pagerState.currentPage
                    Box(
                        modifier =
                            Modifier
                                .size(width = if (selected) 22.dp else 7.dp, height = 7.dp)
                                .background(
                                    color =
                                        if (selected) {
                                            pages[pagerState.currentPage].accent
                                        } else {
                                            MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                                        },
                                    shape = CircleShape,
                                ),
                    )
                }
            }
            Text(
                text = swipeHint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 6.dp),
            )
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary),
            ) {
                Text(actionText, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun WelcomePageCard(
    page: WelcomePage,
    pageNumber: Int,
    pageCount: Int,
    isDark: Boolean,
) {
    Card(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 8.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = onboardingCardColor()),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            WelcomeSparkles(
                accent = page.accent,
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 76.dp, end = 14.dp),
            )

            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 22.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = page.accent.copy(alpha = if (isDark) 0.20f else 0.12f),
                    ) {
                        Icon(
                            imageVector = page.icon,
                            contentDescription = null,
                            tint = page.accent,
                            modifier = Modifier.padding(14.dp).size(30.dp),
                        )
                    }
                    Text(
                        text = "$pageNumber / $pageCount",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(18.dp))
                Text(
                    text = page.eyebrow,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = page.accent,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    text = page.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = page.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(20.dp))

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    page.features.forEach { feature ->
                        WelcomeFeatureRow(
                            feature = feature,
                            accent = page.accent,
                            isDark = isDark,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun onboardingBackgroundColor(): Color =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        AppColors.darkIOSSelectedBackground
    } else {
        AppColors.iOSSelectedBackground
    }

@Composable
private fun onboardingCardColor(): Color =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        AppColors.darkCard
    } else {
        Color(0xFFFDFDFD)
    }

@Composable
private fun WelcomeSparkles(
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .size(82.dp)
                .graphicsLayer { alpha = 0.46f },
    ) {
        Icon(
            imageVector = Icons.Default.AutoAwesome,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.align(Alignment.TopEnd).size(30.dp),
        )
        Box(
            modifier =
                Modifier
                    .align(Alignment.CenterStart)
                    .size(10.dp)
                    .background(accent.copy(alpha = 0.75f), CircleShape),
        )
        Icon(
            imageVector = Icons.Default.AutoAwesome,
            contentDescription = null,
            tint = accent.copy(alpha = 0.75f),
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .offset(x = 8.dp)
                    .size(17.dp),
        )
        Box(
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .offset(x = (-9).dp, y = 8.dp)
                    .size(5.dp)
                    .background(accent, CircleShape),
        )
    }
}

@Composable
private fun WelcomeFeatureRow(
    feature: WelcomeFeature,
    accent: Color,
    isDark: Boolean,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = accent.copy(alpha = if (isDark) 0.16f else 0.09f),
        ) {
            Icon(
                imageVector = feature.icon,
                contentDescription = null,
                modifier = Modifier.padding(9.dp).size(19.dp),
                tint = accent,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = feature.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = feature.body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun welcomePages(isDark: Boolean): List<WelcomePage> {
    val blue = if (isDark) Color(0xFF0A84FF) else Color(0xFF007AFF)
    val teal = if (isDark) Color(0xFF40C8E0) else Color(0xFF30B0C7)
    val green = if (isDark) Color(0xFF30D158) else Color(0xFF28A745)
    val purple = if (isDark) Color(0xFFBF5AF2) else Color(0xFFAF52DE)
    val orange = if (isDark) Color(0xFFFF9F0A) else Color(0xFFFF9500)

    return listOf(
        WelcomePage(
            icon = Icons.Default.Tune,
            eyebrow = onboardingText(SessionTexts.ONBOARDING_SESSION_EYEBROW),
            title = onboardingText(SessionTexts.ONBOARDING_SESSION_TITLE),
            description = onboardingText(SessionTexts.ONBOARDING_SESSION_DESCRIPTION),
            accent = blue,
            features =
                listOf(
                    WelcomeFeature(Icons.Default.HighQuality, onboardingText(SessionTexts.ONBOARDING_RESOLUTION_TITLE), onboardingText(SessionTexts.ONBOARDING_RESOLUTION_BODY)),
                    WelcomeFeature(Icons.Default.Speed, onboardingText(SessionTexts.ONBOARDING_FPS_TITLE), onboardingText(SessionTexts.ONBOARDING_FPS_BODY)),
                    WelcomeFeature(Icons.Default.Devices, onboardingText(SessionTexts.ONBOARDING_CODEC_TITLE), onboardingText(SessionTexts.ONBOARDING_CODEC_BODY)),
                ),
        ),
        WelcomePage(
            icon = Icons.Default.Wifi,
            eyebrow = onboardingText(SessionTexts.ONBOARDING_WIRELESS_EYEBROW),
            title = onboardingText(SessionTexts.ONBOARDING_WIRELESS_TITLE),
            description = onboardingText(SessionTexts.ONBOARDING_WIRELESS_DESCRIPTION),
            accent = teal,
            features =
                listOf(
                    WelcomeFeature(Icons.Default.Key, onboardingText(SessionTexts.ONBOARDING_PAIRING_TITLE), onboardingText(SessionTexts.ONBOARDING_PAIRING_BODY)),
                    WelcomeFeature(Icons.Default.Sensors, onboardingText(SessionTexts.ONBOARDING_DISCOVERY_TITLE), onboardingText(SessionTexts.ONBOARDING_DISCOVERY_BODY)),
                    WelcomeFeature(Icons.AutoMirrored.Filled.AltRoute, onboardingText(SessionTexts.ONBOARDING_ENDPOINTS_TITLE), onboardingText(SessionTexts.ONBOARDING_ENDPOINTS_BODY)),
                ),
        ),
        WelcomePage(
            icon = Icons.Default.Key,
            eyebrow = onboardingText(SessionTexts.ONBOARDING_PAIRING_GUIDE_EYEBROW),
            title = onboardingText(SessionTexts.ONBOARDING_PAIRING_GUIDE_TITLE),
            description = onboardingText(SessionTexts.ONBOARDING_PAIRING_GUIDE_DESCRIPTION),
            accent = teal,
            features =
                listOf(
                    WelcomeFeature(Icons.Default.Tune, onboardingText(SessionTexts.ONBOARDING_ENABLE_DEVELOPER_OPTIONS_TITLE), onboardingText(SessionTexts.ONBOARDING_ENABLE_DEVELOPER_OPTIONS_BODY)),
                    WelcomeFeature(Icons.Default.Wifi, onboardingText(SessionTexts.ONBOARDING_OPEN_WIRELESS_DEBUGGING_TITLE), onboardingText(SessionTexts.ONBOARDING_OPEN_WIRELESS_DEBUGGING_BODY)),
                    WelcomeFeature(Icons.Default.Key, onboardingText(SessionTexts.ONBOARDING_USE_PAIRING_CODE_TITLE), onboardingText(SessionTexts.ONBOARDING_USE_PAIRING_CODE_BODY)),
                    WelcomeFeature(Icons.Default.Devices, onboardingText(SessionTexts.ONBOARDING_ADD_WIRELESS_SESSION_TITLE), onboardingText(SessionTexts.ONBOARDING_ADD_WIRELESS_SESSION_BODY)),
                ),
        ),
        WelcomePage(
            icon = Icons.Default.SportsEsports,
            eyebrow = onboardingText(SessionTexts.ONBOARDING_ADVANCED_EYEBROW),
            title = onboardingText(SessionTexts.ONBOARDING_ADVANCED_TITLE),
            description = onboardingText(SessionTexts.ONBOARDING_ADVANCED_DESCRIPTION),
            accent = green,
            features =
                listOf(
                    WelcomeFeature(Icons.AutoMirrored.Filled.AltRoute, onboardingText(SessionTexts.ONBOARDING_PORT_FORWARD_TITLE), onboardingText(SessionTexts.ONBOARDING_PORT_FORWARD_BODY)),
                    WelcomeFeature(Icons.Default.DesktopWindows, onboardingText(SessionTexts.ONBOARDING_VIRTUAL_DISPLAY_TITLE), onboardingText(SessionTexts.ONBOARDING_VIRTUAL_DISPLAY_BODY)),
                    WelcomeFeature(Icons.Default.SportsEsports, onboardingText(SessionTexts.ONBOARDING_GAME_MODE_TITLE), onboardingText(SessionTexts.ONBOARDING_GAME_MODE_BODY)),
                ),
        ),
        WelcomePage(
            icon = Icons.Default.Devices,
            eyebrow = onboardingText(SessionTexts.ONBOARDING_MANAGE_EYEBROW),
            title = onboardingText(SessionTexts.ONBOARDING_MANAGE_TITLE),
            description = onboardingText(SessionTexts.ONBOARDING_MANAGE_DESCRIPTION),
            accent = purple,
            features =
                listOf(
                    WelcomeFeature(Icons.Default.Apps, onboardingText(SessionTexts.ONBOARDING_APPS_TITLE), onboardingText(SessionTexts.ONBOARDING_APPS_BODY)),
                    WelcomeFeature(Icons.Default.Folder, onboardingText(SessionTexts.ONBOARDING_FILES_TITLE), onboardingText(SessionTexts.ONBOARDING_FILES_BODY)),
                    WelcomeFeature(Icons.Default.Terminal, onboardingText(SessionTexts.ONBOARDING_SHELL_TITLE), onboardingText(SessionTexts.ONBOARDING_SHELL_BODY)),
                ),
        ),
        WelcomePage(
            icon = Icons.Default.Backup,
            eyebrow = onboardingText(SessionTexts.ONBOARDING_BACKUP_EYEBROW),
            title = onboardingText(SessionTexts.ONBOARDING_BACKUP_TITLE),
            description = onboardingText(SessionTexts.ONBOARDING_BACKUP_DESCRIPTION),
            accent = orange,
            features =
                listOf(
                    WelcomeFeature(Icons.Default.Tune, onboardingText(SessionTexts.ONBOARDING_CONFIG_TITLE), onboardingText(SessionTexts.ONBOARDING_CONFIG_BODY)),
                    WelcomeFeature(Icons.Default.Key, onboardingText(SessionTexts.ONBOARDING_KEYS_TITLE), onboardingText(SessionTexts.ONBOARDING_KEYS_BODY)),
                    WelcomeFeature(Icons.Default.Restore, onboardingText(SessionTexts.ONBOARDING_RESTORE_TITLE), onboardingText(SessionTexts.ONBOARDING_RESTORE_BODY)),
                ),
        ),
    )
}
