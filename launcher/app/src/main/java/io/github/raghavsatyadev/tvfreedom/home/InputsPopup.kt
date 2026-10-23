package io.github.raghavsatyadev.tvfreedom.home

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.view.View
import android.view.ViewGroup
import android.widget.Toast

/** Dropdown menu under the Inputs pill: HDMI ports (with connection state), AV and Live TV. */
object InputsPopup {

    private const val CHANNEL_TYPE = "vnd.android.cursor.item/channel"

    fun show(act: Activity, anchor: View, root: ViewGroup, liveTvPkg: String, open: (Intent) -> Unit) {
        // Compact: as wide as the Inputs pill (a touch wider so "HDMI 1" fits), hanging right below it.
        val density = act.resources.displayMetrics.density
        val menu = GlassMenu(act, root, maxOf(anchor.width, (150 * density).toInt()))

        val tim = act.getSystemService(Context.TV_INPUT_SERVICE) as TvInputManager?
        var sawNonHdmi = false
        var tunerId: String? = null
        if (tim != null) {
            // HDMI ports first in port order, then AV and the rest.
            val inputs = tim.tvInputList.sortedWith { a, b ->
                val ha = a.type == TvInputInfo.TYPE_HDMI
                val hb = b.type == TvInputInfo.TYPE_HDMI
                if (ha != hb) {
                    if (ha) -1 else 1
                } else {
                    a.loadLabel(act).toString().compareTo(b.loadLabel(act).toString(), ignoreCase = true)
                }
            }
            // Some TVs (Sony) report every HDMI port as connected whether or not anything is plugged in,
            // so drop the HDMI status then. A TV with every port really in use loses it too.
            val hdmi = inputs.filter { it.isPassthroughInput && it.type == TvInputInfo.TYPE_HDMI }
            val hdmiConnected = hdmi.count { tim.getInputState(it.id) == TvInputManager.INPUT_STATE_CONNECTED }
            val hdmiStateBogus = hdmi.size > 1 && hdmiConnected == hdmi.size
            for (info in inputs) {
                if (info.type == TvInputInfo.TYPE_TUNER &&
                    (info.serviceInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                    (tunerId == null || info.id < tunerId)
                ) {
                    tunerId = info.id
                }
                if (!info.isPassthroughInput) continue
                var label = info.loadLabel(act)?.toString() ?: info.id
                val isHdmi = info.type == TvInputInfo.TYPE_HDMI
                if (isHdmi && !label.uppercase().contains("HDMI")) label = "HDMI $label"
                val id = info.id
                val state = if (isHdmi && hdmiStateBogus) -1 else tim.getInputState(id)
                if (!isHdmi && !menu.isEmpty() && !sawNonHdmi) menu.addSeparator()
                if (!isHdmi) sawNonHdmi = true
                menu.addRow(if (isHdmi) R.drawable.ic_input else R.drawable.ic_av, label, state) {
                    open(Intent(Intent.ACTION_VIEW).setDataAndType(
                        TvContract.buildChannelUriForPassthroughInput(id), CHANNEL_TYPE))
                }
            }
        }
        var liveTv = if (liveTvPkg.isEmpty()) null else act.packageManager.getLeanbackLaunchIntentForPackage(liveTvPkg)
        if (liveTv != null && tunerId != null) {
            liveTv = Intent(Intent.ACTION_VIEW)
                .setDataAndType(TvContract.buildChannelUriForPassthroughInput(tunerId), CHANNEL_TYPE)
                .setPackage(liveTvPkg)
        }
        val liveTvIntent = liveTv
        if (liveTvIntent != null) {
            if (!menu.isEmpty()) menu.addSeparator()
            menu.addRow(R.drawable.ic_livetv, act.getString(R.string.live_tv), -1) { open(liveTvIntent) }
        }
        if (menu.isEmpty()) {
            Toast.makeText(act, R.string.no_inputs, Toast.LENGTH_SHORT).show()
            return
        }
        menu.showBelow(anchor)
    }
}
