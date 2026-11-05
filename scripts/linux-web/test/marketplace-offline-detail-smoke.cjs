// Public product facts are cached per account; no real server or user changes.
process.env.AITA_MARKET_DETAIL_OFFLINE='1';
require('./marketplace-smoke.cjs');
