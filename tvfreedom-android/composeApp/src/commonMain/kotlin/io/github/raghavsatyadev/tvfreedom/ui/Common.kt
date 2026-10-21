@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package io.github.raghavsatyadev.tvfreedom.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A screen: a large flexible top bar that collapses on scroll, then [content] as a lazy list.
 * The problem the last step hit (if any) and the step running now come first; nothing else is narrated.
 */
@Composable
fun Page(
    title: String,
    state: UiState,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: (@Composable () -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(title) },
                subtitle = subtitle?.let { { Text(it) } },
                navigationIcon = { if (onBack != null) IconButton(onClick = onBack, enabled = !state.busy) { Icon(Icons.Back, "Back") } },
                actions = actions,
                scrollBehavior = scroll,
            )
        },
        bottomBar = { if (bottomBar != null) BottomBar(bottomBar) },
    ) { pad ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding() + 4.dp,
                bottom = pad.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.notice.isNotEmpty() && !state.busy) item(key = "notice") { Notice(state.notice) }
            if (state.busy && state.headline.isNotEmpty()) item(key = "busy") { Working(state.headline, state.status) }
            content()
        }
    }
}

@Composable
private fun BottomBar(content: @Composable () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) { content() }
    }
}

/** What is running now: the expressive loading indicator, a plain headline, and the latest detail line. */
@Composable
fun Working(headline: String, detail: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = MaterialTheme.shapes.extraLarge) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            LoadingIndicator(Modifier.size(48.dp), color = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(headline, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.bodySmall, maxLines = 2,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
            }
        }
    }
}

/** Something the user has to know or act on: a failure, or a step that needs them. */
@Composable
fun Notice(text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape = MaterialTheme.shapes.large) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Error, null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.width(12.dp))
            Text(text, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** An outcome: a big expressive shape with a tick or a warning, a headline and one line. */
@Composable
fun OutcomeView(ok: Boolean, headline: String, body: String) {
    val c = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ShapeBadge(if (ok) Icons.Done else Icons.Error, if (ok) c.primaryContainer else c.errorContainer,
            if (ok) c.onPrimaryContainer else c.onErrorContainer, cookie = ok)
        Text(headline, style = MaterialTheme.typography.headlineSmall)
        Text(body, style = MaterialTheme.typography.bodyLarge, color = c.onSurfaceVariant)
    }
}

/** An icon on one of the Material 3 Expressive shapes (cookie for good news, a softer burst otherwise). */
@Composable
fun ShapeBadge(icon: ImageVector, container: Color, content: Color, cookie: Boolean = true, size: Int = 96) {
    val shape = (if (cookie) MaterialShapes.Cookie9Sided else MaterialShapes.SoftBurst).toShape()
    Box(Modifier.size(size.dp).background(container, shape), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size((size * 0.45).dp), tint = content)
    }
}

/** A small label above a group of list items. */
@Composable
fun GroupLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp))
}

/** Shapes for item [i] of [n] in a segmented (connected) list. */
@Composable
fun seg(i: Int, n: Int) = ListItemDefaults.segmentedShapes(i, n)

/** Segmented items sit on a raised container so each group reads as one block. */
@Composable
fun segColors() = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)

/** The one main action of a screen: a medium, full-width button whose shape morphs when pressed. */
@Composable
fun MainAction(text: String, icon: ImageVector?, enabled: Boolean = true, onClick: () -> Unit) {
    val h = ButtonDefaults.MediumContainerHeight
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = h), enabled = enabled, shapes = ButtonDefaults.shapesFor(h),
        contentPadding = ButtonDefaults.contentPaddingFor(h)) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(ButtonDefaults.iconSizeFor(h)))
            Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(h)))
        }
        Text(text, style = ButtonDefaults.textStyleFor(h))
    }
}

@Composable
fun SecondAction(text: String, icon: ImageVector?, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, modifier = modifier.heightIn(min = 52.dp), enabled = enabled, shapes = ButtonDefaults.shapes()) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        }
        Text(text)
    }
}

/** The raw run log, folded away: only for when something needs explaining. */
fun LazyListScope.details(lines: List<String>, open: Boolean, toggle: () -> Unit) {
    if (lines.isEmpty()) return
    item(key = "details") {
        Column {
            TextButton(onClick = toggle) {
                Text(if (open) "Hide details" else "Show details")
                Icon(Icons.Expand, null, Modifier.padding(start = 4.dp))
            }
            AnimatedVisibility(open) { LogView(lines) }
        }
    }
}

@Composable
private fun LogView(lines: List<String>) {
    val list = rememberLazyListState()
    LaunchedEffect(lines.size) { if (lines.isNotEmpty()) list.scrollToItem(lines.size - 1) }
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)) {
        LazyColumn(Modifier.heightIn(max = 320.dp).padding(12.dp), state = list) {
            items(lines) { Text(it, fontFamily = FontFamily.Monospace, fontSize = 11.sp) }
        }
    }
}

/**
 * The human confirmation: the action runs only after the user types [word] exactly.
 * Nothing else in the app can press this for them.
 */
@Composable
fun TypedConfirm(title: String, body: String, word: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    var typed by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Shield, null) },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(body)
                OutlinedTextField(typed, { typed = it }, label = { Text("Type $word to confirm") }, singleLine = true,
                    shape = MaterialTheme.shapes.large)
            }
        },
        // Keyboards add a space after a word (and may capitalise it), so match loosely.
        confirmButton = { Button(onClick = onConfirm, enabled = typed.trim().equals(word, ignoreCase = true), shapes = ButtonDefaults.shapes()) { Text(word) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
