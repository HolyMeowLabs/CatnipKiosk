package com.holymeowlabs.catnipkiosk.setup

import com.holymeowlabs.catnipkiosk.policy.NavDecision
import com.holymeowlabs.catnipkiosk.policy.NavigationPolicy
import com.holymeowlabs.catnipkiosk.settings.DomainInput
import com.holymeowlabs.catnipkiosk.settings.KioskSettings
import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

enum class Scheme { HTTP, HTTPS }

/** Turns what an admin types as the start page into a canonical http(s) URL. */
object StartUrlInput {
    /** The scheme the admin typed, if any; the UI's http/https choice follows it. */
    fun typedScheme(input: String): Scheme? =
        when (schemeWithSlashes.find(input.trim())?.groupValues?.get(1)?.lowercase(Locale.ROOT)) {
            "http" -> Scheme.HTTP
            "https" -> Scheme.HTTPS
            else -> null
        }

    sealed interface Result {
        data class Ok(val url: String) : Result
        data object Invalid : Result
    }

    private val schemeWithSlashes = Regex("^([A-Za-z][A-Za-z0-9+.-]*)://")

    /** "javascript:", "mailto:" and the like; "localhost:8123" is a host and port instead. */
    private val opaqueScheme = Regex("^[A-Za-z][A-Za-z0-9+.-]*:(?![0-9])")

    fun normalize(input: String, defaultScheme: Scheme = Scheme.HTTPS): Result {
        val text = input.trim()
        if (text.isEmpty()) return Result.Invalid

        val scheme: String
        val rest: String
        val match = schemeWithSlashes.find(text)
        if (match != null) {
            scheme = match.groupValues[1].lowercase(Locale.ROOT)
            rest = text.substring(match.range.last + 1)
        } else {
            if (opaqueScheme.containsMatchIn(text)) return Result.Invalid
            scheme = defaultScheme.name.lowercase(Locale.ROOT)
            rest = text
        }
        if (scheme != "http" && scheme != "https") return Result.Invalid

        val authorityEnd = rest.indexOfFirst { it == '/' || it == '?' || it == '#' }.let { if (it < 0) rest.length else it }
        val authority = rest.substring(0, authorityEnd)
        val tail = rest.substring(authorityEnd)
        if ('@' in authority || '*' in authority) return Result.Invalid

        val hostText = authority.substringBefore(':')
        val portText = authority.substringAfter(':', missingDelimiterValue = "")
        // DomainInput gives the punycode, lowercase host that NavigationPolicy compares against.
        val host = DomainInput.normalize(hostText, allowSingleLabel = true) ?: return Result.Invalid
        if (portText.isNotEmpty() && portText.toIntOrNull()?.takeIf { it in 1..65535 } == null) return Result.Invalid
        val port = if (portText.isEmpty()) "" else ":$portText"

        val path = if (tail.isEmpty() || tail.startsWith('?') || tail.startsWith('#')) "/$tail" else tail
        val url = "$scheme://$host$port$path"
        try {
            URI(url)
        } catch (e: URISyntaxException) {
            return Result.Invalid
        }
        // A stored start URL the policy can't parse would block itself.
        if (NavigationPolicy.decide(url, KioskSettings(startUrl = url)) != NavDecision.ALLOW) return Result.Invalid
        return Result.Ok(url)
    }
}
