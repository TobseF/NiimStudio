package io.github.tobsef.niimstudio.protocol

import kotlinx.coroutines.delay

/**
 * Packet sequences for printing. The official SDK ships several per-model
 * "print tasks" (for B18: B18V3PrintTask and B18NewPrintTask). Their exact byte layout
 * can't be read from the packed APK, so the three known families are selectable.
 */
enum class PrintVariant {
    /** PrintStart 7 bytes, SetPageSize 6 bytes (rows, cols, copies). Used by B1-family v3 printers. */
    V3,

    /** PrintStart 1 byte, PrintClear, SetPageSize 4 bytes, PrintQuantity. Used by D110 / older printers. */
    LEGACY,

    /** PrintStart 9 bytes, SetPageSize 13 bytes, no PageStart. Used by protocol v4/v5 printers. */
    V4,
}

data class PrintOptions(
    val density: Int = 2,
    val labelType: Int = LabelType.WITH_GAPS,
    /** Copies of each page. */
    val copies: Int = 1,
    val variant: PrintVariant = PrintVariant.V3,
    val printheadPixels: Int = 96,
    val useIndexedRows: Boolean = true,
    val statusTimeoutMs: Long = 60_000,
)

enum class PrintStage { STARTING, PREPARING, SENDING, PRINTING, DONE, CANCELLED }

/**
 * Print state for the UI (texts are localized there).
 * [label]/[labels]: label being sent (SENDING) or printed (PRINTING); [percent] of the current label.
 */
data class PrintProgress(
    val done: Int,
    val total: Int,
    val stage: PrintStage,
    val fraction: Float,
    val label: Int = 0,
    val labels: Int = 0,
    val percent: Int = 0,
)

class PrintJob(
    private val client: NiimbotClient,
    private val log: (String) -> Unit,
    private val progress: (PrintProgress) -> Unit,
) {
    /** Prints [pages] (distinct images), each [PrintOptions.copies] times. */
    suspend fun print(pages: List<EncodedImage>, o: PrintOptions) {
        require(pages.isNotEmpty())
        val total = pages.size * o.copies
        log("Print: ${pages.size} page(s) × ${o.copies}, variant=${o.variant}, ${pages[0].cols}x${pages[0].rows} px")
        var ok = false
        var done = 0
        fun report(stage: PrintStage, sub: Float = 0f, label: Int = 0, labels: Int = 0) =
            progress(PrintProgress(done, total, stage, ((done + sub) / total).coerceIn(0f, 1f), label, labels))
        try {
            report(PrintStage.PREPARING)
            when (o.variant) {
                PrintVariant.V3 -> {
                    check("SetDensity", client.setDensity(o.density))
                    check("SetLabelType", client.setLabelType(o.labelType))
                    check("PrintStart", client.printStart(u16(total) + byteArrayOf(0, 0, 0, 0, 0)))
                }
                PrintVariant.LEGACY -> {
                    check("SetDensity", client.setDensity(o.density))
                    check("SetLabelType", client.setLabelType(o.labelType))
                    check("PrintStart", client.printStart(byteArrayOf(1)))
                }
                PrintVariant.V4 -> {
                    check("SetLabelType", client.setLabelType(o.labelType))
                    check("SetDensity", client.setDensity(o.density))
                    // pages, 4 x 0, color=0 (single), speed=0, flag=0
                    check("PrintStart", client.printStart(u16(total) + byteArrayOf(0, 0, 0, 0, 0, 0, 0)))
                }
            }

            pages.forEachIndexed { index, img ->
                val rows = ImageEncoder.rowPackets(img, o.printheadPixels, o.useIndexedRows)
                when (o.variant) {
                    PrintVariant.V3 -> {
                        check("PageStart", client.pageStart())
                        check("SetPageSize", client.setPageSize(u16(img.rows) + u16(img.cols) + u16(o.copies)))
                    }
                    PrintVariant.LEGACY -> {
                        soft("PrintClear") { client.printClear() }
                        check("PageStart", client.pageStart())
                        check("SetPageSize", client.setPageSize(u16(img.rows) + u16(img.cols)))
                        check("PrintQuantity", client.setQuantity(o.copies))
                    }
                    PrintVariant.V4 -> check(
                        "SetPageSize",
                        client.setPageSize(u16(img.rows) + u16(img.cols) + u16(o.copies) + u16(0) + byteArrayOf(0, 0, 0) + u16(0)),
                    )
                }
                rows.forEachIndexed { i, p ->
                    client.sendOneWay(p)
                    if (i % 16 == 0) report(PrintStage.SENDING, 0.5f * i / rows.size, index + 1, pages.size)
                }
                check("PageEnd", client.pageEnd())
                done += o.copies
                waitForPages(done, total, o)
            }
            ok = true
        } finally {
            try {
                log("PrintEnd -> ${client.printEnd()}")
            } catch (e: Exception) {
                log("PrintEnd failed: ${e.message}")
            }
            progress(PrintProgress(done, total, if (ok) PrintStage.DONE else PrintStage.CANCELLED, if (ok) 1f else done.toFloat() / total))
        }
    }

    /** Polls PrintStatus until the printer reports [target] printed pages. */
    private suspend fun waitForPages(target: Int, total: Int, o: PrintOptions) {
        val deadline = System.currentTimeMillis() + o.statusTimeoutMs
        var failures = 0
        while (System.currentTimeMillis() < deadline) {
            delay(300)
            try {
                val s = client.printStatus()
                progress(
                    PrintProgress(
                        s.page, total, PrintStage.PRINTING,
                        ((s.page + s.printProgress / 100f) / total).coerceIn(0f, 1f),
                        label = minOf(s.page + 1, total), labels = total, percent = s.printProgress,
                    ),
                )
                if (s.error != 0) throw PrinterException("Print error ${s.error}: ${PrinterError.name(s.error)}", s.error)
                if (s.page >= target) return
                failures = 0
            } catch (e: PrinterException) {
                if (e.code >= 0) throw e
                // A thermal-transfer print can keep the printer silent for a moment; tolerate a few timeouts.
                if (++failures >= 5) throw e
                log("PrintStatus: ${e.message} (retry $failures)")
            }
        }
        throw PrinterException("Timed out waiting for the print to finish")
    }

    private fun check(step: String, ok: Boolean) {
        log("$step -> ${if (ok) "OK" else "REFUSED"}")
        if (!ok) throw PrinterException("$step refused by printer")
    }

    private suspend fun soft(step: String, block: suspend () -> Boolean) {
        try {
            log("$step -> ${block()}")
        } catch (e: Exception) {
            log("$step ignored: ${e.message}")
        }
    }
}
