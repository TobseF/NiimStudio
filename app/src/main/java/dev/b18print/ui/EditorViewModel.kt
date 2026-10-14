package dev.b18print.ui

import dev.b18print.L10n
import dev.b18print.R
import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.b18print.data.Store
import dev.b18print.model.BarcodeEl
import dev.b18print.model.DateEl
import dev.b18print.model.Element
import dev.b18print.model.FitMode
import dev.b18print.model.Frame
import dev.b18print.model.ImageEl
import dev.b18print.model.LabelDoc
import dev.b18print.model.LabelProfile
import dev.b18print.model.QrEl
import dev.b18print.model.ShapeEl
import dev.b18print.model.ShapeKind
import dev.b18print.model.SymbolEl
import dev.b18print.model.Template
import dev.b18print.model.TextEl
import dev.b18print.model.TextStyle
import dev.b18print.model.newId
import dev.b18print.render.MM_PER_PT
import dev.b18print.render.Renderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class AlignMode { LEFT, HCENTER, RIGHT, TOP, VCENTER, BOTTOM }

/** Bottom panel pages. */
enum class PanelTab(@androidx.annotation.StringRes val title: Int) {
    ADD(R.string.tab_add), TEMPLATES(R.string.tab_templates), DATA(R.string.tab_data),
    CONTENT(R.string.tab_content), STYLE(R.string.tab_style), FONTS(R.string.tab_fonts), ARRANGE(R.string.tab_arrange), SYMBOL(R.string.tab_symbol),
}

class EditorViewModel(app: Application) : AndroidViewModel(app) {

    var doc by mutableStateOf(initialDoc())
        private set

    var selectedId by mutableStateOf<String?>(null)
        private set

    var tab by mutableStateOf(PanelTab.ADD)
    var panelExpanded by mutableStateOf(true)

    /** Symbol picker adds a new symbol (true) or replaces the selected one (false). */
    var symbolPickerForNew by mutableStateOf(true)

    /** Data row shown in the editor and print preview. */
    var previewRow by mutableIntStateOf(0)

    /** Increments to ask the UI to open the text editor for the selection. */
    var editRequest by mutableIntStateOf(0)

    private val undoStack = ArrayDeque<LabelDoc>()
    private val redoStack = ArrayDeque<LabelDoc>()
    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set
    private var lastMergeKey: String? = null
    private var lastMergeTime = 0L
    private var draftJob: Job? = null

    val selected: Element? get() = doc.elements.firstOrNull { it.id == selectedId }

    private fun initialDoc(): LabelDoc {
        Store.loadDraft()?.let { return it }
        val p = Store.profile(Store.settings.value.lastProfileId) ?: Store.profiles.value.first()
        return sampleDoc(p)
    }

    private fun sampleDoc(p: LabelProfile): LabelDoc {
        val d = LabelDoc(profile = p)
        val text = L10n.str(R.string.sample_text)
        val w = d.widthMm - 4f
        // Largest size that fits the label height.
        var st = TextStyle(sizePt = 30f)
        while (st.sizePt > 4f && Renderer.textHeightMm(text, st, w) > d.heightMm - 1.5f) st = st.copy(sizePt = st.sizePt - 0.5f)
        val h = Renderer.textHeightMm(text, st, w)
        return d.copy(elements = listOf(TextEl(newId(), Frame(2f, (d.heightMm - h) / 2, w, h), text = text, style = st)))
    }

    // ------------------------------------------------------------------ history

    /**
     * Applies a change. Consecutive changes with the same [merge] key within 1.5 s share
     * one undo step (dragging, typing, slider moves).
     */
    fun update(newDoc: LabelDoc, merge: String? = null) {
        if (newDoc == doc) return
        val now = System.currentTimeMillis()
        val merged = merge != null && merge == lastMergeKey && now - lastMergeTime < 1500
        if (!merged) {
            undoStack.addLast(doc)
            if (undoStack.size > 100) undoStack.removeFirst()
        }
        lastMergeKey = merge
        lastMergeTime = now
        redoStack.clear()
        doc = newDoc
        syncHistoryFlags()
        scheduleDraftSave()
    }

    fun undo() {
        val prev = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(doc)
        doc = prev
        lastMergeKey = null
        afterHistoryJump()
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(doc)
        doc = next
        lastMergeKey = null
        afterHistoryJump()
    }

    private fun afterHistoryJump() {
        if (selected == null) select(null)
        syncHistoryFlags()
        scheduleDraftSave()
    }

    private fun syncHistoryFlags() {
        canUndo = undoStack.isNotEmpty()
        canRedo = redoStack.isNotEmpty()
    }

    private fun scheduleDraftSave() {
        draftJob?.cancel()
        val d = doc
        draftJob = viewModelScope.launch(Dispatchers.IO) {
            delay(600)
            Store.saveDraft(d)
        }
    }

    // ------------------------------------------------------------------ selection

    fun select(id: String?) {
        val changed = id != selectedId
        selectedId = id
        if (!changed) return
        tab = when (selected) {
            null -> if (tab in listOf(PanelTab.ADD, PanelTab.TEMPLATES, PanelTab.DATA)) tab else PanelTab.ADD
            is TextEl, is DateEl -> if (tab in listOf(PanelTab.CONTENT, PanelTab.STYLE, PanelTab.FONTS, PanelTab.ARRANGE)) tab else PanelTab.STYLE
            else -> if (tab == PanelTab.ARRANGE) tab else PanelTab.CONTENT
        }
    }

    fun tabsFor(e: Element?): List<PanelTab> = when (e) {
        null -> if (tab == PanelTab.SYMBOL) listOf(PanelTab.SYMBOL) else listOf(PanelTab.ADD, PanelTab.TEMPLATES, PanelTab.DATA)
        is TextEl, is DateEl -> listOf(PanelTab.CONTENT, PanelTab.STYLE, PanelTab.FONTS, PanelTab.ARRANGE)
        is SymbolEl -> if (tab == PanelTab.SYMBOL) listOf(PanelTab.SYMBOL) else listOf(PanelTab.CONTENT, PanelTab.ARRANGE)
        else -> listOf(PanelTab.CONTENT, PanelTab.ARRANGE)
    }

    // ------------------------------------------------------------------ element edits

    fun updateElement(id: String, merge: String? = null, transform: (Element) -> Element) {
        val list = doc.elements.map { if (it.id == id) normalize(transform(it)) else it }
        update(doc.copy(elements = list), merge)
    }

    fun updateSelected(merge: String? = null, transform: (Element) -> Element) {
        selectedId?.let { updateElement(it, merge, transform) }
    }

    /** Keeps auto-height text boxes sized to their content and codes on whole print dots. */
    private fun normalize(e: Element): Element = when (e) {
        is TextEl -> if (e.style.fit == FitMode.AUTO_HEIGHT) {
            e.copy(frame = e.frame.copy(h = Renderer.textHeightMm(e.text, e.style, e.frame.w)))
        } else e
        is DateEl -> if (e.style.fit == FitMode.AUTO_HEIGHT) {
            e.copy(frame = e.frame.copy(h = Renderer.textHeightMm(Renderer.dateText(e, java.util.Date()), e.style, e.frame.w)))
        } else e
        is BarcodeEl, is QrEl -> e.withFrame(e.frame.copy(x = snapDot(e.frame.x), y = snapDot(e.frame.y)))
        else -> e
    }

    private fun snapDot(v: Float) = Math.round(v * 8f) / 8f

    fun add(e: Element) {
        val n = normalize(e)
        update(doc.copy(elements = doc.elements + n))
        select(n.id)
    }

    private fun centered(w: Float, h: Float) = Frame((doc.widthMm - w) / 2, (doc.heightMm - h) / 2, w, h)

    private fun defaultTextStyle() = TextStyle(sizePt = ((doc.heightMm * 0.35f) / MM_PER_PT).coerceIn(4f, 40f))

    fun addText(text: String = L10n.str(R.string.default_text)) {
        val st = defaultTextStyle()
        val w = (doc.widthMm - 4f).coerceAtLeast(5f)
        val h = Renderer.textHeightMm(text, st, w)
        add(TextEl(newId(), centered(w, h), text = text, style = st))
        tab = PanelTab.CONTENT
    }

    fun addDate() {
        val st = defaultTextStyle()
        val w = (doc.widthMm * 0.7f).coerceAtLeast(5f)
        val pattern = L10n.context().resources.getStringArray(R.array.date_presets).first()
        add(DateEl(newId(), centered(w, 4f), pattern = pattern, style = st))
        tab = PanelTab.CONTENT
    }

    fun addSerial() {
        if (doc.data.mode == dev.b18print.model.DataMode.NONE) {
            update(doc.copy(data = doc.data.copy(mode = dev.b18print.model.DataMode.COUNTER)))
        }
        addText(L10n.str(R.string.default_serial))
    }

    fun addImage(file: String) {
        val src = dev.b18print.render.Images.source(file)
        val maxW = doc.widthMm * 0.8f
        val maxH = doc.heightMm * 0.9f
        var w = maxW
        var h = maxH
        if (src != null) {
            val s = minOf(maxW / src.width, maxH / src.height)
            w = src.width * s
            h = src.height * s
        }
        add(ImageEl(newId(), centered(w, h), file = file))
    }

    fun addOrReplaceSymbol(key: String) {
        val sel = selected
        if (!symbolPickerForNew && sel is SymbolEl) {
            updateElement(sel.id) { (it as SymbolEl).copy(key = key) }
            return
        }
        val s = minOf(doc.heightMm, doc.widthMm) * 0.8f
        add(SymbolEl(newId(), centered(s, s), key = key))
        tab = PanelTab.SYMBOL
    }

    fun addBarcode() {
        val w = (doc.widthMm * 0.8f).coerceAtMost(60f)
        val h = doc.heightMm * 0.8f
        add(BarcodeEl(newId(), centered(w, h), content = "123456789012"))
    }

    fun addQr() {
        val s = minOf(doc.heightMm, doc.widthMm) * 0.9f
        add(QrEl(newId(), centered(s, s), content = "https://example.com"))
    }

    fun addShape(kind: ShapeKind) {
        val f = when (kind) {
            ShapeKind.LINE_H -> centered(doc.widthMm * 0.8f, 1f)
            ShapeKind.LINE_V -> centered(1f, doc.heightMm * 0.8f)
            else -> centered(doc.widthMm * 0.5f, doc.heightMm * 0.7f)
        }
        add(ShapeEl(newId(), f, shape = kind))
    }

    fun delete(id: String) {
        update(doc.copy(elements = doc.elements.filterNot { it.id == id }))
        if (selectedId == id) select(null)
    }

    fun duplicate(id: String) {
        val e = doc.elements.firstOrNull { it.id == id } ?: return
        val copy = e.withId(newId()).withLocked(false).withFrame(e.frame.copy(x = e.frame.x + 1.5f, y = e.frame.y + 1.5f))
        update(doc.copy(elements = doc.elements + copy))
        select(copy.id)
    }

    fun rotate90(id: String) = updateElement(id) { it.withFrame(it.frame.copy(rotation = (it.frame.rotation + 90f) % 360f)) }

    fun toggleLock(id: String) = updateElement(id) { it.withLocked(!it.locked) }

    fun toggleMirror(id: String) = updateElement(id) { it.withFrame(it.frame.copy(mirror = !it.frame.mirror)) }

    fun bringToFront(id: String) {
        val e = doc.elements.firstOrNull { it.id == id } ?: return
        update(doc.copy(elements = doc.elements.filterNot { it.id == id } + e))
    }

    fun sendToBack(id: String) {
        val e = doc.elements.firstOrNull { it.id == id } ?: return
        update(doc.copy(elements = listOf(e) + doc.elements.filterNot { it.id == id }))
    }

    /** Aligns the element's visual bounds to the label edges or centre lines. */
    fun align(id: String, mode: AlignMode) = updateElement(id) { e ->
        val f = e.frame
        val (bw, bh) = f.boundsSize()
        val cx = when (mode) {
            AlignMode.LEFT -> bw / 2
            AlignMode.HCENTER -> doc.widthMm / 2
            AlignMode.RIGHT -> doc.widthMm - bw / 2
            else -> f.cx
        }
        val cy = when (mode) {
            AlignMode.TOP -> bh / 2
            AlignMode.VCENTER -> doc.heightMm / 2
            AlignMode.BOTTOM -> doc.heightMm - bh / 2
            else -> f.cy
        }
        e.withFrame(f.withCenter(cx, cy))
    }

    fun nudge(id: String, dx: Float, dy: Float) = updateElement(id, merge = "nudge-$id") { e ->
        if (e.locked) e else e.withFrame(e.frame.copy(x = e.frame.x + dx, y = e.frame.y + dy))
    }

    // ------------------------------------------------------------------ document

    fun setProfile(p: LabelProfile) {
        Store.updateSettings { it.copy(lastProfileId = p.id) }
        update(doc.copy(profile = p))
    }

    fun togglePortrait() {
        // Rotate the layout with the label: map each element centre and turn it by 90°.
        val old = doc
        val nd = old.copy(portrait = !old.portrait)
        val elements = old.elements.map { e ->
            val f = e.frame
            val (ncx, ncy) = if (!old.portrait) {
                // landscape -> portrait: rotate clockwise
                (old.heightMm - f.cy) to f.cx
            } else {
                f.cy to (old.widthMm - f.cx)
            }
            val rot = (f.rotation + if (!old.portrait) 90f else 270f) % 360f
            e.withFrame(f.copy(rotation = rot).withCenter(ncx, ncy))
        }
        update(nd.copy(elements = elements))
    }

    fun clearAll() {
        update(doc.copy(elements = emptyList()))
        select(null)
    }

    fun newLabel() {
        update(LabelDoc(profile = doc.profile))
        select(null)
        tab = PanelTab.ADD
    }

    fun updateData(transform: (dev.b18print.model.DataSource) -> dev.b18print.model.DataSource) {
        update(doc.copy(data = transform(doc.data)), merge = "data")
        previewRow = previewRow.coerceIn(0, dev.b18print.render.DataVars.count(doc.data) - 1)
    }

    // ------------------------------------------------------------------ templates

    fun loadTemplate(t: Template) {
        update(t.doc.copy(templateId = t.id, templateName = t.name))
        select(null)
        previewRow = 0
    }

    fun saveTemplate(name: String, asNew: Boolean) {
        val id = if (asNew || doc.templateId == null) newId() else doc.templateId!!
        val d = doc.copy(templateId = id, templateName = name)
        Store.saveTemplate(Template(id = id, name = name, doc = d))
        doc = d
        scheduleDraftSave()
    }
}
