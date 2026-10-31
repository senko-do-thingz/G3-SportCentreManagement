#!/usr/bin/env python3
"""
Regression test suite for QR-06 SQL evidence verifier.
Validates genuine positive execution and 9 mandatory negative control categories:
  1. Genuine positive success (exit code 0, 100% deep equality)
  2. Changed snapshotAlgorithm
  3. Changed executedRequests[0].description
  4. Unexpected nested or root key
  5. Changed table digest
  6. Removed or duplicate table
  7. Malformed JSON
  8. Missing testcase in JUnit XML
  9. Testcase containing <failure> / <error> / <skipped>
 10. Marker appearing only in suite-level output (rejected)

All negative controls must fail closed (raise ValueError or exit nonzero).
"""

import copy
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

REPO_ROOT = Path(__file__).resolve().parents[2]
VERIFIER_SCRIPT = REPO_ROOT / "scripts" / "verify-sql-evidence.py"
GENUINE_XML_PATH = (
    REPO_ROOT
    / "tasks"
    / "QR-06 (i)"
    / "reports"
    / "round-05"
    / "ci-evidence"
    / "run-41172370718"
    / "failsafe-reports-integration"
    / "TEST-com.sportify.demoqr.DemoQrDatabaseIntegrationTest.xml"
)
GENUINE_JSON_PATH = (
    REPO_ROOT
    / "tasks"
    / "QR-06 (i)"
    / "reports"
    / "round-05"
    / "sql-evidence"
    / "qr06-round05-sql-evidence.json"
)

# Dynamic import of verify-sql-evidence.py
spec = importlib.util.spec_from_file_location("verify_sql_evidence_mod", VERIFIER_SCRIPT)
verifier_mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(verifier_mod)


class TestVerifySqlEvidence(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        with open(GENUINE_JSON_PATH, "r", encoding="utf-8") as f:
            cls.genuine_json_data = json.load(f)
        _, cls.genuine_xml_data = verifier_mod.extract_json_from_junit_xml(str(GENUINE_XML_PATH))

    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp_dir.cleanup)
        self.work_dir = Path(self.temp_dir.name)

    def run_verifier_process(self, xml_path: Path, json_path: Path) -> subprocess.CompletedProcess:
        cmd = [
            sys.executable,
            str(VERIFIER_SCRIPT),
            "--junit-xml",
            str(xml_path),
            "--json",
            str(json_path),
        ]
        return subprocess.run(cmd, capture_output=True, text=True)

    def create_custom_xml(
        self,
        testcase_name: str = "deskProjectionAndRepeatedPollingProducesStrictlyZeroDatabaseWrites",
        testcase_body: str = "",
        suite_body: str = "",
        failure_msg: str = None,
        error_msg: str = None,
        skipped: bool = False,
        duplicate_testcase: bool = False,
    ) -> Path:
        xml_file = self.work_dir / "custom-test.xml"
        tc_children = []
        if failure_msg:
            tc_children.append(f'<failure message="{failure_msg}">{failure_msg}</failure>')
        if error_msg:
            tc_children.append(f'<error message="{error_msg}">{error_msg}</error>')
        if skipped:
            tc_children.append("<skipped/>")
        if testcase_body:
            tc_children.append(f"<system-out><![CDATA[{testcase_body}]]></system-out>")

        tc_xml = f'<testcase classname="com.sportify.demoqr.DemoQrDatabaseIntegrationTest" name="{testcase_name}">{"".join(tc_children)}</testcase>'
        if duplicate_testcase:
            tc_xml += f'\n<testcase classname="com.sportify.demoqr.DemoQrDatabaseIntegrationTest" name="{testcase_name}">{"".join(tc_children)}</testcase>'

        suite_out = f"<system-out><![CDATA[{suite_body}]]></system-out>" if suite_body else ""

        content = f"""<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="com.sportify.demoqr.DemoQrDatabaseIntegrationTest" tests="1" failures="0" errors="0" skipped="0">
  {tc_xml}
  {suite_out}
</testsuite>
"""
        xml_file.write_text(content, encoding="utf-8")
        return xml_file

    def test_01_genuine_positive_control(self):
        """Genuine Round 05 SQL XML and JSON must verify 100% deep equality and exit 0."""
        diffs = verifier_mod.verify_sql_evidence(self.genuine_xml_data, self.genuine_json_data)
        self.assertEqual([], diffs, f"Expected zero differences in genuine evidence, got: {diffs}")

        proc = self.run_verifier_process(GENUINE_XML_PATH, GENUINE_JSON_PATH)
        self.assertEqual(0, proc.returncode, f"Verifier process failed on genuine data:\n{proc.stdout}\n{proc.stderr}")
        self.assertIn("100% whole-document recursive deep equality and semantic safety verified!", proc.stdout)

    def test_02_negative_changed_snapshot_algorithm(self):
        """Modifying snapshotAlgorithm must trigger a full-path diff and exit code 1."""
        tampered = copy.deepcopy(self.genuine_json_data)
        tampered["snapshotAlgorithm"] = "MD5 over un-sorted raw rows"

        diffs = verifier_mod.verify_sql_evidence(self.genuine_xml_data, tampered)
        self.assertTrue(any("root['snapshotAlgorithm']" in d for d in diffs), f"Expected snapshotAlgorithm diff, got: {diffs}")

        json_file = self.work_dir / "tampered-algo.json"
        json_file.write_text(json.dumps(tampered), encoding="utf-8")
        proc = self.run_verifier_process(GENUINE_XML_PATH, json_file)
        self.assertEqual(1, proc.returncode)
        self.assertIn("root['snapshotAlgorithm']", proc.stdout)

    def test_03_negative_changed_request_description(self):
        """Modifying executedRequests[0].description must trigger a full-path diff and exit code 1."""
        tampered = copy.deepcopy(self.genuine_json_data)
        tampered["executedRequests"][0]["description"] = "Tampered request description text"

        diffs = verifier_mod.verify_sql_evidence(self.genuine_xml_data, tampered)
        self.assertTrue(
            any("root['executedRequests'][0]['description']" in d for d in diffs),
            f"Expected request description diff, got: {diffs}",
        )

        json_file = self.work_dir / "tampered-desc.json"
        json_file.write_text(json.dumps(tampered), encoding="utf-8")
        proc = self.run_verifier_process(GENUINE_XML_PATH, json_file)
        self.assertEqual(1, proc.returncode)
        self.assertIn("root['executedRequests'][0]['description']", proc.stdout)

    def test_04_negative_unexpected_root_and_nested_keys(self):
        """Injecting unexpected keys at root and nested levels must trigger full-path diffs and exit code 1."""
        # 4a. Unexpected root key
        tampered_root = copy.deepcopy(self.genuine_json_data)
        tampered_root["unexpectedRootField"] = "malicious_injection"

        diffs_root = verifier_mod.verify_sql_evidence(self.genuine_xml_data, tampered_root)
        self.assertTrue(
            any("Unexpected key at root['unexpectedRootField']" in d for d in diffs_root),
            f"Expected unexpected root key diff, got: {diffs_root}",
        )

        # 4b. Unexpected nested key in table
        tampered_nested = copy.deepcopy(self.genuine_json_data)
        tampered_nested["tables"][0]["unexpectedTableField"] = True

        diffs_nested = verifier_mod.verify_sql_evidence(self.genuine_xml_data, tampered_nested)
        self.assertTrue(
            any("Unexpected key at root['tables'][0]['unexpectedTableField']" in d for d in diffs_nested),
            f"Expected unexpected nested table key diff, got: {diffs_nested}",
        )

        json_file = self.work_dir / "unexpected-key.json"
        json_file.write_text(json.dumps(tampered_root), encoding="utf-8")
        proc = self.run_verifier_process(GENUINE_XML_PATH, json_file)
        self.assertEqual(1, proc.returncode)
        self.assertIn("root['unexpectedRootField']", proc.stdout)

    def test_05_negative_changed_table_digest(self):
        """Modifying sha256DigestBefore/After of a table must trigger full-path diff and exit code 1."""
        tampered = copy.deepcopy(self.genuine_json_data)
        tampered["tables"][0]["sha256DigestBefore"] = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"

        diffs = verifier_mod.verify_sql_evidence(self.genuine_xml_data, tampered)
        self.assertTrue(
            any("root['tables'][0]['sha256DigestBefore']" in d for d in diffs),
            f"Expected table digest diff, got: {diffs}",
        )

        json_file = self.work_dir / "tampered-digest.json"
        json_file.write_text(json.dumps(tampered), encoding="utf-8")
        proc = self.run_verifier_process(GENUINE_XML_PATH, json_file)
        self.assertEqual(1, proc.returncode)
        self.assertIn("root['tables'][0]['sha256DigestBefore']", proc.stdout)

    def test_06_negative_removed_and_duplicate_table(self):
        """Removing a table or having duplicate table names must fail both semantic safety and deep equality."""
        # Removed table (11 tables instead of 12)
        tampered_removed = copy.deepcopy(self.genuine_json_data)
        tampered_removed["tables"] = tampered_removed["tables"][:11]
        tampered_removed["totalMonitoredTables"] = 11

        diffs_rem = verifier_mod.verify_sql_evidence(self.genuine_xml_data, tampered_removed)
        self.assertTrue(
            any("must be exactly 12" in d or "List length mismatch" in d for d in diffs_rem),
            f"Expected table count failure, got: {diffs_rem}",
        )

        # Duplicate table name
        tampered_dup = copy.deepcopy(self.genuine_json_data)
        tampered_dup["tables"][1]["tableName"] = tampered_dup["tables"][0]["tableName"]
        diffs_dup = verifier_mod.verify_sql_evidence(self.genuine_xml_data, tampered_dup)
        self.assertTrue(
            any("duplicate table names" in d for d in diffs_dup),
            f"Expected duplicate table name failure, got: {diffs_dup}",
        )

    def test_07_negative_malformed_json(self):
        """Malformed JSON payloads in XML or JSON files must fail with error and exit code 1."""
        malformed_body = "=== GENUINE SQL EVIDENCE JSON ===\n{ \"invalid\": [ unquoted string }"
        xml_file = self.create_custom_xml(testcase_body=malformed_body)

        with self.assertRaises(ValueError) as ctx:
            verifier_mod.extract_json_from_junit_xml(str(xml_file))
        self.assertIn("Malformed JSON", str(ctx.exception))

        proc = self.run_verifier_process(xml_file, GENUINE_JSON_PATH)
        self.assertEqual(1, proc.returncode)

    def test_08_negative_missing_testcase(self):
        """JUnit XML without the target testcase must be rejected with exit code 1."""
        xml_file = self.create_custom_xml(testcase_name="otherUnrelatedTestName")

        with self.assertRaises(ValueError) as ctx:
            verifier_mod.extract_json_from_junit_xml(str(xml_file))
        self.assertIn("not found in JUnit XML", str(ctx.exception))

        proc = self.run_verifier_process(xml_file, GENUINE_JSON_PATH)
        self.assertEqual(1, proc.returncode)

    def test_09_negative_testcase_with_failure(self):
        """Testcase containing a <failure> or <error> node must fail closed even if it emitted evidence."""
        evidence_str = json.dumps(self.genuine_json_data)
        body = f"=== GENUINE SQL EVIDENCE JSON ===\n{evidence_str}"
        xml_file = self.create_custom_xml(testcase_body=body, failure_msg="AssertionError: delta was 1")

        with self.assertRaises(ValueError) as ctx:
            verifier_mod.extract_json_from_junit_xml(str(xml_file))
        self.assertIn("contains a <failure> node", str(ctx.exception))

        proc = self.run_verifier_process(xml_file, GENUINE_JSON_PATH)
        self.assertEqual(1, proc.returncode)

    def test_10_negative_marker_only_in_suite_level_output(self):
        """Marker appearing only in suite-level <system-out> instead of testcase <system-out> must be rejected."""
        evidence_str = json.dumps(self.genuine_json_data)
        suite_body = f"=== GENUINE SQL EVIDENCE JSON ===\n{evidence_str}"
        xml_file = self.create_custom_xml(testcase_body="", suite_body=suite_body)

        with self.assertRaises(ValueError) as ctx:
            verifier_mod.extract_json_from_junit_xml(str(xml_file))
        self.assertIn("No <system-out> found inside testcase", str(ctx.exception))

        proc = self.run_verifier_process(xml_file, GENUINE_JSON_PATH)
        self.assertEqual(1, proc.returncode)


if __name__ == "__main__":
    unittest.main()
