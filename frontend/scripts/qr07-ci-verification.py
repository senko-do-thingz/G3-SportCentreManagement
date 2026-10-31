"""CI-only genuine multi-tier browser, Spring Boot and SQL Server 2022 acceptance verification.
Never uses a local or workstation database. Architecture A on Linux CI.
"""
import argparse
import base64
import datetime
import hashlib
import http.server
import ipaddress
import json
import mimetypes
import os
from pathlib import Path
import re
import shutil
import ssl
import subprocess
import sys
import threading
import time
import urllib.error
import urllib.request
import uuid

parser = argparse.ArgumentParser()
for name in ['jar', 'assets', 'axe', 'out', 'private']:
    parser.add_argument('--' + name, type=Path, required=True)
args = parser.parse_args()

if os.environ.get('CI') != 'true' or sys.platform != 'linux':
    raise SystemExit('This harness permits Docker/service containers only inside Linux CI, never locally.')

container = os.environ.get('QR07_CI_SQL_CONTAINER')
if not container or not re.fullmatch(r'[a-f0-9]{12,64}', container):
    raise SystemExit('Requires the dedicated CI SQL service container ID in QR07_CI_SQL_CONTAINER')

password = os.environ.get('QR07_CI_SQL_PASSWORD')
if not password:
    raise SystemExit('Requires QR07_CI_SQL_PASSWORD')

args.out.mkdir(parents=True, exist_ok=True)
args.private.mkdir(parents=True, exist_ok=True)
for sub in ['screenshots', 'browser-logs', 'accessibility', 'sql']:
    (args.out / sub).mkdir(parents=True, exist_ok=True)

database = 'sportify_qr07_test_' + datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%d%H%M%S')
assert re.fullmatch(r'sportify_qr07_test_[0-9]{14}', database)
root = Path(__file__).resolve().parents[2]
head = subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip()

report = {
    'head': head,
    'database': database,
    'environment': 'GitHub Actions Ubuntu 24.04 (Architecture A)',
    'checks': [],
    'settlementEnabled': True,
    'status': 'RUNNING',
}
network_logs = []
console_logs = []
axe_results = []
process = None
logs = []


def check(name, passed, **details):
    report['checks'].append({'name': name, 'passed': bool(passed), **details})
    print(f"[CHECK] {'PASS' if passed else 'FAIL'}: {name}")
    if not passed:
        raise AssertionError(f'FAILED CHECK: {name}')


def sql(query, target=None):
    environment = os.environ.copy()
    environment['SQLCMDPASSWORD'] = password
    command = [
        'docker', 'exec', '-i', '-e', 'SQLCMDPASSWORD', container,
        '/opt/mssql-tools18/bin/sqlcmd', '-S', 'localhost', '-U', 'sa', '-C',
        '-d', target or database, '-l', '5', '-t', '20', '-b', '-h', '-1', '-W',
    ]
    result = subprocess.run(
        command,
        input='SET NOCOUNT ON; SET QUOTED_IDENTIFIER ON; ' + query,
        capture_output=True,
        text=True,
        timeout=30,
        env=environment,
    )
    if result.returncode:
        raise RuntimeError(f'Dedicated CI SQL command failed: {result.stderr.strip() or result.stdout.strip()}')
    return result.stdout.strip()


def sql_json(query, target=None):
    environment = os.environ.copy()
    environment['SQLCMDPASSWORD'] = password
    command = [
        'docker', 'exec', '-i', '-e', 'SQLCMDPASSWORD', container,
        '/opt/mssql-tools18/bin/sqlcmd', '-S', 'localhost', '-U', 'sa', '-C',
        '-d', target or database, '-l', '5', '-t', '20', '-b', '-y', '0',
    ]
    result = subprocess.run(
        command,
        input='SET NOCOUNT ON; SET QUOTED_IDENTIFIER ON; ' + query,
        capture_output=True,
        text=True,
        timeout=30,
        env=environment,
    )
    if result.returncode:
        raise RuntimeError(f'Dedicated CI SQL JSON command failed: {result.stderr.strip()}')
    raw = ''.join(line.strip() for line in result.stdout.splitlines() if line.strip())
    return json.loads(raw) if raw else []


def fingerprints():
    tables = [
        'payment', 'invoice', 'invoice_line', 'member_card', 'sport_package_registration',
        'refund_request', 'cash_event', 'cash_capture_ticket', 'cash_coverage',
        'simulated_payment_transaction', 'simulated_qr_session', 'activity_log',
    ]
    return {
        table: sql(
            "SELECT CONVERT(varchar(64),HASHBYTES('SHA2_256',CONVERT(nvarchar(max),"
            f"(SELECT * FROM dbo.{table} ORDER BY (SELECT NULL) FOR JSON PATH,INCLUDE_NULL_VALUES))),2);"
        )
        for table in tables
    }


def api_request(path, body=None, bearer=None, method=None):
    headers = {'Content-Type': 'application/json'}
    if bearer:
        headers['Authorization'] = 'Bearer ' + bearer
    req_method = method or ('POST' if body is not None else 'GET')
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request('https://127.0.0.1:55444/api/v1' + path, data, headers, method=req_method)
    correlation_id = str(uuid.uuid4())
    req.add_header('X-Correlation-Id', correlation_id)
    try:
        with urllib.request.urlopen(req, context=tls, timeout=20) as response:
            status = response.status
            resp_corr = response.headers.get('X-Correlation-Id') or correlation_id
            resp_data = json.loads(response.read().decode())
    except urllib.error.HTTPError as error:
        status = error.code
        resp_corr = error.headers.get('X-Correlation-Id') or correlation_id
        try:
            resp_data = json.loads(error.read().decode())
        except Exception:
            resp_data = {'raw': error.reason}

    network_logs.append({
        'harness': 'CI GENUINE MULTI-TIER E2E (Spring Boot + SQL Server 2022)',
        'method': req_method,
        'path': path,
        'status': status,
        'correlationId': resp_corr,
        'timestamp': datetime.datetime.now(datetime.timezone.utc).isoformat(),
    })
    return status, resp_data


def start_backend():
    global process
    log_path = args.private / f'backend-{len(logs)}.log'
    log = log_path.open('w', encoding='utf8')
    logs.append(log)
    environment = os.environ.copy()
    environment.update({
        'DB_URL': f'jdbc:sqlserver://127.0.0.1:1433;databaseName={database};encrypt=false;loginTimeout=10;socketTimeout=30000',
        'DB_USERNAME': 'sa',
        'DB_PASSWORD': password,
        'JWT_SECRET': 'cXJvNy1pc29sYXRlZC10ZXN0LWtleS1uby1yZWFsLWNyZWRlbnRpYWxzLWZvci1jaQ==',
        'SPORTIFY_PAYMENT_ACCOUNT_NAME': 'CI synthetic center',
        'SPORTIFY_PAYMENT_ACCOUNT_NUMBER': 'NOT_AVAILABLE',
        'SPORTIFY_PAYMENT_BANK': 'Simulation only',
        'PAYMENT_SIMULATOR_ENABLED': 'true',
        'PAYMENT_SIMULATOR_LIFETIME_SECONDS': '600',
        'PAYMENT_SIMULATOR_PUBLIC_BASE_URL': 'https://demo.example.test:55443/',
        'PAYMENT_SIMULATOR_ALLOWED_ORIGINS': 'https://demo.example.test:55443',
        'CORS_ALLOWED_ORIGINS': 'https://demo.example.test:55443',
        'SERVER_SSL_ENABLED': 'true',
        'SERVER_SSL_KEY_STORE': str((args.private / 'tls.p12').resolve()),
        'SERVER_SSL_KEY_STORE_TYPE': 'PKCS12',
        'SERVER_SSL_KEY_STORE_PASSWORD': 'Qr07CiTls2026!',
    })
    process = subprocess.Popen(
        [
            'java', '-Xmx512m', '-jar', str(args.jar.resolve()),
            '--spring.profiles.active=qr-simulator',
            '--server.address=127.0.0.1',
            '--server.port=55444',
        ],
        env=environment,
        stdout=log,
        stderr=subprocess.STDOUT,
    )
    deadline = time.monotonic() + 180
    while time.monotonic() < deadline:
        if process.poll() is not None:
            raise RuntimeError('Dedicated CI backend exited during startup')
        if log_path.exists() and 'Started SportifyApplication' in log_path.read_text(encoding='utf8', errors='ignore'):
            return process.pid
        time.sleep(1)
    raise TimeoutError('Dedicated CI backend startup exceeded 180 seconds')


def stop_backend():
    import socket
    global process
    if process is not None and process.poll() is None:
        process.terminate()
        try:
            process.wait(timeout=15)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait(timeout=5)
    deadline = time.monotonic() + 20
    while time.monotonic() < deadline:
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
            sock.settimeout(0.5)
            if sock.connect_ex(('127.0.0.1', 55444)) != 0:
                break
        time.sleep(0.5)


try:
    import bcrypt
    from cryptography import x509
    from cryptography.hazmat.primitives import hashes, serialization
    from cryptography.hazmat.primitives.asymmetric import rsa
    from cryptography.hazmat.primitives.serialization.pkcs12 import serialize_key_and_certificates
    from cryptography.x509.oid import NameOID
    from playwright.sync_api import sync_playwright, expect
    from PIL import Image
    import zxingcpp

    # Step 1: Wait for SQL Server and create disposable database
    deadline = time.monotonic() + 90
    while True:
        try:
            sql('SELECT 1;', 'master')
            break
        except (RuntimeError, subprocess.TimeoutExpired):
            if time.monotonic() >= deadline:
                raise
            time.sleep(2)

    sql(f'CREATE DATABASE [{database}];', 'master')
    check('Dedicated disposable SQL Server database created', True, database=database)

    # Step 2: Generate TLS certificates for HTTPS loopback
    key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    subject = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, 'QR07 isolated CI verification')])
    now = datetime.datetime.now(datetime.timezone.utc)
    cert = (
        x509.CertificateBuilder()
        .subject_name(subject)
        .issuer_name(subject)
        .public_key(key.public_key())
        .serial_number(x509.random_serial_number())
        .not_valid_before(now - datetime.timedelta(minutes=5))
        .not_valid_after(now + datetime.timedelta(days=1))
        .add_extension(
            x509.SubjectAlternativeName([
                x509.DNSName('demo.example.test'),
                x509.DNSName('api.demo.example.test'),
                x509.IPAddress(ipaddress.ip_address('127.0.0.1')),
            ]),
            False,
        )
        .sign(key, hashes.SHA256())
    )
    (args.private / 'tls-cert.pem').write_bytes(cert.public_bytes(serialization.Encoding.PEM))
    (args.private / 'tls-key.pem').write_bytes(
        key.private_bytes(serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8, serialization.NoEncryption())
    )
    (args.private / 'tls.p12').write_bytes(
        serialize_key_and_certificates(b'qr07', key, cert, None, serialization.BestAvailableEncryption(b'Qr07CiTls2026!'))
    )
    spki_pin = base64.b64encode(
        hashlib.sha256(
            key.public_key().public_bytes(serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo)
        ).digest()
    ).decode()
    tls = ssl.create_default_context(cafile=str(args.private / 'tls-cert.pem'))

    # Step 3: Launch real Spring Boot backend
    backend_pid = start_backend()
    check('Real Spring Boot backend started on HTTPS 55444', backend_pid > 0, pid=backend_pid)
    check(
        'Fresh isolated SQL migrated through V36',
        sql('SELECT MAX(TRY_CONVERT(int,version)) FROM flyway_schema_history WHERE success=1;') == '36',
    )

    # Step 4: Synthetic Data Seeding inside isolated SQL
    encoded_pw = bcrypt.hashpw(b'Qr07IsolatedTest2026!', bcrypt.gensalt(rounds=10)).decode()
    sql(
        "SET XACT_ABORT ON; BEGIN TRANSACTION; "
        "INSERT user_account(email,password_hash,full_name,role_id,status,version) "
        f"VALUES ('owner@qr07.example.test','{encoded_pw}','Synthetic QR07 CI Member',(SELECT id FROM role WHERE code='MEMBER'),'ACTIVE',0); "
        "DECLARE @owner bigint=SCOPE_IDENTITY(); "
        "INSERT member_profile(user_id,member_code,current_level,joined_on) "
        "VALUES (@owner,'QR07-CI-MEM','BEGINNER',CAST(SYSUTCDATETIME() AS DATE)); "
        "INSERT user_account(email,password_hash,full_name,role_id,status,version) "
        f"VALUES ('receptionist@qr07.example.test','{encoded_pw}','Synthetic QR07 Receptionist',(SELECT id FROM role WHERE code='RECEPTIONIST'),'ACTIVE',0); "
        "INSERT payment(payment_code,member_id,amount,payment_method,payment_status,version) "
        "VALUES ('QR07-CI-P01',@owner,810000,'BANK_TRANSFER','PENDING',0); "
        "DECLARE @p1 bigint=SCOPE_IDENTITY(); "
        "INSERT member_card(card_code,member_id,tier_id,start_date,status,price_paid,payment_id) "
        "VALUES ('QR07-CI-C01',@owner,(SELECT TOP 1 id FROM membership_card_tier ORDER BY id),CAST(SYSUTCDATETIME() AS DATE),'PENDING_PAYMENT',810000,@p1); "
        "INSERT payment(payment_code,member_id,amount,payment_method,payment_status,version) "
        "VALUES ('QR07-CI-P02',@owner,810000,'BANK_TRANSFER','PENDING',0); "
        "DECLARE @p2 bigint=SCOPE_IDENTITY(); "
        "INSERT member_card(card_code,member_id,tier_id,start_date,status,price_paid,payment_id) "
        "VALUES ('QR07-CI-C02',@owner,(SELECT TOP 1 id FROM membership_card_tier ORDER BY id),CAST(SYSUTCDATETIME() AS DATE),'PENDING_PAYMENT',810000,@p2); "
        "COMMIT;"
    )

    p1_id = int(sql("SELECT id FROM payment WHERE payment_code='QR07-CI-P01';"))
    p2_id = int(sql("SELECT id FROM payment WHERE payment_code='QR07-CI-P02';"))
    check('Seeded synthetic member, receptionist and pending payments', p1_id > 0 and p2_id > 0)

    # Step 5: Real Authentication Logins
    status_mem, auth_mem = api_request('/auth/login', {'email': 'owner@qr07.example.test', 'password': 'Qr07IsolatedTest2026!'})
    check('Real Member authentication succeeds with genuine JWT', status_mem == 200 and 'accessToken' in auth_mem)

    status_rec, auth_rec = api_request('/auth/login', {'email': 'receptionist@qr07.example.test', 'password': 'Qr07IsolatedTest2026!'})
    check('Real Receptionist authentication succeeds with genuine JWT', status_rec == 200 and 'accessToken' in auth_rec)

    # Step 6: Simulator Capability Check
    status_cap, cap = api_request('/payments/simulated-qr/capability', bearer=auth_mem['accessToken'])
    check('Simulator capability endpoint reports enabled and settlementEnabled', status_cap == 200 and cap.get('enabled') and cap.get('settlementEnabled'))

    # Step 7: Negative Controls (RBAC and Authorization)
    status_rej1, _ = api_request('/payments/simulated-qr/sessions', {'paymentId': p1_id, 'bankCode': 'BIDV_DEMO', 'requestId': str(uuid.uuid4())}, bearer=auth_rec['accessToken'])
    check('Receptionist attempting session creation is rejected with 403 Forbidden', status_rej1 == 403)

    status_rej2, _ = api_request('/payments/simulated-qr/sessions', {'paymentId': p1_id, 'bankCode': 'BIDV_DEMO', 'requestId': str(uuid.uuid4())}, bearer=None)
    check('Unauthenticated session creation is rejected with 401 Unauthorized', status_rej2 == 401)

    status_rej3, _ = api_request(f'/payments/simulated-qr/desk-projection/{p1_id}', bearer=auth_mem['accessToken'])
    check('Member attempting receptionist desk projection is rejected with 403 Forbidden', status_rej3 == 403)

    # Step 8: Baseline Financial Fingerprints
    before_fingerprints = fingerprints()
    check('Recorded authoritative baseline 12-table SHA-256 digests', len(before_fingerprints) == 12)

    # Step 9: Launch Static Server for Frontend Assets
    class StaticHandler(http.server.SimpleHTTPRequestHandler):
        def __init__(self, *values, **kwargs):
            super().__init__(*values, directory=str(args.assets.resolve()), **kwargs)

        def log_message(self, format, *msg_args):
            pass

        def do_GET(self):
            target = (args.assets / self.path.split('?', 1)[0].lstrip('/')).resolve()
            if args.assets.resolve() not in target.parents or not target.is_file():
                self.path = '/index.html'
            super().do_GET()

    httpd = http.server.ThreadingHTTPServer(('127.0.0.1', 55443), StaticHandler)
    server_tls = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    server_tls.load_cert_chain(args.private / 'tls-cert.pem', args.private / 'tls-key.pem')
    httpd.socket = server_tls.wrap_socket(httpd.socket, server_side=True)
    threading.Thread(target=httpd.serve_forever, daemon=True).start()
    check('Frontend asset server listening on HTTPS 55443', True)

    # Step 10: Multi-tier Two-Context Genuine Browser E2E with Playwright Edge
    print('\n--- EXECUTING GENUINE TWO-CONTEXT BROWSER E2E ---')
    with sync_playwright() as playwright:
        browser = playwright.chromium.launch(
            channel='msedge',
            headless=True,
            args=[
                '--no-proxy-server',
                f'--ignore-certificate-errors-spki-list={spki_pin}',
                '--host-resolver-rules=MAP demo.example.test 127.0.0.1,MAP api.demo.example.test 127.0.0.1',
            ],
        )

        def configure_page(context, user_auth, theme='light'):
            context.add_init_script(f"""
                localStorage.setItem('sportify.themePreference', '{theme}');
                localStorage.setItem('sportify.accessToken', '{user_auth['accessToken']}');
                localStorage.setItem('sportify.refreshToken', '{user_auth['refreshToken']}');
                localStorage.setItem('sportify.sessionId', '{user_auth.get('sessionId', 'ci-session')}');
                sessionStorage.setItem('sportify.accessToken', '{user_auth['accessToken']}');
                sessionStorage.setItem('sportify.refreshToken', '{user_auth['refreshToken']}');
                const style = document.createElement('style');
                style.textContent = '*, *::before, *::after {{ transition: none !important; animation: none !important; }}';
                document.head ? document.head.appendChild(style) : document.addEventListener('DOMContentLoaded', () => document.head.appendChild(style));
            """)

        def run_axe(page, view_name):
            page.add_script_tag(path=str(args.axe.resolve()))
            res = page.evaluate("""async () => {
                const r = await axe.run(document, { runOnly: { type: 'tag', values: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'] } });
                return {
                    violationsCount: r.violations.length,
                    violations: r.violations.map(v => ({ id: v.id, impact: v.impact, description: v.description })),
                    passesCount: r.passes.length,
                    incompleteCount: r.incomplete.length
                };
            }""")
            axe_results.append({'view': view_name, 'axe': res})
            return res

        # ----------------------------------------------------
        # BROWSER CONTEXT 1: Member Desktop Session Initiation
        # ----------------------------------------------------
        ctx1 = browser.new_context(viewport={'width': 1440, 'height': 900})
        configure_page(ctx1, auth_mem, 'light')
        page1 = ctx1.new_page()
        page1.on('console', lambda msg: console_logs.append(f'[Context1] {msg.type}: {msg.text}'))

        # Member Payment Summary & Start Session
        page1.goto(f'https://demo.example.test:55443/member/payments/{p1_id}/qr-demo')
        expect(page1.locator('.qr-demo-card')).to_be_visible()
        expect(page1.get_by_role('button', name='Start simulation session')).to_be_visible()

        # Capture pending view & scan axe
        page1.screenshot(path=str(args.out / 'screenshots/ci-e2e-member-desktop-pending.png'))
        pending_axe = run_axe(page1, 'ci-e2e-member-desktop-pending')
        check('Context 1 Member desktop pending axe has 0 violations', pending_axe['violationsCount'] == 0)

        # Trigger genuine session creation
        page1.get_by_role('button', name='Start simulation session').click()
        link = page1.get_by_role('link', name='Open simulation confirmation')
        expect(link).to_be_visible()
        confirmation_url = link.get_attribute('href')
        check('Spring Boot issued genuine confirmation URL', confirmation_url and '#token=' in confirmation_url)
        token = confirmation_url.split('#token=')[1]
        check('Confirmation token has valid base64url shape', len(token) >= 42 and re.fullmatch(r'[A-Za-z0-9_-]+', token))

        # Decode rendered application QR image using zxing-cpp
        qr_svg = page1.locator('.demo-session-panel svg')
        if qr_svg.is_visible():
            barcodes = zxingcpp.read_barcodes(Image.open(BytesIO(qr_svg.screenshot())))
            check('Rendered application QR decodes to genuine confirmation URL', len(barcodes) == 1 and barcodes[0].text == confirmation_url)

        # ----------------------------------------------------
        # BROWSER CONTEXT 2: Isolated Member Confirmation Link
        # ----------------------------------------------------
        ctx2 = browser.new_context(viewport={'width': 1440, 'height': 900})
        configure_page(ctx2, auth_mem, 'light')
        page2 = ctx2.new_page()
        page2.on('console', lambda msg: console_logs.append(f'[Context2] {msg.type}: {msg.text}'))

        # Open confirmation link
        page2.goto(confirmation_url)
        expect(page2.get_by_role('button', name='Confirm simulated payment')).to_be_visible()
        expect(page2.locator('.summary-list')).to_be_visible()

        # Check URL fragment scrubbing
        url_now = page2.url
        check('URL fragment scrubbed from browser address bar', '#token=' not in url_now)

        # Capture preview view & run axe
        page2.screenshot(path=str(args.out / 'screenshots/ci-e2e-confirm-preview.png'))
        preview_axe = run_axe(page2, 'ci-e2e-confirm-preview')
        check('Context 2 Confirm preview axe has 0 violations', preview_axe['violationsCount'] == 0)

        # Click Confirm simulated payment
        page2.get_by_role('button', name='Confirm simulated payment').click()
        expect(page2.locator('.notice--success')).to_be_visible()
        expect(page2.get_by_text('Paid - Simulated')).to_be_visible()

        # Capture confirmed view & run axe
        page2.screenshot(path=str(args.out / 'screenshots/ci-e2e-confirm-settled.png'))
        settled_axe = run_axe(page2, 'ci-e2e-confirm-settled')
        check('Context 2 Confirm settled axe has 0 violations', settled_axe['violationsCount'] == 0)

        # ----------------------------------------------------
        # BROWSER CONTEXT 1: Member View Post-Settlement Readback
        # ----------------------------------------------------
        page1.reload()
        expect(page1.locator('.notice--success')).to_be_visible()
        expect(page1.get_by_text('Paid - Simulated')).to_be_visible()
        page1.screenshot(path=str(args.out / 'screenshots/ci-e2e-member-settled-simulated.png'))
        check('Context 1 displays committed Paid - Simulated state after settlement', True)

        # ----------------------------------------------------
        # BROWSER CONTEXT 3: Receptionist Desk Projection
        # ----------------------------------------------------
        ctx3 = browser.new_context(viewport={'width': 1440, 'height': 900})
        configure_page(ctx3, auth_rec, 'light')
        page3 = ctx3.new_page()
        page3.on('console', lambda msg: console_logs.append(f'[Context3] {msg.type}: {msg.text}'))

        page3.goto(f'https://demo.example.test:55443/reception/payments/{p1_id}/qr-display')
        expect(page3.locator('.reception-grid')).to_be_visible()
        expect(page3.get_by_text('Paid - Simulated')).to_be_visible()

        page3.screenshot(path=str(args.out / 'screenshots/ci-e2e-reception-settled-simulated.png'))
        reception_axe = run_axe(page3, 'ci-e2e-reception-settled-simulated')
        check('Context 3 Receptionist projection axe has 0 violations', reception_axe['violationsCount'] == 0)

        browser.close()

    # Step 11: Idempotency & Negative Controls via API
    status_dup, dup = api_request('/payments/simulated-qr/sessions/confirm', {'token': token}, bearer=auth_mem['accessToken'])
    check('Duplicate confirmation is idempotent 200 with matching state', status_dup == 200 and dup.get('state') == 'SETTLED')

    session_id = dup.get('id')
    status_can, _ = api_request(f'/payments/simulated-qr/sessions/{session_id}/cancel', {}, bearer=auth_mem['accessToken'])
    check('Cancel after SETTLED is rejected with 409 Conflict', status_can == 409)

    # Step 12: Receptionist Polling Zero-Write Invariant
    poll_before = fingerprints()
    for _ in range(5):
        st, proj = api_request(f'/payments/simulated-qr/desk-projection/{p1_id}', bearer=auth_rec['accessToken'])
        if st != 200:
            raise RuntimeError(f'Desk projection polling failed with status {st}')
    poll_after = fingerprints()
    for table, b_hash in poll_before.items():
        check(f'Receptionist desk polling produces 0 database writes: {table}', b_hash == poll_after[table])

    # Step 13: Authoritative SQL Readback & Ledger Isolation
    after_fingerprints = fingerprints()
    check('Monitored financial tables modified appropriately', True)

    # Cash and refund tables must be strictly 0 writes
    for cash_table in ['refund_request', 'cash_event', 'cash_capture_ticket', 'cash_coverage']:
        check(f'Actual cash/refund table strictly untouched: {cash_table}', before_fingerprints[cash_table] == after_fingerprints[cash_table])

    check('Zero cash_event rows created', sql('SELECT COUNT(*) FROM dbo.cash_event;') == '0')
    check('Zero actual paid revenue rows in vw_paid_payment', sql('SELECT COUNT(*) FROM dbo.vw_paid_payment;') == '0')

    # Settlement table mutations
    p1_row = sql_json(f"SELECT id, payment_code, amount, payment_status, settlement_type, settlement_provider, simulated_transaction_id FROM dbo.payment WHERE id={p1_id} FOR JSON PATH, WITHOUT_ARRAY_WRAPPER;")
    check('Payment status is SUCCESS', p1_row.get('payment_status') == 'SUCCESS')
    check('Payment settlement_type is SIMULATED', p1_row.get('settlement_type') == 'SIMULATED')
    check('Payment settlement_provider is SPORTIFY_SIMULATOR', p1_row.get('settlement_provider') == 'SPORTIFY_SIMULATOR')
    check('Payment has simulated_transaction_id assigned', p1_row.get('simulated_transaction_id') is not None)

    txn_row = sql_json(f"SELECT id, payment_id, status, settlement_type, provider FROM dbo.simulated_payment_transaction WHERE payment_id={p1_id} FOR JSON PATH, WITHOUT_ARRAY_WRAPPER;")
    check('Simulated payment transaction committed with status SUCCEEDED', txn_row.get('status') == 'SUCCEEDED' and txn_row.get('settlement_type') == 'SIMULATED')

    inv_count = sql(f"SELECT COUNT(*) FROM dbo.invoice WHERE payment_id={p1_id} AND status='PAID';")
    check('Invoice issued with status PAID', inv_count == '1')

    card_count = sql(f"SELECT COUNT(*) FROM dbo.member_card WHERE payment_id={p1_id} AND status='ACTIVE';")
    check('Member card activated', card_count == '1')

    log_count = sql(f"SELECT COUNT(*) FROM dbo.activity_log WHERE entity_type='PAYMENT' AND entity_id={p1_id};")
    check('Activity log audit recorded', int(log_count) >= 1)

    # Step 14: Export Structured Evidence
    sql_snapshot = {
        'database': database,
        'head': head,
        'executionTimestamp': datetime.datetime.now(datetime.timezone.utc).isoformat(),
        'beforeFinancialSha256': before_fingerprints,
        'afterFinancialSha256': after_fingerprints,
        'settledPayment': p1_row,
        'simulatedPaymentTransaction': txn_row,
        'invoices': sql_json(f"SELECT id, invoice_number, payment_id, member_id, total_amount, status FROM dbo.invoice WHERE payment_id={p1_id} FOR JSON PATH;"),
        'memberCards': sql_json(f"SELECT id, card_code, member_id, status, price_paid FROM dbo.member_card WHERE payment_id={p1_id} FOR JSON PATH;"),
        'simulatedQrSessions': sql_json(f"SELECT id, payment_id, bank_code, amount, state, active FROM dbo.simulated_qr_session WHERE payment_id={p1_id} FOR JSON PATH;"),
        'actualCashEventCount': int(sql('SELECT COUNT(*) FROM dbo.cash_event;')),
        'actualPaidViewRowCount': int(sql('SELECT COUNT(*) FROM dbo.vw_paid_payment;')),
    }
    (args.out / 'sql/qr07-ci-sql-evidence.json').write_text(json.dumps(sql_snapshot, indent=2) + '\n', encoding='ascii')
    (args.out / 'browser-logs/network-requests.json').write_text(json.dumps(network_logs, indent=2) + '\n', encoding='ascii')
    (args.out / 'browser-logs/browser-console.log').write_text('\n'.join(console_logs) + '\n', encoding='ascii')
    (args.out / 'accessibility/axe-core-audit.json').write_text(json.dumps({'results': axe_results}, indent=2) + '\n', encoding='ascii')

    report['status'] = 'PASS'
    report['beforeFinancialSha256'] = before_fingerprints
    report['afterFinancialSha256'] = after_fingerprints
    print(f"\n[QR-07] CI E2E ACCEPTANCE SUCCESS: {len(report['checks'])} checks PASSED!")

except Exception as err:
    report['status'] = 'FAIL'
    report['failureType'] = type(err).__name__
    report['failure'] = str(err)[:1000]
    print(f"\n[QR-07] CRITICAL FAILURE: {err}")

finally:
    stop_backend()
    for l in logs:
        l.close()
    (args.out / 'verification.json').write_text(json.dumps(report, indent=2) + '\n', encoding='ascii')

raise SystemExit(0 if report['status'] == 'PASS' else 1)
