# SkillBook

A full-stack tutor/freelancer session booking platform where students can discover tutors, view real-time availability, and book sessions — with built-in protection against double-booking, secure authentication, and Stripe-powered payments.

## 📌 Overview

SkillBook connects students with independent tutors. Tutors set their availability and subjects taught; students browse tutors, book open slots, and pay securely online. The project focuses on solving real backend engineering problems: relational schema design, race-condition-safe booking, role-based access control, and third-party payment integration.

## ✨ Features

- **Two user roles** — Student and Tutor, each with a dedicated dashboard
- **Authentication** — secure signup/login using JWT
- **Tutor profiles** — subjects taught, hourly rate, bio, ratings
- **Availability management** — tutors define weekly or one-off available slots
- **Real-time booking** — students book open slots with no double-booking, enforced at the database level
- **Cancellation & rescheduling** — with configurable policies (e.g., no cancellations within 2 hours of a session)
- **Payments** — Stripe integration to charge students on booking
- **Email notifications** — booking confirmations and reminders
- **Reviews & ratings** — students rate tutors after completed sessions

## 🛠️ Tech Stack

| Layer          | Technology                  |
|----------------|------------------------------|
| Frontend       | React (Next.js)              |
| Backend        | Node.js + Express            |
| Database       | PostgreSQL                   |
| Auth           | JWT                          |
| Payments       | Stripe API                   |
| Notifications  | SendGrid / Nodemailer        |
| Deployment     | Docker, hosted on Render/Railway |

> Update this table to match your actual stack if it differs.

## 🗂️ Project Structure

```
SkillBook/
├── client/              # React frontend
│   ├── src/
│   │   ├── components/
│   │   ├── pages/
│   │   └── services/
├── server/              # Node.js/Express backend
│   ├── src/
│   │   ├── controllers/
│   │   ├── models/
│   │   ├── routes/
│   │   ├── middleware/
│   │   └── config/
├── docker-compose.yml
├── .env.example
└── README.md
```

## 🧱 Database Schema (core tables)

- **users** — id, name, email, password_hash, role (student/tutor)
- **tutor_profiles** — user_id, subjects, hourly_rate, bio
- **availability** — tutor_id, day_of_week / date, start_time, end_time
- **bookings** — id, tutor_id, student_id, slot_time, status, created_at
- **payments** — id, booking_id, amount, stripe_payment_id, status
- **reviews** — id, tutor_id, student_id, rating, comment

## ⚙️ Getting Started

### Prerequisites
- Node.js (v18+)
- PostgreSQL
- Docker (optional, for containerized setup)
- Stripe account (test mode API keys)

### Installation

```bash
# Clone the repository
git clone https://github.com/yourusername/tutorlink.git
cd tutorlink

# Install backend dependencies
cd server
npm install

# Install frontend dependencies
cd ../client
npm install
```

### Environment Variables

Create a `.env` file in the `server/` directory based on `.env.example`:

```
DATABASE_URL=postgresql://user:password@localhost:5432/tutorlink
JWT_SECRET=your_jwt_secret
STRIPE_SECRET_KEY=your_stripe_secret_key
EMAIL_API_KEY=your_email_service_key
```

### Running the App

```bash
# Start backend
cd server
npm run dev

# Start frontend
cd client
npm run dev
```

The app should now be running at `http://localhost:3000` (frontend) and `http://localhost:5000` (backend API).

### Running with Docker

```bash
docker-compose up --build
```

## 🔑 Key Engineering Challenges Solved

- **Preventing double-booking** — using database-level row locking / unique constraints so two students can't book the same slot simultaneously, even under concurrent requests.
- **Role-based access control** — separate permissions and views for tutors vs. students.
- **Payment integration** — secure Stripe checkout flow tied to booking confirmation.
- **Time zone handling** — consistent slot display across different student/tutor time zones.

## 🚀 Future Improvements

- Recurring weekly bookings
- Waitlist with auto-booking when a slot opens
- Google Calendar sync
- In-app messaging between tutor and student

## 📄 License

This project is for educational/portfolio purposes.

## 👤 Author

[Your Name] — [Your Email/LinkedIn/GitHub]
