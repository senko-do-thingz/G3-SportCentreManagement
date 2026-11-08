-- MW-02: optional, advisory-only Member health questionnaire. One current row per Member.
-- Answers are YES, NO or PREFER_NOT_TO_ANSWER; NULL means the question was skipped.
-- Additive only: no existing table, row or constraint changes.
CREATE TABLE dbo.member_health_assessment (
    member_id               BIGINT       NOT NULL,
    questionnaire_version   VARCHAR(20)  NOT NULL,
    injury_or_mobility      VARCHAR(25)  NULL,
    activity_restriction    VARCHAR(25)  NULL,
    exertion_symptoms       VARCHAR(25)  NULL,
    coach_should_know       VARCHAR(25)  NULL,
    wants_consultation      VARCHAR(25)  NULL,
    share_with_coach        BIT          NOT NULL CONSTRAINT df_mha_share DEFAULT 0,
    consent_updated_at      DATETIME2    NULL,
    created_at              DATETIME2    NOT NULL,
    updated_at              DATETIME2    NOT NULL,
    version                 INT          NOT NULL CONSTRAINT df_mha_version DEFAULT 0,
    CONSTRAINT pk_member_health_assessment PRIMARY KEY (member_id),
    CONSTRAINT fk_mha_member FOREIGN KEY (member_id) REFERENCES dbo.member_profile(user_id),
    CONSTRAINT ck_mha_injury CHECK (injury_or_mobility IS NULL OR injury_or_mobility IN ('YES', 'NO', 'PREFER_NOT_TO_ANSWER')),
    CONSTRAINT ck_mha_restriction CHECK (activity_restriction IS NULL OR activity_restriction IN ('YES', 'NO', 'PREFER_NOT_TO_ANSWER')),
    CONSTRAINT ck_mha_exertion CHECK (exertion_symptoms IS NULL OR exertion_symptoms IN ('YES', 'NO', 'PREFER_NOT_TO_ANSWER')),
    CONSTRAINT ck_mha_coach CHECK (coach_should_know IS NULL OR coach_should_know IN ('YES', 'NO', 'PREFER_NOT_TO_ANSWER')),
    CONSTRAINT ck_mha_consult CHECK (wants_consultation IS NULL OR wants_consultation IN ('YES', 'NO', 'PREFER_NOT_TO_ANSWER')),
    CONSTRAINT ck_mha_version CHECK (questionnaire_version = 'HQ-2026-11')
);
