-- Two-factor authentication (TOTP, RFC 6238) for the administrator, and the profile shown on the
-- administration's home page.

-- Secrets are encrypted (AES-GCM, key derived from JWT_SECRET). totp_last_step stops a code being reused.
ALTER TABLE admin_user
    ADD COLUMN totp_secret         varchar(200),
    ADD COLUMN totp_pending_secret varchar(200),
    ADD COLUMN totp_enabled        boolean NOT NULL DEFAULT false,
    ADD COLUMN totp_last_step      bigint  NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_admin_totp_enabled CHECK (NOT totp_enabled OR totp_secret IS NOT NULL);

-- Single-use recovery codes, stored as SHA-256 hashes.
CREATE TABLE admin_recovery_code (
    id        bigserial   PRIMARY KEY,
    admin_id  uuid        NOT NULL REFERENCES admin_user (id) ON DELETE CASCADE,
    code_hash varchar(64) NOT NULL,
    used_at   timestamptz
);

CREATE INDEX idx_recovery_code_admin ON admin_recovery_code (admin_id);

-- The owner's profile (a single row): name, title, introduction, skills and experiences.
CREATE TABLE profile (
    id             smallint     PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    full_name      varchar(120) NOT NULL,
    headline       varchar(160) NOT NULL,
    intro          text         NOT NULL,
    availability   varchar(300) NOT NULL,
    skills         jsonb        NOT NULL,
    experiences    jsonb        NOT NULL,
    photo_media_id uuid         REFERENCES media (id) ON DELETE SET NULL,
    updated_at     timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_profile_skills CHECK (jsonb_typeof(skills) = 'array'),
    CONSTRAINT ck_profile_experiences CHECK (jsonb_typeof(experiences) = 'array')
);

INSERT INTO profile (id, full_name, headline, intro, availability, skills, experiences)
VALUES (
    1,
    'Votre nom',
    'Ingénieur logiciel & systèmes d''information',
    'Je conçois et développe des applications fiables, de l''analyse du besoin jusqu''à la mise en production : '
        || 'architecture logicielle, bases de données, API et interfaces, intégrées au système d''information.',
    'Je recherche de nouveaux projets où concevoir des systèmes d''information utiles, sûrs et durables.',
    '["Java", "Spring Boot", "React", "TypeScript", "PostgreSQL", "Docker", "Kubernetes", "Linux", "UML / Merise", "Architecture SI"]',
    '[
      {"role": "Ingénieur logiciel", "organization": "Votre entreprise", "period": "2023 – aujourd''hui",
       "description": "Conception d''applications web, API REST et services métier."},
      {"role": "Analyste systèmes d''information", "organization": "Votre organisation", "period": "2021 – 2023",
       "description": "Analyse des besoins, modélisation des données et conduite du changement."},
      {"role": "Développeur full stack", "organization": "Votre premier poste", "period": "2019 – 2021",
       "description": "Développement front et back, intégration continue et déploiement."}
    ]'
);
