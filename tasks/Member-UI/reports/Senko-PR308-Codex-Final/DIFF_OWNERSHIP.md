# PR #308 Diff Ownership

Comparison base: `origin/main` at `f6774c7d08f9f11801a5071e338575cf196fb088`.

## Preserved Gemini implementation

The original commit `3cc726c871395740f3db7d65fe54147cf80c4ecb` owns the Concept B presentation and safety work in these Member-only sources:

- `BookingCreationPages.tsx`, `MemberBookingsPages.tsx`, `CheckInPassPage.tsx`
- `MemberWalletPage.tsx`, `WalletTopUpPage.tsx`, `WalletWithdrawPage.tsx`, `WalletHistoryPage.tsx`
- `MyPaymentsPage.tsx`, `PaymentSummaryPage.tsx`, `PaymentStatusPage.tsx`, `QrPaymentDemoPage.tsx`, `ReceiptPage.tsx`
- `MyProfilePage.tsx`, `HealthAssessmentPage.tsx`, `MyMembershipPage.tsx`, `MembershipCardsPage.tsx`
- `MemberCommerceComponents.tsx`, `memberCommerce.css`

## Codex remediation commit

Commit `1e50d919044a655fb84f87b34b376b886c4c5fd9` changes only:

| File | Ownership reason |
|---|---|
| `frontend/src/pages/member/WalletWithdrawPage.tsx` | O5 synchronous withdrawal and payout-account save guards; uncertain idempotency copy |
| `frontend/src/pages/member/WalletTopUpPage.tsx` | O4 synchronous create and per-request cancellation guards; uncertain idempotency copy |
| `frontend/src/pages/member/walletWithdraw.test.tsx` | O5 same-tick submit, retry-key, and payout save regressions |
| `frontend/src/pages/member/walletTopUp.test.tsx` | O4 same-tick create/cancel and retry-key regressions |
| `frontend/src/styles/memberCommerce.css` | Member-only print selector scoping |
| `frontend/src/styles/memberCommerceIsolation.test.ts` | automated prevention of global print leakage |

This evidence directory is a documentation-only follow-up commit and contains the reports, capture harness, manifest, and 27 PNGs.

## Frozen PR #309 surface

No #309-owned source was modified or committed by #308: `MemberLayout.tsx`, `navigation.ts`, `memberConceptB.css`, `AppRoutes.tsx`, `MemberDashboardPage.tsx`, `MemberTrainingHubPage.tsx`, `MemberAssistantPage.tsx`, `MemberSupportPage.tsx`, and `TrainingNavigation.tsx` remain byte-for-byte from their respective branch owners. Their changes appeared only in a disposable, unpushed integration worktree.

No backend, migration, lockfile, CODEOWNERS, workflow, Manager, Receptionist, Coach, or Home source is part of the #308 remediation diff.
