package io.github.wingedpanther.cleanmytv.home

import android.content.Context
import java.time.LocalDate
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
    private const val LAST = "last-boundary"

    /** Re-arm immediately after enabling or disabling the schedule on the desktop. */
    fun reset(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(LAST).apply()
    }

    fun check(ctx: Context) {
        val cfg = ConfigStore.config.value
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!cfg.autoTheme) {
            // Re-enabling after a disabled period should apply the current theme anew.
            if (prefs.contains(LAST)) prefs.edit().remove(LAST).apply()
            return
        }
        if (cfg.dayTheme.isBlank() || cfg.nightTheme.isBlank() || cfg.dayTime == cfg.nightTime) return
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        val date = now.toLocalDate()
        val slots = listOf(date, date.minusDays(1)).flatMap { day ->
            listOf(
                day.atTime(LocalTime.parse(cfg.dayTime)).atZone(zone) to cfg.dayTheme,
                day.atTime(LocalTime.parse(cfg.nightTime)).atZone(zone) to cfg.nightTheme
            )
        }
        val active = slots.filter { !it.first.isAfter(now) }.maxByOrNull { it.first.toInstant() } ?: return
        val boundary = active.first.toInstant().toString()
        // Include all schedule options so changes made on the Mac apply on the next check.
        val key = listOf(cfg.dayTheme, cfg.nightTheme, cfg.dayTime, cfg.nightTime, boundary, active.second).joinToString("|")
        if (prefs.getString(LAST, null) == key) return
        val t = ThemePack.list(ctx).firstOrNull { it.id == active.second } ?: return
        if (!ThemePack.picture(ctx, t.id, 0).exists()) return
        ThemePack.apply(ctx, t, 0)
        prefs.edit().putString(LAST, key).apply()
    }
}
