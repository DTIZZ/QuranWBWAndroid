package com.quranwbw.android.ui.screens

import android.animation.ValueAnimator
import android.hardware.SensorManager
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.quranwbw.android.dtadhan.model.AppSettings
import com.quranwbw.android.dtadhan.model.WindUnit
import com.quranwbw.android.dtadhan.qibla.AlignmentState
import com.quranwbw.android.dtadhan.qibla.CompassReading
import com.quranwbw.android.dtadhan.qibla.CompassSensorSource
import com.quranwbw.android.dtadhan.qibla.HeadingMath
import com.quranwbw.android.dtadhan.qibla.QiblaCalculator
import com.quranwbw.android.dtadhan.qibla.SunHint
import com.quranwbw.android.dtadhan.qibla.SunQiblaHint
import java.text.NumberFormat
import java.time.Instant
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QiblaScreen(
    settings: AppSettings,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val location = settings.location
    val useMiles = settings.windUnit == WindUnit.MPH

    var reading by remember { mutableStateOf<CompassReading?>(null) }
    var smoothedHeading by remember { mutableStateOf<Double?>(null) }
    var alignment by remember { mutableStateOf(AlignmentState.OFF) }

    LaunchedEffect(location?.latitude, location?.longitude) {
        if (location == null) return@LaunchedEffect
        val source = CompassSensorSource(context)
        val qibla = QiblaCalculator.calculate(location)
        source.readings(location.latitude, location.longitude).collect { r ->
            reading = r
            if (r.hasSensor) {
                val h = HeadingMath.smoothCircular(smoothedHeading, r.trueHeadingDeg.toDouble())
                smoothedHeading = h
                val delta = HeadingMath.shortestDelta(h, qibla.bearingDegrees)
                alignment = HeadingMath.alignmentWithHysteresis(abs(delta), alignment)
            }
        }
    }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Qibla Finder",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (location == null) {
                NoLocationCard(onOpenSettings)
            } else {
                val qibla = remember(location.latitude, location.longitude) {
                    QiblaCalculator.calculate(location)
                }
                val sunHint = remember(location.latitude, location.longitude) {
                    SunQiblaHint.hintFor(location, Instant.now())
                }
                val r = reading
                if (r != null && !r.hasSensor) {
                    NoSensorView(
                        qiblaBearing = qibla.bearingDegrees,
                        distanceKm = qibla.distanceKm,
                        distanceMiles = qibla.distanceMiles,
                        useMiles = useMiles,
                        locationName = location.name,
                        sunHint = sunHint,
                    )
                } else {
                    val heading = smoothedHeading ?: 0.0
                    val delta = HeadingMath.shortestDelta(heading, qibla.bearingDegrees)
                    val interference = CompassSensorSource.isInterferenceDetected(r?.fieldStrengthMicroTesla)
                    val holdFlat = if (r != null) CompassSensorSource.isTiltedTooMuch(r.pitchDeg, r.rollDeg) else false
                    val showCalibration = r != null && r.accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW

                    CompassView(
                        qiblaBearing = qibla.bearingDegrees,
                        heading = heading,
                        delta = delta,
                        alignment = alignment,
                        distanceKm = qibla.distanceKm,
                        distanceMiles = qibla.distanceMiles,
                        useMiles = useMiles,
                        locationName = location.name,
                        interferenceWarning = interference,
                        holdFlat = holdFlat,
                        showCalibration = showCalibration,
                        sunHint = sunHint,
                    )
                }
            }
        }
    }
}

@Composable
private fun NoLocationCard(onOpenSettings: () -> Unit) {
    Card(
        modifier = Modifier
            .widthIn(max = 520.dp)
            .fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Filled.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text("Location needed for Qibla", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Set your GPS location or ZIP/postal code in Settings to compute the Qibla bearing from where you are.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onOpenSettings) {
                Text("Open Settings")
            }
        }
    }
}

@Composable
private fun NoSensorView(
    qiblaBearing: Double,
    distanceKm: Double,
    distanceMiles: Double,
    useMiles: Boolean,
    locationName: String,
    sunHint: SunHint?,
) {
    Column(
        modifier = Modifier
            .widthIn(max = 520.dp)
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BearingSummaryCard(
            qiblaBearing = qiblaBearing,
            distanceKm = distanceKm,
            distanceMiles = distanceMiles,
            useMiles = useMiles,
            locationName = locationName,
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Compass sensor unavailable", style = MaterialTheme.typography.titleSmall)
                Text(
                    "This device does not have a magnetometer/compass sensor. Use the true-north bearing above or the sun hint below.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                sunHint?.let { hint ->
                    Text(
                        hint.message,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun CompassView(
    qiblaBearing: Double,
    heading: Double,
    delta: Double,
    alignment: AlignmentState,
    distanceKm: Double,
    distanceMiles: Double,
    useMiles: Boolean,
    locationName: String,
    interferenceWarning: Boolean,
    holdFlat: Boolean,
    showCalibration: Boolean,
    sunHint: SunHint?,
) {
    val view = LocalView.current
    val animationsEnabled = remember { ValueAnimator.areAnimatorsEnabled() }

    var unwrappedHeading by remember { mutableFloatStateOf(heading.toFloat()) }
    LaunchedEffect(heading) {
        val currentMod = HeadingMath.normalizeDegrees(unwrappedHeading.toDouble())
        val step = HeadingMath.shortestDelta(currentMod, heading)
        unwrappedHeading += step.toFloat()
    }

    val animatedHeading by animateFloatAsState(
        targetValue = unwrappedHeading,
        animationSpec = if (animationsEnabled) spring(stiffness = 300f, dampingRatio = 0.85f) else tween(0),
        label = "compass_heading",
    )

    var previousAlignment by remember { mutableStateOf(AlignmentState.OFF) }
    LaunchedEffect(alignment) {
        if (alignment == AlignmentState.ALIGNED && previousAlignment != AlignmentState.ALIGNED) {
            val constant = if (Build.VERSION.SDK_INT >= 30) {
                HapticFeedbackConstants.CONFIRM
            } else {
                HapticFeedbackConstants.VIRTUAL_KEY
            }
            view.performHapticFeedback(constant)
        }
        previousAlignment = alignment
    }

    val absDeltaDeg = abs(delta).roundToInt()
    val turnText = when {
        holdFlat -> "Hold the phone flat"
        alignment == AlignmentState.ALIGNED -> "Facing Qibla"
        delta >= 0 -> "Turn $absDeltaDeg° right"
        else -> "Turn $absDeltaDeg° left"
    }

    Column(
        modifier = Modifier
            .widthIn(max = 520.dp)
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (holdFlat) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "Hold the phone flat for an accurate compass reading.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(14.dp),
                )
            }
        }

        CompassDial(
            headingDeg = animatedHeading,
            qiblaBearingDeg = qiblaBearing.toFloat(),
            alignment = alignment,
            modifier = Modifier.size(280.dp),
        )

        Text(
            text = turnText,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (alignment == AlignmentState.ALIGNED && !holdFlat) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            textAlign = TextAlign.Center,
        )

        BearingSummaryCard(
            qiblaBearing = qiblaBearing,
            distanceKm = distanceKm,
            distanceMiles = distanceMiles,
            useMiles = useMiles,
            locationName = locationName,
        )

        if (showCalibration || interferenceWarning) {
            CalibrationCard(
                interferenceWarning = interferenceWarning,
                animationsEnabled = animationsEnabled,
            )
        }

        sunHint?.let { hint ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Explore, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(hint.message, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun BearingSummaryCard(
    qiblaBearing: Double,
    distanceKm: Double,
    distanceMiles: Double,
    useMiles: Boolean,
    locationName: String,
) {
    val numberFormat = remember { NumberFormat.getIntegerInstance() }
    val distanceText = if (useMiles) {
        "${numberFormat.format(distanceMiles.roundToInt())} mi to Makkah"
    } else {
        "${numberFormat.format(distanceKm.roundToInt())} km to Makkah"
    }
    val dir = HeadingMath.compassDirectionLabel(qiblaBearing)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                "Qibla ${qiblaBearing.roundToInt()}° $dir from true north",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "$distanceText · $locationName",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CalibrationCard(
    interferenceWarning: Boolean,
    animationsEnabled: Boolean,
) {
    val phase = if (animationsEnabled) {
        val transition = rememberInfiniteTransition(label = "figure8")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = (2f * PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2600, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "figure8_phase",
        ).value
    } else {
        0f
    }

    val trackColor = MaterialTheme.colorScheme.outlineVariant
    val dotColor = MaterialTheme.colorScheme.primary
    val stroke = remember { Stroke(width = 6f, cap = StrokeCap.Round) }
    val path = remember { Path() }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Canvas(modifier = Modifier.size(64.dp, 40.dp)) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val rx = size.width * 0.42f
                val ry = size.height * 0.38f
                path.reset()
                for (i in 0..48) {
                    val t = (i / 48f) * (2f * PI).toFloat()
                    val x = cx + rx * sin(t)
                    val y = cy + ry * sin(2f * t) * 0.55f
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path = path, color = trackColor, style = stroke)
                val dotX = cx + rx * sin(phase)
                val dotY = cy + ry * sin(2f * phase) * 0.55f
                drawCircle(color = dotColor, radius = 7f, center = Offset(dotX, dotY))
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        if (interferenceWarning) "Magnetic interference detected" else "Calibrate compass",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
                Text(
                    if (interferenceWarning) {
                        "Move away from magnetic cases, wallets, or mounts, then wave your phone in a figure-8."
                    } else {
                        "Wave your phone slowly in a figure-8 motion to improve compass accuracy."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

@Composable
private fun CompassDial(
    headingDeg: Float,
    qiblaBearingDeg: Float,
    alignment: AlignmentState,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val errorColor = MaterialTheme.colorScheme.error
    val density = LocalDensity.current

    val ringStroke = remember(density) { Stroke(width = with(density) { 4.dp.toPx() }) }
    val majorTickWidth = remember(density) { with(density) { 2.5.dp.toPx() } }
    val minorTickWidth = remember(density) { with(density) { 1.dp.toPx() } }
    val pointerStroke = remember(density) { Stroke(width = with(density) { 3.dp.toPx() }, cap = StrokeCap.Round) }
    val kaabaPath = remember { Path() }
    val labelPaint = remember(density, onSurface) {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = android.graphics.Paint.Align.CENTER
            textSize = with(density) { 14.dp.toPx() }
            color = onSurface.toArgb()
            isFakeBoldText = true
        }
    }
    val northPaint = remember(density, errorColor) {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = android.graphics.Paint.Align.CENTER
            textSize = with(density) { 15.dp.toPx() }
            color = errorColor.toArgb()
            isFakeBoldText = true
        }
    }

    val ringColor = when (alignment) {
        AlignmentState.ALIGNED -> primary
        AlignmentState.CLOSE -> primary.copy(alpha = 0.65f)
        AlignmentState.OFF -> surfaceVariant
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension / 2f - 16f

            drawCircle(
                color = ringColor,
                radius = radius,
                center = center,
                style = ringStroke,
            )

            drawLine(
                color = if (alignment == AlignmentState.ALIGNED) primary else onSurface,
                start = Offset(center.x, center.y - radius - 14f),
                end = Offset(center.x, center.y - radius + 12f),
                strokeWidth = pointerStroke.width,
                cap = StrokeCap.Round,
            )

            rotate(degrees = -headingDeg, pivot = center) {
                for (deg in 0 until 360 step 15) {
                    val isMajor = deg % 90 == 0
                    val rad = Math.toRadians((deg - 90).toDouble())
                    val inner = radius - if (isMajor) 18f else 10f
                    val sx = center.x + (inner * cos(rad)).toFloat()
                    val sy = center.y + (inner * sin(rad)).toFloat()
                    val ex = center.x + (radius * cos(rad)).toFloat()
                    val ey = center.y + (radius * sin(rad)).toFloat()
                    drawLine(
                        color = if (deg == 0) errorColor else onSurfaceVariant,
                        start = Offset(sx, sy),
                        end = Offset(ex, ey),
                        strokeWidth = if (isMajor) majorTickWidth else minorTickWidth,
                    )
                }

                val labelRadius = radius - 36f
                drawContext.canvas.nativeCanvas.apply {
                    drawText("N", center.x, center.y - labelRadius + 10f, northPaint)
                    drawText("E", center.x + labelRadius, center.y + 10f, labelPaint)
                    drawText("S", center.x, center.y + labelRadius + 10f, labelPaint)
                    drawText("W", center.x - labelRadius, center.y + 10f, labelPaint)
                }

                rotate(degrees = qiblaBearingDeg, pivot = center) {
                    drawLine(
                        color = primary,
                        start = center,
                        end = Offset(center.x, center.y - radius + 28f),
                        strokeWidth = pointerStroke.width,
                        cap = StrokeCap.Round,
                    )
                    kaabaPath.reset()
                    val tipY = center.y - radius + 18f
                    kaabaPath.moveTo(center.x, tipY)
                    kaabaPath.lineTo(center.x - 14f, tipY + 26f)
                    kaabaPath.lineTo(center.x + 14f, tipY + 26f)
                    kaabaPath.close()
                    drawPath(path = kaabaPath, color = primary)
                    drawRect(
                        color = Color.Black,
                        topLeft = Offset(center.x - 10f, tipY + 32f),
                        size = Size(20f, 20f),
                    )
                    drawRect(
                        color = Color(0xFFD4AF37),
                        topLeft = Offset(center.x - 10f, tipY + 37f),
                        size = Size(20f, 3f),
                    )
                }
            }
        }
    }
}
