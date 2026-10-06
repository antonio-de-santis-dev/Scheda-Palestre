"""Verify the production Nginx image, not Vite's development server."""
import gzip
import re
import time
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

BASE = "http://127.0.0.1:18080"

def fetch(path, encoding=None):
    request = Request(BASE + path, headers={"Accept-Encoding": encoding or "identity"})
    try:
        with urlopen(request, timeout=5) as response:
            return response.status, response.headers, response.read()
    except HTTPError as error:
        return error.code, error.headers, error.read()

for attempt in range(30):
    try:
        status, headers, html = fetch("/")
        if status == 200:
            break
    except (URLError, ConnectionError, TimeoutError):
        pass
    time.sleep(1)
else:
    raise AssertionError("Nginx did not start")

for path in ["/", "/app/history", "/manifest.webmanifest", "/apple-touch-icon.png"]:
    status, headers, body = fetch(path)
    assert status == 200, (path, status)
    assert headers.get("X-Content-Type-Options") == "nosniff", path
    assert headers.get("X-Frame-Options") == "DENY", path
    assert headers.get("Content-Security-Policy"), path
    assert "no-cache" in headers.get("Cache-Control", ""), path

asset = re.search(rb'src="(/assets/[^" ]+\.js)"', html).group(1).decode()
status, headers, plain = fetch(asset)
assert status == 200
assert "immutable" in ", ".join(headers.get_all("Cache-Control", []))
assert headers.get("X-Content-Type-Options") == "nosniff"
assert headers.get("Content-Security-Policy")
status, headers, compressed = fetch(asset, "gzip")
assert headers.get("Content-Encoding") == "gzip"
assert gzip.decompress(compressed) == plain
assert len(compressed) < len(plain)
for path in ["/assets/missing.js", "/icons/missing.png"]:
    assert fetch(path)[0] == 404, path
assert "application/manifest+json" in fetch("/manifest.webmanifest")[1].get("Content-Type", "")
print(f"Nginx OK: SPA, cache, security headers, missing assets, manifest; JS {len(plain)} -> {len(compressed)} bytes via gzip")
