package io.github.wingedpanther.cleanmytv.home

import android.app.AlertDialog
import android.content.Intent
import android.provider.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

/** Capability-aware task UI; lack of privilege is a visible state, never a fabricated running list. */
@Composable
fun OpenAppsScreen(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tasks by remember { mutableStateOf<List<OpenTask>>(emptyList()) }
    var status by remember { mutableStateOf("Checking task access…") }
    var busy by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(TaskAccess.available()) }
    fun refresh() { scope.launch {
        busy = true
        ready = TaskAccess.available()
        try {
            if (!ready) { tasks = emptyList(); status = "Android restricts other apps’ tasks. Optional Shizuku access is required for this task list and verified closing." }
            else {
                TaskAccess.connect(ctx); delay(400)
                tasks = withContext(Dispatchers.IO) { TaskAccess.tasks().filter { it.pkg != ctx.packageName } }
                status = if (tasks.isEmpty()) "No resumable tasks reported by Android." else "${tasks.size} resumable tasks reported by Android. A resumable task need not be running a process."
            }
        } catch (e: Exception) { tasks = emptyList(); status = e.message ?: "Task access unavailable" }
        finally { busy = false }
    } }
    fun close(packages: List<String>) { scope.launch {
        busy = true
        val result = withContext(Dispatchers.IO) {
            packages.distinct().joinToString("\n") { pkg ->
                val label = runCatching { ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
                if (TaskAccess.protected(ctx, pkg)) "$label: protected" else "$label: ${runCatching { TaskAccess.close(pkg) }.getOrElse { it.message ?: "Failed" }}"
            }
        }
        status = result
        tasks = withContext(Dispatchers.IO) { runCatching { TaskAccess.tasks().filter { it.pkg != ctx.packageName } }.getOrDefault(emptyList()) }
        busy = false
    } }
    LaunchedEffect(Unit) { refresh() }
    EditorSheet("Open Apps", onClose) {
        item { Text(status, color = Color.White) }
        if (!busy) {
            item { EditorButton("Refresh task list") { refresh() } }
            if (!ready) {
                item { Text("On supported Android 11+ devices, install Shizuku from shizuku.rikka.app, start it using its Wireless debugging setup, then authorise Enhanced. Shizuku needs restarting after reboot; Android 9/10 normally needs a computer to start it. Device support varies.", color = Color.White) }
                item { EditorButton("Open Shizuku") {
                    val i = ctx.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api") ?: ctx.packageManager.getLeanbackLaunchIntentForPackage("moe.shizuku.privileged.api")
                    if (i == null) status = "Install Shizuku from its official website first." else runCatching { ctx.startActivity(i) }.onFailure { status = "Cannot open Shizuku on this device" }
                } }
                item { EditorButton("Authorise Shizuku access") { runCatching { if (Shizuku.pingBinder()) Shizuku.requestPermission(240) else error("Start Shizuku first") }.onFailure { status = it.message.orEmpty() } } }
                item { EditorButton("Android app settings — individual Force stop controls") { runCatching { ctx.startActivity(Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)) }.onFailure { status = "App settings are unavailable on this device" } } }
            } else {
                val eligible = tasks.map { it.pkg }.distinct().filter { !TaskAccess.protected(ctx, it) }
                if (eligible.isNotEmpty()) item { EditorButton("Close All (${eligible.size} eligible apps)") {
                    AlertDialog.Builder(ctx).setTitle("Close eligible applications?").setMessage("This interrupts playback and unsaved work. System apps, Home and protected services are excluded.").setNegativeButton("Cancel", null).setPositiveButton("Close All") { _, _ -> close(eligible) }.show()
                } }
                for (task in tasks) {
                    val label = runCatching { ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(task.pkg, 0)).toString() }.getOrDefault(task.pkg)
                    item { EditorButton("Switch to $label · task ${task.id}") {
                        val pm = ctx.packageManager
                        val i = pm.getLeanbackLaunchIntentForPackage(task.pkg) ?: pm.getLaunchIntentForPackage(task.pkg)
                        if (i == null) status = "This task has no launchable activity" else {
                            val intent = if (Lock.needsPin(ctx, task.pkg)) LockActivity.intent(ctx, task.pkg, i) else i.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                            runCatching { ctx.startActivity(intent) }.onFailure { status = "Could not open $label" }
                        }
                    } }
                    if (!TaskAccess.protected(ctx, task.pkg)) item { EditorButton("Close $label") { close(listOf(task.pkg)) } }
                }
            }
        }
    }
}
