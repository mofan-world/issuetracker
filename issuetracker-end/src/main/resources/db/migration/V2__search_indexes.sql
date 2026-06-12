CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX idx_tickets_title_trgm
    ON tickets USING GIN (lower(title) gin_trgm_ops);

CREATE INDEX idx_tickets_description_trgm
    ON tickets USING GIN (lower(description) gin_trgm_ops);

CREATE INDEX idx_tickets_number_trgm
    ON tickets USING GIN (lower(ticket_no) gin_trgm_ops);

CREATE INDEX idx_users_username_trgm
    ON users USING GIN (lower(username) gin_trgm_ops);

CREATE INDEX idx_users_display_name_trgm
    ON users USING GIN (lower(display_name) gin_trgm_ops);
