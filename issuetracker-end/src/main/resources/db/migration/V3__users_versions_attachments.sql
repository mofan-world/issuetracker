ALTER TABLE users
    ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN deleted_at TIMESTAMPTZ;

CREATE TABLE product_versions (
    id BIGSERIAL PRIMARY KEY,
    version_no VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    status VARCHAR(20) NOT NULL,
    release_date DATE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_by BIGINT REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO product_versions(version_no, name, description, status, enabled)
VALUES ('LEGACY', '历史版本', 'V3 数据迁移前创建的问题单所属版本', 'ARCHIVED', FALSE);

INSERT INTO product_versions(version_no, name, description, status, enabled)
VALUES ('1.0.0', '初始版本', '系统初始化创建的可用版本', 'ACTIVE', TRUE);

ALTER TABLE tickets
    ADD COLUMN affected_version_id BIGINT REFERENCES product_versions(id),
    ADD COLUMN resolved_version_id BIGINT REFERENCES product_versions(id);

UPDATE tickets
SET affected_version_id = (SELECT id FROM product_versions WHERE version_no = 'LEGACY')
WHERE affected_version_id IS NULL;

ALTER TABLE tickets
    ALTER COLUMN affected_version_id SET NOT NULL;

CREATE TABLE ticket_attachments (
    id BIGSERIAL PRIMARY KEY,
    ticket_id BIGINT NOT NULL REFERENCES tickets(id) ON DELETE CASCADE,
    uploader_id BIGINT NOT NULL REFERENCES users(id),
    original_name VARCHAR(255) NOT NULL,
    storage_key VARCHAR(255) NOT NULL UNIQUE,
    content_type VARCHAR(150),
    file_size BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_users_deleted_created ON users(deleted, created_at DESC);
CREATE INDEX idx_product_versions_enabled_status ON product_versions(enabled, status, created_at DESC);
CREATE INDEX idx_tickets_affected_version ON tickets(affected_version_id, status);
CREATE INDEX idx_tickets_resolved_version ON tickets(resolved_version_id, status);
CREATE INDEX idx_ticket_attachments_ticket_created ON ticket_attachments(ticket_id, created_at);

INSERT INTO permissions(code, name) VALUES
('ticket:update', '更新本人问题单'),
('ticket:update:all', '更新全部问题单'),
('attachment:delete:all', '删除问题单附件'),
('version:manage', '管理产品版本')
ON CONFLICT (code) DO NOTHING;

INSERT INTO roles(code, name) VALUES
('TESTER', '测试人员'),
('DEVELOPER', '开发人员'),
('MANAGER', '管理人员')
ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name;

INSERT INTO role_permissions(role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.code = 'USER'
  AND p.code IN ('ticket:create', 'ticket:read:own', 'ticket:update')
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions(role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.code IN ('AGENT', 'DEVELOPER')
  AND p.code IN ('ticket:create', 'ticket:read:own', 'ticket:read:all', 'ticket:process')
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions(role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.code IN ('REVIEWER', 'TESTER')
  AND p.code IN
      ('ticket:create', 'ticket:read:own', 'ticket:read:all', 'ticket:update', 'ticket:verify', 'ticket:close')
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions(role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.code IN ('MANAGER', 'ADMIN')
ON CONFLICT DO NOTHING;
