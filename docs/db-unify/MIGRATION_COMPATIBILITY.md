# Migration compatibility

All applied migrations are unchanged from origin/main. Source CRC32 values below match every installed canonical disposable Flyway checksum. Protected application histories remain UNKNOWN; they were not queried.

| Version | Script | Flyway checksum | Evidence |
| --- | --- | --- | --- |
| 1 | V1__init_identity_schema.sql | 2025122897 | PASS - real SQL |
| 2 | V2__add_identity_tables.sql | -1182473504 | PASS - real SQL |
| 3 | V3__seed_identity_data.sql | 1471043296 | PASS - real SQL |
| 4 | V4__seed_manager_account.sql | 951747070 | PASS - real SQL |
| 5 | V5__create_catalog_membership_tables.sql | 1514441517 | PASS - real SQL |
| 6 | V6__seed_catalog_data.sql | -476752737 | PASS - real SQL |
| 9 | V9__add_registration_sequence.sql | -2051775082 | PASS - real SQL |
| 10 | V10__context_refresh_schema.sql | -2119898608 | PASS - real SQL |
| 11 | V11__add_refunded_status.sql | 39121240 | PASS - real SQL |
| 12 | V12__align_sport_package_seed_with_figma.sql | -966972013 | PASS - real SQL |
| 13 | V13__add_session_minutes_to_sport_package.sql | -2024906459 | PASS - real SQL |
| 14 | V14__create_payment_and_invoice_tables.sql | -826734179 | PASS - real SQL |
| 15 | V15__create_class_waitlist_table.sql | -1712348220 | PASS - real SQL |
| 16 | V16__create_training_and_attendance_tables.sql | 2108495125 | PASS - real SQL |
| 17 | V17__create_support_and_ai_tables.sql | 1414556433 | PASS - real SQL |
| 18 | V18__create_workout_recommendation_tables.sql | 610567315 | PASS - real SQL |
| 19 | V19__create_notification_and_system_tables.sql | 1024188442 | PASS - real SQL |
| 20 | V20__align_schema_with_topic.sql | -1122409067 | PASS - real SQL |
| 21 | V21__payment_pending_adjustments.sql | 491783546 | PASS - real SQL |
| 22 | V22__add_upgrade_tracking_to_member_card.sql | 284064891 | PASS - real SQL |
| 23 | V23__add_session_cancellation_and_schedule_indexes.sql | -1591383818 | PASS - real SQL |
| 26 | V26__add_front_desk_queue_indexes.sql | -1490461577 | PASS - real SQL |
| 27 | V27__refund_request_payment_link.sql | -620687452 | PASS - real SQL |
| 28 | V28__seed_demo_accounts.sql | -1698370043 | Optional demo seed; canonical excluded |
| 29 | V29__seed_demo_classes_and_sessions.sql | -276723321 | Optional demo seed; canonical excluded |
| 30 | V30__create_finance_report_views.sql | 1048247253 | PASS - real SQL |
| 31 | V31__cash_event_cutover.sql | 1190987890 | PASS - real SQL |
| 32 | V32__seed_remaining_sport_facilities.sql | 795627241 | PASS - real SQL |
| 33 | V33__create_user_avatar.sql | 1170645915 | PASS - real SQL |
| 34 | V34__create_simulated_qr_foundation.sql | -953565762 | PASS - real SQL |
| 35 | V35__create_simulated_qr_settlement.sql | 109106408 | PASS - real SQL |
| 36 | V36__enforce_simulated_settlement_provenance.sql | -1843895410 | PASS - real SQL |
| 37 | V37__personal_member_training.sql | 1349965976 | PASS - real SQL |
| 39 | V39__canonical_session_identity.sql | 1170118544 | PASS - real SQL |
| 40 | V40__session_rescheduling_history.sql | -613756946 | PASS - real SQL |

V38: absent from main and excluded. Any installed unapproved photo V38 or unexpected version/checksum is a preflight blocker, not a renumber/repair candidate. The synthetic incompatible-history test creates an independent SELECT-only V38 fixture through Flyway; it does not copy PR185/193. Fresh canonical applies 33 migrations through V40; optional demo adds V28/V29, resulting in 35 on a disposable seeded fixture. Pre-V39 -> V39 -> V40 tests preserve installed checksums, numeric IDs, booking/attendance/result references and initial history/revision. Repeated migration applies zero scripts. No clean/repair/baseline/outOfOrder/history edit or legacy import is used.
