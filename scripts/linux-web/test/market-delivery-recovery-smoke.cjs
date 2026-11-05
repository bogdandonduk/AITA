// Isolated visible checkout recovery and inline action-validation regression.
process.env.AITA_DELIVERY_RECOVERY='1';
require('./market-delivery-smoke.cjs');
