-- Flow 5 member recommendations (F5-01, F5-05).
-- member_sport_level: an optional level per sport; member_profile.current_level stays the general fallback.
-- saved_recommendation: classes a Member saved to review later. It is not a booking and reserves nothing.
CREATE TABLE member_sport_level (
    member_id BIGINT NOT NULL,
    sport_id BIGINT NOT NULL,
    level NVARCHAR(20) NOT NULL,
    updated_at DATETIME2(0) NOT NULL CONSTRAINT df_member_sport_level_updated_at DEFAULT SYSDATETIME(),
    CONSTRAINT pk_member_sport_level PRIMARY KEY (member_id, sport_id),
    CONSTRAINT fk_member_sport_level_member FOREIGN KEY (member_id) REFERENCES member_profile(user_id),
    CONSTRAINT fk_member_sport_level_sport FOREIGN KEY (sport_id) REFERENCES sport(id),
    CONSTRAINT ck_member_sport_level_level CHECK (level IN ('BEGINNER', 'INTERMEDIATE', 'ADVANCED'))
);
GO
CREATE TABLE saved_recommendation (
    id BIGINT IDENTITY(1,1) NOT NULL,
    member_id BIGINT NOT NULL,
    class_id BIGINT NOT NULL,
    reason NVARCHAR(500) NULL,
    created_at DATETIME2(0) NOT NULL CONSTRAINT df_saved_recommendation_created_at DEFAULT SYSDATETIME(),
    CONSTRAINT pk_saved_recommendation PRIMARY KEY (id),
    CONSTRAINT fk_saved_recommendation_member FOREIGN KEY (member_id) REFERENCES member_profile(user_id),
    CONSTRAINT fk_saved_recommendation_class FOREIGN KEY (class_id) REFERENCES sport_class(id),
    CONSTRAINT ux_saved_recommendation_member_class UNIQUE (member_id, class_id)
);
GO
