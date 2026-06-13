ALTER TABLE product_versions
    ADD COLUMN parent_id BIGINT REFERENCES product_versions(id) ON DELETE RESTRICT,
    ADD CONSTRAINT chk_product_versions_not_self_parent CHECK (parent_id IS NULL OR parent_id <> id);

CREATE INDEX idx_product_versions_parent ON product_versions(parent_id);
CREATE INDEX idx_tickets_creator_status_created ON tickets(creator_id, status, created_at DESC);
CREATE INDEX idx_tickets_resolved_at ON tickets(resolved_at DESC) WHERE resolved_at IS NOT NULL;

