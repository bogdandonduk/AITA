-- Reset legacy refresh sessions after token rotation/session hardening.
-- Users will sign in once after this migration; new sessions use longer sliding refresh TTLs
-- and rotation grace handled in the server application code.
DELETE FROM refresh_sessions;
