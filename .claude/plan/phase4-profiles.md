# Implementation Plan: Phase 4 — Student/Tutor Profiles (CRUD endpoints)

## Task Type
- [x] Backend service/controller layer (entities already exist from Phase 2)
- [x] Role-based access via existing `@EnableMethodSecurity` + `CustomUserDetailsService` (`ROLE_TUTOR`/`ROLE_STUDENT`)

## Routes

| Method | Path | Role | Body → Response |
|--------|------|------|-----------------|
| PUT | `/api/tutors/profile` | TUTOR | `TutorProfileRequest` → `ApiResponse<TutorProfileResponse>` (upsert) |
| GET | `/api/tutors/profile` | TUTOR | → `ApiResponse<TutorProfileResponse>` (own profile) |
| GET | `/api/tutors/{userId}` | AUTHENTICATED | → `ApiResponse<TutorProfileResponse>` (any tutor by userId) |
| PUT | `/api/students/profile` | STUDENT | `StudentProfileRequest` → `ApiResponse<StudentProfileResponse>` (upsert) |
| GET | `/api/students/profile` | STUDENT | → `ApiResponse<StudentProfileResponse>` (own profile) |

## Decisions

### 1. Upsert semantics (not create-only)
The profile tables already have a row per user (created lazily on first PUT). Two cases:
- Profile doesn't exist yet → create new `TutorProfile`, call `user.addTutorProfile(profile)`, save (cascade persists).
- Profile already exists → update fields on the managed entity, save user (dirty checking persists without extra INSERT).

Both paths go through `UserRepository.save(user)`, so the transaction and `@UpdateTimestamp` refresh is consistent.

### 2. DTOs as Java records (matches Phase 3 auth DTOs)
- `TutorProfileRequest`: bio (`@Size(max=2000)`), experienceYears (`@Min(0)`), hourlyRate (`@DecimalMin("0.0") @Digits(integer=6,fraction=2)`)
- `StudentProfileRequest`: bio (`@Size(max=2000)`), educationLevel (`@Size(max=50)`)
- `TutorProfileResponse`: id, userId, name, email, bio, experienceYears, hourlyRate, rating, createdAt
- `StudentProfileResponse`: id, userId, name, email, bio, educationLevel, createdAt

### 3. `@PreAuthorize` for role enforcement
- `PUT /tutors/profile`, `GET /tutors/profile` → `@PreAuthorize("hasRole('TUTOR')")`
- `PUT /students/profile`, `GET /students/profile` → `@PreAuthorize("hasRole('STUDENT')")`
- `GET /tutors/{userId}` → no role restriction (any authenticated user can view a tutor profile)

### 4. No Flyway migration
Entity tables created in Phase 2; `ddl-auto: validate` already verified. No schema changes.

## Test cases (RED first — reference missing classes)

### TutorProfileIntegrationTest
1. `upsertAndRetrieveOwnTutorProfile` — PUT profile, GET own, verify fields
2. `updateExistingTutorProfile` — PUT twice with different values, GET, verify latest
3. `tutorCannotUpdateStudentProfile` — PUT /students/profile as TUTOR → 403
4. `getTutorProfileByUserId` — PUT profile, GET /tutors/{userId}, verify
5. `validationRejectsInvalidPayload` — bio > 2000 chars → 400

### StudentProfileIntegrationTest
6. `upsertAndRetrieveOwnStudentProfile` — PUT profile, GET own, verify
7. `updateExistingStudentProfile` — PUT twice, verify latest
8. `studentCannotUpdateTutorProfile` — PUT /tutors/profile as STUDENT → 403

## Files created
| File |
|------|
| `tutor/dto/TutorProfileRequest.java` |
| `tutor/dto/TutorProfileResponse.java` |
| `tutor/TutorProfileService.java` |
| `tutor/TutorProfileController.java` |
| `user/dto/StudentProfileRequest.java` |
| `user/dto/StudentProfileResponse.java` |
| `user/StudentProfileService.java` |
| `user/StudentProfileController.java` |
| `tutor/TutorProfileIntegrationTest.java` (test) |
| `user/StudentProfileIntegrationTest.java` (test) |

## Verification
```bash
cd backend && JAVA_HOME=<temurin21> mvn test
# curl PUT/GET against Docker PG (5433) with register/login for token
```

## Deferred
- Public (unauthenticated) tutor browsing (comes with Phase 5 discovery)
- Skill list on tutor profile (Phase 5)
- Tutor availability (Phase 6)
