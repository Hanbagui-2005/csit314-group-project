"""Upload the demo before/after photos as real portfolio projects on a running instance.

Goes through the real designer login + "new project" form (CSRF and all), so the photos
get the same validation (IDR-15), storage, and plagiarism hashing (PM-04) as a manual
upload, and land in the project_photos / project_before_photos tables.

Prereqs: app running, demo data imported (--app.demo-data.import=true) so
demo.designer01/02 exist and are approved, and the PNGs generated (generate_photos.py).

Usage: python scripts/demo-photos/seed_projects.py [base_url]   (default http://localhost:8080)
Safe to re-run: a project whose title is already on that designer's dashboard is skipped.
"""
import html
import http.cookiejar
import re
import sys
import urllib.parse
import urllib.request
import uuid
from pathlib import Path

BASE = (sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8080").rstrip("/")
PHOTOS = Path(__file__).resolve().parents[2] / "demo-photos"
PASSWORD = "DemoAccount!2026"

PROJECTS = {
    "demo.designer01@demo.example.test": [
        dict(title="Riverside Living Room Refresh", category="Living Room",
             description="Took a tired, water-stained lounge back to bare walls: patched cracks, "
                         "new oak flooring, a sage feature wall and a warm layered lighting plan.",
             budgetMin=8000, budgetMax=15000, estimatedTimeline="4-6 weeks", roomSizeSqm=24,
             photos=["living-room-after.png"], beforePhotos=["living-room-before.png"]),
        dict(title="Heritage Kitchen Renovation", category="Kitchen",
             description="Replaced worn laminate cabinets with deep green shaker joinery, a stone "
                         "benchtop, subway tile splash and a seated island with brass pendants.",
             budgetMin=25000, budgetMax=40000, estimatedTimeline="8-10 weeks", roomSizeSqm=16,
             photos=["kitchen-after.png"], beforePhotos=["kitchen-before.png"]),
        dict(title="Calm Master Bedroom Makeover", category="Bedroom",
             description="A cluttered spare room turned into a calm main bedroom with a timber "
                         "headboard, soft blush walls, linen curtains and bedside lighting.",
             budgetMin=6000, budgetMax=12000, estimatedTimeline="3-4 weeks", roomSizeSqm=18,
             photos=["bedroom-after.png"], beforePhotos=["bedroom-before.png"]),
    ],
    # PM-04 demo: a second designer passing off designer01's living-room photo as their own,
    # which the plagiarism check should auto-flag into /platform-manager/flagged-projects.
    # Uses the first demo designer from 02 up who can log in (some may be suspended or
    # rejected from earlier admin testing)
    "OTHER_DESIGNER": [
        dict(title="Modern Lounge Makeover", category="Living Room",
             description="Full lounge restyle with new flooring and a navy sofa centrepiece.",
             budgetMin=10000, budgetMax=18000, estimatedTimeline="5 weeks", roomSizeSqm=22,
             photos=["living-room-after.png"], beforePhotos=[]),
    ],
}


class Client:
    def __init__(self):
        self.opener = urllib.request.build_opener(
            urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))

    def get(self, path):
        with self.opener.open(BASE + path) as r:
            return r.geturl(), r.read().decode("utf-8", "replace")

    def post(self, path, body, content_type):
        req = urllib.request.Request(BASE + path, data=body, method="POST",
                                     headers={"Content-Type": content_type})
        with self.opener.open(req) as r:
            return r.geturl(), r.read().decode("utf-8", "replace")


def csrf(page, action):
    form = re.search(r'<form[^>]*action="' + re.escape(action) + r'"[^>]*>(.*?)</form>', page, re.S)
    m = re.search(r'name="_csrf"\s+value="([^"]+)"', form.group(1) if form else page)
    if not m:
        raise SystemExit(f"No CSRF token found for {action}")
    return m.group(1)


def multipart(fields, files):
    boundary = uuid.uuid4().hex
    out = bytearray()
    for name, value in fields:
        out += (f"--{boundary}\r\nContent-Disposition: form-data; name=\"{name}\"\r\n\r\n"
                f"{value}\r\n").encode()
    for name, path in files:
        out += (f"--{boundary}\r\nContent-Disposition: form-data; name=\"{name}\"; "
                f"filename=\"{path.name}\"\r\nContent-Type: image/png\r\n\r\n").encode()
        out += path.read_bytes() + b"\r\n"
    out += f"--{boundary}--\r\n".encode()
    return bytes(out), f"multipart/form-data; boundary={boundary}"


def flash(page):
    m = re.search(r'class="alert[^"]*"[^>]*>\s*(.*?)\s*<', page, re.S)
    return html.unescape(m.group(1)).strip() if m else ""


def login(email):
    c = Client()
    _, page = c.get("/designer/login")
    body = urllib.parse.urlencode({"_csrf": csrf(page, "/designer/login"),
                                   "email": email, "password": PASSWORD}).encode()
    url, page = c.post("/designer/login", body, "application/x-www-form-urlencoded")
    return (c, None) if "/designer/dashboard" in url else (None, flash(page) or url)


def seed_designer(email, projects):
    if email == "OTHER_DESIGNER":
        for i in range(2, 51):
            candidate = f"demo.designer{i:02d}@demo.example.test"
            c, err = login(candidate)
            if c:
                email = candidate
                break
            print(f"  ({candidate} can't log in: {err})")
        else:
            raise SystemExit("No active demo designer found besides designer01")
    else:
        c, err = login(email)
        if not c:
            raise SystemExit(f"Login failed for {email}: {err}. Was the demo data imported?")
    print(f"Logged in as {email}")

    _, form_page = c.get("/designer/projects/new")
    categories = {html.unescape(n).strip(): v for v, n in
                  re.findall(r'<option[^>]*value="(\d+)"[^>]*>([^<]+)</option>', form_page)}
    _, dashboard = c.get("/designer/dashboard")

    for p in projects:
        if html.escape(p["title"]) in dashboard or p["title"] in dashboard:
            print(f"  skip  {p['title']} (already exists)")
            continue
        if p["category"] not in categories:
            raise SystemExit(f"Category {p['category']!r} not found; have {sorted(categories)}")
        _, form_page = c.get("/designer/projects/new")
        fields = [("_csrf", csrf(form_page, "/designer/projects")),
                  ("title", p["title"]), ("description", p["description"]),
                  ("categoryId", categories[p["category"]]),
                  ("budgetMin", p["budgetMin"]), ("budgetMax", p["budgetMax"]),
                  ("estimatedTimeline", p["estimatedTimeline"]), ("roomSizeSqm", p["roomSizeSqm"])]
        files = [("photos", PHOTOS / f) for f in p["photos"]] + \
                [("beforePhotos", PHOTOS / f) for f in p["beforePhotos"]]
        missing = [str(f) for _, f in files if not f.exists()]
        if missing:
            raise SystemExit(f"Missing photos {missing}; run generate_photos.py first")
        data, ctype = multipart(fields, files)
        url, page = c.post("/designer/projects", data, ctype)
        ok = url.endswith("/designer/dashboard")
        print(f"  {'added' if ok else 'FAIL '} {p['title']}: {flash(page)}")
        if not ok:
            raise SystemExit(1)


def main():
    for email, projects in PROJECTS.items():
        seed_designer(email, projects)
    print(f"\nDone. Browse {BASE}/search, or see the PM-04 flag at {BASE}/platform-manager/flagged-projects")


if __name__ == "__main__":
    main()
