#!/usr/bin/env python3
"""
Automated verifier and extractor for QR-06 SQL integration test evidence.
Extracts genuine JSON emitted by DemoQrDatabaseIntegrationTest from JUnit XML system-out
and performs deep assertions against the submitted evidence JSON.

Usage:
    python scripts/verify-sql-evidence.py --junit-xml <path> --json <path> [--extract-to <path>]
"""

import argparse
import json
import sys
import xml.etree.ElementTree as ET
from typing import Any, Dict, List, Tuple


def extract_json_from_junit_xml(xml_path: str, test_method_name: str = "deskProjectionAndRepeatedPollingProducesStrictlyZeroDatabaseWrites") -> Tuple[str, Dict[str, Any]]:
    tree = ET.parse(xml_path)
    root = tree.getroot()

    found_testcase = False
    raw_system_out = None

    for tc in root.findall(".//testcase"):
        name = tc.get("name", "")
        if test_method_name in name:
            found_testcase = True
            sys_out = tc.find("system-out")
            if sys_out is not None and sys_out.text:
                raw_system_out = sys_out.text
                break

    # If not found inside specific testcase, check root system-out
    if not raw_system_out:
        sys_out = root.find("system-out")
        if sys_out is not None and sys_out.text:
            raw_system_out = sys_out.text

    if not found_testcase and not raw_system_out:
        raise ValueError(f"Test case '{test_method_name}' not found in JUnit XML: {xml_path}")

    if not raw_system_out:
        raise ValueError(f"No <system-out> found for test '{test_method_name}' in: {xml_path}")

    marker = "=== GENUINE SQL EVIDENCE JSON ==="
    if marker not in raw_system_out:
        raise ValueError(f"Marker '{marker}' not found in test output of {xml_path}")

    json_str = raw_system_out.split(marker)[1].strip()
    
    # In case there is text after the JSON closing brace:
    first_brace = json_str.find("{")
    if first_brace == -1:
        raise ValueError("No JSON object starting brace '{' found after marker")
    
    depth = 0
    end_index = -1
    for idx, ch in enumerate(json_str[first_brace:], start=first_brace):
        if ch == "{":
            depth += 1
        elif ch == "}":
            depth -= 1
            if depth == 0:
                end_index = idx + 1
                break

    if end_index == -1:
        raise ValueError("Could not find balanced closing brace '}' for JSON object")

    clean_json_str = json_str[first_brace:end_index]
    parsed_json = json.loads(clean_json_str)
    return clean_json_str, parsed_json


def verify_deep_equality(actual: Dict[str, Any], submitted: Dict[str, Any]) -> List[str]:
    differences = []

    # 1. Scalar metadata fields
    for field in ["title", "testClass", "testMethod", "databaseEngine", "targetPaymentId",
                  "utcTimestamp", "emptyTableCanonicalString", "emptyTableSha256Digest",
                  "totalMonitoredTables", "zeroWritesConfirmed"]:
        act_val = actual.get(field)
        sub_val = submitted.get(field)
        if act_val != sub_val:
            differences.append(f"Field mismatch '{field}': XML={act_val!r} vs JSON={sub_val!r}")

    # 2. Verify zeroWritesConfirmed is strictly True
    if not actual.get("zeroWritesConfirmed"):
        differences.append("Actual XML evidence indicates zeroWritesConfirmed is NOT True")
    if not submitted.get("zeroWritesConfirmed"):
        differences.append("Submitted JSON evidence indicates zeroWritesConfirmed is NOT True")

    # 3. Monitored tables verification (12 tables required)
    act_tables = {t["tableName"]: t for t in actual.get("tables", [])}
    sub_tables = {t["tableName"]: t for t in submitted.get("tables", [])}

    if len(act_tables) != 12:
        differences.append(f"Actual monitored tables count is {len(act_tables)} (expected 12)")
    if len(sub_tables) != 12:
        differences.append(f"Submitted monitored tables count is {len(sub_tables)} (expected 12)")

    for t_name, a_tab in act_tables.items():
        if t_name not in sub_tables:
            differences.append(f"Table '{t_name}' missing in submitted JSON")
            continue
        s_tab = sub_tables[t_name]

        # Verify row counts and deltas
        if a_tab.get("rowCountBefore") != s_tab.get("rowCountBefore"):
            differences.append(f"Table '{t_name}' rowCountBefore: XML={a_tab.get('rowCountBefore')} vs JSON={s_tab.get('rowCountBefore')}")
        if a_tab.get("rowCountAfter") != s_tab.get("rowCountAfter"):
            differences.append(f"Table '{t_name}' rowCountAfter: XML={a_tab.get('rowCountAfter')} vs JSON={s_tab.get('rowCountAfter')}")
        if s_tab.get("rowCountDelta") != 0:
            differences.append(f"Table '{t_name}' rowCountDelta is {s_tab.get('rowCountDelta')} (must be 0)")
        if a_tab.get("status") != s_tab.get("status"):
            differences.append(f"Table '{t_name}' status: XML={a_tab.get('status')} vs JSON={s_tab.get('status')}")

        # Verify digests
        if a_tab.get("sha256DigestBefore") != s_tab.get("sha256DigestBefore"):
            differences.append(f"Table '{t_name}' sha256DigestBefore: XML={a_tab.get('sha256DigestBefore')} vs JSON={s_tab.get('sha256DigestBefore')}")
        if a_tab.get("sha256DigestAfter") != s_tab.get("sha256DigestAfter"):
            differences.append(f"Table '{t_name}' sha256DigestAfter: XML={a_tab.get('sha256DigestAfter')} vs JSON={s_tab.get('sha256DigestAfter')}")
        if not s_tab.get("digestsIdentical"):
            differences.append(f"Table '{t_name}' digestsIdentical is False")
        if s_tab.get("mutationDetected"):
            differences.append(f"Table '{t_name}' mutationDetected is True")

        # Empty table digest check
        if s_tab.get("rowCountBefore") == 0 and s_tab.get("sha256DigestBefore") != "4f53cda18c2baa0c0354bb5f9a3ecbe5ed12ab4d8e11ba873c2f11161202b945":
            differences.append(f"Table '{t_name}' is EMPTY but has invalid digest: {s_tab.get('sha256DigestBefore')}")

    # 4. Executed requests verification
    act_reqs = actual.get("executedRequests", [])
    sub_reqs = submitted.get("executedRequests", [])
    if len(act_reqs) != len(sub_reqs):
        differences.append(f"executedRequests count mismatch: XML={len(act_reqs)} vs JSON={len(sub_reqs)}")
    else:
        for idx, (a_req, s_req) in enumerate(zip(act_reqs, sub_reqs)):
            for r_key in ["requestNumber", "role", "method", "expectedStatus", "actualStatus"]:
                if a_req.get(r_key) != s_req.get(r_key):
                    differences.append(f"Request {idx+1} '{r_key}' mismatch: XML={a_req.get(r_key)} vs JSON={s_req.get(r_key)}")

    return differences


def main():
    parser = argparse.ArgumentParser(description="Verify and extract genuine SQL evidence from JUnit XML.")
    parser.add_argument("--junit-xml", required=True, help="Path to failsafe JUnit XML test report")
    parser.add_argument("--json", required=True, help="Path to exported SQL evidence JSON")
    parser.add_argument("--extract-to", help="Optional path to write genuinely extracted JSON directly")
    args = parser.parse_args()

    print(f"[VERIFIER] Reading JUnit XML from: {args.junit_xml}")
    clean_json_str, actual_data = extract_json_from_junit_xml(args.junit_xml)

    if args.extract_to:
        print(f"[VERIFIER] Writing extracted genuine JSON to: {args.extract_to}")
        with open(args.extract_to, "w", encoding="utf-8") as f:
            f.write(json.dumps(actual_data, indent=2))

    print(f"[VERIFIER] Reading submitted evidence JSON from: {args.json}")
    with open(args.json, "r", encoding="utf-8") as f:
        submitted_data = json.load(f)

    print("[VERIFIER] Performing deep equality verification...")
    diffs = verify_deep_equality(actual_data, submitted_data)

    if diffs:
        print(f"[VERIFIER] FAIL: {len(diffs)} discrepancy(ies) detected between JUnit XML and submitted JSON:")
        for d in diffs:
            print(f"  - {d}")
        sys.exit(1)

    print("[VERIFIER] PASS: 100% deep equality verified between JUnit XML system-out and evidence JSON!")
    print(f"  Target Payment ID: {actual_data.get('targetPaymentId')}")
    print(f"  UTC Timestamp:     {actual_data.get('utcTimestamp')}")
    print(f"  Monitored Tables:  {actual_data.get('totalMonitoredTables')} tables, all 0 delta, zero writes confirmed.")
    sys.exit(0)


if __name__ == "__main__":
    main()
