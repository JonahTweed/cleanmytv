package io.github.wingedpanther.cleanmytv.home

/** Every remote editing entry inherits the system-settings policy and the selected app's lock. */
object EditorAccess {
    enum class Entry { APP_MENU, FOLDER, EDITOR, PANEL, OPEN_APPS }
    fun targets(cfg: HomeConfig, hasPin: Boolean, entry: Entry, app: String? = null): List<String> {
        if (!cfg.lockOn || !hasPin) return emptyList()
        val targets = mutableListOf<String>()
        if (cfg.lockSystem) targets.add(Lock.PANEL)
        if (app != null && app in cfg.lockApps) targets.add(app)
        return targets.distinct()
    }
}
