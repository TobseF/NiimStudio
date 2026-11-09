package io.github.tobsef.niimstudio.ui

import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.tobsef.niimstudio.R
import io.github.tobsef.niimstudio.model.ConnLabel
import io.github.tobsef.niimstudio.model.ConnectorEl
import io.github.tobsef.niimstudio.model.LabelProfile
import io.github.tobsef.niimstudio.model.SideMode
import io.github.tobsef.niimstudio.render.Connector
import io.github.tobsef.niimstudio.render.Connectors

/** Connector pictogram drawn with the same paths as on the label. */
@Composable
fun ConnectorIcon(con: Connector, modifier: Modifier = Modifier, color: Color = Color.Black) {
    Canvas(modifier) {
        val pad = Connectors.LW
        val h = minOf(size.height, size.width / (con.ar + pad)) * 0.92f
        drawIntoCanvas { c ->
            val nc = c.nativeCanvas
            nc.save()
            nc.translate((size.width - con.ar * h) / 2, (size.height - h) / 2)
            Connectors.draw(nc, con, h, color.toArgb())
            nc.restore()
        }
    }
}

/** Grid of all connector types; tap adds a connector or changes the selected one. */
@Composable
fun ConnectorPicker(vm: EditorViewModel) {
    val sel = vm.selected as? ConnectorEl
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(Connectors.all, key = { it.key }) { con ->
            val current = !vm.connectorPickerForNew && sel?.key == con.key
            Column(
                Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .border(if (current) 2.dp else 1.dp, if (current) AppColors.Accent else AppColors.Divider, RoundedCornerShape(14.dp))
                    .background(Color.White)
                    .testTag("connector_${con.key}")
                    .clickable { vm.addOrReplaceConnector(con.key) }
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ConnectorIcon(con, Modifier.fillMaxWidth().height(34.dp))
                Text(
                    con.name, Modifier.padding(top = 6.dp),
                    fontSize = 12.sp, color = Color(0xFF444444), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
fun ConnectorContent(vm: EditorViewModel, e: ConnectorEl) {
    val con = Connectors.find(e.key)
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (con != null) {
            Box(
                Modifier.clip(RoundedCornerShape(12.dp)).background(AppColors.Group).padding(8.dp),
                contentAlignment = Alignment.Center,
            ) { ConnectorIcon(con, Modifier.size(64.dp, 30.dp)) }
        }
        Text(con?.name ?: e.key, Modifier.weight(1f).padding(start = 12.dp), fontSize = 16.sp)
        OutlinedButton(onClick = { vm.connectorPickerForNew = false; vm.tab = PanelTab.CONNECTOR }) {
            Text(stringResource(R.string.connector_change))
        }
    }
    SectionLabel(stringResource(R.string.connector_label))
    Segmented(
        listOf(
            ConnLabel.BELOW to stringResource(R.string.connector_label_below),
            ConnLabel.BESIDE to stringResource(R.string.connector_label_beside),
            ConnLabel.NONE to stringResource(R.string.connector_label_none),
        ),
        e.label, { v -> vm.updateElement(e.id) { (it as ConnectorEl).copy(label = v) } },
    )
    if (e.label != ConnLabel.NONE) {
        OutlinedTextField(
            e.text, { v -> vm.updateElement(e.id, "conntext-${e.id}") { (it as ConnectorEl).copy(text = v) } },
            Modifier.fillMaxWidth().padding(top = 10.dp),
            label = { Text(stringResource(R.string.connector_text)) },
            placeholder = { Text(con?.name ?: "") },
            singleLine = true,
        )
    }
    Group(Modifier.padding(top = 10.dp)) {
        Text(stringResource(R.string.connector_icon_scale) + ": ${(e.iconScale * 100).toInt()} %", Modifier.padding(top = 6.dp), fontSize = 15.sp)
        AccentSlider(e.iconScale, 0.2f..1f) { v -> vm.updateElement(e.id, "connscale-${e.id}") { (it as ConnectorEl).copy(iconScale = (v * 20).toInt() / 20f) } }
        if (e.label != ConnLabel.NONE) {
            SwitchRow(stringResource(R.string.uppercase), e.upper, { v -> vm.updateElement(e.id) { (it as ConnectorEl).copy(upper = v) } })
        }
    }
}

/** Single panel, cable flag or wrap label, with wrap zone and fold marks. */
@Composable
fun LayoutPanel(vm: EditorViewModel) {
    val d = vm.doc
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)) {
        Segmented(
            listOf(
                SideMode.SINGLE to stringResource(R.string.sides_single),
                SideMode.FLAG to stringResource(R.string.sides_flag),
                SideMode.WRAP to stringResource(R.string.sides_wrap),
            ),
            d.sides, { m -> vm.updateLayout { it.copy(sides = m) } },
        )
        Text(
            stringResource(
                when (d.sides) {
                    SideMode.SINGLE -> R.string.sides_hint_single
                    SideMode.FLAG -> R.string.sides_hint_flag
                    SideMode.WRAP -> R.string.sides_hint_wrap
                },
            ),
            Modifier.padding(top = 10.dp), color = AppColors.TextGrey, fontSize = 13.sp,
        )
        if (d.sides != SideMode.SINGLE) {
            Group(Modifier.padding(top = 10.dp)) {
                StepperRow(
                    stringResource(R.string.cable_diameter), d.cableDiameterMm, 0.5f,
                    { v -> vm.updateLayout("cable") { it.copy(cableDiameterMm = v) } },
                    min = 0.5f, max = 60f, unit = " mm",
                )
                SwitchRow(stringResource(R.string.auto_wrap_zone), d.autoWrapZone, { v ->
                    vm.updateLayout { it.copy(autoWrapZone = v, wrapZoneMm = it.wrapZone) }
                })
                if (!d.autoWrapZone) {
                    StepperRow(
                        stringResource(R.string.wrap_zone), d.wrapZoneMm, 0.5f,
                        { v -> vm.updateLayout("wrap") { it.copy(wrapZoneMm = v) } },
                        min = 0f, max = 200f, unit = " mm",
                    )
                }
                SwitchRow(stringResource(R.string.fold_marks), d.foldMarks, { v -> vm.updateLayout { it.copy(foldMarks = v) } })
            }
            val f = { v: Float -> LabelProfile.fmt((v * 10).toInt() / 10f) }
            Text(
                stringResource(R.string.layout_summary, f(d.totalLengthMm), f(d.panelLengthMm), f(d.wrapZone)),
                Modifier.padding(top = 10.dp), fontSize = 13.sp,
            )
            Text(stringResource(R.string.layout_design_hint), Modifier.padding(top = 4.dp), color = AppColors.TextGrey, fontSize = 13.sp)
            if (d.panelLengthMm < 8f) {
                Text(stringResource(R.string.layout_panel_short), Modifier.padding(top = 6.dp), color = AppColors.Accent, fontSize = 13.sp)
            }
        }
    }
}
