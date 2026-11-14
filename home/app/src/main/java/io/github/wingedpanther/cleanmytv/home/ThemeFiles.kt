package io.github.wingedpanther.cleanmytv.home

import java.io.File

/** Directory transaction used by real theme installs. Staging never mutates the live library. */
object ThemeFiles {
    fun live(root: File): File {
        val live = File(root, "themes")
        val old = File(root, "themes.old")
        if (!live.exists() && old.exists()) check(old.renameTo(live)) { "Could not recover themes" }
        return live
    }
    fun begin(root: File, now: Long = System.currentTimeMillis()) {
        val stage = File(root, "themes.upload")
        require(!stage.exists() || now - stage.lastModified() > 10 * 60_000) { "A theme upload is in progress. Retry in ten minutes after an interrupted upload." }
        stage.deleteRecursively()
        check(stage.mkdirs()) { "Cannot stage themes: check storage" }
        val current = live(root)
        if (current.exists()) current.copyRecursively(stage, overwrite = true)
    }
    fun commit(root: File, previous: List<String>, incoming: List<String>, writeOrder: (File, List<String>) -> Unit, validate: (File) -> Unit) {
        val validId = Regex("^[a-z0-9-]{1,40}$")
        require((previous + incoming).all { validId.matches(it) } && incoming.distinct().size == incoming.size) { "bad theme order" }
        val merged = previous + incoming.filter { it !in previous }
        val stage = File(root, "themes.upload")
        for (id in merged) validate(File(stage, id))
        writeOrder(stage, merged)
        val old = File(root, "themes.old")
        val current = live(root)
        old.deleteRecursively()
        if (current.exists()) check(current.renameTo(old)) { "Cannot back up themes" }
        if (!stage.renameTo(current)) {
            check(!old.exists() || old.renameTo(current)) { "Theme commit failed. Recovery copy retained in themes.old" }
            error("Cannot commit themes: previous library retained")
        }
        old.deleteRecursively()
    }
}
