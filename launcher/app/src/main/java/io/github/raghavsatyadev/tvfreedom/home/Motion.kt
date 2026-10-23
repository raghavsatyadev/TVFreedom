// Ported from 0xRampey/material-tv-launcher (Apache-2.0); see launcher/NOTICE.
package io.github.raghavsatyadev.tvfreedom.home

import android.view.animation.PathInterpolator

/**
 * Material 3 Expressive motion. Expressive replaces easing+duration with springs; these are the spec's cross-platform
 * fallback curves (m3.material.io/styles/motion). Spatial curves overshoot a little and settle (movement, scale,
 * shape); effects curves never overshoot (color, opacity). Fast is for small components, default for larger ones.
 */
object Motion {
    val FAST_SPATIAL = PathInterpolator(0.42f, 1.67f, 0.21f, 0.90f)
    const val FAST_SPATIAL_MS = 350L
    val DEFAULT_SPATIAL = PathInterpolator(0.38f, 1.21f, 0.22f, 1.00f)
    const val DEFAULT_SPATIAL_MS = 500L
    val FAST_EFFECTS = PathInterpolator(0.31f, 0.94f, 0.34f, 1.00f)
    const val FAST_EFFECTS_MS = 150L
    val DEFAULT_EFFECTS = PathInterpolator(0.34f, 0.80f, 0.34f, 1.00f)
    const val DEFAULT_EFFECTS_MS = 200L
}
