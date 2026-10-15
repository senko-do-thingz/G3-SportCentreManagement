# PR40 verification file paths

## Files created by this review

No existing application source was edited. Existing ignored BE-03 audit logs and the pre-existing untracked docs/pr directory were preserved. The new audit file is also ignored by the repository rule and is not staged or committed.

| File | Absolute workspace path |
| --- | --- |
| docs/reviews/PR40_remediation_verification.md | D:/G3-SportCentreManagement/SWP/docs/reviews/PR40_remediation_verification.md |
| docs/tests/PR40_remediation_verification_test_cases.md | D:/G3-SportCentreManagement/SWP/docs/tests/PR40_remediation_verification_test_cases.md |
| docs/reviews/PR40_verification_modified_files_paths.md | D:/G3-SportCentreManagement/SWP/docs/reviews/PR40_verification_modified_files_paths.md |
| docs/audit/PR40_Remediation_Verification_AI_Audit_Log.docx | D:/G3-SportCentreManagement/SWP/docs/audit/PR40_Remediation_Verification_AI_Audit_Log.docx |

## Remediation archive paths compared with current workspace

| Repository path | Local comparison | Absolute intended repository path |
| --- | --- | --- |
| .github/workflows/ci.yml | different | D:/G3-SportCentreManagement/SWP/.github/workflows/ci.yml |
| scripts/ci/required-integration-tests.txt | missing | D:/G3-SportCentreManagement/SWP/scripts/ci/required-integration-tests.txt |
| backend/pom.xml | different | D:/G3-SportCentreManagement/SWP/backend/pom.xml |
| backend/src/main/resources/db/migration/catalog/V22__add_upgrade_tracking_to_member_card.sql | different | D:/G3-SportCentreManagement/SWP/backend/src/main/resources/db/migration/catalog/V22__add_upgrade_tracking_to_member_card.sql |
| backend/src/main/java/com/sportify/catalog/service/impl/MembershipCardServiceImpl.java | different | D:/G3-SportCentreManagement/SWP/backend/src/main/java/com/sportify/catalog/service/impl/MembershipCardServiceImpl.java |
| frontend/src/pages/member/cardsModel.ts | different | D:/G3-SportCentreManagement/SWP/frontend/src/pages/member/cardsModel.ts |
| docs/tests/BE-03_test_cases.md | different | D:/G3-SportCentreManagement/SWP/docs/tests/BE-03_test_cases.md |
| docs/audit/BE-03_AI_Audit_Log.md | different | D:/G3-SportCentreManagement/SWP/docs/audit/BE-03_AI_Audit_Log.md |
| CHANGELOG.md | different | D:/G3-SportCentreManagement/SWP/CHANGELOG.md |
| proposed/PR_description.md | missing | D:/G3-SportCentreManagement/SWP/proposed/PR_description.md |
| docs/audit/BE-03_AI_Audit_Log.docx | different | D:/G3-SportCentreManagement/SWP/docs/audit/BE-03_AI_Audit_Log.docx |
| PR40_modified_files_paths.md | missing | D:/G3-SportCentreManagement/SWP/PR40_modified_files_paths.md |

The archive contains 11 proposed repository artifacts plus PR40_modified_files_paths.md as a supplementary inventory. different means content differs after CRLF normalization; audit files exist locally but are not tracked.

## Current PR changed files relative to main

| Git change | Repository path | Absolute workspace path |
| --- | --- | --- |
| M | .github/workflows/ci.yml | D:/G3-SportCentreManagement/SWP/.github/workflows/ci.yml |
| M | CHANGELOG.md | D:/G3-SportCentreManagement/SWP/CHANGELOG.md |
| M | backend/pom.xml | D:/G3-SportCentreManagement/SWP/backend/pom.xml |
| A | backend/src/main/java/com/sportify/catalog/controller/ManagerCardTierController.java | D:/G3-SportCentreManagement/SWP/backend/src/main/java/com/sportify/catalog/controller/ManagerCardTierController.java |
| A | backend/src/main/java/com/sportify/catalog/controller/ManagerPackageController.java | D:/G3-SportCentreManagement/SWP/backend/src/main/java/com/sportify/catalog/controller/ManagerPackageController.java |
| M | backend/src/main/java/com/sportify/catalog/controller/MembershipCardController.java | D:/G3-SportCentreManagement/SWP/backend/src/main/java/com/sportify/catalog/controller/MembershipCardController.java |
| M | backend/src/main/java/com/sportify/catalog/controller/SportPackageController.java | D:/G3-SportCentreManagement/SWP/backend/src/main/java/com/sportify/catalog/controller/SportPackageController.java |
| M | backend/src/main/java/com/sportify/catalog/dto/CardTierResponse.java | D:/G3-SportCentreManagement/SWP/backend/src/main/java/com/sportify/catalog/dto/CardTierResponse.java |
| A | backend/src/main/java/com/sportify/catalog/dto/MembershipCardTierUpdateRequest.java | D:/G3-SportCentreManagement/SWP/backend/src/main/java/com/sportify/catalog/dto/MembershipCardTierUpdateRequest.java |
| A | backend/src/main/java/com/sportify/catalog/dto/SportPackageUpdateRequest.java | D:/G3-SportCentreManagement/SWP/backend/src/main/java/com/sportify/catalog/dto/SportPackageUpdateRequest.java |
| M | backend/src/main/java/com/sportify/catalog/entity/MemberCard.java | D:/G3-SportCentreManagement/SWP/backend/src/main/java/com/sportify/catalog/entity/MemberCard.java |
| M | backend/src/main/java/com/sportify/catalog/entity/MembershipCardTier.java | D:/G3-SportCentreManagement/SWP/backend/src/main/java/com/sportify/catalog/entity/MembershipCardTier.java |
| M | backend/src/main/java/com/sportify/catalog/repository/MembershipCardTierRepository.java | D:/G3-SportCentreManagement/SWP/backend/src/main/java/com/sportify/catalog/repository/MembershipCardTierRepository.java |
| M | backend/src/main/java/com/sportify/catalog/repository/SportPackageRepository.java | D:/G3-SportCentreManagement/SWP/backend/src/main/java/com/sportify/catalog/repository/SportPackageRepository.java |
| A | backend/src/main/java/com/sportify/catalog/repository/SportPackageSpecifications.java | D:/G3-SportCentreManagement/SWP/backend/src/main/java/com/sportify/catalog/repository/SportPackageSpecifications.java |
| M | backend/src/main/java/com/sportify/catalog/service/MembershipCardService.java | D:/G3-SportCentreManagement/SWP/backend/src/main/java/com/sportify/catalog/service/MembershipCardService.java |
| M | backend/src/main/java/com/sportify/catalog/service/SportPackageService.java | D:/G3-SportCentreManagement/SWP/backend/src/main/java/com/sportify/catalog/service/SportPackageService.java |
| M | backend/src/main/java/com/sportify/catalog/service/impl/MembershipCardServiceImpl.java | D:/G3-SportCentreManagement/SWP/backend/src/main/java/com/sportify/catalog/service/impl/MembershipCardServiceImpl.java |
| M | backend/src/main/java/com/sportify/catalog/service/impl/SportPackageServiceImpl.java | D:/G3-SportCentreManagement/SWP/backend/src/main/java/com/sportify/catalog/service/impl/SportPackageServiceImpl.java |
| M | backend/src/main/java/com/sportify/core/audit/AuditAction.java | D:/G3-SportCentreManagement/SWP/backend/src/main/java/com/sportify/core/audit/AuditAction.java |
| M | backend/src/main/java/com/sportify/core/exception/BusinessRuleException.java | D:/G3-SportCentreManagement/SWP/backend/src/main/java/com/sportify/core/exception/BusinessRuleException.java |
| A | backend/src/main/resources/db/migration/catalog/V22__add_upgrade_tracking_to_member_card.sql | D:/G3-SportCentreManagement/SWP/backend/src/main/resources/db/migration/catalog/V22__add_upgrade_tracking_to_member_card.sql |
| A | backend/src/test/java/com/sportify/catalog/controller/ManagerCardTierControllerTest.java | D:/G3-SportCentreManagement/SWP/backend/src/test/java/com/sportify/catalog/controller/ManagerCardTierControllerTest.java |
| A | backend/src/test/java/com/sportify/catalog/controller/ManagerCatalogIntegrationTest.java | D:/G3-SportCentreManagement/SWP/backend/src/test/java/com/sportify/catalog/controller/ManagerCatalogIntegrationTest.java |
| A | backend/src/test/java/com/sportify/catalog/controller/ManagerPackageControllerTest.java | D:/G3-SportCentreManagement/SWP/backend/src/test/java/com/sportify/catalog/controller/ManagerPackageControllerTest.java |
| M | backend/src/test/java/com/sportify/catalog/service/MembershipCardServiceImplTest.java | D:/G3-SportCentreManagement/SWP/backend/src/test/java/com/sportify/catalog/service/MembershipCardServiceImplTest.java |
| M | backend/src/test/java/com/sportify/catalog/service/SportPackageServiceImplTest.java | D:/G3-SportCentreManagement/SWP/backend/src/test/java/com/sportify/catalog/service/SportPackageServiceImplTest.java |
| M | docs/database/02-catalog-and-membership.md | D:/G3-SportCentreManagement/SWP/docs/database/02-catalog-and-membership.md |
| M | docs/database/schema/02-catalog-and-membership.md | D:/G3-SportCentreManagement/SWP/docs/database/schema/02-catalog-and-membership.md |
| A | docs/tests/BE-03_test_cases.md | D:/G3-SportCentreManagement/SWP/docs/tests/BE-03_test_cases.md |

## Isolated review evidence directory

C:/Users/TIEN DAT/.codex/visualizations/2026/10/14/01a13a3b-b917-7a82-aa32-a08f8318cf9b/pr40-review

The candidate and baseline snapshots, comparison results and execution logs are review evidence. They do not change the PR branch.
