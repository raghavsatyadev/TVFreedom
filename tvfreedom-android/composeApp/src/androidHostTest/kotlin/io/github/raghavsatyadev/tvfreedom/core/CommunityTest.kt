package io.github.raghavsatyadev.tvfreedom.core

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CommunityTest {
    private val community = Community(File(RepoData.root, "lists/community.lst").readText())
    private val planner = Planner(Known(RepoData.lists), RepoData.keep, community)

    private fun tv(brand: String, model: String, device: String = "", sdk: String = "") =
        TvInfo(host = "x", model = model, device = device, brand = brand, sdk = sdk)

    private fun facts(vararg pkgs: String) =
        Facts(pkgs.associateWith { "/system/app/$it.apk" }, emptySet(), emptySet(), setOf(TV_LAUNCHER), emptyMap())

    private fun plan(info: TvInfo, f: Facts) = runBlocking { planner.build(info, f, classifier = null) }

    @Test
    fun readsEveryModel() {
        assertEquals(131, community.models.size)
        assertTrue(community.models.all { it.rows.isNotEmpty() && it.brand.isNotEmpty() })
    }

    @Test
    fun aKnownModelGetsItsOwnList() {
        val p = community.pick(tv("Xiaomi", "MIBOX4", "oneday", "28"))
        assertEquals("community list for xiaomi-mi-box-s", p.source)
        assertTrue("com.miui.tv.analytics" in p.rows)
    }

    @Test
    fun brandIsMatchedWhateverItsCase() {
        assertTrue(community.pick(tv("XIAOMI", "mibox4")).source.endsWith("xiaomi-mi-box-s"))
    }

    @Test
    fun aSharedModelNameNeedsTheRightBrand() {
        // "AI PONT" is a model name several brands use.
        assertFalse(community.pick(tv("Nobody", "AI PONT")).source.contains("list for"))
    }

    @Test
    fun anUnknownModelGetsWhatItsBrandAgreesOn() {
        val p = community.pick(tv("TCL", "Some new TCL"))
        assertEquals("community lists for TCL", p.source)
        assertTrue(p.rows.isNotEmpty())
    }

    @Test
    fun anUnknownBrandGetsOnlyCommonAdvertsAndTracking() {
        val p = community.pick(tv("Nobody", "X1"))
        assertTrue(p.rows.isNotEmpty())
        assertTrue(p.rows.values.all { it.category == "ads" || it.category == "tracking" })
    }

    @Test
    fun choicesAndAskFirstRowsAreNeverSwitchedOff() {
        for (m in community.models) for (r in Community.usable(m.rows).values) {
            assertFalse(r.ask)
            assertTrue(r.category in Community.PHASE_OF)
        }
    }

    @Test
    fun homeScreensAndRemotePairingStay() {
        val f = facts(TV_LAUNCHER, WRAITH, "com.miui.tv.analytics", "tv.alphonso.alphonso_eula")
        val p = plan(tv("Xiaomi", "MIBOX9", "newbox", "30"), f)
        val gone = p.remove.map { it.pkg }
        assertFalse(TV_LAUNCHER in gone)
        assertFalse(WRAITH in gone)
        assertTrue("com.miui.tv.analytics" in gone)
        assertTrue(p.remove.all { it.action == DISABLE })
    }

    @Test
    fun aModelsNeverTouchListIsKept() {
        val fire = community.models.first { it.id == "amazon-fire-tv-stick-4k-2023" }
        val never = fire.never.first { it.startsWith("com.amazon.") && it != "com.amazon.tv.launcher" }
        val p = plan(tv("Amazon", "AFTKM", "karat", "30"), facts(never, "com.amazon.tv.acr"))
        assertFalse(never in p.remove.map { it.pkg })
    }

    @Test
    fun aModelWithItsOwnListIgnoresTheCommunityLists() {
        val withOwn = Planner(Known(RepoData.lists), RepoData.keep, community)
        val without = Planner(Known(RepoData.lists), RepoData.keep)
        val info = RepoData.info.copy(model = "MiTV-MOOR0")
        val a = runBlocking { withOwn.build(info, RepoData.facts, null) }
        val b = runBlocking { without.build(info, RepoData.facts, null) }
        assertEquals(b.remove, a.remove)
    }

    @Test
    fun withoutAiANewTvStillGetsCleaned() {
        val p = runBlocking { planner.build(RepoData.info.copy(model = "Brand New Xiaomi"), RepoData.facts, null) }
        assertTrue(p.remove.isNotEmpty())
        assertTrue(p.remove.all { it.source.startsWith("community") })
    }

    private companion object {
        const val TV_LAUNCHER = "com.google.android.tvlauncher"
        const val WRAITH = "com.google.android.tungsten.setupwraith"
    }
}
