"""Negative controls for evidence truthfulness, geometry and SQL error handling."""
import unittest
from qr07_evidence import (
    TABLES, constraint_rejected, correlation_observation, localized_contrast,
    validate_fingerprints,
)


class EvidenceControls(unittest.TestCase):
    def test_sql_requires_every_table_and_valid_digest(self):
        values = dict.fromkeys(TABLES, 'A' * 64)
        self.assertEqual(validate_fingerprints(values), values)
        for bad in ({}, {**values, 'payment': 'SQL ERROR'}, {**values, 'invoice': ''}):
            with self.assertRaises(ValueError):
                validate_fingerprints(bad)

    def test_constraint_requires_expected_name_and_error_number(self):
        expected = 'ck_payment_simulated_consistency'
        self.assertTrue(constraint_rejected({'errorNumber': 547, 'errorMessage': expected}, expected))
        for result in ({'errorNumber': 547, 'errorMessage': 'unrelated CHECK constraint'},
                       {'errorNumber': 208, 'errorMessage': expected}, {'accepted': True}):
            self.assertFalse(constraint_rejected(result, expected))

    def test_correlation_never_fabricates_and_echo_gate_fails_closed(self):
        self.assertIsNone(correlation_observation('request', None)['serverCorrelationId'])
        self.assertEqual(correlation_observation('request', 'independent')['serverCorrelationId'], 'independent')
        self.assertEqual(correlation_observation('request', 'request', True)['serverCorrelationId'], 'request')
        for actual in (None, 'other'):
            with self.assertRaises(ValueError):
                correlation_observation('request', actual, True)

    def test_contrast_rejects_low_contrast_despite_unrelated_extrema(self):
        # Unrelated black/white pixels would give the old whole-crop method 21:1.
        original = [(170, 170, 170)] * 10 + [(0, 0, 0), (255, 255, 255)]
        bg = [(255, 255, 255)] * 12
        black = [(0, 0, 0)] * 10 + bg[-2:]
        white = [(255, 255, 255)] * 12
        self.assertFalse(localized_contrast(original, bg, black, white, 12)['passed'])
        original[:10] = [(0, 0, 0)] * 10
        self.assertTrue(localized_contrast(original, bg, black, white, 12)['passed'])

    def test_contrast_fails_closed_for_empty_mask_or_geometry(self):
        pixels = [(255, 255, 255)] * 12
        with self.assertRaises(ValueError):
            localized_contrast(pixels, pixels, pixels, pixels, 12)
        with self.assertRaises(ValueError):
            localized_contrast(pixels, [], pixels, pixels, 12)


if __name__ == '__main__':
    unittest.main()
