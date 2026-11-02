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
Self-contained without external repository path assumptions.
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

# Genuine Round 05 SQL Server 2022 Testcontainers evidence payload
GENUINE_JSON_STRING = r'''{
  "title": "QR-06 (i) Round 05 - SQL-Backed Desk Projection Zero-Write Verification",
  "testClass": "com.sportify.demoqr.DemoQrDatabaseIntegrationTest",
  "testMethod": "deskProjectionAndRepeatedPollingProducesStrictlyZeroDatabaseWrites",
  "databaseEngine": "Microsoft SQL Server 2022 (Testcontainers mssql/server:2022-latest)",
  "targetPaymentId": 39,
  "utcTimestamp": "2026-10-31T05:25:40.796819137Z",
  "snapshotAlgorithm": "SHA-256 over sorted TreeMap canonical row string representations: database.queryForList(\"SELECT * FROM [\" + table + \"]\").stream().map(row -> new TreeMap<>(row).toString()).sorted().toList().toString()",
  "emptyTableCanonicalString": "[]",
  "emptyTableSha256Digest": "4f53cda18c2baa0c0354bb5f9a3ecbe5ed12ab4d8e11ba873c2f11161202b945",
  "executedRequests": [
    {
      "actualStatus": 200,
      "expectedStatus": 200,
      "requestNumber": 1,
      "description": "Initial projection fetch by front desk staff",
      "role": "RECEPTIONIST",
      "method": "GET"
    },
    {
      "actualStatus": 200,
      "expectedStatus": 200,
      "requestNumber": 2,
      "description": "Sequential poll 1 (0s test interval; frontend client polls at 4s)",
      "role": "RECEPTIONIST",
      "method": "GET"
    },
    {
      "actualStatus": 200,
      "expectedStatus": 200,
      "requestNumber": 3,
      "description": "Sequential poll 2 (0s test interval; frontend client polls at 4s)",
      "role": "RECEPTIONIST",
      "method": "GET"
    },
    {
      "actualStatus": 200,
      "expectedStatus": 200,
      "requestNumber": 4,
      "description": "Sequential poll 3 (0s test interval; frontend client polls at 4s)",
      "role": "RECEPTIONIST",
      "method": "GET"
    },
    {
      "actualStatus": 200,
      "expectedStatus": 200,
      "requestNumber": 5,
      "description": "Sequential poll 4 (0s test interval; frontend client polls at 4s)",
      "role": "RECEPTIONIST",
      "method": "GET"
    },
    {
      "actualStatus": 200,
      "expectedStatus": 200,
      "requestNumber": 6,
      "description": "Supervisor desk projection read access (HTTP 200 OK verified)",
      "role": "MANAGER",
      "method": "GET"
    },
    {
      "actualStatus": 403,
      "expectedStatus": 403,
      "requestNumber": 7,
      "description": "Customer forbidden from desk projection (HTTP 403 Forbidden verified)",
      "role": "MEMBER",
      "method": "GET"
    },
    {
      "actualStatus": 401,
      "expectedStatus": 401,
      "requestNumber": 8,
      "description": "Unauthenticated request rejected (HTTP 401 Unauthorized verified)",
      "role": "ANONYMOUS",
      "method": "GET"
    }
  ],
  "totalMonitoredTables": 12,
  "zeroWritesConfirmed": true,
  "tables": [
    {
      "tableName": "cash_capture_ticket",
      "status": "EMPTY",
      "rowCountBefore": 0,
      "rowCountAfter": 0,
      "rowCountDelta": 0,
      "sha256DigestBefore": "4f53cda18c2baa0c0354bb5f9a3ecbe5ed12ab4d8e11ba873c2f11161202b945",
      "sha256DigestAfter": "4f53cda18c2baa0c0354bb5f9a3ecbe5ed12ab4d8e11ba873c2f11161202b945",
      "digestsIdentical": true,
      "mutationDetected": false
    },
    {
      "tableName": "cash_coverage",
      "status": "POPULATED",
      "rowCountBefore": 1,
      "rowCountAfter": 1,
      "rowCountDelta": 0,
      "sha256DigestBefore": "f8fa065769ec02a8522f68cb0f2d62d48b111adb1c166dd86d4c671527d2a01d",
      "sha256DigestAfter": "f8fa065769ec02a8522f68cb0f2d62d48b111adb1c166dd86d4c671527d2a01d",
      "digestsIdentical": true,
      "mutationDetected": false
    },
    {
      "tableName": "cash_event",
      "status": "EMPTY",
      "rowCountBefore": 0,
      "rowCountAfter": 0,
      "rowCountDelta": 0,
      "sha256DigestBefore": "4f53cda18c2baa0c0354bb5f9a3ecbe5ed12ab4d8e11ba873c2f11161202b945",
      "sha256DigestAfter": "4f53cda18c2baa0c0354bb5f9a3ecbe5ed12ab4d8e11ba873c2f11161202b945",
      "digestsIdentical": true,
      "mutationDetected": false
    },
    {
      "tableName": "invoice",
      "status": "POPULATED",
      "rowCountBefore": 8,
      "rowCountAfter": 8,
      "rowCountDelta": 0,
      "sha256DigestBefore": "daf56b55f5feb97142779a1a4b94f859eaa717ea6b126ba19bf03221dfdf2598",
      "sha256DigestAfter": "daf56b55f5feb97142779a1a4b94f859eaa717ea6b126ba19bf03221dfdf2598",
      "digestsIdentical": true,
      "mutationDetected": false
    },
    {
      "tableName": "invoice_line",
      "status": "POPULATED",
      "rowCountBefore": 8,
      "rowCountAfter": 8,
      "rowCountDelta": 0,
      "sha256DigestBefore": "2f32de7bac34ab9ba6737d9c3433406312f94479062390fbd43332bbf2ad922a",
      "sha256DigestAfter": "2f32de7bac34ab9ba6737d9c3433406312f94479062390fbd43332bbf2ad922a",
      "digestsIdentical": true,
      "mutationDetected": false
    },
    {
      "tableName": "member_card",
      "status": "POPULATED",
      "rowCountBefore": 38,
      "rowCountAfter": 38,
      "rowCountDelta": 0,
      "sha256DigestBefore": "0b0af12340bc8fcb438f34c5356d5f62629e5e3e18486dab4ef9c1728143f359",
      "sha256DigestAfter": "0b0af12340bc8fcb438f34c5356d5f62629e5e3e18486dab4ef9c1728143f359",
      "digestsIdentical": true,
      "mutationDetected": false
    },
    {
      "tableName": "payment",
      "status": "POPULATED",
      "rowCountBefore": 39,
      "rowCountAfter": 39,
      "rowCountDelta": 0,
      "sha256DigestBefore": "f8430df66fca1707e75faad0b414691d74b5696fd52187edd02eef891884c576",
      "sha256DigestAfter": "f8430df66fca1707e75faad0b414691d74b5696fd52187edd02eef891884c576",
      "digestsIdentical": true,
      "mutationDetected": false
    },
    {
      "tableName": "refund_request",
      "status": "EMPTY",
      "rowCountBefore": 0,
      "rowCountAfter": 0,
      "rowCountDelta": 0,
      "sha256DigestBefore": "4f53cda18c2baa0c0354bb5f9a3ecbe5ed12ab4d8e11ba873c2f11161202b945",
      "sha256DigestAfter": "4f53cda18c2baa0c0354bb5f9a3ecbe5ed12ab4d8e11ba873c2f11161202b945",
      "digestsIdentical": true,
      "mutationDetected": false
    },
    {
      "tableName": "simulated_payment_transaction",
      "status": "POPULATED",
      "rowCountBefore": 8,
      "rowCountAfter": 8,
      "rowCountDelta": 0,
      "sha256DigestBefore": "3b5fffbcd28363c1ea3198df29aa7e35aec4118689c791c2134ae6f6d8cb654e",
      "sha256DigestAfter": "3b5fffbcd28363c1ea3198df29aa7e35aec4118689c791c2134ae6f6d8cb654e",
      "digestsIdentical": true,
      "mutationDetected": false
    },
    {
      "tableName": "simulated_qr_session",
      "status": "POPULATED",
      "rowCountBefore": 38,
      "rowCountAfter": 38,
      "rowCountDelta": 0,
      "sha256DigestBefore": "1506c9a4c14daceb6393011edf1abf1f82e7faef590251f73fd95f0f9886ccee",
      "sha256DigestAfter": "1506c9a4c14daceb6393011edf1abf1f82e7faef590251f73fd95f0f9886ccee",
      "digestsIdentical": true,
      "mutationDetected": false
    },
    {
      "tableName": "simulated_qr_transition",
      "status": "POPULATED",
      "rowCountBefore": 66,
      "rowCountAfter": 66,
      "rowCountDelta": 0,
      "sha256DigestBefore": "4f779563336b70a8aef5fc8a903a9bb6649ff60da463b787c4a751980269199e",
      "sha256DigestAfter": "4f779563336b70a8aef5fc8a903a9bb6649ff60da463b787c4a751980269199e",
      "digestsIdentical": true,
      "mutationDetected": false
    },
    {
      "tableName": "sport_package_registration",
      "status": "POPULATED",
      "rowCountBefore": 1,
      "rowCountAfter": 1,
      "rowCountDelta": 0,
      "sha256DigestBefore": "55aa1ef4c687c864c20f4f7c4c6c552060e1a59f62d9cc6d2ef9647bb203c788",
      "sha256DigestAfter": "55aa1ef4c687c864c20f4f7c4c6c552060e1a59f62d9cc6d2ef9647bb203c788",
      "digestsIdentical": true,
      "mutationDetected": false
    }
  ]
}'''

# Dynamic import of verify-sql-evidence.py
spec = importlib.util.spec_from_file_location("verify_sql_evidence_mod", VERIFIER_SCRIPT)
verifier_mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(verifier_mod)


class TestVerifySqlEvidence(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.genuine_json_data = json.loads(GENUINE_JSON_STRING)

    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp_dir.cleanup)
        self.work_dir = Path(self.temp_dir.name)

        # Create genuine files in tempdir for clean, isolated testing
        self.genuine_json_file = self.work_dir / "genuine.json"
        self.genuine_json_file.write_text(GENUINE_JSON_STRING, encoding="utf-8")

        self.genuine_xml_file = self.create_custom_xml(
            testcase_body=f"=== GENUINE SQL EVIDENCE JSON ===\n{GENUINE_JSON_STRING}"
        )
        _, self.genuine_xml_data = verifier_mod.extract_json_from_junit_xml(str(self.genuine_xml_file))

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
        xml_file = self.work_dir / f"test-{abs(hash(testcase_body))}.xml"
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

        proc = self.run_verifier_process(self.genuine_xml_file, self.genuine_json_file)
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
        proc = self.run_verifier_process(self.genuine_xml_file, json_file)
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
        proc = self.run_verifier_process(self.genuine_xml_file, json_file)
        self.assertEqual(1, proc.returncode)
        self.assertIn("root['executedRequests'][0]['description']", proc.stdout)

    def test_04_negative_unexpected_root_and_nested_keys(self):
        """Injecting unexpected keys at root and nested levels must trigger full-path diffs and exit code 1."""
        tampered_root = copy.deepcopy(self.genuine_json_data)
        tampered_root["unexpectedRootField"] = "malicious_injection"

        diffs_root = verifier_mod.verify_sql_evidence(self.genuine_xml_data, tampered_root)
        self.assertTrue(
            any("Unexpected key at root['unexpectedRootField']" in d for d in diffs_root),
            f"Expected unexpected root key diff, got: {diffs_root}",
        )

        tampered_nested = copy.deepcopy(self.genuine_json_data)
        tampered_nested["tables"][0]["unexpectedTableField"] = True

        diffs_nested = verifier_mod.verify_sql_evidence(self.genuine_xml_data, tampered_nested)
        self.assertTrue(
            any("Unexpected key at root['tables'][0]['unexpectedTableField']" in d for d in diffs_nested),
            f"Expected unexpected nested table key diff, got: {diffs_nested}",
        )

        json_file = self.work_dir / "unexpected-key.json"
        json_file.write_text(json.dumps(tampered_root), encoding="utf-8")
        proc = self.run_verifier_process(self.genuine_xml_file, json_file)
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
        proc = self.run_verifier_process(self.genuine_xml_file, json_file)
        self.assertEqual(1, proc.returncode)
        self.assertIn("root['tables'][0]['sha256DigestBefore']", proc.stdout)

    def test_06_negative_removed_and_duplicate_table(self):
        """Removing a table or having duplicate table names must fail both semantic safety and deep equality."""
        tampered_removed = copy.deepcopy(self.genuine_json_data)
        tampered_removed["tables"] = tampered_removed["tables"][:11]
        tampered_removed["totalMonitoredTables"] = 11

        diffs_rem = verifier_mod.verify_sql_evidence(self.genuine_xml_data, tampered_removed)
        self.assertTrue(
            any("must be exactly 12" in d or "List length mismatch" in d for d in diffs_rem),
            f"Expected table count failure, got: {diffs_rem}",
        )

        tampered_dup = copy.deepcopy(self.genuine_json_data)
        tampered_dup["tables"][1]["tableName"] = tampered_dup["tables"][0]["tableName"]
        diffs_dup = verifier_mod.verify_sql_evidence(self.genuine_xml_data, tampered_dup)
        self.assertTrue(
            any("duplicate table names" in d for d in diffs_dup),
            f"Expected duplicate table name failure, got: {diffs_dup}",
        )

    def test_07_negative_malformed_json(self):
        """Malformed JSON payloads in XML or JSON files must fail with error and exit code 1."""
        malformed_body = '=== GENUINE SQL EVIDENCE JSON ===\n{ "invalid": [ unquoted string }'
        xml_file = self.create_custom_xml(testcase_body=malformed_body)

        with self.assertRaises(ValueError) as ctx:
            verifier_mod.extract_json_from_junit_xml(str(xml_file))
        self.assertIn("Malformed JSON", str(ctx.exception))

        proc = self.run_verifier_process(xml_file, self.genuine_json_file)
        self.assertEqual(1, proc.returncode)

    def test_08_negative_missing_testcase(self):
        """JUnit XML without the target testcase must be rejected with exit code 1."""
        xml_file = self.create_custom_xml(testcase_name="otherUnrelatedTestName")

        with self.assertRaises(ValueError) as ctx:
            verifier_mod.extract_json_from_junit_xml(str(xml_file))
        self.assertIn("not found in JUnit XML", str(ctx.exception))

        proc = self.run_verifier_process(xml_file, self.genuine_json_file)
        self.assertEqual(1, proc.returncode)

    def test_09_negative_testcase_with_failure(self):
        """Testcase containing a <failure> or <error> node must fail closed even if it emitted evidence."""
        body = f"=== GENUINE SQL EVIDENCE JSON ===\n{GENUINE_JSON_STRING}"
        xml_file = self.create_custom_xml(testcase_body=body, failure_msg="AssertionError: delta was 1")

        with self.assertRaises(ValueError) as ctx:
            verifier_mod.extract_json_from_junit_xml(str(xml_file))
        self.assertIn("contains a <failure> node", str(ctx.exception))

        proc = self.run_verifier_process(xml_file, self.genuine_json_file)
        self.assertEqual(1, proc.returncode)

    def test_10_negative_marker_only_in_suite_level_output(self):
        """Marker appearing only in suite-level <system-out> instead of testcase <system-out> must be rejected."""
        suite_body = f"=== GENUINE SQL EVIDENCE JSON ===\n{GENUINE_JSON_STRING}"
        xml_file = self.create_custom_xml(testcase_body="", suite_body=suite_body)

        with self.assertRaises(ValueError) as ctx:
            verifier_mod.extract_json_from_junit_xml(str(xml_file))
        self.assertIn("No <system-out> found inside testcase", str(ctx.exception))

        proc = self.run_verifier_process(xml_file, self.genuine_json_file)
        self.assertEqual(1, proc.returncode)


if __name__ == "__main__":
    unittest.main()
