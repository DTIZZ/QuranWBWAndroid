package com.quranwbw.android.dtadhan.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
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
import com.quranwbw.android.dtadhan.model.QuranTrackerSettings
import com.quranwbw.android.dtadhan.model.ReadingDayStart
import com.quranwbw.android.dtadhan.model.TemperatureUnit
import com.quranwbw.android.dtadhan.model.WeekStartSetting
import com.quranwbw.android.dtadhan.model.WindUnit
import com.quranwbw.android.dtadhan.stats.AyahReadRecord
import com.quranwbw.android.dtadhan.stats.ReadingDayResolver
import com.quranwbw.android.dtadhan.stats.ReadingSessionRecord
import com.quranwbw.android.dtadhan.weather.OpenMeteo
import com.quranwbw.android.dtadhan.weather.PostalCodes
import com.quranwbw.android.dtadhan.weather.PostalPlace
import com.quranwbw.android.dtadhan.weather.WeatherSnapshot
import com.quranwbw.android.speedread.FocusedReadMode
import com.quranwbw.android.speedread.SpeedReadTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.coroutines.resume

class DtAdhanRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("dtadhan_store", Context.MODE_PRIVATE)

    fun loadSettings(): AppSettings {
        val raw = prefs.getString(KEY_SETTINGS, null) ?: return AppSettings()
        return runCatching { decodeSettings(JSONObject(raw)) }.getOrDefault(AppSettings())
    }

    fun saveSettings(settings: AppSettings) {
        prefs.edit().putString(KEY_SETTINGS, encodeSettings(settings).toString()).apply()
    }

    fun loadWeather(): WeatherSnapshot? {
        val raw = prefs.getString(KEY_WEATHER, null) ?: return null
        return runCatching {
            val obj = JSONObject(raw)
            WeatherSnapshot(
                temperatureC = obj.getDouble("temperatureC"),
                feelsLikeC = obj.getDouble("feelsLikeC"),
                weatherCode = obj.getInt("weatherCode"),
                isDay = obj.getBoolean("isDay"),
                humidityPercent = obj.getInt("humidityPercent"),
                windKmh = obj.getDouble("windKmh"),
                highC = if (obj.isNull("highC")) null else obj.getDouble("highC"),
                lowC = if (obj.isNull("lowC")) null else obj.getDouble("lowC"),
                zoneId = obj.optString("zoneId", ZoneId.systemDefault().id),
                latitude = obj.getDouble("latitude"),
                longitude = obj.getDouble("longitude"),
                fetchedAtEpochMs = obj.getLong("fetchedAtEpochMs"),
            )
        }.getOrNull()
    }

    fun saveWeather(w: WeatherSnapshot) {
        val obj = JSONObject().apply {
            put("temperatureC", w.temperatureC)
            put("feelsLikeC", w.feelsLikeC)
            put("weatherCode", w.weatherCode)
            put("isDay", w.isDay)
            put("humidityPercent", w.humidityPercent)
            put("windKmh", w.windKmh)
            put("highC", w.highC ?: JSONObject.NULL)
            put("lowC", w.lowC ?: JSONObject.NULL)
            put("zoneId", w.zoneId)
            put("latitude", w.latitude)
            put("longitude", w.longitude)
            put("fetchedAtEpochMs", w.fetchedAtEpochMs)
        }
        prefs.edit().putString(KEY_WEATHER, obj.toString()).apply()
    }

    fun loadAyahReads(): List<AyahReadRecord> {
        val raw = prefs.getString(KEY_AYAH_READS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList(arr.length()) {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        AyahReadRecord(
                            sessionId = o.optString("sessionId", "s"),
                            surah = o.getInt("surah"),
                            ayah = o.getInt("ayah"),
                            readAtUtc = Instant.ofEpochMilli(o.getLong("readAtMs")),
                            localDate = LocalDate.parse(o.getString("localDate")),
                            wordsEn = o.getInt("wordsEn"),
                            wordsAr = o.getInt("wordsAr"),
                            dwellMs = o.optLong("dwellMs", 0L),
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun saveAyahReads(reads: List<AyahReadRecord>) {
        val arr = JSONArray()
        for (r in reads) {
            arr.put(
                JSONObject().apply {
                    put("sessionId", r.sessionId)
                    put("surah", r.surah)
                    put("ayah", r.ayah)
                    put("readAtMs", r.readAtUtc.toEpochMilli())
                    put("localDate", r.localDate.toString())
                    put("wordsEn", r.wordsEn)
                    put("wordsAr", r.wordsAr)
                    put("dwellMs", r.dwellMs)
                }
            )
        }
        prefs.edit().putString(KEY_AYAH_READS, arr.toString()).apply()
    }

    fun loadSessions(): List<ReadingSessionRecord> {
        val raw = prefs.getString(KEY_SESSIONS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList(arr.length()) {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        ReadingSessionRecord(
                            id = o.getString("id"),
                            startUtc = Instant.ofEpochMilli(o.getLong("startMs")),
                            endUtc = Instant.ofEpochMilli(o.getLong("endMs")),
                            activeSeconds = o.getInt("activeSeconds"),
                            localDate = LocalDate.parse(o.getString("localDate")),
                            zoneId = o.optString("zoneId", ZoneId.systemDefault().id),
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun saveSessions(sessions: List<ReadingSessionRecord>) {
        val arr = JSONArray()
        for (s in sessions) {
            arr.put(
                JSONObject().apply {
                    put("id", s.id)
                    put("startMs", s.startUtc.toEpochMilli())
                    put("endMs", s.endUtc.toEpochMilli())
                    put("activeSeconds", s.activeSeconds)
                    put("localDate", s.localDate.toString())
                    put("zoneId", s.zoneId)
                }
            )
        }
        prefs.edit().putString(KEY_SESSIONS, arr.toString()).apply()
    }

    fun clearAllReadingStats() {
        prefs.edit().remove(KEY_AYAH_READS).remove(KEY_SESSIONS).apply()
    }

    fun recordAyahRead(
        surah: Int,
        ayah: Int,
        wordsEn: Int,
        wordsAr: Int,
        dwellMs: Long,
        settings: AppSettings,
    ): Pair<List<AyahReadRecord>, List<ReadingSessionRecord>> {
        val now = Instant.now()
        val localDate = ReadingDayResolver.resolveReadingDate(
            instant = now,
            location = settings.location,
            prayerSettings = settings.prayer,
            dayStart = settings.quran.dayStart,
        )
        val zoneId = (settings.location?.zone ?: ZoneId.systemDefault()).id
        val sessionId = "session-$localDate"

        val currentReads = loadAyahReads().toMutableList()
        currentReads.add(
            AyahReadRecord(
                sessionId = sessionId,
                surah = surah,
                ayah = ayah,
                readAtUtc = now,
                localDate = localDate,
                wordsEn = wordsEn,
                wordsAr = wordsAr,
                dwellMs = dwellMs,
            )
        )
        saveAyahReads(currentReads)

        val addSeconds = ((dwellMs.coerceAtLeast(3_000L)) / 1000L).toInt().coerceIn(1, 300)
        val currentSessions = loadSessions().toMutableList()
        val idx = currentSessions.indexOfFirst { it.id == sessionId }
        if (idx >= 0) {
            val existing = currentSessions[idx]
            currentSessions[idx] = existing.copy(
                endUtc = now,
                activeSeconds = existing.activeSeconds + addSeconds,
            )
        } else {
            currentSessions.add(
                ReadingSessionRecord(
                    id = sessionId,
                    startUtc = now,
                    endUtc = now,
                    activeSeconds = addSeconds,
                    localDate = localDate,
                    zoneId = zoneId,
                )
            )
        }
        saveSessions(currentSessions)
        return currentReads to currentSessions
    }

    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    suspend fun resolveGpsLocation(): GeoLocation = withContext(Dispatchers.IO) {
        if (!hasLocationPermission()) {
            throw IllegalStateException("Location permission was not granted")
        }
        val manager = context.getSystemService(LocationManager::class.java)
            ?: throw IllegalStateException("Location is not available on this device")
        if (!LocationManagerCompat.isLocationEnabled(manager)) {
            throw IllegalStateException("Location is turned off. Turn it on, or use a ZIP / postal code.")
        }

        val providers = buildList {
            if (Build.VERSION.SDK_INT >= 31) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.GPS_PROVIDER)
        }.filter { it in manager.allProviders && manager.isProviderEnabled(it) }

        var found: Location? = null
        for (provider in providers) {
            val fix = withTimeoutOrNull(10_000L) {
                suspendCancellableCoroutine<Location?> { cont ->
                    val cancel = CancellationSignal()
                    cont.invokeOnCancellation { cancel.cancel() }
                    LocationManagerCompat.getCurrentLocation(
                        manager,
                        provider,
                        cancel,
                        ContextCompat.getMainExecutor(context),
                    ) { loc -> if (cont.isActive) cont.resume(loc) }
                }
            }
            if (fix != null) {
                found = fix
                break
            }
        }

        val location = found ?: manager.allProviders
            .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
            ?: throw IllegalStateException("Could not get a GPS fix. Try again or enter a ZIP / postal code.")

        val name = reverseGeocodeName(location.latitude, location.longitude)
            ?: "%.3f, %.3f".format(Locale.US, location.latitude, location.longitude)
        val zoneId = ZoneId.systemDefault().id
        GeoLocation(
            latitude = location.latitude,
            longitude = location.longitude,
            name = name,
            zoneId = zoneId,
        )
    }

    suspend fun resolvePostalCode(rawCode: String, countryCode: String): GeoLocation = withContext(Dispatchers.IO) {
        val code = PostalCodes.normalize(rawCode, countryCode)
        val geocoded = forwardGeocode("$code, $countryCode")
        val place = geocoded ?: run {
            val body = httpGet(PostalCodes.zippopotamUrl(countryCode, code))
            PostalCodes.parseZippopotam(body, code)
        }
        // Try to get time zone from Open-Meteo or fallback to system default
        val zoneId = ZoneId.systemDefault().id
        GeoLocation(
            latitude = place.latitude,
            longitude = place.longitude,
            name = place.name,
            zoneId = zoneId,
        )
    }

    suspend fun refreshWeather(location: GeoLocation): WeatherSnapshot = withContext(Dispatchers.IO) {
        val body = httpGet(OpenMeteo.forecastUrl(location.latitude, location.longitude))
        val snapshot = OpenMeteo.parse(body, Instant.now()).copy(
            latitude = location.latitude,
            longitude = location.longitude,
        )
        saveWeather(snapshot)
        snapshot
    }

    private suspend fun forwardGeocode(query: String): PostalPlace? {
        if (!Geocoder.isPresent()) return null
        val addr = geocodeLookup { geocoder, done ->
            if (Build.VERSION.SDK_INT >= 33) {
                geocoder.getFromLocationName(query, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) = done(addresses.firstOrNull())
                    override fun onError(errorMessage: String?) = done(null)
                })
            } else {
                @Suppress("DEPRECATION")
                done(geocoder.getFromLocationName(query, 1)?.firstOrNull())
            }
        } ?: return null
        if (!addr.hasLatitude() || !addr.hasLongitude()) return null
        return PostalPlace(addr.latitude, addr.longitude, addr.displayName() ?: query)
    }

    private suspend fun reverseGeocodeName(lat: Double, lon: Double): String? {
        if (!Geocoder.isPresent()) return null
        return geocodeLookup { geocoder, done ->
            if (Build.VERSION.SDK_INT >= 33) {
                geocoder.getFromLocation(lat, lon, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) = done(addresses.firstOrNull())
                    override fun onError(errorMessage: String?) = done(null)
                })
            } else {
                @Suppress("DEPRECATION")
                done(geocoder.getFromLocation(lat, lon, 1)?.firstOrNull())
            }
        }?.displayName()
    }

    private suspend fun geocodeLookup(block: (Geocoder, (Address?) -> Unit) -> Unit): Address? =
        withTimeoutOrNull(8_000L) {
            suspendCancellableCoroutine { cont ->
                val done: (Address?) -> Unit = { if (cont.isActive) cont.resume(it) }
                try {
                    block(Geocoder(context, Locale.getDefault()), done)
                } catch (_: Exception) {
                    done(null)
                }
            }
        }

    private fun Address.displayName(): String? {
        val city = locality ?: subAdminArea ?: featureName
        val region = adminArea ?: countryName
        return listOfNotNull(city, region).distinct().joinToString(", ").ifBlank { null }
    }

    private fun httpGet(urlString: String): String {
        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("User-Agent", "QuranWBW-DTAdhan/1.1")
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) {
                throw IOException("HTTP $code from $urlString")
            }
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun encodeSettings(s: AppSettings): JSONObject = JSONObject().apply {
        put("locationMode", s.locationMode.name)
        put("manualPostalCode", s.manualPostalCode)
        put("manualCountryCode", s.manualCountryCode)
        s.location?.let { loc ->
            put(
                "location",
                JSONObject().apply {
                    put("latitude", loc.latitude)
                    put("longitude", loc.longitude)
                    put("name", loc.name)
                    put("zoneId", loc.zoneId)
                }
            )
        }
        put(
            "prayer",
            JSONObject().apply {
                put("method", s.prayer.method.name)
                put("madhab", s.prayer.madhab.name)
                put("highLatitudeRule", s.prayer.highLatitudeRule.name)
                put("customFajrAngle", s.prayer.customFajrAngle)
                put("customIshaAngle", s.prayer.customIshaAngle)
                put("customIshaIntervalMinutes", s.prayer.customIshaIntervalMinutes)
                put("duhaOffsetMinutes", s.prayer.duhaOffsetMinutes)
                val adjObj = JSONObject()
                s.prayer.adjustments.forEach { (k, v) -> adjObj.put(k.name, v) }
                put("adjustments", adjObj)
            }
        )
        val alertsObj = JSONObject()
        s.alertEnabled.forEach { (k, v) -> alertsObj.put(k.name, v) }
        put("alertEnabled", alertsObj)
        put("temperatureUnit", s.temperatureUnit.name)
        put("windUnit", s.windUnit.name)
        put("clockFormat", s.clockFormat.name)
        put("hijriOffsetDays", s.hijriOffsetDays)
        put(
            "quran",
            JSONObject().apply {
                put("dayStart", s.quran.dayStart.name)
                put("weekStart", s.quran.weekStart.name)
                put("monthType", s.quran.monthType.name)
                put("dailyGoalMetric", s.quran.dailyGoalMetric.name)
                put("dailyGoalTarget", s.quran.dailyGoalTarget)
                put("weeklyGoalMetric", s.quran.weeklyGoalMetric.name)
                put("weeklyGoalTarget", s.quran.weeklyGoalTarget)
                put("monthlyGoalMetric", s.quran.monthlyGoalMetric.name)
                put("monthlyGoalTarget", s.quran.monthlyGoalTarget)
                put("speedReadWpm", s.quran.speedReadWpm)
                put("speedReadTheme", s.quran.speedReadTheme.name)
                put("focusedReadMode", s.quran.focusedReadMode.name)
            }
        )
    }

    private fun decodeSettings(o: JSONObject): AppSettings {
        val locObj = o.optJSONObject("location")
        val loc = locObj?.let {
            GeoLocation(
                latitude = it.getDouble("latitude"),
                longitude = it.getDouble("longitude"),
                name = it.getString("name"),
                zoneId = it.optString("zoneId", ZoneId.systemDefault().id),
            )
        }
        val pObj = o.optJSONObject("prayer")
        val prayer = if (pObj != null) {
            val adjMap = mutableMapOf<PrayerEvent, Int>()
            val adjObj = pObj.optJSONObject("adjustments")
            if (adjObj != null) {
                PrayerEvent.entries.forEach { ev ->
                    if (adjObj.has(ev.name)) adjMap[ev] = adjObj.getInt(ev.name)
                }
            }
            PrayerSettings(
                method = runCatching { CalcMethod.valueOf(pObj.getString("method")) }.getOrDefault(CalcMethod.NORTH_AMERICA),
                madhab = runCatching { AsrMadhab.valueOf(pObj.getString("madhab")) }.getOrDefault(AsrMadhab.STANDARD),
                highLatitudeRule = runCatching { HighLatRule.valueOf(pObj.getString("highLatitudeRule")) }.getOrDefault(HighLatRule.AUTO),
                adjustments = adjMap,
                customFajrAngle = pObj.optDouble("customFajrAngle", 18.0),
                customIshaAngle = pObj.optDouble("customIshaAngle", 17.0),
                customIshaIntervalMinutes = pObj.optInt("customIshaIntervalMinutes", 0),
                duhaOffsetMinutes = pObj.optInt("duhaOffsetMinutes", 20),
            )
        } else {
            PrayerSettings()
        }

        val alertsMap = AppSettings.defaultAlerts().toMutableMap()
        val alertsObj = o.optJSONObject("alertEnabled")
        if (alertsObj != null) {
            PrayerEvent.entries.forEach { ev ->
                if (alertsObj.has(ev.name)) alertsMap[ev] = alertsObj.getBoolean(ev.name)
            }
        }

        val qObj = o.optJSONObject("quran")
        val quran = if (qObj != null) {
            QuranTrackerSettings(
                dayStart = runCatching { ReadingDayStart.valueOf(qObj.getString("dayStart")) }.getOrDefault(ReadingDayStart.MIDNIGHT),
                weekStart = runCatching { WeekStartSetting.valueOf(qObj.getString("weekStart")) }.getOrDefault(WeekStartSetting.LOCALE_DEFAULT),
                monthType = runCatching { MonthCalendarType.valueOf(qObj.getString("monthType")) }.getOrDefault(MonthCalendarType.GREGORIAN),
                dailyGoalMetric = runCatching { GoalMetric.valueOf(qObj.getString("dailyGoalMetric")) }.getOrDefault(GoalMetric.AYAHS),
                dailyGoalTarget = qObj.optInt("dailyGoalTarget", 20),
                weeklyGoalMetric = runCatching { GoalMetric.valueOf(qObj.getString("weeklyGoalMetric")) }.getOrDefault(GoalMetric.AYAHS),
                weeklyGoalTarget = qObj.optInt("weeklyGoalTarget", 140),
                monthlyGoalMetric = runCatching { GoalMetric.valueOf(qObj.getString("monthlyGoalMetric")) }.getOrDefault(GoalMetric.AYAHS),
                monthlyGoalTarget = qObj.optInt("monthlyGoalTarget", 600),
                speedReadWpm = qObj.optInt("speedReadWpm", 250),
                speedReadTheme = runCatching { SpeedReadTheme.valueOf(qObj.getString("speedReadTheme")) }.getOrDefault(SpeedReadTheme.WARM_SEPIA),
                focusedReadMode = runCatching { FocusedReadMode.valueOf(qObj.getString("focusedReadMode")) }.getOrDefault(FocusedReadMode.ARABIC_WBW),
            )
        } else {
            QuranTrackerSettings()
        }

        return AppSettings(
            locationMode = runCatching { LocationMode.valueOf(o.getString("locationMode")) }.getOrDefault(LocationMode.GPS),
            manualPostalCode = o.optString("manualPostalCode", ""),
            manualCountryCode = o.optString("manualCountryCode", "US"),
            location = loc,
            prayer = prayer,
            alertEnabled = alertsMap,
            temperatureUnit = runCatching { TemperatureUnit.valueOf(o.getString("temperatureUnit")) }.getOrDefault(TemperatureUnit.FAHRENHEIT),
            windUnit = runCatching { WindUnit.valueOf(o.getString("windUnit")) }.getOrDefault(WindUnit.MPH),
            clockFormat = runCatching { ClockFormat.valueOf(o.getString("clockFormat")) }.getOrDefault(ClockFormat.SYSTEM),
            hijriOffsetDays = o.optInt("hijriOffsetDays", 0),
            quran = quran,
        )
    }

    companion object {
        private const val KEY_SETTINGS = "app_settings_json"
        private const val KEY_WEATHER = "weather_snapshot_json"
        private const val KEY_AYAH_READS = "ayah_reads_json"
        private const val KEY_SESSIONS = "reading_sessions_json"
    }
}
