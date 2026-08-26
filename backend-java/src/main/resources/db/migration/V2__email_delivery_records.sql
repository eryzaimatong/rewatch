-- First real migration since V1's inert baseline (see V1__baseline.sql for
-- why it's a no-op). ddl-auto=update would normally create this table too,
-- but tracking it explicitly here means it isn't at the mercy of "whatever
-- Hibernate infers" for a table that exists specifically to be queried by
-- ops/monitoring, not just by the app itself — the column names and types
-- matter to a human running `SELECT * FROM email_delivery_records WHERE
-- status = 'FAILED'`, not just to Hibernate's own mapping.
create table if not exists email_delivery_records (
    id bigserial primary key,
    correlation_id varchar(64) not null,
    recipient varchar(255) not null,
    type varchar(20) not null,
    status varchar(20) not null,
    provider_message_id varchar(255),
    error_message text,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create index if not exists idx_email_delivery_status on email_delivery_records (status);
create index if not exists idx_email_delivery_correlation_id on email_delivery_records (correlation_id);
