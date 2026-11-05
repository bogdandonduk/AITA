package kz.aita.server

import io.ktor.server.plugins.cors.CORSConfig

/** Workspace scope headers are authenticated by each endpoint; CORS only admits their preflight. */
internal fun CORSConfig.allowSupplierWorkspaceHeaders() {
    allowHeader("supplier_id")
    allowHeader("supplier_side")
    allowHeader("mark_seen")
}
