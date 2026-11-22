package com.quranwbw.android.dtadhan.stats

import com.quranwbw.android.dtadhan.model.GeoLocation
import com.quranwbw.android.dtadhan.model.GoalMetric
import com.quranwbw.android.dtadhan.model.GoalPeriod
import com.quranwbw.android.dtadhan.model.MonthCalendarType
import com.quranwbw.android.dtadhan.model.PrayerEvent
import com.quranwbw.android.dtadhan.model.PrayerSettings
import com.quranwbw.android.dtadhan.model.ReadingDayStart
import com.quranwbw.android.dtadhan.prayer.HijriFormatter
import com.quranwbw.android.dtadhan.prayer.PrayerCalculator
import com.quranwbw.android.model.Surah
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.ceil
import kotlin.math.roundToInt

data class AyahReadRecord(
    val sessionId: String,
    val surah: Int,
    val ayah: Int,
    val readAtUtc: Instant,
    val localDate: LocalDate,
    val wordsEn: Int,
    val wordsAr: Int,
    val dwellMs: Long,
)

data class ReadingSessionRecord(
    val id: String,
    val startUtc: Instant,
    val endUtc: Instant,
    val activeSeconds: Int,
    val localDate: LocalDate,
    val zoneId: String,
)

data class PeriodStats(
    val ayahsRead: Int = 0,
    val distinctAyahs: Int = 0,
    val wordsEn: Int = 0,
    val wordsAr: Int = 0,
    val activeSeconds: Int = 0,
    val sessions: Int = 0,
    val avgWpm: Int = 0,
    val surahsTouched: Int = 0,
    val juzCompleted: Int = 0,
) {
    val activeMinutes: Int get() = (activeSeconds / 60.0).roundToInt()
}

data class StreakInfo(
    val currentStreakDays: Int = 0,
    val longestStreakDays: Int = 0,
)

data class KhatmProgress(
    val distinctAyahsSinceStart: Int = 0,
    val totalAyahs: Int = 6236,
    val progressFraction: Float = 0f,
    val projectedCompletionDate: LocalDate? = null,
)

data class ChartBar(
    val label: String,
    val value: Int,
)

/**
 * Standard 30-Juz starting (surah, ayah) boundaries across the Qur'an.
 */
object JuzBoundaries {
    const val TOTAL_QURAN_AYAHS = 6236

    // 1-indexed Juz starts: (surah, ayah)
    private val STARTS: List<Pair<Int, Int>> = listOf(
        1 to 1,    // Juz 1
        2 to 142,  // Juz 2
        2 to 253,  // Juz 3
        3 to 93,   // Juz 4
        4 to 24,   // Juz 5
        4 to 148,  // Juz 6
        5 to 82,   // Juz 7
        6 to 111,  // Juz 8
        7 to 88,   // Juz 9
        8 to 41,   // Juz 10
        9 to 93,   // Juz 11
        11 to 6,   // Juz 12
        12 to 53,  // Juz 13
        15 to 1,   // Juz 14
        17 to 1,   // Juz 15
        18 to 75,  // Juz 16
        21 to 1,   // Juz 17
        23 to 1,   // Juz 18
        25 to 21,  // Juz 19
        27 to 56,  // Juz 20
        29 to 46,  // Juz 21
        33 to 31,  // Juz 22
        36 to 28,  // Juz 23
        39 to 32,  // Juz 24
        41 to 47,  // Juz 25
        46 to 1,   // Juz 26
        51 to 31,  // Juz 27
        58 to 1,   // Juz 28
        67 to 1,   // Juz 29
        78 to 1,   // Juz 30
    )

    fun juzFor(surah: Int, ayah: Int): Int {
        var matched = 1
        for (i in STARTS.indices) {
            val (s, a) = STARTS[i]
            if (surah > s || (surah == s && ayah >= a)) {
                matched = i + 1
            } else {
                break
            }
        }
        return matched
    }

    fun buildJuzMaps(surahs: List<Surah>): Pair<Map<Pair<Int, Int>, Int>, Map<Int, Int>> {
        val juzByAyah = HashMap<Pair<Int, Int>, Int>(TOTAL_QURAN_AYAHS)
        val juzTotals = HashMap<Int, Int>(30)
        for (s in surahs) {
            for (a in s.ayahs) {
                val j = juzFor(s.number, a.number)
                juzByAyah[s.number to a.number] = j
                juzTotals[j] = (juzTotals[j] ?: 0) + 1
            }
        }
        return juzByAyah to juzTotals
    }
}

object ReadingDayResolver {
    fun resolveReadingDate(
        instant: Instant,
        location: GeoLocation?,
        prayerSettings: PrayerSettings,
        dayStart: ReadingDayStart,
    ): LocalDate {
        val zone = location?.zone ?: ZoneId.systemDefault()
        val civilDate = instant.atZone(zone).toLocalDate()
        if (dayStart == ReadingDayStart.FAJR && location != null) {
            val schedule = PrayerCalculator.day(civilDate, location, prayerSettings)
            val fajr = schedule.timeOf(PrayerEvent.FAJR)
            if (fajr != null && instant.isBefore(fajr)) {
                return civilDate.minusDays(1)
            }
        }
        return civilDate
    }
}

object ReadingStatsCalculator {

    private val DAY_LABEL_FMT = DateTimeFormatter.ofPattern("MMM d, yyyy")
    private val SHORT_DAY_FMT = DateTimeFormatter.ofPattern("EEE")
    private val MONTH_LABEL_FMT = DateTimeFormatter.ofPattern("MMMM yyyy")

    fun periodRange(
        period: GoalPeriod,
        anchorDate: LocalDate,
        weekStartDay: DayOfWeek,
        monthType: MonthCalendarType,
        hijriOffsetDays: Int = 0,
    ): Pair<LocalDate, LocalDate> = when (period) {
        GoalPeriod.DAILY -> anchorDate to anchorDate
        GoalPeriod.WEEKLY -> {
            val diff = (anchorDate.dayOfWeek.value - weekStartDay.value + 7) % 7
            val start = anchorDate.minusDays(diff.toLong())
            start to start.plusDays(6)
        }
        GoalPeriod.MONTHLY -> {
            if (monthType == MonthCalendarType.GREGORIAN) {
                anchorDate.withDayOfMonth(1) to anchorDate.withDayOfMonth(anchorDate.lengthOfMonth())
            } else {
                val targetHijri = HijriFormatter.of(anchorDate, hijriOffsetDays)
                    ?: return anchorDate.withDayOfMonth(1) to anchorDate.withDayOfMonth(anchorDate.lengthOfMonth())
                var start = anchorDate
                while (HijriFormatter.of(start.minusDays(1), hijriOffsetDays)?.let {
                        it.year == targetHijri.year && it.month == targetHijri.month
                    } == true
                ) {
                    start = start.minusDays(1)
                }
                var end = anchorDate
                while (HijriFormatter.of(end.plusDays(1), hijriOffsetDays)?.let {
                        it.year == targetHijri.year && it.month == targetHijri.month
                    } == true
                ) {
                    end = end.plusDays(1)
                }
                start to end
            }
        }
    }

    fun periodLabel(
        period: GoalPeriod,
        startDate: LocalDate,
        endDate: LocalDate,
        monthType: MonthCalendarType,
        hijriOffsetDays: Int = 0,
    ): String = when (period) {
        GoalPeriod.DAILY -> DAY_LABEL_FMT.format(startDate)
        GoalPeriod.WEEKLY -> "${DAY_LABEL_FMT.format(startDate)} – ${DAY_LABEL_FMT.format(endDate)}"
        GoalPeriod.MONTHLY -> {
            if (monthType == MonthCalendarType.HIJRI) {
                val h = HijriFormatter.of(startDate, hijriOffsetDays)
                if (h != null) "${h.monthName} ${h.year} AH" else MONTH_LABEL_FMT.format(startDate)
            } else {
                MONTH_LABEL_FMT.format(startDate)
            }
        }
    }

    fun buildBars(
        period: GoalPeriod,
        metric: GoalMetric,
        ayahReads: List<AyahReadRecord>,
        sessions: List<ReadingSessionRecord>,
        startDate: LocalDate,
        endDate: LocalDate,
    ): List<ChartBar> {
        fun metricForDay(date: LocalDate): Int {
            val dayReads = ayahReads.filter { it.localDate == date }
            val daySessions = sessions.filter { it.localDate == date }
            return when (metric) {
                GoalMetric.AYAHS -> dayReads.size
                GoalMetric.WORDS -> dayReads.sumOf { it.wordsEn }
                GoalMetric.MINUTES -> (daySessions.sumOf { it.activeSeconds } / 60.0).roundToInt()
            }
        }

        if (period == GoalPeriod.DAILY) {
            // Show 7-day context ending on endDate
            return (6 downTo 0).map { offset ->
                val d = endDate.minusDays(offset.toLong())
                ChartBar(label = SHORT_DAY_FMT.format(d), value = metricForDay(d))
            }
        }

        val bars = ArrayList<ChartBar>()
        var cursor = startDate
        while (!cursor.isAfter(endDate)) {
            val lbl = if (period == GoalPeriod.WEEKLY) {
                SHORT_DAY_FMT.format(cursor)
            } else {
                cursor.dayOfMonth.toString()
            }
            bars.add(ChartBar(label = lbl, value = metricForDay(cursor)))
            cursor = cursor.plusDays(1)
        }
        return bars
    }

    fun summarizePeriod(
        ayahReads: List<AyahReadRecord>,
        sessions: List<ReadingSessionRecord>,
        juzByAyah: Map<Pair<Int, Int>, Int>,
        juzTotals: Map<Int, Int>,
        startDate: LocalDate,
        endDate: LocalDate,
    ): PeriodStats {
        val periodReads = ayahReads.filter { !it.localDate.isBefore(startDate) && !it.localDate.isAfter(endDate) }
        val periodSessions = sessions.filter { !it.localDate.isBefore(startDate) && !it.localDate.isAfter(endDate) }

        val distinctRefs = periodReads.map { it.surah to it.ayah }.toSet()
        val wordsEn = periodReads.sumOf { it.wordsEn }
        val wordsAr = periodReads.sumOf { it.wordsAr }
        val activeSeconds = periodSessions.sumOf { it.activeSeconds }
        val avgWpm = if (activeSeconds >= 10) {
            ((wordsEn.toDouble() / activeSeconds.toDouble()) * 60.0).roundToInt()
        } else {
            0
        }
        val surahsTouched = distinctRefs.map { it.first }.toSet().size

        val readCountsByJuz = HashMap<Int, Int>()
        for (ref in distinctRefs) {
            val juz = juzByAyah[ref] ?: JuzBoundaries.juzFor(ref.first, ref.second)
            readCountsByJuz[juz] = (readCountsByJuz[juz] ?: 0) + 1
        }
        val juzCompleted = juzTotals.count { (juz, total) ->
            total > 0 && (readCountsByJuz[juz] ?: 0) >= total
        }

        return PeriodStats(
            ayahsRead = periodReads.size,
            distinctAyahs = distinctRefs.size,
            wordsEn = wordsEn,
            wordsAr = wordsAr,
            activeSeconds = activeSeconds,
            sessions = periodSessions.size,
            avgWpm = avgWpm,
            surahsTouched = surahsTouched,
            juzCompleted = juzCompleted,
        )
    }

    fun calculateStreaks(
        ayahReads: List<AyahReadRecord>,
        sessions: List<ReadingSessionRecord>,
        dailyMetric: GoalMetric,
        dailyTarget: Int,
        today: LocalDate,
    ): StreakInfo {
        val readsByDay = ayahReads.groupBy { it.localDate }
        val sessionsByDay = sessions.groupBy { it.localDate }
        val allDates = (readsByDay.keys + sessionsByDay.keys).toSortedSet()
        if (allDates.isEmpty()) return StreakInfo(0, 0)

        fun dayQualifies(date: LocalDate): Boolean {
            val dayReads = readsByDay[date].orEmpty()
            val daySessions = sessionsByDay[date].orEmpty()
            if (dailyTarget <= 0) {
                return dayReads.isNotEmpty() || daySessions.any { it.activeSeconds > 0 }
            }
            val metricValue = when (dailyMetric) {
                GoalMetric.AYAHS -> dayReads.size
                GoalMetric.WORDS -> dayReads.sumOf { it.wordsEn }
                GoalMetric.MINUTES -> (daySessions.sumOf { it.activeSeconds } / 60.0).roundToInt()
            }
            return metricValue >= dailyTarget
        }

        val qualifyingDates = allDates.filter { dayQualifies(it) }.toSet()
        if (qualifyingDates.isEmpty()) return StreakInfo(0, 0)

        var longest = 0
        var run = 0
        var prevDate: LocalDate? = null
        for (d in qualifyingDates.sorted()) {
            run = if (prevDate != null && prevDate.plusDays(1) == d) run + 1 else 1
            if (run > longest) longest = run
            prevDate = d
        }

        var cursor = if (today in qualifyingDates) today else today.minusDays(1)
        var current = 0
        while (cursor in qualifyingDates) {
            current++
            cursor = cursor.minusDays(1)
        }

        return StreakInfo(currentStreakDays = current, longestStreakDays = longest)
    }

    fun calculateKhatmProgress(
        ayahReads: List<AyahReadRecord>,
        totalAyahs: Int = JuzBoundaries.TOTAL_QURAN_AYAHS,
        today: LocalDate,
    ): KhatmProgress {
        val distinctCount = ayahReads.map { it.surah to it.ayah }.toSet().size
        val safeTotal = totalAyahs.coerceAtLeast(1)
        val fraction = (distinctCount.toFloat() / safeTotal.toFloat()).coerceIn(0f, 1f)

        val remaining = (safeTotal - distinctCount).coerceAtLeast(0)
        val projectedDate = if (remaining == 0) {
            today
        } else {
            val windowStart = today.minusDays(13)
            val recentReads = ayahReads.count { !it.localDate.isBefore(windowStart) && !it.localDate.isAfter(today) }
            if (recentReads > 0) {
                val dailyPace = recentReads.toDouble() / 14.0
                val daysNeeded = ceil(remaining / dailyPace).toLong().coerceAtMost(36500L)
                today.plusDays(daysNeeded)
            } else {
                null
            }
        }

        return KhatmProgress(
            distinctAyahsSinceStart = distinctCount,
            totalAyahs = safeTotal,
            progressFraction = fraction,
            projectedCompletionDate = projectedDate,
        )
    }

    fun calculateJuzCoverage(
        ayahReads: List<AyahReadRecord>,
        juzByAyah: Map<Pair<Int, Int>, Int>,
        juzTotals: Map<Int, Int>,
    ): List<Float> {
        val distinctRefs = ayahReads.map { it.surah to it.ayah }.toSet()
        val readByJuz = HashMap<Int, Int>()
        for (ref in distinctRefs) {
            val j = juzByAyah[ref] ?: JuzBoundaries.juzFor(ref.first, ref.second)
            readByJuz[j] = (readByJuz[j] ?: 0) + 1
        }
        return (1..30).map { juz ->
            val total = juzTotals[juz] ?: 200
            val read = readByJuz[juz] ?: 0
            if (total <= 0) 0f else (read.toFloat() / total.toFloat()).coerceIn(0f, 1f)
        }
    }
}
