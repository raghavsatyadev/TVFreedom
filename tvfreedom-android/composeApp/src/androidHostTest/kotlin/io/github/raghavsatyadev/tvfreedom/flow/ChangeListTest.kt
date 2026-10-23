package io.github.raghavsatyadev.tvfreedom.flow

import io.github.raghavsatyadev.tvfreedom.store.RunRecord
import okio.Path.Companion.toPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChangeListTest {
    private val header = "# Applied by the TVFreedom app to MiTV 123: phases [1]"

    private val older = RunRecord("runs/m/ai-2026-10-21-090000".toPath(),
        "$header\ndisable com.a  # Old reason\ndisable com.b  # Ads app\n")
    private val newer = RunRecord("runs/m/ai-2026-10-22-101112".toPath(),
        "$header\ndisable com.a  # Shopping app\nuninstall com.c  # Games store; restore may need the Play Store\n")
    private val extra = RunRecord("runs/m/extra-2026-10-23-080500".toPath(), "$header\n",
        "appop com.a RUN_ANY_IN_BACKGROUND allow\nsetting global private_dns_specifier null\n" +
            "setting global private_dns_mode off\nsetting global window_animation_scale null\ninstalled org.smarttube.stable\n")
    private val home = RunRecord("runs/m/home-2026-10-20-070000".toPath(), "$header\n", settings = "secure screensaver_components null\n")

    private val groups = ChangeList.build(listOf(older, home, extra, newer))

    @Test fun groupsNewestFirstWithPlainTitles() {
        assertEquals(listOf("Extras, 23 Oct 08:05", "Removed apps, 22 Oct 10:11", "Removed apps, 21 Oct 09:00", "New home screen, 20 Oct 07:00"),
            groups.map { it.title })
    }

    @Test fun sameAppKeepsOnlyTheNewestRun() {
        val apps = groups.flatMap { it.changes }.filter { it.kind == ChangeKind.APP }
        assertEquals(listOf("com.a", "com.c", "com.b"), apps.map { it.entry!!.pkg })
        assertEquals("Shopping app", apps[0].title)
        assertEquals("Games store", apps[1].title)
        assertEquals("Put back · com.c", apps[1].detail)
    }

    @Test fun privateDnsIsOneChange() {
        val ex = groups[0].changes
        assertEquals(4, ex.size)
        val dns = ex.single { it.title == "Ad blocking off" }
        assertEquals(2, dns.lines.size)
        assertEquals("Remove SmartTube", ex.last().title)
    }

    @Test fun screensaverFromSettings() {
        val c = groups.last().changes.single()
        assertEquals(ChangeKind.SCREENSAVER, c.kind)
        assertEquals("Screensaver as it was", c.title)
    }

    @Test fun removingLinesKeepsTheHeader() {
        val left = ChangeList.without(newer.applied, listOf("disable com.a  # Shopping app"))
        assertTrue(left.startsWith(header))
        assertTrue("com.a" !in left && "com.c" in left)
        val apps = ChangeList.withoutApps(older.applied, setOf("disable" to "com.a"))
        assertTrue(apps.startsWith(header) && "com.a" !in apps && "com.b" in apps)
        assertEquals("setting a b 1\n", ChangeList.without("setting a b 1\nsetting a b 1\n", listOf("setting a b 1")))
    }

    @Test fun notSetMatchesNull() {
        assertTrue(ChangeList.same("null", ""))
        assertTrue(!ChangeList.same("0.5", "null"))
    }
}
