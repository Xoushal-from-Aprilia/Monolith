package com.monolith.app.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import com.monolith.app.audio.Sounds

/**
 * Plays the light key sound for every tap that something actually reacted to (a button, a row,
 * a switch…). It only listens: it sees the gesture after the children handled it, consumes nothing,
 * and ignores scrolls and long presses.
 */
fun Modifier.tapSounds(): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
        val slop = viewConfiguration.touchSlop
        var moved = false
        while (true) {
            val ev = awaitPointerEvent(PointerEventPass.Final)
            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
            if ((ch.position - down.position).getDistance() > slop) moved = true
            if (!ch.pressed) {
                if (!moved && ch.isConsumed && ch.uptimeMillis - down.uptimeMillis < 600) Sounds.tap()
                break
            }
        }
    }
}

/** bottom sheet with key sounds (sheets live in their own window) */
@Composable
fun MonoSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    containerColor: Color = BottomSheetDefaults.ContainerColor,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier.tapSounds(),
        sheetState = sheetState,
        containerColor = containerColor,
        content = content,
    )
}

/** alert dialog with key sounds on its buttons and content */
@Composable
fun MonoDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = { Box(Modifier.tapSounds()) { confirmButton() } },
        modifier = modifier,
        dismissButton = dismissButton?.let { d -> { Box(Modifier.tapSounds()) { d() } } },
        icon = icon,
        title = title,
        text = text?.let { tx -> { Box(Modifier.tapSounds()) { tx() } } },
    )
}
