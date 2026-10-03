# Sportify Center - Project Structure

This document outlines the proposed package structure for the Sportify Center Spring Boot application. 

Given the scale of the system (53 tables across 8 logical modules), the project should adopt a **Module-Driven (or Feature-Driven) Package Structure**. Instead of grouping files by technical layers (e.g., placing all controllers in one massive `controller` folder), we group them by business domain. This makes the codebase much easier to navigate, maintain, and scale.

## Root Directory

```text
G3-SportCentreManagement/
├── .gitignore
├── README.md
├── pom.xml                        <-- Maven dependencies and build config
├── docs/                          <-- ERD, database design, and architecture docs
├── logs/                          <-- AI audit logs and generated reports
└── src/
    ├── main/
    │   ├── java/com/sportify/     <-- Java source code root
    │   └── resources/             <-- Configuration and static assets
    └── test/
        └── java/com/sportify/     <-- Unit and integration tests
```

## Java Source Code (`src/main/java/com/sportify/`)

```text
com.sportify
├── SportifyApplication.java       <-- Spring Boot main class
│
├── core/                          <-- Cross-cutting concerns and shared configurations
│   ├── config/                    <-- SecurityConfig, SwaggerConfig, AsyncConfig
│   ├── security/                  <-- JWT filters, CustomUserDetailsService
│   ├── exception/                 <-- GlobalExceptionHandler, custom business exceptions
│   ├── audit/                     <-- AuditorAware implementation, BaseAuditableEntity
│   └── common/                    <-- Shared utilities, constants, base DTOs
│
├── identity/                      <-- Module 1: Identity & Access
│   ├── entity/                    <-- UserAccount, Role, MemberProfile, ActivityLog
│   ├── repository/                <-- Spring Data JPA interfaces
│   ├── service/                   <-- Business logic (AuthService, UserManagementService)
│   ├── controller/                <-- REST APIs (/api/v1/auth, /api/v1/users)
│   └── dto/                       <-- Java 17 Records for requests and responses
│
├── catalog/                       <-- Module 2: Catalog & Membership
│   ├── entity/                    <-- Sport, Facility, MembershipPlan, Membership
│   ├── repository/
│   ├── service/
│   ├── controller/
│   └── dto/
│
├── booking/                       <-- Module 3: Classes & Booking
│   ├── entity/                    <-- SportClass, ClassSession, Booking, WaitlistEntry
│   ├── repository/
│   ├── service/                   <-- Handles concurrency (reserving seats)
│   ├── controller/
│   └── dto/
│
├── payment/                       <-- Module 4: Payments & Reports
│   ├── entity/                    <-- Payment, Invoice, vw_paid_payment
│   ├── repository/
│   ├── service/                   <-- Handles payment confirmation and revenue calculation
│   ├── controller/
│   └── dto/
│
├── training/                      <-- Module 5: Training & Progress
│   ├── entity/                    <-- SessionPlan, AttendanceRecord, SessionResult
│   ├── repository/
│   ├── service/
│   ├── controller/
│   └── dto/
│
├── ai_recommendation/             <-- Module 6: AI Workout Recommendation
│   ├── entity/                    <-- WorkoutPlan, RecommendationRuleSet
│   ├── repository/
│   ├── service/                   <-- AI integration service and generation logic
│   ├── controller/
│   └── dto/
│
├── support/                       <-- Module 7: AI Assistant & Support
│   ├── entity/                    <-- AiConversation, SupportRequest, AssistantSetting
│   ├── repository/
│   ├── service/                   <-- LLM chat completion logic and support ticket routing
│   ├── controller/
│   └── dto/
│
└── notification/                  <-- Module 8: Notifications & System
    ├── entity/                    <-- Notification, Announcement, SystemSetting
    ├── repository/
    ├── service/                   <-- Async notification sender, settings cache
    ├── controller/
    └── dto/
```

## Resources (`src/main/resources/`)

```text
resources/
├── application.yml                <-- Main configuration (port, datasource, jpa settings)
├── application-dev.yml            <-- Local development overrides
├── application-prod.yml           <-- Production environment overrides
│
├── db/
│   └── migration/                 <-- Flyway database migration scripts
│       ├── V1__init_schema.sql
│       └── V2__seed_reference_data.sql
│
└── i18n/                          <-- Optional: localization files (messages_vi.properties)
```

## Why this structure?

1. **High Cohesion:** Everything related to `Booking` (entities, controllers, business rules) lives in one folder. If you need to change how booking works, you only look in one place.
2. **Encapsulation:** You can make classes package-private inside a module so they cannot be accidentally bypassed by other modules.
3. **Future Microservices Readiness:** If the system grows massively and the AI or Payment module needs to be extracted into a separate microservice, it is easily detached because its code is already isolated.
