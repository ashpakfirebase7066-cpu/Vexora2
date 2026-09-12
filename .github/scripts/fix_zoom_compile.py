from pathlib import Path
import re

p = Path("app/src/main/java/com/vexora/editor/MainActivity.kt")
s = p.read_text()
s = s.replace('import androidx.compose.ui.unit.DpSize\n', '')
pattern = r'''            Slider\(\n                value = timelineZoom,\n                onValueChange = \{ timelineZoom = it\.coerceIn\(0\.5f, 4f\) \},\n                valueRange = 0\.5f\.\.4f,\n                modifier = Modifier\.width\(105\.dp\)\.height\(30\.dp\),\n                thumb = \{.*?                \}\n            \)'''
replacement = '''            Slider(
                value = timelineZoom,
                onValueChange = { timelineZoom = it.coerceIn(0.5f, 4f) },
                valueRange = 0.5f..4f,
                modifier = Modifier.width(105.dp).height(30.dp)
            )'''
s, count = re.subn(pattern, replacement, s, flags=re.S)
if count > 1:
    raise SystemExit(f"Expected at most one custom zoom Slider, found {count}")
p.write_text(s)
print(f"zoom compile cleanup applied; slider replacements: {count}")
