# SkillBook — Demo Credentials

Ready-made accounts so you can log in and explore every role immediately.
Accounts are stored in the Docker Postgres volume, so you create them **once**
and they survive restarts.

## The three accounts

| Role    | Email                  | Password       |
| ------- | ---------------------- | -------------- |
| Tutor   | `tutor@skillbook.demo` | `TutorDemo123!` |
| Student | `student@skillbook.demo` | `StudentDemo123!` |
| Admin   | `admin@skillbook.demo` | `AdminDemo123!` |

Login at **http://localhost:5173/login** with any of these.

## How to create them (one-time, backend must be running)

Copy-paste into a terminal (backend at `http://localhost:8080/api`):

```bash
# Tutor
curl -s -X POST http://localhost:8080/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"name":"Demo Tutor","email":"tutor@skillbook.demo","password":"TutorDemo123!","role":"TUTOR"}'

# Student
curl -s -X POST http://localhost:8080/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"name":"Demo Student","email":"student@skillbook.demo","password":"StudentDemo123!","role":"STUDENT"}'

# Admin (the UI only offers Student/Tutor, so use the API)
curl -s -X POST http://localhost:8080/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"name":"Demo Admin","email":"admin@skillbook.demo","password":"AdminDemo123!","role":"ADMIN"}'
```

`"success":true` = created. If you get an "email already exists" error, the
account already exists — just log in.

Verify quickly via the API:

```bash
curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"tutor@skillbook.demo","password":"TutorDemo123!"}' | head -c 300
```

## What to explore as each role

- **Tutor** — on http://localhost:5173/ go to *My Profile* (set bio + hourly
  rate + skills), then *Availability* (add weekly rules, generate slots).
- **Student** — *Find a Tutor* on the home page, open a tutor, book a slot,
  then *My Bookings* (pay / cancel; a full refund applies > 24h before start).
- **Admin** — log in (any browser) to confirm the role loads; full admin
  management screens are not built yet (backend role checks exist).

> Note: a `FACULTY` role does not exist — the three roles are
> STUDENT / TUTOR / ADMIN. A "faculty" teaching account is the Tutor role.