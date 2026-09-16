# Implementation Plan: Phase 5 — Skills Catalog + Tutor/Student Skills + Tutor Discovery & Search

## Task Type
- [x] Skill registry API (GET/POST /skills)
- [x] Tutor skill management (replace offered skills)
- [x] Student skill management (replace wanted skills)
- [x] Tutor discovery & search (GET /tutors, filters, sort, pagination, public POST)

## Routes

| Method | Path | Role | Purpose |
|--------|------|------|---------|
| GET | `/api/skills` | PUBLIC | List skills (optional `?q=` name search, sorted by name) |
| POST | `/api/skills` | AUTHENTICATED | Get-or-create a skill by name, returns skill + 201/200 |
| PUT | `/api/tutors/profile/skills` | TUTOR | Replace offered skills (get-or-create each by name) |
| GET | `/api/tutors/profile/skills` | TUTOR | List own offered skills |
| PUT | `/api/students/profile/skills` | STUDENT | Replace wanted skills |
| GET | `/api/students/profile/skills` | STUDENT | List own wanted skills |
| GET | `/api/tutors` | PUBLIC | Paginated tutor discovery with filters (query below) |
| GET | `/api/tutors/{userId}` | PUBLIC | Any tutor profile (used by discovery detail) |

## Discovery query params (GET /tutors)
- `q` — free text: matches user.name, bio, or skill name (case-insensitive contains)
- `skill` — exact skill name (case-insensitive)
- `minRate`, `maxRate` — hourly rate range
- `minRating` — minimum rating (0..5)
- `sort` — `rating_desc` (default), `rating_asc`, `price_asc`, `price_desc`, `name_asc`, `name_desc`
- `page` (default 0), `size` (default 10, max 100)

## Decisions

### 1. Skill model: get-or-create by name
Users submit skill **names** (e.g. "Math"). Service normalizes (trim), looks up by name ignore-case, creates if absent (unique constraint on name). This avoids exposing UUIDs in the write API and keeps the registry self-healing.

### 2. Replace semantics for profile skills
`PUT /.../profile/skills` with `{ "skills": ["Math", "Physics"] }`:
- Delete existing offer/want rows for the user
- Get-or-create each skill and insert new rows
- Return the new `List<SkillResponse>` sorted by name

### 3. Discovery: single JPQL paginated query
`TutorProfileRepository.search(...)` — one JPQL query, no criteria API. Handles all optional filters via `(:param is null or ...)` and uses `exists` subqueries for skill-name matching (so a tutor with "Algebra" + "Geometry" isn't duplicated). `select distinct` + `count(distinct)` with a companion `countQuery`. Sort is whitelisted in the service and mapped to Sort.by in PageRequest.

### 4. DTOs (records, mirror Phase 3-4 style)
- `SkillResponse(id, name, category)`
- `SkillRequest(name @NotBlank @Size(max=100), category @Size(max=100))`
- `SkillNamesRequest(List<@NotBlank @Size(max=100) String> skills)`
- `TutorSearchResponse` — TutorProfile fields + `List<SkillResponse> skills`

### 5. Public access to discovery
`GET /skills`, `GET /tutors`, `GET /tutors/{userId}` become PUBLIC (permitAll) in `SecurityConfig` — this is "public tutor browsing" deferred from Phase 4. Profile-mutation endpoints stay role-gated (method security still applies since filter chains run before method security).

Wait — `/tutors/*` public would expose `/tutors/profile` GET (own profile). Node: keep method `@PreAuthorize("hasRole('TUTOR')")` on GET `/tutors/profile`, so even with permitAll at the URL level, role check still applies via method security. But an unauthenticated caller reaching `/tutors/profile` GET would get 401 from the filter (not 403). To keep it clean: permit only `GET /tutors` and `GET /tutors/{userId}`, NOT `/tutors/profile`.

### 6. No new Flyway migration
Skill tables already exist (V2). `ddl-auto: validate` passes.

## Repositories — additions
| Repo | Adds |
|------|------|
| `SkillRepository` | `findByNameIgnoreCase`, `findByOrderByNameAsc` |
| `SkillOfferedRepository` | `deleteByTutorId`, `findByTutorIdWithSkill` (fetch join) |
| `SkillWantedRepository` | `deleteByStudentId`, `findByStudentIdWithSkill` (fetch join) |
| `TutorProfileRepository` | `search(...)` JPQL (filters + pagination) |

## Test cases (RED first) — see keys below
### SkillCatalogIntegrationTest
1. `getSkillsEmptyInitially`
2. `postCreatesSkill`
3. `postGetOrCreateIsIdempotent` (same name twice → 201 then 200, same id)
4. `postRejectsBlankName` → 400
5. `postRequiresAuthentication` → 401
6. `getAllSortedByName`

### TutorSkillIntegrationTest
7. `tutorReplacesOfferedSkills` (PUT ["Math","Physics"] → GET reflects)
8. `tutorReplaceClearsSkills` (PUT [] → empty)
9. `studentCannotModifyTutorSkills` → 403
10. `unauthenticatedCannotModify` → 401

### StudentSkillIntegrationTest
11. `studentReplacesWantedSkills`
12. `tutorCannotModifyStudentSkills` → 403

### TutorDiscoveryIntegrationTest
13. `searchByNameFragment` (q)
14. `searchBySkillName` (skill)
15. `filterByPriceRange` (minRate/maxRate)
16. `filterByMinRating`
17. `sortByPriceAscAndNameDesc`
18. `paginateLimit`
19. `publicAccessWithoutToken` → 200
20. `ownProfileNotPubliclyExposed` → GET /tutors/profile without token → 401

## Files created
| File |
|------|
| `skill/dto/SkillRequest.java`, `SkillResponse.java` |
| `skill/dto/SkillNamesRequest.java` |
| `skill/SkillService.java`, `skill/SkillController.java` |
| `tutor/dto/TutorSearchResponse.java` |
| Repo methods added to 4 repositories |
| Wired into `TutorProfileController` and a new `TutorSearchController` |
| `SkillOfferedRepository`, `SkillWantedRepository` additions |
| `SecurityConfig` permitAll for GET skills/tutors/(id) |

## Verification
```bash
cd backend && JAVA_HOME=<temurin21> mvn test
# curl POST /skills, PUT/GET profile skills, GET /tutors filters against Docker PG
```

## Deferred
- Skill categories facet list (dropdown) — frontend concern
- Availability-aware search ranking (skips tutors with no availability) — Phase 6
- "Match score" recommendation ordering — Phase 13