package dev.b18print.ui

import androidx.activity.compose.BackHandler
import androidx.compose.ui.res.stringResource
import dev.b18print.R
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.b18print.data.Store
import dev.b18print.model.TextEl
import dev.b18print.printer.ConnState
import dev.b18print.printer.PrinterManager

@Composable
fun EditorScreen(vm: EditorViewModel, onOpenSettings: () -> Unit, onOpenProfiles: () -> Unit) {
    val ctx = LocalContext.current
    // Back leaves sub-pages (pickers, element tabs, templates …) first; only the start page exits.
    BackHandler(enabled = !vm.atHome) { vm.navigateBack() }
    val conn by PrinterManager.state.collectAsState()
    val profiles by Store.profiles.collectAsState()
    val settings by Store.settings.collectAsState()
    var profileMenu by remember { mutableStateOf(false) }
    var printerMenu by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var confirmNew by remember { mutableStateOf(false) }
    var saveOpen by remember { mutableStateOf(false) }
    var printOpen by remember { mutableStateOf(false) }
    var editText by remember { mutableStateOf<TextEl?>(null) }
    var lastEditRequest by remember { mutableIntStateOf(vm.editRequest) }

    LaunchedEffect(vm.editRequest) {
        if (vm.editRequest != lastEditRequest) {
            lastEditRequest = vm.editRequest
            (vm.selected as? TextEl)?.let { editText = it }
        }
    }

    Column(Modifier.fillMaxSize().background(Color.White)) {
        // ---- top bar
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { confirmNew = true }) { Icon(Icons.Outlined.Close, stringResource(R.string.new_label), Modifier.size(28.dp)) }
            IconButton(onClick = vm::undo, enabled = vm.canUndo) { Icon(Icons.AutoMirrored.Outlined.Undo, stringResource(R.string.undo), Modifier.size(28.dp)) }
            IconButton(onClick = vm::redo, enabled = vm.canRedo) { Icon(Icons.AutoMirrored.Outlined.Redo, stringResource(R.string.redo), Modifier.size(28.dp)) }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { confirmClear = true }, enabled = vm.doc.elements.isNotEmpty()) {
                Icon(Icons.Outlined.CleaningServices, stringResource(R.string.clear_all), Modifier.size(28.dp))
            }
            IconButton(onClick = vm::togglePortrait) { Icon(Icons.Outlined.ScreenRotation, stringResource(R.string.rotate_label), Modifier.size(28.dp)) }
            IconButton(onClick = onOpenSettings) { Icon(Icons.Outlined.Settings, stringResource(R.string.settings), Modifier.size(28.dp)) }
            Box {
                val (icon, tint) = when (conn) {
                    is ConnState.Connected -> Icons.Filled.BluetoothConnected to AppColors.Ok
                    is ConnState.Connecting -> Icons.AutoMirrored.Filled.BluetoothSearching to AppColors.Orange
                    is ConnState.Failed -> Icons.Filled.Bluetooth to AppColors.Accent
                    ConnState.Disconnected -> Icons.Filled.Bluetooth to Color(0xFF9A9AA0)
                }
                Box(
                    Modifier.padding(horizontal = 6.dp).size(42.dp).clip(CircleShape).background(tint.copy(alpha = 0.12f))
                        .clickable { printerMenu = true },
                    contentAlignment = Alignment.Center,
                ) { Icon(icon, stringResource(R.string.printer), tint = tint) }
                DropdownMenu(printerMenu, { printerMenu = false }) {
                    val s = settings
                    Text(
                        when (val c = conn) {
                            is ConnState.Connected -> "${c.name}\n${c.info}"
                            else -> connText(c, s)
                        },
                        Modifier.padding(horizontal = 16.dp, vertical = 8.dp), fontSize = 13.sp, color = AppColors.TextGrey,
                    )
                    HorizontalDivider()
                    if (conn is ConnState.Connected) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.disconnect)) }, onClick = { PrinterManager.disconnect(); printerMenu = false })
                    } else if (s.deviceAddress != null) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.connect)) }, onClick = { PrinterManager.connectSaved(ctx); printerMenu = false })
                    }
                    DropdownMenuItem(text = { Text(stringResource(R.string.choose_printer_menu)) }, onClick = { onOpenSettings(); printerMenu = false })
                }
            }
        }
        HorizontalDivider(color = AppColors.Divider)

        // ---- profile bar
        Row(
            Modifier.fillMaxWidth().height(44.dp).background(Color.White).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                Row(Modifier.clickable { profileMenu = true }, verticalAlignment = Alignment.CenterVertically) {
                    Text(profileName(vm.doc.profile), fontSize = 16.sp, color = Color(0xFF333333), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Icon(Icons.Filled.KeyboardArrowDown, null, tint = Color(0xFF666666))
                }
                DropdownMenu(profileMenu, { profileMenu = false }) {
                    for (p in profiles) {
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(profileName(p), fontWeight = if (p.id == vm.doc.profile.id) FontWeight.SemiBold else FontWeight.Normal)
                                    Text(p.sizeLabel, fontSize = 12.sp, color = AppColors.TextGrey)
                                }
                            },
                            leadingIcon = { if (p.id == vm.doc.profile.id) Icon(Icons.Filled.Check, null, tint = AppColors.Accent) else Spacer(Modifier.width(24.dp)) },
                            onClick = { vm.setProfile(p); profileMenu = false },
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.manage_profiles)) },
                        leadingIcon = { Icon(Icons.Outlined.Tune, null) },
                        onClick = { profileMenu = false; onOpenProfiles() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.new_profile_menu)) },
                        leadingIcon = { Icon(Icons.Filled.Add, null) },
                        onClick = { profileMenu = false; onOpenProfiles() },
                    )
                }
            }
            Icon(Icons.Outlined.Info, null, Modifier.size(20.dp), tint = Color(0xFF666666))
            Text(
                " ${vm.doc.profile.sizeLabel}" + when (vm.doc.sides) {
                    dev.b18print.model.SideMode.SINGLE -> ""
                    dev.b18print.model.SideMode.FLAG -> " · " + stringResource(R.string.sides_flag_short)
                    dev.b18print.model.SideMode.WRAP -> " · " + stringResource(R.string.sides_wrap_short)
                } + (vm.doc.templateName?.let { " · $it" } ?: ""),
                fontSize = 14.sp, color = Color(0xFF555555), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }

        // ---- canvas
        LabelCanvas(vm, Modifier.weight(1f).fillMaxWidth())

        // ---- panel
        BottomPanel(vm)

        // ---- bottom actions
        HorizontalDivider(color = AppColors.Divider)
        Row(Modifier.fillMaxWidth().height(60.dp).background(Color.White), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).fillMaxSize().clickable { saveOpen = true },
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Save, null, Modifier.size(28.dp))
                Text("  " + stringResource(R.string.save), fontSize = 18.sp)
            }
            Box(Modifier.width(1.dp).height(28.dp).background(AppColors.Divider))
            Row(
                Modifier.weight(1f).fillMaxSize().clickable { printOpen = true },
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Print, null, Modifier.size(28.dp))
                Text("  " + stringResource(R.string.print), fontSize = 18.sp)
            }
        }
    }

    if (confirmClear) ConfirmDialog(stringResource(R.string.clear_all_title), stringResource(R.string.clear_all_text), stringResource(R.string.action_delete), { confirmClear = false }) { vm.clearAll() }
    if (confirmNew) ConfirmDialog(stringResource(R.string.new_label_title), stringResource(R.string.new_label_text), stringResource(R.string.new_action), { confirmNew = false }) { vm.newLabel() }
    if (saveOpen) SaveTemplateDialog(vm) { saveOpen = false }
    if (printOpen) PrintSheet(vm, onDismiss = { printOpen = false }, onOpenSettings = { printOpen = false; onOpenSettings() })
    editText?.let { e -> TextEditDialog(vm, e) { editText = null } }
}
