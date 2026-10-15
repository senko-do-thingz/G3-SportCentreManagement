# PR40 remediation runtime and artifact evidence

Date: 2026-10-14, Asia/Bangkok. Committed revision remains 22b762f0c2594580b8243e63c60b8974e8fbd003. This document supplements the full audit and execution table; diagnostic probes do not replace full-suite reports.

## Resolved Maven provider mechanism

Effective POM and dependency tree commands exited 0. Both test plugins resolve to 3.6.0. Project Jupiter/platform versions are 5.12.2/1.12.2. Testcontainers 1.21.4 brings JUnit 4.13.2 transitively.

Isolated ZIP diagnostic command: `mvn -B -ntp -X '-Dtest=FlywayMigrationTest' '-Dsurefire.reportsDirectory=D:/G3-SportCentreManagement/.pr40-work/candidate-debug-reports' test`. Exit 0; 1 discovered, 0 failures/errors, 1 skipped. `candidate-provider-debug-2.log` records resolution of junit-vintage-engine 5.12.2 and a compact provider classpath containing it. The test still uses JUnitPlatformProvider; simply selecting that provider does not guarantee correct tag filtering.

Final diagnostic command: `mvn -B -ntp -X '-Dtest=MembershipCardServiceImplTest' '-Dsurefire.reportsDirectory=D:/G3-SportCentreManagement/.pr40-work/final-debug-reports' test`. Exit 0; 83 tests, 0 failures/errors/skips. `final-provider-debug-2.log` records the explicit false junit property and a compact provider classpath without Vintage. The supported property shape was tested by the full 620-test run and its unit-isolation guard.

The first diagnostic commands were not correctly quoted for dotted property names in Windows PowerShell. Both exited 1 before testing with a no-plugin-found error for `.reportsDirectory=D`. Correctly quoted retries produced the evidence above. Original diagnostic logs are retained. An XML project-classpath inspection did not include the provider realm and was not used as proof of engine loading; the provider debug classpaths are the relevant evidence.

Source references: [exact 3.6.0 engine-resolution implementation](https://raw.githubusercontent.com/apache/maven-surefire/surefire-3.6.0/maven-surefire-common/src/main/java/org/apache/maven/plugin/surefire/AbstractSurefireMojo.java), [exact 3.6.0 category/tag filter implementation](https://raw.githubusercontent.com/apache/maven-surefire/surefire-3.6.0/surefire-providers/surefire-junit-platform/src/main/java/org/apache/maven/surefire/junitplatform/JUnitPlatformProvider.java).

## Working-source and policy validation

`D:/G3-SportCentreManagement/.pr40-work/validate-working.py` checks actual added lines for ASCII, checks whole new files, verifies every migration already present on origin/main has unchanged normalized content, checks unique migration versions and V22 being new above the base maximum, checks preserved historical DOCX/user PR hashes, validates every DOCX XML part and explicit page breaks, verifies deliverable existence, and runs git diff --check. Actual output is in `working-validation.log`. A source SHA256 manifest is retained in `source-sha256.json` so full-suite evidence can be attributed to the final working source.

The repository ignored all Python source with `*.py`. Two narrow `.gitignore` exceptions now make the executable CI report guard and its regressions trackable. Other Python and audit-ignore policies are preserved. The source guard conservatively recognizes current class/method integration tags and transitive class inheritance; it is not a general Java compiler or composed-annotation processor. Current manifest entries were validated against the actual project classes.

The first working-validation attempt failed because this runtime-evidence file had not yet been created. Validation was rerun after completing the deliverables. Official convention/migration scripts inspect committed revisions and are documented separately from the working-source checks.

Final working validation exited 0: 33 task files present, 413 ASCII added lines, 19 unchanged main migrations, base maximum version 21, eight explicit audit pages, and three unchanged historical/user artifact hashes. Source hashes cover 20 backend/frontend/CI source paths in `source-sha256.json`. The delivered audit package contains validated XML parts and all eight final fallback pages were inspected without clipping or overlap. The expected nonzero exit from `git check-ignore` confirms the two CI Python files are no longer ignored; it is not a test failure.

## Artifact authoring and QA

The documents skill was read, including its renderer and creation instructions. The supplied artifact-operation marker was executed successfully exactly once with create, one expected output and docx format immediately before authoring. The normal workspace dependency loader/bundled Office rendering runtime was unavailable. System Python/Node were not used to author the DOCX; .NET wrote validated OOXML. Node was used only for the supplied operation marker.

The first native builder attempt exited 1 because System.IO.Compression was not loaded, leaving an incomplete task-owned output. Loading that assembly corrected the builder; the document was recreated successfully. Initial previews revealed overlong path lines; hard wrapping was corrected and a compact final eight-page document was regenerated. All eight final page previews are visually inspected. Every page uses explicit page breaks, hard line breaks and conservative width with black Arial typography. Preview generation rejects vertical overflow. QA PNGs are retained only under `.pr40-work/audit-preview` and are not deliverables.

This is a GDI layout preview of the controlled OOXML content. Actual Word/LibreOffice pagination has not been verified, and the preview is not represented as the normal packaged-renderer result. The delivered DOCX is structurally validated and the fallback limitation is disclosed.

## External state and unexecuted requirements

Public PR REST metadata confirms open, Draft, unmerged, head 22b762f0c2594580b8243e63c60b8974e8fbd003 and base 6e82388ece8c3a2ea35ae57b684e2d3adc83f3f3. GitHub CLI requires authentication; anonymous branch protection returns 401. No protection or bypass claim is supported.

Docker is unavailable on PATH and at the normal system executable location. The user-profile DockerDesktop directory contains only a deletion remnant and no usable CLI/application, so it could not be restored in this execution context. Testcontainers confirms unavailable Docker during Failsafe. The real SQL Server suite is Blocked with 122 skipped and both integration guards exit 1. Staging and persistent migration history access were unavailable. No mocks, H2, historical CI count or proposed checklist were treated as SQL Server or final-head readiness evidence.
