package com.gathin.tvgo.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrandColorsTest {
    private fun lightness(hex: String) = BrandColors.toHsl(0xFF000000.toInt() or hex.removePrefix("#").toInt(16)).third

    @Test
    fun aRedLogoGetsDarkReds() {
        // A red mark on a see-through background: the clear pixels don't count.
        val px = IntArray(100) { if (it < 30) 0xFFE53935.toInt() else 0x00000000 }
        val hue = BrandColors.mainHue(px)!!
        assertTrue(hue.first < 15f || hue.first > 345f)
        val out = BrandColors.suggest(px)
        assertEquals(4, out.size)
        out.forEach { assertTrue("$it is too light for white text", lightness(it) <= 0.17f) }
        val (h, _, _) = BrandColors.toHsl(0xFF000000.toInt() or out[0].removePrefix("#").toInt(16))
        assertTrue(h < 15f || h > 345f)
    }

    @Test
    fun aGreyLogoGetsNeutralGreys() {
        val px = IntArray(50) { if (it % 2 == 0) 0xFF202020.toInt() else 0xFFF0F0F0.toInt() }
        assertNull(BrandColors.mainHue(px))
        BrandColors.suggest(px).forEach { assertTrue(lightness(it) <= 0.2f) }
    }

    @Test
    fun hslRoundTrips() {
        assertEquals("#FF0000", BrandColors.hsl(0f, 1f, 0.5f))
        assertEquals("#000000", BrandColors.hsl(0f, 0f, 0f))
        assertEquals("#FFFFFF", BrandColors.hsl(0f, 0f, 1f))
    }
}
