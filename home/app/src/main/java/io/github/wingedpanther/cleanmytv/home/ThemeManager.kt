package io.github.wingedpanther.cleanmytv.home

import android.app.AlertDialog
import android.widget.EditText
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Composable
fun ThemeManager(cfg: HomeConfig, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val version by ThemePack.version.collectAsState()
    val installed = remember(version) { ThemePack.list(ctx) }
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    fun perform(action: () -> Unit) { scope.launch {
        busy = true
        status = withContext(Dispatchers.IO) { runCatching(action).fold({ "Saved" }, { it.message ?: "Unable to save" }) }
        busy = false
    } }
    fun name(initial: String, save: (String) -> Unit) {
        val input = EditText(ctx).apply { setText(initial); setSingleLine(); filters = arrayOf(android.text.InputFilter.LengthFilter(40)) }
        AlertDialog.Builder(ctx).setTitle("Theme name").setView(input).setNegativeButton("Cancel", null).setPositiveButton("Save") { _, _ ->
            val n = input.text.toString().trim(); if (n.isNotEmpty()) perform { save(n) }
        }.show()
    }
    val theme = installed.firstOrNull { it.id == selected }
    EditorSheet(theme?.name ?: "Installed themes", { if (selected != null) selected = null else onClose() }) {
        item { Text(status, color = Color.White) }
        if (!busy) {
            if (theme == null) {
                item { EditorButton("Save current look as a new theme") { name("") { n -> ThemePack.saveCurrent(ctx, "custom-${java.util.UUID.randomUUID().toString().take(8)}", n) } } }
                for (t in installed) item { EditorButton(t.name) { selected = t.id } }
            } else {
                item { EditorButton("Apply theme") { perform { ThemePack.apply(ctx, theme, 0) } } }
                item { EditorButton("Rename theme") { name(theme.name) { ThemePack.rename(ctx, theme.id, it) } } }
                item { EditorButton("Update this theme from current look") {
                    AlertDialog.Builder(ctx).setTitle("Update ${theme.name}?").setMessage("Save the current wallpaper and appearance into this theme. Other themes and pictures stay installed.").setNegativeButton("Cancel", null).setPositiveButton("Update") { _, _ -> perform { ThemePack.saveCurrent(ctx, theme.id, theme.name) } }.show()
                } }
                val opacity = cfg.themeOpacity[theme.id] ?: theme.look.optInt("tile_opacity", 100)
                item { EditorButton("Tile background opacity: $opacity% (Left/right)", Modifier.onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown || e.key !in listOf(Key.DirectionLeft, Key.DirectionRight)) false else {
                        val value = (opacity + if (e.key == Key.DirectionRight) 5 else -5).coerceIn(0, 100)
                        ConfigStore.update(ctx, JSONObject().put("theme_opacity", JSONObject(cfg.themeOpacity + (theme.id to value))))
                        true
                    }
                }) {} }
                item { EditorButton("Reset opacity to theme default") { ConfigStore.update(ctx, JSONObject().put("theme_opacity", JSONObject(cfg.themeOpacity - theme.id))) } }
                item { Text("To save different spacing or colours, apply the theme, edit its look, then update it here. Existing wallpaper variants are retained.", color = Color.White) }
            }
        }
    }
}
