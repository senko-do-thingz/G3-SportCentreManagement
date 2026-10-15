-- Reuse the existing email, phone and member code unique indexes and code sequence.
CREATE INDEX ix_member_card_queue ON member_card(status, created_at, id);

CREATE INDEX ix_spr_queue ON sport_package_registration(status, created_at, id);

-- V5 already provides ix_check_in_member(member_id, checked_in_at).
-- Its nonunique key includes the clustered primary key id as the final ascending key.
-- For one member_id, a backward scan serves checked_in_at DESC, id DESC.
