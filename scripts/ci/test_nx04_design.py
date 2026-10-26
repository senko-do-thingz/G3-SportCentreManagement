"""Sensitivity tests mutate real NX-04 input copies without filesystem writes."""

import copy
import hashlib
import json
import unittest
from pathlib import Path

import nx04_design_guard as guard


class Nx04DesignGuardTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.inputs = guard.load_inputs(Path(__file__).resolve().parents[2])

    def setUp(self):
        self.data = copy.copy(self.inputs)

    def edit(self, name, old, new):
        path = guard.DOCS + name
        self.assertIn(old, self.data[path], "Control must alter a real document input")
        self.data[path] = self.data[path].replace(old, new, 1)

    def rejected(self, prefix):
        diagnostics = guard.validate(self.data)
        self.assertTrue(any(d.startswith(prefix) for d in diagnostics), diagnostics)

    def test_current_review_package_is_consistent(self):
        self.assertEqual([], guard.validate(self.data))

    def test_missing_deliverable_is_rejected(self):
        del self.data[guard.DOCS + "06-data-dictionary-and-field-classification.md"]
        self.rejected("document:missing:")

    def test_broken_local_link_is_rejected(self):
        self.edit("README.md", "](03-conceptual-data-model.md)", "](missing-model.md)")
        self.rejected("document:link:")

    def test_internal_prompt_path_is_rejected(self):
        self.data[guard.DOCS + "README.md"] += "\n[Private](tasks/NX-04/prompt.md)\n"
        self.rejected("document:private-path-or-secret:")

    def test_non_ascii_punctuation_is_rejected(self):
        self.data[guard.DOCS + "README.md"] += chr(0x2014)
        self.rejected("document:ascii:")

    def test_historical_migration_edit_is_rejected(self):
        inventory = json.loads(self.data[guard.DOCS + "source-inventory.json"])
        migration = next(m for m in inventory["migrations"] if m["version"] == 16)
        self.data[migration["path"]] = self.data[migration["path"]].replace("[score] DECIMAL(5,2)", "[score] DECIMAL(9,4)")
        self.rejected("migration:fingerprint:")

    def test_new_unapproved_migration_is_rejected(self):
        self.data["backend/src/main/resources/db/migration/catalog/V34__unapproved.sql"] = "-- fixture only\n"
        self.rejected("migration:population")

    def add_reviewed_migration_fixture(self):
        """Simulate an append-only inventory review; never write SQL to disk."""
        inventory_path = guard.DOCS + "source-inventory.json"
        inventory = json.loads(self.data[inventory_path])
        version = max(m["version"] for m in inventory["migrations"]) + 1
        path = f"backend/src/main/resources/db/migration/catalog/V{version}__future_fixture.sql"
        content = "CREATE TABLE [nx04_future_fixture] ([id] BIGINT NOT NULL);\n"
        self.data[path] = content
        inventory["migrations"].append({
            "path": path,
            "version": version,
            "creates": ["[nx04_future_fixture]"],
            "drops": [],
            "sha256_lf": hashlib.sha256(content.encode()).hexdigest(),
        })
        inventory["create_occurrences"] += 1
        inventory["create_names"] = sorted(inventory["create_names"] + ["[nx04_future_fixture]"])
        self.data[inventory_path] = json.dumps(inventory)
        return path

    def test_reviewed_additive_inventory_update_is_accepted(self):
        original = json.loads(self.data[guard.DOCS + "source-inventory.json"])["migrations"]
        self.add_reviewed_migration_fixture()
        updated = json.loads(self.data[guard.DOCS + "source-inventory.json"])["migrations"]
        self.assertEqual(original, updated[:-1], "Historical inventory entries must remain unchanged")
        self.assertEqual([], guard.validate(self.data))

    def test_reviewed_addition_preserves_historical_fingerprints(self):
        self.add_reviewed_migration_fixture()
        inventory = json.loads(self.data[guard.DOCS + "source-inventory.json"])
        path = inventory["migrations"][0]["path"]
        self.data[path] += "\n-- changed historical fixture\n"
        self.rejected("migration:fingerprint:" + path)

    def test_reviewed_addition_preserves_historical_population(self):
        self.add_reviewed_migration_fixture()
        inventory = json.loads(self.data[guard.DOCS + "source-inventory.json"])
        del self.data[inventory["migrations"][0]["path"]]
        self.rejected("migration:population")

    def test_reviewed_addition_still_checks_new_fingerprint(self):
        path = self.add_reviewed_migration_fixture()
        self.data[path] += "\n-- drift after inventory update\n"
        self.rejected("migration:fingerprint:" + path)

    def test_reviewed_addition_requires_updated_table_census(self):
        self.add_reviewed_migration_fixture()
        inventory_path = guard.DOCS + "source-inventory.json"
        inventory = json.loads(self.data[inventory_path])
        inventory["create_occurrences"] -= 1
        self.data[inventory_path] = json.dumps(inventory)
        self.rejected("source:table-census")

    def test_missing_historical_migration_is_rejected(self):
        inventory = json.loads(self.data[guard.DOCS + "source-inventory.json"])
        del self.data[inventory["migrations"][0]["path"]]
        self.rejected("migration:population")

    def test_source_definition_drift_is_rejected(self):
        path = guard.DOCS + "source-inventory.json"
        data = json.loads(self.data[path])
        data["source_definitions"][0]["definition"] = "nonexistentDefinition"
        self.data[path] = json.dumps(data)
        self.rejected("source:definition:")

    def test_source_line_out_of_range_is_rejected(self):
        path = guard.DOCS + "source-inventory.json"
        data = json.loads(self.data[path])
        data["source_definitions"][0]["line"] = 999999
        self.data[path] = json.dumps(data)
        self.rejected("source:definition:")

    def test_missing_requirement_mapping_is_rejected(self):
        self.edit("01-scope-and-source-baseline.md", "F4-REQ-07", "F4-REQ-XX")
        self.rejected("traceability:requirement:07")

    def test_duplicate_case_identity_is_rejected(self):
        self.edit("11-test-specifications-and-negative-controls.md", "### NX04-TC-002", "### NX04-TC-001")
        self.rejected("traceability:case-identities")

    def test_missing_case_decision_is_rejected(self):
        self.edit("11-test-specifications-and-negative-controls.md", "Decisions: D-13.ELIGIBILITY", "Decisions: UNDECIDED")
        self.rejected("traceability:case-decision:")

    def test_missing_durable_assertion_is_rejected(self):
        self.edit("11-test-specifications-and-negative-controls.md", "Expected durable result", "Unspecified output")
        self.rejected("traceability:case-fields:")

    def test_changed_relationship_cardinality_is_rejected(self):
        self.edit("05-physical-design-options.md", "E_SESSION_PLAN o|--o{ P_PLAN_IDENTITY", "E_SESSION_PLAN ||--o{ P_PLAN_IDENTITY")
        self.rejected("model:relationships")

    def test_missing_dictionary_entity_is_rejected(self):
        self.edit("06-data-dictionary-and-field-classification.md", "P_PLAN_RECIPIENT", "UNCLASSIFIED_RECIPIENT")
        self.rejected("model:dictionary:")

    def test_false_model_approval_is_rejected(self):
        self.edit("design-contract.json", '"status": "PROPOSED"', '"status": "APPROVED"')
        self.rejected("model:approval-boundary")

    def test_missing_proposal_label_is_rejected(self):
        self.edit("03-conceptual-data-model.md", "Status: PROPOSED", "Status: READY")
        self.rejected("model:proposal-label:")

    def test_receptionist_training_grant_is_rejected(self):
        self.edit("07-ownership-rbac-and-privacy-matrix.md", "| F4-RESULT create/correct | CONDITIONAL_SCOPED_COACH | DENY | DENY |", "| F4-RESULT create/correct | CONDITIONAL_SCOPED_COACH | DENY | ALLOW |")
        self.rejected("rbac:grant:")

    def test_guest_training_grant_is_rejected(self):
        self.edit("07-ownership-rbac-and-privacy-matrix.md", "| NO_VERIFIED_ROLE_GRANT | DENY | D-02", "| NO_VERIFIED_ROLE_GRANT | ALLOW | D-02")
        self.rejected("rbac:grant:")

    def test_member_write_grant_is_rejected(self):
        self.edit("07-ownership-rbac-and-privacy-matrix.md", "| F4-RESULT create/correct | CONDITIONAL_SCOPED_COACH | DENY |", "| F4-RESULT create/correct | CONDITIONAL_SCOPED_COACH | CONDITIONAL_SELF_PROJECTION |")
        self.rejected("rbac:grant:")

    def test_false_signed_gate_is_rejected(self):
        self.edit("12-decision-dependency-and-approval-matrix.md", "| PENDING |", "| VERIFIED_APPROVED |")
        self.rejected("approval:pending-boundary")

    def test_false_runtime_gate_is_rejected(self):
        path = guard.DOCS + "09-migration-plan-and-deployment-gates.md"
        self.data[path] = self.data[path].replace("NOT_RUN_APPROVAL_BLOCKED", "EXECUTED_PASS")
        self.rejected("approval:runtime-boundary")

    def test_census_tampering_is_rejected(self):
        path = guard.DOCS + "source-inventory.json"
        data = json.loads(self.data[path])
        data["create_occurrences"] -= 1
        self.data[path] = json.dumps(data)
        self.rejected("source:table-census")


if __name__ == "__main__":
    unittest.main()
