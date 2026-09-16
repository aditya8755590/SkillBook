# Implementation Plan: Phase 3 — Authentication + JWT + Roles

## Task Type
- [x] Backend security/auth layer (Spring Security + JWT, jjwt 0.12.6 already in pom)
- Endpoints: `/api/auth/register`, `/api/auth/login`, `/api/auth/me` (protected, for frontend wiring in a later /frontend pass)

## Scope (from roadmap / Phase 1 plan)
- Protect every endpoint except public ones (register, login, actuator health/info)
- JWT-based stateless session (ignore Spring's default in-memory user)
- BCrypt password hashing (no plaintext)
- Role support (`STUDENT` / `TUTOR` / `ADMIN`) in claims so `.hasRole(...)` works later
- Config via env vars: `JWT_SECRET`, `JWT_EXPIRATION_MS`

## Routes

| Method | Path | Access | Body → Response |
|--------|------|--------|-----------------|
| POST | `/api/auth/register` | PUBLIC | `RegisterRequest(name,email,passwordHash,role)` → `ApiResponse<AuthResponse>` |
| POST | `/api/auth/login` | PUBLIC | `LoginRequest(email,passwordHash)` → `ApiResponse<AuthResponse>` |
| GET | `/api/auth/me` | AUTHENTICATED | → `ApiResponse<UserResponse>` of current user |

`AuthResponse = { token: String, tokenType: "Bearer", expiresIn: long, user: UserResponse }`
`UserResponse = { id, email, name, role }`

## Decisions

### 1. `@ConfigurationProperties(prefix = "jwt")` — `JwtProperties`
- `secret` (required, >= 32 chars), `expiration-ms` (default 86400000)
- Bound from `JWT_SECRET` / `JWT_EXPIRATION_MS`. Missing secret -> startup failure with clear message (fails fast, not at first request).
- Registered via `@EnableConfigurationProperties(JwtProperties.class)` on `SkillBookApplication`.

### 2. `JwtService` (thin, no Spring beans in unit tests)
- `generateToken(user)` -> JWS HS256, subject = email, claim `role`
- `parseToken(token)` -> `io.jsonwebtoken.Claims`
- Throws typed jjwt exceptions (ExpiredJwtException, JwtException, SignatureException) — filter translates to 401.

### 3. `JwtAuthenticationFilter extends OncePerRequestFilter`
- Reads `Authorization: Bearer <token>`, parses, loads `UserDetails` via `CustomUserDetailsService`, builds `UsernamePasswordAuthenticationToken` (authorities = role), sets it in the `SecurityContext`.
- No token -> filter chain continues; anonymous request to a protected route is rejected by the `AuthenticationEntryPoint` (401 JSON), not by the filter.

### 4. `SecurityConfig` stateless chain
- csrf disabled, sessionManagement STATELESS
- permitAll: `/auth/register`, `/auth/login`, `/actuator/health`, `/actuator/info`, `/error`
- anyRequest authenticated
- custom `AuthenticationEntryPoint` (401) + `AccessDeniedHandler` (403) returning `ApiResponse.error(...)` JSON
- `PasswordEncoder` bean = `BCryptPasswordEncoder`
- filter added before `UsernamePasswordAuthenticationFilter`

### 5. `CustomUserDetailsService`
- Looks up `User` by email via `UserRepository`, maps to Spring `UserDetails` with `ROLE_<role>` authority.
- Exists so the app no longer auto-creates Spring's random in-memory user (removes the "Generated security password" noise from logs).

### 6. Registration semantics
- Reject duplicate email with 409 (IllegalStateException -> existing `GlobalExceptionHandler`).
- Validation via jakarta on `RegisterRequest`/`LoginRequest` -> 400 handled by existing handler.
- BCrypt-encode the password; never return or log it.

### 7. Tests
- **Integration** (`AuthFlowIntegrationTest`, `@SpringBootTest` + MockMvc, test profile H2):
  - register success -> 200/201, token + user in body, token parses, role correct
  - register duplicate email -> 409
  - register validation failure (bad email / short password) -> 400
  - login success -> token
  - login wrong password -> 401
  - login unknown email -> 401
  - `/auth/me` without token -> 401; with token -> 200 + correct email
  - BCrypt: stored password_hash != plaintext (check via repository)
- **Unit** (`JwtServiceTest`, plain JUnit):
  - round-trip generate->parse returns claims (subject, role, expiry)
  - expired token rejected
  - tampered token rejected
  - wrong-secret token rejected
- `HealthCheckIntegrationTest` still passes (actuator permitAll unchanged).

## Files Created / Modified
| File | Kind |
|------|------|
| `auth/JwtProperties.java` | new |
| `auth/JwtService.java` | new |
| `auth/JwtAuthenticationFilter.java` | new |
| `auth/AuthService.java` | new |
| `auth/AuthController.java` | new |
| `auth/CustomUserDetailsService.java` | new |
| `auth/dto/RegisterRequest.java`, `LoginRequest.java`, `AuthResponse.java`, `UserResponse.java` | new |
| `config/SecurityConfig.java` | modify (stateless JWT) |
| `SkillBookApplication.java` | modify (EnableConfigurationProperties) |
| `exception/GlobalExceptionHandler.java` | modify (403/401?) — 401 via entry point |
| `application.yml` | add jwt config (env-backed) |
| `application-test.yml` | add test JWT_SECRET |
| tests: `auth/AuthFlowIntegrationTest.java`, `auth/JwtServiceTest.java` | new |
| `.env.example` | JWT already present — verify |
| `README.md` | mark Phase 3 done + document curl usage |

## Verification
```bash
cd backend && JAVA_HOME=<temurin21> mvn test
# restart backend with JWT_SECRET exported; curl register/login/me against Docker PG
```

## Deferred (later phases)
- Refresh tokens, logout blacklist (out of scope for MVP)
- Email verification
- Password reset
- Role-based endpoint policies beyond `/me` (applies when tutor/skill endpoints arrive in Phases 4–5)