\set ON_ERROR_STOP on
\timing on

\if :{?load_password}
\else
  \set load_password 'LoadTest@123'
\endif

BEGIN;
SET LOCAL synchronous_commit = off;
CREATE EXTENSION IF NOT EXISTS pgcrypto;

INSERT INTO product_versions(version_no, name, description, status, enabled)
VALUES (
    'LOAD-TEST-1.0',
    '百万问题单压测版本',
    '由 scripts/load-test/generate-load-test-data.sql 创建',
    'ACTIVE',
    TRUE
)
ON CONFLICT (version_no) DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    status = EXCLUDED.status,
    enabled = EXCLUDED.enabled,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO projects(code, name, description, enabled, created_by)
VALUES (
    'LOAD_TEST',
    '百万问题单压测项目',
    '由 scripts/load-test/generate-load-test-data.sql 创建',
    TRUE,
    (SELECT id FROM users WHERE username = 'admin' LIMIT 1)
)
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    enabled = TRUE,
    updated_at = CURRENT_TIMESTAMP;

WITH password_value AS (
    SELECT crypt(:'load_password', gen_salt('bf', 12)) AS password_hash
),
generated_users AS (
    SELECT
        'load_tester_' || lpad(number::text, 4, '0') AS username,
        'load_tester_' || lpad(number::text, 4, '0') || '@example.test' AS email,
        '压测测试人员 ' || lpad(number::text, 4, '0') AS display_name
    FROM generate_series(1, 1000) AS number
    UNION ALL
    SELECT
        'load_developer_' || lpad(number::text, 4, '0'),
        'load_developer_' || lpad(number::text, 4, '0') || '@example.test',
        '压测开发人员 ' || lpad(number::text, 4, '0')
    FROM generate_series(1, 1000) AS number
    UNION ALL
    SELECT
        'load_admin_' || lpad(number::text, 3, '0'),
        'load_admin_' || lpad(number::text, 3, '0') || '@example.test',
        '压测管理员 ' || lpad(number::text, 3, '0')
    FROM generate_series(1, 50) AS number
)
INSERT INTO users(
    username,
    email,
    password_hash,
    display_name,
    enabled,
    deleted,
    deleted_at,
    created_at,
    updated_at
)
SELECT
    generated_users.username,
    generated_users.email,
    password_value.password_hash,
    generated_users.display_name,
    TRUE,
    FALSE,
    NULL,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM generated_users
CROSS JOIN password_value
ON CONFLICT (username) DO UPDATE SET
    email = EXCLUDED.email,
    password_hash = EXCLUDED.password_hash,
    display_name = EXCLUDED.display_name,
    enabled = TRUE,
    deleted = FALSE,
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO user_roles(user_id, role_id)
SELECT user_account.id, role.id
FROM users user_account
JOIN roles role ON role.code = CASE
    WHEN user_account.username LIKE 'load_tester_%' THEN 'TESTER'
    WHEN user_account.username LIKE 'load_developer_%' THEN 'DEVELOPER'
    WHEN user_account.username LIKE 'load_admin_%' THEN 'ADMIN'
END
WHERE user_account.username LIKE 'load_tester_%'
   OR user_account.username LIKE 'load_developer_%'
   OR user_account.username LIKE 'load_admin_%'
ON CONFLICT DO NOTHING;

INSERT INTO project_members(project_id, user_id)
SELECT project.id, user_account.id
FROM projects project
CROSS JOIN users user_account
WHERE project.code = 'LOAD_TEST'
  AND (
      user_account.username LIKE 'load_tester_%'
      OR user_account.username LIKE 'load_developer_%'
      OR user_account.username LIKE 'load_admin_%'
  )
ON CONFLICT (project_id, user_id) DO NOTHING;

WITH tester_users AS (
    SELECT
        id,
        row_number() OVER (ORDER BY username) AS tester_number
    FROM users
    WHERE username ~ '^load_tester_[0-9]{4}$'
    ORDER BY username
    LIMIT 1000
),
developer_users AS (
    SELECT
        id,
        row_number() OVER (ORDER BY username) AS developer_number
    FROM users
    WHERE username ~ '^load_developer_[0-9]{4}$'
    ORDER BY username
    LIMIT 1000
),
load_version AS (
    SELECT id
    FROM product_versions
    WHERE version_no = 'LOAD-TEST-1.0'
),
load_project AS (
    SELECT id
    FROM projects
    WHERE code = 'LOAD_TEST'
),
generated_tickets AS (
    SELECT
        tester.id AS creator_id,
        developer.id AS assignee_id,
        tester.tester_number,
        ticket_number,
        version.id AS version_id,
        project.id AS project_id,
        ticket_number <= 900 AS is_closed
    FROM tester_users tester
    CROSS JOIN generate_series(1, 1000) AS ticket_number
    JOIN developer_users developer
      ON developer.developer_number =
         ((tester.tester_number + ticket_number - 2) % 1000) + 1
    CROSS JOIN load_version version
    CROSS JOIN load_project project
)
INSERT INTO tickets(
    ticket_no,
    title,
    description,
    category,
    priority,
    status,
    project_id,
    creator_id,
    assignee_id,
    affected_version_id,
    resolved_version_id,
    resolution,
    version,
    created_at,
    updated_at,
    resolved_at,
    verified_at,
    closed_at
)
SELECT
    'LT-' || lpad(tester_number::text, 4, '0')
        || '-' || lpad(ticket_number::text, 4, '0'),
    '压测问题单 ' || lpad(tester_number::text, 4, '0')
        || '-' || lpad(ticket_number::text, 4, '0'),
    '这是批量生成的压测问题单，用于验证百万级问题单列表、权限和分页性能。',
    CASE ticket_number % 5
        WHEN 0 THEN '性能问题'
        WHEN 1 THEN '功能异常'
        WHEN 2 THEN '数据问题'
        WHEN 3 THEN '安全问题'
        ELSE '其他'
    END,
    CASE ticket_number % 4
        WHEN 0 THEN 'LOW'
        WHEN 1 THEN 'MEDIUM'
        WHEN 2 THEN 'HIGH'
        ELSE 'CRITICAL'
    END,
    CASE WHEN is_closed THEN 'CLOSED' ELSE 'IN_PROGRESS' END,
    project_id,
    creator_id,
    assignee_id,
    version_id,
    CASE WHEN is_closed THEN version_id ELSE NULL END,
    CASE WHEN is_closed THEN '压测数据：问题已解决并完成验证。' ELSE NULL END,
    0,
    CURRENT_TIMESTAMP - ((1000 - ticket_number) || ' minutes')::interval,
    CURRENT_TIMESTAMP,
    CASE WHEN is_closed THEN CURRENT_TIMESTAMP - interval '2 days' ELSE NULL END,
    CASE WHEN is_closed THEN CURRENT_TIMESTAMP - interval '1 day' ELSE NULL END,
    CASE WHEN is_closed THEN CURRENT_TIMESTAMP ELSE NULL END
FROM generated_tickets
ON CONFLICT (ticket_no) DO UPDATE SET
    title = EXCLUDED.title,
    description = EXCLUDED.description,
    category = EXCLUDED.category,
    priority = EXCLUDED.priority,
    status = EXCLUDED.status,
    project_id = EXCLUDED.project_id,
    creator_id = EXCLUDED.creator_id,
    assignee_id = EXCLUDED.assignee_id,
    affected_version_id = EXCLUDED.affected_version_id,
    resolved_version_id = EXCLUDED.resolved_version_id,
    resolution = EXCLUDED.resolution,
    updated_at = CURRENT_TIMESTAMP,
    resolved_at = EXCLUDED.resolved_at,
    verified_at = EXCLUDED.verified_at,
    closed_at = EXCLUDED.closed_at;

INSERT INTO ticket_transitions(
    ticket_id,
    operator_id,
    from_status,
    to_status,
    action,
    comment,
    created_at
)
SELECT
    ticket.id,
    ticket.assignee_id,
    CASE WHEN ticket.status = 'CLOSED' THEN 'VERIFIED' ELSE 'ASSIGNED' END,
    ticket.status,
    CASE WHEN ticket.status = 'CLOSED' THEN 'CLOSE' ELSE 'START' END,
    '压测数据初始化',
    ticket.updated_at
FROM tickets ticket
WHERE ticket.ticket_no ~ '^LT-[0-9]{4}-[0-9]{4}$'
  AND NOT EXISTS (
      SELECT 1
      FROM ticket_transitions transition
      WHERE transition.ticket_id = ticket.id
        AND transition.comment = '压测数据初始化'
  );

COMMIT;

ANALYZE users;
ANALYZE tickets;
ANALYZE ticket_transitions;

SELECT role_code, count(*) AS user_count
FROM (
    SELECT 'TESTER' AS role_code FROM users WHERE username ~ '^load_tester_[0-9]{4}$'
    UNION ALL
    SELECT 'DEVELOPER' FROM users WHERE username ~ '^load_developer_[0-9]{4}$'
    UNION ALL
    SELECT 'ADMIN' FROM users WHERE username ~ '^load_admin_[0-9]{3}$'
) generated_user_counts
GROUP BY role_code
ORDER BY role_code;

SELECT
    count(*) AS total_tickets,
    count(*) FILTER (WHERE status = 'CLOSED') AS closed_tickets,
    count(*) FILTER (WHERE status = 'IN_PROGRESS') AS in_progress_tickets
FROM tickets
WHERE ticket_no ~ '^LT-[0-9]{4}-[0-9]{4}$';
