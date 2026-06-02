UPDATE store_subscription_states
SET plan_id = 'standard_monthly_kzt',
    updated_at_millis = EXTRACT(EPOCH FROM NOW())::bigint * 1000,
    updated_at = NOW()
WHERE plan_id IN ('starter_monthly_kzt', 'business_monthly_kzt', 'business_yearly_kzt', 'aita_store_monthly_kzt')
   OR plan_id IS NULL
   OR trim(plan_id) = '';

DELETE FROM stock_batches
WHERE is_active = FALSE
   OR lower(status) = 'deleted';

DELETE FROM stock_items
WHERE is_active = FALSE;
