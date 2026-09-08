package androidx.compose.foundation

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput as composePointerInput

/** Compatibility bridge for the editor's pointerInput import. */
fun Modifier.pointerInput(
    key1: Any?,
    block: suspend PointerInputScope.() -> Unit
): Modifier = composePointerInput(key1, block)
