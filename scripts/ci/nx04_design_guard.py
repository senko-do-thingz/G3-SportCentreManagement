"""Read-only validation of the NX-04 review package; no database access.

This is a bounded Markdown/source consistency guard, not a SQL, Java or Mermaid
grammar parser. Runtime policy and migration tests remain separately gated.
"""

import hashlib
import json
import re
from pathlib import Path, PurePosixPath
from urllib.parse import unquote


DOCS = "docs/nx-04/"
REQUIRED = (
    "README.md",
    "01-scope-and-source-baseline.md",
    "02-existing-schema-reuse-inventory.md",
    "03-conceptual-data-model.md",
    "04-logical-erd-and-relationships.md",
    "05-physical-design-options.md",
    "06-data-dictionary-and-field-classification.md",
    "07-ownership-rbac-and-privacy-matrix.md",
    "08-integrity-uniqueness-and-concurrency.md",
    "09-migration-plan-and-deployment-gates.md",
    "10-rollback-and-compatibility-strategy.md",
    "11-test-specifications-and-negative-controls.md",
    "12-decision-dependency-and-approval-matrix.md",
    "13-downstream-handoff-nx07-nx10.md",
    "14-review-checklist-and-signoff.md",
    "source-inventory.json",
    "design-contract.json",
)
RELATION = re.compile(r"^    (\w+ [|o}{.\-]+ \w+ : \w+)$", re.M)
FORBIDDEN = re.compile(
    r"tasks[/\\]|round[ -]reports?[/\\]|AI[_ -]Audit[_ -]Log|"
    r"NX-04-Codex-Agent-Prompt|(?:password|token|secret)\s*[:=]\s*[\"'][^\"']+[\"']",
    re.I,
)


def load_inputs(root):
    """Read tracked design inputs and source populations without modifying them."""
    inputs = {}
    for directory, patterns in (
        (root / "docs", ("*.md", "*.json")),
        (root / "backend/src/main/resources/db", ("*.sql",)),
        (root / "backend/src/main/java", ("*.java",)),
    ):
        for pattern in patterns:
            for path in directory.rglob(pattern):
                inputs[path.relative_to(root).as_posix()] = path.read_text(encoding="utf-8-sig")
    for path in (root / ".github").rglob("*"):
        if path.is_file() and path.suffix in (".yml", ".md", ""):
            inputs[path.relative_to(root).as_posix()] = path.read_text(encoding="utf-8-sig")
    for path in (root / "backend").rglob("*"):
        if path.is_file() and (path.suffix in (".xml", ".yml") or "src/test/java" in path.as_posix()):
            inputs[path.relative_to(root).as_posix()] = path.read_text(encoding="utf-8-sig")
    return inputs


def resolve_link(origin, target):
    parts = []
    for part in (PurePosixPath(origin).parent / unquote(target)).parts:
        if part == "..":
            if not parts:
                return None
            parts.pop()
        elif part != ".":
            parts.append(part)
    return "/".join(parts)


def validate(inputs):
    """Return stable diagnostics so callers can assert rejection of bad inputs."""
    errors = []
    for name in REQUIRED:
        if DOCS + name not in inputs:
            errors.append("document:missing:" + name)
    if errors:
        return errors
    docs = {name: inputs[DOCS + name] for name in REQUIRED if name.endswith(".md")}
    for name, content in docs.items():
        if not content.isascii():
            errors.append("document:ascii:" + name)
        if FORBIDDEN.search(content):
            errors.append("document:private-path-or-secret:" + name)
        for link in re.findall(r"\[[^\]]+\]\(([^)]+)\)", content):
            if re.match(r"https?://", link) or link.startswith("#"):
                continue
            target, _, anchor = link.partition("#")
            resolved = resolve_link(DOCS + name, target)
            if not resolved or resolved not in inputs:
                errors.append("document:link:" + name + ":" + link)
            elif re.fullmatch(r"L\d+", anchor):
                if not 1 <= int(anchor[1:]) <= len(inputs[resolved].splitlines()):
                    errors.append("document:line-link:" + name + ":" + link)

    try:
        inventory = json.loads(inputs[DOCS + "source-inventory.json"])
        contract = json.loads(inputs[DOCS + "design-contract.json"])
    except (ValueError, TypeError) as error:
        return errors + ["metadata:json:" + str(error)]
    if not re.fullmatch(r"[0-9a-f]{40}", inventory.get("baseline", "")):
        errors.append("source:baseline")
    if contract.get("status") != "PROPOSED" or contract.get("implementation") != "BLOCKED_PENDING_APPROVAL":
        errors.append("model:approval-boundary")

    migrations = {m["path"]: m for m in inventory["migrations"]}
    current_migrations = {
        path: content for path, content in inputs.items()
        if path.startswith("backend/src/main/resources/db/") and path.endswith(".sql")
    }
    if set(migrations) != set(current_migrations):
        errors.append("migration:population")
    versions = []
    create_names = []
    for path, migration in migrations.items():
        if path not in current_migrations:
            continue
        content = current_migrations[path]
        if hashlib.sha256(content.encode()).hexdigest() != migration["sha256_lf"]:
            errors.append("migration:fingerprint:" + path)
        version = re.search(r"/V(\d+)__", path)
        if not version or int(version[1]) != migration["version"]:
            errors.append("migration:version:" + path)
        else:
            versions.append(int(version[1]))
        sql = re.sub(r"/\*.*?\*/", "", content, flags=re.S)
        sql = re.sub(r"--[^\n]*", "", sql)
        names = re.findall(r"\bCREATE\s+TABLE\s+((?:\[?\w+\]?\.)?\[?\w+\]?)", sql, re.I)
        if names != migration["creates"]:
            errors.append("migration:declarations:" + path)
        create_names.extend(names)
    if len(versions) != len(set(versions)):
        errors.append("migration:duplicate-version")
    if len(create_names) != inventory["create_occurrences"] or sorted(set(create_names)) != inventory["create_names"]:
        errors.append("source:table-census")
    current_entities = {
        path for path, text in inputs.items()
        if path.startswith("backend/src/main/java/") and re.search(r"@Entity\b", text)
    }
    if current_entities != {entity["path"] for entity in inventory["entities"]}:
        errors.append("source:entity-census")
    source_ids = []
    for source in inventory["source_definitions"]:
        source_ids.append(source["id"])
        text = inputs.get(source["path"], "").splitlines()
        line = source["line"]
        if not 1 <= line <= len(text) or source["definition"] not in text[line - 1]:
            errors.append("source:definition:" + source["id"])
    if len(source_ids) != len(set(source_ids)):
        errors.append("source:duplicate-id")

    scope = docs["01-scope-and-source-baseline.md"]
    for number in range(1, 8):
        if f"F4-REQ-{number:02d}" not in scope:
            errors.append(f"traceability:requirement:{number:02d}")
    specs = docs["11-test-specifications-and-negative-controls.md"]
    cases = re.findall(r"^### (NX04-TC-\d{3}) (.+?)\n(.*?)(?=^### |^## |\Z)", specs, re.M | re.S)
    case_ids = [case[0] for case in cases]
    expected = [f"NX04-TC-{i:03d}" for i in range(1, contract["business_cases"] + 1)]
    if sorted(case_ids) != expected or len(case_ids) < 50:
        errors.append("traceability:case-identities")
    for case_id, title, body in cases:
        for required in ("Requirements:", "Decisions:", "Preconditions:", "Action/input:",
                         "Expected durable result", "Authorization/error implications:", "Layer:",
                         "SPECIFIED_NOT_EXECUTED", "Rationale:"):
            if required not in body:
                errors.append("traceability:case-fields:" + case_id + ":" + required)
        if not re.search(r"Decisions: D-\d{2}", body):
            errors.append("traceability:case-decision:" + case_id)
        if not re.search(r"F4-REQ-0[1-7]", body):
            errors.append("traceability:case-requirement:" + case_id)

    physical = docs["05-physical-design-options.md"]
    diagrams = []
    for name in ("03-conceptual-data-model.md", "04-logical-erd-and-relationships.md", "05-physical-design-options.md"):
        blocks = re.findall(r"```mermaid\n(.*?)```", docs[name], re.S)
        if len(blocks) != 1 or not blocks[0].startswith("erDiagram\n"):
            errors.append("model:diagram:" + name)
        diagrams.extend(blocks)
    relations = RELATION.findall(physical)
    if sorted(relations) != sorted(contract["physical_relationships"]):
        errors.append("model:relationships")
    parsed_entities = {
        node: re.findall(r"^        \w+ (\w+)", body, re.M)
        for node, body in re.findall(r"^    (P_\w+) \{\n(.*?)^    \}", physical, re.M | re.S)
    }
    if set(parsed_entities) != set(contract["entities"]):
        errors.append("model:entity-population")
    dictionary = docs["06-data-dictionary-and-field-classification.md"]
    for node, entity in contract["entities"].items():
        if entity.get("status") != "PROPOSED" or not entity.get("decisions"):
            errors.append("model:classification:" + node)
        if node not in dictionary or parsed_entities.get(node) != entity["fields"]:
            errors.append("model:dictionary:" + node)
    for name in ("03-conceptual-data-model.md", "04-logical-erd-and-relationships.md", "05-physical-design-options.md"):
        if "Status: PROPOSED" not in docs[name]:
            errors.append("model:proposal-label:" + name)

    matrix = docs["07-ownership-rbac-and-privacy-matrix.md"]
    rows = [line for line in matrix.splitlines() if line.startswith("| F4-")]
    if len(rows) != 10 or "deny-by-default" not in matrix:
        errors.append("rbac:matrix")
    for row in rows:
        cells = [c.strip() for c in row.split("|")[1:-1]]
        if len(cells) != 8:
            errors.append("rbac:shape")
            continue
        label, coach, member, receptionist, manager, admin, guest, dependencies = cells
        arrival = "existing arrival" in label
        member_read = label in (
            "F4-PLAN current/history read", "F4-RESULT current/history read",
            "F4-ATTEND participation/history read", "F4-PROGRESS own visible feedback",
        )
        valid = (
            coach == ("DENY" if arrival else "CONDITIONAL_SCOPED_COACH")
            and member == ("CONDITIONAL_SELF_PROJECTION" if member_read else "DENY")
            and receptionist == ("OBSERVED_EXISTING" if arrival else "DENY")
            and manager == ("OBSERVED_EXISTING" if arrival else "PENDING_NO_NEW_GRANT")
            and admin == "NO_VERIFIED_ROLE_GRANT" and guest == "DENY"
        )
        if not valid:
            errors.append("rbac:grant:" + label)
        if "D-" not in dependencies:
            errors.append("rbac:decisions:" + label)
    approvals = docs["12-decision-dependency-and-approval-matrix.md"]
    if "Status: BLOCKED_PENDING_APPROVAL" not in approvals or re.search(r"\|\s*VERIFIED_APPROVED\s*\|", approvals):
        errors.append("approval:pending-boundary")
    if "NOT_RUN_APPROVAL_BLOCKED" not in docs["09-migration-plan-and-deployment-gates.md"]:
        errors.append("approval:runtime-boundary")
    return errors


def main():
    root = Path(__file__).resolve().parents[2]
    errors = validate(load_inputs(root))
    for error in errors:
        print(error)
    if not errors:
        print("NX-04 static review-package validation passed; no runtime DB claim.")
    return 1 if errors else 0


if __name__ == "__main__":
    raise SystemExit(main())
