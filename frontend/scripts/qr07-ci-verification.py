"""CI-only genuine multi-tier browser, Spring Boot and SQL Server 2022 acceptance verification.
Never uses a local or workstation database. Architecture A on Linux CI.
"""
import argparse
import base64
import datetime
import hashlib
import http.server
from io import BytesIO
import ipaddress
import json
import math
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
from urllib.parse import urlsplit
import uuid
from qr07_evidence import (
    TABLES, constraint_rejected, correlation_observation, localized_contrast, validate_fingerprints,
)

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
    'runId': os.environ.get('GITHUB_RUN_ID'),
    'runAttempt': os.environ.get('GITHUB_RUN_ATTEMPT'),
    'runUrl': f"https://github.com/{os.environ.get('GITHUB_REPOSITORY')}/actions/runs/{os.environ.get('GITHUB_RUN_ID')}",
    'database': database,
    'environment': 'GitHub Actions Ubuntu 24.04 (Architecture A)',
    'checks': [],
    'settlementEnabled': True,
    'status': 'RUNNING',
}
network_logs = []
console_logs = []
axe_results = []
contrast_results = []
browser_events = []
sql_evidence = {'head': head, 'database': database, 'runId': report['runId'], 'snapshots': {}}
primary_keys = {}
secrets = [password]
process = None
logs = []


def check(name, passed, **details):
    report['checks'].append({'name': name, 'passed': bool(passed), 'timestamp': timestamp(), **details})
    print(f"[CHECK] {'PASS' if passed else 'FAIL'}: {name}")
    if not passed:
        raise AssertionError(f'FAILED CHECK: {name}')


def timestamp():
    return datetime.datetime.now(datetime.timezone.utc).isoformat()


def sanitize(value):
    text = str(value)
    for secret in secrets:
        if secret:
            text = text.replace(secret, '[REDACTED]')
    text = re.sub(r'(?i)(bearer\s+)[A-Za-z0-9._-]+', r'\1[REDACTED]', text)
    text = re.sub(r'(?i)([?#&]token=)[A-Za-z0-9_-]+', r'\1[REDACTED]', text)
    return text.encode('ascii', errors='backslashreplace').decode('ascii')


def snapshot(name):
    result = fingerprints()
    sql_evidence['snapshots'][name] = {'timestamp': timestamp(), 'sha256': result}
    return result


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
    if not raw:
        raise ValueError('SQL JSON command returned no evidence')
    return json.loads(raw)


def fingerprints():
    if set(primary_keys) != set(TABLES):
        raise ValueError('Primary key discovery incomplete')
    values = {
        table: sql_json(
            "SELECT CONVERT(varchar(64),HASHBYTES('SHA2_256',CONVERT(nvarchar(max),COALESCE("
            f"(SELECT * FROM dbo.[{table}] ORDER BY {','.join('[' + c + ']' for c in primary_keys[table])} "
            "FOR JSON PATH,INCLUDE_NULL_VALUES),N'[]'))),2) AS digest FOR JSON PATH,WITHOUT_ARRAY_WRAPPER;"
        ).get('digest')
        for table in TABLES
    }
    sql_evidence['lastFingerprintOutput'] = values
    return validate_fingerprints(values)


def api_request(path, body=None, bearer=None, method=None, response_format='json'):
    headers = {'Content-Type': 'application/json'}
    if bearer:
        headers['Authorization'] = 'Bearer ' + bearer
    req_method = method or ('POST' if body is not None else 'GET')
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request('https://127.0.0.1:55444/api/v1' + path, data, headers, method=req_method)
    client_corr = str(uuid.uuid4())
    req.add_header('X-Correlation-Id', client_corr)
    sent_corr = req.get_header('X-correlation-id')
    if sent_corr != client_corr:
        raise ValueError('Correlation header missing from actual outbound request')
    server_corr = None
    try:
        with urllib.request.urlopen(req, context=tls, timeout=20) as response:
            status = response.status
            server_corr = response.headers.get('X-Correlation-Id')
            raw_response = response.read().decode('utf-8-sig')
            resp_data = json.loads(raw_response) if response_format == 'json' else raw_response
    except urllib.error.HTTPError as error:
        status = error.code
        server_corr = error.headers.get('X-Correlation-Id')
        try:
            resp_data = json.loads(error.read().decode())
        except Exception:
            resp_data = {'raw': error.reason}

    network_logs.append({
        'harness': 'CI GENUINE MULTI-TIER E2E (Spring Boot + SQL Server 2022)',
        'method': req_method,
        'path': path,
        'status': status,
        'clientCorrelationId': client_corr,
        'serverCorrelationId': server_corr,
        'sentHeaderVerified': sent_corr == client_corr,
        'timestamp': datetime.datetime.now(datetime.timezone.utc).isoformat(),
    })
    return status, resp_data


def start_backend(simulator_enabled='true'):
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
        'PAYMENT_SIMULATOR_ENABLED': simulator_enabled,
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
    check('Dedicated disposable SQL Server database created', sql('SELECT DB_NAME();') == database, database=database)

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
    sql_evidence['server'] = sql_json('SELECT DB_NAME() AS databaseName, @@VERSION AS version FOR JSON PATH, WITHOUT_ARRAY_WRAPPER;')
    key_rows = sql_json("""
        SELECT t.name AS tableName, c.name AS columnName, ic.key_ordinal AS ordinal
        FROM sys.tables t JOIN sys.indexes i ON i.object_id=t.object_id AND i.is_primary_key=1
        JOIN sys.index_columns ic ON ic.object_id=i.object_id AND ic.index_id=i.index_id
        JOIN sys.columns c ON c.object_id=ic.object_id AND c.column_id=ic.column_id
        WHERE SCHEMA_NAME(t.schema_id)='dbo' ORDER BY t.name,ic.key_ordinal FOR JSON PATH;
    """)
    for row in key_rows:
        if row['tableName'] in TABLES:
            if not re.fullmatch(r'[a-z_]+', row['columnName']):
                raise ValueError('Unexpected SQL identifier')
            primary_keys.setdefault(row['tableName'], []).append(row['columnName'])
    check('All 12 fingerprint orderings use discovered unique primary keys', set(primary_keys) == set(TABLES), keys=primary_keys)
    sql_evidence['primaryKeys'] = primary_keys

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
        "INSERT user_account(email,password_hash,full_name,role_id,status,version) "
        f"VALUES ('manager@qr07.example.test','{encoded_pw}','Synthetic QR07 Manager',(SELECT id FROM role WHERE code='MANAGER'),'ACTIVE',0); "
        "INSERT user_account(email,password_hash,full_name,role_id,status,version) "
        f"VALUES ('coach@qr07.example.test','{encoded_pw}','Synthetic QR07 Coach',(SELECT id FROM role WHERE code='COACH'),'ACTIVE',0); "
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
    secrets.extend(auth.get(field) for auth in (auth_mem, auth_rec) for field in ('accessToken', 'refreshToken', 'sessionId'))
    status_mgr, auth_mgr = api_request('/auth/login', {'email': 'manager@qr07.example.test', 'password': 'Qr07IsolatedTest2026!'})
    check('Real Manager authentication succeeds for read-only finance regression', status_mgr == 200 and 'accessToken' in auth_mgr)
    secrets.extend(auth_mgr.get(field) for field in ('accessToken', 'refreshToken', 'sessionId'))
    status_coach, auth_coach = api_request('/auth/login', {'email': 'coach@qr07.example.test', 'password': 'Qr07IsolatedTest2026!'})
    check('Real Coach authentication succeeds for forbidden-confirm negative control', status_coach == 200 and 'accessToken' in auth_coach)
    secrets.extend(auth_coach.get(field) for field in ('accessToken', 'refreshToken', 'sessionId'))
    finance_month = datetime.datetime.now(datetime.timezone.utc).strftime('%Y-%m')
    status_finance, finance_before = api_request('/manager/finance/overview?month=' + finance_month, bearer=auth_mgr['accessToken'])
    check('Read-only finance baseline records actual and simulated totals', status_finance == 200 and finance_before['simulatedTransactions'] == 0)
    sql_evidence['financeBefore'] = finance_before

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
    before_fingerprints = snapshot('beforeSessionCreation')
    check('Recorded authoritative baseline 12-table SHA-256 digests', len(before_fingerprints) == 12)
    check('Repeated baseline SQL snapshot is deterministic', before_fingerprints == snapshot('repeatedBaseline'))

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
    with urllib.request.urlopen('https://127.0.0.1:55443/', context=tls, timeout=10) as response:
        check('Frontend asset server listening on HTTPS 55443', response.status == 200)

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
                    violations: r.violations,
                    passesCount: r.passes.length,
                    incompleteCount: r.incomplete.length,
                    incomplete: r.incomplete
                };
            }""")
            axe_results.append({'view': view_name, 'axe': res})
            return res

        def observe_page(page, label):
            def event(kind, request, **details):
                parsed = urlsplit(request.url)
                if parsed.path.startswith('/api/v1/'):
                    browser_events.append({'context': label, 'event': kind, 'timestamp': timestamp(),
                                           'method': request.method, 'path': parsed.path,
                                           'clientCorrelationId': request.headers.get('x-correlation-id'), **details})
            page.on('request', lambda request: event('request', request))
            page.on('requestfailed', lambda request: event('requestfailed', request, failure=request.failure))
            def response_received(response):
                details = {'status': response.status, 'serverCorrelationId': response.headers.get('x-correlation-id')}
                if '/desk-projection/' in response.url and response.status == 200:
                    data = response.json()
                    details.update({key: data.get(key) for key in (
                        'paymentStatus', 'settlementType', 'settlementProvider', 'simulatedTransactionId')})
                    check('Live desk GET response contains no secret confirmation material',
                          all(key not in json.dumps(data).lower() for key in ('token', 'confirmationurl')))
                event('response', response.request, **details)
            page.on('response', response_received)
            page.on('console', lambda msg: console_logs.append(sanitize(f'[{label}] {msg.type}: {msg.text}')))

        def measure_brand(page, label, selector='.brand__name'):
            element = page.locator(selector).first
            expect(element).to_be_visible()
            page.evaluate('() => document.fonts.ready')
            box = element.bounding_box()
            if not box:
                raise ValueError('Brand geometry unavailable')
            clip = {'x': math.floor(box['x']), 'y': math.floor(box['y']),
                    'width': math.ceil(box['x'] + box['width']) - math.floor(box['x']),
                    'height': math.ceil(box['y'] + box['height']) - math.floor(box['y'])}
            css = element.evaluate("""el => {
                const s = getComputedStyle(el);
                return {color:s.color, textFill:s.webkitTextFillColor, backgroundImage:s.backgroundImage,
                        backgroundClip:s.backgroundClip, opacity:s.opacity, fontSize:s.fontSize,
                        fontWeight:s.fontWeight, textShadow:s.textShadow, style:el.getAttribute('style')};
            }""")
            captures = {}
            try:
                for mode in ('original', 'background', 'black', 'white'):
                    element.evaluate("""(el, args) => {
                        const [mode, saved] = args;
                        saved === null ? el.removeAttribute('style') : el.setAttribute('style', saved);
                        if (mode === 'background') el.style.setProperty('visibility','hidden','important');
                        if (mode === 'black' || mode === 'white') {
                            const color = mode === 'black' ? '#000000' : '#ffffff';
                            el.style.setProperty('background','none','important');
                            el.style.setProperty('color',color,'important');
                            el.style.setProperty('-webkit-text-fill-color',color,'important');
                            el.style.setProperty('text-shadow','none','important');
                        }
                    }""", [mode, css['style']])
                    relative = f'accessibility/{label}-{mode}.png'
                    raw = page.screenshot(path=str(args.out / relative), clip=clip, animations='disabled')
                    img = Image.open(BytesIO(raw)).convert('RGB')
                    captures[mode] = {'pixels': list(img.getdata()), 'width': img.width, 'height': img.height,
                                      'path': relative, 'sha256': hashlib.sha256(raw).hexdigest()}
            finally:
                element.evaluate("(el, saved) => saved === null ? el.removeAttribute('style') : el.setAttribute('style', saved)", css['style'])
            check(f'{label}: paired contrast captures have identical dimensions',
                  len({(v['width'], v['height']) for v in captures.values()}) == 1)
            result = localized_contrast(*(captures[mode]['pixels'] for mode in ('original', 'background', 'black', 'white')),
                                        captures['original']['width'])
            result.update({'view': label, 'selector': selector, 'clip': clip, 'computedCss': css,
                           'method': 'Paired opaque glyph mask and background at identical coordinates; minimum local ratio',
                           'images': {mode: {k: v for k, v in data.items() if k != 'pixels'} for mode, data in captures.items()}})
            contrast_results.append(result)
            return result

        def keyboard_check(page, label):
            page.bring_to_front()
            page.locator('a.brand').first.focus()
            focus = []
            for key in ('Tab', 'Tab', 'Tab', 'Shift+Tab'):
                page.keyboard.press(key)
                state = page.evaluate("""() => {
                    const el=document.activeElement, s=getComputedStyle(el), r=el.getBoundingClientRect();
                    return {tag:el.tagName, text:el.textContent.slice(0,100), href:el.getAttribute('href'), outline:s.outlineStyle,
                            outlineWidth:s.outlineWidth, visible:r.width>0 && r.height>0, key:null};
                }""")
                state['key'] = key
                focus.append(state)
            check(f'{label}: keyboard forward and reverse focus is visible',
                  all(s['visible'] and s['tag'] != 'BODY' and s['outline'] != 'none' and s['outlineWidth'] != '0px' for s in focus)
                  and focus[1]['text'] == focus[3]['text'] and focus[1]['href'] == focus[3]['href'], steps=focus)
            return {'view': label, 'steps': focus}

        # ----------------------------------------------------
        # BROWSER CONTEXT 3: Receptionist Desk Projection (OPENED BEFORE SETTLEMENT)
        # ----------------------------------------------------
        ctx3 = browser.new_context(viewport={'width': 1440, 'height': 900}, ignore_https_errors=True)
        configure_page(ctx3, auth_rec, 'light')
        page3 = ctx3.new_page()
        observe_page(page3, 'Receptionist')
        rec_navigations = []
        rec_documents = []
        page3.on('framenavigated', lambda frame: rec_navigations.append({'url': frame.url.split('#')[0], 'timestamp': timestamp()}) if frame == page3.main_frame else None)
        page3.on('request', lambda request: rec_documents.append({'path': urlsplit(request.url).path, 'timestamp': timestamp()}) if request.is_navigation_request() and request.frame == page3.main_frame else None)

        # Navigate to desk projection BEFORE Member initiates or confirms
        page3.goto(f'https://demo.example.test:55443/reception/payments/{p1_id}/qr-display')
        expect(page3.locator('.reception-grid')).to_be_visible()
        expect(page3.get_by_text('Pending payment').first).to_be_visible()
        expect(page3.locator('.qr-demo-card').first).to_be_visible()

        # Capture pending view & scan axe
        page3.screenshot(path=str(args.out / 'screenshots/ci-e2e-reception-pending.png'))
        rec_pending_axe = run_axe(page3, 'ci-e2e-reception-pending')
        check('Context 3 Receptionist projection pending axe has 0 violations', rec_pending_axe['violationsCount'] == 0)
        check('Receptionist display opens in PENDING state with informational QR prior to settlement',
              page3.get_by_text('Pending payment').first.is_visible() and page3.locator('.qr-demo-card svg').count() == 1)
        rec_identity = page3.evaluate('() => ({timeOrigin:performance.timeOrigin, url:location.href})')
        rec_initial_navigations = len(rec_navigations)
        rec_initial_documents = len(rec_documents)
        pending_before = snapshot('beforeLivePendingPolling')
        page3.wait_for_timeout(8500)
        check('Live pending browser GET polling causes zero changes in all 12 tables', pending_before == snapshot('afterLivePendingPolling'))
        pending_polls = [e for e in browser_events if e['context'] == 'Receptionist' and e['event'] == 'response'
                         and '/desk-projection/' in e['path'] and e.get('paymentStatus') == 'PENDING']
        check('Receptionist performed real recurring pending GET polling', len(pending_polls) >= 3, responses=pending_polls)
        poll_intervals = [(datetime.datetime.fromisoformat(b['timestamp']) - datetime.datetime.fromisoformat(a['timestamp'])).total_seconds()
                          for a, b in zip(pending_polls, pending_polls[1:])]
        check('Observed pending polling interval matches 4-second timer within scheduling tolerance',
              all(2.5 <= interval <= 6 for interval in poll_intervals), intervalsSeconds=poll_intervals)

        # ----------------------------------------------------
        # BROWSER CONTEXT 1: Member Desktop Session Initiation
        # ----------------------------------------------------
        ctx1 = browser.new_context(viewport={'width': 1440, 'height': 900}, ignore_https_errors=True)
        configure_page(ctx1, auth_mem, 'light')
        page1 = ctx1.new_page()
        observe_page(page1, 'Member')

        # Member Payment Summary & Start Session
        page1.goto(f'https://demo.example.test:55443/member/payments/{p1_id}/qr-demo')
        expect(page1.locator('.qr-demo-card')).to_be_visible()
        expect(page1.get_by_role('button', name='Start simulation session')).to_be_visible()

        # Capture pending view & scan axe
        page1.screenshot(path=str(args.out / 'screenshots/ci-e2e-member-desktop-pending.png'))
        pending_axe = run_axe(page1, 'ci-e2e-member-desktop-pending')
        check('Context 1 Member desktop pending axe has 0 violations', pending_axe['violationsCount'] == 0)

        # Verify actual brand pixels against their matching local background.
        keyboard_results = [keyboard_check(page3, 'reception-pending')]
        for live_page, label in ((page1, 'member-light'), (page3, 'reception-light')):
            measured = measure_brand(live_page, label)
            check(f'{label}: brand local contrast meets conservative 4.5 threshold', measured['passed'],
                  minimumRatio=measured['minimumLocalContrastRatio'])

        # Trigger genuine session creation
        page1.get_by_role('button', name='Start simulation session').click()
        link = page1.get_by_role('link', name='Open simulation confirmation')
        expect(link).to_be_visible()
        confirmation_url = link.get_attribute('href')
        check('Spring Boot issued genuine confirmation URL', confirmation_url and '#token=' in confirmation_url)
        token = confirmation_url.split('#token=')[1]
        secrets.append(token)
        check('Confirmation token has valid base64url shape', len(token) >= 42 and re.fullmatch(r'[A-Za-z0-9_-]+', token))

        # Decode rendered application QR image using zxing-cpp
        qr_svg = page1.locator('.demo-session-panel svg')
        expect(qr_svg).to_be_visible()
        if qr_svg.is_visible():
            barcodes = zxingcpp.read_barcodes(Image.open(BytesIO(qr_svg.screenshot())))
            check('Rendered application QR decodes to genuine confirmation URL', len(barcodes) == 1 and barcodes[0].text == confirmation_url)

        # A real JVM restart, distinct from the historical new-service-object test.
        pending_restart_before = snapshot('beforePendingJvmRestart')
        first_pid = process.pid
        stop_backend()
        restarted_pid = start_backend()
        persisted_status, persisted_pending = api_request('/payments/simulated-qr/sessions/preview', {'token': token}, bearer=auth_mem['accessToken'])
        check('Real JVM restart preserves authenticated pending session and token verifier',
              restarted_pid != first_pid and persisted_status == 200 and persisted_pending.get('state') == 'PENDING')
        check('Pending JVM restart and preview preserve all 12 SQL tables', pending_restart_before == snapshot('afterPendingJvmRestart'))
        for forbidden_role, forbidden_auth in (('Coach', auth_coach), ('Manager', auth_mgr), ('Receptionist', auth_rec)):
            denied_status, _ = api_request('/payments/simulated-qr/sessions/confirm', {'token': token}, bearer=forbidden_auth['accessToken'])
            check(forbidden_role + ' cannot confirm genuine pending owner session', denied_status == 403)
        anonymous_read, _ = api_request('/payments/simulated-qr/sessions/' + persisted_pending['id'])
        check('Anonymous caller cannot read genuine owner session', anonymous_read == 401)

        # ----------------------------------------------------
        # BROWSER CONTEXT 2: Isolated Member Confirmation with Genuine Response Loss (R4-01)
        # ----------------------------------------------------
        ctx2 = browser.new_context(viewport={'width': 1440, 'height': 900}, ignore_https_errors=True)
        configure_page(ctx2, auth_mem, 'light')
        page2 = ctx2.new_page()
        observe_page(page2, 'Confirmation')

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

        confirm_before = snapshot('beforeLostResponseConfirmation')
        check('Repeated live preview reads preserve pending SQL state', confirm_before == sql_evidence['snapshots']['afterPendingJvmRestart']['sha256'])
        measured = measure_brand(page2, 'confirmation-light')
        check('confirmation-light: brand local contrast meets conservative 4.5 threshold', measured['passed'])

        # R4-01: Genuine Transport Failure AFTER commit
        # 1. Forward real confirmation request to Spring Boot backend.
        # 2. Wait for backend to commit transaction (status 200).
        # 3. Deliberately abort client response so browser experiences transport response loss.
        # 4. Abort immediate preview catch-block call once so client transitions to uncertainty alert.
        transport_drop = {'confirm_committed': False, 'preview_dropped': False, 'errors': []}

        def intercept_transport(route, request):
            path = urlsplit(request.url).path
            try:
                if request.method == 'POST' and path.endswith('/sessions/confirm') and not transport_drop['confirm_committed']:
                    # Playwright APIRequest uses OS DNS, unlike Edge host-resolver-rules.
                    # Forward the unchanged real POST to the same HTTPS loopback backend.
                    res = route.fetch(url='https://127.0.0.1:55444' + path, max_retries=0, max_redirects=0)
                    data = res.json()
                    check('Real upstream confirmation returns committed SETTLED state', res.status == 200 and data.get('state') == 'SETTLED')
                    committed = sql_json(f"SELECT payment_status,settlement_type,simulated_transaction_id FROM dbo.payment WHERE id={p1_id} FOR JSON PATH, WITHOUT_ARRAY_WRAPPER;")
                    check('Independent SQL read sees commit before client response is dropped',
                          committed['payment_status'] == 'SUCCESS' and committed['settlement_type'] == 'SIMULATED'
                          and committed['simulated_transaction_id'] is not None)
                    transport_drop.update({'confirm_committed': True, 'upstreamStatus': res.status,
                                           'upstreamState': data['state'], 'committedSql': committed,
                                           'committedAt': timestamp()})
                    sql_evidence['commitBeforeResponseAbort'] = committed
                    route.abort('connectionreset')
                elif request.method == 'POST' and path.endswith('/sessions/preview') and transport_drop['confirm_committed'] and not transport_drop['preview_dropped']:
                    transport_drop['preview_dropped'] = True
                    route.abort('connectionreset')
                else:
                    route.continue_()
            except Exception as error:
                # Route.fetch exceptions include bearer headers; never print them.
                transport_drop['errors'].append(sanitize(error))
                route.abort('failed')

        page2.route('**/api/v1/payments/simulated-qr/sessions/**', intercept_transport)

        # Click Confirm simulated payment
        page2.get_by_role('button', name='Confirm simulated payment').click()

        # Verify client displays uncertainty alert
        expect(page2.locator('.notice--danger')).to_be_visible(timeout=20000)
        expect(page2.get_by_role('alert')).to_contain_text('The simulation outcome could not be confirmed')
        page2.screenshot(path=str(args.out / 'screenshots/ci-e2e-confirm-uncertainty-alert.png'))
        check('Client displays explicit uncertainty alert on transport loss after backend commit',
              transport_drop['confirm_committed'] and transport_drop['preview_dropped'] and not transport_drop['errors']
              and page2.get_by_role('alert').is_visible(), transport=transport_drop)
        confirm_failures = [e for e in browser_events if e['context'] == 'Confirmation' and e['event'] == 'requestfailed' and e['path'].endswith('/sessions/confirm')]
        confirm_responses = [e for e in browser_events if e['context'] == 'Confirmation' and e['event'] == 'response' and e['path'].endswith('/sessions/confirm')]
        check('Browser records connection reset and receives no successful confirmation response',
              len(confirm_failures) == 1 and 'CONNECTION_RESET' in confirm_failures[0]['failure'] and not confirm_responses,
              failures=confirm_failures, responses=confirm_responses)
        sql_evidence['transportDrop'] = transport_drop
        snapshot('afterLostResponseCommit')

        # Remove route interception to restore network connectivity
        page2.unroute('**/api/v1/payments/simulated-qr/sessions/**', intercept_transport)

        reconciliation_start = timestamp()
        # Client reconciles state via fresh backend read: Click "Refresh Simulation Status"
        page2.get_by_role('button', name='Refresh Simulation Status').click()

        # Verify UI reconciles to committed Paid - Simulated state
        expect(page2.locator('.notice--success')).to_be_visible()
        expect(page2.get_by_text('Paid - Simulated').first).to_be_visible()
        page2.screenshot(path=str(args.out / 'screenshots/ci-e2e-confirm-settled.png'))
        settled_axe = run_axe(page2, 'ci-e2e-confirm-settled')
        check('Context 2 Confirm settled axe has 0 violations', settled_axe['violationsCount'] == 0)
        fresh_reads = [e for e in browser_events if e['context'] == 'Confirmation' and e['event'] == 'response'
                       and e['path'].endswith('/sessions/preview') and e['timestamp'] >= reconciliation_start and e['status'] == 200]
        check('Client reconciles to authoritative committed state via fresh backend read',
              bool(fresh_reads) and page2.get_by_text('Paid - Simulated').first.is_visible(), reads=fresh_reads)
        check('Reconciliation read leaves all 12 SQL tables unchanged',
              sql_evidence['snapshots']['afterLostResponseCommit']['sha256'] == snapshot('afterReconciliationRead'))

        # ----------------------------------------------------
        # BROWSER CONTEXT 3: Receptionist Live Polling Transition (R4-02)
        # ----------------------------------------------------
        # Page 3 was kept open in PENDING state. The 4-second polling timer now
        # automatically updates the DOM to Paid - Simulated without reload or navigation.
        expect(page3.get_by_text('Paid - Simulated').first).to_be_visible(timeout=20000)
        expect(page3.get_by_text('SIMULATED (SPORTIFY_SIMULATOR)', exact=True)).to_be_visible()
        page3.screenshot(path=str(args.out / 'screenshots/ci-e2e-reception-settled-simulated.png'))
        rec_settled_axe = run_axe(page3, 'ci-e2e-reception-settled-simulated')
        check('Context 3 Receptionist projection settled axe has 0 violations', rec_settled_axe['violationsCount'] == 0)
        settled_polls = [e for e in browser_events if e['context'] == 'Receptionist' and e['event'] == 'response'
                         and '/desk-projection/' in e['path'] and e.get('paymentStatus') == 'SUCCESS']
        check('Receptionist desk projection updates live from PENDING to Paid - Simulated via polling without reload',
              bool(settled_polls) and len(rec_navigations) == rec_initial_navigations and len(rec_documents) == rec_initial_documents
              and rec_identity == page3.evaluate('() => ({timeOrigin:performance.timeOrigin, url:location.href})')
              and all(e['method'] == 'GET' and e.get('settlementType') == 'SIMULATED' for e in settled_polls),
              pageIdentity=rec_identity, initialNavigationCount=rec_initial_navigations,
              navigations=rec_navigations, documents=rec_documents, settledResponses=settled_polls)
        check('Settled desk displays no scannable QR or settlement action', page3.locator('.qr-demo-card svg').count() == 0
              and page3.get_by_role('button', name='Confirm simulated payment').count() == 0)
        keyboard_results.append(keyboard_check(page3, 'reception-settled'))

        # ----------------------------------------------------
        # BROWSER CONTEXT 1: Member View Post-Settlement Readback
        # ----------------------------------------------------
        page1.reload()
        expect(page1.locator('.notice--success')).to_be_visible()
        expect(page1.get_by_text('Paid - Simulated').first).to_be_visible()
        page1.screenshot(path=str(args.out / 'screenshots/ci-e2e-member-settled-simulated.png'))
        check('Context 1 displays committed Paid - Simulated state after settlement', page1.get_by_text('Paid - Simulated').first.is_visible())

        # Execute live light/dark audits, contrast, reflow and CSS zoom separately.
        for live_page, label in ((page1, 'member'), (page2, 'confirmation'), (page3, 'reception')):
            live_page.get_by_role('button', name='Switch to dark mode').click()
            dark_axe = run_axe(live_page, label + '-dark-settled')
            check(label + ': dark settled axe has 0 violations', dark_axe['violationsCount'] == 0)
            measured = measure_brand(live_page, label + '-dark')
            check(label + ': dark brand local contrast meets conservative 4.5 threshold', measured['passed'])
            live_page.screenshot(path=str(args.out / f'screenshots/ci-e2e-{label}-dark-settled.png'))
        layout_results = []
        for width, height, zoom, label in ((720, 450, '1', 'reflow-720'), (1440, 900, '2', 'css-zoom-200')):
            page3.set_viewport_size({'width': width, 'height': height})
            page3.evaluate('(zoom) => document.body.style.zoom = zoom', zoom)
            geometry = page3.evaluate('() => ({scrollWidth:document.documentElement.scrollWidth, clientWidth:document.documentElement.clientWidth, cssZoom:getComputedStyle(document.body).zoom})')
            check(label + ': no horizontal overflow', geometry['scrollWidth'] <= geometry['clientWidth'], geometry=geometry)
            page3.screenshot(path=str(args.out / f'screenshots/ci-e2e-reception-{label}.png'))
            layout_results.append({'label': label, **geometry, 'nativeBrowserZoom': 'NOT RUN'})
        (args.out / 'browser-logs/keyboard-navigation.json').write_text(json.dumps(keyboard_results, indent=2) + '\n', encoding='ascii')
        (args.out / 'browser-logs/layout-geometry.json').write_text(json.dumps(layout_results, indent=2) + '\n', encoding='ascii')
        negative_page = ctx1.new_page()
        negative_page.set_content('<html><body><span id="low" style="font:bold 32px sans-serif;color:#aaa;background:#fff">Low contrast control</span></body></html>')
        negative = measure_brand(negative_page, 'known-low-contrast-negative-control', '#low')
        check('Rendered known low-contrast negative control is rejected', not negative['passed'] and negative['minimumLocalContrastRatio'] < 4.5)
        browser.close()

    # Step 11: Idempotency & Negative Controls via API
    replay_before = snapshot('beforeDuplicateRetry')
    status_dup, dup = api_request('/payments/simulated-qr/sessions/confirm', {'token': token}, bearer=auth_mem['accessToken'])
    check('Duplicate confirmation is idempotent 200 with matching state', status_dup == 200 and dup.get('state') == 'SETTLED')
    check('Duplicate confirmation changes none of the 12 SQL tables', replay_before == snapshot('afterDuplicateRetry'))
    status_staff_confirm, _ = api_request('/payments/simulated-qr/sessions/confirm', {'token': token}, bearer=auth_rec['accessToken'])
    check('Receptionist cannot confirm even with genuine owner session token', status_staff_confirm == 403)

    session_id = dup.get('id')
    status_can, _ = api_request(f'/payments/simulated-qr/sessions/{session_id}/cancel', {}, bearer=auth_mem['accessToken'])
    check('Cancel after SETTLED is rejected with 409 Conflict', status_can == 409)

    # Step 12: Receptionist Polling Zero-Write Invariant
    poll_before = snapshot('beforeSettledReadOnlyPolling')
    for _ in range(5):
        st, proj = api_request(f'/payments/simulated-qr/desk-projection/{p1_id}', bearer=auth_rec['accessToken'])
        if st != 200:
            raise RuntimeError(f'Desk projection polling failed with status {st}')
    poll_after = snapshot('afterSettledReadOnlyPolling')
    for table, b_hash in poll_before.items():
        check(f'Receptionist desk polling produces 0 database writes: {table}', b_hash == poll_after[table])

    # Step 13: Authoritative SQL Readback & Ledger Isolation
    after_fingerprints = snapshot('afterSettlementAndRetry')

    # Specific before/after financial snapshot assertions (R4-05)
    for mutated_tbl in ['payment', 'simulated_payment_transaction', 'simulated_qr_session', 'invoice', 'invoice_line', 'member_card', 'activity_log']:
        check(f'Authoritative SQL state transition recorded in table: {mutated_tbl}', before_fingerprints[mutated_tbl] != after_fingerprints[mutated_tbl])

    for untouched_tbl in ['cash_event', 'cash_capture_ticket', 'cash_coverage', 'refund_request', 'sport_package_registration']:
        check(f'Financial ledger table strictly untouched (0 writes, identical digest): {untouched_tbl}', before_fingerprints[untouched_tbl] == after_fingerprints[untouched_tbl])

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
    check('Exactly 1 matching simulated transaction exists',
          sql(f"SELECT COUNT(*) FROM dbo.simulated_payment_transaction WHERE payment_id={p1_id};") == '1'
          and txn_row['id'] == p1_row['simulated_transaction_id'] and txn_row['provider'] == 'SPORTIFY_SIMULATOR')
    check('Transaction amount, session and owner match payment', sql(f"""
        SELECT COUNT(*) FROM dbo.simulated_payment_transaction t
        JOIN dbo.payment p ON p.id=t.payment_id AND p.simulated_transaction_id=t.id
        JOIN dbo.simulated_qr_session s ON s.id=t.session_id AND s.payment_id=p.id
        WHERE p.id={p1_id} AND t.amount=p.amount AND s.amount=p.amount AND t.member_id=p.member_id
        AND s.owner_id=p.member_id AND s.state='SETTLED' AND s.active=0 AND t.currency='VND';
    """) == '1')

    inv_count = sql(f"SELECT COUNT(*) FROM dbo.invoice WHERE payment_id={p1_id} AND status='PAID';")
    check('Exactly 1 invoice issued with status PAID', inv_count == '1')

    card_count = sql(f"SELECT COUNT(*) FROM dbo.member_card WHERE payment_id={p1_id} AND status='ACTIVE';")
    check('Exactly 1 member card activated', card_count == '1')
    check('Invoice total and single entitlement line match authoritative payment', sql(f"""
        SELECT COUNT(*) FROM dbo.invoice i JOIN dbo.invoice_line l ON l.invoice_id=i.id
        JOIN dbo.member_card c ON c.id=l.item_reference_id AND c.payment_id=i.payment_id
        JOIN dbo.payment p ON p.id=i.payment_id WHERE p.id={p1_id} AND i.total_amount=p.amount
        AND l.line_total=p.amount AND l.quantity=1 AND l.item_type='MEMBERSHIP_CARD' AND c.status='ACTIVE';
    """) == '1')

    audits = sql_json(f"SELECT id,actor_user_id,action,entity_type,entity_id,details FROM dbo.activity_log WHERE entity_type='PAYMENT' AND entity_id={p1_id} ORDER BY id FOR JSON PATH;")
    check('Exactly 1 PAYMENT_CONFIRMED activity log audit recorded', len(audits) == 1 and audits[0]['action'] == 'PAYMENT_CONFIRMED')
    audit_details = json.loads(audits[0]['details'])
    check('Audit contains simulated provenance and matches real transaction and member actor',
          audit_details.get('settlementType') == 'SIMULATED' and audit_details.get('settlementProvider') == 'SPORTIFY_SIMULATOR'
          and audit_details.get('simulatedTransactionId') == txn_row['id']
          and str(audits[0]['actor_user_id']) == sql(f'SELECT member_id FROM payment WHERE id={p1_id};'))
    check('Unrelated pending payment and entitlement remain unchanged', sql(f"""
        SELECT COUNT(*) FROM dbo.payment p JOIN dbo.member_card c ON c.payment_id=p.id WHERE p.id={p2_id}
        AND p.payment_status='PENDING' AND p.simulated_transaction_id IS NULL AND c.status='PENDING_PAYMENT';
    """) == '1')

    # Negative Control: Attempt unauthorized table mutation violating check constraint (R4-05)
    controls = []
    for mutation in (
        "settlement_provider=NULL", "settlement_type=NULL", "simulated_transaction_id=NULL",
        "settlement_type='ACTUAL'", "settlement_provider='UNAPPROVED_PROVIDER'",
    ):
        # Always roll back even if an invalid mutation unexpectedly succeeds.
        control = sql_json(f"""
            BEGIN TRY
                BEGIN TRANSACTION;
                UPDATE dbo.payment SET {mutation} WHERE id={p1_id};
                ROLLBACK;
                SELECT 0 AS errorNumber,'MUTATION ACCEPTED' AS errorMessage FOR JSON PATH, WITHOUT_ARRAY_WRAPPER;
            END TRY BEGIN CATCH
                IF @@TRANCOUNT>0 ROLLBACK;
                SELECT ERROR_NUMBER() AS errorNumber,ERROR_MESSAGE() AS errorMessage FOR JSON PATH, WITHOUT_ARRAY_WRAPPER;
            END CATCH;
        """)
        controls.append({'mutation': mutation, **control})
        check('V36 rejects invalid provenance: ' + mutation, constraint_rejected(control, 'ck_payment_simulated_consistency'), sqlResult=control)
    check('Every rolled-back provenance negative control preserves all 12 tables', after_fingerprints == snapshot('afterProvenanceNegativeControls'))
    sql_evidence['constraintNegativeControls'] = controls
    sql_evidence['paymentAudit'] = audits

    status_finance, finance_after = api_request('/manager/finance/overview?month=' + finance_month, bearer=auth_mgr['accessToken'])
    check('Simulated finance total increments exactly once and actual revenue is unchanged',
          status_finance == 200 and finance_after['simulatedTransactions'] == 1
          and float(finance_after['simulatedRevenue']) == 810000
          and all(finance_after[key] == finance_before[key] for key in ('paidRevenue', 'paidTransactions', 'refundedAmount', 'refundedTransactions')))
    sql_evidence['financeAfter'] = finance_after
    status_transactions, transactions = api_request('/manager/finance/transactions?search=QR07-CI-P01', bearer=auth_mgr['accessToken'])
    check('Manager transaction list contains committed simulated provenance', status_transactions == 200 and
          any(str(item.get('settlementType')) == 'SIMULATED' and item.get('settlementProvider') == 'SPORTIFY_SIMULATOR'
              for item in transactions.get('content', [])))
    export_status, export_csv = api_request('/manager/finance/revenue/export', bearer=auth_mgr['accessToken'], response_format='text')
    check('Actual revenue CSV export does not include simulated payment', export_status == 200 and 'QR07-CI-P01' not in export_csv)
    sql_evidence['financeExport'] = {'status': export_status, 'sha256': hashlib.sha256(export_csv.encode()).hexdigest(),
                                    'lines': export_csv.splitlines(), 'contract': 'Actual revenue export excludes simulated settlements'}

    stop_backend()
    disabled_pid = start_backend(simulator_enabled='false')
    history_status, history = api_request('/payments/my', bearer=auth_mem['accessToken'])
    receipt_status, receipt = api_request('/invoices/by-payment/' + str(p1_id), bearer=auth_mem['accessToken'])
    check('History and receipt survive JVM restart with simulator kill switch disabled',
          disabled_pid > 0 and history_status == 200 and receipt_status == 200
          and any(item.get('id') == p1_id and item.get('settlementType') == 'SIMULATED' for item in history.get('content', []))
          and receipt.get('paymentId') == p1_id)
    disabled_status, disabled_capability = api_request('/payments/simulated-qr/capability', bearer=auth_mem['accessToken'])
    check('Restarted server reports simulator disabled', disabled_status == 200 and not disabled_capability.get('enabled') and not disabled_capability.get('settlementEnabled'))
    check('Kill-switch history and receipt reads preserve all 12 SQL tables', after_fingerprints == snapshot('afterDisabledJvmRestartHistoryReads'))
    private_log_content = '\n'.join(p.read_text(encoding='utf8', errors='replace') for p in args.private.glob('backend-*.log'))
    check('Real backend logs contain no raw QR confirmation token', token not in private_log_content)

    # Correlation ID audit verification (R4-03)
    check('Client correlation ID is attached to every harness API request', bool(network_logs) and all(e.get('sentHeaderVerified') for e in network_logs))
    observations = [correlation_observation(e['clientCorrelationId'], e['serverCorrelationId']) for e in network_logs]
    report['correlation'] = {'observations': observations, 'serverEchoContract': 'NOT IMPLEMENTED / NOT RUN',
                             'browserClientHeaders': 'Recorded as observed, including null; no injected fallback'}

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
    sql_evidence.update(sql_snapshot)

    report['status'] = 'PASS'
    report['beforeFinancialSha256'] = before_fingerprints
    report['afterFinancialSha256'] = after_fingerprints
    print(f"\n[QR-07] CI E2E ACCEPTANCE SUCCESS: {len(report['checks'])} checks PASSED!")

except Exception as err:
    report['status'] = 'FAIL'
    report['failureType'] = type(err).__name__
    report['failure'] = sanitize(err)[:1000]
    print(f"\n[QR-07] CRITICAL FAILURE: {sanitize(err)}")

finally:
    stop_backend()
    for l in logs:
        l.close()
    # Persist partial authentic evidence on failures, too; sanitize before upload.
    for relative, payload in (
        ('verification.json', report), ('sql/qr07-ci-sql-evidence.json', sql_evidence),
        ('browser-logs/network-requests.json', network_logs), ('browser-logs/browser-events.json', browser_events),
        ('accessibility/axe-core-audit.json', {'results': axe_results}),
        ('accessibility/rendered-pixel-contrast.json', {'results': contrast_results}),
    ):
        (args.out / relative).write_text(sanitize(json.dumps(payload, indent=2)) + '\n', encoding='ascii')
    (args.out / 'browser-logs/browser-console.log').write_text(sanitize('\n'.join(console_logs)) + '\n', encoding='ascii')
    provenance = {key: report[key] for key in ('head', 'runId', 'runAttempt', 'runUrl', 'database', 'environment', 'status')}
    provenance['files'] = {p.relative_to(args.out).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest()
                           for p in sorted(args.out.rglob('*')) if p.is_file()}
    (args.out / 'artifact-provenance.json').write_text(json.dumps(provenance, indent=2) + '\n', encoding='ascii')

raise SystemExit(0 if report['status'] == 'PASS' else 1)
