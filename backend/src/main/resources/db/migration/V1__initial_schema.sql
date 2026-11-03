CREATE TABLE app_metadata (
    id BIGSERIAL PRIMARY KEY,
    app_key VARCHAR(100) NOT NULL UNIQUE,
    app_value VARCHAR(500) NOT NULL
);

INSERT INTO app_metadata (app_key, app_value)
VALUES ('schema_version', '1');
