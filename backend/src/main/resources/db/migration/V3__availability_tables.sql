-- SkillBook availability: weekly rules + materialized concrete slots

CREATE TABLE availability_rules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tutor_id UUID NOT NULL,
    day_of_week SMALLINT NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    timezone VARCHAR(64) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_availability_rules_day CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT ck_availability_rules_time_range CHECK (end_time > start_time),
    CONSTRAINT uk_availability_rules_tutor_day_start UNIQUE (tutor_id, day_of_week, start_time),
    CONSTRAINT fk_availability_rules_tutor FOREIGN KEY (tutor_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE availability_slots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tutor_id UUID NOT NULL,
    start_at TIMESTAMPTZ NOT NULL,
    end_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    source VARCHAR(20) NOT NULL,
    rule_id UUID,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_availability_slots_status CHECK (status IN ('AVAILABLE', 'BOOKED')),
    CONSTRAINT ck_availability_slots_source CHECK (source IN ('RULE', 'ONE_OFF')),
    CONSTRAINT ck_availability_slots_time_range CHECK (end_at > start_at),
    CONSTRAINT uk_availability_slots_tutor_start UNIQUE (tutor_id, start_at),
    CONSTRAINT fk_availability_slots_tutor FOREIGN KEY (tutor_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_availability_slots_rule FOREIGN KEY (rule_id) REFERENCES availability_rules (id) ON DELETE CASCADE
);

CREATE INDEX idx_availability_rules_tutor ON availability_rules (tutor_id);
CREATE INDEX idx_availability_slots_tutor_start ON availability_slots (tutor_id, start_at);
CREATE INDEX idx_availability_slots_tutor_status ON availability_slots (tutor_id, status);