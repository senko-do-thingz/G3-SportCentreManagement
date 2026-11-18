"""Executable regressions for the npm audit gate and its reviewed exception list."""
from datetime import date
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('npm_audit_gate', Path(__file__).with_name('npm_audit_gate.py'))
gate = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gate)

PICOMATCH = {'name': 'picomatch', 'severity': 'high', 'title': 'ReDoS',
             'url': 'https://github.com/advisories/GHSA-fc5q-39xc-vjpf'}
CRITICAL = {'name': 'other', 'severity': 'critical', 'title': 'RCE',
            'url': 'https://github.com/advisories/GHSA-aaaa-bbbb-cccc'}
MODERATE = {'name': 'minimatch', 'severity': 'moderate', 'title': 'DoS',
            'url': 'https://github.com/advisories/GHSA-g3hv-75wg-m249'}
EXCEPTION = {'id': 'GHSA-fc5q-39xc-vjpf', 'package': 'picomatch', 'reason': 'No fix', 'expires': '2027-01-31'}


def report(*advisories):
    vulnerabilities = {advisory['name']: {'severity': advisory['severity'], 'via': [advisory]} for advisory in advisories}
    # A dependent package only references the vulnerable package by name.
    vulnerabilities['vite'] = {'severity': 'high', 'via': ['picomatch']}
    return {'auditReportVersion': 2, 'vulnerabilities': vulnerabilities, 'metadata': {'vulnerabilities': {}}}


class NpmAuditGateTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)

    def write(self, name, value):
        path = self.root / name
        path.write_text(json.dumps(value), encoding='utf-8')
        return str(path)

    def run_gate(self, audit, exceptions):
        return gate.main([self.write('audit.json', audit), self.write('exceptions.json', exceptions)])

    def test_clean_report_passes(self):
        self.assertEqual(0, self.run_gate(report(), []))

    def test_moderate_advisory_does_not_block(self):
        self.assertEqual(0, self.run_gate(report(MODERATE), []))

    def test_unlisted_high_advisory_blocks(self):
        self.assertEqual(1, self.run_gate(report(PICOMATCH), []))

    def test_listed_high_advisory_is_accepted(self):
        self.assertEqual(0, self.run_gate(report(PICOMATCH), [EXCEPTION]))

    def test_exception_does_not_cover_other_advisories(self):
        self.assertEqual(1, self.run_gate(report(PICOMATCH, CRITICAL), [EXCEPTION]))

    def test_exception_must_match_the_package(self):
        self.assertEqual(1, self.run_gate(report(PICOMATCH), [dict(EXCEPTION, package='vite')]))

    def test_expired_exception_fails(self):
        with self.assertRaisesRegex(ValueError, 'expired'):
            gate.load_exceptions(self.write('exceptions.json', [EXCEPTION]), date(2027, 2, 1))

    def test_exception_without_reason_fails(self):
        self.assertEqual(1, self.run_gate(report(PICOMATCH), [dict(EXCEPTION, reason='')]))

    def test_npm_error_output_fails(self):
        self.assertEqual(1, self.run_gate({'error': {'code': 'ENOAUDIT'}}, []))

    def test_unexpected_report_format_fails(self):
        self.assertEqual(1, self.run_gate({'advisories': {}}, []))

    def test_unused_exception_is_reported_without_failing(self):
        accepted, rejected, unused = gate.evaluate(report(), {'GHSA-fc5q-39xc-vjpf': EXCEPTION})
        self.assertEqual(([], [], ['GHSA-fc5q-39xc-vjpf']), (accepted, rejected, unused))

    def test_repository_exception_file_is_valid(self):
        path = Path(__file__).resolve().parents[2] / 'frontend' / 'npm-audit-exceptions.json'
        self.assertTrue(gate.load_exceptions(path, date(2026, 1, 1)))


if __name__ == '__main__':
    unittest.main()
