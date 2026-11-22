package com.quranwbw.android.ui.screens

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.quranwbw.android.dtadhan.model.AppSettings
import com.quranwbw.android.dtadhan.model.ClockFormat
import com.quranwbw.android.dtadhan.model.DayEvent
import com.quranwbw.android.dtadhan.model.DaySchedule
import com.quranwbw.android.dtadhan.model.PrayerEvent
import com.quranwbw.android.dtadhan.prayer.DayBrowser
import com.quranwbw.android.dtadhan.prayer.HijriFormatter
import com.quranwbw.android.dtadhan.prayer.NextEventFinder
import com.quranwbw.android.dtadhan.prayer.PrayerCalculator
import com.quranwbw.android.dtadhan.qibla.HeadingMath
import com.quranwbw.android.dtadhan.qibla.QiblaCalculator
import com.quranwbw.android.dtadhan.weather.Units
import com.quranwbw.android.dtadhan.weather.WeatherCodes
import com.quranwbw.android.dtadhan.weather.WeatherSnapshot
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

@Composable
fun rememberTimeFormatter(clockFormat: ClockFormat): DateTimeFormatter {
    val context = LocalContext.current
    val is24Hour = remember(clockFormat, context) {
        when (clockFormat) {
            ClockFormat.H24 -> true
            ClockFormat.H12 -> false
            ClockFormat.SYSTEM -> DateFormat.is24HourFormat(context)
        }
    }
    return remember(is24Hour) {
        DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "h:mm a")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    settings: AppSettings,
    weather: WeatherSnapshot?,
    isRefreshing: Boolean,
    statusMessage: String?,
    onRefresh: () -> Unit,
    onUseGps: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenQibla: () -> Unit,
    onToggleAlert: (PrayerEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000L)
            now = Instant.now()
        }
    }

    var selectedDate by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    val formatter = rememberTimeFormatter(settings.clockFormat)
    val location = settings.location
    val zone = location?.zone ?: ZoneId.systemDefault()
    val todayDate = now.atZone(zone).toLocalDate()
    val isRamadan = HijriFormatter.isRamadan(todayDate, settings.hijriOffsetDays)

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (isRamadan) "Adhan Times 🌙 Ramadan" else "Adhan & Prayer Times",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        location?.let {
                            Text(
                                text = it.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                actions = {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(24.dp)
                                .padding(2.dp),
                            strokeWidth = 2.dp,
                        )
                    } else if (location != null) {
                        IconButton(onClick = onRefresh) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Refresh location and weather")
                        }
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { padding ->
        if (location == null) {
            SetupLocationCard(
                modifier = Modifier.padding(padding),
                statusMessage = statusMessage,
                onUseGps = onUseGps,
                onEnterPostalCode = onOpenSettings,
            )
            return@Scaffold
        }

        val schedules: List<DaySchedule> = remember(location, settings.prayer, todayDate) {
            PrayerCalculator.window(now, location, settings.prayer)
        }
        val next = NextEventFinder.next(now, schedules)
        val todaySchedule = schedules.firstOrNull { it.date == todayDate }
        val upcomingDay = next?.let { n -> schedules.firstOrNull { it.date == n.scheduleDate } } ?: todaySchedule
        val browsedSchedule = remember(selectedDate, location, settings.prayer) {
            selectedDate?.let { d -> PrayerCalculator.day(d, location, settings.prayer) }
        }
        val listDay = browsedSchedule ?: upcomingDay

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!statusMessage.isNullOrBlank()) {
                item(key = "status_msg") {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = statusMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            }

            item(key = "next") {
                NextPrayerCard(
                    next = next,
                    now = now,
                    zone = zone,
                    formatter = formatter,
                    hijriOffset = settings.hijriOffsetDays,
                )
            }

            if (weather != null && weather.isFor(location)) {
                item(key = "weather") {
                    WeatherCard(weather = weather, settings = settings, now = now)
                }
            }

            if (listDay != null) {
                item(key = "list_title") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = DayBrowser.relativeLabel(listDay.date, todayDate),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        val qibla = remember(location.latitude, location.longitude) {
                            QiblaCalculator.calculate(location)
                        }
                        val label = "${qibla.bearingDegrees.roundToInt()}° ${HeadingMath.compassDirectionLabel(qibla.bearingDegrees)}"
                        AssistChip(
                            onClick = onOpenQibla,
                            label = { Text("Qibla $label") },
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.Explore,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                        )
                    }
                }

                item(key = "day_nav") {
                    DayNavigator(
                        date = listDay.date,
                        today = todayDate,
                        hijriOffset = settings.hijriOffsetDays,
                        browsing = selectedDate != null,
                        onSelect = { d ->
                            selectedDate = if (d == upcomingDay?.date) null else d
                        },
                        onReset = { selectedDate = null },
                    )
                }

                val mainEvents = listDay.events.filter { it.event in PrayerEvent.COUNTDOWN_TARGETS }
                items(mainEvents, key = { "${it.scheduleDate}-${it.event}" }) { e ->
                    PrayerRow(
                        event = e,
                        isNext = e == next,
                        formatter = formatter,
                        zone = zone,
                        alertOn = settings.isAlertEnabled(e.event),
                        onToggle = onToggleAlert,
                    )
                }

                item(key = "optional_title") {
                    Text(
                        text = "Optional & Sunnah Times",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                val optionalEvents = listDay.events.filter { it.event !in PrayerEvent.COUNTDOWN_TARGETS }
                items(optionalEvents, key = { "${it.scheduleDate}-${it.event}" }) { e ->
                    PrayerRow(
                        event = e,
                        isNext = false,
                        formatter = formatter,
                        zone = zone,
                        alertOn = settings.isAlertEnabled(e.event),
                        onToggle = onToggleAlert,
                    )
                }
            }

            item(key = "footer") {
                Text(
                    text = "${settings.prayer.method.label} · Asr ${settings.prayer.madhab.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun SetupLocationCard(
    modifier: Modifier,
    statusMessage: String?,
    onUseGps: () -> Unit,
    onEnterPostalCode: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Where are you?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.size(8.dp))
        Text(
            text = "Prayer times, Qibla bearing, and weather depend on your location. Use your phone's GPS location, or enter a ZIP / postal code in Settings.",
            style = MaterialTheme.typography.bodyMedium,
        )
        if (!statusMessage.isNullOrBlank()) {
            Spacer(Modifier.size(12.dp))
            Text(
                text = statusMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Spacer(Modifier.size(24.dp))
        Button(
            onClick = onUseGps,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp),
        ) {
            Text("Use my location (GPS)")
        }
        Spacer(Modifier.size(8.dp))
        OutlinedButton(
            onClick = onEnterPostalCode,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp),
        ) {
            Text("Enter ZIP / postal code in Settings")
        }
    }
}

@Composable
private fun NextPrayerCard(
    next: DayEvent?,
    now: Instant,
    zone: ZoneId,
    formatter: DateTimeFormatter,
    hijriOffset: Int,
) {
    val date = now.atZone(zone).toLocalDate()
    val hijri = HijriFormatter.of(date, hijriOffset)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                text = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)) +
                    (hijri?.let { "\n$it" } ?: ""),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.size(12.dp))
            if (next == null) {
                Text("Calculating…", style = MaterialTheme.typography.titleLarge)
            } else {
                Text(
                    text = "Next: ${next.displayName}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = formatter.format(next.time.atZone(zone)),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = "in " + NextEventFinder.formatRemaining(Duration.between(now, next.time)),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun WeatherCard(
    weather: WeatherSnapshot,
    settings: AppSettings,
    now: Instant,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = WeatherCodes.emoji(weather.condition.kind, weather.isDay),
                style = MaterialTheme.typography.displaySmall,
            )
            Spacer(Modifier.size(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = Units.temperatureLabel(weather.temperatureC, settings.temperatureUnit),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(weather.condition.label, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "Feels ${Units.temperatureLabel(weather.feelsLikeC, settings.temperatureUnit)} · " +
                        "Humidity ${weather.humidityPercent}% · " +
                        "Wind ${Units.windLabel(weather.windKmh, settings.windUnit)}",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (weather.highC != null && weather.lowC != null) {
                    Text(
                        text = "High ${Units.temperatureLabel(weather.highC, settings.temperatureUnit)} · " +
                            "Low ${Units.temperatureLabel(weather.lowC, settings.temperatureUnit)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                val age = Duration.between(weather.fetchedAt, now)
                if (weather.isStale(now)) {
                    Text(
                        text = "Not updated for ${age.toHours()} h (offline?)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun PrayerRow(
    event: DayEvent,
    isNext: Boolean,
    formatter: DateTimeFormatter,
    zone: ZoneId,
    alertOn: Boolean,
    onToggle: (PrayerEvent) -> Unit,
) {
    val highlight = if (isNext) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = highlight),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = event.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isNext) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = formatter.format(event.time.atZone(zone)),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isNext) FontWeight.Bold else FontWeight.Normal,
            )
            IconButton(onClick = { onToggle(event.event) }) {
                Icon(
                    imageVector = if (alertOn) Icons.Filled.NotificationsActive else Icons.Outlined.NotificationsOff,
                    contentDescription = if (alertOn) "Alert on for ${event.displayName}" else "Alert off for ${event.displayName}",
                    tint = if (alertOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayNavigator(
    date: LocalDate,
    today: LocalDate,
    hijriOffset: Int,
    browsing: Boolean,
    onSelect: (LocalDate) -> Unit,
    onReset: () -> Unit,
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    val last = DayBrowser.lastDay(today)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            IconButton(
                onClick = { onSelect(date.minusDays(1)) },
                enabled = date.isAfter(today),
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous day")
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clickable { picking = true }
                    .padding(vertical = 8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(DayBrowser.dateLabel(date), style = MaterialTheme.typography.titleSmall)
                }
                HijriFormatter.of(date, hijriOffset)?.let {
                    Text(
                        text = it.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(
                onClick = { onSelect(date.plusDays(1)) },
                enabled = date.isBefore(last),
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next day")
            }
        }
        if (browsing) {
            TextButton(
                onClick = onReset,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text("Back to today")
            }
        }
    }
    if (picking) {
        val years = today.year..last.year
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = DayBrowser.toPickerMillis(date),
            yearRange = years,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) =
                    DayBrowser.isSelectable(DayBrowser.fromPickerMillis(utcTimeMillis), today)
                override fun isSelectableYear(year: Int) = year in years
            },
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { onSelect(DayBrowser.fromPickerMillis(it)) }
                        picking = false
                    }
                ) {
                    Text("Show")
                }
            },
            dismissButton = {
                TextButton(onClick = { picking = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}
