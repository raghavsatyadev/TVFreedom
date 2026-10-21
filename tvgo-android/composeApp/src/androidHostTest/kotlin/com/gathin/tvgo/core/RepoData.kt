package com.gathin.tvgo.core

import java.io.File

/** The repo's data/ and lists/, the same inputs tools/make_golden.py feeds the Python code. */
object RepoData {
    val root = File(System.getProperty("tvgo.repo") ?: "../..")

    private fun data(name: String) = File(root, "data/$name").readText()
    private fun names(name: String) = data(name).lines().map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    val facts: Facts by lazy {
        val paths = Regex("^package:(.+)=([^=\\s]+)\\r?$", RegexOption.MULTILINE).findAll(data("packages-all.txt"))
            .associate { it.groupValues[2] to it.groupValues[1] }
        Facts(paths, names("packages-disabled.txt"), names("packages-user.txt"),
            home = setOf("com.google.android.tvlauncher"), uids = mapOf("com.android.phone" to 1001))
    }

    val info = TvInfo(host = "x", model = "", device = "moor0", brand = "Xiaomi", android = "11", sdk = "30",
        serial = "s", ime = "com.google.android.inputmethod.latin/.LatinIME")

    val keep: Set<String> by lazy { Guard.parseKeep(File(root, "lists/keep.txt").readText()) }

    /** Every phase file under lists/ (keep.txt and other files are left out), keyed by its path in lists/. */
    val lists: Map<String, String> by lazy {
        val dir = File(root, "lists")
        dir.walkTopDown().filter { it.isFile && ListFiles.isPhaseFile(it.name) }
            .associate { it.relativeTo(dir).invariantSeparatorsPath to it.readText() }
    }

    fun golden(name: String) = File(root, "tvgo-android/composeApp/src/androidHostTest/resources/golden/$name").readText()
}
