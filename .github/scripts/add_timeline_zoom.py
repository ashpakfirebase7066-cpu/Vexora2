from pathlib import Path

p = Path('app/src/main/java/com/vexora/editor/MainActivity.kt')
s = p.read_text()

# Gesture import
if 'import androidx.compose.foundation.gestures.detectTransformGestures' not in s:
    s = s.replace(
        'import androidx.compose.foundation.gestures.detectDragGestures\n',
        'import androidx.compose.foundation.gestures.detectDragGestures\nimport androidx.compose.foundation.gestures.detectTransformGestures\n'
    )

# Add zoom state and zoomed base width.
s = s.replace(
    '    val timelineScroll = rememberScrollState()\n    val totalDuration = clips.sumOf { it.duration }.coerceAtLeast(3000L)\n    val pixelsPerSecond = 55f\n    val contentWidth = (totalDuration / 1000f * pixelsPerSecond + 8f).coerceAtLeast(360f)\n',
    '    val timelineScroll = rememberScrollState()\n    var timelineZoom by remember { mutableFloatStateOf(1f) }\n    val totalDuration = clips.sumOf { it.duration }.coerceAtLeast(3000L)\n    val pixelsPerSecond = 55f\n    val zoomedPixelsPerSecond = pixelsPerSecond * timelineZoom\n    val contentWidth = (totalDuration / 1000f * zoomedPixelsPerSecond + 8f).coerceAtLeast(360f)\n'
)

# Header: add compact zoom slider before navigation controls.
old = '''            Spacer(Modifier.weight(1f))\n            IconButton(onClick = onLeft, enabled = selected > 0, modifier = Modifier.size(34.dp)) {'''
new = '''            Spacer(Modifier.weight(1f))\n            Text("${"%.1f".format(timelineZoom)}×", color = SecondaryText, fontSize = 9.sp)\n            Slider(\n                value = timelineZoom,\n                onValueChange = { timelineZoom = it.coerceIn(0.5f, 4f) },\n                valueRange = 0.5f..4f,\n                modifier = Modifier.width(105.dp).height(30.dp),\n                thumb = {\n                    SliderDefaults.Thumb(\n                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },\n                        thumbSize = DpSize(10.dp, 10.dp)\n                    )\n                },\n                track = { sliderState ->\n                    SliderDefaults.Track(sliderState, modifier = Modifier.height(3.dp))\n                }\n            )\n            IconButton(onClick = onLeft, enabled = selected > 0, modifier = Modifier.size(34.dp)) {'''
s = s.replace(old, new)

# Pinch gesture on the timeline viewport. Clamp to 0.5x..4x.
s = s.replace(
    '            BoxWithConstraints(Modifier.fillMaxHeight().weight(1f)) {\n                val visibleWidth = max(maxWidth.value, contentWidth)',
    '''            BoxWithConstraints(\n                Modifier.fillMaxHeight().weight(1f)\n                    .pointerInput(Unit) {\n                        detectTransformGestures { _, _, zoomChange, _ ->\n                            timelineZoom = (timelineZoom * zoomChange).coerceIn(0.5f, 4f)\n                        }\n                    }\n            ) {\n                val visibleWidth = max(maxWidth.value, contentWidth)'''
)

# Pass zoom to media lane and use zoomed ruler spacing.
s = s.replace(
    '                        contentWidth = visibleWidth,\n                        toolbarVisible = toolbarVisible',
    '                        contentWidth = visibleWidth,\n                        timelineZoom = timelineZoom,\n                        toolbarVisible = toolbarVisible'
)
s = s.replace(
    '                    TimeMarkers(clips, visibleWidth, pixelsPerSecond)',
    '                    TimeMarkers(clips, visibleWidth, zoomedPixelsPerSecond)'
)

# MainMediaLane parameter.
s = s.replace(
    '    contentWidth: Float,\n    toolbarVisible: Boolean\n) {',
    '    contentWidth: Float,\n    timelineZoom: Float,\n    toolbarVisible: Boolean\n) {'
)

# Scale every clip visually, without changing duration.
s = s.replace(
    '                    val width = (clip.duration / 1000f * 55f).coerceAtLeast(1f)\n',
    '                    val width = (clip.duration / 1000f * 55f * timelineZoom).coerceAtLeast(1f)\n'
)

# Scale toolbar position to match zoomed clips.
s = s.replace(
    '(it.duration / 1000f * 55f).coerceAtLeast(1f).toDouble()',
    '(it.duration / 1000f * 55f * timelineZoom).coerceAtLeast(1f).toDouble()'
)

p.write_text(s)
