package com.holymeowlabs.catnipkiosk.setup

import com.google.common.truth.Truth.assertThat
import com.holymeowlabs.catnipkiosk.setup.StartUrlInput.Result.Invalid
import com.holymeowlabs.catnipkiosk.setup.StartUrlInput.Result.Ok
import org.junit.Test

class StartUrlInputTest {

    private fun ok(input: String) = StartUrlInput.normalize(input)

    @Test
    fun missingSchemeSpacesAndCaseAreNormalised() {
        assertThat(ok(" Dashboard.Example.com/lobby ")).isEqualTo(Ok("https://dashboard.example.com/lobby"))
    }

    @Test
    fun lanAddressKeepsHttpAndPortAndGetsARootPath() {
        assertThat(ok("http://192.168.1.20:8123")).isEqualTo(Ok("http://192.168.1.20:8123/"))
    }

    @Test
    fun schemeAndHostAreLowercasedButThePathIsNot() {
        assertThat(ok("HTTPS://EXAMPLE.COM/Path")).isEqualTo(Ok("https://example.com/Path"))
    }

    @Test
    fun hostWithPortButNoSchemeDefaultsToHttps() {
        assertThat(ok("localhost:8123/x")).isEqualTo(Ok("https://localhost:8123/x"))
    }

    @Test
    fun queryAndFragmentAreKept() {
        assertThat(ok("example.com/a?b=1#c")).isEqualTo(Ok("https://example.com/a?b=1#c"))
    }

    @Test
    fun unicodeHostIsStoredAsPunycode() {
        assertThat(ok("bücher.example/x")).isEqualTo(Ok("https://xn--bcher-kva.example/x"))
    }

    @Test
    fun chosenSchemeAppliesWhenNoneIsTyped() {
        assertThat(StartUrlInput.normalize("192.168.1.20:8123", Scheme.HTTP)).isEqualTo(Ok("http://192.168.1.20:8123/"))
        assertThat(StartUrlInput.normalize("192.168.1.20:8123", Scheme.HTTPS)).isEqualTo(Ok("https://192.168.1.20:8123/"))
    }

    @Test
    fun aTypedSchemeWinsOverTheChoice() {
        assertThat(StartUrlInput.normalize("https://example.com/", Scheme.HTTP)).isEqualTo(Ok("https://example.com/"))
        assertThat(StartUrlInput.typedScheme(" HTTP://example.com")).isEqualTo(Scheme.HTTP)
        assertThat(StartUrlInput.typedScheme("https://example.com")).isEqualTo(Scheme.HTTPS)
        assertThat(StartUrlInput.typedScheme("example.com")).isNull()
    }

    @Test
    fun unusableInputIsRejected() {
        listOf(
            "",
            "   ",
            "not a url",
            "ftp://example.com",
            "javascript:alert(1)",
            "mailto:a@example.com",
            "my_host.example.com",
            "https://*.example.com/",
            "https://example.com:99999/",
            "https://example.com/a b",
        ).forEach { assertThat(ok(it)).isEqualTo(Invalid) }
    }
}
