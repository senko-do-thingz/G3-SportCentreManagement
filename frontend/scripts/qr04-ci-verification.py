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
report = {'head': head, 'database': database, 'checks': [], 'settlementEnabled': True}
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


def sql_json(query, target=None):
    environment = os.environ.copy()
    environment['SQLCMDPASSWORD'] = password
    command = ['docker', 'exec', '-i', '-e', 'SQLCMDPASSWORD', container,
               '/opt/mssql-tools18/bin/sqlcmd', '-S', 'localhost', '-U', 'sa', '-C',
               '-d', target or database, '-l', '5', '-t', '20', '-b', '-y', '0']
    result = subprocess.run(command, input='SET NOCOUNT ON; SET QUOTED_IDENTIFIER ON; ' + query,
                            capture_output=True, text=True, timeout=30, env=environment)
    if result.returncode:
        raise RuntimeError('Dedicated CI SQL JSON command failed')
    raw = ''.join(line.strip() for line in result.stdout.splitlines() if line.strip())
    return json.loads(raw) if raw else []



def fingerprints():
    tables = ['payment', 'invoice', 'invoice_line', 'member_card', 'sport_package_registration',
              'refund_request', 'cash_event', 'cash_capture_ticket', 'cash_coverage',
              'simulated_payment_transaction']
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
    import socket
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
    check('Fresh isolated SQL migrated to V35', sql('SELECT MAX(TRY_CONVERT(int,version)) FROM flyway_schema_history WHERE success=1;') == '35')
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
        "VALUES ('QR04-CI-P02',@owner,810000,'BANK_TRANSFER','PENDING',0); DECLARE @two bigint=SCOPE_IDENTITY(); "
        "INSERT member_card(card_code,member_id,tier_id,start_date,status,price_paid,payment_id) "
        "VALUES ('QR04-CI-C02',@owner,(SELECT TOP 1 id FROM membership_card_tier ORDER BY id),CAST(SYSUTCDATETIME() AS DATE),'PENDING_PAYMENT',810000,@two); "
        "INSERT payment(payment_code,member_id,amount,payment_method,payment_status,version) "
        "VALUES ('QR04-CI-P03',@owner,510000,'BANK_TRANSFER','PENDING',0); DECLARE @three bigint=SCOPE_IDENTITY(); "
        "INSERT member_card(card_code,member_id,tier_id,start_date,status,price_paid,payment_id) "
        "VALUES ('QR04-CI-C03',@owner,(SELECT TOP 1 id FROM membership_card_tier ORDER BY id),CAST(SYSUTCDATETIME() AS DATE),'PENDING_PAYMENT',510000,@three); COMMIT;")
    payment_ids = [int(value) for value in sql("SELECT id FROM payment WHERE payment_code IN ('QR04-CI-P01','QR04-CI-P02','QR04-CI-P03') ORDER BY payment_code;").splitlines()]
    status, auth = request('/auth/login', {'email': 'owner@qr04.example.test', 'password': 'Qr04IsolatedTest2026!'})
    check('Real owner authenticates', status == 200)
    before = fingerprints()
    for payment in [payment_ids[0], payment_ids[2]]:
        status, value = request('/payments/simulated-qr/sessions', {'paymentId': payment, 'bankCode': 'BIDV_DEMO', 'requestId': str(uuid.uuid4())}, auth['accessToken'])
        check('Create durable restart fixture', status == 200)
        created.append(value)
    confirm_token = created[1]['confirmationUrl'].split('#token=')[1]
    status, settled = request('/payments/simulated-qr/sessions/confirm-intent', {'token': confirm_token}, auth['accessToken'])
    check('Confirmation settles simulated payment', status == 200 and settled['state'] == 'SETTLED' and settled.get('settlementType') == 'SIMULATED' and settled.get('settlementProvider') == 'SPORTIFY_SIMULATOR')
    status, duplicate = request('/payments/simulated-qr/sessions/confirm-intent', {'token': confirm_token}, auth['accessToken'])
    check('Duplicate confirmation is idempotent', status == 200 and duplicate['state'] == 'SETTLED' and duplicate.get('simulatedTransactionId') == settled.get('simulatedTransactionId'))
    status, cancel_after = request('/payments/simulated-qr/sessions/' + created[1]['session']['id'] + '/cancel', {}, auth['accessToken'])
    check('Cancel after SETTLED rejected with 409', status == 409)
    stop_backend()
    second_pid = start_backend()
    check('Actual JVM was replaced', first_pid != second_pid, stoppedPid=first_pid, restartedPid=second_pid)
    status, pending_view = request('/payments/simulated-qr/sessions/' + created[0]['session']['id'], bearer=auth['accessToken'])
    check('Restart preserves PENDING', status == 200 and pending_view.get('state') == 'PENDING')
    status, canceled_view = request('/payments/simulated-qr/sessions/' + created[0]['session']['id'] + '/cancel', {}, auth['accessToken'])
    check('Restart pending token canceled', status == 200 and canceled_view.get('state') == 'CANCELED')
    status, settled_view = request('/payments/simulated-qr/sessions/' + created[1]['session']['id'], bearer=auth['accessToken'])
    check('Restart preserves SETTLED and provenance', status == 200 and settled_view.get('state') == 'SETTLED' and settled_view.get('settlementType') == 'SIMULATED')
    sql('UPDATE dbo.simulated_qr_rate_limit SET create_count=0, access_count=0;')
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
                    '--axe', str(args.axe), '--payment-id', str(payment_ids[0]), '--dark-payment-id', str(payment_ids[1]),
                    '--native-https', '--tls-ca', str(args.private / 'tls-cert.pem'),
                    '--spki-pin', pin, '--host-resolver-rules', 'MAP demo.example.test 127.0.0.1,MAP api.demo.example.test 127.0.0.1']
    result = subprocess.run(browser_args, capture_output=True, text=True, timeout=300)
    browser_result = json.loads((candidate / 'browser-logs/qr04-round03-browser-results.json').read_text())
    (args.out / 'browser-results.json').write_text(json.dumps(browser_result, indent=2) + '\n', encoding='ascii')
    safe = bool(browser_result['tokenInvalidation']) and all(item['confirmedTerminal'] for item in browser_result['tokenInvalidation'])
    check('Native SQL-backed Edge browser completes', result.returncode == 0, functionalChecks=len(browser_result['checks']), axeScans=len(browser_result['accessibility']))
    check('Every screenshot token verified terminal', safe)
    if safe:
        shutil.copytree(candidate / 'screenshots', args.out / 'screenshots')
        if (candidate / 'browser-logs').is_dir():
            shutil.copytree(candidate / 'browser-logs', args.out / 'browser-logs')
    after = fingerprints()
    for cash_table in ['refund_request', 'cash_event', 'cash_capture_ticket', 'cash_coverage']:
        check('Actual cash/refund table unchanged: ' + cash_table, before[cash_table] == after[cash_table])
    check('Zero cash_event rows created', sql('SELECT COUNT(*) FROM dbo.cash_event;') == '0')
    check('Zero actual paid revenue rows in vw_paid_payment', sql('SELECT COUNT(*) FROM dbo.vw_paid_payment;') == '0')
    check('All three simulated payments settled with SIMULATED provenance',
          sql("SELECT COUNT(*) FROM dbo.payment WHERE payment_status='SUCCESS' AND settlement_type='SIMULATED' AND settlement_provider='SPORTIFY_SIMULATOR';") == '3')
    check('Three immutable simulated_payment_transaction rows committed',
          sql("SELECT COUNT(*) FROM dbo.simulated_payment_transaction WHERE status='SUCCEEDED' AND settlement_type='SIMULATED';") == '3')
    check('All three member cards activated', sql("SELECT COUNT(*) FROM dbo.member_card WHERE status='ACTIVE';") == '3')
    check('All three invoices issued as PAID', sql("SELECT COUNT(*) FROM dbo.invoice WHERE status='PAID';") == '3')
    check('No active screenshot/restart token remains', sql('SELECT COUNT(*) FROM simulated_qr_session WHERE active=1;') == '0')
    sql_snapshot = {
        'database': database,
        'beforeFinancialSha256': before,
        'afterFinancialSha256': after,
        'payments': sql_json("SELECT id, payment_code, member_id, amount, payment_method, payment_status, settlement_type, settlement_provider, simulated_transaction_id, reference_code, payment_time, confirmed_by_user_id FROM dbo.payment ORDER BY id FOR JSON PATH, INCLUDE_NULL_VALUES;"),
        'simulatedPaymentTransactions': sql_json("SELECT id, transaction_code, payment_id, session_id, member_id, amount, currency, bank_code, settlement_type, provider, status, settled_at_utc, created_at_utc FROM dbo.simulated_payment_transaction ORDER BY id FOR JSON PATH, INCLUDE_NULL_VALUES;"),
        'simulatedQrSessions': sql_json("SELECT id, payment_id, owner_id, bank_code, amount, currency, state, active, version, created_at_utc, expires_at_utc, state_changed_at_utc FROM dbo.simulated_qr_session ORDER BY created_at_utc FOR JSON PATH, INCLUDE_NULL_VALUES;"),
        'memberCards': sql_json("SELECT id, card_code, member_id, tier_id, status, price_paid, payment_id, start_date, end_date FROM dbo.member_card ORDER BY id FOR JSON PATH, INCLUDE_NULL_VALUES;"),
        'invoices': sql_json("SELECT id, invoice_number, payment_id, member_id, subtotal_amount, discount_amount, tax_amount, total_amount, status, issue_date FROM dbo.invoice ORDER BY id FOR JSON PATH, INCLUDE_NULL_VALUES;"),
        'activityLogs': sql_json("SELECT id, actor_user_id, action, entity_type, entity_id, entity_code, summary, details, created_at FROM dbo.activity_log WHERE entity_type='PAYMENT' ORDER BY id FOR JSON PATH, INCLUDE_NULL_VALUES;"),
        'actualCashEventCount': int(sql('SELECT COUNT(*) FROM dbo.cash_event;')),
        'actualCashCaptureTicketCount': int(sql('SELECT COUNT(*) FROM dbo.cash_capture_ticket;')),
        'actualPaidViewRowCount': int(sql('SELECT COUNT(*) FROM dbo.vw_paid_payment;')),
        'actualPaidViewSum': sql("SELECT CONVERT(varchar(32), COALESCE(SUM(amount), 0)) FROM dbo.vw_paid_payment;"),
        'simulatedPaidSum': sql("SELECT CONVERT(varchar(32), COALESCE(SUM(amount), 0)) FROM dbo.payment WHERE payment_status='SUCCESS' AND settlement_type='SIMULATED';"),
    }
    (args.out / 'sql-evidence.json').write_text(json.dumps(sql_snapshot, indent=2) + '\n', encoding='ascii')
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
