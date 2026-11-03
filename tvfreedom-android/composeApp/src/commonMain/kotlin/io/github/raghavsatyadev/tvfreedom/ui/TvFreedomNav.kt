package io.github.raghavsatyadev.tvfreedom.ui

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay

/**
 * The screen back leads to from [screen], or null where back leaves the app.
 * Matches each page's back arrow, so the system back and the arrow agree.
 */
internal fun backTarget(screen: Screen, hasTv: Boolean, onboarded: Boolean): Screen? = when (screen) {
    Screen.CONNECT -> null
    Screen.SETUP -> Screen.SETTINGS.takeIf { onboarded }
    Screen.TV -> Screen.CONNECT
    Screen.HOME -> Screen.PLAN
    else -> if (hasTv) Screen.TV else Screen.CONNECT
}

/** The back stack for [current]: its chain of back targets, oldest first. While a job runs it is just [current]. */
internal fun backStackFor(current: Screen, busy: Boolean, hasTv: Boolean, onboarded: Boolean): List<Screen> {
    if (busy) return listOf(current)
    val stack = ArrayDeque(listOf(current))
    var next = backTarget(current, hasTv, onboarded)
    while (next != null && next !in stack) {
        stack.addFirst(next)
        next = backTarget(next, hasTv, onboarded)
    }
    return stack
}

/** Shows the view model's screen with Navigation 3, so system back gets predictive-back previews. */
@Composable
fun TvFreedomNav(vm: TvFreedomViewModel) {
    val s = vm.state
    val stack = backStackFor(s.screen, s.busy, s.info != null, vm.settings.onboarded)
    NavDisplay(
        backStack = stack,
        onBack = { if (s.screen == Screen.TV) vm.disconnect() else stack.getOrNull(stack.size - 2)?.let(vm::go) },
        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
        entryProvider = entryProvider {
            entry<Screen> { screen ->
                when (screen) {
                    Screen.SETUP -> SetupScreen(vm)
                    Screen.CONNECT -> ConnectScreen(vm)
                    Screen.TV -> TvScreen(vm)
                    Screen.PLAN -> PlanScreen(vm)
                    Screen.RUN -> RunScreen(vm)
                    Screen.RESTORE -> RestoreScreen(vm)
                    Screen.SETTINGS -> SettingsScreen(vm)
                    Screen.HOME -> HomeScreen(vm)
                    Screen.DESIGNS -> DesignsScreen(vm)
                }
            }
        },
    )
}
