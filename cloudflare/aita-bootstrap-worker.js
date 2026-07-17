const DEFAULT_SERVER_URL = "https://api.aita.kz";
const BOOTSTRAP_PATHS = new Set([
  "/.well-known/aita-server.json",
  "/config/server",
]);

function normalizedServerUrl(rawValue) {
  const candidate = String(rawValue || DEFAULT_SERVER_URL).trim();

  try {
    const url = new URL(candidate);
    if (url.protocol !== "https:" && url.protocol !== "http:") {
      throw new Error("AITA_CURRENT_SERVER_URL must use http or https");
    }
    if (url.username || url.password || url.search || url.hash) {
      throw new Error("AITA_CURRENT_SERVER_URL must not contain credentials, query, or fragment");
    }
    url.pathname = url.pathname.replace(/\/+$/, "") || "/";
    return url.toString().replace(/\/$/, "");
  } catch (error) {
    throw new Error(`Invalid AITA_CURRENT_SERVER_URL: ${error.message}`);
  }
}

function commonHeaders(extra = {}) {
  return {
    "Access-Control-Allow-Headers": "Accept, Content-Type, X-AITA-Installation-Id, X-AITA-Device-Name, X-AITA-Device-Platform, X-AITA-Device-Os, X-AITA-App-Name, X-AITA-App-Version, X-AITA-Device-Locale",
    "Access-Control-Allow-Methods": "GET, HEAD, OPTIONS",
    "Access-Control-Allow-Origin": "*",
    "Cache-Control": "no-store, max-age=0",
    "Content-Security-Policy": "default-src 'none'; frame-ancestors 'none'",
    Pragma: "no-cache",
    "Referrer-Policy": "no-referrer",
    "X-Content-Type-Options": "nosniff",
    ...extra,
  };
}

function jsonResponse(payload, status = 200, includeBody = true) {
  return new Response(includeBody ? `${JSON.stringify(payload)}\n` : null, {
    status,
    headers: commonHeaders({ "Content-Type": "application/json; charset=UTF-8" }),
  });
}

export default {
  async fetch(request, env) {
    const method = request.method.toUpperCase();
    if (method === "OPTIONS") {
      return new Response(null, { status: 204, headers: commonHeaders() });
    }

    if (method !== "GET" && method !== "HEAD") {
      return jsonResponse(
        { error: "method_not_allowed", allowedMethods: ["GET", "HEAD", "OPTIONS"] },
        405,
        method !== "HEAD",
      );
    }

    const requestUrl = new URL(request.url);
    if (requestUrl.pathname === "/healthz" || requestUrl.pathname === "/readyz") {
      return jsonResponse(
        { status: "ok", service: "aita-bootstrap" },
        200,
        method !== "HEAD",
      );
    }

    if (!BOOTSTRAP_PATHS.has(requestUrl.pathname)) {
      return jsonResponse(
        {
          error: "not_found",
          bootstrapPath: "/.well-known/aita-server.json",
        },
        404,
        method !== "HEAD",
      );
    }

    try {
      const serverUrl = normalizedServerUrl(env.AITA_CURRENT_SERVER_URL);
      return jsonResponse(
        {
          serverUrl,
          globalConfigPath: "config/global",
          globalConfigUrl: `${serverUrl}/config/global`,
          environment: "bootstrap",
          version: String(env.AITA_RELEASE_VERSION || ""),
          updatedAtMillis: Date.now(),
        },
        200,
        method !== "HEAD",
      );
    } catch (error) {
      console.error("AITA bootstrap configuration error", error);
      return jsonResponse(
        { error: "bootstrap_configuration_error" },
        503,
        method !== "HEAD",
      );
    }
  },
};
