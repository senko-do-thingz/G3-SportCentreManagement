"""Actual Edge browser zoom through Settings in isolated temporary profiles, not CSS/pinch scaling."""
import argparse
import hashlib
import json
import subprocess
import tempfile
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import urlparse
from playwright.sync_api import sync_playwright, expect

parser = argparse.ArgumentParser()
parser.add_argument('--base', default='http://127.0.0.1:5191')
parser.add_argument('--out', required=True)
args = parser.parse_args()
out = Path(args.out)
integration = out / 'integration-f-i/round-02'
sha = subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip()
fixture_script = "const fs=require('node:fs');const s=fs.readFileSync('scripts/generate-all-evidence.mjs','utf8');const c=s.slice(s.indexOf('const MEMBER ='),s.indexOf('function countPdfPages'));console.log(new Function(c+';return JSON.stringify({MEMBER,RECEPTIONIST,PAYMENT_PACKAGE,REFUND_11})')());"
fixtures = json.loads(subprocess.check_output(['node', '-e', fixture_script], text=True))
report = {'sha': sha, 'method': 'Edge Settings settingsPrivate.setDefaultZoom(2) in temporary profile', 'cases': [], 'screenshots': []}
with sync_playwright() as pw:
    for code, path in [('HOME', '/'), ('UI-27', '/member/payments/52'), ('UI-28', '/member/payments/52/status'), ('UI-32', '/reception/refunds/11?memberId=2')]:
        for theme in ['light', 'dark']:
            with tempfile.TemporaryDirectory(prefix='native-zoom-', dir=integration, ignore_cleanup_errors=True) as profile:
                assert Path(profile).resolve().is_relative_to(integration.resolve())
                ctx = pw.chromium.launch_persistent_context(profile, channel='msedge', headless=True,
                    viewport={'width': 1440, 'height': 900}, reduced_motion='reduce')
                settings = ctx.pages[0]
                settings.goto('edge://settings/appearance')
                zoom = settings.evaluate('()=>new Promise(resolve=>chrome.settingsPrivate.setDefaultZoom(2,()=>chrome.settingsPrivate.getDefaultZoom(resolve)))')
                assert zoom == 2
                role = 'RECEPTIONIST' if code == 'UI-32' else 'MEMBER'
                seed = {'sportify.themePreference': theme}
                if code != 'HOME': seed.update({'sportify.accessToken': 'native-zoom-fixture', 'sportify.refreshToken': 'fixture-refresh', 'sportify.sessionId': 'native-zoom-fixture'})
                ctx.add_init_script(f'for (const [k,v] of Object.entries({json.dumps(seed)})) localStorage.setItem(k,v)')
                def api(route):
                    endpoint = urlparse(route.request.url).path.split('/api/v1')[-1]
                    data = []
                    if endpoint == '/users/me': data = fixtures['RECEPTIONIST' if role == 'RECEPTIONIST' else 'MEMBER']
                    elif endpoint == '/payments/52': data = fixtures['PAYMENT_PACKAGE']
                    elif endpoint.startswith('/refunds/member/'): data = [fixtures['REFUND_11']]
                    elif endpoint == '/sports': data = [{'id': 1, 'code': 'FOOTBALL', 'name': 'Football'}]
                    route.fulfill(json=data, content_type='application/json', headers={'Access-Control-Allow-Origin': '*'})
                ctx.route('**/api/v1/**', api)
                page = ctx.new_page()
                page.goto(args.base + path)
                page.locator('h1').first.wait_for()
                if code == 'UI-27': expect(page.get_by_role('region', name='Order summary')).to_be_visible()
                elif code == 'UI-28': expect(page.get_by_role('region', name='Payment status')).to_be_visible()
                elif code == 'UI-32': expect(page.get_by_role('heading', name='Refund Approved', exact=True)).to_be_visible()
                page.evaluate('document.fonts.ready')
                metrics = page.evaluate('({innerWidth,innerHeight,dpr:devicePixelRatio,visualScale:visualViewport.scale,overflow:document.documentElement.scrollWidth > innerWidth})')
                assert metrics == {'innerWidth': 720, 'innerHeight': 450, 'dpr': 2, 'visualScale': 1, 'overflow': False}, metrics
                folder = integration / 'screenshots' if code == 'HOME' else out / f'{code}-f-i/round-02/screenshots'
                prefix = 'HOME-reference-round-02' if code == 'HOME' else f'{code}-f-i-round-02-after'
                def capture(state):
                    file = folder / f'{prefix}-{theme}-desktop-1440-native-200pct-{state}.png'
                    page.screenshot(path=str(file), full_page=False, animations='disabled')
                    report['screenshots'].append({'ui': code, 'path': str(file.relative_to(out)), 'sha': sha, 'route': path,
                        'role': 'GUEST' if code == 'HOME' else role, 'theme': theme, 'state': state,
                        'viewport': metrics, 'physicalViewport': '1440x900', 'browser': ctx.browser.version,
                        'mode': 'synthetic API fixtures; native browser zoom 2', 'time': datetime.now(timezone.utc).isoformat(),
                        'hash': hashlib.sha256(file.read_bytes()).hexdigest()})
                capture('first-viewport')
                if code == 'UI-32':
                    page.get_by_label('I have returned the approved amount').check()
                    page.get_by_role('button', name='Record completed refund', exact=True).click()
                    modal = page.get_by_role('dialog')
                    expect(modal).to_be_visible()
                    assert modal.evaluate('(e)=>e.getBoundingClientRect().top>=0 && e.getBoundingClientRect().bottom<=innerHeight')
                    capture('modal')
                    page.keyboard.press('Escape')
                report['cases'].append({'ui': code, 'theme': theme, 'zoom': zoom, 'metrics': metrics, 'pass': True})
                ctx.close()
(integration / 'native-zoom-results.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
print(json.dumps({'sha': sha, 'nativeZoomCases': len(report['cases']), 'screenshots': len(report['screenshots'])}))
