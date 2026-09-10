from pathlib import Path
import re

p = Path("app/src/main/java/com/vexora/editor/MainActivity.kt")
s = p.read_text()

# Keep image thumbnails repeated across the whole image duration.
old_image = '''            AsyncImage(clip.uri, "Timeline image", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)'''
new_image = '''            Row(Modifier.fillMaxSize()) {
                val thumbnailCount = max(1, kotlin.math.ceil(clip.duration / 1000.0).toInt())
                repeat(thumbnailCount) {
                    AsyncImage(
                        clip.uri,
                        "Timeline image thumbnail",
                        Modifier.width(55.dp).fillMaxHeight(),
                        contentScale = ContentScale.Crop
                    )
                }
            }'''
if old_image in s:
    s = s.replace(old_image, new_image)

s = s.replace(
    'val width = (clip.duration / 1000f * 55f).coerceIn(58f, 360f)',
    'val width = (clip.duration / 1000f * 55f).coerceAtLeast(1f)'
)

s = s.replace(
    'val toolbarStartPx = 8f + clips.take(selected).sumOf {\n                (it.duration / 1000f * 55f).coerceIn(58f, 360f).toDouble()\n            }.toFloat()',
    'val toolbarStartPx = 8f + clips.take(selected).sumOf {\n                (it.duration / 1000f * 55f).coerceAtLeast(1f).toDouble()\n            }.toFloat()'
)

# Keep resizing continuous while dragging. Snap to 0.1 second only when the gesture ends.
old_resize = '''    fun updateSelectedDuration(newDuration: Long) {
        if (selected in clips.indices) {
            val old = clips[selected]
            val snapped = ((newDuration.coerceIn(1000L, 60000L) + 500L) / 1000L) * 1000L
            clips[selected] = old.copy(duration = snapped.coerceIn(1000L, 60000L))
        }
    }'''
new_resize = '''    fun updateSelectedDuration(newDuration: Long) {
        if (selected in clips.indices) {
            val old = clips[selected]
            clips[selected] = old.copy(duration = newDuration.coerceIn(1000L, 60000L))
        }
    }

    fun snapSelectedDuration() {
        if (selected in clips.indices) {
            val old = clips[selected]
            val snapped = ((old.duration + 50L) / 100L) * 100L
            clips[selected] = old.copy(duration = snapped.coerceIn(1000L, 60000L))
        }
    }'''
if old_resize in s:
    s = s.replace(old_resize, new_resize)

# Add an end-of-drag callback to TimelineClip.
s = s.replace(
    'onResize: ((Float) -> Unit)?,\n    toolbarVisible: Boolean,',
    'onResize: ((Float) -> Unit)?,\n    onResizeEnd: (() -> Unit)?,\n    toolbarVisible: Boolean,'
)

# Pass the callback from MainMediaLane into TimelineClip.
s = s.replace(
    'onResize = onResize,\n                        contentWidth = visibleWidth,',
    'onResize = onResize,\n                        onResizeEnd = onResizeEnd,\n                        contentWidth = visibleWidth,'
)

# Add the callback to MainMediaLane and pass it to TimelineClip.
s = s.replace(
    'onResize: (Float) -> Unit,\n    contentWidth: Float,',
    'onResize: (Float) -> Unit,\n    onResizeEnd: () -> Unit,\n    contentWidth: Float,'
)

# Add the callback to Timeline() and pass it to MainMediaLane.
s = s.replace(
    'onResize: (Float) -> Unit,\n    onLeft: () -> Unit,',
    'onResize: (Float) -> Unit,\n    onResizeEnd: () -> Unit,\n    onLeft: () -> Unit,'
)
s = s.replace(
    'onResize = onResize,\n                        contentWidth = visibleWidth,\n                        toolbarVisible = toolbarVisible',
    'onResize = onResize,\n                        onResizeEnd = onResizeEnd,\n                        contentWidth = visibleWidth,\n                        toolbarVisible = toolbarVisible'
)

# Wire the end callback from VexoraEditor into Timeline.
s = s.replace(
    'onResize = { deltaPx ->\n                if (selected in clips.indices && !clips[selected].video) {\n                    val deltaMs = (deltaPx / 55f * 1000f).roundToLong()\n                    updateSelectedDuration(clips[selected].duration + deltaMs)\n                }\n            },\n            onLeft = {',
    'onResize = { deltaPx ->\n                if (selected in clips.indices && !clips[selected].video) {\n                    val deltaMs = (deltaPx / 55f * 1000f).roundToLong()\n                    updateSelectedDuration(clips[selected].duration + deltaMs)\n                }\n            },\n            onResizeEnd = { snapSelectedDuration() },\n            onLeft = {'
)

# Replace the resize gesture with a continuous drag. Do not quantize each motion event.
s = re.sub(
    r'\.clickable \{ arrowActive = !arrowActive \}\n                    \.pointerInput\(clip\.id\) \{.*?\n                    \},',
    '''.pointerInput(clip.id) {
                        var moved = false
                        detectDragGestures(
                            onDragStart = {
                                moved = false
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                if (dragAmount.x != 0f) moved = true
                                onResize(dragAmount.x)
                            },
                            onDragEnd = {
                                if (!moved) arrowActive = !arrowActive
                                else onResizeEnd?.invoke()
                                moved = false
                            },
                            onDragCancel = {
                                moved = false
                            }
                        )
                    },''',
    s,
    flags=re.S
)

# Arrow must always be black.
s = s.replace(
    'tint = if (arrowActive) Color.White else Color.Black,',
    'tint = Color.Black,'
)

pattern = r'@Composable\nprivate fun TimeMarkers\(clips: List<Clip>, contentWidth: Float, pixelsPerSecond: Float\) \{.*?\n\}\n\n@Composable\nprivate fun BottomTools'
replacement = '''@Composable
private fun TimeMarkers(clips: List<Clip>, contentWidth: Float, pixelsPerSecond: Float) {
    val totalMs = clips.sumOf { it.duration }
    val totalSeconds = max(1, kotlin.math.ceil(totalMs / 1000.0).toInt())

    Row(
        Modifier.width(contentWidth.dp).height(32.dp).background(Color(0xFF15161A)),
        verticalAlignment = Alignment.Top
    ) {
        for (second in 0..totalSeconds) {
            val x = second * pixelsPerSecond
            if (x <= contentWidth) {
                Box(Modifier.width(pixelsPerSecond.dp).fillMaxHeight()) {
                    Box(Modifier.width(1.dp).height(6.dp).background(SecondaryText))
                    Text(
                        "${second}s",
                        color = SecondaryText,
                        fontSize = 7.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomTools'''
s, count = re.subn(pattern, replacement, s, flags=re.S)
if count != 1:
    raise SystemExit(f"Expected one TimeMarkers function, found {count}")

p.write_text(s)
