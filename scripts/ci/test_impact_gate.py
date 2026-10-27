import contextlib
import io
import json
import os
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import gate
import impact


class ImpactPolicyTest(unittest.TestCase):
    def test_path_matrix(self):
        cases = {
            'README.md': (False, False, False),
            'CHANGELOG.md': (False, False, False),
            'docs/tests/guide.md': (False, False, False),
            'frontend/src/pages/public/HomePage.tsx': (False, True, False),
            'frontend/package-lock.json': (False, True, False),
            'frontend/package.json': (False, True, False),
            'frontend/vite.config.ts': (False, True, False),
            'frontend/src/styles/home.css': (False, True, False),
            'frontend/src/api/client.ts': (True, True, True),
            'frontend/src/auth/roles.ts': (True, True, True),
            'backend/src/main/java/com/sportify/catalog/service/Service.java': (True, False, False),
            'backend/src/test/java/com/sportify/payment/CashMigrationIntegrationTest.java': (True, False, False),
            'backend/pom.xml': (True, False, True),
            'backend/src/main/resources/db/migration/catalog/V99__finance.sql': (True, True, True),
            'backend/src/main/java/com/sportify/identity/service/Security.java': (True, True, True),
            'backend/src/main/java/com/sportify/catalog/dto/Contract.java': (True, True, True),
            'backend/src/main/java/com/sportify/catalog/controller/Controller.java': (True, True, True),
            'docs/api/public.yaml': (True, True, True),
            'docs/database/README.md': (True, True, True),
            '.github/workflows/ci.yml': (True, True, True),
            '.github/CODEOWNERS': (True, True, True),
            'scripts/ci/impact.py': (True, True, True),
            'frontend/scripts/home-ui-check.py': (True, True, True),
            'deploy/compose.yaml': (True, True, True),
            'unknown/new-file': (True, True, True),
            'docs/generator.py': (True, True, True),
        }
        for path, expected in cases.items():
            for status in ['A', 'M']:
                with self.subTest(path=path, status=status):
                    self.assertEqual(tuple(impact.classify([(status, [path])])['flags'].values()), expected)

    def test_mixed_paths_union_and_unknown_fallback(self):
        changes = [('M', ['frontend/src/a.ts']), ('M', ['backend/src/test/ThingTest.java'])]
        self.assertEqual(impact.classify(changes)['flags'], dict(backend=True, frontend=True, compose=False))
        self.assertEqual(impact.classify(changes + [('A', ['unknown'])])['mode'], 'full')

    def test_deletions_renames_types_and_empty_are_full(self):
        for changes in [[], [('D', ['README.md'])], [('R100', ['docs/a.md', 'docs/b.md'])],
                        [('T', ['frontend/src/a.ts'])], [('?', ['x'])]]:
            with self.subTest(changes=changes):
                self.assertTrue(all(impact.classify(changes)['flags'].values()))

    def test_nul_paths_and_truncation(self):
        self.assertEqual(impact.parse_diff(b'R100\0docs/a.md\0docs/b.md\0'), [('R100', ['docs/a.md', 'docs/b.md'])])
        self.assertEqual(impact.parse_diff(b'A\0frontend/src/name with space.ts\0')[0][1], ['frontend/src/name with space.ts'])
        for data in [b'M\0a', b'R100\0old\0', b'M\0\xff\0']:
            with self.subTest(data=data), self.assertRaises(ValueError):
                impact.parse_diff(data)

    def test_main_schedule_dispatch_release_are_always_full(self):
        for event in ['push', 'schedule', 'workflow_dispatch', 'workflow_call', 'unknown']:
            self.assertTrue(all(impact.select(event, {}, '.')['flags'].values()))


class GitDiffTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.git('init', '-q', '-b', 'main')
        self.git('config', 'user.email', 'ci@example.invalid')
        self.git('config', 'user.name', 'CI policy test')
        self.write('README.md', 'base\n')
        self.base = self.commit()

    def git(self, *args):
        return subprocess.check_output(['git', *args], cwd=self.root, stderr=subprocess.PIPE).decode().strip()

    def write(self, path, content):
        target = self.root / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(content)

    def commit(self):
        self.git('add', '-A')
        self.git('commit', '-qm', 'Synthetic change-impact fixture')
        return self.git('rev-parse', 'HEAD')

    def policy(self, base, head):
        return impact.select('pull_request', {'pull_request': {'base': {'sha': base}, 'head': {'sha': head}}}, self.root)

    def test_old_main_branch_uses_merge_base_not_new_main_changes(self):
        self.git('checkout', '-qb', 'feature')
        self.write('frontend/src/a.ts', 'export const a=1\n')
        head = self.commit()
        self.git('checkout', 'main')
        self.write('backend/src/service.java', 'class Server {}\n')
        base = self.commit()
        policy = self.policy(base, head)
        self.assertEqual(policy['source']['merge_base'], self.base)
        self.assertEqual(policy['flags'], dict(backend=False, frontend=True, compose=False))

    def test_real_git_rename_delete_and_new_file(self):
        self.git('mv', 'README.md', 'RENAMED.md')
        renamed = self.commit()
        self.assertEqual(self.policy(self.base, renamed)['mode'], 'full')
        self.git('rm', 'RENAMED.md')
        deleted = self.commit()
        self.assertEqual(self.policy(renamed, deleted)['mode'], 'full')
        self.write('frontend/src/new.ts', 'new\n')
        added = self.commit()
        self.assertFalse(self.policy(deleted, added)['flags']['backend'])

    def test_missing_unrelated_force_push_or_unsafe_sha_rejected(self):
        with self.assertRaises(subprocess.CalledProcessError):
            self.policy(self.base, '0' * 40)
        with self.assertRaises(ValueError):
            self.policy('HEAD; echo injected', self.base)
        tree = self.git('rev-parse', 'HEAD^{tree}')
        unrelated = self.git('commit-tree', tree, '-m', 'Unrelated history')
        with self.assertRaises(subprocess.CalledProcessError):
            self.policy(self.base, unrelated)

    def test_classifier_error_emits_full_outputs_and_nonzero(self):
        event = self.root/'event.json'
        output = self.root/'output.txt'
        report = self.root/'report.json'
        event.write_text('{"pull_request":{}}')
        with patch('sys.argv', ['impact.py', '--event', 'pull_request', '--event-path', str(event), '--output', str(output), '--report', str(report)]), contextlib.redirect_stdout(io.StringIO()):
            self.assertEqual(impact.main(), 1)
        self.assertIn('backend=true', output.read_text())
        self.assertIn('frontend=true', output.read_text())
        self.assertIn('compose=true', output.read_text())
        self.assertIn('error', json.loads(report.read_text()))


class MergeGateTest(unittest.TestCase):
    def children(self, policy):
        result = {name: {'result': 'success'} for name in ['impact', 'conventions', 'security']}
        for name, flag in [('unit-tests', 'backend'), ('integration-tests', 'backend'), ('package', 'backend'),
                           ('migration-guard', 'backend'), ('frontend', 'frontend'), ('compose-smoke', 'compose')]:
            result[name] = {'result': 'success' if policy['flags'][flag] else 'skipped'}
        return result

    def test_documentation_only_intentional_skips_pass(self):
        policy = impact.classify([('M', ['README.md'])])
        self.assertEqual(len(gate.validate(policy, self.children(policy))), 9)

    def test_every_required_failure_cancellation_skip_or_missing_fails(self):
        policy = impact.full('full regression')
        for child in self.children(policy):
            for state in ['failure', 'cancelled', 'skipped', None]:
                needs = self.children(policy)
                needs[child]['result'] = state
                with self.subTest(child=child, state=state), self.assertRaises(ValueError):
                    gate.validate(policy, needs)
            needs = self.children(policy)
            del needs[child]
            with self.assertRaises(ValueError):
                gate.validate(policy, needs)

    def test_optional_failure_is_not_hidden(self):
        policy = impact.classify([('M', ['README.md'])])
        for state in ['failure', 'cancelled']:
            needs = self.children(policy)
            needs['unit-tests']['result'] = state
            with self.assertRaises(ValueError):
                gate.validate(policy, needs)

    def test_failed_classifier_or_missing_flags_never_green(self):
        for policy in [{}, {'version': 1, 'flags': {}}, {**impact.full('fallback'), 'error': 'GitError'}]:
            with self.assertRaises(ValueError):
                gate.validate(policy, {})

    def test_security_required_by_actual_impact_and_event(self):
        for backend, frontend, pr in [(True, True, True), (False, True, True), (False, False, True), (True, True, False)]:
            needs = {name: {'result': 'success' if required else 'skipped'} for name, required in [
                ('codeql', backend), ('frontend-codeql', frontend), ('dependency-review', pr)]}
            gate.validate({}, needs, True, backend, frontend, pr)
            for name in needs:
                broken = {key: dict(value) for key, value in needs.items()}
                broken[name]['result'] = 'failure'
                with self.assertRaises(ValueError):
                    gate.validate({}, broken, True, backend, frontend, pr)


if __name__ == '__main__':
    unittest.main()
