package com.quranwbw.android.dtadhan.weather

import com.quranwbw.android.dtadhan.model.GeoLocation
import com.quranwbw.android.dtadhan.model.TemperatureUnit
import com.quranwbw.android.dtadhan.model.WindUnit
import org.json.JSONObject
import java.net.URLEncoder
import java.time.Duration
import java.time.Instant
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

enum class WeatherKind { CLEAR, PARTLY_CLOUDY, CLOUDY, FOG, DRIZZLE, RAIN, FREEZING_RAIN, SNOW, THUNDERSTORM, UNKNOWN }

data class WeatherCondition(val label: String, val kind: WeatherKind)

object WeatherCodes {
    fun describe(code: Int): WeatherCondition = when (code) {
        0 -> WeatherCondition("Clear", WeatherKind.CLEAR)
        1 -> WeatherCondition("Mainly clear", WeatherKind.CLEAR)
        2 -> WeatherCondition("Partly cloudy", WeatherKind.PARTLY_CLOUDY)
        3 -> WeatherCondition("Overcast", WeatherKind.CLOUDY)
        45 -> WeatherCondition("Fog", WeatherKind.FOG)
        48 -> WeatherCondition("Rime fog", WeatherKind.FOG)
        51 -> WeatherCondition("Light drizzle", WeatherKind.DRIZZLE)
        53 -> WeatherCondition("Drizzle", WeatherKind.DRIZZLE)
        55 -> WeatherCondition("Heavy drizzle", WeatherKind.DRIZZLE)
        56 -> WeatherCondition("Light freezing drizzle", WeatherKind.FREEZING_RAIN)
        57 -> WeatherCondition("Freezing drizzle", WeatherKind.FREEZING_RAIN)
        61 -> WeatherCondition("Light rain", WeatherKind.RAIN)
        63 -> WeatherCondition("Rain", WeatherKind.RAIN)
        65 -> WeatherCondition("Heavy rain", WeatherKind.RAIN)
        66 -> WeatherCondition("Light freezing rain", WeatherKind.FREEZING_RAIN)
        67 -> WeatherCondition("Freezing rain", WeatherKind.FREEZING_RAIN)
        71 -> WeatherCondition("Light snow", WeatherKind.SNOW)
        73 -> WeatherCondition("Snow", WeatherKind.SNOW)
        75 -> WeatherCondition("Heavy snow", WeatherKind.SNOW)
        77 -> WeatherCondition("Snow grains", WeatherKind.SNOW)
        80 -> WeatherCondition("Light showers", WeatherKind.RAIN)
        81 -> WeatherCondition("Showers", WeatherKind.RAIN)
        82 -> WeatherCondition("Heavy showers", WeatherKind.RAIN)
        85 -> WeatherCondition("Snow showers", WeatherKind.SNOW)
        86 -> WeatherCondition("Heavy snow showers", WeatherKind.SNOW)
        95 -> WeatherCondition("Thunderstorm", WeatherKind.THUNDERSTORM)
        96 -> WeatherCondition("Thunderstorm, hail", WeatherKind.THUNDERSTORM)
        99 -> WeatherCondition("Thunderstorm, heavy hail", WeatherKind.THUNDERSTORM)
        else -> WeatherCondition("Unknown", WeatherKind.UNKNOWN)
    }

    fun emoji(kind: WeatherKind, isDay: Boolean): String = when (kind) {
        WeatherKind.CLEAR -> if (isDay) "☀️" else "🌙"
        WeatherKind.PARTLY_CLOUDY -> if (isDay) "⛅" else "☁️"
        WeatherKind.CLOUDY -> "☁️"
        WeatherKind.FOG -> "🌫️"
        WeatherKind.DRIZZLE, WeatherKind.RAIN -> "🌧️"
        WeatherKind.FREEZING_RAIN -> "🌨️"
        WeatherKind.SNOW -> "❄️"
        WeatherKind.THUNDERSTORM -> "⛈️"
        WeatherKind.UNKNOWN -> "🌡️"
    }
}

data class WeatherSnapshot(
    val temperatureC: Double,
    val feelsLikeC: Double,
    val weatherCode: Int,
    val isDay: Boolean,
    val humidityPercent: Int,
    val windKmh: Double,
    val highC: Double?,
    val lowC: Double?,
    val zoneId: String,
    val latitude: Double,
    val longitude: Double,
    val fetchedAtEpochMs: Long,
) {
    val condition: WeatherCondition get() = WeatherCodes.describe(weatherCode)
    val fetchedAt: Instant get() = Instant.ofEpochMilli(fetchedAtEpochMs)

    fun isStale(now: Instant): Boolean = Duration.between(fetchedAt, now) > STALE_AFTER

    fun isFor(location: GeoLocation): Boolean =
        abs(latitude - location.latitude) <= LOCATION_TOLERANCE_DEG &&
            abs(longitude - location.longitude) <= LOCATION_TOLERANCE_DEG

    companion object {
        val STALE_AFTER: Duration = Duration.ofHours(3)
        const val LOCATION_TOLERANCE_DEG = 0.1
    }
}

object Units {
    fun temperature(celsius: Double, unit: TemperatureUnit): Int = when (unit) {
        TemperatureUnit.CELSIUS -> celsius.roundToInt()
        TemperatureUnit.FAHRENHEIT -> (celsius * 9.0 / 5.0 + 32.0).roundToInt()
    }

    fun temperatureLabel(celsius: Double, unit: TemperatureUnit): String =
        "${temperature(celsius, unit)}°" + if (unit == TemperatureUnit.CELSIUS) "C" else "F"

    fun wind(kmh: Double, unit: WindUnit): Int = when (unit) {
        WindUnit.KMH -> kmh.roundToInt()
        WindUnit.MPH -> (kmh / 1.609344).roundToInt()
    }

    fun windLabel(kmh: Double, unit: WindUnit): String =
        "${wind(kmh, unit)} " + if (unit == WindUnit.KMH) "km/h" else "mph"
}

object OpenMeteo {
    const val BASE_URL = "https://api.open-meteo.com/v1/forecast"

    fun coarse(value: Double): String = "%.2f".format(Locale.US, value)

    fun forecastUrl(latitude: Double, longitude: Double, baseUrl: String = BASE_URL): String =
        "$baseUrl?latitude=${coarse(latitude)}" +
            "&longitude=${coarse(longitude)}" +
            "&current=temperature_2m,apparent_temperature,weather_code,is_day,relative_humidity_2m,wind_speed_10m" +
            "&daily=temperature_2m_max,temperature_2m_min&forecast_days=2&timezone=auto"

    fun parse(body: String, fetchedAt: Instant): WeatherSnapshot {
        val root = JSONObject(body)
        val current = root.getJSONObject("current")
        val daily = root.optJSONObject("daily")
        val maxArr = daily?.optJSONArray("temperature_2m_max")
        val minArr = daily?.optJSONArray("temperature_2m_min")
        val highC = if (maxArr != null && maxArr.length() > 0 && !maxArr.isNull(0)) maxArr.getDouble(0) else null
        val lowC = if (minArr != null && minArr.length() > 0 && !minArr.isNull(0)) minArr.getDouble(0) else null

        return WeatherSnapshot(
            temperatureC = current.getDouble("temperature_2m"),
            feelsLikeC = current.getDouble("apparent_temperature"),
            weatherCode = current.getInt("weather_code"),
            isDay = current.optInt("is_day", 1) == 1,
            humidityPercent = current.getInt("relative_humidity_2m"),
            windKmh = current.getDouble("wind_speed_10m"),
            highC = highC,
            lowC = lowC,
            zoneId = root.optString("timezone", "UTC"),
            latitude = root.getDouble("latitude"),
            longitude = root.getDouble("longitude"),
            fetchedAtEpochMs = fetchedAt.toEpochMilli(),
        )
    }
}

data class PostalPlace(val latitude: Double, val longitude: Double, val name: String)

object PostalCodes {
    val COUNTRIES: List<Pair<String, String>> = listOf(
        "US" to "United States", "CA" to "Canada", "GB" to "United Kingdom", "AU" to "Australia",
        "DE" to "Germany", "FR" to "France", "NL" to "Netherlands", "BE" to "Belgium",
        "ES" to "Spain", "IT" to "Italy", "TR" to "Türkiye", "IN" to "India", "PK" to "Pakistan",
        "MY" to "Malaysia", "ZA" to "South Africa", "NZ" to "New Zealand", "SE" to "Sweden",
        "NO" to "Norway", "DK" to "Denmark", "CH" to "Switzerland", "AT" to "Austria",
    )

    private val US_ZIP = Regex("^\\d{5}(-\\d{4})?$")
    private val GENERIC = Regex("^[A-Za-z0-9][A-Za-z0-9 \\-]{1,9}$")

    fun normalize(raw: String, countryCode: String): String {
        val code = raw.trim().uppercase()
        val valid = when (countryCode.uppercase()) {
            "US" -> US_ZIP.matches(code)
            else -> GENERIC.matches(code)
        }
        require(valid) { "\"${raw.trim()}\" is not a valid postal code" }
        return if (countryCode.equals("US", ignoreCase = true)) code.take(5) else code
    }

    fun zippopotamUrl(countryCode: String, postalCode: String, baseUrl: String = "https://api.zippopotam.us"): String {
        val code = if (countryCode.equals("GB", true)) postalCode.substringBefore(' ') else postalCode
        return "$baseUrl/${countryCode.lowercase()}/${URLEncoder.encode(code, "UTF-8").replace("+", "%20")}"
    }

    fun parseZippopotam(body: String, postalCode: String): PostalPlace {
        val root = JSONObject(body)
        val places = root.optJSONArray("places")
        if (places == null || places.length() == 0) {
            throw IllegalArgumentException("No place found for postal code $postalCode")
        }
        val place = places.getJSONObject(0)
        val lat = place.getString("latitude").toDoubleOrNull()
        val lon = place.getString("longitude").toDoubleOrNull()
        if (lat == null || lon == null) {
            throw IllegalArgumentException("No place found for postal code $postalCode")
        }
        val placeName = place.optString("place name", "")
        val stateAbbr = place.optString("state abbreviation", "")
        val name = listOf(placeName, stateAbbr).filter { it.isNotBlank() }.joinToString(", ")
        return PostalPlace(lat, lon, name.ifBlank { postalCode })
    }
}
