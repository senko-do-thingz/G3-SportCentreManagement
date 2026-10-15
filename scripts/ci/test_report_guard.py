"""Executable regressions for shared CI report selection and evidence guards."""
import importlib.util
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('report_guard', Path(__file__).with_name('check-test-reports.py'))
guard = importlib.util.module_from_spec(spec)
spec.loader.exec_module(guard)


class ReportGuardTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.sources = self.root / 'sources'
        self.sources.mkdir()
        (self.sources / 'Base.java').write_text('package sample; @Tag("integration") abstract class Base {}')
        (self.sources / 'FlywayMigrationTest.java').write_text('package sample; class FlywayMigrationTest extends Base {}')
        (self.sources / 'RepositoryTest.java').write_text('package sample; class RepositoryTest extends FlywayMigrationTest {}')
        (self.sources / 'UnitTest.java').write_text('package sample; class UnitTest {}')
        self.reports = self.root / 'reports'
        self.reports.mkdir()
        self.manifest = self.root / 'required.txt'
        self.manifest.write_text('# Required tests\n\n sample.RepositoryTest # inherited integration tag\n')

    def report(self, name='sample.RepositoryTest', tests=1, failures=0, errors=0, skipped=0, body=''):
        cases = ''.join(f'<testcase classname="{name}" name="case{i}">{body}</testcase>' for i in range(tests))
        (self.reports / ('TEST-' + name + '.xml')).write_text(
            f'<testsuite name="{name}" tests="{tests}" failures="{failures}" errors="{errors}" skipped="{skipped}">{cases}</testsuite>')

    def validate(self, phase='integration', reports=None):
        return guard.validate(reports or self.reports, phase, self.sources, self.manifest)

    def test_valid_required_suite(self):
        self.report()
        self.assertEqual(1, self.validate()['tests'])

    def test_valid_unit_suite(self):
        self.report('sample.UnitTest')
        self.assertEqual(1, self.validate('unit')['tests'])

    def test_missing_directory(self):
        with self.assertRaisesRegex(ValueError, 'directory'):
            self.validate(reports=self.root / 'missing')

    def test_empty_directory(self):
        with self.assertRaisesRegex(ValueError, 'No test reports'):
            self.validate()

    def test_zero_execution(self):
        self.report(tests=0)
        with self.assertRaisesRegex(ValueError, 'Zero tests'):
            self.validate()

    def test_missing_required_suite(self):
        self.report('sample.FlywayMigrationTest')
        with self.assertRaisesRegex(ValueError, 'did not execute'):
            self.validate()

    def test_invalid_manifest_entry(self):
        self.report()
        self.manifest.write_text('sample.MissingTest\n')
        with self.assertRaisesRegex(ValueError, 'project class'):
            self.validate()

    def test_blank_manifest(self):
        self.report()
        self.manifest.write_text('# comment\n\n')
        with self.assertRaisesRegex(ValueError, 'empty'):
            self.validate()

    def test_skipped_suite(self):
        self.report(skipped=1, body='<skipped/>')
        with self.assertRaisesRegex(ValueError, 'skipped'):
            self.validate()

    def test_skip_element_cannot_hide_behind_zero_counter(self):
        self.report(body='<skipped/>')
        with self.assertRaisesRegex(ValueError, 'skipped'):
            self.validate()

    def test_failures_and_errors(self):
        for key in ('failures', 'errors'):
            with self.subTest(key=key):
                self.report(**{key: 1})
                with self.assertRaises(ValueError):
                    self.validate()

    def test_inherited_tag_without_integration_suffix_leaks(self):
        self.report()
        with self.assertRaisesRegex(ValueError, 'leaked into Surefire'):
            self.validate('unit')

    def test_unit_leaks_into_failsafe(self):
        self.report('sample.UnitTest')
        with self.assertRaisesRegex(ValueError, 'leaked into Failsafe'):
            self.validate()

    def test_nested_integration_class_leaks(self):
        self.report('sample.RepositoryTest$Nested')
        with self.assertRaisesRegex(ValueError, 'leaked into Surefire'):
            self.validate('unit')

    def test_method_tag_is_conservatively_guarded(self):
        (self.sources / 'UnitTest.java').write_text('package sample; class UnitTest { @Tag("integration") void test() {} }')
        self.report('sample.UnitTest')
        with self.assertRaisesRegex(ValueError, 'leaked into Surefire'):
            self.validate('unit')


if __name__ == '__main__':
    unittest.main()
