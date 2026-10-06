package com.holymeowlabs.catnipkiosk.policy

import com.holymeowlabs.catnipkiosk.settings.KioskSettings
import com.holymeowlabs.catnipkiosk.settings.NavMode
import java.net.IDN
import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

enum class NavDecision { ALLOW, BLOCK }

/**
 * Decides whether a main-frame navigation may proceed. Subframes and
 * subresources are never filtered; callers apply this to top-level loads only.
 */
object NavigationPolicy {
    private val webSchemes = setOf("http", "https")

    fun decide(url: String, settings: KioskSettings): NavDecision {
        if (url == "about:blank") return NavDecision.ALLOW
        val target = parse(url) ?: return NavDecision.BLOCK
        if (target.scheme?.lowercase(Locale.ROOT) !in webSchemes) return NavDecision.BLOCK
        val start = parse(settings.startUrl) ?: return NavDecision.BLOCK
        val host = hostOf(target) ?: return NavDecision.BLOCK
        val startHost = hostOf(start) ?: return NavDecision.BLOCK

        val allowed = when (settings.navMode) {
            NavMode.PAGE_ONLY -> host == startHost && pathOf(target) == pathOf(start)
            NavMode.DOMAIN ->
                host == startHost ||
                    (settings.includeSubdomains && isSubdomain(host, startHost)) ||
                    settings.extraDomains.any { extra ->
                        val e = normalizeHost(extra)
                        e != null && (host == e || isSubdomain(host, e))
                    }
        }
        return if (allowed) NavDecision.ALLOW else NavDecision.BLOCK
    }

    /** Lowercased ASCII (punycode) host without a trailing dot, or null if unusable. */
    fun normalizeHost(host: String?): String? {
        if (host.isNullOrBlank()) return null
        return try {
            IDN.toASCII(host.trimEnd('.'), IDN.ALLOW_UNASSIGNED).lowercase(Locale.ROOT)
                .takeIf { it.isNotEmpty() }
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private fun parse(url: String): URI? =
        try {
            URI(url.trim())
        } catch (e: URISyntaxException) {
            null
        }

    /**
     * java.net.URI leaves getHost() null for non-ASCII hosts, so fall back to
     * the authority with any userinfo and port removed.
     */
    private fun hostOf(uri: URI): String? {
        val raw = uri.host ?: uri.rawAuthority
            ?.substringAfterLast('@')
            ?.let { if (it.startsWith("[")) it.substringBefore(']') + "]" else it.substringBefore(':') }
        return normalizeHost(raw)
    }

    private fun pathOf(uri: URI): String = uri.rawPath.takeUnless { it.isNullOrEmpty() } ?: "/"

    private fun isIpLiteral(host: String) = host.startsWith("[") || host.all { it.isDigit() || it == '.' }

    private fun isSubdomain(host: String, parent: String) = !isIpLiteral(parent) && host.endsWith(".$parent")
}
