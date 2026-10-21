package io.github.raghavsatyadev.tvfreedom.core

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlannerTest {
    /** Stands in for the AI: every asked package gets [remove], phase 2, like make_golden.py's fake agent. */
    private class FakeAi(val remove: Boolean) : Classifier {
        override val name = "fake"
        var asked = emptyList<AskItem>()
        override suspend fun classify(info: TvInfo, items: List<AskItem>, examples: Map<String, Example>,
                                      keep: Set<String>, log: (String) -> Unit): Map<String, Verdict> {
            asked = items
            return items.associate { it.pkg to Verdict(remove, 2, "fake agent") }
        }
    }

    private val planner = Planner(Known(RepoData.lists), RepoData.keep)

    private fun plan(model: String, remove: Boolean, ai: Classifier? = FakeAi(remove)) = runBlocking {
        planner.build(RepoData.info.copy(model = model), RepoData.facts, ai)
    }

    /** Python's plan (tvfreedom.py on the PC) and the app's must be the same, home-screen packages included. */
    private fun assertMatchesGolden(case: String, model: String, remove: Boolean) {
        val g = Json.parseToJsonElement(RepoData.golden("plan-$case.json")).jsonObject
        val p = plan(model, remove)
        assertEquals(g["remove"]!!.jsonArray.map { it.jsonObject.toRemoval() }, p.remove)
        assertEquals(g["kept"]!!.jsonObject.mapValues { it.value.jsonPrimitive.content }, p.kept.toSortedMap())
        assertEquals(g["conflicts"]!!.jsonArray.map { it.jsonPrimitive.content }, p.conflicts.map { it.pkg })
        assertEquals(g["already_off"]!!.jsonArray.map { it.jsonPrimitive.content }, p.alreadyOff)
    }

    private fun JsonObject.toRemoval() = Removal(
        this["pkg"]!!.jsonPrimitive.content, this["action"]!!.jsonPrimitive.content,
        this["phase"]!!.jsonPrimitive.int, this["reason"]!!.jsonPrimitive.content,
        this["source"]!!.jsonPrimitive.content)

    @Test fun miTvWithItsOwnListsMatchesPython() = assertMatchesGolden("mitv-keep", "MiTV-MOOR0", remove = false)
    @Test fun newTvAllKeptMatchesPython() = assertMatchesGolden("newtv-keep", "Brand New TV", remove = false)
    @Test fun newTvAiRemovesMatchesPython() = assertMatchesGolden("newtv-remove", "Brand New TV", remove = true)
    @Test fun miBox4ListsMatchPython() = assertMatchesGolden("mibox4-remove", "MIBOX4", remove = true)

    @Test
    fun guardWinsOverListsAndAi() {
        val f = RepoData.facts
        val p = plan("MiTV-MOOR0", remove = true)
        val removed = p.remove.map { it.pkg }.toSet()
        f.paths.forEach { (pkg, apk) ->
            if (Guard.keepReason(pkg, apk, f.home, RepoData.info.ime, RepoData.keep, f.uids[pkg]).isNotEmpty())
                assertTrue("$pkg was removed", pkg !in removed)
        }
        // the Mi TV's own phase 1 list disables the updater, but the guard keeps OTA
        assertTrue("com.xiaomi.mitv.updateservice" in p.kept)
        assertTrue(p.conflicts.any { it.pkg == "com.xiaomi.mitv.updateservice" })
    }

    @Test
    fun aiNeverSeesGuardedOrListedPackages() {
        val ai = FakeAi(remove = false)
        plan("MiTV-MOOR0", remove = false, ai = ai)
        val own = Known(RepoData.lists).forModel("MiTV-MOOR0").keys
        assertTrue(ai.asked.isNotEmpty())
        ai.asked.forEach {
            assertEquals("", Guard.keepReason(it.pkg, it.apk, RepoData.facts.home, RepoData.info.ime, RepoData.keep))
            assertTrue(it.pkg !in own)
        }
    }

    @Test
    fun homeScreenPackagesStayOutOfTheDebloat() {
        val ai = FakeAi(remove = true)
        val p = plan("MiTV-MOOR0", remove = true, ai = ai)
        listOf("com.google.android.apps.tv.dreamx").forEach { pkg ->
            assertTrue(pkg !in p.remove.map { it.pkg })
            assertTrue(pkg !in ai.asked.map { it.pkg })
            assertEquals(Planner.HOME_OWNED, p.kept[pkg])
        }
    }

    @Test
    fun stopUpdatesLetsOnlyTheModelsOwnListTurnOffOta() {
        val on = runBlocking { planner.build(RepoData.info.copy(model = "MiTV-MOOR0"), RepoData.facts, null, stopUpdates = true) }
        assertTrue(on.remove.any { it.pkg == "com.xiaomi.mitv.updateservice" && it.phase == 1 })
        assertTrue(on.remove.none { Guard.keepReason(it.pkg).let { why -> why.isNotEmpty() && why != Guard.OTA } })
        val newTv = runBlocking { planner.build(RepoData.info.copy(model = "Brand New TV"), RepoData.facts, FakeAi(true), stopUpdates = true) }
        assertTrue(newTv.remove.none { Guard.keepReason(it.pkg) == Guard.OTA })
    }

    @Test
    fun noAiKeepsEverythingUnlisted() {
        val p = plan("Brand New TV", remove = false, ai = null)
        assertTrue(p.remove.isEmpty())
        assertTrue(p.kept.values.any { it == "no AI: kept" })
    }

    @Test
    fun phase2bCountsAsPhase2AndAppliedAsPhase3() {
        assertEquals(2, ListFiles.phaseOf("lists/phase2b.txt"))
        assertEquals(5, ListFiles.phaseOf("mibox4/phase5.txt"))
        assertEquals(3, ListFiles.phaseOf("mibox4/ai-2026/applied.txt"))
    }

    @Test
    fun aiAnswersAreParsedForgivinglyAndSanitised() {
        val text = """sure! [{"pkg":"a.b","verdict":"REMOVE","phase":"7","reason":"ads\n# evil"},""" +
            """{"pkg":"not.asked","verdict":"remove"},{"pkg":"c.d","verdict":"maybe"}]"""
        assertEquals(mapOf("a.b" to Verdict(true, 3, "ads evil")), Prompt.parse(text, setOf("a.b", "c.d")))
        val salvaged = Prompt.parse("""{"pkg":"a.b","verdict":"keep","phase":1} junk {broken""", setOf("a.b"))
        assertEquals(Verdict(false, 1, ""), salvaged["a.b"])
    }

    @Test
    fun reasonsCannotBreakTheListFormat() {
        val rows = listOf(Removal("x.y", DISABLE, 1, "line one\nline two # hash", "agent"))
        val text = ListFiles.formatPhase(RepoData.info.copy(model = "M"), 1, rows)
        assertEquals(listOf(ListEntry(DISABLE, "x.y", "line one line two hash [agent]")), ListFiles.readEntries(text))
    }
}
