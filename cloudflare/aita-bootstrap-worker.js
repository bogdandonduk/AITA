const DEFAULT_SERVER_URL = "https://aita-api.bogdan-dond.uk.workers.dev";
const BOOTSTRAP_PATHS = new Set([
  "/.well-known/aita-server.json",
  "/config/server",
]);
const SAFE_METHODS = new Set(["GET", "HEAD", "OPTIONS"]);

function commonHeaders(extra = {}) {
  return {
    "Access-Control-Allow-Headers":
      "Accept, Content-Type, X-AITA-Installation-Id, X-AITA-Device-Name, " +
      "X-AITA-Device-Platform, X-AITA-Device-Os, X-AITA-App-Name, " +
      "X-AITA-App-Version, X-AITA-Device-Locale",
    "Access-Control-Allow-Methods": "GET, HEAD, OPTIONS",
    "Access-Control-Allow-Origin": "*",
    "Cache-Control": "no-store, max-age=0",
    "Content-Security-Policy": "default-src 'none'; frame-ancestors 'none'",
    Pragma: "no-cache",
    "Referrer-Policy": "no-referrer",
    "X-AITA-Bootstrap": "AITA",
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

function bootstrapPayload(env) {
  const serverUrl = DEFAULT_SERVER_URL;
  const serverCandidates = [
    {
      url: serverUrl,
      priority: 100,
      supportsRealtime: true,
      role: "canonical-workers-vpc",
    },
  ];

  return {
    schemaVersion: 2,
    sequence: Number(env.AITA_BOOTSTRAP_SEQUENCE || 1),
    serverUrl,
    serverCandidates,
    globalConfigPath: "config/global",
    globalConfigUrl: `${serverUrl}/config/global`,
    environment: "bootstrap-worker",
    version: String(env.AITA_RELEASE_VERSION || ""),
    updatedAtMillis: Date.now(),
  };
}

function htmlResponse(env, method) {
  const payload = bootstrapPayload(env);
  const serverUrl = payload.serverUrl;
  const html = `<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width,initial-scale=1">
  <meta name="robots" content="noindex,nofollow,noarchive">
  <title>AITA Bootstrap</title>
  <style>
    :root { color-scheme: light dark; font-family: Inter, ui-sans-serif, system-ui, sans-serif; }
    body { min-height: 100vh; margin: 0; display: grid; place-items: center; padding: 24px; background: #f7f7f4; color: #32363e; }
    main { width: min(680px,100%); padding: clamp(28px,6vw,52px); border-radius: 30px; background: rgba(255,255,255,.9); border: 1px solid rgba(50,54,62,.1); box-shadow: 0 28px 80px rgba(38,42,49,.12); }
    .mark { width: 54px; height: 54px; display: grid; place-items: center; border-radius: 18px; background: #fff7e3; font-weight: 850; letter-spacing: -.08em; }
    h1 { margin: 28px 0 14px; font-size: clamp(34px,7vw,54px); line-height: 1; letter-spacing: -.055em; }
    p { margin: 0 0 24px; line-height: 1.65; opacity: .68; }
    a { display: inline-block; padding: 12px 15px; border-radius: 14px; background: #ffba24; color: #2d3036; text-decoration: none; font-weight: 750; }
    code { display: block; margin-top: 24px; overflow-wrap: anywhere; opacity: .55; }
    @media (prefers-color-scheme: dark) { body { background: #15171b; color: #f2f3f5; } main { background: #1c1f24; border-color: rgba(255,255,255,.08); } }
  </style>
</head>
<body>
  <main>
    <div class="mark">aita</div>
    <h1>AITA bootstrap is online</h1>
    <p>This compatibility resolver directs older AITA builds to the single domain-independent production gateway.</p>
    <a href="/.well-known/aita-server.json">Open bootstrap JSON</a>
    <code>${serverUrl}</code>
  </main>
</body>
</html>`;
  return new Response(method === "HEAD" ? null : html, {
    status: 200,
    headers: commonHeaders({
      "Content-Type": "text/html; charset=UTF-8",
      "Content-Security-Policy":
        "default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; " +
        "form-action 'none'; frame-ancestors 'none'",
    }),
  });
}

export default {
  async fetch(request, env) {
    const method = request.method.toUpperCase();
    if (!SAFE_METHODS.has(method)) {
      return jsonResponse(
        { error: "method_not_allowed", allowedMethods: [...SAFE_METHODS] },
        405,
        method !== "HEAD",
      );
    }

    if (method === "OPTIONS") {
      return new Response(null, { status: 204, headers: commonHeaders() });
    }

    const requestUrl = new URL(request.url);
    if (requestUrl.pathname === "/") {
      return htmlResponse(env, method);
    }

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
      return jsonResponse(bootstrapPayload(env), 200, method !== "HEAD");
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
