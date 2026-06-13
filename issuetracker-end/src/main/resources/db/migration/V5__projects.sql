CREATE TABLE projects (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_by BIGINT REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO projects(id, code, name, description, enabled, created_by)
VALUES (
    1,
    'DEFAULT',
    '默认项目',
    '用于承接项目功能上线前的历史用户和问题单',
    TRUE,
    (SELECT id FROM users WHERE deleted = FALSE ORDER BY id LIMIT 1)
);

SELECT setval(pg_get_serial_sequence('projects', 'id'), 1, TRUE);

CREATE TABLE project_members (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_project_members_project_user UNIQUE (project_id, user_id)
);

INSERT INTO project_members(project_id, user_id)
SELECT 1, id
FROM users
WHERE deleted = FALSE
ON CONFLICT DO NOTHING;

ALTER TABLE tickets
    ADD COLUMN project_id BIGINT NOT NULL DEFAULT 1 REFERENCES projects(id);

ALTER TABLE tickets
    ALTER COLUMN project_id DROP DEFAULT;

CREATE INDEX idx_project_members_user_project ON project_members(user_id, project_id);
CREATE INDEX idx_project_members_project_created ON project_members(project_id, created_at);
CREATE INDEX idx_projects_enabled_name ON projects(enabled, name);
CREATE INDEX idx_tickets_project_created ON tickets(project_id, created_at DESC);
CREATE INDEX idx_tickets_project_status_created ON tickets(project_id, status, created_at DESC);

INSERT INTO permissions(code, name)
VALUES ('project:manage', '管理项目和项目成员')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions(role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.code IN ('ADMIN', 'MANAGER')
  AND p.code = 'project:manage'
ON CONFLICT DO NOTHING;
