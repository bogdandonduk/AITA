// The same disposable lifecycle, including receipt scan → linked return → destination batch.
process.env.AITA_GUIDED_RETURN='1';
require('./journey.cjs');
