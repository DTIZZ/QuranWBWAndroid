package com.quranwbw.android.ui.screens

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quranwbw.android.model.ArabicLetterUnit
import com.quranwbw.android.model.Ayah
import com.quranwbw.android.model.QuranWord
import com.quranwbw.android.model.Surah
import com.quranwbw.android.speedread.FocusedReadMode
import com.quranwbw.android.speedread.SpeedReadTheme
import com.quranwbw.android.ui.viewmodel.ReaderUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    uiState: ReaderUiState,
    onSelectSurah: (Int) -> Unit,
    onWordClick: (Ayah, QuranWord) -> Unit,
    onReplayWord: () -> Unit,
    onLetterClick: (ArabicLetterUnit) -> Unit,
    onToggleAyahPlay: (Ayah) -> Unit,
    onToggleSpeedReadMode: () -> Unit,
    onSpeedReadWpmChange: (Int) -> Unit,
    onSpeedReadThemeChange: (SpeedReadTheme) -> Unit,
    onFocusedReadModeChange: (FocusedReadMode) -> Unit,
    onSpeedReadAyahCompleted: (Int, Long) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    var showSurahSheet by remember { mutableStateOf(false) }
    var isSpeedReadPlaying by remember { mutableStateOf(false) }
    val context = LocalContext.current

    DisposableEffect(uiState.isSpeedReadMode, isSpeedReadPlaying, context) {
        val window = (context as? Activity)?.window
        if (uiState.isSpeedReadMode && isSpeedReadPlaying) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Quran WBW",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        uiState.currentSurah?.let { surah ->
                            Text(
                                text = "${surah.number}. ${surah.nameEnglish} (${surah.meaning})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    if (uiState.surahs.isNotEmpty()) {
                        FilledTonalButton(
                            onClick = {
                                if (uiState.isSpeedReadMode) {
                                    isSpeedReadPlaying = false
                                }
                                onToggleSpeedReadMode()
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = if (uiState.isSpeedReadMode) {
                                    Icons.AutoMirrored.Filled.MenuBook
                                } else {
                                    Icons.Filled.Speed
                                },
                                contentDescription = if (uiState.isSpeedReadMode) "Standard reader" else "Speed Read"
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (uiState.isSpeedReadMode) "Standard" else "Speed Read")
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        TextButton(onClick = { showSurahSheet = true }) {
                            Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = "Browse Surahs")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("All ${uiState.surahs.size}")
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (!uiState.isSpeedReadMode) {
                WordAndLetterInspectorBar(
                    selectedWord = uiState.selectedWord,
                    parsedLetters = uiState.parsedLetters,
                    selectedLetter = uiState.selectedLetter,
                    onReplayWord = onReplayWord,
                    onLetterClick = onLetterClick
                )
            }
        }
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Loading 114 Surahs…", style = MaterialTheme.typography.bodyMedium)
                }
            }
            return@Scaffold
        }

        val chipRowState = rememberLazyListState()
        LaunchedEffect(uiState.selectedSurahIndex) {
            if (uiState.surahs.isNotEmpty()) {
                chipRowState.animateScrollToItem(uiState.selectedSurahIndex)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyRow(
                state = chipRowState,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(uiState.surahs, key = { _, s -> s.number }) { idx, surah ->
                    FilterChip(
                        selected = idx == uiState.selectedSurahIndex,
                        onClick = { onSelectSurah(idx) },
                        label = { Text("${surah.number}. ${surah.nameEnglish}") }
                    )
                }
            }

            val currentSurah = uiState.currentSurah
            if (uiState.isSpeedReadMode && currentSurah != null) {
                QuranSpeedReader(
                    surah = currentSurah,
                    initialAyahNumber = uiState.selectedAyahNumber ?: 1,
                    wpm = uiState.speedReadWpm,
                    theme = uiState.speedReadTheme,
                    focusedReadMode = uiState.focusedReadMode,
                    onWpmChange = onSpeedReadWpmChange,
                    onThemeChange = onSpeedReadThemeChange,
                    onModeChange = onFocusedReadModeChange,
                    onPlayingChanged = { isSpeedReadPlaying = it },
                    onAyahCompleted = onSpeedReadAyahCompleted,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                val ayahs = currentSurah?.ayahs.orEmpty()
                val ayahListState = rememberLazyListState()
                LaunchedEffect(uiState.selectedSurahIndex) {
                    ayahListState.scrollToItem(0)
                }

                LazyColumn(
                    state = ayahListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(ayahs, key = { it.number }) { ayah ->
                        AyahCard(
                            ayah = ayah,
                            isPlayingAyah = uiState.playingAyahNumber == ayah.number,
                            highlightedWordIndex = if (uiState.playingAyahNumber == ayah.number) {
                                uiState.highlightedWordIndex
                            } else {
                                null
                            },
                            selectedWord = if (uiState.selectedAyahNumber == ayah.number) {
                                uiState.selectedWord
                            } else {
                                null
                            },
                            onWordClick = { word -> onWordClick(ayah, word) },
                            onTogglePlay = { onToggleAyahPlay(ayah) }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }
    }

    if (showSurahSheet) {
        SurahPickerBottomSheet(
            surahs = uiState.surahs,
            selectedSurahIndex = uiState.selectedSurahIndex,
            onSelect = { idx ->
                onSelectSurah(idx)
                showSurahSheet = false
            },
            onDismiss = { showSurahSheet = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SurahPickerBottomSheet(
    surahs: List<Surah>,
    selectedSurahIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(surahs, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) {
            surahs.mapIndexed { idx, s -> idx to s }
        } else {
            surahs.mapIndexedNotNull { idx, s ->
                if (s.number.toString().contains(q) ||
                    s.nameEnglish.lowercase().contains(q) ||
                    s.meaning.lowercase().contains(q) ||
                    s.nameArabic.contains(q)
                ) {
                    idx to s
                } else {
                    null
                }
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = "Select Surah (${surahs.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search by number, name, or meaning…") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
            ) {
                items(filtered, key = { (_, s) -> s.number }) { (idx, surah) ->
                    ListItem(
                        headlineContent = {
                            Text(
                                text = "${surah.number}. ${surah.nameEnglish}",
                                fontWeight = if (idx == selectedSurahIndex) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        supportingContent = {
                            Text("${surah.meaning} • ${surah.ayahs.size} ayahs")
                        },
                        trailingContent = {
                            Text(
                                text = surah.nameArabic,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        },
                        modifier = Modifier.clickable { onSelect(idx) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AyahCard(
    ayah: Ayah,
    isPlayingAyah: Boolean,
    highlightedWordIndex: Int?,
    selectedWord: QuranWord?,
    onWordClick: (QuranWord) -> Unit,
    onTogglePlay: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Ayah ${ayah.number}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                IconButton(onClick = onTogglePlay) {
                    Icon(
                        imageVector = if (isPlayingAyah) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = if (isPlayingAyah) "Stop Ayah" else "Play Ayah"
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ayah.words.forEach { word ->
                        val isActive = highlightedWordIndex == word.index || selectedWord?.index == word.index
                        WordChip(
                            word = word,
                            isSelected = isActive,
                            onClick = { onWordClick(word) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WordChip(
    word: QuranWord,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = word.arabic,
                fontSize = 24.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
            Text(
                text = word.transliteration,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = word.translation,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WordAndLetterInspectorBar(
    selectedWord: QuranWord?,
    parsedLetters: List<ArabicLetterUnit>,
    selectedLetter: ArabicLetterUnit?,
    onReplayWord: () -> Unit,
    onLetterClick: (ArabicLetterUnit) -> Unit
) {
    Surface(
        tonalElevation = 6.dp,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            if (selectedWord == null) {
                Text(
                    text = "Tap any word to hear its audio and inspect its letters.",
                    style = MaterialTheme.typography.bodyMedium
                )
                return@Column
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${selectedWord.arabic}  •  ${selectedWord.transliteration}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = selectedWord.translation,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                FilledTonalButton(onClick = onReplayWord) {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Word")
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

            Text(
                text = "Tap a letter to hear its pronunciation:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    parsedLetters.forEach { letter ->
                        val isLetterSelected = selectedLetter?.index == letter.index
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isLetterSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            modifier = Modifier.clickable { onLetterClick(letter) }
                        ) {
                            Text(
                                text = letter.display,
                                fontSize = 22.sp,
                                color = if (isLetterSelected) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            selectedLetter?.let { letter ->
                Spacer(modifier = Modifier.height(8.dp))
                val harakatText = if (letter.harakatNames.isEmpty()) {
                    "No harakat"
                } else {
                    letter.harakatNames.joinToString(" + ")
                }
                Text(
                    text = "${letter.display}  —  ${letter.letterName} ($harakatText)  [audio key: ${letter.audioKey}]",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
