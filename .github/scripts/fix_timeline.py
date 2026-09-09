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

# Keep the thumbnail strip full-width. The visual resize handle is drawn outside the clip edge.
s = s.replace(
    'Row(Modifier.fillMaxSize().padding(end = 18.dp)) {\n                    val thumbnailCount = max(1, kotlin.math.ceil(clip.duration / 1000.0).toInt())\n                    val thumbnailWidth = ((width.value - 18f) / thumbnailCount).coerceAtLeast(1f).dp',
    'Row(Modifier.fillMaxSize()) {\n                    val thumbnailCount = max(1, kotlin.math.ceil(clip.duration / 1000.0).toInt())\n                    val thumbnailWidth = (width.value / thumbnailCount).coerceAtLeast(1f).dp'
)

# Put the resize handle completely OUTSIDE the selected clip.
s = s.replace(
    '''                    .offset(x = 18.dp)\n                    .width(18.dp).fillMaxHeight()\n                    .zIndex(10f)\n                    .pointerInput(clip.id) {''',
    '''                    .offset(x = 18.dp)\n                    .width(18.dp).fillMaxHeight()\n                    .pointerInput(clip.id) {'''
)

# If an older source still has the handle without the external offset, add it.
s = s.replace(
    '''                    .width(18.dp).fillMaxHeight()\n                    .pointerInput(clip.id) {''',
    '''                    .offset(x = 18.dp)\n                    .width(18.dp).fillMaxHeight()\n                    .pointerInput(clip.id) {'''
)

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