package io.github.raghavsatyadev.tvfreedom.shell

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckTest {

    private val apps = listOf(mapOf("pkg" to "com.netflix", "label" to "Netflix"), mapOf("pkg" to "com.yt", "label" to "YouTube"))

    private fun design(vararg components: String) = """[
      {"version": "v0.9", "createSurface": {"surfaceId": "home", "catalogId": "${TvCatalog.ID}"}},
      {"version": "v0.9", "updateComponents": {"surfaceId": "home", "components": [${components.joinToString()}]}}
    ]"""

    private fun problem(d: String) = runBlocking { Check.problem(d, apps) }

    @Test fun goodDesignPasses() = assertNull(problem(design(
        """{"id": "root", "component": "Column", "children": ["hi", "panel"]}""",
        """{"id": "hi", "component": "Greeting"}""",
        """{"id": "panel", "component": "Panel", "child": "grid"}""",
        """{"id": "grid", "component": "AppGrid", "columns": 4, "apps": {"path": "/apps"}}""",
    )))

    @Test fun literalPackageListPasses() = assertNull(problem(design(
        """{"id": "root", "component": "AppRow", "apps": ["com.yt", "not.installed"]}""",
    )))

    @Test fun unknownTypeIsNamed() {
        val p = problem(design("""{"id": "root", "component": "Button"}"""))!!
        assertTrue(p, "Button" in p && "AppRow" in p)
    }

    @Test fun missingChildIsNamed() {
        val p = problem(design("""{"id": "root", "component": "Column", "children": ["gone"]}"""))!!
        assertTrue(p, "\"gone\"" in p)
    }

    @Test fun noRoot() = assertTrue(problem(design("""{"id": "top", "component": "Clock"}"""))!!.contains("root"))

    @Test fun nothingToPick() {
        val p = problem(design("""{"id": "root", "component": "AppRow", "apps": ["not.installed"]}"""))!!
        assertTrue(p, "remote" in p)
    }

    @Test fun notJson() = assertTrue(problem("hello")!!.startsWith("Not a design"))

    @Test fun noSurface() = assertEquals("No screen: the design needs a createSurface message first.", problem("[]"))

    @Test fun loopIsCaught() {
        val p = problem(design(
            """{"id": "root", "component": "Column", "children": ["a"]}""",
            """{"id": "a", "component": "Column", "children": ["root"]}""",
        ))!!
        assertTrue(p, "twice" in p)
    }
}
