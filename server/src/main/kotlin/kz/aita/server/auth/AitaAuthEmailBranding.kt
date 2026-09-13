package kz.aita.server.auth

import java.util.Base64

internal data class AitaInlineEmailImage(val contentId: String, val filename: String, val base64: String)

/** Raster copy of composeResources/drawable/0_0.svg. No remote image request, tracking pixel,
 * or run-time SVG renderer; immutable attachment bytes become part of the encrypted outbox.
 */
internal object AitaAuthEmailBranding {
    const val LOGO_CID = "aita-logo"
    val images: List<AitaInlineEmailImage> by lazy {
        val bytes = requireNotNull(javaClass.getResourceAsStream("/auth/aita-email-logo.png")) {
            "Bundled AITA email logo is missing"
        }.use { it.readBytes() }
        listOf(AitaInlineEmailImage(LOGO_CID, "aita-logo.png", Base64.getEncoder().encodeToString(bytes)))
    }
}

internal fun aitaAuthEmailLayout(title: String, instruction: String, code: String?, expiry: String, footer: String, language: String): String {
    // All strings originate in the fixed localized copy; escape anyway to keep this helper safe.
    fun safe(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    val codePanel = code?.let { """<tr><td align="center" style="padding:24px 0"><table role="presentation" cellpadding="0" cellspacing="0" width="100%"><tr><td align="center" dir="ltr" style="padding:20px 10px;background-color:#fff1cd;border:1px solid #ffe0a0;border-radius:18px;font-family:Arial,sans-serif;font-size:36px;line-height:46px;font-weight:700;letter-spacing:7px;color:#262830;white-space:nowrap">${safe(it)}</td></tr></table></td></tr>""" }.orEmpty()
    return """<!doctype html>
<html lang="${safe(language)}"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>AITA · ${safe(title)}</title></head>
<body style="margin:0;padding:0;background-color:#f4f5f7;font-family:Arial,Helvetica,sans-serif;color:#262830">
<div style="display:none;max-height:0;overflow:hidden;opacity:0">${safe(title)} · AITA</div>
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0"><tr><td align="center" style="padding:28px 16px">
<table role="presentation" width="480" cellpadding="0" cellspacing="0" border="0" style="width:100%;max-width:480px;table-layout:fixed;background-color:#ffffff;border:1px solid #e8e9ec;border-radius:24px;overflow:hidden">
<tr><td height="5" style="height:5px;background-color:#ffbb21;border-radius:24px 24px 0 0"></td></tr>
<tr><td align="center" style="padding:32px 24px 28px;text-align:center">
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0">
<tr><td align="center" style="padding:0 0 28px"><img src="cid:${AitaAuthEmailBranding.LOGO_CID}" alt="AITA" width="144" height="72" style="display:block;width:144px;height:72px;border:0;border-radius:12px;background-color:#ffffff"></td></tr>
<tr><td align="center" style="overflow-wrap:anywhere;word-wrap:break-word;word-break:break-word;font-size:25px;line-height:32px;font-weight:700;color:#262830">${safe(title)}</td></tr>
<tr><td align="center" style="padding-top:14px;font-size:16px;line-height:25px;color:#626772">${safe(instruction)}</td></tr>
$codePanel
<tr><td align="center" style="padding-top:8px;font-size:13px;line-height:21px;color:#626772">${safe(expiry)}</td></tr>
<tr><td style="padding-top:24px"><div style="height:1px;background-color:#eeeff2"></div></td></tr>
<tr><td align="center" style="padding-top:20px;font-size:13px;line-height:21px;color:#777c85">${safe(footer)}</td></tr>
</table></td></tr></table></td></tr>
<tr><td align="center" style="padding:0 16px 28px;font-size:12px;line-height:18px;color:#777c85">AITA · ${safe(if (language == "ru") "Безопасность аккаунта" else if (language == "kk") "Аккаунт қауіпсіздігі" else if (language == "tg") "Амнияти ҳисоб" else if (language == "ky") "Аккаунттун коопсуздугу" else if (language == "uz") "Hisob xavfsizligi" else "Account security")}</td></tr>
</table></body></html>"""
}
