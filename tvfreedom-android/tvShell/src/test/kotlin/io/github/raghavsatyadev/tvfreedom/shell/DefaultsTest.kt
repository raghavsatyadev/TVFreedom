package io.github.raghavsatyadev.tvfreedom.shell

import io.github.raghavsatyadev.tvfreedom.design.Defaults
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The phone's ready-made designs must pass the TV's own check, so the two never drift apart. */
class DefaultsTest {

    private val tv = listOf("com.netflix.ninja", "com.google.android.youtube.tv", "com.google.android.tv", "com.android.vending",
        "com.android.tv.settings").map { mapOf("pkg" to it, "label" to it.substringAfterLast('.')) }

    @Test fun everyDefaultPasses() {
        val designs = Defaults.all(tv.map { it["pkg"]!! }, favourites = listOf("com.android.vending"))
        assertEquals(listOf("Simple", "Big tiles", "Live TV", "Streaming", "Bright", "Minimal"), designs.map { it.name })
        designs.forEach { assertNull(it.name, runBlocking { Check.problem(it.json, tv) }) }
    }

    /** Writes the designs for this emulator's apps to build/designs, to look at them on a TV. */
    @Test fun writeForEmulator() {
        val apps = System.getProperty("designs.apps")?.split(',') ?: return
        val dir = java.io.File("build/designs").apply { mkdirs() }
        Defaults.all(apps).forEachIndexed { i, d -> java.io.File(dir, "$i.json").writeText(d.json) }
    }

    @Test fun unknownAppsFallBackToTheTvsList() =
        Defaults.all(emptyList()).forEach { assertNull(it.name, runBlocking { Check.problem(it.json, tv) }) }
}
