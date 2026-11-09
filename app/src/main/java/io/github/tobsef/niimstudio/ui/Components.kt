package io.github.tobsef.niimstudio.ui

import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.runtime.DisposableEffect
import io.github.tobsef.niimstudio.data.Store
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.res.stringResource
import io.github.tobsef.niimstudio.R
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

/** Key of the [StepperRow] whose inline editor is open; only one is open at a time. */
private var openStepper by mutableStateOf<String?>(null)

/**
 * "Label   –  value  +" row. Tapping the value expands an inline editor below it
 * (no popup, every change is applied live): step size between ◀ ▶, rounding to 1 / 5 / 10,
 * direct input and the last three values set by hand for this unit.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
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
    /** Language-independent test tag ("stepper_<tag>") for UI automation. */
    tag: String? = null,
) {
    val key = "$label|$unit"
    val current by rememberUpdatedState(value)
    val steps = remember(step, unit) { ValueTools.steps(step, unit) }
    var stepIndex by remember(key) { mutableIntStateOf(steps.indexOfFirst { kotlin.math.abs(it - step) < 1e-4f }.coerceAtLeast(0)) }
    val curStep = steps[stepIndex.coerceIn(steps.indices)]
    val open = openStepper == key
    val unitKey = unit.trim()
    /** Result of step-button changes that still has to go into the recent values. */
    var pending by remember(key) { mutableStateOf<Float?>(null) }

    fun storeRecent(v: Float) = Store.updateSettings { st ->
        st.copy(recentValues = st.recentValues + (unitKey to ValueTools.pushRecent(st.recentValues[unitKey].orEmpty(), v)))
    }
    fun flushPending() {
        pending?.let(::storeRecent)
        pending = null
    }
    /** Applies a value; step changes are remembered once they settle, explicit choices at once. */
    fun set(v: Float, byHand: Boolean = false, stepped: Boolean = false) {
        val c = v.coerceIn(min, max)
        onChange(c)
        when {
            byHand -> {
                pending = null
                storeRecent(c)
            }
            stepped -> pending = c
        }
    }

    // Step changes count as "set by hand" when the user pauses, closes the editor or leaves
    // (another element selected, other tab) – whichever comes first.
    LaunchedEffect(pending) {
        if (pending != null) {
            delay(1500)
            flushPending()
        }
    }
    LaunchedEffect(open) { if (!open) flushPending() }
    DisposableEffect(key) { onDispose { flushPending() } }

    // The editor unfolds below the row: scroll the panel so that all of it is visible.
    val intoView = remember { BringIntoViewRequester() }
    LaunchedEffect(open) {
        if (open) {
            delay(320) // let the unfold animation finish
            intoView.bringIntoView()
        }
    }

    Column(modifier.fillMaxWidth().bringIntoViewRequester(intoView)) {
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), fontSize = 15.sp)
            RepeatButton(Icons.Filled.Remove, stringResource(R.string.less), { set(current - curStep, stepped = true) })
            Box(
                Modifier
                    .width(84.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (open) AppColors.AccentSoft else Color.Transparent)
                    .then(if (tag != null) Modifier.testTag("stepper_$tag") else Modifier)
                    .clickable { openStepper = if (open) null else key },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    format(value) + unit, fontSize = 15.sp,
                    color = if (open) AppColors.Accent else Color.Unspecified,
                    fontWeight = if (open) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                )
            }
            RepeatButton(Icons.Filled.Add, stringResource(R.string.more), { set(current + curStep, stepped = true) })
        }
        AnimatedVisibility(open) {
            InlineValueEditor(
                value = value,
                unit = unit,
                step = curStep,
                onNextStep = { stepIndex = (stepIndex + 1) % steps.size },
                min = min,
                max = max,
                recents = Store.settings.collectAsState().value.recentValues[unitKey].orEmpty().filter { it in min..max },
                onStep = { d -> set(current + d, stepped = true) },
                onSet = { v, byHand -> set(v, byHand = byHand) },
                onDone = { openStepper = null },
            )
        }
    }
}

@Composable
private fun InlineValueEditor(
    value: Float,
    unit: String,
    step: Float,
    onNextStep: () -> Unit,
    min: Float,
    max: Float,
    recents: List<Float>,
    onStep: (Float) -> Unit,
    onSet: (Float, Boolean) -> Unit,
    onDone: () -> Unit,
) {
    val sep = java.text.DecimalFormatSymbols.getInstance(io.github.tobsef.niimstudio.L10n.locale()).decimalSeparator
    var text by remember { mutableStateOf(ValueTools.plain(value, sep)) }
    var focused by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    // Keep the field in sync with changes made elsewhere (step buttons, chips, canvas), also
    // while it has focus – otherwise losing focus would write the stale text back.
    LaunchedEffect(value) {
        val typed = ValueTools.parse(text)
        if (typed == null || kotlin.math.abs(typed - value) > 1e-4f) text = ValueTools.plain(value, sep)
    }

    // Fixed grid: the first column (input field / row labels) has a constant width and
    // every row a constant height, so nothing moves while values change.
    val firstCol = 84.dp // four digits + decimal separator + unit
    val rowH = 40.dp
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Row 1: input field  ·  ◀ step ▶
        Row(Modifier.height(rowH), verticalAlignment = Alignment.CenterVertically) {
            Row(
                // Room for four digits plus the unit – more never fits on a label.
                Modifier
                    .width(firstCol)
                    .height(rowH)
                    .clip(RoundedCornerShape(10.dp))
                    .background(AppColors.Group)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    text,
                    { t ->
                        text = t
                        ValueTools.parse(t)?.let { onSet(it, false) }
                    },
                    Modifier.weight(1f).testTag("stepper_input").onFocusChanged { st ->
                        if (focused && !st.isFocused) ValueTools.parse(text)?.let { onSet(it, true) }
                        focused = st.isFocused
                    },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        ValueTools.parse(text)?.let { onSet(it, true) }
                        focus.clearFocus()
                        onDone()
                    }),
                )
                if (unit.isNotBlank()) Text(unit.trim(), Modifier.padding(start = 2.dp), fontSize = 13.sp, color = AppColors.TextGrey)
            }
            RepeatButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.less), { onStep(-step) })
            Box(
                Modifier
                    .width(96.dp)
                    .height(rowH)
                    .clip(RoundedCornerShape(10.dp))
                    .background(AppColors.Group)
                    .clickable(onClick = onNextStep),
                contentAlignment = Alignment.Center,
            ) {
                Text("± " + ValueTools.plain(step, sep) + unit, fontSize = 13.sp, color = AppColors.Accent, maxLines = 1)
            }
            RepeatButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.more), { onStep(step) })
        }
        // Row 2: rounding
        ChipRow(stringResource(R.string.stepper_round), firstCol, rowH) {
            val rounds = ValueTools.roundings(value).filter { it in min..max }
            if (rounds.isEmpty()) Text("—", fontSize = 13.sp, color = AppColors.TextGrey)
            for (r in rounds) ValueChip(ValueTools.plain(r, sep)) { onSet(r, true) }
        }
        // Row 3: last values set by hand
        ChipRow(stringResource(R.string.stepper_recent), firstCol, rowH) {
            if (recents.isEmpty()) Text(stringResource(R.string.stepper_no_recent), fontSize = 13.sp, color = AppColors.TextGrey)
            for (r in recents) ValueChip(ValueTools.plain(r, sep)) { onSet(r, true) }
        }
    }
}

/** A labelled row of value chips; the label column has the width of the input field. */
@Composable
private fun ChipRow(label: String, labelWidth: androidx.compose.ui.unit.Dp, height: androidx.compose.ui.unit.Dp, content: @Composable RowScope.() -> Unit) {
    Row(Modifier.height(height), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(labelWidth).padding(start = 4.dp), fontSize = 13.sp, color = AppColors.TextGrey, maxLines = 1)
        // Up to four chips; scrolls sideways instead of clipping on narrow screens.
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            content = content,
        )
    }
}

@Composable
private fun ValueChip(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(AppColors.AccentSoft)
            .testTag("chip_$text")
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(text, color = AppColors.Accent, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
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
                    .testTag("segment_$v")
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

/**
 * Exposes Compose test tags as Android resource ids, so UI automation (tools/teaser/record.py)
 * can find controls independent of the app language. Dialogs and sheets are separate windows
 * and need it on their own root.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
fun Modifier.automationIds(): Modifier = semantics { testTagsAsResourceId = true }
