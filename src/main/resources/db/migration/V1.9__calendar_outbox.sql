-- Outbox of Google Calendar notifications (plan section 5). A reservation write inserts one row in the same
-- transaction; a background task pushes the pending rows to Google and deletes them, so only pending and failing rows
-- stay. The table is internal: no audit columns, not exposed in the API.
--
-- reservation_id has no foreign key on purpose: a DELETE row outlives the reservation it refers to. It is also the
-- Google Calendar event id.
create table calendar_outbox
(
    id                INTEGER       not null primary key autoincrement,
    reservation_id    VARCHAR(1024) not null,
    operation         VARCHAR(10)   not null,
    created_time      TEXT          not null,
    attempts          INTEGER       not null default 0,
    last_error        TEXT,
    next_attempt_time TEXT          not null,
    constraint chk_calendar_outbox_operation check (operation in ('INSERT', 'UPDATE', 'DELETE'))
);

-- The drain picks the pending rows that are due, oldest first
create index idx_calendar_outbox_next_attempt_time on calendar_outbox (next_attempt_time, id);
