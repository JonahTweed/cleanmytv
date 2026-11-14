package io.github.wingedpanther.cleanmytv.home

import org.junit.Test
import org.junit.Assert.*
import org.json.JSONObject
import java.nio.file.Files
import java.io.File
import java.time.ZonedDateTime

class EnhancedTest {
    @Test fun `opacity overrides persist and do not alter unrelated data`() {
        val cfg = HomeConfig(rows = listOf(Group("TV", listOf("org.test.app"))), tileOpacity = 18,
            themeOpacity = mapOf("day" to 0, "night" to 75), topSpace = 12)
        assertEquals(cfg, HomeConfig.fromJson(JSONObject(cfg.toJson().toString())))
        assertEquals(emptyMap<String, Int>(), HomeConfig.fromJson(JSONObject("{\"theme_opacity\":{}}"), cfg).themeOpacity)
        assertEquals(mapOf("day" to 0, "night" to 75), HomeConfig.fromJson(JSONObject("{\"accent\":\"#AABBCC\"}"), cfg).themeOpacity)
    }
    @Test fun `moving a dock app to a folder preserves unrelated rows and can be cancelled without mutation`() {
        val cfg = HomeConfig(order = listOf("org.a.app", "org.b.app", "org.c.app"), dockSize = 2,
            rows = listOf(Group("TV", listOf("org.c.app"))), folders = listOf(Group("Tools", emptyList())))
        val next = HomeEdit.move(cfg, "org.a.app", 3, 0, cfg.order)
        assertEquals(1, next.dockSize)
        assertEquals(listOf("org.b.app"), HomeEdit.apps(next, 0, cfg.order))
        assertEquals(listOf("org.a.app"), next.folders[0].apps)
        assertEquals(cfg.rows, next.rows)
        assertEquals(emptyList<String>(), cfg.folders[0].apps)
        assertEquals(2, cfg.dockSize)
    }
    @Test fun `removing from dock leaves all row placements and folders untouched`() {
        val cfg = HomeConfig(order = listOf("org.a.app", "org.b.app"), dockSize = 2,
            rows = listOf(Group("TV", listOf("org.a.app"))), folders = listOf(Group("Tools", emptyList())))
        val next = HomeEdit.remove(cfg, "org.a.app", "dock", cfg.order)
        assertEquals(1, next.dockSize)
        assertEquals(cfg.rows, next.rows)
        assertEquals(cfg.folders, next.folders)
        assertTrue(HomeEdit.apps(next, 1, cfg.order).contains("org.a.app"))
    }
    @Test fun `moving within dock and row respects insertion position`() {
        val cfg = HomeConfig(order = listOf("org.a.app", "org.b.app", "org.c.app"), dockSize = 2, rows = listOf(Group("TV", listOf("org.a.app", "org.c.app"))))
        assertEquals(listOf("org.b.app", "org.a.app"), HomeEdit.move(cfg, "org.a.app", 0, 1, cfg.order).order.take(2))
        assertEquals(listOf("org.c.app", "org.a.app"), HomeEdit.move(cfg, "org.a.app", 2, 1, cfg.order).rows[0].apps)
    }
    @Test fun `deleting a folder releases apps to all apps without hiding or uninstalling`() {
        val cfg = HomeConfig(order = listOf("org.a.app", "org.b.app"), dockSize = 0, folders = listOf(Group("Tools", listOf("org.a.app"))))
        val ungrouped = cfg.copy(folders = emptyList())
        assertTrue(HomeEdit.apps(ungrouped, 1, cfg.order).contains("org.a.app"))
        assertEquals(cfg.hidden, ungrouped.hidden)
    }
    @Test fun `deleting a folder preserves Dock positions while releasing its apps`() {
        val cfg = HomeConfig(order = listOf("org.a.app", "org.b.app", "org.c.app"), dockSize = 2,
            folders = listOf(Group("Tools", listOf("org.a.app"))))
        val before = HomeEdit.apps(cfg, 0, cfg.order)
        val next = HomeEdit.deleteGroup(cfg, true, 0, cfg.order)
        assertEquals(before, HomeEdit.apps(next, 0, cfg.order))
        assertTrue(HomeEdit.apps(next, 1, cfg.order).contains("org.a.app"))
        assertEquals(2, next.dockSize)
    }
    @Test fun `schedule boundaries handle overnight day intervals and DST`() {
        val spring = ZonedDateTime.parse("2026-03-29T02:31:00+01:00[Europe/London]")
        assertEquals("day", ScheduleClock.active(spring, "01:30", "19:00", "day", "night")!!.second)
        val beforeSpring = ZonedDateTime.parse("2026-03-29T00:59:00Z[Europe/London]")
        assertEquals("night", ScheduleClock.active(beforeSpring, "01:30", "19:00", "day", "night")!!.second)
        val overnight = ZonedDateTime.parse("2026-11-14T02:00:00Z[Europe/London]")
        assertEquals("day", ScheduleClock.active(overnight, "19:00", "07:00", "day", "night")!!.second)
        val autumn = ZonedDateTime.parse("2026-10-25T01:45:00Z[Europe/London]")
        assertEquals("day", ScheduleClock.active(autumn, "01:30", "19:00", "day", "night")!!.second)
    }
    @Test fun `task parser reads resumable tasks only from current user and rejects unknown formats`() {
        val dump = """ACTIVITY MANAGER RECENT TASKS
          * Recent #0: Task{abc #42 type=standard A=10034:org.test.video U=0}
            realActivity=org.test.video/.Main
          * Recent #1: Task{def #43 type=standard A=10035:org.work.video U=10}
            realActivity=org.work.video/.Main
        """.trimIndent()
        assertEquals(listOf(OpenTask(42, "org.test.video")), TaskRecords.parse(dump, 0))
        assertThrows(IllegalArgumentException::class.java) { TaskRecords.parse("usage history org.old.app", 0) }
    }
    @Test fun `theme add update and interrupted commit retain all six existing packs and order`() {
        val root = Files.createTempDirectory("enhanced-themes").toFile()
        try {
            val ids = (1..6).map { "theme-$it" }
            val live = ThemeFiles.live(root).apply { mkdirs() }
            for (id in ids) File(live, id).apply { mkdirs(); File(this, "0.jpg").writeText("original-$id"); File(this, "1.jpg").writeText("variant-$id") }
            File(live, "order").writeText(ids.joinToString(","))
            ThemeFiles.begin(root)
            File(root, "themes.upload/new").apply { mkdirs(); File(this, "0.jpg").writeText("new") }
            ThemeFiles.commit(root, ids, listOf("new"), { stage, merged -> File(stage, "order").writeText(merged.joinToString(",")) }, { require(File(it, "0.jpg").exists()) })
            assertEquals((ids + "new").joinToString(","), File(live, "order").readText())
            for (id in ids) assertEquals("variant-$id", File(live, "$id/1.jpg").readText())
            ThemeFiles.begin(root)
            File(root, "themes.upload/theme-2/0.jpg").writeText("updated")
            ThemeFiles.commit(root, ids + "new", listOf("theme-2"), { stage, merged -> File(stage, "order").writeText(merged.joinToString(",")) }, { require(File(it, "0.jpg").exists()) })
            assertEquals("updated", File(live, "theme-2/0.jpg").readText())
            assertEquals("variant-theme-2", File(live, "theme-2/1.jpg").readText())
            ThemeFiles.begin(root)
            assertThrows(IllegalArgumentException::class.java) { ThemeFiles.commit(root, ids + "new", listOf("broken"), { _, _ -> }, { require(File(it, "0.jpg").exists()) }) }
            assertEquals("updated", File(live, "theme-2/0.jpg").readText())
            // Process death after moving the old library, before installing the staged library.
            assertTrue(live.renameTo(File(root, "themes.old")))
            assertEquals("updated", File(ThemeFiles.live(root), "theme-2/0.jpg").readText())
        } finally { root.deleteRecursively() }
    }
}
