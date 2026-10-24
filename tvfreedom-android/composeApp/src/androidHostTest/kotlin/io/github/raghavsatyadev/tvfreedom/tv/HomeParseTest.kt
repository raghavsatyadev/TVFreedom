package io.github.raghavsatyadev.tvfreedom.tv

import io.github.raghavsatyadev.tvfreedom.design.TvLook
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class HomeParseTest {
    @Test
    fun columnsFitTheScreen() {
        assertEquals(4, Parse.columns("Physical size: 1920x1080", "Physical density: 320")) // Mi TV, Mi Box
        assertEquals(4, Parse.columns("Physical size: 3840x2160", "Physical density: 640"))
        assertEquals(6, Parse.columns("Physical size: 1920x1080", "Physical density: 213"))
        assertEquals(3, Parse.columns("Physical size: 1280x720\nOverride size: 1280x720", "Physical density: 320\nOverride density: 400"))
        assertEquals(4, Parse.columns("", "Physical density: 320"))
    }

    @Test
    fun launcherReportIsDecoded() {
        val out = "Broadcasting: Intent { flg=0x20 cmp=io.github.raghavsatyadev.tvfreedom.home/.ConfigReceiver }\n" +
            "Broadcast completed: result=0, data=\"ok favourites=com.netflix.ninja%2Corg.jellyfin.androidtv&columns=5" +
            "&weather_place=St+John%27s&weather_latitude=12.34&weather_longitude=-56.78&dream_calendars=Holidays+in+India%7CFamily\""
        assertEquals(LauncherConfig(listOf("com.netflix.ninja", "org.jellyfin.androidtv"), 5, "St John's", "12.34", "-56.78",
            "Holidays in India|Family"), Parse.launcherConfig(out))
        assertNull(Parse.launcherConfig("Broadcast completed: result=0"))
        val v4 = "Broadcast completed: result=0, data=\"ok favourites=&columns=4&brand_color=%23123ABC&logo=20480\""
        assertEquals(LauncherConfig(emptyList(), 4, brandColor = "#123ABC", logoBytes = 20480), Parse.launcherConfig(v4))
        val v16 = "Broadcast completed: result=0, data=\"ok favourites=&columns=4&true_black=1&continue_watching=&logo=0\""
        assertEquals(LauncherConfig(emptyList(), 4, trueBlack = true), Parse.launcherConfig(v16))
        val v19 = "Broadcast completed: result=0, data=\"ok favourites=&columns=3&true_black=1&tile_shape=square&names=focus&accent=%237FA7E0\""
        val c = Parse.launcherConfig(v19)!!
        assertEquals(TvLook(3, true, "square", "focus", "#7FA7E0"), c.look())
    }

    @Test
    fun valuesAreSafeInAShellCommand() {
        assertEquals("'St John'\\''s; rm -rf /'", HomeOps.quote("St John's; rm -rf /"))
        assertEquals("12.97", Parse.coordinate(" 12.97 "))
        assertEquals("#12AB3C", Parse.hexColor(" #12ab3c "))
        assertEquals("", Parse.hexColor("#12ab3c'; reboot"))
        assertEquals("", Parse.hexColor("red"))
        assertEquals("", Parse.coordinate("12,97"))
        assertEquals("", Parse.coordinate("1; reboot"))
        assertEquals("com.google.android.apps.tv.dreamx/.service.Backdrop",
            Parse.safeComponent("com.google.android.apps.tv.dreamx/.service.Backdrop"))
        assertThrows(IllegalArgumentException::class.java) { Parse.safeComponent("a/.B; reboot") }
    }

    @Test
    fun pngIsFoundAfterVendorText() {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 1, 2, 3)
        assertArrayEquals(png, Parse.png("screencap: debug line\n".toByteArray() + png))
        assertNull(Parse.png("no image".toByteArray()))
    }

    @Test
    fun ramAndVersion() {
        assertEquals(1024, Parse.memAvailableMb("MemTotal:  2000000 kB\nMemAvailable:    1048576 kB\n"))
        assertNull(Parse.memAvailableMb("permission denied"))
        assertEquals(3L, Parse.versionCode("    versionCode=3 minSdk=26 targetSdk=29"))
    }

    @Test
    fun onlyAppsTurnedOffByHandAreUserDisabled() {
        val out = """
            |  Package [com.factory.off] (1a2b):
            |  Package [com.user.off] (3c4d):
            |    User 0: ceDataInode=1 installed=true hidden=false suspended=false stopped=true notLaunched=false enabled=3 instant=false
            |  Package [com.on] (5e6f):
        """.trimMargin()
        assertEquals(setOf("com.user.off"), Parse.userDisabled(out))
    }
}
