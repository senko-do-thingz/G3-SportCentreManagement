"""Desktop-only Release 1.1 checks inside the existing NEW-database native harness.

Never accepts a database or server target. The caller creates the guarded fixture.
Tokens and passwords remain process-local; public output contains synthetic IDs only.
"""
import datetime
import json
import os
from pathlib import Path
import re
import uuid

BASE = 'http://127.0.0.1:15173'
API = 'http://127.0.0.1:18080/api/v1'


def exercise_release11(sql, request, out, head):
    from playwright.sync_api import sync_playwright, expect
    out = Path(out)
    fixture = json.loads((out / 'flow4-results.json').read_text())['fixture']
    password = os.environ['R11_DEMO_PASSWORD']
    report = {'sourceHead': head, 'viewport': '1440x900', 'fixture': fixture, 'checks': [], 'status': 'RUNNING'}
    pages = []

    def check(name, passed, **details):
        report['checks'].append({'name': name, 'passed': bool(passed), **details})
        if not passed:
            raise AssertionError(name)

    def login(email):
        status, body = request('/auth/login', {'email': email, 'password': password})
        check('API login for synthetic fixture actor', status == 200)
        return body['accessToken']

    try:
        check('Owned fixture only', bool(re.fullmatch(r'sportify_unify_test_[0-9]{14}(?:_[a-z0-9]+)?', sql('SELECT DB_NAME()'))))
        coach = login(fixture['coachEmail'])
        reception = login(fixture['receptionEmail'])
        manager = login('manager@sportify.com')
        member = login(fixture['memberEmail'])
        booking, session = fixture['bookingId'], fixture['sessionId']
        before_credit = sql(f"SELECT remaining_sessions FROM sport_package_registration WHERE id={fixture['registrationId']}")
        status, _ = request('/coach/training/attendance', {'bookingId': booking, 'status': 'PRESENT', 'notes': 'Release 1.1 rehearsal'}, coach, method='POST')
        check('Existing Coach attendance write succeeds', status == 201)
        status, plan = request('/coach/training/plans', {'classId': fixture['classId'], 'memberId': fixture['memberId'], 'bookingId': booking, 'title': 'Release 1.1 passing plan', 'description': None, 'steps': [{'title': 'Controlled movement', 'durationMinutes': 10, 'instructions': None}]}, coach, method='POST')
        check('Existing personal plan write succeeds', status == 201)
        status, _ = request(f"/coach/training/plans/{plan['id']}/feedback", {'rating': 4, 'comments': 'Release 1.1 saved guidance', 'strengths': 'Control', 'areasForImprovement': 'Consistency'}, coach, method='POST')
        check('Existing feedback write succeeds', status == 201)
        status, result = request('/coach/training/results', {'sessionId': session, 'memberId': fixture['memberId'], 'notes': 'Release 1.1 recorded outcome', 'scores': []}, coach, method='POST')
        check('Existing notes-only result write succeeds', status == 201)
        report['resultId'] = result['id']
        with sync_playwright() as playwright:
            browser = playwright.chromium.launch(channel='msedge', headless=True)
            report['browserVersion'] = browser.version

            def screen(email, role):
                context = browser.new_context(viewport={'width': 1440, 'height': 900})
                page = context.new_page()
                pages.append(page)
                page.set_default_timeout(30000)
                page.goto(BASE + '/login')
                page.locator('input[name=email]').fill(email)
                page.locator('input[name=password]').fill(password)
                page.get_by_role('button', name='Log in', exact=True).click()
                page.wait_for_url(re.compile('/' + role + r'(?:/|$)'))
                check(role + ' real form login', True)
                return page

            def capture(page, name):
                page.screenshot(path=str(out / f'r11-{name}-1440x900.png'), full_page=True)
                check(name + ' desktop has no document overflow', page.evaluate('document.documentElement.scrollWidth <= innerWidth'))

            rp = screen(fixture['receptionEmail'], 'reception')
            rp.goto(BASE + '/reception/support')
            rp.get_by_label('Search existing member', exact=True).fill(fixture['memberEmail'])
            rp.get_by_role('button', name='Search members', exact=True).click()
            rp.get_by_label(re.compile(r'^Member(?:\s+\*)?$')).select_option(str(fixture['memberId']))
            rp.get_by_label(re.compile(r'^Subject')).fill('Release 1.1 committee support')
            rp.get_by_label(re.compile(r'^Issue details')).fill('Confirm the training room for the next visit.')
            with rp.expect_response(lambda response: response.request.method == 'POST' and response.url.endswith('/reception/support-requests')) as saved:
                rp.get_by_role('button', name='Create request', exact=True).click()
            ticket = saved.value.json()
            check('Support UI persists real request', saved.value.status == 201 and ticket['id'] > 0)
            report['ticketId'] = ticket['id']
            expect(rp.get_by_role('heading', name='Request ' + ticket['code'], exact=True)).to_be_visible()
            for state in ['IN_PROGRESS', 'RESOLVED', 'CLOSED']:
                rp.get_by_label('Next status', exact=True).select_option(state)
                if state in ['RESOLVED', 'CLOSED']:
                    rp.get_by_label('Resolution notes', exact=True).fill('Training room confirmed with the member.')
                rp.get_by_role('button', name='Update status', exact=True).click()
                expect(rp.get_by_text('Current status: ' + state, exact=True)).to_be_visible()
            rp.reload()
            rp.get_by_label('Status filter', exact=True).select_option('CLOSED')
            rp.get_by_label('Filter member ID', exact=True).fill(str(fixture['memberId']))
            expect(rp.get_by_role('button', name='View ' + ticket['code'], exact=True)).to_be_visible()
            capture(rp, 'support')

            mp = screen('manager@sportify.com', 'manager')
            today = datetime.datetime.now(datetime.timezone(datetime.timedelta(hours=7))).date().isoformat()
            mp.goto(BASE + '/manager/reports?from=' + today + '&to=' + today)
            expect(mp.get_by_text('Members registered in range', exact=True)).to_be_visible()
            status, operations = request('/manager/reports/operations?from=' + today + '&to=' + today, token=manager)
            check('Operations API returns selected dates', status == 200 and operations['from'] == today and operations['to'] == today)
            expected_members = int(sql("SELECT COUNT(*) FROM user_account u JOIN role r ON r.id=u.role_id WHERE r.code='MEMBER' AND LOWER(u.email) NOT LIKE '%@unified-demo.invalid' AND CONVERT(date,u.created_at)=CONVERT(date,DATEADD(hour,7,SYSUTCDATETIME()))"))
            check('Operations registration count agrees with owned SQL', operations['registeredMembers'] == expected_members)
            expect(mp.get_by_text('Members registered in range', exact=True).locator('..')).to_contain_text(str(operations['registeredMembers']))
            capture(mp, 'operations')
            mp.goto(BASE + '/manager/training/attendance')
            mp.get_by_label('Class', exact=True).select_option(str(fixture['classId']))
            expect(mp.get_by_role('button', name='View ' + fixture['publicId'], exact=True)).to_be_visible()
            mp.get_by_role('button', name='View ' + fixture['publicId'], exact=True).click()
            expect(mp.get_by_role('heading', name='Attendance details - ' + fixture['publicId'], exact=True)).to_be_visible()
            capture(mp, 'attendance')
            status, overview = request(f"/manager/training/attendance/overview?from={today}&to={today}&classId={fixture['classId']}", token=manager)
            row = next(item for item in overview['sessions'] if item['id'] == session)
            check('Attendance reads confirmed SQL fixture accurately', status == 200 and row['confirmed'] == 1 and row['present'] == 1 and row['unmarked'] == 0)

            cp = screen(fixture['coachEmail'], 'coach')
            cp.goto(BASE + f"/coach/training/progress?classId={fixture['classId']}&memberId={fixture['memberId']}")
            expect(cp.get_by_text('Release 1.1 recorded outcome', exact=True)).to_be_visible()
            expect(cp.get_by_text('Release 1.1 saved guidance', exact=True)).to_be_visible()
            capture(cp, 'progress')
            up = screen(fixture['memberEmail'], 'member')
            up.goto(BASE + '/member/training/results')
            expect(up.get_by_text('Release 1.1 recorded outcome', exact=True)).to_be_visible()
            capture(up, 'member-results')
            check('Member direct Manager overview denied', request('/manager/training/attendance/overview?from=' + today + '&to=' + today, token=member)[0] == 403)
            check('Coach direct Reception support denied', request('/reception/support-requests', token=coach)[0] == 403)
            check('Anonymous Manager report denied', request('/manager/reports/operations')[0] == 401)
            check('Invalid terminal support transition rejected', request(f"/reception/support-requests/{ticket['id']}/status", {'status': 'OPEN', 'resolutionNotes': None}, reception, method='PUT')[0] == 409)
            check('Peer cannot read owner progress through self API', request('/member/training/progress', token=login(fixture['peerEmail']))[1]['sessions'] == [])
            check('All training writes and reads preserve remaining credit', sql(f"SELECT remaining_sessions FROM sport_package_registration WHERE id={fixture['registrationId']}") == before_credit)
            browser.close()
        report['status'] = 'PASS'
    except Exception as failure:
        report['status'] = 'FAIL'
        report['failure'] = type(failure).__name__ + ': ' + str(failure)[:1200]
        for index, page in enumerate(pages):
            try:
                page.screenshot(path=str(out / f'r11-failure-{index}.png'), full_page=True)
            except Exception:
                pass
        raise
    finally:
        (out / 'r11-results.json').write_text(json.dumps(report, indent=2, ensure_ascii=True), encoding='ascii')
    return {'status': report['status'], 'checks': len(report['checks']), 'evidence': 'r11-results.json'}


def verify_restart(sql, request, out):
    out = Path(out)
    report = json.loads((out / 'r11-results.json').read_text())
    fixture = report['fixture']
    status, auth = request('/auth/login', {'email': fixture['coachEmail'], 'password': os.environ['R11_DEMO_PASSWORD']})
    assert status == 200
    status, progress = request(f"/coach/training/classes/{fixture['classId']}/members/{fixture['memberId']}/progress", token=auth['accessToken'])
    assert status == 200 and any(item['resultId'] == report['resultId'] for item in progress['sessions'])
    assert sql(f"SELECT status FROM support_request WHERE id={report['ticketId']}").strip() == 'CLOSED'
    return {'status': 'PASS', 'checks': ['Owned backend restart retains Coach result', 'SQL support status remains CLOSED']}
