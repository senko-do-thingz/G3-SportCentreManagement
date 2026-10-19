# FE-13 Finance contract alignment and open decisions

Status: frontend aligned to the BE-21 refund and BE-22 manager finance contracts as reviewed on 2026-10-18 (BE-22 PR #59). No backend code or schema was changed.

The current V1 checkout's backend source does not contain a manager finance controller. Therefore, starting the backend from this checkout will not provide the BE-22 finance routes until that backend work is merged into the checked-out branch and deployed. The frontend uses the reviewed contract and does not hide this dependency with mock data.

## Contract behavior implemented in the frontend

- Financial overview reads the BE-22 `FinanceOverviewResponse` fields: `paidRevenue`, `paidTransactions`, `averagePayment`, `pendingAmount`, `pendingTransactions`, `refundedAmount`, `refundedTransactions`, and prior-month comparison fields.
- The six-month trend uses `GET /manager/finance/trend?months=6`. BE-22 anchors the range to the current month, not the month selected on the overview.
- Transaction month and search UI state is sent to BE-22 as `from`, `to`, and `search`; payment status and zero-based paging remain separate filters.
- Revenue reports read `RevenueBreakdownResponse` and its `totalRevenue`, `totalTransactions`, `rows[].label`, `revenue`, `transactions`, and `sharePercent` fields. Supported group values are `ITEM_TYPE`, `METHOD`, and `SPORT`.
- CSV export uses the authenticated `GET /manager/finance/revenue/export?from=&to=` endpoint. The UI honors its `Content-Disposition` filename, with the contract-compatible `revenue_<from>_<to>.csv` name as a fallback.
- Pending refund approvals continue to use BE-21 `GET /refunds/pending` and `PUT /refunds/{id}/review`.

## Backend contract decisions requested

1. **Net collected on the overview and revenue report:** BE-22 reports paid revenue by payment time and completed refunds by their completion/update time. It does not return `netCollected`, and subtracting these period totals may combine different accounting periods. Please decide whether net revenue is required and, if so, define its accounting period and add an explicit backend field or endpoint. FE-13 does not calculate or display a fabricated net amount.
2. **Approved but unpaid refund queue:** BE-21's manager queue lists pending refunds. The current contract has no manager-wide query for approved refunds awaiting payout and no matching `approvedNotReturnedCount`. Please decide whether payout tracking and this queue are in scope; if yes, provide the status, paging, and payout-state contract. FE-13 removes the invalid payment-status link and does not infer refund payout status.
3. **Refund breakdown in revenue reports:** BE-22 revenue rows contain payment revenue, transaction counts, and revenue shares, but no refund amounts by item type, method, or sport. If the report must compare refunds or net amounts by group, define a backend response contract with the refund grouping and period semantics. FE-13 currently reports only fields the API supplies.
4. **Historical trend anchor:** The current trend endpoint accepts `months` only and ends at the current month. If a selected historical month should anchor the chart, add an optional `endMonth` (or equivalent) contract. Until then, the UI labels the chart as ending in the current month.

## Manual verification checklist

- Sign in as MANAGER and compare the overview KPI values and selected month against the finance API response.
- Search transactions and select a month; verify requests contain `from`, `to`, and `search`, not `month` or `query`.
- Switch report groups and verify the request uses `METHOD` for payment method and never includes `sportId`.
- Export a report and verify the authenticated CSV endpoint, file contents, and `revenue_<from>_<to>.csv` filename.
- Review a refund with a successful response, a conflict, and a timeout/HTTP 408; verify uncertain outcomes do not invite a blind duplicate submission.

These live-browser checks require the BE-21/BE-22 services to be deployed and were not established by frontend fixture tests.
