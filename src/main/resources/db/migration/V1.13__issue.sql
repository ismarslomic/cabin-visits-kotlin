-- Operational problems reported through IssueReporter (JLR Data Hub login, place lookups, trip fetch). One row per
-- code and issue_key: a repeat of an open problem updates the row, a repeat of a resolved one reopens it. The severity is
-- not stored, it comes from the code. `key` is a SQL keyword, hence issue_key. The audit columns are the same as on the
-- other tables; version changes only when status or read_time changes.
create table issue
(
    id                  INTEGER      not null primary key autoincrement,
    code                VARCHAR(50)  not null,
    issue_key           VARCHAR(255) not null,
    status              VARCHAR(10)  not null default 'OPEN',
    message             TEXT         not null,
    cause_type          VARCHAR(255),
    occurrence_count    INTEGER      not null default 1,
    first_occurred_time TEXT         not null,
    last_occurred_time  TEXT         not null,
    resolved_time       TEXT,
    read_time           TEXT,
    created_time        TEXT         not null,
    created_by          TEXT         not null,
    updated_time        TEXT,
    updated_by          TEXT,
    version             SMALLINT     not null default 1,
    constraint chk_issue_status check (status in ('OPEN', 'RESOLVED')),
    constraint chk_issue_signed_short_version check (version BETWEEN -32768 AND 32767)
);

create unique index uq_issue_code_key on issue (code, issue_key);

-- The list and the unread count filter on status and read_time
create index idx_issue_status_read_time on issue (status, read_time);
