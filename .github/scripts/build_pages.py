"""Builds the GitHub Pages site into _site/: the privacy policy (from PRIVACY.md) at /privacy-policy/.

Usage: python build_pages.py <repo root> <output dir>   (needs: pip install markdown)
"""
import os
import sys

import markdown

PAGE = """<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>{title}</title>
<style>
  :root {{ color-scheme: light dark; --fg: #1b1f27; --bg: #fff; --muted: #5b6475; --line: #d9dee8; --link: #284777; }}
  @media (prefers-color-scheme: dark) {{ :root {{ --fg: #e4e8f0; --bg: #12161f; --muted: #9aa3b5; --line: #2c3342; --link: #9db8ee; }} }}
  body {{ margin: 0; background: var(--bg); color: var(--fg); font: 16px/1.6 system-ui, sans-serif; }}
  main {{ max-width: 760px; margin: 0 auto; padding: 32px 16px 64px; }}
  a {{ color: var(--link); }}
  h1 {{ line-height: 1.2; }}
  h2 {{ margin-top: 2em; padding-top: .5em; border-top: 1px solid var(--line); font-size: 1.25em; }}
  table {{ border-collapse: collapse; width: 100%; display: block; overflow-x: auto; }}
  th, td {{ border: 1px solid var(--line); padding: 8px 10px; text-align: left; vertical-align: top; }}
  code {{ font-size: .9em; }}
</style>
</head>
<body><main>
{body}
</main></body>
</html>
"""


def write(path, title, body):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        f.write(PAGE.format(title=title, body=body))


def main(repo, out):
    with open(os.path.join(repo, "PRIVACY.md"), encoding="utf-8") as f:
        # nl2br keeps the "App:" and "Effective date:" lines apart, as GitHub shows them
        policy = markdown.markdown(f.read(), extensions=["tables", "nl2br"])
    write(os.path.join(out, "privacy-policy", "index.html"), "Summit Privacy Policy", policy)
    write(os.path.join(out, "index.html"), "Summit",
          '<h1>Summit</h1><p>A calm, fast home screen for Android TV.</p>'
          '<p><a href="privacy-policy/">Privacy Policy</a> · '
          '<a href="https://github.com/exendahal/summit_android_tv_launcher">Source code</a></p>')


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
