package io.github.raghavsatyadev.tvfreedom.core

/**
 * The Clean Your TV community lists (lists/community.lst, made by scripts/community_lists.py): what to switch off
 * on 131 TV models from 44 brands, so a TV without its own list in lists/ is cleaned without any AI.
 *
 * A TV gets its model's list, else what its brand's lists agree on, else the adverts and tracking that many brands
 * agree on. Rows marked "ask first" and choice categories (voice, casting, updates...) are never switched off here.
 */
class Community(text: String) {
    data class Row(val pkg: String, val category: String, val ask: Boolean, val note: String)

    data class Model(
        val id: String,
        val brand: String,
        val models: List<String>,
        val devices: List<String>,
        val sdkMin: Int?,
        val sdkMax: Int?,
        val never: Set<String>,
        val rows: List<Row>,
    )

    /** The list a TV gets: [rows] to switch off, [never] to leave alone, and where they came from. */
    data class Pick(val source: String, val rows: Map<String, Row>, val never: Set<String>)

    val models: List<Model> = parse(text)

    fun pick(info: TvInfo): Pick {
        val sameBrand = models.filter { it.brand.equals(info.brand, ignoreCase = true) }
        exact(sameBrand, info)?.let { m -> return Pick("community list for ${m.id}", usable(m.rows), m.never) }
        if (sameBrand.isNotEmpty()) {
            val agree = minOf(2, sameBrand.size)
            val rows = agreed(sameBrand) { pkgs -> pkgs.size >= agree }
            return Pick("community lists for ${sameBrand.first().brand}", rows, sameBrand.flatMap { it.never }.toSet())
        }
        val rows = agreed(models) { owners -> owners.map { it.brand.lowercase() }.distinct().size >= COMMON_BRANDS }
            .filterValues { it.category in COMMON }
        return Pick("community lists, common to many brands", rows, models.flatMap { it.never }.toSet())
    }

    /** Brand plus model or device; ties go to the one whose Android version fits. */
    private fun exact(sameBrand: List<Model>, info: TvInfo): Model? {
        val hits = sameBrand.filter { m ->
            m.models.any { it.equals(info.model, ignoreCase = true) } ||
                info.device.isNotEmpty() && m.devices.any { it.equals(info.device, ignoreCase = true) }
        }
        val sdk = info.sdk.toIntOrNull()
        return hits.firstOrNull { sdk != null && (it.sdkMin ?: 0) <= sdk && sdk <= (it.sdkMax ?: Int.MAX_VALUE) }
            ?: hits.firstOrNull()
    }

    /** Usable rows that [enough] of the lists naming them agree on; the first list's note wins. */
    private fun agreed(from: List<Model>, enough: (List<Model>) -> Boolean): Map<String, Row> {
        val owners = linkedMapOf<String, MutableList<Model>>()
        val first = linkedMapOf<String, Row>()
        for (m in from) for (r in usable(m.rows).values) {
            owners.getOrPut(r.pkg) { mutableListOf() } += m
            if (r.pkg !in first) first[r.pkg] = r
        }
        return first.filterKeys { enough(owners.getValue(it)) }
    }

    companion object {
        /** Categories the clean-up may switch off on its own; the rest are the owner's choice. */
        val PHASE_OF = mapOf(
            "ads" to 1, "tracking" to 1,
            "preloads" to 2, "demo" to 2,
            "brand" to 3, "other" to 3,
        )
        private val COMMON = setOf("ads", "tracking")
        private const val COMMON_BRANDS = 3

        fun usable(rows: List<Row>): Map<String, Row> =
            rows.filter { !it.ask && it.category in PHASE_OF }.associateBy { it.pkg }

        /** Plain words for a category, for plans and screens. */
        fun title(category: String): String = when (category) {
            "ads" -> "Adverts and sponsored rows"
            "tracking" -> "Tracking"
            "preloads" -> "Apps nobody asked for"
            "demo" -> "Shop demo mode"
            "brand" -> "Brand extras"
            else -> "Background extras"
        }

        fun parse(text: String): List<Model> {
            val out = mutableListOf<Model>()
            var head: List<String>? = null
            var never = emptySet<String>()
            val rows = mutableListOf<Row>()
            fun flush() {
                val h = head ?: return
                fun list(i: Int) = h[i].split(',').filter { it.isNotEmpty() }
                out += Model(h[0], h[1], list(2), list(3), h[4].toIntOrNull(), h[5].toIntOrNull(), never, rows.toList())
                rows.clear()
            }
            for (line in text.lines()) {
                when {
                    line.startsWith("@ ") -> {
                        flush()
                        head = line.removePrefix("@ ").split('|').let { it + List(maxOf(0, 7 - it.size)) { "" } }
                        never = emptySet()
                    }
                    line.startsWith("! ") -> never = line.removePrefix("! ").split(' ').filter { it.isNotEmpty() }.toSet()
                    line.isBlank() || line.startsWith("#") || head == null -> Unit
                    else -> {
                        val body = line.substringBefore('#').trim().split(Regex("""\s+"""))
                        if (body.size != 2) continue
                        val ask = body[0].endsWith('?')
                        rows += Row(body[1], body[0].removeSuffix("?"), ask, line.substringAfter('#', "").trim())
                    }
                }
            }
            flush()
            return out
        }
    }
}
