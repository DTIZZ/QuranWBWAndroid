package com.quranwbw.android.ui.screens

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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.quranwbw.android.dtadhan.model.AppSettings
import com.quranwbw.android.dtadhan.model.AsrMadhab
import com.quranwbw.android.dtadhan.model.CalcMethod
import com.quranwbw.android.dtadhan.model.ClockFormat
import com.quranwbw.android.dtadhan.model.GeoLocation
import com.quranwbw.android.dtadhan.model.GoalMetric
import com.quranwbw.android.dtadhan.model.HighLatRule
import com.quranwbw.android.dtadhan.model.LocationMode
import com.quranwbw.android.dtadhan.model.MonthCalendarType
import com.quranwbw.android.dtadhan.model.PrayerEvent
import com.quranwbw.android.dtadhan.model.PrayerSettings
import com.quranwbw.android.dtadhan.model.ReadingDayStart
import com.quranwbw.android.dtadhan.model.TemperatureUnit
import com.quranwbw.android.dtadhan.model.WeekStartSetting
import com.quranwbw.android.dtadhan.model.WindUnit
import com.quranwbw.android.dtadhan.prayer.HijriFormatter
import com.quranwbw.android.dtadhan.weather.PostalCodes
import com.quranwbw.android.speedread.FocusedReadMode
import com.quranwbw.android.speedread.SpeedReadTheme
import com.quranwbw.android.speedread.SpeedReadTokenizer
import kotlin.math.roundToInt

private val PRESET_CITIES = listOf(
    GeoLocation(21.4225, 39.8262, "Makkah, Saudi Arabia", "Asia/Riyadh"),
    GeoLocation(24.4672, 39.6112, "Madinah, Saudi Arabia", "Asia/Riyadh"),
    GeoLocation(40.7128, -74.0060, "New York, NY", "America/New_York"),
    GeoLocation(41.8781, -87.6298, "Chicago, IL", "America/Chicago"),
    GeoLocation(34.0522, -118.2437, "Los Angeles, CA", "America/Los_Angeles"),
    GeoLocation(51.5074, -0.1278, "London, UK", "Europe/London"),
    GeoLocation(43.6532, -79.3832, "Toronto, Canada", "America/Toronto"),
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    isBusy: Boolean,
    statusMessage: String?,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
    onUseGps: () -> Unit,
    onUsePostalCode: (String, String) -> Unit,
    onSelectPresetLocation: (GeoLocation) -> Unit,
    onClearReadingStats: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var zip by rememberSaveable(settings.manualPostalCode) { mutableStateOf(settings.manualPostalCode) }
    var country by rememberSaveable(settings.manualCountryCode) { mutableStateOf(settings.manualCountryCode) }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (isBusy) {
                item(key = "busy") {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
            if (!statusMessage.isNullOrBlank()) {
                item(key = "status") {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = statusMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            }

            // 1. Location Section
            item(key = "location") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Location", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        settings.location?.let { loc ->
                            Text(
                                text = "${loc.name} (${ "%.3f".format(loc.latitude) }, ${ "%.3f".format(loc.longitude) })\nTime zone: ${loc.zoneId}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        } ?: Text(
                            text = "No location set yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = settings.locationMode == LocationMode.GPS,
                                onClick = {
                                    onUpdateSettings { it.copy(locationMode = LocationMode.GPS) }
                                    onUseGps()
                                },
                                label = { Text("Phone GPS") },
                            )
                            FilterChip(
                                selected = settings.locationMode == LocationMode.MANUAL,
                                onClick = {
                                    onUpdateSettings { it.copy(locationMode = LocationMode.MANUAL) }
                                },
                                label = { Text("ZIP / Postal Code") },
                            )
                        }

                        if (settings.locationMode == LocationMode.MANUAL) {
                            DropdownField(
                                label = "Country",
                                options = PostalCodes.COUNTRIES.map { it.first },
                                selected = country,
                                display = { code -> PostalCodes.COUNTRIES.firstOrNull { it.first == code }?.second ?: code },
                                onSelect = { country = it },
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = zip,
                                    onValueChange = { zip = it.take(10) },
                                    label = { Text("ZIP / postal code") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(
                                        capitalization = KeyboardCapitalization.Characters,
                                        imeAction = ImeAction.Search,
                                    ),
                                    keyboardActions = KeyboardActions(onSearch = { onUsePostalCode(zip, country) }),
                                    modifier = Modifier.weight(1f),
                                )
                                Spacer(Modifier.width(8.dp))
                                Button(onClick = { onUsePostalCode(zip, country) }) {
                                    Text("Set")
                                }
                            }
                        } else {
                            OutlinedButton(onClick = onUseGps) {
                                Text("Update GPS Location Now")
                            }
                        }

                        Text("Quick city presets:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            PRESET_CITIES.forEach { preset ->
                                AssistChip(
                                    onClick = { onSelectPresetLocation(preset) },
                                    label = { Text(preset.name.substringBefore(",")) },
                                )
                            }
                        }
                    }
                }
            }

            // 2. Prayer Calculation Section
            item(key = "prayer_calc") {
                val p = settings.prayer
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Prayer Time Calculation", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                        DropdownField(
                            label = "Calculation method",
                            options = CalcMethod.entries,
                            selected = p.method,
                            display = { it.label },
                            onSelect = { m -> onUpdateSettings { it.copy(prayer = it.prayer.copy(method = m)) } },
                        )

                        if (p.method == CalcMethod.CUSTOM) {
                            StepperRow(
                                label = "Fajr angle",
                                value = p.customFajrAngle.roundToInt(),
                                range = 10..25,
                                unit = "°",
                                onChange = { v -> onUpdateSettings { it.copy(prayer = it.prayer.copy(customFajrAngle = v.toDouble())) } },
                            )
                            StepperRow(
                                label = "Isha angle",
                                value = p.customIshaAngle.roundToInt(),
                                range = 10..25,
                                unit = "°",
                                onChange = { v -> onUpdateSettings { it.copy(prayer = it.prayer.copy(customIshaAngle = v.toDouble())) } },
                            )
                        }

                        Text("Asr juristic method (Madhab)", style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AsrMadhab.entries.forEach { m ->
                                FilterChip(
                                    selected = p.madhab == m,
                                    onClick = { onUpdateSettings { it.copy(prayer = it.prayer.copy(madhab = m)) } },
                                    label = { Text(if (m == AsrMadhab.STANDARD) "Standard (Shafi'i)" else "Hanafi") },
                                )
                            }
                        }

                        DropdownField(
                            label = "High-latitude rule",
                            options = HighLatRule.entries,
                            selected = p.highLatitudeRule,
                            display = { it.label },
                            onSelect = { r -> onUpdateSettings { it.copy(prayer = it.prayer.copy(highLatitudeRule = r)) } },
                        )

                        HorizontalDivider()
                        Text("Per-prayer adjustments (minutes)", style = MaterialTheme.typography.labelLarge)
                        PrayerEvent.ADJUSTABLE.forEach { ev ->
                            StepperRow(
                                label = ev.label,
                                value = p.adjustment(ev),
                                range = -PrayerSettings.ADJUSTMENT_RANGE..PrayerSettings.ADJUSTMENT_RANGE,
                                unit = " min",
                                onChange = { v ->
                                    onUpdateSettings {
                                        it.copy(prayer = it.prayer.copy(adjustments = it.prayer.adjustments + (ev to v)))
                                    }
                                },
                            )
                        }
                        StepperRow(
                            label = "Duha after Sunrise",
                            value = p.duhaOffsetMinutes,
                            range = PrayerSettings.DUHA_OFFSET_RANGE,
                            unit = " min",
                            onChange = { v -> onUpdateSettings { it.copy(prayer = it.prayer.copy(duhaOffsetMinutes = v)) } },
                        )
                    }
                }
            }

            // 3. Display & Units Section
            item(key = "display_units") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Display & Calendar", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                        Text("Temperature unit", style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TemperatureUnit.entries.forEach { u ->
                                FilterChip(
                                    selected = settings.temperatureUnit == u,
                                    onClick = { onUpdateSettings { it.copy(temperatureUnit = u) } },
                                    label = { Text(if (u == TemperatureUnit.FAHRENHEIT) "Fahrenheit (°F)" else "Celsius (°C)") },
                                )
                            }
                        }

                        Text("Distance & wind unit", style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            WindUnit.entries.forEach { u ->
                                FilterChip(
                                    selected = settings.windUnit == u,
                                    onClick = { onUpdateSettings { it.copy(windUnit = u) } },
                                    label = { Text(if (u == WindUnit.MPH) "Miles (mph / mi)" else "Kilometres (km/h / km)") },
                                )
                            }
                        }

                        DropdownField(
                            label = "Clock format",
                            options = ClockFormat.entries,
                            selected = settings.clockFormat,
                            display = { it.label },
                            onSelect = { c -> onUpdateSettings { it.copy(clockFormat = c) } },
                        )

                        StepperRow(
                            label = "Hijri calendar adjustment",
                            value = settings.hijriOffsetDays,
                            range = -HijriFormatter.MAX_OFFSET..HijriFormatter.MAX_OFFSET,
                            unit = " days",
                            onChange = { v -> onUpdateSettings { it.copy(hijriOffsetDays = v) } },
                        )
                    }
                }
            }

            // 4. Qur'an Tracker & Speed Reading Section
            item(key = "quran_settings") {
                val q = settings.quran
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Qur'an Reading & Speed Reader", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                        Text("Speed reading pace: ${q.speedReadWpm} WPM", style = MaterialTheme.typography.bodyMedium)
                        Slider(
                            value = q.speedReadWpm.toFloat(),
                            onValueChange = { v ->
                                val stepped = ((v / 10f).roundToInt() * 10)
                                    .coerceIn(SpeedReadTokenizer.MIN_SPEED_WPM, SpeedReadTokenizer.MAX_SPEED_WPM)
                                onUpdateSettings { it.copy(quran = it.quran.copy(speedReadWpm = stepped)) }
                            },
                            valueRange = SpeedReadTokenizer.MIN_SPEED_WPM.toFloat()..SpeedReadTokenizer.MAX_SPEED_WPM.toFloat(),
                        )

                        DropdownField(
                            label = "Speed reader color theme",
                            options = SpeedReadTheme.entries,
                            selected = q.speedReadTheme,
                            display = { it.label },
                            onSelect = { t -> onUpdateSettings { it.copy(quran = it.quran.copy(speedReadTheme = t)) } },
                        )

                        DropdownField(
                            label = "Default speed reader mode",
                            options = FocusedReadMode.entries,
                            selected = q.focusedReadMode,
                            display = { it.label },
                            onSelect = { m -> onUpdateSettings { it.copy(quran = it.quran.copy(focusedReadMode = m)) } },
                        )

                        HorizontalDivider()

                        DropdownField(
                            label = "Reading day starts at",
                            options = ReadingDayStart.entries,
                            selected = q.dayStart,
                            display = { it.label },
                            onSelect = { d -> onUpdateSettings { it.copy(quran = it.quran.copy(dayStart = d)) } },
                        )

                        DropdownField(
                            label = "Week starts on",
                            options = WeekStartSetting.entries,
                            selected = q.weekStart,
                            display = { it.label },
                            onSelect = { w -> onUpdateSettings { it.copy(quran = it.quran.copy(weekStart = w)) } },
                        )

                        DropdownField(
                            label = "Monthly statistics calendar",
                            options = MonthCalendarType.entries,
                            selected = q.monthType,
                            display = { it.label },
                            onSelect = { m -> onUpdateSettings { it.copy(quran = it.quran.copy(monthType = m)) } },
                        )

                        DropdownField(
                            label = "Daily goal metric",
                            options = GoalMetric.entries,
                            selected = q.dailyGoalMetric,
                            display = { it.label },
                            onSelect = { gm -> onUpdateSettings { it.copy(quran = it.quran.copy(dailyGoalMetric = gm)) } },
                        )

                        StepperRow(
                            label = "Daily goal target (${q.dailyGoalMetric.label.lowercase()})",
                            value = q.dailyGoalTarget,
                            range = 0..500,
                            unit = "",
                            step = 5,
                            onChange = { v -> onUpdateSettings { it.copy(quran = it.quran.copy(dailyGoalTarget = v)) } },
                        )

                        OutlinedButton(onClick = onClearReadingStats) {
                            Text("Reset Reading Statistics")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun <T> DropdownField(
    label: String,
    options: List<T>,
    selected: T,
    display: (T) -> String,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(display(selected))
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(display(option)) },
                        onClick = {
                            expanded = false
                            onSelect(option)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun StepperRow(
    label: String,
    value: Int,
    range: IntRange,
    unit: String,
    step: Int = 1,
    onChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = { onChange((value - step).coerceIn(range)) },
                enabled = value > range.first,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text("-")
            }
            Text(
                text = "$value$unit",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            OutlinedButton(
                onClick = { onChange((value + step).coerceIn(range)) },
                enabled = value < range.last,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text("+")
            }
        }
    }
}
