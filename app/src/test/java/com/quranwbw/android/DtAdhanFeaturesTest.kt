package com.quranwbw.android

import com.quranwbw.android.dtadhan.model.AsrMadhab
import com.quranwbw.android.dtadhan.model.CalcMethod
import com.quranwbw.android.dtadhan.model.GeoLocation
import com.quranwbw.android.dtadhan.model.GoalMetric
import com.quranwbw.android.dtadhan.model.PrayerEvent
import com.quranwbw.android.dtadhan.model.PrayerSettings
import com.quranwbw.android.dtadhan.prayer.HijriFormatter
import com.quranwbw.android.dtadhan.prayer.NextEventFinder
import com.quranwbw.android.dtadhan.prayer.PrayerCalculator
import com.quranwbw.android.dtadhan.qibla.AlignmentState
import com.quranwbw.android.dtadhan.qibla.HeadingMath
import com.quranwbw.android.dtadhan.qibla.QiblaCalculator
import com.quranwbw.android.dtadhan.qibla.SunQiblaHint
import com.quranwbw.android.dtadhan.stats.AyahReadRecord
import com.quranwbw.android.dtadhan.stats.JuzBoundaries
import com.quranwbw.android.dtadhan.stats.ReadingSessionRecord
import com.quranwbw.android.dtadhan.stats.ReadingStatsCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class DtAdhanFeaturesTest {

    @Test
    fun calculatesChronologicalPrayerScheduleAndHanafiLaterThanStandard() {
        val nyc = GeoLocation(40.7128, -74.0060, "New York, NY", "America/New_York")
        val date = LocalDate.of(2026, 5, 15)

        val standard = PrayerCalculator.day(date, nyc, PrayerSettings(method = CalcMethod.NORTH_AMERICA, madhab = AsrMadhab.STANDARD))
        val hanafi = PrayerCalculator.day(date, nyc, PrayerSettings(method = CalcMethod.NORTH_AMERICA, madhab = AsrMadhab.HANAFI))

        assertEquals(9, standard.events.size)
        for (i in 0 until standard.events.lastIndex) {
            assertTrue(
                "${standard.events[i].event} should be before ${standard.events[i + 1].event}",
                standard.events[i].time.isBefore(standard.events[i + 1].time)
            )
        }
        assertTrue(hanafi.timeOf(PrayerEvent.ASR)!!.isAfter(standard.timeOf(PrayerEvent.ASR)!!))

        val next = NextEventFinder.next(standard.timeOf(PrayerEvent.FAJR)!!, listOf(standard))
        assertNotNull(next)
        assertEquals(PrayerEvent.SUNRISE, next!!.event)
    }

    @Test
    fun calculatesQiblaBearingAndCircularHeadingMath() {
        val nyc = GeoLocation(40.7128, -74.0060, "New York, NY", "America/New_York")
        val qibla = QiblaCalculator.calculate(nyc)
        // NYC Qibla bearing is ~58.5° ENE
        assertTrue("Expected ~58.5 deg, got ${qibla.bearingDegrees}", qibla.bearingDegrees in 57.0..60.0)
        assertEquals("ENE", HeadingMath.compassDirectionLabel(qibla.bearingDegrees))
        assertTrue(qibla.distanceKm > 10_000.0)

        assertEquals(2.0, HeadingMath.shortestDelta(359.0, 1.0), 1e-6)
        assertEquals(-2.0, HeadingMath.shortestDelta(1.0, 359.0), 1e-6)
        assertEquals(AlignmentState.ALIGNED, HeadingMath.alignmentWithHysteresis(2.5, AlignmentState.OFF))

        val noonUtc = Instant.parse("2026-06-15T16:00:00Z")
        val sunHint = SunQiblaHint.hintFor(nyc, noonUtc)
        assertNotNull(sunHint)
        assertTrue(sunHint!!.message.contains("Qibla"))
    }

    @Test
    fun formatsHijriDatesAndSummarizesReadingStats() {
        val today = LocalDate.of(2026, 11, 22)
        val hijri = HijriFormatter.of(today)
        assertNotNull(hijri)

        assertEquals(1, JuzBoundaries.juzFor(1, 1))
        assertEquals(2, JuzBoundaries.juzFor(2, 142))
        assertEquals(30, JuzBoundaries.juzFor(78, 1))

        val reads = listOf(
            AyahReadRecord("s1", 1, 1, Instant.now(), today.minusDays(1), 9, 4, 4_000L),
            AyahReadRecord("s2", 1, 2, Instant.now(), today, 8, 4, 4_000L),
        )
        val sessions = listOf(
            ReadingSessionRecord("s1", Instant.now(), Instant.now(), 60, today.minusDays(1), "UTC"),
            ReadingSessionRecord("s2", Instant.now(), Instant.now(), 60, today, "UTC"),
        )

        val stats = ReadingStatsCalculator.summarizePeriod(
            ayahReads = reads,
            sessions = sessions,
            juzByAyah = emptyMap(),
            juzTotals = mapOf(1 to 148),
            startDate = today.minusDays(6),
            endDate = today,
        )
        assertEquals(2, stats.ayahsRead)
        assertEquals(2, stats.distinctAyahs)
        assertEquals(17, stats.wordsEn)
        assertEquals(8, stats.wordsAr)

        val streaks = ReadingStatsCalculator.calculateStreaks(
            ayahReads = reads,
            sessions = sessions,
            dailyMetric = GoalMetric.AYAHS,
            dailyTarget = 1,
            today = today,
        )
        assertEquals(2, streaks.currentStreakDays)
        assertEquals(2, streaks.longestStreakDays)
    }
}
