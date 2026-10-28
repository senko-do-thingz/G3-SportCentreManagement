"""UI-27/UI-28/UI-32 browser acceptance against merged Home.

Use requirements-visual-check.txt. Run from frontend against vite preview:
python scripts/payment-home-check.py --base http://127.0.0.1:5187 --out <reports-root>
API responses are deterministic fixtures, never real financial mutations.
"""
import argparse
import hashlib
import json
import subprocess
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import urlparse

from playwright.sync_api import sync_playwright, expect


def contrast(first, second):
    import re
    def luminance(value):
        channels = [float(v) / 255 for v in re.findall(r'[\d.]+', value)[:3]]
        channels = [v / 12.92 if v <= .04045 else ((v + .055) / 1.055) ** 2.4 for v in channels]
        return sum(v * weight for v, weight in zip(channels, [.2126, .7152, .0722]))
    high, low = sorted([luminance(first), luminance(second)], reverse=True)
    return (high + .05) / (low + .05)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--base', default='http://127.0.0.1:5187')
    parser.add_argument('--out', required=True)
    parser.add_argument('--phase', choices=['before', 'after'], default='after')
    parser.add_argument('--smoke', action='store_true', help='Only the mobile theme and interaction matrix')
    args = parser.parse_args()
    label = args.phase + ('-smoke' if args.smoke else '')
    out = Path(args.out)
    integration = out / 'integration-f-i/round-01'
    integration.mkdir(parents=True, exist_ok=True)
    # Reuse the repository's established synthetic payment fixtures.
    fixture_script = """const fs = require('node:fs');
const s = fs.readFileSync('scripts/generate-all-evidence.mjs', 'utf8');
const code = s.slice(s.indexOf('const MEMBER ='), s.indexOf('function countPdfPages'));
console.log(new Function(code + ';return JSON.stringify({MEMBER,RECEPTIONIST,PAYMENT_PACKAGE,PAYMENT_SINGLE,REFUND_11})')());"""
    fixtures = json.loads(subprocess.check_output(['node', '-e', fixture_script], text=True))
    sha = subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip()
    report = {'sha': sha, 'dirty': bool(subprocess.check_output(['git', 'status', '--porcelain'], text=True).strip()),
              'phase': args.phase, 'time': datetime.now(timezone.utc).isoformat(), 'screenshots': [], 'checks': [],
              'mode': 'intercepted API fixtures; no backend financial operations', 'consoleErrors': []}

    with sync_playwright() as pw:
        browser = pw.chromium.launch(channel='msedge', headless=True)
        report['browser'] = 'Microsoft Edge ' + browser.version

        def open_page(code, route, width=1440, theme='light', state='default'):
            context = browser.new_context(viewport={'width': width, 'height': 900 if width == 1440 else 1024 if width == 768 else 844},
                                          reduced_motion='reduce', permissions=['clipboard-read', 'clipboard-write'])
            role = 'RECEPTIONIST' if code == 'UI-32' else 'MEMBER' if code in ['UI-27', 'UI-28'] else 'MANAGER' if code == 'MANAGER' else 'GUEST'
            storage = {'sportify.themePreference': theme}
            if role != 'GUEST':
                storage.update({'sportify.accessToken': 'payment-browser-fixture', 'sportify.refreshToken': 'fixture-refresh',
                                'sportify.sessionId': 'payment-browser-fixture'})
            context.add_init_script(f'for (const [k,v] of Object.entries({json.dumps(storage)})) localStorage.setItem(k,v)')
            requests = []
            model = {'status': 'PENDING', 'refund': 'APPROVED', 'mode': state}
            payment = dict(fixtures['PAYMENT_SINGLE'] if state == 'single-visit' else fixtures['PAYMENT_PACKAGE'])
            payment['id'] = 53 if state == 'single-visit' else 52
            registration = {'id': 1052, 'registrationCode': 'REG-1052', 'paymentId': payment['id'], 'status': 'ACTIVE',
                            'packageName': 'Football Foundations', 'totalSessions': 1 if state == 'single-visit' else 12,
                            'remainingSessions': 6, 'startDate': '2026-10-28', 'endDate': '2026-10-28' if state == 'single-visit' else '2026-11-28', 'paidAmount': payment['amount'],
                            'trainingFormat': 'GROUP', 'packageType': 'SINGLE_VISIT' if state == 'single-visit' else 'STANDARD'}

            def api(r):
                path = urlparse(r.request.url).path.split('/api/v1')[-1]
                requests.append({'path': path, 'method': r.request.method, 'body': r.request.post_data})
                data = []
                status = 200
                if path == '/users/me': data = dict(fixtures['RECEPTIONIST' if role in ['RECEPTIONIST', 'MANAGER'] else 'MEMBER'], role=role)
                elif path.startswith('/payments/') and path.split('/')[-1] in ['52', '53']:
                    if model['mode'] == 'loading': return
                    if model['mode'] == 'error': status, data = 503, {'message': 'Synthetic payment service unavailable'}
                    else: data = dict(payment, status=model['status'])
                elif path.endswith('/method'): data = payment
                elif path == '/members': data = {'content': [dict(fixtures['MEMBER'], userId=2, memberCode='MEM-0002')], 'page': 0, 'size': 10, 'totalElements': 1, 'totalPages': 1}
                elif path.startswith('/packages/registrations/'): data = [registration]
                elif path == '/refunds/quote':
                    data = {'registrationId': 1052, 'memberId': 2, 'memberCode': 'MEM-0002', 'memberFullName': 'Demo Member',
                            'registrationCode': 'REG-1052', 'serviceName': 'Football Foundations', 'paymentCode': 'PAY-0052',
                            'paymentStatus': 'SUCCESS', 'paidAmount': 810000, 'paymentAmount': 810000,
                            'suggestedAmount': 450000, 'unusedSessions': 0 if state == 'ineligible' else 6}
                elif path.startswith('/refunds/member/'):
                    if model['mode'] == 'loading': return
                    if model['mode'] == 'error': status, data = 503, {'message': 'Synthetic refund service unavailable'}
                    else: data = [] if state == 'empty' else [dict(fixtures['REFUND_11'], status=model['refund'])]
                elif path == '/refunds' and r.request.method == 'POST':
                    model['refund'] = 'PENDING'
                    data = dict(fixtures['REFUND_11'], status='PENDING')
                elif path.endswith('/complete'):
                    model['refund'] = 'COMPLETED'
                    data = dict(fixtures['REFUND_11'], status='COMPLETED')
                elif path == '/sports': data = [{'id': 1, 'code': 'FOOTBALL', 'name': 'Football'}]
                elif path == '/sessions' or path.endswith('/payments/my'):
                    data = {'content': [], 'page': 0, 'size': 20, 'totalElements': 0, 'totalPages': 0}
                r.fulfill(status=status, json=data, content_type='application/json', headers={'Access-Control-Allow-Origin': '*'})

            context.route('**/api/v1/**', api)
            page = context.new_page()
            page.on('pageerror', lambda error: report['consoleErrors'].append(str(error)))
            page.goto(args.base + route)
            page.locator('h1').first.wait_for()
            page.evaluate('document.fonts.ready')
            if code == 'UI-27' and state not in ['loading', 'error', 'invalid-link']:
                expect(page.get_by_role('region', name='Order summary')).to_be_visible()
            elif code == 'UI-28' and state not in ['loading', 'error']:
                expect(page.get_by_role('region', name='Payment status')).to_be_visible()
            elif code == 'UI-32' and '/11?' in route and state not in ['loading', 'error', 'empty']:
                expect(page.get_by_role('heading', name='Refund Approved', exact=True)).to_be_visible()
            return context, page, model, requests, role

        def check(name, condition, detail=None):
            report['checks'].append({'name': name, 'pass': bool(condition), 'detail': detail})
            (integration / f'payment-browser-{label}.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
            if not condition: raise AssertionError(f'{name}: {detail}')

        def capture(code, page, route, theme, state, role):
            width = page.viewport_size['width']
            viewport = 'desktop' if width == 1440 else 'tablet' if width == 768 else 'effective-200pct' if width == 720 else 'mobile'
            prefix = f'{code}-f-i-{args.phase}' if code != 'HOME' else f'HOME-reference-{args.phase}'
            if args.smoke: prefix += '-smoke'
            folder = out / f'{code}-f-i/round-01/screenshots' if code != 'HOME' else integration / 'screenshots'
            folder.mkdir(parents=True, exist_ok=True)
            file = folder / f'{prefix}-{theme}-{viewport}-{width}-{state}.png'
            page.screenshot(path=str(file), full_page='modal' not in state, animations='disabled')
            actual_url = urlparse(page.url)
            actual_route = actual_url.path + ('?' + actual_url.query if actual_url.query else '')
            report['screenshots'].append({'ui': code, 'path': str(file.relative_to(out)), 'sha': sha, 'route': actual_route, 'requestedRoute': route,
                'viewport': f"{width}x{page.viewport_size['height']}", 'theme': theme, 'state': state, 'role': role,
                'browser': report['browser'], 'mode': report['mode'], 'time': datetime.now(timezone.utc).isoformat(),
                'masks': 'none; synthetic data only', 'exists': file.is_file(), 'hash': hashlib.sha256(file.read_bytes()).hexdigest()})
            (integration / f'payment-browser-{label}.json').write_text(json.dumps(report, indent=2), encoding='utf-8')

        routes = {'UI-27': '/member/payments/52', 'UI-28': '/member/payments/52/status', 'UI-32': '/reception/refunds/11?memberId=2', 'HOME': '/'}
        for code, route in routes.items():
            for width in [390] if args.smoke else [1440, 768, 390, 720]:
                for theme in ['light', 'dark']:
                    ctx, page, model, requests, role = open_page(code, route, width, theme)
                    check(f'{code} {theme} {width} no page overflow', page.evaluate('document.documentElement.scrollWidth <= innerWidth'))
                    if args.phase == 'after' and code != 'HOME':
                        css = page.locator('.payment-hero').evaluate('(e) => ({radius:getComputedStyle(e).borderRadius, background:getComputedStyle(e).backgroundImage, font:getComputedStyle(e.querySelector("h1")).fontFamily})')
                        check(f'{code} Home heading geometry', css['radius'] == '0px' and css['background'] == 'none' and 'Be Vietnam Pro' in css['font'], css)
                        check(f'{code} square cards', page.locator('.card').first.evaluate('(e)=>getComputedStyle(e).borderRadius') == '0px')
                        tones = page.locator('.payment-workspace').evaluate('(e)=>{const s=getComputedStyle(e),h=getComputedStyle(e.querySelector("h1"));return {text:s.color,bg:s.backgroundColor,heading:h.fontFamily,body:s.fontFamily}}')
                        check(f'{code} body text contrast', contrast(tones['text'], tones['bg']) >= 4.5, tones)
                        for button in page.locator('.btn--primary').all():
                            colors = button.evaluate('(e)=>({text:getComputedStyle(e).color,bg:getComputedStyle(e).backgroundColor})')
                            check(f'{code} primary contrast', contrast(colors['text'], colors['bg']) >= 4.5, colors)
                    capture(code, page, route, theme, 'main' if width != 720 else 'reflow-equivalent-not-native-zoom', role)
                    if code == 'UI-32':
                        page.get_by_label('I have returned the approved amount').check()
                        trigger = page.get_by_role('button', name='Record completed refund', exact=True)
                        trigger.click()
                        dialog = page.get_by_role('dialog')
                        expect(dialog).to_contain_text('450.000 VND')
                        check('Payout dialog fits viewport vertically', dialog.evaluate('(e)=>e.getBoundingClientRect().top >= 0 && e.getBoundingClientRect().bottom <= innerHeight'))
                        capture(code, page, route, theme, 'modal-open', role)
                        for _ in range(5):
                            page.keyboard.press('Tab')
                            check('Modal keyboard focus remains inside', dialog.evaluate('(e)=>e.contains(document.activeElement)'))
                        page.keyboard.press('Shift+Tab')
                        check('Modal reverse focus remains inside', dialog.evaluate('(e)=>e.contains(document.activeElement)'))
                        page.keyboard.press('Escape')
                        expect(dialog).not_to_be_visible()
                        check('Escape restores trigger focus', trigger.evaluate('(e)=>e===document.activeElement'))
                        check('Escape sends no completion', not any(r['method'] == 'PUT' for r in requests))
                        if args.phase == 'after' and width == 1440 and theme == 'light':
                            trigger.click()
                            dialog.get_by_role('button', name='Cancel', exact=True).click()
                            check('Cancel restores trigger', trigger.evaluate('(e)=>e===document.activeElement'))
                            trigger.click()
                            dialog.get_by_role('button', name='Yes, record').click()
                            expect(page.get_by_role('heading', name='Refund Completed', exact=True)).to_be_visible()
                            check('One completion PUT', sum(r['method'] == 'PUT' and r['path'].endswith('/complete') for r in requests) == 1)
                            capture(code, page, route, theme, 'completed', role)
                    if code == 'UI-28' and args.phase == 'after':
                        copy = page.get_by_role('button', name='Copy transfer content', exact=True)
                        text = copy.locator('..').locator('.copy-value__text').inner_text()
                        copy.click()
                        check('Clipboard equals displayed transfer content', page.evaluate('navigator.clipboard.readText()') == text, text)
                        capture(code, page, route, theme, 'copy-feedback', role)
                        model['status'] = 'SUCCESS'
                        page.get_by_role('button', name='Refresh status').click()
                        expect(page.get_by_role('heading', name='Payment confirmed', exact=True)).to_be_visible()
                        capture(code, page, route, theme, 'confirmed-no-invoice', role)
                        if width == 1440 and theme == 'light':
                            count = len(requests)
                            page.wait_for_timeout(15500)
                            check('Polling stops after confirmed status for a full interval', len(requests) == count)
                    if code == 'UI-27' and args.phase == 'after':
                        button = page.get_by_role('button', name='Continue to payment')
                        button.focus()
                        page.keyboard.press('Tab')
                        page.keyboard.press('Shift+Tab')
                        check('Keyboard reaches Continue', button.evaluate('(e)=>e===document.activeElement'))
                        capture(code, page, route, theme, 'keyboard-focus', role)
                        button.click()
                        expect(page).to_have_url(args.base + '/member/payments/52/status')
                    ctx.unroute_all(behavior='ignoreErrors'); ctx.close()

        if args.phase == 'after' and not args.smoke:
            for code in ['UI-27', 'UI-28', 'UI-32']:
                route = routes[code]
                for state in ['loading', 'error', 'empty'] if code == 'UI-32' else ['loading', 'error']:
                    ctx, page, model, requests, role = open_page(code, route, 390, 'dark', state)
                    if state == 'error': expect(page.get_by_text('We could not load', exact=False).first).to_be_visible()
                    capture(code, page, route, 'dark', state, role)
                    ctx.unroute_all(behavior='ignoreErrors'); ctx.close()
            for state in ['single-visit', 'default']:
                route = '/member/payments/53' if state == 'single-visit' else '/member/payments/invalid'
                ctx, page, model, requests, role = open_page('UI-27', route, 390, 'light', state if state == 'single-visit' else 'invalid-link')
                expect(page.get_by_role('heading', name='Single Visit Payment Summary' if state == 'single-visit' else 'Payment not found', exact=True)).to_be_visible()
                capture('UI-27', page, route, 'light', state if state == 'single-visit' else 'invalid-link', role)
                ctx.unroute_all(behavior='ignoreErrors'); ctx.close()
            for state in ['default', 'ineligible', 'empty']:
                route = '/reception/refunds?memberId=2&registrationId=1052' if state != 'empty' else '/reception/refunds?memberId=2'
                ctx, page, model, requests, role = open_page('UI-32', route, 390, 'light', state)
                if state == 'default':
                    page.get_by_role('button', name='Submit for approval').click()
                    expect(page.get_by_text('Reason is required', exact=True)).to_be_visible()
                    capture('UI-32', page, route, 'light', 'validation', role)
                    page.get_by_label('Reason', exact=False).fill('Synthetic refund reason')
                    page.get_by_role('button', name='Submit for approval').click()
                    expect(page.get_by_role('heading', name='Refund Request Submitted', exact=True)).to_be_visible()
                    check('One refund POST', sum(r['method'] == 'POST' and r['path'] == '/refunds' for r in requests) == 1)
                elif state == 'ineligible':
                    expect(page.get_by_role('button', name='Submit for approval')).to_be_disabled()
                else:
                    expect(page.get_by_role('heading', name='No approved refund is waiting', exact=True)).to_be_visible()
                capture('UI-32', page, route, 'light', state, role)
                ctx.unroute_all(behavior='ignoreErrors'); ctx.close()
            for width in [1440, 768, 390]:
                for theme in ['light', 'dark']:
                    for route, state in [('/reception/refunds?memberId=2', 'populated-queues'),
                                         ('/reception/refunds?memberId=2&registrationId=1052', 'refund-form'),
                                         ('/reception/refunds', 'member-search')]:
                        ctx, page, model, requests, role = open_page('UI-32', route, width, theme)
                        if state == 'populated-queues':
                            expect(page.get_by_role('link', name='Open refund REF-0011')).to_be_visible()
                            check('Refund table remains scrollable locally', page.locator('.table-scroll').first.evaluate('(e)=>getComputedStyle(e).overflowX') == 'auto')
                            check('Refund action stays visible horizontally', page.get_by_role('link', name='Open refund REF-0011').evaluate('(e)=>{const a=e.getBoundingClientRect(),s=e.closest(".table-scroll").getBoundingClientRect();return a.left >= s.left && a.right <= s.right}'))
                            check('Refund action label stays on one line', page.get_by_role('link', name='Open refund REF-0011').evaluate('(e)=>getComputedStyle(e).whiteSpace') == 'nowrap')
                            scroll = page.get_by_role('region', name='Approved refunds waiting to be refunded - scroll to view all columns')
                            scroll.focus()
                            if scroll.evaluate('(e)=>e.scrollWidth > e.clientWidth'):
                                page.keyboard.press('ArrowRight')
                                page.wait_for_timeout(200)
                                check('Refund table scrolls with the keyboard', scroll.evaluate('(e)=>e.scrollLeft') > 0)
                                scroll.evaluate('(e)=>e.scrollLeft=0')
                        elif state == 'refund-form':
                            expect(page.get_by_label('Refund amount')).to_have_value('450000')
                        else:
                            page.get_by_role('button', name='Find member', exact=True).click()
                            expect(page.get_by_text('Enter a name, phone, email or member code')).to_be_visible()
                            page.get_by_label('Member', exact=True).fill('Demo')
                            page.get_by_role('button', name='Find member', exact=True).click()
                            expect(page.get_by_role('button', name='Select Taylor Nguyen MEM-0002')).to_be_visible()
                            if width == 390:
                                page.get_by_role('button', name='Menu', exact=True).click()
                                expect(page.get_by_role('link', name='Refund requests', exact=True)).to_be_visible()
                        check('Refund page fits viewport', page.evaluate('document.documentElement.scrollWidth <= innerWidth'))
                        for empty in page.locator('.table__state-cell').all():
                            check('Empty refund queue fits its scroll region', empty.evaluate('(e)=>e.getBoundingClientRect().width <= e.closest(".table-scroll").clientWidth + 1'))
                        capture('UI-32', page, route, theme, state, role)
                        ctx.unroute_all(behavior='ignoreErrors'); ctx.close()
            for status in ['PENDING', 'REJECTED']:
                route = routes['UI-32']
                ctx, page, model, requests, role = open_page('UI-32', route, 390, 'dark')
                model['refund'] = status
                page.reload()
                expect(page.get_by_role('heading', name='Refund Request Submitted' if status == 'PENDING' else 'Refund Rejected', exact=True)).to_be_visible()
                capture('UI-32', page, route, 'dark', status.lower(), role)
                ctx.unroute_all(behavior='ignoreErrors'); ctx.close()
            # Long financial references and member text must wrap without hiding actions.
            route = routes['UI-32']
            ctx, page, model, requests, role = open_page('UI-32', route, 390, 'dark')
            fixtures['REFUND_11']['memberFullName'] = 'Synthetic long member name ' * 7
            fixtures['REFUND_11']['refundCode'] = 'REF-' + '1234567890' * 8
            page.reload()
            page.get_by_label('I have returned the approved amount').check()
            page.get_by_role('button', name='Record completed refund', exact=True).click()
            dialog = page.get_by_role('dialog')
            check('Long modal content stays within screen', dialog.evaluate('(e)=>e.scrollWidth <= e.clientWidth'))
            capture('UI-32', page, route, 'dark', 'long-text-modal', role)
            ctx.unroute_all(behavior='ignoreErrors'); ctx.close()
            for theme in ['light', 'dark']:
                for route, code in [('/login', 'HOME'), ('/register', 'HOME'), ('/manager', 'MANAGER')]:
                    ctx, page, model, requests, role = open_page(code, route, 390, theme)
                    check(f'{route} has no payment presentation scope', page.locator('.payment-workspace').count() == 0)
                    check(f'{route} responsive integration', page.evaluate('document.documentElement.scrollWidth <= innerWidth'))
                    if code == 'MANAGER':
                        page.get_by_role('button', name='Menu', exact=True).click()
                        expect(page.locator('#staff-menu')).to_be_visible()
                    capture('HOME', page, route, theme, route.strip('/') + '-integration', role)
                    ctx.unroute_all(behavior='ignoreErrors'); ctx.close()
            ctx, page, model, requests, role = open_page('HOME', '/', 1440, 'light')
            page.locator('.home-select__button').first.click()
            expect(page.locator('.home-select__list').first).to_be_visible()
            capture('HOME', page, '/', 'light', 'dropdown-open', role)
            page.keyboard.press('Escape')
            ctx.unroute_all(behavior='ignoreErrors'); ctx.close()
        check('No uncaught browser errors', not report['consoleErrors'], report['consoleErrors'])
        browser.close()
    log = integration / f'payment-browser-{label}.json'
    log.write_text(json.dumps(report, indent=2), encoding='utf-8')
    print(json.dumps({'checks': len(report['checks']), 'screenshots': len(report['screenshots']), 'consoleErrors': report['consoleErrors'], 'log': str(log)}))


if __name__ == '__main__':
    main()
