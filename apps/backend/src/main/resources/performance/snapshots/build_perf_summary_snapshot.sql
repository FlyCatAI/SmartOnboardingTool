WITH params AS (
    SELECT
        CAST(:biz_date AS date) AS biz_date,
        CAST(:batch_finished_at AS timestamptz) AS batch_finished_at,
        CAST(:source_batch_id AS varchar) AS source_batch_id
),
periods AS (
    SELECT
        'current_year'::varchar AS period_type,
        make_timestamptz(EXTRACT(YEAR FROM biz_date)::int, 1, 1, 0, 0, 0, 'Asia/Shanghai') AS period_start,
        (biz_date::timestamp + time '23:59:59.999999') AT TIME ZONE 'Asia/Shanghai' AS period_end,
        NULL::integer AS history_start_year
    FROM params
    UNION ALL
    SELECT
        'all_time',
        TIMESTAMPTZ '2026-01-01 00:00:00+08',
        (biz_date::timestamp + time '23:59:59.999999') AT TIME ZONE 'Asia/Shanghai',
        2026
    FROM params
),
employees AS (
    SELECT r.employee_id
    FROM rm_relationship_manager_snapshot r
    JOIN params p ON p.biz_date = r.biz_date
    WHERE r.is_active = true
),
new_counts AS (
    SELECT
        e.employee_id,
        p.period_type,
        COUNT(DISTINCT oi.merchant_id) AS new_merchants
    FROM employees e
    CROSS JOIN periods p
    JOIN rm_merchant_ownership_interval oi
        ON oi.employee_id = e.employee_id
       AND oi.start_at <= p.period_end
       AND COALESCE(oi.end_at, 'infinity'::timestamptz) > p.period_start
    WHERE (
        p.period_type = 'current_year'
        AND oi.change_reason = 'onboarded'
        AND oi.start_at BETWEEN p.period_start AND p.period_end
    ) OR (
        p.period_type = 'all_time'
        AND COALESCE(oi.end_at, 'infinity'::timestamptz) > p.period_end
    )
    GROUP BY e.employee_id, p.period_type
),
status_counts AS (
    SELECT
        oi.employee_id,
        p.period_type,
        COUNT(DISTINCT se.merchant_id) FILTER (WHERE se.status = 'qualified') AS qualified_merchants,
        COUNT(DISTINCT se.merchant_id) FILTER (WHERE se.status = 'active') AS active_merchants
    FROM periods p
    JOIN rm_merchant_status_event se
        ON se.occurred_at BETWEEN p.period_start AND p.period_end
    JOIN rm_merchant_ownership_interval oi
        ON oi.merchant_id = se.merchant_id
       AND oi.start_at <= se.occurred_at
       AND COALESCE(oi.end_at, 'infinity'::timestamptz) > se.occurred_at
    GROUP BY oi.employee_id, p.period_type
),
income_counts AS (
    SELECT
        i.employee_id_at_event AS employee_id,
        p.period_type,
        COALESCE(SUM(i.amount), 0)::numeric(18,2) AS income_amount
    FROM periods p
    JOIN rm_income_fact i
        ON i.occurred_at BETWEEN p.period_start AND p.period_end
    GROUP BY i.employee_id_at_event, p.period_type
),
aum AS (
    SELECT a.employee_id, a.aum_total
    FROM rm_aum_snapshot a
    JOIN params p ON p.biz_date = a.biz_date
)
INSERT INTO rm_perf_summary_snapshot (
    biz_date,
    employee_id,
    period_type,
    new_merchants,
    qualified_merchants,
    active_merchants,
    income_amount,
    aum_total,
    history_start_year,
    batch_finished_at,
    is_delayed,
    source_batch_id
)
SELECT
    p.biz_date,
    e.employee_id,
    period.period_type,
    COALESCE(n.new_merchants, 0),
    COALESCE(s.qualified_merchants, 0),
    COALESCE(s.active_merchants, 0),
    COALESCE(i.income_amount, 0),
    a.aum_total,
    period.history_start_year,
    p.batch_finished_at,
    p.biz_date < ((p.batch_finished_at AT TIME ZONE 'Asia/Shanghai')::date - 1),
    p.source_batch_id
FROM params p
CROSS JOIN employees e
CROSS JOIN periods period
LEFT JOIN new_counts n
    ON n.employee_id = e.employee_id AND n.period_type = period.period_type
LEFT JOIN status_counts s
    ON s.employee_id = e.employee_id AND s.period_type = period.period_type
LEFT JOIN income_counts i
    ON i.employee_id = e.employee_id AND i.period_type = period.period_type
LEFT JOIN aum a
    ON a.employee_id = e.employee_id
ON CONFLICT (biz_date, employee_id, period_type)
DO UPDATE SET
    new_merchants = EXCLUDED.new_merchants,
    qualified_merchants = EXCLUDED.qualified_merchants,
    active_merchants = EXCLUDED.active_merchants,
    income_amount = EXCLUDED.income_amount,
    aum_total = EXCLUDED.aum_total,
    history_start_year = EXCLUDED.history_start_year,
    batch_finished_at = EXCLUDED.batch_finished_at,
    is_delayed = EXCLUDED.is_delayed,
    source_batch_id = EXCLUDED.source_batch_id,
    created_at = now();
