create table app_metadata (
    id bigint primary key,
    metadata_key varchar(255) unique not null,
    metadata_value varchar(255),
    created_at timestamp not null
);

insert into app_metadata (id, metadata_key, metadata_value, created_at)
values (1, 'schema_version', 'initial', current_timestamp);
