package io.github.wingedpanther.cleanmytv.home

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.EditText
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.*
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Pure layout operations shared by the editor and the long-press menu. Nothing uninstalls apps. */
object HomeEdit {
    fun containers(c: HomeConfig) = listOf("Dock", "All apps") + c.rows.map { "Row: ${it.name}" } + c.folders.map { "Folder: ${it.name}" }
    fun apps(c: HomeConfig, container: Int, all: List<String>): List<String> = when {
        container == 0 -> dock(c, all)
        container == 1 -> (c.order + all).distinct().filter { it !in dock(c, all) && c.folders.none { g -> it in g.apps } }
        container < 2 + c.rows.size -> c.rows[container - 2].apps
        else -> c.folders[container - 2 - c.rows.size].apps
    }
    private fun dock(c: HomeConfig, all: List<String>) = c.dockApps ?: (c.order + all).distinct().filter { it in all && it !in c.hidden && c.folders.none { g -> it in g.apps } }.take(c.dockSize)
    fun source(c: HomeConfig, section: String): Int? = when {
        section == "dock" -> 0
        section == "grid" -> 1
        section.startsWith("row") -> section.removePrefix("row").toIntOrNull()?.takeIf { it in c.rows.indices }?.plus(2)
        section.startsWith("folder:") -> c.folders.indexOfFirst { it.name == section.removePrefix("folder:") }.takeIf { it >= 0 }?.plus(2 + c.rows.size)
        else -> null
    }
    /** Only the selected source placement moves; other shortcuts are independent. Null source adds a placement. */
    fun move(c: HomeConfig, pkg: String, to: Int, at: Int, all: List<String>, from: Int? = null): HomeConfig {
        require(to in containers(c).indices && (from == null || from in containers(c).indices))
        val target = apps(c, to, all).filter { it != pkg }.toMutableList()
        target.add(at.coerceIn(0, target.size), pkg)
        val oldDock = dock(c, all)
        val newDock = if (to == 0) target else if (from == 0) oldDock - pkg else oldDock
        require(newDock.size <= 8) { "Dock holds up to eight apps" }
        require(to < 2 || target.size <= 60) { "A row or folder holds up to sixty apps" }
        fun group(g: Group, container: Int) = g.copy(apps = when (container) { to -> target; from -> g.apps - pkg; else -> g.apps })
        val rows = c.rows.mapIndexed { i, g -> group(g, i + 2) }
        val folders = c.folders.mapIndexed { i, g -> group(g, i + 2 + c.rows.size) }
        val order = if (to == 1) newDock + target + (c.order + all).filter { it !in newDock && it !in target } else newDock + (c.order + all).distinct().filter { it !in newDock }
        return c.copy(order = order.distinct(), dockApps = newDock, dockSize = newDock.size, rows = rows, folders = folders, hidden = c.hidden - pkg,
            sections = (c.sections + when { to == 0 -> "dock"; to == 1 -> "grid"; to < 2 + c.rows.size -> "rows"; else -> "folders" }).distinct())
    }
    fun renameGroup(c: HomeConfig, folder: Boolean, index: Int, name: String): HomeConfig {
        val groups = if (folder) c.folders else c.rows
        require(index in groups.indices && name.isNotBlank() && name.length <= 30)
        require(groups.withIndex().none { (i,g) -> i != index && g.name.equals(name, true) }) { "That name already exists" }
        val next = groups.mapIndexed { i,g -> if (i == index) g.copy(name = name) else g }
        return if (folder) c.copy(folders = next) else c.copy(rows = next)
    }
    fun reorderGroup(c: HomeConfig, folder: Boolean, index: Int, direction: Int): HomeConfig {
        val groups = (if (folder) c.folders else c.rows).toMutableList()
        require(index in groups.indices && direction in -1..1)
        val target = (index + direction).coerceIn(0, groups.lastIndex)
        java.util.Collections.swap(groups, index, target)
        return if (folder) c.copy(folders = groups) else c.copy(rows = groups)
    }
    fun deleteGroup(c: HomeConfig, folder: Boolean, index: Int, all: List<String>): HomeConfig {
        val oldDock = dock(c, all)
        val next = if (folder) c.copy(folders = c.folders.filterIndexed { i, _ -> i != index }) else c.copy(rows = c.rows.filterIndexed { i, _ -> i != index })
        return next.copy(dockApps = oldDock, order = oldDock + (c.order + all).distinct().filter { it !in oldDock })
    }
    fun remove(c: HomeConfig, pkg: String, source: String, all: List<String>): HomeConfig = when {
        source == "dock" -> {
            val remaining = dock(c, all).filter { it != pkg }
            c.copy(dockApps = remaining, order = remaining + (c.order + all).distinct().filter { it !in remaining }, dockSize = remaining.size)
        }
        source.startsWith("row") -> c.copy(rows = c.rows.mapIndexed { i, g -> if (source == "row$i") g.copy(apps = g.apps - pkg) else g })
        source.startsWith("folder:") -> c.copy(folders = c.folders.map { if (it.name == source.removePrefix("folder:")) it.copy(apps = it.apps - pkg) else it })
        else -> c
    }
}

@Composable
fun EditorSheet(title: String, onClose: () -> Unit, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    val first = remember { FocusRequester() }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.width(640.dp).fillMaxHeight(0.9f).background(Color(0xFA1C2A1F)).padding(24.dp)) {
            Text(title, color = Color.White, fontSize = 24.sp)
            Text("D-pad to navigate • OK to confirm • Back to cancel", color = Color(0xFFD7CFC4), fontSize = 13.sp)
            LazyColumn(Modifier.weight(1f).padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { EditorButton("Back / Cancel", Modifier.focusRequester(first), onClose) }
                content()
            }
        }
        LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    }
}

@Composable
fun EditorButton(text: String, modifier: Modifier = Modifier, action: () -> Unit) {
    Surface(onClick = action, modifier = modifier.fillMaxWidth(), colors = ClickableSurfaceDefaults.colors(containerColor = Color(0xFF2D3A2E), focusedContainerColor = Color(0xFF506451))) {
        Text(text, color = Color.White, fontSize = 16.sp, modifier = Modifier.padding(12.dp))
    }
}

private fun rename(ctx: Context, title: String, value: String, done: (String) -> Unit) {
    val field = EditText(ctx).apply { setText(value); setSingleLine(true); filters = arrayOf(android.text.InputFilter.LengthFilter(30)) }
    AlertDialog.Builder(ctx).setTitle(title).setView(field).setNegativeButton("Cancel", null).setPositiveButton("Save") { _, _ ->
        field.text.toString().trim().takeIf { it.isNotEmpty() }?.let(done)
    }.show()
}

@Composable
fun MoveAppEditor(app: TvApp, initialCfg: HomeConfig, all: List<TvApp>, source: String? = null, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val cfg = remember { initialCfg }
    val revision = remember { ConfigStore.revision }
    var destination by remember { mutableIntStateOf(0) }
    var position by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf("") }
    val containers = HomeEdit.containers(cfg)
    val packages = all.map { it.pkg }
    val target = HomeEdit.apps(cfg, destination, packages).filter { it != app.pkg }
    EditorSheet("Move ${app.label}", onClose) {
        item { Text("Up/down: destination • Left/right: position", color = Color.White) }
        item {
            EditorButton("${containers[destination]} — position ${position + 1} of ${target.size + 1}", Modifier.onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) false else when (e.key) {
                    Key.DirectionUp -> { destination = (destination - 1).coerceAtLeast(0); position = 0; true }
                    Key.DirectionDown -> { destination = (destination + 1).coerceAtMost(containers.lastIndex); position = 0; true }
                    Key.DirectionLeft -> { position = (position - 1).coerceAtLeast(0); true }
                    Key.DirectionRight -> { position = (position + 1).coerceAtMost(target.size); true }
                    else -> false
                }
            }) {
                runCatching { ConfigStore.update(ctx, HomeEdit.move(cfg, app.pkg, destination, position, packages, source?.let { HomeEdit.source(cfg, it) }).toJson().put("_revision", revision)) }
                    .onSuccess { onClose() }.onFailure { error = it.message ?: "Move failed" }
            }
        }
        itemsIndexed(target) { i, pkg -> Text("${if (i == position) "→ " else ""}${all.firstOrNull { it.pkg == pkg }?.label ?: pkg}", color = Color.White) }
        item { Text(error, color = Color(0xFFFFB23C)) }
    }
}

@Composable
fun AppMenu(app: TvApp, source: String, cfg: HomeConfig, all: List<TvApp>, onMove: () -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    fun open(i: Intent) { runCatching { ctx.startActivity(i) }; onClose() }
    val scope = rememberCoroutineScope()
    var closeResult by remember { mutableStateOf("") }
    var closing by remember { mutableStateOf(false) }
    val pm = ctx.packageManager
    val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.pkg}"))
    val uninstall = Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.pkg}"))
    val system = runCatching { pm.getApplicationInfo(app.pkg, 0).flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0 }.getOrDefault(true)
    EditorSheet(app.label, onClose) {
        item { EditorButton("Open App") { if (Lock.needsPin(ctx, app.pkg)) open(LockActivity.intent(ctx, app.pkg, app.launch)) else open(app.launch) } }
        item { EditorButton("Move App / Move to Folder", action = onMove) }
        if (source == "dock" || source.startsWith("row") || source.startsWith("folder:")) item {
            EditorButton("Remove from this section") { ConfigStore.update(ctx, HomeEdit.remove(ConfigStore.config.value, app.pkg, source, all.map { it.pkg }).toJson()); onClose() }
        }
        if (TaskAccess.available() && !TaskAccess.protected(ctx, app.pkg) && !closing) item { EditorButton("Close App") {
            AlertDialog.Builder(ctx).setTitle("Close ${app.label}?").setMessage("Playback and unsaved work may be interrupted.").setNegativeButton("Cancel", null).setPositiveButton("Close") { _, _ ->
                scope.launch {
                    closing = true
                    closeResult = withContext(Dispatchers.IO) {
                        runCatching { TaskAccess.connect(ctx); kotlinx.coroutines.delay(400); TaskAccess.close(app.pkg) }.getOrElse { it.message ?: "Closing failed" }
                    }
                    closing = false
                }
            }.show()
        } }
        if (closeResult.isNotBlank()) item { Text(closeResult, color = Color.White) }
        if (pm.resolveActivity(details, 0) != null) {
            item { EditorButton("App Info") { if (Lock.needsPin(ctx, Lock.PANEL)) open(LockActivity.intent(ctx, Lock.PANEL, details)) else open(details) } }
            item { EditorButton("Close App — open Android Force stop controls") { if (Lock.needsPin(ctx, Lock.PANEL)) open(LockActivity.intent(ctx, Lock.PANEL, details)) else open(details) } }
        }
        if (!system && pm.resolveActivity(uninstall, 0) != null) item { EditorButton("Uninstall — Android confirmation") { if (Lock.needsPin(ctx, Lock.PANEL)) open(LockActivity.intent(ctx, Lock.PANEL, uninstall)) else open(uninstall) } }
    }
}

@Composable
fun HomeEditor(cfg: HomeConfig, all: List<TvApp>, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var choosingApp by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf<TvApp?>(null) }
    var themeManager by remember { mutableStateOf(false) }
    if (themeManager) { ThemeManager(cfg) { themeManager = false }; return }
    moving?.let { app -> MoveAppEditor(app, cfg, all) { moving = null }; return }
    if (choosingApp) {
        EditorSheet("Choose an app to move", { choosingApp = false }) {
            itemsIndexed(all) { _, app -> EditorButton(app.label) { moving = app } }
        }
        return
    }
    var selectedGroup by remember { mutableStateOf<Pair<Boolean, Int>?>(null) }
    fun set(c: HomeConfig) {
        val previous = cfg.toJson()
        val next = c.toJson()
        val patch = JSONObject()
        for (key in next.keys()) if (next.get(key).toString() != previous.get(key).toString()) patch.put(key, next.get(key))
        val revision = ConfigStore.revision
        val current = ConfigStore.config.value.toJson()
        val changedElsewhere = patch.keys().asSequence().any { current.get(it).toString() != previous.get(it).toString() }
        runCatching {
            check(!changedElsewhere) { "Settings changed. Reopen this editor and try again." }
            ConfigStore.update(ctx, patch.put("_revision", revision))
        }.onFailure { android.widget.Toast.makeText(ctx, it.message ?: "Settings were not saved", android.widget.Toast.LENGTH_LONG).show() }
    }
    selectedGroup?.let { (folder, index) ->
        val groups = if (folder) cfg.folders else cfg.rows
        val g = groups.getOrNull(index)
        if (g == null) { selectedGroup = null; return }
        EditorSheet(g.name, { selectedGroup = null }) {
            item { EditorButton("Add or rearrange apps") { choosingApp = true } }
            item { EditorButton("Rename") { rename(ctx, "Rename", g.name) { name -> if (groups.withIndex().any { (i, old) -> i != index && old.name.equals(name, true) }) android.widget.Toast.makeText(ctx, "That name already exists", android.widget.Toast.LENGTH_LONG).show() else set(HomeEdit.renameGroup(cfg, folder, index, name)) } } }
            item { EditorButton("Move earlier") { if (index > 0) { set(HomeEdit.reorderGroup(cfg, folder, index, -1)); selectedGroup = folder to index - 1 } } }
            item { EditorButton("Move later") { if (index < groups.lastIndex) { set(HomeEdit.reorderGroup(cfg, folder, index, 1)); selectedGroup = folder to index + 1 } } }
            item { EditorButton("Delete ${if (folder) "folder" else "row"} — keep all apps") {
                AlertDialog.Builder(ctx).setTitle("Delete ${g.name}?").setMessage("Applications stay installed and remain available in All apps.").setNegativeButton("Cancel", null).setPositiveButton("Delete") { _, _ -> set(HomeEdit.deleteGroup(cfg, folder, index, all.map { it.pkg })); selectedGroup = null }.show()
            } }
            item { Text("Hold OK on an app to move it into or out of this group, or rearrange it.", color = Color.White) }
        }
        return
    }
    EditorSheet("Edit Home Screen", onClose) {
        item { EditorButton("Move apps between Dock, rows and folders") { choosingApp = true } }
        item { EditorButton("Manage installed themes") { themeManager = true } }
        item { EditorButton("Layout: ${cfg.layout}") { val choices = listOf("shelf", "split", "rail"); set(cfg.copy(layout = choices[(choices.indexOf(cfg.layout) + 1) % choices.size])) } }
        item { EditorButton("Tile size: ${cfg.tileScale}% (Left/right)", Modifier.onPreviewKeyEvent { e ->
            if (e.type != KeyEventType.KeyDown || e.key !in listOf(Key.DirectionLeft, Key.DirectionRight)) false
            else { set(cfg.copy(tileScale = (cfg.tileScale + if (e.key == Key.DirectionRight) 5 else -5).coerceIn(60, 150))); true }
        }) {} }
        item { EditorButton("Dock size: ${cfg.dockScale}% (Left/right)", Modifier.onPreviewKeyEvent { e ->
            if (e.type != KeyEventType.KeyDown || e.key !in listOf(Key.DirectionLeft, Key.DirectionRight)) false
            else { set(cfg.copy(dockScale = (cfg.dockScale + if (e.key == Key.DirectionRight) 5 else -5).coerceIn(60, 150))); true }
        }) {} }
        item { EditorButton("Tile spacing: ${cfg.tileGap} dp (Left/right)", Modifier.onPreviewKeyEvent { e ->
            if (e.type != KeyEventType.KeyDown || e.key !in listOf(Key.DirectionLeft, Key.DirectionRight)) false
            else { set(cfg.copy(tileGap = (cfg.tileGap + if (e.key == Key.DirectionRight) 2 else -2).coerceIn(4, 40))); true }
        }) {} }
        item { EditorButton("Accent colour") { val colours = listOf(0xFFFFB23CL, 0xFF2EC4B6L, 0xFF7C5CFFL, 0xFF3DDC84L, 0xFFFF3000L, 0xFFFFFFFFL); set(cfg.copy(accent = colours[(colours.indexOf(cfg.accent) + 1).mod(colours.size)])) } }
        item { EditorButton("Wallpaper dimming: ${cfg.wallpaperDim}% (Left/right)", Modifier.onPreviewKeyEvent { e ->
            if (e.type != KeyEventType.KeyDown || e.key !in listOf(Key.DirectionLeft, Key.DirectionRight)) false
            else { set(cfg.copy(wallpaperDim = (cfg.wallpaperDim + if (e.key == Key.DirectionRight) 5 else -5).coerceIn(0, 80))); true }
        }) {} }
        item { EditorButton("Top spacing: ${cfg.topSpace}% (Left/right)", Modifier.onPreviewKeyEvent { e ->
            if (e.type != KeyEventType.KeyDown || e.key !in listOf(Key.DirectionLeft, Key.DirectionRight)) false
            else { set(cfg.copy(topSpace = (cfg.topSpace + if (e.key == Key.DirectionRight) 1 else -1).coerceIn(0, 40))); true }
        }) {} }
        item { EditorButton("Icon style: ${cfg.iconStyle}") { val opts = listOf("banner", "colour", "mono", "duo"); set(cfg.copy(iconStyle = opts[(opts.indexOf(cfg.iconStyle) + 1) % opts.size])) } }
        item { EditorButton("Tile shape: ${cfg.tileShape}") { val opts = listOf("wide", "square", "squircle", "round"); set(cfg.copy(tileShape = opts[(opts.indexOf(cfg.tileShape) + 1) % opts.size])) } }
        item { EditorButton("Tile background opacity: ${cfg.tileOpacity}% (Left/right)", Modifier.onPreviewKeyEvent { e ->
            if (e.type != KeyEventType.KeyDown || e.key !in listOf(Key.DirectionLeft, Key.DirectionRight)) false
            else { ConfigStore.update(ctx, JSONObject().put("tile_opacity", (cfg.tileOpacity + if (e.key == Key.DirectionRight) 5 else -5).coerceIn(0, 100))); true }
        }) {} }
        item { EditorButton("App labels: ${if (cfg.showNames) "shown" else "hidden"}") { set(cfg.copy(showNames = !cfg.showNames)) } }
        for (section in listOf("nowplaying", "dock", "rows", "folders", "grid")) item {
            EditorButton("${section.replaceFirstChar { it.uppercase() }}: ${if (section in cfg.sections) "shown" else "hidden"} — OK to toggle; Left/right to reorder", Modifier.onPreviewKeyEvent { e ->
                val i = cfg.sections.indexOf(section)
                if (e.type != KeyEventType.KeyDown || i < 0 || e.key !in listOf(Key.DirectionLeft, Key.DirectionRight)) false
                else { val j = (i + if (e.key == Key.DirectionRight) 1 else -1).coerceIn(0, cfg.sections.lastIndex); val ss = cfg.sections.toMutableList(); java.util.Collections.swap(ss, i, j); set(cfg.copy(sections = ss)); true }
            }) { set(cfg.copy(sections = if (section in cfg.sections) cfg.sections - section else cfg.sections + section)) }
        }
        item { Text("Section order: ${cfg.sections.joinToString(" → ")}", color = Color.White) }
        for (folder in listOf(false, true)) {
            val groups = if (folder) cfg.folders else cfg.rows
            val kind = if (folder) "folder" else "row"
            if (groups.size < if (folder) 12 else 8) item { EditorButton("Create $kind") { rename(ctx, "New $kind", "") { name -> if (groups.any { it.name.equals(name, true) }) android.widget.Toast.makeText(ctx, "That name already exists", android.widget.Toast.LENGTH_LONG).show() else set(if (folder) cfg.copy(folders = groups + Group(name, emptyList())) else cfg.copy(rows = groups + Group(name, emptyList()))) } } }
            itemsIndexed(groups) { i, g -> EditorButton("Edit $kind: ${g.name}") { selectedGroup = folder to i } }
        }
    }
}
