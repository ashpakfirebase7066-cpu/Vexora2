from pathlib import Path

p = Path("app/src/main/java/com/vexora/editor/MainActivity.kt")
s = p.read_text()
s = s.replace('.offset(x = 0.dp)', '.offset(x = -5.dp)')
p.write_text(s)
