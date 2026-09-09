from pathlib import Path
import re

p = Path("app/src/main/java/com/vexora/editor/MainActivity.kt")
s = p.read_text()

# Keep image thumbnail framing stable and repeat thumbnails across duration.
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

# Make the clip width exactly match its duration at the same 55dp/sec ruler scale.
s = s.replace(
    'val width = (clip.duration / 1000f * 55f).coerceIn(58f, 360f)',
    'val width = (clip.duration / 1000f * 55f).coerceAtLeast(1f)'
)

# Keep toolbar aligned with the same clip scale.
s = s.replace(
    'val toolbarStartPx = 8f + clips.take(selected).sumOf {\n                    (it.duration / 1000f * 55f).coerceIn(58f, 360f).toDouble()\n                }.toFloat()',
    'val toolbarStartPx = 8f + clips.take(selected).sumOf {\n                    (it.duration / 1000f * 55f).coerceAtLeast(1f).toDouble()\n                }.toFloat()'
)

# Do not add an artificial one-second ruler section after the project end.
s = s.replace(
    'val contentWidth = (totalDuration / 1000f * pixelsPerSecond + 70f).coerceAtLeast(360f)',
    'val contentWidth = (totalDuration / 1000f * pixelsPerSecond + 8f).coerceAtLeast(360f)'
)

# Image duration is snapped to whole seconds so the shown duration, thumbnails and ruler
# cannot disagree (e.g. 7s clip with an 8s thumbnail/ruler endpoint).
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

# Replace TimelineClip so the resize handle is OUTSIDE the thumbnail strip at its right edge.
# The clip itself remains exactly duration * 55dp wide; the handle is overlaid just beyond the edge
# and does not consume timeline time or become part of the thumbnail area.
pattern_clip = r'@Composable\nprivate fun TimelineClip\(.*?\n\}\n\n@Composable\nprivate fun TimeMarkers'
replacement_clip = '''@Composable
private fun TimelineClip(
    clip: Clip,
    selected: Boolean,
    width: Dp,
    onClick: () -> Unit,
    onResize: ((Float) -> Unit)?
) {
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
                    repeat(thumbnailCount) {
                        AsyncImage(
                            clip.uri,
                            "Timeline image thumbnail",
                            Modifier.width(55.dp).fillMaxHeight(),
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

        if (onResize != null) {
            Box(
                Modifier.align(Alignment.CenterEnd)
                    .offset(x = 9.dp)
                    .width(18.dp).fillMaxHeight()
                    .pointerInput(clip.id) {
                        detectDragGestures(
                            onDrag = { change, dragAmount ->
                                change.consume()
                                onResize(dragAmount.x)
                            }
                        )
                    },
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

@Composable
private fun TimeMarkers'''
s, count = re.subn(pattern_clip, replacement_clip, s, flags=re.S)
if count != 1:
    raise SystemExit(f"Expected one TimelineClip function, found {count}")

p.write_text(s)