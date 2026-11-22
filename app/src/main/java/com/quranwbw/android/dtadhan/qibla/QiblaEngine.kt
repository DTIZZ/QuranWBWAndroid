package com.quranwbw.android.dtadhan.qibla

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.view.Surface
import android.view.WindowManager
import com.quranwbw.android.dtadhan.model.GeoLocation
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.time.Instant
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

enum class AlignmentState {
    ALIGNED,
    CLOSE,
    OFF,
}

object HeadingMath {
    const val ALIGNED_ENTER_DEG = 3.0
    const val ALIGNED_EXIT_DEG = 4.0
    const val CLOSE_ENTER_DEG = 10.0
    const val CLOSE_EXIT_DEG = 12.0

    fun normalizeDegrees(deg: Double): Double {
        if (deg.isNaN() || deg.isInfinite()) return 0.0
        val mod = deg % 360.0
        val positive = if (mod < 0.0) mod + 360.0 else mod
        return if (positive >= 360.0) 0.0 else positive
    }

    fun shortestDelta(currentDeg: Double, targetDeg: Double): Double {
        val diff = normalizeDegrees(targetDeg - currentDeg)
        return if (diff > 180.0) diff - 360.0 else diff
    }

    fun smoothCircular(previousDeg: Double?, newDeg: Double, alpha: Double = 0.25): Double {
        val normNew = normalizeDegrees(newDeg)
        if (previousDeg == null) return normNew
        val clampedAlpha = alpha.coerceIn(0.01, 1.0)
        val prevRad = Math.toRadians(normalizeDegrees(previousDeg))
        val newRad = Math.toRadians(normNew)
        val x = (1.0 - clampedAlpha) * cos(prevRad) + clampedAlpha * cos(newRad)
        val y = (1.0 - clampedAlpha) * sin(prevRad) + clampedAlpha * sin(newRad)
        if (abs(x) < 1e-9 && abs(y) < 1e-9) return normNew
        return normalizeDegrees(Math.toDegrees(atan2(y, x)))
    }

    fun alignmentWithHysteresis(absDeltaDeg: Double, previous: AlignmentState): AlignmentState {
        val d = abs(absDeltaDeg)
        return when (previous) {
            AlignmentState.ALIGNED -> when {
                d <= ALIGNED_EXIT_DEG -> AlignmentState.ALIGNED
                d <= CLOSE_ENTER_DEG -> AlignmentState.CLOSE
                else -> AlignmentState.OFF
            }
            AlignmentState.CLOSE -> when {
                d <= ALIGNED_ENTER_DEG -> AlignmentState.ALIGNED
                d <= CLOSE_EXIT_DEG -> AlignmentState.CLOSE
                else -> AlignmentState.OFF
            }
            AlignmentState.OFF -> when {
                d <= ALIGNED_ENTER_DEG -> AlignmentState.ALIGNED
                d <= CLOSE_ENTER_DEG -> AlignmentState.CLOSE
                else -> AlignmentState.OFF
            }
        }
    }

    fun compassDirectionLabel(bearingDeg: Double): String {
        val dirs = listOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")
        val index = ((normalizeDegrees(bearingDeg) + 11.25) / 22.5).toInt() % 16
        return dirs[index]
    }
}

data class QiblaResult(
    val bearingDegrees: Double,
    val distanceKm: Double,
    val distanceMiles: Double,
)

object QiblaCalculator {
    const val KAABA_LATITUDE = 21.4225
    const val KAABA_LONGITUDE = 39.8262
    private const val EARTH_RADIUS_KM = 6371.0
    private const val KM_PER_MILE = 1.609344

    fun calculate(location: GeoLocation): QiblaResult =
        calculate(location.latitude, location.longitude)

    fun calculate(latitude: Double, longitude: Double): QiblaResult {
        val distanceKm = haversineKm(latitude, longitude, KAABA_LATITUDE, KAABA_LONGITUDE)
        val rawBearing = if (distanceKm < 1e-4) {
            0.0
        } else {
            val dLon = Math.toRadians(KAABA_LONGITUDE - longitude)
            val lat1 = Math.toRadians(latitude)
            val lat2 = Math.toRadians(KAABA_LATITUDE)
            val y = sin(dLon) * cos(lat2)
            val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
            Math.toDegrees(atan2(y, x))
        }
        val bearing = HeadingMath.normalizeDegrees(rawBearing)
        return QiblaResult(
            bearingDegrees = bearing,
            distanceKm = distanceKm,
            distanceMiles = distanceKm / KM_PER_MILE,
        )
    }

    fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val rLat1 = Math.toRadians(lat1)
        val rLat2 = Math.toRadians(lat2)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(rLat1) * cos(rLat2) * sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * asin(sqrt(a.coerceIn(0.0, 1.0)))
        return EARTH_RADIUS_KM * c
    }
}

data class SunPosition(
    val azimuthDegrees: Double,
    val elevationDegrees: Double,
)

data class SunHint(
    val sunAzimuthDegrees: Double,
    val sunElevationDegrees: Double,
    val relativeAngleDegrees: Double,
    val message: String,
)

object SunQiblaHint {
    fun hintFor(location: GeoLocation, now: Instant): SunHint? {
        val sun = solarPosition(location.latitude, location.longitude, now)
        if (sun.elevationDegrees <= 0.0) return null

        val qibla = QiblaCalculator.calculate(location).bearingDegrees
        val delta = HeadingMath.shortestDelta(sun.azimuthDegrees, qibla)
        val rounded = abs(delta).roundToInt()
        val message = when {
            rounded <= 2 -> "Qibla is directly toward the sun."
            abs(abs(delta) - 180.0) <= 2.0 -> "Qibla is directly opposite the sun."
            delta > 0 -> "Qibla is $rounded° to the right of the sun."
            else -> "Qibla is $rounded° to the left of the sun."
        }
        return SunHint(
            sunAzimuthDegrees = sun.azimuthDegrees,
            sunElevationDegrees = sun.elevationDegrees,
            relativeAngleDegrees = delta,
            message = message,
        )
    }

    fun solarPosition(latitudeDeg: Double, longitudeDeg: Double, now: Instant): SunPosition {
        val jd = now.epochSecond / 86400.0 + 2440587.5
        val jc = (jd - 2451545.0) / 36525.0

        val geomMeanLongSunDeg = (280.46646 + jc * (36000.76983 + jc * 0.0003032)) % 360.0
        val geomMeanAnomSunDeg = 357.52911 + jc * (35999.05029 - 0.0001537 * jc)
        val eccentEarthOrbit = 0.016708634 - jc * (0.000042037 + 0.0000001267 * jc)

        val geomMeanAnomRad = Math.toRadians(geomMeanAnomSunDeg)
        val sunEqOfCtr = sin(geomMeanAnomRad) * (1.914602 - jc * (0.004817 + 0.000014 * jc)) +
            sin(2 * geomMeanAnomRad) * (0.019993 - 0.000101 * jc) +
            sin(3 * geomMeanAnomRad) * 0.000289

        val sunTrueLongDeg = geomMeanLongSunDeg + sunEqOfCtr
        val sunAppLongDeg = sunTrueLongDeg - 0.00569 - 0.00478 * sin(Math.toRadians(125.04 - 1934.136 * jc))

        val meanObliqEclipticDeg = 23.0 + (26.0 + (21.448 - jc * (46.815 + jc * (0.00059 - jc * 0.001813))) / 60.0) / 60.0
        val obliqCorrDeg = meanObliqEclipticDeg + 0.00256 * cos(Math.toRadians(125.04 - 1934.136 * jc))

        val obliqCorrRad = Math.toRadians(obliqCorrDeg)
        val sunAppLongRad = Math.toRadians(sunAppLongDeg)
        val sunDeclRad = asin((sin(obliqCorrRad) * sin(sunAppLongRad)).coerceIn(-1.0, 1.0))

        val varY = tan(obliqCorrRad / 2.0) * tan(obliqCorrRad / 2.0)
        val geomMeanLongRad = Math.toRadians(geomMeanLongSunDeg)
        val eqOfTimeMin = 4.0 * Math.toDegrees(
            varY * sin(2 * geomMeanLongRad) -
                2 * eccentEarthOrbit * sin(geomMeanAnomRad) +
                4 * eccentEarthOrbit * varY * sin(geomMeanAnomRad) * cos(2 * geomMeanLongRad) -
                0.5 * varY * varY * sin(4 * geomMeanLongRad) -
                1.25 * eccentEarthOrbit * eccentEarthOrbit * sin(2 * geomMeanAnomRad),
        )

        val utcMinutes = ((now.epochSecond % 86400L + 86400L) % 86400L) / 60.0
        val trueSolarTimeMin = ((utcMinutes + eqOfTimeMin + 4.0 * longitudeDeg) % 1440.0 + 1440.0) % 1440.0
        val hourAngleDeg = if (trueSolarTimeMin / 4.0 < 0.0) {
            trueSolarTimeMin / 4.0 + 180.0
        } else {
            trueSolarTimeMin / 4.0 - 180.0
        }

        val latRad = Math.toRadians(latitudeDeg)
        val hourAngleRad = Math.toRadians(hourAngleDeg)
        val cosZenith = (sin(latRad) * sin(sunDeclRad) + cos(latRad) * cos(sunDeclRad) * cos(hourAngleRad))
            .coerceIn(-1.0, 1.0)
        val zenithRad = acos(cosZenith)
        val elevationDeg = 90.0 - Math.toDegrees(zenithRad)

        val denom = cos(latRad) * sin(zenithRad)
        val azimuthDeg = if (abs(denom) < 1e-9) {
            180.0
        } else {
            val cosAz = ((sin(latRad) * cosZenith - sin(sunDeclRad)) / denom).coerceIn(-1.0, 1.0)
            val az = Math.toDegrees(acos(cosAz))
            if (hourAngleDeg > 0.0) {
                HeadingMath.normalizeDegrees(az + 180.0)
            } else {
                HeadingMath.normalizeDegrees(540.0 - az)
            }
        }
        return SunPosition(azimuthDegrees = azimuthDeg, elevationDegrees = elevationDeg)
    }
}

data class CompassReading(
    val trueHeadingDeg: Float,
    val pitchDeg: Float,
    val rollDeg: Float,
    val accuracy: Int,
    val fieldStrengthMicroTesla: Float?,
    val hasSensor: Boolean = true,
) {
    companion object {
        val NO_SENSOR = CompassReading(
            trueHeadingDeg = 0f,
            pitchDeg = 0f,
            rollDeg = 0f,
            accuracy = SensorManager.SENSOR_STATUS_UNRELIABLE,
            fieldStrengthMicroTesla = null,
            hasSensor = false,
        )
    }
}

class CompassSensorSource(private val context: Context) {
    private val sensorManager: SensorManager? = context.getSystemService(SensorManager::class.java)

    fun readings(latitude: Double, longitude: Double): Flow<CompassReading> = callbackFlow {
        val manager = sensorManager
        if (manager == null) {
            trySend(CompassReading.NO_SENSOR)
            awaitClose { }
            return@callbackFlow
        }

        val rotationVector = manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val accelerometer = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magnetometer = manager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        val useRotationVector = rotationVector != null
        val useFallback = !useRotationVector && accelerometer != null && magnetometer != null

        if (!useRotationVector && !useFallback) {
            trySend(CompassReading.NO_SENSOR)
            awaitClose { }
            return@callbackFlow
        }

        val rotationMatrix = FloatArray(9)
        val remappedMatrix = FloatArray(9)
        val orientation = FloatArray(3)
        val accelValues = FloatArray(3)
        val magValues = FloatArray(3)
        var hasAccel = false
        var hasMag = false
        var lastAccuracy = SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM
        var latestFieldStrength: Float? = null

        fun emitOrientation(matrix: FloatArray, accuracy: Int) {
            val rotation = currentDisplayRotation(context)
            remapForDisplayRotation(matrix, rotation, remappedMatrix)
            SensorManager.getOrientation(remappedMatrix, orientation)

            val azimuthDeg = Math.toDegrees(orientation[0].toDouble())
            val pitchDeg = Math.toDegrees(orientation[1].toDouble()).toFloat()
            val rollDeg = Math.toDegrees(orientation[2].toDouble()).toFloat()

            val declination = GeomagneticField(
                latitude.toFloat(),
                longitude.toFloat(),
                0f,
                System.currentTimeMillis()
            ).declination
            val trueHeading = HeadingMath.normalizeDegrees(azimuthDeg + declination).toFloat()

            trySend(
                CompassReading(
                    trueHeadingDeg = trueHeading,
                    pitchDeg = pitchDeg,
                    rollDeg = rollDeg,
                    accuracy = accuracy,
                    fieldStrengthMicroTesla = latestFieldStrength,
                    hasSensor = true,
                )
            )
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR -> {
                        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                        val acc = if (event.accuracy == SensorManager.SENSOR_STATUS_NO_CONTACT) {
                            SensorManager.SENSOR_STATUS_UNRELIABLE
                        } else {
                            event.accuracy.coerceAtLeast(lastAccuracy.coerceAtMost(event.accuracy))
                        }
                        lastAccuracy = event.accuracy
                        emitOrientation(rotationMatrix, acc)
                    }
                    Sensor.TYPE_ACCELEROMETER -> {
                        System.arraycopy(event.values, 0, accelValues, 0, 3)
                        hasAccel = true
                        if (useFallback && hasMag) {
                            if (SensorManager.getRotationMatrix(rotationMatrix, null, accelValues, magValues)) {
                                emitOrientation(rotationMatrix, lastAccuracy)
                            }
                        }
                    }
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        System.arraycopy(event.values, 0, magValues, 0, 3)
                        val x = event.values[0]
                        val y = event.values[1]
                        val z = event.values[2]
                        latestFieldStrength = sqrt(x * x + y * y + z * z)
                        lastAccuracy = event.accuracy
                        hasMag = true
                        if (useFallback && hasAccel) {
                            if (SensorManager.getRotationMatrix(rotationMatrix, null, accelValues, magValues)) {
                                emitOrientation(rotationMatrix, lastAccuracy)
                            }
                        }
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
                if (sensor.type == Sensor.TYPE_ROTATION_VECTOR || sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
                    lastAccuracy = accuracy
                }
            }
        }

        if (useRotationVector) {
            manager.registerListener(listener, rotationVector, SensorManager.SENSOR_DELAY_UI)
            if (magnetometer != null) {
                manager.registerListener(listener, magnetometer, SensorManager.SENSOR_DELAY_UI)
            }
        } else {
            manager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_UI)
            manager.registerListener(listener, magnetometer, SensorManager.SENSOR_DELAY_UI)
        }

        awaitClose {
            manager.unregisterListener(listener)
        }
    }

    companion object {
        fun remapForDisplayRotation(inMatrix: FloatArray, displayRotation: Int, outMatrix: FloatArray) {
            when (displayRotation) {
                Surface.ROTATION_90 -> SensorManager.remapCoordinateSystem(
                    inMatrix, SensorManager.AXIS_Y, SensorManager.AXIS_MINUS_X, outMatrix,
                )
                Surface.ROTATION_180 -> SensorManager.remapCoordinateSystem(
                    inMatrix, SensorManager.AXIS_MINUS_X, SensorManager.AXIS_MINUS_Y, outMatrix,
                )
                Surface.ROTATION_270 -> SensorManager.remapCoordinateSystem(
                    inMatrix, SensorManager.AXIS_MINUS_Y, SensorManager.AXIS_X, outMatrix,
                )
                else -> System.arraycopy(inMatrix, 0, outMatrix, 0, 9)
            }
        }

        @Suppress("DEPRECATION")
        private fun currentDisplayRotation(context: Context): Int {
            return if (Build.VERSION.SDK_INT >= 30) {
                runCatching { context.display?.rotation }.getOrNull()
                    ?: context.getSystemService(WindowManager::class.java)?.defaultDisplay?.rotation
                    ?: Surface.ROTATION_0
            } else {
                context.getSystemService(WindowManager::class.java)?.defaultDisplay?.rotation
                    ?: Surface.ROTATION_0
            }
        }

        fun isInterferenceDetected(fieldStrengthMicroTesla: Float?): Boolean {
            if (fieldStrengthMicroTesla == null) return false
            return fieldStrengthMicroTesla < 25f || fieldStrengthMicroTesla > 65f
        }

        fun isTiltedTooMuch(pitchDeg: Float, rollDeg: Float): Boolean =
            abs(pitchDeg) > 60f || abs(rollDeg) > 60f
    }
}
