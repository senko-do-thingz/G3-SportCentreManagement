"""Exercise CI shell control flow with fake Docker commands, never a Docker daemon."""
import base64
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
HELPER = ROOT / 'scripts/ci/compose-smoke.sh'

FAKE_DOCKER = r'''
import json, os, sys
from pathlib import Path
args=sys.argv[1:]
case=os.environ.get('FAKE_CASE','policy')
exit_code={'policy-255':'255','zero':'0','signal':'137','term':'143','abort':'134','oom':'137',
           'invalid-exit':'invalid','negative-exit':'-1','out-of-range':'256','exit-mismatch':'255'}.get(case,'1')
with open(os.environ['FAKE_CALLS'],'a') as out:
    out.write(json.dumps(args)+'\n')
cmd=args[0]
if cmd in ('pull','create','start'):
    if case == cmd+'-failure':
        print('unrelated command failure: password policy requirements',file=sys.stderr)
        sys.exit(125)
    if cmd == 'create':
        assert os.environ.get('MSSQL_SA_PASSWORD') == 'weak'
        assert os.environ.get('DB_PASSWORD') == 'UnitOnlyStrong123!'
        assert 'MSSQL_SA_PASSWORD=weak' not in args
        print('fixture-container')
elif cmd == 'wait':
    if case == 'wait-failure': sys.exit(125)
    print(exit_code)
elif cmd == 'logs':
    if case == 'logs-failure': sys.exit(6)
    if case == 'unrelated-exit': print('A filesystem error terminated SQL Server')
    elif case == 'generic-policy': print('Checking password policy settings')
    elif case == 'policy-infrastructure': print('Unable to set system administrator password: Password validation failed with an infrastructure error')
    elif case == 'policy-without-sa': print('Password validation failed. The password does not meet SQL Server password policy requirements because it is too short')
    else:
        print('ERROR: Unable to set system administrator password: Password validation failed. '
              'The password does not meet SQL Server password policy requirements because it is not complex enough.')
        if case == 'large-log': print('tail diagnostics\n'*30000)
elif cmd == 'inspect':
    failure_format={'inspect-failure':True,'state-inspect-failure':'.State.Status' in args[2],
                    'error-inspect-failure':'.State.Error' in args[2],
                    'env-inspect-failure':'.Config.Env' in args[2]}.get(case,False)
    if failure_format:
        print('inspect failure: weak '+os.environ['DB_PASSWORD']+' '+os.environ['JWT_SECRET'],file=sys.stderr)
        sys.exit(5)
    if '.State.Error' in args[2]:
        print('engine failure: weak '+os.environ['DB_PASSWORD']+' '+os.environ['JWT_SECRET'] if case == 'engine-error' else '')
    elif '.Config.Env' in args[2]:
        print('DB_PASSWORD='+os.environ['DB_PASSWORD'])
        print('MSSQL_SA_PASSWORD='+('different' if case == 'wrong-candidate' else 'weak'))
        if case == 'duplicate-candidate': print('MSSQL_SA_PASSWORD=weak')
    else:
        print({'oom':'exited 137 true','running':'running 0 false','timeout':'running 0 false',
               'wait-killed':'running 0 false','exit-mismatch':'exited 1 false'}.get(case,'exited '+exit_code+' false'))
elif cmd == 'rm':
    if case == 'remove-failure': sys.exit(7)
elif cmd == 'compose':
    assert os.environ.get('DB_PASSWORD')
    assert Path('deploy/.env').is_file()
    if args[-1] == 'config':
        assert not os.environ.get('IMAGE_TAG')
        if case == 'config-success': sys.exit(0)
        print('IMAGE_TAG is required' if case == 'policy' else 'unrelated Docker error',file=sys.stderr)
        sys.exit(14)
    assert os.environ.get('IMAGE_TAG')
    if case == 'compose-failure': sys.exit(8)
    print('fixture compose output')
else:
    raise AssertionError('Unexpected Docker operation: '+cmd)
'''


class ComposeSmokeTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='be07-compose-')
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        (self.root / 'deploy').mkdir()
        for name in ('compose.yaml', '.env.example'):
            shutil.copyfile(ROOT / 'deploy' / name, self.root / 'deploy' / name)
        self.bin = self.root / 'bin'
        self.bin.mkdir()
        self.calls = self.root / 'calls.jsonl'
        fake = self.root / 'docker.py'
        fake.write_text(FAKE_DOCKER, encoding='ascii')
        (self.bin / 'docker').write_text('#!/usr/bin/env bash\nexec "$FAKE_PYTHON" "$FAKE_DOCKER_SCRIPT" "$@"\n', encoding='ascii', newline='\n')
        (self.bin / 'timeout').write_text(
            '#!/usr/bin/env bash\nif [ "$FAKE_CASE" = timeout ]; then exit 124; fi\nif [ "$FAKE_CASE" = wait-killed ]; then exit 137; fi\nshift\nexec "$@"\n', encoding='ascii', newline='\n')
        for path in self.bin.iterdir():
            path.chmod(0o755)
        self.env = dict(os.environ, GITHUB_ACTIONS='true', RUNNER_TEMP=self.root.as_posix(),
                        GITHUB_ENV=(self.root / 'github.env').as_posix(), GITHUB_RUN_ID='123', GITHUB_RUN_ATTEMPT='1',
                        DB_PASSWORD='UnitOnlyStrong123!', JWT_SECRET='unit-only', IMAGE_TAG='ci',
                        FAKE_CASE='policy', FAKE_CALLS=self.calls.as_posix(),
                        FAKE_BIN=self.bin.as_posix(),
                        FAKE_PYTHON=Path(sys.executable).as_posix(), FAKE_DOCKER_SCRIPT=fake.as_posix(),
                        MSYS_NO_PATHCONV='1')
        self.env['PATH'] = str(self.bin) + os.pathsep + os.environ['PATH']

    def run_helper(self, mode, case='policy', **overrides):
        env = dict(self.env, FAKE_CASE=case, **overrides)
        return self.run_bash([HELPER.as_posix(), mode],env)

    def run_bash(self, args, env):
        # Windows Bash launchers can prepend system directories; install stubs after startup.
        result = subprocess.run(['bash', '-c', 'export PATH="$(cd "$FAKE_BIN" && pwd):$PATH"; exec "$BASH" "$@"',
                                 'compose-test', *args], cwd=self.root, env=env,
                                stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, timeout=30)
        self.output = re.sub(r'^::add-mask::.*\n', '', result.stdout, flags=re.M) + result.stderr
        self.assertNotIn('UnitOnlyStrong123!',self.output)
        self.assertNotIn('CiDiagnosticsOnly123!',self.output)
        return result

    def commands(self):
        return [json.loads(line) for line in self.calls.read_text().splitlines()] if self.calls.exists() else []

    def test_policy_rejection_requires_a_real_nonzero_exit_and_specific_diagnostic(self):
        self.assertEqual(0, self.run_helper('password-policy').returncode, self.output)
        self.assertIn('Verified:', self.output)
        self.assertNotIn('weak', self.output)
        commands = self.commands()
        self.assertEqual(['pull','create','start','wait','logs','inspect','inspect','inspect','rm'], [c[0] for c in commands])
        self.assertNotIn('run', [c[0] for c in commands])

    def test_large_logs_do_not_trigger_echo_grep_sigpipe(self):
        self.assertEqual(0, self.run_helper('password-policy','large-log').returncode, self.output)
        self.assertLess(len(self.output), 1000)

    def test_application_exit_255_with_confirmed_policy_rejection_is_not_a_docker_cli_failure(self):
        self.assertEqual(0,self.run_helper('password-policy','policy-255').returncode,self.output)
        for field in ('wait_status=0','timeout=none','state=exited','state_exit=255','oom_killed=false',
                      'wait_exit=255','policy_signature=matched','candidate_matches=1'):
            self.assertIn(field,self.output)

    def test_zero_exit_still_fails_even_with_a_policy_rejection_signature(self):
        self.assertNotEqual(0,self.run_helper('password-policy','zero').returncode)
        self.assertIn('wait_exit=0',self.output)
        self.assertIn('policy_signature=matched',self.output)

    def test_timeout_and_killed_wait_cannot_be_hidden_by_policy_logs(self):
        for case,classification in (('timeout','deadline-exceeded'),('wait-killed','wait-killed')):
            with self.subTest(case=case):
                self.assertNotEqual(0,self.run_helper('password-policy',case).returncode)
                self.assertIn('timeout='+classification,self.output)
                self.assertIn('policy_signature=matched',self.output)
                self.assertEqual('rm',self.commands()[-1][0])

    def test_oom_and_signal_termination_fail_even_with_policy_logs(self):
        for case in ('oom','signal','term','abort'):
            with self.subTest(case=case):
                self.assertNotEqual(0,self.run_helper('password-policy',case).returncode)
                self.assertIn('policy_signature=matched',self.output)
                if case == 'oom': self.assertIn('oom_killed=true',self.output)

    def test_each_inspect_failure_is_reported_redacted_and_never_passes(self):
        for case in ('inspect-failure','state-inspect-failure','error-inspect-failure','env-inspect-failure'):
            with self.subTest(case=case):
                self.assertNotEqual(0,self.run_helper('password-policy',case).returncode)
                self.assertIn('inspect failure:',self.output)
                self.assertIn('[REDACTED]',self.output)
                self.assertNotIn('weak',self.output)
                self.assertNotIn('unit-only',self.output)
                self.assertEqual('rm',self.commands()[-1][0])

    def test_only_explicit_sa_complexity_rejection_satisfies_the_signature(self):
        for case in ('unrelated-exit','generic-policy','policy-infrastructure','policy-without-sa'):
            with self.subTest(case=case):
                self.assertNotEqual(0,self.run_helper('password-policy',case).returncode)
                self.assertIn('policy_signature=missing',self.output)

    def test_wrong_or_duplicate_candidate_and_mismatching_exit_evidence_fail(self):
        for case in ('wrong-candidate','duplicate-candidate','exit-mismatch','negative-exit','out-of-range'):
            with self.subTest(case=case):
                self.assertNotEqual(0,self.run_helper('password-policy',case).returncode)
                self.assertNotIn('weak',self.output)

    def test_engine_error_field_is_redacted_before_failure_output(self):
        self.assertNotEqual(0,self.run_helper('password-policy','engine-error').returncode)
        self.assertIn('engine_error=engine',self.output)
        self.assertNotIn('weak',self.output)
        self.assertNotIn('unit-only',self.output)

    def test_diagnostic_redaction_handles_all_credentials_as_literal_strings(self):
        password='fixture[.*]/&$!9'
        jwt='jwt[.*]/&$!9'
        (self.root/'sportify-password-policy.log').write_text('weak '+password+' '+jwt+'\n',encoding='ascii')
        self.assertEqual(0,self.run_helper('diagnostics',DB_PASSWORD=password,JWT_SECRET=jwt).returncode,self.output)
        self.assertNotIn(password,self.output)
        self.assertNotIn(jwt,self.output)
        self.assertNotIn('weak',self.output)
        self.assertIn('[REDACTED] [REDACTED] [REDACTED]',self.output)

    def test_infrastructure_timeout_and_unrelated_failures_never_count_as_policy_success(self):
        cases=('pull-failure','create-failure','start-failure','wait-failure','timeout','zero','signal',
               'invalid-exit','oom','running','engine-error','unrelated-exit','generic-policy','logs-failure','remove-failure')
        for case in cases:
            with self.subTest(case=case):
                self.calls.unlink(missing_ok=True)
                self.assertNotEqual(0, self.run_helper('password-policy',case).returncode)
                commands=[c[0] for c in self.commands()]
                if case not in ('pull-failure','create-failure'):
                    self.assertEqual('rm',commands[-1])
                self.assertNotIn('weak', self.output)

    def test_diagnostics_work_without_env_file_or_password_and_redact_policy_log(self):
        (self.root / 'sportify-password-policy.log').write_text('candidate password: weak\n',encoding='ascii')
        self.assertEqual(0,self.run_helper('diagnostics',DB_PASSWORD='',IMAGE_TAG='').returncode,self.output)
        self.assertIn('[REDACTED]',self.output)
        self.assertNotIn('weak',self.output)
        self.assertFalse((self.root/'deploy/.env').exists())
        self.assertEqual('compose',self.commands()[0][0])

    def test_diagnostics_accept_invalid_nonempty_password_for_parsing_only(self):
        self.assertEqual(0,self.run_helper('diagnostics',DB_PASSWORD='weak').returncode,self.output)
        self.assertNotIn('weak',self.output)
        self.assertFalse((self.root/'deploy/.env').exists())

    def test_diagnostics_preserve_an_existing_test_environment(self):
        path=self.root/'deploy/.env'
        path.write_text('fixture environment\n')
        self.assertEqual(0,self.run_helper('diagnostics').returncode,self.output)
        self.assertEqual('fixture environment\n',path.read_text())

    def test_diagnostic_failure_is_not_marked_successful_and_temporary_file_is_removed(self):
        self.assertEqual(8,self.run_helper('diagnostics','compose-failure',DB_PASSWORD='').returncode)
        self.assertFalse((self.root/'deploy/.env').exists())

    def test_cleanup_works_without_environment_and_propagates_real_failures(self):
        for case,expected in (('policy',0),('compose-failure',8)):
            with self.subTest(case=case):
                self.assertEqual(expected,self.run_helper('cleanup',case,DB_PASSWORD='',IMAGE_TAG='').returncode)
                self.assertFalse((self.root/'deploy/.env').exists())

    def test_prepare_masks_generated_credentials_and_exports_matching_values(self):
        result=self.run_helper('prepare')
        self.assertEqual(0,result.returncode,self.output)
        exported=dict(line.split('=',1) for line in (self.root/'github.env').read_text().splitlines())
        content=(self.root/'deploy/.env').read_text()
        password=exported['DB_PASSWORD']
        self.assertRegex(password,r'^Ci[0-9a-f]{48}!9$')
        self.assertEqual(32,len(base64.b64decode(exported['JWT_SECRET'])))
        self.assertEqual(['::add-mask::'+password,'::add-mask::'+exported['JWT_SECRET']],result.stdout.splitlines())
        self.assertIn('DB_PASSWORD='+password,content)
        self.assertIn('JWT_SECRET='+exported['JWT_SECRET'],content)
        self.assertNotIn(password,self.output)
        self.assertNotIn(exported['JWT_SECRET'],self.output)
        active=dict(line.split('=',1) for line in content.splitlines() if line and not line.startswith('#'))
        self.assertEqual(password,active['DB_PASSWORD'])
        self.assertEqual(exported['JWT_SECRET'],active['JWT_SECRET'])
        self.assertEqual('UnitOnlyStrong123!',self.env['DB_PASSWORD'])
        self.assertEqual([],self.commands())

    def test_prepare_does_not_overwrite_an_existing_environment(self):
        path=self.root/'deploy/.env'
        path.write_text('existing\n')
        self.assertNotEqual(0,self.run_helper('prepare').returncode)
        self.assertEqual('existing\n',path.read_text())

    def test_helper_rejects_non_ci_invocation(self):
        self.assertEqual(2,self.run_helper('cleanup',GITHUB_ACTIONS='false').returncode)
        self.assertEqual([],self.commands())

    def test_missing_image_tag_assertion_rejects_other_errors_and_unexpected_success(self):
        workflow=(ROOT/'.github/workflows/compose-smoke-test.yml').read_text()
        block=workflow.split('name: Assert compose config requires IMAGE_TAG\n        run: |\n',1)[1].split('\n      - name:',1)[0]
        script='\n'.join(line[10:] for line in block.splitlines())
        (self.root/'deploy/.env').write_text('fixture environment\n')
        for case,expected in (('policy',0),('config-error',1),('config-success',1)):
            with self.subTest(case=case):
                env=dict(self.env,FAKE_CASE=case)
                self.assertEqual(expected,self.run_bash(['-e','-o','pipefail','-c',script],env).returncode,self.output)
                self.assertTrue((self.root/'deploy/.env').exists())

    def test_workflow_preserves_security_checks_and_prepares_env_before_negatives(self):
        workflow=(ROOT/'.github/workflows/compose-smoke-test.yml').read_text()
        self.assertLess(workflow.index('name: Create temporary test .env'),workflow.index('name: Assert compose config requires IMAGE_TAG'))
        self.assertLess(workflow.index('name: Create temporary test .env'),workflow.index('name: Assert password policy violation'))
        self.assertIn('if: failure()\n        run: bash scripts/ci/compose-smoke.sh diagnostics',workflow)
        self.assertIn('if: always()\n        run: bash scripts/ci/compose-smoke.sh cleanup',workflow)
        self.assertNotIn('continue-on-error',workflow)
        self.assertNotIn('TestPass1234!StrongSA',workflow)
        for check in ('Assert non-sa DB_USERNAME','Assert unedited JWT_SECRET','Assert port 1433','Assert Flyway migrations'):
            self.assertIn(check,workflow)
        self.assertIn('getMoreResults', (ROOT/'backend/src/test/java/com/sportify/core/config/SqlServerScriptReplay.java').read_text())

    def test_pinned_image_and_compose_requirements_match_the_real_deployment(self):
        compose=(ROOT/'deploy/compose.yaml').read_text()
        helper=HELPER.read_text()
        image=re.search(r'^policy_image=(.+)$',helper,re.M)[1]
        self.assertIn('image: '+image,compose)
        self.assertEqual(2,compose.count('${DB_PASSWORD:?DB_PASSWORD is required}'))
        self.assertIn('${IMAGE_TAG:?IMAGE_TAG is required}',compose)
        self.assertNotIn('db/demo',(ROOT/'backend/src/main/resources/application.yml').read_text())
        self.assertNotIn('db/demo',(ROOT/'backend/src/main/resources/application-prod.yml').read_text())


if __name__ == '__main__':
    unittest.main()
