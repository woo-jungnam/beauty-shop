-- V63__fix_loyalty_point_awards_check_constraint.sql
-- Allow points to be 0 when loyalty award is reversed on full order return or cancellation.

ALTER TABLE loyalty_point_awards DROP CONSTRAINT chk_loyalty_award_points;
ALTER TABLE loyalty_point_awards ADD CONSTRAINT chk_loyalty_award_points CHECK (points >= 0);
