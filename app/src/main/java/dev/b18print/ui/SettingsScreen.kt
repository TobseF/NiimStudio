package dev.b18print.ui

import androidx.compose.material.icons.filled.Check
import dev.b18print.L10n
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import dev.b18print.R
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.b18print.BuildConfig
import dev.b18print.data.Store
import dev.b18print.model.LabelDoc
import dev.b18print.model.LabelProfile
import dev.b18print.model.TransportKind
import dev.b18print.printer.BluetoothScanner
import dev.b18print.printer.ConnState
import dev.b18print.printer.PrinterManager
import dev.b18print.protocol.ImageEncoder
import dev.b18print.protocol.PrintOptions
import dev.b18print.protocol.PrintVariant
import dev.b18print.render.Renderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubScreen(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}, content: @Composable (Modifier) -> Unit) {
    Scaffold(
        containerColor = AppColors.CanvasBg,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                actions = { actions() },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
            )
        },
    ) { pad -> content(Modifier.padding(pad)) }
}

@Composable
private fun Card(title: String, content: @Composable () -> Unit) {
    Text(title, Modifier.padding(start = 20.dp, top = 18.dp, bottom = 6.dp), color = AppColors.TextGrey, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp).clip(RoundedCornerShape(16.dp)).background(Color.White).padding(horizontal = 16.dp, vertical = 6.dp),
    ) { content() }
}

@Composable
private fun NavRow(label: String, value: String? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontSize = 15.sp)
        if (value != null) Text(value, color = AppColors.TextGrey, fontSize = 14.sp)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = AppColors.TextGrey)
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenProfiles: () -> Unit, onOpenLog: () -> Unit, requestPermissions: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val s by Store.settings.collectAsState()
    val conn by PrinterManager.state.collectAsState()
    val profiles by Store.profiles.collectAsState()
    var scanOpen by remember { mutableStateOf(false) }
    var variantMenu by remember { mutableStateOf(false) }
    var rotationMenu by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val logSize = PrinterManager.log.collectAsState().value.size

    SubScreen(stringResource(R.string.settings), onBack) { mod ->
        Column(mod.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Card(stringResource(R.string.section_language)) {
                LanguagePicker()
            }

            Card(stringResource(R.string.section_printer)) {
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    val col = when (conn) {
                        is ConnState.Connected -> AppColors.Ok
                        is ConnState.Connecting -> AppColors.Orange
                        is ConnState.Failed -> AppColors.Accent
                        ConnState.Disconnected -> AppColors.TextGrey
                    }
                    Icon(Icons.Filled.Bluetooth, null, tint = col, modifier = Modifier.size(32.dp))
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(s.deviceName ?: stringResource(R.string.conn_no_printer), fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Text(
                            when (val c = conn) {
                                is ConnState.Connected -> stringResource(R.string.settings_connected_info, c.info.toString())
                                is ConnState.Connecting -> stringResource(R.string.settings_connecting)
                                is ConnState.Failed -> failText(c)
                                ConnState.Disconnected -> s.deviceAddress ?: stringResource(R.string.settings_tap_search)
                            },
                            fontSize = 12.sp, color = col,
                        )
                    }
                    if (conn is ConnState.Connecting) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                    Button(
                        onClick = { if (PrinterManager.hasPermissions(ctx)) scanOpen = true else requestPermissions() },
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.Accent),
                    ) { Text(stringResource(R.string.search_printer)) }
                    if (conn is ConnState.Connected) {
                        OutlinedButton(onClick = { PrinterManager.disconnect() }) { Text(stringResource(R.string.disconnect)) }
                    } else if (s.deviceAddress != null) {
                        OutlinedButton(onClick = { PrinterManager.connectSaved(ctx) }) { Text(stringResource(R.string.connect)) }
                    }
                }
                SectionLabel(stringResource(R.string.connection_type))
                Segmented(listOf(TransportKind.SPP to stringResource(R.string.transport_spp), TransportKind.BLE to "Bluetooth LE"), s.transport, { v -> Store.updateSettings { it.copy(transport = v) } })
                SwitchRow(stringResource(R.string.auto_connect), s.autoConnect, { v -> Store.updateSettings { it.copy(autoConnect = v) } })
                SwitchRow(stringResource(R.string.keep_alive), s.keepAlive, { v -> Store.updateSettings { it.copy(keepAlive = v) } })
            }

            Card(stringResource(R.string.section_print)) {
                SectionLabel(stringResource(R.string.default_density))
                Segmented((1..5).map { it to it.toString() }, s.density, { v -> Store.updateSettings { it.copy(density = v) } })
                Box {
                    NavRow(
                        stringResource(R.string.protocol_variant),
                        s.variant?.let { v -> PrintVariant.entries.firstOrNull { it.name == v }?.name } ?: stringResource(R.string.automatic),
                    ) { variantMenu = true }
                    DropdownMenu(variantMenu, { variantMenu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.variant_auto)) }, onClick = { Store.updateSettings { it.copy(variant = null) }; variantMenu = false })
                        for (v in PrintVariant.entries) {
                            DropdownMenuItem(text = { Text(stringResource(variantRes(v))) }, onClick = { Store.updateSettings { it.copy(variant = v.name) }; variantMenu = false })
                        }
                    }
                }
                Box {
                    NavRow(stringResource(R.string.print_rotation), "${s.rotation}°") { rotationMenu = true }
                    DropdownMenu(rotationMenu, { rotationMenu = false }) {
                        for (r in listOf(90, 270, 0, 180)) {
                            DropdownMenuItem(text = { Text("$r°" + if (r == 90) " (" + stringResource(R.string.default_marker) + ")" else "") }, onClick = { Store.updateSettings { it.copy(rotation = r) }; rotationMenu = false })
                        }
                    }
                }
                StepperRow(stringResource(R.string.printhead_dots), s.printheadDots.toFloat(), 8f, { v -> Store.updateSettings { it.copy(printheadDots = v.toInt()) } }, min = 8f, max = 2048f, format = { it.toInt().toString() })
                SwitchRow(stringResource(R.string.center_on_head), s.centerOnHead, { v -> Store.updateSettings { it.copy(centerOnHead = v) } })
                StepperRow(stringResource(R.string.packet_delay), s.packetDelayMs.toFloat(), 5f, { v -> Store.updateSettings { it.copy(packetDelayMs = v.toInt()) } }, min = 0f, max = 500f, format = { it.toInt().toString() }, unit = " ms")
                SwitchRow(stringResource(R.string.indexed_rows), s.indexedRows, { v -> Store.updateSettings { it.copy(indexedRows = v) } }, subtitle = stringResource(R.string.indexed_rows_hint))
            }

            Card(stringResource(R.string.section_editor)) {
                NavRow(stringResource(R.string.label_profiles), "${profiles.size}") { onOpenProfiles() }
                SwitchRow(stringResource(R.string.snapping), s.snapping, { v -> Store.updateSettings { it.copy(snapping = v) } }, subtitle = stringResource(R.string.snapping_hint))
            }

            Card(stringResource(R.string.section_diagnostics)) {
                NavRow(stringResource(R.string.log_title_long), pluralStringResource(R.plurals.log_lines, logSize, logSize)) { onOpenLog() }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                    OutlinedButton(onClick = {
                        scope.launch {
                            message = PrinterManager.refreshInfo()?.toString() ?: ctx.getString(R.string.not_connected)
                        }
                    }) { Text(stringResource(R.string.query_status)) }
                    OutlinedButton(onClick = {
                        scope.launch {
                            message = printTestPattern(ctx)
                        }
                    }) {
                        Icon(Icons.Filled.Print, null)
                        Text(" " + stringResource(R.string.test_pattern))
                    }
                }
                message?.let { Text(it, fontSize = 12.sp, color = AppColors.TextGrey, modifier = Modifier.padding(bottom = 8.dp)) }
            }

            Card(stringResource(R.string.section_info)) {
                Text("B18 Print ${BuildConfig.VERSION_NAME}", Modifier.padding(vertical = 10.dp), fontSize = 15.sp)
                Text(
                    stringResource(R.string.about_text),
                    Modifier.padding(bottom = 10.dp), fontSize = 12.sp, color = AppColors.TextGrey,
                )
            }
        }
    }

    if (scanOpen) DeviceScanDialog(onDismiss = { scanOpen = false }) { d ->
        Store.updateSettings { it.copy(deviceAddress = d.address, deviceName = d.name) }
        PrinterManager.connect(ctx, d.address, d.name, s.transport)
        scanOpen = false
    }
}

/** Prints a border/ruler test pattern on the current default profile. */
private suspend fun printTestPattern(ctx: android.content.Context): String {
    if (!PrinterManager.ensureConnected(ctx)) return ctx.getString(R.string.not_connected)
    val s = Store.settings.value
    val p = Store.profile(s.lastProfileId) ?: Store.profiles.value.first()
    val doc = LabelDoc(
        profile = p,
        elements = listOf(
            dev.b18print.model.ShapeEl(dev.b18print.model.newId(), dev.b18print.model.Frame(0.5f, 0.5f, p.lengthMm - 1f, p.tapeWidthMm - 1f), strokeMm = 0.25f),
            dev.b18print.model.ShapeEl(dev.b18print.model.newId(), dev.b18print.model.Frame(1f, 1f, 3f, 3f), filled = true),
            dev.b18print.model.TextEl(
                dev.b18print.model.newId(),
                dev.b18print.model.Frame(5f, 1f, p.lengthMm - 6f, p.tapeWidthMm - 2f),
                text = "B18 TEST ${p.sizeLabel}",
                style = dev.b18print.model.TextStyle(fit = dev.b18print.model.FitMode.FIT),
            ),
        ),
    )
    val img = withContext(Dispatchers.Default) { ImageEncoder.encode(Renderer.printImage(doc, s, 0)) }
    val variant = s.variant?.let { v -> PrintVariant.entries.firstOrNull { it.name == v } }
        ?: if ((PrinterManager.info?.protocolVersion ?: 0) >= 4) PrintVariant.V4 else PrintVariant.V3
    PrinterManager.print(listOf(img), PrintOptions(density = s.density, labelType = p.labelType, variant = variant, printheadPixels = s.printheadDots, useIndexedRows = s.indexedRows))
    return ctx.getString(R.string.test_pattern_sent, p.name, variant.name)
}

@Composable
fun DeviceScanDialog(onDismiss: () -> Unit, onPick: (BluetoothScanner.FoundDevice) -> Unit) {
    val ctx = LocalContext.current
    var devices by remember { mutableStateOf<List<BluetoothScanner.FoundDevice>>(emptyList()) }
    var showAll by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(true) }
    DisposableEffect(Unit) {
        val sc = BluetoothScanner(ctx) { devices = it }
        sc.start()
        onDispose { sc.stop() }
    }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(15_000)
        scanning = false
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.search_printer), Modifier.weight(1f))
                if (scanning) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        },
        text = {
            Column {
                SwitchRow(stringResource(R.string.show_all_devices), showAll, { showAll = it })
                val list = devices.filter { showAll || it.isB18 }
                if (list.isEmpty()) {
                    Text(
                        if (showAll) stringResource(R.string.scanning) else stringResource(R.string.no_b18_found),
                        Modifier.padding(vertical = 16.dp), color = AppColors.TextGrey,
                    )
                }
                LazyColumn(Modifier.heightIn(max = 340.dp)) {
                    items(list, key = { it.address }) { d ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onPick(d) }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.Print, null, tint = if (d.isB18) AppColors.Accent else AppColors.TextGrey)
                            Column(Modifier.padding(start = 12.dp)) {
                                Text(d.name, fontWeight = FontWeight.Medium)
                                Text(
                                    d.address + (if (d.bonded) " · " + stringResource(R.string.paired) else "") + (if (d.ble) " · BLE" else ""),
                                    fontSize = 12.sp, color = AppColors.TextGrey,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

@Composable
fun LogScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val log by PrinterManager.log.collectAsState()
    val state = rememberLazyListState()
    LaunchedEffect(log.size) { if (log.isNotEmpty()) state.scrollToItem(log.size - 1) }
    SubScreen(stringResource(R.string.log_title), onBack, actions = {
        IconButton(onClick = {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "B18Print Log")
                putExtra(Intent.EXTRA_TEXT, log.joinToString("\n"))
            }
            ctx.startActivity(Intent.createChooser(send, ctx.getString(R.string.share_log)))
        }) { Icon(Icons.Filled.Share, stringResource(R.string.share)) }
        IconButton(onClick = { PrinterManager.clearLog() }) { Icon(Icons.Filled.Delete, stringResource(R.string.clear)) }
    }) { mod ->
        LazyColumn(mod.fillMaxSize().background(Color(0xFF111111)).padding(6.dp), state = state) {
            items(log) { line ->
                Text(line, color = Color(0xFFB5F5B5), fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun ProfilesScreen(onBack: () -> Unit, onUse: (LabelProfile) -> Unit) {
    val profiles by Store.profiles.collectAsState()
    var editing by remember { mutableStateOf<LabelProfile?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    val newProfileName = stringResource(R.string.new_profile)
    SubScreen(stringResource(R.string.label_profiles), onBack, actions = {
        TextButton(onClick = { confirmReset = true }) { Text(stringResource(R.string.default_marker)) }
    }) { mod ->
        Box(mod.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp, horizontal = 0.dp)) {
                items(profiles, key = { it.id }) { p ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(16.dp)).background(Color.White)
                            .clickable { editing = p }.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // proportional preview
                        val ratio = (p.lengthMm / p.tapeWidthMm).coerceIn(1f, 6f)
                        Box(Modifier.width(64.dp).height(40.dp), contentAlignment = Alignment.Center) {
                            Box(
                                Modifier.width((16 * ratio).coerceAtMost(64f).dp).height(16.dp)
                                    .clip(RoundedCornerShape(p.cornerMm.coerceAtMost(4f).dp)).background(AppColors.CanvasBg),
                            )
                        }
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(profileName(p), fontWeight = FontWeight.Medium, fontSize = 16.sp)
                            Text(
                                "${p.sizeLabel} · " + stringResource(labelTypeRes(p.labelType)) +
                                    (if (p.density > 0) " · " + stringResource(R.string.density_value, p.density) else ""),
                                fontSize = 12.sp, color = AppColors.TextGrey,
                            )
                        }
                        TextButton(onClick = { onUse(p) }) { Text(stringResource(R.string.use)) }
                    }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
            Button(
                onClick = { editing = LabelProfile(name = newProfileName, tapeWidthMm = 12f, lengthMm = 40f) },
                modifier = Modifier.align(Alignment.BottomCenter).padding(20.dp).fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Accent),
            ) { Text("+ " + stringResource(R.string.new_profile), fontSize = 16.sp) }
        }
    }
    editing?.let { p -> ProfileEditDialog(p, isNew = profiles.none { it.id == p.id }, onDismiss = { editing = null }) }
    if (confirmReset) ConfirmDialog(stringResource(R.string.reset_profiles_title), stringResource(R.string.reset_profiles_text), stringResource(R.string.reset), { confirmReset = false }) { Store.resetProfiles() }
}

@Composable
fun ProfileEditDialog(initial: LabelProfile, isNew: Boolean, onDismiss: () -> Unit) {
    var p by remember { mutableStateOf(initial.copy(name = profileName(initial))) }
    var typeMenu by remember { mutableStateOf(false) }
    val copySuffix = stringResource(R.string.copy_suffix)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) stringResource(R.string.new_profile) else stringResource(R.string.edit_profile)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                androidx.compose.material3.OutlinedTextField(p.name, { p = p.copy(name = it) }, label = { Text(stringResource(R.string.name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                StepperRow(stringResource(R.string.tape_width), p.tapeWidthMm, 0.5f, { p = p.copy(tapeWidthMm = it) }, min = 4f, max = 50f, unit = " mm")
                StepperRow(stringResource(R.string.length), p.lengthMm, 1f, { p = p.copy(lengthMm = it) }, min = 5f, max = 500f, unit = " mm")
                Box {
                    NavRow(stringResource(R.string.label_type), stringResource(labelTypeRes(p.labelType))) { typeMenu = true }
                    DropdownMenu(typeMenu, { typeMenu = false }) {
                        for ((id, _) in dev.b18print.protocol.LabelType.all) {
                            DropdownMenuItem(text = { Text(stringResource(labelTypeRes(id))) }, onClick = { p = p.copy(labelType = id); typeMenu = false })
                        }
                    }
                }
                StepperRow(stringResource(R.string.gap), p.gapMm, 0.5f, { p = p.copy(gapMm = it) }, min = 0f, max = 20f, unit = " mm")
                StepperRow(stringResource(R.string.corner_radius), p.cornerMm, 0.5f, { p = p.copy(cornerMm = it) }, min = 0f, max = 10f, unit = " mm")
                StepperRow(stringResource(R.string.offset_along), p.offsetXmm, 0.125f, { p = p.copy(offsetXmm = it) }, min = -10f, max = 10f, format = { "%.2f".format(it) }, unit = " mm")
                StepperRow(stringResource(R.string.offset_across), p.offsetYmm, 0.125f, { p = p.copy(offsetYmm = it) }, min = -10f, max = 10f, format = { "%.2f".format(it) }, unit = " mm")
                StepperRow(stringResource(R.string.density_profile), p.density.toFloat(), 1f, { p = p.copy(density = it.toInt()) }, min = 0f, max = 5f, format = { it.toInt().toString() })
                if (!isNew) {
                    TextButton(onClick = { Store.deleteProfile(p.id); onDismiss() }) { Text(stringResource(R.string.delete_profile), color = AppColors.Accent) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { Store.saveProfile(p); onDismiss() }) { Text(stringResource(R.string.save)) } },
        dismissButton = {
            Row {
                if (!isNew) TextButton(onClick = { Store.saveProfile(p.copy(id = dev.b18print.model.newId(), name = p.name + copySuffix)); onDismiss() }) { Text(stringResource(R.string.action_duplicate)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

/** "System" plus every language found in the resources; switching recreates the activity. */
@Composable
private fun LanguagePicker() {
    val ctx = LocalContext.current
    val s by Store.settings.collectAsState()
    val options = listOf(L10n.LANGUAGE_SYSTEM) + L10n.available
    Column {
        for (code in options) {
            val selected = s.language == code || (code == L10n.LANGUAGE_SYSTEM && s.language !in options)
            Row(
                Modifier.fillMaxWidth().clickable {
                    if (!selected) {
                        Store.updateSettings { it.copy(language = code) }
                        (ctx as? android.app.Activity)?.recreate()
                    }
                }.padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    if (code == L10n.LANGUAGE_SYSTEM) {
                        Text(stringResource(R.string.language_system), fontSize = 15.sp)
                        Text(
                            stringResource(R.string.language_system_hint, L10n.displayName(L10n.systemLocale().language)),
                            fontSize = 12.sp, color = AppColors.TextGrey,
                        )
                    } else {
                        Text(L10n.displayName(code), fontSize = 15.sp)
                    }
                }
                if (selected) Icon(Icons.Filled.Check, null, tint = AppColors.Accent)
            }
        }
    }
}
