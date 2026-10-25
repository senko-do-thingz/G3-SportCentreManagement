# NX-01 existing API inventory

Round 2 remediation: unchanged source analysis revalidated at `d20833c3c16e4f7d95d12b6df21798fc528bd559`. All 220 prior source-file hashes match; 19 explicit high-impact samples pass semantic checks. Counts in this report describe static source, not deployed behavior. See [independent reconciliation](NX-01-inventory-reconciliation.md) and workbook reconciliation (`evidence/workbook-source-reconciliation.md` - local review reference, not published).

Report index (`NX-01-report-index.md` - local review reference, not published). Round 2. Source baseline: `d20833c3c16e4f7d95d12b6df21798fc528bd559`.

Source findings refer to fetched remote main, not the unchanged task checkout. Static evidence does not prove a deployed database or runtime response. NX-00 formal scope approval remains unverified.

Inventory: 37 controllers, 105 composed mapping entries including aliases and parameter variants. Actuator health is framework-managed and recorded separately. These are static mappings, not HTTP probes. There is no configured servlet context-path in the inspected application files. EV-018 (`NX-01-evidence-index.md#ev-018` - local review reference, not published), EV-023 (`NX-01-evidence-index.md#ev-023` - local review reference, not published)

The route security filter permits auth routes, OPTIONS, health and the explicit public catalog GET allowlist; all other routes require authentication. Method/class PreAuthorize restrictions below apply as well. A method restriction replaces the class restriction, rather than adding new actor rights. Success bodies use concrete DTOs, arrays, PageResponse or ResponseEntity, not one universal success envelope. Global errors use timestamp/status/message and optional details/code; 401/403 filters also have dedicated handlers. EV-018 (`NX-01-evidence-index.md#ev-018` - local review reference, not published), EV-020 (`NX-01-evidence-index.md#ev-020` - local review reference, not published)

## AgeGroupController

[`backend/src/main/java/com/sportify/catalog/controller/AgeGroupController.java`](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/java/com/sportify/catalog/controller/AgeGroupController.java). EV-100 (`NX-01-evidence-index.md#ev-100` - local review reference, not published)


| Method and composed path | Parameter selection | Authorization | Handler signature and DTO/validation annotations | Service call | Lines |
| --- | --- | --- | --- | --- | --- |
| GET `/api/v1/age-groups` | No mapping-level parameter condition | hasAnyRole('MANAGER', 'RECEPTIONIST') | `ResponseEntity<List<AgeGroupResponse>> getAgeGroups()` | ageGroupService.getActiveAgeGroups()) | 25-28 |

## BookingController

[`backend/src/main/java/com/sportify/catalog/controller/BookingController.java`](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/java/com/sportify/catalog/controller/BookingController.java). EV-101 (`NX-01-evidence-index.md#ev-101` - local review reference, not published)


| Method and composed path | Parameter selection | Authorization | Handler signature and DTO/validation annotations | Service call | Lines |
| --- | --- | --- | --- | --- | --- |
| POST `/api/v1/bookings` | No mapping-level parameter condition | hasAnyRole('MEMBER', 'RECEPTIONIST', 'MANAGER') | `BookingResponse createBooking( @Valid @RequestBody BookingCreateRequest request, @AuthenticationPrincipal CustomUserDetails userDetails)` | bookingService.createBooking(request, userDetails.getUserAccount()) | 29-36 |
| DELETE `/api/v1/bookings/{id}` | No mapping-level parameter condition | hasAnyRole('MEMBER', 'RECEPTIONIST', 'MANAGER') | `BookingResponse cancelBooking( @PathVariable Long id, @AuthenticationPrincipal CustomUserDetails userDetails)` | bookingService.cancelBooking(id, userDetails.getUserAccount()) | 38-44 |
| GET `/api/v1/bookings/my` | {"!page", "!size"} | hasRole('MEMBER') | `List<BookingResponse> getMyBookings( @RequestParam(required = false) BookingWhen when, @AuthenticationPrincipal CustomUserDetails userDetails)` | bookingService.getMyBookings(userDetails.getUserAccount()) : bookingService.getMyBookings(userDetails.getUserAccount(), when) | 47-54 |
| GET `/api/v1/bookings/my` | No mapping-level parameter condition | hasRole('MEMBER') | `PageResponse<BookingResponse> getMyBookingsPage( @RequestParam(required = false) BookingWhen when, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "" + PagingDefaults.DEFAULT_PAGE_SIZE) int size, @AuthenticationPrincipal CustomUserDetails userDetails)` | bookingService.getMyBookings(userDetails.getUserAccount(), when, page, size) | 56-64 |
| GET `/api/v1/bookings/member/{memberId}` | {"!page", "!size"} | hasAnyRole('RECEPTIONIST', 'MANAGER') | `List<BookingResponse> getMemberBookings(@PathVariable Long memberId, @RequestParam(required = false) BookingWhen when, @RequestParam(required = false) BookingStatus status, @AuthenticationPrincipal CustomUserDetails principal)` | bookingService.getMemberBookings(memberId, when, status, principal.getUserAccount()) | 66-73 |
| GET `/api/v1/bookings/member/{memberId}` | No mapping-level parameter condition | hasAnyRole('RECEPTIONIST', 'MANAGER') | `PageResponse<BookingResponse> getMemberBookingsPage(@PathVariable Long memberId, @RequestParam(required = false) BookingWhen when, @RequestParam(required = false) BookingStatus status, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "" + PagingDefaults.DEFAULT_PAGE_SIZE) int size, @AuthenticationPrincipal CustomUserDetails principal)` | bookingService.getMemberBookings(memberId, when, status, page, size, principal.getUserAccount()) | 75-84 |
| GET `/api/v1/bookings/today/{memberId}` | No mapping-level parameter condition | hasAnyRole('RECEPTIONIST', 'MANAGER') | `List<BookingResponse> getTodayBookings(@PathVariable Long memberId)` | bookingService.getTodayBookings(memberId) | 86-90 |
| POST `/api/v1/bookings/self-training/attendance` | No mapping-level parameter condition | hasAnyRole('RECEPTIONIST', 'MANAGER') | `SelfTrainingAttendanceResponse recordSelfTrainingAttendance( @Valid @RequestBody SelfTrainingAttendanceRequest request, @AuthenticationPrincipal CustomUserDetails userDetails)` | bookingService.recordSelfTrainingAttendance(request, userDetails.getUserAccount()) | 92-98 |

## CheckInController

[`backend/src/main/java/com/sportify/catalog/controller/CheckInController.java`](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/java/com/sportify/catalog/controller/CheckInController.java). EV-102 (`NX-01-evidence-index.md#ev-102` - local review reference, not published)


| Method and composed path | Parameter selection | Authorization | Handler signature and DTO/validation annotations | Service call | Lines |
| --- | --- | --- | --- | --- | --- |
| POST `/api/v1/check-ins` | No mapping-level parameter condition | hasAnyRole('MANAGER', 'RECEPTIONIST') | `CheckInResponse recordCheckIn( @Valid @RequestBody CheckInRequest request, @AuthenticationPrincipal CustomUserDetails userDetails)` | checkInService.recordCheckIn(request, userDetails.getUserAccount()) | 26-33 |
| POST `/api/v1/check-in` | No mapping-level parameter condition | hasAnyRole('MANAGER', 'RECEPTIONIST') | `CheckInResponse recordCheckIn( @Valid @RequestBody CheckInRequest request, @AuthenticationPrincipal CustomUserDetails userDetails)` | checkInService.recordCheckIn(request, userDetails.getUserAccount()) | 26-33 |
| GET `/api/v1/check-ins` | No mapping-level parameter condition | hasAnyRole('MANAGER', 'RECEPTIONIST') | `List<CheckInResponse> getCheckInHistory( @RequestParam(required = false) Long memberId, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date)` | checkInService.getCheckInsOn(date, memberId); checkInService.getCheckInHistory(memberId) | 35-47 |
| GET `/api/v1/check-in` | No mapping-level parameter condition | hasAnyRole('MANAGER', 'RECEPTIONIST') | `List<CheckInResponse> getCheckInHistory( @RequestParam(required = false) Long memberId, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date)` | checkInService.getCheckInsOn(date, memberId); checkInService.getCheckInHistory(memberId) | 35-47 |

## CoachScheduleController

[`backend/src/main/java/com/sportify/catalog/controller/CoachScheduleController.java`](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/java/com/sportify/catalog/controller/CoachScheduleController.java). EV-103 (`NX-01-evidence-index.md#ev-103` - local review reference, not published)


| Method and composed path | Parameter selection | Authorization | Handler signature and DTO/validation annotations | Service call | Lines |
| --- | --- | --- | --- | --- | --- |
| GET `/api/v1/coach/sessions` | No mapping-level parameter condition | hasAnyRole('COACH', 'MANAGER') | `List<CoachSessionResponse> listSessions(@RequestParam String from, @RequestParam String to, @RequestParam(required = false) Long coachId)` | service.listSessions(from, to, coachId) | 22-26 |
| GET `/api/v1/coach/sessions/{id}/roster` | No mapping-level parameter condition | hasAnyRole('COACH', 'MANAGER') | `List<CoachRosterResponse> roster(@PathVariable Long id)` | service.roster(id) | 28-31 |

## DevMembershipController

[`backend/src/main/java/com/sportify/catalog/controller/DevMembershipController.java`](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/java/com/sportify/catalog/controller/DevMembershipController.java). EV-104 (`NX-01-evidence-index.md#ev-104` - local review reference, not published)

Profile guard: "dev". A mapped class is only reachable when its profile is active.


| Method and composed path | Parameter selection | Authorization | Handler signature and DTO/validation annotations | Service call | Lines |
| --- | --- | --- | --- | --- | --- |
| POST `/api/v1/dev/memberships/{id}/activate` | No mapping-level parameter condition | hasRole('MANAGER') | `void activateMembership(@PathVariable Long id, @org.springframework.security.core.annotation.AuthenticationPrincipal com.sportify.identity.security.CustomUserDetails userDetails)` | membershipService.activateMembership(id, userDetails.getUserAccount()) | 18-24 |

## FacilityController

[`backend/src/main/java/com/sportify/catalog/controller/FacilityController.java`](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/java/com/sportify/catalog/controller/FacilityController.java). EV-105 (`NX-01-evidence-index.md#ev-105` - local review reference, not published)


| Method and composed path | Parameter selection | Authorization | Handler signature and DTO/validation annotations | Service call | Lines |
| --- | --- | --- | --- | --- | --- |
| GET `/api/v1/facilities` | No mapping-level parameter condition | hasRole('MANAGER') | `ResponseEntity<List<FacilityResponse>> getFacilities()` | facilityService.getFacilities()) | 21-24 |

## FrontDeskMemberOverviewController

[`backend/src/main/java/com/sportify/catalog/controller/FrontDeskMemberOverviewController.java`](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/java/com/sportify/catalog/controller/FrontDeskMemberOverviewController.java). EV-106 (`NX-01-evidence-index.md#ev-106` - local review reference, not published)


| Method and composed path | Parameter selection | Authorization | Handler signature and DTO/validation annotations | Service call | Lines |
| --- | --- | --- | --- | --- | --- |
| GET `/api/v1/members/{id}/overview` | No mapping-level parameter condition | hasAnyRole('RECEPTIONIST', 'MANAGER') | `MemberOverviewResponse overview(@PathVariable Long id)` | service.overview(id) | 17-21 |

## FrontDeskQueueController

[`backend/src/main/java/com/sportify/catalog/controller/FrontDeskQueueController.java`](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/java/com/sportify/catalog/controller/FrontDeskQueueController.java). EV-107 (`NX-01-evidence-index.md#ev-107` - local review reference, not published)


| Method and composed path | Parameter selection | Authorization | Handler signature and DTO/validation annotations | Service call | Lines |
| --- | --- | --- | --- | --- | --- |
| GET `/api/v1/membership-cards/pending` | No mapping-level parameter condition | hasAnyRole('RECEPTIONIST', 'MANAGER') | `Page<FrontDeskCardResponse> pendingCards(@PageableDefault(size = 20) Pageable pageable)` | service.pendingCards(pageable) | 21-24 |
| GET `/api/v1/packages/registrations/pending` | No mapping-level parameter condition | hasAnyRole('RECEPTIONIST', 'MANAGER') | `Page<FrontDeskPackageResponse> pendingPackages(@PageableDefault(size = 20) Pageable pageable)` | service.pendingPackages(pageable) | 26-29 |

## ManagerCardTierController

[`backend/src/main/java/com/sportify/catalog/controller/ManagerCardTierController.java`](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/java/com/sportify/catalog/controller/ManagerCardTierController.java). EV-108 (`NX-01-evidence-index.md#ev-108` - local review reference, not published)


| Method and composed path | Parameter selection | Authorization | Handler signature and DTO/validation annotations | Service call | Lines |
| --- | --- | --- | --- | --- | --- |
| GET `/api/v1/manager/card-tiers` | No mapping-level parameter condition | hasRole('MANAGER') | `List<CardTierResponse> getAllCardTiers()` | membershipCardService.getAllTiersForManager() | 24-27 |

## ManagerClassController

[`backend/src/main/java/com/sportify/catalog/controller/ManagerClassController.java`](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/java/com/sportify/catalog/controller/ManagerClassController.java). EV-109 (`NX-01-evidence-index.md#ev-109` - local review reference, not published)


| Method and composed path | Parameter selection | Authorization | Handler signature and DTO/validation annotations | Service call | Lines |
| --- | --- | --- | --- | --- | --- |
| GET `/api/v1/manager/classes` | No mapping-level parameter condition | hasRole('MANAGER') | `ResponseEntity<PageResponse<ManagerClassResponse>> listClasses( @RequestParam(required = false) Long sportId, @RequestParam(required = false) ClassLevel level, @RequestParam(required = false) Long coachId, @RequestParam(required = false) Boolean active, @RequestParam(required = false) String search, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size)` | managerClassService.listClasses(sportId, level, coachId, active, search, page, size)) | 38-48 |
| GET `/api/v1/manager/classes/{id}` | No mapping-level parameter condition | hasRole('MANAGER') | `ResponseEntity<ManagerClassResponse> getClass(@PathVariable Long id)` | managerClassService.getClass(id)) | 50-53 |
| POST `/api/v1/manager/classes` | No mapping-level parameter condition | hasRole('MANAGER') | `ResponseEntity<ManagerClassResponse> createClass( @Valid @RequestBody SportClassRequest request, @AuthenticationPrincipal CustomUserDetails principal)` | managerClassService.createClass(request, actor(principal))) | 55-61 |
| PUT `/api/v1/manager/classes/{id}` | No mapping-level parameter condition | hasRole('MANAGER') | `ResponseEntity<ManagerClassResponse> updateClass( @PathVariable Long id, @Valid @RequestBody SportClassRequest request, @AuthenticationPrincipal CustomUserDetails principal)` | managerClassService.updateClass(id, request, actor(principal))) | 63-69 |
| PATCH `/api/v1/manager/classes/{id}/status` | No mapping-level parameter condition | hasRole('MANAGER') | `ResponseEntity<ManagerClassResponse> changeStatus( @PathVariable Long id, @Valid @RequestBody ClassStatusUpdateRequest request, @AuthenticationPrincipal CustomUserDetails principal)` | managerClassService.changeStatus(id, request.active(), actor(principal))) | 71-77 |

## ManagerMembershipPlanController

[`backend/src/main/java/com/sportify/catalog/controller/ManagerMembershipPlanController.java`](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/java/com/sportify/catalog/controller/ManagerMembershipPlanController.java). EV-110 (`NX-01-evidence-index.md#ev-110` - local review reference, not published)


| Method and composed path | Parameter selection | Authorization | Handler signature and DTO/validation annotations | Service call | Lines |
| --- | --- | --- | --- | --- | --- |
| GET `/api/v1/manager/plans` | No mapping-level parameter condition | hasRole('MANAGER') | `ResponseEntity<PageResponse<PlanResponse>> getAllPlans( @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size)` | planService.getAllPlans(page, size)) | 38-43 |
| POST `/api/v1/manager/plans` | No mapping-level parameter condition | hasRole('MANAGER') | `ResponseEntity<PlanResponse> createPlan( @Valid @RequestBody PlanCreateRequest request, @AuthenticationPrincipal CustomUserDetails principal)` | planService.createPlan(request, actorId(principal))) | 45-50 |
| PUT `/api/v1/manager/plans/{id}` | No mapping-level parameter condition | hasRole('MANAGER') | `ResponseEntity<PlanResponse> updatePlan( @PathVariable Long id, @Valid @RequestBody PlanUpdateRequest request, @AuthenticationPrincipal CustomUserDetails principal)` | planService.updatePlan(id, request, actorId(principal))) | 52-58 |
| PATCH `/api/v1/manager/plans/{id}/status` | No mapping-level parameter condition | hasRole('MANAGER') | `ResponseEntity<PlanResponse> changeStatus( @PathVariable Long id, @Valid @RequestBody PlanStatusUpdateRequest request, @AuthenticationPrincipal CustomUserDetails principal)` | planService.changeStatus(id, request, actorId(principal))) | 60-66 |

## ManagerPackageController

[`backend/src/main/java/com/sportify/catalog/controller/ManagerPackageController.java`](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/java/com/sportify/catalog/controller/ManagerPackageController.java). EV-111 (`NX-01-evidence-index.md#ev-111` - local review reference, not published)


| Method and composed path | Parameter selection | Authorization | Handler signature and DTO/validation annotations | Service call | Lines |
| --- | --- | --- | --- | --- | --- |
| GET `/api/v1/manager/packages` | No mapping-level parameter condition | hasRole('MANAGER') | `List<SportPackageResponse> listPackages( @RequestParam(required = false) Long sportId, @RequestParam(required = false) TrainingFormat format, @RequestParam(required = false) Boolean active)` | sportPackageService.getManagerPackages(sportId, format, active) | 26-32 |

## ManagerSessionController

[`backend/src/main/java/com/sportify/catalog/controller/ManagerSessionController.java`](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/java/com/sportify/catalog/controller/ManagerSessionController.java). EV-112 (`NX-01-evidence-index.md#ev-112` - local review reference, not published)


| Method and composed path | Parameter selection | Authorization | Handler signature and DTO/validation annotations | Service call | Lines |
| --- | --- | --- | --- | --- | --- |
| GET `/api/v1/manager/sessions` | No mapping-level parameter condition | hasRole('MANAGER') | `ResponseEntity<List<ManagerSessionResponse>> listSessions( @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to, @RequestParam(required = false) Long classId, @RequestParam(required = false) Long coachId, @RequestParam(required = false) Long facilityId)` | managerSessionService.listSessions(from, to, classId, coachId, facilityId)) | 36-44 |
| POST `/api/v1/manager/classes/{classId}/sessions` | No mapping-level parameter condition | hasRole('MANAGER') | `ResponseEntity<List<ManagerSessionResponse>> createSessions(@PathVariable Long classId, @Valid @RequestBody SessionCreateRequest request, @AuthenticationPrincipal CustomUserDetails principal)` | managerSessionService.createSessions(classId, request, actor(principal))) | 46-52 |
| PUT `/api/v1/manager/sessions/{id}` | No mapping-level parameter condition | hasRole('MANAGER') | `ResponseEntity<ManagerSessionResponse> updateSession(@PathVariable Long id, @Valid @RequestBody SessionUpdateRequest request, @AuthenticationPrincipal CustomUserDetails principal)` | managerSessionService.updateSession(id, request, actor(principal))) | 54-59 |
| PUT `/api/v1/manager/sessions/{id}/cancel` | No mapping-level parameter condition | hasRole('MANAGER') | `ResponseEntity<ManagerSessionResponse> cancelSession(@PathVariable Long id, @Valid @RequestBody SessionCancelRequest request, @AuthenticationPrincipal CustomUserDetails principal)` | managerSessionService.cancelSession(id, request, actor(principal))) | 61-66 |

## MembershipCardController

[`backend/src/main/java/com/sportify/catalog/controller/MembershipCardController.java`](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/java/com/sportify/catalog/controller/MembershipCardController.java). EV-113 (`NX-01-evidence-index.md#ev-113` - local review reference, not published)


| Method and composed path | Parameter selection | Authorization | Handler signature and DTO/validation annotations | Service call | Lines |
| --- | --- | --- | --- | --- | --- |
| GET `/api/v1/membership-cards/tiers` | No mapping-level parameter condition | Public route allowed by SecurityConfig; no controller role restriction | `List<CardTierResponse> getAllActiveTiers()` | membershipCardService.getAllActiveTiers() | 27-30 |
| PUT `/api/v1/membership-cards/tiers/{id}` | No mapping-level parameter condition | hasRole('MANAGER') | `CardTierResponse updateTier( @PathVariable Long id, @Valid @RequestBody MembershipCardTierUpdateRequest request, @AuthenticationPrincipal CustomUserDetails userDetails)` | membershipCardService.updateTier(id, request, userDetails.getUserAccount()) | 32-39 |
| POST `/api/v1/membership-cards/purchase` | No mapping-level parameter condition | hasAnyRole('MEMBER', 'RECEPTIONIST', 'MANAGER') | `MemberCardResponse purchaseCard( @Valid @RequestBody MemberCardPurchaseRequest request, @AuthenticationPrincipal CustomUserDetails userDetails)` | membershipCardService.purchaseCard(request, userDetails.getUserAccount()) | 41-48 |
| PUT `/api/v1/membership-cards/{id}/activate` | No mapping-level parameter condition | hasAnyRole('RECEPTIONIST', 'MANAGER') | `MemberCardResponse activateCard( @PathVariable Long id, @RequestBody(required = false) ActivatePaymentRequest request, @AuthenticationPrincipal CustomUserDetails userDetails)` | request.getPaymentMethod() : null; membershipCardService.activateCard(id, userDetails.getUserAccount(), actualMethod) | 51-59 |
| PUT `/api/v1/membership-cards/{id}/cancel` | No mapping-level parameter condition | hasAnyRole('MEMBER', 'RECEPTIONIST', 'MANAGER') | `MemberCardResponse cancelCard( @PathVariable Long id, @AuthenticationPrincipal CustomUserDetails userDetails)` | membershipCardService.cancelCard(id, userDetails.getUserAccount()) | 62-68 |
| GET `/api/v1/membership-cards/my` | No mapping-level parameter condition | hasRole('MEMBER') | `List<MemberCardResponse> getMyCards( @AuthenticationPrincipal CustomUserDetails userDetails)` | membershipCardService.getMyCards(userDetails.getUserAccount()) | 70-75 |
| GET `/api/v1/membership-cards/member/{memberId}` | No mapping-level parameter condition | hasAnyRole('RECEPTIONIST', 'MANAGER') | `List<MemberCardResponse> getMemberCards(@PathVariable Long memberId)` | membershipCardService.getMemberCards(memberId) | 77-81 |

## MembershipController

[`backend/src/main/java/com/sportify/catalog/controller/MembershipController.java`](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/java/com/sportify/catalog/controller/MembershipController.java). EV-114 (`NX-01-evidence-index.md#ev-114` - local review reference, not published)


| Method and composed path | Parameter selection | Authorization | Handler signature and DTO/validation annotations | Service call | Lines |
| --- | --- | --- | --- | --- | --- |
| POST `/api/v1/memberships` | No mapping-level parameter condition | hasRole('MEMBER') | `MembershipResponse registerMembership( @AuthenticationPrincipal CustomUserDetails userDetails, @Valid @RequestBody MembershipRegistrationRequest request)` | membershipService.registerMembership(userDetails.getUserAccount(), request) | 25-32 |
| POST `/api/v1/memberships/{id}/cancel` | No mapping-level parameter condition | hasAnyRole('MEMBER','RECEPTIONIST','MANAGER') | `void cancelMembership( @PathVariable Long id, @AuthenticationPrincipal CustomUserDetails userDetails)` | membershipService.cancelMembership(id, userDetails.getUserAccount()) | 34-41 |
| GET `/api/v1/memberships/me` | No mapping-level parameter condition | hasRole('MEMBER') | `List<MembershipResponse> getMyMemberships( @AuthenticationPrincipal CustomUserDetails userDetails)` | membershipService.getMyMemberships(userDetails.getUserAccount()) | 43-48 |
| POST `/api/v1/memberships/register-for-member` | No mapping-level parameter condition | hasAnyRole('MANAGER', 'RECEPTIONIST') | `MembershipResponse registerForMember( @RequestParam Long memberId, @AuthenticationPrincipal CustomUserDetails userDetails, @Valid @RequestBody MembershipRegistrationRequest request)` | membershipService.registerForMember(memberId, userDetails.getUserAccount(), request) | 50-58 |

## MembershipPlanController

[`backend/src/main/java/com/sportify/catalog/controller/MembershipPlanController.java`](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/java/com/sportify/catalog/controller/MembershipPlanController.java). EV-115 (`NX-01-evidence-index.md#ev-115` - local review reference, not published)


| Method and composed path | Parameter selection | Authorization | Handler signature and DTO/validation annotations | Service call | Lines |
| --- | --- | --- | --- | --- | --- |
| GET `/api/v1/plans` | No mapping-level parameter condition | Public route allowed by SecurityConfig; no controller role restriction | `ResponseEntity<List<PlanResponse>> getActivePlans()` | planService.getActivePlans()) | 24-27 |
| GET `/api/v1/plans/{id}` | No mapping-level parameter condition | Public route allowed by SecurityConfig; no controller role restriction | `ResponseEntity<PlanResponse> getActivePlan(@PathVariable Long id)` | planService.getActivePlan(id)) | 29-32 |

## PublicFacilityController

[`backend/src/main/java/com/sportify/catalog/controller/PublicFacilityController.java`](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/java/com/sportify/catalog/controller/PublicFacilityController.java). EV-116 (`NX-01-evidence-index.md#ev-116` - local review reference, not published)


| Method and composed path | Parameter selection | Authorization | Handler signature and DTO/validation annotations | Service call | Lines |
| --- | --- | --- | --- | --- | --- |
| GET `/api/v1/facilities/public` | No mapping-level parameter condition | Public route allowed by SecurityConfig; no controller role restriction | `ResponseEntity<List<PublicFacilityResponse>> getPublicFacilities()` | facilityService.getPublicFacilities()) | 23-26 |

## RefundController

[`backend/src/main/java/com/sportify/catalog/controller/RefundController.java`](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/java/com/sportify/catalog/controller/RefundController.java). EV-117 (`NX-01-evidence-index.md#ev-117` - local review reference, not published)


| Method and composed path | Parameter selection | Authorization | Handler signature and DTO/validation annotations | Service call | Lines |
| --- | --- | --- | --- | --- | --- |
| GET `/api/v1/refunds/quote` | No mapping-level parameter condition | hasAnyRole('MEMBER', 'RECEPTIONIST', 'MANAGER') | `RefundQuoteResponse quoteRefund( @RequestParam(required = false) Long registrationId, @AuthenticationPrincipal CustomUserDetails userDetails)` | refundService.quoteRefund(registrationId, userDetails.getUserAccount()) | 30-36 |
| POST `/api/v1/refunds` | No mapping-level parameter condition | hasAnyRole('MEMBER', 'RECEPTIONIST', 'MANAGER') | `RefundResponse submitRefund( @Valid @RequestBody RefundCreateRequest request, @AuthenticationPrincipal CustomUserDetails userDetails)` | refundService.submitRefund(request, userDetails.getUserAccount()) | 38-45 |
| GET `/api/v1/refunds/pending` | No mapping-level parameter condition | hasRole('MANAGER') | `List<RefundResponse> getPendingRefunds()` | refundService.getPendingRefunds() | 47-51 |
| PUT `/api/v1/refunds/{id}/review` | No mapping-level parameter condition | hasRole('MANAGER') | `RefundResponse reviewRefund( @PathVariable Long id, @Valid @RequestBody RefundReviewRequest request, @AuthenticationPrincipal CustomUserDetails userDetails)` | refundService.reviewRefund(id, request, userDetails.getUserAccount()) | 53-60 |
| PUT `/api/v1/refunds/{id}/complete` | No mapping-level parameter condition | hasAnyRole('RECEPTIONIST', 'MANAGER') | `RefundResponse completeRefund( @PathVariable Long id, @AuthenticationPrincipal CustomUserDetails userDetails)` | refundService.completeRefund(id, userDetails.getUserAccount()) | 62-68 |
| GET `/api/v1/refunds/my` | No mapping-level parameter condition | hasRole('MEMBER') | `List<RefundResponse> getMyRefunds( @AuthenticationPrincipal CustomUserDetails userDetails)` | refundService.getMyRefunds(userDetails.getUserAccount()) | 70-75 |
| GET `/api/v1/refunds/member/{memberId}` | No mapping-level parameter condition | hasAnyRole('RECEPTIONIST', 'MANAGER') | `List<RefundResponse> getMemberRefunds(@PathVariable Long memberId)` | refundService.getMemberRefunds(memberId) | 77-81 |

## SessionController

[`backend/src/main/java/com/sportify/catalog/controller/SessionController.java`](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/java/com/sportify/catalog/controller/SessionController.java). EV-118 (`NX-01-evidence-index.md#ev-118` - local review reference, not published)


| Method and composed path | Parameter selection | Authorization | Handler signature and DTO/validation annotations | Service call | Lines |
| --- | --- | --- | --- | --- | --- |
| GET `/api/v1/sessions` | No mapping-level parameter condition | hasAnyRole('MEMBER', 'RECEPTIONIST', 'MANAGER') | `PageResponse<SessionResponse> listSessions( @RequestParam(required = false) Long sportId, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to, @RequestParam(required = false) TrainingFormat format, @RequestParam(required = false) ClassLevel level, @RequestParam(required = false) Long coachId, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "" + PagingDefaults.DEFAULT_PAGE_SIZE) int size, @AuthenticationPrincipal CustomUserDetails principal)` | service.listSessions(new SessionSearchQuery(sportId, from, to, format, level, coachId, page, size), principal == null ? null : principal.getUserAccount()) | 31-44 |
| GET `/api/v1/sessions/{id}` | No mapping-level parameter condition | hasAnyRole('MEMBER', 'RECEPTIONIST', 'MANAGER') | `SessionDetailResponse getSession(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails principal)` | service.getSession(id, principal == null ? null : principal.getUserAccount()) | 46-50 |
| GET `/api/v1/sessions/{id}/eligibility` | No mapping-level parameter condition | hasAnyRole('RECEPTIONIST', 'MANAGER') | `SessionDetailResponse getMemberEligibility(@PathVariable Long id, @RequestParam Long memberId, @AuthenticationPrincipal CustomUserDetails principal)` | service.getMemberEligibility(id, memberId, principal == null ? null : principal.getUserAccount()) | 52-57 |

## SportController

[`backend/src/main/java/com/sportify/catalog/controller/SportController.java`](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/java/com/sportify/catalog/controller/SportController.java). EV-119 (`NX-01-evidence-index.md#ev-119` - local review reference, not published)


| Method and composed path | Parameter selection | Authorization | Handler signature and DTO/validation annotations | Service call | Lines |
| --- | --- | --- | --- | --- | --- |
| GET `/api/v1/sports` | No mapping-level parameter condition | Public route allowed by SecurityConfig; no controller role restriction | `ResponseEntity<List<SportResponse>> getActiveSports()` | sportService.getActiveSports()) | 23-26 |

## SportPackageController

[`backend/src/main/java/com/sportify/catalog/controller/SportPackageController.java`](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/java/com/sportify/catalog/controller/SportPackageController.java). EV-120 (`NX-01-evidence-index.md#ev-120` - local review reference, not published)


| Method and composed path | Parameter selection | Authorization | Handler signature and DTO/validation annotations | Service call | Lines |
| --- | --- | --- | --- | --- | --- |
| GET `/api/v1/packages` | No mapping-level parameter condition | Public route allowed by SecurityConfig; no controller role restriction | `List<SportPackageResponse> getAllActivePackages()` | sportPackageService.getAllActivePackages() | 28-31 |
| GET `/api/v1/packages/sport/{sportId}` | No mapping-level parameter condition | Public route allowed by SecurityConfig; no controller role restriction | `List<SportPackageResponse> getPackagesBySport(@PathVariable Long sportId)` | sportPackageService.getPackagesBySport(sportId) | 33-36 |
| POST `/api/v1/packages` | No mapping-level parameter condition | hasRole('MANAGER') | `SportPackageResponse createPackage( @Valid @RequestBody SportPackageCreateRequest request, @AuthenticationPrincipal CustomUserDetails userDetails)` | sportPackageService.createPackage(request, userDetails.getUserAccount()) | 38-45 |
| PUT `/api/v1/packages/{id}` | No mapping-level parameter condition | hasRole('MANAGER') | `SportPackageResponse updatePackage( @PathVariable Long id, @Valid @RequestBody SportPackageUpdateRequest request, @AuthenticationPrincipal CustomUserDetails userDetails)` | sportPackageService.updatePackage(id, request, userDetails.getUserAccount()) | 47-54 |
| POST `/api/v1/packages/registrations` | No mapping-level parameter condition | hasAnyRole('MEMBER', 'RECEPTIONIST', 'MANAGER') | `PackageRegistrationResponse registerPackage( @Valid @RequestBody PackageRegistrationRequest request, @AuthenticationPrincipal CustomUserDetails userDetails)` | sportPackageService.registerPackage(request, userDetails.getUserAccount()) | 56-63 |
| GET `/api/v1/packages/registrations/my` | No mapping-level parameter condition | hasRole('MEMBER') | `List<PackageRegistrationResponse> getMyRegistrations( @AuthenticationPrincipal CustomUserDetails userDetails)` | sportPackageService.getMyRegistrations(userDetails.getUserAccount()) | 65-70 |
| GET `/api/v1/packages/registrations/member/{memberId}` | No mapping-level parameter condition | hasAnyRole('RECEPTIONIST', 'MANAGER') | `List<PackageRegistrationResponse> getMemberRegistrations(@PathVariable Long memberId)` | sportPackageService.getMemberRegistrations(memberId) | 72-76 |
| PUT `/api/v1/packages/registrations/{id}/activate` | No mapping-level parameter condition | hasAnyRole('RECEPTIONIST', 'MANAGER') | `PackageRegistrationResponse activateRegistration( @PathVariable Long id, @RequestBody(required = false) ActivatePaymentRequest request, @AuthenticationPrincipal CustomUserDetails userDetails)` | request.getPaymentMethod() : null; sportPackageService.activateRegistration(id, userDetails.getUserAccount(), actualMethod) | 78-86 |
| PUT `/api/v1/packages/registrations/{id}/cancel` | No mapping-level parameter condition | hasAnyRole('MEMBER', 'RECEPTIONIST', 'MANAGER') | `PackageRegistrationResponse cancelRegistration( @PathVariable Long id, @AuthenticationPrincipal CustomUserDetails userDetails)` | sportPackageService.cancelRegistration(id, userDetails.getUserAccount()) | 89-95 |

## ActivityLogController

[`backend/src/main/java/com/sportify/identity/controller/ActivityLogController.java`](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/java/com/sportify/identity/controller/ActivityLogController.java). EV-121 (`NX-01-evidence-index.md#ev-121` - local review reference, not published)


| Method and composed path | Parameter selection | Authorization | Handler signature and DTO/validation annotations | Service call | Lines |
| --- | --- | --- | --- | --- | --- |
| GET `/api/v1/manager/activity-logs` | No mapping-level parameter condition | hasRole('MANAGER') | `ResponseEntity<PageResponse<ActivityLogResponse>> list( @RequestParam(required = false) Long actorId, @RequestParam(required = false) String action, @RequestParam(required = false) String from, @RequestParam(required = false) String to, @RequestParam(required = false) String search, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size)` | activityLogService.list(actorId, action, from, to, search, page, size)) | 21-31 |

## AuthenticationController

[`backend/src/main/java/com/sportify/identity/controller/AuthenticationController.java`](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/java/com/sportify/identity/controller/AuthenticationController.java). EV-122 (`NX-01-evidence-index.md#ev-122` - local review reference, not published)


| Method and composed path | Parameter selection | Authorization | Handler signature and DTO/validation annotations | Service call | Lines |
| --- | --- | --- | --- | --- | --- |
| POST `/api/v1/auth/register` | No mapping-level parameter condition | Public route allowed by SecurityConfig; no controller role restriction | `ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request)` | service.register(request)) | 20-23 |
| POST `/api/v1/auth/login` | No mapping-level parameter condition | Public route allowed by SecurityConfig; no controller role restriction | `ResponseEntity<AuthResponse> authenticate(@Valid @RequestBody AuthRequest request)` | service.authenticate(request)) | 25-28 |
| POST `/api/v1/auth/refresh` | No mapping-level parameter condition | Public route allowed by SecurityConfig; no controller role restriction | `ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request)` | service.refreshToken(request)) | 30-33 |

## CoachController

[`backend/src/main/java/com/sportify/identity/controller/CoachController.java`](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/java/com/sportify/identity/controller/CoachController.java). EV-123 (`NX-01-evidence-index.md#ev-123` - local review reference, not published)


| Method and composed path | Parameter selection | Authorization | Handler signature and DTO/validation annotations | Service call | Lines |
| --- | --- | --- | --- | --- | --- |
| GET `/api/v1/coaches` | No mapping-level parameter condition | hasAnyRole('MANAGER', 'RECEPTIONIST') | `ResponseEntity<List<CoachOptionResponse>> getCoaches(@RequestParam(required = false) Long sportId)` | coachDirectoryService.getActiveCoaches(sportId)) | 26-29 |

## CoachPublicProfileController

[`backend/src/main/java/com/sportify/identity/controller/CoachPublicProfileController.java`](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/java/com/sportify/identity/controller/CoachPublicProfileController.java). EV-124 (`NX-01-evidence-index.md#ev-124` - local review reference, not published)


| Method and composed path | Parameter selection | Authorization | Handler signature and DTO/validation annotations | Service call | Lines |
| --- | --- | --- | --- | --- | --- |
| GET `/api/v1/coaches/{id}` | No mapping-level parameter condition | hasAnyRole('MEMBER', 'RECEPTIONIST', 'MANAGER') | `CoachPublicProfileResponse getProfile(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails principal)` | service.getProfile(id, principal == null ? null : principal.getUserAccount()) | 20-24 |

## ManagerCoachProfileController

[`backend/src/main/java/com/sportify/identity/controller/ManagerCoachProfileController.java`](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/java/com/sportify/identity/controller/ManagerCoachProfileController.java). EV-125 (`NX-01-evidence-index.md#ev-125` - local review reference, not published)


| Method and composed path | Parameter selection | Authorization | Handler signature and DTO/validation annotations | Service call | Lines |
| --- | --- | --- | --- | --- | --- |
| GET `/api/v1/manager/users/{id}/coach-profile` | No mapping-level parameter condition | hasRole('MANAGER') | `ResponseEntity<ManagerCoachProfileResponse> getProfile(@PathVariable Long id)` | coachProfileService.getProfile(id)) | 37-40 |
| PUT `/api/v1/manager/users/{id}/coach-profile` | No mapping-level parameter condition | hasRole('MANAGER') | `ResponseEntity<ManagerCoachProfileResponse> updateProfile( @PathVariable Long id, @Valid @RequestBody ManagerCoachProfileUpdateRequest request, @AuthenticationPrincipal CustomUserDetails principal)` | principal.getUserAccount(); coachProfileService.updateProfile(id, request, actor)) | 42-49 |
| POST `/api/v1/manager/users/{id}/coach-profile/photo` | No mapping-level parameter condition | hasRole('MANAGER') | `ResponseEntity<ManagerCoachProfileResponse> uploadPhoto( @PathVariable Long id, @RequestPart("file") MultipartFile file, @AuthenticationPrincipal CustomUserDetails principal) throws IOException` | principal.getUserAccount(); coachProfileService.uploadPhoto(id, file.getBytes(), actor)) | 52-59 |

## ManagerUserController

[`backend/src/main/java/com/sportify/identity/controller/ManagerUserController.java`](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/java/com/sportify/identity/controller/ManagerUserController.java). EV-126 (`NX-01-evidence-index.md#ev-126` - local review reference, not published)


| Method and composed path | Parameter selection | Authorization | Handler signature and DTO/validation annotations | Service call | Lines |
| --- | --- | --- | --- | --- | --- |
| GET `/api/v1/manager/users` | No mapping-level parameter condition | hasRole('MANAGER') | `ResponseEntity<PageResponse<ManagerUserResponse>> listUsers( @RequestParam(required = false) String role, @RequestParam(required = false) UserStatus status, @RequestParam(required = false) String search, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size)` | managerUserService.listUsers(role, status, search, page, size)) | 37-45 |
| POST `/api/v1/manager/users` | No mapping-level parameter condition | hasRole('MANAGER') | `ResponseEntity<ManagerUserResponse> createStaffAccount( @Valid @RequestBody StaffAccountCreateRequest request, @AuthenticationPrincipal CustomUserDetails principal)` | managerUserService.createStaffAccount(request, actor(principal))) | 47-53 |
| PATCH `/api/v1/manager/users/{id}/status` | No mapping-level parameter condition | hasRole('MANAGER') | `ResponseEntity<ManagerUserResponse> changeStatus( @PathVariable Long id, @Valid @RequestBody UserStatusUpdateRequest request, @AuthenticationPrincipal CustomUserDetails principal)` | managerUserService.changeStatus(id, request.status(), actor(principal))) | 55-61 |

## MemberController

[`backend/src/main/java/com/sportify/identity/controller/MemberController.java`](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/java/com/sportify/identity/controller/MemberController.java). EV-127 (`NX-01-evidence-index.md#ev-127` - local review reference, not published)


| Method and composed path | Parameter selection | Authorization | Handler signature and DTO/validation annotations | Service call | Lines |
| --- | --- | --- | --- | --- | --- |
| GET `/api/v1/members` | No mapping-level parameter condition | hasAnyRole('MANAGER', 'RECEPTIONIST') | `Page<MemberSearchResponse> searchMembers( @RequestParam(required = false) String query, @PageableDefault(size = 20, sort = "id") Pageable pageable)` | memberSearchService.searchMembers(query, pageable) | 33-40 |
| POST `/api/v1/members` | No mapping-level parameter condition | hasAnyRole('MANAGER', 'RECEPTIONIST') | `ResponseEntity<MemberCreateResponse> create(@Valid @RequestBody MemberCreateRequest request)` | frontDeskMemberService.create(request); response.userId() + "/overview") | 42-48 |

## PublicCoachController

[`backend/src/main/java/com/sportify/identity/controller/PublicCoachController.java`](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/java/com/sportify/identity/controller/PublicCoachController.java). EV-128 (`NX-01-evidence-index.md#ev-128` - local review reference, not published)


| Method and composed path | Parameter selection | Authorization | Handler signature and DTO/validation annotations | Service call | Lines |
| --- | --- | --- | --- | --- | --- |
| GET `/api/v1/coaches/public` | No mapping-level parameter condition | Public route allowed by SecurityConfig; no controller role restriction | `ResponseEntity<List<PublicCoachResponse>> getPublicCoaches(@RequestParam(required = false) Long sportId)` | coachDirectoryService.getPublicCoaches(sportId)) | 24-27 |

## UserAvatarController

[`backend/src/main/java/com/sportify/identity/controller/UserAvatarController.java`](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/java/com/sportify/identity/controller/UserAvatarController.java). EV-129 (`NX-01-evidence-index.md#ev-129` - local review reference, not published)


| Method and composed path | Parameter selection | Authorization | Handler signature and DTO/validation annotations | Service call | Lines |
| --- | --- | --- | --- | --- | --- |
| GET `/api/v1/users/{id}/avatar` | No mapping-level parameter condition | Public route allowed by SecurityConfig; no controller role restriction | `ResponseEntity<byte[]> getAvatar(@PathVariable Long id)` | avatarStore.findPublicCoachAvatar(id) .orElseThrow(() -> new ResourceNotFoundException("Photo not found")); avatar.contentType())) .cacheControl(CacheControl.noStore()) .header("X-Content-Type-Options", "nosniff") .body(avatar.data()) | 23-32 |

## UserController

[`backend/src/main/java/com/sportify/identity/controller/UserController.java`](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/java/com/sportify/identity/controller/UserController.java). EV-130 (`NX-01-evidence-index.md#ev-130` - local review reference, not published)


| Method and composed path | Parameter selection | Authorization | Handler signature and DTO/validation annotations | Service call | Lines |
| --- | --- | --- | --- | --- | --- |
| GET `/api/v1/users/me` | No mapping-level parameter condition | Authenticated; no controller role restriction | `ResponseEntity<UserProfileResponse> getCurrentUser(@AuthenticationPrincipal CustomUserDetails userDetails)` | userService.getCurrentUserProfile(userDetails)) | 25-28 |
| PUT `/api/v1/users/me` | No mapping-level parameter condition | Authenticated; no controller role restriction | `ResponseEntity<UserProfileResponse> updateCurrentUser( @AuthenticationPrincipal CustomUserDetails userDetails, @Valid @RequestBody UpdateProfileRequest request)` | userService.updateCurrentUserProfile(userDetails, request)) | 30-34 |
| PUT `/api/v1/users/me/password` | No mapping-level parameter condition | Authenticated; no controller role restriction | `ResponseEntity<Void> changePassword( @AuthenticationPrincipal CustomUserDetails userDetails, @Valid @RequestBody ChangePasswordRequest request)` | userService.changeCurrentUserPassword(userDetails, request) | 36-41 |

## CashFlowController

[`backend/src/main/java/com/sportify/payment/controller/CashFlowController.java`](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/java/com/sportify/payment/controller/CashFlowController.java). EV-131 (`NX-01-evidence-index.md#ev-131` - local review reference, not published)


| Method and composed path | Parameter selection | Authorization | Handler signature and DTO/validation annotations | Service call | Lines |
| --- | --- | --- | --- | --- | --- |
| GET `/api/v1/manager/finance/cash-flow/overview` | No mapping-level parameter condition | hasRole('MANAGER') | `Overview overview(@RequestParam(required = false) String month)` | service.overview(month) | 20-21 |
| GET `/api/v1/manager/finance/cash-flow/trend` | No mapping-level parameter condition | hasRole('MANAGER') | `List<Overview> trend(@RequestParam(required = false) Integer months)` | service.trend(months) | 22-23 |
| GET `/api/v1/manager/finance/cash-flow/breakdown` | No mapping-level parameter condition | hasRole('MANAGER') | `Breakdown breakdown( @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to, @RequestParam(defaultValue = "ITEM_TYPE") RevenueGroupBy groupBy)` | service.breakdown(from, to, groupBy) | 24-28 |
| GET `/api/v1/manager/finance/cash-flow/export` | No mapping-level parameter condition | hasRole('MANAGER') | `ResponseEntity<byte[]> export( @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to)` | service.export(from, to); result.fileName()).build().toString()) .body(result.content()) | 29-37 |

## InvoiceController

[`backend/src/main/java/com/sportify/payment/controller/InvoiceController.java`](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/java/com/sportify/payment/controller/InvoiceController.java). EV-132 (`NX-01-evidence-index.md#ev-132` - local review reference, not published)


| Method and composed path | Parameter selection | Authorization | Handler signature and DTO/validation annotations | Service call | Lines |
| --- | --- | --- | --- | --- | --- |
| GET `/api/v1/invoices/{id}` | No mapping-level parameter condition | hasAnyRole('MEMBER', 'RECEPTIONIST', 'MANAGER') | `InvoiceResponse getInvoice( @PathVariable Long id, @AuthenticationPrincipal CustomUserDetails userDetails)` | receiptService.getInvoice(id, userDetails.getUserAccount()) | 25-31 |
| GET `/api/v1/invoices/by-payment/{paymentId}` | No mapping-level parameter condition | hasAnyRole('MEMBER', 'RECEPTIONIST', 'MANAGER') | `InvoiceResponse getInvoiceByPayment( @PathVariable Long paymentId, @AuthenticationPrincipal CustomUserDetails userDetails)` | receiptService.getInvoiceByPayment(paymentId, userDetails.getUserAccount()) | 33-39 |

## ManagerFinanceController

[`backend/src/main/java/com/sportify/payment/controller/ManagerFinanceController.java`](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/java/com/sportify/payment/controller/ManagerFinanceController.java). EV-133 (`NX-01-evidence-index.md#ev-133` - local review reference, not published)


| Method and composed path | Parameter selection | Authorization | Handler signature and DTO/validation annotations | Service call | Lines |
| --- | --- | --- | --- | --- | --- |
| GET `/api/v1/manager/finance/overview` | No mapping-level parameter condition | hasRole('MANAGER') | `FinanceOverviewResponse overview(@RequestParam(required = false) String month)` | financeReportService.overview(month) | 45-48 |
| GET `/api/v1/manager/finance/trend` | No mapping-level parameter condition | hasRole('MANAGER') | `List<RevenueTrendPoint> trend(@RequestParam(required = false) Integer months)` | financeReportService.trend(months) | 50-53 |
| GET `/api/v1/manager/finance/transactions` | No mapping-level parameter condition | hasRole('MANAGER') | `PageResponse<FinanceTransactionResponse> transactions( @RequestParam(required = false) PaymentStatus status, @RequestParam(required = false) PaymentMethod method, @RequestParam(required = false) InvoiceItemType type, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to, @RequestParam(required = false) String search, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "" + FinanceReportService.DEFAULT_PAGE_SIZE) int size)` | financeReportService.transactions(status, method, type, from, to, search, page, size) | 55-66 |
| GET `/api/v1/manager/finance/revenue` | No mapping-level parameter condition | hasRole('MANAGER') | `RevenueBreakdownResponse revenue( @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to, @RequestParam(defaultValue = "ITEM_TYPE") RevenueGroupBy groupBy)` | financeReportService.revenue(from, to, groupBy) | 68-74 |
| GET `/api/v1/manager/finance/revenue/export` | No mapping-level parameter condition | hasRole('MANAGER') | `ResponseEntity<byte[]> exportRevenue( @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to)` | financeReportService.exportRevenue(from, to); export.fileName()).build().toString()) .body(export.content()) | 77-87 |

## MemberPaymentController

[`backend/src/main/java/com/sportify/payment/controller/MemberPaymentController.java`](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/java/com/sportify/payment/controller/MemberPaymentController.java). EV-134 (`NX-01-evidence-index.md#ev-134` - local review reference, not published)


| Method and composed path | Parameter selection | Authorization | Handler signature and DTO/validation annotations | Service call | Lines |
| --- | --- | --- | --- | --- | --- |
| GET `/api/v1/payments/my` | No mapping-level parameter condition | hasRole('MEMBER') | `PageResponse<MemberPaymentResponse> listMyPayments( @RequestParam(required = false) PaymentStatus status, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "" + ReceiptService.DEFAULT_PAGE_SIZE) int size, @AuthenticationPrincipal CustomUserDetails userDetails)` | receiptService.listMyPayments(status, page, size, userDetails.getUserAccount()) | 27-35 |

## PaymentController

[`backend/src/main/java/com/sportify/payment/controller/PaymentController.java`](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/java/com/sportify/payment/controller/PaymentController.java). EV-135 (`NX-01-evidence-index.md#ev-135` - local review reference, not published)


| Method and composed path | Parameter selection | Authorization | Handler signature and DTO/validation annotations | Service call | Lines |
| --- | --- | --- | --- | --- | --- |
| GET `/api/v1/payments/{id}` | No mapping-level parameter condition | hasAnyRole('MEMBER', 'RECEPTIONIST', 'MANAGER') | `PaymentResponse getPayment( @PathVariable Long id, @AuthenticationPrincipal CustomUserDetails userDetails)` | paymentService.getPayment(id, userDetails.getUserAccount()) | 20-26 |
| PUT `/api/v1/payments/{id}/method` | No mapping-level parameter condition | hasAnyRole('MEMBER', 'RECEPTIONIST', 'MANAGER') | `PaymentResponse updatePaymentMethod( @PathVariable Long id, @Valid @RequestBody UpdatePaymentMethodRequest request, @AuthenticationPrincipal CustomUserDetails userDetails)` | paymentService.updatePaymentMethod(id, request, userDetails.getUserAccount()) | 28-35 |

## PaymentVerificationController

[`backend/src/main/java/com/sportify/payment/controller/PaymentVerificationController.java`](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/java/com/sportify/payment/controller/PaymentVerificationController.java). EV-136 (`NX-01-evidence-index.md#ev-136` - local review reference, not published)


| Method and composed path | Parameter selection | Authorization | Handler signature and DTO/validation annotations | Service call | Lines |
| --- | --- | --- | --- | --- | --- |
| GET `/api/v1/payments` | No mapping-level parameter condition | hasAnyRole('RECEPTIONIST', 'MANAGER') | `PageResponse<StaffPaymentResponse> listPayments( @RequestParam(required = false) PaymentStatus status, @RequestParam(required = false) PaymentMethod method, @RequestParam(required = false) String search, @RequestParam(required = false) String query, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "" + PaymentVerificationService.DEFAULT_PAGE_SIZE) int size)` | search.isBlank() ? search : query; paymentVerificationService.listPayments(status, method, text, from, to, page, size) | 34-47 |
| PUT `/api/v1/payments/{id}/confirm` | No mapping-level parameter condition | hasAnyRole('RECEPTIONIST', 'MANAGER') | `StaffPaymentResponse confirmPayment( @PathVariable Long id, @Valid @RequestBody ConfirmPaymentRequest request, @AuthenticationPrincipal CustomUserDetails userDetails)` | paymentVerificationService.confirmPayment(id, request, userDetails.getUserAccount()) | 49-56 |
| PUT `/api/v1/payments/{id}/fail` | No mapping-level parameter condition | hasAnyRole('RECEPTIONIST', 'MANAGER') | `StaffPaymentResponse failPayment( @PathVariable Long id, @Valid @RequestBody FailPaymentRequest request, @AuthenticationPrincipal CustomUserDetails userDetails)` | paymentVerificationService.failPayment(id, request, userDetails.getUserAccount()) | 58-65 |

## Ownership and filter semantics for reuse

Booking /my binds the account ID from the authenticated principal. The no-page AND no-size mapping preserves an array; page/size selects PageResponse. Staff /member/{memberId} adds when/status and staff validation. Page is zero-based, size 1..100 and offset bounded. Check-in history is staff-only, either a date with optional member filter or memberId; an absent date and memberId is 400. These reads are not Member training history. EV-004 (`NX-01-evidence-index.md#ev-004` - local review reference, not published), EV-005 (`NX-01-evidence-index.md#ev-005` - local review reference, not published), EV-006 (`NX-01-evidence-index.md#ev-006` - local review reference, not published)

Coach calendar requires from/to, strict years 0001..9999 and at most 62 days inclusive. A coach can request only the current account's coach ID; manager may filter/all assigned coaches. Foreign or unassigned rosters are forbidden for coaches, and the PII query repeats session ownership. The roster contains phone and package identifiers; it is not permission to export those fields to an AI provider. EV-008 (`NX-01-evidence-index.md#ev-008` - local review reference, not published), EV-009 (`NX-01-evidence-index.md#ev-009` - local review reference, not published)

Member session discovery filters sport, dates, training format, level and coach with pagination. Member eligibility uses the principal; only staff may provide another memberId to the distinct eligibility route. Manager class/session administration has independent class, coach, sport, facility, time, capacity and conflict rules. EV-017 (`NX-01-evidence-index.md#ev-017` - local review reference, not published), EV-137 (`NX-01-evidence-index.md#ev-137` - local review reference, not published), EV-138 (`NX-01-evidence-index.md#ev-138` - local review reference, not published)

Framework endpoint GET /actuator/health is public with details disabled; other actuator endpoints are not web-exposed by application.yml. DevMembershipController is dev-profile-only and manager-only. No training-plan/result/coach-attendance/AI/chat/notification endpoint was found. EV-018 (`NX-01-evidence-index.md#ev-018` - local review reference, not published), EV-023 (`NX-01-evidence-index.md#ev-023` - local review reference, not published), EV-026 (`NX-01-evidence-index.md#ev-026` - local review reference, not published)

## DTO and validator register

Declarations below are extracted from the DTO classes imported by controllers. Nested DTOs and service validation remain authoritative in source; this list is not an approved future contract. An annotation name records source validation, not proof of an executed invalid-input scenario.
- `ActivatePaymentRequest`: @Setter @NoArgsConstructor @AllArgsConstructor @Builder private PaymentMethod paymentMethod;. EV-139 (`NX-01-evidence-index.md#ev-139` - local review reference, not published)
- `AgeGroupResponse`: AgeGroupResponse( Long id, String code, String label, Integer minAge, Integer maxAge). EV-140 (`NX-01-evidence-index.md#ev-140` - local review reference, not published)
- `BookingCreateRequest`: @NoArgsConstructor @AllArgsConstructor @Builder @Positive(message = "Member ID must be positive") private Long memberId;; @NotNull(message = "Session ID is required") @Positive(message = "Session ID must be positive") private Long sessionId;; @Positive(message = "Package registration ID must be positive") private Long packageRegistrationId;. EV-141 (`NX-01-evidence-index.md#ev-141` - local review reference, not published)
- `BookingResponse`: @Setter @NoArgsConstructor @AllArgsConstructor @Builder private Long id;; private String bookingCode;; private Long sessionId;; private LocalDate sessionDate;; private LocalTime startTime;; private LocalTime endTime;; private String sportName;; private Long sportId;; private Long classId;; private String className;; private Long coachId;; private String coachName;; private Long facilityId;; private String facilityName;; private TrainingFormat trainingType;; private Long memberId;; private String memberCode;; private String memberFullName;; private Long packageRegistrationId;; private Long packageId;; private String packageName;; private BookingStatus status;; private LocalDateTime bookedAt;; private LocalDateTime cancelledAt;; private String cancelReason;; private Long cancelledById;; private String cancelledByName;; private String cancelledByRole;. EV-142 (`NX-01-evidence-index.md#ev-142` - local review reference, not published)
- `BookingWhen`: See declared fields and nested types in source. EV-143 (`NX-01-evidence-index.md#ev-143` - local review reference, not published)
- `CardTierResponse`: @Setter @NoArgsConstructor @AllArgsConstructor @Builder private Long id;; private String code;; private String name;; private BigDecimal price;; private Integer durationMonths;; private Integer discountPercentage;; private String description;; private Boolean isActive;; private Integer tierRank;. EV-144 (`NX-01-evidence-index.md#ev-144` - local review reference, not published)
- `CheckInRequest`: @Data @NotBlank(message = "Identifier is required") private String identifier; // member_code or user_id as string; private Long bookingId;; private String note;. EV-145 (`NX-01-evidence-index.md#ev-145` - local review reference, not published)
- `CheckInResponse`: @Data @Builder private Long id;; private Long memberId;; private String memberName;; private Long membershipId;; private Long bookingId;; private String bookingCode;; private Long packageRegistrationId;; private LocalDateTime checkedInAt;; private String result;; private String denialReason;; private String note;. EV-146 (`NX-01-evidence-index.md#ev-146` - local review reference, not published)
- `ClassStatusUpdateRequest`: ClassStatusUpdateRequest( @NotNull(message = "Active flag is required") Boolean active). EV-147 (`NX-01-evidence-index.md#ev-147` - local review reference, not published)
- `CoachRosterResponse`: CoachRosterResponse(Long bookingId, String memberCode, String memberName, String memberPhone, Long packageRegistrationId, Long packageId, String packageName, LocalDateTime bookedAt). EV-148 (`NX-01-evidence-index.md#ev-148` - local review reference, not published)
- `CoachSessionResponse`: CoachSessionResponse(Long id, Long classId, String className, Long sportId, String sportName, Long coachId, String coachName, Long facilityId, String facilityName, LocalDate sessionDate, LocalTime startTime, LocalTime endTime, Integer capacity, long bookedCount, SessionStatus status). EV-149 (`NX-01-evidence-index.md#ev-149` - local review reference, not published)
- `FacilityResponse`: FacilityResponse(Long id, String name, boolean active). EV-150 (`NX-01-evidence-index.md#ev-150` - local review reference, not published)
- `FrontDeskCardResponse`: FrontDeskCardResponse(Long id, String cardCode, Long memberId, String memberCode, String memberName, String tierCode, String tierName, CardStatus status, LocalDate startDate, LocalDate endDate, BigDecimal amount, LocalDateTime requestedAt). EV-151 (`NX-01-evidence-index.md#ev-151` - local review reference, not published)
- `FrontDeskPackageResponse`: FrontDeskPackageResponse(Long id, String registrationCode, Long memberId, String memberCode, String memberName, Long packageId, String packageName, PackageRegistrationStatus status, LocalDate startDate, LocalDate endDate, Integer totalSessions, Integer remainingSessions, BigDecimal amount, LocalDateTime requestedAt). EV-152 (`NX-01-evidence-index.md#ev-152` - local review reference, not published)
- `ManagerClassResponse`: ManagerClassResponse( Long id, String code, String name, Long sportId, String sportName, Long ageGroupId, String ageGroupLabel, String level, Long coachId, String coachName, Integer maxMembers, long upcomingSessions, boolean active, LocalDateTime createdAt, LocalDateTime updatedAt). EV-153 (`NX-01-evidence-index.md#ev-153` - local review reference, not published)
- `ManagerSessionResponse`: ManagerSessionResponse( Long id, LocalDate date, LocalTime startTime, LocalTime endTime, @JsonProperty("class") ClassSummary sportClass, ResourceSummary coach, ResourceSummary facility, Integer capacity, long bookedCount, TrainingFormat trainingType, SessionStatus status, String cancelReason, LocalDateTime cancelledAt, Long cancelledByUserId). EV-154 (`NX-01-evidence-index.md#ev-154` - local review reference, not published)
- `MemberCardPurchaseRequest`: @Setter @NoArgsConstructor @AllArgsConstructor @Builder private Long memberId;; @NotNull(message = "Card tier ID is required") private Long tierId;; @JsonAlias({"method"}) private PaymentMethod paymentMethod;; @Size(max = 100, message = "Reference code must be at most 100 characters") @JsonAlias({"paymentReference", "reference"}) private String referenceCode;; @DecimalMin(value = "0.01", message = "Amount received must be greater than zero") @Digits(integer = 10, fraction = 2, message = "Amount received must have at most 10 integer digits and 2 decimals") private BigDecimal amountReceived;; @DecimalMin(value = "0.00", message = "Expected amount due must not be negative") @Digits(integer = 10, fraction = 2, message = "Expected amount due must have at most 10 integer digits and 2 decimals") private BigDecimal expectedAmountDue;. EV-155 (`NX-01-evidence-index.md#ev-155` - local review reference, not published)
- `MemberCardResponse`: @Setter @NoArgsConstructor @AllArgsConstructor @Builder private Long id;; private String cardCode;; private Long memberId;; private String memberCode;; private String memberFullName;; private Long tierId;; private String tierCode;; private String tierName;; private Integer tierRank;; private boolean upgradeRequest;; private Long replacesCardId;; private Integer discountPercentage;; private LocalDate startDate;; private LocalDate endDate;; private CardStatus status;; private BigDecimal pricePaid;; private Long paymentId;; private String paymentCode;; private Long invoiceId;; private String invoiceNumber;; private BigDecimal amountReceived;; private BigDecimal changeAmount;; private LocalDateTime createdAt;. EV-156 (`NX-01-evidence-index.md#ev-156` - local review reference, not published)
- `MemberOverviewResponse`: MemberOverviewResponse(MemberSearchResponse profile, FrontDeskCardResponse currentCard, FrontDeskCardResponse pendingCardRequest, List<FrontDeskPackageResponse> activePackageRegistrations, List<FrontDeskPackageResponse> pendingPackageRegistrations, List<FrontDeskCheckInResponse> recentCheckIns). EV-157 (`NX-01-evidence-index.md#ev-157` - local review reference, not published)
- `MembershipCardTierUpdateRequest`: @Builder @NotNull(message = "Price is required") @DecimalMin(value = "0.00", message = "Price must be greater than or equal to zero") @Digits(integer = 12, fraction = 2, message = "Price format is invalid") private BigDecimal price;; @NotNull(message = "Duration in months is required") @Min(value = 0, message = "Duration months must be non-negative") @Max(value = 120, message = "Duration months cannot exceed 120") private Integer durationMonths;; @NotNull(message = "Discount percentage is required") @Min(value = 0, message = "Discount percentage cannot be less than 0") @Max(value = 100, message = "Discount percentage cannot be greater than 100") private Integer discountPercentage;; @Size(max = 500, message = "Description must not exceed 500 characters") private String description;; @NotNull(message = "Active flag is required") private Boolean active;. EV-158 (`NX-01-evidence-index.md#ev-158` - local review reference, not published)
- `MembershipRegistrationRequest`: @Data @NotNull(message = "Plan ID is required") private Long planId;; @NotEmpty(message = "At least one sport must be selected") private List<Long> sportIds;. EV-159 (`NX-01-evidence-index.md#ev-159` - local review reference, not published)
- `MembershipResponse`: @Data @Builder private Long id;; private String registrationCode;; private Long planId;; private String planName;; private String registrationType;; private String channel;; private String status;; private BigDecimal priceAmount;; private Integer durationDays;; private LocalDate startDate;; private LocalDate endDate;; private List<String> sports;. EV-160 (`NX-01-evidence-index.md#ev-160` - local review reference, not published)
- `PackageRegistrationRequest`: @Setter @NoArgsConstructor @AllArgsConstructor @Builder private Long memberId;; @NotNull(message = "Package ID is required") private Long packageId;; private RegistrationChannel channel;; private LocalDate startDate;; @JsonAlias({"method"}) private PaymentMethod paymentMethod;; @Size(max = 100, message = "Reference code must be at most 100 characters") @JsonAlias({"paymentReference", "reference"}) private String referenceCode;; @DecimalMin(value = "0.01", message = "Amount received must be greater than zero") @Digits(integer = 10, fraction = 2, message = "Amount received must have at most 10 integer digits and 2 decimals") private BigDecimal amountReceived;; @DecimalMin(value = "0.00", message = "Expected amount due must not be negative") @Digits(integer = 10, fraction = 2, message = "Expected amount due must have at most 10 integer digits and 2 decimals") private BigDecimal expectedAmountDue;. EV-161 (`NX-01-evidence-index.md#ev-161` - local review reference, not published)
- `PackageRegistrationResponse`: @Setter @NoArgsConstructor @AllArgsConstructor @Builder private Long id;; private String registrationCode;; private Long memberId;; private String memberCode;; private String memberFullName;; private Long packageId;; private String packageCode;; private String packageName;; private String sportName;; private TrainingFormat trainingFormat;; private RegistrationChannel channel;; private BigDecimal originalPrice;; private Integer discountPercentage;; private BigDecimal paidAmount;; private Integer totalSessions;; private Integer remainingSessions;; private LocalDate startDate;; private LocalDate endDate;; private PackageRegistrationStatus status;; private Long paymentId;; private String paymentCode;; private Long invoiceId;; private String invoiceNumber;; private BigDecimal amountReceived;; private BigDecimal changeAmount;; private LocalDateTime activatedAt;; private LocalDateTime createdAt;. EV-162 (`NX-01-evidence-index.md#ev-162` - local review reference, not published)
- `PageResponse`: PageResponse<T>( List<T> content, int page, int size, long totalElements, int totalPages). EV-163 (`NX-01-evidence-index.md#ev-163` - local review reference, not published)
- `PlanCreateRequest`: PlanCreateRequest( @NotBlank(message = "Code is required") @Size(max = 30, message = "Code must be at most 30 characters") String code, @NotBlank(message = "Name is required") @Size(max = 100, message = "Name must be at most 100 characters") String name, @Size(max = 100, message = "Tagline must be at most 100 characters") String tagline, @Size(max = 500, message = "Description must be at most 500 characters") String description, @NotNull(message = "Price is required") @DecimalMin(value = "0.00", message = "Price must be greater than or equal to 0") @Digits(integer = 12, fraction = 2, message = "Price must have at most 12 integer digits and 2 decimals") BigDecimal price, @NotNull(message = "Duration (days) is required") @Min(value = 1, message = "Duration (days) must be greater than 0") Integer durationDays, @NotNull(message = "Max sports is required") @Min(value = 1, message = "Max sports must be at least 1") Integer maxSports, /* Optional, defaults to false. */ Boolean isFeatured, /* Optional, defaults to 0. */ Integer displayOrder, /* Optional, display order follows list order. */ List<@NotBlank(message = "Feature text must not be blank") @Size(max = 150, message = "Feature text must be at most 150 characters") String> features, /* Optional for DRAFT plans; must contain at least maxSports sports before activation. */ Set<@NotNull(message = "Sport id must not be null") Long> eligibleSportIds). EV-164 (`NX-01-evidence-index.md#ev-164` - local review reference, not published)
- `PlanResponse`: PlanResponse( Long id, String code, String name, String tagline, String description, BigDecimal price, Integer durationDays, Integer maxSports, Boolean isFeatured, PlanStatus status, Integer displayOrder, List<String> features, List<SportSummaryResponse> eligibleSports, Integer version). EV-165 (`NX-01-evidence-index.md#ev-165` - local review reference, not published)
- `PlanStatusUpdateRequest`: PlanStatusUpdateRequest( @NotNull(message = "Status is required") PlanStatus status, Integer version). EV-166 (`NX-01-evidence-index.md#ev-166` - local review reference, not published)
- `PlanUpdateRequest`: PlanUpdateRequest( @NotBlank(message = "Name is required") @Size(max = 100, message = "Name must be at most 100 characters") String name, @Size(max = 100, message = "Tagline must be at most 100 characters") String tagline, @Size(max = 500, message = "Description must be at most 500 characters") String description, @NotNull(message = "Price is required") @DecimalMin(value = "0.00", message = "Price must be greater than or equal to 0") @Digits(integer = 12, fraction = 2, message = "Price must have at most 12 integer digits and 2 decimals") BigDecimal price, @NotNull(message = "Duration (days) is required") @Min(value = 1, message = "Duration (days) must be greater than 0") Integer durationDays, @NotNull(message = "Max sports is required") @Min(value = 1, message = "Max sports must be at least 1") Integer maxSports, @NotNull(message = "Featured flag is required") Boolean isFeatured, @NotNull(message = "Display order is required") Integer displayOrder, @NotNull(message = "Features are required (use an empty list for none)") List<@NotBlank(message = "Feature text must not be blank") @Size(max = 150, message = "Feature text must be at most 150 characters") String> features, @NotNull(message = "Eligible sport ids are required (use an empty list for none)") Set<@NotNull(message = "Sport id must not be null") Long> eligibleSportIds, Integer version). EV-167 (`NX-01-evidence-index.md#ev-167` - local review reference, not published)
- `PublicFacilityResponse`: PublicFacilityResponse(Long id, String name, FacilityType facilityType, Long sportId, Integer capacity). EV-168 (`NX-01-evidence-index.md#ev-168` - local review reference, not published)
- `RefundCreateRequest`: @NoArgsConstructor @AllArgsConstructor @Builder @NotNull(message = "Package registration ID is required") private Long packageRegistrationId;; @NotBlank(message = "Refund reason is required") private String reason;; @Positive(message = "Amount requested must be greater than zero") @Digits(integer = 12, fraction = 2, message = "Amount requested must have at most 2 decimal places") private BigDecimal amountRequested;. EV-169 (`NX-01-evidence-index.md#ev-169` - local review reference, not published)
- `RefundQuoteResponse`: @Setter @NoArgsConstructor @AllArgsConstructor @Builder private Long registrationId;; private String registrationCode;; private String packageName;; private Long paymentId;; private String paymentCode;; private PaymentMethod paymentMethod;; private BigDecimal paymentAmount;; private BigDecimal paidAmount;; private Integer totalSessions;; private Integer remainingSessions;; private Integer upcomingBookings;; private Integer unusedSessions;; private Integer usedSessions;; private BigDecimal suggestedAmount;. EV-170 (`NX-01-evidence-index.md#ev-170` - local review reference, not published)
- `RefundResponse`: @Setter @NoArgsConstructor @AllArgsConstructor @Builder private Long id;; private String refundCode;; private Long packageRegistrationId;; private Long paymentId;; private String paymentCode;; private String registrationCode;; private Long memberId;; private String memberCode;; private String memberFullName;; private BigDecimal amountRequested;; private BigDecimal amountApproved;; private String reason;; private RefundStatus status;; private Long requestedById;; private String requestedByName;; private Long reviewedById;; private String reviewedByName;; private LocalDateTime reviewedAt;; private String managerNote;; private LocalDateTime createdAt;; private Integer cancelledBookings;. EV-171 (`NX-01-evidence-index.md#ev-171` - local review reference, not published)
- `RefundReviewRequest`: @NoArgsConstructor @AllArgsConstructor @Builder @NotNull(message = "Decision (approve or reject) is required") private Boolean approved;; @Positive(message = "Amount approved must be greater than zero") @Digits(integer = 12, fraction = 2, message = "Amount approved must have at most 2 decimal places") private BigDecimal amountApproved;; private String managerNote;. EV-172 (`NX-01-evidence-index.md#ev-172` - local review reference, not published)
- `SelfTrainingAttendanceRequest`: @NoArgsConstructor @AllArgsConstructor @Builder @NotNull(message = "Booking ID is required") private Long bookingId;; private String note;. EV-173 (`NX-01-evidence-index.md#ev-173` - local review reference, not published)
- `SelfTrainingAttendanceResponse`: @Setter @NoArgsConstructor @AllArgsConstructor @Builder private Long bookingId;; private String bookingCode;; private Long memberId;; private String memberCode;; private String memberFullName;; private String sportName;; private Integer remainingSessions;; private LocalDateTime confirmedAt;; private String recordedByName;; private String message;. EV-174 (`NX-01-evidence-index.md#ev-174` - local review reference, not published)
- `SessionCancelRequest`: SessionCancelRequest( @NotBlank(message = "Cancellation reason is required") @Size(max = 255, message = "Cancellation reason must be at most 255 characters") String reason). EV-175 (`NX-01-evidence-index.md#ev-175` - local review reference, not published)
- `SessionCreateRequest`: SessionCreateRequest( LocalDate date, Set<DayOfWeek> daysOfWeek, LocalDate fromDate, LocalDate toDate, @NotNull(message = "Start time is required") LocalTime startTime, @NotNull(message = "End time is required") LocalTime endTime, @NotNull(message = "Facility is required") Long facilityId, Long coachId, @NotNull(message = "Capacity is required") @Min(value = 1, message = "Capacity must be at least 1") Integer capacity, @NotNull(message = "Training type is required") TrainingFormat trainingType, SessionStatus status). EV-176 (`NX-01-evidence-index.md#ev-176` - local review reference, not published)
- `SessionDetailResponse`: SessionDetailResponse(SessionResponse session, boolean memberContext, BookingStatus bookingStatus, Boolean alreadyBooked, Boolean eligible, EligibilityReason reason, List<SuitableRegistration> suitableRegistrations). EV-177 (`NX-01-evidence-index.md#ev-177` - local review reference, not published)
- `SessionResponse`: SessionResponse(Long id, Long sportId, String sportName, Long classId, String className, TrainingFormat trainingFormat, String level, Long coachId, String coachName, Long facilityId, String facilityName, LocalDate sessionDate, LocalTime startTime, LocalTime endTime, int capacity, long bookedCount, long freeSeats, boolean full, SessionStatus status). EV-178 (`NX-01-evidence-index.md#ev-178` - local review reference, not published)
- `SessionSearchQuery`: SessionSearchQuery(Long sportId, LocalDate from, LocalDate to, TrainingFormat format, ClassLevel level, Long coachId, int page, int size). EV-179 (`NX-01-evidence-index.md#ev-179` - local review reference, not published)
- `SessionUpdateRequest`: SessionUpdateRequest( @NotNull(message = "Date is required") LocalDate date, @NotNull(message = "Start time is required") LocalTime startTime, @NotNull(message = "End time is required") LocalTime endTime, @NotNull(message = "Facility is required") Long facilityId, Long coachId, @NotNull(message = "Capacity is required") @Min(value = 1, message = "Capacity must be at least 1") Integer capacity). EV-180 (`NX-01-evidence-index.md#ev-180` - local review reference, not published)
- `SportClassRequest`: SportClassRequest( @NotBlank(message = "Class name is required") @Size(max = 100, message = "Class name must be at most 100 characters") String name, @NotNull(message = "Sport is required") Long sportId, @NotNull(message = "Age group is required") Long ageGroupId, @NotNull(message = "Level is required") ClassLevel level, @NotNull(message = "Maximum members is required") @Min(value = 1, message = "Maximum members must be at least 1") @Max(value = 100, message = "Maximum members must be at most 100") Integer maxMembers, @NotNull(message = "Assigned coach is required") Long coachId). EV-181 (`NX-01-evidence-index.md#ev-181` - local review reference, not published)
- `SportPackageCreateRequest`: @NoArgsConstructor @AllArgsConstructor @Builder @NotBlank(message = "Package code is required") private String code;; @NotBlank(message = "Package name is required") private String name;; @NotNull(message = "Sport ID is required") private Long sportId;; @NotNull(message = "Training format is required") private TrainingFormat trainingFormat;; @NotNull(message = "Duration in days is required") @Min(value = 1, message = "Duration must be at least 1 day") private Integer durationDays;; @NotNull(message = "Session count is required") @Min(value = 1, message = "Session count must be at least 1") private Integer sessionCount;; private Integer sessionMinutes;; @NotNull(message = "Price amount is required") @DecimalMin(value = "0.0", inclusive = true, message = "Price amount must be non-negative") private BigDecimal priceAmount;; private String description;. EV-182 (`NX-01-evidence-index.md#ev-182` - local review reference, not published)
- `SportPackageResponse`: @Setter @NoArgsConstructor @AllArgsConstructor @Builder private Long id;; private String code;; private String name;; private Long sportId;; private String sportName;; private String sportCode;; private TrainingFormat trainingFormat;; private Integer durationDays;; private Integer sessionCount;; private Integer sessionMinutes;; private BigDecimal priceAmount;; private String description;; private Boolean isActive;. EV-183 (`NX-01-evidence-index.md#ev-183` - local review reference, not published)
- `SportPackageUpdateRequest`: @AllArgsConstructor @Builder @NotBlank(message = "Package name is required") @Size(max = 100, message = "Package name must not exceed 100 characters") private String name;; @NotNull(message = "Price is required") @DecimalMin(value = "0.01", message = "Price must be greater than zero") @Digits(integer = 12, fraction = 2, message = "Price format is invalid") private BigDecimal price;; @NotNull(message = "Session count is required") @Min(value = 1, message = "Session count must be at least 1") @Max(value = 1000, message = "Session count cannot exceed 1000") private Integer sessionCount;; @NotNull(message = "Duration in days is required") @Min(value = 1, message = "Duration must be at least 1 day") @Max(value = 3650, message = "Duration in days cannot exceed 3650") private Integer durationDays;; @NotNull(message = "Session minutes is required") private Integer sessionMinutes;; @NotNull(message = "Active flag is required") private Boolean active;. EV-184 (`NX-01-evidence-index.md#ev-184` - local review reference, not published)
- `SportResponse`: SportResponse( Long id, String code, String name, SportVenueType venueType, String description, String imageUrl, Integer displayOrder). EV-185 (`NX-01-evidence-index.md#ev-185` - local review reference, not published)
- `ActivityLogResponse`: ActivityLogResponse(Long id, LocalDateTime createdAt, Long actorId, String actorName, String actorRole, String action, String entityType, Long entityId, String shortDetail). EV-186 (`NX-01-evidence-index.md#ev-186` - local review reference, not published)
- `AuthRequest`: @AllArgsConstructor @NoArgsConstructor @NotBlank(message = "Email is required") @Email(message = "Email is invalid") private String email;; @NotBlank(message = "Password is required") private String password;. EV-187 (`NX-01-evidence-index.md#ev-187` - local review reference, not published)
- `AuthResponse`: @Data @Builder @AllArgsConstructor @NoArgsConstructor private String accessToken;; private String refreshToken;. EV-188 (`NX-01-evidence-index.md#ev-188` - local review reference, not published)
- `ChangePasswordRequest`: @Getter @Setter @NotBlank(message = "Current password is required") private String currentPassword;; @NotBlank(message = "New password is required") @StrongPassword private String newPassword;; @NotBlank(message = "Confirm password is required") private String confirmPassword;. EV-189 (`NX-01-evidence-index.md#ev-189` - local review reference, not published)
- `CoachOptionResponse`: CoachOptionResponse( Long id, String fullName, String headline, String specialties, Integer yearsExperience, List<Long> sportIds). EV-190 (`NX-01-evidence-index.md#ev-190` - local review reference, not published)
- `CoachPublicProfileResponse`: CoachPublicProfileResponse(Long id, String name, String headline, String bio, Integer yearsExperience, String specialties, List<TaughtSport> sports, List<Certification> certifications, PageResponse<SessionResponse> upcomingSessions). EV-191 (`NX-01-evidence-index.md#ev-191` - local review reference, not published)
- `ManagerCoachProfileResponse`: ManagerCoachProfileResponse( Long userId, String fullName, String avatarUrl, String headline, String bio, Integer yearsExperience, String specialties, boolean isPublic, int displayOrder). EV-192 (`NX-01-evidence-index.md#ev-192` - local review reference, not published)
- `ManagerCoachProfileUpdateRequest`: ManagerCoachProfileUpdateRequest( @Size(max = 500, message = "Photo link must be at most 500 characters") @Pattern(regexp = "^\\s*$|^https?://\\S+$|^/api/v1/users/\\d+/avatar(\\?v=\\d+)?$|^/images/coaches/[a-z0-9-]+\\.(jpe?g|png|webp)$", message = "Photo link must be an http(s) link, an uploaded photo or a demo photo path") String avatarUrl, @Size(max = 150, message = "Headline must be at most 150 characters") String headline, @Size(max = 1000, message = "Bio must be at most 1000 characters") String bio, @Min(value = 0, message = "Years of experience cannot be negative") @Max(value = 60, message = "Years of experience must be at most 60") Integer yearsExperience, @Size(max = 255, message = "Specialties must be at most 255 characters") String specialties, @NotNull(message = "Show on Home is required") Boolean isPublic, @NotNull(message = "Display order is required") @Min(value = 0, message = "Display order cannot be negative") @Max(value = 999, message = "Display order must be at most 999") Integer displayOrder). EV-193 (`NX-01-evidence-index.md#ev-193` - local review reference, not published)
- `ManagerUserResponse`: ManagerUserResponse( Long id, String fullName, String email, String phone, String role, String status, LocalDateTime createdAt). EV-194 (`NX-01-evidence-index.md#ev-194` - local review reference, not published)
- `MemberCreateRequest`: MemberCreateRequest( @NotBlank(message = "Full name is required") @Size(max = 100, message = "Full name must be at most 100 characters") String fullName, @NotBlank(message = "Phone is required") @Pattern(regexp = "^\\+?\\d{8,15}$", message = "Phone must be 8-15 digits with optional leading +") String phone, @NotBlank(message = "Email is required") @Email(message = "Email is invalid") @Size(max = 255, message = "Email must be at most 255 characters") String email). EV-195 (`NX-01-evidence-index.md#ev-195` - local review reference, not published)
- `MemberCreateResponse`: MemberCreateResponse(Long userId, String memberCode, String fullName, String phone, String email, String status, String temporaryPassword). EV-196 (`NX-01-evidence-index.md#ev-196` - local review reference, not published)
- `MemberSearchResponse`: @Data @Builder private Long userId;; private String memberCode;; private String fullName;; private String email;; private String phone;; private String currentLevel;; private String cardTier;; private String status;. EV-197 (`NX-01-evidence-index.md#ev-197` - local review reference, not published)
- `PublicCoachResponse`: PublicCoachResponse( Long id, String fullName, String headline, String specialties, Integer yearsExperience, String bio, String avatarUrl, List<Long> sportIds). EV-198 (`NX-01-evidence-index.md#ev-198` - local review reference, not published)
- `RefreshTokenRequest`: @Builder @AllArgsConstructor @NoArgsConstructor @NotBlank(message = "Refresh token is required") private String refreshToken;. EV-199 (`NX-01-evidence-index.md#ev-199` - local review reference, not published)
- `RegisterRequest`: @AllArgsConstructor @NoArgsConstructor @NotBlank(message = "Full name is required") @Size(max = 100, message = "Full name must be at most 100 characters") private String fullName;; @NotBlank(message = "Email is required") @Email(message = "Email is invalid") @Size(max = 255, message = "Email must be at most 255 characters") private String email;; @NotBlank(message = "Password is required") @StrongPassword private String password;; @Size(max = 20, message = "Phone must be at most 20 characters") @Pattern(regexp = "^\\+?\\d{8,15}$", message = "Phone must be 8-15 digits with optional leading +") private String phone;. EV-200 (`NX-01-evidence-index.md#ev-200` - local review reference, not published)
- `StaffAccountCreateRequest`: StaffAccountCreateRequest( @NotBlank(message = "Full name is required") @Size(max = 100, message = "Full name must be at most 100 characters") String fullName, @NotBlank(message = "Email is required") @Email(message = "Email is invalid") @Size(max = 255, message = "Email must be at most 255 characters") String email, @NotBlank(message = "Phone is required") @Size(max = 20, message = "Phone must be at most 20 characters") @Pattern(regexp = "^\\+?\\d{8,15}$", message = "Phone must be 8-15 digits with optional leading +") String phone, @NotBlank(message = "Temporary password is required") @StrongPassword String temporaryPassword, @NotBlank(message = "Role is required") @Size(max = 20, message = "Role must be at most 20 characters") String role, @Size(max = 20, message = "At most 20 sports can be assigned") List<@NotNull(message = "Sport id must not be null") Long> sportIds). EV-201 (`NX-01-evidence-index.md#ev-201` - local review reference, not published)
- `UpdateProfileRequest`: @Getter @JsonIgnore private final Set<String> suppliedFields = new HashSet<>();; @Size(max = 100, message = "Full name must be at most 100 characters") @Pattern(regexp = "(?s).*[^\\p{javaWhitespace}].*", message = "Full name is required") private String fullName;; @Pattern(regexp = "^\\+?\\d{8,15}$", message = "Phone must be 8-15 digits with optional leading +") private String phone;; @Past(message = "Date of birth must be before today") private LocalDate dateOfBirth;; @Size(max = 255, message = "Address must be at most 255 characters") private String address;; @Pattern(regexp = "IMPROVE_FITNESS|BUILD_SKILLS|COMPETITION", message = "Training goal is invalid") private String mainGoal;; @Pattern(regexp = "BEGINNER|INTERMEDIATE|ADVANCED", message = "Experience level is invalid") private String currentLevel;. EV-202 (`NX-01-evidence-index.md#ev-202` - local review reference, not published)
- `UserProfileResponse`: @Data @Builder @NoArgsConstructor @AllArgsConstructor private Long id;; private String email;; private String fullName;; private String phone;; private String role;; private String status;; private LocalDate dateOfBirth;; private String address;; private String mainGoal;; private String currentLevel;; private String memberCode;; private ProfileCardResponse membershipCard;; private List<SportSummaryResponse> sports;. EV-203 (`NX-01-evidence-index.md#ev-203` - local review reference, not published)
- `UserStatusUpdateRequest`: UserStatusUpdateRequest( @NotNull(message = "Status is required") UserStatus status). EV-204 (`NX-01-evidence-index.md#ev-204` - local review reference, not published)
- `ConfirmPaymentRequest`: @AllArgsConstructor @Builder @NotNull(message = "Payment method is required") @JsonAlias({"paymentMethod"}) private PaymentMethod method;; @Size(max = 100, message = "Reference code must be at most 100 characters") @JsonAlias({"paymentReference", "reference"}) private String referenceCode;; @NotNull(message = "Amount received is required") @DecimalMin(value = "0.01", message = "Amount received must be greater than zero") @Digits(integer = 10, fraction = 2, message = "Amount received must have at most 10 integer digits and 2 decimals") private BigDecimal amountReceived;; @Size(max = 255, message = "Note must be at most 255 characters") private String note;. EV-205 (`NX-01-evidence-index.md#ev-205` - local review reference, not published)
- `FailPaymentRequest`: @AllArgsConstructor @Builder @NotBlank(message = "Reason is required") @Size(max = 255, message = "Reason must be at most 255 characters") private String reason;. EV-206 (`NX-01-evidence-index.md#ev-206` - local review reference, not published)
- `InvoiceResponse`: @Setter @NoArgsConstructor @AllArgsConstructor @Builder private Long id;; private String invoiceNumber;; private InvoiceStatus status;; private LocalDateTime issueDate;; private Long paymentId;; private String paymentCode;; private PaymentMethod paymentMethod;; private String paymentReference;; private LocalDateTime paymentTime;; private Long memberId;; private String memberCode;; private String memberFullName;; private BigDecimal subtotalAmount;; private BigDecimal discountAmount;; private BigDecimal taxAmount;; private BigDecimal totalAmount;; private List<InvoiceLineResponse> lines;. EV-207 (`NX-01-evidence-index.md#ev-207` - local review reference, not published)
- `MemberPaymentResponse`: @Setter @NoArgsConstructor @AllArgsConstructor @Builder private Long id;; private String code;; private PaymentStatus status;; private BigDecimal amount;; private PaymentMethod method;; private InvoiceItemType itemType;; private Long itemReferenceId;; private String itemCode;; private String itemDescription;; private BigDecimal originalPrice;; private BigDecimal discount;; private LocalDateTime time;; private LocalDateTime paymentTime;; private LocalDateTime createdAt;; private Long memberId;; private String memberCode;; private Long invoiceId;; private String invoiceNumber;. EV-208 (`NX-01-evidence-index.md#ev-208` - local review reference, not published)
- `PaymentResponse`: @Setter @NoArgsConstructor @AllArgsConstructor @Builder private Long id;; private String code;; private PaymentStatus status;; private BigDecimal amount;; private PaymentMethod method;; private String itemDescription;; private BigDecimal originalPrice;; private BigDecimal discount;; private LocalDateTime paymentTime;; @JsonInclude(JsonInclude.Include.NON_NULL) private BankTransferInstructions bankTransferInstructions;; private Long memberId;; private String memberCode;; private String notes;; private LocalDateTime createdAt;; private LocalDateTime updatedAt;. EV-209 (`NX-01-evidence-index.md#ev-209` - local review reference, not published)
- `StaffPaymentResponse`: @Setter @NoArgsConstructor @AllArgsConstructor @Builder private Long id;; private String code;; private PaymentStatus status;; private BigDecimal amount;; private PaymentMethod method;; private String referenceCode;; private String notes;; private LocalDateTime paymentTime;; private Long memberId;; private String memberCode;; private String memberFullName;; private InvoiceItemType itemType;; private Long itemReferenceId;; private String itemCode;; private String itemDescription;; private String itemStatus;; private BigDecimal originalPrice;; private BigDecimal discount;; private Long confirmedByUserId;; private String confirmedByName;; private Long invoiceId;; private String invoiceNumber;; private LocalDateTime createdAt;; private LocalDateTime updatedAt;. EV-210 (`NX-01-evidence-index.md#ev-210` - local review reference, not published)
- `UpdatePaymentMethodRequest`: @AllArgsConstructor @Builder @NotNull(message = "Payment method is required") @JsonAlias({"method", "paymentMethod"}) private PaymentMethod paymentMethod;. EV-211 (`NX-01-evidence-index.md#ev-211` - local review reference, not published)

## Existing client and regression constraints

Reception uses /check-ins, coach uses /coach/sessions and roster, and Member booking clients expect the legacy array response. Preserve singular check-in compatibility, principal ownership, manager session cancellation, package balance and payment/refund/invoice behavior. Client inspection proves consumption in source only. EV-033 (`NX-01-evidence-index.md#ev-033` - local review reference, not published), EV-034 (`NX-01-evidence-index.md#ev-034` - local review reference, not published), EV-035 (`NX-01-evidence-index.md#ev-035` - local review reference, not published)

## PROPOSED_ONLY future contract groups

F4-PLAN, F4-RESULT, F4-ATTEND, F4-PROGRESS, F5-REC and F6-CHAT remain pending workbook groups. No new method/path/DTO is approved or implemented by NX-01. NX-02 establishes semantics, NX-03 and relevant FE owners agree APIs, and NX-04 handles later schema design after review. EV-002 (`NX-01-evidence-index.md#ev-002` - local review reference, not published)

## Round 2 independent coverage verification

The current primary extractor and independently implemented comparator agree on 37 controller files, 103 method mapping declarations, 31 class mapping declarations and 105 composed static records. These correspond to 103 distinct HTTP-method/path pairs before query-parameter selection. Alias expansion and the two booking array/page selection variants explain why these populations differ. 1 security/routing configuration file is separately inventoried.

Each identity includes source file and line; route identity also includes method, composed path and mapping-level params. Profiles are preserved, including dev-only DevMembershipController. Class/method URL arrays and HTTP-method cross products are tested in isolated fixtures. No inherited/composed custom controller route was found in this source scope; unsupported fixture forms produce UNVERIFIED instead of a guessed route. Source context-path/servlet-path overrides were not found in application config, but environment/deployment overrides and complete Spring registration are UNVERIFIED. Health is framework-managed and outside the source-controller population.

Fresh metadata: routes (`evidence/routes.json` - local review reference, not published), controller/config manifest (`evidence/api-controller-file-manifest.json` - local review reference, not published), independent mapping reconciliation (`evidence/api-mapping-reconciliation.json` - local review reference, not published). The detailed unchanged DTO/handler/security descriptions above remain tied to the same SHA and current source hashes.
