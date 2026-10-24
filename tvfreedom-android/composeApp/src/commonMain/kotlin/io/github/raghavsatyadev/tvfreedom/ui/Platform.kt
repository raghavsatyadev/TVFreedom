package io.github.raghavsatyadev.tvfreedom.ui

import io.github.raghavsatyadev.tvfreedom.core.Classifier
import io.github.raghavsatyadev.tvfreedom.store.Brand
import io.github.raghavsatyadev.tvfreedom.store.Net
import io.github.raghavsatyadev.tvfreedom.store.Settings
import io.github.raghavsatyadev.tvfreedom.store.Storage
import io.github.raghavsatyadev.tvfreedom.tv.TvLink
import kotlinx.coroutines.flow.StateFlow

/** What a long job shows in the notification while TVFreedom is in the background. */
data class Progress(val title: String, val text: String, val done: Int = 0, val total: Int = 0, val attention: Boolean = false)

/** A town and its coordinates, for the screensaver's weather. */
data class Place(val name: String, val latitude: String, val longitude: String)

/** The next thing the user does to use Claude in Termux. */
/** What a Gemini Nano check says when the model is on the phone. */
const val AI_READY = "ready"

enum class TermuxStep { INSTALL_TERMUX, PERMISSION, OPEN_TERMUX, ALLOW_APPS, INSTALL_CLAUDE, LOGIN, READY }

/** Everything the shared app needs from the platform it runs on. */
interface AppPlatform {
    val storage: Storage
    val brand: Brand
    val link: TvLink
    val net: Net

    /** More than 0 while TVFreedom is on screen (on-phone AI only answers then). */
    val open: StateFlow<Int>

    /** Keep the app alive while a long job runs, showing [p]; [update] it as the job goes, [stop] at the end. */
    fun startJob(p: Progress)
    fun updateJob(p: Progress)
    fun stopJob()

    /** The AI that plans, built for [ai]; null for none. [waitOpen] waits for TVFreedom to be on screen. */
    fun classifier(ai: Settings.Ai, settings: Settings, waitOpen: suspend () -> Unit, log: (String) -> Unit): Classifier?

    /** "Claude is ready", "ready" (Nano) or what the user still has to do for [ai]. */
    suspend fun checkAi(ai: Settings.Ai, settings: Settings, log: (String) -> Unit): String

    suspend fun termuxStep(log: (String) -> Unit): TermuxStep

    /** This phone's town, if location is allowed and found. */
    fun locationGranted(): Boolean
    suspend fun phonePlace(): Place?

    /** The TVFreedom home screen APK for the TV (from the newest GitHub release) and its versionCode. */
    suspend fun launcherApk(log: (String) -> Unit): Pair<ByteArray, Long>
}
