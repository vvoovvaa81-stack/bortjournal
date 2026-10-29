"""Собирает app/src/main/assets/index.html из web/bortjournal.html (общий исходник веб- и Android-версии)."""
import re, pathlib
root = pathlib.Path(__file__).resolve().parent.parent
s = (root / 'web/bortjournal.html').read_text(encoding='utf-8')
rngs = (('cyrillic', 'U+0301,U+0400-045F,U+0490-0491,U+04B0-04B1,U+2116'),
        ('latin', 'U+0000-00FF,U+0131,U+0152-0153,U+02BB-02BC,U+02C6,U+02DA,U+02DC,U+0304,U+0308,U+0329,U+2000-206F,U+20AC,U+20B8,U+2122,U+2191,U+2193,U+2212,U+2215,U+FEFF,U+FFFD'))
ff = '\n'.join(f"@font-face{{font-family:'Roboto Condensed';font-weight:{w};font-style:normal;font-display:swap;"
               f"src:url('fonts/roboto-condensed-{sub}-{w}-normal.woff2') format('woff2');unicode-range:{rng}}}"
               for w in (500, 600, 700) for sub, rng in rngs)
s = re.sub(r'<link rel="preconnect"[^>]*>\n', '', s)
s = re.sub(r'<link id="webfonts"[^>]*>', f'<style>{ff}</style>', s)
s = s.replace('<meta name="claude:viewport-fit-sides" content="cover">\n', '')
doc = ('<!doctype html><html lang="ru"><head><meta charset="utf-8">'
       '<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">'
       '<style>body{margin:0;font:14px system-ui}img{max-width:100%}[hidden]{display:none!important}</style>'
       '</head><body>' + s + '</body></html>')
(root / 'app/src/main/assets/index.html').write_text(doc, encoding='utf-8')
print('ok', len(doc))
