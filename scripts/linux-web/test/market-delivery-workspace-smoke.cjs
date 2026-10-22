// Uses the isolated compiled-app fixture with no real accounts or server writes.
process.env.AITA_DELIVERY_WORKSPACE='1';
require('./market-delivery-smoke.cjs');
