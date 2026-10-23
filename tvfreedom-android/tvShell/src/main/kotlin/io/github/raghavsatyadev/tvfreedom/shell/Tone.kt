package io.github.raghavsatyadev.tvfreedom.shell

import android.graphics.Color
import dynamiccolor.DynamicColor
import dynamiccolor.DynamicScheme
import dynamiccolor.MaterialDynamicColors
import hct.Hct
import scheme.SchemeContent
import scheme.SchemeExpressive
import scheme.SchemeFidelity
import scheme.SchemeFruitSalad
import scheme.SchemeMonochrome
import scheme.SchemeNeutral
import scheme.SchemeRainbow
import scheme.SchemeTonalSpot
import scheme.SchemeVibrant

/**
 * A design's Material 3 colours: one seed colour and a scheme give every colour role (dark theme), with Google's
 * material-color-utilities, as the launcher does. A colour property is a role name ("primaryContainer") or a hex.
 */
class Tone(seed: Int, scheme: String?) {

    private val s: DynamicScheme = Hct.fromInt(seed).let {
        when (scheme) {
            "vibrant" -> SchemeVibrant(it, true, 0.0)
            "expressive" -> SchemeExpressive(it, true, 0.0)
            "fidelity" -> SchemeFidelity(it, true, 0.0)
            "content" -> SchemeContent(it, true, 0.0)
            "rainbow" -> SchemeRainbow(it, true, 0.0)
            "fruitSalad" -> SchemeFruitSalad(it, true, 0.0)
            "monochrome" -> SchemeMonochrome(it, true, 0.0)
            "neutral" -> SchemeNeutral(it, true, 0.0)
            else -> SchemeTonalSpot(it, true, 0.0)
        }
    }
    private val cache = HashMap<String, Int>()

    /** A role's colour. */
    fun role(name: String): Int = cache.getOrPut(name) { (ROLES[name] ?: ROLES.getValue("onSurface"))(M).getArgb(s) }

    /** A colour property: a role name or "#RRGGBB" / "#AARRGGBB"; anything else is the [default] role. */
    fun color(v: Any?, default: String): Int {
        val str = v as? String ?: return role(default)
        if (str in ROLES) return role(str)
        return runCatching { Color.parseColor(str) }.getOrNull() ?: role(default)
    }

    companion object {
        const val SEED = 0xFF6750A4.toInt() // Material baseline purple

        val SCHEMES = listOf("tonalSpot", "vibrant", "expressive", "fidelity", "content", "rainbow", "fruitSalad",
            "monochrome", "neutral")

        private val M = MaterialDynamicColors()

        /** The roles a design may name. */
        val ROLES: Map<String, (MaterialDynamicColors) -> DynamicColor> = mapOf(
            "surface" to { it.surface() }, "surfaceDim" to { it.surfaceDim() }, "surfaceBright" to { it.surfaceBright() },
            "surfaceContainerLowest" to { it.surfaceContainerLowest() }, "surfaceContainerLow" to { it.surfaceContainerLow() },
            "surfaceContainer" to { it.surfaceContainer() }, "surfaceContainerHigh" to { it.surfaceContainerHigh() },
            "surfaceContainerHighest" to { it.surfaceContainerHighest() },
            "onSurface" to { it.onSurface() }, "onSurfaceVariant" to { it.onSurfaceVariant() },
            "inverseSurface" to { it.inverseSurface() }, "inverseOnSurface" to { it.inverseOnSurface() },
            "outline" to { it.outline() }, "outlineVariant" to { it.outlineVariant() },
            "primary" to { it.primary() }, "onPrimary" to { it.onPrimary() },
            "primaryContainer" to { it.primaryContainer() }, "onPrimaryContainer" to { it.onPrimaryContainer() },
            "inversePrimary" to { it.inversePrimary() },
            "secondary" to { it.secondary() }, "onSecondary" to { it.onSecondary() },
            "secondaryContainer" to { it.secondaryContainer() }, "onSecondaryContainer" to { it.onSecondaryContainer() },
            "tertiary" to { it.tertiary() }, "onTertiary" to { it.onTertiary() },
            "tertiaryContainer" to { it.tertiaryContainer() }, "onTertiaryContainer" to { it.onTertiaryContainer() },
            "error" to { it.error() }, "errorContainer" to { it.errorContainer() },
        )

        /** Why colour [v] can't be used, or null when it can. */
        fun problem(v: Any?): String? {
            val str = v as? String ?: return "is not a colour"
            if (str in ROLES) return null
            if (Regex("#([0-9a-fA-F]{6}|[0-9a-fA-F]{8})").matches(str)) return null
            return "\"$str\" is not a colour; use #RRGGBB or a role: ${ROLES.keys.joinToString()}"
        }

        /** The design's tone, from the root's seed and scheme. */
        fun of(rootProps: Map<String, Any?>?): Tone {
            val seed = (rootProps?.get("seed") as? String)?.let { runCatching { Color.parseColor(it) }.getOrNull() } ?: SEED
            return Tone(seed, rootProps?.get("scheme") as? String)
        }
    }
}
