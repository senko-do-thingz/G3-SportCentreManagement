# Deterministic training guidance

Class recommendations use GET /api/v1/member/training/recommendations for enabled Members. Optional preferences are sportId, level, goal, from, to, earliest, latest, page and size. Supported levels and goals match Member profile values. Omitted level and goal use the signed-in Member's saved profile. Dates use center time in Asia/Ho_Chi_Minh. The default range is 14 days; a selected range can span at most 30 days. Pages contain 1-20 classes.

Only active sports/classes with published upcoming sessions and positive remaining capacity qualify. Already-booked sessions are excluded. Coach-led sessions require the current assigned active coach. Available times restrict the complete session. One session is selected per class after scoring and stable sorting by score descending, date, start time, class ID and session ID.

The class-rules-v1 score adds 50 for a selected sport, 20 for the selected or saved level, 5 for the Member's own recorded participation in that sport, and 15 for a goal match. BUILD_SKILLS matches coached sessions, COMPETITION matches advanced coached sessions, and IMPROVE_FITNESS matches sessions lasting at least 60 minutes. These are transparent product heuristics and do not assert fitness or medical outcomes.

The response declares mode RULE_BASED, ruleVersion class-rules-v1 and bookingEligibilityChecked false. Items include real class/session IDs, capacity snapshots, scores and reasons. Session details and the existing booking service remain authoritative for package eligibility and capacity. A recommendation creates no booking, invoice, credit debit or assigned plan.

No model-provider integration or third-party data transfer is used. Genuine LLM integration remains unavailable until a provider and data contract are approved. These rules do not imply CODEOWNER approval of pending release policies.
