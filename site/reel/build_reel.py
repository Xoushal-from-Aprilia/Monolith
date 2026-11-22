"""Builds the reel as one HTML file: the film's drawings and the Inter font inlined.

usage: python3 build_reel.py <out.html> [source.html]   (default reel.src.html)
Fonts come from @fontsource/inter (npm i @fontsource/inter in /home/claude/tools).
"""
import base64
import pathlib
import re
import sys

here = pathlib.Path(__file__).resolve().parent
fonts_dir = pathlib.Path("/home/claude/tools/node_modules/@fontsource/inter/files")

film = (here.parent / "src" / "film.js").read_text(encoding="utf-8")
film = re.sub(r"^export ", "", film, flags=re.M)

faces = []
for weight in (500, 900):
    data = base64.b64encode((fonts_dir / f"inter-latin-{weight}-normal.woff2").read_bytes()).decode()
    faces.append(
        "@font-face { font-family: Inter; font-style: normal; font-display: block; "
        f"font-weight: {weight}; src: url(data:font/woff2;base64,{data}) format('woff2'); }}"
    )

src = (here / (sys.argv[2] if len(sys.argv) > 2 else "reel.src.html")).read_text(encoding="utf-8")
out = src.replace("/*FONTS*/", "\n".join(faces)).replace("/*FILM*/", film)
pathlib.Path(sys.argv[1]).write_text(out, encoding="utf-8")
print(sys.argv[1], len(out) // 1024, "KB")
