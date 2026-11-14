package io.github.wingedpanther.cleanmytv.home

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** A remote button and what it does: an action id, or "app" with a package. */
data class ButtonMap(val code: Int, val action: String, val app: String? = null)

/** A named group of apps: a row on the home screen, or a folder. */
data class Group(val name: String, val apps: List<String>)

/** Everything CleanMyTV can change. The desktop app sends any subset; the rest keeps its value. */
data class HomeConfig(
    // Arrangement
    val layout: String = "shelf", // shelf, split, rail
    val sections: List<String> = listOf("nowplaying", "dock", "rows", "folders", "grid"), // top to bottom; absent = hidden
    val topSpace: Int = 25, // percent of the screen left open above the apps (shelf)
    val quick: List<String> = listOf("inputs", "wifi", "settings"), // quick-settings icons and "app:<package>" shortcuts, in order
    val rows: List<Group> = emptyList(),
    val folders: List<Group> = emptyList(),
    val dockScale: Int = 100, // percent
    val tileScale: Int = 100,
    val tileGap: Int = 16, // percent
    // Remote buttons (KeyService)
    val buttonsOn: Boolean = false,
    val buttons: List<ButtonMap> = emptyList(),
    // Look: CleanMyTV's dark green-and-earth world by default
    val accent: Long = 0xFF4CAF50,
    val focus: String = "lift", // lift, glow, outline, none
    val background: Long = 0xFF1C2A1F,
    val wallpaperDim: Int = 35, // percent of black over the wallpaper
    // Layout
    val order: List<String> = emptyList(), // apps first in this order, the rest alphabetically
    val hidden: Set<String> = emptySet(),
    val dockApps: List<String>? = null, // null preserves legacy implicit Dock membership
    val dockSize: Int = 5, // the first visible apps in `order` sit in the dock
    val columns: Int = 6, // apps per row in the grid
    val showNames: Boolean = true,
    // Feel
    val nameFocus: Boolean = false, // with showNames: only the focused app's name shows
    val followColour: Boolean = false, // the background takes the focused app's colour
    val pinFocus: Boolean = false, // the focused tile stays put and the row slides under it
    val tilt: Boolean = false, // with lift: the tile leans toward the move, then settles
    // Icons: "banner" is the app's own TV banner; the rest redraw every app's logo the same way
    val iconStyle: String = "banner", // banner, colour, mono, duo
    val tileShape: String = "wide", // wide, square, squircle, round; banner tiles are always wide
    val themeOpacity: Map<String, Int> = emptyMap(),
    val tileOpacity: Int = 100, // percentage of existing icon-tile background opacity; logos remain opaque
    val intro: Boolean = true, // a new wallpaper comes in like a sunrise
    /** Which built-in theme picture the wallpaper is, as "<theme id>:<picture>", or "" for anything else. */
    val wallpaperFrom: String = "",
    // Local day/night schedule. IDs refer to theme packs already installed on the TV.
    val autoTheme: Boolean = false,
    val dayTheme: String = "",
    val nightTheme: String = "",
    val dayTime: String = "07:00",
    val nightTime: String = "19:00",
    // App lock: the PIN itself lives in Lock, never in these settings
    val lockOn: Boolean = false,
    val lockApps: List<String> = emptyList(),
    val lockSystem: Boolean = false, // Settings, the Play Store, the uninstaller and Quick settings too; off until ticked
    val lockFor: String = "sleep", // sleep, 15, always: how long an unlock lasts
    val lockShow: String = "badge", // badge, hide: locked apps on Home
    // Fireplace: a looping video CleanMyTV puts on the TV, as the screensaver, full screen, or the wallpaper
    val fireWall: Boolean = false, // behind Home instead of the picture
    val fireSound: Boolean = true, // full screen and as the screensaver
    val fireWallSound: Boolean = false, // behind Home: quiet unless asked
    val fireVolume: Int = 40,
    val otherApps: Boolean = false, // phone apps with no TV banner go in an "Other apps" folder (#3)
    val fireTimer: Int = 0, // full screen: minutes until the fire goes out and the TV sleeps; 0 burns on
    // Slideshow: a new wallpaper every few minutes, from the owner's pictures or the current theme's; 0 is off
    val slideMinutes: Int = 0,
    val slideFrom: String = "mine", // mine, theme
    val clock: String = "time_date", // time_date, time, off
    val greeting: String = "",
    // Idle screen: after this many minutes without a button press, only the wallpaper and the clock show; 0 is off
    val idleMinutes: Int = 0,
    // Weather next to the clock, from Open-Meteo; off while the place is blank
    val weatherPlace: String = "",
    val weatherLat: Double = 0.0,
    val weatherLon: Double = 0.0,
    val weatherUnit: String = "c", // c, f
    // Music bars
    val visualiser: Boolean = true,
    val vizStyle: String = "bars", // bars, mirror, wave
    val vizBars: Int = 32,
    val vizHeight: Int = 12, // percent of the screen height
    val vizOpacity: Int = 85,
    val vizColour: String = "accent", // accent, rainbow, white
    val vizPeaks: Boolean = true,
    val vizSensitivity: Int = 100, // percent
) {
    fun toJson(): JSONObject = JSONObject()
        .put("layout", layout)
        .put("sections", JSONArray(sections))
        .put("top_space", topSpace)
        .put("quick", JSONArray(quick))
        .put("rows", groupsJson(rows))
        .put("folders", groupsJson(folders))
        .put("dock_scale", dockScale)
        .put("tile_scale", tileScale)
        .put("tile_gap", tileGap)
        .put("buttons_on", buttonsOn)
        .put("buttons", JSONArray(buttons.map { b -> JSONObject().put("code", b.code).put("action", b.action).apply { b.app?.let { put("app", it) } } }))
        .put("accent", hex(accent))
        .put("focus", focus)
        .put("background", hex(background))
        .put("wallpaper_dim", wallpaperDim)
        .put("order", JSONArray(order))
        .put("hidden", JSONArray(hidden.sorted()))
        .put("dock_apps", dockApps?.let { JSONArray(it) } ?: JSONObject.NULL)
        .put("dock_size", dockSize)
        .put("columns", columns)
        .put("show_names", showNames)
        .put("name_focus", nameFocus)
        .put("follow_colour", followColour)
        .put("pin_focus", pinFocus)
        .put("tilt", tilt)
        .put("icon_style", iconStyle)
        .put("tile_shape", tileShape)
        .put("theme_opacity", JSONObject(themeOpacity))
        .put("tile_opacity", tileOpacity)
        .put("intro", intro)
        .put("wallpaper_from", wallpaperFrom)
        .put("auto_theme", autoTheme)
        .put("day_theme", dayTheme)
        .put("night_theme", nightTheme)
        .put("day_time", dayTime)
        .put("night_time", nightTime)
        .put("lock_on", lockOn)
        .put("lock_apps", JSONArray(lockApps))
        .put("lock_system", lockSystem)
        .put("lock_for", lockFor)
        .put("lock_show", lockShow)
        .put("fire_wall", fireWall)
        .put("fire_sound", fireSound)
        .put("fire_wall_sound", fireWallSound)
        .put("fire_volume", fireVolume)
        .put("other_apps", otherApps)
        .put("fire_timer", fireTimer)
        .put("slide_minutes", slideMinutes)
        .put("slide_from", slideFrom)
        .put("clock", clock)
        .put("greeting", greeting)
        .put("idle_minutes", idleMinutes)
        .put("weather_place", weatherPlace)
        .put("weather_lat", weatherLat)
        .put("weather_lon", weatherLon)
        .put("weather_unit", weatherUnit)
        .put("visualiser", visualiser)
        .put("viz_style", vizStyle)
        .put("viz_bars", vizBars)
        .put("viz_height", vizHeight)
        .put("viz_opacity", vizOpacity)
        .put("viz_colour", vizColour)
        .put("viz_peaks", vizPeaks)
        .put("viz_sensitivity", vizSensitivity)

    companion object {
        private val HEX = Regex("^#[0-9A-Fa-f]{6}$")
        private val PKG = Regex("^[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")

        private fun hex(c: Long) = "#%06X".format(c and 0xFFFFFF)
        val QUICK = setOf("settings", "wifi", "inputs", "bluetooth", "display", "apps", "sleep", "search", "music", "panel", "themes", "fireplace")
        private val THEME_PICTURE = Regex("^[a-z0-9-]{1,40}:[0-9]$")
        private val THEME_ID = Regex("^[a-z0-9-]{1,40}$")
        private val LOCAL_TIME = Regex("^(?:[01][0-9]|2[0-3]):[0-5][0-9]$")
        private val SECTIONS = setOf("nowplaying", "dock", "rows", "folders", "grid")
        val BUTTON_ACTIONS = QUICK - "inputs" + setOf("inputs", "home", "back", "none", "app", "next_wallpaper")
        /** App shortcuts in the top bar, after the quick-settings icons that fit. */
        const val MAX_QUICK_APPS = 6
        fun quickApp(entry: String): String? = entry.removePrefix("app:").takeIf { entry.startsWith("app:") && PKG.matches(it) }

        private fun groupsJson(gs: List<Group>) = JSONArray(gs.map { JSONObject().put("name", it.name).put("apps", JSONArray(it.apps)) })

        /** Unknown keys are ignored and bad values keep what was there, so an old or new desktop app can't break Home. */
        fun fromJson(o: JSONObject, base: HomeConfig = HomeConfig()): HomeConfig {
            fun colour(key: String, cur: Long) =
                o.optString(key).takeIf { HEX.matches(it) }?.let { 0xFF000000 or it.substring(1).toLong(16) } ?: cur
            fun int(key: String, cur: Int, lo: Int, hi: Int) =
                if (o.has(key)) o.optInt(key, cur).coerceIn(lo, hi) else cur
            fun real(key: String, cur: Double, lim: Double) =
                if (o.has(key)) o.optDouble(key, cur).takeIf { it.isFinite() }?.coerceIn(-lim, lim) ?: cur else cur
            fun bool(key: String, cur: Boolean) = if (o.has(key)) o.optBoolean(key, cur) else cur
            fun choice(key: String, cur: String, allowed: Set<String>) = o.optString(key).takeIf { it in allowed } ?: cur
            fun pkgs(key: String): List<String>? = o.optJSONArray(key)?.let { a ->
                (0 until minOf(a.length(), 500)).map { a.optString(it) }.filter { PKG.matches(it) }.distinct()
            }
            fun strings(key: String, allowed: Set<String>): List<String>? = o.optJSONArray(key)?.let { a ->
                (0 until a.length()).map { a.optString(it) }.filter { it in allowed }.distinct()
            }
            fun groups(key: String, max: Int): List<Group>? = o.optJSONArray(key)?.let { a ->
                (0 until minOf(a.length(), max)).mapNotNull { i ->
                    val g = a.optJSONObject(i) ?: return@mapNotNull null
                    val name = g.optString("name").replace(Regex("[\\p{Cntrl}]"), "").trim().take(30)
                    val apps = g.optJSONArray("apps")?.let { x -> (0 until minOf(x.length(), 60)).map { x.optString(it) }.filter { PKG.matches(it) }.distinct() } ?: emptyList()
                    if (name.isEmpty()) null else Group(name, apps)
                }
            }
            fun buttons(): List<ButtonMap>? = o.optJSONArray("buttons")?.let { a ->
                (0 until minOf(a.length(), 30)).mapNotNull { i ->
                    val b = a.optJSONObject(i) ?: return@mapNotNull null
                    val code = b.optInt("code", -1)
                    val action = b.optString("action")
                    val app = b.optString("app").takeIf { PKG.matches(it) }
                    if (code !in 1..65535 || action !in BUTTON_ACTIONS || (action == "app" && app == null)) null
                    else ButtonMap(code, action, if (action == "app") app else null)
                }.distinctBy { it.code }
            }
            return base.copy(
                buttonsOn = bool("buttons_on", base.buttonsOn),
                buttons = buttons() ?: base.buttons,
                layout = choice("layout", base.layout, setOf("shelf", "split", "rail")),
                sections = strings("sections", SECTIONS) ?: base.sections,
                topSpace = int("top_space", base.topSpace, 0, 40),
                quick = o.optJSONArray("quick")?.let { a ->
                    val all = (0 until minOf(a.length(), 30)).map { a.optString(it) }.filter { it in QUICK || quickApp(it) != null }.distinct()
                    val apps = all.filter { quickApp(it) != null }.take(MAX_QUICK_APPS).toSet()
                    all.filter { it in QUICK || it in apps }
                } ?: base.quick,
                rows = groups("rows", 8) ?: base.rows,
                folders = groups("folders", 12) ?: base.folders,
                dockScale = int("dock_scale", base.dockScale, 60, 150),
                tileScale = int("tile_scale", base.tileScale, 60, 150),
                tileGap = int("tile_gap", base.tileGap, 4, 40),
                accent = colour("accent", base.accent),
                focus = choice("focus", base.focus, setOf("lift", "glow", "outline", "none")),
                background = colour("background", base.background),
                wallpaperDim = int("wallpaper_dim", base.wallpaperDim, 0, 80),
                order = pkgs("order") ?: base.order,
                hidden = pkgs("hidden")?.toSet() ?: base.hidden,
                dockApps = if (o.has("dock_apps")) if (o.isNull("dock_apps")) null else pkgs("dock_apps")?.take(8) ?: base.dockApps else base.dockApps,
                dockSize = int("dock_size", base.dockSize, 0, 8),
                columns = int("columns", base.columns, 3, 8),
                showNames = bool("show_names", base.showNames),
                nameFocus = bool("name_focus", base.nameFocus),
                followColour = bool("follow_colour", base.followColour),
                pinFocus = bool("pin_focus", base.pinFocus),
                tilt = bool("tilt", base.tilt),
                iconStyle = choice("icon_style", base.iconStyle, setOf("banner", "colour", "mono", "duo")),
                tileShape = choice("tile_shape", base.tileShape, setOf("wide", "square", "squircle", "round")),
                themeOpacity = o.optJSONObject("theme_opacity")?.let { m ->
                    m.keys().asSequence().take(500).filter { THEME_ID.matches(it) && m.opt(it) is Number }.associateWith { m.optInt(it).coerceIn(0, 100) }
                } ?: base.themeOpacity,
                tileOpacity = int("tile_opacity", base.tileOpacity, 0, 100),
                intro = bool("intro", base.intro),
                wallpaperFrom = if (o.has("wallpaper_from")) o.optString("wallpaper_from").takeIf { it.isEmpty() || THEME_PICTURE.matches(it) } ?: base.wallpaperFrom else base.wallpaperFrom,
                autoTheme = bool("auto_theme", base.autoTheme),
                dayTheme = if (o.has("day_theme")) o.optString("day_theme").takeIf { it.isEmpty() || THEME_ID.matches(it) } ?: base.dayTheme else base.dayTheme,
                nightTheme = if (o.has("night_theme")) o.optString("night_theme").takeIf { it.isEmpty() || THEME_ID.matches(it) } ?: base.nightTheme else base.nightTheme,
                dayTime = if (o.has("day_time")) o.optString("day_time").takeIf { LOCAL_TIME.matches(it) } ?: base.dayTime else base.dayTime,
                nightTime = if (o.has("night_time")) o.optString("night_time").takeIf { LOCAL_TIME.matches(it) } ?: base.nightTime else base.nightTime,
                lockOn = bool("lock_on", base.lockOn),
                lockApps = pkgs("lock_apps") ?: base.lockApps,
                lockSystem = bool("lock_system", base.lockSystem),
                lockFor = choice("lock_for", base.lockFor, setOf("sleep", "15", "always")),
                lockShow = choice("lock_show", base.lockShow, setOf("badge", "hide")),
                fireWall = bool("fire_wall", base.fireWall),
                fireSound = bool("fire_sound", base.fireSound),
                fireWallSound = bool("fire_wall_sound", base.fireWallSound),
                fireVolume = int("fire_volume", base.fireVolume, 0, 100),
                otherApps = bool("other_apps", base.otherApps),
                fireTimer = int("fire_timer", base.fireTimer, 0, 240),
                slideMinutes = int("slide_minutes", base.slideMinutes, 0, 240),
                slideFrom = choice("slide_from", base.slideFrom, setOf("mine", "theme")),
                clock = choice("clock", base.clock, setOf("time_date", "time", "off")),
                idleMinutes = int("idle_minutes", base.idleMinutes, 0, 60),
                greeting = if (o.has("greeting")) o.optString("greeting").take(40) else base.greeting,
                weatherPlace = if (o.has("weather_place")) o.optString("weather_place").replace(Regex("[\\p{Cntrl}]"), "").trim().take(40) else base.weatherPlace,
                weatherLat = real("weather_lat", base.weatherLat, 90.0),
                weatherLon = real("weather_lon", base.weatherLon, 180.0),
                weatherUnit = choice("weather_unit", base.weatherUnit, setOf("c", "f")),
                visualiser = bool("visualiser", base.visualiser),
                vizStyle = choice("viz_style", base.vizStyle, setOf("bars", "mirror", "wave")),
                vizBars = int("viz_bars", base.vizBars, 8, 64),
                vizHeight = int("viz_height", base.vizHeight, 4, 40),
                vizOpacity = int("viz_opacity", base.vizOpacity, 20, 100),
                vizColour = choice("viz_colour", base.vizColour, setOf("accent", "rainbow", "white")),
                vizPeaks = bool("viz_peaks", base.vizPeaks),
                vizSensitivity = int("viz_sensitivity", base.vizSensitivity, 25, 300),
            )
        }
    }
}

/**
 * Settings and the wallpaper live in the app's private storage, which is there as soon as Home starts.
 * That is the fix for upstream's reboot reset (#21) and the folder Android won't let ADB create (#20).
 */
object ConfigStore {
    private val _config = MutableStateFlow(HomeConfig())
    val config: StateFlow<HomeConfig> = _config
    /** Bumped when a new wallpaper lands, so Home reloads it. */
    private val _wallpaperVersion = MutableStateFlow(0)
    val wallpaperVersion: StateFlow<Int> = _wallpaperVersion
    private var loaded = false
    var revision: Long = 0
        private set

    private fun file(ctx: Context) = File(ctx.filesDir, "config.json")
    /** False on a fresh install, before anything was set: CleanMyTV then puts its saved copy back. */
    fun configured(ctx: Context) = file(ctx).exists()
    fun wallpaper(ctx: Context) = File(ctx.filesDir, "wallpaper.jpg")
    fun wallpaperUpload(ctx: Context) = File(ctx.filesDir, "wallpaper.upload")

    @Synchronized
    fun load(ctx: Context) {
        if (loaded) return
        loaded = true
        val f = file(ctx)
        if (f.exists()) runCatching { val saved = JSONObject(String(android.util.AtomicFile(f).readFully(), Charsets.UTF_8)); _config.value = HomeConfig.fromJson(saved); revision = saved.optLong("_revision", 0) }
    }

    /** Merges a partial update, saves it atomically, and the UI follows through the flow. */
    @Synchronized
    fun update(ctx: Context, json: JSONObject): HomeConfig {
        load(ctx)
        if (json.has("_revision") && json.getLong("_revision") != revision) throw IllegalStateException("Settings changed on the TV. Refresh before editing again.")
        var next = HomeConfig.fromJson(json, _config.value)
        val id = next.wallpaperFrom.substringBefore(":")
        if (json.has("tile_opacity") && !json.has("wallpaper_from") && id.isNotEmpty()) {
            next = next.copy(themeOpacity = next.themeOpacity + (id to next.tileOpacity))
        }
        if (json.has("theme_opacity") && id.isNotEmpty()) {
            val theme = ThemePack.list(ctx).firstOrNull { it.id == id }
            next = next.copy(tileOpacity = next.themeOpacity[id] ?: theme?.look?.optInt("tile_opacity", 100) ?: 100)
        }
        val nextRevision = revision + 1
        val text = next.toJson().put("_revision", nextRevision).toString(2)
        val storage = android.util.AtomicFile(file(ctx))
        var stream: java.io.FileOutputStream? = null
        try {
            stream = storage.startWrite()
            stream.write(text.toByteArray(Charsets.UTF_8))
            storage.finishWrite(stream)
        } catch (e: Exception) {
            storage.failWrite(stream)
            throw e
        }
        revision = nextRevision
        _config.value = next
        if (json.has("wallpaper_from") && !json.optBoolean("_scheduled", false)) ThemeSchedule.manual(ctx, next)
        return next
    }

    fun wallpaperChanged() { _wallpaperVersion.value++ }
}
