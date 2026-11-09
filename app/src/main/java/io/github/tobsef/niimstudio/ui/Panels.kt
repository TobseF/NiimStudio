package io.github.tobsef.niimstudio.ui

import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import io.github.tobsef.niimstudio.R
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CropSquare
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Numbers
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.tobsef.niimstudio.data.Store
import io.github.tobsef.niimstudio.model.BarcodeEl
import io.github.tobsef.niimstudio.model.ConnectorEl
import io.github.tobsef.niimstudio.model.DataMode
import io.github.tobsef.niimstudio.model.DateEl
import io.github.tobsef.niimstudio.model.Element
import io.github.tobsef.niimstudio.model.FitMode
import io.github.tobsef.niimstudio.model.ImageEl
import io.github.tobsef.niimstudio.model.QrEl
import io.github.tobsef.niimstudio.model.ShapeEl
import io.github.tobsef.niimstudio.model.ShapeKind
import io.github.tobsef.niimstudio.model.SymbolEl
import io.github.tobsef.niimstudio.model.Template
import io.github.tobsef.niimstudio.model.TextEl
import io.github.tobsef.niimstudio.model.TextStyle
import io.github.tobsef.niimstudio.render.Barcodes
import io.github.tobsef.niimstudio.render.DataVars
import io.github.tobsef.niimstudio.render.Fonts
import io.github.tobsef.niimstudio.render.RenderCtx
import io.github.tobsef.niimstudio.render.Renderer
import io.github.tobsef.niimstudio.render.SymbolDef
import io.github.tobsef.niimstudio.render.Symbols
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date

@Composable
fun BottomPanel(vm: EditorViewModel, modifier: Modifier = Modifier) {
    val sel = vm.selected
    val tabs = vm.tabsFor(sel)
    if (vm.tab !in tabs) vm.tab = tabs.first()
    Column(modifier.fillMaxWidth().background(Color.White)) {
        // tab row
        Row(
            Modifier.fillMaxWidth().background(AppColors.TabBg).height(52.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.Bottom) {
                for (t in tabs) {
                    val selTab = t == vm.tab
                    Box(
                        Modifier
                            .height(if (selTab) 52.dp else 46.dp)
                            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                            .background(if (selTab) Color.White else Color.Transparent)
                            .testTag("tab_${t.name}")
                            .clickable { vm.tab = t; vm.panelExpanded = true }
                            .padding(horizontal = 18.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                stringResource(t.title),
                                color = if (selTab) AppColors.Accent else Color(0xFF666666),
                                fontSize = 16.sp,
                                fontWeight = if (selTab) FontWeight.SemiBold else FontWeight.Normal,
                            )
                            if (selTab && sel == null) {
                                Box(Modifier.padding(top = 3.dp).size(18.dp, 3.dp).clip(CircleShape).background(AppColors.Accent))
                            }
                        }
                    }
                }
            }
            Box(
                Modifier.size(56.dp, 52.dp).testTag("panel_toggle").clickable {
                    if (vm.tab == PanelTab.SYMBOL) {
                        vm.tab = if (sel is SymbolEl) PanelTab.CONTENT else PanelTab.ADD
                    } else if (vm.tab == PanelTab.CONNECTOR) {
                        vm.tab = if (sel is ConnectorEl) PanelTab.CONTENT else PanelTab.ADD
                    } else {
                        vm.panelExpanded = !vm.panelExpanded
                    }
                },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (vm.panelExpanded) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
                    stringResource(R.string.expand_collapse),
                )
            }
        }
        if (vm.panelExpanded) {
            Box(Modifier.fillMaxWidth().height(340.dp)) {
                when (vm.tab) {
                    PanelTab.ADD -> AddPanel(vm)
                    PanelTab.TEMPLATES -> TemplatesPanel(vm)
                    PanelTab.DATA -> DataPanel(vm)
                    PanelTab.CONTENT -> sel?.let { ContentPanel(vm, it) }
                    PanelTab.STYLE -> sel?.let { StylePanel(vm, it) }
                    PanelTab.FONTS -> sel?.let { FontsPanel(vm, it) }
                    PanelTab.ARRANGE -> sel?.let { ArrangePanel(vm, it) }
                    PanelTab.SYMBOL -> SymbolPicker(vm)
                    PanelTab.CONNECTOR -> ConnectorPicker(vm)
                    PanelTab.LAYOUT -> LayoutPanel(vm)
                }
            }
        }
    }
}

// ====================================================================== Add

@Composable
private fun AddPanel(vm: EditorViewModel) {
    val ctx = LocalContext.current
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            try {
                vm.addImage(Store.importImage(uri))
            } catch (e: Exception) {
                Toast.makeText(ctx, ctx.getString(R.string.err_image_import, e.message), Toast.LENGTH_LONG).show()
            }
        }
    }
    data class Item(val icon: ImageVector, val label: String, val tag: String, val action: () -> Unit)
    val bestBeforePrefix = stringResource(R.string.best_before_prefix)
    val items = listOf(
        Item(Icons.Filled.TextFields, stringResource(R.string.add_text), "add_text") { vm.addText() },
        Item(Icons.Outlined.Image, stringResource(R.string.add_image), "add_image") { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        Item(Icons.Outlined.EmojiEmotions, stringResource(R.string.add_symbol), "add_symbol") { vm.symbolPickerForNew = true; vm.tab = PanelTab.SYMBOL },
        Item(Icons.Filled.Usb, stringResource(R.string.add_connector), "add_connector") { vm.select(null); vm.connectorPickerForNew = true; vm.tab = PanelTab.CONNECTOR },
        Item(Icons.Outlined.Schedule, stringResource(R.string.add_time), "add_time") { vm.addDate() },
        Item(Icons.Filled.ViewWeek, stringResource(R.string.add_barcode), "add_barcode") { vm.addBarcode() },
        Item(Icons.Filled.QrCode2, stringResource(R.string.add_qr), "add_qr") { vm.addQr() },
        Item(Icons.Outlined.Numbers, stringResource(R.string.add_serial), "add_serial") { vm.addSerial() },
        Item(Icons.Outlined.CropSquare, stringResource(R.string.add_shape), "add_shape") { vm.addShape(ShapeKind.RECT) },
        Item(Icons.Filled.HorizontalRule, stringResource(R.string.add_line), "add_line") { vm.addShape(ShapeKind.LINE_H) },
        Item(Icons.Outlined.CalendarMonth, stringResource(R.string.add_best_before), "add_best_before") {
            vm.add(
                DateEl(
                    io.github.tobsef.niimstudio.model.newId(),
                    io.github.tobsef.niimstudio.model.Frame(2f, 2f, vm.doc.widthMm - 4f, 4f),
                    prefix = bestBeforePrefix, offsetDays = 7,
                    style = TextStyle(sizePt = ((vm.doc.heightMm * 0.3f) / io.github.tobsef.niimstudio.render.MM_PER_PT).coerceIn(4f, 30f)),
                ),
            )
            vm.align(vm.selectedId!!, AlignMode.VCENTER)
        },
    )
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(items) { it2 ->
            Column(
                Modifier.fillMaxWidth().testTag(it2.tag).clickable { it2.action() },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(64.dp)
                        .shadow(3.dp, RoundedCornerShape(18.dp))
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.White),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(it2.icon, it2.label, Modifier.size(32.dp), tint = Color(0xFF222222))
                }
                Text(it2.label, Modifier.padding(top = 6.dp), color = Color(0xFF555555), fontSize = 13.sp, maxLines = 1)
            }
        }
    }
}

// ====================================================================== Templates

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TemplatesPanel(vm: EditorViewModel) {
    val templates by Store.templates.collectAsState()
    var menuFor by remember { mutableStateOf<Template?>(null) }
    var renameFor by remember { mutableStateOf<Template?>(null) }
    var saveOpen by remember { mutableStateOf(false) }
    val copySuffix = stringResource(R.string.copy_suffix)
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .height(108.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, AppColors.Divider, RoundedCornerShape(14.dp))
                    .clickable { saveOpen = true },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Filled.Add, null, tint = AppColors.Accent)
                Text(stringResource(R.string.template_save_current), fontSize = 12.sp, color = AppColors.TextGrey, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        items(templates, key = { it.id }) { t ->
            Box {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .height(108.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(AppColors.Group)
                        .combinedClickable(onClick = { vm.loadTemplate(t) }, onLongClick = { menuFor = t })
                        .padding(8.dp),
                ) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        DocThumbnail(t)
                    }
                    Text(t.name, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                    Text(t.doc.profile.sizeLabel, fontSize = 11.sp, color = AppColors.TextGrey)
                }
                DropdownMenu(expanded = menuFor?.id == t.id, onDismissRequest = { menuFor = null }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.open)) }, onClick = { vm.loadTemplate(t); menuFor = null })
                    DropdownMenuItem(text = { Text(stringResource(R.string.rename)) }, onClick = { renameFor = t; menuFor = null })
                    DropdownMenuItem(text = { Text(stringResource(R.string.action_duplicate)) }, onClick = {
                        Store.saveTemplate(t.copy(id = io.github.tobsef.niimstudio.model.newId(), name = t.name + copySuffix, updated = System.currentTimeMillis()))
                        menuFor = null
                    })
                    DropdownMenuItem(text = { Text(stringResource(R.string.action_delete), color = AppColors.Accent) }, onClick = { Store.deleteTemplate(t.id); menuFor = null })
                }
            }
        }
    }
    if (saveOpen) SaveTemplateDialog(vm) { saveOpen = false }
    renameFor?.let { t ->
        TextInputDialog(stringResource(R.string.template_rename), t.name, onDismiss = { renameFor = null }) {
            Store.saveTemplate(t.copy(name = it, doc = t.doc.copy(templateName = it), updated = System.currentTimeMillis()))
            renameFor = null
        }
    }
}

@Composable
fun DocThumbnail(t: Template) {
    val bmp by produceState<Bitmap?>(null, t.id, t.updated) {
        value = withContext(Dispatchers.Default) {
            try {
                val sc = minOf(6f, 240f / t.doc.widthMm)
                Renderer.renderLabel(t.doc, RenderCtx(vars = DataVars.vars(t.doc.data, 0)), sc, applyOffsets = false)
            } catch (e: Exception) {
                null
            }
        }
    }
    bmp?.let {
        Image(
            it.asImageBitmap(), null,
            Modifier.border(1.dp, AppColors.Divider, RoundedCornerShape(4.dp)),
            contentScale = ContentScale.Fit,
        )
    }
}

// ====================================================================== Data source

@Composable
private fun DataPanel(vm: EditorViewModel) {
    val ctx = LocalContext.current
    val ds = vm.doc.data
    val importCsv = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val text = Store.readText(uri)
                vm.updateData { it.copy(mode = DataMode.LIST, listText = text) }
            } catch (e: Exception) {
                Toast.makeText(ctx, ctx.getString(R.string.err_import, e.message), Toast.LENGTH_LONG).show()
            }
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)) {
        Segmented(
            listOf(DataMode.NONE to stringResource(R.string.data_none), DataMode.LIST to stringResource(R.string.data_list), DataMode.COUNTER to stringResource(R.string.data_counter)),
            ds.mode, { m -> vm.updateData { it.copy(mode = m) } },
        )
        val count = DataVars.count(ds)
        when (ds.mode) {
            DataMode.NONE -> Text(
                stringResource(R.string.data_none_hint),
                Modifier.padding(top = 14.dp), color = AppColors.TextGrey, fontSize = 13.sp,
            )
            DataMode.LIST -> {
                OutlinedTextField(
                    ds.listText, { v -> vm.updateData { it.copy(listText = v) } },
                    Modifier.fillMaxWidth().padding(top = 10.dp).height(130.dp),
                    label = { Text(stringResource(R.string.data_list_label)) },
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                )
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { importCsv.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel")) }) {
                        Icon(Icons.Filled.FileOpen, null)
                        Text(" " + stringResource(R.string.data_import))
                    }
                    Spacer(Modifier.weight(1f))
                    val cols = DataVars.columns(ds)
                    Text(
                        pluralStringResource(R.plurals.label_count, count, count) + " · " + (0 until cols.coerceAtMost(26)).joinToString(" ") { "{${'A' + it}}" } + " {N}",
                        fontSize = 12.sp, color = AppColors.TextGrey,
                    )
                }
            }
            DataMode.COUNTER -> {
                Group(Modifier.padding(top = 10.dp)) {
                    StepperRow(stringResource(R.string.counter_start), ds.counterStart.toFloat(), 1f, { v -> vm.updateData { it.copy(counterStart = v.toInt()) } }, format = { it.toInt().toString() })
                    StepperRow(stringResource(R.string.counter_step), ds.counterStep.toFloat(), 1f, { v -> vm.updateData { it.copy(counterStep = v.toInt()) } }, format = { it.toInt().toString() })
                    StepperRow(stringResource(R.string.counter_count), ds.counterCount.toFloat(), 1f, { v -> vm.updateData { it.copy(counterCount = v.toInt()) } }, min = 1f, max = 9999f, format = { it.toInt().toString() })
                    StepperRow(stringResource(R.string.counter_digits), ds.counterDigits.toFloat(), 1f, { v -> vm.updateData { it.copy(counterDigits = v.toInt()) } }, min = 0f, max = 12f, format = { it.toInt().toString() })
                }
                Text(stringResource(R.string.counter_hint), Modifier.padding(top = 8.dp), color = AppColors.TextGrey, fontSize = 13.sp)
            }
        }
        if (ds.mode != DataMode.NONE) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.preview_label), Modifier.weight(1f), fontSize = 15.sp)
                RepeatButton(Icons.Filled.ChevronLeft, stringResource(R.string.previous), { vm.previewRow = (vm.previewRow - 1).coerceAtLeast(0) })
                Text("${vm.previewRow + 1} / $count", fontSize = 15.sp)
                RepeatButton(Icons.Filled.ChevronRight, stringResource(R.string.next), { vm.previewRow = (vm.previewRow + 1).coerceAtMost(count - 1) })
            }
        }
    }
}

// ====================================================================== Content

@Composable
private fun ContentPanel(vm: EditorViewModel, e: Element) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)) {
        when (e) {
            is TextEl -> TextContent(vm, e)
            is DateEl -> DateContent(vm, e)
            is ImageEl -> ImageContent(vm, e)
            is SymbolEl -> SymbolContent(vm, e)
            is BarcodeEl -> BarcodeContent(vm, e)
            is QrEl -> QrContent(vm, e)
            is ShapeEl -> ShapeContent(vm, e)
            is ConnectorEl -> ConnectorContent(vm, e)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VariableChips(vm: EditorViewModel, onInsert: (String) -> Unit) {
    val ds = vm.doc.data
    if (ds.mode == DataMode.NONE) return
    val cols = if (ds.mode == DataMode.LIST) DataVars.columns(ds).coerceAtMost(26) else 0
    FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.insert_variable), color = AppColors.TextGrey, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        for (i in 0 until cols) Pill("{${'A' + i}}", false, { onInsert("{${'A' + i}}") })
        Pill("{N}", false, { onInsert("{N}") })
    }
}

@Composable
private fun TextContent(vm: EditorViewModel, e: TextEl) {
    OutlinedTextField(
        e.text, { v -> vm.updateElement(e.id, "text-${e.id}") { (it as TextEl).copy(text = v) } },
        Modifier.fillMaxWidth().testTag("content_text"),
        label = { Text(stringResource(R.string.content_text)) },
        minLines = 2,
    )
    VariableChips(vm) { v -> vm.updateElement(e.id) { (it as TextEl).copy(text = it.text + v) } }
    SectionLabel(stringResource(R.string.size_mode))
    FitModeChooser(e.style.fit) { m -> vm.updateElement(e.id) { (it as TextEl).copy(style = it.style.copy(fit = m)) } }
}

@Composable
private fun FitModeChooser(fit: FitMode, onChange: (FitMode) -> Unit) = Segmented(
    listOf(FitMode.AUTO_HEIGHT to stringResource(R.string.fit_auto_height), FitMode.FIXED to stringResource(R.string.fit_fixed), FitMode.FIT to stringResource(R.string.fit_fit)),
    fit, onChange,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DateContent(vm: EditorViewModel, e: DateEl) {
    val presets = stringArrayResource(R.array.date_presets).toList()
    val now = Date()
    SectionLabel(stringResource(R.string.date_format))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (p in presets) {
            val sample = try { SimpleDateFormat(p, io.github.tobsef.niimstudio.L10n.locale()).format(now) } catch (ex: Exception) { p }
            Pill(sample, e.pattern == p, { vm.updateElement(e.id) { (it as DateEl).copy(pattern = p) } })
        }
    }
    OutlinedTextField(
        e.pattern, { v -> vm.updateElement(e.id, "pattern-${e.id}") { (it as DateEl).copy(pattern = v) } },
        Modifier.fillMaxWidth().padding(top = 10.dp), label = { Text(stringResource(R.string.date_pattern)) }, singleLine = true,
    )
    OutlinedTextField(
        e.prefix, { v -> vm.updateElement(e.id, "prefix-${e.id}") { (it as DateEl).copy(prefix = v) } },
        Modifier.fillMaxWidth().padding(top = 6.dp), label = { Text(stringResource(R.string.date_prefix)) }, singleLine = true,
    )
    Group(Modifier.padding(top = 10.dp)) {
        StepperRow(stringResource(R.string.date_offset), e.offsetDays.toFloat(), 1f, { v -> vm.updateElement(e.id, "off-${e.id}") { (it as DateEl).copy(offsetDays = v.toInt()) } }, format = { (if (it > 0) "+" else "") + it.toInt() })
    }
    Text(stringResource(R.string.date_preview, Renderer.dateText(e, now)), Modifier.padding(top = 8.dp), color = AppColors.TextGrey, fontSize = 13.sp)
    SectionLabel(stringResource(R.string.size_mode))
    FitModeChooser(e.style.fit) { m -> vm.updateElement(e.id) { (it as DateEl).copy(style = it.style.copy(fit = m)) } }
}

@Composable
private fun ImageContent(vm: EditorViewModel, e: ImageEl) {
    val ctx = LocalContext.current
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            try {
                val f = Store.importImage(uri)
                vm.updateElement(e.id) { (it as ImageEl).copy(file = f) }
            } catch (ex: Exception) {
                Toast.makeText(ctx, ctx.getString(R.string.err_image_import, ex.message), Toast.LENGTH_LONG).show()
            }
        }
    }
    OutlinedButton(onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
        Icon(Icons.Filled.Image, null); Text("  " + stringResource(R.string.image_replace))
    }
    Group(Modifier.padding(top = 10.dp)) {
        SwitchRow(stringResource(R.string.image_dither), e.dither, { v -> vm.updateElement(e.id) { (it as ImageEl).copy(dither = v) } })
        SwitchRow(stringResource(R.string.invert), e.invert, { v -> vm.updateElement(e.id) { (it as ImageEl).copy(invert = v) } })
        SwitchRow(stringResource(R.string.image_keep_ratio), e.keepRatio, { v -> vm.updateElement(e.id) { (it as ImageEl).copy(keepRatio = v) } })
        Text(stringResource(R.string.image_threshold, e.threshold), Modifier.padding(top = 6.dp), fontSize = 15.sp)
        AccentSlider(e.threshold.toFloat(), 20f..235f) { v -> vm.updateElement(e.id, "thr-${e.id}") { (it as ImageEl).copy(threshold = v.toInt()) } }
    }
}

@Composable
private fun SymbolContent(vm: EditorViewModel, e: SymbolEl) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(Symbols.find(e.key)?.name ?: e.key, Modifier.weight(1f), fontSize = 16.sp)
        OutlinedButton(onClick = { vm.symbolPickerForNew = false; vm.tab = PanelTab.SYMBOL }) { Text(stringResource(R.string.symbol_change)) }
    }
    Group(Modifier.padding(top = 10.dp)) {
        SwitchRow(stringResource(R.string.invert_white_on_black), e.invert, { v -> vm.updateElement(e.id) { (it as SymbolEl).copy(invert = v) } })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BarcodeContent(vm: EditorViewModel, e: BarcodeEl) {
    OutlinedTextField(
        e.content, { v -> vm.updateElement(e.id, "bc-${e.id}") { (it as BarcodeEl).copy(content = v) } },
        Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.content)) }, singleLine = true,
    )
    VariableChips(vm) { v -> vm.updateElement(e.id) { (it as BarcodeEl).copy(content = it.content + v) } }
    val r = Barcodes.encode(DataVars.apply(e.content, DataVars.vars(vm.doc.data, vm.previewRow)), e.format)
    if (r is Barcodes.Result.Err) Text("⚠ ${r.msg}", color = AppColors.Accent, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
    SectionLabel(stringResource(R.string.code_type))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for ((k, label) in Barcodes.linear) Pill(label, e.format == k, { vm.updateElement(e.id) { (it as BarcodeEl).copy(format = k) } })
    }
    Group(Modifier.padding(top = 10.dp)) {
        SwitchRow(stringResource(R.string.barcode_show_text), e.showText, { v -> vm.updateElement(e.id) { (it as BarcodeEl).copy(showText = v) } })
        if (e.showText) StepperRow(stringResource(R.string.barcode_text_size), e.textSizePt, 0.5f, { v -> vm.updateElement(e.id, "bts-${e.id}") { (it as BarcodeEl).copy(textSizePt = v) } }, min = 3f, max = 30f, unit = " pt")
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QrContent(vm: EditorViewModel, e: QrEl) {
    var wifiOpen by remember { mutableStateOf(false) }
    OutlinedTextField(
        e.content, { v -> vm.updateElement(e.id, "qr-${e.id}") { (it as QrEl).copy(content = v) } },
        Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.qr_content)) }, minLines = 2,
    )
    VariableChips(vm) { v -> vm.updateElement(e.id) { (it as QrEl).copy(content = it.content + v) } }
    val r = Barcodes.encode(DataVars.apply(e.content, DataVars.vars(vm.doc.data, vm.previewRow)), e.format, e.ecc)
    if (r is Barcodes.Result.Err) Text("⚠ ${r.msg}", color = AppColors.Accent, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { wifiOpen = true }) { Icon(Icons.Filled.Wifi, null); Text(" " + stringResource(R.string.qr_wifi)) }
        OutlinedButton(onClick = { vm.updateElement(e.id) { (it as QrEl).copy(content = "https://") } }) { Text("URL") }
        OutlinedButton(onClick = { vm.updateElement(e.id) { (it as QrEl).copy(content = "tel:") } }) { Text(stringResource(R.string.qr_phone)) }
    }
    SectionLabel(stringResource(R.string.code_type))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for ((k, label) in Barcodes.matrix) Pill(label, e.format == k, { vm.updateElement(e.id) { (it as QrEl).copy(format = k) } })
    }
    if (e.format == "QR_CODE") {
        SectionLabel(stringResource(R.string.qr_ecc))
        Segmented(listOf("L" to "L 7%", "M" to "M 15%", "Q" to "Q 25%", "H" to "H 30%"), e.ecc, { v -> vm.updateElement(e.id) { (it as QrEl).copy(ecc = v) } })
    }
    if (wifiOpen) WifiDialog(onDismiss = { wifiOpen = false }) { content ->
        vm.updateElement(e.id) { (it as QrEl).copy(content = content) }
        wifiOpen = false
    }
}

@Composable
private fun ShapeContent(vm: EditorViewModel, e: ShapeEl) {
    SectionLabel(stringResource(R.string.shape))
    Segmented(
        listOf(ShapeKind.RECT to stringResource(R.string.shape_rect), ShapeKind.ROUND_RECT to stringResource(R.string.shape_round), ShapeKind.ELLIPSE to stringResource(R.string.shape_ellipse), ShapeKind.LINE_H to stringResource(R.string.shape_line_h), ShapeKind.LINE_V to stringResource(R.string.shape_line_v)),
        e.shape, { k -> vm.updateElement(e.id) { (it as ShapeEl).copy(shape = k) } },
    )
    Group(Modifier.padding(top = 10.dp)) {
        StepperRow(stringResource(R.string.shape_stroke), e.strokeMm, 0.125f, { v -> vm.updateElement(e.id, "sw-${e.id}") { (it as ShapeEl).copy(strokeMm = v) } }, min = 0.125f, max = 10f, format = { "%.2f".format(it) }, unit = " mm")
        if (e.shape != ShapeKind.LINE_H && e.shape != ShapeKind.LINE_V) {
            SwitchRow(stringResource(R.string.shape_filled), e.filled, { v -> vm.updateElement(e.id) { (it as ShapeEl).copy(filled = v) } })
        }
        if (e.shape == ShapeKind.ROUND_RECT) {
            StepperRow(stringResource(R.string.corner_radius), e.cornerMm, 0.25f, { v -> vm.updateElement(e.id, "cr-${e.id}") { (it as ShapeEl).copy(cornerMm = v) } }, min = 0f, max = 20f, unit = " mm")
        }
    }
}

// ====================================================================== Style (text)

private fun styleOf(e: Element): TextStyle? = when (e) {
    is TextEl -> e.style
    is DateEl -> e.style
    is ConnectorEl -> e.style
    else -> null
}

private fun withStyle(e: Element, st: TextStyle): Element = when (e) {
    is TextEl -> e.copy(style = st)
    is DateEl -> e.copy(style = st)
    is ConnectorEl -> e.copy(style = st)
    else -> e
}

@Composable
private fun StylePanel(vm: EditorViewModel, e: Element) {
    val st = styleOf(e) ?: return
    fun set(merge: String? = null, f: (TextStyle) -> TextStyle) = vm.updateElement(e.id, merge) { withStyle(it, f(styleOf(it)!!)) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)) {
        Group {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ToggleTile(st.bold, { set { it.copy(bold = !it.bold) } }, Modifier.weight(1f), icon = Icons.Filled.FormatBold)
                ToggleTile(st.underline, { set { it.copy(underline = !it.underline) } }, Modifier.weight(1f), icon = Icons.Filled.FormatUnderlined)
                ToggleTile(st.strike, { set { it.copy(strike = !it.strike) } }, Modifier.weight(1f), icon = Icons.Filled.FormatStrikethrough)
                ToggleTile(st.italic, { set { it.copy(italic = !it.italic) } }, Modifier.weight(1f), icon = Icons.Filled.FormatItalic)
                VDivider()
                ToggleTile(st.fit == FitMode.FIT, { set { it.copy(fit = if (it.fit == FitMode.FIT) FitMode.AUTO_HEIGHT else FitMode.FIT) } }, Modifier.weight(1f), icon = Icons.Filled.FitScreen)
                VDivider()
                // black text / inverse (white on black)
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.size(34.dp).clip(CircleShape).background(Color.Black)
                            .border(if (!st.inverse) 3.dp else 0.dp, Color.White, CircleShape)
                            .clickable { set { it.copy(inverse = false) } },
                    )
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.size(34.dp).clip(CircleShape).background(Color.Black)
                            .border(if (st.inverse) 3.dp else 0.dp, AppColors.Accent, CircleShape)
                            .clickable { set { it.copy(inverse = true) } },
                        contentAlignment = Alignment.Center,
                    ) { Text("A", color = Color.White, fontWeight = FontWeight.Bold) }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(AppColors.Divider))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("AA", fontSize = 18.sp, modifier = Modifier.padding(end = 8.dp))
                Column(Modifier.weight(1f)) {
                    AccentSlider(st.sizePt, 4f..72f, enabled = st.fit != FitMode.FIT) { v -> set("size-${e.id}") { it.copy(sizePt = (v * 2).toInt() / 2f) } }
                    Text(if (st.fit == FitMode.FIT) stringResource(R.string.size_auto) else "%.1f".format(st.sizePt), fontSize = 12.sp, color = AppColors.TextGrey)
                }
                RepeatButton(Icons.Filled.Remove, stringResource(R.string.smaller), { set("size-${e.id}") { it.copy(sizePt = (it.sizePt - 0.5f).coerceAtLeast(2f)) } }, enabled = st.fit != FitMode.FIT)
                RepeatButton(Icons.Filled.Add, stringResource(R.string.bigger), { set("size-${e.id}") { it.copy(sizePt = (it.sizePt + 0.5f).coerceAtMost(300f)) } }, enabled = st.fit != FitMode.FIT)
            }
        }
        Group(Modifier.padding(top = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ToggleTile(st.alignH == 0, { set { it.copy(alignH = 0) } }, Modifier.weight(1f), icon = Icons.AutoMirrored.Filled.FormatAlignLeft)
                ToggleTile(st.alignH == 1, { set { it.copy(alignH = 1) } }, Modifier.weight(1f), icon = Icons.Filled.FormatAlignCenter)
                ToggleTile(st.alignH == 2, { set { it.copy(alignH = 2) } }, Modifier.weight(1f), icon = Icons.AutoMirrored.Filled.FormatAlignRight)
                VDivider()
                ToggleTile(st.alignV == 0, { set { it.copy(alignV = 0) } }, Modifier.weight(1f), icon = Icons.Filled.VerticalAlignTop)
                ToggleTile(st.alignV == 1, { set { it.copy(alignV = 1) } }, Modifier.weight(1f), icon = Icons.Filled.VerticalAlignCenter)
                ToggleTile(st.alignV == 2, { set { it.copy(alignV = 2) } }, Modifier.weight(1f), icon = Icons.Filled.VerticalAlignBottom)
            }
        }
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                SectionLabel(stringResource(R.string.letter_spacing))
                MiniStepper(st.letterSpacing, 0.02f, { v -> set("ls-${e.id}") { it.copy(letterSpacing = v.coerceIn(-0.3f, 2f)) } }, "%.2f")
            }
            Column(Modifier.weight(1f)) {
                SectionLabel(stringResource(R.string.line_spacing))
                MiniStepper(st.lineSpacing, 0.1f, { v -> set("lsp-${e.id}") { it.copy(lineSpacing = v.coerceIn(-5f, 20f)) } }, "%.1f")
            }
        }
        Group(Modifier.padding(top = 10.dp)) {
            SwitchRow(stringResource(R.string.wrap_words), st.wrapWord, { v -> set { it.copy(wrapWord = v) } })
        }
    }
}

@Composable
private fun MiniStepper(value: Float, step: Float, onChange: (Float) -> Unit, fmt: String) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(AppColors.Group).height(52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RepeatButton(Icons.Filled.Remove, stringResource(R.string.less), { onChange(value - step) })
        Text(fmt.format(value), Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontSize = 16.sp)
        RepeatButton(Icons.Filled.Add, stringResource(R.string.more), { onChange(value + step) })
    }
}

/** Slider in the original style: thin rounded track, red fill, round white thumb. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AccentSlider(value: Float, range: ClosedFloatingPointRange<Float>, enabled: Boolean = true, onChange: (Float) -> Unit) {
    val active = if (enabled) AppColors.Accent else Color(0xFFD0D0D4)
    Slider(
        value.coerceIn(range), onChange, valueRange = range, enabled = enabled,
        thumb = {
            Box(
                Modifier.size(26.dp).shadow(3.dp, CircleShape).clip(CircleShape).background(Color.White),
            )
        },
        track = { state ->
            val frac = ((state.value - state.valueRange.start) / (state.valueRange.endInclusive - state.valueRange.start)).coerceIn(0f, 1f)
            Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(Color(0xFFE2E2E6))) {
                Box(Modifier.fillMaxWidth(frac).height(6.dp).clip(CircleShape).background(active))
            }
        },
    )
}

// ====================================================================== Fonts

@Composable
private fun FontsPanel(vm: EditorViewModel, e: Element) {
    val ctx = LocalContext.current
    val st = styleOf(e) ?: return
    val settings by Store.settings.collectAsState()
    var filter by remember { mutableStateOf(0) } // 0 all, 1 recent, 2 favourites
    var refresh by remember { mutableStateOf(0) }
    val upload = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val key = Store.importFont(uri)
                refresh++
                applyFont(vm, e, key)
            } catch (ex: Exception) {
                Toast.makeText(ctx, ctx.getString(R.string.err_font_import, ex.message), Toast.LENGTH_LONG).show()
            }
        }
    }
    val all = remember(refresh) { Fonts.all() }
    val list = when (filter) {
        1 -> settings.recentFonts.mapNotNull { k -> all.firstOrNull { it.key == k } }
        2 -> all.filter { it.key in settings.favoriteFonts }
        else -> all
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            FilterIcon(Icons.Filled.Apps, filter == 0) { filter = 0 }
            FilterIcon(Icons.Filled.History, filter == 1) { filter = 1 }
            FilterIcon(Icons.Filled.Star, filter == 2) { filter = 2 }
            Text(
                stringResource(R.string.upload), color = AppColors.Orange, fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
                modifier = Modifier.clickable { upload.launch(arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/octet-stream", "*/*")) },
            )
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(list, key = { it.key }) { f ->
                val selected = st.font == f.key
                Row(
                    Modifier.fillMaxWidth().clickable { applyFont(vm, e, f.key) }.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(36.dp)) {
                        if (selected) Icon(Icons.Filled.Check, null, tint = AppColors.Accent)
                    }
                    Text(
                        f.label, Modifier.weight(1f),
                        fontFamily = FontFamily(Fonts.base(f.key)),
                        fontSize = if (selected) 22.sp else 18.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    )
                    val fav = f.key in settings.favoriteFonts
                    Icon(
                        if (fav) Icons.Filled.Star else Icons.Outlined.StarOutline, stringResource(R.string.favorite),
                        tint = if (fav) AppColors.Orange else Color(0xFFBBBBBB),
                        modifier = Modifier.clickable {
                            Store.updateSettings { s -> s.copy(favoriteFonts = if (fav) s.favoriteFonts - f.key else s.favoriteFonts + f.key) }
                        },
                    )
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(AppColors.Divider))
            }
            if (list.isEmpty()) item { Text(stringResource(R.string.fonts_empty), Modifier.padding(20.dp), color = AppColors.TextGrey) }
        }
    }
}

private fun applyFont(vm: EditorViewModel, e: Element, key: String) {
    vm.updateElement(e.id) { withStyle(it, styleOf(it)!!.copy(font = key)) }
    Store.updateSettings { s -> s.copy(recentFonts = (listOf(key) + s.recentFonts.filterNot { it == key }).take(10)) }
}

@Composable
private fun FilterIcon(icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
        Icon(icon, null, tint = if (selected) Color.Black else Color(0xFFB0B0B5))
        Box(Modifier.padding(top = 4.dp).size(24.dp, 3.dp).clip(CircleShape).background(if (selected) AppColors.Accent else Color.Transparent))
    }
}

// ====================================================================== Arrange

@Composable
private fun ArrangePanel(vm: EditorViewModel, e: Element) {
    var step by remember { mutableStateOf(0.5f) }
    val f = e.frame
    fun setFrame(merge: String, t: (io.github.tobsef.niimstudio.model.Frame) -> io.github.tobsef.niimstudio.model.Frame) =
        vm.updateElement(e.id, merge) { it.withFrame(t(it.frame)) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Group(Modifier.weight(1.1f).height(150.dp)) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    ToggleTile(false, { vm.align(e.id, AlignMode.LEFT) }, Modifier.weight(1f).testTag("align_LEFT"), enabled = !e.locked, icon = Icons.AutoMirrored.Filled.AlignHorizontalLeft)
                    ToggleTile(false, { vm.align(e.id, AlignMode.HCENTER) }, Modifier.weight(1f).testTag("align_HCENTER"), enabled = !e.locked, icon = Icons.Filled.AlignHorizontalCenter)
                    ToggleTile(false, { vm.align(e.id, AlignMode.RIGHT) }, Modifier.weight(1f).testTag("align_RIGHT"), enabled = !e.locked, icon = Icons.AutoMirrored.Filled.AlignHorizontalRight)
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(AppColors.Divider))
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    ToggleTile(false, { vm.align(e.id, AlignMode.TOP) }, Modifier.weight(1f).testTag("align_TOP"), enabled = !e.locked, icon = Icons.Filled.AlignVerticalTop)
                    ToggleTile(false, { vm.align(e.id, AlignMode.VCENTER) }, Modifier.weight(1f).testTag("align_VCENTER"), enabled = !e.locked, icon = Icons.Filled.AlignVerticalCenter)
                    ToggleTile(false, { vm.align(e.id, AlignMode.BOTTOM) }, Modifier.weight(1f).testTag("align_BOTTOM"), enabled = !e.locked, icon = Icons.Filled.AlignVerticalBottom)
                }
            }
            // D-pad for fine positioning
            Group(Modifier.weight(0.9f).height(150.dp)) {
                Box(Modifier.fillMaxSize()) {
                    RepeatButton(Icons.Filled.ArrowDropUp, stringResource(R.string.move_up), { vm.nudge(e.id, 0f, -step) }, Modifier.align(Alignment.TopCenter), enabled = !e.locked, iconSize = 40.dp)
                    RepeatButton(Icons.Filled.ArrowDropDown, stringResource(R.string.move_down), { vm.nudge(e.id, 0f, step) }, Modifier.align(Alignment.BottomCenter), enabled = !e.locked, iconSize = 40.dp)
                    RepeatButton(Icons.AutoMirrored.Filled.ArrowLeft, stringResource(R.string.move_left), { vm.nudge(e.id, -step, 0f) }, Modifier.align(Alignment.CenterStart), enabled = !e.locked, iconSize = 40.dp)
                    RepeatButton(Icons.AutoMirrored.Filled.ArrowRight, stringResource(R.string.move_right), { vm.nudge(e.id, step, 0f) }, Modifier.align(Alignment.CenterEnd), enabled = !e.locked, iconSize = 40.dp)
                    Box(
                        Modifier.align(Alignment.Center).clip(RoundedCornerShape(10.dp)).background(Color.White)
                            .clickable { step = when (step) { 0.125f -> 0.5f; 0.5f -> 1f; 1f -> 5f; else -> 0.125f } }
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                    ) {
                        Text(if (step < 0.2f) stringResource(R.string.step_one_dot) else "${LabelProfileFmt(step)} mm", fontSize = 11.sp, color = AppColors.Accent)
                    }
                }
            }
        }
        Group(Modifier.padding(top = 10.dp)) {
            StepperRow("X", f.x, step, tag = "x", onChange = { v -> setFrame("x-${e.id}") { it.copy(x = v) } }, format = { "%.2f".format(it) }, unit = " mm")
            StepperRow("Y", f.y, step, tag = "y", onChange = { v -> setFrame("y-${e.id}") { it.copy(y = v) } }, format = { "%.2f".format(it) }, unit = " mm")
            StepperRow(stringResource(R.string.width), f.w, step, tag = "width", onChange = { v -> setFrame("w-${e.id}") { it.copy(w = v) } }, min = 0.5f, format = { "%.2f".format(it) }, unit = " mm")
            if (!(e is TextEl && e.style.fit == FitMode.AUTO_HEIGHT) && !(e is DateEl && e.style.fit == FitMode.AUTO_HEIGHT)) {
                StepperRow(stringResource(R.string.height), f.h, step, tag = "height", onChange = { v -> setFrame("h-${e.id}") { it.copy(h = v) } }, min = 0.5f, format = { "%.2f".format(it) }, unit = " mm")
            }
            StepperRow(stringResource(R.string.rotation), f.rotation, 15f, tag = "rotation", onChange = { v -> setFrame("r-${e.id}") { it.copy(rotation = ((v % 360f) + 360f) % 360f) } }, format = { it.toInt().toString() }, unit = "°")
        }
        Group(Modifier.padding(top = 10.dp)) {
            Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.layer), Modifier.weight(1f), fontSize = 15.sp)
                OutlinedButton(onClick = { vm.sendToBack(e.id) }) { Icon(Icons.Filled.FlipToBack, null); Text(" " + stringResource(R.string.layer_back)) }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { vm.bringToFront(e.id) }) { Icon(Icons.Filled.FlipToFront, null); Text(" " + stringResource(R.string.layer_front)) }
            }
        }
        Group(Modifier.padding(top = 10.dp)) {
            SwitchRow(stringResource(R.string.mirror), f.mirror, { vm.toggleMirror(e.id) })
            SwitchRow(stringResource(R.string.lock_position), e.locked, { vm.toggleLock(e.id) })
        }
    }
}

@Suppress("FunctionName")
private fun LabelProfileFmt(v: Float) = io.github.tobsef.niimstudio.model.LabelProfile.fmt(v)

// ====================================================================== Symbols

@Composable
private fun SymbolPicker(vm: EditorViewModel) {
    val ctx = LocalContext.current
    val settings by Store.settings.collectAsState()
    var query by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf(Symbols.categories.first().first) }
    val upload = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            try {
                vm.addImage(Store.importImage(uri))
            } catch (e: Exception) {
                Toast.makeText(ctx, ctx.getString(R.string.err_import, e.message), Toast.LENGTH_LONG).show()
            }
        }
    }
    val list: List<SymbolDef> = when {
        query.isNotBlank() -> Symbols.search(query)
        cat == "★" -> settings.favoriteSymbols.mapNotNull { Symbols.find(it) }
        else -> Symbols.all.filter { it.category == cat }
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                Modifier.clip(RoundedCornerShape(20.dp)).background(AppColors.Group).padding(horizontal = 12.dp, vertical = 8.dp).width(120.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Search, null, Modifier.size(20.dp), tint = AppColors.TextGrey)
                Box(Modifier.padding(start = 6.dp)) {
                    if (query.isEmpty()) Text(stringResource(R.string.search), color = AppColors.TextGrey, fontSize = 15.sp)
                    BasicTextField(query, { query = it }, singleLine = true, textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp))
                }
            }
            Icon(
                Icons.Filled.Star, stringResource(R.string.favorites),
                tint = if (cat == "★" && query.isBlank()) AppColors.Orange else Color(0xFFBBBBBB),
                modifier = Modifier.clickable { cat = "★"; query = "" },
            )
            Text(stringResource(R.string.upload), color = AppColors.Orange, fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
                modifier = Modifier.clickable { upload.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })
            for ((c, title) in Symbols.categories) {
                val sel = c == cat && query.isBlank()
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { cat = c; query = "" }) {
                    Text(stringResource(title), color = if (sel) Color.Black else Color(0xFF999999), fontSize = 16.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
                    Box(Modifier.padding(top = 3.dp).size(22.dp, 3.dp).clip(CircleShape).background(if (sel) AppColors.Accent else Color.Transparent))
                }
            }
        }
        if (list.isEmpty()) {
            Text(if (cat == "★") stringResource(R.string.symbols_no_favorites) else stringResource(R.string.nothing_found), Modifier.padding(20.dp), color = AppColors.TextGrey)
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 12.dp),
        ) {
            items(list, key = { it.key }) { s -> SymbolCell(vm, s, s.key in settings.favoriteSymbols) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SymbolCell(vm: EditorViewModel, s: SymbolDef, fav: Boolean) {
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, AppColors.Divider, RoundedCornerShape(14.dp))
            .background(Color.White)
            .combinedClickable(
                onClick = { vm.addOrReplaceSymbol(s.key) },
                onLongClick = {
                    Store.updateSettings { st -> st.copy(favoriteSymbols = if (fav) st.favoriteSymbols - s.key else st.favoriteSymbols + s.key) }
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(s.icon, s.name, Modifier.size(34.dp), tint = Color.Black)
        if (fav) Icon(Icons.Filled.Star, null, Modifier.align(Alignment.TopEnd).padding(3.dp).size(12.dp), tint = AppColors.Orange)
    }
}
