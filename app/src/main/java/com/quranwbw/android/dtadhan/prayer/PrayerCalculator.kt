package com.quranwbw.android.dtadhan.prayer

import com.quranwbw.android.dtadhan.model.CalcMethod
import com.quranwbw.android.dtadhan.model.DayEvent
import com.quranwbw.android.dtadhan.model.DaySchedule
import com.quranwbw.android.dtadhan.model.GeoLocation
import com.quranwbw.android.dtadhan.model.HighLatRule
import com.quranwbw.android.dtadhan.model.PrayerEvent
import com.quranwbw.android.dtadhan.model.PrayerSettings
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.chrono.HijrahDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoField
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.tan

/**
 * Astronomical Islamic prayer time calculator supporting all 12 calculation methods,
 * Standard/Hanafi Asr shadow factor, High-Latitude night portion rules, per-prayer adjustments,
 * Duha, Midnight, and Last Third of the Night.
 */
object PrayerCalculator {

    fun day(date: LocalDate, location: GeoLocation, settings: PrayerSettings): DaySchedule {
        val zone = location.zone
        val lat = location.latitude
        val lon = location.longitude

        val jd = julianDate(date.year, date.monthValue, date.dayOfMonth) - lon / (15.0 * 24.0)
        val (declDeg, eqTimeHours) = sunPosition(jd + 0.5)

        val transitUtcHours = 12.0 - lon / 15.0 - eqTimeHours

        val sunriseAngle = -0.8333
        val sunriseDiffHours = hourAngleHours(lat, declDeg, sunriseAngle) ?: 6.0
        val sunriseUtcHours = transitUtcHours - sunriseDiffHours
        val sunsetUtcHours = transitUtcHours + sunriseDiffHours

        val fajrAngle = if (settings.method == CalcMethod.CUSTOM) {
            settings.customFajrAngle.coerceIn(PrayerSettings.ANGLE_RANGE)
        } else {
            settings.method.fajrAngle
        }
        val ishaAngle = if (settings.method == CalcMethod.CUSTOM) {
            settings.customIshaAngle.coerceIn(PrayerSettings.ANGLE_RANGE)
        } else {
            settings.method.ishaAngle
        }
        val ishaInterval = if (settings.method == CalcMethod.CUSTOM) {
            settings.customIshaIntervalMinutes.coerceIn(PrayerSettings.ISHA_INTERVAL_RANGE)
        } else {
            settings.method.ishaIntervalMinutes
        }

        val nightDurationHours = (24.0 - (sunsetUtcHours - sunriseUtcHours)).coerceIn(1.0, 23.0)
        val effectiveHighLat = if (settings.highLatitudeRule == HighLatRule.AUTO) {
            if (abs(lat) > 48.0) HighLatRule.SEVENTH_OF_THE_NIGHT else HighLatRule.MIDDLE_OF_THE_NIGHT
        } else {
            settings.highLatitudeRule
        }

        fun nightPortion(angle: Double): Double = when (effectiveHighLat) {
            HighLatRule.MIDDLE_OF_THE_NIGHT, HighLatRule.AUTO -> 0.5
            HighLatRule.SEVENTH_OF_THE_NIGHT -> 1.0 / 7.0
            HighLatRule.TWILIGHT_ANGLE -> (angle / 60.0).coerceIn(0.05, 0.5)
        }

        val rawFajrDiff = hourAngleHours(lat, declDeg, -fajrAngle)?.let { (it - sunriseDiffHours).coerceAtLeast(0.5) }
        val maxFajrDiff = nightDurationHours * nightPortion(fajrAngle)
        val fajrDiffHours = if (rawFajrDiff == null || rawFajrDiff > maxFajrDiff) maxFajrDiff else rawFajrDiff
        val fajrUtcHours = sunriseUtcHours - fajrDiffHours

        val ishaUtcHours = if (ishaInterval > 0) {
            sunsetUtcHours + ishaInterval / 60.0
        } else {
            val rawIshaDiff = hourAngleHours(lat, declDeg, -ishaAngle)?.let { (it - sunriseDiffHours).coerceAtLeast(0.5) }
            val maxIshaDiff = nightDurationHours * nightPortion(ishaAngle)
            val ishaDiffHours = if (rawIshaDiff == null || rawIshaDiff > maxIshaDiff) maxIshaDiff else rawIshaDiff
            sunsetUtcHours + ishaDiffHours
        }

        val asrAltDeg = Math.toDegrees(
            atan(1.0 / (settings.madhab.shadowFactor + tan(Math.toRadians(abs(lat - declDeg)))))
        )
        val asrDiffHours = hourAngleHours(lat, declDeg, asrAltDeg) ?: (sunriseDiffHours * 0.6)
        val asrUtcHours = transitUtcHours + asrDiffHours

        // Convert UTC fractional hours relative to date's 00:00 UTC into Instant
        val baseUtc = ZonedDateTime.of(date, LocalTime.MIDNIGHT, ZoneId.of("UTC")).toInstant()
        fun utcHoursToInstant(hours: Double, adjEvent: PrayerEvent? = null): Instant {
            val adjMin = if (adjEvent != null) {
                settings.adjustment(adjEvent).coerceIn(-PrayerSettings.ADJUSTMENT_RANGE, PrayerSettings.ADJUSTMENT_RANGE)
            } else {
                0
            }
            val totalSeconds = (hours * 3600.0).roundToLong() + adjMin * 60L
            return baseUtc.plusSeconds(totalSeconds)
        }

        val fajrInstant = utcHoursToInstant(fajrUtcHours, PrayerEvent.FAJR)
        val sunriseInstant = utcHoursToInstant(sunriseUtcHours, PrayerEvent.SUNRISE)
        val duhaInstant = sunriseInstant.plus(Duration.ofMinutes(settings.duhaOffsetMinutes.toLong()))
        // Standard Dhuhr precaution (~65 seconds after solar transit)
        val dhuhrInstant = utcHoursToInstant(transitUtcHours + 65.0 / 3600.0, PrayerEvent.DHUHR)
        val asrInstant = utcHoursToInstant(asrUtcHours, PrayerEvent.ASR)
        val maghribInstant = utcHoursToInstant(sunsetUtcHours, PrayerEvent.MAGHRIB)
        val ishaInstant = utcHoursToInstant(ishaUtcHours, PrayerEvent.ISHA)

        // Compute next day's Fajr for exact Sunnah Midnight and Last Third of the Night
        val nextDate = date.plusDays(1)
        val nextJd = julianDate(nextDate.year, nextDate.monthValue, nextDate.dayOfMonth) - lon / (15.0 * 24.0)
        val (nextDecl, nextEq) = sunPosition(nextJd + 0.5)
        val nextTransit = 12.0 - lon / 15.0 - nextEq
        val nextSunriseDiff = hourAngleHours(lat, nextDecl, sunriseAngle) ?: 6.0
        val nextSunrise = nextTransit - nextSunriseDiff
        val nextRawFajrDiff = hourAngleHours(lat, nextDecl, -fajrAngle)?.let { (it - nextSunriseDiff).coerceAtLeast(0.5) }
        val nextFajrDiff = nextRawFajrDiff?.coerceAtMost(maxFajrDiff) ?: maxFajrDiff
        val nextFajrInstant = baseUtc.plusSeconds(((24.0 + nextSunrise - nextFajrDiff) * 3600.0).roundToLong())
            .plusSeconds(settings.adjustment(PrayerEvent.FAJR).coerceIn(-PrayerSettings.ADJUSTMENT_RANGE, PrayerSettings.ADJUSTMENT_RANGE) * 60L)

        val nightSeconds = Duration.between(maghribInstant, nextFajrInstant).seconds.coerceAtLeast(3600L)
        val midnightInstant = maghribInstant.plusSeconds(nightSeconds / 2)
        val lastThirdInstant = maghribInstant.plusSeconds((nightSeconds * 2) / 3)

        val events = listOf(
            DayEvent(PrayerEvent.FAJR, fajrInstant, date),
            DayEvent(PrayerEvent.SUNRISE, sunriseInstant, date),
            DayEvent(PrayerEvent.DUHA, duhaInstant, date),
            DayEvent(PrayerEvent.DHUHR, dhuhrInstant, date),
            DayEvent(PrayerEvent.ASR, asrInstant, date),
            DayEvent(PrayerEvent.MAGHRIB, maghribInstant, date),
            DayEvent(PrayerEvent.ISHA, ishaInstant, date),
            DayEvent(PrayerEvent.MIDNIGHT, midnightInstant, date),
            DayEvent(PrayerEvent.LAST_THIRD, lastThirdInstant, date),
        )
        return DaySchedule(date, zone, events)
    }

    fun window(now: Instant, location: GeoLocation, settings: PrayerSettings): List<DaySchedule> {
        val today = now.atZone(location.zone).toLocalDate()
        return listOf(today.minusDays(1), today, today.plusDays(1)).map { day(it, location, settings) }
    }

    private fun hourAngleHours(latDeg: Double, declDeg: Double, altitudeDeg: Double): Double? {
        val latRad = Math.toRadians(latDeg)
        val declRad = Math.toRadians(declDeg)
        val altRad = Math.toRadians(altitudeDeg)
        val denom = cos(latRad) * cos(declRad)
        if (abs(denom) < 1e-9) return null
        val cosH = (sin(altRad) - sin(latRad) * sin(declRad)) / denom
        if (cosH < -1.0 || cosH > 1.0) return null
        return Math.toDegrees(acos(cosH)) / 15.0
    }

    private fun julianDate(year: Int, month: Int, day: Int): Double {
        var y = year
        var m = month
        if (m <= 2) {
            y -= 1
            m += 12
        }
        val a = floor(y / 100.0)
        val b = 2.0 - a + floor(a / 4.0)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + day + b - 1524.5
    }

    private fun sunPosition(jd: Double): Pair<Double, Double> {
        val d = jd - 2451545.0
        val g = fixAngle(357.529 + 0.98560028 * d)
        val q = fixAngle(280.459 + 0.98564736 * d)
        val l = fixAngle(q + 1.915 * sin(Math.toRadians(g)) + 0.020 * sin(Math.toRadians(2.0 * g)))
        val e = 23.439 - 0.00000036 * d
        val eRad = Math.toRadians(e)
        val lRad = Math.toRadians(l)
        val raHours = Math.toDegrees(atan2(cos(eRad) * sin(lRad), cos(lRad))) / 15.0
        val declDeg = Math.toDegrees(asin(sin(eRad) * sin(lRad)))
        var eqTime = q / 15.0 - fixHour(raHours)
        if (eqTime > 12.0) eqTime -= 24.0
        if (eqTime < -12.0) eqTime += 24.0
        return declDeg to eqTime
    }

    private fun fixAngle(a: Double): Double {
        val mod = a % 360.0
        return if (mod < 0) mod + 360.0 else mod
    }

    private fun fixHour(h: Double): Double {
        val mod = h % 24.0
        return if (mod < 0) mod + 24.0 else mod
    }
}

object NextEventFinder {
    fun next(
        now: Instant,
        schedules: List<DaySchedule>,
        include: Set<PrayerEvent> = PrayerEvent.COUNTDOWN_TARGETS,
    ): DayEvent? = schedules.asSequence()
        .flatMap { it.events.asSequence() }
        .filter { it.event in include && it.time.isAfter(now) }
        .minByOrNull { it.time }

    fun current(
        now: Instant,
        schedules: List<DaySchedule>,
        include: Set<PrayerEvent> = PrayerEvent.COUNTDOWN_TARGETS,
    ): DayEvent? = schedules.asSequence()
        .flatMap { it.events.asSequence() }
        .filter { it.event in include && !it.time.isAfter(now) }
        .maxByOrNull { it.time }

    fun formatRemaining(remaining: Duration): String {
        val total = remaining.seconds.coerceAtLeast(0)
        val hours = total / 3600
        val minutes = (total % 3600) / 60
        val seconds = total % 60
        return when {
            hours > 0 -> "%dh %02dm".format(hours, minutes)
            minutes > 0 -> "%dm".format(minutes)
            else -> "%ds".format(seconds)
        }
    }
}

data class HijriDate(val day: Int, val month: Int, val year: Int) {
    val monthName: String get() = HijriFormatter.MONTHS[month - 1]
    val isRamadan: Boolean get() = month == HijriFormatter.RAMADAN
    override fun toString(): String = "$day $monthName $year AH"
}

object HijriFormatter {
    val MONTHS = listOf(
        "Muharram", "Safar", "Rabi' al-Awwal", "Rabi' al-Thani", "Jumada al-Ula", "Jumada al-Akhirah",
        "Rajab", "Sha'ban", "Ramadan", "Shawwal", "Dhu al-Qi'dah", "Dhu al-Hijjah",
    )
    const val MAX_OFFSET = 2
    const val RAMADAN = 9

    fun isRamadan(date: LocalDate, offsetDays: Int = 0): Boolean = of(date, offsetDays)?.isRamadan == true

    fun of(date: LocalDate, offsetDays: Int = 0): HijriDate? {
        val shifted = date.plusDays(offsetDays.coerceIn(-MAX_OFFSET, MAX_OFFSET).toLong())
        val hijrah = runCatching { HijrahDate.from(shifted) }.getOrNull() ?: return null
        return HijriDate(
            day = hijrah.get(ChronoField.DAY_OF_MONTH),
            month = hijrah.get(ChronoField.MONTH_OF_YEAR),
            year = hijrah.get(ChronoField.YEAR),
        )
    }
}

object DayBrowser {
    const val MAX_DAYS_AHEAD = 365L
    private const val MILLIS_PER_DAY = 86_400_000L
    private val DATE_FMT = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy")

    fun lastDay(today: LocalDate): LocalDate = today.plusDays(MAX_DAYS_AHEAD)

    fun isSelectable(date: LocalDate, today: LocalDate): Boolean =
        !date.isBefore(today) && !date.isAfter(lastDay(today))

    fun relativeLabel(date: LocalDate, today: LocalDate): String =
        when (val days = ChronoUnit.DAYS.between(today, date)) {
            0L -> "Today"
            1L -> "Tomorrow"
            -1L -> "Yesterday"
            else -> if (days > 0) "In $days days" else "${-days} days ago"
        }

    fun dateLabel(date: LocalDate): String = DATE_FMT.format(date)

    fun fromPickerMillis(utcMillis: Long): LocalDate =
        LocalDate.ofEpochDay(Math.floorDiv(utcMillis, MILLIS_PER_DAY))

    fun toPickerMillis(date: LocalDate): Long = date.toEpochDay() * MILLIS_PER_DAY
}
