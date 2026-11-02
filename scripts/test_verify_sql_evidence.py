#!/usr/bin/env python3
"""Convenience runner for QR-06 SQL verifier regression tests."""
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[1]
if str(REPO_ROOT) not in sys.path:
    sys.path.insert(0, str(REPO_ROOT))

import unittest
from scripts.ci.test_verify_sql_evidence import TestVerifySqlEvidence

if __name__ == "__main__":
    suite = unittest.defaultTestLoader.loadTestsFromTestCase(TestVerifySqlEvidence)
    runner = unittest.TextTestRunner(verbosity=2)
    result = runner.run(suite)
    sys.exit(0 if result.wasSuccessful() else 1)
