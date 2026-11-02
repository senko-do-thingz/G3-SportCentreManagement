#!/usr/bin/env python3
"""
Automated strict verifier and extractor for QR-06 SQL integration test evidence.
Extracts genuine JSON emitted by DemoQrDatabaseIntegrationTest from JUnit XML system-out
and performs strict recursive whole-document deep equality and semantic safety assertions.

Usage:
    python scripts/verify-sql-evidence.py --junit-xml <path> --json <path> [--extract-to <path>]
"""

import argparse
import json
import re
import sys
import xml.etree.ElementTree as ET
from typing import Any, Dict, List, Tuple


def extract_json_from_junit_xml(
    xml_path: str,
    test_method_name: str = "deskProjectionAndRepeatedPollingProducesStrictlyZeroDatabaseWrites"
) -> Tuple[str, Dict[str, Any]]:
    """
    Extracts the genuine evidence JSON strictly from the designated testcase system-out.
    Fails closed if the testcase is missing, duplicated, failed, errored, skipped,
    lacks system-out, or has invalid/missing/duplicate markers.
    Never inspects or falls back to suite-root system-out.
    """
    try:
        tree = ET.parse(xml_path)
    except Exception as ex:
        raise ValueError(f"Malformed or unparseable JUnit XML ({xml_path}): {ex}")

    root = tree.getroot()

    # Locate strictly matching testcases
    matching_testcases = []
    for tc in root.findall(".//testcase"):
        name = tc.get("name", "")
        if name == test_method_name:
            matching_testcases.append(tc)

    if len(matching_testcases) == 0:
        raise ValueError(f"Test case '{test_method_name}' not found in JUnit XML: {xml_path}")

    if len(matching_testcases) > 1:
        raise ValueError(
            f"Ambiguous JUnit XML: found {len(matching_testcases)} duplicate testcases with name '{test_method_name}' in: {xml_path}"
        )

    tc = matching_testcases[0]

    # Reject if testcase contains failure, error, or skipped elements
    failure_elem = tc.find("failure")
    if failure_elem is not None:
        msg = failure_elem.get("message", failure_elem.text or "unspecified failure")
        raise ValueError(f"Test case '{test_method_name}' contains a <failure> node in {xml_path}: {msg}")

    error_elem = tc.find("error")
    if error_elem is not None:
        msg = error_elem.get("message", error_elem.text or "unspecified error")
        raise ValueError(f"Test case '{test_method_name}' contains an <error> node in {xml_path}: {msg}")

    skipped_elem = tc.find("skipped")
    if skipped_elem is not None:
        raise ValueError(f"Test case '{test_method_name}' was skipped in {xml_path}")

    # Extract strictly from this testcase's own <system-out>
    sys_out = tc.find("system-out")
    if sys_out is None or not sys_out.text or not sys_out.text.strip():
        raise ValueError(f"No <system-out> found inside testcase '{test_method_name}' in: {xml_path}")

    raw_system_out = sys_out.text

    marker = "=== GENUINE SQL EVIDENCE JSON ==="
    marker_count = raw_system_out.count(marker)
    if marker_count == 0:
        raise ValueError(f"Marker '{marker}' not found in testcase <system-out> of: {xml_path}")
    if marker_count > 1:
        raise ValueError(f"Multiple ({marker_count}) evidence markers found in testcase <system-out> of: {xml_path}")

    json_part = raw_system_out.split(marker)[1].strip()
    first_brace = json_part.find("{")
    if first_brace == -1:
        raise ValueError("No JSON object starting brace '{' found after marker")

    depth = 0
    end_index = -1
    for idx, ch in enumerate(json_part[first_brace:], start=first_brace):
        if ch == "{":
            depth += 1
        elif ch == "}":
            depth -= 1
            if depth == 0:
                end_index = idx + 1
                break

    if end_index == -1:
        raise ValueError("Could not find balanced closing brace '}' for JSON object")

    clean_json_str = json_part[first_brace:end_index]

    try:
        parsed_json = json.loads(clean_json_str)
    except Exception as ex:
        raise ValueError(f"Malformed JSON extracted from testcase <system-out> in {xml_path}: {ex}")

    return clean_json_str, parsed_json


def deep_compare(actual: Any, submitted: Any, path: str = "root") -> List[str]:
    """
    Unconditionally enforces whole-document recursive deep equality for every JSON
    object key and array value, providing full-path diagnostics.
    """
    differences = []

    # 1. Type check
    if type(actual) is not type(submitted):
        return [
            f"Type mismatch at {path}: expected {type(actual).__name__}, got {type(submitted).__name__} (XML={actual!r} vs JSON={submitted!r})"
        ]

    # 2. Dictionary check
    if isinstance(actual, dict):
        actual_keys = set(actual.keys())
        submitted_keys = set(submitted.keys())

        missing_keys = sorted(actual_keys - submitted_keys)
        unexpected_keys = sorted(submitted_keys - actual_keys)

        for k in missing_keys:
            differences.append(
                f"Missing key at {path}['{k}']: expected in submitted JSON, present in XML with value {actual[k]!r}"
            )
        for k in unexpected_keys:
            differences.append(
                f"Unexpected key at {path}['{k}']: not present in XML, found in submitted JSON with value {submitted[k]!r}"
            )

        common_keys = sorted(actual_keys & submitted_keys)
        for k in common_keys:
            differences.extend(deep_compare(actual[k], submitted[k], f"{path}['{k}']"))

    # 3. List check
    elif isinstance(actual, list):
        if len(actual) != len(submitted):
            differences.append(f"List length mismatch at {path}: XML length={len(actual)} vs JSON length={len(submitted)}")

        min_len = min(len(actual), len(submitted))
        for idx in range(min_len):
            differences.extend(deep_compare(actual[idx], submitted[idx], f"{path}[{idx}]"))

        if len(submitted) > len(actual):
            for idx in range(len(actual), len(submitted)):
                differences.append(f"Unexpected list item at {path}[{idx}]: JSON has extra item {submitted[idx]!r}")
        elif len(actual) > len(submitted):
            for idx in range(len(submitted), len(actual)):
                differences.append(f"Missing list item at {path}[{idx}]: XML has item {actual[idx]!r} missing in JSON")

    # 4. Primitives
    else:
        if actual != submitted:
            differences.append(f"Value mismatch at {path}: XML={actual!r} vs JSON={submitted!r}")

    return differences


def validate_semantic_safety(data: Dict[str, Any], source_label: str = "Evidence") -> List[str]:
    """
    Validates mandatory semantic invariants: zeroWritesConfirmed is True,
    12 monitored tables with 0 delta and valid 64-char SHA-256 digests,
    and executed requests matching expected HTTP responses and role boundaries.
    """
    diffs = []
    sha256_pattern = re.compile(r"^[0-9a-f]{64}$")
    empty_digest = "4f53cda18c2baa0c0354bb5f9a3ecbe5ed12ab4d8e11ba873c2f11161202b945"

    # 1. zeroWritesConfirmed boolean
    if data.get("zeroWritesConfirmed") is not True:
        diffs.append(f"{source_label} 'zeroWritesConfirmed' is not boolean True (value: {data.get('zeroWritesConfirmed')!r})")

    # 2. Table count & uniqueness
    tables = data.get("tables")
    if not isinstance(tables, list):
        diffs.append(f"{source_label} 'tables' is not a list")
        return diffs

    total_tables = data.get("totalMonitoredTables")
    if total_tables != 12:
        diffs.append(f"{source_label} 'totalMonitoredTables' is {total_tables} (must be exactly 12)")
    if len(tables) != 12:
        diffs.append(f"{source_label} tables list length is {len(tables)} (must be exactly 12)")

    table_names = [t.get("tableName") for t in tables if isinstance(t, dict)]
    if len(set(table_names)) != len(table_names):
        diffs.append(f"{source_label} contains duplicate table names: {table_names}")

    # 3. Table entries verification
    for t in tables:
        if not isinstance(t, dict):
            diffs.append(f"{source_label} table entry is not an object: {t!r}")
            continue

        name = t.get("tableName", "<unknown>")
        b_count = t.get("rowCountBefore")
        a_count = t.get("rowCountAfter")
        delta = t.get("rowCountDelta")
        b_digest = t.get("sha256DigestBefore", "")
        a_digest = t.get("sha256DigestAfter", "")
        status = t.get("status")
        identical = t.get("digestsIdentical")
        mutation = t.get("mutationDetected")

        if delta != 0:
            diffs.append(f"{source_label} table '{name}' rowCountDelta={delta} (must be 0)")
        if b_count != a_count:
            diffs.append(f"{source_label} table '{name}' rowCountBefore ({b_count}) != rowCountAfter ({a_count})")
        if b_digest != a_digest:
            diffs.append(f"{source_label} table '{name}' sha256DigestBefore != sha256DigestAfter")
        if identical is not True:
            diffs.append(f"{source_label} table '{name}' digestsIdentical is not True")
        if mutation is not False:
            diffs.append(f"{source_label} table '{name}' mutationDetected is not False")

        # Hash validity
        if not sha256_pattern.match(b_digest):
            diffs.append(f"{source_label} table '{name}' invalid sha256DigestBefore: {b_digest!r}")
        if not sha256_pattern.match(a_digest):
            diffs.append(f"{source_label} table '{name}' invalid sha256DigestAfter: {a_digest!r}")

        # Empty vs Populated
        if b_count == 0:
            if status != "EMPTY":
                diffs.append(f"{source_label} table '{name}' with 0 rows has status '{status}' (expected EMPTY)")
            if b_digest != empty_digest:
                diffs.append(f"{source_label} empty table '{name}' digest {b_digest!r} != expected {empty_digest!r}")
        else:
            if status != "POPULATED":
                diffs.append(f"{source_label} table '{name}' with {b_count} rows has status '{status}' (expected POPULATED)")

    # 4. Executed requests verification
    requests = data.get("executedRequests")
    if not isinstance(requests, list) or len(requests) == 0:
        diffs.append(f"{source_label} 'executedRequests' is missing or empty")
    else:
        for idx, req in enumerate(requests):
            if not isinstance(req, dict):
                diffs.append(f"{source_label} request #{idx} is not an object")
                continue
            act_st = req.get("actualStatus")
            exp_st = req.get("expectedStatus")
            role = req.get("role")
            if act_st != exp_st:
                diffs.append(f"{source_label} request #{idx} actualStatus ({act_st}) != expectedStatus ({exp_st})")
            if role == "RECEPTIONIST" and exp_st != 200:
                diffs.append(f"{source_label} RECEPTIONIST request #{idx} expectedStatus={exp_st} (must be 200)")
            elif role == "MANAGER" and exp_st != 200:
                diffs.append(f"{source_label} MANAGER request #{idx} expectedStatus={exp_st} (must be 200)")
            elif role == "MEMBER" and exp_st != 403:
                diffs.append(f"{source_label} MEMBER request #{idx} expectedStatus={exp_st} (must be 403)")
            elif role == "ANONYMOUS" and exp_st != 401:
                diffs.append(f"{source_label} ANONYMOUS request #{idx} expectedStatus={exp_st} (must be 401)")

    return diffs


def verify_sql_evidence(actual_data: Dict[str, Any], submitted_data: Dict[str, Any]) -> List[str]:
    """
    Performs complete evidence verification:
    1. Semantic safety on XML-extracted actual data
    2. Semantic safety on submitted JSON data
    3. Unconditional whole-document recursive deep equality
    """
    diffs = []
    diffs.extend(validate_semantic_safety(actual_data, "XML-extracted evidence"))
    diffs.extend(validate_semantic_safety(submitted_data, "Submitted JSON evidence"))
    diffs.extend(deep_compare(actual_data, submitted_data, "root"))
    return diffs


def main():
    parser = argparse.ArgumentParser(description="Verify and extract genuine SQL evidence from JUnit XML.")
    parser.add_argument("--junit-xml", required=True, help="Path to failsafe JUnit XML test report")
    parser.add_argument("--json", required=True, help="Path to exported SQL evidence JSON")
    parser.add_argument("--extract-to", help="Optional path to write genuinely extracted JSON directly")
    args = parser.parse_args()

    print(f"[VERIFIER] Reading JUnit XML from: {args.junit_xml}")
    try:
        clean_json_str, actual_data = extract_json_from_junit_xml(args.junit_xml)
    except Exception as ex:
        print(f"[VERIFIER] FAIL: JUnit XML extraction failed: {ex}")
        sys.exit(1)

    if args.extract_to:
        print(f"[VERIFIER] Writing extracted genuine JSON to: {args.extract_to}")
        with open(args.extract_to, "w", encoding="utf-8") as f:
            f.write(json.dumps(actual_data, indent=2))

    print(f"[VERIFIER] Reading submitted evidence JSON from: {args.json}")
    try:
        with open(args.json, "r", encoding="utf-8") as f:
            submitted_data = json.load(f)
    except Exception as ex:
        print(f"[VERIFIER] FAIL: Could not parse submitted JSON file: {ex}")
        sys.exit(1)

    print("[VERIFIER] Performing strict recursive deep equality and semantic validation...")
    diffs = verify_sql_evidence(actual_data, submitted_data)

    if diffs:
        print(f"[VERIFIER] FAIL: {len(diffs)} discrepancy(ies) detected:")
        for d in diffs:
            print(f"  - {d}")
        sys.exit(1)

    print("[VERIFIER] PASS: 100% whole-document recursive deep equality and semantic safety verified!")
    print(f"  Target Payment ID: {actual_data.get('targetPaymentId')}")
    print(f"  UTC Timestamp:     {actual_data.get('utcTimestamp')}")
    print(f"  Monitored Tables:  {actual_data.get('totalMonitoredTables')} tables, all 0 delta, zero writes confirmed.")
    sys.exit(0)


if __name__ == "__main__":
    main()
