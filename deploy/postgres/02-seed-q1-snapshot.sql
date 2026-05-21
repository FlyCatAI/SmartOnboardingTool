-- Q-1 sample snapshot data for the annual-performance-summary E2E environment.
-- Loaded after deploy/postgres/01-schema.sql on first postgres start.
-- Idempotent: ON CONFLICT DO NOTHING on the unique (biz_date, employee_id, period_type) triple.
--
-- Coverage matrix (matches E2E E1..E16 from TR-GRA-65-20260521-01):
--   RM001 张三  — happy path, non-zero values, AUM present (E5/E10/E16)
--   RM002 李四  — zero-value snapshot, AUM null  (E11/zero-value)
--   RM003 王五  — T+1 delayed snapshot           (E9 data_delay=true)
--   RM004 赵六  — second-day snapshot to verify cache key biz_date (E12)
--
-- biz_date = 2026-05-20 (T-1 from issue due_date 2026-05-21).

INSERT INTO rm_relationship_manager_snapshot
    (biz_date, employee_id, employee_name, is_active, source_batch_id, batch_finished_at)
VALUES
    ('2026-05-20', 'RM001', '张三', true, 'seed-2026-05-20', '2026-05-20 02:30:00+08'),
    ('2026-05-20', 'RM002', '李四', true, 'seed-2026-05-20', '2026-05-20 02:30:00+08'),
    ('2026-05-20', 'RM003', '王五', true, 'seed-2026-05-20', '2026-05-20 02:30:00+08'),
    ('2026-05-20', 'RM004', '赵六', true, 'seed-2026-05-20', '2026-05-20 02:30:00+08')
ON CONFLICT DO NOTHING;

-- Optional AUM snapshots — only present for RMs whose values should not be null.
INSERT INTO rm_aum_snapshot
    (biz_date, employee_id, aum_total, batch_finished_at, source_batch_id)
VALUES
    ('2026-05-20', 'RM001',  8560000.00, '2026-05-20 02:30:00+08', 'seed-2026-05-20'),
    ('2026-05-20', 'RM003', 12345678.90, '2026-05-20 02:30:00+08', 'seed-2026-05-20'),
    ('2026-05-20', 'RM004',  3300000.00, '2026-05-20 02:30:00+08', 'seed-2026-05-20')
ON CONFLICT DO NOTHING;

-- Pre-built T+1 snapshots for both period_type values.
INSERT INTO rm_perf_summary_snapshot
    (biz_date, employee_id, period_type,
     new_merchants, qualified_merchants, active_merchants,
     income_amount, aum_total, history_start_year,
     batch_finished_at, is_delayed, source_batch_id)
VALUES
    -- RM001 happy path: 12 / 8 / 6, 1234567.89 income, 8560000 AUM
    ('2026-05-20', 'RM001', 'current_year',
        12,  8,  6, 1234567.89, 8560000.00, NULL,
        '2026-05-20 02:30:00+08', false, 'seed-2026-05-20'),
    ('2026-05-20', 'RM001', 'all_time',
        135, 92, 70, 9876543.21, 8560000.00, 2026,
        '2026-05-20 02:30:00+08', false, 'seed-2026-05-20'),

    -- RM002 zero-value + null AUM (E11)
    ('2026-05-20', 'RM002', 'current_year',
        0, 0, 0, 0.00, NULL, NULL,
        '2026-05-20 02:30:00+08', false, 'seed-2026-05-20'),
    ('2026-05-20', 'RM002', 'all_time',
        0, 0, 0, 0.00, NULL, 2026,
        '2026-05-20 02:30:00+08', false, 'seed-2026-05-20'),

    -- RM003 delayed snapshot (E9)
    ('2026-05-20', 'RM003', 'current_year',
        7, 5, 4, 580000.50, 12345678.90, NULL,
        '2026-05-20 02:30:00+08', true, 'seed-2026-05-20'),
    ('2026-05-20', 'RM003', 'all_time',
        88, 60, 42, 7654321.00, 12345678.90, 2026,
        '2026-05-20 02:30:00+08', true, 'seed-2026-05-20'),

    -- RM004 has a second-day snapshot to exercise cache key biz_date eviction (E12)
    ('2026-05-19', 'RM004', 'current_year',
        3, 2, 1, 99000.00, 3300000.00, NULL,
        '2026-05-19 02:30:00+08', false, 'seed-2026-05-19'),
    ('2026-05-19', 'RM004', 'all_time',
        50, 30, 20, 4000000.00, 3300000.00, 2026,
        '2026-05-19 02:30:00+08', false, 'seed-2026-05-19'),
    ('2026-05-20', 'RM004', 'current_year',
        4, 3, 2, 120000.00, 3300000.00, NULL,
        '2026-05-20 02:30:00+08', false, 'seed-2026-05-20'),
    ('2026-05-20', 'RM004', 'all_time',
        51, 31, 21, 4120000.00, 3300000.00, 2026,
        '2026-05-20 02:30:00+08', false, 'seed-2026-05-20')
ON CONFLICT DO NOTHING;
