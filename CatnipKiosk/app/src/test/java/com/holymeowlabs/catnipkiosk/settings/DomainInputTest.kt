package com.holymeowlabs.catnipkiosk.settings

import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

class DomainInputTest {

    private fun check(vararg cases: Pair<String, String?>) {
        for ((input, expected) in cases) {
            assertWithMessage("normalize(\"$input\")").that(DomainInput.normalize(input)).isEqualTo(expected)
        }
    }

    @Test
    fun urlsAreReducedToTheirHost() = check(
        " https://Login.Example.com/path " to "login.example.com",
        "http://login.example.com:8443/x?y=1#z" to "login.example.com",
        "login.example.com/path" to "login.example.com",
    )

    @Test
    fun aUrlInsideTheQueryOrFragmentIsNotMistakenForTheHost() = check(
        "example.com/login?next=https://evil.example/x" to "example.com",
        "example.com#https://other.example" to "example.com",
        "https://example.com/?u=http://evil.example" to "example.com",
    )

    @Test
    fun bareHostsAreTrimmedAndLowercased() = check(" Example.COM " to "example.com", "example.com." to "example.com")

    @Test
    fun leadingWildcardIsDropped() = check("*.example.com" to "example.com", "https://*.example.com/" to "example.com")

    @Test
    fun unicodeHostsAreStoredInPunycode() = check("bücher.example" to "xn--bcher-kva.example")

    @Test
    fun localhostAndIpAddressesAreAccepted() = check(
        "localhost" to "localhost",
        "192.168.1.20:8123" to "192.168.1.20",
    )

    @Test
    fun invalidInputIsRejected() = check(
        "" to null,
        "   " to null,
        "." to null,
        "https://" to null,
        "exa mple.com" to null,
        "exa_mple.com" to null,
        "user@example.com" to null,
        "example" to null,
        "*.com" to null,
    )

    @Test
    fun hostsJavaUriCannotParseAreRejected() = check(
        // NavigationPolicy only trusts java.net.URI's host; a stored host it can't parse would never match.
        "example.123" to null,
        "-example.com" to null,
    )
}
