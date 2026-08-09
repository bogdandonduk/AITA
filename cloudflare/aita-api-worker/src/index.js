const BOOTSTRAP_PATHS = new Set([
  "/.well-known/aita-server.json",
  "/config/server",
]);
const EDGE_HEALTH_PATH = "/_edge/health";
const ROOT_PATH = "/";
const FAVICON_PATHS = new Set(["/favicon.svg", "/favicon.ico"]);
const ROBOTS_PATH = "/robots.txt";
const BODYLESS_METHODS = new Set(["GET", "HEAD"]);
const BOOTSTRAP_METHODS = new Set(["GET", "HEAD", "OPTIONS"]);
const GATEWAY_VERSION = "2026-08-01";

function normalizedPublicOrigin(request) {
  const url = new URL(request.url);
  return `${url.protocol}//${url.host}`;
}

function commonSecurityHeaders(extra = {}) {
  return {
    "cache-control": "no-store, max-age=0",
    pragma: "no-cache",
    "referrer-policy": "no-referrer",
    "x-content-type-options": "nosniff",
    "x-frame-options": "DENY",
    "permissions-policy": "camera=(), microphone=(), geolocation=(), payment=(), usb=()",
    ...extra,
  };
}

function bootstrapCorsHeaders(extra = {}) {
  return commonSecurityHeaders({
    "access-control-allow-origin": "*",
    "access-control-allow-methods": "GET, HEAD, OPTIONS",
    "access-control-allow-headers":
      "Accept, Content-Type, X-AITA-Installation-Id, X-AITA-Device-Name, " +
      "X-AITA-Device-Platform, X-AITA-Device-Os, X-AITA-App-Name, " +
      "X-AITA-App-Version, X-AITA-Device-Locale",
    "access-control-max-age": "86400",
    ...extra,
  });
}

function jsonResponse(payload, status = 200, includeBody = true, headers = {}) {
  return new Response(includeBody ? `${JSON.stringify(payload)}\n` : null, {
    status,
    headers: commonSecurityHeaders({
      "content-type": "application/json; charset=UTF-8",
      ...headers,
    }),
  });
}

function bootstrapJsonResponse(payload, status = 200, includeBody = true) {
  return new Response(includeBody ? `${JSON.stringify(payload)}\n` : null, {
    status,
    headers: bootstrapCorsHeaders({
      "content-type": "application/json; charset=UTF-8",
      "x-aita-bootstrap": "AITA",
    }),
  });
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

function statusLabel(result) {
  if (!result) return { label: "Unknown", css: "unknown" };
  if (result.ok) return { label: "Healthy", css: "healthy" };
  if (result.status > 0) return { label: `HTTP ${result.status}`, css: "unhealthy" };
  return { label: "Unavailable", css: "unhealthy" };
}

async function probeOrigin(env, path) {
  const startedAt = Date.now();
  try {
    const response = await env.AITA_ORIGIN.fetch(
      new Request(`http://localhost${path}`, {
        method: "GET",
        headers: {
          accept: "application/json",
          "cache-control": "no-cache",
          "x-aita-connection-probe": "1",
          "x-forwarded-proto": "https",
        },
      }),
    );
    return {
      ok: response.ok,
      status: response.status,
      elapsedMillis: Date.now() - startedAt,
    };
  } catch (error) {
    return {
      ok: false,
      status: 0,
      elapsedMillis: Date.now() - startedAt,
      error: error instanceof Error ? error.message : String(error),
    };
  }
}

function landingHtml({ publicOrigin, health, readiness }) {
  const healthLabel = statusLabel(health);
  const readinessLabel = statusLabel(readiness);
  const overallHealthy = health?.ok && readiness?.ok;
  const overallLabel = overallHealthy ? "AITA is online" : "AITA gateway is online";
  const overallText = overallHealthy
    ? "The private production server is reachable through Cloudflare Workers VPC."
    : "The public gateway is running, but the private origin is not fully ready yet.";

  return `<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width,initial-scale=1">
  <meta name="color-scheme" content="light dark">
  <meta name="robots" content="noindex,nofollow,noarchive">
  <link rel="icon" href="/favicon.svg" type="image/svg+xml">
  <title>AITA Gateway</title>
  <style>
    :root { color-scheme: light dark; font-family: Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; }
    * { box-sizing: border-box; }
    body { margin: 0; min-height: 100vh; display: grid; place-items: center; padding: 24px; background: radial-gradient(circle at top, rgba(255,186,36,.17), transparent 42%), #f7f7f4; color: #292c32; }
    main { width: min(720px, 100%); border: 1px solid rgba(50,54,62,.11); border-radius: 30px; background: rgba(255,255,255,.86); box-shadow: 0 28px 80px rgba(38,42,49,.12); padding: clamp(26px, 5vw, 48px); backdrop-filter: blur(18px); }
    .brand { display: flex; align-items: center; gap: 14px; margin-bottom: 30px; }
    .mark { width: 50px; height: 50px; display: grid; place-items: center; border-radius: 17px; background: #fff7e3; color: #32363e; font-weight: 850; letter-spacing: -.08em; box-shadow: inset 0 0 0 1px rgba(255,186,36,.22); }
    .brand strong { display: block; font-size: 20px; letter-spacing: -.03em; }
    .brand span { display: block; opacity: .58; font-size: 13px; margin-top: 2px; }
    h1 { font-size: clamp(32px, 6vw, 50px); letter-spacing: -.055em; line-height: 1.02; margin: 0 0 14px; }
    .lead { margin: 0 0 30px; color: rgba(41,44,50,.7); line-height: 1.65; max-width: 58ch; }
    .grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; margin-bottom: 26px; }
    .card { padding: 18px; border-radius: 20px; background: rgba(50,54,62,.045); border: 1px solid rgba(50,54,62,.075); }
    .card small { display: block; opacity: .56; margin-bottom: 8px; }
    .status { display: inline-flex; align-items: center; gap: 8px; font-weight: 720; }
    .status::before { content: ""; width: 9px; height: 9px; border-radius: 50%; background: currentColor; box-shadow: 0 0 0 5px color-mix(in srgb, currentColor 13%, transparent); }
    .healthy { color: #17824f; }
    .unhealthy { color: #bd4b43; }
    .unknown { color: #8a6f2d; }
    .meta { opacity: .54; font-size: 12px; margin-top: 8px; }
    nav { display: flex; flex-wrap: wrap; gap: 10px; }
    a { color: #32363e; text-decoration: none; padding: 11px 14px; border-radius: 14px; background: #ffba24; font-weight: 720; font-size: 13px; }
    a.secondary { background: rgba(50,54,62,.07); }
    footer { margin-top: 28px; opacity: .46; font-size: 12px; overflow-wrap: anywhere; }
    @media (max-width: 560px) { .grid { grid-template-columns: 1fr; } main { border-radius: 24px; } }
    @media (prefers-color-scheme: dark) {
      body { background: radial-gradient(circle at top, rgba(255,186,36,.13), transparent 42%), #15171b; color: #f2f3f5; }
      main { background: rgba(28,31,36,.9); border-color: rgba(255,255,255,.09); box-shadow: 0 28px 80px rgba(0,0,0,.34); }
      .lead { color: rgba(242,243,245,.67); }
      .card { background: rgba(255,255,255,.045); border-color: rgba(255,255,255,.07); }
      a { color: #2c2f35; }
      a.secondary { color: #f2f3f5; background: rgba(255,255,255,.08); }
    }
  </style>
</head>
<body>
  <main>
    <div class="brand"><div class="mark">aita</div><div><strong>AITA</strong><span>Domain-independent production gateway</span></div></div>
    <h1>${escapeHtml(overallLabel)}</h1>
    <p class="lead">${escapeHtml(overallText)} This page is a human-readable status surface; AITA clients use the same address as their API and bootstrap endpoint.</p>
    <section class="grid">
      <article class="card"><small>Origin health</small><span class="status ${healthLabel.css}">${escapeHtml(healthLabel.label)}</span><div class="meta">${health?.elapsedMillis ?? 0} ms</div></article>
      <article class="card"><small>Origin readiness</small><span class="status ${readinessLabel.css}">${escapeHtml(readinessLabel.label)}</span><div class="meta">${readiness?.elapsedMillis ?? 0} ms</div></article>
    </section>
    <nav>
      <a href="/.well-known/aita-server.json">Bootstrap JSON</a>
      <a class="secondary" href="/healthz">Health JSON</a>
      <a class="secondary" href="/readyz">Readiness JSON</a>
    </nav>
    <footer>${escapeHtml(publicOrigin)} · gateway ${GATEWAY_VERSION}</footer>
  </main>
</body>
</html>`;
}

function faviconResponse(includeBody = true) {
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64"><rect width="64" height="64" rx="18" fill="#fffaf0"/><path d="M15 40c0-11 8-21 20-21 7 0 12 3 15 7l-7 5c-2-3-5-4-8-4-7 0-11 5-11 13h-9Z" fill="#32363e"/><circle cx="47" cy="18" r="5" fill="#ffba24"/><circle cx="54" cy="31" r="4" fill="#ffba24"/><circle cx="44" cy="42" r="4" fill="#ffba24"/></svg>`;
  return new Response(includeBody ? svg : null, {
    status: 200,
    headers: commonSecurityHeaders({
      "content-type": "image/svg+xml; charset=UTF-8",
      "cache-control": "public, max-age=86400",
      "content-security-policy": "default-src 'none'; style-src 'none'; sandbox",
    }),
  });
}

function bootstrapPayload(request, env) {
  const publicOrigin = normalizedPublicOrigin(request);
  const legacyServerUrl = String(env.AITA_LEGACY_SERVER_URL || "https://api.aita.kz")
    .trim()
    .replace(/\/+$/, "");
  const candidates = [
    {
      url: publicOrigin,
      priority: 100,
      supportsRealtime: true,
      role: "primary-domainless",
    },
  ];
  if (legacyServerUrl && legacyServerUrl !== publicOrigin) {
    candidates.push({
      url: legacyServerUrl,
      priority: 40,
      supportsRealtime: true,
      role: "legacy-domain",
    });
  }
  return {
    schemaVersion: 2,
    sequence: Number(env.AITA_BOOTSTRAP_SEQUENCE || 1),
    serverUrl: publicOrigin,
    serverCandidates: candidates,
    globalConfigPath: "config/global",
    globalConfigUrl: `${publicOrigin}/config/global`,
    environment: "workers-vpc",
    version: String(env.AITA_RELEASE_VERSION || GATEWAY_VERSION),
    updatedAtMillis: Date.now(),
  };
}

function trustedForwardingHeaders(request, incomingUrl) {
  const headers = new Headers(request.headers);
  const clientIp = request.headers.get("cf-connecting-ip");
  headers.delete("x-forwarded-for");
  headers.delete("x-real-ip");
  headers.delete("forwarded");
  if (clientIp) {
    headers.set("x-forwarded-for", clientIp);
    headers.set("x-real-ip", clientIp);
  }
  headers.set("x-forwarded-proto", "https");
  headers.set("x-forwarded-host", incomingUrl.host);
  headers.set("x-forwarded-port", "443");
  headers.set("x-aita-public-origin", incomingUrl.origin);
  headers.set("x-aita-edge", "cloudflare-workers-vpc");
  return headers;
}

async function proxyToOrigin(request, env) {
  const incomingUrl = new URL(request.url);
  const targetUrl = new URL(`${incomingUrl.pathname}${incomingUrl.search}`, "http://localhost");
  const init = {
    method: request.method,
    headers: trustedForwardingHeaders(request, incomingUrl),
    redirect: "manual",
  };
  if (!BODYLESS_METHODS.has(request.method.toUpperCase())) {
    init.body = request.body;
    // Required by standards-compliant runtimes when forwarding a streaming request body.
    // Cloudflare Workers accepts the half-duplex request-stream contract as well.
    init.duplex = "half";
  }
  const upstreamRequest = new Request(targetUrl, init);
  return env.AITA_ORIGIN.fetch(upstreamRequest);
}

export default {
  async fetch(request, env) {
    const method = request.method.toUpperCase();
    const requestUrl = new URL(request.url);

    if (requestUrl.pathname === EDGE_HEALTH_PATH) {
      return jsonResponse(
        {
          status: "ok",
          gateway: "aita-workers-vpc",
          version: GATEWAY_VERSION,
          publicOrigin: requestUrl.origin,
        },
        200,
        method !== "HEAD",
        { "x-aita-gateway": "AITA" },
      );
    }

    if (BOOTSTRAP_PATHS.has(requestUrl.pathname)) {
      if (!BOOTSTRAP_METHODS.has(method)) {
        return bootstrapJsonResponse(
          { error: "method_not_allowed", allowedMethods: [...BOOTSTRAP_METHODS] },
          405,
          method !== "HEAD",
        );
      }
      if (method === "OPTIONS") {
        return new Response(null, { status: 204, headers: bootstrapCorsHeaders() });
      }
      return bootstrapJsonResponse(bootstrapPayload(request, env), 200, method !== "HEAD");
    }

    if (FAVICON_PATHS.has(requestUrl.pathname) && (method === "GET" || method === "HEAD")) {
      return faviconResponse(method !== "HEAD");
    }

    if (requestUrl.pathname === ROBOTS_PATH && (method === "GET" || method === "HEAD")) {
      return new Response(method === "HEAD" ? null : "User-agent: *\nDisallow: /\n", {
        status: 200,
        headers: commonSecurityHeaders({ "content-type": "text/plain; charset=UTF-8" }),
      });
    }

    if (requestUrl.pathname === ROOT_PATH && (method === "GET" || method === "HEAD")) {
      const acceptsHtml = (request.headers.get("accept") || "").includes("text/html");
      if (!acceptsHtml) {
        return jsonResponse(
          {
            service: "AITA domain-independent API gateway",
            status: "ok",
            bootstrapUrl: `${requestUrl.origin}/.well-known/aita-server.json`,
            healthUrl: `${requestUrl.origin}/healthz`,
            readinessUrl: `${requestUrl.origin}/readyz`,
          },
          200,
          method !== "HEAD",
          { "x-aita-gateway": "AITA" },
        );
      }
      const [health, readiness] = await Promise.all([
        probeOrigin(env, "/healthz"),
        probeOrigin(env, "/readyz"),
      ]);
      return new Response(
        method === "HEAD" ? null : landingHtml({ publicOrigin: requestUrl.origin, health, readiness }),
        {
          status: 200,
          headers: commonSecurityHeaders({
            "content-type": "text/html; charset=UTF-8",
            "content-security-policy":
              "default-src 'none'; style-src 'unsafe-inline'; img-src 'self' data:; " +
              "base-uri 'none'; form-action 'none'; frame-ancestors 'none'",
            "x-aita-gateway": "AITA",
          }),
        },
      );
    }

    try {
      return await proxyToOrigin(request, env);
    } catch (error) {
      console.error("AITA private origin unavailable", {
        method,
        path: requestUrl.pathname,
        message: error instanceof Error ? error.message : String(error),
      });
      return jsonResponse(
        {
          error: "origin_unavailable",
          message: "The private AITA origin is temporarily unavailable.",
          retryable: true,
        },
        503,
        method !== "HEAD",
        {
          "retry-after": "5",
          "x-aita-gateway": "AITA",
        },
      );
    }
  },
};
