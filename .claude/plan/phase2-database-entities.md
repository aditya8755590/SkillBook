# Implementation Plan: Phase 2 — Database Entities + JPA Relationships

## Task Type
- [x] Backend (Java/Spring Boot) — data layer only, no REST endpoints

## Scope (from Phase 1 plan "Next Steps")
Define the core entity model and their JPA relationships:
- `User` (extends `BaseEntity`) — email, password_hash, name, role
- `Role` enum — STUDENT / TUTOR / ADMIN
- `TutorProfile` — 1:1 with User, bio, experience, hourly_rate, rating
- `StudentProfile` — 1:1 with User, bio, education_level
- `Skill` — normalized skill registry
- `SkillOffered` — join: TutorProfile ←→ Skill (many-to-many through table)
- `SkillWanted` — join: StudentProfile ←→ Skill (many-to-many through table)

## Decisions

### 1. Join tables as explicit entities (not @ManyToMany)
| Choice | Why |
|--------|-----|
| `SkillOffered` / `SkillWanted` as own entities | Future phases add `certification`, `years_teaching`, `hourly_rate` per skill without schema changes. `@ManyToMany` sets are the classic "adding a column to the join table" trap. |

### 2. Naming & package layout (matches Phase 1 structure)
```
com.skillbook.user/       → User, Role, StudentProfile, repositories
com.skillbook.tutor/      → TutorProfile, repository
com.skillbook.skill/      → Skill, SkillOffered, SkillWanted, repositories
```
Every entity extends `common.BaseEntity` (UUID PK, createdAt/updatedAt).

### 3. DB strategy
- New Flyway migration `V2__skillbook_core_tables.sql` (users already exists from V1)
- `ddl-auto: validate` stays ON — entities must match Flyway exactly
- Tests run on H2 (`create-drop`) with Flyway off — no PG needed

### 4. Cascade strategy
- Owning side: `TutorProfile.user_id` FK (mirrors V1 philosophy — profiles existential on user)
- `cascade = ALL, orphanRemoval = true` on User → profiles & skills joins
- Both 1:1 and M:N are User-owned: deleting a User removes everything

## Entity Relationship Diagram

```
users (V1)
  ├── 1:1 ── TutorProfile (user_id FK, UNIQUE)
  ├── 1:1 ── StudentProfile (user_id FK, UNIQUE)
  ├── 1:N ── SkillOffered (tutor_id FK)  ── N:1 ── Skill
  └── 1:N ── SkillWanted (student_id FK) ── N:1 ── Skill
```

## Column Maps (must match V2 migration EXACTLY for validate mode)

### User (users — exists, extends V1)
| column | type | entity |
|--------|------|--------|
| email | VARCHAR(255) UNIQUE | `@Column(nullable=false, unique=true, length=255)`, `@Email`, `@NotBlank` |
| password_hash | VARCHAR(255) | `@Column(name="password_hash", nullable=false)` |
| name | VARCHAR(100) | `@NotBlank`, `@Size(max=100)` |
| role | VARCHAR(20) CHECK | `@Enumerated(STRING)`, `@Column(nullable=false, length=20)` |

### TutorProfile
| column | type | entity |
|--------|------|--------|
| user_id | UUID FK UNIQUE | `@OneToOne` |
| bio | TEXT | `@Column(columnDefinition="TEXT")`, `@Size(max=2000)` |
| experience_years | INTEGER NOT NULL | int, `@Min(0)` |
| hourly_rate | NUMERIC(10,2) | `BigDecimal`, `@DecimalMin("0")`, precision=10 scale=2 |
| rating | NUMERIC(3,2) | `BigDecimal`, precision=3 scale=2 |

### StudentProfile
| column | type | entity |
|--------|------|--------|
| user_id | UUID FK UNIQUE | `@OneToOne` |
| bio | TEXT | `@Size(max=2000)` |
| education_level | VARCHAR(50) | `@Size(max=50)` |

### Skill
| column | type | entity |
|--------|------|--------|
| name | VARCHAR(100) UNIQUE | `@NotBlank`, `@Size(max=100)`, unique |
| category | VARCHAR(100) | `@Size(max=100)` |

### SkillOffered / SkillWanted (identical shape)
| column | type | entity |
|--------|------|--------|
| tutor_id / student_id | UUID FK | `@ManyToOne` |
| skill_id | UUID FK | `@ManyToOne` |
| UNIQUE (tutor_id, skill_id) / (student_id, skill_id) | constraint | `@Table(uniqueConstraints=...)` |

## Files Created

| File |
|------|
| `backend/.../user/Role.java` |
| `backend/.../user/User.java` |
| `backend/.../user/StudentProfile.java` |
| `backend/.../user/UserRepository.java` |
| `backend/.../user/StudentProfileRepository.java` |
| `backend/.../tutor/TutorProfile.java` |
| `backend/.../tutor/TutorProfileRepository.java` |
| `backend/.../skill/Skill.java` |
| `backend/.../skill/SkillOffered.java` |
| `backend/.../skill/SkillWanted.java` |
| `backend/.../skill/SkillRepository.java` |
| `backend/.../skill/SkillOfferedRepository.java` |
| `backend/.../skill/SkillWantedRepository.java` |
| `backend/src/main/resources/db/migration/V2__skillbook_core_tables.sql` |
| `backend/.../test/.../entities/UserEntityTest.java` |
| `backend/.../test/.../entities/TutorProfileEntityTest.java` |
| `backend/.../test/.../entities/SkillEntityTest.java` |

## Verification
```bash
cd backend && JAVA_HOME=<jdk21> mvn test
# Docker DB (real PG, validate mode):
# restart backend → Flyway V2 applies → app boots → health UP
```

## Deferred (later phases)
- REST controllers (Phase 3+)
- Skill match scoring (Phase 5)
- Availability, Booking, Payment, etc. (Phases 6-8)
- `@Embedded` audit/createdBy (not needed yet)