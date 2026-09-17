// Serve SQL.js locally, including offline-capable persistence; no third-party runtime CDN.
const databaseFs = require('fs');
const databaseWebpack = require('webpack');
config.plugins.push({
    apply(compiler) {
        compiler.hooks.thisCompilation.tap('AitaBrowserDatabase', compilation => {
            compilation.hooks.processAssets.tap({
                name: 'AitaBrowserDatabase',
                stage: databaseWebpack.Compilation.PROCESS_ASSETS_STAGE_ADDITIONAL
            }, () => {
                for (const file of ['sql-wasm.js', 'sql-wasm.wasm']) {
                    compilation.emitAsset(file, new databaseWebpack.sources.RawSource(
                        databaseFs.readFileSync(require.resolve('sql.js/dist/' + file))));
                }
            });
        });
    }
});
