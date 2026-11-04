-- Purpose-scoped opaque capabilities. Raw QR credentials are never stored.
CREATE TABLE member_check_in_pass (
    pass_id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_member_check_in_pass PRIMARY KEY,
    token_hash CHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL CONSTRAINT uq_member_check_in_pass_hash UNIQUE,
    member_id BIGINT NOT NULL,
    member_version INT NOT NULL,
    purpose VARCHAR(20) NOT NULL CONSTRAINT df_member_check_in_pass_purpose DEFAULT 'MEMBER_ARRIVAL',
    issued_at_utc DATETIME2(7) NOT NULL,
    expires_at_utc DATETIME2(7) NOT NULL,
    redeemed_at_utc DATETIME2(7) NULL,
    check_in_id BIGINT NULL,
    CONSTRAINT fk_member_check_in_pass_member FOREIGN KEY(member_id) REFERENCES member_profile(user_id),
    CONSTRAINT fk_member_check_in_pass_arrival FOREIGN KEY(check_in_id) REFERENCES check_in(id),
    CONSTRAINT ck_member_check_in_pass_version CHECK(member_version>=0),
    CONSTRAINT ck_member_check_in_pass_purpose CHECK(purpose='MEMBER_ARRIVAL'),
    CONSTRAINT ck_member_check_in_pass_expiry CHECK(expires_at_utc>issued_at_utc AND expires_at_utc<=DATEADD(SECOND,120,issued_at_utc)),
    CONSTRAINT ck_member_check_in_pass_redemption CHECK(
        (redeemed_at_utc IS NULL AND check_in_id IS NULL) OR (redeemed_at_utc IS NOT NULL AND check_in_id IS NOT NULL))
);
