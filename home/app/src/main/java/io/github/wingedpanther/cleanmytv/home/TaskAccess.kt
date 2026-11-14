package io.github.wingedpanther.cleanmytv.home

import android.content.*
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.*
import android.provider.Settings
import rikka.shizuku.Shizuku
import java.util.concurrent.TimeUnit

/** Only actual ActivityTaskManager task records; never UsageStats or a launch history. */
data class OpenTask(val id: Int, val pkg: String)
object TaskRecords {
    private val component = Regex("realActivity=([A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)+)/")
    fun parse(dump: String, user: Int): List<OpenTask> {
        require(dump.contains("ACTIVITY MANAGER") && dump.contains("Task")) { "This device does not expose a recognised task snapshot" }
        return dump.split(Regex("(?m)(?=^\\s*\\*?\\s*(?:Recent #\\d+: )?Task\\{)")).mapNotNull { block ->
            val header = block.lineSequence().firstOrNull { it.contains("Task{") } ?: return@mapNotNull null
            val id = Regex("#(\\d+)").find(header.substringAfter("Task{"))?.groupValues?.get(1)?.toIntOrNull() ?: return@mapNotNull null
            val taskUser = Regex("(?:U=|userId=|mUserId=)(\\d+)").find(block)?.groupValues?.get(1)?.toIntOrNull() ?: return@mapNotNull null
            val pkg = component.find(block)?.groupValues?.get(1) ?: return@mapNotNull null
            if (taskUser == user) OpenTask(id, pkg) else null
        }.distinctBy { it.id }
    }
}

/** Small explicit Binder protocol: no arbitrary shell command exposed to the UI. Runs only via Shizuku. */
class TaskUserService : Binder() {
    private fun runCommand(vararg args: String): String {
        val p = ProcessBuilder(*args).redirectErrorStream(true).start()
        val output = StringBuilder()
        val reader = Thread { p.inputStream.bufferedReader().useLines { lines -> lines.forEach { if (output.length < 2_000_000) output.appendLine(it) } } }
        reader.start()
        if (!p.waitFor(8, TimeUnit.SECONDS)) { p.destroyForcibly(); reader.join(1000); error("Device task command timed out") }
        reader.join(1000)
        check(p.exitValue() == 0) { "Device rejected task command" }
        return output.toString()
    }
    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        if (code == 16777115) { kotlin.system.exitProcess(0) }
        data.enforceInterface(TOKEN)
        try {
            val user = runCommand("am", "get-current-user").trim().toInt()
            val result = when (code) {
                1 -> "$user\n" + runCommand("dumpsys", "activity", "recents")
                2 -> {
                    val pkg = data.readString().orEmpty()
                    require(Regex("^[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$").matches(pkg))
                    require(pkg != "io.github.wingedpanther.cleanmytv.home" && pkg != "moe.shizuku.privileged.api")
                    val userApps = runCommand("pm", "list", "packages", "-3", "--user", "$user").lineSequence().map { it.removePrefix("package:").trim() }.toSet()
                    require(pkg in userApps) { "Protected or system application" }
                    val before = runCommand("dumpsys", "package", pkg)
                    require(listOf("BIND_ACCESSIBILITY_SERVICE", "BIND_DEVICE_ADMIN", "BIND_VPN_SERVICE", "BIND_INPUT_METHOD", "BIND_NOTIFICATION_LISTENER_SERVICE", "BIND_REMOTEVIEWS", "BIND_DEVICE_POLICY_MANAGEMENT_ROLE_HOLDER_SERVICE").none { before.contains(it) }) { "Protected service application" }
                    runCommand("am", "force-stop", "--user", "$user", pkg)
                    val after = runCommand("dumpsys", "package", pkg)
                    val state = after.lineSequence().firstOrNull { it.trimStart().startsWith("User $user:") }.orEmpty()
                    if (Regex("\\bstopped=true\\b").containsMatchIn(state)) "Force-stop verified" else "Requested; force-stop could not be verified"
                }
                else -> error("Unsupported task operation")
            }
            reply?.writeNoException(); reply?.writeString(result)
        } catch (e: Exception) { reply?.writeException(IllegalStateException(e.message ?: "Task operation failed")) }
        return true
    }
    companion object { const val TOKEN = "cleanmytv.enhanced.tasks" }
}

object TaskAccess {
    @Volatile private var binder: IBinder? = null
    private var binding = false
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) { binder = service; binding = false }
        override fun onServiceDisconnected(name: ComponentName) { binder = null; binding = false }
    }
    fun available() = runCatching { Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }.getOrDefault(false)
    fun connect(ctx: Context) {
        check(available()) { "Start Shizuku and grant access first" }
        if (binder?.isBinderAlive == true || binding) return
        binding = true
        try {
            Shizuku.bindUserService(Shizuku.UserServiceArgs(ComponentName(ctx, TaskUserService::class.java)).daemon(false).processNameSuffix("tasks").debuggable(false).version(24), connection)
        } catch (e: Exception) { binding = false; throw e }
    }
    private fun call(code: Int, pkg: String? = null): String {
        check(available()) { "Shizuku is no longer available" }
        val b = binder?.takeIf { it.isBinderAlive } ?: error("Task service is connecting. Refresh in a moment.")
        val data = Parcel.obtain(); val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(TaskUserService.TOKEN)
            if (pkg != null) data.writeString(pkg)
            check(b.transact(code, data, reply, 0))
            reply.readException(); reply.readString().orEmpty()
        } finally { data.recycle(); reply.recycle() }
    }
    fun tasks(): List<OpenTask> {
        val result = call(1)
        val user = result.substringBefore('\n').toInt()
        return TaskRecords.parse(result.substringAfter('\n'), user)
    }
    fun close(pkg: String) = call(2, pkg)
    fun protected(ctx: Context, pkg: String): Boolean {
        if (pkg == ctx.packageName || pkg == "moe.shizuku.privileged.api") return true
        if (Lock.needsPin(ctx, pkg)) return true
        val pm = ctx.packageManager
        val info = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull() ?: return true
        if (info.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0) return true
        val services = runCatching { pm.getPackageInfo(pkg, PackageManager.GET_SERVICES).services.orEmpty() }.getOrDefault(emptyArray())
        if (services.any { it.permission?.startsWith("android.permission.BIND_") == true }) return true
        val home = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
        if (home.any { it.activityInfo.packageName == pkg }) return true
        val enabled = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        return enabled.split(':').any { it.substringBefore('/') == pkg }
    }
}
