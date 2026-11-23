package com.aiec.elevatoros.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiec.elevatoros.domain.Phase
import com.aiec.elevatoros.domain.Stage
import com.aiec.elevatoros.i18n.I18n
import com.aiec.elevatoros.i18n.LocalLang
import com.aiec.elevatoros.i18n.t
import com.aiec.elevatoros.ui.theme.Aiec

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiecTopBar(
    title: String, onBack: (() -> Unit)?, onListen: (() -> Unit)?, onLanguage: () -> Unit, practice: Boolean,
) {
    Column {
        if (practice) {
            Text(
                "🎓 " + t("practice_banner"), color = Aiec.Navy, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().background(Aiec.Gold).padding(vertical = 4.dp)
            )
        }
        TopAppBar(
            title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() }) },
            navigationIcon = { if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = t("back")) } },
            actions = {
                if (onListen != null) IconButton(onClick = onListen) { Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = t("listen")) }
                val lang = LocalLang.current
                TextButton(onClick = onLanguage, modifier = Modifier.semantics { contentDescription = I18n.t(lang, "language") }) {
                    Text(when (lang) { "mr" -> "म"; "hi" -> "हि"; else -> "EN" }, color = Aiec.Gold, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Aiec.Navy, titleContentColor = Color.White, navigationIconContentColor = Color.White, actionIconContentColor = Color.White)
        )
    }
}

/** The signature big dark button ("Start My Next Task"), at least 64 dp tall. */
@Composable
fun BigButton(text: String, icon: String? = null, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = enabled, shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
        border = BorderStroke(0.dp, Color.Transparent),
        modifier = modifier.fillMaxWidth().heightIn(min = 64.dp)
    ) {
        if (icon != null) Text(icon, fontSize = 22.sp, modifier = Modifier.padding(end = 10.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
    }
}

@Composable
fun SecondaryButton(text: String, icon: String? = null, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick, enabled = enabled, shape = RoundedCornerShape(14.dp), modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline)
    ) {
        if (icon != null) Text(icon, fontSize = 20.sp, modifier = Modifier.padding(end = 8.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp).semantics { heading() })
}

@Composable
fun AiecCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, highlight: Boolean = false, content: @Composable () -> Unit) {
    val m = modifier.fillMaxWidth().padding(vertical = 5.dp)
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    val border = if (highlight) BorderStroke(2.dp, Aiec.Gold) else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    if (onClick != null) Card(onClick = onClick, modifier = m, colors = colors, border = border, shape = RoundedCornerShape(16.dp)) { Box(Modifier.padding(14.dp)) { content() } }
    else Card(modifier = m, colors = colors, border = border, shape = RoundedCornerShape(16.dp)) { Box(Modifier.padding(14.dp)) { content() } }
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text, color = color, style = MaterialTheme.typography.labelMedium,
        modifier = modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.12f)).padding(horizontal = 10.dp, vertical = 3.dp)
    )
}

/**
 * The two bars always shown together (original "Two bars on every task"): work done on top, time used against the
 * real limit below, with on time / behind / late — or grey "Paused" when the work waits on someone else.
 */
@Composable
fun TwoBars(progress: Float, progressLabel: String, daysUsed: Int, limit: Int, paused: Boolean = false, done: Boolean = false) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(progressLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)), color = Aiec.Ok, trackColor = MaterialTheme.colorScheme.surfaceVariant, drawStopIndicator = {})
        Spacer(Modifier.height(6.dp))
        val (color, key) = when {
            done -> Aiec.Ok to "bar_done"
            paused -> Aiec.Paused to "bar_paused"
            daysUsed > limit -> Aiec.Late to "bar_late"
            daysUsed >= limit - 1 && limit > 1 -> Aiec.Warn to "bar_behind"
            else -> Aiec.Ok to "bar_on_time"
        }
        Text(t(key) + " · " + t("bar_day_of", "n" to daysUsed.toString(), "limit" to limit.toString()), style = MaterialTheme.typography.labelMedium, color = color)
        LinearProgressIndicator(progress = { if (done) 1f else (daysUsed.toFloat() / maxOf(1, limit)).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)), color = color, trackColor = MaterialTheme.colorScheme.surfaceVariant, drawStopIndicator = {})
    }
}

/** Seven phases as tiles; current phase highlighted in gold (like the original "My lift" stepper). */
@Composable
fun PhaseStepper(stage: Stage) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Phase.entries.forEachIndexed { i, ph ->
            val cur = stage.phaseIndex
            val done = stage == Stage.AMC || (cur >= 0 && i < cur)
            val now = i == cur && stage != Stage.AMC
            val bg = when { done -> Aiec.Ok.copy(alpha = 0.14f); now -> Aiec.Gold.copy(alpha = 0.25f); else -> MaterialTheme.colorScheme.surfaceVariant }
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(bg).then(if (now) Modifier.border(2.dp, Aiec.Gold, RoundedCornerShape(10.dp)) else Modifier).padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(if (done) "✅" else ph.icon, fontSize = 18.sp)
                Text(t("ph_" + ph.key), style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp), maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
fun PinPad(length: Int, entered: String, onDigit: (Char) -> Unit, onDelete: () -> Unit, enabled: Boolean = true) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(vertical = 18.dp).semantics { contentDescription = "${entered.length} / $length" }) {
            repeat(length) { i ->
                Box(Modifier.size(20.dp).clip(CircleShape).background(if (i < entered.length) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
            }
        }
        val rows = listOf("123", "456", "789", "x0<")
        rows.forEach { r ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                r.forEach { c ->
                    when (c) {
                        'x' -> Spacer(Modifier.size(78.dp))
                        '<' -> Surface(onClick = onDelete, enabled = enabled, shape = CircleShape, color = Color.Transparent, modifier = Modifier.size(78.dp)) {
                            Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.Backspace, contentDescription = t("remove")) }
                        }
                        else -> Surface(onClick = { onDigit(c) }, enabled = enabled, shape = CircleShape, color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), modifier = Modifier.size(78.dp)) {
                            Box(contentAlignment = Alignment.Center) { Text(c.toString(), fontSize = 28.sp, fontWeight = FontWeight.SemiBold) }
                        }
                    }
                }
            }
        }
    }
}

/** Finger signature. [onDone] receives a white-background bitmap of the drawing. */
@Composable
fun SignaturePad(label: String, signed: Boolean, onDone: (Bitmap) -> Unit, onClear: () -> Unit) {
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    val current = remember { mutableStateListOf<Offset>() }
    val wh = remember { IntArray(2) { 1 } }
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Box(
            Modifier.fillMaxWidth().aspectRatio(2.4f).padding(top = 6.dp).clip(RoundedCornerShape(12.dp)).background(Color.White)
                .border(2.dp, if (signed) Aiec.Ok else MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
        ) {
            Canvas(Modifier.matchParentSize().pointerInput(signed) {
                if (signed) return@pointerInput
                detectDragGestures(
                    onDragStart = { current.clear(); current.add(it) },
                    onDrag = { ch, _ -> current.add(ch.position) },
                    onDragEnd = { strokes.add(current.toList()); current.clear() }
                )
            }) {
                wh[0] = size.width.toInt().coerceAtLeast(1); wh[1] = size.height.toInt().coerceAtLeast(1)
                (strokes + listOf(current.toList())).forEach { s ->
                    if (s.size > 1) {
                        val p = Path().apply { moveTo(s[0].x, s[0].y); s.drop(1).forEach { lineTo(it.x, it.y) } }
                        drawPath(p, Color(0xFF0F1B33), style = Stroke(width = 5f, cap = StrokeCap.Round))
                    }
                }
            }
            if (strokes.isEmpty() && current.isEmpty() && !signed) Text("✍️ " + t("sign_here"), color = Color.Gray, modifier = Modifier.align(Alignment.Center))
            if (signed) Text("✅ " + t("signed"), color = Aiec.Ok, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Center))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
            TextButton(onClick = { strokes.clear(); current.clear(); onClear() }) { Text(t("clear")) }
            if (!signed) TextButton(enabled = strokes.isNotEmpty(), onClick = {
                val bmp = Bitmap.createBitmap(wh[0], wh[1], Bitmap.Config.ARGB_8888)
                val c = android.graphics.Canvas(bmp); c.drawColor(android.graphics.Color.WHITE)
                val paint = android.graphics.Paint().apply { color = 0xFF0F1B33.toInt(); strokeWidth = 5f; style = android.graphics.Paint.Style.STROKE; isAntiAlias = true; strokeCap = android.graphics.Paint.Cap.ROUND }
                strokes.forEach { s -> for (i in 1 until s.size) c.drawLine(s[i - 1].x, s[i - 1].y, s[i].x, s[i].y, paint) }
                onDone(bmp)
            }) { Text(t("save_signature")) }
        }
    }
}


@Composable
fun ChoiceChips(options: List<Pair<String, String>>, selected: String?, onSelect: (String) -> Unit) {
    FlowRowCompat {
        options.forEach { (value, label) ->
            val sel = value == selected
            Surface(
                onClick = { onSelect(value) }, shape = RoundedCornerShape(12.dp),
                color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.5.dp, if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                modifier = Modifier.padding(end = 8.dp, bottom = 8.dp).heightIn(min = 48.dp).semantics { contentDescription = label + if (sel) " ✓" else "" }
            ) {
                Box(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                    Text(label, color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun FlowRowCompat(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth()) { content() }
}

@Composable
fun InfoBox(text: String, color: Color = MaterialTheme.colorScheme.secondaryContainer, icon: String = "ℹ️") {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(12.dp)).background(color).padding(12.dp)) {
        Text(icon, modifier = Modifier.padding(end = 8.dp)); Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun KeyValue(k: String, v: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(k, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(140.dp))
        Text(v, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
    }
}

@Composable
fun RowScope.Avatar(icon: String) {
    Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) { Text(icon, fontSize = 24.sp) }
    Spacer(Modifier.width(12.dp))
}

@Composable
fun ClickRow(onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, content = content)
}
