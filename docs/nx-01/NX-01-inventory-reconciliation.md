# NX-01 inventory reconciliation

Report index (`NX-01-report-index.md` - local review reference, not published). Round 2. Audited origin/main `d20833c3c16e4f7d95d12b6df21798fc528bd559`.

The primary SQL lexical tokenizer and balanced Java annotation extractor are inventory_primary.py (`evidence/inventory_primary.py` - local review reference, not published). A separately implemented regex/pattern comparator, with its own SQL comment/literal masking and Git-tracked file enumeration, is inventory_compare.py (`evidence/inventory_compare.py` - local review reference, not published). The comparator imports no producer code. Negative tests use copied standalone fixtures only.


| Population | Primary | Independent | Outcome |
| --- | --- | --- | --- |
| All SQL migration/demo files | 29 | 29 | PASS |
| Default Flyway migration files | 27 | 27 | Default location membership, not execution |
| Opt-in demo files | 2 | 2 | Supplemental, not default profile |
| CREATE TABLE identities | 62 | 62 | PASS |
| Controller files | 37 | 37 | PASS |
| Security/routing config files | 1 | 1 | PASS |
| Mapping annotation identities | 134 | 134 | PASS |
| Composed static route records | 105 | 105 | PASS |

Identity comparisons include migration/file path, declared schema or <default>, normalized table name and exact source line. API comparisons include declaration scope/name/line and composed method/path/params. Missing, extra and multiplicity differences fail the comparator; totals are only diagnostic. Current declaration and file reconciliation has no missing/extra records. Entity/table-name matches are separately inspected and never equated with applied Hibernate validation.

## Round 1 omissions


| Source | Line | Declared table | Current evidence state |
| --- | --- | --- | --- |
| backend/src/main/resources/db/migration/catalog/V31__cash_event_cutover.sql | 9 | dbo.cash_coverage | SOURCE_CONFIRMED: explicit CREATE TABLE |
| backend/src/main/resources/db/migration/catalog/V31__cash_event_cutover.sql | 18 | dbo.cash_capture_ticket | SOURCE_CONFIRMED: explicit CREATE TABLE |
| backend/src/main/resources/db/migration/catalog/V31__cash_event_cutover.sql | 28 | dbo.cash_event | SOURCE_CONFIRMED: explicit CREATE TABLE |

ROUND_1_PARSER_RESULT is 59; it is preserved as a historical parser result. Current source discovery is 62, including all three V31 names, not only cash_event. Omission list (`evidence/round1-schema-omissions.json` - local review reference, not published).

## Source object categories


| Category | Source occurrence count |
| --- | --- |
| CHECK_CONSTRAINT | 70 |
| DYNAMIC_EXEC | 6 |
| FOREIGN_KEY_REFERENCE | 99 |
| INDEX_CREATE | 54 |
| PRIMARY_KEY | 70 |
| SEQUENCE_CREATE | 11 |
| TABLE_ALTER | 30 |
| TABLE_CREATE | 62 |
| TABLE_VARIABLE_DECLARATION | 9 |
| TRIGGER_CREATE | 5 |
| UNIQUE_CONSTRAINT | 28 |
| VIEW_CREATE | 2 |

These categories are separate. Constraint occurrences can occur in ALTER statements and table-variable declarations; they are not a count of unique deployed constraints. DECLARE @name TABLE variables are excluded from the permanent CREATE TABLE population. Indexes, views, triggers and sequences are not tables.

## Exceptions and negative controls

The source SQL primary pass conservatively flagged two demo EXEC statements. Direct inspection identifies sys.sp_getapplock calls (V28:14 and V29:12), not dynamic CREATE statements. Four sp_executesql literals at V13:24/36 and V22:108/295 contain backfills/validation, not hidden table DDL; no SQL was executed. The raw warnings are retained and manually disposed in the exception register. No unresolved CREATE anchor or API mapping syntax remained in this source scope. Dynamic SQL, nested SQL block comments in the flat comparator, custom composed mappings and controller inheritance have explicit UNVERIFIED fixture outcomes and must be manually resolved if introduced.

Fresh isolated controls: 16 tests, 0 failures, 0 errors, 0 skips. Omitted/new/duplicate table or route fixtures return checker exit 1 as expected; unsupported forms return 2, while the negative test itself passes. Results (`evidence/negative-controls.json` - local review reference, not published), fixtures (`evidence/fixtures/qualified.sql` - local review reference, not published), exception register (`evidence/unsupported-and-unverified-declarations.md` - local review reference, not published).

The two discovery implementations are pragmatic source analyses, not formally complete SQL or Java parsers. Agreement and mutation controls support this pinned source population only. Unsupported future syntax requires explicit review; no live-schema completeness is certified.
