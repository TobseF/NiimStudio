package io.github.tobsef.niimstudio.ui

import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.tobsef.niimstudio.R
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.tobsef.niimstudio.data.Store
import io.github.tobsef.niimstudio.model.TextEl
import io.github.tobsef.niimstudio.printer.ConnState
import io.github.tobsef.niimstudio.printer.PrinterManager
import io.github.tobsef.niimstudio.protocol.ImageEncoder
import io.github.tobsef.niimstudio.protocol.PrintOptions
import io.github.tobsef.niimstudio.protocol.PrintVariant
import io.github.tobsef.niimstudio.render.DataVars
import io.github.tobsef.niimstudio.render.Renderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun TextInputDialog(title: String, initial: String, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(text, { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(onClick = { if (text.isNotBlank()) onOk(text.trim()) }) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun SaveTemplateDialog(vm: EditorViewModel, onDismiss: () -> Unit) {
    val existing = vm.doc.templateName
    val templates by Store.templates.collectAsState()
    val defaultName = stringResource(R.string.template_default_name, templates.size + 1)
    var name by remember { mutableStateOf(existing ?: defaultName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.template_save_title)) },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (existing != null) Text(stringResource(R.string.template_opened, existing), Modifier.padding(top = 8.dp), color = AppColors.TextGrey, fontSize = 13.sp)
            }
        },
        confirmButton = {
            Row {
                if (existing != null) {
                    TextButton(onClick = { vm.saveTemplate(name.trim(), asNew = true); onDismiss() }) { Text(stringResource(R.string.template_save_as_new)) }
                }
                TextButton(onClick = { if (name.isNotBlank()) { vm.saveTemplate(name.trim(), asNew = false); onDismiss() } }) {
                    Text(if (existing != null) stringResource(R.string.template_overwrite) else stringResource(R.string.save))
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Opened by double tap / edit button on a text element. */
@Composable
fun TextEditDialog(vm: EditorViewModel, e: TextEl, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(e.text) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.text_edit_title)) },
        text = {
            OutlinedTextField(
                text, { text = it },
                Modifier.fillMaxWidth().focusRequester(focus),
                minLines = 3,
            )
        },
        confirmButton = {
            TextButton(onClick = {
                vm.updateElement(e.id) { (it as TextEl).copy(text = text) }
                onDismiss()
            }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun WifiDialog(onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var ssid by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var sec by remember { mutableStateOf("WPA") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.wifi_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(ssid, { ssid = it }, label = { Text(stringResource(R.string.wifi_ssid)) }, singleLine = true)
                OutlinedTextField(pass, { pass = it }, label = { Text(stringResource(R.string.wifi_password)) }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                Segmented(listOf("WPA" to "WPA/WPA2", "WEP" to "WEP", "nopass" to stringResource(R.string.wifi_open)), sec, { sec = it })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                fun esc(s: String) = s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace(":", "\\:").replace("\"", "\\\"")
                onOk("WIFI:T:$sec;S:${esc(ssid)};" + (if (sec != "nopass") "P:${esc(pass)};" else "") + ";")
            }) { Text(stringResource(R.string.apply)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrintSheet(vm: EditorViewModel, onDismiss: () -> Unit, onOpenSettings: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by Store.settings.collectAsState()
    val conn by PrinterManager.state.collectAsState()
    val progress by PrinterManager.printing.collectAsState()
    val doc = vm.doc
    val pages = DataVars.count(doc.data)
    var copies by remember { mutableIntStateOf(1) }
    var density by remember { mutableIntStateOf(if (doc.profile.density > 0) doc.profile.density else settings.density) }
    var row by remember { mutableIntStateOf(vm.previewRow.coerceIn(0, pages - 1)) }
    var error by remember { mutableStateOf<String?>(null) }
    var preparing by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }

    val preview by produceState<Bitmap?>(null, doc, row, settings.rotation, settings.printheadDots, settings.centerOnHead) {
        value = withContext(Dispatchers.Default) {
            try {
                val mono = Renderer.printImage(doc, settings, row)
                Renderer.monoPreview(mono, (360 - Renderer.printRotation(doc, settings)) % 360)
            } catch (e: Exception) {
                null
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = { if (progress == null) onDismiss() },
        modifier = Modifier.automationIds(),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color.White,
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding()) {
            Text(stringResource(R.string.print_title), fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Text("${profileName(doc.profile)} · ${doc.profile.sizeLabel}", color = AppColors.TextGrey, fontSize = 13.sp)

            Box(
                Modifier.fillMaxWidth().padding(vertical = 14.dp).clip(RoundedCornerShape(16.dp)).background(AppColors.CanvasBg).padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                preview?.let {
                    Image(
                        it.asImageBitmap(), stringResource(R.string.print_preview),
                        Modifier.fillMaxWidth().heightIn(max = 160.dp),
                        contentScale = ContentScale.Fit,
                        filterQuality = FilterQuality.None,
                    )
                } ?: Box(Modifier.height(80.dp))
            }
            if (pages > 1) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.preview_label), Modifier.weight(1f))
                    RepeatButton(Icons.Filled.ChevronLeft, stringResource(R.string.previous), { row = (row - 1).coerceAtLeast(0) })
                    Text("${row + 1} / $pages")
                    RepeatButton(Icons.Filled.ChevronRight, stringResource(R.string.next), { row = (row + 1).coerceAtMost(pages - 1) })
                }
            }

            Group {
                StepperRow(if (pages > 1) stringResource(R.string.copies_each) else stringResource(R.string.copies), copies.toFloat(), 1f, { copies = it.toInt() }, min = 1f, max = 999f, format = { it.toInt().toString() })
            }
            SectionLabel(stringResource(R.string.density))
            Segmented((1..5).map { it to it.toString() }, density, { density = it })
            Text(
                pluralStringResource(R.plurals.print_total, pages * copies, pages * copies),
                Modifier.padding(top = 10.dp), fontWeight = FontWeight.Medium,
            )

            // Connection line
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                val txt = connText(conn, settings)
                val col = when (conn) {
                    is ConnState.Connected -> AppColors.Ok
                    is ConnState.Connecting -> AppColors.Orange
                    is ConnState.Failed -> AppColors.Accent
                    ConnState.Disconnected -> AppColors.TextGrey
                }
                Icon(Icons.Filled.Bluetooth, null, tint = col)
                Text(txt, Modifier.weight(1f).padding(start = 6.dp), color = col, fontSize = 14.sp)
                if (settings.deviceAddress == null) {
                    TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.choose_printer)) }
                } else if (conn !is ConnState.Connected && conn !is ConnState.Connecting) {
                    TextButton(onClick = { PrinterManager.connectSaved(ctx) }) { Text(stringResource(R.string.connect)) }
                }
            }

            progress?.let { p ->
                LinearProgressIndicator(progress = { p.fraction }, Modifier.fillMaxWidth().padding(top = 10.dp), color = AppColors.Accent)
                Text(progressText(p), Modifier.padding(top = 4.dp), fontSize = 13.sp)
            }
            if (finished && progress == null && error == null) Text("✓ " + stringResource(R.string.print_done), Modifier.padding(top = 8.dp), color = AppColors.Ok)
            error?.let { Text(it, Modifier.padding(top = 8.dp), color = AppColors.Accent, fontSize = 14.sp) }

            Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (progress != null) {
                    OutlinedButton(onClick = { PrinterManager.cancelPrint() }, Modifier.weight(1f).height(52.dp)) { Text(stringResource(R.string.cancel)) }
                } else {
                    OutlinedButton(onClick = onDismiss, Modifier.weight(1f).height(52.dp).testTag("print_close")) { Text(stringResource(R.string.close)) }
                    Button(
                        onClick = {
                            error = null
                            finished = false
                            preparing = true
                            scope.launch {
                                try {
                                    if (!PrinterManager.ensureConnected(ctx)) {
                                        error = ctx.getString(if (settings.deviceAddress == null) R.string.print_need_printer else R.string.print_unreachable)
                                        return@launch
                                    }
                                    val images = withContext(Dispatchers.Default) {
                                        (0 until pages).map { ImageEncoder.encode(Renderer.printImage(doc, settings, it)) }
                                    }
                                    val info = PrinterManager.info
                                    val variant = settings.variant?.let { v -> PrintVariant.entries.firstOrNull { it.name == v } }
                                        ?: if ((info?.protocolVersion ?: 0) >= 4) PrintVariant.V4 else PrintVariant.V3
                                    val opts = PrintOptions(
                                        density = density,
                                        labelType = doc.profile.labelType,
                                        copies = copies,
                                        variant = variant,
                                        printheadPixels = settings.printheadDots,
                                        useIndexedRows = settings.indexedRows,
                                    )
                                    PrinterManager.print(images, opts) { err ->
                                        if (err != null) error = ctx.getString(R.string.print_failed, errorText(ctx, err)) else finished = true
                                    }
                                } catch (e: Exception) {
                                    error = errorText(ctx, e)
                                } finally {
                                    preparing = false
                                }
                            }
                        },
                        enabled = !preparing,
                        modifier = Modifier.weight(1.4f).height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.Accent),
                    ) {
                        Icon(Icons.Filled.Print, null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (preparing) stringResource(R.string.please_wait) else stringResource(R.string.print_action), fontSize = 16.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.automationIds(),
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }, Modifier.testTag("confirm_ok")) { Text(confirm, color = AppColors.Accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

