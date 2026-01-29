package com.mobile.scrcpy.android.feature.codec.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobile.scrcpy.android.core.common.AppDimens.listItemHeight
import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.manager.LogManager
import com.mobile.scrcpy.android.core.common.manager.TTSManager
import com.mobile.scrcpy.android.core.designsystem.component.AppDivider
import com.mobile.scrcpy.android.core.designsystem.component.DialogBottomSpacer
import com.mobile.scrcpy.android.core.designsystem.component.DialogPage
import com.mobile.scrcpy.android.core.designsystem.component.IOSStyledDropdownMenu
import com.mobile.scrcpy.android.core.designsystem.component.IOSStyledDropdownMenuItem
import com.mobile.scrcpy.android.core.i18n.CodecTexts
import com.mobile.scrcpy.android.core.i18n.CommonTexts
import kotlinx.coroutines.launch

@Composable
fun CodecTestScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    val txtTitle = CodecTexts.CODEC_TEST_TITLE.get()
    val txtDone = CommonTexts.BUTTON_DONE.get()
    val txtTestButton = CodecTexts.CODEC_TEST_BUTTON.get()
    val txtSearchPlaceholder = CodecTexts.CODEC_TEST_SEARCH_PLACEHOLDER.get()
    val txtFoundCount = CodecTexts.CODEC_TEST_FOUND_COUNT.get()
    val txtAudioCodecs = CodecTexts.CODEC_TEST_AUDIO_CODECS.get()
    val txtWarningOpus = CodecTexts.CODEC_TEST_WARNING_OPUS.get()
    val txtInfoCompatibility = CodecTexts.CODEC_TEST_INFO_COMPATIBILITY.get()
    val txtTypeLabel = CodecTexts.CODEC_TEST_TYPE_LABEL.get()
    val txtEncoder = CodecTexts.CODEC_TEST_ENCODER.get()
    val txtDecoder = CodecTexts.CODEC_TEST_DECODER.get()
    val txtFilterAll = CodecTexts.CODEC_TEST_FILTER_ALL.get()
    val txtSampleRate = CodecTexts.CODEC_TEST_SAMPLE_RATE.get()
    val txtMaxChannels = CodecTexts.CODEC_TEST_MAX_CHANNELS.get()
    val txtActual = CodecTexts.CODEC_TEST_ACTUAL.get()
    val txtNoDetails = CodecTexts.CODEC_TEST_NO_DETAILS.get()

    val allCodecs =
        remember {
            getAudioCodecs(txtSampleRate, txtMaxChannels, txtActual, txtNoDetails)
        }

    var searchText by remember { mutableStateOf("") }
    var filterType by remember { mutableStateOf(FilterType.ALL) }
    var codecTypeFilter by remember { mutableStateOf(CodecTypeFilter.ALL) }
    var testingCodec by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (!TTSManager.isReady()) {
            TTSManager.init(context)
            // 等待 TTS 初始化完成（最多 5 秒）
            var waitCount = 0
            while (!TTSManager.isReady() && waitCount < 50) {
                kotlinx.coroutines.delay(100)
                waitCount++
            }
        }
    }

    val filteredCodecs =
        remember(searchText, filterType, codecTypeFilter, allCodecs) {
            allCodecs.filter { codec ->
                val matchesSearch =
                    searchText.isEmpty() ||
                        codec.name.contains(searchText, ignoreCase = true) ||
                        codec.type.contains(searchText, ignoreCase = true)

                val matchesFilter =
                    when (filterType) {
                        FilterType.ALL -> true
                        FilterType.DECODER -> !codec.isEncoder
                        FilterType.ENCODER -> codec.isEncoder
                    }

                val matchesCodecType =
                    when (codecTypeFilter) {
                        CodecTypeFilter.ALL -> true
                        CodecTypeFilter.OPUS -> codec.type == "audio/opus"
                        CodecTypeFilter.AAC -> codec.type == "audio/mp4a-latm"
                        CodecTypeFilter.FLAC -> codec.type == "audio/flac"
                        CodecTypeFilter.RAW -> codec.type == "audio/raw"
                    }

                matchesSearch && matchesFilter && matchesCodecType
            }
        }

    DialogPage(
        title = txtTitle,
        onDismiss = onBack,
        showBackButton = true,
        rightButtonText = txtDone,
        onRightButtonClick = onBack,
        maxHeightRatio = 0.8f,
        horizontalPadding = 10.dp,
        enableScroll = false,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                BasicTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(38.dp)
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp),
                    textStyle =
                        androidx.compose.ui.text.TextStyle(
                            fontSize = 14.sp,
                            lineHeight = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                    singleLine = true,
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (searchText.isEmpty()) {
                                Text(
                                    text = txtSearchPlaceholder,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            innerTextField()
                        }
                    },
                )

                var codecTypeExpanded by remember { mutableStateOf(false) }
                Box {
                    Button(
                        onClick = { codecTypeExpanded = true },
                        modifier =
                            Modifier
                                .widthIn(min = 30.dp, max = 80.dp)
                                .wrapContentWidth()
                                .height(listItemHeight),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = Color(0xFF007AFF),
                            ),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Text(
                            when (codecTypeFilter) {
                                CodecTypeFilter.ALL -> txtFilterAll
                                CodecTypeFilter.OPUS -> "OPUS"
                                CodecTypeFilter.AAC -> "AAC"
                                CodecTypeFilter.FLAC -> "FLAC"
                                CodecTypeFilter.RAW -> "RAW"
                            },
                            fontSize = 13.sp,
                        )
                    }

                    IOSStyledDropdownMenu(
                        expanded = codecTypeExpanded,
                        offset = DpOffset(0.dp, 100.dp),
                        onDismissRequest = { codecTypeExpanded = false },
                    ) {
                        IOSStyledDropdownMenuItem(
                            text = txtFilterAll,
                            onClick = {
                                codecTypeFilter = CodecTypeFilter.ALL
                                codecTypeExpanded = false
                            },
                        )
                        IOSStyledDropdownMenuItem(
                            text = "OPUS",
                            onClick = {
                                codecTypeFilter = CodecTypeFilter.OPUS
                                codecTypeExpanded = false
                            },
                        )
                        IOSStyledDropdownMenuItem(
                            text = "AAC",
                            onClick = {
                                codecTypeFilter = CodecTypeFilter.AAC
                                codecTypeExpanded = false
                            },
                        )
                        IOSStyledDropdownMenuItem(
                            text = "FLAC",
                            onClick = {
                                codecTypeFilter = CodecTypeFilter.FLAC
                                codecTypeExpanded = false
                            },
                        )
                        IOSStyledDropdownMenuItem(
                            text = "RAW",
                            onClick = {
                                codecTypeFilter = CodecTypeFilter.RAW
                                codecTypeExpanded = false
                            },
                        )
                    }
                }

                var expanded by remember { mutableStateOf(false) }
                Box {
                    Button(
                        onClick = { expanded = true },
                        modifier =
                            Modifier
                                .widthIn(min = 30.dp, max = 80.dp)
                                .wrapContentWidth()
                                .height(listItemHeight),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = Color(0xFF007AFF),
                            ),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        contentPadding =
                            androidx.compose.foundation.layout
                                .PaddingValues(0.dp),
                    ) {
                        Text(
                            when (filterType) {
                                FilterType.ALL -> txtFilterAll
                                FilterType.DECODER -> txtDecoder
                                FilterType.ENCODER -> txtEncoder
                            },
                            fontSize = 13.sp,
                        )
                    }

                    IOSStyledDropdownMenu(
                        expanded = expanded,
                        offset = DpOffset(0.dp, 100.dp),
                        onDismissRequest = { expanded = false },
                    ) {
                        IOSStyledDropdownMenuItem(
                            text = txtFilterAll,
                            onClick = {
                                filterType = FilterType.ALL
                                expanded = false
                            },
                        )
                        IOSStyledDropdownMenuItem(
                            text = txtDecoder,
                            onClick = {
                                filterType = FilterType.DECODER
                                expanded = false
                            },
                        )
                        IOSStyledDropdownMenuItem(
                            text = txtEncoder,
                            onClick = {
                                filterType = FilterType.ENCODER
                                expanded = false
                            },
                        )
                    }
                }
            }

            Text(
                "$txtFoundCount ${filteredCodecs.size} $txtAudioCodecs",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFFF3CD), RoundedCornerShape(6.dp))
                        .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    txtWarningOpus,
                    fontSize = 12.sp,
                    color = Color(0xFF856404),
                    lineHeight = 16.sp,
                )
                Text(
                    txtInfoCompatibility,
                    fontSize = 12.sp,
                    color = Color(0xFF856404),
                    lineHeight = 16.sp,
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
        ) {
            filteredCodecs.forEach { codec ->
                Card(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !codec.isEncoder && testingCodec == null) {
                                scope.launch {
                                    testingCodec = codec.name
                                    testAudioDecoder(codec.type, codec.name, TTSManager.getInstance())
                                    testingCodec = null
                                }
                            },
                    colors =
                        CardDefaults.cardColors(
                            containerColor =
                                if (testingCodec ==
                                    codec.name
                                ) {
                                    Color(0xFFE3F2FD)
                                } else {
                                    MaterialTheme.colorScheme.surface
                                },
                        ),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                codec.name,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            if (!codec.isEncoder && testingCodec == null) {
                                Text(
                                    txtTestButton,
                                    fontSize = 12.sp,
                                    color = Color(0xFF007AFF),
                                )
                            } else if (testingCodec == codec.name) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                )
                            }
                        }
                        Text(
                            "$txtTypeLabel: ${codec.type} | ${if (codec.isEncoder) txtEncoder else txtDecoder}",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (codec.capabilities.isNotEmpty()) {
                            Text(
                                codec.capabilities,
                                fontSize = 12.sp,
                                color = Color(0xFF666666),
                            )
                        }

                        if (codec.type == "audio/opus" && !codec.isEncoder) {
                            Text(
                                txtWarningOpus,
                                fontSize = 11.sp,
                                color = Color(0xFFFF6B00),
                            )
                        }
                    }
                }
                AppDivider()
            }
            DialogBottomSpacer()
        }
    }
}
