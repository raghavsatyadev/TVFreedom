package io.github.raghavsatyadev.tvfreedom.design

/** What the AI gets to pick a look for the TV's home screen. The phone checks its answer ([Looks.extract]). */
object DesignPrompt {

    fun build(request: String, current: TvLook, bad: String?, why: String?) = buildString {
        appendLine("You pick the look of an Android TV home screen. Answer with only one JSON object,")
        appendLine("no words or code fences before or after it. Its keys:")
        appendLine("- \"columns\": apps per row, 3 to 6 (fewer means bigger tiles)")
        appendLine("- \"true_black\": true for a pure black background (good on OLED TVs), false for a soft tinted one")
        appendLine("- \"tile_shape\": \"\" mixed playful shapes, \"circle\", \"square\" (rounded square) or \"flower\"")
        appendLine("- \"names\": \"\" app names always shown, \"focus\" only under the chosen app, \"off\" never")
        appendLine("- \"accent\": \"\" colours follow the chosen app, or one colour for everything like \"#7FA7E0\"")
        appendLine("- \"layout\": \"\" favourite apps in a row at the bottom, \"wall\" every app in a grid from the top,")
        appendLine("  \"banner\" wide app pictures (like Google TV)")
        appendLine("- \"clock\": \"\" a small clock in the corner, \"big\" a huge clock, \"analog\" a clock face with hands")
        appendLine("- \"background\": \"\" a big shape that follows the chosen app, \"glow\" a colour wash from the top,")
        appendLine("  \"plain\" one flat colour (true_black wins over it)")
        appendLine()
        appendLine("The look on the TV now (change it as asked, keep the rest):")
        appendLine(Looks.toJson(current))
        appendLine()
        appendLine("What the user wants:")
        appendLine(request.trim())
        if (bad != null && why != null) {
            appendLine()
            appendLine("Your last answer did not work: $why")
            appendLine("It was:")
            appendLine(bad)
            appendLine("Fix that and answer with the whole JSON object again.")
        }
    }

    /** A short name for a look, from what the user asked. */
    fun title(request: String): String {
        val line = request.trim().lineSequence().first().trim()
        return if (line.length <= 40) line else line.take(39).trimEnd() + "…"
    }
}
