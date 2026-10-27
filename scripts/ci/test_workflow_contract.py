"""Verify the actual workflow wiring agrees with the tested selector and gate contracts."""
import re
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]


class WorkflowContractTest(unittest.TestCase):
    def setUp(self):
        self.ci=(ROOT/'.github/workflows/ci.yml').read_text()
        self.security=(ROOT/'.github/workflows/security.yml').read_text()
        self.compose=(ROOT/'.github/workflows/compose-smoke-test.yml').read_text()

    def job(self,name):
        return re.search(r'^  '+re.escape(name)+r':\n(.*?)(?=^  [\w-]+:|\Z)',self.ci,re.M|re.S)[1]

    def test_single_orchestrator_has_no_workflow_path_skip(self):
        triggers=self.ci.split('permissions:')[0]
        self.assertIn('  pull_request:',triggers)
        self.assertIn('    branches: [main]',triggers)
        self.assertIn('  schedule:',triggers)
        self.assertNotIn('paths:',triggers)
        for workflow in [self.security,self.compose]:
            triggers=workflow.split('permissions:')[0]
            self.assertIn('  workflow_call:',triggers)
            self.assertNotIn('  pull_request:',triggers)
            self.assertNotIn('  push:',triggers)
            self.assertNotIn('  schedule:',triggers)

    def test_all_required_children_feed_stable_always_gate(self):
        gate=self.job('ci-gate')
        self.assertIn('name: CI gate',gate)
        self.assertIn('if: always()',gate)
        actual=set(re.search(r'needs: \[([^]]+)\]',gate)[1].replace(' ','').split(','))
        self.assertEqual(actual,{'impact','conventions','migration-guard','unit-tests','integration-tests','package','frontend','security','compose-smoke'})
        self.assertIn('NEEDS_JSON: ${{ toJSON(needs) }}',gate)
        self.assertIn('POLICY_JSON: ${{ needs.impact.outputs.policy }}',gate)
        self.assertIn('python3 scripts/ci/gate.py',gate)

    def test_failed_classifier_runs_every_relevant_branch(self):
        for job,flag in [('unit-tests','backend'),('integration-tests','backend'),('frontend','frontend'),('migration-guard','backend'),('compose-smoke','compose')]:
            self.assertIn("if: ${{ !cancelled() && (needs.impact.result != 'success' || needs.impact.outputs."+flag+" == 'true') }}",self.job(job))
        self.assertIn("needs.impact.result != 'success' || needs.impact.outputs.backend == 'true'",self.job('security'))
        self.assertIn("needs.impact.result != 'success' || needs.impact.outputs.frontend == 'true'",self.job('security'))
        self.assertIn("if: ${{ !cancelled() }}",self.job('security'))

    def test_security_cannot_be_advisory_and_contains_both_languages(self):
        self.assertNotIn('continue-on-error:',self.security)
        self.assertIn('languages: java-kotlin',self.security)
        self.assertIn('languages: javascript-typescript',self.security)
        self.assertIn('run: mvn -B -ntp test-compile',self.security)
        self.assertNotIn('-DskipTests',self.security)
        self.assertIn('python3 scripts/ci/gate.py --security',self.security)
        self.assertIn('fail-on-severity: high',self.security)
        self.assertNotIn('pull_request_target:',self.ci+self.security+self.compose)

    def test_parallel_tests_same_source_artifact_and_safe_cache(self):
        self.assertEqual(re.search(r'needs: (.*)',self.job('integration-tests'))[1],'impact')
        self.assertIn('mvn -B -ntp verify -DskipUnitTests=true',self.job('integration-tests'))
        self.assertIn('verify_package.py',self.job('package'))
        self.assertNotIn('mvn ',self.job('package'))
        self.assertIn('cache-dependency-path: backend/pom.xml',self.ci)
        self.assertIn('cache-dependency-path: frontend/package-lock.json',self.ci)
        self.assertNotIn('node_modules',self.ci)

    def test_old_reruns_and_authoritative_runs_do_not_share_cancel_group(self):
        concurrency=self.ci.split('permissions:')[0]
        self.assertIn("github.event_name == 'pull_request' && github.run_attempt == 1 && format('pr-{0}'",concurrency)
        self.assertIn("format('run-{0}-attempt-{1}', github.run_id, github.run_attempt)",concurrency)
        self.assertIn("cancel-in-progress: ${{ github.event_name == 'pull_request' && github.run_attempt == 1 }}",concurrency)

    def test_remote_ci_actions_are_immutable(self):
        for workflow in [self.ci,self.security,self.compose]:
            for action in re.findall(r'uses: (\S+)',workflow):
                if not action.startswith('./'):
                    self.assertRegex(action,r'^[\w-]+/[\w/-]+@[0-9a-f]{40}$')

    def test_workflow_cancellation_has_a_native_failing_step_after_gate(self):
        for workflow in [self.job('ci-gate'), self.security.split('  security-gate:')[1]]:
            self.assertRegex(workflow,r'run: python3 scripts/ci/gate.py[^\n]*\n\s+- name: Reject workflow cancellation\n\s+if: cancelled\(\)\n\s+run: exit 1')

    def test_test_report_publishers_do_not_mask_failure_or_empty_input(self):
        for name in ['unit-tests','integration-tests']:
            job=self.job(name)
            self.assertIn('fail_on_failure: true',job)
            self.assertIn('fail_on_parse_error: true',job)
            self.assertIn('require_tests: true',job)


if __name__=='__main__':unittest.main()
