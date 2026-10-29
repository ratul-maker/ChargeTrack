package com.ratulmaker.chargetrack

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class ChargePoint(val time: Long, val percent: Int, val plugged: Boolean)

data class ChargeSession(
    val id: String,
    val startTime: Long,
    var endTime: Long?,
    val startPercent: Int,
    var endPercent: Int,
    var status: String,
    val points: MutableList<ChargePoint> = mutableListOf()
) {
    fun durationMs(now: Long = System.currentTimeMillis()): Long =
        ((endTime ?: now) - startTime).coerceAtLeast(0L)

    fun estimatedMah(ratedMah: Int): Double =
        ((endPercent - startPercent) * ratedMah / 100.0).coerceAtLeast(0.0)
}

object ChargeData {
    private const val PREFS = "chargetrack_data"
    private const val SESSIONS = "sessions_json"
    private const val ACTIVE_ID = "active_session_id"
    private const val RATED = "rated_capacity"
    private const val TRACKING = "tracking_enabled"

    @Synchronized
    fun ratedCapacity(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(RATED, 5000).coerceIn(500, 20000)

    fun setRatedCapacity(context: Context, value: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(RATED, value.coerceIn(500, 20000)).apply()
    }

    fun trackingEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(TRACKING, false)

    fun setTrackingEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(TRACKING, enabled).apply()
    }

    @Synchronized
    fun sessions(context: Context): MutableList<ChargeSession> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(SESSIONS, "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            MutableList(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                val pointsArr = o.optJSONArray("points") ?: JSONArray()
                val points = MutableList(pointsArr.length()) { p ->
                    val point = pointsArr.getJSONObject(p)
                    ChargePoint(point.optLong("time"), point.optInt("percent"), point.optBoolean("plugged"))
                }
                ChargeSession(
                    id = o.optString("id", UUID.randomUUID().toString()),
                    startTime = o.optLong("startTime"),
                    endTime = if (o.isNull("endTime")) null else o.optLong("endTime"),
                    startPercent = o.optInt("startPercent"),
                    endPercent = o.optInt("endPercent"),
                    status = o.optString("status", "Incomplete"),
                    points = points
                )
            }
        } catch (_: Exception) {
            mutableListOf()
        }
    }

    @Synchronized
    fun saveSessions(context: Context, sessions: List<ChargeSession>) {
        val arr = JSONArray()
        sessions.forEach { s ->
            val o = JSONObject()
            o.put("id", s.id)
            o.put("startTime", s.startTime)
            if (s.endTime == null) o.put("endTime", JSONObject.NULL) else o.put("endTime", s.endTime)
            o.put("startPercent", s.startPercent)
            o.put("endPercent", s.endPercent)
            o.put("status", s.status)
            val points = JSONArray()
            s.points.forEach { p ->
                points.put(JSONObject().put("time", p.time).put("percent", p.percent).put("plugged", p.plugged))
            }
            o.put("points", points)
            arr.put(o)
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(SESSIONS, arr.toString()).apply()
    }

    fun activeId(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(ACTIVE_ID, null)

    fun setActiveId(context: Context, id: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(ACTIVE_ID, id).apply()
    }

    @Synchronized
    fun updateBattery(context: Context, percent: Int, plugged: Boolean) {
        val list = sessions(context)
        val activeId = activeId(context)
        var active = list.firstOrNull { it.id == activeId && it.endTime == null }

        if (plugged && active == null) {
            // Close any stale open record before starting a new one.
            list.filter { it.endTime == null }.forEach {
                it.endTime = System.currentTimeMillis()
                it.status = "Interrupted"
            }
            active = ChargeSession(
                id = UUID.randomUUID().toString(),
                startTime = System.currentTimeMillis(),
                endTime = null,
                startPercent = percent,
                endPercent = percent,
                status = "In progress"
            )
            active.points.add(ChargePoint(System.currentTimeMillis(), percent, true))
            list.add(0, active)
            setActiveId(context, active.id)
        } else if (!plugged && active != null) {
            active.endPercent = percent
            active.points.add(ChargePoint(System.currentTimeMillis(), percent, false))
            active.endTime = System.currentTimeMillis()
            active.status = if (percent >= 100) "Completed" else "Interrupted"
            setActiveId(context, null)
        } else if (plugged && active != null) {
            if (active.points.lastOrNull()?.percent != percent) {
                active.points.add(ChargePoint(System.currentTimeMillis(), percent, true))
            }
            active.endPercent = percent
            if (percent >= 100) active.status = "Full"
        }
        saveSessions(context, list)
    }
}
