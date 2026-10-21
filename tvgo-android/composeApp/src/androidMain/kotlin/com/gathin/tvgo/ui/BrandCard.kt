@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.gathin.tvgo.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gathin.tvgo.flow.HomeSetup

/**
 * A company's logo and the TV home screen's colour, with a small picture of how the TV will look.
 * Kept on this phone, so every TV debloated from it gets the same look (plan phase 4 sends it).
 */
@Composable
fun BrandCard(vm: TvGoViewModel) {
    val s = vm.state
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(vm::pickLogo) }
    val logo = rememberLogo(vm)
    LaunchedEffect(Unit) { if (s.brandColors.isEmpty()) vm.loadBrand() }
    val color = vm.brand.color
    Card(shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("For a company's TVs: its logo on the home screen, on a colour taken from the logo, and above the " +
                "screensaver's clock. A square logo gets a taller spot, a wide one a longer spot.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val c = s.homeConfig ?: s.homeDraft?.config
            HomePreview(logo, color, c?.favourites?.ifEmpty { null } ?: HomeSetup.DEFAULT_FAVOURITES, c?.columns ?: 4)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondAction(if (logo == null) "Add a logo" else "Change logo", Icons.Add, enabled = !s.busy) {
                    pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                if (logo != null || color.isNotEmpty()) SecondAction("Remove", Icons.Close, enabled = !s.busy, onClick = vm::clearBrand)
            }
            if (s.brandColors.isNotEmpty()) {
                Text("Background colour", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    (s.brandColors + "").forEach { c -> Swatch(c, selected = c.equals(color, ignoreCase = true)) { vm.setBrandColor(c) } }
                }
            }
        }
    }
}

@Composable
private fun Swatch(c: String, selected: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(40.dp).clip(CircleShape).background(parse(c))
        .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
        .clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        if (selected) Icon(Icons.Done, "Chosen", tint = Color.White)
    }
}
