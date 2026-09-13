package kz.aita.server.marketplace

/** One physical-location gate for shop details, the directory, and all public offer queries.
 * Bind the same server instant twice: access start and timed/paid end. Never inherit a parent's
 * subscription or expose a branch whose parent was disabled.
 */
internal object MarketplacePublicVisibility {
    val shopJoins = """
        FROM marketplace_storefronts f JOIN stores s ON s.id=f.store_id
        LEFT JOIN stores p ON p.id=s.parent_store_id
        JOIN store_subscription_states e ON e.store_id=f.store_id
    """.trimIndent()
    val shopPredicate = """
        f.is_published AND s.is_active AND (s.parent_store_id IS NULL OR p.is_active)
        AND e.status='active' AND coalesce(e.current_period_start_millis,e.started_at_millis)<=?
        AND ((e.access_kind='lifetime' AND e.plan_id='internal_lifetime' AND e.current_period_end_millis IS NULL AND NOT e.auto_renew)
          OR (e.access_kind IN ('paid','timed') AND e.current_period_end_millis>?))
    """.trimIndent()
}
