-- Phase 9: in-app notifications + one-shot session reminder marker

CREATE TABLE notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    booking_id UUID NULL REFERENCES bookings (id) ON DELETE SET NULL,
    type VARCHAR(30) NOT NULL,
    message VARCHAR(500) NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_notifications_user ON notifications (user_id, is_read, created_at);

ALTER TABLE bookings ADD COLUMN reminder_sent_at TIMESTAMPTZ NULL;
