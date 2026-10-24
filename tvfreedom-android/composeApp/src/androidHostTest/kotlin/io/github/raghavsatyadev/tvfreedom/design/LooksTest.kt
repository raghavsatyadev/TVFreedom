package io.github.raghavsatyadev.tvfreedom.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LooksTest {
    @Test
    fun everyReadyLookGoesThereAndBack() {
        Looks.ALL.forEach { assertEquals(it.name, it.look, Looks.fromJson(Looks.toJson(it.look)).getOrThrow()) }
        assertEquals(Looks.ALL.size, Looks.ALL.map { it.name }.toSet().size)
    }

    @Test
    fun anAiAnswerIsReadFromItsWords() {
        val answer = "Sure! ```json\n{\"columns\": 5, \"true_black\": true, \"tile_shape\": \"flower\", \"names\": \"off\", \"accent\": \"#f2a65a\"}\n```"
        assertEquals(TvLook(5, true, "flower", "off", "#F2A65A"), Looks.extract(answer).getOrThrow())
        assertEquals(TvLook(), Looks.extract("{}").getOrThrow())
        assertEquals(TvLook(layout = "wall", clock = "big", background = "plain"),
            Looks.extract("{\"layout\": \"wall\", \"clock\": \"big\", \"background\": \"plain\"}").getOrThrow())
    }

    @Test
    fun aBadAnswerSaysWhatIsWrong() {
        fun why(s: String) = Looks.extract(s).exceptionOrNull()?.message.orEmpty()
        assertTrue(why("no json here"), "no JSON" in why("no json here"))
        assertTrue("columns" in why("{\"columns\": 9}"))
        assertTrue("columns" in why("{\"columns\": \"four\"}"))
        assertTrue("tile_shape" in why("{\"tile_shape\": \"star\"}"))
        assertTrue("names" in why("{\"names\": \"big\"}"))
        assertTrue("accent" in why("{\"accent\": \"red\"}"))
        assertTrue("layout" in why("{\"layout\": \"list\"}"))
        assertTrue("clock" in why("{\"clock\": \"digital\"}"))
        assertTrue("background" in why("{\"background\": \"photo\"}"))
    }
}
