CREATE TABLE permissions (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE roles (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE role_permissions (
    role_id BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_id BIGINT NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE tickets (
    id BIGSERIAL PRIMARY KEY,
    ticket_no VARCHAR(40) NOT NULL UNIQUE,
    title VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    category VARCHAR(50) NOT NULL,
    priority VARCHAR(20) NOT NULL,
    status VARCHAR(30) NOT NULL,
    creator_id BIGINT NOT NULL REFERENCES users(id),
    assignee_id BIGINT REFERENCES users(id),
    resolution TEXT,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMPTZ,
    verified_at TIMESTAMPTZ,
    closed_at TIMESTAMPTZ
);

CREATE TABLE ticket_transitions (
    id BIGSERIAL PRIMARY KEY,
    ticket_id BIGINT NOT NULL REFERENCES tickets(id) ON DELETE CASCADE,
    operator_id BIGINT NOT NULL REFERENCES users(id),
    from_status VARCHAR(30),
    to_status VARCHAR(30) NOT NULL,
    action VARCHAR(30) NOT NULL,
    comment VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_tickets_creator_created ON tickets(creator_id, created_at DESC);
CREATE INDEX idx_tickets_assignee_status ON tickets(assignee_id, status);
CREATE INDEX idx_tickets_status_priority_created ON tickets(status, priority, created_at DESC);
CREATE INDEX idx_ticket_transitions_ticket_created ON ticket_transitions(ticket_id, created_at);
CREATE INDEX idx_users_created_at ON users(created_at DESC);

INSERT INTO permissions(code, name) VALUES
('ticket:create', '创建问题单'),
('ticket:read:own', '查看本人相关问题单'),
('ticket:read:all', '查看全部问题单'),
('ticket:assign', '分派问题单'),
('ticket:process', '处理问题单'),
('ticket:verify', '验证问题单'),
('ticket:close', '关闭问题单'),
('user:manage', '管理用户和角色');

INSERT INTO roles(code, name) VALUES
('USER', '普通用户'),
('AGENT', '处理人员'),
('REVIEWER', '验证人员'),
('ADMIN', '系统管理员');

INSERT INTO role_permissions(role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.code = 'USER' AND p.code IN ('ticket:create', 'ticket:read:own');

INSERT INTO role_permissions(role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.code = 'AGENT' AND p.code IN
('ticket:create', 'ticket:read:own', 'ticket:read:all', 'ticket:process');

INSERT INTO role_permissions(role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.code = 'REVIEWER' AND p.code IN
('ticket:create', 'ticket:read:own', 'ticket:read:all', 'ticket:verify', 'ticket:close');

INSERT INTO role_permissions(role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.code = 'ADMIN';

