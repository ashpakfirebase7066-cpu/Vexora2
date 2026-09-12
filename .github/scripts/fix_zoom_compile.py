from pathlib import Path
import re

p = Path("app/src/main/java/com/vexora/editor/MainActivity.kt")
s = p.read_text()
s = s.replace('import androidx.compose.ui.unit.DpSize\n', '')

# Timeline uses the Material3 Slider thumb API, which is experimental in
# the project's Material3 version. Opt in only for this composable.
if '@OptIn(ExperimentalMaterial3Api::class)\n@Composable\nprivate fun Timeline(' not in s:
    s = s.replace(
        '@Composable\nprivate fun Timeline(',
        '@OptIn(ExperimentalMaterial3Api::class)\n@Composable\nprivate fun Timeline(',
        1
    )

# Keep the zoom slider compatible with Material3 while giving it a small
# circular white thumb/knob.
pattern = r'''            Slider\(\n                value = timelineZoom,\n                onValueChange = \{ timelineZoom = it\.coerceIn\(0\.5f, 4f\) \},\n                valueRange = 0\.5f\.\.4f,\n                modifier = Modifier\.width\(105\.dp\)\.height\(30\.dp\),\n                thumb = \{.*?                \}\n            \)'''
replacement = '''            Slider(
                value = timelineZoom,
                onValueChange = { timelineZoom = it.coerceIn(0.5f, 4f) },
                valueRange = 0.5f..4f,
                modifier = Modifier.width(105.dp).height(30.dp),
                thumb = {
                    Box(
                        Modifier
                            .size(12.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color.White)
                    )
                }
            )'''
s, count = re.subn(pattern, replacement, s, flags=re.S)
if count > 1:
    raise SystemExit(f"Expected at most one custom zoom Slider, found {count}")

# If the previous workflow left the default Slider in place, upgrade it too.
default_pattern = r'''            Slider\(\n                value = timelineZoom,\n                onValueChange = \{ timelineZoom = it\.coerceIn\(0\.5f, 4f\) \},\n                valueRange = 0\.5f\.\.4f,\n                modifier = Modifier\.width\(105\.dp\)\.height\(30\.dp\)\n            \)'''
s, default_count = re.subn(default_pattern, replacement, s, flags=re.S)
if default_count > 1:
    raise SystemExit(f"Expected at most one default zoom Slider, found {default_count}")

p.write_text(s)
print(f"zoom slider circular knob applied; custom replacements: {count}; default replacements: {default_count}")
