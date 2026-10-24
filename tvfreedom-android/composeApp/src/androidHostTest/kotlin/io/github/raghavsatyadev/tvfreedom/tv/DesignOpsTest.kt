package io.github.raghavsatyadev.tvfreedom.tv

import io.github.raghavsatyadev.tvfreedom.design.DesignPrompt
import io.github.raghavsatyadev.tvfreedom.store.DesignStore
import io.github.raghavsatyadev.tvfreedom.store.Take
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignOpsTest {
    @Test
    fun callResultIsRead() {
        // As the emulator's `content call` printed them, with \r\n line ends.
        assertEquals("ok", Parse.callResult("Result: Bundle[{result=ok}]\r\n"))
        assertEquals("No screen: the design needs a createSurface message first.",
            Parse.callResult("Result: Bundle[{result=No screen: the design needs a createSurface message first.}]\r\n"))
        val catalog = "Result: Bundle[{result=A design is a JSON array of A2UI v0.9 messages:\r\n" +
            "1. {\"version\":\"v0.9\",\"createSurface\":{\"surfaceId\":\"home\"}}\r\n" +
            "Nothing else is supported: no images, buttons or actions; OK on a tile opens that app.}]\r\n"
        val text = Parse.callResult(catalog)
        assertTrue(text.startsWith("A design is"))
        assertTrue(text.endsWith("opens that app."))
        assertTrue("{\"surfaceId\":\"home\"}}\n" in text)
        assertEquals("The TV gave no answer.", Parse.callResult("  \r\n"))
        assertEquals("Error: no provider", Parse.callResult("Error: no provider\r\n"))
    }

    @Test
    fun designIsCutOutOfTheAnswer() {
        val answer = "Here is your design:\n```json\n[{\"version\": \"v0.9\", \"a\": [1, 2]}]\n```\nEnjoy!"
        assertEquals("[{\"version\":\"v0.9\",\"a\":[1,2]}]", DesignPrompt.extract(answer).getOrThrow())
        assertTrue(DesignPrompt.extract("No idea.").exceptionOrNull()!!.message!!.contains("no JSON array"))
        assertTrue(DesignPrompt.extract("[{\"a\": }]").exceptionOrNull()!!.message!!.contains("does not parse"))
        assertTrue(DesignPrompt.extract("[]").isFailure)
    }

    @Test
    fun takesKeepTheNewest() {
        var list = emptyList<Take>()
        repeat(DesignStore.KEEP + 3) { list = DesignStore.pushed(list, Take("t$it", "", "[1]")) }
        assertEquals(DesignStore.KEEP, list.size)
        assertEquals("t3", list.first().name)
        assertEquals("t${DesignStore.KEEP + 2}", list.last().name)
        assertEquals("Bigger clock", DesignPrompt.title("  Bigger clock\nand more"))
        assertEquals(40, DesignPrompt.title("x".repeat(80)).length)
    }
}
