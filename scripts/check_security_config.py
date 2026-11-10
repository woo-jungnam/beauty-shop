"""Offline checks for proxy headers and tunnel credential fallbacks.

Run: python -I scripts/check_security_config.py
This is a source check, not a replacement for nginx -t.
"""
import re
from pathlib import Path

root = Path(__file__).resolve().parent.parent
nginx = (root / "nginx/nginx.conf").read_text(encoding="utf-8")
assert re.search(r'map "\$scheme:\$http_x_forwarded_proto" \$hsts\s*\{\s*default "";', nginx)
assert '~^https: "max-age=31536000";' in nginx
assert '"http:https" "max-age=31536000";' in nginx
# A location declaring add_header does not inherit the parent headers.
blocks = [[]]
header_blocks = []
for line in nginx.splitlines():
    if line.strip().endswith("{"):
        blocks.append([])
    elif line.strip() == "}":
        header_blocks.append(blocks.pop())
    elif line.strip().startswith("add_header "):
        blocks[-1].append(line.strip())
assert len(blocks) == 1, "Unbalanced proxy configuration blocks"
for headers in header_blocks:
    if headers:
        assert 'add_header Strict-Transport-Security $hsts always;' in headers, headers

for extension in ("sh", "bat"):
    script = root / f"scripts/cloudflare/run_cloudflared.{extension}"
    if script.exists():
        text = script.read_text(encoding="utf-8-sig")
        assert "CLOUDFLARE_TUNNEL_TOKEN is required" in text
        assert not re.search(r'(?:TOKEN=|TOKEN=")[A-Za-z0-9_-]{40,}', text), script.name
print("PASS: HSTS mapping, header inheritance, tunnel credential fallbacks (source checks)")
