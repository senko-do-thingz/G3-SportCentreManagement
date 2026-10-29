"""Real Edge with unmodified authenticated HTTP responses from isolated SQL Server."""
import argparse
import hashlib
import json
import mimetypes
import ssl
from io import BytesIO
from pathlib import Path
from urllib.error import HTTPError
from urllib.parse import urlparse
from urllib.request import Request, urlopen
from PIL import Image
from playwright.sync_api import sync_playwright, expect
import zxingcpp

parser = argparse.ArgumentParser()
parser.add_argument('--base', default='https://demo.example.test')
parser.add_argument('--api', default='http://127.0.0.1:8094/api/v1')
parser.add_argument('--out', required=True)
parser.add_argument('--asset-root', required=True)
parser.add_argument('--axe', required=True)
parser.add_argument('--payment-id', type=int, required=True)
parser.add_argument('--email', default='owner@qr04.example.test')
parser.add_argument('--native-https', action='store_true', help='Use real HTTPS sockets and browser CORS without request interception.')
parser.add_argument('--tls-ca', help='Isolated test CA PEM for Python HTTP verification.')
parser.add_argument('--spki-pin', help='Trust only this certificate public-key pin in the isolated Edge context.')
parser.add_argument('--host-resolver-rules', help='Optional isolated Edge DNS mappings for test hosts.')
args = parser.parse_args()
out = Path(args.out)
for folder in ['screenshots', 'browser-logs']:
    (out / folder).mkdir(parents=True, exist_ok=True)
report = {'transport': 'Real Edge, compiled disk assets, unmodified loopback HTTP to isolated SQL Server. Native CORS/TLS and physical device are not validated.',
          'checks': [], 'accessibility': [], 'requests': [], 'pageErrors': [], 'physicalPhone': 'NOT RUN', 'screenReader': 'NOT RUN', 'native200PercentZoom': 'NOT RUN'}
tokens = []
auth = None
tls_context = ssl.create_default_context(cafile=args.tls_ca) if args.tls_ca else None
if args.native_https:
    report['transport'] = 'Native HTTPS sockets, real browser CORS and real SQL backend; no request interception. Certificate trust and DNS overrides are scoped to the isolated QA Edge context.'

def request(path, body=None, bearer=None, method=None):
    headers = {'Content-Type': 'application/json'}
    if bearer:
        headers['Authorization'] = 'Bearer ' + bearer
    req = Request(args.api + path, json.dumps(body).encode() if body is not None else None, headers, method=method or ('POST' if body is not None else 'GET'))
    try:
        with urlopen(req, timeout=30, context=tls_context) as response:
            return response.status, json.load(response)
    except HTTPError as failure:
        return failure.code, json.load(failure)

def check(name, passed):
    report['checks'].append({'name': name, 'result': 'PASS' if passed else 'FAIL'})
    if not passed:
        raise AssertionError(name)

def install(context, theme):
    context.add_init_script('const values=' + json.dumps({'auth': auth, 'theme': theme}) + ''';
      localStorage.setItem('sportify.themePreference',values.theme);
      sessionStorage.setItem('sportify.accessToken',values.auth.accessToken);
      sessionStorage.setItem('sportify.refreshToken',values.auth.refreshToken);
    ''')
    root = Path(args.asset_root).resolve()
    def transport(route):
        parsed = urlparse(route.request.url)
        if parsed.path.startswith('/api/v1/'):
            headers = {key: value for key, value in route.request.headers.items() if key.lower() in ['authorization', 'content-type']}
            req = Request(args.api.removesuffix('/api/v1') + parsed.path + ('?' + parsed.query if parsed.query else ''), route.request.post_data_buffer, headers, method=route.request.method)
            try:
                result = urlopen(req, timeout=90)
            except HTTPError as failure:
                result = failure
            body = result.read()
            report['requests'].append({'method': route.request.method, 'path': parsed.path, 'status': result.code})
            route.fulfill(status=result.code, body=body, content_type='application/json')
        elif parsed.netloc == urlparse(args.base).netloc:
            target = (root / parsed.path.lstrip('/')).resolve()
            if root not in target.parents or not target.is_file():
                target = root / 'index.html'
            route.fulfill(body=target.read_bytes(), content_type=mimetypes.guess_type(target.name)[0] or 'application/octet-stream')
        else:
            route.abort()
    if not args.native_https:
        context.route('**/*', transport)
    context.set_default_timeout(90000)

def inspect(page, name):
    page.evaluate('document.fonts.ready')
    page.screenshot(path=str(out / 'screenshots' / (name + '.png')), full_page=True, animations='disabled')
    check('No horizontal overflow: ' + name, page.evaluate('document.documentElement.scrollWidth<=innerWidth'))
    page.add_script_tag(path=args.axe)
    result = page.evaluate('''async()=>{const r=await axe.run(document,{runOnly:{type:'tag',values:['wcag2a','wcag2aa','wcag21aa','wcag22aa']}});return {violations:r.violations,incomplete:r.incomplete,passes:r.passes.map(p=>p.id),version:r.testEngine.version}}''')
    report['accessibility'].append({'name': name, 'result': 'FAIL' if result['violations'] else 'PASS', **result})

def new_page(browser, theme='light', width=390):
    context = browser.new_context(viewport={'width': width, 'height': 900}, reduced_motion='reduce')
    install(context, theme)
    page = context.new_page()
    page.on('pageerror', lambda error: report['pageErrors'].append(str(error)))
    return context, page

try:
    status, auth = request('/auth/login', {'email': args.email, 'password': 'Qr04IsolatedTest2026!'})
    check('Real owner JWT login', status == 200)
    status, capability = request('/payments/simulated-qr/capability', bearer=auth['accessToken'])
    check('Intent enabled with settlement disabled', status == 200 and capability['enabled'] and not capability['settlementEnabled'])
    with sync_playwright() as playwright:
        launch_args = []
        if args.native_https:
            launch_args.append('--no-proxy-server')
            if args.spki_pin:
                launch_args.append('--ignore-certificate-errors-spki-list=' + args.spki_pin)
            if args.host_resolver_rules:
                launch_args.append('--host-resolver-rules=' + args.host_resolver_rules)
        browser = playwright.chromium.launch(channel='msedge', headless=True, args=launch_args)
        report['browserVersion'] = browser.version
        for theme in ['light', 'dark']:
            context, desktop = new_page(browser, theme, 1440)
            desktop.goto(args.base + f'/member/payments/{args.payment_id}/qr-demo')
            expect(desktop.get_by_role('button', name='Start simulation session')).to_be_visible()
            desktop.get_by_role('button', name='Start simulation session').click()
            link = desktop.get_by_role('link', name='Review confirmation intent')
            expect(link).to_be_visible()
            url = link.get_attribute('href')
            tokens.append(url.split('#token=')[1])
            values = zxingcpp.read_barcodes(Image.open(BytesIO(desktop.locator('.demo-session-panel svg').screenshot())))
            check('Rendered application QR decodes: ' + theme, len(values) == 1 and values[0].text == url)
            legacy = zxingcpp.read_barcodes(Image.open(BytesIO(desktop.locator('.qr-demo-card svg').first.screenshot())))
            check('QR03 NON_PAYABLE decoder: ' + theme, len(legacy) == 1 and 'MODE=NON_PAYABLE' in legacy[0].text)
            for width in [390, 768, 1440]:
                desktop.set_viewport_size({'width': width, 'height': 900})
                inspect(desktop, f'desktop-pending-{width}-{theme}')
            check('Reduced motion preference active: ' + theme, desktop.evaluate("matchMedia('(prefers-reduced-motion: reduce)').matches"))
            menu = desktop.get_by_role('button', name='Open member navigation')
            if menu.is_visible():
                menu.click()
                desktop.keyboard.press('Escape')
                check('Escape closes member navigation and restores focus: ' + theme,
                      menu.get_attribute('aria-expanded') == 'false' and menu.evaluate('(element)=>document.activeElement===element'))
            mobile_context, mobile = new_page(browser, theme)
            mobile.goto(url)
            expect(mobile.get_by_role('button', name='Record confirmation intent')).to_be_visible()
            check('Fragment cleared: ' + theme, '#' not in mobile.url)
            inspect(mobile, 'mobile-pending-' + theme)
            mobile.get_by_role('button', name='Record confirmation intent').focus()
            mobile.keyboard.press('Enter')
            expect(mobile.get_by_text('Confirmation intent received; payment not yet completed.')).to_be_visible()
            inspect(mobile, 'mobile-intent-' + theme)
            check('Keyboard confirmation moves focus to result: ' + theme, mobile.evaluate("document.activeElement?.getAttribute('aria-label')") == 'Demo session result')
            desktop.get_by_role('button', name='Refresh Simulation Status').click()
            expect(desktop.get_by_text('AUTHORIZED_PENDING_SETTLEMENT', exact=True)).to_be_visible()
            status, preview = request('/payments/simulated-qr/sessions/preview', {'token': tokens[-1]}, auth['accessToken'])
            request('/payments/simulated-qr/sessions/' + preview['id'] + '/cancel', {}, auth['accessToken'])
            mobile.get_by_role('button', name='Refresh Simulation Status').click()
            expect(mobile.get_by_text('CANCELED', exact=True)).to_be_visible()
            inspect(mobile, 'mobile-canceled-' + theme)
            mobile.goto(args.base + '/demo/qr/confirm#token=' + 'z' * 43)
            expect(mobile.get_by_role('alert')).to_be_visible()
            inspect(mobile, 'mobile-unavailable-' + theme)
            mobile_context.close()
            context.close()
        browser.close()
except Exception as failure:
    report['failure'] = {'type': type(failure).__name__, 'message': str(failure)}
finally:
    report['tokenInvalidation'] = []
    if auth:
        for raw in tokens:
            entry = {'tokenSha256': hashlib.sha256(raw.encode()).hexdigest(), 'confirmedTerminal': False}
            try:
                status, view = request('/payments/simulated-qr/sessions/preview', {'token': raw}, auth['accessToken'])
                if status == 200 and view['state'] not in ['CANCELED', 'EXPIRED']:
                    status, view = request('/payments/simulated-qr/sessions/' + view['id'] + '/cancel', {}, auth['accessToken'])
                entry.update({'httpStatus': status, 'state': view.get('state'), 'confirmedTerminal': status == 200 and view.get('state') in ['CANCELED', 'EXPIRED']})
            except Exception as failure:
                entry['failureType'] = type(failure).__name__
            report['tokenInvalidation'].append(entry)
    invalidated = bool(tokens) and all(item['confirmedTerminal'] for item in report['tokenInvalidation'])
    report['screenshotTokenPolicy'] = 'All captured links verified canceled/expired before packaging.' if invalidated else 'BLOCKED: token invalidation unverified; keep screenshots private and do not package.'
    report['functionalOutcome'] = 'PASS' if 'failure' not in report and invalidated else 'FAIL'
    report['axeViolationsResult'] = 'FAIL' if any(item['result'] == 'FAIL' for item in report['accessibility']) else 'PASS'
    serialized = json.dumps(report, indent=2, ensure_ascii=True)
    for raw in tokens:
        serialized = serialized.replace(raw, '[INVALIDATED_TOKEN_SHA256:' + hashlib.sha256(raw.encode()).hexdigest() + ']')
    if auth:
        for key in ['accessToken', 'refreshToken']:
            if key in auth:
                serialized = serialized.replace(auth[key], '[REDACTED]')
    (out / 'browser-logs/qr04-round03-browser-results.json').write_text(serialized + '\n', encoding='ascii')
    print(json.dumps({'checks': len(report['checks']), 'failures': sum(x['result'] == 'FAIL' for x in report['checks']), 'axeFailures': sum(x['result'] == 'FAIL' for x in report['accessibility']), 'runtimeFailure': report.get('failure', {}).get('type')}))
if report['functionalOutcome'] != 'PASS':
    raise SystemExit(1)
if report['axeViolationsResult'] == 'FAIL':
    raise SystemExit(2)
