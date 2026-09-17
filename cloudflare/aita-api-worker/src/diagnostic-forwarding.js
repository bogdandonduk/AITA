// Client-supplied diagnostic location headers never cross the gateway/origin trust boundary.
export function setTrustedDiagnosticHeaders(headers, request, env, incomingUrl) {
  for (const name of [...headers.keys()]) {
    if (name.toLowerCase().startsWith("x-aita-diagnostic")) headers.delete(name);
  }
  if (request.method.toUpperCase() !== "POST" || !/^\/diagnostics\/events(?:\/anonymous)?\/?$/.test(incomingUrl.pathname)) return;
  const key = typeof env.AITA_DIAGNOSTICS_GATEWAY_KEY === "string" ? env.AITA_DIAGNOSTICS_GATEWAY_KEY : "";
  if (key.length < 32 || key.length > 512 || !/^[\x21-\x7e]+$/.test(key) || !request.cf) return;
  const country = request.cf.country;
  if (typeof country !== "string" || !/^[A-Z]{2}$/.test(country) || country === "XX" || country === "T1") return;
  headers.set("x-aita-diagnostics-gateway-key", key);
  headers.set("x-aita-diagnostic-country", country);
  const region = request.cf.regionCode;
  if (typeof region === "string" && /^[A-Za-z0-9 -]{1,32}$/.test(region)) headers.set("x-aita-diagnostic-region", region);
}
