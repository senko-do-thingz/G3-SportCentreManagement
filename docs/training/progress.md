# Recorded training progress

`GET /api/v1/member/training/progress` returns the signed-in Member's chronological results and private feedback. It ignores any caller-supplied Member ID. `GET /api/v1/coach/training/classes/{classId}/members/{memberId}/progress` requires a currently assigned, enabled Coach and a real, currently enrolled learner; it limits results and feedback to that class and Coach.

Each outcome includes the persisted public Session code, session date, notes, current attendance status and source metric ID, name, maximum and score. Compare only identical metric IDs with identical scales. Sessions with no scores are explicitly notes-only. No fabricated trend, normalization, inference or AI is produced. Current Coach access ends after withdrawal or reassignment; a Member retains their own historical outcomes. Existing attendance, duplicate-result, score bounds and correction guards remain authoritative.
