package io.github.wingedpanther.cleanmytv.home

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** A theme CleanMyTV put on the TV: its look, and a few pictures drawn for it. */
data class PackTheme(val id: String, val name: String, val look: JSONObject, val pictures: Int)

/**
 * The built-in themes, so the TV can switch them without the computer: CleanMyTV draws each theme's
 * pictures and uploads them once ("Put themes on the TV"). They go into themes.upload/ and replace
 * themes/ only when the last one has arrived, so a broken upload leaves the old set alone.
 */
object ThemePack {
    private val ID = Regex("^[a-z0-9-]{1,40}$")
    const val MAX_PICTURES = 4
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version

    private fun dir(ctx: Context) = ThemeFiles.live(ctx.filesDir)
    private fun upload(ctx: Context) = File(ctx.filesDir, "themes.upload")
    fun picture(ctx: Context, id: String, v: Int) = File(File(dir(ctx), id), "$v.jpg")

    @Synchronized
    fun list(ctx: Context): List<PackTheme> {
        val order = runCatching { JSONArray(File(dir(ctx), "order.json").readText()) }.getOrNull() ?: return emptyList()
        return (0 until order.length()).mapNotNull { i ->
            val id = order.optString(i).takeIf { ID.matches(it) } ?: return@mapNotNull null
            val meta = runCatching { JSONObject(File(File(dir(ctx), id), "meta.json").readText()) }.getOrNull() ?: return@mapNotNull null
            val n = (0 until MAX_PICTURES).count { picture(ctx, id, it).exists() }
            if (n == 0) null else PackTheme(id, meta.optString("name", id).take(40), meta.optJSONObject("look") ?: JSONObject(), n)
        }
    }

    // ---- upload, from ConfigProvider --------------------------------------------------------------

    @Synchronized
    fun begin(ctx: Context) { ThemeFiles.begin(ctx.filesDir) }

    fun meta(ctx: Context, o: JSONObject) {
        val id = o.optString("id").takeIf { ID.matches(it) } ?: throw IllegalArgumentException("bad theme id")
        // Only the look settings a theme may carry; the rest of the config stays the owner's.
        val look = HomeConfig.fromJson(o.optJSONObject("look") ?: JSONObject()).toJson()
        val keep = JSONObject()
        for (k in LOOK_KEYS) if (o.optJSONObject("look")?.has(k) == true) keep.put(k, look.get(k))
        File(upload(ctx), id).mkdirs()
        File(File(upload(ctx), id), "meta.json").writeText(JSONObject().put("name", o.optString("name").take(40)).put("look", keep).toString())
    }

    /** Where the next picture's bytes go, as "<id>:<picture>". */
    fun target(ctx: Context, spec: String): File {
        val (id, v) = spec.split(":").let { it.getOrNull(0).orEmpty() to (it.getOrNull(1)?.toIntOrNull() ?: -1) }
        require(ID.matches(id) && v in 0 until MAX_PICTURES) { "bad picture $spec" }
        return File(File(upload(ctx), id), "$v.jpg")
    }

    @Synchronized
    fun end(ctx: Context, order: JSONArray) {
        val previous = list(ctx).map { it.id }
        val incoming = (0 until order.length()).map { order.getString(it) }
        ThemeFiles.commit(ctx.filesDir, previous, incoming,
            writeOrder = { stage, merged -> File(stage, "order.json").writeText(JSONArray(merged).toString()) }) { folder ->
            val id = folder.name
            require(File(folder, "meta.json").isFile && File(folder, "0.jpg").isFile) { "Incomplete theme: $id" }
            JSONObject(File(folder, "meta.json").readText())
            for (v in 0 until MAX_PICTURES) {
                val pic = File(folder, "$v.jpg")
                if (pic.exists()) {
                    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    android.graphics.BitmapFactory.decodeFile(pic.path, bounds)
                    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Invalid picture: $id:$v" }
                }
            }
        }
        _version.value++
    }

    /** Save a current look entirely on-device. No wallpaper roundtrip or replacement library. */
    @Synchronized
    fun saveCurrent(ctx: Context, id: String, name: String) {
        require(ID.matches(id) && name.isNotBlank()) { "Choose a valid theme name" }
        val cfg = ConfigStore.config.value
        require(list(ctx).none { it.id != id && it.name.equals(name, ignoreCase = true) }) { "That name already exists. Select that theme to update it." }
        require(ConfigStore.wallpaper(ctx).isFile) { "Choose a wallpaper before saving a theme" }
        begin(ctx)
        meta(ctx, JSONObject().put("id", id).put("name", name).put("look", cfg.toJson()))
        ConfigStore.wallpaper(ctx).copyTo(target(ctx, "$id:0"), overwrite = true)
        end(ctx, JSONArray(listOf(id)))
    }

    fun rename(ctx: Context, id: String, name: String) {
        val theme = list(ctx).firstOrNull { it.id == id } ?: error("Theme no longer installed")
        require(name.isNotBlank() && list(ctx).none { it.id != id && it.name.equals(name, true) }) { "Choose a unique name" }
        begin(ctx)
        meta(ctx, JSONObject().put("id", id).put("name", name).put("look", theme.look))
        end(ctx, JSONArray(listOf(id)))
    }

    fun clear(ctx: Context) { dir(ctx).deleteRecursively(); _version.value++ }

    // ---- use, from the carousel and the remote ----------------------------------------------------

    /** The theme's look and picture [v] become Home's, and the new wallpaper comes in with the intro. */
    fun apply(ctx: Context, t: PackTheme, v: Int, withLook: Boolean = true, scheduled: Boolean = false) {
        val src = picture(ctx, t.id, v)
        if (!src.exists()) return
        val tmp = ConfigStore.wallpaperUpload(ctx)
        src.copyTo(tmp, overwrite = true)
        check(tmp.renameTo(ConfigStore.wallpaper(ctx))) { "Cannot apply wallpaper: previous wallpaper retained" }
        val change = if (withLook) JSONObject(t.look.toString()).put("tile_opacity", ConfigStore.config.value.themeOpacity[t.id] ?: t.look.optInt("tile_opacity", 100)) else JSONObject()
        ConfigStore.update(ctx, change.put("wallpaper_from", "${t.id}:$v").put("_scheduled", scheduled))
        ConfigStore.wallpaperChanged()
    }

    /** The current theme's next picture, for a remote button. Nothing happens on a wallpaper of the owner's own. */
    fun next(ctx: Context) {
        val (id, v) = ConfigStore.config.value.wallpaperFrom.split(":").takeIf { it.size == 2 } ?: return
        val t = list(ctx).firstOrNull { it.id == id } ?: run { Log.i(TAG, "next wallpaper: $id isn't on the TV"); return }
        apply(ctx, t, ((v.toIntOrNull() ?: 0) + 1) % t.pictures, withLook = false)
    }

    private val LOOK_KEYS = listOf(
        "dock_scale", "tile_scale", "tile_gap", "show_names", "clock", "visualiser", "viz_bars", "viz_height", "viz_opacity", "viz_sensitivity", "accent", "background", "focus", "wallpaper_dim", "top_space", "viz_style", "viz_colour", "viz_peaks", "icon_style", "tile_shape", "tile_opacity",
    )
}
