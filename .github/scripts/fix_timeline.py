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

# Exact 55dp per second clip scale.
s = s.replace(
    'val width = (clip.duration / 1000f * 55f).coerceIn(58f, 360f)',
    'val width = (clip.duration / 1000f * 55f).coerceAtLeast(1f)'
)

# Keep toolbar aligned to clip start.
s = s.replace(
    'val toolbarStartPx = 8f + clips.take(selected).sumOf {\n                    (it.duration / 1000f * 55f).coerceIn(58f, 360f).toDouble()\n                }.toFloat()',
    'val toolbarStartPx = 8f + clips.take(selected).sumOf {\n                    (it.duration / 1000f * 55f).coerceAtLeast(1f).toDouble()\n                }.toFloat()'
)

# Timeline content follows the real duration scale.
s = s.replace(
    'val contentWidth = (totalDuration / 1000f * pixelsPerSecond + 70f).coerceAtLeast(360f)',
    'val contentWidth = (totalDuration / 1000f * pixelsPerSecond + 8f).coerceAtLeast(360f)'
)

# Whole-second duration keeps ruler, thumbnail count and visible duration consistent.
old_resize = '''    fun updateSelectedDuration(newDuration: Long) {
        if (selected in clips.indices) {
            val old = clips[selected]
            clips[selected] = old.copy(duration = newDuration.coerceIn(500L, 60000L))
        }
    }'''
new_resize = '''    fun updateSelectedDuration(newDuration: Long) {
        if (selected in clips.indices) {
            val old = clips[selected]
            val snapped = ((newDuration.coerceIn(1000L, 60000L) + 500L) / 1000L) * 1000L
            clips[selected] = old.copy(duration = snapped.coerceIn(1000L, 60000L))
        }
    }'''
if old_resize in s:
    s = s.replace(old_resize, new_resize)

# Replace TimelineClip so the resize arrow is visually outside the image and hidden by default.
# The transparent hit zone stays inside the clip's right edge, so it can be tapped even while the
# visual arrow is hidden/outside the clip. Tapping the zone reveals the arrow; dragging resizes.
timeline_pattern = r'@Composable\nprivate fun TimelineClip\(.*?\n\}\n\n@Composable\nprivate fun TimeMarkers'
timeline_replacement = '''@Composable
private fun TimelineClip(
    clip: Clip,
    selected: Boolean,
    width: Dp,
    onClick: () -> Unit,
    onResize: ((Float) -> Unit)?,
    toolbarVisible: Boolean,
    onDuplicate: () -> Unit,
    onAction: (String) -> Unit
) {
    var handleVisible by remember(clip.id) { mutableStateOf(false) }

    // Reset the visual handle whenever this clip is no longer selected.
    LaunchedEffect(selected) {
        if (!selected) handleVisible = false
    }

    Box(
        Modifier.width(width).fillMaxHeight().padding(end = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp))
                .border(
                    if (selected) 2.dp else 1.dp,
                    if (selected) TimelineAccent else Color(0xFF3B3C45),
                    RoundedCornerShape(4.dp)
                )
                .background(Panel2)
                .clickable(onClick = onClick)
        ) {
            if (clip.video) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(Icons.Default.VideoLibrary, null, tint = Color.White, modifier = Modifier.size(22.dp))
                    Text("VIDEO", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Row(Modifier.fillMaxSize()) {
                    val thumbnailCount = max(1, kotlin.math.ceil(clip.duration / 1000.0).toInt())
                    val thumbnailWidth = (width.value / thumbnailCount).coerceAtLeast(1f).dp
                    repeat(thumbnailCount) {
                        AsyncImage(
                            clip.uri,
                            "Timeline image thumbnail",
                            Modifier.width(thumbnailWidth).fillMaxHeight(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }

            Text(
                time(clip.duration),
                color = Color.White,
                fontSize = 8.sp,
                modifier = Modifier.align(Alignment.BottomEnd)
                    .background(Color.Black.copy(.65f))
                    .padding(3.dp)
            )
        }

        if (onResize != null && selected) {
            // Invisible tap/drag target is inside the clip edge; the actual chevron is drawn
            // completely outside the image strip so it never covers a thumbnail.
            Box(
                Modifier.align(Alignment.CenterEnd)
                    .width(24.dp)
                    .fillMaxHeight()
                    .clickable { handleVisible = true }
                    .pointerInput(clip.id) {
                        var pendingPx = 0f
                        detectDragGestures(
                            onDragStart = {
                                pendingPx = 0f
                                handleVisible = true
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                handleVisible = true
                                pendingPx += dragAmount.x
                                val wholeSeconds = (pendingPx / 55f).toInt()
                                if (wholeSeconds != 0) {
                                    onResize(wholeSeconds * 55f)
                                    pendingPx -= wholeSeconds * 55f
                                }
                            },
                            onDragEnd = { pendingPx = 0f },
                            onDragCancel = { pendingPx = 0f }
                        )
                    },
                contentAlignment = Alignment.CenterEnd
            ) {
                if (handleVisible) {
                    Box(
                        Modifier
                            .offset(x = 18.dp)
                            .width(18.dp)
                            .fillMaxHeight()
                            .zIndex(10f),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            Modifier.width(5.dp).fillMaxHeight().padding(vertical = 8.dp)
                                .background(TimelineAccent, RoundedCornerShape(4.dp))
                        )
                        Icon(
                            Icons.Default.ChevronRight,
                            "Drag to extend image duration",
                            tint = Color.Black,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeMarkers'''
s, count = re.subn(timeline_pattern, timeline_replacement, s, flags=re.S)
if count != 1:
    raise SystemExit(f"Expected one TimelineClip function, found {count}")

# Continuous ruler from 0s through the project duration.
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