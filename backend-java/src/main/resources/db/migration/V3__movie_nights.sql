-- Movie Night: a shared, no-account "what do we all watch?" session.
-- A night is a short code + an expiry; each friend who joins leaves a name and
-- five mood answers (stored as the raw answer indexes plus the derived trait
-- vector, so results can be recomputed if the scoring ever changes).
create table if not exists movie_nights (
    id bigserial primary key,
    code varchar(8) not null unique,
    created_at timestamptz not null,
    expires_at timestamptz not null
);

create table if not exists movie_night_members (
    id bigserial primary key,
    night_id bigint not null references movie_nights (id) on delete cascade,
    name varchar(24) not null,
    answers varchar(32) not null,
    vector varchar(160) not null,
    joined_at timestamptz not null
);

create index if not exists idx_movie_night_members_night on movie_night_members (night_id);
create index if not exists idx_movie_nights_expires on movie_nights (expires_at);
