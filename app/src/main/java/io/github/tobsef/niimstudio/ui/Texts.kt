package io.github.tobsef.niimstudio.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.tobsef.niimstudio.R
import io.github.tobsef.niimstudio.model.AppSettings
import io.github.tobsef.niimstudio.printer.ConnState
import io.github.tobsef.niimstudio.printer.FailReason
import io.github.tobsef.niimstudio.protocol.LabelType
import io.github.tobsef.niimstudio.protocol.PrintProgress
import io.github.tobsef.niimstudio.protocol.PrintStage
import io.github.tobsef.niimstudio.protocol.PrintVariant
import io.github.tobsef.niimstudio.protocol.PrinterException

/** Localized texts for values coming from the language-neutral printer/protocol layers. */

fun labelTypeRes(id: Int): Int = when (id) {
    LabelType.WITH_GAPS -> R.string.label_type_gaps
    LabelType.CONTINUOUS -> R.string.label_type_continuous
    LabelType.TRANSPARENT -> R.string.label_type_transparent
    LabelType.BLACK_MARK -> R.string.label_type_black_mark
    LabelType.BLACK_MARK_GAP -> R.string.label_type_black_mark_gap
    LabelType.HEAT_SHRINK_TUBE -> R.string.label_type_tube
    else -> R.string.label_type_unknown
}

fun variantRes(v: PrintVariant): Int = when (v) {
    PrintVariant.V3 -> R.string.variant_v3
    PrintVariant.LEGACY -> R.string.variant_legacy
    PrintVariant.V4 -> R.string.variant_v4
}

@Composable
fun connText(c: ConnState, s: AppSettings): String = when (c) {
    is ConnState.Connected -> stringResource(R.string.conn_connected, c.name)
    is ConnState.Connecting -> stringResource(R.string.conn_connecting, c.name)
    is ConnState.Failed -> stringResource(R.string.conn_failed, failText(c))
    ConnState.Disconnected -> s.deviceName?.let { stringResource(R.string.conn_disconnected_named, it) }
        ?: stringResource(R.string.conn_no_printer)
}

@Composable
fun failText(c: ConnState.Failed): String = when (c.reason) {
    FailReason.BLUETOOTH_OFF -> stringResource(R.string.fail_bluetooth_off)
    FailReason.NO_PERMISSION -> stringResource(R.string.fail_no_permission)
    FailReason.CONNECT_FAILED -> c.detail ?: stringResource(R.string.fail_connect)
}

@Composable
fun progressText(p: PrintProgress): String = when (p.stage) {
    PrintStage.STARTING -> stringResource(R.string.progress_starting)
    PrintStage.PREPARING -> stringResource(R.string.progress_preparing)
    PrintStage.SENDING -> stringResource(R.string.progress_sending, p.label, p.labels)
    PrintStage.PRINTING -> stringResource(R.string.progress_printing, p.label, p.labels, p.percent)
    PrintStage.DONE -> stringResource(R.string.progress_done)
    PrintStage.CANCELLED -> stringResource(R.string.progress_cancelled)
}

/** Printer error codes (0xDB packet / status byte) with a translation. */
private val printerErrorRes = mapOf(
    0x01 to R.string.perr_cover_open, 0x02 to R.string.perr_no_paper, 0x03 to R.string.perr_low_battery,
    0x04 to R.string.perr_battery, 0x05 to R.string.perr_user_cancel, 0x06 to R.string.perr_data,
    0x07 to R.string.perr_overheat, 0x08 to R.string.perr_paper_out, 0x09 to R.string.perr_busy,
    0x0A to R.string.perr_no_head, 0x0B to R.string.perr_temp_low, 0x0C to R.string.perr_head_loose,
    0x0D to R.string.perr_no_ribbon, 0x0E to R.string.perr_wrong_ribbon, 0x0F to R.string.perr_used_ribbon,
    0x10 to R.string.perr_wrong_paper, 0x16 to R.string.perr_communication, 0x32 to R.string.perr_illegal_page,
    0x34 to R.string.perr_timeout, 0x35 to R.string.perr_non_dedicated_ribbon,
)

/** Message for a failed print/connection, localized where the printer reported a known code. */
fun errorText(ctx: Context, t: Throwable): String {
    if (t is PrinterException && t.code >= 0) {
        val name = printerErrorRes[t.code]?.let(ctx::getString) ?: io.github.tobsef.niimstudio.protocol.PrinterError.name(t.code)
        return ctx.getString(R.string.printer_error, t.code, name)
    }
    return t.message ?: t.javaClass.simpleName
}

private var defaultProfileNames: Map<String, Set<String>>? = null

/**
 * Name to show for a profile: built-in profiles that were not renamed follow the app
 * language, user-defined names are shown unchanged.
 */
fun profileName(p: io.github.tobsef.niimstudio.model.LabelProfile): String {
    val names = defaultProfileNames ?: io.github.tobsef.niimstudio.L10n.available.flatMap { lang ->
        val base = io.github.tobsef.niimstudio.data.Store.appContext
        val c = base.createConfigurationContext(
            android.content.res.Configuration(base.resources.configuration).apply { setLocale(java.util.Locale.forLanguageTag(lang)) },
        )
        io.github.tobsef.niimstudio.model.LabelProfile.defaults(
            c.getString(R.string.profile_white), c.getString(R.string.profile_cable), c.getString(R.string.profile_continuous),
        ) + io.github.tobsef.niimstudio.model.LabelProfile.defaults()
    }.groupBy({ it.id }, { it.name }).mapValues { it.value.toSet() }.also { defaultProfileNames = it }
    if (p.name !in names[p.id].orEmpty()) return p.name
    return io.github.tobsef.niimstudio.data.Store.localizedDefaults().firstOrNull { it.id == p.id }?.name ?: p.name
}
