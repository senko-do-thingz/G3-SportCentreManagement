# Sportify Center Backend (G3-SportCentreManagement)

[![CI/CD](https://github.com/senko-do-thingz/G3-SportCentreManagement/actions/workflows/ci-cd.yml/badge.svg)](https://github.com/senko-do-thingz/G3-SportCentreManagement/actions/workflows/ci-cd.yml)

Welcome to the **Sportify Center** backend repository. This project aims to build a comprehensive backend system for managing a multi-sport facility, handling memberships, class bookings, payments, and AI-driven workout recommendations. 

*Note: This project is currently in the initial foundation and data modeling phase.*

## 🎯 Module Status

| Module | Status |
|---|---|
| **Identity & Access** | Implemented - Fixed roles (MEMBER, COACH, RECEPTIONIST, MANAGER), profiles, validation |
| **Catalog & Membership** | In Progress - Refreshed with Sport Packages, Membership Cards, Multi-package, Check-in |
| **Classes & Booking** | Designed - Updated for coach-led vs self-training sessions and package reservations |
| **Payments & Reports** | Designed - Updated for package invoices, card discounts, and refund workflow |
| **Training & Progress** | Designed - Updated for coach sessions and receptionist self-training attendance |
| **AI Workout Recommendation** | Planned - Goal-driven recommendations and coach exercise drafts |
| **AI Assistant & Support** | Planned - Conversational support and receptionist inbox |
| **Security & JWT** | Implemented - Role-based endpoint authorization |

## 🛠 Technology Stack

Currently active technologies in the project:
- **Language:** Java 25
- **Framework:** Spring Boot 3.2.x
- **Persistence:** Spring Data JPA (Hibernate)
- **Database:** Microsoft SQL Server 2022
- **Database Migrations:** Flyway
- **Utilities:** MapStruct, Lombok
- **Testing:** JUnit 5, Testcontainers (MSSQL), JaCoCo

## 📁 Project Structure

The project follows a strict module-driven structure:
- `src/main/java/com/sportify/`
  - `identity/` - User accounts, profiles, roles, permissions
  - `core/` - Common utilities, audit logging
- `src/test/java/com/sportify/` - **100% mirrored** test directory structure

## 📚 Documentation

Detailed documentation regarding the database design and architectural decisions can be found in the `docs/` directory.

- [Database Design & Schema](docs/database/README.md)
- [Architecture & Project Structure Guidelines](docs/architecture/project_structure.md)

## 🚀 Getting Started

### Prerequisites
- JDK 25
- Maven 3.9+
- Docker & Docker Compose (for local database)

### Setup & Run

#### 1. Database Setup (Chạy database)
You have two options to run the database:

**Option A: Local SQL Server (Recommended for local dev without Docker)**
1. Enable **SQL Server and Windows Authentication mode** in SSMS (Server Properties -> Security).
2. Enable the `sa` account and set a password: `ALTER LOGIN sa ENABLE; ALTER LOGIN sa WITH PASSWORD = 'YourPassword';`
3. Create the database: `CREATE DATABASE sportify;`
4. Enable TCP/IP on port 1433 in SQL Server Configuration Manager.
5. Restart the SQL Server service.
6. Copy `.env.example` to a new file named `.env`, fill in your local `DB_PASSWORD`, and generate a secure `JWT_SECRET` (e.g., using `openssl rand -base64 32`).

**Option B: Docker Compose (Optional)**
If you prefer Docker, you can start the database using:
```bash
docker compose up -d
```
*(This will automatically create the `sportify` database)*
Copy `.env.example` to `.env` and configure the credentials.

#### 2. Run the Application
1. Build the project and run tests:
   ```bash
   mvn clean verify
   ```
   *Note: Integration tests use Testcontainers. If Docker is running, tests will spin up an isolated MSSQL database. If Docker is NOT installed or running, those specific tests will automatically be skipped.*
2. Run the application (default port is `8080`):
   ```bash
   mvn spring-boot:run
   ```
   *Database migrations are automatically applied via Flyway on startup.*

   Note: The `dev` profile is active by default in `application.yml`, which enables development endpoints (like manual membership activation). For production, you must set `SPRING_PROFILES_ACTIVE=prod`.

## 📏 Development Guidelines

- **MapStruct**: Use MapStruct for all DTO-Entity mappings.
- **Service Layer**: Strict separation of Interface and Implementation (e.g., `UserService` and `UserServiceImpl`).
- **Test Mirroring**: Every test class must reflect the package structure of the main class.
- **Conventional Commits**: Use `feat`, `fix`, `chore`, `build`, `docs`, `test` prefixes. Each task group must have its own commit.

## 👥 Team

- **Author:** Senko ([@senko-do-thingz](https://github.com/senko-do-thingz))
- **Contributors:** [Placeholder for team members]
