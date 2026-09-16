# TDD Evidence Report — Phase 5: Skills Catalog + Tutor/Student Skills + Tutor Discovery

## Source plan
[`.claude/plan/phase5-skills-discovery.md`](../plan/phase5-skills-discovery.md)

## User journeys
- As a **tutor**, I want to attach a list of skills I can teach, so that students can find me.
- As a **student**, I want to record the skills I want to learn, so that tutors can find me.
- As a visitor, I want to browse and search tutors by keyword/skill/price/rating without logging in, so that I can decide who to book.
- As an authenticated user, I want a shared skills catalog (get-or-create by name), so that skill names stay normalized and deduplicated.

## Task report

### 1. Skills catalog — GET /skills (public, sorted) + POST /skills (authenticated, get-or-create)
- Execution: `SkillService` (get-or-create via `findByNameIgnoreCase`, returns `CreationResult` for 201/200), `SkillController`, DTOs (`SkillRequest`, `SkillResponse`, `SkillNamesRequest`). `SecurityConfig` permits `GET /skills`; writes require auth.
- Validation: `mvn test -Dtest=SkillCatalogIntegrationTest` → RED before implementation (endpoint absent), GREEN 6/6 after.
- Live (PostgreSQL): `POST /skills` new → 201, existing (case-insensitive) → 200; `GET /skills` public, sorted by name.

### 2. Tutor skills — PUT/GET /tutors/profile/skills (TUTOR)
- Execution: `TutorSkillService.replaceOfferedSkills` (delete-all → get-or-create → insert, `flush()` after delete), `SkillOfferedRepository` (find/delete by tutor, batch find by tutorIds). Endpoints added to `TutorProfileController`.
- Validation: `mvn test -Dtest=TutorSkillIntegrationTest` → RED before, GREEN 5/5 after.
- Live: PUT→200 with normalized skills; GET returns them; STUDENT role → 403; anonymous → 401.

### 3. Student skills — PUT/GET /students/profile/skills (STUDENT)
- Execution: `StudentSkillService.replaceWantedSkills`, `SkillWantedRepository`, endpoints added to `StudentProfileController`.
- Validation: `mvn test -Dtest=StudentSkillIntegrationTest` → RED before, GREEN 3/3 after.
- Live: PUT→200; cross-role (student hitting tutor endpoint) → 403.

### 4. Tutor discovery — GET /tutors (public search)
- Execution: `TutorProfileRepository.search` JPQL (correlated `exists` on `SkillOffered`, nullable params, explicit `countQuery`), `TutorSearchService` (sort whitelist `rating_desc|rating_asc|price_asc|price_desc|name_asc|name_desc`, batch skill loading via `findByTutorIdsWithSkill` to avoid N+1), `TutorSearchController`, `TutorSearchResponse`.
- Validation: `mvn test -Dtest=DiscoveryIntegrationTest` → RED before, GREEN 8/8 after.
- Live (PostgreSQL): no-filter → all 3 tutors; `?skill=Calculus` → 1; `?q=physics` → 1 (matches name/bio/skill); `?maxRate=60` → 1; `?sort=price_asc` → ordered; `size=1` → pagination correct; anonymous allowed; `GET /tutors/profile` anonymous → 401; invalid sort → 400.

### GREEN outcome
Full suite `mvn -q test`: **60 tests, 0 failures, 0 errors** (38 prior + 22 Phase 5).

## Test specification

| # | What is guaranteed | Test file | Result | Evidence |
|---|--------------------|-----------|--------|----------|
| 1 | GET /skills returns empty list initially | SkillCatalogIntegrationTest | PASS | `mvn test` |
| 2 | POST /skills creates new skill (201) | SkillCatalogIntegrationTest | PASS | `mvn test` |
| 3 | POST /skills get-or-create is idempotent (200) | SkillCatalogIntegrationTest | PASS | `mvn test` |
| 4 | GET /skills returns sorted, deduplicated list | SkillCatalogIntegrationTest | PASS | `mvn test` |
| 5 | Skills catalog is public, writes require auth | SkillCatalogIntegrationTest | PASS | `mvn test` |
| 6 | Tutor can replace offered skills | TutorSkillIntegrationTest | PASS | `mvn test` |
| 7 | Tutor skills persist and are retrieved sorted | TutorSkillIntegrationTest | PASS | `mvn test` |
| 8 | Student role blocked from tutor skill endpoints (403) | TutorSkillIntegrationTest/StudentSkillIntegrationTest | PASS | `mvn test` |
| 9 | Student can replace/read wanted skills | StudentSkillIntegrationTest | PASS | `mvn test` |
| 10 | Discovery list returns tutors with skills (no N+1) | DiscoveryIntegrationTest | PASS | `mvn test` |
| 11 | Keyword `q` matches name/bio/skill | DiscoveryIntegrationTest | PASS | `mvn test` |
| 12 | `skill` filter exact match, case-insensitive | DiscoveryIntegrationTest | PASS | `mvn test` |
| 13 | minRate/maxRate/minRating filters | DiscoveryIntegrationTest | PASS | `mvn test` |
| 14 | Sort whitelist + default rating_desc | DiscoveryIntegrationTest | PASS | `mvn test` |
| 15 | Pagination (page/size) | DiscoveryIntegrationTest | PASS | `mvn test` |
| 16 | Discovery is public; own profile requires auth (401) | DiscoveryIntegrationTest | PASS | `mvn test` |
| 17 | Invalid sort rejected (400) | DiscoveryIntegrationTest | PASS | `mvn test` |

## Coverage and known gaps
- Coverage command not collected this phase (backend has no coverage plugin threshold configured; suite is integration-heavy via MockMvc).
- POST /skills requires authentication (NOT public writes) — deliberate: catalog is user-moderated before admin work exists.
- Skill names get-or-created case-insensitively; on case variant they reuse the existing skill (200) rather than 201 — documented behavior.
- Deferred to later phases: rating auto-computation (currently defaults 0), admin skill moderation.

## Merge evidence (RED/GREEN summary)
- RED: tests written against unimplemented endpoints; `mvn test -Dtest=SkillCatalogIntegrationTest,...` → 20/22 failed (endpoints 404; 2 passing were auth-filter 401s).
- GREEN: implementation complete; `mvn test` → 60/60 PASS.
- Post-GREEN live verification on PostgreSQL surfaced a real bug H2 missed: `lower(?)`/`concat('%',?,'%')` bound null params as `bytea` → `function lower(bytea) does not exist`. Fixed by computing the LIKE pattern in Java (`toLikePattern`) and `cast(:skill as string)`; also cleaned FK-violating test setup (`skill_wanted`/`skill_offered`/`skills` cleared before `users` deleteAll). Re-verified: full suite green + all curl checks pass on Postgres.