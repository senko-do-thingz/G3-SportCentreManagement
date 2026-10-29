"""QR-04 real Edge acceptance against real HTTP demo state. Declare the SQL or fixture boundary explicitly."""
import argparse
import base64
from datetime import datetime, timezone
import hashlib
from io import BytesIO
import json
import mimetypes
from pathlib import Path
import tempfile
import subprocess
import time
from urllib.error import HTTPError
from urllib.parse import urlparse
from urllib.request import Request, urlopen

from PIL import Image
from playwright.sync_api import sync_playwright, expect
import zxingcpp

parser = argparse.ArgumentParser()
parser.add_argument('--base', required=True)
parser.add_argument('--extra-only', action='store_true')
parser.add_argument('--backend-mode', choices=['live-sql', 'fixture'], default='live-sql')
parser.add_argument('--normal-asset-root')
parser.add_argument('--api', default='http://127.0.0.1:8094/api/v1')
parser.add_argument('--out', required=True)
parser.add_argument('--asset-root', required=True)
parser.add_argument('--axe', required=True)
parser.add_argument('--payment-id', type=int, required=True)
parser.add_argument('--second-payment-id', type=int, required=True)
parser.add_argument('--transport', choices=['native', 'proxy'], default='native')
args = parser.parse_args()
out = Path(args.out)
for directory in ['screenshots', 'decoded-qr', 'logs']:
    (out / directory).mkdir(parents=True, exist_ok=True)
report = {'startedAtUtc': datetime.now(timezone.utc).isoformat(), 'mode': ('LIVE SQL SERVER BACKEND + REAL EDGE' if args.backend_mode == 'live-sql' else 'REAL SPRING DEMO STATE + SYNTHETIC AUTH/PAYMENT REPOSITORY FIXTURE + REAL EDGE'),
          'transport': args.transport, 'physicalPhone': 'NOT RUN - no physical phone available',
          'checks': [], 'screenshots': [], 'decodes': [], 'accessibility': [], 'requests': [], 'pageErrors': []}
report['sourceHead'] = subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip()
report['browserScriptSha256'] = hashlib.sha256(Path(__file__).read_bytes()).hexdigest()
report['assetHashes'] = {p.relative_to(Path(args.asset_root)).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest() for p in Path(args.asset_root).rglob('*') if p.is_file()}
tokens = []
sessions = []
password = 'Qr04IsolatedTest2026!'


def save():
    serialized = json.dumps(report, indent=2, ensure_ascii=True)
    for token in tokens:
        serialized = serialized.replace(token, '[REVOKED_TOKEN_SHA256:' + hashlib.sha256(token.encode()).hexdigest() + ']')
    (out / 'logs/qr04-browser-results.json').write_text(serialized + '\n', encoding='ascii')


def check(name, passed, detail=None):
    report['checks'].append({'name': name, 'pass': bool(passed), 'detail': detail})
    save()
    if not passed:
        raise AssertionError(name)


def request(path, body=None, bearer=None, method=None):
    headers = {'Content-Type': 'application/json'}
    if bearer:
        headers['Authorization'] = 'Bearer ' + bearer
    payload = json.dumps(body).encode() if body is not None else None
    req = Request(args.api + path, payload, headers, method=method or ('POST' if body is not None else 'GET'))
    try:
        with urlopen(req, timeout=90) as response:
            return response.status, json.load(response)
    except HTTPError as error:
        return error.code, json.load(error)


def login(email):
    status, result = request('/auth/login', {'email': email, 'password': password})
    check('Real JWT login: ' + email.split('@')[0], status == 200)
    return result


def install(context, auth=None, theme='light'):
    context.add_init_script('const values=' + json.dumps({'auth': auth, 'theme': theme}) + ''';
      localStorage.setItem('sportify.themePreference', values.theme);
      if(values.auth){sessionStorage.setItem('sportify.accessToken',values.auth.accessToken);
      sessionStorage.setItem('sportify.refreshToken',values.auth.refreshToken);}
    ''')
    if args.transport == 'proxy':
        root = Path(args.asset_root).resolve()

        def transport(route):
            parsed = urlparse(route.request.url)
            if parsed.path.startswith('/api/v1/'):
                headers = {key: value for key, value in route.request.headers.items() if key.lower() in ['authorization', 'content-type', 'origin', 'access-control-request-method', 'access-control-request-headers']}
                req = Request(args.api.removesuffix('/api/v1') + parsed.path + ('?' + parsed.query if parsed.query else ''),
                              route.request.post_data_buffer, headers, method=route.request.method)
                try:
                    result = urlopen(req, timeout=90)
                except HTTPError as error:
                    result = error
                body = result.read()
                report['requests'].append({'method': route.request.method, 'path': parsed.path, 'status': result.code,
                                           'responseSha256': hashlib.sha256(body).hexdigest()})
                route.fulfill(status=result.code, body=body, headers={key: value for key, value in result.headers.items()
                    if key.lower() not in ['content-length', 'transfer-encoding', 'connection']})
            elif parsed.netloc == urlparse(args.base).netloc:
                target = (root / parsed.path.lstrip('/')).resolve()
                if root not in target.parents or not target.is_file():
                    target = root / 'index.html'
                route.fulfill(body=target.read_bytes(), content_type=mimetypes.guess_type(target.name)[0] or 'application/octet-stream')
            else:
                route.abort()
        context.route('**/*', transport)
    context.set_default_timeout(90000)


def screenshot(page, name):
    path = out / 'screenshots' / (name + '.png')
    page.screenshot(path=str(path), full_page=True, animations='disabled')
    report['screenshots'].append({'path': str(path.relative_to(out)), 'viewport': page.viewport_size, 'atUtc': datetime.now(timezone.utc).isoformat()})
    check('No horizontal overflow: ' + name, page.evaluate('document.documentElement.scrollWidth <= innerWidth'))


def decode(locator, name, link=False):
    path = out / 'decoded-qr' / (name + '.png')
    data = locator.screenshot(path=str(path))
    values = zxingcpp.read_barcodes(Image.open(BytesIO(data)))
    check('Rendered QR decodes: ' + name, len(values) == 1)
    value = values[0].text
    if link:
        tokens.append(value.split('#token=')[1])
        parsed = urlparse(value)
        check('App link origin and path: ' + name, parsed.netloc == urlparse(args.base).netloc and parsed.path == '/demo/qr/confirm' and parsed.fragment.startswith('token='))
        recorded = value.split('#token=')[0] + '#token=[REVOKED_TOKEN_SHA256:' + hashlib.sha256(tokens[-1].encode()).hexdigest() + ']'
    else:
        recorded = value
    report['decodes'].append({'image': str(path.relative_to(out)), 'decoded': recorded, 'payloadSha256': hashlib.sha256(value.encode()).hexdigest(),
                              'tokenEvidencePolicy': 'All backend sessions are revoked by process shutdown before final publication' if link else 'Non-payable text'})
    save()
    return value


def axe(page, name):
    page.add_script_tag(path=args.axe)
    data = page.evaluate('async()=>{const r=await axe.run(document.querySelector("main"),{runOnly:{type:"tag",values:["wcag2a","wcag2aa","wcag21aa","wcag22aa"]}});return {violations:r.violations,incomplete:r.incomplete,passes:r.passes.map(p=>p.id),version:r.testEngine.version}}')
    report['accessibility'].append({'name': name, **data})
    check('Axe zero violations: ' + name, not data['violations'])


owner = login('owner@qr04.example.test')
other = login('other@qr04.example.test')
status, payment = request('/payments/' + str(args.payment_id), bearer=owner['accessToken'])
check(('Genuine payment initially PENDING' if args.backend_mode == 'live-sql' else 'Synthetic fixture payment DTO initially PENDING'), status == 200 and payment['status'] == 'PENDING')
with sync_playwright() as playwright:
    browser = playwright.chromium.launch(channel='msedge', headless=True,
        args=['--unsafely-treat-insecure-origin-as-secure=' + args.base])
    report['browser'] = browser.version
    report['transportDescription'] = 'Loopback real API responses forwarded without modification; byte-identical compiled assets served from disk. This does not validate native browser CORS or physical LAN connectivity.' if args.transport == 'proxy' else 'Native browser HTTP and CORS'
    desktop_context = browser.new_context(viewport={'width': 1440, 'height': 1000}, reduced_motion='reduce')
    install(desktop_context, owner)
    desktop_context.grant_permissions(['clipboard-read', 'clipboard-write'], origin=args.base)
    desktop = desktop_context.new_page()
    desktop.on('pageerror', lambda error: report['pageErrors'].append(str(error)))
    desktop.goto(args.base + f'/member/payments/{args.payment_id}/qr-demo')
    expect(desktop.get_by_role('combobox', name='Demo bank')).to_be_visible()
    if args.extra_only:
        desktop.get_by_role('button', name='Start demo confirmation session').click()
        expect(desktop.get_by_role('link', name='Open demo confirmation')).to_be_visible()
        link = desktop.get_by_role('link', name='Open demo confirmation').get_attribute('href')
        tokens.append(link.split('#token=')[1])
        desktop.get_by_role('button', name='Copy demo confirmation link').click()
        expect(desktop.get_by_text('Time-limited demo link copied.', exact=False)).to_be_visible()
        cdp = browser.new_browser_cdp_session()
        context_id = cdp.send('Target.getBrowserContexts')['browserContextIds'][0]
        for unsanitized in [False, True]:
            cdp.send('Browser.setPermission', {'permission': {'name': 'clipboard-write', 'allowWithoutSanitization': unsanitized},
                'setting': 'denied', 'origin': args.base, 'browserContextId': context_id})
        check('Actual clipboard permission denied', desktop.evaluate('navigator.permissions.query({name:"clipboard-write"}).then(p=>p.state)') == 'denied')
        desktop.get_by_role('button', name='Copy demo confirmation link').focus()
        desktop.keyboard.press('Enter')
        expect(desktop.get_by_text('Could not copy the demo link.', exact=False)).to_be_visible()
        check('Old clipboard success cleared', desktop.get_by_text('Time-limited demo link copied.', exact=False).count() == 0)
        screenshot(desktop, 'clipboard-permission-denied')
        axe(desktop, 'clipboard-permission-denied')
        for theme in ['light', 'dark']:
            if theme == 'dark':
                desktop.get_by_role('button', name='Switch to dark mode').click()
            for width in [320, 390, 768, 1440]:
                desktop.set_viewport_size({'width': width, 'height': 900})
                screenshot(desktop, f'desktop-pending-{width}-{theme}')
                axe(desktop, f'desktop-pending-{width}-{theme}')
            decode(desktop.locator('.demo-session-panel svg'), 'desktop-link-' + theme, True)
        for theme in ['light', 'dark']:
            context = browser.new_context(viewport={'width': 390, 'height': 844}, reduced_motion='reduce')
            install(context, owner, theme)
            page = context.new_page()
            held = []
            page.route('**/api/v1/demo/qr/sessions/preview', lambda route: held.append(route))
            page.goto(link, wait_until='domcontentloaded')
            expect(page.get_by_text('Loading demo session...', exact=True)).to_be_visible()
            screenshot(page, 'loading-' + theme)
            check('Loading disallows confirmation ' + theme, page.get_by_role('button', name='Confirm Demo Payment').count() == 0)
            check('Deferred preview request observed ' + theme, len(held) == 1)
            held[0].fallback()
            expect(page.get_by_text('PENDING', exact=True)).to_be_visible()
            page.unroute('**/api/v1/demo/qr/sessions/preview')
            page.route('**/api/v1/demo/qr/sessions/preview', lambda route: route.abort('internetdisconnected'))
            page.goto(link)
            expect(page.get_by_text('Demo link unavailable. It may be', exact=False)).to_be_visible()
            screenshot(page, 'network-error-' + theme)
            check('Network failure shows no private amount ' + theme, page.get_by_text('810.000 VND').count() == 0)
            axe(page, 'network-error-' + theme)
            context.close()
        report['faultInjection'] = 'Actual browser permission denial; preview requests deliberately deferred then forwarded, or aborted to model offline behavior. No fabricated API state.'
        desktop.get_by_role('button', name='Cancel Demo Session').click()
        expect(desktop.locator('.demo-session-panel').get_by_text('CANCELED', exact=True)).to_be_visible()
        check('Session canceled after negative browser checks', True)
        desktop_context.close(); browser.close()
        report['completedAtUtc'] = datetime.now(timezone.utc).isoformat()
        save()
        print(json.dumps({'checks': len(report['checks']), 'failed': sum(not item['pass'] for item in report['checks']), 'screenshots': len(report['screenshots'])}))
        raise SystemExit(0)
    codes = {'vietcombank': 'VIETCOMBANK_DEMO', 'bidv': 'BIDV_DEMO', 'techcombank': 'TECHCOMBANK_DEMO', 'mb': 'MB_BANK_DEMO'}
    desktop.get_by_role('button', name='Copy demo reference').click()
    check('Actual clipboard demo reference', desktop.evaluate('navigator.clipboard.readText()') == f'DEMO-{args.payment_id}')
    for bank, code in codes.items():
        desktop.get_by_role('combobox', name='Demo bank').select_option(bank)
        data = decode(desktop.locator('.qr-demo-card__symbol').first, 'data-' + bank)
        check('QR-03 exact V2 payload: ' + bank, data == f'SPORTIFY_DEMO_QR|VERSION=2|PAYMENT_ID={args.payment_id}|MODE=NON_PAYABLE|BANK_CODE={code}')
        desktop.get_by_role('button', name='Start demo confirmation session').click()
        expect(desktop.get_by_role('link', name='Open demo confirmation')).to_be_visible()
        link = decode(desktop.locator('.demo-session-panel svg'), 'link-' + bank, True)
        screenshot(desktop, 'desktop-' + bank)
        if bank != 'mb':
            desktop.get_by_role('button', name='Cancel Demo Session').click()
            expect(desktop.locator('.demo-session-panel').get_by_text('CANCELED', exact=True)).to_be_visible()
    desktop.get_by_role('button', name='Copy demo confirmation link').click()
    check('Actual clipboard confirmation link', desktop.evaluate('navigator.clipboard.readText()') == link)
    mobile_context = browser.new_context(viewport={'width': 390, 'height': 844}, is_mobile=True, has_touch=True, reduced_motion='reduce')
    install(mobile_context)
    phone = mobile_context.new_page()
    phone.goto(link)
    expect(phone.get_by_role('heading', name='Sign in as the payment owner')).to_be_visible()
    check('Fragment removed before sign-in', urlparse(phone.url).fragment == '')
    screenshot(phone, 'phone-owner-sign-in')
    axe(phone, 'owner-sign-in-390-light')
    phone.get_by_label('Email', exact=False).fill('owner@qr04.example.test')
    phone.get_by_label('Password', exact=False).first.fill(password)
    phone.get_by_role('button', name='Sign in to review demo').click()
    expect(phone.get_by_text('PENDING', exact=True)).to_be_visible()
    check('Scanning and login do not confirm', request('/demo/qr/sessions/preview', {'token': tokens[-1]}, owner['accessToken'])[1]['state'] == 'PENDING')
    screenshot(phone, 'phone-pending-390-light')
    axe(phone, 'pending-390-light')
    phone.get_by_role('button', name='Confirm Demo Payment').dblclick()
    expect(phone.get_by_text('Demo confirmation completed. The real payment remains unchanged.')).to_be_visible()
    screenshot(phone, 'phone-confirmed-390-light')
    desktop.get_by_role('button', name='Refresh Demo Status').click()
    expect(desktop.locator('.demo-session-panel').get_by_text('CONFIRMED', exact=True)).to_be_visible()
    screenshot(desktop, 'desktop-confirmed-refresh')
    check(('Real payment still PENDING after cross-context confirmation' if args.backend_mode == 'live-sql' else 'Synthetic fixture payment DTO still PENDING after cross-context confirmation'), request('/payments/' + str(args.payment_id), bearer=owner['accessToken'])[1]['status'] == 'PENDING')

    for theme in ['light', 'dark']:
        for width in [320, 390, 768, 1440]:
            context = browser.new_context(viewport={'width': width, 'height': 900}, reduced_motion='reduce', has_touch=True)
            install(context, owner, theme)
            page = context.new_page()
            page.goto(link)
            expect(page.get_by_text('CONFIRMED', exact=True)).to_be_visible()
            screenshot(page, f'confirmed-{width}-{theme}')
            axe(page, f'confirmed-{width}-{theme}')
            for button in page.get_by_role('button').all():
                box = button.bounding_box()
                check(f'Touch target {width} {theme}', box is not None and box['height'] >= 24 and box['width'] >= 24)
            page.keyboard.press('Tab')
            check(f'Keyboard focus {width} {theme}', page.evaluate('document.activeElement !== document.body'))
            context.close()
    wrong_context = browser.new_context(viewport={'width': 390, 'height': 844})
    install(wrong_context, other)
    wrong = wrong_context.new_page()
    wrong.goto(link)
    expect(wrong.get_by_text('Demo link unavailable. It may be', exact=False)).to_be_visible()
    check('Wrong member cannot see demo amount', wrong.get_by_text('810.000 VND').count() == 0)
    screenshot(wrong, 'phone-wrong-owner')
    axe(wrong, 'wrong-owner')
    phone.reload()
    expect(phone.get_by_text('The token is missing or malformed.', exact=False)).to_be_visible()
    screenshot(phone, 'phone-reload-missing-token')
    desktop.goto(args.base + f'/member/payments/{args.second_payment_id}/qr-demo')
    data = decode(desktop.locator('.qr-demo-card__symbol').first, 'second-payment')
    check('Authorized payment transition changes actual QR', f'PAYMENT_ID={args.second_payment_id}|' in data)
    check('Payment transition removes old confirmation link', desktop.get_by_role('link', name='Open demo confirmation').count() == 0)
    screenshot(desktop, 'desktop-second-payment')
    # Independent native browser zoom, not CSS transform or deviceScaleFactor.
    for theme in ['light', 'dark']:
        with tempfile.TemporaryDirectory(prefix='qr04-zoom-', ignore_cleanup_errors=True) as profile:
            context = playwright.chromium.launch_persistent_context(profile, channel='msedge', headless=True, viewport={'width': 1440, 'height': 900}, reduced_motion='reduce')
            settings = context.pages[0]
            settings.goto('edge://settings/appearance')
            zoom = settings.evaluate('()=>new Promise(r=>chrome.settingsPrivate.setDefaultZoom(2,()=>chrome.settingsPrivate.getDefaultZoom(r)))')
            install(context, owner, theme)
            page = context.new_page()
            page.goto(link)
            expect(page.get_by_text('CONFIRMED', exact=True)).to_be_visible()
            metrics = page.evaluate('({width:innerWidth,dpr:devicePixelRatio,scale:visualViewport.scale,overflow:document.documentElement.scrollWidth>innerWidth})')
            check('Native 200 percent zoom ' + theme, zoom == 2 and metrics == {'width': 720, 'dpr': 2, 'scale': 1, 'overflow': False}, metrics)
            screenshot(page, 'native-200pct-' + theme)
            context.close()
    # Canceled links are still owner-readable but can never confirm.
    canceled = args.base + '/demo/qr/confirm#token=' + tokens[0]
    for theme in ['light', 'dark']:
        context = browser.new_context(viewport={'width': 390, 'height': 844}, reduced_motion='reduce')
        install(context, owner, theme)
        page = context.new_page()
        page.goto(canceled)
        expect(page.get_by_text('CANCELED', exact=True)).to_be_visible()
        screenshot(page, 'phone-canceled-' + theme)
        axe(page, 'canceled-' + theme)
        context.close()
    # Let a real server session expire while other verification proceeds independently.
    for attempt in range(65):
        status, expiring = request('/demo/qr/sessions', {'paymentId': args.second_payment_id, 'bankCode': 'BIDV_DEMO'}, owner['accessToken'])
        if status != 429:
            break
        time.sleep(1)
    check('Real short-lived session creation', status == 200)
    expiry_link = expiring['confirmationUrl']
    tokens.append(expiry_link.split('#token=')[1])
    for theme in ['light', 'dark']:
        context = browser.new_context(viewport={'width': 390, 'height': 844}, reduced_motion='reduce')
        install(context, owner, theme)
        page = context.new_page()
        page.goto(expiry_link)
        expect(page.get_by_text('PENDING', exact=True)).to_be_visible()
        screenshot(page, 'phone-pending-' + theme)
        axe(page, 'pending-' + theme)
        context.close()
    expiry_context = browser.new_context(viewport={'width': 320, 'height': 700}, reduced_motion='reduce')
    install(expiry_context, owner, 'dark')
    expiry_page = expiry_context.new_page()
    expiry_page.goto(expiry_link)
    expect(expiry_page.get_by_text('The displayed deadline has passed.', exact=False)).to_be_visible(timeout=310000)
    expiry_page.get_by_role('button', name='Refresh Demo Status').click()
    expect(expiry_page.get_by_text('EXPIRED', exact=True)).to_be_visible()
    screenshot(expiry_page, 'phone-expired-320-dark')
    axe(expiry_page, 'expired-320-dark')
    check('Expired real server confirmation denied', request('/demo/qr/sessions/confirm', {'token': tokens[-1]}, owner['accessToken'])[0] == 409)
    expiry_context.close()
    if args.normal_asset_root:
        demo_root = args.asset_root
        args.asset_root = args.normal_asset_root
        normal_context = browser.new_context(viewport={'width': 390, 'height': 844})
        install(normal_context, owner)
        normal = normal_context.new_page()
        normal.goto(args.base + '/demo/qr/confirm#token=' + tokens[-1])
        expect(normal.get_by_role('heading', name='Page not found')).to_be_visible()
        check('Normal production build rejects confirmation even with frontend flag true', normal.get_by_role('button', name='Confirm Demo Payment').count() == 0)
        screenshot(normal, 'normal-production-disabled')
        normal_context.close()
        args.asset_root = demo_root
    check('No browser page errors', not report['pageErrors'], report['pageErrors'])
    desktop_context.close(); mobile_context.close(); wrong_context.close(); browser.close()
report['completedAtUtc'] = datetime.now(timezone.utc).isoformat()
save()
print(json.dumps({'checks': len(report['checks']), 'failed': sum(not c['pass'] for c in report['checks']),
                  'screenshots': len(report['screenshots']), 'decodes': len(report['decodes']), 'axeRuns': len(report['accessibility'])}))
