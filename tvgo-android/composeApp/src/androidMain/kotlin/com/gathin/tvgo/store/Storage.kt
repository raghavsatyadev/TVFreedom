package com.gathin.tvgo.store

import android.content.Context
import com.gathin.tvgo.core.Guard
import com.gathin.tvgo.core.ListEntry
import com.gathin.tvgo.core.ListFiles
import com.gathin.tvgo.core.PHASES
import com.gathin.tvgo.core.Plan
import com.gathin.tvgo.core.Removal
import com.gathin.tvgo.core.TvInfo
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Something restore can undo: a list (repo phase or a tvgo run) or everything back to a backup. */
data class RestoreOption(val label: String, val kind: Kind, val text: String = "", val dir: File? = null) {
    enum class Kind { LIST, BACKUP }

    val entries: List<ListEntry> get() = ListFiles.readEntries(text)
}

/**
 * App-private files, in the repo's layout: backups/<date>-tvgo-<model>-<serial>/ and runs/<model>/ai-<stamp>/
 * (phaseN.txt, plan.json, applied.txt, verified). The repo's lists/ ship as assets.
 */
class Storage(private val ctx: Context) {
    val root = File(ctx.filesDir, "tvgo")
    val keysDir = File(root, "keys")
    private val backups = File(root, "backups")
    private val runs = File(root, "runs")
    val settings = Settings(ctx.getSharedPreferences("tvgo", Context.MODE_PRIVATE))

    private fun asset(path: String) = ctx.assets.open(path).bufferedReader().use { it.readText() }

    /** Every file under assets/lists, keyed by its path inside lists/. */
    private fun assetLists(): Map<String, String> {
        val out = sortedMapOf<String, String>()
        fun walk(dir: String) {
            for (name in ctx.assets.list(dir).orEmpty()) {
                val path = "$dir/$name"
                if (name.endsWith(".txt")) out[path.removePrefix("lists/")] = asset(path) else walk(path)
            }
        }
        walk("lists")
        return out
    }

    fun keep(): Set<String> = runCatching { Guard.parseKeep(asset("lists/keep.txt")) }.getOrDefault(emptySet())

    /** Repo phase lists plus every verified run on this phone, keyed the way [com.gathin.tvgo.core.Known] wants. */
    fun knownFiles(): Map<String, String> {
        val out = assetLists().filterKeys { ListFiles.isPhaseFile(it) }.toMutableMap()
        runs.listFiles().orEmpty().filter { it.isDirectory }.forEach { modelDir ->
            learnedRuns(modelDir).forEach { run ->
                val prefix = if (modelDir.name == ListFiles.ROOT_MODEL) "" else "${modelDir.name}/"
                out["$prefix${run.name}/applied.txt"] = File(run, "applied.txt").readText()
            }
        }
        return out
    }

    private fun learnedRuns(modelDir: File) = modelDir.listFiles().orEmpty()
        .filter { it.name.startsWith("ai-") && File(it, "applied.txt").exists() && File(it, VERIFIED).exists() }
        .sortedBy { it.name }

    private fun stamp(pattern: String) = SimpleDateFormat(pattern, Locale.US).format(Date())

    private fun safe(s: String) = ListFiles.slug(s)

    fun writeBackup(info: TvInfo, files: Map<String, String>): File {
        val d = File(backups, "${stamp("yyyy-MM-dd-HHmmss")}-tvgo-${safe(info.model)}-${safe(info.serial.ifEmpty { "noserial" })}")
        d.mkdirs()
        files.forEach { (name, text) -> File(d, name).writeText(text) }
        return d
    }

    /** Backups of this TV (by serial, else model), newest first. */
    fun backupsFor(info: TvInfo): List<File> {
        val key = "-tvgo-${safe(info.model)}-${safe(info.serial.ifEmpty { "noserial" })}"
        return backups.listFiles().orEmpty().filter { it.isDirectory && it.name.endsWith(key) }.sortedByDescending { it.name }
    }

    /** Write phaseN.txt files and plan.json under runs/<model>/ai-<stamp>/. */
    fun savePlan(plan: Plan): File {
        val d = File(File(runs, safe(plan.info.model)), "ai-${stamp("yyyy-MM-dd-HHmmss")}")
        d.mkdirs()
        plan.phases.forEach { phase ->
            File(d, "phase$phase.txt").writeText(ListFiles.formatPhase(plan.info, phase, plan.remove.filter { it.phase == phase }))
        }
        File(d, "plan.json").writeText(planJson(plan))
        return d
    }

    fun writeApplied(runDir: File, info: TvInfo, phases: List<Int>, rows: List<Removal>): File =
        File(runDir, "applied.txt").apply { writeText(ListFiles.formatApplied(info, phases, rows)) }

    /**
     * A home-screen setup run: runs/<model>/home-<stamp>/ with applied.txt (what it turns off) and settings.txt
     * (each setting it changes, with the value from before). Written before the TV changes, so Restore can undo it.
     * The home- prefix keeps it out of what the planner learns from.
     */
    fun writeHomeRun(info: TvInfo, phase: Int, rows: List<Removal>, settings: Map<String, String>): File {
        val d = File(File(runs, safe(info.model)), "home-${stamp("yyyy-MM-dd-HHmmss")}")
        d.mkdirs()
        writeApplied(d, info, listOf(phase), rows)
        File(d, SETTINGS).writeText(settings.entries.joinToString("") { (k, v) -> "secure $k $v\n" })
        return d
    }

    /**
     * A phase 6 run: runs/<model>/extra-<stamp>/ with applied.txt (stock YouTube, if SmartTube replaces it) and
     * extras.txt, the undo line of each other change, appended just before the change is made.
     */
    fun writeExtraRun(info: TvInfo, rows: List<Removal>): File {
        val d = File(File(runs, safe(info.model)), "extra-${stamp("yyyy-MM-dd-HHmmss")}")
        d.mkdirs()
        writeApplied(d, info, listOf(6), rows)
        File(d, EXTRAS).writeText("")
        return d
    }

    fun addUndo(runDir: File, line: String) = File(runDir, EXTRAS).appendText(line.trim() + "\n")

    fun undoLines(runDir: File): List<String> = File(runDir, EXTRAS).takeIf { it.exists() }?.readLines().orEmpty().filter { it.isNotBlank() }

    /** Phase 6 runs made after this backup, newest first: restoring the backup undoes them too. */
    fun extraRunsSince(info: TvInfo, backupDir: File): List<File> = File(runs, safe(info.model)).listFiles().orEmpty()
        .filter { it.name.startsWith("extra-") && it.lastModified() >= backupDir.lastModified() }.sortedByDescending { it.name }

    /** A run that was put back at once: empty its files so Restore doesn't offer it again. */
    fun clearRun(runDir: File) {
        File(runDir, EXTRAS).writeText("")
        File(runDir, "applied.txt").writeText("")
    }

    fun cacheDir(): File = ctx.cacheDir

    /** (setting, value before) pairs a run changed, from its settings.txt. */
    fun savedSettings(runDir: File): Map<String, String> = File(runDir, SETTINGS).takeIf { it.exists() }?.readLines().orEmpty()
        .map { it.trim().split(' ', limit = 3) }.filter { it.size == 3 && it[0] == "secure" }.associate { it[1] to it[2] }

    /** This model's phase N list from the repo (lists/phaseN.txt or lists/<model>/phaseN.txt), or null if it has none. */
    fun modelList(info: TvInfo, phase: Int): List<ListEntry>? {
        val slug = safe(info.model)
        val path = if (slug == ListFiles.ROOT_MODEL) "phase$phase.txt" else "$slug/phase$phase.txt"
        return assetLists()[path]?.let { ListFiles.readEntries(it) }
    }

    /** A new folder for a screenshot taken outside a run. */
    fun shotDir(): File = File(File(root, "shots"), stamp("yyyy-MM-dd-HHmmss")).apply { mkdirs() }

    /** The launcher APK bundled at build time (launcher/, without personal settings). */
    fun launcherApk(): ByteArray = ctx.assets.open("launcher/tvhome.apk").use { it.readBytes() }

    fun markVerified(runDir: File, ok: Boolean) {
        val marker = File(runDir, VERIFIED)
        if (ok) marker.writeText("") else marker.delete()
    }

    /** Runs verified after this backup are undone by restoring it: stop learning them. */
    fun unlearnSince(info: TvInfo, backupDir: File) {
        val since = backupDir.lastModified()
        File(runs, safe(info.model)).listFiles().orEmpty().forEach { run ->
            val marker = File(run, VERIFIED)
            if (marker.exists() && marker.lastModified() >= since) marker.delete()
        }
    }

    /** What restore can undo on this TV: tvgo runs (newest first), the repo's phase lists, then backups. */
    fun restoreOptions(info: TvInfo): List<RestoreOption> {
        val out = mutableListOf<RestoreOption>()
        File(runs, safe(info.model)).listFiles().orEmpty().filter { it.name.startsWith("ai-") || it.name.startsWith("home-") || it.name.startsWith("extra-") }
            .sortedByDescending { it.name.substringAfter('-') }.forEach { run ->
                File(run, "applied.txt").takeIf { it.exists() }
                    ?.takeIf { !run.name.startsWith("extra-") || undoLines(run).isNotEmpty() || ListFiles.readEntries(it.readText()).isNotEmpty() }?.let {
                    val label = if (run.name.startsWith("home-")) "Home screen setup ${run.name.removePrefix("home-")}: the old home screen back"
                    else if (run.name.startsWith("extra-")) "Extras ${run.name.removePrefix("extra-")}: background, settings, DNS and YouTube as they were"
                    else "Run ${run.name.removePrefix("ai-")}: everything it applied"
                    out += RestoreOption(label, RestoreOption.Kind.LIST, it.readText(), run)
                }
            }
        val slug = safe(info.model)
        assetLists().filterKeys { ListFiles.isPhaseFile(it) }.forEach { (path, text) ->
            val model = if ('/' in path) path.substringBefore('/') else ListFiles.ROOT_MODEL
            if (model == slug) {
                val phase = ListFiles.phaseOf(path)
                out += RestoreOption("Repo list ${path.substringAfterLast('/')}: phase $phase (${PHASES[phase] ?: "other"})",
                    RestoreOption.Kind.LIST, text)
            }
        }
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
        putJsonObject("kept") { p.kept.toSortedMap().forEach { (k, v) -> put(k, v) } }
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
