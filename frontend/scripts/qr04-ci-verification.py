"""CI-only real SQL/browser/restart verification; never uses a workstation database."""
import argparse
import base64
import datetime
import hashlib
import http.server
import ipaddress
import json
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

import bcrypt
from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.hazmat.primitives.serialization.pkcs12 import serialize_key_and_certificates
from cryptography.x509.oid import NameOID

parser = argparse.ArgumentParser()
for name in ['jar', 'assets', 'axe', 'out', 'private']:
    parser.add_argument('--' + name, type=Path, required=True)
args = parser.parse_args()
if os.environ.get('CI') != 'true' or sys.platform != 'linux':
    raise SystemExit('This harness permits Docker only inside Linux CI, never locally.')
container = os.environ['QR04_CI_SQL_CONTAINER']
if not re.fullmatch(r'[a-f0-9]{12,64}', container):
    raise SystemExit('Requires the dedicated CI SQL service container ID')
password = os.environ['QR04_CI_SQL_PASSWORD']
args.out.mkdir(parents=True, exist_ok=True)
args.private.mkdir(parents=True, exist_ok=True)
database = 'sportify_qr04_test_' + datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%d%H%M%S')
assert re.fullmatch(r'sportify_qr04_test_[0-9]{14}', database)
root = Path(__file__).resolve().parents[2]
head = subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip()
report = {'head': head, 'database': database, 'checks': [], 'settlementEnabled': False}
process = None
logs = []
created = []
auth = None


def check(name, passed, **details):
    report['checks'].append({'name': name, 'passed': bool(passed), **details})
    if not passed:
        raise AssertionError(name)


def sql(query, target=None):
    environment = os.environ.copy()
    environment['SQLCMDPASSWORD'] = password
    command = ['docker', 'exec', '-i', '-e', 'SQLCMDPASSWORD', container,
               '/opt/mssql-tools18/bin/sqlcmd', '-S', 'localhost', '-U', 'sa', '-C',
               '-d', target or database, '-l', '5', '-t', '20', '-b', '-h', '-1', '-W']
    result = subprocess.run(command, input='SET NOCOUNT ON; SET QUOTED_IDENTIFIER ON; ' + query,
                            capture_output=True, text=True, timeout=30, env=environment)
    if result.returncode:
        raise RuntimeError('Dedicated CI SQL command failed')
    return result.stdout.strip()


def fingerprints():
    tables = ['payment', 'invoice', 'invoice_line', 'member_card', 'sport_package_registration',
              'refund_request', 'cash_event', 'cash_capture_ticket', 'cash_coverage']
    return {table: sql("SELECT CONVERT(varchar(64),HASHBYTES('SHA2_256',CONVERT(nvarchar(max),"
                       "(SELECT * FROM dbo." + table + " ORDER BY (SELECT NULL) FOR JSON PATH,INCLUDE_NULL_VALUES))),2);")
            for table in tables}


def request(path, body=None, bearer=None):
    headers = {'Content-Type': 'application/json'}
    if bearer:
        headers['Authorization'] = 'Bearer ' + bearer
    req = urllib.request.Request('https://127.0.0.1:55444/api/v1' + path,
                                 json.dumps(body).encode() if body is not None else None, headers)
    try:
        response = urllib.request.urlopen(req, context=tls, timeout=20)
    except urllib.error.HTTPError as error:
        response = error
    return response.status, json.loads(response.read())


def start_backend():
    global process
    log_path = args.private / ('backend-' + str(len(logs)) + '.log')
    log = log_path.open('w', encoding='utf8')
    logs.append(log)
    environment = os.environ.copy()
    environment.update({'DB_URL': f'jdbc:sqlserver://127.0.0.1:1433;databaseName={database};encrypt=false;loginTimeout=10;socketTimeout=30000',
                        'DB_USERNAME': 'sa', 'DB_PASSWORD': password,
                        'JWT_SECRET': 'cXJvNC1pc29sYXRlZC10ZXN0LWtleS1uby1yZWFsLWNyZWRlbnRpYWxz',
                        'SPORTIFY_PAYMENT_ACCOUNT_NAME': 'CI synthetic center',
                        'SPORTIFY_PAYMENT_ACCOUNT_NUMBER': 'NOT_AVAILABLE', 'SPORTIFY_PAYMENT_BANK': 'Simulation only',
                        'PAYMENT_SIMULATOR_ENABLED': 'true', 'PAYMENT_SIMULATOR_LIFETIME_SECONDS': '600',
                        'PAYMENT_SIMULATOR_PUBLIC_BASE_URL': 'https://demo.example.test:55443/',
                        'PAYMENT_SIMULATOR_ALLOWED_ORIGINS': 'https://demo.example.test:55443',
                        'CORS_ALLOWED_ORIGINS': 'https://demo.example.test:55443',
                        'SERVER_SSL_ENABLED': 'true', 'SERVER_SSL_KEY_STORE': str((args.private / 'tls.p12').resolve()),
                        'SERVER_SSL_KEY_STORE_TYPE': 'PKCS12', 'SERVER_SSL_KEY_STORE_PASSWORD': 'Qr04CiTls2026!'})
    process = subprocess.Popen(['java', '-Xmx512m', '-jar', str(args.jar.resolve()),
                                '--spring.profiles.active=qr-simulator', '--server.address=127.0.0.1', '--server.port=55444'],
                               env=environment, stdout=log, stderr=subprocess.STDOUT)
    deadline = time.monotonic() + 180
    while time.monotonic() < deadline:
        if process.poll() is not None:
            raise RuntimeError('Dedicated CI backend exited during startup')
        if 'Started SportifyApplication' in log_path.read_text():
            return process.pid
        time.sleep(1)
    raise TimeoutError('Dedicated CI backend startup exceeded 180 seconds')


def stop_backend():
    if process is not None and process.poll() is None:
        process.terminate()
        try:
            process.wait(timeout=15)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait(timeout=5)


try:
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
    key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    subject = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, 'QR04 isolated CI verification')])
    now = datetime.datetime.now(datetime.timezone.utc)
    cert = (x509.CertificateBuilder().subject_name(subject).issuer_name(subject).public_key(key.public_key())
            .serial_number(x509.random_serial_number()).not_valid_before(now - datetime.timedelta(minutes=5))
            .not_valid_after(now + datetime.timedelta(days=1))
            .add_extension(x509.SubjectAlternativeName([x509.DNSName('demo.example.test'),
                           x509.DNSName('api.demo.example.test'), x509.IPAddress(ipaddress.ip_address('127.0.0.1'))]), False)
            .sign(key, hashes.SHA256()))
    (args.private / 'tls-cert.pem').write_bytes(cert.public_bytes(serialization.Encoding.PEM))
    (args.private / 'tls-key.pem').write_bytes(key.private_bytes(serialization.Encoding.PEM,
                                              serialization.PrivateFormat.PKCS8, serialization.NoEncryption()))
    (args.private / 'tls.p12').write_bytes(serialize_key_and_certificates(b'qr04', key, cert, None,
                                          serialization.BestAvailableEncryption(b'Qr04CiTls2026!')))
    pin = base64.b64encode(hashlib.sha256(key.public_key().public_bytes(serialization.Encoding.DER,
                                        serialization.PublicFormat.SubjectPublicKeyInfo)).digest()).decode()
    tls = ssl.create_default_context(cafile=str(args.private / 'tls-cert.pem'))
    first_pid = start_backend()
    check('Fresh isolated SQL migrated to V34', sql('SELECT MAX(version) FROM flyway_schema_history WHERE success=1;') == '34')
    encoded = bcrypt.hashpw(b'Qr04IsolatedTest2026!', bcrypt.gensalt(rounds=10)).decode()
    sql("SET XACT_ABORT ON; BEGIN TRANSACTION; INSERT user_account(email,password_hash,full_name,role_id,status,version) "
        f"VALUES ('owner@qr04.example.test','{encoded}','Synthetic QR04 CI Member',(SELECT id FROM role WHERE code='MEMBER'),'ACTIVE',0); "
        "DECLARE @owner bigint=SCOPE_IDENTITY(); INSERT member_profile(user_id,member_code,current_level,joined_on) "
        "VALUES (@owner,'QR04-CI','BEGINNER',CAST(SYSUTCDATETIME() AS DATE)); "
        "INSERT payment(payment_code,member_id,amount,payment_method,payment_status,version) "
        "VALUES ('QR04-CI-P01',@owner,810000,'BANK_TRANSFER','PENDING',0); DECLARE @one bigint=SCOPE_IDENTITY(); "
        "INSERT member_card(card_code,member_id,tier_id,start_date,status,price_paid,payment_id) "
        "VALUES ('QR04-CI-C01',@owner,(SELECT TOP 1 id FROM membership_card_tier ORDER BY id),CAST(SYSUTCDATETIME() AS DATE),'PENDING_PAYMENT',810000,@one); "
        "INSERT payment(payment_code,member_id,amount,payment_method,payment_status,version) "
        "VALUES ('QR04-CI-P02',@owner,510000,'BANK_TRANSFER','PENDING',0); DECLARE @two bigint=SCOPE_IDENTITY(); "
        "INSERT member_card(card_code,member_id,tier_id,start_date,status,price_paid,payment_id) "
        "VALUES ('QR04-CI-C02',@owner,(SELECT TOP 1 id FROM membership_card_tier ORDER BY id),CAST(SYSUTCDATETIME() AS DATE),'PENDING_PAYMENT',510000,@two); COMMIT;")
    payment_ids = [int(value) for value in sql("SELECT id FROM payment WHERE payment_code IN ('QR04-CI-P01','QR04-CI-P02') ORDER BY payment_code;").splitlines()]
    status, auth = request('/auth/login', {'email': 'owner@qr04.example.test', 'password': 'Qr04IsolatedTest2026!'})
    check('Real owner authenticates', status == 200)
    before = fingerprints()
    for payment in payment_ids:
        status, value = request('/payments/simulated-qr/sessions', {'paymentId': payment, 'bankCode': 'BIDV_DEMO', 'requestId': str(uuid.uuid4())}, auth['accessToken'])
        check('Create durable restart fixture', status == 200)
        created.append(value)
    status, intent = request('/payments/simulated-qr/sessions/confirm-intent', {'token': created[1]['confirmationUrl'].split('#token=')[1]}, auth['accessToken'])
    check('Intent remains pending settlement', status == 200 and intent['state'] == 'AUTHORIZED_PENDING_SETTLEMENT')
    stop_backend()
    second_pid = start_backend()
    check('Actual JVM was replaced', first_pid != second_pid, stoppedPid=first_pid, restartedPid=second_pid)
    for item, expected in zip(created, ['PENDING', 'AUTHORIZED_PENDING_SETTLEMENT']):
        status, view = request('/payments/simulated-qr/sessions/' + item['session']['id'], bearer=auth['accessToken'])
        check('Restart preserves ' + expected, status == 200 and view.get('state') == expected)
        status, view = request('/payments/simulated-qr/sessions/' + item['session']['id'] + '/cancel', {}, auth['accessToken'])
        check('Restart token canceled', status == 200 and view.get('state') == 'CANCELED')
    class Handler(http.server.SimpleHTTPRequestHandler):
        def __init__(self, *values, **kwargs):
            super().__init__(*values, directory=str(args.assets.resolve()), **kwargs)
        def log_message(self, *values):
            pass
        def do_GET(self):
            target = (args.assets / self.path.split('?', 1)[0].lstrip('/')).resolve()
            if args.assets.resolve() not in target.parents or not target.is_file():
                self.path = '/index.html'
            super().do_GET()
    server = http.server.ThreadingHTTPServer(('127.0.0.1', 55443), Handler)
    server_tls = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    server_tls.load_cert_chain(args.private / 'tls-cert.pem', args.private / 'tls-key.pem')
    server.socket = server_tls.wrap_socket(server.socket, server_side=True)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    candidate = args.private / 'browser'
    browser_args = [sys.executable, str(root / 'frontend/scripts/qr04-browser-check.py'), '--base', 'https://demo.example.test:55443',
                    '--api', 'https://127.0.0.1:55444/api/v1', '--out', str(candidate), '--asset-root', str(args.assets),
                    '--axe', str(args.axe), '--payment-id', str(payment_ids[0]), '--native-https', '--tls-ca', str(args.private / 'tls-cert.pem'),
                    '--spki-pin', pin, '--host-resolver-rules', 'MAP demo.example.test 127.0.0.1,MAP api.demo.example.test 127.0.0.1']
    result = subprocess.run(browser_args, capture_output=True, text=True, timeout=300)
    browser_result = json.loads((candidate / 'browser-logs/qr04-round03-browser-results.json').read_text())
    (args.out / 'browser-results.json').write_text(json.dumps(browser_result, indent=2) + '\n', encoding='ascii')
    safe = bool(browser_result['tokenInvalidation']) and all(item['confirmedTerminal'] for item in browser_result['tokenInvalidation'])
    check('Native SQL-backed Edge browser completes', result.returncode == 0, functionalChecks=len(browser_result['checks']), axeScans=len(browser_result['accessibility']))
    check('Every screenshot token verified terminal', safe)
    if safe:
        shutil.copytree(candidate / 'screenshots', args.out / 'screenshots')
    after = fingerprints()
    check('Nine complete SQL SHA-256 financial fingerprints unchanged', before == after)
    check('Both Payments remain PENDING', sql("SELECT COUNT(*) FROM payment WHERE payment_status='PENDING';") == '2')
    check('No active screenshot/restart token remains', sql('SELECT COUNT(*) FROM simulated_qr_session WHERE active=1;') == '0')
    report.update({'beforeFinancialSha256': before, 'afterFinancialSha256': after, 'status': 'PASS'})
except Exception as error:
    report.update({'status': 'FAIL', 'failureType': type(error).__name__, 'failure': str(error)[:600]})
finally:
    if auth is not None and process is not None and process.poll() is None:
        for item in created:
            try:
                request('/payments/simulated-qr/sessions/' + item['session']['id'] + '/cancel', {}, auth['accessToken'])
            except Exception:
                pass
    stop_backend()
    for log in logs:
        log.close()
    (args.out / 'verification.json').write_text(json.dumps(report, indent=2) + '\n', encoding='ascii')
    print(json.dumps({'status': report['status'], 'checks': len(report['checks']), 'head': head}))
raise SystemExit(0 if report['status'] == 'PASS' else 1)
