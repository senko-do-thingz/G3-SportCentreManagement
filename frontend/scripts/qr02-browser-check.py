"""QR-02 real Edge acceptance using synthetic read-only API fixtures and the actual clipboard."""
import argparse
import base64
from datetime import datetime, timezone
from io import BytesIO
import hashlib
import json
import mimetypes
from pathlib import Path
import subprocess
import tempfile
from urllib.parse import urlparse

from PIL import Image
from playwright.sync_api import sync_playwright, expect
import zxingcpp

parser = argparse.ArgumentParser()
parser.add_argument('--base', default='http://127.0.0.1:5184')
parser.add_argument('--normal-base', default='http://127.0.0.1:5185')
parser.add_argument('--out', required=True)
parser.add_argument('--axe', required=True)
parser.add_argument('--asset-root', help='Optional byte-identical compiled asset transport for local Windows network failures')
parser.add_argument('--normal-asset-root')
args = parser.parse_args()
out = Path(args.out)
out.joinpath('screenshots').mkdir(parents=True, exist_ok=True)
out.joinpath('logs').mkdir(exist_ok=True)
sha = subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip()
report = {'sha': sha, 'dirty': bool(subprocess.check_output(['git', 'status', '--porcelain'], text=True).strip()),
          'mode': 'FIXTURE/MOCK - intercepted synthetic API; real Edge rendering and clipboard',
          'checks': [], 'screenshots': [], 'requests': [], 'blockedExternal': [], 'decodes': [], 'clipboard': [],
          'accessibility': [], 'comparisons': [], 'pageErrors': []}
report['assetTransport'] = {'demoRoot': args.asset_root, 'normalRoot': args.normal_asset_root,
    'description': 'Optional byte-identical compiled assets fulfilled from disk because local Windows browser HTTP requests intermittently returned ERR_NETWORK_ACCESS_DENIED. API responses remain explicit synthetic fixtures.'}
PAYLOAD = 'SPORTIFY_DEMO_QR|VERSION=1|PAYMENT_ID=52|MODE=NON_PAYABLE|BANK_LABEL=VIETCOMBANK_DEMO'
BANKS = {'vietcombank': 'Vietcombank (demo)', 'bidv': 'BIDV (demo)', 'techcombank': 'Techcombank (demo)', 'mb': 'MB Bank (demo)'}
ROUTE = '/member/payments/52/qr-demo'
PAYMENT = {'id': 52, 'code': 'DEMO-52', 'status': 'PENDING', 'amount': 810000, 'method': 'MOMO',
           'originalPrice': 900000, 'discount': 90000, 'itemDescription': 'Demo football package',
           'paymentTime': None, 'memberId': 2, 'createdAt': '2026-10-29T09:00:00',
           'bankTransferInstructions': {'bank': 'PRIVATE BANK MUST NOT BE USED', 'accountName': 'PRIVATE RECIPIENT',
                                        'accountNumber': 'PRIVATE-ACCOUNT', 'transferContent': 'PRIVATE TRANSFER'}}
MEMBER = {'id': 2, 'email': 'demo@example.invalid', 'fullName': 'Demo Member', 'phone': None, 'role': 'MEMBER', 'status': 'ACTIVE'}


def save():
    (out / 'logs/qr02-browser-results.json').write_text(json.dumps(report, indent=2), encoding='utf-8')


def check(name, value, detail=None):
    report['checks'].append({'name': name, 'pass': bool(value), 'detail': detail})
    save()
    if not value:
        raise AssertionError(name + ': ' + str(detail))


def install(ctx, theme, mode='default', role='MEMBER'):
    ctx.qr02_pending_routes = []
    seed = {'sportify.themePreference': theme}
    if role != 'ANONYMOUS':
        seed.update({'sportify.accessToken': 'qr02-fixture-only', 'sportify.refreshToken': 'fixture-only', 'sportify.sessionId': 'qr02-fixture'})
    ctx.add_init_script('for(const [k,v] of Object.entries(' + json.dumps(seed) + '))localStorage.setItem(k,v)')

    def intercept(r):
        req = r.request
        url = urlparse(req.url)
        record = {'method': req.method, 'origin': url.scheme + '://' + url.netloc, 'path': url.path, 'mode': mode}
        report['requests'].append(record)
        if '/api/v1/' in url.path:
            path = url.path.split('/api/v1')[-1]
            if req.method not in ['GET', 'OPTIONS']:
                r.abort('blockedbyclient')
                check('No financial mutations', False, record)
                return
            status = 200
            data = []
            if path == '/users/me':
                data = dict(MEMBER, role=role)
            elif path.startswith('/payments/') and path != '/payments/my':
                if mode == 'loading':
                    ctx.qr02_pending_routes.append(r)
                    return
                if mode in ['error', 'forbidden', 'missing']:
                    status = {'error': 503, 'forbidden': 403, 'missing': 404}[mode]
                    data = {'message': 'Synthetic payment unavailable'}
                else:
                    data = dict(PAYMENT, status='SUCCESS' if mode == 'terminal' else 'PENDING',
                                id=53 if mode == 'mismatch' else 52, amount='invalid' if mode == 'bad-amount' else 810000,
                                method='BANK_TRANSFER' if mode == 'bank-transfer' else 'MOMO')
            elif path == '/sports':
                data = [{'id': 1, 'code': 'FOOTBALL', 'name': 'Football'}]
            r.fulfill(status=status, json=data, headers={'Access-Control-Allow-Origin': '*',
                      'Access-Control-Allow-Headers': 'Authorization, Content-Type', 'Access-Control-Allow-Methods': 'GET, OPTIONS'})
        elif url.hostname in ['127.0.0.1', 'localhost', '::1']:
            asset_root = args.normal_asset_root if url.netloc == urlparse(args.normal_base).netloc else args.asset_root
            if asset_root:
                root = Path(asset_root).resolve()
                asset = (root / url.path.lstrip('/')).resolve()
                if not asset.is_relative_to(root):
                    r.abort('blockedbyclient')
                    return
                if not asset.is_file() and not url.path.startswith('/assets/'):
                    asset = root / 'index.html'
                if not asset.is_file():
                    r.abort('failed')
                    return
                contents = asset.read_bytes()
                record['assetSha256'] = hashlib.sha256(contents).hexdigest()
                r.fulfill(body=contents, content_type=mimetypes.guess_type(asset)[0] or 'application/octet-stream')
            else:
                r.continue_()
        else:
            report['blockedExternal'].append({'host': url.hostname, 'path': url.path})
            r.abort('blockedbyclient')

    ctx.route('**/*', intercept)


def page_for(browser, width=1440, theme='light', route=ROUTE, mode='default', role='MEMBER', base=None):
    ctx = browser.new_context(viewport={'width': width, 'height': {1440: 900, 768: 1024, 390: 844}[width]},
                              reduced_motion='reduce')
    install(ctx, theme, mode, role)
    page = ctx.new_page()
    page.on('pageerror', lambda e: report['pageErrors'].append(str(e)))
    page.on('requestfailed', lambda r: report.setdefault('requestFailures', []).append({'path': urlparse(r.url).path, 'failure': r.failure}))
    page.goto((base or args.base) + route)
    ctx.grant_permissions(['clipboard-read', 'clipboard-write'], origin=base or args.base)
    page.evaluate('document.fonts.ready')
    return ctx, page


def capture(page, theme, state, bank=None, pixels=None):
    width = page.viewport_size['width']
    size = 'desktop-1440' if width == 1440 else 'tablet-768' if width == 768 else 'mobile-390'
    file = out / f'screenshots/QR-02-i-round-01-after-{theme}-{size}-{state}-fixture.png'
    if pixels is None:
        page.evaluate('async()=>{window.scrollTo(0,0);await new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r)))}')
        page.screenshot(path=str(file), full_page=True, animations='disabled')
    else:
        file.write_bytes(pixels)
    report['screenshots'].append({'path': str(file), 'sha': sha, 'dirty': report['dirty'],
        'sha256': hashlib.sha256(file.read_bytes()).hexdigest(), 'route': urlparse(page.url).path,
        'viewport': page.viewport_size, 'theme': theme, 'bank': bank, 'state': state, 'mode': report['mode'],
        'capture': 'physical viewport' if pixels is not None else 'full page at specified viewport',
        'browser': report['browser'], 'time': datetime.now(timezone.utc).isoformat()})
    save()


def safe_payload(text):
    if text != PAYLOAD or 'MODE=NON_PAYABLE' not in text or any(s in text for s in ['000201', 'https:', 'ACCOUNT=', 'NAPAS']):
        raise ValueError('Unsafe or changed QR payload')


def decode(page, bank):
    pixels = page.locator('.qr-demo-card__symbol').screenshot(animations='disabled')
    result = zxingcpp.read_barcode(Image.open(BytesIO(pixels)))
    check('Independent decoder reads actual QR for ' + bank, result is not None)
    safe_payload(result.text)
    report['decodes'].append({'bank': BANKS[bank], 'text': result.text, 'sha256': hashlib.sha256(pixels).hexdigest(),
                               'viewport': page.viewport_size, 'theme': page.locator('html').get_attribute('data-theme')})
    save()


def styles(locator):
    return locator.evaluate('(e)=>{const s=getComputedStyle(e);return Object.fromEntries(["width","height","font-family","font-size","font-style","font-weight","letter-spacing","border-radius","color","background-color"].map(k=>[k,s.getPropertyValue(k)]))}')


def accessible(page, label):
    page.add_script_tag(path=args.axe)
    data = page.evaluate('async()=>{const r=await axe.run({include:[[".qr-demo-card"]]},{runOnly:{type:"tag",values:["wcag2a","wcag2aa","wcag21aa","wcag22aa"]}});return {violations:r.violations,incomplete:r.incomplete,passes:r.passes.map(p=>p.id),version:r.testEngine.version}}')
    report['accessibility'].append({'state': label, **data})
    check('Axe WCAG card ' + label, not data['violations'], data['violations'])


with sync_playwright() as pw:
    browser = pw.chromium.launch(channel='msedge', headless=True)
    report['browser'] = 'Microsoft Edge ' + browser.version
    try:
        for width in [1440, 768, 390]:
            for theme in ['light', 'dark']:
                ctx, page = page_for(browser, width, theme, '/', role='ANONYMOUS')
                expect(page.locator('.home-header')).to_be_visible()
                home = {s: styles(page.locator('.home-header ' + s)) for s in ['.brand__mark', '.brand__name', '.home-theme']}
                capture(page, theme, 'home-baseline')
                ctx.close()
                ctx, page = page_for(browser, width, theme)
                select = page.get_by_role('combobox', name='Demo bank', exact=True)
                expect(select).to_have_value('vietcombank')
                check('Exactly four approved options ' + theme + str(width), select.locator('option').all_text_contents() == list(BANKS.values()))
                for s, expected in home.items():
                    actual = styles(page.locator('.payment-workspace-header ' + s))
                    report['comparisons'].append({'selector': s, 'viewport': width, 'theme': theme, 'home': expected, 'qr02': actual})
                    check('Home header parity ' + s + theme + str(width), actual == expected, {'home': expected, 'card': actual})
                check('No overflow ' + theme + str(width), page.evaluate('document.documentElement.scrollWidth<=innerWidth'))
                check('Safe account and no real instructions', page.get_by_text('DEMO - NOT AVAILABLE', exact=True).is_visible() and 'PRIVATE' not in page.locator('.qr-demo-card').inner_text())
                check('Disclosure present', 'fixed, non-payable Vietcombank demo sample' in page.locator('.qr-demo-card').inner_text())
                decode(page, 'vietcombank')
                capture(page, theme, 'default', 'Vietcombank (demo)')
                accessible(page, theme + '-' + str(width))
                geometry = page.locator('.qr-demo-card button, .qr-demo-card select').evaluate_all('(els)=>els.map(e=>({name:e.textContent.trim(),w:e.getBoundingClientRect().width,h:e.getBoundingClientRect().height}))')
                check('Touch targets at least 44 by 44 ' + theme + str(width), all(g['w'] >= 44 and g['h'] >= 44 for g in geometry), geometry)
                ctx.close()

        ctx, page = page_for(browser)
        select = page.get_by_role('combobox', name='Demo bank')
        expect(select).to_be_visible()
        select.focus()
        page.keyboard.press('ArrowDown')
        expect(select).to_have_value('bidv')
        page.keyboard.press('Home')
        expect(select).to_have_value('vietcombank')
        page.keyboard.press('Space')
        capture(page, 'light', 'selector-open')
        report['nativeSelectorCapture'] = 'Actual native selector open attempt; DOM option inventory is separately asserted. OS popup may not be included by compositor capture.'
        page.keyboard.press('Escape')
        page.keyboard.press('Tab')
        ref = page.get_by_role('button', name='Copy demo reference', exact=True)
        expect(ref).to_be_focused()
        check('Visible keyboard focus', ref.evaluate('(e)=>getComputedStyle(e).outlineStyle') != 'none')
        capture(page, 'light', 'keyboard-focus')
        page.keyboard.press('Enter')
        expect(page.get_by_role('status')).to_contain_text('Demo reference copied.')
        text = page.evaluate('navigator.clipboard.readText()')
        check('Actual clipboard exact reference', text == 'DEMO-52', text)
        report['clipboard'].append({'action': 'reference', 'actual': text, 'mode': 'real clipboard API'})
        expect(ref).to_be_focused()
        capture(page, 'light', 'copy-reference-success')
        page.keyboard.press('Tab')
        details = page.get_by_role('button', name='Copy demo details', exact=True)
        expect(details).to_be_focused()
        page.keyboard.press('Shift+Tab')
        expect(ref).to_be_focused()
        ctx.close()

        for bank, width, theme in [('vietcombank', 1440, 'light'), ('bidv', 390, 'dark'), ('techcombank', 1440, 'light'), ('mb', 768, 'dark')]:
            ctx, page = page_for(browser, width, theme)
            select = page.get_by_role('combobox', name='Demo bank')
            select.select_option(bank)
            expect(page.locator('.summary-list dd').first).to_have_text(BANKS[bank])
            decode(page, bank)
            capture(page, theme, bank + '-selected', BANKS[bank])
            page.get_by_role('button', name='Copy demo details', exact=True).click()
            expect(page.get_by_role('status')).to_contain_text('Demo details copied.')
            actual = page.evaluate('navigator.clipboard.readText()')
            expected = '\n'.join(['Demo bank: ' + BANKS[bank], 'Account: DEMO - NOT AVAILABLE', 'Amount from payment: 810.000 VND', 'Demo reference: DEMO-52', 'DEMO ONLY - NOT A REAL PAYMENT'])
            check('Actual clipboard exact details with OS newline normalization ' + bank, actual.replace('\r\n', '\n') == expected, actual)
            report['clipboard'].append({'bank': BANKS[bank], 'actual': actual, 'expectedLf': expected,
                                        'newlinePolicy': 'Windows clipboard readText normalizes LF to CRLF; all other characters exact', 'mode': 'real clipboard API'})
            capture(page, theme, bank + '-selected-copy-details-success', BANKS[bank])
            select.select_option('mb' if bank != 'mb' else 'bidv')
            expect(page.get_by_role('status')).to_be_empty()
            ctx.close()

        ctx, page = page_for(browser, 390, 'dark')
        expect(page.locator('.qr-demo-card')).to_be_visible()
        ctx.clear_permissions()
        cdp = browser.new_browser_cdp_session()
        contexts = cdp.send('Target.getBrowserContexts')['browserContextIds']
        check('Unique clipboard denial test context', len(contexts) == 1)
        for unsanitized in [False, True]:
            cdp.send('Browser.setPermission', {'permission': {'name': 'clipboard-write', 'allowWithoutSanitization': unsanitized},
                     'setting': 'denied', 'origin': args.base, 'browserContextId': contexts[0]})
        permission = page.evaluate('navigator.permissions.query({name:"clipboard-write"}).then(p=>p.state)')
        check('Real browser clipboard permission denied', permission == 'denied', permission)
        report['clipboardDenied'] = {'permissionState': permission, 'mode': 'Actual CDP permission denial in the test context, no writeText stub'}
        button = page.get_by_role('button', name='Copy demo reference', exact=True)
        button.focus()
        page.keyboard.press('Enter')
        expect(page.get_by_role('alert')).to_contain_text('Could not copy.')
        expect(page.get_by_role('status')).to_be_empty()
        expect(button).to_be_focused()
        capture(page, 'dark', 'clipboard-denied')
        accessible(page, 'clipboard-denied')
        ctx.close()

        for mode in ['loading', 'error', 'forbidden', 'missing', 'terminal', 'mismatch', 'bad-amount']:
            ctx, page = page_for(browser, 390, 'dark', mode=mode)
            if mode == 'loading':
                expect(page.get_by_text('Loading the payment demo', exact=True)).to_be_attached()
            else:
                title = 'We could not load the payment demo' if mode in ['error', 'forbidden', 'missing'] else 'Demo unavailable for this payment' if mode == 'terminal' else 'Payment details unavailable'
                expect(page.get_by_role('heading', name=title, exact=True)).to_be_visible()
            check('No QR/copy for ' + mode, page.locator('.qr-demo-card').count() == 0)
            if mode == 'error':
                page.get_by_role('button', name='Try again', exact=True).click()
                expect(page.get_by_role('heading', name='We could not load the payment demo', exact=True)).to_be_visible()
            capture(page, 'dark', mode)
            for pending in ctx.qr02_pending_routes:
                pending.abort('aborted')
            ctx.unroute_all(behavior='ignoreErrors')
            ctx.close()

        for route, role, label in [('/member/payments/invalid/qr-demo', 'MEMBER', 'invalid-id'), (ROUTE, 'ANONYMOUS', 'anonymous-guard'), (ROUTE, 'RECEPTIONIST', 'role-guard')]:
            start = len(report['requests'])
            ctx, page = page_for(browser, 390, 'light', route, role=role)
            expect(page.get_by_role('heading', level=1)).to_be_visible()
            if label == 'invalid-id':
                expect(page.get_by_text('The demo payment link is not valid.', exact=True)).to_be_visible()
            check(label + ' no payment read', not any('/api/v1/payments/' in r['path'] for r in report['requests'][start:]))
            check(label + ' no copy', page.locator('.qr-demo-card').count() == 0)
            capture(page, 'light', label)
            ctx.close()

        ctx, page = page_for(browser, 1440, 'light', '/member/payments/52')
        expect(page.get_by_role('button', name='Continue to payment', exact=True)).to_be_enabled()
        expect(page.get_by_role('radio', name='MoMo', exact=True)).to_be_disabled()
        capture(page, 'light', 'qr01-entry')
        page.get_by_role('link', name='QR Payment (Demo)', exact=True).click()
        expect(page.get_by_role('combobox', name='Demo bank')).to_be_visible()
        page.get_by_role('link', name='Back to Payment Summary', exact=True).click()
        expect(page.get_by_role('heading', name='Order summary', exact=True)).to_be_visible()
        check('Back to summary retains Continue', page.get_by_role('button', name='Continue to payment', exact=True).is_enabled())
        ctx.close()
        ctx, page = page_for(browser, 390, 'light', '/member/payments/52/status', mode='bank-transfer')
        expect(page.get_by_role('heading', name='Bank transfer', exact=False)).to_be_visible()
        capture(page, 'light', 'existing-ui28')
        ctx.close()
        start = len(report['requests'])
        ctx, page = page_for(browser, 390, 'light', base=args.normal_base)
        expect(page.get_by_role('heading', name='Page not found', exact=True)).to_be_visible()
        check('Standard production no payment read', not any('/api/v1/payments/' in r['path'] for r in report['requests'][start:]))
        capture(page, 'light', 'standard-production-disabled')
        page.goto(args.normal_base + '/member/payments/52')
        expect(page.get_by_role('heading', name='Order summary', exact=True)).to_be_visible()
        check('Standard production entry absent with flag true', page.get_by_role('link', name='QR Payment (Demo)', exact=True).count() == 0)
        ctx.close()

        for theme in ['light', 'dark']:
            with tempfile.TemporaryDirectory(prefix='qr02-zoom-', ignore_cleanup_errors=True) as profile:
                ctx = pw.chromium.launch_persistent_context(profile, channel='msedge', headless=True, viewport={'width': 1440, 'height': 900}, reduced_motion='reduce')
                settings = ctx.pages[0]
                settings.goto('edge://settings/appearance')
                zoom = settings.evaluate('()=>new Promise(r=>chrome.settingsPrivate.setDefaultZoom(2,()=>chrome.settingsPrivate.getDefaultZoom(r)))')
                install(ctx, theme)
                page = ctx.new_page()
                page.goto(args.base + ROUTE)
                expect(page.locator('.qr-demo-card')).to_be_visible()
                metrics = page.evaluate('({width:innerWidth,height:innerHeight,dpr:devicePixelRatio,scale:visualViewport.scale,overflow:document.documentElement.scrollWidth>innerWidth})')
                check('Native 200 percent zoom ' + theme, zoom == 2 and metrics == {'width': 720, 'height': 450, 'dpr': 2, 'scale': 1, 'overflow': False}, metrics)
                page.locator('.qr-demo-card__symbol').scroll_into_view_if_needed()
                page.evaluate('()=>{const q=document.querySelector(".qr-demo-card__symbol").getBoundingClientRect(),h=document.querySelector(".payment-workspace-header").getBoundingClientRect();window.scrollBy(0,q.top-h.bottom-16)}')
                pixels = base64.b64decode(ctx.new_cdp_session(page).send('Page.captureScreenshot', {'format': 'png', 'fromSurface': True, 'captureBeyondViewport': False})['data'])
                capture(page, theme, 'native-200pct-qr', pixels=pixels)
                for button in page.locator('.qr-demo-card button').all():
                    button.scroll_into_view_if_needed()
                    check('Zoom copy button within horizontal viewport', button.evaluate('(e)=>{const r=e.getBoundingClientRect();return r.left>=0&&r.right<=innerWidth}'))
                capture(page, theme, 'native-200pct-copy-controls', pixels=base64.b64decode(ctx.new_cdp_session(page).send('Page.captureScreenshot', {'format': 'png', 'fromSurface': True, 'captureBeyondViewport': False})['data']))
                ctx.close()

        for bad in ['000201010212BANK_TRANSFER', PAYLOAD.replace('MODE=NON_PAYABLE', 'MODE=PAYABLE')]:
            rejected = False
            try:
                safe_payload(bad)
            except ValueError:
                rejected = True
            check('QR security negative control rejects ' + bad, rejected)
        check('No financial network mutations', all(r['method'] in ['GET', 'OPTIONS'] for r in report['requests']))
        check('No bank/gateway/remote QR request', not any(any(s in (r['host'] or '').lower() for s in ['vietqr', 'napas', 'bank', 'momo', 'vnpay', 'zalopay']) for r in report['blockedExternal']))
        check('No uncaught app errors', not report['pageErrors'], report['pageErrors'])
    except Exception as error:
        report['failure'] = {'message': str(error), 'url': page.url, 'body': page.locator('body').inner_text()}
        capture(page, theme, 'harness-failure')
        raise
    finally:
        browser.close()
        save()
print(json.dumps({'checks': len(report['checks']), 'screenshots': len(report['screenshots']), 'decodes': len(report['decodes']), 'sha': sha}))
