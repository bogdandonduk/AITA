package kz.aita.server.marketplace

/** One internet-branch gate for every public marketplace read. Bind the same server instant
 * twice; neither the management parent nor a sibling can lend subscription access to this branch.
 * f.store_id is a stable public shop identity, not an inventory or subscription owner.
 */
internal object MarketplacePublicVisibility {
    // Earlier child branches could share their original operating parent's product identity.
    // Keep those reviewed listings usable only while that original catalogue remains in this family.
    val cataloguePredicate = """
        (i.store_id IN (s.id,coalesce(s.parent_store_id,s.id)) OR EXISTS (
            SELECT 1 FROM store_management_parent_migrations legacy_map
            JOIN stores legacy_store ON legacy_store.id=legacy_map.original_store_id
            WHERE legacy_map.original_store_id=i.store_id AND legacy_map.management_store_id=s.parent_store_id
                AND legacy_store.parent_store_id=s.parent_store_id))
    """.trimIndent()
    val shopJoins = """
        FROM marketplace_storefronts f JOIN stores s ON s.id=f.branch_store_id
        LEFT JOIN stores p ON p.id=s.parent_store_id
        JOIN store_subscription_states e ON e.store_id=s.id
    """.trimIndent()
    val shopPredicate = """
        f.is_published AND s.is_active AND s.parent_store_id IS NOT NULL AND s.branch_type='INTERNET'
        AND p.is_active AND p.parent_store_id IS NULL
        AND e.status='active' AND coalesce(e.current_period_start_millis,e.started_at_millis)<=?
        AND ((e.access_kind='lifetime' AND e.plan_id='internal_lifetime' AND e.current_period_end_millis IS NULL AND NOT e.auto_renew)
          OR (e.access_kind IN ('paid','timed') AND e.current_period_end_millis>?))
    """.trimIndent()
}
