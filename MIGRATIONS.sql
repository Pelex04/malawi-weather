-- =============================================================================
--  Malawi Weather API — Production Schema Migrations
--  Apply these in order via Flyway or Liquibase.
-- =============================================================================


-- -----------------------------------------------------------------------------
--  V1__add_weather_cache_unique_constraint.sql
--
--  FIX B5: Prevent duplicate weather_cache rows under concurrent requests.
--  The application now does find-then-update (upsert), but this constraint
--  is the final safety net if two requests slip through simultaneously.
-- -----------------------------------------------------------------------------

ALTER TABLE weather_cache
  ADD CONSTRAINT uq_weather_cache_district_type
  UNIQUE (district_name, cache_type);


-- -----------------------------------------------------------------------------
--  V2__add_usage_logs_indexes.sql
--
--  FIX P2: The daily-limit check:
--    SELECT COUNT(*) FROM usage_logs
--    WHERE api_key_id = ? AND CAST(timestamp AS date) = CURRENT_DATE
--
--  Without an index this is a full sequential scan. As usage_logs grows
--  (no retention = unbounded), this becomes the dominant query cost.
--
--  A partial index covering only today's rows keeps the index tiny and fast.
-- -----------------------------------------------------------------------------

-- Partial index for today's rows only (PostgreSQL syntax)
CREATE INDEX CONCURRENTLY idx_usage_logs_key_today
  ON usage_logs (api_key_id, timestamp)
  WHERE timestamp >= CURRENT_DATE;

-- Full composite index for historical analytics queries (admin stats page)
CREATE INDEX CONCURRENTLY idx_usage_logs_key_timestamp
  ON usage_logs (api_key_id, timestamp DESC);


-- -----------------------------------------------------------------------------
--  V3__remove_developer_is_active_redundancy.sql
--
--  FIX B4: developer.is_active duplicates developer.status.
--  Phase 1: ensure is_active is always consistent with status (backfill).
--  Phase 2: the column can be dropped once the application no longer reads it.
--           Mark it as a future migration — do not drop yet if old code may still
--           be reading it during a rolling deploy.
-- -----------------------------------------------------------------------------

-- Phase 1: backfill any rows where the two fields disagree
UPDATE developers
SET    is_active = CASE WHEN status = 'ACTIVE' THEN true ELSE false END
WHERE  is_active != (status = 'ACTIVE');

-- Phase 2 (run after confirming all application instances are on new code):
-- ALTER TABLE developers DROP COLUMN is_active;


-- -----------------------------------------------------------------------------
--  V4__usage_logs_retention.sql
--
--  FIX S8: usage_logs grows unboundedly. Archive rows older than 90 days.
--  Schedule this via pg_cron or a nightly job outside the application.
--
--  Option A — delete immediately (simple):
-- -----------------------------------------------------------------------------

-- Run as a scheduled job (pg_cron example):
-- SELECT cron.schedule('0 3 * * *', $$
--   DELETE FROM usage_logs WHERE timestamp < NOW() - INTERVAL '90 days';
-- $$);

-- Option B — archive to a separate table before deleting:
-- CREATE TABLE IF NOT EXISTS usage_logs_archive AS TABLE usage_logs WITH NO DATA;
-- INSERT INTO usage_logs_archive SELECT * FROM usage_logs WHERE timestamp < NOW() - INTERVAL '90 days';
-- DELETE FROM usage_logs WHERE timestamp < NOW() - INTERVAL '90 days';


-- -----------------------------------------------------------------------------
--  V5__developer_status_index.sql
--
--  Admin console queries pending developers frequently.
--  Status is low-cardinality but this index makes the filter instant.
-- -----------------------------------------------------------------------------

CREATE INDEX CONCURRENTLY idx_developers_status
  ON developers (status)
  WHERE status = 'PENDING';
