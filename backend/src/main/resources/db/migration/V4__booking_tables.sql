-- SkillBook bookings: concurrency-safe session bookings

CREATE TABLE bookings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id UUID NOT NULL,
    slot_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'CONFIRMED',
    message VARCHAR(500),
    amount NUMERIC(8, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    refund_amount NUMERIC(8, 2),
    cancellation_fee NUMERIC(8, 2),
    cancelled_at TIMESTAMPTZ,
    cancelled_by UUID,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_bookings_status CHECK (status IN ('CONFIRMED', 'CANCELLED', 'COMPLETED')),
    CONSTRAINT ck_bookings_currency CHECK (currency = 'USD'),
    CONSTRAINT ck_bookings_amount_non_negative CHECK (amount >= 0),
    CONSTRAINT uk_bookings_slot UNIQUE (slot_id),
    CONSTRAINT fk_bookings_student FOREIGN KEY (student_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_bookings_slot FOREIGN KEY (slot_id) REFERENCES availability_slots (id) ON DELETE RESTRICT,
    CONSTRAINT fk_bookings_cancelled_by FOREIGN KEY (cancelled_by) REFERENCES users (id) ON DELETE SET NULL
);

CREATE INDEX idx_bookings_student ON bookings (student_id);
CREATE INDEX idx_bookings_slot ON bookings (slot_id);
CREATE INDEX idx_bookings_status ON bookings (status);