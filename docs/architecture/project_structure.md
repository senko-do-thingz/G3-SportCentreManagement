# Sportify Center - Project Structure

This document outlines the proposed package structure for the Sportify Center Spring Boot application. 

Given the scale of the system (53 tables across 8 logical modules), the project should adopt a **Module-Driven (or Feature-Driven) Package Structure**. Instead of grouping files by technical layers (e.g., placing all controllers in one massive `controller` folder), we group them by business domain. This makes the codebase much easier to navigate, maintain, and scale.

## Core Architectural Standards

1. **MapStruct for Mappers**: Every module must contain a `mapper/` package. All entity-to-DTO and DTO-to-entity conversions must be done using MapStruct interfaces. Business logic services and controllers must not contain manual mapping code to prevent "Fat Services".
2. **Interface-Implementation Separation**: The `service/` layer must strictly follow the Interface-Implementation pattern. Service interfaces are placed directly in the `service/` package, while their concrete implementations reside in a nested `impl/` package. This optimizes Dependency Injection and makes unit testing with Mocks straightforward.
3. **Mirrored Test Structure**: The directory structure inside `src/test/java/com/sportify/` must mirror `src/main/java/com/sportify/` exactly (100% identically) to ensure tests are logically mapped to the classes they verify.

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
        └── java/com/sportify/     <-- Unit and integration tests (100% mirrored structure)
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
│   ├── mapper/                    <-- MapStruct mappers (e.g., UserMapper)
│   ├── dto/                       <-- Java 17 Records for requests and responses
│   ├── service/                   <-- Business logic interfaces (e.g., AuthService)
│   │   └── impl/                  <-- Concrete implementations (e.g., AuthServiceImpl)
│   └── controller/                <-- REST APIs (/api/v1/auth, /api/v1/users)
│
├── catalog/                       <-- Module 2: Catalog & Membership
│   ├── entity/                    
│   ├── repository/
│   ├── mapper/
│   ├── dto/
│   ├── service/
│   │   └── impl/
│   └── controller/
│
├── booking/                       <-- Module 3: Classes & Booking
│   ├── entity/                    <-- SportClass, ClassSession, Booking, WaitlistEntry
│   ├── repository/                <-- BookingRepository
│   ├── mapper/                    <-- BookingMapper, ClassSessionMapper
│   ├── dto/                       <-- BookingRequest, BookingResponse
│   ├── service/                   <-- BookingService, ClassSessionService
│   │   └── impl/                  <-- BookingServiceImpl, ClassSessionServiceImpl
│   └── controller/                <-- BookingController, ClassSessionController
│
├── payment/                       <-- Module 4: Payments & Reports
│   ├── entity/                    
│   ├── repository/
│   ├── mapper/
│   ├── dto/
│   ├── service/                   
│   │   └── impl/                  
│   └── controller/
│
├── training/                      <-- Module 5: Training & Progress
│   ├── entity/                    
│   ├── repository/
│   ├── mapper/
│   ├── dto/
│   ├── service/
│   │   └── impl/
│   └── controller/
│
├── ai_recommendation/             <-- Module 6: AI Workout Recommendation
│   ├── entity/                    
│   ├── repository/
│   ├── mapper/
│   ├── dto/
│   ├── service/                   
│   │   └── impl/                  
│   └── controller/
│
├── support/                       <-- Module 7: AI Assistant & Support
│   ├── entity/                    
│   ├── repository/
│   ├── mapper/
│   ├── dto/
│   ├── service/                   
│   │   └── impl/                  
│   └── controller/
│
└── notification/                  <-- Module 8: Notifications & System
    ├── entity/                    
    ├── repository/
    ├── mapper/
    ├── dto/
    ├── service/                   
    │   └── impl/                  
    └── controller/
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
4. **Clean Testing:** The 100% mirrored `src/test/` structure ensures test coverage is easy to verify and mock dependencies correctly map to their target components.
