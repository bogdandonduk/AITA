package kz.aita

import kz.aita.updates.GeneratedClientBuild

internal fun diagnosticBuildContext(device: DiagnosticDevice): DiagnosticContext {
    val build = GeneratedClientBuild.identity
    return DiagnosticContext(version = build.version, build = build.build, channel = build.channel.name,
        revision = build.revision, device = device).sanitized()
}
