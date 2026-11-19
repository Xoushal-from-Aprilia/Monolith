package com.monolith.app.ui.components

import android.icu.text.BreakIterator
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monolith.app.core.FriendQr
import com.monolith.app.core.t
import com.monolith.app.data.MsgStatus
import com.monolith.app.ui.theme.Grotesk
import com.monolith.app.ui.theme.LocalDark
import com.monolith.app.ui.theme.Motion

// ====================================================================== shared transitions

val LocalSharedScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalNavScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

@Composable
fun Modifier.sharedBoundsKey(key: String): Modifier {
    val s = LocalSharedScope.current ?: return this
    val a = LocalNavScope.current ?: return this
    return with(s) { this@sharedBoundsKey.sharedBounds(rememberSharedContentState(key), animatedVisibilityScope = a) }
}

@Composable
fun Modifier.sharedElementKey(key: String): Modifier {
    val s = LocalSharedScope.current ?: return this
    val a = LocalNavScope.current ?: return this
    return with(s) { this@sharedElementKey.sharedElement(rememberSharedContentState(key), animatedVisibilityScope = a) }
}

// ====================================================================== press feedback

fun Modifier.bounceClick(scaleDown: Float = 0.94f, enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) scaleDown else 1f,
        spring(dampingRatio = 0.45f, stiffness = 700f),
        label = "bounce",
    )
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = interaction, indication = LocalIndication.current, enabled = enabled, onClick = onClick)
}

// ====================================================================== logo

/**
 * The Monolith mark: a 4:9 slab with a lit top face and a shaded side, standing on a horizon line.
 * [rise] animates it out of the ground (0..1), [sweep] moves a soft light reflection across the front face.
 */
@Composable
fun MonolithMark(modifier: Modifier = Modifier, rise: Float = 1f, sweep: Float = -1f, horizon: Float = 1f) {
    val dark = LocalDark.current
    val front = if (dark) Color(0xFFE6E6E6) else Color(0xFF0A0A0A)
    val side = if (dark) Color(0xFF9E9E9E) else Color(0xFF3A3A3A)
    val top = if (dark) Color(0xFFFFFFFF) else Color(0xFF8A8A8A)
    val line = if (dark) Color(0xFF4A4A4A) else Color(0xFFBDBDBD)
    Canvas(modifier.aspectRatio(0.62f)) {
        val w = size.width
        val h = size.height
        // geometry in "units": slab 21 wide, 46.5 tall, depth offset (5, -4); horizon spans 48
        val unit = h / 56f
        val ox = w / 2f - 13f * unit
        val baseY = h - 4f * unit
        fun p(x: Float, y: Float) = Offset(ox + x * unit, baseY - y * unit)
        val horizonHalf = 24f * unit * horizon
        drawLine(line, Offset(w / 2f - horizonHalf, baseY + 0.4f * unit), Offset(w / 2f + horizonHalf, baseY + 0.4f * unit), strokeWidth = 0.8f * unit)
        val lift = (1f - rise.coerceIn(0f, 1f)) * 50.5f
        clipRect(0f, 0f, w, baseY) {
            fun poly(pts: List<Offset>): Path = Path().apply {
                moveTo(pts[0].x, pts[0].y)
                for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
                close()
            }
            val l = -lift
            drawPath(poly(listOf(p(0f, 46.5f + l), p(5f, 50.5f + l), p(26f, 50.5f + l), p(21f, 46.5f + l))), top)
            drawPath(poly(listOf(p(21f, 46.5f + l), p(26f, 50.5f + l), p(26f, 4f + l), p(21f, 0f + l))), side)
            val frontPath = poly(listOf(p(0f, 0f + l), p(21f, 0f + l), p(21f, 46.5f + l), p(0f, 46.5f + l)))
            drawPath(frontPath, front)
            if (sweep in 0f..1f) {
                val x0 = ox + (-10f + sweep * 45f) * unit
                drawPath(
                    frontPath,
                    Brush.linearGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = if (dark) 0.35f else 0.16f), Color.Transparent),
                        start = Offset(x0, baseY),
                        end = Offset(x0 + 12f * unit, baseY - 30f * unit),
                    ),
                )
            }
        }
    }
}

/** looping, floating mark for empty states and loaders */
@Composable
fun FloatingMark(modifier: Modifier = Modifier, height: Dp = 72.dp) {
    val inf = rememberInfiniteTransition(label = "float")
    val dy by inf.animateFloat(0f, -6f, infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "dy")
    val sweep by inf.animateFloat(-0.2f, 1.2f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "sweep")
    MonolithMark(modifier.height(height).offset(y = dy.dp), sweep = sweep)
}

// ====================================================================== avatar

@Composable
fun Avatar(
    emoji: String,
    size: Dp,
    modifier: Modifier = Modifier,
    online: Boolean = false,
    ring: Boolean = false,
) {
    val density = LocalDensity.current
    val fs = with(density) { (size * 0.5f).toSp() }
    Box(modifier.size(size)) {
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .then(if (ring) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(emoji.ifBlank { "◼" }, fontSize = fs, textAlign = TextAlign.Center)
        }
        if (online) {
            val dot = (size * 0.28f).coerceAtLeast(10.dp)
            PulseDot(Modifier.align(Alignment.BottomEnd), dot)
        }
    }
}

@Composable
fun PulseDot(modifier: Modifier = Modifier, size: Dp = 10.dp, color: Color = MaterialTheme.colorScheme.primary) {
    val inf = rememberInfiniteTransition(label = "pulse")
    val s by inf.animateFloat(1f, 1.9f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Restart), label = "s")
    val a by inf.animateFloat(0.45f, 0f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Restart), label = "a")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(size)
                .graphicsLayer { scaleX = s; scaleY = s; alpha = a }
                .clip(CircleShape)
                .background(color)
        )
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .padding(2.dp)
                .clip(CircleShape)
                .background(color)
        )
    }
}

// ====================================================================== misc

@Composable
fun TypingDots(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    val inf = rememberInfiniteTransition(label = "typing")
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { i ->
            val y by inf.animateFloat(
                0f, -5f,
                infiniteRepeatable(tween(420, delayMillis = i * 140, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                label = "dot$i",
            )
            Box(Modifier.offset(y = y.dp).size(6.dp).clip(CircleShape).background(color))
        }
    }
}

@Composable
fun StatusTicks(status: Int, tint: Color, modifier: Modifier = Modifier) {
    val (icon, alpha) = when (status) {
        MsgStatus.FAILED -> Icons.Rounded.ErrorOutline to 1f
        MsgStatus.PENDING -> Icons.Rounded.Schedule to 0.6f
        MsgStatus.SENT -> Icons.Rounded.Done to 0.7f
        MsgStatus.DELIVERED -> Icons.Rounded.DoneAll to 0.55f
        else -> Icons.Rounded.DoneAll to 1f
    }
    Icon(icon, null, modifier.size(15.dp), tint = tint.copy(alpha = alpha))
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 1.4.sp,
    )
}

@Composable
fun EmptyState(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        FloatingMark(height = 64.dp)
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
fun BigTitle(text: String, modifier: Modifier = Modifier, subtitle: String? = null) {
    Column(modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 8.dp)) {
        Text(text, style = MaterialTheme.typography.displaySmall, fontFamily = Grotesk, fontWeight = FontWeight.Bold)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** round icon button with spring press */
@Composable
fun RoundAction(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    filled: Boolean = false,
    onClick: () -> Unit,
) {
    val bg = if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
    val fg = if (filled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(bg)
            .bounceClick(0.88f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = fg, modifier = Modifier.size(size * 0.48f))
    }
}

/** content that pops in with a spring the first time it is composed */
@Composable
fun PopIn(delayMs: Int = 0, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (delayMs > 0) kotlinx.coroutines.delay(delayMs.toLong())
        anim.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow))
    }
    Box(modifier.graphicsLayer {
        alpha = anim.value.coerceIn(0f, 1f)
        val s = 0.92f + 0.08f * anim.value
        scaleX = s; scaleY = s
        translationY = (1f - anim.value) * 24f
    }) { content() }
}

// ====================================================================== emoji picker

private val EMOJI_SETS = linkedMapOf(
    "Faccine" to "😀 😃 😄 😁 😆 😅 😂 🤣 😊 😇 🙂 😉 😌 😍 🥰 😘 😎 🤓 🧐 🤔 🤗 🤩 🥳 😴 🤯 😤 😭 😱 🥶 🤠 👻 💀 👽 🤖 😈 🙃",
    "Persone" to "👋 ✌️ 🤞 👍 👎 👏 🙌 🤝 🙏 💪 🧠 👀 ❤️ 🖤 🤍 🩶 💔 🔥 ✨ ⭐ 💫 👑 💎 🫶",
    "Attività" to "⚽ 🏀 🏐 🎾 🏓 🥊 🏋️ 🚴 🏃 🧘 🏊 ⛷️ 🎮 🎲 🎯 🎳 🎸 🎹 🎤 🎧 🎬 🎨 📸 🎉 🎊 🎈",
    "Cibo" to "☕ 🍵 🍺 🍷 🍸 🍕 🍔 🍟 🌮 🍣 🍜 🍝 🥗 🍰 🎂 🍪 🍩 🍫 🍎 🍓 🍉 🥑 🍞 🧀 🍿",
    "Luoghi" to "🏠 🏢 🏫 🏥 🏦 ⛪ 🏛️ 🏟️ 🏖️ 🏔️ 🏕️ 🌋 🗽 🗼 🚗 🚕 🚌 🚆 ✈️ 🚀 ⛵ 🚲 🛵 🗺️ 📍",
    "Oggetti" to "📅 📌 📎 📝 📚 📖 ✏️ 💼 💻 📱 ⌚ ⏰ 🔔 💡 🔑 🔒 💊 🩺 💰 💳 🎁 🛒 📦 🧾 🧳",
    "Natura" to "🌞 🌙 🌈 ☁️ 🌧️ ❄️ 🌊 🌸 🌹 🌻 🌲 🌵 🍀 🐶 🐱 🐼 🦊 🦁 🐸 🐢 🐬 🦋 🐝 🦄 🐙",
    "Simboli" to "◼️ ◻️ ⬛ ⬜ ▪️ ▫️ ⚫ ⚪ 🔲 🔳 ✅ ❌ ⚠️ ❗ ❓ 💯 🔴 🟢 🔵 🟡 🟣 🟤 🔶 🔷 ♾️",
)

fun firstGrapheme(s: String): String {
    if (s.isEmpty()) return s
    val it = BreakIterator.getCharacterInstance()
    it.setText(s)
    val end = it.next()
    return if (end == BreakIterator.DONE) s else s.substring(0, end)
}

@Composable
fun EmojiPickerSheet(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var cat by remember { mutableIntStateOf(0) }
    var custom by remember { mutableStateOf("") }
    val cats = EMOJI_SETS.keys.toList()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(bottom = 16.dp)) {
            Text(t("Scegli un'emoji"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp))
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                cats.forEachIndexed { i, c ->
                    FilterChip(selected = cat == i, onClick = { cat = i }, label = { Text(t(c)) })
                }
            }
            val list = EMOJI_SETS[cats[cat]]!!.split(" ").filter { it.isNotBlank() }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(52.dp),
                modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp).padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                items(list) { e ->
                    Box(
                        Modifier
                            .padding(4.dp)
                            .aspectRatio(1f)
                            .clip(CircleShape)
                            .bounceClick(0.8f) { onPick(e) },
                        contentAlignment = Alignment.Center,
                    ) { Text(e, fontSize = 28.sp) }
                }
            }
            Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = custom,
                    onValueChange = { custom = firstGrapheme(it.trim()) },
                    modifier = Modifier.width(180.dp),
                    singleLine = true,
                    label = { Text(t("Qualsiasi emoji")) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (custom.isNotBlank()) onPick(custom) }),
                )
                Spacer(Modifier.width(12.dp))
                TextButton(onClick = { if (custom.isNotBlank()) onPick(custom) }, enabled = custom.isNotBlank()) { Text(t("Usa")) }
            }
        }
    }
}

/** a small rounded "chip" surface with text */
@Composable
fun Pill(text: String, modifier: Modifier = Modifier, strong: Boolean = false) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = if (strong) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (strong) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
    }
}

/** QR code drawn in the current theme colours */
@Composable
fun QrImage(text: String, modifier: Modifier = Modifier) {
    val dark = LocalDark.current
    val bmp = remember(text, dark) {
        FriendQr.bitmap(
            text, 512,
            dark = if (dark) android.graphics.Color.rgb(242, 242, 242) else android.graphics.Color.rgb(10, 10, 10),
            light = if (dark) android.graphics.Color.rgb(10, 10, 10) else android.graphics.Color.WHITE,
        ).asImageBitmap()
    }
    androidx.compose.foundation.Image(bmp, contentDescription = "QR", modifier = modifier, filterQuality = androidx.compose.ui.graphics.FilterQuality.None)
}

@Suppress("unused")
private val unusedSize = Size.Zero
