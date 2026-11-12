"""Read-only Playwright checks against the running Sportify frontend and sportify API."""

import json
import os
from pathlib import Path

from playwright.sync_api import sync_playwright


BASE = os.getenv("SPORTIFY_BASE_URL", "http://localhost:5173")
PASSWORD = os.environ["SPORTIFY_DEMO_PASSWORD"]
MEMBER_ID = os.environ["SPORTIFY_FLOW2_MEMBER_ID"]
PAID_MEMBER_ID = os.getenv("SPORTIFY_FLOW2_PAID_MEMBER_ID")
OUT = Path(os.getenv("SPORTIFY_FLOW2_EVIDENCE", "frontend/output/playwright/flow2-package-recommendations"))
OUT.mkdir(parents=True, exist_ok=True)
checks = []


def record(name, passed, detail):
    checks.append({"name": name, "passed": bool(passed), "detail": detail})
    print(f"{'PASS' if passed else 'FAIL'} {name}: {detail}")


def sign_in(page, email):
    page.goto(f"{BASE}/login")
    page.get_by_label("Email address").fill(email)
    page.locator('input[name="password"]').fill(PASSWORD)
    page.get_by_role("button", name="Log in").click()
    page.wait_for_url(lambda url: "/login" not in url, timeout=15000)


with sync_playwright() as playwright:
    browser = playwright.chromium.launch(headless=True, executable_path=r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe")
    context = browser.new_context(viewport={"width": 1440, "height": 900})
    page = context.new_page()
    sign_in(page, "lan.reception@example.com")
    booking = f"{BASE}/reception/bookings/new?memberId={MEMBER_ID}&week=2026-11-13&sessionId=131126BK01"
    page.goto(booking)
    page.get_by_role("heading", name="Recommended Sport Packages").wait_for(timeout=15000)
    panel = page.locator(".booking-recommendations")
    rows = panel.locator(".booking-package")
    names = rows.locator("strong").all_text_contents()
    record("reception recommendations", len(names) == 3 and all("Basketball" in name and "Coach-led" in name for name in names), names)
    record("no mismatched recommendations", rows.count() == 3 and all("Self-training" not in row for row in rows.all_text_contents()), names)
    page.screenshot(path=str(OUT / "reception-no-package.png"), full_page=True)
    first = panel.get_by_role("link", name="Register this package").first
    first.click()
    page.wait_for_url("**/reception/register?*")
    page.get_by_role("form", name="Sell a sport package").wait_for(timeout=15000)
    params = dict((key, value[0]) for key, value in __import__("urllib.parse", fromlist=["parse_qs"]).parse_qs(page.url.split("?", 1)[1]).items())
    package_id = params.get("packageId")
    selected = page.get_by_role("form", name="Sell a sport package").locator("select").first.input_value()
    record("registration handoff", params.get("memberId") == MEMBER_ID and params.get("sessionId") == "131126BK01" and selected == package_id, {"memberId": params.get("memberId"), "sessionId": params.get("sessionId"), "packageId": package_id, "selected": selected})
    page.screenshot(path=str(OUT / "reception-registration-handoff.png"), full_page=True)
    page.get_by_role("link", name="Return to selected session").first.click()
    page.wait_for_url("**/reception/bookings/new?*")
    page.get_by_role("heading", name="Recommended Sport Packages").wait_for(timeout=15000)
    record("reception return", f"memberId={MEMBER_ID}" in page.url and "sessionId=131126BK01" in page.url, page.url)
    if PAID_MEMBER_ID:
        page.goto(f"{BASE}/reception/bookings/new?memberId={PAID_MEMBER_ID}&week=2026-11-13&sessionId=131126BK01")
        page.get_by_text("A suitable paid package is available.", exact=False).wait_for(timeout=15000)
        enabled = page.get_by_role("button", name="Confirm booking").is_enabled()
        record("paid package remains bookable", enabled and page.locator(".booking-package input[type=radio]").count() > 0, f"enabled={enabled}")
        page.screenshot(path=str(OUT / "reception-eligible-package.png"), full_page=True)
    else:
        print("SKIP paid package remains bookable: no eligible registration in the selected sportify data")
    context.close()

    member_context = browser.new_context(viewport={"width": 1440, "height": 900})
    member_page = member_context.new_page()
    sign_in(member_page, "bao.nocard@example.com")
    member_page.goto(f"{BASE}/member/sessions/131126BK01")
    member_page.get_by_role("heading", name="Recommended Sport Packages").wait_for(timeout=15000)
    member_rows = member_page.locator(".booking-recommendations .booking-package")
    record("member recommendations", member_rows.count() == 3, member_rows.locator("strong").all_text_contents())
    member_page.screenshot(path=str(OUT / "member-no-package.png"), full_page=True)
    member_page.get_by_role("link", name="View matching package").first.click()
    member_page.wait_for_url("**/member/packages/*?*")
    member_page.get_by_role("link", name="Return to selected session").wait_for(timeout=15000)
    record("member package handoff", "sessionId=131126BK01" in member_page.url, member_page.url)
    member_page.screenshot(path=str(OUT / "member-package-handoff.png"), full_page=True)
    member_context.close()
    browser.close()

(OUT / "results.json").write_text(json.dumps(checks, indent=2), encoding="utf-8")
if not all(item["passed"] for item in checks):
    raise SystemExit(1)
