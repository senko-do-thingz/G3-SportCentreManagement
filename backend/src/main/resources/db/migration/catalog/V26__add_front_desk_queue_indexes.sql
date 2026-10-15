-- V24 and V25 are reserved on the refund and revenue report feature branches.
-- Reuse the existing email, phone and member code unique indexes and code sequence.
CREATE INDEX ix_member_card_queue ON member_card(status, created_at, id);

CREATE INDEX ix_spr_queue ON sport_package_registration(status, created_at, id);

-- V5 already provides ix_check_in_member(member_id, checked_in_at).
-- A backward index scan serves recent arrivals for one member; no duplicate index is needed.
