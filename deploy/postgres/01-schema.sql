-- Schema bootstrap for the Postgres test container.
-- This file is mounted into /docker-entrypoint-initdb.d/ and psql will source it
-- after creating the database (POSTGRES_DB=rm) on first start.
--
-- The canonical migration lives at migrations/V202605201441__annual_performance_summary.sql.
-- This file uses psql's \i to apply that migration verbatim — keeping a single
-- source of truth and avoiding drift between integration tests and the test cluster.

\i /migrations/annual-performance-summary.sql
