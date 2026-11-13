# Test Cases - Spori Gemini Assistant for All Roles (PR #330)

## 1. Scope and provenance
- Branch: feat/spori-gemini-reply (PR #330).
- Request: team lead brief "Replace Hardcoded Spori Responses with Gemini AI" for Member, Manager, Receptionist and Coach.
- Affected API: POST /api/v1/assistant/chat (new, read only). POST /api/v1/member/training/consultation is kept for compatibility.
- Affected screens: floating Spori chat in Member and Staff layouts; Member full assistant page (/member/assistant).

## 2. Change summary
- Gemini writes every Spori reply from Sportify data selected on the server; keyword rules remain the fallback.
- Role-scoped data: Members see their own records; staff see aggregate figures; a Coach also sees their own sessions.
- Server-side handoff field: RECEPTIONIST (payments, VietQR, refunds, charges, account) or COACH (attendance, performance, exercises, coaching).
- Off-topic questions are refused politely; greetings and thanks are answered naturally.
- The same floating chat is mounted in Staff layouts; sending a message never navigates away.

## 3. Acceptance criteria
| ID | Criterion | Verification |
|---|---|---|
| AC-1 | All four roles can call /assistant/chat; other roles are rejected | SporiController @PreAuthorize; SporiChatTest.unknownRolesAreRejected |
| AC-2 | Replies are written by Gemini (mode AI_GENERATED) when AI is enabled | Live check, section 5 |
| AC-3 | Greetings and thanks get a natural reply | Live check: "Xin chao", "Cam on", "Thank you" |
| AC-4 | Off-topic questions get the polite Spori refusal | Live check: "How old are you?", "Who is the president of the USA?", programming, movies; SporiChatTest.anOffTopicReply... |
| AC-5 | Payment, VietQR, refund and account topics name the Receptionist | SporiChatTest.paymentVietQrRefundAndAccountTopicsGoToReception; live check |
| AC-6 | Attendance and training topics name the Coach | SporiChatTest.attendanceAndTrainingTopicsGoToTheCoach; live check |
| AC-7 | Spori never claims to approve refunds, confirm payments or change attendance | Prompt rule 6; live check "can you refund her?" |
| AC-8 | Staff answers never read Member names, contacts, payments or attendance | SporiChatTest.staffGetTheirRoleOverviewAndNeverMemberPrivateColumns |
| AC-9 | Private, injection and health questions never reach Gemini | AssistantReplyWriterTest.healthAndPrivateQuestionsNeverReachTheWriter; SporiChatTest.aGuardedRefundRequest... |
| AC-10 | Staff layouts show the same floating chat; sending stays on the page | StaffSpori.test.tsx; browser check on Manager and Coach pages |
| AC-11 | Gemini failure or missing key falls back to the rule answer | AssistantReplyWriterTest.withoutASignedInMemberOrOnFailureTheRuleAnswerStays |
| AC-12 | No API key in the repository or frontend | backend/.env not committed; key read only by the backend |

## 4. Executed automated verification
Run on the working tree on 2026-11-13.

| Command | Result |
|---|---|
| backend: mvn test -Dtest=SporiChatTest,AssistantReplyWriterTest,CenterConsultation*,AssistantAiReleaseGateTest,TrainingGuidance*,ApplicationYamlPropertiesTest,CorsPropertiesConfigTest | Passed (10 + 9 + 48 + 27 + 17 + 2 + 1 + 5 tests) |
| frontend: npx tsc -b --noEmit | Passed |
| frontend: npx eslint src/pages/member src/layouts src/api/sporiApi.ts | Passed |
| frontend: npx vitest run | Passed, 133 files and 2067 tests |
| bash scripts/ci/check-conventions.sh | Passed |

## 5. Live verification (local backend, database sportify, real Gemini)
| Role | Question | Expected | Observed |
|---|---|---|---|
| Member | Xin chao | Natural greeting | AI_GENERATED greeting in Vietnamese |
| Member | Badminton classes this week (Vietnamese) | Sessions from the database | Five sessions with dates, times and free places |
| Member | How old are you? | Polite refusal | Standard Spori refusal |
| Member | VietQR transfer not confirmed (Vietnamese) | Receptionist | handoff=RECEPTIONIST |
| Member | Wrong attendance last session (Vietnamese) | Coach | handoff=COACH |
| Manager | Overview of the center today | Aggregate figures | Active members, coaches, sessions, open requests |
| Manager | Member paid twice, can you refund her? | Receptionist, no action claimed | handoff=RECEPTIONIST, "I cannot process refunds" |
| Manager | Write a Python script | Polite refusal | Standard Spori refusal |
| Receptionist | Movie question (Vietnamese) | Polite refusal | Standard Spori refusal in Vietnamese |
| Coach | Show my sessions for the next 7 days | Own sessions only | Three own sessions with booked/total places |

## 6. Manual checks for reviewers
1. Sign in as each role, open the robot button, send a question: the panel stays open on the same page.
2. As a Member, ask about a refund: "Contact Receptionist" button opens Contact reception.
3. As a Coach or Manager, ask about a refund: the reply shows "Handled by: Reception team" and no Member link.
4. Switch to dark mode and a 390px viewport: the chat stays readable and opens full screen on phones.
