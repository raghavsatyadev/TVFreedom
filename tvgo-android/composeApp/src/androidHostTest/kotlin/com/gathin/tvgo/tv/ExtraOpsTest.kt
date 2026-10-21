package com.gathin.tvgo.tv

import com.gathin.tvgo.flow.ExtrasChoice
import com.gathin.tvgo.flow.ExtrasDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtraOpsTest {
    @Test
    fun appopModesAsTheMiBoxPrintsThem() {
        assertEquals("default", Parse.appopMode("No operations.\nDefault mode: allow\n", "RUN_ANY_IN_BACKGROUND"))
        assertEquals("ignore", Parse.appopMode("RUN_ANY_IN_BACKGROUND: ignore; time=+1m ago\n", "RUN_ANY_IN_BACKGROUND"))
        // RUN_IN_BACKGROUND must not match RUN_ANY_IN_BACKGROUND's line.
        assertEquals("default", Parse.appopMode("RUN_ANY_IN_BACKGROUND: ignore\n", "RUN_IN_BACKGROUND"))
    }

    @Test
    fun pingSaysWhetherANameResolved() {
        assertTrue(Parse.resolved("PING connectivitycheck.gstatic.com (142.251.150.120) 56(84) bytes of data.\n"))
        assertFalse(Parse.resolved("ping: unknown host nonexist.invalid\n"))
    }

    @Test
    fun onlyPlainHostnamesAndValuesReachTheShell() {
        assertEquals("dns.adguard-dns.com", Parse.hostname(" dns.adguard-dns.com "))
        listOf("dns.adguard-dns.com; reboot", "localhost", "a b.com", "-x.com").forEach {
            assertTrue(it, runCatching { Parse.hostname(it) }.isFailure)
        }
        assertEquals("0.5", Parse.settingValue("0.5"))
        assertTrue(runCatching { Parse.settingValue("1;reboot") }.isFailure)
    }

    private val draft = ExtrasDraft(28, mapOf("a.app" to "default", "b.app" to "ignore"),
        mapOf("window_animation_scale" to "0.5", "transition_animation_scale" to "0.5", "send_action_app_error" to "0",
            "wifi_scan_always_enabled" to "0"), "null", "null", youtubeOn = true, smartTube = false)

    @Test
    fun onlyWhatIsSwitchedOnAndNotDoneYetIsDue() {
        assertFalse(ExtrasChoice().due(draft))                          // lockdown, DNS and SmartTube start off
        val c = ExtrasChoice(lockdown = true)
        assertEquals(listOf("a.app"), c.lockTargets(draft))           // b.app is already stopped
        assertFalse(c.settingsDue(draft))                               // every key already set
        assertFalse(c.dnsDue(draft) || c.tubeDue(draft))                // both start off
        assertTrue(c.due(draft))
        assertFalse(c.copy(lockSkip = setOf("a.app")).due(draft))
        assertTrue(c.copy(lockdown = false, smartTube = true).due(draft))
        assertFalse(c.copy(dns = true).dnsDue(draft.copy(sdk = 27)))    // Private DNS needs Android 9
        assertFalse(c.copy(dns = true).dnsDue(draft.copy(dnsMode = "hostname", dnsHost = c.dnsHost)))
    }
}
