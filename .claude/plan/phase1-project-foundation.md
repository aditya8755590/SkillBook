# Implementation Plan: Phase 1 — Project Foundation

## Task Type

- [x] Backend (Java/Spring Boot)
- [x] Fullstack scaffolding (monorepo structure)

---

## Current State

| Item       | Status                                                             |
| ---------- | ------------------------------------------------------------------ |
| Repository | Empty — only `README.md` with outdated Node.js/Express description |
| Java       | OpenJDK 21 (Temurin) ✅                                            |
| Maven      | 3.9.16 ✅                                                          |
| PostgreSQL | Required — assume running locally on `localhost:5432`              |
| Git        | Clean `main` branch, 2 commits                                     |

---

## Technical Decisions (with rationale)

### 1. Project Name & Group ID

| Value                         | Rationale                          |
| ----------------------------- | ---------------------------------- |
| ArtifactId: `skillbook`       | Matches project name               |
| GroupId: `com.skillbook`      | Standard Java convention           |
| Base package: `com.skillbook` | Clean root for all domain packages |

### 2. Java Version

| Choice  | Why                                                                                                                                           |
| ------- | --------------------------------------------------------------------------------------------------------------------------------------------- |
| Java 21 | You have Temurin 21 installed; Spring Boot 3.3+ supports it fully. Using 17 would also work but 21 gives you virtual threads later if needed. |

### 3. Spring Boot Version

| Choice                            | Why                                                                      |
| --------------------------------- | ------------------------------------------------------------------------ |
| Spring Boot 3.3.x (latest stable) | Latest stable line, excellent JPA + Security support, active maintenance |

### 4. Build Tool

| Choice | Why                                                                 |
| ------ | ------------------------------------------------------------------- |
| Maven  | You listed it in requirements; stable, universal in enterprise Java |

### 5. Lombok

| Choice | Why                                                                                                                                            |
| ------ | ---------------------------------------------------------------------------------------------------------------------------------------------- |
| Yes    | Reduces boilerplate (getters, setters, constructors, builders). Standard in Spring Boot projects. Safe for portfolio — interviewers expect it. |

### 6. Modular Monolith (Domain-Based Packages)

| Choice                        | Why                                                                                                                                              |
| ----------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------ |
| Package-per-domain from day 1 | Avoids restructuring later. Each domain (auth, user, tutor, booking...) is self-contained with its own controller/service/repository/entity/dto. |

### 7. TypeScript vs JavaScript (Frontend)

| Choice     | Why                                                                       |
| ---------- | ------------------------------------------------------------------------- |
| TypeScript | Stronger portfolio signal, catches bugs, industry standard for React apps |

### 8. Frontend Tooling

| Choice                    | Why                                            |
| ------------------------- | ---------------------------------------------- |
| Vite + React + TypeScript | Faster than CRA (deprecated), modern, clean DX |

### 9. Database Strategy

| Choice                          | Why                                                                      |
| ------------------------------- | ------------------------------------------------------------------------ |
| Spring profiles: local/dev/test | Each profile has its own `application-{profile}.yml`. No secrets in Git. |
| Flyway for migrations           | Industry standard for DB version control in Spring Boot                  |

---

## Target Directory Structure

```
SkillBook/
├── backend/                              # Spring Boot application
│   ├── pom.xml
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/skillbook/
│   │   │   │   ├── SkillBookApplication.java
│   │   │   │   ├── config/
│   │   │   │   │   └── (empty — will add SecurityConfig, etc. in Phase 3)
│   │   │   │   ├── exception/
│   │   │   │   │   └── GlobalExceptionHandler.java
│   │   │   │   ├── common/
│   │   │   │   │   ├── BaseEntity.java
│   │   │   │   │   └── ApiResponse.java
│   │   │   │   ├── auth/                  # (empty — Phase 3)
│   │   │   │   ├── user/                  # (empty — Phase 3)
│   │   │   │   ├── tutor/                 # (empty — Phase 4)
│   │   │   │   ├── skill/                 # (empty — Phase 5)
│   │   │   │   ├── availability/          # (empty — Phase 6)
│   │   │   │   ├── booking/               # (empty — Phase 7)
│   │   │   │   ├── payment/               # (empty — Phase 8)
│   │   │   │   ├── meeting/               # (empty — Phase 11)
│   │   │   │   ├── review/                # (empty — Phase 12)
│   │   │   │   └── notification/          # (empty — Phase 9)
│   │   │   └── resources/
│   │   │       ├── application.yml
│   │   │       ├── application-local.yml
│   │   │       └── db/migration/          # Flyway — empty for now
│   │   └── test/
│   │       └── java/com/skillbook/
│   │           └── SkillBookApplicationTests.java
│   └── .gitignore
├── frontend/                             # React + Vite + TypeScript
│   ├── package.json
│   ├── tsconfig.json
│   ├── vite.config.ts
│   ├── index.html
│   ├── src/
│   │   ├── main.tsx
│   │   ├── App.tsx
│   │   ├── components/                   # (empty — will add as we build)
│   │   ├── pages/
│   │   │   ├── Login.tsx
│   │   │   └── Register.tsx
│   │   ├── services/                     # Axios API layer
│   │   │   └── api.ts
│   │   ├── types/                        # TypeScript interfaces
│   │   └── styles/
│   └── .gitignore
├── docker-compose.yml                    # PostgreSQL only for now
├── .env.example
├── .gitignore                            # Root gitignore
└── README.md                             # Updated
```

---

## Implementation Steps

### Step 1: Initialize Spring Boot Backend (Maven)

**Deliverable:** Working `pom.xml` with all Phase 1 dependencies

**pom.xml dependencies to include:**

```xml
<!-- Core -->
spring-boot-starter-web
spring-boot-starter-data-jpa
spring-boot-starter-validation
spring-boot-starter-security          <!-- basic setup only — full auth in Phase 3 -->

<!-- Database -->
postgresql                            <!-- runtime driver -->
flyway-core                           <!-- migrations -->
flyway-database-postgresql

<!-- Security foundation -->
spring-boot-starter-security

<!-- Utility -->
lombok
spring-boot-starter-actuator          <!-- health checks -->

<!-- Test -->
spring-boot-starter-test
spring-security-test
h2                                    <!-- in-memory DB for tests -->
```

**Why these dependencies:**

- `spring-boot-starter-security` — included from day 1 even though full auth is Phase 3. Reason: Spring Boot auto-configures security by default; having the dependency means the app boots cleanly. We'll configure it minimally now and expand in Phase 3.
- `flyway-core` — DB migrations must be version-controlled from the start. Adding Flyway later means retrofitting existing schema.
- `h2` — test-scoped only. Lets tests run without PostgreSQL. Integration tests use `@ActiveProfiles("test")` with H2.
- `spring-boot-starter-actuator` — exposes `/actuator/health`. Useful for Docker health checks and local debugging.

### Step 2: Create Base Entities and Common Classes

**Deliverable:** `BaseEntity` + `ApiResponse` + `GlobalExceptionHandler`

```
backend/src/main/java/com/skillbook/common/BaseEntity.java
```

- Abstract class with `id` (UUID), `createdAt`, `updatedAt`
- All entities will extend this
- Why UUID: avoids sequential ID guessing (security), works well with distributed systems, JPA native support

```
backend/src/main/java/com/skillbook/common/ApiResponse.java
```

- Standard JSON envelope: `{ "success": boolean, "data": T, "error": String, "timestamp": String }`
- Every endpoint returns this shape

```
backend/src/main/java/com/skillbook/exception/GlobalExceptionHandler.java
```

- `@RestControllerAdvice` class
- Handles: `MethodArgumentNotValidException` → 400, `EntityNotFoundException` → 404, generic → 500
- Returns `ApiResponse` with error details

### Step 3: Configure PostgreSQL + Flyway + Spring Profiles

**Deliverable:** `application.yml`, `application-local.yml`, first Flyway migration

```yaml
# application.yml (shared defaults)
spring:
  jpa:
    hibernate:
      ddl-auto: validate # Flyway manages schema; Hibernate only validates
    properties:
      hibernate:
        format_sql: true
  flyway:
    enabled: true
    locations: classpath:db/migration

server:
  port: 8080
  servlet:
    context-path: /api

management:
  endpoints:
    web:
      exposure:
        include: health,info
```

```yaml
# application-local.yml (developer machine)
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/skillbook
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD:password}
    driver-class-name: org.postgresql.Driver
  jpa:
    show-sql: true
```

**First Flyway migration:** `V1__init_schema.sql` — creates a minimal `users` table (id, email, password_hash, name, role, created_at, updated_at) with unique constraint on email. This validates the Flyway pipeline works end-to-end.

**Why `ddl-auto: validate`?** In production, Hibernate should never create/alter tables — Flyway owns the schema. `validate` ensures Hibernate entities match what Flyway created. Mismatches cause a clear startup error instead of silent corruption.

### Step 4: Create `.env.example` and `.gitignore`

**Deliverable:** Root-level configuration files

`.env.example`:

```
# PostgreSQL
DB_USERNAME=postgres
DB_PASSWORD=password
DB_URL=jdbc:postgresql://localhost:5432/skillbook

# JWT (will be used in Phase 3)
JWT_SECRET=change-me-to-a-random-string
JWT_EXPIRATION_MS=86400000

# Stripe (will be used in Phase 8)
STRIPE_SECRET_KEY=sk_test_xxx
STRIPE_WEBHOOK_SECRET=whsec_xxx

# Mail (will be used in Phase 9)
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=
MAIL_PASSWORD=
```

`.gitignore` (root):

```
# IDE
.idea/
*.iml
.vscode/
.settings/
.project
.classpath

# Java
target/
*.class
*.jar
*.war

# Node
node_modules/
dist/
.next/

# Environment
.env
.env.local
*.env

# OS
.DS_Store
Thumbs.db

# Logs
*.log
```

### Step 5: Create docker-compose.yml

**Deliverable:** Docker Compose with PostgreSQL (no Spring Boot container yet)

```yaml
services:
  postgres:
    image: postgres:16-alpine
    container_name: skillbook-db
    environment:
      POSTGRES_DB: skillbook
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: password
    ports:
      - "5432:5432"
    volumes:
      - pgdata:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres -d skillbook"]
      interval: 5s
      timeout: 3s
      retries: 5

volumes:
  pgdata:
```

**Why not include Spring Boot in docker-compose yet?** Adding it now means rebuilding the Docker image on every code change. During active development, run Spring Boot locally and only containerize PostgreSQL. We'll add the Spring Boot service in Phase 15 (Docker + deployment).

### Step 6: Initialize React + Vite + TypeScript Frontend

**Deliverable:** Scaffolding only — Login and Register page shells

Commands:

```bash
cd frontend
npm create vite@latest . -- --template react-ts
npm install
npm install react-router-dom axios
```

**Minimal files:**

- `src/main.tsx` — entry point with `BrowserRouter`
- `src/App.tsx` — routes: `/login` → `Login.tsx`, `/register` → `Register.tsx`
- `src/services/api.ts` — Axios instance with `baseURL: http://localhost:8080/api`
- `src/pages/Login.tsx` — placeholder form
- `src/pages/Register.tsx` — placeholder form

**Why Axios over Fetch?** Axios interceptors make adding JWT tokens (Phase 3) clean. Fetch works but requires more boilerplate for auth headers.

### Step 7: Update README.md

**Deliverable:** Accurate README reflecting the actual Java/Spring Boot stack

Update the tech stack table, project structure, and getting-started instructions to match the real implementation.

### Step 8: Verify Everything Works

**Deliverable:** App boots, health endpoint responds, tests pass

```bash
# Start PostgreSQL
docker-compose up -d

# Run Spring Boot
cd backend
./mvnw spring-boot:run

# Verify
curl http://localhost:8080/api/actuator/health

# Run tests
./mvnw test

# Frontend
cd frontend
npm run dev
```

---

## Key Files

| File                                                                        | Operation | Description                           |
| --------------------------------------------------------------------------- | --------- | ------------------------------------- |
| `backend/pom.xml`                                                           | Create    | Maven project with all dependencies   |
| `backend/src/main/java/com/skillbook/SkillBookApplication.java`             | Create    | Spring Boot entry point               |
| `backend/src/main/java/com/skillbook/common/BaseEntity.java`                | Create    | Abstract entity with UUID, timestamps |
| `backend/src/main/java/com/skillbook/common/ApiResponse.java`               | Create    | Standard API response envelope        |
| `backend/src/main/java/com/skillbook/exception/GlobalExceptionHandler.java` | Create    | Centralized error handling            |
| `backend/src/main/resources/application.yml`                                | Create    | Shared config                         |
| `backend/src/main/resources/application-local.yml`                          | Create    | Local PostgreSQL config               |
| `backend/src/main/resources/db/migration/V1__init_schema.sql`               | Create    | Initial users table                   |
| `backend/src/test/java/.../SkillBookApplicationTests.java`                  | Create    | Context load test                     |
| `frontend/` (scaffold)                                                      | Create    | Vite + React + TypeScript             |
| `docker-compose.yml`                                                        | Create    | PostgreSQL service                    |
| `.env.example`                                                              | Create    | All environment variables             |
| `.gitignore`                                                                | Create    | Root gitignore                        |
| `README.md`                                                                 | Update    | Accurate project docs                 |

---

## Risks and Mitigations

| Risk                                | Mitigation                                                  |
| ----------------------------------- | ----------------------------------------------------------- |
| PostgreSQL not running locally      | docker-compose starts it; README documents both options     |
| Spring Boot version incompatibility | Pin to 3.3.x, verify on first build                         |
| Flyway migration fails on empty DB  | V1\_\_init_schema.sql is simple; test with `flyway migrate` |
| Frontend port conflict with backend | Vite on 5173, Spring Boot on 8080 — different ports         |
| Google Drive sync issues with .git  | .gitignore excludes IDE files; Git handles the rest         |

---

## Learning Notes (for your understanding)

### Why Spring Boot 3.3+?

Spring Boot 3.x migrated from `javax.*` to `jakarta.*` (Jakarta EE 9+). This is the current industry standard. All new Spring projects use 3.x.

### Why `@RestControllerAdvice`?

It's a Spring MVC annotation that lets you handle exceptions globally across all controllers. Instead of `try/catch` in every controller method, you define one handler. It uses AOP (Aspect-Oriented Programming) under the hood but you don't need to understand AOP deeply to use it.

### Why UUID as primary key?

- Not guessable (unlike sequential `1, 2, 3`)
- Can generate on the client side before sending to server
- No collision risk in distributed systems
- JPA supports `@GeneratedValue(strategy = GenerationType.UUID)` natively in Hibernate 6+

### Why Flyway instead of Hibernate `ddl-auto: create/update`?

`ddl-auto: create` destroys your data on every restart. `ddl-auto: update` generates untrackable DDL changes that can silently diverge from your intended schema. Flyway gives you versioned, reviewed, repeatable migrations — the industry standard for any serious application.

---

## SESSION_ID (for /ccg:execute use)

- CODEX_SESSION: N/A (CCG tools not available)
- ANTIGRAVITY_SESSION: N/A (CCG tools not available)

---

## Next Steps

After Phase 1 is verified, proceed to **Phase 2: Database Entities + JPA Relationships** where we define the core entity model (User, TutorProfile, StudentProfile, Skill, SkillOffered, SkillWanted, etc.) and their relationships.
