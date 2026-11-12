-- Partial guest stays (plan section 9): a guest can stay only part of a reservation. from_date is the guest's first day
-- and to_date the last day (both inclusive, ISO dates). Null means the guest follows the reservation, so existing rows
-- keep their meaning and the statistics do not change.
alter table reservation_guest
    add column from_date TEXT;
alter table reservation_guest
    add column to_date TEXT;
