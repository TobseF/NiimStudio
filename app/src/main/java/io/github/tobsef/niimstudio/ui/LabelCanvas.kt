package io.github.tobsef.niimstudio.ui

import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.runtime.produceState
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.ui.res.stringResource
import io.github.tobsef.niimstudio.R
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Rotate90DegreesCw
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.tobsef.niimstudio.data.Store
import io.github.tobsef.niimstudio.model.DateEl
import io.github.tobsef.niimstudio.model.Element
import io.github.tobsef.niimstudio.model.FitMode
import io.github.tobsef.niimstudio.model.Frame
import io.github.tobsef.niimstudio.model.LabelDoc
import io.github.tobsef.niimstudio.model.TextEl
import io.github.tobsef.niimstudio.render.DataVars
import io.github.tobsef.niimstudio.render.RenderCtx
import io.github.tobsef.niimstudio.render.Renderer
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private enum class Handle { RIGHT, BOTTOM, CORNER }

private data class Guide(val vertical: Boolean, val mm: Float)

private fun rad(deg: Float) = Math.toRadians(deg.toDouble()).toFloat()

/** Element-local point (origin top-left of the unrotated box) to label millimetres. */
private fun Frame.toWorld(lx: Float, ly: Float): Offset {
    val dx = lx - w / 2
    val dy = ly - h / 2
    val r = rad(rotation)
    return Offset(cx + dx * cos(r) - dy * sin(r), cy + dx * sin(r) + dy * cos(r))
}

private fun Frame.toLocal(p: Offset): Offset {
    val dx = p.x - cx
    val dy = p.y - cy
    val r = -rad(rotation)
    return Offset(dx * cos(r) - dy * sin(r) + w / 2, dx * sin(r) + dy * cos(r) + h / 2)
}

private fun rotateVec(v: Offset, deg: Float): Offset {
    val r = rad(deg)
    return Offset(v.x * cos(r) - v.y * sin(r), v.x * sin(r) + v.y * cos(r))
}

@Composable
fun LabelCanvas(vm: EditorViewModel, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    var size by remember { mutableStateOf(IntSize.Zero) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var dragging by remember { mutableStateOf(false) }
    var guides by remember { mutableStateOf<List<Guide>>(emptyList()) }
    val settings by Store.settings.collectAsState()

    val doc = vm.doc
    LaunchedEffect(doc.widthMm, doc.heightMm) {
        zoom = 1f
        pan = Offset.Zero
    }

    /** Space at the bottom for the whole-label preview of flag/wrap layouts. */
    fun reserve(d: LabelDoc): Float = if (d.sides != io.github.tobsef.niimstudio.model.SideMode.SINGLE) with(density) { 92.dp.toPx() } else 0f

    fun scale(d: LabelDoc = vm.doc): Float {
        val pad = with(density) { 28.dp.toPx() }
        val s = min((size.width - 2 * pad) / d.widthMm, (size.height - reserve(d) - 2 * pad) / d.heightMm)
        return max(s, 1f) * zoom
    }

    fun base(s: Float, d: LabelDoc = vm.doc) =
        Offset((size.width - d.widthMm * s) / 2f, (size.height - reserve(d) - d.heightMm * s) / 2f)
    fun origin(s: Float) = base(s) + pan
    fun toMm(p: Offset): Offset {
        val s = scale()
        return (p - origin(s)) / s
    }
    fun toScreen(mm: Offset): Offset {
        val s = scale()
        return origin(s) + mm * s
    }

    fun hitElement(mm: Offset): Element? {
        val tol = with(density) { 6.dp.toPx() } / scale()
        return vm.doc.elements.lastOrNull { e ->
            val l = e.frame.toLocal(mm)
            l.x >= -tol && l.y >= -tol && l.x <= e.frame.w + tol && l.y <= e.frame.h + tol
        }
    }

    fun handlePos(f: Frame, h: Handle) = when (h) {
        Handle.RIGHT -> f.toWorld(f.w, f.h / 2)
        Handle.BOTTOM -> f.toWorld(f.w / 2, f.h)
        Handle.CORNER -> f.toWorld(f.w, f.h)
    }

    fun hitHandle(e: Element, screen: Offset): Handle? {
        if (e.locked) return null
        val r = with(density) { 20.dp.toPx() }
        return Handle.entries.sortedBy { if (it == Handle.CORNER) 0 else 1 }
            .firstOrNull { (toScreen(handlePos(e.frame, it)) - screen).getDistance() <= r }
    }

    /** Snaps the frame's visual bounds to label edges/centre and other elements. */
    fun snap(f: Frame, selfId: String): Pair<Frame, List<Guide>> {
        if (!Store.settings.value.snapping) return f to emptyList()
        val d = vm.doc
        val tol = with(density) { 8.dp.toPx() } / scale()
        val (bw, bh) = f.boundsSize()
        val xs = mutableListOf(0f, d.widthMm / 2, d.widthMm)
        val ys = mutableListOf(0f, d.heightMm / 2, d.heightMm)
        for (o in d.elements) {
            if (o.id == selfId) continue
            val (ow, oh) = o.frame.boundsSize()
            xs += listOf(o.frame.cx - ow / 2, o.frame.cx, o.frame.cx + ow / 2)
            ys += listOf(o.frame.cy - oh / 2, o.frame.cy, o.frame.cy + oh / 2)
        }
        fun best(center: Float, half: Float, cands: List<Float>): Pair<Float, Float>? {
            var bestShift: Float? = null
            var at = 0f
            for (ref in listOf(center - half, center, center + half)) for (c in cands) {
                val diff = c - ref
                if (abs(diff) < tol && (bestShift == null || abs(diff) < abs(bestShift))) {
                    bestShift = diff
                    at = c
                }
            }
            return bestShift?.let { it to at }
        }
        var cx = f.cx
        var cy = f.cy
        val g = mutableListOf<Guide>()
        best(cx, bw / 2, xs)?.let { (shift, at) -> cx += shift; g += Guide(true, at) }
        best(cy, bh / 2, ys)?.let { (shift, at) -> cy += shift; g += Guide(false, at) }
        return f.withCenter(cx, cy) to g
    }

    Box(
        modifier
            .background(AppColors.CanvasBg)
            .testTag("canvas")
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                var lastTapTime = 0L
                var lastTapPos = Offset.Zero
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = true) // taps handled by the floating bar are ignored here
                    val startMm = toMm(down.position)
                    val sel = vm.selected
                    val handle = sel?.let { hitHandle(it, down.position) }
                    val target: Element? = if (handle != null) sel else hitElement(startMm)
                    val startEl = target
                    val gestureKey = "g${down.uptimeMillis}"
                    var moved = false
                    var pinching = false
                    val slop = viewConfiguration.touchSlop

                    while (true) {
                        val ev = awaitPointerEvent()
                        val pressed = ev.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break
                        if (pressed.size >= 2) {
                            pinching = true
                            moved = true
                            dragging = false
                            guides = emptyList()
                            val zc = ev.calculateZoom()
                            val pc = ev.calculatePan()
                            val centroid = ev.calculateCentroid()
                            val s0 = scale()
                            val mmC = (centroid - origin(s0)) / s0
                            zoom = (zoom * zc).coerceIn(0.5f, 8f)
                            val s1 = scale()
                            pan = centroid - mmC * s1 - base(s1) + pc
                            ev.changes.forEach { it.consume() }
                            continue
                        }
                        if (pinching) continue
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        val total = ch.position - down.position
                        if (!moved && total.getDistance() > slop) {
                            moved = true
                            if (startEl != null && handle == null && vm.selectedId != startEl.id) vm.select(startEl.id)
                        }
                        if (!moved) continue
                        ch.consume()
                        val curMm = toMm(ch.position)
                        val dMm = curMm - startMm
                        when {
                            startEl == null || startEl.locked -> {
                                pan += ch.positionChange()
                            }
                            handle != null -> {
                                dragging = true
                                val f0 = startEl.frame
                                val dl = rotateVec(dMm, -f0.rotation)
                                val minS = 1f
                                var sFactor = 1f
                                val (w1, h1) = when (handle) {
                                    Handle.RIGHT -> max(minS, f0.w + dl.x) to f0.h
                                    Handle.BOTTOM -> f0.w to max(minS, f0.h + dl.y)
                                    Handle.CORNER -> if (startEl.keepsAspect) {
                                        sFactor = ((f0.w + dl.x) * f0.w + (f0.h + dl.y) * f0.h) / (f0.w * f0.w + f0.h * f0.h)
                                        sFactor = max(sFactor, minS / min(f0.w, f0.h))
                                        f0.w * sFactor to f0.h * sFactor
                                    } else {
                                        max(minS, f0.w + dl.x) to max(minS, f0.h + dl.y)
                                    }
                                }
                                val tl = f0.toWorld(0f, 0f)
                                val c = tl + rotateVec(Offset(w1 / 2, h1 / 2), f0.rotation)
                                val nf = f0.copy(w = w1, h = h1).withCenter(c.x, c.y)
                                vm.updateElement(startEl.id, merge = gestureKey) {
                                    resized(startEl, nf, handle, sFactor)
                                }
                            }
                            else -> {
                                dragging = true
                                val f0 = startEl.frame
                                val (nf, g) = snap(f0.copy(x = f0.x + dMm.x, y = f0.y + dMm.y), startEl.id)
                                guides = g
                                vm.updateElement(startEl.id, merge = gestureKey) { it.withFrame(nf) }
                            }
                        }
                    }
                    dragging = false
                    guides = emptyList()

                    if (!moved) {
                        val now = down.uptimeMillis
                        val isDouble = now - lastTapTime < 320 && (down.position - lastTapPos).getDistance() < 60f
                        lastTapTime = now
                        lastTapPos = down.position
                        if (target == null) {
                            vm.select(null)
                            if (isDouble) {
                                zoom = 1f
                                pan = Offset.Zero
                            }
                        } else {
                            vm.select(target.id)
                            if (isDouble) openEditor(vm, target)
                        }
                    }
                }
            },
    ) {
        val rc = RenderCtx(vars = DataVars.vars(doc.data, vm.previewRow))
        Canvas(Modifier.fillMaxSize()) {
            if (size.width == 0) return@Canvas
            val s = scale(doc)
            val o = origin(s)
            val lw = doc.widthMm * s
            val lh = doc.heightMm * s
            val corner = CornerRadius(doc.profile.cornerMm * s)
            drawRoundRect(Color(0x12000000), o + Offset(0f, 3.dp.toPx()), Size(lw, lh), corner)
            drawRoundRect(Color.White, o, Size(lw, lh), corner)

            // Area outside the print head (e.g. 14 mm tape on the 12 mm head).
            val printable = settings.printheadDots / 8f
            val tape = doc.profile.tapeWidthMm
            if (tape > printable + 0.1f) {
                val m = (tape - printable) / 2 * s
                val shade = Color(0x0E000000)
                if (!doc.portrait) {
                    drawRect(shade, o, Size(lw, m))
                    drawRect(shade, Offset(o.x, o.y + lh - m), Size(lw, m))
                } else {
                    drawRect(shade, o, Size(m, lh))
                    drawRect(shade, Offset(o.x + lw - m, o.y), Size(m, lh))
                }
            }

            clipRect(o.x, o.y, o.x + lw, o.y + lh) {
                drawIntoCanvas { c ->
                    val nc = c.nativeCanvas
                    nc.save()
                    nc.translate(o.x, o.y)
                    nc.scale(s, s)
                    Renderer.drawElements(nc, doc, rc)
                    nc.restore()
                }
            }

            // Snap guides
            val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
            for (g in guides) {
                if (g.vertical) {
                    val x = o.x + g.mm * s
                    drawLine(AppColors.Accent, Offset(x, o.y - 40f), Offset(x, o.y + lh + 40f), 2f, pathEffect = dash)
                } else {
                    val y = o.y + g.mm * s
                    drawLine(AppColors.Accent, Offset(o.x - 40f, y), Offset(o.x + lw + 40f, y), 2f, pathEffect = dash)
                }
            }

            // Selection
            val sel = doc.elements.firstOrNull { it.id == vm.selectedId }
            if (sel != null) {
                val f = sel.frame
                val pts = listOf(f.toWorld(0f, 0f), f.toWorld(f.w, 0f), f.toWorld(f.w, f.h), f.toWorld(0f, f.h)).map { o + it * s }
                val path = Path().apply {
                    moveTo(pts[0].x, pts[0].y)
                    pts.drop(1).forEach { lineTo(it.x, it.y) }
                    close()
                }
                drawPath(
                    path, AppColors.Selection,
                    style = Stroke(1.5.dp.toPx(), pathEffect = if (sel.locked) PathEffect.dashPathEffect(floatArrayOf(12f, 8f)) else null),
                )
                if (!sel.locked && !dragging) {
                    val r = 6.dp.toPx()
                    for (h in listOf(Handle.RIGHT, Handle.BOTTOM)) {
                        val p = o + handlePos(f, h) * s
                        drawCircle(Color.White, r + 2.dp.toPx(), p)
                        drawCircle(AppColors.Selection, r, p)
                    }
                    val cp = o + handlePos(f, Handle.CORNER) * s
                    drawCircle(Color.White, 10.dp.toPx(), cp)
                    drawCircle(Color(0x22000000), 10.dp.toPx(), cp, style = Stroke(1f))
                    val a = 4.dp.toPx()
                    val dir = rotateVec(Offset(1f, 1f), f.rotation)
                    val n = dir / dir.getDistance()
                    drawLine(AppColors.Selection, cp - n * a, cp + n * a, 2.dp.toPx())
                    val perp = Offset(-n.y, n.x)
                    for (sign in listOf(-1f, 1f)) {
                        val tip = cp + n * a * sign
                        drawLine(AppColors.Selection, tip, tip - (n * sign + perp) * (a * 0.6f), 2.dp.toPx())
                        drawLine(AppColors.Selection, tip, tip - (n * sign - perp) * (a * 0.6f), 2.dp.toPx())
                    }
                }
            }
        }

        // Whole printed label (both panels) for flag and wrap layouts
        if (doc.sides != io.github.tobsef.niimstudio.model.SideMode.SINGLE) {
            val strip by produceState<android.graphics.Bitmap?>(null, doc, vm.previewRow) {
                value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    val sc = (900f / maxOf(doc.totalLengthMm, 1f)).coerceAtMost(12f)
                    Renderer.renderLabel(doc, RenderCtx(vars = DataVars.vars(doc.data, vm.previewRow)), sc, applyOffsets = false)
                }
            }
            strip?.let { bmp ->
                Column(
                    Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(R.string.layout_overview), fontSize = 11.sp, color = AppColors.TextGrey)
                    Image(
                        bmp.asImageBitmap(), stringResource(R.string.layout_overview),
                        Modifier
                            .padding(top = 3.dp)
                            .heightIn(max = 56.dp)
                            .widthIn(max = 320.dp)
                            .border(1.dp, AppColors.Divider),
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    )
                }
            }
        }

        // Floating action bar above the selection
        val sel = vm.selected
        if (sel != null && !dragging && size.width > 0) {
            val s = scale(doc)
            val o = origin(s)
            val f = sel.frame
            val pts = listOf(f.toWorld(0f, 0f), f.toWorld(f.w, 0f), f.toWorld(f.w, f.h), f.toWorld(0f, f.h)).map { o + it * s }
            val top = pts.minOf { it.y }
            val bottom = pts.maxOf { it.y }
            val cx = (pts.minOf { it.x } + pts.maxOf { it.x }) / 2
            var barSize by remember { mutableStateOf(IntSize(0, 0)) }
            val gap = with(density) { 12.dp.toPx() }
            val above = top - gap - barSize.height
            val y = if (above > with(density) { 4.dp.toPx() }) above else bottom + gap
            val x = (cx - barSize.width / 2).coerceIn(with(density) { 8.dp.toPx() }, max(0f, size.width - barSize.width - with(density) { 8.dp.toPx() }))
            Row(
                Modifier
                    .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                    .onSizeChanged { barSize = it }
                    // Swallow touches on the bar (also between its buttons) so the canvas never sees them.
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false).consume()
                            do {
                                val ev = awaitPointerEvent()
                                ev.changes.forEach { it.consume() }
                            } while (ev.changes.any { it.pressed })
                        }
                    }
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF111111))
                    .height(48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BarIcon(Icons.Outlined.Edit, stringResource(R.string.action_edit)) { openEditor(vm, sel) }
                BarSep()
                BarIcon(Icons.Outlined.Delete, stringResource(R.string.action_delete)) { vm.delete(sel.id) }
                BarSep()
                BarIcon(Icons.Outlined.ContentCopy, stringResource(R.string.action_duplicate)) { vm.duplicate(sel.id) }
                BarSep()
                BarIcon(Icons.Outlined.Rotate90DegreesCw, stringResource(R.string.action_rotate), enabled = !sel.locked) { vm.rotate90(sel.id) }
                BarSep()
                Text(
                    stringResource(R.string.action_align),
                    Modifier
                        .testTag("bar_align")
                        .clickable { vm.tab = PanelTab.ARRANGE; vm.panelExpanded = true }
                        .padding(horizontal = 14.dp),
                    color = Color.White,
                    fontSize = 15.sp,
                )
                BarSep()
                BarIcon(if (sel.locked) Icons.Filled.Lock else Icons.Outlined.LockOpen, stringResource(R.string.action_lock)) { vm.toggleLock(sel.id) }
            }
        }
    }
}

/** Applies a resize to element-specific properties (font size follows the corner handle). */
private fun resized(start: Element, nf: Frame, handle: Handle, s: Float): Element = when (start) {
    is TextEl -> when {
        handle == Handle.CORNER && start.style.fit != FitMode.FIT ->
            start.copy(frame = nf, style = start.style.copy(sizePt = (start.style.sizePt * s).coerceIn(2f, 300f)))
        handle == Handle.BOTTOM && start.style.fit == FitMode.AUTO_HEIGHT ->
            start.copy(frame = nf, style = start.style.copy(fit = FitMode.FIXED))
        else -> start.copy(frame = nf)
    }
    is DateEl -> when {
        handle == Handle.CORNER && start.style.fit != FitMode.FIT ->
            start.copy(frame = nf, style = start.style.copy(sizePt = (start.style.sizePt * s).coerceIn(2f, 300f)))
        handle == Handle.BOTTOM && start.style.fit == FitMode.AUTO_HEIGHT ->
            start.copy(frame = nf, style = start.style.copy(fit = FitMode.FIXED))
        else -> start.copy(frame = nf)
    }
    else -> start.withFrame(nf)
}

fun openEditor(vm: EditorViewModel, e: Element) {
    vm.select(e.id)
    vm.panelExpanded = true
    when (e) {
        is TextEl -> vm.editRequest++
        is io.github.tobsef.niimstudio.model.SymbolEl -> {
            vm.symbolPickerForNew = false
            vm.tab = PanelTab.SYMBOL
        }
        else -> vm.tab = PanelTab.CONTENT
    }
}

@Composable
private fun BarIcon(icon: ImageVector, desc: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier
            .size(52.dp, 48.dp)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, desc, tint = if (enabled) Color.White else Color(0xFF777777))
    }
}

@Composable
private fun BarSep() = Box(Modifier.size(1.dp, 48.dp).background(Color(0xFF3A3A3A)))
