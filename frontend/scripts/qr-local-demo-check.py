"""Non-Docker local QR acceptance on a fresh, explicitly named SQL Server database.

Reads local backend/.env without logging credentials. Never drops databases or edits existing rows.
Only processes started by this script are stopped. Public evidence contains no authentication tokens.
"""
import argparse
import datetime
import json
import os
from pathlib import Path
import re
import subprocess
import time
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[2]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--database', required=True)
parser.add_argument('--out', type=Path, required=True)
parser.add_argument('--create-only', action='store_true')
parser.add_argument('--jar', type=Path)
args = parser.parse_args()
if not re.fullmatch(r'sportify_qr04_test_[0-9]{14}', args.database):
    parser.error('Only a timestamped sportify_qr04_test database is permitted')
args.out.mkdir(parents=True, exist_ok=True)
settings = {}
for line in (ROOT / 'backend/.env').read_text().splitlines():
    if re.match(r'^[A-Z_]+=', line):
        key, value = line.split('=', 1)
        settings[key] = value.strip()
environment = os.environ.copy()
environment.update(settings)
environment['SQLCMDPASSWORD'] = settings['DB_PASSWORD']


def sql(query, database=None):
    result = subprocess.run(['sqlcmd', '-S', '127.0.0.1,1433', '-U', settings.get('DB_USERNAME', 'sa'),
                             '-C', '-l', '5', '-t', '30', '-b', '-h', '-1', '-y', '8000',
                             '-d', database or args.database, '-Q', 'SET NOCOUNT ON; ' + query],
                            env=environment, capture_output=True, text=True, timeout=40)
    if result.returncode:
        raise RuntimeError('Loopback test SQL failed: ' + (result.stdout + result.stderr)[:1000])
    return result.stdout.strip()


if args.create_only:
    # A failed CREATE when a name already exists is intentional. Existing databases are never reused here.
    sql(f'CREATE DATABASE [{args.database}];', 'master')
    (args.out / 'database-name.txt').write_text(args.database + '\n', encoding='ascii')
    print('Created fresh isolated database ' + args.database)
    raise SystemExit(0)
if not args.jar or not args.jar.is_file():
    parser.error('--jar must name the verified executable backend jar')
report = {'database': args.database, 'checks': [], 'transport': 'Loopback HTTP, real SQL Server and Edge; no Docker'}
backend = None
frontend = None
frontend_log = None
log = None


def check(name, condition, **evidence):
    report['checks'].append({'name': name, 'status': 'PASS' if condition else 'FAIL', **evidence})
    if not condition:
        raise AssertionError(name)


def request(path, body=None, token=None):
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    req = urllib.request.Request('http://127.0.0.1:8080/api/v1' + path,
                                 data=None if body is None else json.dumps(body).encode(), headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=30) as response:
            return response.status, json.load(response)
    except urllib.error.HTTPError as error:
        return error.code, None


try:
    environment.update({'DB_URL': f'jdbc:sqlserver://127.0.0.1:1433;databaseName={args.database};encrypt=false;trustServerCertificate=true',
                        'PAYMENT_LOCAL_DEMO_ENABLED': 'true', 'SERVER_ADDRESS': '127.0.0.1',
                        'CORS_ALLOWED_ORIGINS': 'http://127.0.0.1:5173',
                        'SPORTIFY_PAYMENT_ACCOUNT_NAME': 'Demo center',
                        'SPORTIFY_PAYMENT_ACCOUNT_NUMBER': 'NOT_AVAILABLE', 'SPORTIFY_PAYMENT_BANK': 'Simulation only'})
    log = (args.out / 'browser-backend.log').open('w', encoding='utf8')
    backend = subprocess.Popen(['java', '-Xmx512m', '-jar', str(args.jar.resolve()),
                                '--spring.profiles.active=dev,demo,local-demo', '--server.port=8080'],
                               cwd=ROOT / 'backend', env=environment, stdout=log, stderr=subprocess.STDOUT)
    for _ in range(180):
        if backend.poll() is not None:
            raise RuntimeError('Test backend exited during startup; inspect browser-backend.log')
        try:
            if request('/auth/login', {'email': 'invalid@example.test', 'password': 'invalid'})[0] == 401:
                break
        except (OSError, TimeoutError):
            pass
        time.sleep(1)
    else:
        raise TimeoutError('Loopback backend did not become ready')
    check('Fresh schema includes release V62', sql('SELECT MAX(TRY_CONVERT(int,version)) FROM flyway_schema_history WHERE success=1;') == '62')
    check('All five additive release migrations applied', sql('SELECT COUNT(*) FROM flyway_schema_history WHERE TRY_CONVERT(int,version) BETWEEN 42 AND 46 AND success=1;') == '5')
    status, auth = request('/auth/login', {'email': 'an.gold@example.com', 'password': 'SportifyDemo2026!'})
    check('Demo Member authenticates', status == 200)
    token = auth['accessToken']
    status, registration = request('/packages/registrations', {'packageId': 1, 'paymentMethod': 'BANK_TRANSFER'}, token)
    check('Official registration creates pending payment', status == 201)
    payment_id = registration['paymentId']
    before = json.loads(sql('SELECT (SELECT COUNT(*) FROM cash_event) cashEvents, '
                            '(SELECT COUNT(*) FROM vw_paid_payment) actualPaid FOR JSON PATH, WITHOUT_ARRAY_WRAPPER'))
    frontend_environment = os.environ.copy()
    frontend_environment.update({'VITE_QR_PAYMENT_DEMO': 'true', 'VITE_API_BASE_URL': 'http://127.0.0.1:8080/api/v1'})
    frontend_log = (args.out / 'browser-frontend.log').open('w', encoding='utf8')
    frontend = subprocess.Popen(['node', str(ROOT / 'frontend/node_modules/vite/bin/vite.js'),
                                 '--host', '127.0.0.1', '--port', '5173', '--strictPort'],
                                cwd=ROOT / 'frontend', env=frontend_environment,
                                stdout=frontend_log, stderr=subprocess.STDOUT)
    for _ in range(60):
        if frontend.poll() is not None:
            raise RuntimeError('Test frontend exited; inspect browser-frontend.log')
        try:
            with urllib.request.urlopen('http://127.0.0.1:5173', timeout=2) as response:
                if response.status == 200:
                    break
        except OSError:
            pass
        time.sleep(1)
    else:
        raise TimeoutError('Loopback frontend did not become ready')
    from playwright.sync_api import sync_playwright, expect
    from PIL import Image
    import zxingcpp
    with sync_playwright() as playwright:
        browser = playwright.chromium.launch(channel='msedge', headless=True)
        report['browserVersion'] = browser.version
        context = browser.new_context(viewport={'width': 1440, 'height': 1000})
        page = context.new_page()
        page.set_default_timeout(30000)
        page.set_default_navigation_timeout(60000)
        errors = []
        page.on('pageerror', lambda error: errors.append(str(error)))
        # API authentication only sets the same session keys as a normal login. Every screen read and confirmation uses real HTTP.
        page.goto('http://127.0.0.1:5173', wait_until='domcontentloaded')
        page.evaluate('(auth) => { sessionStorage.setItem("sportify.accessToken", auth.accessToken); sessionStorage.setItem("sportify.refreshToken", auth.refreshToken); sessionStorage.setItem("sportify.sessionId", "qr-local-qa"); }', auth)
        page.goto(f'http://127.0.0.1:5173/member/payments/{payment_id}/qr-demo', wait_until='domcontentloaded')
        card = page.locator('.qr-demo-card')
        button = page.get_by_role('button', name='Simulate Payment Confirmation', exact=True)
        expect(button).to_be_enabled(timeout=30000)
        check('One pending QR card and one confirmation action', card.count() == 1 and button.count() == 1)
        status, payment = request(f'/payments/{payment_id}', token=token)
        expected_payload = f'SPORTIFY_DEMO|REF={payment["code"]}|NON_PAYABLE=1'
        external = card.locator('img')
        if external.count():
            url = external.get_attribute('src')
            from urllib.parse import parse_qs, urlparse
            check('External QR URL contains only synthetic public reference', parse_qs(urlparse(url).query)['data'] == [expected_payload]
                  and external.get_attribute('referrerpolicy') == 'no-referrer')
        else:
            check('External image outage already displays local fallback', card.locator('svg[data-testid="demo-qr-svg"]').count() == 1)
        # An explicit provider outage must preserve PENDING and yield the same independently decodable symbol.
        page.route('https://api.qrserver.com/**', lambda route: route.abort())
        page.reload(wait_until='domcontentloaded')
        expect(card.locator('svg[data-testid="demo-qr-svg"]')).to_be_visible(timeout=30000)
        card.locator('svg[data-testid="demo-qr-svg"]').screenshot(path=str(args.out / 'qr-fallback.png'))
        decoded = zxingcpp.read_barcode(Image.open(args.out / 'qr-fallback.png'))
        check('Offline QR decodes independently to non-payable reference', decoded is not None and decoded.text == expected_payload)
        for width, theme in [(1440, 'light'), (390, 'light'), (390, 'dark'), (1440, 'dark')]:
            page.set_viewport_size({'width': width, 'height': 1000})
            page.emulate_media(color_scheme=theme)
            page.evaluate('(theme) => document.documentElement.setAttribute("data-theme", theme)', theme)
            check(f'Layout has no horizontal overflow at {width}px {theme}', page.evaluate('document.documentElement.scrollWidth <= innerWidth'))
            page.screenshot(path=str(args.out / f'pending-{width}-{theme}.png'), full_page=True)
        # A network failure must not display success or mutate the payment.
        page.route(f'**/api/v1/payments/{payment_id}/demo-confirm', lambda route: route.abort())
        button.click()
        expect(card.get_by_role('alert')).not_to_be_empty(timeout=10000)
        check('Network failure preserves pending backend state', request(f'/payments/{payment_id}', token=token)[1]['status'] == 'PENDING')
        page.unroute(f'**/api/v1/payments/{payment_id}/demo-confirm')
        expect(button).to_be_disabled()
        page.reload(wait_until='domcontentloaded')
        expect(button).to_be_enabled(timeout=30000)
        button.focus()
        button.press('Enter')
        expect(page.get_by_role('link', name='View payment receipt')).to_be_visible(timeout=30000)
        page.screenshot(path=str(args.out / 'settled.png'), full_page=True)
        page.reload(wait_until='domcontentloaded')
        receipt = page.get_by_role('link', name='View payment receipt')
        expect(receipt).to_be_visible(timeout=30000)
        check('Refresh persists simulated result and invoice link', page.get_by_text('Paid - Simulated', exact=True).count() >= 1)
        receipt.click()
        expect(page.get_by_text('Paid - Simulated', exact=True).first).to_be_visible(timeout=30000)
        page.screenshot(path=str(args.out / 'receipt.png'), full_page=True)
        check('Receipt screen renders without runtime exceptions', not errors)
        context.close()
        browser.close()
    after = json.loads(sql('SELECT (SELECT COUNT(*) FROM cash_event) cashEvents, '
                           '(SELECT COUNT(*) FROM vw_paid_payment) actualPaid FOR JSON PATH, WITHOUT_ARRAY_WRAPPER'))
    check('Browser simulation changes no cash events or actual paid rows', before == after, before=before, after=after)
    outcome = json.loads(sql(f"SELECT p.id,p.payment_status,p.settlement_type,p.settlement_provider,p.amount,"
                             f"(SELECT COUNT(*) FROM simulated_payment_transaction WHERE payment_id=p.id) transactionCount,"
                             f"(SELECT COUNT(*) FROM invoice WHERE payment_id=p.id) invoiceCount,"
                             f"(SELECT COUNT(*) FROM activity_log WHERE entity_id=p.id AND action='PAYMENT_CONFIRMED') auditCount "
                             f"FROM payment p WHERE id={int(payment_id)} FOR JSON PATH, WITHOUT_ARRAY_WRAPPER"))
    check('One durable transaction invoice and audit entry', outcome['transactionCount'] == outcome['invoiceCount'] == outcome['auditCount'] == 1, payment=outcome)
    report['status'] = 'PASS'
except Exception as error:
    report.update({'status': 'FAIL', 'failureType': type(error).__name__, 'failure': str(error)[:1000]})
finally:
    if frontend and frontend.poll() is None:
        frontend.terminate()
        try:
            frontend.wait(timeout=10)
        except subprocess.TimeoutExpired:
            frontend.kill()
            frontend.wait(timeout=5)
    if frontend_log:
        frontend_log.close()
    if backend and backend.poll() is None:
        backend.terminate()
        try:
            backend.wait(timeout=15)
        except subprocess.TimeoutExpired:
            backend.kill()
            backend.wait(timeout=5)
    if log:
        log.close()
    (args.out / 'browser-results.json').write_text(json.dumps(report, indent=2, ensure_ascii=True) + '\n', encoding='ascii')
    print(json.dumps(report, indent=2, ensure_ascii=True))
raise SystemExit(0 if report['status'] == 'PASS' else 1)
