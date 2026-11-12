"""DB-UNIFY acceptance derived from the merged QR local harness; disposable SQL only.

Uses explicit environment credentials without logging them. Creates and mutates only its new owned fixture.
Only processes started by this script are stopped. Public evidence contains no authentication tokens.
"""
import argparse
import hashlib
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
JAVA = str(Path(os.environ['JAVA_HOME']) / 'bin' / ('java.exe' if os.name == 'nt' else 'java'))
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--database', required=True)
parser.add_argument('--out', type=Path, required=True)
parser.add_argument('--create-only', action='store_true')
parser.add_argument('--jar', type=Path)
parser.add_argument('--flow4', action='store_true', help='Also exercise the existing Flow 4 cross-role demo on this new fixture')
parser.add_argument('--flow4-only', action='store_true', help='Focused Flow 4 diagnostic; does not replace full QR regression')
parser.add_argument('--flow4-prepare-only', action='store_true', help='Prepare a new untouched training fixture for the committee; no browser training writes')
parser.add_argument('--release11', action='store_true', help='Run desktop Release 1.1 acceptance on a newly created owned fixture')
args = parser.parse_args()
if args.flow4_only or args.flow4_prepare_only:
    args.flow4 = True
if not re.fullmatch(r'sportify_unify_test_[0-9]{14}(?:_[a-z0-9]+)?', args.database):
    parser.error('Only a timestamped sportify_unify_test database is permitted')
args.out.mkdir(parents=True, exist_ok=True)
settings = {key: os.environ[key] for key in ('DB_USERNAME', 'DB_PASSWORD', 'JWT_SECRET') if key in os.environ}
if 'DB_PASSWORD' not in settings or 'JWT_SECRET' not in settings:
    parser.error('Explicit DB_PASSWORD and JWT_SECRET environment variables are required')
environment = os.environ.copy()
environment.update(settings)
environment['SQLCMDPASSWORD'] = settings['DB_PASSWORD']


def sql(query, database=None):
    result = subprocess.run(['sqlcmd', '-S', '127.0.0.1,1433', '-U', settings.get('DB_USERNAME', 'sa'),
                             '-C', '-I', '-l', '5', '-t', '30', '-b', '-h', '-1', '-y', '8000',
                             '-d', database or args.database, '-Q', 'SET NOCOUNT ON; ' + query],
                            env=environment, capture_output=True, text=True, timeout=40)
    if result.returncode:
        raise RuntimeError('Loopback test SQL failed: ' + (result.stdout + result.stderr)[:1000])
    return result.stdout.strip()


if not args.create_only:
    import socket
    for port in (18080, 15173):
        with socket.socket() as probe:
            try:
                probe.bind(('127.0.0.1', port))
            except OSError:
                parser.error(f'Required isolated loopback port {port} is occupied; existing processes are preserved')
# Always CREATE a new fixture. Name reuse fails before any migration or row mutation.
sql(f'CREATE DATABASE [{args.database}];', 'master')
(args.out / 'database-name.txt').write_text(args.database + '\n', encoding='ascii')
if args.create_only:
    print('Created fresh isolated database ' + args.database)
    raise SystemExit(0)
if not args.jar or not args.jar.is_file():
    parser.error('--jar must name the verified executable backend jar')
report = {'database': args.database, 'checks': [], 'transport': 'Loopback HTTP, real SQL Server and Edge; no Docker',
          'sourceHead': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
          'trackedWorktreeDirty': bool(subprocess.check_output(['git', 'status', '--porcelain', '--untracked-files=no'], cwd=ROOT, text=True).strip()),
          'jarSha256': hashlib.sha256(args.jar.read_bytes()).hexdigest(),
          'startedAtUtc': datetime.datetime.now(datetime.timezone.utc).isoformat()}
backend = None
frontend = None
frontend_log = None
log = None


def check(name, condition, **evidence):
    report['checks'].append({'name': name, 'status': 'PASS' if condition else 'FAIL', **evidence})
    if not condition:
        raise AssertionError(name)


def request(path, body=None, token=None, method=None):
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    req = urllib.request.Request('http://127.0.0.1:18080/api/v1' + path,
                                 data=None if body is None else json.dumps(body).encode(), headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=30) as response:
            return response.status, json.load(response)
    except urllib.error.HTTPError as error:
        return error.code, None


try:
    environment.update({'DB_URL': f'jdbc:sqlserver://127.0.0.1:1433;databaseName={args.database};encrypt=false;trustServerCertificate=true',
                        'PAYMENT_LOCAL_DEMO_ENABLED': 'false', 'PAYMENT_UNIFIED_DEMO_ENABLED': 'true', 'PAYMENT_UNIFIED_DEMO_TEST_ENABLED': 'true', 'PAYMENT_UNIFIED_DEMO_MEMBER_IDS': '1', 'SERVER_ADDRESS': '127.0.0.1',
                        'CORS_ALLOWED_ORIGINS': 'http://127.0.0.1:15173',
                        'SPORTIFY_PAYMENT_ACCOUNT_NAME': 'Demo center',
                        'SPORTIFY_PAYMENT_ACCOUNT_NUMBER': 'NOT_AVAILABLE', 'SPORTIFY_PAYMENT_BANK': 'Simulation only'})
    log = (args.out / 'browser-backend.log').open('w', encoding='utf8')
    backend = subprocess.Popen([JAVA, '-Xmx512m', '-jar', str(args.jar.resolve()),
                                '--spring.profiles.active=dev,demo,unified-demo,unified-demo-test', '--server.port=18080', '--spring.datasource.url=' + environment['DB_URL']],
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
    backend.terminate()
    backend.wait(timeout=30)
    # Restrict synthetic identities on this freshly seeded, owned fixture before enabling simulation.
    sql("UPDATE user_account SET email=REPLACE(email,'@example.com','@unified-demo.invalid') WHERE email IN ('an.gold@example.com','mai.standard@example.com');")
    sql("UPDATE user_account SET password_hash=(SELECT password_hash FROM user_account WHERE email='an.gold@unified-demo.invalid') WHERE email='manager@sportify.com';")
    member_ids = sql("SELECT STRING_AGG(CONVERT(varchar(20),id),',') FROM user_account WHERE email LIKE '%@unified-demo.invalid';")
    environment.update({'PAYMENT_UNIFIED_DEMO_ENABLED': 'true', 'PAYMENT_UNIFIED_DEMO_TEST_ENABLED': 'true',
                        'PAYMENT_UNIFIED_DEMO_MEMBER_IDS': member_ids})
    def start_unified():
        process = subprocess.Popen([JAVA, '-Xmx512m', '-jar', str(args.jar.resolve()),
                                    '--spring.profiles.active=dev,demo,unified-demo,unified-demo-test', '--server.port=18080', '--spring.datasource.url=' + environment['DB_URL']],
                                   cwd=ROOT / 'backend', env=environment, stdout=log, stderr=subprocess.STDOUT)
        for _ in range(180):
            if process.poll() is not None:
                raise RuntimeError('Unified backend exited during startup')
            try:
                if request('/auth/login', {'email': 'invalid@example.test', 'password': 'invalid'})[0] == 401:
                    return process
            except (OSError, TimeoutError):
                pass
            time.sleep(1)
        process.terminate()
        raise TimeoutError('Unified backend did not become ready')
    backend = start_unified()
    check('Fresh schema includes release V56', sql('SELECT MAX(TRY_CONVERT(int,version)) FROM flyway_schema_history WHERE success=1;') == '56')
    check('All five additive release migrations applied', sql('SELECT COUNT(*) FROM flyway_schema_history WHERE TRY_CONVERT(int,version) BETWEEN 42 AND 46 AND success=1;') == '5')
    status, auth = request('/auth/login', {'email': 'an.gold@unified-demo.invalid', 'password': 'SportifyDemo2026!'})
    check('Demo Member authenticates', status == 200)
    token = auth['accessToken']
    status, registration = request('/packages/registrations', {'packageId': 1, 'paymentMethod': 'BANK_TRANSFER'}, token)
    check('Official registration creates pending payment', status == 201)
    payment_id = registration['paymentId']
    before = json.loads(sql('SELECT (SELECT COUNT(*) FROM cash_event) cashEvents, '
                            '(SELECT COUNT(*) FROM vw_paid_payment) actualPaid FOR JSON PATH, WITHOUT_ARRAY_WRAPPER'))
    if args.flow4_prepare_only:
        from flow4plus_demo_workflow import exercise_flow4
        report['mode'] = 'PREPARATION_ONLY_NOT_BROWSER_ACCEPTANCE'
        report['runtime'] = {'profiles': 'dev,demo,unified-demo,unified-demo-test', 'syntheticMemberIds': member_ids,
                             'apiOrigin': 'http://127.0.0.1:18080', 'frontendOrigin': 'http://127.0.0.1:15173'}
        report['flow4'] = exercise_flow4(sql, request, args.out, report['sourceHead'], prepare_only=True)
        report['status'] = 'PREPARED_NOT_EXECUTED'
        raise SystemExit(0)
    frontend_environment = os.environ.copy()
    frontend_environment.update({'VITE_QR_PAYMENT_DEMO': 'false' if args.release11 else 'true', 'VITE_API_BASE_URL': 'http://127.0.0.1:18080/api/v1'})
    frontend_log = (args.out / 'browser-frontend.log').open('w', encoding='utf8')
    frontend = subprocess.Popen(['node', str(ROOT / 'frontend/node_modules/vite/bin/vite.js'),
                                 '--host', '127.0.0.1', '--port', '15173', '--strictPort'],
                                cwd=ROOT / 'frontend', env=frontend_environment,
                                stdout=frontend_log, stderr=subprocess.STDOUT)
    for _ in range(60):
        if frontend.poll() is not None:
            raise RuntimeError('Test frontend exited; inspect browser-frontend.log')
        try:
            with urllib.request.urlopen('http://127.0.0.1:15173', timeout=2) as response:
                if response.status == 200:
                    break
        except OSError:
            pass
        time.sleep(1)
    else:
        raise TimeoutError('Loopback frontend did not become ready')
    if args.release11:
        from flow4plus_demo_workflow import exercise_flow4
        from r11_release_workflow import exercise_release11, verify_restart
        report['mode'] = 'RELEASE_1_1_DESKTOP_ONLY'
        exercise_flow4(sql, request, args.out, report['sourceHead'], prepare_only=True)
        report['release11'] = exercise_release11(sql, request, args.out, report['sourceHead'])
        backend.terminate()
        backend.wait(timeout=30)
        backend = start_unified()
        report['restart'] = verify_restart(sql, request, args.out)
        report['status'] = 'PASS'
        raise SystemExit(0)
    if args.flow4_only:
        from flow4plus_demo_workflow import exercise_flow4, verify_restart
        report['mode'] = 'FLOW4_ONLY_QR_REGRESSION_NOT_RUN'
        report['flow4'] = exercise_flow4(sql, request, args.out, report['sourceHead'])
        backend.terminate()
        backend.wait(timeout=30)
        backend = start_unified()
        report['flow4'] = verify_restart(sql, request, args.out)
        report['status'] = 'PASS'
        raise SystemExit(0)
    from playwright.sync_api import sync_playwright, expect
    from PIL import Image
    import zxingcpp
    with sync_playwright() as playwright:
        browser = playwright.chromium.launch(channel='msedge', headless=True)
        report['browserVersion'] = browser.version
        context = browser.new_context(viewport={'width': 1440, 'height': 1000})
        page = context.new_page()
        page.set_default_timeout(60000)
        page.set_default_navigation_timeout(60000)
        errors = []
        page.on('pageerror', lambda error: errors.append(str(error)))
        # API authentication only sets the same session keys as a normal login. Every screen read and confirmation uses real HTTP.
        page.goto('http://127.0.0.1:15173/login', wait_until='domcontentloaded')
        page.locator('input[name=email]').fill('an.gold@unified-demo.invalid')
        page.locator('input[name=password]').fill('SportifyDemo2026!')
        page.get_by_role('button', name='Log in', exact=True).click()
        page.wait_for_url('**/member', timeout=30000)
        check('Member signs in through real browser form', page.url.endswith('/member'))
        page.goto(f'http://127.0.0.1:15173/member/payments/{payment_id}/qr-demo', wait_until='domcontentloaded')
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
        for width, theme in [(1440, 'light'), (768, 'light'), (390, 'light'), (390, 'dark'), (768, 'dark'), (1440, 'dark')]:
            page.set_viewport_size({'width': width, 'height': 1000})
            page.emulate_media(color_scheme=theme)
            page.evaluate('(theme) => document.documentElement.setAttribute("data-theme", theme)', theme)
            check(f'Layout has no horizontal overflow at {width}px {theme}', page.evaluate('document.documentElement.scrollWidth <= innerWidth'))
            page.evaluate('async () => { await document.fonts.ready; await Promise.all(document.getAnimations().filter(a => a.effect?.getTiming().iterations !== Infinity).map(a => a.finished.catch(() => {}))); }')
            themed_qr = args.out / f'qr-{width}-{theme}.png'
            card.locator('svg[data-testid="demo-qr-svg"]').screenshot(path=str(themed_qr), animations='disabled')
            themed_decoded = zxingcpp.read_barcode(Image.open(themed_qr))
            check(f'Non-payable QR remains decodable at {width}px {theme}', themed_decoded is not None and themed_decoded.text == expected_payload)
            page.screenshot(path=str(args.out / f'pending-{width}-{theme}.png'), full_page=True, animations='disabled')
        # A network failure must not display success or mutate the payment.
        page.route(f'**/api/v1/payments/{payment_id}/demo-confirm', lambda route: route.abort())
        button.click()
        expect(card.get_by_role('alert')).not_to_be_empty(timeout=10000)
        check('Network failure preserves pending backend state', request(f'/payments/{payment_id}', token=token)[1]['status'] == 'PENDING')
        page.unroute(f'**/api/v1/payments/{payment_id}/demo-confirm')
        expect(button).to_be_disabled()
        page.reload(wait_until='domcontentloaded')
        expect(button).to_be_enabled(timeout=30000)
        def lose_confirmation_response(route):
            route.fetch()
            route.abort()
        page.route(f'**/api/v1/payments/{payment_id}/demo-confirm', lose_confirmation_response)
        button.focus()
        button.press('Enter')
        expect(card.get_by_role('alert')).not_to_be_empty(timeout=30000)
        check('Lost response occurs after durable server settlement', request(f'/payments/{payment_id}', token=token)[1]['status'] == 'SUCCESS')
        page.unroute(f'**/api/v1/payments/{payment_id}/demo-confirm')
        page.reload(wait_until='domcontentloaded')
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
        for role, email, path in [
            ('manager', 'manager@sportify.com', '/manager/schedule'),
            ('coach', 'quan.coach@example.com', '/coach'),
            ('reception', 'lan.reception@example.com', '/reception/training/attendance'),
            ('member', 'an.gold@unified-demo.invalid', '/member/training/attendance')
        ]:
            role_context = browser.new_context(viewport={'width': 1440, 'height': 1000})
            role_page = role_context.new_page()
            role_page.set_default_timeout(60000)
            role_page.goto('http://127.0.0.1:15173/login', wait_until='domcontentloaded')
            role_page.locator('input[name=email]').fill(email)
            role_page.locator('input[name=password]').fill('SportifyDemo2026!')
            role_page.get_by_role('button', name='Log in', exact=True).click()
            role_page.wait_for_url(re.compile(r'/' + role + r'(?:/|$)') if role != 'reception' else re.compile(r'/reception(?:/|$)'))
            role_page.goto('http://127.0.0.1:15173' + path, wait_until='networkidle')
            expect(role_page.locator('main h1').first).to_be_visible()
            check(role + ' real browser authenticated role screen', 'Page not found' not in role_page.locator('main').inner_text())
            if role == 'manager':
                session_before = json.loads(sql("SELECT TOP(1) id,CONVERT(varchar(10),session_date,23) date,session_code,schedule_revision,booked_count FROM class_session WHERE class_id IS NOT NULL AND session_date>CAST(GETDATE() AS DATE) AND status='PUBLISHED' ORDER BY booked_count DESC,session_date,id FOR JSON PATH,WITHOUT_ARRAY_WRAPPER"))
                old_date = datetime.date.fromisoformat(session_before['date'])
                week = old_date - datetime.timedelta(days=old_date.weekday())
                new_date = old_date + datetime.timedelta(days=1)
                preserved_query = "SELECT (SELECT COALESCE(CHECKSUM_AGG(BINARY_CHECKSUM(*)),0) FROM booking) bookings,(SELECT COALESCE(CHECKSUM_AGG(BINARY_CHECKSUM(*)),0) FROM sport_package_registration) registrations FOR JSON PATH,WITHOUT_ARRAY_WRAPPER"
                preserved_before = sql(preserved_query)
                role_page.goto(f'http://127.0.0.1:15173/manager/schedule?week={week.isoformat()}', wait_until='networkidle')
                role_page.locator('article.f2-session').filter(has_text=session_before['session_code']).get_by_role('button', name='Edit', exact=True).click()
                dialog = role_page.get_by_role('dialog', name='Edit session')
                dialog.locator('input[name=date]').fill(new_date.isoformat())
                dialog.locator('input[name=rescheduleReason]').fill('Disposable DB-UNIFY browser regression')
                if dialog.locator('select[name=trainingType]').input_value() == 'COACH_LED':
                    expect(dialog.locator('select[name=coachId]')).to_be_enabled(timeout=60000)
                    expect(dialog.locator('select[name=coachId]')).not_to_have_value('')
                with role_page.expect_response(lambda response: response.request.method == 'PUT' and response.url.endswith('/manager/sessions/' + str(session_before['id']))) as changed:
                    dialog.get_by_role('button', name='Save session', exact=True).click()
                check('Manager browser reschedule succeeds through real HTTP', changed.value.status == 200, httpStatus=changed.value.status, response=changed.value.json())
                saved = changed.value.json()
                expect(dialog).not_to_be_visible()
                check('Manager date change preserves numeric key and changes persisted public ID', saved['id'] == session_before['id'] and saved['date'] == new_date.isoformat() and saved['sessionId'] != session_before['session_code'] and saved['scheduleRevision'] == session_before['schedule_revision'] + 1)
                check('Manager date change preserves bookings and package credits', sql(preserved_query) == preserved_before and saved['bookedCount'] == session_before['booked_count'])
                auth_status, manager_auth = request('/auth/login', {'email': 'manager@sportify.com', 'password': 'SportifyDemo2026!'})
                check('Manager fixture authenticates for alias verification', auth_status == 200)
                alias_status, alias = request('/manager/sessions/by-code/' + session_before['session_code'], token=manager_auth['accessToken'])
                check('Retired public Session ID still resolves same numeric record', alias_status == 200 and alias['id'] == saved['id'])
                stale_body = {'date': saved['date'], 'startTime': saved['startTime'], 'endTime': saved['endTime'], 'capacity': saved['capacity'], 'facilityId': saved['facility']['id'], 'coachId': saved['coach']['id'] if saved['coach'] else None, 'expectedRevision': session_before['schedule_revision']}
                stale_status, _ = request('/manager/sessions/' + str(saved['id']), stale_body, manager_auth['accessToken'], method='PUT')
                check('Stale schedule revision receives HTTP 409', stale_status == 409)
            for theme in ('light', 'dark'):
                role_page.emulate_media(color_scheme=theme)
                role_page.evaluate('(theme) => document.documentElement.setAttribute("data-theme", theme)', theme)
                role_page.evaluate('async () => { await document.fonts.ready; await Promise.all(document.getAnimations().filter(a => a.effect?.getTiming().iterations !== Infinity).map(a => a.finished.catch(() => {}))); }')
                role_page.screenshot(path=str(args.out / f'{role}-1440-{theme}.png'), full_page=True, animations='disabled')
            role_context.close()
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
    flow4 = None
    if args.flow4:
        from flow4plus_demo_workflow import exercise_flow4
        flow4 = exercise_flow4(sql, request, args.out, report['sourceHead'])
        report['flow4'] = flow4
    status, payment_before_restart = request(f'/payments/{payment_id}', token=token)
    backend.terminate()
    backend.wait(timeout=30)
    backend = start_unified()
    status, restarted_auth = request('/auth/login', {'email': 'an.gold@unified-demo.invalid', 'password': 'SportifyDemo2026!'})
    check('Member reauthenticates after backend restart', status == 200)
    status, payment_after_restart = request(f'/payments/{payment_id}', token=restarted_auth['accessToken'])
    check('Receipt and simulated transaction persist after actual process restart', status == 200
          and payment_before_restart['invoiceId'] == payment_after_restart['invoiceId']
          and payment_before_restart['simulatedTransactionId'] == payment_after_restart['simulatedTransactionId'])
    if flow4:
        from flow4plus_demo_workflow import verify_restart
        report['flow4'] = verify_restart(sql, request, args.out)
    report['status'] = 'PASS'
except Exception as error:
    try:
        if 'page' in globals() and not page.is_closed():
            page.screenshot(path=str(args.out / 'failure.png'), full_page=True)
            (args.out / 'failure-body.txt').write_text(page.locator('body').inner_text(), encoding='utf8')
    except Exception:
        pass
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
    report['finishedAtUtc'] = datetime.datetime.now(datetime.timezone.utc).isoformat()
    (args.out / 'browser-results.json').write_text(json.dumps(report, indent=2, ensure_ascii=True) + '\n', encoding='ascii')
    print(json.dumps(report, indent=2, ensure_ascii=True))
raise SystemExit(0 if report['status'] == 'PASS' else 1)
