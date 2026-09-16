# TDD Evidence Report: Phase 4 — Student/Tutor Profiles (CRUD endpoints)

## Source plan
- Implementation plan: `.claude/plan/phase4-profiles.md`
- Derives from roadmap in `.claude/plan/phase1-project-foundation.md`

## Epics / journeys
1. **Tutor profile upsert** — a TUTOR creates or updates their own profile (bio, experience, hourly rate) via PUT; id + createdAt are returned.
2. **Tutor profile view** — a TUTOR can GET their own profile; any authenticated user can view a tutor profile by userId (GET /tutors/{userId}).
3. **Student profile upsert** — a STUDENT creates or updates their own profile (bio, education level).
4. **Student profile view** — a STUDENT can GET their own profile.
5. **Cross-role enforcement** — a TUTOR cannot create/update a student profile; a STUDENT cannot create/update a tutor profile; both get 403.

## Task report

### Task: Profile CRUD endpoint layer (service + controller + DTOs)
- **Execution**: RED wrote `TutorProfileIntegrationTest` (5 tests) + `StudentProfileIntegrationTest` (3 tests) referencing missing `TutorProfileService`, `TutorProfileController`, `StudentProfileService`, `StudentProfileController`, and DTO records. First RED gate: runtime 500 (no handler mapped). Implemented 8 new files: `TutorProfileRequest`/`Response` + `StudentProfileRequest`/`Response` (Java records with validation), `TutorProfileService` + `StudentProfileService` (upsert via `saveAndFlush` for immediate id/createdAt), `TutorProfileController` (3 endpoints) + `StudentProfileController` (2 endpoints), all using `@PreAuthorize("hasRole(...)")`. Added explicit `AccessDeniedException → 403` handler in `GlobalExceptionHandler` (without it, method security denials were swallowed by the catch-all `Exception` handler → 500). Fixed `TutorProfileResponse`/`StudentProfileResponse` type mismatch (Instant → LocalDateTime to match `BaseEntity`).
- **Files created/modified**:
  - New: `tutor/dto/TutorProfileRequest.java`, `tutor/dto/TutorProfileResponse.java`, `tutor/TutorProfileService.java`, `tutor/TutorProfileController.java`
  - New: `user/dto/StudentProfileRequest.java`, `user/dto/StudentProfileResponse.java`, `user/StudentProfileService.java`, `user/StudentProfileController.java`
  - Modified: `exception/GlobalExceptionHandler.java` (+`AccessDeniedException` handler)
- **GREEN test result**:
  ```
  Tests run: 38, Failures: 0, Errors: 0, Skipped: 0
  BUILD SUCCESS
  ```
- **Real-DB curl verification** (Docker PG 5433, fresh backend restart):
  - `PUT /api/tutors/profile` as TUTOR → 200, id + createdAt populated, bio/experience/rate stored
  - `GET /api/tutors/profile` as TUTOR → 200, consistent with PUT
  - `GET /api/tutors/{userId}` as TUTOR → 200, public profile view
  - `PUT /api/students/profile` as STUDENT → 200, id + createdAt populated
  - `GET /api/students/profile` as STUDENT → 200, own profile
  - `PUT /api/students/profile` as TUTOR → **403** "Access denied"
  - `PUT /api/tutors/profile` as STUDENT → **403** "Access denied"
- **Bug found and fixed during live verification**: first-time upsert returned `null` id/createdAt (cascade from `userRepository.save()` not flushed before response built). Fixed by using `tutorProfileRepository.saveAndFlush(profile)` directly.

## Test specification

| # | What is guaranteed | Test file : method | Type | Result | Evidence |
|---|--------------------|--------------------|------|--------|----------|
| 1 | PUT tutor profile → 200, GET returns same data | `TutorProfileIntegrationTest.upsertAndRetrieveOwnTutorProfile` | integration | PASS | `mvn test` + curl |
| 2 | PUT tutor profile twice → latest values returned | `TutorProfileIntegrationTest.updateExistingTutorProfile` | integration | PASS | `mvn test` |
| 3 | TUTOR cannot PUT student profile → 403 | `TutorProfileIntegrationTest.tutorCannotUpdateStudentProfile` | integration | PASS | `mvn test` + curl |
| 4 | GET /tutors/{userId} → 200 correct profile | `TutorProfileIntegrationTest.getTutorProfileByUserId` | integration | PASS | `mvn test` |
| 5 | Invalid payload (bio > 2000, negative rate) → 400 | `TutorProfileIntegrationTest.validationRejectsInvalidPayload` | integration | PASS | `mvn test` |
| 6 | PUT student profile → 200, GET returns same data | `StudentProfileIntegrationTest.upsertAndRetrieveOwnStudentProfile` | integration | PASS | `mvn test` + curl |
| 7 | PUT student profile twice → latest values | `StudentProfileIntegrationTest.updateExistingStudentProfile` | integration | PASS | `mvn test` |
| 8 | STUDENT cannot PUT tutor profile → 403 | `StudentProfileIntegrationTest.studentCannotUpdateTutorProfile` | integration | PASS | `mvn test` + curl |

## Coverage and known gaps
- No JaCoCo configured; all tests are real integration tests against H2 (MockMvc + real Spring context).
- Deliberately deferred: skill list on tutor profile (Phase 5), tutor availability (Phase 6), public (unauthenticated) tutor browsing.
- Tutor ratings always default to 0.0 on create — rating aggregation comes in Phase 12 (reviews).

## Merge evidence
- No git checkpoint commits created (per `rules.md` protocol). RED/GREEN evidence preserved above.
