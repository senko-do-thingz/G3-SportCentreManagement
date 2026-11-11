import json
import os
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
RECEPTION_EMAIL = "lan.reception@example.com"
RECEPTION_PW = "SportifyDemo2026!"
PENDING_PAYMENT_MEMBER_EMAIL = "bao.nocard@example.com"
PENDING_PAYMENT_MEMBER_PW = "SportifyDemo2026!"

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

def login(browser, email, password):
    context = browser.new_context(
        viewport={"width": 1440, "height": 900},
        device_scale_factor=1.0
    )
    page = context.new_page()
    page.goto(f"{BASE_URL}/login", wait_until="domcontentloaded")
    page.wait_for_selector('input[name="email"]', timeout=10000)
    page.fill('input[name="email"]', email)
    page.fill('input[name="password"]', password)
    page.click('button[type="submit"]')
    page.wait_for_timeout(1200)
    return context, page

def run():
    print("Starting Wallet Retirement Playwright checks at 1440x900 desktop viewport...", flush=True)
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)

        # =========================================================================
        # Check 1: Member Navigation and Dashboard have NO Wallet items or balance
        # =========================================================================
        print("\n--- Check 1: Member Navigation & Dashboard (No Wallet Entry Points) ---", flush=True)
        try:
            ctx1, page1 = login(browser, MEMBER_EMAIL, MEMBER_PW)
            page1.wait_for_url("**/member**", timeout=10000)
            page1.wait_for_selector(".member-concept__nav", timeout=10000)
            page1.wait_for_timeout(1000)

            # 1. Assert navigation sidebar has NO "My wallet" link
            wallet_nav_links = page1.locator('a:has-text("My wallet"), a[href*="/member/wallet"]')
            wallet_nav_count = wallet_nav_links.count()
            print(f"Member navigation 'My wallet' link count: {wallet_nav_count}", flush=True)
            assert wallet_nav_count == 0, f"Expected 0 wallet links in navigation, found {wallet_nav_count}"

            # 2. Assert dashboard has NO "Wallet balance" stat card
            wallet_stat_card = page1.locator('.dashboard-wallet-stat, .stat-card:has-text("Wallet balance")')
            wallet_stat_count = wallet_stat_card.count()
            print(f"Member dashboard 'Wallet balance' stat card count: {wallet_stat_count}", flush=True)
            assert wallet_stat_count == 0, f"Expected 0 wallet balance cards on dashboard, found {wallet_stat_count}"

            page1.screenshot(path=str(SCREENSHOT_DIR / "01_member_dashboard_no_wallet_1440x900.png"))
            record_check("Member Navigation & Dashboard Wallet Removal", True, "Sidebar has no 'My wallet' link, and Dashboard contains no 'Wallet balance' stat card.")
            ctx1.close()
        except Exception as e:
            record_check("Member Navigation & Dashboard Wallet Removal", False, str(e))

        # =========================================================================
        # Check 2: Direct URL access to retired member wallet routes returns 404
        # =========================================================================
        print("\n--- Check 2: Direct URL Access to Retired Member Wallet Routes (404) ---", flush=True)
        try:
            ctx2, page2 = login(browser, MEMBER_EMAIL, MEMBER_PW)
            retired_routes = ["/member/wallet", "/member/wallet/topup", "/member/wallet/withdraw", "/member/wallet/history"]
            all_404 = True
            for route in retired_routes:
                page2.goto(f"{BASE_URL}{route}", wait_until="domcontentloaded")
                page2.wait_for_selector(".status-page", timeout=10000)
                is_404 = page2.locator('.status-page__title:has-text("Page not found")').is_visible()
                print(f"Route {route} -> 404 Page not found: {is_404}", flush=True)
                if not is_404:
                    all_404 = False

            # Take screenshot of /member/wallet showing 404
            page2.goto(f"{BASE_URL}/member/wallet", wait_until="domcontentloaded")
            page2.wait_for_selector(".status-page", timeout=10000)
            page2.screenshot(path=str(SCREENSHOT_DIR / "02_member_wallet_routes_404_1440x900.png"))
            record_check("Retired Member Wallet Routes 404", all_404, "All member wallet routes (/member/wallet, topup, withdraw, history) fall through to Page not found.")
            ctx2.close()
        except Exception as e:
            record_check("Retired Member Wallet Routes 404", False, str(e))

        # =========================================================================
        # Check 3: Receptionist Navigation & Inbox (No Wallet Queues or Tasks)
        # =========================================================================
        print("\n--- Check 3: Receptionist Navigation & Inbox (No Wallet Workflows) ---", flush=True)
        try:
            ctx3, page3 = login(browser, RECEPTION_EMAIL, RECEPTION_PW)
            page3.wait_for_url("**/reception**", timeout=10000)
            page3.wait_for_selector(".sidebar", timeout=10000)
            page3.wait_for_timeout(1000)

            # 1. Assert receptionist sidebar has NO "Wallet top-ups" or "Wallet withdrawals"
            rec_wallet_links = page3.locator('a:has-text("Wallet top-ups"), a:has-text("Wallet withdrawals"), a[href*="/reception/wallet"]')
            rec_wallet_count = rec_wallet_links.count()
            print(f"Receptionist sidebar wallet links count: {rec_wallet_count}", flush=True)
            assert rec_wallet_count == 0, f"Expected 0 wallet links in reception sidebar, found {rec_wallet_count}"

            # 2. Check Receptionist inbox
            page3.goto(f"{BASE_URL}/reception/inbox", wait_until="domcontentloaded")
            page3.wait_for_selector(".page", timeout=10000)
            page3.wait_for_timeout(1000)

            # Verify no wallet tasks in inbox
            inbox_wallet_tasks = page3.locator('.rcp-list li:has-text("wallet"), .rcp-list li:has-text("top-up"), .rcp-list li:has-text("withdrawal")')
            inbox_wallet_count = inbox_wallet_tasks.count()
            print(f"Receptionist inbox wallet tasks count: {inbox_wallet_count}", flush=True)
            assert inbox_wallet_count == 0, f"Expected 0 wallet tasks in inbox, found {inbox_wallet_count}"

            # 3. Direct access to /reception/wallet/topups returns 404
            page3.goto(f"{BASE_URL}/reception/wallet/topups", wait_until="domcontentloaded")
            page3.wait_for_selector(".status-page", timeout=10000)
            rec_route_404 = page3.locator('.status-page__title:has-text("Page not found")').is_visible()
            print(f"Route /reception/wallet/topups -> 404: {rec_route_404}", flush=True)
            assert rec_route_404, "Expected /reception/wallet/topups to render Page not found"

            # Return to inbox for clean screenshot
            page3.goto(f"{BASE_URL}/reception/inbox", wait_until="domcontentloaded")
            page3.wait_for_selector(".page", timeout=10000)
            page3.screenshot(path=str(SCREENSHOT_DIR / "03_reception_no_wallet_nav_and_inbox_1440x900.png"))
            record_check("Receptionist Wallet Workflows Removal", rec_wallet_count == 0 and inbox_wallet_count == 0 and rec_route_404,
                         "Receptionist sidebar has no wallet queue links, inbox contains no wallet tasks, and queue routes render Page not found.")
            ctx3.close()
        except Exception as e:
            record_check("Receptionist Wallet Workflows Removal", False, str(e))

        # =========================================================================
        # Check 4: Payment Summary Screen (No Wallet Pay Card; Bank/Cash Preserved)
        # =========================================================================
        print("\n--- Check 4: Payment Summary & My Payments (No Wallet Pay Card) ---", flush=True)
        try:
            ctx4, page4 = login(browser, PENDING_PAYMENT_MEMBER_EMAIL, PENDING_PAYMENT_MEMBER_PW)
            page4.wait_for_url("**/member**", timeout=10000)
            page4.wait_for_timeout(1000)

            # Navigate to /member/payments
            page4.goto(f"{BASE_URL}/member/payments", wait_until="domcontentloaded")
            page4.wait_for_selector(".page", timeout=10000)
            page4.wait_for_timeout(1000)

            # 1. Assert no "Wallet activity" link on My Payments page
            wallet_activity_link = page4.locator('.payment-wallet-link, a:has-text("Wallet activity")')
            wallet_link_count = wallet_activity_link.count()
            print(f"My Payments wallet activity link count: {wallet_link_count}", flush=True)
            assert wallet_link_count == 0, f"Expected 0 wallet activity links on My Payments, found {wallet_link_count}"

            # 2. Open payment summary for pending payment
            page4.goto(f"{BASE_URL}/member/payments/1", wait_until="domcontentloaded")
            page4.wait_for_selector(".payment-summary", timeout=10000)
            page4.wait_for_timeout(1000)

            # 3. Verify WalletPayCard is NOT present
            wallet_pay_card = page4.locator('.wallet-pay, button:has-text("from wallet"), button:has-text("From wallet")')
            wallet_pay_count = wallet_pay_card.count()
            print(f"Payment Summary wallet pay card count: {wallet_pay_count}", flush=True)
            assert wallet_pay_count == 0, f"Expected 0 wallet pay cards, found {wallet_pay_count}"

            # 4. Verify Bank Transfer method is available and active
            bank_radio = page4.locator('input[type="radio"][value="BANK_TRANSFER"]')
            bank_radio_visible = bank_radio.is_visible()
            bank_radio_checked = bank_radio.is_checked()
            print(f"Bank Transfer radio visible: {bank_radio_visible}, checked: {bank_radio_checked}", flush=True)
            assert bank_radio_visible, "Expected Bank Transfer radio button to be visible"

            # 5. Verify payment summary card and amount are present
            amount_visible = page4.locator('[data-testid="payment-amount"]').is_visible()
            print(f"Payment amount visible: {amount_visible}", flush=True)
            assert amount_visible, "Expected payment amount to be displayed"

            page4.screenshot(path=str(SCREENSHOT_DIR / "04_payment_summary_no_wallet_pay_card_1440x900.png"))
            record_check("Payment Summary Wallet Pay Card Removal", wallet_link_count == 0 and wallet_pay_count == 0 and bank_radio_visible,
                         "My Payments has no wallet link, payment summary has no WalletPayCard, and Bank Transfer remains available.")
            ctx4.close()
        except Exception as e:
            record_check("Payment Summary Wallet Pay Card Removal", False, str(e))

        browser.close()

    # Save summary report
    with open(E2E_DIR / "wallet_retirement_test_report.json", "w", encoding="utf-8") as f:
        json.dump(results, f, indent=2)

    print("\n=== Playwright Execution Summary ===", flush=True)
    all_passed = True
    for c in results["checks"]:
        print(f"[{c['status']}] {c['check']} - {c['details']}", flush=True)
        if c["status"] != "PASS":
            all_passed = False

    return all_passed

if __name__ == "__main__":
    success = run()
    sys.exit(0 if success else 1)
