-- Repair the empty selections left by clients which sent a clear command after a deleted-store rejection.
-- Preserve every existing selection, and never guess across multiple owned management parents.
WITH candidates AS (
    SELECT u.id AS user_id, min(s.id::text)::uuid AS store_id
    FROM users u JOIN stores s ON s.owner_user_ids @> jsonb_build_array(u.id::text)
    WHERE u.active_store_id IS NULL AND s.parent_store_id IS NULL AND s.is_active = TRUE
    GROUP BY u.id HAVING count(*) = 1
)
UPDATE users u SET active_store_id = c.store_id FROM candidates c
WHERE u.id = c.user_id AND u.active_store_id IS NULL;
