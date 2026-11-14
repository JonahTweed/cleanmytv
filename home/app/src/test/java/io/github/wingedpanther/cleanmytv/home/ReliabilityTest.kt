package io.github.wingedpanther.cleanmytv.home

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject

class ReliabilityTest {
    private val a = "org.a.app"
    private val b = "org.b.app"
    private val c = "org.c.app"
    private val all = listOf(a,b,c)
    private fun fixture() = HomeConfig(order = all, dockSize = 2, dockApps = listOf(a,b),
        rows = listOf(Group("One", listOf(a,c)), Group("Two", listOf(c,a))),
        folders = listOf(Group("First", listOf(a,b)), Group("Second", listOf(c,a))))

    @Test fun `Dock reorder preserves independent rows folders and duplicates`() {
        val cfg = fixture()
        val next = HomeEdit.move(cfg, a, 0, 1, all, 0)
        assertEquals(listOf(b,a), HomeEdit.apps(next, 0, all))
        assertEquals(cfg.rows,next.rows); assertEquals(cfg.folders,next.folders)
        assertEquals(cfg,HomeConfig.fromJson(cfg.toJson()))
    }
    @Test fun `moving from one row affects only source and destination`() {
        val cfg=fixture(); val next=HomeEdit.move(cfg,a,3,0,all,2)
        assertEquals(listOf(c),next.rows[0].apps)
        assertEquals(listOf(a,c),next.rows[1].apps)
        assertEquals(cfg.folders,next.folders); assertEquals(cfg.dockApps,next.dockApps)
    }
    @Test fun `moving from one folder preserves other folder and row shortcuts`() {
        val cfg=fixture(); val next=HomeEdit.move(cfg,a,5,0,all,4)
        assertEquals(listOf(b),next.folders[0].apps)
        assertEquals(listOf(a,c),next.folders[1].apps)
        assertEquals(cfg.rows,next.rows); assertEquals(cfg.dockApps,next.dockApps)
    }
    @Test fun `adding a shortcut without source preserves all existing placements`() {
        val cfg=fixture(); val next=HomeEdit.move(cfg,b,2,1,all)
        assertEquals(cfg.folders,next.folders); assertEquals(cfg.rows[1],next.rows[1]); assertEquals(cfg.dockApps,next.dockApps)
        assertEquals(listOf(a,b,c),next.rows[0].apps)
    }
    @Test fun `row reorder removal and deletion cannot change other groups`() {
        val cfg=fixture(); val next=HomeEdit.move(cfg,a,2,1,all,2)
        assertEquals(listOf(c,a),next.rows[0].apps)
        assertEquals(cfg.rows[1],next.rows[1]); assertEquals(cfg.folders,next.folders)
        val removed=HomeEdit.remove(cfg,a,"row0",all)
        assertEquals(listOf(c),removed.rows[0].apps); assertEquals(cfg.rows[1],removed.rows[1])
        val deleted=HomeEdit.deleteGroup(cfg,false,0,all)
        assertEquals(listOf(cfg.rows[1]),deleted.rows); assertEquals(cfg.folders,deleted.folders)
        assertEquals(cfg.dockApps,deleted.dockApps)
    }
    @Test fun `folder deletion preserves duplicates elsewhere and all settings`() {
        val cfg=fixture(); val next=HomeEdit.deleteGroup(cfg,true,0,all)
        assertEquals(listOf(cfg.folders[1]),next.folders); assertEquals(cfg.rows,next.rows)
        assertEquals(cfg.dockApps,next.dockApps); assertEquals(cfg.hidden,next.hidden)
        assertEquals(cfg.accent,next.accent); assertEquals(cfg.themeOpacity,next.themeOpacity)
    }
    @Test fun `legacy config stays implicit until edited and explicit Dock survives roundtrip`() {
        val cfg=HomeConfig.fromJson(JSONObject().put("order",org.json.JSONArray(all)).put("dock_size",2))
        assertNull(cfg.dockApps); assertEquals(listOf(a,b),HomeEdit.apps(cfg,0,all))
        val edited=HomeEdit.move(cfg,a,0,1,all,0)
        assertEquals(edited,HomeConfig.fromJson(JSONObject(edited.toJson().toString())))
        assertEquals(listOf(b,a),edited.dockApps)
    }
    @Test fun `editing Dock preserves hidden saved shortcuts and legacy stale packages cannot displace visible apps`() {
        val cfg=fixture().copy(hidden=setOf(a))
        val next=HomeEdit.move(cfg,b,0,0,all,0)
        assertTrue(next.dockApps!!.contains(a)); assertEquals(setOf(a),next.hidden)
        val legacy=HomeConfig(order=listOf("org.removed.app",a,b),dockSize=2)
        assertEquals(listOf(a,b),HomeEdit.apps(legacy,0,all))
    }
    @Test fun `whole row reorder rename and boundary moves preserve contents and independent groups`() {
        val cfg=fixture()
        val moved=HomeEdit.reorderGroup(cfg,false,0,1)
        assertEquals(cfg.rows.reversed(),moved.rows); assertEquals(cfg.folders,moved.folders)
        assertEquals(cfg.dockApps,moved.dockApps)
        val renamed=HomeEdit.renameGroup(moved,false,1,"Renamed")
        assertEquals(cfg.rows[0].apps,renamed.rows[1].apps)
        assertEquals(cfg.rows[1],renamed.rows[0]); assertEquals("Renamed",renamed.rows[1].name)
        assertEquals(cfg,HomeEdit.reorderGroup(cfg,false,0,-1))
        assertThrows(IllegalArgumentException::class.java) { HomeEdit.renameGroup(cfg,false,0,"Two") }
        assertThrows(IllegalArgumentException::class.java) { HomeEdit.renameGroup(cfg,false,0,"") }
    }
    @Test fun `legacy Dock capture preserves rendered membership when Other apps and locked apps are excluded`() {
        val phone="org.phone.app"; val locked="org.locked.app"
        val cfg=HomeConfig(order=listOf(phone,locked,a,b,c),dockSize=2,otherApps=true)
        val captured=HomeEdit.captureDock(cfg,all)
        assertEquals(listOf(a,b),captured.dockApps)
        val moved=HomeEdit.move(captured,a,0,1,listOf(phone,locked)+all,0)
        assertEquals(listOf(b,a),moved.dockApps)
        assertEquals(cfg.folders,moved.folders); assertTrue(moved.otherApps)
        val explicit=cfg.copy(dockApps=listOf(phone,a))
        assertEquals(explicit,HomeEdit.captureDock(explicit,all))
    }
    @Test fun `every editor entry has the same panel and selected-app protection policy`() {
        val cfg=fixture().copy(lockOn=true,lockSystem=true,lockApps=listOf(a))
        for (entry in EditorAccess.Entry.entries) {
            assertEquals(listOf(Lock.PANEL,a),EditorAccess.targets(cfg,true,entry,a))
            assertEquals(listOf(Lock.PANEL),EditorAccess.targets(cfg,true,entry,b))
            assertEquals(emptyList<String>(),EditorAccess.targets(cfg,false,entry,a))
            assertEquals(emptyList<String>(),EditorAccess.targets(cfg.copy(lockOn=false),true,entry,a))
            assertEquals(listOf(a),EditorAccess.targets(cfg.copy(lockSystem=false),true,entry,a))
        }
    }
    @Test fun `held OK cannot activate app on release and repeats fire once`() {
        val press=SelectPress(); assertTrue(press.down(a)); assertFalse(press.down(a))
        assertTrue(press.hold(a)); assertFalse(press.hold(a)); assertFalse(press.up(a))
        assertTrue(press.down(a)); assertTrue(press.up(a)); assertFalse(press.up(a)); assertFalse(press.down(a,false))
    }
    @Test fun `focus change pause cancellation and identity mismatch never activate another app`() {
        val press=SelectPress(); assertFalse(press.down(b, false)); press.down(a); assertFalse(press.hold(b)); press.cancel()
        assertFalse(press.down(a, false)); assertFalse(press.hold(a)); assertFalse(press.up(a)); assertFalse(press.up(b))
        press.down(b); assertTrue(press.up(b))
    }
}
