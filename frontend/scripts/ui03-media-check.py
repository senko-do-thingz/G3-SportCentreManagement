"""UI-03 media check in a real browser (before = main, after = the UI-03 branch).

For two running builds, opens Home, Photo credits and the member Find Classes page in Chromium and records:
  - which Unsplash width the browser requests for every photo (srcset and sizes at work), per viewport and pixel ratio;
  - whether every rendered photo has enough source pixels for its slot (no upscaled, blurry image);
  - which hero video file is requested first, and whether it is requested only after the page load event;
  - that every image still renders when the CDN is offline (bundled fallbacks);
  - full-page screenshots of Home and Photo credits, compared pixel by pixel.

Remote photos are answered with solid placeholder JPEGs of the requested width (one color per photo), so the
layout is real and comparable while the test does not depend on the Unsplash CDN. The API is answered with
fixed JSON. Videos are recorded and then blocked.

Usage (PowerShell, from frontend/):
  pip install playwright pillow; python -m playwright install chromium
  npm run build -- --outDir ../dist-after   (on the UI-03 branch; repeat on main into ../dist-before)
  npx vite preview --outDir ../dist-before --port 4301   (and ../dist-after on 4302, in two other windows)
  python scripts/ui03-media-check.py --before http://localhost:4301 --after http://localhost:4302 --out docs/screenshots/ui-03
Exit code 0 when every check passes.
"""

import argparse
import hashlib
import io
import json
import re
import sys
from pathlib import Path

from PIL import Image, ImageChops
from playwright.sync_api import sync_playwright

MEMBER = {"id": 2, "email": "member@sportify.demo", "fullName": "Taylor Nguyen", "phone": "0901234567", "role": "MEMBER", "status": "ACTIVE"}
SPORT_NAMES = ["Football", "Badminton", "Basketball", "Volleyball", "Swimming", "Tennis"]
SPORTS = [{"id": index + 1, "code": name.upper(), "name": name} for index, name in enumerate(SPORT_NAMES)]
SESSIONS = [{"id": index + 1, "sportId": index + 1, "sportName": name, "classId": None, "className": f"{name} Foundations",
             "trainingFormat": "COACH_LED", "level": "BEGINNER", "coachId": None, "coachName": "Quan Coach", "facilityId": None,
             "facilityName": "Court A", "sessionDate": "2026-10-30", "startTime": "16:00:00", "endTime": "17:00:00",
             "capacity": 20, "bookedCount": 2, "freeSeats": 18, "full": False, "status": "PUBLISHED"} for index, name in enumerate(SPORT_NAMES)]
FIXTURES = {
    "/users/me": MEMBER,
    "/sports": SPORTS,
    "/packages": [{"id": 81, "name": "Football Independent", "sportId": 1, "sportName": "Football", "trainingFormat": "SELF_TRAINING",
                   "durationDays": 30, "sessionCount": 8, "priceAmount": 500000, "isActive": True}],
    "/membership-cards/tiers": [{"id": 1, "name": "Gold", "discountPercentage": 5, "isActive": True}],
    "/coaches/public": [{"id": 22, "fullName": "Le Hoang Nam", "headline": None, "specialties": None, "yearsExperience": 4, "sportIds": [1]}],
    "/facilities/public": [{"id": 61 + index, "name": f"{name} Court", "facilityType": "POOL" if name == "Swimming" else "COURT",
                            "sportId": index + 1, "capacity": 8} for index, name in enumerate(SPORT_NAMES)],
}

# (name, width, height, device scale factor)
VIEWPORTS = [("phone-390-3x", 390, 844, 3), ("390", 390, 844, 1), ("768", 768, 1024, 1), ("1440", 1440, 900, 1), ("1920", 1920, 1080, 1)]
SCREENSHOT_VIEWPORTS = ["390", "768", "1440"]


def placeholder(url):
    """Solid JPEG of the requested size; the color depends only on the photo id, so every width looks the same."""
    photo = re.search(r"photo-[\w-]+", url).group(0)
    width = int(re.search(r"[?&]w=(\d+)", url).group(1))
    height_match = re.search(r"[?&]h=(\d+)", url)
    height = int(height_match.group(1)) if height_match else round(width * 2 / 3)
    shade = hashlib.sha1(photo.encode()).digest()
    buffer = io.BytesIO()
    Image.new("RGB", (width, height), (shade[0], shade[1], shade[2])).save(buffer, "JPEG", quality=60)
    return buffer.getvalue(), photo, width


def answer_api(route):
    path = route.request.url.split("/api/v1", 1)[1]
    bare = path.split("?", 1)[0]
    if bare == "/sessions":
        body = {"content": SESSIONS, "page": 0, "size": 20, "totalElements": len(SESSIONS), "totalPages": 1}
    elif bare in FIXTURES:
        body = FIXTURES[bare]
    elif bare.startswith("/coaches"):
        body = []
    else:
        route.fulfill(status=404, content_type="application/json", body=json.dumps({"message": "Not found"}))
        return
    route.fulfill(status=200, content_type="application/json", body=json.dumps(body))


IMAGE_AUDIT = r"""(dpr) => [...document.images].filter((img) => img.getBoundingClientRect().width > 0).map((img) => {
  const rect = img.getBoundingClientRect()
  const src = img.currentSrc || img.src
  // With a w-descriptor srcset, naturalWidth is density-corrected, so read the file width from the URL instead.
  const fileWidth = Number(/[?&]w=(\d+)/.exec(src)?.[1]) || img.naturalWidth
  return { src: src.replace(location.origin, ''), rendered: [Math.round(rect.width), Math.round(rect.height)],
    natural: [fileWidth, img.naturalHeight], broken: img.complete && img.naturalWidth === 0,
    upscaled: fileWidth > 0 && fileWidth < Math.round(rect.width * dpr * 0.75) }
})"""


def visit(browser, base_url, route_path, viewport, offline=False, member=False, screenshot=None):
    _, width, height, scale = viewport
    context = browser.new_context(viewport={"width": width, "height": height}, device_scale_factor=scale, reduced_motion="no-preference")
    script = "try { localStorage.setItem('sportify.themePreference', 'light')"
    if member:
        script += "; localStorage.setItem('sportify.accessToken', 'ui03-access'); localStorage.setItem('sportify.refreshToken', 'ui03-refresh')"
    context.add_init_script(script + " } catch {}")
    photos, videos = [], []

    def unsplash(route):
        if offline:
            route.abort()
            return
        body, photo, requested = placeholder(route.request.url)
        photos.append({"photo": photo, "width": requested})
        route.fulfill(status=200, content_type="image/jpeg", body=body)

    def mixkit(route):
        videos.append({"file": route.request.url.rsplit("/", 1)[1], "at": page.evaluate("performance.now()")})
        route.abort()

    context.route("**/api/v1/**", answer_api)
    context.route(lambda url: "images.unsplash.com" in url, unsplash)
    context.route(lambda url: "mixkit.co" in url, mixkit)
    page = context.new_page()
    page.goto(base_url + route_path, wait_until="load")
    load_end = page.evaluate("performance.getEntriesByType('navigation')[0].loadEventEnd")
    page.wait_for_load_state("networkidle")
    for _ in range(40):
        page.mouse.wheel(0, height)
        page.wait_for_timeout(60)
    page.wait_for_load_state("networkidle")
    page.evaluate("window.scrollTo(0, 0)")
    page.wait_for_timeout(400)
    images = page.evaluate(IMAGE_AUDIT, scale)
    if screenshot:
        page.add_style_tag(content="*, *::before, *::after { animation: none !important; transition: none !important; } video { display: none !important; }")
        page.wait_for_timeout(200)
        page.screenshot(path=str(screenshot), full_page=True)
    context.close()
    return {"photos": photos, "videos": videos, "load_event_end_ms": round(load_end), "images": images}


def compare(before_path, after_path, diff_path):
    before, after = Image.open(before_path).convert("RGB"), Image.open(after_path).convert("RGB")
    if before.size != after.size:
        return {"identical": False, "reason": f"size {before.size} vs {after.size}"}
    red, green, blue = ImageChops.difference(before, after).split()
    mask = ImageChops.lighter(ImageChops.lighter(red, green), blue).point(lambda value: 255 if value else 0)
    changed = mask.histogram()[255]
    if changed:
        mask.save(diff_path)
    return {"identical": changed == 0, "changed_pixels": changed, "size": list(before.size)}


def megapixels(photos):
    return round(sum(entry["width"] * entry["width"] * 2 / 3 for entry in photos) / 1e6, 2)


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--before", required=True)
    parser.add_argument("--after", required=True)
    parser.add_argument("--out", required=True)
    args = parser.parse_args()
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    builds = {"before": args.before.rstrip("/"), "after": args.after.rstrip("/")}
    report = {"home_photo_requests": {}, "video": {}, "member_images": {}, "offline": {}, "screens": [], "failures": []}
    fail = report["failures"].append
    with sync_playwright() as playwright:
        browser = playwright.chromium.launch()
        for viewport in VIEWPORTS:
            name = viewport[0]
            row = {}
            for build, url in builds.items():
                shot = out / f"home-{name}-{build}.png" if name in SCREENSHOT_VIEWPORTS else None
                result = visit(browser, url, "/", viewport, screenshot=shot)
                row[build] = {"requests": len(result["photos"]), "widths": sorted(entry["width"] for entry in result["photos"]),
                              "requested_megapixels": megapixels(result["photos"]),
                              "broken": [image["src"] for image in result["images"] if image["broken"]],
                              "upscaled": [image for image in result["images"] if image["upscaled"]]}
                if name in ("1440", "1920", "390"):
                    first = result["videos"][0] if result["videos"] else None
                    report["video"][f"{name}-{build}"] = {"first_file": first and first["file"], "first_request_ms": first and round(first["at"]),
                                                         "load_event_end_ms": result["load_event_end_ms"]}
            report["home_photo_requests"][name] = row
            if row["after"]["requested_megapixels"] > row["before"]["requested_megapixels"]:
                fail(f"Home {name}: requests more pixels after ({row['after']['requested_megapixels']} MP) than before")
            if row["after"]["broken"]:
                fail(f"Home {name}: broken images {row['after']['broken']}")
            if row["after"]["upscaled"]:
                fail(f"Home {name}: upscaled images {[image['src'] for image in row['after']['upscaled']]}")
            if name in SCREENSHOT_VIEWPORTS:
                result = {"screen": f"home-{name}", **compare(out / f"home-{name}-before.png", out / f"home-{name}-after.png", out / f"home-{name}-diff.png")}
                report["screens"].append(result)
                if not result["identical"]:
                    fail(f"home-{name}: screenshot differs")

        video = report["video"]
        if video["1440-after"]["first_file"] and not video["1440-after"]["first_file"].endswith("-720.mp4"):
            fail(f"video 1440: first file {video['1440-after']['first_file']}, expected the 720p file")
        if video["1920-after"]["first_file"] and not video["1920-after"]["first_file"].endswith("-1080.mp4"):
            fail(f"video 1920: first file {video['1920-after']['first_file']}, expected the 1080p file")
        for key in ("1440-after", "1920-after"):
            if video[key]["first_file"] is None or video[key]["first_request_ms"] < video[key]["load_event_end_ms"]:
                fail(f"video {key}: not requested after the page load event ({video[key]})")
        if video["390-after"]["first_file"] is not None:
            fail("video 390: a phone must not request the hero video")

        for viewport in [VIEWPORTS[0], VIEWPORTS[3]]:
            for build, url in builds.items():
                result = visit(browser, url, "/member/classes", viewport, member=True, screenshot=out / f"find-classes-{viewport[0]}-{build}.png")
                report["member_images"][f"{viewport[0]}-{build}"] = {
                    "images": len(result["images"]), "upscaled": sum(image["upscaled"] for image in result["images"]),
                    "broken": sum(image["broken"] for image in result["images"]), "photo_widths": sorted(entry["width"] for entry in result["photos"]),
                    "natural_widths": sorted({image["natural"][0] for image in result["images"]})}
            after = report["member_images"][f"{viewport[0]}-after"]
            if after["images"] == 0 or after["upscaled"] or after["broken"]:
                fail(f"Find Classes {viewport[0]}: {after}")

        for route_path, member in (("/", False), ("/member/classes", True)):
            result = visit(browser, builds["after"], route_path, VIEWPORTS[3], offline=True, member=member)
            broken = [image["src"] for image in result["images"] if image["broken"]]
            report["offline"][route_path] = {"images": len(result["images"]), "broken": broken}
            if broken or not result["images"]:
                fail(f"offline {route_path}: broken {broken}")

        for viewport in [VIEWPORTS[1], VIEWPORTS[3]]:
            for build, url in builds.items():
                visit(browser, url, "/credits", viewport, screenshot=out / f"credits-{viewport[0]}-{build}.png")
            result = {"screen": f"credits-{viewport[0]}", **compare(out / f"credits-{viewport[0]}-before.png", out / f"credits-{viewport[0]}-after.png", out / f"credits-{viewport[0]}-diff.png")}
            report["screens"].append(result)
            if not result["identical"]:
                fail(f"credits-{viewport[0]}: screenshot differs")
        browser.close()

    (out / "report.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    for line in report["failures"]:
        print("FAIL", line)
    for name, row in report["home_photo_requests"].items():
        print(f"Home {name}: before {row['before']['requested_megapixels']} MP {row['before']['widths']} | after {row['after']['requested_megapixels']} MP {row['after']['widths']}")
    print(json.dumps(report["video"]), json.dumps(report["member_images"]), json.dumps(report["offline"]))
    print(f"{len(report['screens'])} screen pairs, {sum(s['identical'] for s in report['screens'])} identical, {len(report['failures'])} failures")
    return 1 if report["failures"] else 0


if __name__ == "__main__":
    sys.exit(main())
