/* SQLDelight worker protocol with durable, acknowledged IndexedDB writes. */
importScripts('/sql-wasm.js');

let database;
let storage;
let inTransaction = false;
let transactionChanged = false;
let committedBytes;

function readSnapshot() {
    return new Promise((resolve, reject) => {
        const request = storage.transaction('sqlite', 'readonly').objectStore('sqlite').get('main');
        request.onsuccess = () => resolve(request.result);
        request.onerror = () => reject(request.error);
    });
}
function writeSnapshot(bytes) {
    return new Promise((resolve, reject) => {
        const transaction = storage.transaction('sqlite', 'readwrite');
        transaction.objectStore('sqlite').put(bytes, 'main');
        transaction.oncomplete = resolve;
        transaction.onerror = () => reject(transaction.error);
        transaction.onabort = () => reject(transaction.error || new Error('Browser storage write aborted'));
    });
}

let SQL;
const ready = new Promise((resolve, reject) => {
    if (!self.navigator.locks) {
        reject(new Error('This browser does not support the storage lock required by AITA'));
        return;
    }
    // The cart book and session are live in memory. A second writer tab must not overwrite
    // the first tab's newer drafts or rotate its authentication token behind its back.
    self.navigator.locks.request('aita.local.database.v1', { ifAvailable: true }, async lock => {
        if (!lock) throw new Error('AITA is already open in another tab. Close that tab and reload.');
        SQL = await initSqlJs({ locateFile: () => '/sql-wasm.wasm' });
        storage = await new Promise((done, fail) => {
            const request = indexedDB.open('aita.local.v1', 1);
            request.onupgradeneeded = () => request.result.createObjectStore('sqlite');
            request.onsuccess = () => done(request.result);
            request.onerror = () => fail(request.error);
            request.onblocked = () => fail(new Error('Browser storage is blocked'));
        });
        committedBytes = await readSnapshot();
        database = committedBytes ? new SQL.Database(committedBytes) : new SQL.Database();
        resolve();
        await new Promise(() => {}); // Worker termination releases the tab's exclusive lock.
    }).catch(reject);
});
// Requests can arrive after a failed startup; retain the rejection without an unhandled event.
ready.catch(() => {});

async function persist() {
    const bytes = database.export();
    try {
        await writeSnapshot(bytes);
        committedBytes = bytes;
    } catch (error) {
        database.close();
        database = committedBytes ? new SQL.Database(committedBytes) : new SQL.Database();
        throw error;
    }
}
async function execute(request) {
    await ready;
    let result = { values: [] };
    switch (request.action) {
        case 'exec': {
            if (!request.sql) throw new Error('Missing SQL query');
            const statement = request.sql.trim().replace(/;\s*$/, '');
            // Startup clears obsolete account cache keys. A missing key changes nothing;
            // exporting and committing the whole inventory database for each such DELETE
            // delays every following read. Count trigger changes too; all real writes
            // still wait for IndexedDB durability before acknowledgement.
            const cacheDelete = /^DELETE\s+FROM\s+key_value\b/i.test(statement) && !statement.includes(';');
            const changesBefore = cacheDelete ? database.exec('SELECT total_changes()')[0].values[0][0] : null;
            result = database.exec(request.sql, request.params)[0] || result;
            // A CTE can precede UPDATE/DELETE/INSERT as well as SELECT. Do not acknowledge
            // one of those writes without storing it just because a nested SELECT exists.
            // Ambiguous statements are deliberately treated as writes.
            const readOnly = /^PRAGMA\s+user_version\s*$/i.test(statement) ||
                (/^(SELECT\b|WITH\b[\s\S]*?\bSELECT\b)/i.test(statement) && !statement.includes(';') &&
                    !/\b(INSERT|UPDATE|DELETE|REPLACE|CREATE|DROP|ALTER|PRAGMA|VACUUM|REINDEX|ATTACH|DETACH|BEGIN|COMMIT|ROLLBACK|SAVEPOINT|RELEASE)\b/i.test(statement));
            const unchangedDelete = cacheDelete && database.exec('SELECT total_changes()')[0].values[0][0] === changesBefore;
            if (!readOnly && !unchangedDelete) {
                if (inTransaction) transactionChanged = true;
                else await persist();
            }
            break;
        }
        case 'begin_transaction':
            database.run('BEGIN TRANSACTION');
            inTransaction = true;
            transactionChanged = false;
            break;
        case 'end_transaction':
            database.run('COMMIT');
            inTransaction = false;
            if (transactionChanged) await persist();
            transactionChanged = false;
            break;
        case 'rollback_transaction':
            database.run('ROLLBACK');
            inTransaction = false;
            transactionChanged = false;
            break;
        default:
            throw new Error('Unsupported database action');
    }
    return result;
}
let queue = Promise.resolve();
self.onmessage = event => {
    const request = event.data;
    queue = queue.then(async () => {
        try {
            const results = await execute(request);
            self.postMessage({ id: request.id, results });
        } catch (error) {
            self.postMessage({ id: request.id, error: { name: error.name, message: error.message } });
        }
    });
};
