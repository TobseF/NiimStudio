package dev.b18print.protocol

import kotlinx.coroutines.delay

/**
 * Packet sequences for printing one image. The official SDK ships several per-model
 * "print tasks" (for B18: B18V3PrintTask and B18NewPrintTask). Their exact byte layout
 * can't be read from the packed APK, so we offer the three known families and
 * let the user pick until the right one is confirmed on hardware.
 */
enum class PrintVariant(val label: String) {
    /** PrintStart 7 bytes, SetPageSize 6 bytes (rows, cols, copies). Used by B1-family v3 printers. */
    V3("V3 / B1-style (start 7b, size 6b)"),

    /** PrintStart 1 byte, PrintClear, SetPageSize 4 bytes, PrintQuantity. Used by D110 / older printers. */
    LEGACY("Legacy / D110-style (start 1b, size 4b + qty)"),

    /** PrintStart 9 bytes, SetPageSize 13 bytes, no PageStart. Used by protocol v4/v5 printers. */
    V4("V4 / new-style (start 9b, size 13b)"),
}

data class PrintOptions(
    val density: Int = 2,
    val labelType: Int = LabelType.WITH_GAPS,
    val copies: Int = 1,
    val variant: PrintVariant = PrintVariant.V3,
    val printheadPixels: Int = 96,
    val useIndexedRows: Boolean = true,
    val statusTimeoutMs: Long = 60_000,
)

class PrintJob(
    private val client: NiimbotClient,
    private val log: (String) -> Unit,
    private val progress: (String) -> Unit,
) {
    suspend fun print(img: EncodedImage, o: PrintOptions) {
        val rows = ImageEncoder.rowPackets(img, o.printheadPixels, o.useIndexedRows)
        log("Print: ${img.cols}x${img.rows} px, ${rows.size} row packets, copies=${o.copies}, variant=${o.variant}")
        var ok = false
        try {
            when (o.variant) {
                PrintVariant.V3 -> {
                    check("SetDensity", client.setDensity(o.density))
                    check("SetLabelType", client.setLabelType(o.labelType))
                    check("PrintStart", client.printStart(u16(o.copies) + byteArrayOf(0, 0, 0, 0, 0)))
                    check("PageStart", client.pageStart())
                    check("SetPageSize", client.setPageSize(u16(img.rows) + u16(img.cols) + u16(o.copies)))
                }
                PrintVariant.LEGACY -> {
                    check("SetDensity", client.setDensity(o.density))
                    check("SetLabelType", client.setLabelType(o.labelType))
                    check("PrintStart", client.printStart(byteArrayOf(1)))
                    soft("PrintClear") { client.printClear() }
                    check("PageStart", client.pageStart())
                    check("SetPageSize", client.setPageSize(u16(img.rows) + u16(img.cols)))
                    check("PrintQuantity", client.setQuantity(o.copies))
                }
                PrintVariant.V4 -> {
                    check("SetLabelType", client.setLabelType(o.labelType))
                    check("SetDensity", client.setDensity(o.density))
                    // pages, 4 x 0, color=0 (single), speed=0, flag=0
                    check("PrintStart", client.printStart(u16(o.copies) + byteArrayOf(0, 0, 0, 0, 0, 0, 0)))
                    // rows, cols, copies, cutHeight=0, cutType=0, 0, sendAll=0, partHeight=0
                    check(
                        "SetPageSize",
                        client.setPageSize(u16(img.rows) + u16(img.cols) + u16(o.copies) + u16(0) + byteArrayOf(0, 0, 0) + u16(0)),
                    )
                }
            }

            progress("Sending image…")
            rows.forEachIndexed { i, p ->
                client.sendOneWay(p)
                if (i % 20 == 0) progress("Sending rows ${i + 1}/${rows.size}")
            }
            progress("Rows sent")

            check("PageEnd", client.pageEnd())
            waitForFinished(o)
            ok = true
        } finally {
            try {
                val ended = client.printEnd()
                log("PrintEnd -> $ended")
            } catch (e: Exception) {
                log("PrintEnd failed: ${e.message}")
            }
            progress(if (ok) "Done" else "Failed")
        }
    }

    private suspend fun waitForFinished(o: PrintOptions) {
        val deadline = System.currentTimeMillis() + o.statusTimeoutMs
        var failures = 0
        while (System.currentTimeMillis() < deadline) {
            delay(300)
            try {
                val s = client.printStatus()
                progress("Printing: page ${s.page}/${o.copies}  print ${s.printProgress}%  feed ${s.feedProgress}%")
                if (s.error != 0) throw PrinterException("Print error ${s.error}: ${PrinterError.name(s.error)}", s.error)
                if (s.page >= o.copies) return
                failures = 0
            } catch (e: PrinterException) {
                if (e.code >= 0) throw e
                // A thermal-transfer print can keep the printer silent for a moment; tolerate a few timeouts.
                if (++failures >= 5) throw e
                log("PrintStatus: ${e.message} (retry $failures)")
            }
        }
        throw PrinterException("Timed out waiting for print to finish")
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
