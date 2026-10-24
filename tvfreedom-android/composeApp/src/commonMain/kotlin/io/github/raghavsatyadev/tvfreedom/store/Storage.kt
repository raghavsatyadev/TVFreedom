package io.github.raghavsatyadev.tvfreedom.store

import io.github.raghavsatyadev.tvfreedom.core.Community
import io.github.raghavsatyadev.tvfreedom.core.Guard
import io.github.raghavsatyadev.tvfreedom.core.ListEntry
import io.github.raghavsatyadev.tvfreedom.core.ListFiles
import io.github.raghavsatyadev.tvfreedom.core.PHASES
import io.github.raghavsatyadev.tvfreedom.core.Plan
import io.github.raghavsatyadev.tvfreedom.core.Removal
import io.github.raghavsatyadev.tvfreedom.core.TvInfo
import io.github.raghavsatyadev.tvfreedom.core.sortedByKey
import io.github.raghavsatyadev.tvfreedom.flow.ChangeList
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okio.FileSystem
import okio.Path

/** Something restore can undo: a list (repo phase or a TVFreedom run) or everything back to a backup. */
data class RestoreOption(val label: String, val kind: Kind, val text: String = "", val dir: Path? = null) {
    enum class Kind { LIST, BACKUP, FACTORY }

    val entries: List<ListEntry> get() = ListFiles.readEntries(text)
}

/** A TVFreedom run's three record files as text (empty when a file is missing). */
data class RunRecord(val dir: Path, val applied: String, val extras: String = "", val settings: String = "")

/**
 * App-private files, in the repo's layout: backups/<date>-tvfreedom-<model>-<serial>/ and runs/<model>/ai-<stamp>/
 * (phaseN.txt, plan.json, applied.txt, verified). The repo's lists/ ship as assets.
 */
class Storage(
    private val fs: FileSystem,
    filesDir: Path,
    private val cache: Path,
    private val assets: Assets,
    prefs: Prefs,
) {
    val root = filesDir / "tvfreedom"
    val keysDir = root / "keys"
    private val backups = root / "backups"
    private val runs = root / "runs"
    val settings = Settings(prefs)
    val designs = DesignStore(fs, root / "designs")

    private fun asset(path: String) = assets.read(path).decodeToString()

    /** Every file under assets/lists, keyed by its path inside lists/. */
    private fun assetLists(): Map<String, String> {
        val out = mutableMapOf<String, String>()
        fun walk(dir: String) {
            for (name in assets.list(dir)) {
                val path = "$dir/$name"
                if (name.endsWith(".txt")) out[path.removePrefix("lists/")] = asset(path) else walk(path)
            }
        }
        walk("lists")
        return out.sortedByKey()
    }

    /** The Clean Your TV community lists, read once. */
    val community: Community? by lazy { runCatching { Community(asset("lists/community.lst")) }.getOrNull() }

    fun keep(): Set<String> = runCatching { Guard.parseKeep(asset("lists/keep.txt")) }.getOrDefault(emptySet())

    /** Repo phase lists plus every verified run on this phone, keyed the way [io.github.raghavsatyadev.tvfreedom.core.Known] wants. */
    fun knownFiles(): Map<String, String> {
        val out = assetLists().filterKeys { ListFiles.isPhaseFile(it) }.toMutableMap()
        fs.dirs(runs).forEach { modelDir ->
            learnedRuns(modelDir).forEach { run ->
                val prefix = if (modelDir.name == ListFiles.ROOT_MODEL) "" else "${modelDir.name}/"
                out["$prefix${run.name}/applied.txt"] = fs.readText(run / "applied.txt")
            }
        }
        return out
    }

    private fun learnedRuns(modelDir: Path) = fs.listOrNull(modelDir).orEmpty()
        .filter { it.name.startsWith("ai-") && fs.exists(it / "applied.txt") && fs.exists(it / VERIFIED) }
        .sortedBy { it.name }

    private fun safe(s: String) = ListFiles.slug(s)

    private fun newDir(parent: Path, name: String): Path = (parent / name).also { fs.createDirectories(it) }

    fun writeBackup(info: TvInfo, files: Map<String, String>): Path {
        val d = newDir(backups, "${stampNow()}-tvfreedom-${safe(info.model)}-${safe(info.serial.ifEmpty { "noserial" })}")
        files.forEach { (name, text) -> fs.writeText(d / name, text) }
        return d
    }

    fun readFile(p: Path): String = fs.readText(p)

    fun readBytes(p: Path): ByteArray? = p.takeIf { fs.isFile(it) }?.let { fs.readBytes(it) }

    fun writePng(p: Path, png: ByteArray): Path = p.also { fs.writeBytes(it, png) }

    /** Backups of this TV (by serial, else model), newest first. */
    fun backupsFor(info: TvInfo): List<Path> {
        val key = "-tvfreedom-${safe(info.model)}-${safe(info.serial.ifEmpty { "noserial" })}"
        return fs.dirs(backups).filter { it.name.endsWith(key) }.sortedByDescending { it.name }
    }

    /** Write phaseN.txt files and plan.json under runs/<model>/ai-<stamp>/. */
    fun savePlan(plan: Plan): Path {
        val d = newDir(runs / safe(plan.info.model), "ai-${stampNow()}")
        plan.phases.forEach { phase ->
            fs.writeText(d / "phase$phase.txt", ListFiles.formatPhase(plan.info, phase, plan.remove.filter { it.phase == phase }))
        }
        fs.writeText(d / "plan.json", planJson(plan))
        return d
    }

    fun writeApplied(runDir: Path, info: TvInfo, phases: List<Int>, rows: List<Removal>): Path =
        (runDir / "applied.txt").also { fs.writeText(it, ListFiles.formatApplied(info, phases, rows)) }

    /**
     * A home-screen setup run: runs/<model>/home-<stamp>/ with applied.txt (what it turns off) and settings.txt
     * (each setting it changes, with the value from before). Written before the TV changes, so Restore can undo it.
     * The home- prefix keeps it out of what the planner learns from.
     */
    fun writeHomeRun(info: TvInfo, phase: Int, rows: List<Removal>, settings: Map<String, String>): Path {
        val d = newDir(runs / safe(info.model), "home-${stampNow()}")
        writeApplied(d, info, listOf(phase), rows)
        fs.writeText(d / SETTINGS, settings.entries.joinToString("") { (k, v) -> "secure $k $v\n" })
        return d
    }

    /**
     * A phase 6 run: runs/<model>/extra-<stamp>/ with applied.txt (stock YouTube, if SmartTube replaces it) and
     * extras.txt, the undo line of each other change, appended just before the change is made.
     */
    fun writeExtraRun(info: TvInfo, rows: List<Removal>): Path {
        val d = newDir(runs / safe(info.model), "extra-${stampNow()}")
        writeApplied(d, info, listOf(6), rows)
        fs.writeText(d / EXTRAS, "")
        return d
    }

    fun addUndo(runDir: Path, line: String) = fs.appendText(runDir / EXTRAS, line.trim() + "\n")

    private fun lines(p: Path): List<String> = if (fs.exists(p)) fs.readText(p).lines() else emptyList()

    fun undoLines(runDir: Path): List<String> = lines(runDir / EXTRAS).filter { it.isNotBlank() }

    /** Phase 6 runs made after this backup, newest first: restoring the backup undoes them too. */
    fun extraRunsSince(info: TvInfo, backupDir: Path): List<Path> = fs.listOrNull(runs / safe(info.model)).orEmpty()
        .filter { it.name.startsWith("extra-") && fs.modified(it) >= fs.modified(backupDir) }.sortedByDescending { it.name }

    /** Every TVFreedom run on this model, newest first. */
    fun allRuns(info: TvInfo): List<Path> = fs.dirs(runs / safe(info.model)).sortedByDescending { it.name.substringAfter('-') }

    private fun isRun(p: Path) = p.name.startsWith("ai-") || p.name.startsWith("home-") || p.name.startsWith("extra-")

    private fun textOf(p: Path) = if (fs.exists(p)) fs.readText(p) else ""

    /** Every run on this model with what it recorded, for Restore's "By change" list. */
    fun runRecords(info: TvInfo): List<RunRecord> = fs.dirs(runs / safe(info.model)).filter(::isRun)
        .map { RunRecord(it, textOf(it / "applied.txt"), textOf(it / EXTRAS), textOf(it / SETTINGS)) }

    /** Write back a run's files after some of its changes were undone (only the files it has). */
    fun rewriteRun(r: RunRecord) {
        listOf("applied.txt" to r.applied, EXTRAS to r.extras, SETTINGS to r.settings).forEach { (name, text) ->
            if (fs.exists(r.dir / name)) fs.writeText(r.dir / name, text)
        }
    }

    /** A run that was put back at once: empty its files so Restore doesn't offer it again. */
    fun clearRun(runDir: Path) {
        fs.writeText(runDir / EXTRAS, "")
        fs.writeText(runDir / "applied.txt", "")
    }

    fun readCache(name: String): ByteArray? = (cache / name).takeIf { fs.isFile(it) }?.let { fs.readBytes(it) }

    fun writeCache(name: String, bytes: ByteArray) = fs.writeBytes(cache / name, bytes)

    /** (setting, value before) pairs a run changed, from its settings.txt. */
    fun savedSettings(runDir: Path): Map<String, String> = lines(runDir / SETTINGS)
        .map { it.trim().split(' ', limit = 3) }.filter { it.size == 3 && it[0] == "secure" }.associate { it[1] to it[2] }

    /** This model's phase N list from the repo (lists/phaseN.txt or lists/<model>/phaseN.txt), or null if it has none. */
    fun modelList(info: TvInfo, phase: Int): List<ListEntry>? {
        val slug = safe(info.model)
        val path = if (slug == ListFiles.ROOT_MODEL) "phase$phase.txt" else "$slug/phase$phase.txt"
        return assetLists()[path]?.let { ListFiles.readEntries(it) }
    }

    /** A new folder for a screenshot taken outside a run. */
    fun shotDir(): Path = newDir(root / "shots", stampNow())



    fun markVerified(runDir: Path, ok: Boolean) {
        val marker = runDir / VERIFIED
        if (ok) fs.writeText(marker, "") else fs.delete(marker, mustExist = false)
    }

    /** Runs verified after this backup are undone by restoring it: stop learning them. */
    fun unlearnSince(info: TvInfo, backupDir: Path) {
        val since = fs.modified(backupDir)
        fs.listOrNull(runs / safe(info.model)).orEmpty().forEach { run ->
            val marker = run / VERIFIED
            if (fs.exists(marker) && fs.modified(marker) >= since) fs.delete(marker)
        }
    }

    /** What restore can undo on this TV: TVFreedom runs (newest first), the repo's phase lists, then backups. */
    fun restoreOptions(info: TvInfo): List<RestoreOption> {
        val out = mutableListOf<RestoreOption>()
        fs.listOrNull(runs / safe(info.model)).orEmpty().filter(::isRun)
            .sortedByDescending { it.name.substringAfter('-') }.forEach { run ->
                // A run with nothing left to undo (all of it put back) is not offered.
                (run / "applied.txt").takeIf { fs.exists(it) }?.let { fs.readText(it) }
                    ?.takeIf { ListFiles.readEntries(it).isNotEmpty() || undoLines(run).isNotEmpty() || savedSettings(run).isNotEmpty() }?.let {
                    val title = ChangeList.groupTitle(run.name)
                    val label = when {
                        // Phase 5 saves the old screensaver; phase 4 doesn't.
                        run.name.startsWith("home-") -> if ("screensaver_components" in savedSettings(run)) {
                            "Screensaver, ${title.substringAfter(", ")}: the old one back"
                        } else "Home screen, ${title.substringAfter(", ")}: the old one back"
                        run.name.startsWith("extra-") -> "$title: background, settings, DNS and YouTube as they were"
                        else -> "$title: everything it applied"
                    }
                    out += RestoreOption(label, RestoreOption.Kind.LIST, it, run)
                }
            }
        val slug = safe(info.model)
        // Lists with the same name (two files for one phase) are offered once, together.
        assetLists().filterKeys { ListFiles.isPhaseFile(it) }.filterKeys { path ->
            (if ('/' in path) path.substringBefore('/') else ListFiles.ROOT_MODEL) == slug
        }.entries.groupBy { (path, _) ->
            val phase = ListFiles.phaseOf(path)
            "Built-in list: ${LIST_NAMES[phase] ?: PHASES[phase] ?: path.substringAfterLast('/')}"
        }.forEach { (label, files) ->
            out += RestoreOption(label, RestoreOption.Kind.LIST, files.joinToString("\n") { it.value })
        }
        out += RestoreOption("Everything back to how the TV came", RestoreOption.Kind.FACTORY)
        backupsFor(info).forEach { out += RestoreOption("Everything back to backup ${it.name.take(17)}", RestoreOption.Kind.BACKUP, dir = it) }
        return out
    }

    private fun planJson(p: Plan): String = buildJsonObject {
        putJsonObject("info") {
            put("host", p.info.host); put("model", p.info.model); put("brand", p.info.brand)
            put("device", p.info.device); put("android", p.info.android); put("serial", p.info.serial)
        }
        putJsonArray("remove") {
            p.remove.forEach { r ->
                add(buildJsonObject {
                    put("pkg", r.pkg); put("action", r.action); put("phase", r.phase)
                    put("reason", r.reason); put("source", r.source)
                })
            }
        }
        putJsonObject("kept") { p.kept.sortedByKey().forEach { (k, v) -> put(k, v) } }
        put("conflicts", JsonArray(p.conflicts.map { JsonPrimitive("${it.pkg}: ${it.keptAs}; earlier ${it.earlier}") }))
        put("already_off", JsonArray(p.alreadyOff.map { JsonPrimitive(it) }))
        put("unanswered", JsonArray(p.unanswered.map { JsonPrimitive(it) }))
    }.toString()

    companion object {
        const val VERIFIED = "verified" // marker in a run dir: applied, rebooted and the home screen came back
        private const val SETTINGS = "settings.txt"
        private const val EXTRAS = "extras.txt"
    }
}

/** Plain names for the built-in lists on the Restore screen. */
private val LIST_NAMES = mapOf(
    1 to "tracking and ads", 2 to "apps you don't use", 3 to "maker's extras", 4 to "TV's own home screen", 5 to "screensaver",
)
