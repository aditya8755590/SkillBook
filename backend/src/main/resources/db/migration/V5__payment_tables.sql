-- SkillBook payments: Stripe-hosted checkout for bookings

CREATE TABLE payments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id UUID NOT NULL,
    student_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    amount NUMERIC(8, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    stripe_checkout_session_id VARCHAR(255),
    stripe_payment_intent_id VARCHAR(255),
    stripe_refund_id VARCHAR(255),
    refund_amount NUMERIC(8, 2),
    paid_at TIMESTAMPTZ,
    refunded_at TIMESTAMPTZ,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_payments_status CHECK (status IN ('PENDING', 'PAID', 'FAILED', 'REFUNDED', 'PARTIALLY_REFUNDED')),
    CONSTRAINT ck_payments_currency CHECK (currency = 'USD'),
    CONSTRAINT ck_payments_amount_non_negative CHECK (amount >= 0),
    CONSTRAINT uk_payments_booking UNIQUE (booking_id),
    CONSTRAINT uk_payments_checkout_session UNIQUE (stripe_checkout_session_id),
    CONSTRAINT fk_payments_booking FOREIGN KEY (booking_id) REFERENCES bookings (id) ON DELETE CASCADE,
    CONSTRAINT fk_payments_student FOREIGN KEY (student_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_payments_student ON payments (student_id);
CREATE INDEX idx_payments_status ON payments (status);