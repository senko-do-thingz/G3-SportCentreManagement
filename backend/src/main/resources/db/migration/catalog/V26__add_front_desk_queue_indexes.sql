-- V24 and V25 are reserved on the refund and revenue report feature branches.
-- Reuse the existing email, phone and member code unique indexes and code sequence.
CREATE INDEX ix_member_card_queue ON member_card(status, created_at, id)
    INCLUDE (member_id, tier_id, price_paid, start_date, end_date, card_code);

CREATE INDEX ix_spr_queue ON sport_package_registration(status, created_at, id)
    INCLUDE (member_id, package_id, paid_amount);

CREATE INDEX ix_check_in_member_recent ON check_in(member_id, checked_in_at DESC, id DESC);
