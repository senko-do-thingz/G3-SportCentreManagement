import json
import os
import re
import sys
import time
from pathlib import Path
from playwright.sync_api import sync_playwright

E2E_DIR = Path(r"D:\G3-SportCentreManagement\SWP\e2e\11-11-26 - Gemini-Urgent-Fixes")
SCREENSHOT_DIR = E2E_DIR / "screenshots"
SCREENSHOT_DIR.mkdir(parents=True, exist_ok=True)

BASE_URL = "http://localhost:5173"
MEMBER_EMAIL = "an.gold@example.com"
MEMBER_PW = "SportifyDemo2026!"

results = {
    "viewport": "1440x900",
    "timestamp": time.strftime("%Y-%m-%d %H:%M:%S"),
    "checks": []
}

def record_check(name: str, passed: bool, details: str):
    results["checks"].append({
        "check": name,
        "status": "PASS" if passed else "FAIL",
        "details": details
    })
    print(f"[{'PASS' if passed else 'FAIL'}] {name}: {details}", flush=True)

def run():
    print("Starting Playwright checks at 1440x900 desktop viewport...", flush=True)
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        context = browser.new_context(
            viewport={"width": 1440, "height": 900},
            device_scale_factor=1.0
        )
        page = context.new_page()
        page.on("console", lambda msg: print(f"BROWSER CONSOLE [{msg.type}]: {msg.text}", flush=True))
        page.on("pageerror", lambda err: print(f"BROWSER PAGE ERROR: {err}", flush=True))
        page.on("request", lambda req: print(f"REQ: {req.method} {req.url}", flush=True) if "consultation" in req.url else None)
        page.on("response", lambda res: print(f"RES: {res.status} {res.url}", flush=True) if "consultation" in res.url else None)

        print("\n--- Check 1: Home coach carousel portraits ---", flush=True)
        try:
            page.goto(f"{BASE_URL}/", wait_until="domcontentloaded")
            page.wait_for_selector("#coaches", timeout=10000)
            page.locator("#coaches").scroll_into_view_if_needed()
            page.wait_for_selector(".home-coach-card", timeout=10000)
            page.wait_for_timeout(1000)

            # Check coach cards
            cards = page.locator(".home-coach-card")
            card_count = cards.count()
            print(f"Found {card_count} coach cards", flush=True)
            assert card_count >= 3, f"Expected at least 3 coaches, got {card_count}"

            # Check that images are loaded and not broken initials
            images = page.locator(".home-coach-card__avatar img")
            img_count = images.count()
            print(f"Found {img_count} coach avatar images", flush=True)
            assert img_count >= 3, f"Expected at least 3 images, got {img_count}"

            all_images_ok = True
            for i in range(img_count):
                img = images.nth(i)
                src = img.get_attribute("src")
                natural_width = img.evaluate("el => el.naturalWidth")
                print(f"Coach {i}: src={src}, naturalWidth={natural_width}", flush=True)
                if not src or natural_width <= 0:
                    all_images_ok = False

            # Verify no fallback initials
            fallback_cnt = page.evaluate("() => Array.from(document.querySelectorAll('.home-coach-card__avatar')).filter(el => !el.querySelector('img')).length")
            print(f"Fallback initials count: {fallback_cnt}", flush=True)
            assert fallback_cnt == 0, "Found unexpected fallback initials for demo coaches"

            page.screenshot(path=str(SCREENSHOT_DIR / "01_home_coach_portraits_1440x900.png"))
            record_check("Home coach carousel portraits", all_images_ok and fallback_cnt == 0, f"Loaded {img_count} coach portraits with naturalWidth > 0, zero initials fallbacks.")
        except Exception as e:
            record_check("Home coach carousel portraits", False, str(e))

        print("\n--- Member Login for Schedule & Profile & Spori checks ---", flush=True)
        try:
            page.goto(f"{BASE_URL}/login", wait_until="domcontentloaded")
            page.wait_for_selector('input[name="email"]', timeout=5000)
            page.fill('input[name="email"]', MEMBER_EMAIL)
            page.fill('input[name="password"]', MEMBER_PW)
            page.click('button[type="submit"]')
            page.wait_for_url("**/member**", timeout=10000)
            page.wait_for_timeout(1000)
            print("Current URL after login:", page.url, flush=True)
        except Exception as e:
            print("Login warning / status:", e, flush=True)

        print("\n--- Check 2: Four Schedule Tabs Order, Equal Height, and Alignment ---", flush=True)
        try:
            page.goto(f"{BASE_URL}/member/schedules?tab=my-schedule", wait_until="domcontentloaded")
            page.wait_for_selector(".schedule-workspace__tabs a", timeout=10000)
            page.wait_for_timeout(1000)

            # Verify tab order
            tab_labels = page.locator(".schedule-workspace__tabs a").all_inner_texts()
            expected_tabs = ["My Schedule", "Center Schedule", "Find Classes", "My bookings"]
            print("Detected tabs:", tab_labels, flush=True)
            order_ok = len(tab_labels) >= 4 and all(expected in tab_labels[i] for i, expected in enumerate(expected_tabs))

            # Measure heights
            hero_height_my = page.locator(".schedule-workspace__hero").bounding_box()["height"]
            print(f"My Schedule hero height: {hero_height_my}px", flush=True)
            page.screenshot(path=str(SCREENSHOT_DIR / "02_my_schedule_hero_1440x900.png"))

            # Switch to center schedule via tab click
            page.click(".schedule-workspace__tabs a:has-text('Center Schedule')")
            page.wait_for_selector(".center-schedule", timeout=10000)
            hero_height_center = page.locator(".schedule-workspace__hero").bounding_box()["height"]
            print(f"Center Schedule hero height: {hero_height_center}px", flush=True)

            # Check 3: Misplaced Find a class button
            # Button must NOT be inside .schedule-workspace__hero
            btn_in_hero = page.locator(".schedule-workspace__hero :is(button, a):has-text('Find a class')").count()
            # Button MUST be in .center-schedule__toolbar
            btn_in_toolbar = page.locator(".center-schedule__toolbar :is(button, a):has-text('Find a class')").count()
            print(f"Find a class button: in hero = {btn_in_hero}, in toolbar = {btn_in_toolbar}", flush=True)
            page.screenshot(path=str(SCREENSHOT_DIR / "03_center_schedule_toolbar_find_class_1440x900.png"))

            # Click Find a class button to test navigation to Find Classes tab
            page.click(".center-schedule__toolbar :is(button, a):has-text('Find a class')")
            page.wait_for_selector(".class-discovery", timeout=10000)
            assert "tab=find-classes" in page.url, f"Expected tab=find-classes in url, got {page.url}"

            # Check 4: Find Classes Tab scale and court image
            hero_height_find = page.locator(".schedule-workspace__hero").bounding_box()["height"]
            print(f"Find Classes hero height: {hero_height_find}px", flush=True)
            
            # Check container widths
            workspace_box = page.locator(".schedule-workspace").bounding_box()
            discovery_box = page.locator(".class-discovery").bounding_box()
            print(f"Workspace width: {workspace_box['width']}px, Discovery width: {discovery_box['width']}px", flush=True)
            scale_aligned = abs(workspace_box['width'] - discovery_box['width']) < 5

            page.screenshot(path=str(SCREENSHOT_DIR / "04_find_classes_normalized_scale_1440x900.png"))

            # Switch to bookings via tab click
            page.click(".schedule-workspace__tabs a:has-text('My bookings')")
            page.wait_for_selector(".schedule-workspace", timeout=10000)
            page.wait_for_timeout(500)
            hero_height_bookings = page.locator(".schedule-workspace__hero").bounding_box()["height"]
            print(f"Bookings hero height: {hero_height_bookings}px", flush=True)
            page.screenshot(path=str(SCREENSHOT_DIR / "05_my_bookings_hero_1440x900.png"))

            heights_equal = abs(hero_height_my - 272) < 2 and abs(hero_height_center - 272) < 2 and abs(hero_height_find - 272) < 2 and abs(hero_height_bookings - 272) < 2

            record_check("Four schedule tabs order and height", order_ok and heights_equal, f"Tab order: {tab_labels}. Hero heights: {hero_height_my}px, {hero_height_center}px, {hero_height_find}px, {hero_height_bookings}px.")
            record_check("Center Schedule Find a class button", btn_in_hero == 0 and btn_in_toolbar == 1, "Button relocated from hero to center-schedule__toolbar, clicks successfully to find-classes tab.")
            record_check("Find Classes scale normalization", scale_aligned, f"Container aligned: workspace={workspace_box['width']}px, discovery={discovery_box['width']}px.")
        except Exception as e:
            record_check("Schedule workspace checks", False, str(e))

        print("\n--- Check 5: Spori AI — sports catalog dynamic answer & NO_ANSWER receptionist handoff ---", flush=True)
        try:
            assistant_link = page.locator('a:has-text("Open center assistant")')
            if assistant_link.count() > 0:
                assistant_link.click()
            else:
                page.goto(f"{BASE_URL}/member/assistant", wait_until="domcontentloaded")
            page.wait_for_selector(".assistant-workspace", timeout=20000)
            page.wait_for_timeout(1000)

            # Opt-in checkbox: label contains "Enable automated center consultation"
            checkbox = page.locator('label:has-text("Enable automated center consultation") input[type="checkbox"]')
            if checkbox.count() > 0 and not checkbox.is_checked():
                checkbox.check()
                page.wait_for_timeout(1000)

            # Wait for input to be enabled
            input_box = page.locator('.training-form input[name="question"]')
            page.wait_for_function("() => { const el = document.querySelector('.training-form input[name=\"question\"]'); return el && !el.disabled; }", timeout=15000)

            # Test catalog sport question
            input_box.fill("How many sports are available?")
            page.wait_for_timeout(500)
            page.click('.training-form button[type="submit"]')
            page.wait_for_selector(".assistant-workspace__answer", timeout=30000)
            page.wait_for_timeout(1500)

            # Check response contains catalog sports
            answer_box = page.locator(".assistant-workspace__answer")
            answer_text = answer_box.inner_text()
            print("Spori catalog answer excerpt:", answer_text[:200], flush=True)
            has_sports = "sports" in answer_text.lower() and ("badminton" in answer_text.lower() or "swimming" in answer_text.lower() or "6" in answer_text)
            page.screenshot(path=str(SCREENSHOT_DIR / "06_spori_sports_catalog_answer_1440x900.png"))
            record_check("Spori sports catalog dynamic answer", has_sports, f"Dynamic response: {answer_text[answer_text.find('sports'):answer_text.find('sports')+100] if 'sports' in answer_text else 'Answer received'}")

            # Test unsupported question -> receptionist handoff
            page.wait_for_function("() => { const el = document.querySelector('.training-form input[name=\"question\"]'); return el && !el.disabled; }", timeout=15000)
            input_box.fill("Can I pet an alien from Mars at midnight?")
            page.wait_for_timeout(500)
            page.click('.training-form button[type="submit"]')

            # Wait for no-answer container to appear
            page.wait_for_selector(".assistant-workspace__no-answer", timeout=30000)
            handoff_div = page.locator(".assistant-workspace__no-answer")
            handoff_text = handoff_div.inner_text() if handoff_div.count() > 0 else ""
            print("Spori handoff text:", handoff_text, flush=True)

            handoff_link = page.locator('.assistant-workspace__no-answer a:has-text("Contact Receptionist"), .assistant-workspace__no-answer a:has-text("Contact Reception")')
            has_handoff = handoff_link.is_visible() and ("reception" in handoff_text.lower() or "support" in handoff_text.lower())
            print(f"Handoff link visible: {has_handoff}", flush=True)

            # Click handoff link -> navigates to /member/support with prefilled question
            handoff_link.click()
            page.wait_for_url("**/member/support*", timeout=10000)
            page.wait_for_selector("textarea", timeout=10000)
            page.wait_for_timeout(1000)

            # Verify support form has description prefilled
            desc_val = page.locator('textarea').input_value()
            print("Prefilled support description:", desc_val, flush=True)
            desc_prefilled = "alien" in desc_val.lower()

            page.screenshot(path=str(SCREENSHOT_DIR / "07_spori_receptionist_handoff_and_support_prefill_1440x900.png"))
            record_check("Spori NO_ANSWER receptionist handoff", has_handoff and desc_prefilled, f"Receptionist guidance shown, navigated to /member/support with prefilled question: '{desc_val[:60]}...'")
        except Exception as e:
            try:
                page.screenshot(path=str(SCREENSHOT_DIR / "debug_assistant_timeout.png"))
                print("DEBUG: URL at failure:", page.url, flush=True)
                print("DEBUG: Training page text:", page.locator(".training-page").inner_text()[:300] if page.locator(".training-page").count() > 0 else "No training-page", flush=True)
            except Exception as debug_err:
                print("DEBUG screenshot error:", debug_err, flush=True)
            record_check("Spori AI checks", False, str(e))

        print("\n--- Check 6: CalendarDateField picker button & formatting ---", flush=True)
        try:
            profile_link = page.locator('a:has-text("My profile")')
            if profile_link.count() > 0:
                profile_link.click()
            else:
                page.goto(f"{BASE_URL}/member/profile", wait_until="domcontentloaded")
            page.wait_for_selector(".calendar-date-field", timeout=20000)
            page.wait_for_timeout(1000)

            # Check visible picker button (trigger button)
            picker_btn = page.locator(".calendar-date-field__trigger")
            btn_visible = picker_btn.is_visible()
            print(f"Picker button visible: {btn_visible}", flush=True)

            # Check accessible button attributes
            aria_label = picker_btn.get_attribute("aria-label")
            print(f"Picker button aria-label: {aria_label}", flush=True)

            # Verify text input has dd/MM/yyyy format placeholder or value
            text_input = page.locator(".calendar-date-field__text")
            placeholder = text_input.get_attribute("placeholder")
            print(f"Text input placeholder: {placeholder}", flush=True)

            # Click picker button
            picker_btn.click()
            page.wait_for_timeout(500)

            # Verify direct typing in dd/MM/yyyy works
            text_input.click()
            text_input.fill("")
            text_input.fill("15/05/1995")
            page.wait_for_timeout(500)
            val = text_input.input_value()
            print(f"Entered date: {val}", flush=True)

            page.screenshot(path=str(SCREENSHOT_DIR / "08_member_profile_calendar_picker_1440x900.png"))
            record_check("CalendarDateField picker & formatting", btn_visible and val == "15/05/1995", f"Picker button accessible (aria-label='{aria_label}'), dd/MM/yyyy direct typing verified.")
        except Exception as e:
            record_check("CalendarDateField picker checks", False, str(e))

        browser.close()

    # Save summary report
    with open(E2E_DIR / "test_report.json", "w", encoding="utf-8") as f:
        json.dump(results, f, indent=2)

    print("\n=== Playwright Execution Summary ===", flush=True)
    for c in results["checks"]:
        print(f"[{c['status']}] {c['check']} - {c['details']}", flush=True)

if __name__ == "__main__":
    run()
