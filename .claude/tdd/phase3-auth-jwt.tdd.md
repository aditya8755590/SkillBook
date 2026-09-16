# TDD Evidence Report: Phase 3 — Authentication + JWT + Roles

## Source plan
- Implementation plan: `.claude/plan/phase3-auth-jwt.md`
- Derives from roadmap in `.claude/plan/phase1-project-foundation.md` ("Next Steps")

## Epics / journeys
1. **User registration** — a new user signs up with name, email, password, role; gets a JWT immediately; password stored hashed (BCrypt), never plaintext.
2. **User login** — a registered user logs in with email/password and receives a JWT; wrong password or unknown email is rejected with 401.
3. **Protected APIs** — endpoints require a valid `Bearer` token; `/auth/me` returns the current user; missing/invalid/expired token → 401.

## Task report

### Task: JWT plumbing + auth endpoints + hardening SecurityConfig
- **Execution**: Wrote a unit test (`JwtServiceTest`) that constructs the as-yet-unwritten `JwtService` directly (compile-time RED: `cannot find symbol: class JwtService`), plus an end-to-end MockMvc integration test (`AuthFlowIntegrationTest`) covering 9 auth behaviors over H2. Implemented `JwtProperties` (record, fails fast on short secret — ctor-bound), `JwtService` (j jtw 0.12.6 HS signing/parsing), `JwtAuthenticationFilter` (Bearer extraction → `CustomUserDetailsService` → `SecurityContext`), `AuthService`, `AuthController` (`/register` 201, `/login` 200, `/me`), DTOs, `CustomUserDetailsService` (`ROLE_<role>` authority), then rewrote `SecurityConfig` to a stateless chain: permit only `/auth/register`, `/auth/login`, actuator health/info, `/error`; authenticated elsewhere; JSON 401/403 via `AuthenticationEntryPoint`/`AccessDeniedHandler`; BCrypt `PasswordEncoder`. Added `@EnableConfigurationProperties(JwtProperties.class)` and `jwt.*` config to `application.yml` + test secret in `application-test.yml`. Added a `BadCredentialsException` handler (→ 401) to `GlobalExceptionHandler`.
- **One test-design correction during GREEN**: my `registerCreatesUserAndReturnsToken` originally asserted a `Location` header that the API contract never specified (there is no `/users/{id}` resource yet). Removed the over-specified assertion; the implementation was correct.
- **Validation run**: `cd backend && JAVA_HOME=<temurin21> mvn test`
- **RED excerpt**:
  ```
  [ERROR] .../JwtServiceTest.java:[15,13] cannot find symbol
  [ERROR]   symbol:   class JwtService
  [ERROR] BUILD FAILURE
  ```
- **GREEN excerpt**:
  ```
  [INFO] Tests run: 30, Failures: 0, Errors: 0, Skipped: 0
  [INFO] BUILD SUCCESS
  ```
- **Real-DB GREEN**: backend restarted with exported `JWT_SECRET` (random 64-char) against Docker PostgreSQL (5433). `curl` verified:
  - register → `201`, returns `data.token` (HS512 signed), `data.user` with id/email/name/role
  - login (valid) → `200` + token
  - `/auth/me` with token → `200` correct email/name/role
  - `/auth/me` without token → `401 {error:"Authentication required"}`
  - login wrong password → `401 {error:"Invalid email or password"}`
- **Guaranteed by passing tests**: tokens round-trip (subject + role claim + expiry); expired/tampered/wrong-secret/garbage tokens rejected; duplicate-email register → 409; invalid payloads → 400; passwords stored BCrypt-hashed and verified via `PasswordEncoder.matches`; health endpoints stay public (Phase 1 behavior preserved).

## Test specification

| # | What is guaranteed | Test file : method | Type | Result | Evidence |
|---|--------------------|--------------------|------|--------|----------|
| 1 | Token generate→parse round-trips subject, role, expiry | `JwtServiceTest.generatesTokenThatParsesBackToClaims` | unit | PASS | `mvn test` |
| 2 | Expired token rejected | `JwtServiceTest.rejectsExpiredToken` | unit | PASS | `mvn test` |
| 3 | Tampered token rejected | `JwtServiceTest.rejectsTamperedToken` | unit | PASS | `mvn test` |
| 4 | Wrong-secret token rejected | `JwtServiceTest.rejectsTokenSignedWithDifferentSecret` | unit | PASS | `mvn test` |
| 5 | Garbage input rejected as JWT exception | `JwtServiceTest.rejectsGarbageInput` | unit | PASS | `mvn test` |
| 6 | Register → 201 with token + user, role preserved | `AuthFlowIntegrationTest.registerCreatesUserAndReturnsToken` | integration | PASS | `mvn test` + curl |
| 7 | Password stored hashed, BCrypt-verified | `AuthFlowIntegrationTest.registerStoresHashedPassword` | integration | PASS | `mvn test` |
| 8 | Duplicate email register → 409 | `AuthFlowIntegrationTest.registerRejectsDuplicateEmail` | integration | PASS | `mvn test` |
| 9 | Invalid register payload → 400 | `AuthFlowIntegrationTest.registerRejectsInvalidPayload` | integration | PASS | `mvn test` |
| 10 | Valid login → 200 + token | `AuthFlowIntegrationTest.loginReturnsTokenForValidCredentials` | integration | PASS | `mvn test` + curl |
| 11 | Wrong password → 401 | `AuthFlowIntegrationTest.loginRejectsWrongPassword` | integration | PASS | `mvn test` + curl |
| 12 | Unknown email → 401 | `AuthFlowIntegrationTest.loginRejectsUnknownEmail` | integration | PASS | `mvn test` |
| 13 | `/auth/me` unauthenticated → 401 | `AuthFlowIntegrationTest.meRequiresAuthentication` | integration | PASS | `mvn test` + curl |
| 14 | `/auth/me` with token → 200 current user | `AuthFlowIntegrationTest.meReturnsCurrentUserWithValidToken` | integration | PASS | `mvn test` + curl |
| 15 | Actuator health stays public | `HealthCheckIntegrationTest.actuatorHealthReturnsUp` | integration | PASS | `mvn test` |

## Coverage and known gaps
- No JaCoCo configured (deferred to Phase 14 testing/security). All 15 auth-behavior guarantees above are exercised through real `SecurityFilterChain` or real Hibernate I/O — no mocks for security/repository paths.
- Deliberately deferred: refresh tokens / logout blacklist, email verification, password reset, per-role endpoint policies (arrive with tutor/skill endpoints in Phases 4–5; `ROLE_*` authorities are already assigned so `.hasRole(...)` works).
- Minor deviation from plan: sign algorithm is HS512 (jjwt picks from key size ≥ 32 bytes) rather than the plan's literal HS256 — functionally equal, HS512 is stronger; no test asserted the algorithm.

## Merge evidence
- No git checkpoint commits created (`rules.md` prohibits git mutations without explicit per-action approval). RED/GREEN evidence preserved above and in the `mvn` console output for future squash merges.