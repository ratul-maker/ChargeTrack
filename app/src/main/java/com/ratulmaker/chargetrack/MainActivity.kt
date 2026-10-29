package com.ratulmaker.chargetrack

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private val Navy = androidx.compose.ui.graphics.Color(0xFF07111F)
private val Panel = androidx.compose.ui.graphics.Color(0xFF101F31)
private val Cyan = androidx.compose.ui.graphics.Color(0xFF43E5FF)
private val Ice = androidx.compose.ui.graphics.Color(0xFFBCEFFF)
private val Muted = androidx.compose.ui.graphics.Color(0xFF8FA7BD)
private val Violet = androidx.compose.ui.graphics.Color(0xFF9B8CFF)

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) {
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Cyan,
                    secondary = Violet,
                    background = Navy,
                    surface = Panel,
                    onBackground = Ice,
                    onSurface = Ice
                )
            ) {
                ChargeTrackApp(
                    onStartTracking = {
                        val intent = Intent(this, TrackingService::class.java)
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                ContextCompat.startForegroundService(this, intent)
                            } else startService(intent)
                        } catch (_: Exception) { }
                    },
                    onStopTracking = {
                        stopService(Intent(this, TrackingService::class.java))
                        ChargeData.setTrackingEnabled(this, false)
                    }
                )
            }
        }
    }
}

private val sections = listOf(
    "Tracker", "Speed", "Predictor", "Capacity", "Live", "Reports"
)

@Composable
private fun ChargeTrackApp(onStartTracking: () -> Unit, onStopTracking: () -> Unit) {
    var selected by remember { mutableIntStateOf(0) }
    var refresh by remember { mutableIntStateOf(0) }
    var ratedCapacity by remember { mutableIntStateOf(ChargeData.ratedCapacity(androidx.compose.ui.platform.LocalContext.current)) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val sessions = remember(refresh) { ChargeData.sessions(context) }
    val batteryManager = remember(context) {
        context.getSystemService(android.content.Context.BATTERY_SERVICE) as android.os.BatteryManager
    }
    val currentNow = remember { mutableIntStateOf(0) }
    val voltageNow = remember { mutableIntStateOf(0) }
    val percentNow = remember { mutableIntStateOf(0) }
    val pluggedNow = remember { mutableStateOf(false) }
    DisposableEffect(context) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: android.content.Context?, i: Intent?) {
                if (i?.action != Intent.ACTION_BATTERY_CHANGED) return
                val level = i.getIntExtra("level", 0)
                val scale = i.getIntExtra("scale", 100).coerceAtLeast(1)
                percentNow.intValue = (level * 100f / scale).roundToInt().coerceIn(0, 100)
                voltageNow.intValue = i.getIntExtra("voltage", 0)
                pluggedNow.value = i.getIntExtra("plugged", 0) != 0
                val current = batteryManager.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
                currentNow.intValue = if (current == Int.MIN_VALUE) 0 else current / 1000
            }
        }
        context.registerReceiver(receiver, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        onDispose { try { context.unregisterReceiver(receiver) } catch (_: Exception) {} }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Navy) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                Text("CHARGETRACK", color = Cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                Text(sections[selected], color = Ice, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text("BATTERY INTELLIGENCE • OFFLINE", color = Muted, fontSize = 10.sp, letterSpacing = 1.4.sp)
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (selected) {
                    0 -> TrackerScreen(percentNow.intValue, pluggedNow.value, sessions, onStartTracking, onStopTracking) { refresh++ }
                    1 -> SpeedScreen(sessions)
                    2 -> PredictorScreen(percentNow.intValue, pluggedNow.value, sessions)
                    3 -> CapacityScreen(sessions, ratedCapacity) { value ->
                        ratedCapacity = value
                        ChargeData.setRatedCapacity(context, value)
                    }
                    4 -> LiveScreen(percentNow.intValue, pluggedNow.value, currentNow.intValue, voltageNow.intValue)
                    else -> ReportsScreen(sessions, ratedCapacity)
                }
            }
            NavigationBar(containerColor = Panel, contentColor = Ice) {
                sections.forEachIndexed { index, label ->
                    NavigationBarItem(
                        selected = selected == index,
                        onClick = { selected = index; refresh++ },
                        icon = {
                            Text(
                                listOf("◉", "↗", "◷", "▦", "ϟ", "▤")[index],
                                color = if (selected == index) Cyan else Muted,
                                fontSize = 18.sp
                            )
                        },
                        label = { Text(label, fontSize = 9.sp, maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Cyan,
                            indicatorColor = Cyan.copy(alpha = 0.12f),
                            selectedTextColor = Cyan,
                            unselectedTextColor = Muted
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun ScreenColumn(content: @Composable ColumnScope.() -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = { item { Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = content) } }
    )
}

@Composable
private fun PanelCard(title: String, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Panel),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = Ice, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) Text(subtitle, color = Muted, fontSize = 11.sp)
            content()
        }
    }
}

@Composable
private fun BigBattery(percent: Int, plugged: Boolean) {
    Box(Modifier.size(184.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawArc(Panel.copy(alpha = 0.95f), -90f, 360f, false, style = Stroke(12.dp.toPx(), cap = StrokeCap.Round))
            drawArc(Cyan, -90f, 360f * percent.coerceIn(0, 100) / 100f, false, style = Stroke(12.dp.toPx(), cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$percent%", fontSize = 42.sp, fontWeight = FontWeight.Bold, color = Ice)
            Text(if (plugged) "CONNECTED" else "ON BATTERY", color = if (plugged) Cyan else Muted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun TrackerScreen(
    percent: Int, plugged: Boolean, sessions: List<ChargeSession>,
    onStart: () -> Unit, onStop: () -> Unit, refresh: () -> Unit
) {
    ScreenColumn {
        PanelCard("LIVE BATTERY", "Current device-reported percentage") {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { BigBattery(percent, plugged) }
            Text(if (plugged) "Power connection detected" else "Not currently charging", color = Muted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onStart(); refresh() }, modifier = Modifier.weight(1f)) { Text("Start tracking") }
                OutlinedButton(onClick = { onStop(); refresh() }, modifier = Modifier.weight(1f)) { Text("Stop") }
            }
        }
        PanelCard("LATEST SESSION") {
            val latest = sessions.firstOrNull()
            if (latest == null) Text("No session recorded yet. Start tracking while charging.", color = Muted)
            else {
                MetricLine("Status", latest.status)
                MetricLine("Start", formatDate(latest.startTime))
                MetricLine("Battery range", "${latest.startPercent}% → ${latest.endPercent}%")
                MetricLine("Duration", formatDuration(latest.durationMs()))
            }
        }
        PanelCard("TRACKING NOTES") {
            Text("Keep tracking enabled to record observed battery changes. Android may limit tracking after force-stop or under manufacturer battery restrictions.", color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun SpeedScreen(sessions: List<ChargeSession>) {
    ScreenColumn {
        PanelCard("CHARGING SPEED", "Observed time between recorded percentage changes") {
            val points = sessions.flatMap { it.points }.sortedBy { it.time }
            val intervals = points.zipWithNext().filter { it.second.percent > it.first.percent }
                .map { (a, b) -> (b.time - a.time).coerceAtLeast(0L) / 1000.0 / (b.percent - a.percent) }
            MetricLine("Recorded increases", intervals.size.toString())
            MetricLine("Average seconds per 1%", if (intervals.isEmpty()) "Not enough data" else "${intervals.average().roundToInt()} sec")
            MetricLine("Fastest observed", if (intervals.isEmpty()) "—" else "${intervals.minOrNull()!!.roundToInt()} sec / %")
            MetricLine("Slowest observed", if (intervals.isEmpty()) "—" else "${intervals.maxOrNull()!!.roundToInt()} sec / %")
        }
        PanelCard("INTERPRETATION") {
            Text("These values use recorded percentage changes. Android may report several changes together, so the result is an observed interval rather than a laboratory measurement.", color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun PredictorScreen(percent: Int, plugged: Boolean, sessions: List<ChargeSession>) {
    ScreenColumn {
        PanelCard("TIME TO TARGET", "Estimate based on recorded charging history") {
            val points = sessions.flatMap { it.points }.sortedBy { it.time }
            val intervals = points.zipWithNext().filter { it.second.percent > it.first.percent }
                .map { (a, b) -> (b.time - a.time).toDouble() / (b.percent - a.percent) }
                .filter { it > 0 }
            val msPerPercent = if (intervals.isNotEmpty()) intervals.average() else 0.0
            Text("Current battery: $percent%", color = Cyan, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            listOf(50, 80, 90, 100).forEach { target ->
                val remaining = (target - percent).coerceAtLeast(0)
                val estimate = if (msPerPercent > 0) formatDuration((remaining * msPerPercent).toLong()) else "Collect more data"
                MetricLine("To $target%", if (remaining == 0) "Reached" else if (!plugged) "Connect charger to estimate" else estimate)
            }
            Text("Predictions are estimates and may change with charging speed, battery level, and device behaviour.", color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun CapacityScreen(sessions: List<ChargeSession>, ratedCapacity: Int, onCapacityChange: (Int) -> Unit) {
    var capacityText by remember(ratedCapacity) { mutableStateOf(ratedCapacity.toString()) }
    ScreenColumn {
        PanelCard("CAPACITY MODEL", "Rated battery capacity used for rough calculations") {
            Text("Rated capacity (mAh)", color = Muted, fontSize = 12.sp)
            OutlinedTextField(
                value = capacityText,
                onValueChange = { capacityText = it.filter(Char::isDigit).take(5) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Capacity in mAh") }
            )
            Button(onClick = { capacityText.toIntOrNull()?.let(onCapacityChange) }) { Text("Save capacity") }
            MetricLine("Configured rating", "$ratedCapacity mAh")
            val total = sessions.sumOf { it.estimatedMah(ratedCapacity) }
            MetricLine("Estimated charge added", "${total.roundToInt()} mAh")
            Text("Percentage-based mAh values are rough estimates, not direct measurements of true stored charge or battery health.", color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun LiveScreen(percent: Int, plugged: Boolean, currentMa: Int, voltageMv: Int) {
    ScreenColumn {
        PanelCard("LIVE ELECTRICAL MONITOR", "Readings reported by the device; availability varies") {
            MetricLine("Battery", "$percent%")
            MetricLine("Connection", if (plugged) "Connected" else "Not connected")
            MetricLine("Current", if (currentMa == 0) "Unavailable / zero reported" else "$currentMa mA")
            MetricLine("Voltage", if (voltageMv <= 0) "Unavailable" else "%.3f V".format(Locale.US, voltageMv / 1000.0))
            val powerW = if (currentMa != 0 && voltageMv > 0) currentMa * voltageMv / 1_000_000.0 else null
            MetricLine("Calculated battery-side power", powerW?.let { "%.2f W".format(Locale.US, kotlin.math.abs(it)) } ?: "Unavailable")
        }
        PanelCard("MEASUREMENT LIMITS") {
            Text("Current and voltage are device-reported values. Calculated battery-side power is not the same as USB charger output power. Accumulated mAh is not directly measured in this initial version.", color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ReportsScreen(sessions: List<ChargeSession>, ratedCapacity: Int) {
    ScreenColumn {
        PanelCard("REPORT OVERVIEW", "All saved sessions • estimates are derived from percentage changes") {
            val completed = sessions.count { it.status == "Completed" || it.status == "Full" }
            val totalMah = sessions.sumOf { it.estimatedMah(ratedCapacity) }
            val totalDuration = sessions.sumOf { it.durationMs() }
            MetricLine("Sessions recorded", sessions.size.toString())
            MetricLine("Completed / full", completed.toString())
            MetricLine("Estimated mAh added", "${totalMah.roundToInt()} mAh")
            MetricLine("Recorded elapsed time", formatDuration(totalDuration))
            MetricLine("Configured rated capacity", "$ratedCapacity mAh")
        }
        PanelCard("SESSION HISTORY") {
            if (sessions.isEmpty()) {
                Text("Your reports will appear here after charging sessions are recorded.", color = Muted)
            } else {
                sessions.forEach { session ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
                        Text(formatDate(session.startTime), color = Cyan, fontWeight = FontWeight.SemiBold)
                        MetricLine("Status", session.status)
                        MetricLine("Battery range", "${session.startPercent}% → ${session.endPercent}%")
                        MetricLine("Duration", formatDuration(session.durationMs()))
                        MetricLine("Estimated mAh added", "${session.estimatedMah(ratedCapacity).roundToInt()} mAh")
                        MetricLine("Observed percentage events", session.points.size.toString())
                    }
                    HorizontalDivider(color = Muted.copy(alpha = 0.2f))
                }
            }
        }
        PanelCard("REPORT ACCURACY") {
            Text("mAh is estimated from the percentage difference multiplied by the configured rated capacity. This is not the battery's true measured stored charge. Background restrictions, reboot gaps, and unavailable readings can make reports incomplete.", color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun MetricLine(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(label, color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(10.dp))
        Text(value, color = Ice, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
    }
}

private fun formatDate(time: Long): String =
    SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(time))

private fun formatDuration(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L) / 1000L)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }
}
