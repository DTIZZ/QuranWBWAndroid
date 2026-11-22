package com.quranwbw.android.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quranwbw.android.model.Surah
import com.quranwbw.android.speedread.FocusedReadMode
import com.quranwbw.android.speedread.SpeedReadTheme
import com.quranwbw.android.speedread.SpeedReadToken
import com.quranwbw.android.speedread.SpeedReadTokenizer
import com.quranwbw.android.speedread.WordByWordToken
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

data class SpeedReadPalette(
    val background: Color,
    val stageBackground: Color,
    val stageBorder: Color,
    val wordText: Color,
    val focalAccent: Color,
    val mutedText: Color,
)

fun SpeedReadTheme.palette(): SpeedReadPalette = when (this) {
    SpeedReadTheme.WARM_SEPIA -> SpeedReadPalette(
        background = Color(0xFFF4ECD8),
        stageBackground = Color(0xFFEBE0C5),
        stageBorder = Color(0xFFD5C5A3),
        wordText = Color(0xFF33261E),
        focalAccent = Color(0xFFB85219),
        mutedText = Color(0xFF6E5A4A),
    )
    SpeedReadTheme.AMBER_NIGHT -> SpeedReadPalette(
        background = Color(0xFF121110),
        stageBackground = Color(0xFF1C1916),
        stageBorder = Color(0xFF332C23),
        wordText = Color(0xFFE6D5B8),
        focalAccent = Color(0xFFE5A93C),
        mutedText = Color(0xFF9C8D76),
    )
    SpeedReadTheme.FOREST_SAGE -> SpeedReadPalette(
        background = Color(0xFF131F1A),
        stageBackground = Color(0xFF1B2B24),
        stageBorder = Color(0xFF2D463B),
        wordText = Color(0xFFD8ECE2),
        focalAccent = Color(0xFF63D497),
        mutedText = Color(0xFF8AA899),
    )
    SpeedReadTheme.SOFT_PAPER -> SpeedReadPalette(
        background = Color(0xFFF7F5F0),
        stageBackground = Color(0xFFECE9E1),
        stageBorder = Color(0xFFD4CFC3),
        wordText = Color(0xFF242424),
        focalAccent = Color(0xFF196B52),
        mutedText = Color(0xFF62605B),
    )
    SpeedReadTheme.SLATE_DUSK -> SpeedReadPalette(
        background = Color(0xFF19202A),
        stageBackground = Color(0xFF222B38),
        stageBorder = Color(0xFF344256),
        wordText = Color(0xFFDCE4F0),
        focalAccent = Color(0xFF7EB6FF),
        mutedText = Color(0xFF8E9BAE),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuranSpeedReader(
    surah: Surah,
    initialAyahNumber: Int,
    wpm: Int,
    theme: SpeedReadTheme,
    focusedReadMode: FocusedReadMode,
    onWpmChange: (Int) -> Unit,
    onThemeChange: (SpeedReadTheme) -> Unit,
    onModeChange: (FocusedReadMode) -> Unit,
    onPlayingChanged: (Boolean) -> Unit,
    onAyahCompleted: (ayahNumber: Int, dwellMs: Long) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val palette = theme.palette()
    val ayahs = surah.ayahs
    if (ayahs.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(palette.background),
            contentAlignment = Alignment.Center,
        ) {
            Text("Loading verses…", color = palette.mutedText)
        }
        return
    }

    var currentAyahIndex by rememberSaveable(surah.number) {
        val idx = ayahs.indexOfFirst { it.number >= initialAyahNumber }.coerceAtLeast(0)
        mutableIntStateOf(idx)
    }
    val safeAyahIndex = currentAyahIndex.coerceIn(0, ayahs.lastIndex)
    val currentAyah = ayahs[safeAyahIndex]

    val allEnTokens = remember(surah.number, ayahs) {
        ayahs.map { SpeedReadTokenizer.tokenizeAyahEnglish(it) }
    }
    val allWbwTokens = remember(surah.number, ayahs) {
        ayahs.map { SpeedReadTokenizer.tokenizeAyahWbw(it) }
    }

    val enTokens = allEnTokens[safeAyahIndex]
    val wbwTokens = allWbwTokens[safeAyahIndex]

    val isArabicWbw = focusedReadMode == FocusedReadMode.ARABIC_WBW
    val totalTokensCount = if (isArabicWbw) wbwTokens.size else enTokens.size

    var currentWordIndex by rememberSaveable(surah.number, currentAyah.number, focusedReadMode) {
        mutableIntStateOf(0)
    }
    val safeWordIndex = if (totalTokensCount == 0) 0 else currentWordIndex.coerceIn(0, totalTokensCount - 1)
    val currentEnToken: SpeedReadToken? = if (!isArabicWbw) enTokens.getOrNull(safeWordIndex) else null
    val currentWbwToken: WordByWordToken? = if (isArabicWbw) wbwTokens.getOrNull(safeWordIndex) else null

    val totalSurahMs = remember(allEnTokens, allWbwTokens, wpm, isArabicWbw) {
        if (isArabicWbw) {
            SpeedReadTokenizer.estimateWbwDurationMs(allWbwTokens, wpm)
        } else {
            SpeedReadTokenizer.estimateEnglishDurationMs(allEnTokens, wpm)
        }
    }
    val remainingSurahMs = remember(allEnTokens, allWbwTokens, wpm, isArabicWbw, safeAyahIndex, safeWordIndex) {
        if (isArabicWbw) {
            SpeedReadTokenizer.estimateWbwDurationMs(allWbwTokens, wpm, safeAyahIndex, safeWordIndex)
        } else {
            SpeedReadTokenizer.estimateEnglishDurationMs(allEnTokens, wpm, safeAyahIndex, safeWordIndex)
        }
    }
    val remainingLabel = SpeedReadTokenizer.formatEstimatedDuration(remainingSurahMs)
    val totalLabel = SpeedReadTokenizer.formatEstimatedDuration(totalSurahMs)

    var isPlaying by rememberSaveable(surah.number) { mutableStateOf(false) }
    var ayahElapsedMs by rememberSaveable(surah.number, currentAyah.number, focusedReadMode) { mutableLongStateOf(0L) }

    LaunchedEffect(isPlaying) {
        onPlayingChanged(isPlaying)
    }

    LaunchedEffect(isPlaying, safeAyahIndex, safeWordIndex, wpm, focusedReadMode, totalTokensCount) {
        if (!isPlaying || totalTokensCount == 0) return@LaunchedEffect
        val delayMs = if (isArabicWbw) {
            SpeedReadTokenizer.delayMillisForWbw(wbwTokens[safeWordIndex], wpm)
        } else {
            SpeedReadTokenizer.delayMillisFor(enTokens[safeWordIndex], wpm)
        }
        delay(delayMs)
        ayahElapsedMs += delayMs
        if (safeWordIndex < totalTokensCount - 1) {
            currentWordIndex = safeWordIndex + 1
        } else {
            onAyahCompleted(currentAyah.number, ayahElapsedMs)
            if (safeAyahIndex < ayahs.lastIndex) {
                currentAyahIndex = safeAyahIndex + 1
                currentWordIndex = 0
                ayahElapsedMs = 0L
            } else {
                isPlaying = false
            }
        }
    }

    val surahProgress = (safeAyahIndex + 1).toFloat() / ayahs.size.toFloat()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Header bar: Ayah number, word progress, and ETA
        Column {
            Text(
                text = "Ayah ${surah.number}:${currentAyah.number}  (${safeAyahIndex + 1} of ${ayahs.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = palette.wordText,
            )
            Text(
                text = if (totalTokensCount > 0) {
                    "Word ${safeWordIndex + 1} of $totalTokensCount · $wpm WPM"
                } else {
                    "$wpm WPM"
                },
                style = MaterialTheme.typography.bodySmall,
                color = palette.mutedText,
            )
            Text(
                text = if (safeAyahIndex == 0 && safeWordIndex == 0) {
                    "Est. Surah time: ~$totalLabel"
                } else {
                    "~$remainingLabel left (of ~$totalLabel total)"
                },
                style = MaterialTheme.typography.labelSmall,
                color = palette.focalAccent,
                fontWeight = FontWeight.Medium,
            )
        }

        LinearProgressIndicator(
            progress = { surahProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = palette.focalAccent,
            trackColor = palette.stageBorder,
        )

        // Reading Mode Selector: Word-by-Word Arabic + English vs. English Words (ORP)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FocusedReadMode.entries.forEach { mode ->
                val selected = mode == focusedReadMode
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) palette.focalAccent.copy(alpha = 0.16f) else palette.stageBackground)
                        .border(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) palette.focalAccent else palette.stageBorder,
                            shape = RoundedCornerShape(12.dp),
                        )
                        .clickable {
                            isPlaying = false
                            onModeChange(mode)
                        }
                        .padding(vertical = 10.dp, horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = mode.label,
                        color = if (selected) palette.wordText else palette.mutedText,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    )
                }
            }
        }

        // Single-word Focused Reading Stage
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(215.dp)
                .clickable { isPlaying = !isPlaying },
            shape = RoundedCornerShape(20.dp),
            color = palette.stageBackground,
            border = BorderStroke(1.5.dp, palette.stageBorder),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
            ) {
                // Top focal guide tick
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .width(2.dp)
                        .height(12.dp)
                        .background(palette.focalAccent.copy(alpha = 0.55f)),
                )

                if (isArabicWbw && currentWbwToken != null) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                            Text(
                                text = currentWbwToken.arabicWord,
                                fontSize = 48.sp,
                                lineHeight = 60.sp,
                                color = palette.wordText,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        if (currentWbwToken.transliteration.isNotEmpty()) {
                            Text(
                                text = currentWbwToken.transliteration,
                                style = MaterialTheme.typography.bodyMedium,
                                color = palette.mutedText,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Text(
                            text = currentWbwToken.englishGloss,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.focalAccent,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                } else if (!isArabicWbw && currentEnToken != null) {
                    val word = currentEnToken.word
                    val orp = currentEnToken.orpIndex.coerceIn(0, (word.length - 1).coerceAtLeast(0))
                    val annotatedWord = buildAnnotatedString {
                        if (word.isNotEmpty()) {
                            append(word.substring(0, orp))
                            withStyle(SpanStyle(color = palette.focalAccent, fontWeight = FontWeight.Bold)) {
                                append(word[orp].toString())
                            }
                            if (orp + 1 < word.length) {
                                append(word.substring(orp + 1))
                            }
                        }
                    }
                    Text(
                        text = annotatedWord,
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Medium,
                        color = palette.wordText,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth(),
                    )
                } else {
                    Text(
                        text = "—",
                        fontSize = 36.sp,
                        color = palette.mutedText,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                // Bottom focal guide tick
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .width(2.dp)
                        .height(12.dp)
                        .background(palette.focalAccent.copy(alpha = 0.55f)),
                )
            }
        }

        // Playback Controls: Prev Ayah | -1 Word | Play/Pause | +1 Word | Next Ayah
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = {
                    isPlaying = false
                    if (safeAyahIndex > 0) {
                        currentAyahIndex = safeAyahIndex - 1
                        currentWordIndex = 0
                    } else {
                        currentWordIndex = 0
                    }
                },
                enabled = safeAyahIndex > 0 || safeWordIndex > 0,
            ) {
                Icon(
                    Icons.Filled.SkipPrevious,
                    contentDescription = "Previous ayah",
                    tint = palette.wordText,
                )
            }

            IconButton(
                onClick = {
                    isPlaying = false
                    if (safeWordIndex > 0) {
                        currentWordIndex = safeWordIndex - 1
                    } else if (safeAyahIndex > 0) {
                        currentAyahIndex = safeAyahIndex - 1
                        currentWordIndex = 0
                    }
                },
                enabled = safeWordIndex > 0 || safeAyahIndex > 0,
            ) {
                Icon(
                    Icons.Filled.FastRewind,
                    contentDescription = "Previous word",
                    tint = palette.wordText,
                )
            }

            Button(
                onClick = {
                    if (!isPlaying && safeAyahIndex == ayahs.lastIndex && safeWordIndex >= totalTokensCount - 1) {
                        currentWordIndex = 0
                    }
                    isPlaying = !isPlaying
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.focalAccent,
                    contentColor = Color.White,
                ),
                shape = CircleShape,
                modifier = Modifier.height(52.dp),
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (isPlaying) "Pause" else "Play", fontWeight = FontWeight.SemiBold)
            }

            IconButton(
                onClick = {
                    isPlaying = false
                    if (safeWordIndex < totalTokensCount - 1) {
                        currentWordIndex = safeWordIndex + 1
                    } else if (safeAyahIndex < ayahs.lastIndex) {
                        currentAyahIndex = safeAyahIndex + 1
                        currentWordIndex = 0
                    }
                },
                enabled = safeWordIndex < totalTokensCount - 1 || safeAyahIndex < ayahs.lastIndex,
            ) {
                Icon(
                    Icons.Filled.FastForward,
                    contentDescription = "Next word",
                    tint = palette.wordText,
                )
            }

            IconButton(
                onClick = {
                    isPlaying = false
                    if (safeAyahIndex < ayahs.lastIndex) {
                        currentAyahIndex = safeAyahIndex + 1
                        currentWordIndex = 0
                    }
                },
                enabled = safeAyahIndex < ayahs.lastIndex,
            ) {
                Icon(
                    Icons.Filled.SkipNext,
                    contentDescription = "Next ayah",
                    tint = palette.wordText,
                )
            }
        }

        // Speed (Words Per Minute) Control Card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = palette.stageBackground,
            border = BorderStroke(1.dp, palette.stageBorder),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Pace: $wpm words / min",
                        style = MaterialTheme.typography.titleSmall,
                        color = palette.wordText,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = {
                                onWpmChange((wpm - 25).coerceAtLeast(SpeedReadTokenizer.MIN_SPEED_WPM))
                            },
                            border = BorderStroke(1.dp, palette.stageBorder),
                        ) {
                            Text("-25", color = palette.wordText)
                        }
                        OutlinedButton(
                            onClick = {
                                onWpmChange((wpm + 25).coerceAtMost(SpeedReadTokenizer.MAX_SPEED_WPM))
                            },
                            border = BorderStroke(1.dp, palette.stageBorder),
                        ) {
                            Text("+25", color = palette.wordText)
                        }
                    }
                }

                Slider(
                    value = wpm.toFloat(),
                    onValueChange = { v ->
                        val stepped = ((v / 10f).roundToInt() * 10)
                            .coerceIn(SpeedReadTokenizer.MIN_SPEED_WPM, SpeedReadTokenizer.MAX_SPEED_WPM)
                        onWpmChange(stepped)
                    },
                    valueRange = SpeedReadTokenizer.MIN_SPEED_WPM.toFloat()..SpeedReadTokenizer.MAX_SPEED_WPM.toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = palette.focalAccent,
                        activeTrackColor = palette.focalAccent,
                        inactiveTrackColor = palette.stageBorder,
                    ),
                )
            }
        }

        // Eye-friendly Color Combination Selector
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = palette.stageBackground,
            border = BorderStroke(1.dp, palette.stageBorder),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Eye-comfort color theme",
                    style = MaterialTheme.typography.titleSmall,
                    color = palette.wordText,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SpeedReadTheme.entries.forEach { candidate ->
                        val p = candidate.palette()
                        val selected = candidate == theme
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(p.background)
                                .border(
                                    width = if (selected) 2.5.dp else 1.dp,
                                    color = if (selected) p.focalAccent else p.stageBorder,
                                    shape = RoundedCornerShape(12.dp),
                                )
                                .clickable { onThemeChange(candidate) }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(p.focalAccent),
                                )
                                Text(
                                    text = candidate.label,
                                    color = p.wordText,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Full Ayah Context Preview
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = palette.stageBackground,
            border = BorderStroke(1.dp, palette.stageBorder),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Verse context (${surah.number}:${currentAyah.number})",
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.mutedText,
                )
                if (isArabicWbw) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            wbwTokens.forEachIndexed { idx, tok ->
                                val active = idx == safeWordIndex
                                Column(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (active) palette.focalAccent.copy(alpha = 0.20f) else palette.background.copy(alpha = 0.55f),
                                        )
                                        .border(
                                            width = if (active) 1.5.dp else 1.dp,
                                            color = if (active) palette.focalAccent else palette.stageBorder,
                                            shape = RoundedCornerShape(10.dp),
                                        )
                                        .clickable {
                                            isPlaying = false
                                            currentWordIndex = idx
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(
                                        text = tok.arabicWord,
                                        fontSize = 22.sp,
                                        color = if (active) palette.wordText else palette.mutedText,
                                        textAlign = TextAlign.Center,
                                    )
                                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                        Text(
                                            text = tok.englishGloss,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                            color = if (active) palette.focalAccent else palette.mutedText,
                                            textAlign = TextAlign.Center,
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    val contextAnnotated = buildAnnotatedString {
                        enTokens.forEachIndexed { idx, tok ->
                            if (idx > 0) append(" ")
                            if (idx == safeWordIndex) {
                                withStyle(
                                    SpanStyle(
                                        color = palette.wordText,
                                        fontWeight = FontWeight.Bold,
                                        background = palette.focalAccent.copy(alpha = 0.22f),
                                    ),
                                ) {
                                    append(tok.word)
                                }
                            } else {
                                withStyle(SpanStyle(color = palette.mutedText)) {
                                    append(tok.word)
                                }
                            }
                        }
                    }
                    Text(
                        text = contextAnnotated,
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 22.sp,
                    )
                }
            }
        }
    }
}
