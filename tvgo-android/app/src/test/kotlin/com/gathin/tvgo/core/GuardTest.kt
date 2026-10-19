package com.gathin.tvgo.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GuardTest {
    private val f = RepoData.facts

    @Test
    fun matchesPythonGuardForEveryPackage() {
        val expected = RepoData.golden("guard.tsv").lines().filter { it.isNotEmpty() }
            .associate { it.substringBefore('\t') to it.substringAfter('\t') }
        assertEquals(f.paths.size, expected.size)
        val actual = f.paths.toSortedMap().mapValues { (pkg, apk) ->
            Guard.keepReason(pkg, apk, f.home, RepoData.info.ime, RepoData.keep, f.uids[pkg])
        }
        assertEquals(expected, actual)
    }

    @Test
    fun keepListHomeKeyboardAndSystemUidComeFirst() {
        assertEquals("your keep list", Guard.keepReason("com.netflix.ninja", keep = setOf("com.netflix.ninja")))
        assertEquals("launcher/setup (HOME)", Guard.keepReason("a.b", home = setOf("a.b")))
        assertEquals("input/remote (current keyboard)", Guard.keepReason("x.kbd", ime = "x.kbd/.Ime"))
        assertEquals("core (system uid)", Guard.keepReason("x.y", uid = 1000))
        assertEquals("core", Guard.keepReason("x.y", apkPath = "/apex/x/y.apk"))
        assertEquals("", Guard.keepReason("com.example.game", "/data/app/game.apk", uid = 10123))
    }

    @Test
    fun coreClassesAreAlwaysKept() {
        listOf("android", "com.android.systemui", "com.google.android.gms", "com.android.vending",
            "com.google.android.katniss", "com.google.android.tvlauncher", "com.android.bluetooth",
            "com.mediatek.tvinput", "com.xiaomi.mitv.updateservice", "com.google.android.apps.mediashell")
            .forEach { assertTrue(it, Guard.keepReason(it).isNotEmpty()) }
    }

    @Test
    fun keepFileParsesCommentsAndBlanks() {
        assertEquals(setOf("a.b", "c.d"), Guard.parseKeep("# header\na.b   # note\n\nc.d\n"))
    }
}
