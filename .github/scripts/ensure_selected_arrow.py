from pathlib import Path

p = Path("app/src/main/java/com/vexora/editor/MainActivity.kt")
s = p.read_text()

# Keep the selected clip (and its outside resize handle) above the next clip.
if "import androidx.compose.ui.zIndex" not in s:
    s = s.replace(
        "import androidx.compose.ui.viewinterop.AndroidView",
        "import androidx.compose.ui.viewinterop.AndroidView\nimport androidx.compose.ui.zIndex"
    )

old = "Modifier.width(width).fillMaxHeight().padding(end = 3.dp),"
new = "Modifier.width(width).fillMaxHeight().padding(end = 3.dp)\n            .then(if (selected) Modifier.zIndex(2f) else Modifier),"
if old in s and new not in s:
    s = s.replace(old, new, 1)

p.write_text(s)
