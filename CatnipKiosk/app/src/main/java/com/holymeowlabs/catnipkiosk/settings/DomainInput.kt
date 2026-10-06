package com.holymeowlabs.catnipkiosk.settings

import java.net.IDN
import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

/** Turns what an admin types as an "extra allowed domain" into a stored bare host. */
object DomainInput {

    /**
     * "https://Login.Example.com/x" → "login.example.com", "*.example.com" → "example.com",
     * "bücher.example" → "xn--bcher-kva.example". Returns null for anything that is not a
     * usable host, including hosts java.net.URI cannot parse (NavigationPolicy would never
     * match them).
     */
    fun normalize(input: String): String? {
        var s = input.trim()
        // Only a scheme at the very start counts; "://" inside a query or fragment does not.
        if (Regex("^[A-Za-z][A-Za-z0-9+.-]*://").containsMatchIn(s)) s = s.substringAfter("://")
        s = s.takeWhile { it != '/' && it != '?' && it != '#' }
        s = s.substringBefore(':').removePrefix("*.").trimEnd('.')
        if (s.isEmpty()) return null

        val ascii = try {
            IDN.toASCII(s).lowercase(Locale.ROOT)
        } catch (e: IllegalArgumentException) {
            return null
        }
        if (ascii.isEmpty() || (ascii != "localhost" && '.' !in ascii)) return null

        val parsedHost = try {
            URI("https", ascii, "/", null).host
        } catch (e: URISyntaxException) {
            null
        }
        return ascii.takeIf { parsedHost == it }
    }
}
