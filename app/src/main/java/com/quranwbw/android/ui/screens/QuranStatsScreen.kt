package com.quranwbw.android.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.quranwbw.android.dtadhan.model.AppSettings
import com.quranwbw.android.dtadhan.model.GoalMetric
import com.quranwbw.android.dtadhan.model.GoalPeriod
import com.quranwbw.android.dtadhan.stats.AyahReadRecord
import com.quranwbw.android.dtadhan.stats.ChartBar
import com.quranwbw.android.dtadhan.stats.JuzBoundaries
import com.quranwbw.android.dtadhan.stats.ReadingDayResolver
import com.quranwbw.android.dtadhan.stats.ReadingSessionRecord
import com.quranwbw.android.dtadhan.stats.ReadingStatsCalculator
import com.quranwbw.android.model.Surah
import java.time.Instant
import kotlin.math.max
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuranStatsScreen(
    surahs: List<Surah>,
    settings: AppSettings,
    ayahReads: List<AyahReadRecord>,
    sessions: List<ReadingSessionRecord>,
    modifier: Modifier = Modifier,
) {
    val today = remember(settings.location, settings.prayer, settings.quran.dayStart) {
        ReadingDayResolver.resolveReadingDate(
            instant = Instant.now(),
            location = settings.location,
            prayerSettings = settings.prayer,
            dayStart = settings.quran.dayStart,
        )
    }

    var period by rememberSaveable { mutableStateOf(GoalPeriod.WEEKLY) }
    var metric by rememberSaveable { mutableStateOf(settings.quran.dailyGoalMetric) }
    var anchorDate by rememberSaveable { mutableStateOf(today) }

    val (juzByAyah, juzTotals) = remember(surahs) {
        JuzBoundaries.buildJuzMaps(surahs)
    }
    val totalAyahCount = remember(surahs) {
        surahs.sumOf { it.ayahs.size }.takeIf { it > 0 } ?: JuzBoundaries.TOTAL_QURAN_AYAHS
    }

    val weekStartDay = settings.quran.weekStart.resolveDayOfWeek()
    val (startDate, endDate) = remember(period, anchorDate, weekStartDay, settings.quran.monthType, settings.hijriOffsetDays) {
        ReadingStatsCalculator.periodRange(
            period = period,
            anchorDate = anchorDate,
            weekStartDay = weekStartDay,
            monthType = settings.quran.monthType,
            hijriOffsetDays = settings.hijriOffsetDays,
        )
    }
    val periodLabel = remember(period, startDate, endDate, settings.quran.monthType, settings.hijriOffsetDays) {
        ReadingStatsCalculator.periodLabel(
            period = period,
            startDate = startDate,
            endDate = endDate,
            monthType = settings.quran.monthType,
            hijriOffsetDays = settings.hijriOffsetDays,
        )
    }
    val bars = remember(period, metric, ayahReads, sessions, startDate, endDate) {
        ReadingStatsCalculator.buildBars(
            period = period,
            metric = metric,
            ayahReads = ayahReads,
            sessions = sessions,
            startDate = startDate,
            endDate = endDate,
        )
    }
    val stats = remember(ayahReads, sessions, juzByAyah, juzTotals, startDate, endDate) {
        ReadingStatsCalculator.summarizePeriod(
            ayahReads = ayahReads,
            sessions = sessions,
            juzByAyah = juzByAyah,
            juzTotals = juzTotals,
            startDate = startDate,
            endDate = endDate,
        )
    }
    val streaks = remember(ayahReads, sessions, settings.quran.dailyGoalMetric, settings.quran.dailyGoalTarget, today) {
        ReadingStatsCalculator.calculateStreaks(
            ayahReads = ayahReads,
            sessions = sessions,
            dailyMetric = settings.quran.dailyGoalMetric,
            dailyTarget = settings.quran.dailyGoalTarget,
            today = today,
        )
    }
    val khatm = remember(ayahReads, totalAyahCount, today) {
        ReadingStatsCalculator.calculateKhatmProgress(
            ayahReads = ayahReads,
            totalAyahs = totalAyahCount,
            today = today,
        )
    }
    val juzCoverage = remember(ayahReads, juzByAyah, juzTotals) {
        ReadingStatsCalculator.calculateJuzCoverage(
            ayahReads = ayahReads,
            juzByAyah = juzByAyah,
            juzTotals = juzTotals,
        )
    }

    fun stepPeriod(direction: Long) {
        anchorDate = when (period) {
            GoalPeriod.DAILY -> anchorDate.plusDays(direction)
            GoalPeriod.WEEKLY -> anchorDate.plusWeeks(direction)
            GoalPeriod.MONTHLY -> anchorDate.plusMonths(direction)
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
                            text = "Qur'an Reading Statistics",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Tracked automatically while reading & speed-reading",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "period_selector") {
                val periods = listOf(
                    GoalPeriod.DAILY to "Day",
                    GoalPeriod.WEEKLY to "Week",
                    GoalPeriod.MONTHLY to "Month",
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    periods.forEachIndexed { index, (p, label) ->
                        SegmentedButton(
                            selected = period == p,
                            onClick = { period = p },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = periods.size),
                        ) {
                            Text(label)
                        }
                    }
                }
            }

            item(key = "period_nav") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { stepPeriod(-1L) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous period")
                    }
                    Text(periodLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    IconButton(onClick = { stepPeriod(1L) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next period")
                    }
                }
            }

            item(key = "chart") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GoalMetric.entries.forEach { m ->
                                FilterChip(
                                    selected = metric == m,
                                    onClick = { metric = m },
                                    label = { Text(m.label) },
                                )
                            }
                        }
                        BarChart(
                            bars = bars,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp),
                        )
                    }
                }
            }

            item(key = "khatm_progress") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("Khatm Progress", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = "${(khatm.progressFraction * 100f).roundToInt()}% (${khatm.distinctAyahsSinceStart} / ${khatm.totalAyahs})",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        LinearProgressIndicator(
                            progress = { khatm.progressFraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                        )
                        khatm.projectedCompletionDate?.let { proj ->
                            Text(
                                text = "Projected Khatm completion: $proj",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            item(key = "totals") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Period Totals", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        StatRow("Ayahs read", "${stats.ayahsRead} (${stats.distinctAyahs} distinct)")
                        StatRow("English words", "${stats.wordsEn}")
                        StatRow("Arabic words", "${stats.wordsAr}")
                        StatRow("Active time", "${stats.activeMinutes} min (${stats.sessions} sessions)")
                        StatRow("Average pace", "${stats.avgWpm} wpm")
                        StatRow("Surahs touched", "${stats.surahsTouched}")
                        StatRow("Juz completed", "${stats.juzCompleted}")
                        StatRow("Current / Longest streak", "${streaks.currentStreakDays}d / ${streaks.longestStreakDays}d")
                    }
                }
            }

            item(key = "juz_grid") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Juz coverage (1–30)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        for (row in 0 until 5) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                for (col in 0 until 6) {
                                    val juzIdx = row * 6 + col
                                    val frac = juzCoverage.getOrElse(juzIdx) { 0f }
                                    val bg = if (frac <= 0f) {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.primary.copy(alpha = (0.2f + 0.8f * frac).coerceIn(0.2f, 1f))
                                    }
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(36.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(bg),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = "${juzIdx + 1}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (frac > 0.5f) {
                                                MaterialTheme.colorScheme.onPrimary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            textAlign = TextAlign.Center,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BarChart(bars: List<ChartBar>, modifier: Modifier = Modifier) {
    val barColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val maxVal = max(1, bars.maxOfOrNull { it.value } ?: 1)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(modifier = Modifier.fillMaxWidth().weight(1f)) {
            if (bars.isEmpty()) return@Canvas
            val count = bars.size
            val gap = 6f
            val barWidth = ((size.width - gap * (count - 1)) / count).coerceAtLeast(4f)
            bars.forEachIndexed { index, bar ->
                val x = index * (barWidth + gap)
                drawRoundRect(
                    color = trackColor,
                    topLeft = Offset(x, 0f),
                    size = Size(barWidth, size.height),
                    cornerRadius = CornerRadius(4f, 4f),
                )
                val h = (bar.value.toFloat() / maxVal.toFloat()) * size.height
                if (h > 0f) {
                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset(x, size.height - h),
                        size = Size(barWidth, h),
                        cornerRadius = CornerRadius(4f, 4f),
                    )
                }
            }
        }
        if (bars.size <= 10) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                bars.forEach { bar ->
                    Text(bar.label, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
