-- SkillBook core tables: tutor_profiles, student_profiles, skills, skill_offered, skill_wanted

CREATE TABLE tutor_profiles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    bio VARCHAR(2000),
    experience_years INTEGER NOT NULL DEFAULT 0,
    hourly_rate NUMERIC(8, 2),
    rating NUMERIC(3, 2) NOT NULL DEFAULT 0.00,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_tutor_profiles_user UNIQUE (user_id),
    CONSTRAINT fk_tutor_profiles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE student_profiles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    bio VARCHAR(2000),
    education_level VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_student_profiles_user UNIQUE (user_id),
    CONSTRAINT fk_student_profiles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE skills (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL,
    category VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_skills_name UNIQUE (name)
);

CREATE TABLE skill_offered (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tutor_id UUID NOT NULL,
    skill_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_skill_offered_tutor_skill UNIQUE (tutor_id, skill_id),
    CONSTRAINT fk_skill_offered_tutor FOREIGN KEY (tutor_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_skill_offered_skill FOREIGN KEY (skill_id) REFERENCES skills (id) ON DELETE CASCADE
);

CREATE TABLE skill_wanted (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id UUID NOT NULL,
    skill_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_skill_wanted_student_skill UNIQUE (student_id, skill_id),
    CONSTRAINT fk_skill_wanted_student FOREIGN KEY (student_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_skill_wanted_skill FOREIGN KEY (skill_id) REFERENCES skills (id) ON DELETE CASCADE
);

CREATE INDEX idx_skill_offered_tutor ON skill_offered (tutor_id);
CREATE INDEX idx_skill_offered_skill ON skill_offered (skill_id);
CREATE INDEX idx_skill_wanted_student ON skill_wanted (student_id);
CREATE INDEX idx_skill_wanted_skill ON skill_wanted (skill_id);
CREATE INDEX idx_skills_category ON skills (category);