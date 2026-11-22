package com.quranwbw.android.dtadhan.model

import com.quranwbw.android.speedread.FocusedReadMode
import com.quranwbw.android.speedread.SpeedReadTheme
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.WeekFields
import java.util.Locale

enum class PrayerEvent(val isRequiredPrayer: Boolean, val label: String) {
    FAJR(true, "Fajr"),
    SUNRISE(false, "Sunrise"),
    DUHA(false, "Duha"),
    DHUHR(true, "Dhuhr"),
    ASR(true, "Asr"),
    MAGHRIB(true, "Maghrib"),
    ISHA(true, "Isha"),
    MIDNIGHT(false, "Midnight"),
    LAST_THIRD(false, "Last third of night");

    companion object {
        val REQUIRED: Set<PrayerEvent> = entries.filter { it.isRequiredPrayer }.toSet()
        val OPTIONAL: Set<PrayerEvent> = entries.filterNot { it.isRequiredPrayer }.toSet()
        val COUNTDOWN_TARGETS: Set<PrayerEvent> = REQUIRED + SUNRISE
        val ADJUSTABLE: List<PrayerEvent> = listOf(FAJR, SUNRISE, DHUHR, ASR, MAGHRIB, ISHA)
    }
}

enum class CalcMethod(
    val label: String,
    val fajrAngle: Double,
    val ishaAngle: Double,
    val ishaIntervalMinutes: Int = 0,
) {
    NORTH_AMERICA("ISNA / North America (15°/15°)", 15.0, 15.0),
    MUSLIM_WORLD_LEAGUE("Muslim World League (18°/17°)", 18.0, 17.0),
    EGYPTIAN("Egyptian General Authority (19.5°/17.5°)", 19.5, 17.5),
    UMM_AL_QURA("Umm al-Qura, Makkah (18.5°, Isha 90 min)", 18.5, 0.0, 90),
    KARACHI("University of Islamic Sciences, Karachi (18°/18°)", 18.0, 18.0),
    DUBAI("Dubai (18.2°/18.2°)", 18.2, 18.2),
    QATAR("Qatar (18°, Isha 90 min)", 18.0, 0.0, 90),
    KUWAIT("Kuwait (18°/17.5°)", 18.0, 17.5),
    MOON_SIGHTING_COMMITTEE("Moonsighting Committee (18°/18°)", 18.0, 18.0),
    SINGAPORE("Singapore (20°/18°)", 20.0, 18.0),
    TURKEY("Diyanet, Turkey (18°/17°)", 18.0, 17.0),
    CUSTOM("Custom angles", 18.0, 17.0),
}

enum class AsrMadhab(val label: String, val shadowFactor: Int) {
    STANDARD("Standard (Shafi'i, Maliki, Hanbali)", 1),
    HANAFI("Hanafi", 2),
}

enum class HighLatRule(val label: String) {
    AUTO("Automatic (recommended for latitude)"),
    MIDDLE_OF_THE_NIGHT("Middle of the night"),
    SEVENTH_OF_THE_NIGHT("Seventh of the night"),
    TWILIGHT_ANGLE("Twilight angle"),
}

data class PrayerSettings(
    val method: CalcMethod = CalcMethod.NORTH_AMERICA,
    val madhab: AsrMadhab = AsrMadhab.STANDARD,
    val highLatitudeRule: HighLatRule = HighLatRule.AUTO,
    val adjustments: Map<PrayerEvent, Int> = emptyMap(),
    val customFajrAngle: Double = 18.0,
    val customIshaAngle: Double = 17.0,
    val customIshaIntervalMinutes: Int = 0,
    val duhaOffsetMinutes: Int = 20,
) {
    fun adjustment(event: PrayerEvent): Int = adjustments[event] ?: 0

    companion object {
        const val ADJUSTMENT_RANGE = 30
        val DUHA_OFFSET_RANGE = 10..60
        val ANGLE_RANGE = 10.0..25.0
        val ISHA_INTERVAL_RANGE = 0..150
    }
}

data class GeoLocation(
    val latitude: Double,
    val longitude: Double,
    val name: String,
    val zoneId: String,
) {
    val zone: ZoneId get() = runCatching { ZoneId.of(zoneId) }.getOrDefault(ZoneId.systemDefault())
}

data class DayEvent(
    val event: PrayerEvent,
    val time: Instant,
    val scheduleDate: LocalDate,
) {
    val isJumuah: Boolean get() = event == PrayerEvent.DHUHR && scheduleDate.dayOfWeek == DayOfWeek.FRIDAY
    val displayName: String get() = if (isJumuah) "Jumu'ah" else event.label
}

data class DaySchedule(
    val date: LocalDate,
    val zone: ZoneId,
    val events: List<DayEvent>,
) {
    fun timeOf(event: PrayerEvent): Instant? = events.firstOrNull { it.event == event }?.time
}

enum class LocationMode { GPS, MANUAL }

enum class TemperatureUnit { FAHRENHEIT, CELSIUS }

enum class WindUnit { MPH, KMH }

enum class ClockFormat(val label: String) {
    SYSTEM("System default"),
    H12("12-hour (3:45 PM)"),
    H24("24-hour (15:45)"),
}

enum class ReadingDayStart(val label: String) {
    MIDNIGHT("Midnight"),
    FAJR("Fajr"),
}

enum class WeekStartSetting(val label: String) {
    LOCALE_DEFAULT("System default"),
    SATURDAY("Saturday"),
    SUNDAY("Sunday"),
    MONDAY("Monday");

    fun resolveDayOfWeek(locale: Locale = Locale.getDefault()): DayOfWeek = when (this) {
        LOCALE_DEFAULT -> WeekFields.of(locale).firstDayOfWeek
        SATURDAY -> DayOfWeek.SATURDAY
        SUNDAY -> DayOfWeek.SUNDAY
        MONDAY -> DayOfWeek.MONDAY
    }
}

enum class MonthCalendarType(val label: String) {
    GREGORIAN("Gregorian"),
    HIJRI("Hijri"),
}

enum class GoalPeriod { DAILY, WEEKLY, MONTHLY }

enum class GoalMetric(val label: String) {
    AYAHS("Ayahs"),
    WORDS("Words"),
    MINUTES("Minutes"),
}

data class QuranTrackerSettings(
    val dayStart: ReadingDayStart = ReadingDayStart.MIDNIGHT,
    val weekStart: WeekStartSetting = WeekStartSetting.LOCALE_DEFAULT,
    val monthType: MonthCalendarType = MonthCalendarType.GREGORIAN,
    val dailyGoalMetric: GoalMetric = GoalMetric.AYAHS,
    val dailyGoalTarget: Int = 20,
    val weeklyGoalMetric: GoalMetric = GoalMetric.AYAHS,
    val weeklyGoalTarget: Int = 140,
    val monthlyGoalMetric: GoalMetric = GoalMetric.AYAHS,
    val monthlyGoalTarget: Int = 600,
    val speedReadWpm: Int = 250,
    val speedReadTheme: SpeedReadTheme = SpeedReadTheme.WARM_SEPIA,
    val focusedReadMode: FocusedReadMode = FocusedReadMode.ARABIC_WBW,
)

data class AppSettings(
    val locationMode: LocationMode = LocationMode.GPS,
    val manualPostalCode: String = "",
    val manualCountryCode: String = "US",
    val location: GeoLocation? = null,
    val prayer: PrayerSettings = PrayerSettings(),
    val alertEnabled: Map<PrayerEvent, Boolean> = defaultAlerts(),
    val temperatureUnit: TemperatureUnit = TemperatureUnit.FAHRENHEIT,
    val windUnit: WindUnit = WindUnit.MPH,
    val clockFormat: ClockFormat = ClockFormat.SYSTEM,
    val hijriOffsetDays: Int = 0,
    val quran: QuranTrackerSettings = QuranTrackerSettings(),
) {
    fun isAlertEnabled(event: PrayerEvent): Boolean = alertEnabled[event] ?: event.isRequiredPrayer

    companion object {
        fun defaultAlerts(): Map<PrayerEvent, Boolean> =
            PrayerEvent.entries.associateWith { it.isRequiredPrayer }
    }
}
