package io.github.wingedpanther.cleanmytv.home

import android.content.Context
import android.content.Intent
import android.content.BroadcastReceiver
import android.app.AlarmManager
import android.app.PendingIntent
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Checks the two local daily boundaries without requiring the desktop app to remain running.
 * A manual theme change persists until the next scheduled boundary (not the next check).
 * All theme files and the schedule stay in the launcher's private storage across restarts.
 */
object ThemeSchedule {
    private const val PREFS = "cleanmytv-theme-schedule"
    private var armedFor: Long? = null
    private const val LAST = "last-boundary"

    /** Re-arm immediately after enabling or disabling the schedule on the desktop. */
    fun reset(ctx: Context) {
        armedFor = null
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(LAST).apply()
    }

    @Synchronized
    fun check(ctx: Context) {
        ConfigStore.load(ctx)
        val cfg = ConfigStore.config.value
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        arm(ctx, cfg)
        if (!cfg.autoTheme) {
            // Re-enabling after a disabled period should apply the current theme anew.
            if (prefs.contains(LAST)) prefs.edit().remove(LAST).apply()
            return
        }
        if (cfg.dayTheme.isBlank() || cfg.nightTheme.isBlank() || cfg.dayTime == cfg.nightTime) return
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        val active = ScheduleClock.active(now, cfg.dayTime, cfg.nightTime, cfg.dayTheme, cfg.nightTheme) ?: return
        val boundary = active.first.toInstant().toString()
        // Include all schedule options so changes made on the computer apply on the next check.
        val key = listOf(cfg.dayTheme, cfg.nightTheme, cfg.dayTime, cfg.nightTime, boundary, zone.id, active.second).joinToString("|")
        if (prefs.getString(LAST, null) == key) return
        val t = ThemePack.list(ctx).firstOrNull { it.id == active.second } ?: return
        if (!ThemePack.picture(ctx, t.id, 0).exists()) return
        ThemePack.apply(ctx, t, 0, scheduled = true)
        prefs.edit().putString(LAST, key).apply()
    }
    /** Record the current boundary even when a manual selection precedes the first scheduler tick. */
    fun manual(ctx: Context, cfg: HomeConfig) {
        if (!cfg.autoTheme || cfg.dayTheme.isBlank() || cfg.nightTheme.isBlank() || cfg.dayTime == cfg.nightTime) return
        val now = ZonedDateTime.now()
        val active = ScheduleClock.active(now, cfg.dayTime, cfg.nightTime, cfg.dayTheme, cfg.nightTheme) ?: return
        val key = listOf(cfg.dayTheme, cfg.nightTheme, cfg.dayTime, cfg.nightTime, active.first.toInstant().toString(), now.zone.id, active.second).joinToString("|")
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(LAST, key).apply()
    }

    private fun arm(ctx: Context, cfg: HomeConfig) {
        val manager = ctx.getSystemService(AlarmManager::class.java)
        val intent = PendingIntent.getBroadcast(ctx, 24, Intent(ctx, ThemeScheduleReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        if (!cfg.autoTheme || cfg.dayTheme.isBlank() || cfg.nightTheme.isBlank() || cfg.dayTime == cfg.nightTime) {
            manager.cancel(intent); armedFor = null; return
        }
        val now = ZonedDateTime.now()
        val next = ScheduleClock.boundaries(now, cfg.dayTime, cfg.nightTime, cfg.dayTheme, cfg.nightTheme)
            .filter { it.first.isAfter(now) }.minOf { it.first.toInstant().toEpochMilli() }
        if (armedFor == next) return
        // No exact-alarm permission requested. Android may defer this in deep sleep; Home checks on resume too.
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, intent)
        armedFor = next
    }

}

/** Local wall-clock boundaries use java.time's explicit DST gap/overlap resolution. */
object ScheduleClock {
    fun boundaries(now: ZonedDateTime, day: String, night: String, dayId: String, nightId: String): List<Pair<ZonedDateTime, String>> =
        (-1L..1L).flatMap { offset ->
            val date = now.toLocalDate().plusDays(offset)
            listOf(date.atTime(LocalTime.parse(day)).atZone(now.zone) to dayId, date.atTime(LocalTime.parse(night)).atZone(now.zone) to nightId)
        }
    fun active(now: ZonedDateTime, day: String, night: String, dayId: String, nightId: String) =
        boundaries(now, day, night, dayId, nightId).filter { !it.first.isAfter(now) }.maxByOrNull { it.first.toInstant() }
}

class ThemeScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val pending = goAsync()
        Thread {
            try { ConfigStore.load(ctx); ThemeSchedule.check(ctx) }
            catch (e: Exception) { android.util.Log.w(TAG, "Scheduled theme unavailable", e) }
            finally { pending.finish() }
        }.start()
    }
}
