package io.github.raghavsatyadev.tvfreedom.shell

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import java.io.File
import kotlinx.coroutines.runBlocking

/**
 * Where the phone sends a design, over adb:
 *   head -c N | content write --uri content://io.github.raghavsatyadev.tvfreedom.shell.design/design
 *   content call --uri content://io.github.raghavsatyadev.tvfreedom.shell.design --method apply
 * "apply" tries the sent design and keeps it only if it works; the answer's "result" is "ok" or what is
 * wrong. "reset" goes back to the built-in design; "catalog" describes the blocks a design may use.
 * Only adb (which holds DUMP) may call.
 */
class DesignProvider : ContentProvider() {

    override fun onCreate() = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val ctx = context ?: throw IllegalStateException("no context")
        require('w' in mode) { "only writing a design is allowed" }
        return ParcelFileDescriptor.open(Designs.staged(ctx),
            ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE)
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val ctx = context ?: throw IllegalStateException("no context")
        // The manifest's permissions guard openFile only; call() checks for itself.
        if (ctx.checkCallingOrSelfPermission(android.Manifest.permission.DUMP) != PackageManager.PERMISSION_GRANTED) {
            throw SecurityException("only adb may change the design")
        }
        val result = when (method) {
            "apply" -> Designs.apply(ctx)
            "reset" -> { Designs.saved(ctx).delete(); Designs.changed(); "ok" }
            "catalog" -> Guide.TEXT
            else -> "Unknown method $method; use apply, reset or catalog."
        }
        return Bundle().apply { putString("result", result) }
    }

    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun getType(uri: Uri) = "application/json"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?) = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0
}

/** The TV's saved design and the one waiting to be checked. */
object Designs {
    fun saved(ctx: Context) = File(ctx.filesDir, "design.json")
    fun staged(ctx: Context) = File(ctx.filesDir, "design.next.json")

    /** Runs on the main thread whenever the saved design changes; the open screen redraws. */
    @Volatile var listener: (() -> Unit)? = null

    fun changed() = Handler(Looper.getMainLooper()).post { listener?.invoke() }

    /** The saved design, else the built-in one. */
    fun current(ctx: Context): String {
        val f = saved(ctx)
        return if (f.exists()) f.readText() else ctx.assets.open("sample.json").bufferedReader().use { it.readText() }
    }

    /** Checks the staged design; keeps it if it works. "ok" or the reason it doesn't. */
    fun apply(ctx: Context): String {
        val next = staged(ctx)
        if (!next.exists() || next.length() == 0L) return "No design was sent."
        val problem = runCatching { runBlocking { Check.problem(next.readText(), tvApps(ctx)) } }
            .getOrElse { "The design could not be read: ${it.message}" }
        if (problem != null) return problem
        if (!next.renameTo(saved(ctx))) return "The TV could not save the design."
        changed()
        return "ok"
    }
}

/** The TV's apps (those with a TV or phone launcher entry), this one left out. */
fun tvApps(ctx: Context): List<Map<String, String>> {
    val pm = ctx.packageManager
    val seen = LinkedHashMap<String, String>()
    for (cat in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(cat), 0).forEach {
            val pkg = it.activityInfo.packageName
            if (pkg != ctx.packageName && pkg !in seen) seen[pkg] = it.loadLabel(pm).toString()
        }
    }
    return seen.map { (pkg, label) -> mapOf("pkg" to pkg, "label" to label) }
}
