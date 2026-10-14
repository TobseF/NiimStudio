package dev.b18print.ui

import androidx.compose.ui.res.stringResource
import dev.b18print.R
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object AppColors {
    val Accent = Color(0xFFF5413D)
    val AccentSoft = Color(0xFFFFEDEC)
    val Selection = Color(0xFF1890FF)
    val CanvasBg = Color(0xFFF1F1F5)
    val Group = Color(0xFFF7F7F9)
    val TabBg = Color(0xFFF2F2F5)
    val TextGrey = Color(0xFF8A8A8F)
    val Divider = Color(0xFFE6E6EA)
    val Orange = Color(0xFFE29A1B)
    val Ok = Color(0xFF2EB85C)
}

val AppColorScheme: ColorScheme = lightColorScheme(
    primary = AppColors.Accent,
    onPrimary = Color.White,
    primaryContainer = AppColors.AccentSoft,
    onPrimaryContainer = Color(0xFF5A0B08),
    secondary = AppColors.Selection,
    background = Color.White,
    surface = Color.White,
    surfaceVariant = AppColors.Group,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = AppColors.Group,
    outline = Color(0xFFD0D0D6),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = AppColorScheme, content = content)

/** Light grey rounded group as in the original panels. */
@Composable
fun Group(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.Group)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        content = content,
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) =
    Text(text, modifier.padding(start = 4.dp, top = 10.dp, bottom = 4.dp), color = AppColors.TextGrey, fontSize = 13.sp)

/** Square toggle button with an icon or text; selected state uses the accent colour on white. */
@Composable
fun ToggleTile(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    text: String? = null,
) {
    val tint = when {
        !enabled -> Color(0xFFC8C8CC)
        selected -> AppColors.Accent
        else -> Color(0xFF222222)
    }
    Box(
        modifier
            .height(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Color.White else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) Icon(icon, text, tint = tint, modifier = Modifier.size(24.dp))
        else if (text != null) Text(text, color = tint, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

/** Icon button that repeats while pressed (used for nudging and steppers). */
@Composable
fun RepeatButton(icon: ImageVector, desc: String, onStep: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, tint: Color = Color(0xFF222222), iconSize: androidx.compose.ui.unit.Dp = 24.dp) {
    val step by rememberUpdatedState(onStep)
    val scope = rememberCoroutineScope()
    Box(
        modifier
            .size(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown()
                    step()
                    val job = scope.launch {
                        delay(400)
                        while (true) {
                            step()
                            delay(70)
                        }
                    }
                    waitForUpOrCancellation()
                    job.cancel()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, desc, Modifier.size(iconSize), tint = if (enabled) tint else Color(0xFFC8C8CC))
    }
}

/** "Label   –  value  +" row; tapping the value opens a numeric input dialog. */
@Composable
fun StepperRow(
    label: String,
    value: Float,
    step: Float,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    min: Float = -9999f,
    max: Float = 9999f,
    format: (Float) -> String = { "%.1f".format(it) },
    unit: String = "",
) {
    val current by rememberUpdatedState(value)
    var editing by remember { mutableStateOf(false) }
    Row(modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontSize = 15.sp)
        RepeatButton(Icons.Filled.Remove, stringResource(R.string.less), { onChange((current - step).coerceIn(min, max)) })
        Text(
            format(value) + unit,
            Modifier.width(72.dp).clickable { editing = true },
            fontSize = 15.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        RepeatButton(Icons.Filled.Add, stringResource(R.string.more), { onChange((current + step).coerceIn(min, max)) })
    }
    if (editing) {
        NumberDialog(label, value, onDismiss = { editing = false }) {
            onChange(it.coerceIn(min, max))
            editing = false
        }
    }
}

@Composable
fun NumberDialog(title: String, value: Float, onDismiss: () -> Unit, onOk: (Float) -> Unit) {
    var text by remember { mutableStateOf(if (value % 1f == 0f) value.toInt().toString() else "%.2f".format(value).trimEnd('0')) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                text, { text = it },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        },
        confirmButton = {
            TextButton(onClick = { text.replace(',', '.').toFloatOrNull()?.let(onOk) }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, subtitle: String? = null) {
    Row(
        modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 15.sp)
            if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = AppColors.TextGrey)
        }
        Switch(
            checked, onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = AppColors.Accent,
                uncheckedTrackColor = Color(0xFFE9E9EC),
                uncheckedBorderColor = Color.Transparent,
                uncheckedThumbColor = Color.White,
            ),
        )
    }
}

/** Compact segmented choice. */
@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.Group)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        for ((v, label) in options) {
            val sel = v == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (sel) Color.White else Color.Transparent)
                    .clickable { onSelect(v) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (sel) AppColors.Accent else Color(0xFF444444),
                    fontSize = 13.sp,
                    fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Chip-like selectable pill. */
@Composable
fun Pill(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) AppColors.AccentSoft else AppColors.Group)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(text, color = if (selected) AppColors.Accent else Color(0xFF333333), fontSize = 13.sp)
    }
}

@Composable
fun RowScope.VDivider() = Box(Modifier.width(1.dp).height(28.dp).background(AppColors.Divider))
